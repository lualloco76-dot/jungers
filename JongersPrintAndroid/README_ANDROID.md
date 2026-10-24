# Jonger's Print Android · Fase 17

Proyecto fuente para Android Studio, **NO es un APK compilado**.

- Android 8.0+; Gradle plugin 8.7.3, JDK 17, SDK 35, Java.
- Conectividad HTTPS con `https://jongers.com.co/public/terminal_api.php`.
- Credencial aleatoria de 64 caracteres generada en Equipos e impresoras, guardada cifrada con Android Keystore.
- Servicio visible con notificación persistente: consulta la cola aprox. cada 3,5 s con red disponible.
- Impresión ESC/POS: Bluetooth **clásico RFCOMM SPP**, USB **bulk OUT** mediante OTG y red local TCP/IP puerto 9100.
- Bluetooth BLE-only, impresoras con protocolos propietarios y terminales POS con SDK cerrado requieren adaptadores específicos; sin modelo no se garantiza compatibilidad.

## Compilar
1. Abrir carpeta `JongersPrintAndroid` en Android Studio (modo Project).
2. Permitir sincronización Gradle y descargar SDK 35.
3. Build > Build Bundle(s) / APK(s) > Build APK(s). Para distribuir, generar APK firmado en Build > Generate Signed Bundle / APK.
4. Instalar la APK en la tablet Android principal. Conceder permisos Bluetooth/notificaciones (y USB al conectar).

## Conexión
1. FuegoPOS > Equipos e impresoras > Registrar equipo. Seleccionar **Android (Bluetooth / USB / Wi-Fi)** tanto para cocina como para recibo. Generar la clave.
2. Pegar clave en app y pulsar **Guardar** (no enviar clave por chats ni incluirla en capturas).
3. Emparejar impresora ESC/POS Bluetooth desde Ajustes Android, conectar impresora por USB con OTG (usar **Autorizar USB** en la app), o agregar IP de impresora de red.
4. Pulsar **INICIAR IMPRESIÓN AUTOMÁTICA** y mantener la notificación del servicio. Desactivar optimización de batería para esta app en ajustes de Android si el fabricante la suspende.
5. Volver a FuegoPOS > Equipos e impresoras, actualizar y asignar cocina / recibos. Si hay una única impresora, puede autoseleccionarse. Para más de una se elige manualmente.
6. En Nueva venta seleccionar la terminal Android. Probar comanda y recibo con poco papel antes de operación real.

## Límites y auditoría
- No imprimir desde el navegador Android; la app nativa comunica con la impresora y confirma recepción local.
- El estado impreso significa que se transmitieron bytes al puerto/controlador, NO que el papel salió.
- Si falla o se corta la conexión en medio de un ticket, se necesita revisar físicamente antes de reintentar para evitar doble impresión.
- El servicio foreground necesita quedar activo; no garantizamos reinicio automático tras reboot ni frente a políticas agresivas del fabricante.
- La lista de impresoras USB identifica por VID/PID. Dos impresoras USB con idéntico VID/PID podrían ser ambiguas: en ese caso usar un único dispositivo USB o Bluetooth/Red.
- Si la impresora no tiene ESC/POS o no expone Bluetooth SPP / USB bulk / TCP 9100, hará falta el driver/SDK del fabricante.
- No subir este proyecto Android a `public_html`; solo se suben los archivos del ZIP del servidor.

Nota Bluetooth: antes de pulsar Iniciar, pulsa **Elegir impresora Bluetooth emparejada** y selecciona la impresora real. El agente no incluye auriculares ni otros dispositivos Bluetooth en el descubrimiento de impresoras. Usa **IMPRIMIR TICKET DE PRUEBA** para verificar compatibilidad.
