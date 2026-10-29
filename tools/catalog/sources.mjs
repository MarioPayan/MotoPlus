import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { norm, fold, compact } from './matching.mjs';

export const CACHE = new URL('./.cache/', import.meta.url);
export const MAX_YEAR = new Date().getUTCFullYear() + 1;
let nextVpicRequest = 0;
const pause = ms => new Promise(r => setTimeout(r, ms));
const refresh = process.argv.includes('--refresh');
mkdirSync(CACHE, { recursive: true });

// Store successful raw responses atomically; a failed source must not produce a partial catalog.
export async function cached(name, url, headers = {}) {
  const file = new URL(name, CACHE);
  if (!refresh && existsSync(file)) return readFileSync(file);
  let error;
  for (let i = 0; i < 4; i++) {
    let retry = 1000 * (i + 1);
    try {
      if (url.startsWith('https://vpic.nhtsa.dot.gov/')) {
        const at = Math.max(Date.now(), nextVpicRequest);
        nextVpicRequest = at + 1000;
        await pause(Math.max(0, at - Date.now()));
      }
      const r = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0 MotoPlusCatalog/0.2', ...headers },
        signal: AbortSignal.timeout(60000) });
      if (!r.ok) {
        if (r.status === 403 || r.status === 429) retry = 60000;
        throw new Error(`HTTP ${r.status}`);
      }
      const bytes = Buffer.from(await r.arrayBuffer());
      if (name.endsWith('.json')) JSON.parse(bytes.toString());
      if (name.endsWith('.pdf') && bytes.subarray(0, 5).toString() !== '%PDF-') throw new Error('Invalid PDF');
      const temp = new URL(name + '.tmp', CACHE);
      writeFileSync(temp, bytes);
      renameSync(temp, file);
      return bytes;
    } catch (e) { error = e; }
    if (i < 3) {
      console.warn(`${name}: ${error.message}; retry in ${retry / 1000}s`);
      await pause(retry);
    }
  }
  throw new Error(`${name}: ${error.message} (${url})`);
}
export async function getJson(name, url, headers) {
  return JSON.parse((await cached(name, url, headers)).toString());
}

export const REGISTRIES = [
  { id: 'x9pp-pcn5', tag: 'risaralda', field: 'clase', publisher: 'Gobernación de Risaralda' },
  { id: 'em3d-hmim', tag: 'boyaca', field: 'clase', publisher: 'Gobernación de Boyacá' },
  { id: 'aj2e-7jwg', tag: 'fusagasuga', field: 'tipo', publisher: 'Alcaldía de Fusagasugá' },
];
export const ANNEX_URL = 'https://mintransporte.gov.co/info/mintransporte/media/anexos/4IOheFc2.PDF';
export const attribution = Object.fromEntries(REGISTRIES.map(r => [r.tag,
  `${r.publisher}, datos.gov.co ${r.id}, CC BY-SA 4.0, https://www.datos.gov.co/d/${r.id}`]));

export async function registries() {
  return (await Promise.all(REGISTRIES.map(async r => {
    // Only Boyacá needs schema discovery. Other view metadata can contain plate previews.
    if (r.tag === 'boyaca') {
      const meta = await getJson(`${r.id}-metadata.json`, `https://www.datos.gov.co/api/views/${r.id}.json`);
      const fields = new Set(meta.columns.map(c => c.fieldName));
      if (![r.field, 'marca', 'linea', 'modelo'].every(f => fields.has(f))) throw new Error(`${r.id}: schema changed`);
    }
    const q = new URLSearchParams({
      '$select': 'marca,linea,min(modelo) as y0,max(modelo) as y1,count(*) as n',
      '$where': `${r.field}='MOTOCICLETA'`, '$group': 'marca,linea', '$limit': '50000',
      '$order': 'n DESC,marca,linea',
    });
    // Only aggregates: never request individual vehicle records or plates.
    const rows = await getJson(`${r.id}-aggregates.json`, `https://www.datos.gov.co/resource/${r.id}.json?${q}`);
    if (rows.length >= 50000) throw new Error(`${r.id}: aggregate limit reached`);
    console.log(`${r.tag}: ${rows.length} aggregate rows`);
    return rows.map(row => ({ ...row, src: r.tag }));
  }))).flat();
}

export function year(value) {
  const s = norm(value).replace(/^(\d)\.(\d{3})$/, '$1$2');
  if (!/^\d{4}$/.test(s)) return null;
  const y = Number(s);
  return y >= 1950 && y <= MAX_YEAR ? y : null;
}
export function yearRange(y0, y1) {
  const valid = [year(y0), year(y1)].filter(y => y !== null);
  if (!valid.length) return [];
  return Array.from({ length: Math.max(...valid) - Math.min(...valid) + 1 }, (_, i) => Math.min(...valid) + i);
}

