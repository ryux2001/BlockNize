# Fase 1 — Consolidación Android

Fecha: 4 de noviembre de 2026.

## Alcance de la implementación

- Copia lógica completa de las 12 tablas funcionales y las preferencias de apariencia.
- Inspección de la copia en Room temporal, resumen y confirmación antes de sustituir datos.
- Restauración transaccional sin mezclar bases ni reproducir acciones de usuario.
- Room v5: tabla técnica `pending_restore_preferences` y migración no destructiva 4 → 5.
- Recuperación de preferencias pendientes al iniciar, antes de escrituras y desde el worker del widget.
- Política explícita de backup Android, separada de la exportación manual.
- Resolución compartida del tiempo civil, cambios de zona con proceso vivo, 24:00 como fin.
- Segmentación de la gráfica en las transiciones de horario estacional.
- Refresco del widget ante cambios temporales y diagnóstico sin contenido de agenda.

No incluye cuentas, backend, sincronización, KMP, web, IA ni firma de producción.
No se han creado commits ni descartado cambios previos del usuario.

## Contrato de copia completa v1

```text
format: "blocknize-backup"
formatVersion: 1
databaseVersion: 5
appVersion: "1.0"
exportedAt: instante ISO-8601 UTC
data: objeto con las 12 tablas funcionales
preferences: { accent: entero ARGB opaco, widgetOpacity: 0..100 }
```

Las tablas conservan todos sus campos, identificadores, nulos y secuencias originales.
Las revisiones y snapshots se conservan como JSON, sin reinterpretarlos como operaciones nuevas.
El orden de filas exportadas es el de su clave primaria. Los eventos se insertan con su secuencia.
Las referencias históricas sin entidad editable vigente son válidas cuando el modelo de Room las permite.
No se exporta la tabla técnica de recuperación: se termina una recuperación pendiente antes de exportar.

Límites iniciales: 32 MiB de UTF-8 y 100.000 filas por tabla. La copia se procesa en memoria;
antes de elevar estos límites hay que medir memoria y diseñar un formato por streaming.
La versión del formato es independiente de Room, pero esta primera implementación admite
únicamente el esquema de copia 5. Versiones futuras deben añadir conversión explícita.

### Privacidad

El JSON manual **no está cifrado** y contiene agenda e historial. Usar un destino privado.
No contiene WorkManager, cachés, IDs del launcher ni estado transitorio de pantallas.
La transferencia de una plantilla continúa siendo una función distinta (`blocknize-template`),
que crea una copia con identidades nuevas y no sustituye toda la base.

## Flujo de restauración y recuperación

1. Selección mediante Storage Access Framework y lectura acotada fuera del hilo principal.
2. Comprobación de formato, versiones, tablas y tipos exactos de columnas.
3. Validación de horarios, fechas, zonas, snapshots y acciones conocidas.
4. Inserción en Room temporal: restricciones únicas y claves foráneas; coherencia de plantilla/revisión.
5. Resumen y confirmación explícita. La interfaz ofrece exportar primero los datos actuales.
6. Nueva validación antes de aplicar; bloqueo compartido con las escrituras de la aplicación.
7. Transacción Room: borrar en orden inverso de dependencias, insertar en orden de dependencias
   y guardar las preferencias pendientes en la misma transacción.
8. Aplicar acento/opacidad juntos en una edición de DataStore.
9. Eliminar el marcador únicamente después del éxito de DataStore y solicitar refresco del widget.

Room y DataStore no tienen una transacción distribuida. Si el proceso muere antes del commit de
Room, se conserva la base anterior. Si muere después, la base nueva y el marcador permanecen.
El siguiente coordinador aplica de nuevo las preferencias de forma idempotente. También se
recupera el marcador antes de cualquier escritura desde AppViewModel para no sobrescribir
posteriormente ajustes nuevos con preferencias antiguas pendientes.

Las transacciones de lectura de agenda evitan observar una mezcla de filas previas y restauradas.
La coordinación de exportación y preferencias cubre las rutas actuales de escritura de la app;
las futuras rutas de escritura deberán respetar `LocalDataAccess`.

## Reglas temporales

- Los horarios son civiles locales, no minutos reales transcurridos desde la medianoche zonificada.
- `24:00` solo se admite como fin y representa la siguiente medianoche local.
- No se añaden bloques que crucen medianoche.
- Una hora inexistente se desplaza hacia delante y una hora ambigua usa el offset anterior,
  siguiendo `java.time.LocalDateTime.atZone`.
- Un intervalo colapsado por el salto primaveral nunca produce duración negativa. Una temporal
  o sustitución nueva para esa fecha se rechaza; una recurrencia ya existente puede quedar
  sin duración efectiva ese día y no se dibuja como ocupación en la gráfica.
