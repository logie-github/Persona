// Persona 1's screen after the reference picture: the disc and moon, the girl (her own layers from the picture, skinned live on the GPU), and the menu
// as warped lettering. Every word's glyph outlines are bent to a quad that follows the rim and fans down the page; the chosen word sits on a
// skewed white band, and the lettering turns gold exactly where the band falls (a clip), purple elsewhere. The app talks to it through
// window.Skin and the Android bridge (select / activate).
import opentype from './vendor/opentype.module.js';
import { Girl } from './girl.js';

const AW = 1977, AH = 1398;
const ART = await (await fetch('art.json')).json();
const C = { outside: '#c2a9da', disc: '#d8b0fa', rim: '#8548a3', moon: '#fdf89d', ring: '#e7cfea', shard: '#aa83c8', white: '#fcfafc', gold: '#f2c31a',
  grape: '#770ca4', violet: '#9741d6', deep: '#481d6e', ink: '#2a1041', black: '#201b1e' };
const WORD_COLOURS = [C.grape, C.violet, C.deep, C.rim, C.ink, C.black];
C.rim2 = C.rim;
const FONT_FILES = { Anton: 'fonts/Anton-Regular.ttf', Bowlby: 'fonts/BowlbyOne-Regular.ttf', Bungee: 'fonts/Bungee-Regular.ttf', Passion: 'fonts/PassionOne-Black.ttf', Archivo: 'fonts/ArchivoBlack-Regular.ttf' };
const LABEL_FONT = { PLAY: 'Bowlby', INSTALL: 'Anton', UNINSTALL: 'Anton', VERSION: 'Passion', MODS: 'Bungee', OPTIONS: 'Archivo' };
const FALLBACK = ['Anton', 'Bowlby', 'Passion', 'Bungee', 'Archivo'];
const fonts = {};
await Promise.all(Object.entries(FONT_FILES).map(async ([k, u]) => { fonts[k] = await opentype.load(u); }));

const $ = (id) => document.getElementById(id);
const SVGNS = 'http://www.w3.org/2000/svg';
const mk = (name, attrs, parent) => { const e = document.createElementNS(SVGNS, name); for (const k in attrs) e.setAttribute(k, attrs[k]); parent && parent.appendChild(e); return e; };

const state = { game: { title: 'PERSONA', version: '' }, entries: [], selected: 0, progress: null };
let pos = 0, vel = 0, nudge = 0, nudgeVel = 0, L = { s: 1, ox: 0, oy: 0 };

// ---------- the glyph outlines, as contours in the word's own unit square ----------
const cache = new Map();
function flatten(cmds) {
  const contours = []; let cur = null, x = 0, y = 0, sx = 0, sy = 0;
  const bez = (pts, n) => { for (let i = 1; i <= n; i++) { const t = i / n, u = 1 - t; if (pts.length === 4) cur.push([u*u*u*pts[0][0] + 3*u*u*t*pts[1][0] + 3*u*t*t*pts[2][0] + t*t*t*pts[3][0], u*u*u*pts[0][1] + 3*u*u*t*pts[1][1] + 3*u*t*t*pts[2][1] + t*t*t*pts[3][1]]); else cur.push([u*u*pts[0][0] + 2*u*t*pts[1][0] + t*t*pts[2][0], u*u*pts[0][1] + 2*u*t*pts[1][1] + t*t*pts[2][1]]); } };
  for (const c of cmds) {
    if (c.type === 'M') { cur = [[c.x, c.y]]; contours.push(cur); x = sx = c.x; y = sy = c.y; }
    else if (c.type === 'L') { cur.push([c.x, c.y]); x = c.x; y = c.y; }
    else if (c.type === 'C') { bez([[x, y], [c.x1, c.y1], [c.x2, c.y2], [c.x, c.y]], 14); x = c.x; y = c.y; }
    else if (c.type === 'Q') { bez([[x, y], [c.x1, c.y1], [c.x, c.y]], 10); x = c.x; y = c.y; }
    else if (c.type === 'Z') { x = sx; y = sy; }
  }
  return contours;
}
function glyphs(text, fontName) {
  const key = text + '|' + fontName; if (cache.has(key)) return cache.get(key);
  const path = fonts[fontName].getPath(text, 0, 0, 200); const cs = flatten(path.commands);
  let x1 = 1e9, y1 = 1e9, x2 = -1e9, y2 = -1e9;
  for (const c of cs) for (const [x, y] of c) { x1 = Math.min(x1, x); y1 = Math.min(y1, y); x2 = Math.max(x2, x); y2 = Math.max(y2, y); }
  const out = cs.map((c) => c.map(([x, y]) => [(x - x1) / (x2 - x1), (y - y1) / (y2 - y1)]));
  const r = { contours: out, aspect: (x2 - x1) / (y2 - y1) }; cache.set(key, r); return r;
}
// a point of the unit square onto the word's quad (bilinear), with the baseline bowed a little so the word curves along the rim
function warp(q, u, v) {
  const x = (1 - v) * ((1 - u) * q.TL[0] + u * q.TR[0]) + v * ((1 - u) * q.BL[0] + u * q.BR[0]);
  const y = (1 - v) * ((1 - u) * q.TL[1] + u * q.TR[1]) + v * ((1 - u) * q.BL[1] + u * q.BR[1]);
  return [x, y + q.bow * Math.sin(Math.PI * u) * (1 - 0.5 * v)];
}
function pathOf(contours, q) {
  let d = '';
  for (const c of contours) { for (let i = 0; i < c.length; i++) { const [px, py] = warp(q, c[i][0], c[i][1]); d += (i ? 'L' : 'M') + px.toFixed(1) + ' ' + py.toFixed(1); } d += 'Z'; }
  return d;
}

