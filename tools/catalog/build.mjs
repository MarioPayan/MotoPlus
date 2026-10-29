// Build the catalog from open sources. Run with --refresh to replace cached responses.
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { category } from './category.mjs';
import { norm, fold, compact, modelKey } from './matching.mjs';
import { getJson, registries, annex, attribution, ANNEX_URL, MAX_YEAR, yearRange,
  cleanLine, cleanDisplay, usable, title, displayName } from './sources.mjs';

const OUT = new URL('../../app/src/main/assets/catalog.json', import.meta.url);
const YEARS = Array.from({ length: MAX_YEAR - 2005 + 1 }, (_, i) => 2005 + i);

const BRAND_ALIASES = {
  'Yamaha Motor Company': 'Yamaha', 'Kawasaki Motors': 'Kawasaki', 'Kawasaki Heavy Industries': 'Kawasaki',
  'Suzuki Motor Corporation': 'Suzuki', 'BMW Motorrad': 'BMW', 'Triumph Motorcycles Ltd': 'Triumph',
  'Kwang Yang Motor': 'Kymco', 'Birmingham Small Arms Company': 'BSA', 'Norton Motorcycle Company': 'Norton',
  'Peugeot Motocycles': 'Peugeot', 'Honda Motor Company': 'Honda', 'Harley-Davidson Motor Company': 'Harley-Davidson',
  'Piaggio & C. SpA': 'Piaggio', 'Bajaj Auto': 'Bajaj', 'TVS Motor Company': 'TVS', 'Hero MotoCorp': 'Hero',
  'Royal Enfield (India)': 'Royal Enfield', 'KTM AG': 'KTM', 'Ducati Motor Holding': 'Ducati',
  'Beta Motor': 'Beta', 'Buell Motorcycle Company': 'Buell', 'Can-Am motorcycles': 'Can-Am', 'Husqvarna Motorcycles': 'Husqvarna',
  'Indian Motorcycle': 'Indian', 'BSA motorcycles': 'BSA', 'IMZ-Ural': 'Ural', 'Honda Racing Corporation': 'Honda',
  'Keeway Motors': 'Keeway', 'Lifan Group': 'Lifan', 'MZ Motorrad- und Zweiradwerk': 'MZ',
  'Sachs Bikes': 'Sachs', 'Suzuki Motorcycle India Limited': 'Suzuki',
  'Triumph Motorcycles': 'Triumph', 'Zero Motorcycles': 'Zero',
  'NSU Motorenwerke': 'NSU', 'N.S.U': 'NSU', 'Lohia Machinery Limited': 'LML',
  'Lotus - LML': 'LML', 'Steyr-Daimler-Puch': 'Puch', 'Morini': 'Moto Morini',
  'Izhmash': 'Izh', 'Kalashnikov Concern': 'Izh',
};
// Checked against all three cached motorcycle registry aggregates. None of
// these has a motorcycle line there. MinTransporte alone also contains generic
// class labels and misattributed makes (e.g. Scooter / XJ750, ATV / WZGC2503).
export const EXCLUDED_BRANDS = new Set([
  'Opel', 'Fiat', 'Hummer', 'Aston Martin Lagonda', 'Subaru Corporation', 'Auto Union',
  'Gottlieb Daimler', 'Wilhelm Maybach', 'Teiyū Ichiryūsai', 'ATV', 'Scooter',
  'cafe racer', 'Motorcycle', 'Motorcycles', 'Motocicleta', 'Moto', 'Motos',
  'Turismo', 'Cuatrimoto', 'Quad', 'UTV',
].map(compact));
export function allowedBrand(brand, model) {
  if (EXCLUDED_BRANDS.has(compact(brand))) return false;
  // SUPERMOTO is also an actual make here: HS200GY has seven registrations
  // across Boyacá and Fusagasugá, and an official Annex 5 motorcycle row.
  return compact(brand) !== 'supermoto' || compact(cleanDisplay(model)) === 'hs200gy';
}
export function allowedDisplay(brand, model, sources) {
  return /^[\p{L}\p{N}]/u.test(model)
    || (brand === 'BMW' && sources.includes('wikidata') && /^\/[56] motorcycles$/.test(model));
}
const VPIC_MAKES = ['Honda', 'Yamaha', 'Kawasaki', 'Suzuki', 'BMW', 'Ducati', 'KTM', 'Triumph', 'Harley-Davidson',
  'Aprilia', 'Moto Guzzi', 'MV Agusta', 'Royal Enfield', 'Husqvarna', 'Benelli', 'CFMoto', 'Kymco', 'SYM', 'Vespa',
  'Piaggio', 'Indian', 'Zero', 'GasGas', 'Beta', 'Sherco', 'Kove', 'Janus', 'Ural', 'Buell', 'Can-Am'];
