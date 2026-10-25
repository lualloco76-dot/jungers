# Jonger's Print Libre 2.0 — Servicio de impresión Android

App independiente y gratuita, sin suscripciones ni cuota de tickets, escrita para Jonger's.

## Instalación
1. Instalar APK en Android 6 o superior.
2. Abrir **Jonger's Print Libre**.
3. Elegir la impresora: **USB OTG**, **Bluetooth clásico emparejado** o **Wi-Fi/Ethernet ESC/POS (puerto 9100)**.
4. Para USB, conceder permiso USB; para Bluetooth, emparejar antes en Ajustes. Seleccionar el ancho de papel (58/80mm).
5. Pulsar **Imprimir ticket de prueba** y comprobar que salga correctamente.
6. Abrir **Ajustes de impresión de Android** y habilitar **Jonger's Print Libre**.
7. Al imprimir desde Chrome u otra aplicación, seleccionar la impresora Jonger's Print Libre en el menú de impresión de Android.

## Modos
- **Impresión del sistema Android**: imprime documentos desde Chrome y aplicaciones compatibles a impresoras ESC/POS. No necesita emparejamiento con el servidor y por tanto evita la dependencia del endpoint para este modo. Android puede mostrar el diálogo de impresión, que el usuario debe confirmar.
- **Impresión remota FuegoPOS**: vínculo opcional por código y recepción de cola de cocina. La impresión automática cuando un mesero envía desde *otro equipo* depende del API POST /public/terminal_api.php y de la excepción de Imunify360 del hosting. Hasta corregir el HTTP 403 de Imunify360, no se puede garantizar este modo.

## Limitaciones
- USB: impresoras ESC/POS con endpoint USB BULK OUT y permiso Android; no todas las impresoras USB son compatibles.
- Bluetooth: clásico SPP/RFCOMM; no BLE ni todos los modelos.
- Wi-Fi: impresoras ESC/POS que acepten TCP directo en la LAN, no CloudPRNT u otros protocolos.
- La transmisión de bytes no confirma físicamente que salió papel; revisar estados y tickets de prueba.
- Android debe tener activado el servicio de impresión del sistema; la impresión web requiere confirmación del diálogo.
- El corte automático al final del ticket depende del mecanismo de la impresora.
- APK compilada por GitHub Actions es de pruebas (debug); para distribución estable se debe firmar una versión release con una clave propia y conservar esa clave para actualizaciones.