export const cleanLine = s => norm(s).replace(/^MOTO(?:CICLETA|CILCETA|TCICLETA)\b\s*[-–—]?\s*/i, '');
// Registry exports truncate NUEVA/NUEVO and sometimes append part of a capacity.
// These are administrative annotations, never model/engine identifiers.
export const NEW_SUFFIX = /(?:(?:\s*[-(]\s*|\s+)NU(?:E(?:V(?:[AO])?)?)?|\s+-\s*N)[\s\d).,-]*$/i;
export const EMISSION_SUFFIX = /(?:\s+|(?<=\d))E(?:III|3)\s*$/i;
// A capacity written as a decimal is administrative data (FZ 2.0 is a
// generation name). Repeated whole capacities follow an existing model number;
// short suffixes such as JH 125 16, 4V and 2T remain model identifiers.
export function cleanDisplacement(value) {
  return norm(value
    .replace(/(?<![\w.])\d{2,4}[.,]\d+(?:\s*CC)?(?=\s|$)/gi, '')
    .replace(/(\b\d{2,4})\s+([5-9]\d|[1-9]\d{2,3})(?:\s+\d{1,2})?(?=\s|$)/g, '$1'));
}
export function cleanDisplay(value) {
  let line = cleanLine(value), previous;
  do {
    previous = line;
    line = norm(line
      .replace(/\([^()]*\)/g, part => /^(?:l[ií]nea\s+)?(?:base(?:\s+est[aá]ndar)?|est[aá]ndar)$/i
        .test(part.slice(1, -1).trim()) ? '' : part)
      .replace(NEW_SUFFIX, '')
      .replace(/\bNuev[ao]\b/gi, '')
      // MT at the beginning of MT 09 is Yamaha's model family. Hyphenated
      // MT-07 and alphanumeric AT1 are also model identifiers.
      .replace(/(?<![\p{L}\p{N}-])(?:MT|AT|CVT)(?![\p{L}\p{N}-])/giu,
        (token, at, text) => /^MT$/i.test(token) && at === 0 && /^\s+\d/.test(text.slice(token.length)) ? token : '')
      .replace(/(?:\s+(?:special|limited|black|white|soho|racer|adventure|moto2(?:tm)?))?\s+(?:edition|edici[oó]n|ed\.?\s+(?:aniv\w*|especial|limitada))(?:\s+\d{2,4})?$/i, '')
      .replace(/(\d[A-Z]*)(FAZER)\b/gi, '$1 Fazer')
      .replace(/\s*[-({]\s*FAZER\s*[})]?$/i, ' Fazer')
      .replace(/\s+-\s*(ABS|FI|CBS|TK)\b/gi, ' $1')
      .replace(/\s*-\s*/g, '-')
      .replace(/[\s\-(]+$/, '').replace(EMISSION_SUFFIX, ''));
    line = cleanDisplacement(line);
  } while (line !== previous);
  return line;
}
export const usable = s => !!compact(s) && !/^(sin (linea|marca|informacion)\b.*|no (registra|definid[ao])|n[/.]?a|ningun[ao]|otros?|\d+)$/i.test(fold(s));

const keepUpper = /^(?:[A-Z]{1,3}(?:-[A-Z]{1,3})*|.*\d.*|ABS|CFMOTO|NMAX|BWS|NKD|TTR|TTX)$/;
export function title(s) {
  return norm(s).split(' ').filter(Boolean).map(w => keepUpper.test(w) ? w : w[0].toUpperCase() + w.slice(1).toLowerCase()).join(' ');
}

// Registration codes are retained verbatim as aliases, even when a commercial name is known.
export function displayName(brand, value, rawBrand = brand) {
  let line = cleanDisplay(value).replace(new RegExp(`^${brand.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s+`, 'i'), '');
  if (brand === 'Bajaj') {
    line = line.replace(/^BAJAJ\s+/i, '');
    if (['pulsar', 'discover'].includes(compact(rawBrand)) && !fold(line).startsWith(fold(rawBrand))) line = rawBrand + ' ' + line;
  }
  if (brand === 'Victory') line = line.replace(/^VICTORY\s+/i, '');
  if (brand === 'AKT') {
    const code = compact(line).replace(/eiii$/, '');
    const m = code.match(/^ak(\d{3})(nkd[r]?|flex|ttr|ttx|tt|cr4|cr5|ds)$/);
    if (m) return `${m[2].startsWith('nkd') ? 'NKD' : m[2] === 'flex' ? 'Flex' : m[2] === 'ds' ? 'TT DS' : m[2].toUpperCase()} ${m[1]}`;
  }
  // PRM is the equipment trim of the same Apache model.
  if (brand === 'TVS') line = line.replace(/^(APACHE RTR 310) PRM$/i, '$1');
  // A malformed FZ(-S) annotation used to select just "-S". The complete
  // FZ-S token is explicit evidence; it does not identify FZ 2.0 as the same bike.
  if (brand === 'Yamaha' && /\bFZ\s*[-.(]?\s*-?\s*S\s*[)}]?(?:\s+CC)?$/i.test(line)) return 'FZ-S';
  const features = /^(?:(?:ABS|FI|CBS|EIII|E3|TK)\s*)+$/i;
  line = line.replace(/\(([^()]+)\)/g, (all, part) => features.test(part.trim()) ? ` ${part.trim()} ` : all);
  line = line.replace(/\s+-\s*(ABS|FI|CBS|TK)\b/gi, ' $1')
    .replace(/\b(?:ABS|FI|CBS|TK)\b/gi, feature => feature.toUpperCase());
  const commercial = line.match(/\(([^()]+)\)/)?.[1];
  if (commercial && !/linea|estandar|base|electrica|\d+(?:cc|kw)/i.test(commercial)
      && /^[\p{L}\p{N}]/u.test(commercial.trim()) && /[a-z]/i.test(commercial)
      && !/^\w+-\d+$/i.test(commercial)) line = commercial;
  line = line.replace(/([A-Z]{2,})(\d{2,})/g, '$1 $2');
  return title(cleanDisplay(line));
}

