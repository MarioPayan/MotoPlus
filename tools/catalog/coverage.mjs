// Run after build.mjs; pass a catalog path to compare an older asset with the same search rule.
import { readFileSync } from 'node:fs';
import { search } from './matching.mjs';
import { CACHE } from './sources.mjs';
import { brandOf } from './build.mjs';
import { randomInt } from 'node:crypto';
import { qualityCounts } from './quality.mjs';

const path = process.argv[2] ?? new URL('../../app/src/main/assets/catalog.json', import.meta.url);
const catalog = JSON.parse(readFileSync(path, 'utf8'));
const models = catalog.brands.flatMap(b => b.models);
console.log(`brands=${catalog.brands.length} models=${models.length} models-with-years=${models.filter(m => m.y.length).length} aliases=${models.reduce((n, m) => n + (m.a?.length ?? 0), 0)}`);
const top = JSON.parse(readFileSync(new URL('x9pp-pcn5-aggregates.json', CACHE), 'utf8'))
  .sort((a, b) => Number(b.n) - Number(a.n) || `${a.marca} ${a.linea}`.localeCompare(`${b.marca} ${b.linea}`, 'en')).slice(0, 100);
let hits = 0;
for (const row of top) {
  const q = `${brandOf(row.marca, row.linea)} ${row.linea}`;
  if (search(catalog, q, 1).length) hits++;
  else console.log(`MISS Risaralda: ${q} (n=${row.n})`);
}
console.log(`Risaralda top 100: hits=${hits} misses=${top.length - hits}`);

const checks = [
  'AKT NKD 125', 'AKT TT 125', 'AKT Flex 125', 'AKT TTR 200', 'Bajaj Boxer CT100', 'Pulsar NS 200',
  'Pulsar N125', 'Pulsar NS 400Z', 'Discover 125', 'TVS Apache RTR 160 4V', 'Apache RTR 310', 'TVS Raider 125',
  'Yamaha FZ 2.0', 'XTZ 150', 'NMAX', 'BWS 125', 'Honda CB 125F', 'XR 150L', 'Suzuki Gixxer 150', 'GN 125',
  'DR 150', 'Hero Hunk 160 4V', 'Xpulse 200 4V', 'Victory Black', 'Victory Nitro', 'Victory MRX 150',
  'Kymco Agility', 'KTM Duke 200', 'KTM Duke 390', 'KTM 390 Adventure', 'Royal Enfield Classic 350',
  'Benelli TNT 135', 'Benelli TNT 150i',
];
let found = 0;
for (const q of checks) {
  const hit = search(catalog, q, 1)[0];
  if (hit) found++;
  console.log(`${hit ? 'HIT' : 'MISS'} check: ${q}${hit ? ` -> ${hit.brand} ${hit.model.n}` : ''}`);
}
console.log(`Requested checks: hits=${found} misses=${checks.length - found}`);

console.log('Quality:');
for (const [pattern, count] of Object.entries(qualityCounts(catalog))) console.log(`  ${pattern}=${count}`);
console.log(`  totalModels=${models.length}`);
const sample = catalog.brands.flatMap(b => b.models.map(m => `${b.n} ${m.n}`));
for (let i = sample.length - 1; i > 0; i--) {
  const j = randomInt(i + 1);
  [sample[i], sample[j]] = [sample[j], sample[i]];
}
console.log('Random display names (40):');
sample.slice(0, 40).forEach(name => console.log(`  ${name}`));
