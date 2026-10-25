// Builds app/src/main/assets/catalog.json from open sources:
//   Wikidata (CC0)          — global motorcycle models
//   NHTSA vPIC (public dom.) — US models by model year
//   seed-latam.json         — hand-curated models popular in Spain and Latin America
// Run: node tools/catalog/build.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { category } from './category.mjs';

const UA = 'MotoPlusCatalog/0.1 (personal project)';
const OUT = new URL('../../app/src/main/assets/catalog.json', import.meta.url);
const YEARS = [];
for (let y = 2005; y <= 2027; y++) YEARS.push(y);

const BRAND_ALIASES = {
  'Yamaha Motor Company': 'Yamaha', 'Kawasaki Motors': 'Kawasaki', 'Kawasaki Heavy Industries': 'Kawasaki',
  'Suzuki Motor Corporation': 'Suzuki', 'BMW Motorrad': 'BMW', 'Triumph Motorcycles Ltd': 'Triumph',
  'Kwang Yang Motor': 'Kymco', 'Birmingham Small Arms Company': 'BSA', 'Norton Motorcycle Company': 'Norton',
  'Peugeot Motocycles': 'Peugeot', 'Honda Motor Company': 'Honda', 'Harley-Davidson Motor Company': 'Harley-Davidson',
  'Piaggio & C. SpA': 'Piaggio', 'Bajaj Auto': 'Bajaj', 'TVS Motor Company': 'TVS', 'Hero MotoCorp': 'Hero',
  'Royal Enfield (India)': 'Royal Enfield', 'KTM AG': 'KTM', 'Ducati Motor Holding': 'Ducati',
  'Beta Motor': 'Beta', 'Buell Motorcycle Company': 'Buell', 'Can-Am motorcycles': 'Can-Am', 'Husqvarna Motorcycles': 'Husqvarna',
  'Indian Motorcycle': 'Indian', 'BSA motorcycles': 'BSA', 'IMZ-Ural': 'Ural', 'Honda Racing Corporation': 'Honda',
};
const NOT_BRANDS = new Set(['cafe racer']);
const VPIC_MAKES = ['Honda', 'Yamaha', 'Kawasaki', 'Suzuki', 'BMW', 'Ducati', 'KTM', 'Triumph', 'Harley-Davidson',
  'Aprilia', 'Moto Guzzi', 'MV Agusta', 'Royal Enfield', 'Husqvarna', 'Benelli', 'CFMoto', 'Kymco', 'SYM', 'Vespa',
  'Piaggio', 'Indian', 'Zero', 'GasGas', 'Beta', 'Sherco', 'Kove', 'Janus', 'Ural', 'Buell', 'Can-Am'];
// vPIC mixes ATVs, UTVs and side-by-sides into "motorcycle"
const OFFROAD_4W = /grizzly|kodiak|viking|wolverine|raptor|yfz|yxz|rhino|big bear|brute force|mule|teryx|kfx|kingquad|quadsport|pioneer|talon|fourtrax|rancher|foreman|rubicon|recon|trx|outlander|renegade|maverick|defender|commander|cforce|uforce|zforce|mxu|uxv|sportsman|ranger|rzr|general|scrambler 850|scrambler xp|ryker|spyder/i;

const norm = s => s.normalize('NFKC').replace(/\s+/g, ' ').trim();
const brandOf = b => BRAND_ALIASES[b] ?? b;
const key = s => norm(s).toLowerCase().replace(/[\s-]/g, '');

async function getJson(url, opts = {}, tries = 3) {
  for (let i = 0; i < tries; i++) {
    try {
      const r = await fetch(url, { ...opts, headers: { 'User-Agent': UA, ...(opts.headers ?? {}) } });
      if (r.ok) return await r.json();
    } catch {}
    await new Promise(res => setTimeout(res, 1000 * (i + 1)));
  }
  throw new Error('fetch failed: ' + url);
}

async function wikidata() {
  const q = `SELECT ?m ?es ?en ?brandLabel WHERE {
    ?m wdt:P31/wdt:P279* wd:Q23866334 . ?m wdt:P176 ?brand .
    OPTIONAL{?m rdfs:label ?es FILTER(lang(?es)="es")} OPTIONAL{?m rdfs:label ?en FILTER(lang(?en)="en")}
    ?brand rdfs:label ?brandLabel FILTER(lang(?brandLabel)="en") }`;
  const d = await getJson('https://query.wikidata.org/sparql?query=' + encodeURIComponent(q), { headers: { Accept: 'application/sparql-results+json' } });
  const out = [];
  for (const b of d.results.bindings) {
    const brand = brandOf(norm(b.brandLabel.value));
    if (NOT_BRANDS.has(brand)) continue;
    if (!b.en && !b.es) continue;
    let label = norm((b.en ?? b.es).value);
    const short = brand.split(' ')[0];
    if (label.toLowerCase().startsWith(short.toLowerCase() + ' ')) label = label.slice(short.length + 1);
    if (!label || /^Q\d+$/.test(label)) continue;
    out.push({ brand, model: label, years: [], src: 'wikidata' });
  }
  return out;
}

async function vpic() {
  const jobs = [];
  for (const make of VPIC_MAKES) for (const y of YEARS) jobs.push({ make, y });
  const out = [];
  let i = 0;
  async function worker() {
    while (i < jobs.length) {
      const { make, y } = jobs[i++];
      const d = await getJson(`https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMakeYear/make/${encodeURIComponent(make)}/modelyear/${y}/vehicletype/motorcycle?format=json`).catch(() => null);
      for (const r of d?.Results ?? []) {
        const model = norm(r.Model_Name);
        if (!model || OFFROAD_4W.test(model)) continue;
        out.push({ brand: make, model, years: [y], src: 'nhtsa' });
      }
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker));
  return out;
}

const seed = JSON.parse(readFileSync(new URL('./seed-latam.json', import.meta.url), 'utf8'))
  .flatMap(b => b.models.map(m => ({ brand: b.brand, model: m, years: [], src: 'curado' })));

const [wd, us] = await Promise.all([wikidata(), vpic()]);
const brands = new Map();
for (const e of [...us, ...seed, ...wd]) {
  const b = brands.get(key(e.brand)) ?? { n: e.brand, models: new Map() };
  brands.set(key(e.brand), b);
  const m = b.models.get(key(e.model)) ?? { n: e.model, y: new Set(), src: new Set() };
  b.models.set(key(e.model), m);
  e.years.forEach(y => m.y.add(y));
  m.src.add(e.src);
}
const catalog = {
  generated: new Date().toISOString().slice(0, 10),
  sources: { wikidata: 'Wikidata, CC0', nhtsa: 'NHTSA vPIC, dominio público', curado: 'Lista curada a mano (revisar)' },
  brands: [...brands.values()].map(b => ({
    n: b.n,
    models: [...b.models.values()].map(m => ({ n: m.n, y: [...m.y].sort(), c: category(m.n), s: [...m.src].sort() }))
      .sort((a, z) => a.n.localeCompare(z.n, 'es')),
  })).filter(b => b.models.length).sort((a, z) => a.n.localeCompare(z.n, 'es')),
};
writeFileSync(OUT, JSON.stringify(catalog));
const total = catalog.brands.reduce((s, b) => s + b.models.length, 0);
console.log(`brands=${catalog.brands.length} models=${total} wikidata=${wd.length} nhtsa=${us.length} seed=${seed.length}`);