// ---------- layout ----------
function layout() {
  const W = innerWidth, H = innerHeight;
  const s = Math.min(W / AW, H / AH); L = { W, H, s, ox: (W - AW * s) / 2, oy: (H - AH * s) / 2 };
  $('stage').style.transform = `translate(${L.ox}px, ${L.oy}px) scale(${s})`;
  $('art').setAttribute('transform', `translate(${L.ox} ${L.oy}) scale(${s})`);
  if (window.__girl) window.__girl.resize(s, devicePixelRatio || 1);
}
const rimX = (y, extra) => { const r = ART.disc.r + ART.disc.rim + extra, dy = y - ART.disc.y; return ART.disc.x + Math.sqrt(Math.max(0, r * r - dy * dy)); };

function slots() {
  const n = state.entries.length, top = 90, bottom = 1225, base = (bottom - top) / (n + 0.9);
  let y = top; const out = [];
  for (let i = 0; i < n; i++) { const h = base * (1 + 0.9 * (1 - Math.min(Math.abs(i - pos), 1))); out.push({ top: y, h }); y += h; }
  return out;
}
function quadFor(i, slot, aspect) {
  const { top, h } = slot, yc = top + h / 2;
  const xr = AW - 18, xlC = rimX(yc, 46), room = xr - xlC;
  // a word is as tall as its length lets it be (a long word cannot be stretched into a tall one), within its slot's share of the page
  const hNat = room / aspect, hUse = Math.max(hNat * 0.7, Math.min(h * 0.94, hNat * 2.0));
  const t0 = yc - hUse / 2, t1 = yc + hUse / 2;
  const xlT = rimX(t0, 46), xlB = rimX(t1, 46);
  const fan = i - pos;                                              // words above the chosen one tilt up to the right, those below tilt down
  const tilt = Math.tan((-4.5 + 2.6 * fan) * Math.PI / 180) * room;
  const hR = hUse * (1 - 0.05 * fan);                               // and are a little shorter at the far end
  return { TL: [xlT, t0], TR: [xr, yc + tilt - hR / 2], BL: [xlB, t1], BR: [xr, yc + tilt + hR / 2], bow: hUse * 0.10 * Math.sign(fan || 1) * Math.min(1, Math.abs(fan) + 0.3) * -1 };
}
const wordEls = [];
function buildWords() {
  $('words').innerHTML = ''; $('gold').innerHTML = ''; wordEls.length = 0;
  state.entries.forEach((e, i) => {
    const font = LABEL_FONT[e.label] || FALLBACK[i % FALLBACK.length];
    const p = mk('path', { fill: WORD_COLOURS[i % WORD_COLOURS.length], opacity: e.enabled === false ? 0.55 : 1 }, $('words'));
    const hit = mk('polygon', { class: 'hit', points: '' }, $('words'));
    const g = mk('path', { fill: C.gold }, $('gold'));
    hit.addEventListener('pointerdown', (ev) => { ev.preventDefault(); tap(i); });
    wordEls.push({ p, hit, g, font, label: e.label });
  });
}
function draw() {
  const n = state.entries.length; if (!n) return;
  const sl = slots(); let chosenQ = null;
  state.entries.forEach((e, i) => {
    const w = wordEls[i]; const gl = glyphs(w.label, w.font); const q = quadFor(i, sl[i], gl.aspect);
    const d = pathOf(gl.contours, q);
    w.p.setAttribute('d', d); w.hit.setAttribute('points', [q.TL, q.TR, q.BR, q.BL].map((p) => p.join(',')).join(' '));
    w.p.setAttribute('fill', WORD_COLOURS[i % WORD_COLOURS.length]);
    if (i === state.selected) { chosenQ = q; w.g.setAttribute('d', d); w.g.style.display = ''; } else { w.g.style.display = 'none'; w.g.setAttribute('d', ''); }
  });
  // the band: a skewed white box that runs off the right edge; the chosen word is gold where it falls and stays purple where it does not
  if (chosenQ) {
    const q = chosenQ, wq = (q.TR[0] - q.TL[0]), ex = 0.14 * (q.BL[1] - q.TL[1]);
    const P = [[q.TL[0] + 0.17 * wq, q.TL[1] - ex * 1.1], [AW + 160, q.TR[1] - ex * 1.0 - 30], [AW + 160, q.BR[1] + ex * 0.9 - 30], [q.BL[0] - 0.05 * wq, q.BL[1] + ex * 0.9]];
    const pts = P.map((p) => p.map((v) => v.toFixed(1)).join(',')).join(' ');
    $('band').setAttribute('points', pts); $('bandclip-poly').setAttribute('points', pts);
  }
}
function tap(i) {
  if (i === state.selected) { const e = state.entries[i]; if (e && e.enabled !== false) bridge('activate', i); }
  else { select(i); bridge('select', i); }
}
function select(i) { if (i === state.selected) return; state.selected = i; nudgeVel = 6; }
function bridge(kind, i) { try { window.Android && window.Android[kind] && window.Android[kind](i); } catch (e) {} window.dispatchEvent(new CustomEvent('skin-' + kind, { detail: i })); }
let dragY = null;
addEventListener('pointerdown', (e) => { dragY = e.clientY; });
addEventListener('pointermove', (e) => { if (dragY == null) return; const dy = e.clientY - dragY; if (Math.abs(dy) > 70) { dragY = e.clientY; const j = Math.min(state.entries.length - 1, Math.max(0, state.selected + (dy < 0 ? 1 : -1))); if (j !== state.selected) { select(j); bridge('select', j); } } });
addEventListener('pointerup', () => { dragY = null; });

