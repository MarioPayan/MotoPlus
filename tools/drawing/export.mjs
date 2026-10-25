// Exports the bike plate drawn in design/prototipo.html to app/src/main/assets/bike_naked.json.
// Each primitive keeps its zone id (or null), its style kind and an SVG path string,
// so the app can draw it on a Compose Canvas and recolor zones by state.
// Run: node tools/drawing/export.mjs
import { readFileSync, writeFileSync } from 'node:fs';

const html = readFileSync(new URL('../../design/prototipo.html', import.meta.url), 'utf8');
const start = html.indexOf('/* ---------- Lámina: geometría ---------- */');
const end = html.indexOf('function paint(');
const zonesSrc = html.slice(html.indexOf('const ZONES = {'), html.indexOf('const ZKEYS'));
const code = zonesSrc + 'const ZKEYS=Object.keys(ZONES);const fmt=n=>String(n);' + html.slice(start, end) + 'return {svg:bikeSVG({plate:false}), ZONES};';
const { svg, ZONES } = new Function(code)();

const num = (tag, a) => { const m = tag.match(new RegExp(`\\s${a}="([^"]*)"`)); return m ? m[1] : null; };
const f = n => +n.toFixed(2);
const circlePath = (cx, cy, r) => `M${f(cx - r)} ${f(cy)} A${f(r)} ${f(r)} 0 1 0 ${f(cx + r)} ${f(cy)} A${f(r)} ${f(r)} 0 1 0 ${f(cx - r)} ${f(cy)} Z`;
function ellipsePath(cx, cy, rx, ry, rot) {
  const a = rot * Math.PI / 180, pts = [];
  for (let i = 0; i < 48; i++) {
    const t = i / 48 * 2 * Math.PI, x = rx * Math.cos(t), y = ry * Math.sin(t);
    pts.push(`${f(cx + x * Math.cos(a) - y * Math.sin(a))} ${f(cy + x * Math.sin(a) + y * Math.cos(a))}`);
  }
  return 'M' + pts.join(' L') + ' Z';
}
// translate(dx,dy) only appears on the engine group; shift absolute coordinates in its paths
function translatePath(d, dx, dy) {
  if (!dx && !dy) return d;
  let cmd = '', idx = 0;
  return d.replace(/([MLCAZHVQ])|(-?\d*\.?\d+(?:e-?\d+)?)/gi, (tok, c, n) => {
    if (c) { cmd = c.toUpperCase(); idx = 0; return c; }
    const v = parseFloat(n), i = idx++;
    let isX = false, isY = false;
    if (cmd === 'H') isX = true; else if (cmd === 'V') isY = true;
    else if (cmd === 'A') { const k = i % 7; isX = k === 5; isY = k === 6; }
    else { isX = i % 2 === 0; isY = i % 2 === 1; }
    if (/[a-z]/.test(c ?? '')) return n;
    return String(f(v + (isX ? dx : isY ? dy : 0)));
  });
}

const items = [];
const zoneStack = [], trStack = [];
for (const tag of svg.match(/<[^>]+>/g)) {
  if (tag.startsWith('</g')) { zoneStack.pop(); trStack.pop(); continue; }
  if (tag.startsWith('</')) continue;
  if (tag.startsWith('<g')) {
    zoneStack.push(num(tag, 'data-z'));
    const t = (num(tag, 'transform') ?? '').match(/translate\(([-\d.]+),([-\d.]+)\)/);
    trStack.push(t ? [+t[1], +t[2]] : [0, 0]);
    continue;
  }
  const kind = tag.match(/^<(\w+)/)[1];
  if (!['path', 'circle', 'ellipse'].includes(kind)) continue;
  const cls = (num(tag, 'class') ?? '').split(/\s+/);
  if (cls.includes('dim') || cls.includes('scan')) continue;
  const fill = num(tag, 'fill');
  if (fill === 'transparent') continue; // hit areas: the app computes its own
  let d;
  if (kind === 'path') d = num(tag, 'd');
  else if (kind === 'circle') d = circlePath(+num(tag, 'cx'), +num(tag, 'cy'), +num(tag, 'r'));
  else {
    const rot = (num(tag, 'transform') ?? '').match(/rotate\(([-\d.]+)/);
    d = ellipsePath(+num(tag, 'cx'), +num(tag, 'cy'), +num(tag, 'rx'), +num(tag, 'ry'), rot ? +rot[1] : 0);
  }
  if (!d) continue;
  const [dx, dy] = trStack.reduce((s, t) => [s[0] + t[0], s[1] + t[1]], [0, 0]);
  d = translatePath(d, dx, dy);
  const w = num(tag, 'stroke-width');
  const style = num(tag, 'style') ?? '';
  const sw = style.match(/stroke-width:([\d.]+)/);
  const dash = style.match(/stroke-dasharray:([\d. ]+)/);
  items.push({
    z: zoneStack.filter(Boolean).at(-1) ?? null,
    k: cls.filter(c => c && c !== 'zone').join(' '),
    d,
    ...(w || sw ? { w: +(w ?? sw[1]) } : {}),
    ...(dash ? { dash: dash[1].trim().split(/\s+/).map(Number) } : {}),
    ...(num(tag, 'fill-rule') === 'evenodd' ? { eo: true } : {}),
  });
}
const vb = svg.match(/viewBox="([^"]+)"/)[1].split(' ').map(Number);
writeFileSync(new URL('../../app/src/main/assets/bike_naked.json', import.meta.url), JSON.stringify({ viewBox: vb, zones: Object.keys(ZONES), items }));
console.log(`items=${items.length} zones=${new Set(items.map(i => i.z)).size - 1} viewBox=${vb}`);
