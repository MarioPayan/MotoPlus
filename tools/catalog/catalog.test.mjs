import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { merge, brandOf, registryEntries, annexEntries } from './build.mjs';
import { modelKey, search } from './matching.mjs';
import { yearRange, MAX_YEAR, displayName, cleanDisplay, parseAnnex } from './sources.mjs';
import { qualityIssues } from './quality.mjs';

const entry = (brand, model, src, years = [], aliases = []) => ({ brand, model, src, years, aliases });

test('model keys ignore spacing, hyphens, accents and token order', () => {
  assert.equal(modelKey('390 Duke'), modelKey('Duke-390'));
  assert.equal(modelKey('NMAX155'), modelKey('NMAX 155'));
  assert.equal(modelKey('Métralla-250'), modelKey('250 Metralla'));
  assert.notEqual(modelKey('NS 200'), modelKey('NS 400Z'));
});

test('aliases bridge groups while preserving the best source name and all years', () => {
  const brands = merge([
    entry('KTM', '390 Duke', 'curado', [2025]),
    entry('KTM', 'Registration Code', 'nhtsa', [2023]),
    entry('KTM', 'Registration Code', 'risaralda', [2024], ['Duke 390']),
    entry('Yamaha', '390 Duke', 'wikidata'),
  ]);
  const models = brands.find(b => b.n === 'KTM').models;
  assert.equal(models.length, 1);
  assert.equal(models[0].n, '390 Duke');
  assert.deepEqual(models[0].y, [2023, 2024, 2025]);
  assert.deepEqual(models[0].s, ['curado', 'nhtsa', 'risaralda']);
  assert.ok(models[0].a.includes('Registration Code'));
  assert.equal(brands.find(b => b.n === 'Yamaha').models.length, 1);
});

test('registry counts accumulate across datasets and aliases before the threshold', () => {
  const rows = registryEntries([
    { marca: 'AUTECO BAJAJ', linea: 'PULSAR NS200', n: '3', y0: '2020', y1: '2022', src: 'risaralda' },
    { marca: 'BAJAJ', linea: 'MOTOCICLETA - PULSAR 200 NS', n: '2', y0: '2023', y1: 'NA', src: 'boyaca' },
    { marca: 'TVS', linea: 'RAIDER 125', n: '4', y0: '2025', y1: '2025', src: 'fusagasuga' },
    { marca: 'HONDA', linea: 'SIN LINEA', n: '100', src: 'fusagasuga' },
  ]);
  assert.equal(rows.length, 2);
  const catalog = { brands: merge(rows) };
  assert.equal(catalog.brands[0].n, 'Bajaj');
  assert.equal(catalog.brands[0].models.length, 1);
  assert.equal(search(catalog, '200 bajaj ns').length, 1);
  assert.equal(search(catalog, 'bajaj pulsarns200').length, 1);
});

test('year bounds ignore noise and decode thousands dots', () => {
  assert.deepEqual(yearRange('2.024', '2.026'), [2024, 2025, 2026]);
  assert.deepEqual(yearRange('1936', '2000'), [2000]);
  assert.deepEqual(yearRange('NA', String(MAX_YEAR + 1)), []);
  assert.deepEqual(yearRange('1950', '1951'), [1950, 1951]);
});

test('commercial names and distributor brands retain recognizable identities', () => {
  assert.equal(brandOf('AUTECO', 'PULSAR NS 200'), 'Bajaj');
  assert.equal(brandOf('AUTECO VICTORY'), 'Victory');
  assert.equal(brandOf('AKT-VOGE'), 'Voge');
  assert.equal(displayName('AKT', 'AK125NKD EIII'), 'NKD 125');
  assert.equal(displayName('Yamaha', 'GPD155-A (NMAX155)'), 'NMAX 155');
  assert.equal(displayName('Yamaha', 'FZN150D-6 (FZ-S)'), 'FZ-S');
});

function layout(fields) {
  const row = Array(200).fill(' ');
  for (const [at, s] of fields) [...s].forEach((c, i) => { row[at + i] = c; });
  return row.join('').trimEnd();
}
const header = layout([[28, 'CLASE (2)'], [43, 'MARCA (3)'], [75, 'LINEA (4)'], [92, 'CILINDRAJE (5)'],
  [128, '2001 y'], [141, '2002'], [150, '2003'], [159, '2004']]);
const row = (brand, model) => layout([[26, 'MOTOCICLETA'], [41, brand], [57, model], [100, '125'], [143, '500'], [161, '700']]);

