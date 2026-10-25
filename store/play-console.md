# Google Play Console: lo que hay que rellenar

## Antes de subir
1. Cuenta de desarrollador (pago único de 25 $) a tu nombre.
2. Activar **Play App Signing** y subir `app/build/outputs/bundle/release/app-release.aab`, firmado con la clave de subida de `~/.local/share/motoplus-keys/upload.jks`.
3. Publicar `store/privacidad.md` en una URL pública (por ejemplo, Cloudflare Pages) y ponerla en la ficha.

## Seguridad de los datos
- Recopila datos: **No**. Todo se queda en el dispositivo; la copia opcional va al Google Drive del propio usuario, con su cuenta y el permiso drive.file, y el desarrollador nunca la recibe. Confirmar al rellenar el formulario si Google cuenta esa copia como recopilación; si lo hace, declararla como datos cifrados en tránsito que el usuario puede borrar.
- Comparte datos: **No**.
- Ubicación precisa: se usa en el dispositivo para la funcionalidad de la app, no se recopila.

## Permisos sensibles
- **Ubicación en segundo plano.** Función principal: "Registra los kilómetros de cada salida en moto para avisar del mantenimiento sin que el usuario apunte el odómetro". Vídeo (30 s): activar "Detectar salidas solas" → aviso propio → permisos → salida registrada en la notificación → odómetro actualizado.
- **Servicio en primer plano de tipo ubicación.** El registro de una salida, con notificación visible y botón "Terminar".
- **Reconocimiento de actividad.** Detectar el inicio y el fin de una salida.

## Prueba cerrada (cuentas personales nuevas)
- Unos 12 testers durante 14 días seguidos antes de pedir el acceso a producción.
