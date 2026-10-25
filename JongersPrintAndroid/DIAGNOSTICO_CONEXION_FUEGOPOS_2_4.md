# Jonger's Print Libre 2.4 — Diagnóstico y reconexión remota

## Qué corrige esta rama de Android
- Diferencia **actividad local** del servicio y **respuesta autenticada real** de FuegoPOS.
- El botón **COMPROBAR CONEXION AUTENTICADA** realiza `discover` autenticado sin retirar pedidos de la cola. Muestra el error HTTP si falla.
- Reconexión con reintento gradual (hasta 30 s de espera), sin descartar el token cifrado.
- Reinicio del servicio tras reiniciar el Android o actualizar la app, siempre que el usuario lo haya activado y el fabricante lo permita.
- Mantiene `printed_job_ids` y la confirmación pendiente para reducir trabajos duplicados.
- NO cambia la URL, el protocolo ESC/POS, el emparejamiento de un solo uso ni los métodos de pago.
- NO soluciona restricciones de red, WAF, la cola en el hosting o asignación de terminal equivocada.

## Prueba de 5 minutos
1. Abre el navegador **en el Android principal**, no desde el PC, y consulta
   `https://jongers.com.co/public/terminal_api.php?health=1`.
   Si recibes HTTP 403/HTML, el hosting está interceptando la API; solicita al administrador que permita ese endpoint para la aplicación, con sus controles de autenticación y rate limits intactos. No desactives Imunify360 de todo el dominio.
2. Dentro de Jonger's Print: empareja la impresora Bluetooth clásica / USB / Wi-Fi y pulsa **IMPRIMIR TICKET DE PRUEBA**. Si no imprime, corrige Bluetooth, energía, cable USB o IP Wi-Fi antes de usar FuegoPOS.
3. Si nunca vinculaste este Android, solicita un código temporal de **un solo uso** en FuegoPOS → Equipos e impresoras. El código caduca; no debe repetirse para cada venta.
4. Pulsa **COMPROBAR CONEXION AUTENTICADA** en la app. Debe confirmar sincronización con el servidor. Si falla, conserva el mensaje del error SIN compartir el código ni la credencial.
5. Activa la recepción automática. Configura permisos de notificaciones/Bluetooth/USB y, en Ajustes de Android, permite el funcionamiento en segundo plano y autoinicio según el fabricante. Mantén el dispositivo con energía.
6. En FuegoPOS → Equipos e impresoras, elige exactamente **ese Android** como equipo principal, activa el equipo y asigna la impresora detectada a destinos cocina y recibos (puede ser la misma). Que la app esté vinculada NO garantiza que haya impresora asignada.
7. Ejecuta el SQL SOLO LECTURA para comparar `NOW()` con `last_seen`. Crea un pedido de prueba, revisa **Cola de impresión**. Si el trabajo está pendiente, no vuelvas a cobrar ni crear el pedido; revisa la cola/terminal.

## Correcciones necesarias EN EL SERVIDOR PHP (requieren acceder a los archivos)
- En `terminal_api.php`, cada `discover` y `poll` autorizado debe actualizar `print_terminals.last_seen=NOW()` usando el mismo reloj de la base. No mezclar `date()` de PHP Bogotá con `NOW()` de un MySQL configurado en UTC sin conversión.
- La UI debe distinguir: *vinculado*, *online con heartbeat reciente*, *sin impresora asignada*, *con trabajos pendientes*, *bloqueo API*.
- Definir margen razonable de presencia (por ejemplo 90 segundos) y cache corto; que `last_seen` sea la fuente real, no solo el arranque de Android.
- Los pedidos deben guardarse con sus trabajos en `print_jobs` aunque el terminal esté temporalmente offline; el trabajo debe permanecer pendiente hasta ACK válido. No borrar ni reasignar automáticamente a otro equipo, no imprimir dos veces la misma versión.
- La terminal elegida para esa venta debe corresponder a `orders.print_terminal_id` y permanecer asociada al trabajo hasta impresión o reenvío manual.
- Conservar el nombre interno `kitchen` para la impresora de comandas aunque el módulo visual Cocina se haya retirado.
- Para error 403, comprobar reglas WAF/mod_security del hosting y pedir excepción **limitada al endpoint**, sin quitar autenticación ni compartir token.

## Limitaciones
Este cambio se preparó solo en una rama de GitHub; **no está instalado ni validado en el Android ni en el hosting del negocio**. Puede requerir habilitar el autoinicio según el fabricante Android. **No reinstalar ni borrar datos de la app existente** antes de respaldar la vinculación con el servidor: al desinstalar se pierde la credencial cifrada y hará falta generar un nuevo código.

## Nota operativa
FuegoPOS y Jonger's Print son dos programas independientes. Actualizar el agente Android no puede, por sí solo, arreglar el indicador de equipo desconectado, los estados de `print_jobs` o el bloqueo de un firewall del hosting.