// ---------- the title block (the picture's "MOON PHASE FULL") and the install gauge ----------
function drawTitle() {
  const g = $('title'); g.innerHTML = '';
  const lines = (state.game.lines || state.game.title.split(' ')).map((t) => t.toUpperCase()); let y = 18;
  lines.forEach((t) => {
    const gl = glyphs(t, 'Bungee'); const h = 112, w = Math.min(430, h * gl.aspect);
    mk('path', { d: pathOf(gl.contours, { TL: [1040, y], TR: [1040 + w, y], BL: [1040, y + h], BR: [1040 + w, y + h], bow: 0 }), fill: C.white }, g); y += h + 14;
  });
  if (state.game.version) {
    const t = state.game.version.toUpperCase(); const gl = glyphs(t, 'Bungee'); const h = 52, w = Math.min(380, h * gl.aspect);
    mk('path', { d: pathOf(gl.contours, { TL: [1040, y], TR: [1040 + w, y], BL: [1040, y + h], BR: [1040 + w, y + h], bow: 0 }), fill: C.violet }, g);
  }
  const gg = $('gauge'); gg.innerHTML = '';
  if (state.progress) {
    const x = 1040, y = 400, w = 420, h = 60, f = Math.max(0, Math.min(1, state.progress.fraction));
    mk('polygon', { points: `${x + 30},${y} ${x + w},${y} ${x + w - 30},${y + h} ${x},${y + h}`, fill: 'rgba(42,16,65,.5)' }, gg);
    const fx = x + w * f; mk('polygon', { points: `${x + 30},${y} ${Math.max(x + 30, fx)},${y} ${Math.max(x, fx - 30)},${y + h} ${x},${y + h}`, fill: C.gold }, gg);
  }
}
window.Skin = {
  setState(s) { Object.assign(state, s); buildWords(); pos = state.selected; drawTitle(); draw(); },
  select(i) { select(i); },
};

