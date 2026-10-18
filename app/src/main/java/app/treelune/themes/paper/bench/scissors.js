"use strict";
/*
  The cut paper theme's scissors (docs/design/paper-theme.md, line 9), shared by its benches: a
  Lucide icon walked along its strokes, as IconStrokes does on the phone (a point every 0.08 of
  the grid of 24), then cut again in straight facets, the way scissors go round a curve: a point
  kept wherever the stroke truly turns (a corner of the icon), and between two of them one every
  `facet` units. Each stroke is laid as a strip of the cut's width, its ends cut square and its
  joins sharp, on a hard shadow shifted down and right. A stroke shorter than its width (Lucide's
  dots, a ".01" long line held up by round caps) is a small square of paper instead.
  PaperIcons.kt does the same with the same numbers.
*/
window.PaperScissors = (() => {
  /** The settings the bench opens on: the theme's, chosen here on 2026-10-18. */
  const SETTINGS = { width: 2.6, facet: 2.1, corner: 29, shadow: 1.2, jitter: 0.36 };
  const STEP = 0.08;

  let measure = null;
  function measurer() {
    if (!measure) {
      measure = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      measure.style.cssText = "position:absolute;width:0;height:0;overflow:hidden";
      measure.setAttribute("viewBox", "0 0 24 24");
      document.body.appendChild(measure);
    }
    return measure;
  }

  /** The SVG's inner elements, without its own <svg> tag. */
  const lucideInner = (svg) => svg.replace(/^[\s\S]*?<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");

  /**
   * The icon's strokes as lists of points STEP apart, split where a path jumps (a new "M"), each
   * marked closed when it ends where it began; and the elements Lucide fills, as their outlines.
   */
  function strokes(inner) {
    const svg = measurer();
    svg.innerHTML = inner;
    const out = [], fills = [];
    for (const el of svg.querySelectorAll("path, circle, rect, line, polyline, polygon, ellipse")) {
      const fill = el.getAttribute("fill");
      const total = el.getTotalLength();
      let cur = [], prev = null;
      const push = () => { if (cur.length) out.push(cur); cur = []; };
      for (let s = 0; s <= total + 1e-6; s += STEP) {
        const p = el.getPointAtLength(Math.min(s, total));
        if (prev && Math.hypot(p.x - prev.x, p.y - prev.y) > STEP * 6) push();
        cur.push({ x: p.x, y: p.y });
        prev = p;
      }
      push();
      if (fill && fill !== "none") fills.push(el);
    }
    for (const s of out) {
      const a = s[0], b = s[s.length - 1];
      s.closed = s.length > 2 && Math.hypot(a.x - b.x, a.y - b.y) < STEP * 2;
    }
    return { list: out, fills: fills.map((el) => el.outerHTML) };
  }

  const length = (s) => s.reduce((sum, p, i) => i ? sum + Math.hypot(p.x - s[i - 1].x, p.y - s[i - 1].y) : 0, 0);
  const rand = (i, seed) => { const x = Math.sin(i * 12.9898 + seed * 78.233) * 43758.5453; return x - Math.floor(x); };

  /**
   * A stroke cut in facets: the indices where it turns more than `corner` degrees, measured over
   * a few points either side so that a curve's slow turn never counts, then a point every `facet`
   * between two of them, the facets of a stretch made equal.
   */
  function cut(s, p) {
    const n = s.length, w = 4;
    const dir = (i, j) => Math.atan2(s[j].y - s[i].y, s[j].x - s[i].x);
    const keep = [0];
    for (let i = w; i < n - w; i++) {
      let turn = Math.abs(dir(i, i + w) - dir(i - w, i)) * 180 / Math.PI;
      if (turn > 180) turn = 360 - turn;
      // A corner is the sharpest point of its neighbourhood, kept once.
      if (turn > p.corner && i - keep[keep.length - 1] > w) {
        let best = i, bestTurn = turn;
        for (let j = i + 1; j < Math.min(n - w, i + w); j++) {
          let t = Math.abs(dir(j, j + w) - dir(j - w, j)) * 180 / Math.PI;
          if (t > 180) t = 360 - t;
          if (t > bestTurn) { best = j; bestTurn = t; }
        }
        keep.push(best);
        i = best + w;
      }
    }
    keep.push(n - 1);
    const pts = [];
    for (let k = 0; k < keep.length - 1; k++) {
      const a = keep[k], b = keep[k + 1];
      const stretch = length(s.slice(a, b + 1));
      const parts = Math.max(1, Math.round(stretch / p.facet));
      for (let q = 0; q < parts; q++) pts.push(s[Math.round(a + (b - a) * q / parts)]);
    }
    pts.push(s[n - 1]);
    return pts;
  }

  /** Draw the icon on a canvas of cssSize px: its shadow, then the paper, in `colour`. */
  function paint(canvas, drawing, cssSize, colour, shadowColour, p = SETTINGS, seed = 1) {
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(cssSize * dpr); canvas.height = Math.round(cssSize * dpr);
    canvas.style.width = cssSize + "px"; canvas.style.height = cssSize + "px";
    const ctx = canvas.getContext("2d");
    ctx.scale(canvas.width / 24, canvas.height / 24);
    const shapes = [];
    for (const s of drawing.list) {
      if (length(s) < p.width) {
        // A dot: a small square of paper, a little askew.
        const c = s[Math.floor(s.length / 2)], h = p.width / 2, a = (rand(shapes.length, seed) - 0.5) * 0.5;
        const path = new Path2D();
        const corner = (dx, dy) => [c.x + dx * Math.cos(a) - dy * Math.sin(a), c.y + dx * Math.sin(a) + dy * Math.cos(a)];
        path.moveTo(...corner(-h, -h)); path.lineTo(...corner(h, -h)); path.lineTo(...corner(h, h)); path.lineTo(...corner(-h, h)); path.closePath();
        shapes.push({ path, fill: true });
        continue;
      }
      const pts = cut(s, p);
      const path = new Path2D();
      pts.forEach((q, i) => {
        const j = p.jitter * (rand(i + shapes.length * 31, seed) - 0.5);
        i ? path.lineTo(q.x + j, q.y - j) : path.moveTo(q.x, q.y);
      });
      if (s.closed) path.closePath();
      shapes.push({ path, fill: false });
    }
    for (const svg of drawing.fills) shapes.push({ path: new Path2D(svgPath(svg)), fill: true });
    for (const [dx, dy, col] of [[p.shadow, p.shadow, shadowColour], [0, 0, colour]]) {
      if (!col) continue;
      ctx.save(); ctx.translate(dx, dy);
      ctx.strokeStyle = col; ctx.fillStyle = col;
      ctx.lineWidth = p.width; ctx.lineCap = "butt"; ctx.lineJoin = "miter"; ctx.miterLimit = 4;
      for (const sh of shapes) sh.fill ? ctx.fill(sh.path) : ctx.stroke(sh.path);
      ctx.restore();
    }
  }

  /** The path data of a filled element: a path's own, or its strokes' outline traced point by point. */
  function svgPath(svgEl) {
    const m = svgEl.match(/\sd="([^"]+)"/);
    if (m) return m[1];
    const svg = measurer();
    svg.innerHTML = svgEl;
    const el = svg.firstElementChild, total = el.getTotalLength();
    let d = "";
    for (let s = 0; s <= total; s += STEP * 4) { const q = el.getPointAtLength(s); d += (d ? "L" : "M") + q.x + " " + q.y; }
    return d + "Z";
  }

  return { SETTINGS, lucideInner, strokes, cut, paint };
})();