test('Annex parser respects blank year cells and skips wrapped or missing names', () => {
  const text = [header, row('HONDA', 'CB 125F'), '', row('', 'NO BRAND'), '',
    layout([[57, 'WRAPPED MODEL']]), row('HONDA', ''), '', row('HONDA', 'UNBALANCED (')].join('\n');
  const parsed = parseAnnex(text);
  assert.equal(parsed.length, 1);
  assert.equal(parsed[0].brand, 'HONDA');
  assert.equal(parsed[0].model, 'CB 125F');
  assert.equal(parsed[0].cc, 125);
  assert.deepEqual(parsed[0].years, [2002, 2004]);
});

test('JS coverage search matches the app cases and ranking', () => {
  const catalog = { brands: [
    { n: 'AKT', models: [{ n: 'NKD 125', a: ['AK125NKD EIII'] }] },
    { n: 'Bultacó', models: [{ n: 'Métralla-250' }] },
    { n: 'KTM', models: [{ n: 'Super Duke', s: ['a', 'b'] }, { n: 'Duke 200' }, { n: 'Duke' }] },
  ] };
  for (const q of ['125 akt nkd', 'AKT ak125nkd']) assert.equal(search(catalog, q)[0].model.n, 'NKD 125');
  assert.equal(search(catalog, 'bultaco metralla250')[0].model.n, 'Métralla-250');
  assert.deepEqual(search(catalog, 'KTM duke').map(h => h.model.n), ['Duke', 'Duke 200', 'Super Duke']);
  assert.equal(search(catalog, 'ktm').length, 3);
  assert.equal(search(catalog, ' -- ').length, 0);
});


test('Annex capacities confirm commercial names without merging distinct engines', () => {
  const known = [entry('Suzuki', 'Gixxer 150', 'curado'), entry('Suzuki', 'Gixxer 250', 'curado')];
  const rows = annexEntries([
    { ...entry('SUZUKI', 'GIXXER', 'mintransporte', [2024]), cc: 154 },
    { ...entry('SUZUKI', 'GIXXER', 'mintransporte', [2025]), cc: 250 },
  ], known);
  const models = merge([...known, ...rows])[0].models;
  assert.equal(models.length, 2);
  assert.deepEqual(models.find(m => m.n === 'Gixxer 150').y, [2024]);
  assert.deepEqual(models.find(m => m.n === 'Gixxer 250').y, [2025]);
  assert.ok(models.every(m => m.a.includes('GIXXER')));
});

test('administrative new/truncated suffixes disappear before model identity is merged', () => {
  for (const suffix of [' - Nueva', ' - Nuevo', ' - Nuevo 249', ' - Nuev', ' (nuev', '-nueva',
    ' - NU', ' NU', ' - NUE', ' - N', ' -N', ' - NUEVA249', ' - Nuevo15', ' Nueva149', ' -', '-']) {
    const raw = `Hunk 160${suffix}`;
    assert.equal(displayName('Hero', raw), 'Hunk 160', raw);
    const models = merge([entry('Hero', 'Hunk 160', 'curado', [2025]),
      entry('Hero', raw, 'boyaca', [2026])])[0].models;
    assert.equal(models.length, 1, raw);
    assert.equal(models[0].n, 'Hunk 160');
    assert.deepEqual(models[0].y, [2025, 2026]);
    assert.ok(models[0].a.includes(raw));
  }
  const raw = 'TNT 150I - Nuevo';
  const models = merge([entry('Benelli', 'TNT 150i', 'curado'), entry('Benelli', raw, 'boyaca')])[0].models;
  assert.equal(models.length, 1);
  assert.equal(models[0].n, 'TNT 150i');
  assert.ok(models[0].a.includes(raw));
  assert.equal(cleanDisplay('CB 150 Nueva Invicta MT'), 'CB 150 Invicta');
  assert.equal(cleanDisplay('FZ6-N'), 'FZ6-N');
});

test('motorcycle prefixes and registry typos merge into their base model', () => {
  for (const prefix of ['Motocicleta ', 'MOTOCICLETA - ', 'MOTOCICLETA-',
    'MOTOCICLETA -', 'MOTOCILCETA ', 'MOTOTCICLETA - ']) {
    const raw = `${prefix}PULSAR 180 FI`;
    const rows = registryEntries([{ marca: 'BAJAJ', linea: raw, n: '5', src: 'boyaca' }]);
    const models = merge([entry('Bajaj', 'Pulsar 180 FI', 'curado'), ...rows])[0].models;
    assert.equal(models.length, 1, raw);
    assert.equal(models[0].n, 'Pulsar 180 FI');
    assert.ok(models[0].a.includes(raw));
  }
});

