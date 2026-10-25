// Renders every plate to PNG for visual review: node tools/drawing/preview.mjs <outDir>
import { PLATES } from './bikes.mjs';
import { chromium } from '/home/kazemu/repos/Kaze/Agenda/node_modules/.pnpm/playwright-core@1.62.1/node_modules/playwright-core/index.mjs';
const out = process.argv[2] ?? '/tmp';
const css = `body{margin:0;background:#fff}svg{width:1400px;height:auto;display:block}
.l{fill:none;stroke:var(--lc,#1d2a33);stroke-width:1.3;stroke-linejoin:round;stroke-linecap:round}.t{stroke-width:.75}.h{stroke-dasharray:5 4}
.sf{fill:#fff;stroke:var(--lc,#1d2a33);stroke-width:1.3;stroke-linejoin:round}.po{fill:none;stroke:var(--lc,#1d2a33);stroke-linecap:round;stroke-linejoin:round}
.pi{fill:none;stroke:#fff;stroke-linecap:round}.rollers{fill:none;stroke:var(--lc,#1d2a33);stroke-width:3.4;stroke-linecap:round;stroke-dasharray:0 7.1}.spring{stroke-width:2.6}
g[data-z]{--lc:#2f8f5b}g[data-z] .sf{fill:#e9f4ee}g[data-z] .pi{stroke:#e9f4ee}`;
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1400, height: 1000 } });
for (const [name, fn] of Object.entries(PLATES)) {
  const { svg, viewBox } = fn();
  await p.setContent(`<style>${css}</style><svg viewBox="${viewBox.join(' ')}">${svg}<line x1="60" y1="640" x2="1100" y2="640" stroke="#1d2a33"/></svg>`);
  await (await p.$('svg')).screenshot({ path: `${out}/plate-${name}.png` });
}
await b.close();
console.log('ok');
