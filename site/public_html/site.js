// The site's gestures (docs/design/website.md): the opening band's themes and the light/dark switch,
// the explorers whose choices change what the phone shows, and a capture or a video enlarged alone.
// The page is rendered whole by page.php, its first choices included; this only makes it respond.
"use strict";

// A horizontal swipe on an element calls step(-1) or step(1)
function onSwipe(el, step) {
  let x0 = null, y0 = null;
  el.addEventListener("touchstart", e => { x0 = e.touches[0].clientX; y0 = e.touches[0].clientY; }, { passive: true });
  el.addEventListener("touchend", e => {
    if (x0 === null) return;
    const dx = e.changedTouches[0].clientX - x0, dy = e.changedTouches[0].clientY - y0;
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) step(dx < 0 ? 1 : -1);
    x0 = null;
  });
}

const fontFamily = family => family === "system-ui" ? "system-ui, sans-serif" : `"${family}", system-ui, sans-serif`;

// ---- The opening band: a theme dresses it, in the site's mode ----

const bandData = JSON.parse(document.getElementById("band-themes").textContent);
const THEMES = bandData.themes;
const band = document.getElementById("band");
const bandShot = document.getElementById("band-shot");
const pickerList = document.getElementById("picker-list");
const modeSwitch = document.getElementById("mode-switch");

// Light or dark for the whole site: the visitor's preference first, then the switch, remembered in this browser
const prefersDark = window.matchMedia("(prefers-color-scheme: dark)");
function storedMode() { try { return localStorage.getItem("mode"); } catch (e) { return null; } }
let mode = storedMode() || (prefersDark.matches ? "dark" : "light");
let current = THEMES[bandData.first];

function applyTheme(theme) {
  current = theme;
  const [ground, ink] = theme[mode];
  const line = ink + "22";
  band.style.background = theme.grid
    ? `linear-gradient(${line} 1px, transparent 1px) 0 0 / 22px 22px, linear-gradient(90deg, ${line} 1px, transparent 1px) 0 0 / 22px 22px, ${ground}`
    : ground;
  band.style.color = ink;
  band.style.setProperty("--band-bg", ground);
  const shot = theme.shots[mode];
  bandShot.src = shot.src;
  bandShot.alt = theme.alt;
  // A home capture not taken yet keeps its mark, as page.php writes it
  bandShot.parentElement.querySelector(".stand-in")?.remove();
  if (shot.stand_in) {
    const mark = document.createElement("span");
    mark.className = "stand-in";
    mark.textContent = bandData.standIn.replace("%s", shot.file);
    bandShot.after(mark);
  }
  pickerList.querySelectorAll("button").forEach(b => b.setAttribute("aria-pressed", b.dataset.id === theme.id));
  const name = document.getElementById("band-current");
  name.textContent = theme.name;
  name.style.fontFamily = fontFamily(theme.family);
  document.getElementById("band-rank").textContent = `${THEMES.indexOf(theme) + 1} / ${THEMES.length}`;
}

function applyMode(m) {
  mode = m;
  document.documentElement.dataset.theme = m;
  modeSwitch.querySelectorAll("button").forEach(b => b.setAttribute("aria-pressed", b.dataset.mode === m));
  applyTheme(current);
}

pickerList.querySelectorAll("button").forEach(b => b.onclick = () => applyTheme(THEMES.find(t => t.id === b.dataset.id)));
const stepTheme = d => applyTheme(THEMES[(THEMES.indexOf(current) + d + THEMES.length) % THEMES.length]);
document.querySelectorAll("#band-carousel .arrow").forEach(b => b.onclick = () => stepTheme(+b.dataset.step));
onSwipe(document.querySelector(".band .phone-box"), stepTheme);

modeSwitch.querySelectorAll("button").forEach(b => b.onclick = () => {
  try { localStorage.setItem("mode", b.dataset.mode); } catch (e) {}
  applyMode(b.dataset.mode);
});
prefersDark.addEventListener("change", e => { if (!storedMode()) applyMode(e.matches ? "dark" : "light"); });
applyMode(mode);

// On a phone held upright, the header's mark stays away while the large one of the pitch screen is in view
const upright = window.matchMedia("(max-width: 820px) and (orientation: portrait), (max-height: 500px) and (orientation: landscape)");
const brand = document.getElementById("brand");
new IntersectionObserver(([e]) => brand.classList.toggle("away", upright.matches && e.isIntersecting), { threshold: .4 })
  .observe(document.querySelector(".pitch"));
brand.classList.toggle("away", upright.matches && window.scrollY < window.innerHeight / 2);

// The arrow at the bottom of the pitch brings the next screen right under the header
document.getElementById("more").onclick = () => {
  const showcase = document.getElementById("showcase");
  const next = getComputedStyle(showcase).display === "contents" ? document.querySelector(".privacy") : showcase;
  next.scrollIntoView({ behavior: "smooth" });
};

// ---- The explorers: a choice changes the stage, or outlines its tile on a shared one ----

document.querySelectorAll(".explorer").forEach(root => {
  const items = JSON.parse(root.querySelector(".ex-items").textContent);
  const stage = root.querySelector(".ex-stage");
  const list = root.querySelectorAll(".ex-list button");
  let at = 0;

  function choose(i) {
    at = i;
    const item = items[i];
    root.querySelector(".carousel .current").innerHTML = item.pill + `<b></b><span class="rank">${i + 1} / ${items.length}</span>`;
    root.querySelector(".carousel .current b").textContent = item.name;
    root.querySelector(".ex-caption h3").textContent = item.name;
    root.querySelector(".ex-caption p").textContent = item.text;
    root.querySelector(".ex-caption .theme-label").textContent = item.theme;
    list.forEach((b, j) => b.setAttribute("aria-pressed", i === j));
    const template = root.querySelector(`template[data-stage="${i}"]`);
    if (template) {
      stage.replaceChildren(template.content.cloneNode(true));
    } else {
      // The zone: one capture for all, the chosen tool's tile outlined; the routine has none
      stage.querySelectorAll("[data-tile]").forEach(tile => tile.setAttribute("aria-current", tile.dataset.tile === item.tile));
      stage.querySelector(".zone-screen")?.classList.toggle("focus", item.tile !== null);
    }
  }

  list.forEach(b => b.onclick = () => choose(+b.dataset.index));
  const step = d => choose((at + d + items.length) % items.length);
  root.querySelectorAll(".carousel .arrow").forEach(b => b.onclick = () => step(+b.dataset.step));
  onSwipe(stage, step);
  // Touching a tile on the zone's capture chooses its tool: another way to the same choice
  stage.addEventListener("click", e => {
    const tile = e.target.closest("[data-tile]");
    if (tile) choose(items.findIndex(item => item.tile === tile.dataset.tile));
  });
  choose(0);
});

// ---- A capture or a video touched: shown alone, full screen, closed by a touch or Escape ----

const lightbox = document.getElementById("lightbox");
const lightboxBody = lightbox.querySelector(".lb-body");
document.addEventListener("click", e => {
  const media = e.target.closest(".zoomable");
  if (!media) return;
  const copy = media.cloneNode(true);
  copy.classList.remove("zoomable");
  copy.removeAttribute("id");
  lightboxBody.replaceChildren(copy);
  if (copy.tagName === "VIDEO") copy.play().catch(() => {});
  lightbox.showModal();
});
lightbox.addEventListener("click", () => lightbox.close());
lightbox.addEventListener("close", () => lightboxBody.replaceChildren());
