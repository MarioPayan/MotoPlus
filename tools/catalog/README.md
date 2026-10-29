# Bike catalog

Run from the repo root (Node 20+ and Poppler's `pdftotext`):

```sh
node tools/catalog/build.mjs
node tools/catalog/coverage.mjs
node --test tools/catalog/catalog.test.mjs
```

Raw successful responses live in `.cache/` (ignored by Git). Rebuilds reuse them;
`node tools/catalog/build.mjs --refresh` downloads them again. Keep the cache to
reproduce a source snapshot. The asset's `sources` records attribution and licenses.
Do not add Fasecolda or API Ninjas data.

- Wikidata: CC0; English display names and Spanish aliases.
- NHTSA vPIC: public domain; motorcycle models by year, with four-wheelers filtered.
  Uncached requests run at most once per second and back off on HTTP 403/429.
  A source failure leaves the existing asset untouched. Retry after the service
  recovers; successful cached responses do not need downloading again.
- datos.gov.co: CC BY-SA 4.0; Gobernación de Risaralda (`x9pp-pcn5`), Gobernación
  de Boyacá (`em3d-hmim`), Alcaldía de Fusagasugá (`aj2e-7jwg`). Only grouped
  motorcycle brand/line/year/count queries are made. Only Boyacá's plate-free
  schema metadata is needed. No individual vehicle records are requested.
- MinTransporte: official base gravable 2026, Annex 5. Only confidently aligned
  motorcycle rows are imported; rejected motorcycle rows are counted. Other
  vehicle classes are excluded. Although the fiscal year is 2026, the supplied
  PDF's printed model-year headers end at 2025. The `2001 y anteriores` cell
  contributes 2001; it cannot identify individual earlier years. Valuation
  columns describe the table's coverage, not guaranteed production years.
- `seed-latam.json`: hand-curated Latin American names.

Brands are normalized before grouping. Model identity sorts letter/number tokens,
so `390 Duke`, `Duke-390` and `Duke390` share a key. Registration codes remain
search aliases. Registry counts are summed across datasets and equivalent model
names before requiring at least five registrations. Noisy year endpoints outside
1950 through next year are ignored; valid endpoint ranges are expanded. Display
priority is seed, Wikidata, vPIC, registry, Annex 5. Each merged model retains all
source tags and years; aliases are omitted when empty. Generic Annex names use
capacity to confirm an existing family name within 8% or 10 cc (e.g. the 154 cc
GIXXER row matches Gixxer 150). A shared generic alias does not merge different
engine capacities.

Display names omit registry `MOTOCICLETA` prefixes, `NUEVA`/`NUEVO` annotations
(including truncated tails and appended numbers), and `EIII`/`E3` emission
suffixes before grouping. Original lines remain search aliases. A parenthesized
feature such as ABS, FI, CBS or TK stays attached to the model instead of
replacing its name. Explicit Yamaha FZ-S annotations retain the complete name;
FZ 2.0 stays separate. Corporate brand aliases collapse to their canonical makes.
Unsubstantiated car/person/class brands are excluded after checking all three
cached registry aggregates. `Supermoto HS200GY` is retained: seven motorcycle
registrations across Boyacá and Fusagasugá and the official Annex 5 confirm it.

The merge also removes Annex `LÍNEA BASE ESTÁNDAR` boilerplate, standalone
transmission tokens (`MT`, `AT`, `CVT`), edition tails, decimal displacement
values, and repeated whole capacities after a model number (including split
decimals such as `FZ 16 152 90`). `MT-07`, `MT 09`, `FZ 2.0`, `4V`, `2T`,
and short model suffixes remain intact. Marketing `Nueva`/`Nuevo` words are
removed anywhere, hyphen spacing is normalized, and glued Fazer descriptions
are separated (`15SFAZER` → `15S Fazer`). Fazer annotations stay attached to
their model codes. Original lines and Annex capacities remain search aliases.
Brand-only rows are dropped when an identified model exists; an otherwise lone
brand uses `Sin modelo` so its display never repeats the make. Equivalent
cleaned names union their years, aliases and source tags. Annex names match
existing cleaned names before any capacity is synthesized, and model numbers
already containing digits never receive an extra synthesized displacement.

Search requires every query word to occur in a compacted brand, model or alias.
Case, accents, spaces and hyphens do not matter. Exact model keys rank first,
then name prefixes, source count and alphabetical order. Keep `matching.mjs`
and `Catalog.search` in `Store.kt` consistent. The catalog search hint also
accepts the line printed on the rider's registration card.

`coverage.mjs [catalog.json]` checks the top 100 Risaralda lines by registrations
and the requested Colombian bike list using this search rule. Passing an older
asset compares coverage under the same rule, independently of the old app search.
The report also prints zero-required quality counts, total models, and 40 random
brand/model display names. Regression tests check each quality category against
the rebuilt asset as well as source/merge fixtures. To audit an older asset with
the same regression gates, run
`CATALOG_TEST_ASSET=/path/to/catalog.json node --test tools/catalog/catalog.test.mjs`.