// ---------- the disc, rim, moon and the little squares (SVG, in the art's own units) ----------
function buildBackground() {
  const g = $('art'); g.innerHTML = '';
  const d = ART.disc;
  mk('rect', { x: -6000, y: -6000, width: 14000, height: 14000, fill: C.outside }, g);
  mk('circle', { cx: d.x, cy: d.y, r: d.r, fill: C.disc }, g);
  mk('circle', { cx: d.x, cy: d.y, r: d.r + d.rim / 2, fill: 'none', stroke: C.rim, 'stroke-width': d.rim }, g);
}
const shardList = ART.shards.filter((s) => Math.max(s.w, s.h) * AH < 190 && Math.max(s.w, s.h) / Math.max(1e-6, Math.min(s.w, s.h)) < 2.4).map((s) => ({ cx: s.x * AW, cy: s.y * AH, w: s.w * AH, h: s.h * AH, a: s.a }));

buildBackground();
const girl = window.__girl = new Girl($('girl'), await (await fetch('girl/layers.json')).json(), await (await fetch('girl/rig.json')).json());
await girl.load('girl/');
girl.setScene({ moon: ART.moon, colors: { moon: C.moon, ring: C.ring, shard: C.shard }, craters: [[440, 150, 44], [800, 165, 22], [590, 40, 18], [870, 430, 14], [330, 110, 16]], shards: shardList });
layout(); addEventListener('resize', layout);
const q = new URLSearchParams(location.search);
if (q.get('mock')) {
  const sel = +(q.get('sel') || 1);
  const sets = {
    p1: [['PLAY', '', false], ['INSTALL', ''], ['OPTIONS', '']],
    p4: [['PLAY', 'Start the game'], ['UNINSTALL', 'Take the game off this phone; saves are kept'], ['MODS', 'Add or remove mods'], ['OPTIONS', 'Graphics, controls and launch options']],
    p3: [['PLAY', 'Start the game'], ['UNINSTALL', 'Take the game off this phone; saves are kept'], ['VERSION', 'Change version: Portable'], ['MODS', 'Add or remove mods'], ['OPTIONS', 'Graphics, controls and launch options']],
  };
  const set = sets[q.get('set') || 'p3'];
  window.Skin.setState({ game: { title: q.get('title') || 'Persona 3', lines: q.get('lines') ? q.get('lines').split('|') : undefined, version: q.get('ver') || 'Portable' }, selected: sel, entries: set.map(([label, , ok]) => ({ label, enabled: ok !== false })), progress: q.get('p') ? { text: 'Installing', fraction: 0.62 } : null });
}
let last = performance.now() / 1000; const t0 = last;
let lastPos = NaN, lastSel = -1, lastCount = -1;
function frame() {
  const ms0 = performance.now();
  const now = performance.now() / 1000, dt = Math.min(0.05, now - last); last = now;
  const t = q.get('t') ? +q.get('t') : now - t0;
  vel += ((state.selected - pos) * 140 - vel * 15) * dt; pos += vel * dt;
  nudgeVel += (-nudge * 70 - nudgeVel * 7) * dt; nudge += nudgeVel * dt; girl.nudgeAngle = nudge;
  // the lettering is redrawn only while it moves (the words settle in about a third of a second), not sixty times a second for nothing
  if (Math.abs(pos - lastPos) > 1e-4 || state.selected !== lastSel || state.entries.length !== lastCount) { draw(); lastPos = pos; lastSel = state.selected; lastCount = state.entries.length; }
  girl.draw(t);
  if (q.get('perf')) (window.__ms = window.__ms || []).push(performance.now() - ms0);
  requestAnimationFrame(frame);
}
frame();
setTimeout(() => { window.done = true; }, 700);