// vPIC mixes ATVs, UTVs and side-by-sides into "motorcycle"
const OFFROAD_4W = /grizzly|kodiak|viking|wolverine|raptor|yfz|yxz|rhino|big bear|brute force|mule|teryx|kfx|kingquad|quadsport|pioneer|talon|fourtrax|rancher|foreman|rubicon|recon|trx|outlander|renegade|maverick|defender|commander|cforce|uforce|zforce|mxu|uxv|sportsman|ranger|rzr|general|scrambler 850|scrambler xp|ryker|spyder/i;


const seed = JSON.parse(readFileSync(new URL('./seed-latam.json', import.meta.url), 'utf8'))
  .flatMap(b => b.models.map(m => ({ brand: b.brand, model: m, years: [], src: 'curado' })));
const canonicalBrands = new Map([...seed.map(e => e.brand), ...VPIC_MAKES, ...Object.values(BRAND_ALIASES),
  'AKT', 'TVS', 'BSA', 'SYM', 'UM', 'AMC', 'CMC', 'ACB', 'AG', 'LMX', 'BMW', 'Victory', 'Voge', 'Ayco']
  .map(b => [compact(b), b]));
const brandAliases = new Map(Object.entries({ ...BRAND_ALIASES,
  'AUTECO VICTORY': 'Victory', 'AUTECO BAJAJ': 'Bajaj', 'AUTECO KYMCO': 'Kymco', 'AUTECO KTM': 'KTM',
  'AKT-VOGE': 'Voge', 'AKT-SYM': 'SYM', 'HERO HONDA': 'Hero', 'UNITED MOTORS': 'UM',
  'PULSAR': 'Bajaj', 'DISCOVER': 'Bajaj', 'HARLEY DAVIDSON': 'Harley-Davidson', 'AP PIAGGIO': 'Piaggio', 'BRP CAN AM': 'Can-Am', 'BRP/CAN AM': 'Can-Am',
}).map(([a, b]) => [compact(a), b]));
export function brandOf(value, line = '') {
  const b = compact(value);
  if (b === 'auteco') {
    if (/bajaj|pulsar|boxer|discover|platino|ct.?100|dominar|avenger|caliber|wind|legend|chetak/i.test(line)) return 'Bajaj';
    if (/kymco|agility|twist|fly|uni.k|activ/i.test(line)) return 'Kymco';
    if (/victory|mrx|furax|advance|combat|bomber|nitro|switch/i.test(line)) return 'Victory';
    if (/ktm|duke/i.test(line)) return 'KTM';
  }
  if (b === 'akt' && /^VOGE\b/i.test(line)) return 'Voge';
  return brandAliases.get(b) ?? canonicalBrands.get(b) ?? title(norm(value));
}

async function wikidata() {
  const q = `SELECT ?m ?es ?en ?brandLabel WHERE {
    ?m wdt:P31/wdt:P279* wd:Q23866334 . ?m wdt:P176 ?brand .
    OPTIONAL{?m rdfs:label ?es FILTER(lang(?es)="es")} OPTIONAL{?m rdfs:label ?en FILTER(lang(?en)="en")}
    ?brand rdfs:label ?brandLabel FILTER(lang(?brandLabel)="en") }`;
  const d = await getJson('wikidata.json', 'https://query.wikidata.org/sparql?query=' + encodeURIComponent(q),
    { Accept: 'application/sparql-results+json' });
  const out = [];
  for (const b of d.results.bindings) {
    const brand = brandOf(norm(b.brandLabel.value));
    if (!allowedBrand(brand, '') || (!b.en && !b.es)) continue;
    const labels = [b.en?.value, b.es?.value].filter(Boolean).map(value => {
      let label = norm(value);
      const prefix = [norm(b.brandLabel.value), brand, brand.split(' ')[0]]
        .find(p => fold(label).startsWith(fold(p) + ' '));
      if (prefix) label = label.slice(prefix.length + 1);
      return label;
    }).filter(l => l && !/^Q\d+$/.test(l));
    if (labels.length) out.push({ brand, model: labels[0], aliases: labels.slice(1), years: [], src: 'wikidata' });
  }
  return out;
}

