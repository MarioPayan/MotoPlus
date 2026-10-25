# MotoPlus

App Android para llevar el mantenimiento de la moto por piezas. La moto se dibuja como una lámina de taller y cada conjunto se pinta según cuánto le queda. El kilometraje se estima solo, y cuando algo suena raro la IA del propio usuario hace el informe, que vuelve a la app.

Kotlin + Jetpack Compose, solo Android. Todo vive en el móvil (`garage.json` en el almacenamiento de la app); no hay servidor.

## Comandos

```sh
export JAVA_HOME=~/.local/share/motoplus-tools/jdk-21.0.13+9
./gradlew :app:testDebugUnitTest      # dominio: km, estados, plantillas, informe IA
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:bundleRelease         # AAB firmado (clave en ~/.local/share/motoplus-keys, fuera del repo)
PATH=~/.local/share/motoplus-tools/maestro/bin:$PATH maestro test .maestro/01_alta_moto.yaml   # flujos en el emulador, en orden 01 → 03

node tools/catalog/build.mjs          # regenera assets/catalog.json (necesita red)
node tools/catalog/recategorize.mjs   # reaplica tools/catalog/category.mjs sin descargar nada
node tools/drawing/export.mjs         # regenera assets/bike_*.json (naked desde design/prototipo.html, el resto desde tools/drawing/bikes.mjs)
node tools/drawing/preview.mjs /tmp   # PNG de cada lámina para revisarla
```

## Datos

- **Catálogo de motos** (`app/src/main/assets/catalog.json`): Wikidata (CC0), NHTSA vPIC (dominio público, modelos por año para EE. UU.) y `tools/catalog/seed-latam.json`, una lista curada a mano de modelos de España y Latinoamérica que hay que revisar. Si una moto no está, se puede escribir a mano.
- **Intervalos**: genéricos por tipo de moto, transmisión y refrigeración (`domain/Templates.kt`). Todavía no son los del manual de cada modelo.
- **Láminas**: una por tipo (naked, deportiva, trail, custom, scooter). Los ids de zona (`cadena`, `freno_del`…) son el contrato entre el dibujo y la app; `export.mjs` falla si a una lámina le falta una zona.
- **Publicación**: `store/` tiene la ficha de Play, la política de privacidad y lo que pide la consola.

## Informe IA

1. *Compartir con mi IA* manda el estado completo de la moto por el menú Compartir de Android.
2. La IA responde con un bloque ```` ```motoplus ```` en JSON, o con un enlace `https://motoplus.app/i#r=<base64url>`.
3. El usuario comparte esa respuesta de vuelta a MotoPlus (o la pega) y la app la guarda y marca las zonas en la lámina.
