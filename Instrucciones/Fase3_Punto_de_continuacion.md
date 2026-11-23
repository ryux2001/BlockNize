# Fase 3 — Punto de continuación

Fecha de actualización: 17 de noviembre de 2026  
Estado de trabajo: retomado; H3 sigue incompleta. Se completó una batería adicional de aceptación
manual con dos AVD; quedan pendientes los límites indicados al final de esta nota.

Lee primero el [plan detallado de Fase 3](Fase3_Plan_Sincronizacion.md). Esta nota registra el
estado del árbol de trabajo y los siguientes pasos para retomar sin repetir la exploración.

## Qué quedó implementado

- Android conserva el modo local sin cuenta y `blocknize.db`. La nube abre una base Room separada
  por entorno e identidad; la versión de Room actual es v9.
- Ajustes incorpora login/registro, activación de nube, confirmación antes de importar datos,
  sincronización manual, cambio al espacio local y resolución de conflictos.
- Las mutaciones principales de agenda y plantillas escriben su cambio y operación pendiente en
  una transacción local. WorkManager sincroniza con red, al volver a primer plano y periódicamente.
- El worker aplica un snapshot consistente cuando cambia el cursor del servidor. Un cursor local
  vacío fuerza el primer bootstrap, incluso para cuentas anteriores a las tablas de sync que aún
  notifican cursor `0`.
- La UI muestra operaciones pendientes, última sincronización correcta y errores de conexión o
  sesión. El widget usa la zona horaria de la cuenta en modo nube.
- Las operaciones nuevas usan claves `bn-sync:<deviceId>:<operationId>`. El backend guarda el
  hash, dispositivo y respuesta sin caducidad; las claves antiguas de la cola conservan su
  formato para no cambiar la idempotencia de operaciones ya creadas.
- El backend ofrece `/sync/state`, `/sync/changes`, `/sync/bootstrap` y `/sync/import`. La
  importación inicial es transaccional y solo admite cuentas vacías; una cuenta con datos queda
  separada de la agenda local, sin fusión automática.
- Las acciones offline principales envían instante y zona de origen para que una subida tardía
  no se rechace únicamente porque la fecha de la actividad ya pasó.

## Verificación disponible

Pasaron estas comprobaciones durante la reanudación:

```text
.\android\gradlew.bat -p android :app:compileDebugKotlin  -> BUILD SUCCESSFUL
npm.cmd run build (desde backend/)                           -> correcto
npm.cmd run test:acceptance (desde backend/)                 -> 2/2 pruebas; limpieza verificada
.\android\gradlew.bat -p android :app:assembleDebug         -> BUILD SUCCESSFUL
GET /api/v1/health/ready                                     -> ready; PostgreSQL conectado
```

En un AVD se instaló la APK, se inició sesión con la cuenta sintética Neon, se activó el espacio
nube y una semana creada desde Android apareció en `GET /templates`; Ajustes mostró cero operaciones
pendientes y una sincronización correcta. Se eliminó aquella semana y se limpió el estado de la app
en ese AVD. La segunda instancia AVD no se registró en ADB y dejó un proceso QEMU headless que
bloqueaba el AVD principal; se cerró y el AVD principal arrancó en frío, sin borrar datos, y se
apagó limpiamente. Se regeneró el snapshot `default_boot`; el usuario confirmó después que Android
Studio vuelve a abrir el AVD y BlockNize llega a Inicio.

El 17 de noviembre se conectaron simultáneamente `emulator-5554` y `emulator-5556` a la misma cuenta
sintética. La semana vacía `F3-TwoAVD-20261117` creada en el primero apareció en el segundo al
sincronizar. Después se comprobó edición cruzada: el cambio de nombre hecho en el segundo AVD llegó
al primero. Esa prueba detectó un defecto real: `CloudSyncWorker` enviaba `template.rename` sin
`If-Match` ni `Idempotency-Key`, y el backend respondía HTTP 428. Se añadió `headers()` a esa llamada,
se reconstruyó la APK y se instaló en ambos AVD; el renombrado y su propagación pasaron después.

También pasaron estas pruebas manuales:

- **Sin conexión y reinicio:** con el primero en modo avión se renombró la semana. Tras forzar la
  detención y volver a abrir la app, el cambio seguía presente y Ajustes mostraba una operación
  pendiente. Al recuperar red, WorkManager la subió y el segundo dispositivo recibió el cambio.
