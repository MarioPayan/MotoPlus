// Re-applies category.mjs to the existing catalog without fetching anything: node tools/catalog/recategorize.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { category } from './category.mjs';
const f = new URL('../../app/src/main/assets/catalog.json', import.meta.url);
const c = JSON.parse(readFileSync(f, 'utf8'));
let changed = 0;
for (const b of c.brands) for (const m of b.models) { const n = category(m.n); if (n !== m.c) { m.c = n; changed++; } }
writeFileSync(f, JSON.stringify(c));
console.log(`changed=${changed}`);