test('FZ-S extraction preserves the full name and keeps FZ 2.0 separate', () => {
  const codes = ['FNZ150D-6 (FZ-S)', 'FZN150D-6 FZ(-S)', 'FZN150D-6 (FZ-S', 'FZ - S'];
  const rows = registryEntries(codes.map(linea => ({ marca: 'YAMAHA', linea, n: '5', src: 'risaralda' })));
  assert.ok(rows.every(e => e.model === 'FZ-S'));
  const known = [entry('Yamaha', 'FZ 2.0', 'curado'), ...rows];
  const annex = annexEntries([149, 150].map(cc => ({ ...entry('YAMAHA', 'FZN150D-6 (FZ-S)', 'mintransporte'), cc })), known);
  const models = merge([...known, ...annex])[0].models;
  assert.deepEqual(models.map(m => m.n), ['FZ 2.0', 'FZ-S']);
  assert.ok(codes.every(code => models.find(m => m.n === 'FZ-S').a.includes(code)));
  assert.deepEqual(merge([entry('Yamaha', '-s', 'boyaca')]), []);
  assert.equal(displayName('Yamaha', 'FZ S 149'), 'FZ S 149');
  assert.equal(displayName('Yamaha', 'FZ S Hybrid'), 'FZ S Hybrid');
  assert.equal(merge([entry('BMW', '/5 motorcycles', 'wikidata')])[0].models[0].n, '/5 motorcycles');
  assert.deepEqual(merge([entry('BMW', '/5 motorcycles', 'boyaca')]), []);
  assert.equal(merge([entry('MZ', '125', 'wikidata')])[0].models[0].n, '125');
});

test('parenthesized features never replace model names or bridge unrelated bikes', () => {
  for (const feature of ['ABS', 'FI', 'CBS', 'EIII', 'TK', 'ABS FI']) {
    const raw = `FZ6-N (${feature})`;
    assert.equal(displayName('Yamaha', raw), `FZ6-N${feature === 'EIII' ? '' : ` ${feature}`}`);
  }
  assert.equal(displayName('Bajaj', 'PULSAR 180 -FI'), 'Pulsar 180 FI');
  const models = merge(annexEntries([{ ...entry('Yamaha', 'FZ6-N (ABS)', 'mintransporte'), cc: 600 }], []))[0].models;
  assert.equal(models[0].n, 'FZ6-N ABS');
  assert.ok(models[0].a.includes('FZ6-N (ABS)'));
  const rows = registryEntries(['STREET TRIPLE R (ABS)', 'TIGER 800 (ABS)', 'TIGER SPORT (ABS)']
    .map(linea => ({ marca: 'TRIUMPH', linea, n: '5', src: 'risaralda' })));
  assert.deepEqual(merge(rows)[0].models.map(m => m.n), ['Street Triple R ABS', 'Tiger 800 ABS', 'Tiger Sport ABS']);
});

test('emission suffixes merge with base names while retaining raw aliases and years', () => {
  for (const suffix of [' Eiii', ' EIII', ' E3', 'EIII', ' EIII - NU', ' E3 - NUE']) {
    const raw = `JH 250${suffix}`;
    const models = merge([entry('Jialing', 'JH 250', 'curado', [2020]),
      entry('Jialing', raw, 'risaralda', [2026])])[0].models;
    assert.equal(models.length, 1, raw);
    assert.equal(models[0].n, 'JH 250');
    assert.ok(models[0].a.includes(raw));
    assert.deepEqual(models[0].y, [2020, 2026]);
  }
});

test('corporate makes collapse to canonical brands without losing model metadata', () => {
  const pairs = [['Keeway Motors', 'Keeway'], ['Lifan Group', 'Lifan'],
    ['MZ Motorrad- Und Zweiradwerk', 'MZ'], ['Sachs Bikes', 'Sachs'],
    ['Suzuki Motorcycle India Limited', 'Suzuki'], ['Triumph Motorcycles', 'Triumph'],
    ['Zero Motorcycles', 'Zero'], ['NSU Motorenwerke', 'NSU'], ['N.s.u', 'NSU'],
    ['Lohia Machinery Limited', 'LML'], ['Lotus - LML', 'LML'], ['Steyr-Daimler-Puch', 'Puch'],
    ['Morini', 'Moto Morini'], ['Izhmash', 'Izh'], ['Kalashnikov Concern', 'Izh']];
  for (const [corporate, canonical] of pairs) {
    assert.equal(brandOf(corporate), canonical);
    const brands = merge([entry(corporate, 'Model 125', 'wikidata', [2020]),
      entry(canonical, 'Model 125', 'risaralda', [2026])]);
    assert.equal(brands.length, 1, corporate);
    assert.equal(brands[0].n, canonical);
    assert.equal(brands[0].models.length, 1);
    assert.deepEqual(brands[0].models[0].y, [2020, 2026]);
    assert.deepEqual(brands[0].models[0].s, ['risaralda', 'wikidata']);
  }
});

