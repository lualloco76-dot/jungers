-- FuegoPOS: diagnostico de conectividad de impresoras (SOLO LECTURA).
-- No modifica pedidos, usuarios, impresoras ni trabajos.
-- Verificar que el nombre coincida con la BD REAL del hosting.
USE `jongersc_fuegopos`;

SELECT DATABASE() AS base_seleccionada, NOW() AS mysql_ahora,
       UTC_TIMESTAMP() AS utc_ahora, @@session.time_zone AS zona_sesion,
       @@global.time_zone AS zona_global;

-- Si "segundos_sin_senal" es alto aun con Android encendido:
-- revisar acceso del endpoint, heartbeats y zona horaria de la escritura.
SELECT id, name, is_active, is_primary,
       kitchen_mode, receipt_mode, kitchen_printer, receipt_printer,
       last_seen, TIMESTAMPDIFF(SECOND,last_seen,NOW()) AS segundos_sin_senal,
       CASE
         WHEN is_active=0 THEN 'DESACTIVADO'
         WHEN last_seen IS NULL THEN 'NUNCA SINCRONIZADO'
         WHEN last_seen>DATE_ADD(NOW(),INTERVAL 90 SECOND) THEN 'RELOJ DESAJUSTADO'
         WHEN last_seen<DATE_SUB(NOW(),INTERVAL 90 SECOND) THEN 'SIN HEARTBEAT RECIENTE'
         ELSE 'HEARTBEAT RECIENTE'
       END AS diagnostico_servidor
FROM print_terminals
ORDER BY is_primary DESC, id;

-- Verificar si existe cola y qué columnas tiene la version instalada.
SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN ('orders','print_jobs','print_terminals','print_pair_codes');

SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='print_jobs' ORDER BY ordinal_position;

-- No pedir tokens, hashes, claves ni datos de clientes como evidencia.
