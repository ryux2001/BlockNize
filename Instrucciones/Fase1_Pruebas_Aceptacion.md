# Fase 1 — Entregable y pruebas de aceptación

Fecha de revisión: 12 de noviembre de 2026.

La implementación de consolidación Android ya está registrada en el commit
`df32003` y se conserva en el árbol actual. El detalle técnico y los resultados
anteriores están en [Fase1_Consolidacion_Android.md](Fase1_Consolidacion_Android.md).
NestJS y Neon corresponden a la fase 2 del plan general.

## Instalación

Compilar desde la raíz del repositorio:

```powershell
.\android\gradlew.bat -p android :app:assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
Instalar como actualización de la aplicación existente, conservando su firma.
Antes de probar una sustitución de datos, exportar una copia completa y guardarla
fuera del almacenamiento de la aplicación. El JSON contiene datos sin cifrar.

## Prueba manual reproducible

Registrar modelo del móvil, versión de Android, versión instalada y fecha.
Marcar cada caso solo después de comprobar el resultado en el dispositivo.

| Caso | Pasos | Resultado esperado | Estado |
|---|---|---|---|
| Actualización | Con datos existentes, instalar el APK sin desinstalar | Se conservan plantillas, temporales, excepciones, completados e historial | Pendiente |
| Datos de referencia | Crear una plantilla, activarla, añadir una temporal, completar una ocurrencia y mover u omitir otra futura | Inicio, calendario y Registro reflejan las acciones | Pendiente |
| Copia completa | En Ajustes, usar «Exportar copia completa» y guardar con el selector de documentos | Se genera un JSON accesible fuera de la app | Pendiente |
| Restauración | Anotar agenda, Registro, acento y opacidad; exportar; cambiar datos; seleccionar la copia y confirmar «Sustituir y restaurar» | Vuelven los datos y preferencias originales, sin mezclar los cambios posteriores | Pendiente |
| Repetición | Restaurar dos veces la misma copia | No aparecen duplicados ni eventos nuevos de usuario | Pendiente |
| Cancelación | Cancelar el selector y después cancelar la confirmación de restauración | No cambian los datos | Pendiente |
| Archivo incorrecto | Intentar restaurar un JSON de plantilla y un JSON truncado | Se muestra un error y se conservan los datos actuales | Pendiente |
| Reinicio | Cerrar y volver a abrir la app después de restaurar; reiniciar el móvil | Persisten los datos y preferencias recuperados | Pendiente |
| Fin del día | Crear un bloque que termine a las 24:00 | Termina en la medianoche siguiente sin duración negativa | Pendiente |
| Zona y hora | Cambiar la zona y hora del sistema con la app abierta y con un widget instalado | Se actualizan agenda y widget; el historial guardado conserva su identidad | Pendiente |
| Historial | Intentar editar una excepción cuyo origen ya pasó | La app delimita la operación y explica la restricción | Pendiente |
| Widget | Cambiar tareas y completados, apagar pantalla y probar tras reiniciar | El widget converge con la agenda; registrar retrasos por ahorro de batería | Pendiente |
| Backup Android | En un dispositivo de pruebas, ejecutar backup y restauración mediante el transporte Android disponible | Se recuperan base y apariencia sin depender del JSON manual | Pendiente |

La prueba de backup Android y transferencia necesita un entorno de pruebas con
transporte compatible; no se acredita únicamente revisando sus reglas XML.
No desinstalar una instalación con datos únicos para realizar esta comprobación.

## Registro de incidencias

Para cada fallo anotar: caso, pasos exactos, resultado esperado, resultado real,
modelo/Android, zona horaria y si se reproduce tras reiniciar. No adjuntar copias
con datos personales a incidencias públicas.

## Criterio de aceptación

H1 permanece pendiente hasta validar recuperación real y flujos principales en
dispositivo físico. Las pruebas automatizadas complementan estas comprobaciones;
no sustituyen las del selector de documentos, backup del sistema y launcher real.

## Verificación de esta revisión

El 12/11/2026 se comprobó que no hay dispositivos conectados mediante ADB.
Se intentó repetir unitarias, compilación Debug, compilación de pruebas
instrumentadas y lint. Gradle 9.4.1 se descargó correctamente, pero la ejecución
con acceso a red no llegó a producir resultados antes de interrumpirla.
La comprobación posterior en modo offline identificó dependencias ausentes en
caché: Android Gradle Plugin/builder 9.2.1, Kotlin Gradle Plugin 2.2.10,
bundletool 1.18.3, protos 32.2.1 y bcprov 1.79.
Por tanto, esta revisión no acredita una nueva compilación ni pruebas superadas.
El APK local existente está fechado el 10/11/2026; no se ha regenerado hoy.
