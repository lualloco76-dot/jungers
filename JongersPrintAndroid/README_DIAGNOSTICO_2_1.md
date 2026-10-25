# Jonger's Print Libre 2.1 - estabilidad de impresión Android

Se analizó el APK de referencia PosWebPrinter 2.0.11 únicamente para identificar su arquitectura pública (Android PrintService, representación PDF y control USB). No se incluyó código, biblioteca ni material gráfico de terceros.

## Mejoras
- Mantiene servicio de impresión Android con impresoras ESC/POS USB OTG, Bluetooth SPP y Wi-Fi.
- Copia documentos temporales entregados por Android a almacenamiento privado para garantizar PDF seekable.
- Controla excepciones de descubrimiento y procesamiento para reportar trabajos fallidos sin forzar la aplicación.
- Muestra diagnóstico y último cierre en la pantalla principal.
- Si la transmisión termina sin errores, se registra como enviada; no garantiza físicamente la salida del papel.
- Mantiene el modo remoto FuegoPOS, sujeto al bloqueo Imunify360 del hosting.

## Prueba
1. Instala la versión 2.1 y abre la aplicación.
2. Conecta la impresora USB OTG, autoriza el dispositivo, elige 58/80 mm y usa el botón de prueba.
3. Activa Jonger's Print Libre en Ajustes > Impresión > Servicios de impresión.
4. Imprime una vista previa del ticket desde Chrome y selecciona el servicio.
5. Si no imprime, abre la app y consulta el campo DIAGNOSTICO y ULTIMO CIERRE.