- La gráfica divide los intervalos en cada cambio de offset para no perder tramos reales cuando
  la hora local retrocede. La escala sigue siendo civil de 24 h: no es una línea de tiempo de 25 h.
- El total de la gráfica sigue siendo la unión del tiempo civil dibujado, no una estadística de
  duración real que cuente dos veces la hora repetida.
- No se reescriben zonas ni instantes históricos guardados.
- Se conserva la restricción de edición cuando el origen de una excepción está en el pasado.
  La interfaz explica este límite. Restaurar no recrea bloques eliminados ni activa plantillas.

## Widget y backup Android

El worker reintenta fallos y propaga correctamente la cancelación. Sus logs contienen tipo
de error, intento y siguiente transición, no títulos, descripciones ni mensajes SQLite.
La siguiente medianoche se usa como transición de reserva cuando no hay más bloques.
Los cambios de hora/zona/fecha, reinicio y actualización de paquete solicitan reprogramación
si hay widgets instalados. La actividad observa también cambios temporales mientras está visible.

Los XML de backup incluyen solo `blocknize.db`, sus archivos auxiliares SQLite y
`datastore/appearance.preferences_pb`. Se excluyen por omisión WorkManager y otros datos.
Android 12+ exige capacidades de cifrado para la copia en nube; la transferencia de dispositivo
tiene sus propias reglas. La disponibilidad, cuota y ejecución del backup dependen de Android
y de su transporte; estas reglas no demuestran una restauración real del sistema.

WorkManager no garantiza exactitud al minuto: Doze, ahorro y fabricantes pueden retrasarlo.

## Verificación automatizada

Comandos de referencia:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug :app:assembleRelease
```

Pruebas nuevas:

- `FullBackupRepositoryTest`: equivalencia de las 12 tablas, agenda e historial, sustitución e
  idempotencia, errores de formato/tipos/relaciones/snapshots/secuencias, rollback real mediante
  trigger de fallo, recuperación de preferencias incluso cerrando/reabriendo Room y reactividad.
- `AppDatabaseMigrationTest`: rutas 1/2/3/4 → 5, conservación de completados y excepciones v4.
- `CivilTimeTest`: 24:00, saltos de primavera, hora repetida y reloj sensible a cambios de zona.
- `WeeklyScheduleLayoutTest`: intervalos históricos que atraviesan ambos cambios estacionales.
- `BackupSettingsTest`: controles de copia completa y aviso de privacidad.

### Resultados efectivos

Ejecutado con éxito:

```powershell
.\gradlew.bat --no-configuration-cache :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:connectedDebugAndroidTest :app:lintDebug :app:assembleRelease
```

- **31 pruebas unitarias**, sin fallos, errores ni omitidas.
- **57 pruebas instrumentadas**, sin fallos, errores ni omitidas, en `emulator-5554`,
  AVD `Medium_Phone_API_36.1`, Android 16.
- Compilación Debug y Release correctas. Release es un APK **sin firma de producción**.
- Lint: **0 errores, 33 advertencias y 4 sugerencias**. No se han actualizado dependencias
  ni eliminado recursos solo para silenciar avisos. Persisten los avisos KSP de índices/clave
  primaria del snapshot embebido de excepciones y la opción experimental de Gradle.
- Esquema `app/schemas/com.blocknize.app.data.AppDatabase/5.json` generado por Room.

Artefactos y evidencia:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`
- `app/build/reports/lint-results-debug.html`

La primera comprobación necesitó descargar Gradle y componentes SDK; no constituye una
batería de referencia del árbol original anterior a los cambios de esta fase. Hubo intentos
intermedios fallidos por bloqueos de caché entre daemons. Se detuvieron esos procesos y se
ejecutó la comprobación final en una única invocación, sin caché de configuración.

## Pendientes que impiden certificar H1

- Restauración real de Auto Backup y transferencia entre dispositivos.
- Probar el selector de documentos con proveedores reales, cancelación, falta de espacio y
  una copia exportada/restaurada desde la interfaz de extremo a extremo.
- Cierre forzado real del proceso en las distintas etapas; las pruebas automatizadas inyectan
  fallos y reabren Room, pero no equivalen a matar el proceso Android.
- Dispositivo físico: reinicio, cambios de hora/zona, pantalla apagada, Doze y launcher real.
- Recorrer guardar/modificar/mover/omitir/restaurar desde Inicio y ambos calendarios hasta Registro y widget.
- Validar Release en dispositivo y, antes de distribuir, configuración de firma/versionado.
- Medir grandes historiales en dispositivos de memoria limitada.

No declarar H1 completado mientras estas validaciones críticas sigan pendientes.
