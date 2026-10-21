"use strict";
/*
  The ink theme's brush (docs/themes/ink.md, line 9), shared by its benches: any SVG drawing
  (a Lucide icon, a rule, a ring) walked along its strokes and stamped in discs whose size follows
  the brush. Held at a slant, it presses where it lands and where the stroke turns, swells once
  slowly over a stroke and lifts off in a point. The theme does the same on the phone with the
  strokes of the drawing.

  Lengths are in the drawing's own units, scaled by `unit`: 1 for a Lucide icon on its grid of 24,
  where the width 1.3 is the stroke's; for a rule drawn in dp, the dp a width of 1 stands for.
*/
window.InkBrush = (() => {
  /** The settings chosen on 2026-10-17 at the brush bench. */
  const SETTINGS = { width: 1.3, taper: 0.5, direction: 0.45, corner: 1, attack: 0.45, swell: 0.3, dry: 0, wet: true };

  // A hidden SVG in the page, so each element can be measured along its length.
  let measure = null;
  function measurer(viewBox) {
    if (!measure) {
      measure = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      measure.style.cssText = "position:absolute;width:0;height:0;overflow:hidden";
      document.body.appendChild(measure);
    }
    measure.setAttribute("viewBox", viewBox);
    return measure;
  }

  /**
   * A drawing's strokes as lists of points, one point every `step` units, split where a path jumps
   * (a new "M"). `inner` is the SVG's content, its elements drawn in the coordinates of `viewBox`.
   */
  function strokes(inner, viewBox = "0 0 24 24", unit = 1) {
    const step = 0.08 * unit;
    const svg = measurer(viewBox);
    svg.innerHTML = inner;
    const out = [];
    for (const el of svg.querySelectorAll("path, circle, rect, line, polyline, polygon, ellipse")) {
      const total = el.getTotalLength();
      let cur = [];
      let prev = null;
      for (let s = 0; s <= total + 1e-6; s += step) {
        const p = el.getPointAtLength(Math.min(s, total));
        if (prev && Math.hypot(p.x - prev.x, p.y - prev.y) > step * 6) { if (cur.length > 1) out.push(cur); cur = []; }
        cur.push({ x: p.x, y: p.y });
        prev = p;
      }
      if (cur.length > 1) out.push(cur);
      else if (cur.length === 1) out.push([cur[0], cur[0]]);
    }
    return { list: out, step, unit };
  }

  const smooth = (t) => t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t);
  const rand = (i, seed) => { const x = Math.sin(i * 12.9898 + seed * 78.233) * 43758.5453; return x - Math.floor(x); };

  /** The width along each stroke, as a factor of the brush's width. */
  function widths(drawn, p, seed) {
    const { list, step, unit } = drawn;
    return list.map((pts, si) => {
      const n = pts.length;
      const len = (n - 1) * step;
      const closed = n > 3 && Math.hypot(pts[0].x - pts[n - 1].x, pts[0].y - pts[n - 1].y) < 0.2 * unit;
      const attackLen = Math.min(len * 0.3, 1.2 * unit);
      const releaseLen = Math.min(len * 0.5, (0.6 + 3.4 * p.taper) * unit);
      const phase = (rand(si, seed) - 0.5) * 0.8;
      const dir = (a, b) => Math.atan2(pts[b].y - pts[a].y, pts[b].x - pts[a].x);
      const at = (i) => Math.max(0, Math.min(n - 1, i));
      const raw = [];
      for (let i = 0; i < n; i++) {
        const u = i * step;
        const rest = len - u;
        let w = 1;
        // The brush is held at a slant: a stroke rising gently to the right is its thinnest.
        const across = Math.abs(Math.sin(dir(at(i - 4), at(i + 4)) + 0.2));
        w *= 1 - 0.55 * p.direction * (1 - across);
        // It presses where the stroke turns, as the hand pauses.
        const d = dir(at(i), at(i + 6)) - dir(at(i - 6), at(i));
        const turn = Math.abs(Math.atan2(Math.sin(d), Math.cos(d)));
        w *= 1 + 0.6 * p.corner * Math.min(1, turn / (Math.PI / 2));
        // One slow swell over the whole stroke, never a ripple.
        w *= 1 + p.swell * Math.sin(Math.PI * (u / Math.max(len, 0.01)) + phase);
        if (!closed) {
          // It lands pressed (or lightly), and lifts off in a long point.
          if (attackLen > 0) w *= 1 + 0.6 * p.attack * (1 - smooth(u / attackLen));
          if (releaseLen > 0) w *= 0.06 + 0.94 * smooth(rest / releaseLen);
        }
        raw.push(w);
      }
      // Smoothed, so a width only ever flows from one value to the next.
      const half = 5;
      return raw.map((_, i) => { let sum = 0, cnt = 0; for (let j = i - half; j <= i + half; j++) if (j >= 0 && j < n) { sum += raw[j]; cnt++; } return sum / cnt; });
    });
  }

  /**
   * Paints `drawn` (from strokes) on `canvas`, `cssW` × `cssH` CSS pixels showing the drawing's
   * `viewW` × `viewH` units, in `ink`. A wet brush lays a rim at half its opacity around a core at
   * 72 % of its width.
   */
  function paint(canvas, drawn, { viewW, viewH, cssW, cssH }, ink, p = SETTINGS, seed = 1) {
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(cssW * dpr); canvas.height = Math.round(cssH * dpr);
    canvas.style.width = cssW + "px"; canvas.style.height = cssH + "px";
    const ctx = canvas.getContext("2d");
    const transform = [cssW * dpr / viewW, 0, 0, cssH * dpr / viewH, 0, 0];
    ctx.setTransform(...transform);
    ctx.fillStyle = ink;
    const w = widths(drawn, p, seed);
    const { list, step, unit } = drawn;
    const pass = (c, scale) => {
      list.forEach((pts, si) => {
        const n = pts.length;
        const len = (n - 1) * step;
        const releaseLen = Math.min(len * 0.5, (0.6 + 3.4 * p.taper) * unit);
        for (let i = 0; i < n; i++) {
          const rest = len - i * step;
          // A dry brush leaves gaps towards the end of a long stroke.
          if (p.dry > 0 && len > 3 * unit && rest < releaseLen * 1.2 && rand(i * 7 + si, seed) < p.dry * (1 - rest / (releaseLen * 1.2)) * 0.8) continue;
          const r = Math.max(0.04 * unit, p.width * unit * w[si][i] * scale / 2);
          c.beginPath(); c.arc(pts[i].x, pts[i].y, r, 0, Math.PI * 2); c.fill();
        }
      });
    };
    if (p.wet) {
      // The pale rim drawn opaque on its own canvas, then laid at once, so overlapping discs don't add up.
      const rim = document.createElement("canvas");
      rim.width = canvas.width; rim.height = canvas.height;
      const rctx = rim.getContext("2d");
      rctx.setTransform(...transform); rctx.fillStyle = ink;
      pass(rctx, 1);
      ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.globalAlpha = 0.5; ctx.drawImage(rim, 0, 0); ctx.globalAlpha = 1;
      ctx.setTransform(...transform);
      pass(ctx, 0.72);
    } else pass(ctx, 1);
  }

  /** A Lucide icon's content (what its <svg> holds), from its file's text. */
  const lucideInner = (svgText) => svgText.match(/>\s*([\s\S]*)<\/svg>/)[1].trim();

  return { SETTINGS, strokes, paint, lucideInner };
})();