test('unsubstantiated makes and generic brands are excluded with a verified Supermoto exception', () => {
  for (const brand of ['Opel', 'Fiat', 'Hummer', 'Aston Martin Lagonda', 'Subaru Corporation',
    'Auto Union', 'Gottlieb Daimler', 'Wilhelm Maybach', 'Teiyū Ichiryūsai', 'ATV', 'Scooter',
    'cafe racer', 'Motorcycle', 'Motocicleta', 'Turismo', 'Quad', 'UTV']) {
    assert.deepEqual(merge([entry(brand, 'Model 125', 'wikidata')]), [], brand);
    assert.deepEqual(registryEntries([{ marca: brand, linea: 'Model 125', n: '5', src: 'boyaca' }]), [], brand);
    assert.deepEqual(annexEntries([{ ...entry(brand, 'Model 125', 'mintransporte'), cc: 125 }], []), [], brand);
  }
  const rows = registryEntries([
    { marca: 'SUPERMOTO', linea: 'HS200GY', n: '3', src: 'boyaca' },
    { marca: 'SUPERMOTO', linea: 'MOTOCICLETA - HS200GY', n: '2', src: 'fusagasuga' },
    { marca: 'SUPERMOTO', linea: 'TURISMO', n: '5', src: 'boyaca' },
  ]);
  const brands = merge(rows);
  assert.equal(brands.length, 1);
  assert.equal(brands[0].n, 'Supermoto');
  assert.deepEqual(brands[0].models.map(m => m.n), ['HS 200GY']);
});

// An alternate asset lets the same regression gates audit archived builds.
const catalog = JSON.parse(readFileSync(process.env.CATALOG_TEST_ASSET
  ?? new URL('../../app/src/main/assets/catalog.json', import.meta.url), 'utf8'));
for (const pattern of ['runtSuffix', 'trailingFragments', 'motorcyclePrefix', 'brokenExtraction',
  'emissionSuffix', 'corporateBrand', 'bogusBrand', 'annexBoilerplate', 'transmissionToken',
  'displacementToken', 'repeatedBrand', 'marketingWord', 'editionTail', 'gluedToken',
  'spacedHyphen', 'normalizedDuplicate', 'annexOnlyVariant']) {
  test(`rebuilt catalog quality: ${pattern} must be zero`, () => {
    assert.deepEqual(qualityIssues(catalog)[pattern], []);
  });
}

test('rebuilt catalog exposes corrected names and retains the original lookup strings', () => {
  for (const [query, brand, name] of [
    ['Hero Hunk 160 - Nueva', 'Hero', 'Hunk 160'],
    ['Benelli TNT 150I - Nuevo', 'Benelli', 'TNT 150i'],
    ['Bajaj Motocicleta Pulsar 180 FI', 'Bajaj', 'Pulsar 180 FI'],
    ['Yamaha FNZ150D-6 (FZ-S)', 'Yamaha', 'FZ-S'],
    ['Yamaha FZ6-N (ABS)', 'Yamaha', 'FZ6-N ABS'],
    ['Honda XR150L E3', 'Honda', 'XR 150L'],
  ]) {
    const hits = search(catalog, query);
    const exact = hits.filter(h => h.brand === brand && h.model.n === name);
    assert.equal(exact.length, 1, query);
  }
  assert.ok(catalog.brands.find(b => b.n === 'Yamaha').models.some(m => m.n === 'FZ 2.0'));
});

