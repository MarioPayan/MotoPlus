# Google Drive: configurar el acceso (unos 10 minutos)

La copia en Drive ya está programada en la app, pero Google solo la deja funcionar cuando la app está registrada en un proyecto de Google Cloud. Hasta entonces, «Conectar Google Drive» falla con el aviso «Drive aún no está configurado».

No hace falta ninguna clave dentro de la app: Google reconoce la app por su paquete y por la huella de la clave con la que está firmada.

## Pasos

1. Entra en <https://console.cloud.google.com> con tu cuenta y crea un proyecto llamado **MotoPlus**.
2. **APIs y servicios → Biblioteca**: busca **Google Drive API** y actívala.
3. **APIs y servicios → Pantalla de consentimiento de OAuth** (o «Google Auth Platform»):
   - Tipo de usuario: **Externo**.
   - Nombre de la app: MotoPlus. Correo de asistencia: el tuyo.
   - Ámbitos: añade `https://www.googleapis.com/auth/drive.file`. Google lo clasifica como no sensible, así que no debería pedir verificación; confírmalo en esa misma pantalla.
   - Mientras la app esté en pruebas, añade como **usuarios de prueba** las cuentas que la vayan a usar, empezando por la tuya.
4. **APIs y servicios → Credenciales → Crear credenciales → ID de cliente de OAuth → Android**. Crea **dos**, uno por cada huella:

   | Para | Paquete | SHA-1 |
   |---|---|---|
   | Versión de publicación (la que instalo en el emulador y la del AAB) | `com.kaze.motoplus` | `1B:DE:33:3C:93:E3:18:02:3C:12:25:C6:3F:CA:54:47:71:CD:AD:0C` |
   | Versión de depuración | `com.kaze.motoplus` | `D6:46:7F:9A:4B:41:20:C3:50:DC:F3:E3:CB:1B:3E:42:D0:22:65:2D` |

5. Cuando subas la app a Google Play con **Play App Signing**, Google la vuelve a firmar con su propia clave. Copia la SHA-1 de **Play Console → Prueba y publicación → Integridad de la app → Firma de apps** y crea un tercer ID de cliente Android con ella. Sin ese tercero, la copia funciona en lo que instalo yo pero no en lo que se descarga de Play.

## Para probarlo en el emulador

Inicia sesión con una cuenta de Google en el emulador (Ajustes → Contraseñas y cuentas) que esté en la lista de usuarios de prueba. Luego, en la app: menú **Mi moto → Datos y copia en Drive → Conectar Google Drive → Copia ahora**. Debe aparecer `MotoPlus/motoplus-AAAA-MM-DD.zip` en tu Drive.

Las huellas SHA-1 no son secretas: identifican la clave pero no permiten firmar con ella.
