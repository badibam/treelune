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

// ---- The header's menu: the section on screen, its link marked, and on a phone its name and rank ----

const nav = document.getElementById("nav");
const where = document.getElementById("where");
const whereRank = document.getElementById("where-rank");

// What a link leads to: its section, or the opening whole for the themes, which on a computer are
// laid out without a box of their own, in the opening's one screen
const boxOf = el => getComputedStyle(el).display === "contents" ? el.closest(".band") : el;
const sectionLinks = [...nav.querySelectorAll('a[href^="#"]')].map(a => ({ a, section: document.getElementById(a.hash.slice(1)) }));
// A link scrolls smoothly to its section, unless the visitor asked for less motion
const lessMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
sectionLinks.forEach(({ a, section }) => a.addEventListener("click", e => {
  e.preventDefault();
  boxOf(section).scrollIntoView({ behavior: lessMotion.matches ? "auto" : "smooth", block: "start" });
}));
// The section on screen: the last one of the menu starting at or above the header's bottom; none on the pitch
function showWhere() {
  const line = window.scrollY + document.querySelector("header").offsetHeight + 2;
  let on = null;
  sectionLinks.forEach(link => { if (boxOf(link.section).getBoundingClientRect().top + window.scrollY <= line) on = link; });
  sectionLinks.forEach(link => link.a.setAttribute("aria-current", link === on));
  where.textContent = on ? on.a.textContent : "";
  whereRank.textContent = on ? `${sectionLinks.indexOf(on) + 1} / ${sectionLinks.length}` : "";
}
let whereQueued = false;
window.addEventListener("scroll", () => {
  if (whereQueued) return;
  whereQueued = true;
  requestAnimationFrame(() => { whereQueued = false; showWhere(); });
}, { passive: true });
window.addEventListener("resize", showWhere);
showWhere();

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
      // The zone: one capture for all, the chosen piece's tile outlined
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

// ---- The other pieces, a sideways row on a phone and a low screen: the carousel's arrows and rank ----

const piecesRow = document.querySelector(".pieces .grid");
const piecesCards = [...piecesRow.children];
const piecesRank = document.querySelector(".pieces-carousel .rank");
const nearestCard = () => {
  const middle = piecesRow.scrollLeft + piecesRow.clientWidth / 2;
  return piecesCards.reduce((best, card, i) =>
    Math.abs(card.offsetLeft + card.offsetWidth / 2 - middle) < Math.abs(piecesCards[best].offsetLeft + piecesCards[best].offsetWidth / 2 - middle) ? i : best, 0);
};
piecesRow.addEventListener("scroll", () => { piecesRank.textContent = `${nearestCard() + 1} / ${piecesCards.length}`; }, { passive: true });
document.querySelectorAll(".pieces-carousel .arrow").forEach(b => b.onclick = () => {
  const card = piecesCards[(nearestCard() + +b.dataset.step + piecesCards.length) % piecesCards.length];
  piecesRow.scrollTo({ left: card.offsetLeft - (piecesRow.clientWidth - card.offsetWidth) / 2, behavior: "smooth" });
});

// ---- The keyboard: the up and down arrows, and page up and down, go to the previous or the next section ----

// The points the scrolling settles on, as laid out now: a section's start, or each screen of the
// opening on a phone held upright or a low screen; two at the same height count once
function sectionStarts() {
  const starts = [];
  document.querySelectorAll(".band, .band .pitch, .band .showcase, main section").forEach(el => {
    if (!getComputedStyle(el).scrollSnapAlign.includes("start")) return;
    const top = el.getBoundingClientRect().top + window.scrollY;
    if (!starts.some(s => Math.abs(s.top - top) < 2)) starts.push({ el, top });
  });
  return starts.sort((a, b) => a.top - b.top);
}
document.addEventListener("keydown", e => {
  if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey || lightbox.open) return;
  const step = { ArrowDown: 1, PageDown: 1, ArrowUp: -1, PageUp: -1 }[e.key];
  if (!step || e.target.closest("input, textarea, select, [contenteditable]")) return;
  const header = document.querySelector("header").offsetHeight;
  const starts = sectionStarts();
  const at = window.scrollY + header;
  // The section on screen: the last one starting at or above the header's bottom
  let current = 0;
  starts.forEach((s, i) => { if (s.top <= at + 2) current = i; });
  const next = starts[current + step];
  if (!next) return;
  e.preventDefault();
  next.el.scrollIntoView({ behavior: lessMotion.matches ? "auto" : "smooth", block: "start" });
});