const noisyNames = [
  ['KTM', '450 (linea Base Estandar)', '450', 450],
  ['Piaggio', 'Vespa (linea Base Estandar) 150', 'Vespa 150', 150],
  ['Honda', 'CBF 150 MT', 'CBF 150', 150],
  ['Vento', 'Phantom GT5 AT 150', 'Phantom GT5', 150],
  ['Suzuki', 'Boulevard 800 VL MT', 'Boulevard 800 VL', 800],
  ['Bajaj', 'Pulsar DTS-I 178.6', 'Pulsar DTS-I', 178.6],
  ['Yamaha', 'FZ 16 152 90', 'FZ 16', 152.9],
  ['Yamaha', 'FZ 16 152.9 ST Fazer', 'FZ 16 ST Fazer', 152.9],
  ['Yamaha', 'FZ 16 153', 'FZ 16', 153],
  ['Kawasaki', 'Z ZX 1000G MT', 'Z ZX 1000G', 1000],
  ['Honda', 'CB 150 Nueva Invicta MT', 'CB 150 Invicta', 150],
  ['Bajaj', 'Pulsar NS 150 Ed. Aniver', 'Pulsar NS 150', 150],
  ['Yamaha', 'FZ 15SFAZER', 'FZ 15S Fazer', 149],
  ['Yamaha', 'FZ -16 ST', 'FZ-16 ST', 153],
];
for (const [brand, raw, expected, cc] of noisyNames) {
  test(`clean and merge regression: ${brand} ${raw}`, () => {
    const base = entry(brand, expected, 'curado', [2026], ['Commercial alias']);
    // The old Annex importer synthesized GT5's trailing 150 from cilindraje;
    // its source line contains only PHANTOM GT5 AT.
    const source = brand === 'Vento' ? 'Phantom GT5 AT' : raw;
    const annex = annexEntries([{ ...entry(brand, source, 'mintransporte', [2001, 2025], [raw]), cc }], [base]);
    const models = merge([base, ...annex, entry(brand, source, 'boyaca', [2024])])[0].models;
    assert.equal(models.length, 1);
    assert.equal(models[0].n, expected);
    assert.deepEqual(models[0].y, [2001, 2024, 2025, 2026]);
    assert.deepEqual(models[0].s, ['boyaca', 'curado', 'mintransporte']);
    assert.ok(models[0].a.includes(raw));
    assert.ok(models[0].a.includes('Commercial alias'));
  });
  test(`asset regression: ${brand} ${raw}`, () => {
    const models = catalog.brands.find(b => b.n === brand).models;
    assert.ok(!models.some(m => m.n === raw), raw);
    // Generic VESPA already exists in a registry; Annex must enrich that entry.
    const preferred = brand === 'Piaggio' ? 'Vespa' : expected;
    assert.equal(models.filter(m => modelKey(m.n) === modelKey(preferred)).length, 1);
    assert.ok(search(catalog, `${brand} ${raw}`).some(h => modelKey(h.model.n) === modelKey(preferred)));
  });
}

test('brand-only names are dropped beside real models and never repeat a lone brand', () => {
  const rows = [entry('Kawasaki', 'Kawasaki', 'wikidata'), entry('Kawasaki', 'Ninja 400', 'curado')];
  assert.deepEqual(merge(rows)[0].models.map(m => m.n), ['Ninja 400']);
  const lone = merge(rows.slice(0, 1))[0];
  assert.equal(lone.models.length, 1);
  assert.equal(lone.models[0].n, 'Sin modelo');
  assert.ok(lone.models[0].a.includes('Kawasaki'));
  assert.ok(!catalog.brands.find(b => b.n === 'Kawasaki').models.some(m => m.n === 'Kawasaki'));
});

test('Annex normalization prefers an existing registry model before capacity synthesis', () => {
  const known = [entry('Honda', 'CBF 150', 'boyaca', [2026]), entry('Vento', 'Phantom GT5', 'risaralda', [2026])];
  const annex = annexEntries([
    { ...entry('Honda', 'CBF 150 MT', 'mintransporte', [2001]), cc: 149 },
    { ...entry('Vento', 'Phantom GT5 AT', 'mintransporte', [2025]), cc: 150 },
  ], known);
  for (const brand of merge([...annex, ...known])) {
    assert.equal(brand.models.length, 1);
    assert.equal(brand.models[0].n, known.find(e => e.brand === brand.n).model);
    assert.equal(brand.models[0].s.length, 2);
  }
});

test('cleanup preserves model families, capacities, features and meaningful numbers', () => {
  for (const name of ['MT-07', 'MT 09', 'AT1', 'FZ 2.0', 'FZ6-N', 'CR4 125',
    'Tuono V4 1100 Factory', 'Super 8 150', 'Apache RTR 160 4V', 'RR 250 2T', 'JH 125 16']) {
    assert.equal(cleanDisplay(name), name);
  }
  for (const raw of ['CB 150 Nuevo Invicta CVT', 'CB 150 Nueva Invicta AT']) {
    assert.equal(cleanDisplay(raw), 'CB 150 Invicta');
  }
  assert.equal(cleanDisplay('Duke 200 Limited Edition'), 'Duke 200');
  assert.equal(cleanDisplay('450 (Línea Base Estándar)'), '450');
  assert.equal(cleanDisplay('FZ - 16 ST'), 'FZ-16 ST');
});
