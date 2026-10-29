import { NEW_SUFFIX, EMISSION_SUFFIX, cleanDisplay } from './sources.mjs';
import { allowedBrand, allowedDisplay, brandOf } from './build.mjs';
import { compact, fold, modelKey } from './matching.mjs';

// Counts concern display fields only; original administrative strings remain
// searchable aliases. Brand counts count brands, model counts count models.
export function qualityIssues(catalog) {
  const rows = catalog.brands.flatMap(b => b.models.map(m => ({ brand: b.n, ...m })));
  const names = predicate => rows.filter(predicate).map(m => `${m.brand} ${m.n}`);
  return {
    runtSuffix: names(m => NEW_SUFFIX.test(m.n)),
    trailingFragments: names(m => /[\s\-(]+$/.test(m.n)),
    motorcyclePrefix: names(m => /\bMOTO(?:CICLETA|CILCETA|TCICLETA)\b/i.test(m.n)),
    brokenExtraction: names(m => !allowedDisplay(m.brand, m.n, m.s ?? [])
      || /^(?:(?:ABS|FI|CBS|EIII|E3|TK)\s*)+(?:\d+)?$/i.test(m.n)),
    emissionSuffix: names(m => EMISSION_SUFFIX.test(m.n)),
    corporateBrand: catalog.brands.filter(b => brandOf(b.n) !== b.n).map(b => b.n),
    bogusBrand: catalog.brands.filter(b => b.models.some(m => !allowedBrand(b.n, m.n))).map(b => b.n),
    annexBoilerplate: names(m => /\b(?:l[ií]nea\s+base|base\s+est[aá]ndar)\b/i.test(m.n)),
    transmissionToken: names(m => /(?:^|\s)(?:AT|CVT)(?=\s|$)|\sMT(?=\s|$)/i.test(m.n)),
    displacementToken: names(m => /(?:^|\s)\d{2,4}[.,]\d+(?=\s|$)|\b\d{2,4}\s+(?:[5-9]\d|[1-9]\d{2,3})(?:\s|$)/.test(m.n)),
    repeatedBrand: names(m => compact(m.n) === compact(m.brand)
      || fold(m.n).startsWith(fold(m.brand) + ' ')),
    marketingWord: names(m => /\bNuev[ao]\b/i.test(m.n)),
    editionTail: names(m => /\b(?:Ed\.?\s+Aniv\w*|Edition|Edici[oó]n)(?:\s+\d+)?$/i.test(m.n)),
    gluedToken: names(m => /\d[A-Z]*FAZER\b/i.test(m.n)),
    spacedHyphen: names(m => /\s-|-\s/.test(m.n)),
    normalizedDuplicate: catalog.brands.flatMap(b => {
      const seen = new Set();
      return b.models.filter(m => {
        const key = modelKey(cleanDisplay(m.n)), duplicate = seen.has(key);
        seen.add(key);
        return duplicate;
      }).map(m => `${b.n} ${m.n}`);
    }),
    annexOnlyVariant: catalog.brands.flatMap(b => {
      const established = new Set(b.models.filter(m => m.s?.some(s => s !== 'mintransporte'))
        .map(m => modelKey(cleanDisplay(m.n))));
      return b.models.filter(m => m.s?.length === 1 && m.s[0] === 'mintransporte'
        && established.has(modelKey(cleanDisplay(m.n)))).map(m => `${b.n} ${m.n}`);
    }),
  };
}

export function qualityCounts(catalog) {
  return Object.fromEntries(Object.entries(qualityIssues(catalog)).map(([key, rows]) => [key, rows.length]));
}