async function vpic() {
  const jobs = VPIC_MAKES.flatMap(make => YEARS.map(y => ({ make, y })));
  const out = [];
  let done = 0;
  for (const { make, y } of jobs) {
    const d = await getJson(`nhtsa-${compact(make)}-${y}.json`,
      `https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMakeYear/make/${encodeURIComponent(make)}/modelyear/${y}/vehicletype/motorcycle?format=json`);
    if (!Array.isArray(d.Results)) throw new Error(`vPIC: invalid response for ${make} ${y}`);
    for (const r of d.Results) {
      const model = norm(r.Model_Name);
      if (!model || OFFROAD_4W.test(model)) continue;
      out.push({ brand: make, model, years: [y], src: 'nhtsa' });
    }
    if (++done % 100 === 0) console.log(`vPIC: ${done}/${jobs.length} responses`);
  }
  return out;
}

export function registryEntries(rows) {
  const combos = new Map();
  let dropped = 0;
  for (const row of rows) {
    const raw = norm(row.linea), line = cleanLine(raw);
    if (!usable(line) || !usable(row.marca)) { dropped++; continue; }
    const brand = brandOf(row.marca, line);
    const model = displayName(brand, line, row.marca);
    if (!usable(model) || !allowedBrand(brand, model) || !allowedDisplay(brand, model, [row.src])) { dropped++; continue; }
    const key = compact(brand) + ':' + modelKey(model);
    const combo = combos.get(key) ?? { n: 0, entries: [] };
    combo.n += Number(row.n);
    combo.entries.push({ brand, model, years: yearRange(row.y0, row.y1), aliases: [raw, line], src: row.src });
    combos.set(key, combo);
  }
  const kept = [...combos.values()].filter(c => c.n >= 5);
  console.log(`Registries: ${kept.length} model combos with >=5 registrations; dropped placeholders=${dropped}, low-count combos=${combos.size - kept.length}`);
  return kept.flatMap(c => c.entries);
}

export function annexEntries(rows, known) {
  const knownBrands = new Map();
  for (const e of known) {
    const b = compact(brandOf(e.brand, e.model));
    const names = knownBrands.get(b) ?? new Map();
    const model = cleanDisplay(e.model);
    if (!names.has(modelKey(model))) names.set(modelKey(model), { ...e, model });
    knownBrands.set(b, names);
  }
  const variants = new Map();
  const prepared = rows.filter(e => allowedBrand(brandOf(e.brand, e.model), e.model)).map(e => {
    const brand = brandOf(e.brand, e.model);
    const model = displayName(brand, e.model, e.brand);
    const key = compact(brand) + ':' + modelKey(model);
    const cc = variants.get(key) ?? new Set();
    cc.add(e.cc); variants.set(key, cc);
    return { e, brand, model, key };
  });
  return prepared.map(({ e, brand, model, key }) => {
    const names = [...(knownBrands.get(compact(brand))?.values() ?? [])];
    const existing = knownBrands.get(compact(brand))?.get(modelKey(model));
    if (existing) model = existing.model;
    else if (!/\d/.test(model) && e.cc > 0) {
      const candidates = names.flatMap(k => {
        const numbers = k.model.match(/\d{2,4}/g) ?? [];
        if (numbers.length !== 1 || compact(k.model.replace(/\d{2,4}/g, '')) !== compact(model)) return [];
        const distance = Math.abs(Number(numbers[0]) - e.cc);
        return distance <= Math.max(10, e.cc * 0.08) ? [{ model: k.model, distance }] : [];
      }).sort((a, b) => a.distance - b.distance);
      if (candidates.length) model = candidates[0].model;
      else {
        const capacities = [...variants.get(key)].filter(cc => cc > 0);
        const distinctEngines = Math.max(...capacities) - Math.min(...capacities) > Math.max(10, e.cc * 0.08);
        if (distinctEngines || !names.some(k => modelKey(k.model) === modelKey(model))) model += ' ' + Math.round(e.cc);
      }
    }
    // Generic lines such as GIXXER cover different capacities; they remain searchable
    // without joining the 150 and 250 into one model through a shared alias.
    return { ...e, brand, model, aliases: [e.model, ...(e.aliases ?? []), String(e.cc)], identityAliases: [] };
  });
}