export async function annex() {
  const pdf = new URL('mintransporte-2026.pdf', CACHE);
  await cached('mintransporte-2026.pdf', ANNEX_URL);
  const txt = new URL('mintransporte-2026.txt', CACHE);
  if (refresh || !existsSync(txt)) execFileSync('pdftotext', ['-layout', pdf.pathname, txt.pathname]);
  return parseAnnex(readFileSync(txt, 'utf8'));
}

export function parseAnnex(text) {
  const out = [];
  let candidates = 0, skipped = 0;
  const headers = new Set();
  for (const page of text.split('\f')) {
    const lines = page.split('\n');
    const header = lines.find(l => l.includes('MARCA (3)') && l.includes('2001 y'));
    if (!header) continue;
    const cols = [...header.matchAll(/\b20\d{2}\b/g)].map(m => ({ year: Number(m[0]), center: m.index + 2 }));
    cols.forEach(c => headers.add(c.year));
    // Centers in the layout text locate the valuation cells, including blank cells.
    const bounds = cols.slice(1).map((c, i) => Math.floor((cols[i].center + c.center) / 2) + 1);
    const first = cols[0].center - 1;
    const classStart = header.indexOf('CLASE') - 4;
    const brandStart = header.indexOf('MARCA') - 3;
    const lineStart = header.indexOf('MARCA') + 14;
    const ccStart = header.indexOf('CILINDRAJE') - 2;
    for (let i = 0; i < lines.length; i++) {
      const row = lines[i];
      if (!/\bMOTOCICLETA\b/.test(row.slice(classStart, brandStart))) continue;
      candidates++;
      const left = row.slice(classStart, ccStart).match(/MOTOCICLETA\s+(.+)/)?.[1] ?? '';
      const longBrand = ['ROYAL ENFIELD', 'HARLEY DAVIDSON', 'UNITED MOTORS', 'LOTUS - LML']
        .find(b => left.startsWith(b + ' '));
      const parts = left.trim().split(/\s{2,}/);
      const brand = longBrand ?? (parts.length === 2 ? norm(parts[0]) : '');
      const model = longBrand ? norm(left.slice(longBrand.length)) : norm(parts[1]);
      const cc = norm(row.slice(ccStart, first - 22));
      const years = [];
      let valid = true;
      for (let c = 0; c < cols.length; c++) {
        const cell = row.slice(c ? bounds[c - 1] : first, bounds[c]).trim();
        if (cell && !/^\d+(?:[.,]\d+)?$/.test(cell)) valid = false;
        if (cell && Number(cell.replace(',', '.')) > 0) years.push(cols[c].year);
      }
      // Reject missing brands and names continued above/below the valuation row.
      const continuation = l => l && norm(l.slice(lineStart, ccStart)) && !/\bMOTOCICLETA\b/.test(l)
        && !/MINISTERIO|TABLA|LINEA \(4\)/.test(l);
      const hasBrand = !!norm(row.slice(brandStart, header.indexOf('MARCA') + 3));
      if (!hasBrand || !brand || !model || !usable(brand) || !usable(model) || !/^\d+(?:[.,]\d+)?$/.test(cc)
          || !valid || !years.length || continuation(lines[i - 1]) || continuation(lines[i + 1])
          || (model.match(/\(/g) ?? []).length !== (model.match(/\)/g) ?? []).length) {
        skipped++; continue;
      }
      // The combined "2001 and earlier" cell cannot identify individual earlier years.
      out.push({ brand, model, years, cc: Number(cc.replace(',', '.')), src: 'mintransporte' });
    }
  }
  if (!candidates || !out.length) throw new Error('Annex 5: no confident motorcycle rows');
  console.log(`Annex 5: ${out.length}/${candidates} motorcycle rows, skipped=${skipped}; year columns=${[...headers].sort().join(',')}`);
  return out;
}