- **Conflicto simultáneo:** se renombró la misma semana de forma distinta en cada AVD mientras ambos
  estaban sin red. Al reconectar, la segunda subida produjo un conflicto explícito. Se eligió
  **Conservar mi cambio** y ambos terminaron con `F3-Conflict-B-20261117`, sin pendientes ni conflictos.
- **Aislamiento de cuenta:** en el segundo AVD se creó una cuenta sintética temporal. Su lista estaba
  vacía y no mostraba las semanas de la cuenta A. Al volver a A, reapareció su semana y no había
  datos de B. La cuenta y su perfil temporales se borraron de Neon después de la prueba.
- **Acción offline con fecha ya pasada según el dispositivo:** se creó y completó una tarea temporal
  sin red, se reinició la app y se avanzó solo el reloj del segundo AVD hasta el 20 de noviembre.
  Al reconectar, la cola se envió correctamente. El avance también dejó caducado el access token:
  la app lo renovó con la cookie de sesión aún válida. Se restauraron hora automática y fecha del AVD;
  la tarea temporal se borró y su eliminación se sincronizó al primer dispositivo.

La comprobación automatizada `npm.cmd run test:acceptance` pasó 2/2 casos de API, incluyendo
aislamiento de tokens Neon, y verificó la limpieza de su cuenta temporal. La compilación de la APK
terminó con `BUILD SUCCESSFUL`. Al cerrar las pruebas, ambos emuladores estaban conectados a la red,
con fecha automática activa en el segundo, y mostraban `F3-Conflict-B-20261117`. Los contadores de
operaciones pendientes quedaron en cero.

La semana `F3-Conflict-B-20261117` permanece en la cuenta sintética y en ambos AVD: la app no ofrece
eliminar semanas y la API no expone una operación para eliminarlas. La tarea temporal sí se eliminó.

## Migraciones y despliegue

Las migraciones de sync se aplicaron correctamente a la rama Neon `desarrollo` el 17 de
noviembre de 2026 con `npm run db:migrate`:

- `backend/migrations/0001_brown_anthem.sql`: cursor y commits por cuenta.
- `backend/migrations/0002_flaky_enchantress.sql`: recibos idempotentes duraderos por dispositivo.

El usuario confirmó que ambas URLs apuntaban a `desarrollo` y autorizó la migración. Los hosts
configurados son distintos, así que esa confirmación fue necesaria; no copiar valores de `.env`
a documentación, logs o mensajes.

## Trabajo pendiente prioritario

1. Completar aceptación en dos dispositivos con cuentas sintéticas: pasaron login, bootstrap,
   edición cruzada, desconexión/reconexión, reinicio con una operación pendiente, resolución de un
   conflicto concurrente, aislamiento al cambiar de cuenta y renovación de un access token caducado.
   La prueba de fecha pasada avanzó el reloj del cliente, pero no el reloj del backend; falta probar
   que la fecha del servidor también sea posterior al día de la actividad. Tampoco se revocó ni se
   dejó caducar la cookie de renovación, así que no se verificó la reautenticación tras expirar por
   completo la sesión. Mantener H3 abierta hasta completar estos casos y los demás del plan.
2. Cerrar los límites del protocolo servidor. `/sync/changes` solo pagina marcadores de commit;
   el cliente descarga snapshots completos y el bootstrap todavía no pagina. Faltan feed de
   entidades, retención/caducidad de cursores y registro, generación y revocación de dispositivos.
3. Comparar agenda e historial entre Android y backend con fixtures de zona horaria, horario de
   verano, selección/revisiones, temporales, excepciones y completados; entonces actualizar el
   informe de aceptación y decidir si H3 se puede cerrar.

## Limitaciones conocidas

- Las filas históricas locales de `occurrence_exception_events` no tienen `eventId`; al importarlas
  el servidor genera IDs para esos eventos. Debe quedar cubierto en el informe de aceptación.
- No se fusionan automáticamente una agenda local y una cuenta nube que ya contiene datos.
- Las migraciones Room y backend, además de los archivos de implementación, siguen sin commit;
  conservar los cambios actuales del árbol al retomar.

