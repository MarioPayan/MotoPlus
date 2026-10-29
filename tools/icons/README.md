# Phosphor Icons

The app uses Phosphor Icons 2.1.1 (MIT, see [LICENSE](LICENSE)). `icons.mjs` maps existing app identifiers to regular icons; the four active tabs also use fill variants.

From the repository root:

```sh
mkdir -p design/.phosphor
npm pack @phosphor-icons/core@2.1.1 --pack-destination design/.phosphor
tar -xzf design/.phosphor/phosphor-icons-core-2.1.1.tgz -C design/.phosphor
node tools/icons/build.mjs
```

The converter accepts only path-based SVGs and fails on unsupported elements. It writes `res/drawable/ph_*.xml`, the Kotlin resource map, and the MIT license. The downloaded package is ignored by Git.

Barlow Condensed 400/500/600/700 is bundled from Google Fonts (`ofl/barlowcondensed`); its OFL license is in `app/src/main/assets/licenses/OFL-barlow-condensed.txt`.
