# Jonger's Print — instalación Android

Versión 1.1 de pruebas; Android 6.0 (API 23) o superior. La aplicación contiene solo Java, por lo que no necesita un APK distinto según el procesador ARM de 32/64 bits.

## Instalación

1. Descarga el artefacto 'Jongers-Print-Android-APK' desde la ejecución de GitHub Actions que haya terminado correctamente.
2. Extrae el ZIP y abre `app-debug.apk` en el Android.
3. Autoriza la instalación de aplicaciones desde la fuente que utilizas (Chrome o Archivos). No desactives Play Protect.
4. Si Android dice 'No se instaló la aplicación' y ya tenías otra versión de pruebas, puede existir un conflicto de firma: anota la credencial de la terminal y desinstala la aplicación anterior, luego instala esta APK nueva. La desinstalación borra los datos locales.
5. Abre Jonger's Print, copia la credencial generada en FuegoPOS > Equipos e impresoras, concede los permisos necesarios, selecciona una impresora y emite un ticket de prueba antes de imprimir ventas reales.

**Limitaciones:** no funciona en Android 5.x ni anteriores. El Bluetooth soportado es clásico SPP; BLE/GATT y las impresoras que requieren SDK propietario pueden necesitar una integración distinta. El funcionamiento depende de la impresora y los permisos de Android. La prueba de compilación no equivale a una prueba con hardware real.

Los APK debug de GitHub Actions suelen firmarse con claves distintas entre ejecuciones; para actualizaciones sin desinstalar será necesario configurar una firma de lanzamiento estable y privada.