const priority = { curado: 0, wikidata: 1, nhtsa: 2, risaralda: 4, boyaca: 4, fusagasuga: 4, mintransporte: 5 };
export function merge(entries) {
  const brands = new Map();
  let order = 0;
  const prepared = entries.map(e => {
    const brand = brandOf(e.brand, e.model);
    let model = cleanDisplay(e.model);
    // Remove a repeated make prefix without changing preferred commercial casing.
    const prefix = new RegExp(`^${brand.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s+`, 'i');
    while (prefix.test(model)) model = model.replace(prefix, '');
    return { ...e, brand, model, aliases: [e.model, ...(e.aliases ?? [])] };
  });
  const identifiedBrands = new Set(prepared.filter(e => compact(e.model)
    && compact(e.model) !== compact(e.brand) && allowedBrand(e.brand, e.model)
    && allowedDisplay(e.brand, e.model, [e.src])).map(e => compact(e.brand)));
  for (const e of prepared.sort((a, b) => priority[a.src] - priority[b.src]
      || a.model.localeCompare(b.model, 'en') || a.src.localeCompare(b.src, 'en'))) {
    const brand = brandOf(e.brand, e.model);
    if (compact(e.model) === compact(brand)) {
      if (identifiedBrands.has(compact(brand))) continue;
      e.model = 'Sin modelo';
      e.identityAliases = [];
    }
    if (!compact(e.model) || !allowedBrand(brand, e.model) || !allowedDisplay(brand, e.model, [e.src])) continue;
    const b = brands.get(compact(brand)) ?? { n: brand, index: new Map(), models: new Set() };
    brands.set(compact(brand), b);
    const names = new Set([e.model, ...(e.aliases ?? [])].map(norm).filter(Boolean));
    const keys = [e.model, ...(e.identityAliases ?? e.aliases ?? [])].map(cleanDisplay).map(modelKey).filter(Boolean);
    const matches = new Set(keys.map(k => b.index.get(k)).filter(Boolean));
    const m = [...matches].sort((a, b) => a.order - b.order)[0] ?? { order: order++, n: e.model, y: new Set(), s: new Set(), a: new Set(), keys: new Set() };
    // An alias can bridge two already-seen names; collapse the entire group.
    for (const other of matches) {
      if (other === m) continue;
      other.y.forEach(y => m.y.add(y)); other.s.forEach(s => m.s.add(s));
      other.a.forEach(a => m.a.add(a)); m.a.add(other.n);
      other.keys.forEach(k => { b.index.set(k, m); m.keys.add(k); });
      b.models.delete(other);
    }
    names.forEach(a => m.a.add(a));
    keys.forEach(k => { b.index.set(k, m); m.keys.add(k); });
    e.years.filter(y => Number.isInteger(y) && y >= 1950 && y <= MAX_YEAR).forEach(y => m.y.add(y));
    m.s.add(e.src);
    b.models.add(m);
  }
  return [...brands.values()].map(b => ({ n: b.n, models: [...b.models].map(m => {
    const a = [...m.a].filter(a => a !== m.n).sort((a, b) => a.localeCompare(b, 'en'));
    return { n: m.n, y: [...m.y].sort((a, b) => a - b), c: category(m.n), s: [...m.s].sort(), ...(a.length ? { a } : {}) };
  }).sort((a, b) => a.n.localeCompare(b.n, 'es')) })).sort((a, b) => a.n.localeCompare(b.n, 'es'));
}

async function main() {
  const [wd, us, rows, tax] = await Promise.all([wikidata(), vpic(), registries(), annex()]);
  const co = registryEntries(rows);
  const ministry = annexEntries(tax, [...seed, ...wd, ...us, ...co]);
  const catalog = {
    generated: new Date().toISOString().slice(0, 10),
    sources: { wikidata: 'Wikidata, CC0', nhtsa: 'NHTSA vPIC, public domain', curado: 'Hand-curated Latin American models',
      ...attribution, mintransporte: `Ministerio de Transporte, base gravable 2026, Annex 5 (official act), ${ANNEX_URL}` },
    brands: merge([...seed, ...wd, ...us, ...ministry, ...co]),
  };
  const data = JSON.stringify(catalog);
  writeFileSync(OUT, data);
  console.log(`brands=${catalog.brands.length} models=${catalog.brands.reduce((s, b) => s + b.models.length, 0)} wikidata=${wd.length} nhtsa=${us.length} seed=${seed.length} bytes=${Buffer.byteLength(data)}`);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
