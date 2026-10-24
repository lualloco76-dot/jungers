# Cómo obtener Jonger's Print.apk sin instalar Android Studio

Este ZIP **NO es el APK**, es el proyecto Android preparado para que **GitHub Actions compile el APK**.

## Pasos

1. Abre https://github.com/new e inicia sesión con una cuenta de GitHub.
2. Crea un repositorio **privado** con el nombre `jongers-print-android`, sin plantilla y con rama `main`.
3. Descomprime este ZIP en tu computador. Abre la carpeta `JongersPrintAndroid` y sube **todo su contenido** al repositorio, incluida la carpeta oculta `.github`. Para ello puedes usar **Add file → Upload files**, y luego **Commit changes**.
4. En el repositorio entra en **Actions → Compilar Jongers Print Android**. Si no se ejecutó automáticamente, pulsa **Run workflow → Run workflow**.
5. Una vez que la compilación muestre el indicador verde, abre esa ejecución. Abajo, en **Artifacts**, descarga `Jongers-Print-Android-APK`.
6. Descomprime el artefacto; el archivo interior `app-debug.apk` es el instalador para Android 8.0 o superior.
7. Pásalo al dispositivo Android y ábrelo para instalar. Es posible que debas permitir la instalación de aplicaciones de esa fuente.

## Configurar Jonger's Print

- **Primero** debe estar instalada en el servidor la migración y el PHP de la fase 17, incluyendo `public/terminal_api.php`.
- En Jonger's POS → **Equipos e impresoras**, crea o selecciona un equipo Android y genera una credencial exclusiva.
- En el dispositivo Android, empareja la impresora Bluetooth desde los Ajustes del sistema; abre **Jonger's Print**, guarda la credencial y selecciona el dispositivo emparejado.
- Pulsa **Imprimir ticket de prueba**; si imprime correctamente, habilita la impresión automática.
- El servicio funciona en primer plano, con notificación persistente; algunos dispositivos requieren desactivar el ahorro agresivo de batería para la aplicación.

## Seguridad y límites

- Este flujo genera un **APK debug** para **pruebas controladas**, no un APK de producción firmado con la clave empresarial. La firma de depuración puede cambiar entre compilaciones. Para actualizar a una futura versión firmada con otra clave podría ser necesario desinstalar esta versión y volver a ingresar la credencial.
- No publiques en GitHub la credencial de tu terminal ni información privada; esta app guarda la credencial cuando se ejecuta en Android, no en el código fuente.
- Una compilación verde solo significa que se generó el APK: **no demuestra que la impresión Bluetooth funcione en tu impresora**. Compruébalo con un ticket de prueba antes de recibir pedidos reales.
- Si falla el proceso, abre el paso rojo de GitHub Actions y guarda el texto del error. No envíes claves privadas en las capturas.
