// Keep search normalization and ranking in sync with Catalog.search in Store.kt.
export const norm = s => String(s ?? '').normalize('NFKC').replace(/\s+/g, ' ').trim();
export const fold = s => norm(s).normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
export const compact = s => fold(s).replace(/[^a-z0-9]/g, '');
export const modelKey = s => (fold(s).match(/[a-z]+|[0-9]+/g) ?? []).sort().join('|');

const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;

export function search(catalog, query, limit = Infinity) {
  const words = fold(query).split(/\s+/).map(compact).filter(Boolean);
  if (!words.length) return [];
  return catalog.brands.flatMap(b => b.models.map(m => ({ brand: b.n, model: m })))
    .filter(h => {
      const fields = [h.brand, h.model.n, ...(h.model.a ?? [])].map(compact);
      return words.every(w => fields.some(f => f.includes(w)));
    })
    .map(h => {
      const q = words.filter(w => !compact(h.brand).includes(w)).join('');
      return { ...h, rank: !q ? 2 : modelKey(h.model.n) === modelKey(q) ? 0 : compact(h.model.n).startsWith(q) ? 1 : 2 };
    })
    .sort((a, b) => a.rank - b.rank || (b.model.s?.length ?? 0) - (a.model.s?.length ?? 0)
      || compare(fold(a.brand), fold(b.brand)) || compare(fold(a.model.n), fold(b.model.n)))
    .slice(0, Math.max(0, limit));
}
