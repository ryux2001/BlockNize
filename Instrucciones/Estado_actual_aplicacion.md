# BlockNize — Informe del estado actual de la aplicación

**Fecha del informe:** 3 de noviembre de 2026  
**Versión declarada:** `1.0` (`versionCode = 1`)  
**Identificador Android:** `com.blocknize.app`  
**Alcance:** código fuente del proyecto, configuración, pruebas y funcionalidades disponibles.

> Este informe describe la implementación existente, no únicamente los objetivos del documento de desarrollo. Una función implementada o una prueba compilada no equivale a una función validada visualmente en un dispositivo. Las recomendaciones siguientes no implican cambios ya realizados.

## 1. Resumen ejecutivo

BlockNize dispone de una base funcional de organización personal por bloques horarios. Permite gestionar varias semanas reutilizables, mantener una única semana activa, añadir tareas temporales, registrar finalizaciones y consultar programación e historial.

La aplicación es Android nativa, está escrita en Kotlin y utiliza Jetpack Compose para sus pantallas. Los datos se almacenan localmente en Room/SQLite y las preferencias visuales en DataStore. No existe un backend propio ni se necesita una cuenta para utilizar las funciones implementadas.

Los cambios más recientes son:

1. Fondo del widget negro puro con opacidad configurable desde la aplicación.
2. Programación de refresco del widget cada 15 minutos, con actualización del trabajo previamente registrado.
3. Nueva vista semanal gráfica en Calendario, conservando la vista mensual.

**Estado verificable:** la compilación Debug es correcta, las 13 pruebas unitarias registradas pasan y las pruebas instrumentadas compilan. No se ha ejecutado la batería instrumentada durante la preparación de este informe. No debe considerarse una versión certificada para producción solo por estos resultados.

## 2. Plataforma y tecnologías

| Elemento | Configuración actual |
|---|---|
| Plataforma | Android nativo |
| Lenguaje | Kotlin `2.2.10` |
| Interfaz de la aplicación | Jetpack Compose y Material 3 |
| Compose BOM | `2026.02.01` |
| Persistencia principal | Room `2.7.2` sobre SQLite |
| Preferencias | Preferences DataStore `1.1.7` |
| Widget | Jetpack Glance `1.1.1` |
| Trabajo programado | WorkManager `2.10.1` |
| Procesamiento de Room | KSP `2.2.10-2.0.2` |
| Android Gradle Plugin | `9.2.1` |
| Android mínimo | API 28, Android 9 |
| SDK objetivo | API 36 |
| SDK de compilación | API 36, nivel menor 1 |
| Compatibilidad de código Java | Java 11; no implica que Gradle deba ejecutarse con Java 11 |
| Intercambio | JSON propio mediante `org.json` |
| Selección de archivos | Storage Access Framework de Android |

Las versiones están centralizadas en `gradle/libs.versions.toml`. La configuración Android y las dependencias del módulo se encuentran en `app/build.gradle.kts`.

## 3. Arquitectura actual

### 3.1. Flujo de información

```text
MainActivity
  └── BlockNizeTheme
       └── BlockNizeApp y pantallas Compose
            └── AppViewModel
                 ├── TemplateRepository
                 │    └── AppDatabase / AppDao / SQLite
                 └── AccentStore / DataStore

CurrentTaskWidget
  ├── TemplateRepository / Room
  └── AccentStore / DataStore

WidgetRefreshWorker
  ├── Actualiza todas las instancias del widget
  └── Programa el siguiente inicio o fin de bloque
```

El widget lee el repositorio directamente: no necesita que una pantalla ni el ViewModel permanezcan abiertos.

### 3.2. Organización de archivos principales

Las rutas Kotlin siguientes son relativas a `app/src/main/java/com/blocknize/app/`:

| Archivo | Responsabilidad |
|---|---|
| `MainActivity.kt` | Entrada de la aplicación, tema, refresco del reloj y comprobación de programación del widget al volver a la app |
| `AppViewModel.kt` | Estado compartido, observación de datos, operaciones de usuario y solicitud de refresco del widget |
| `data/AppDatabase.kt` | Entidades, consultas DAO, base de datos y migraciones |
| `data/TemplateRepository.kt` | Semanas, bloques, tareas temporales, completados y reconstrucción histórica |
| `data/TemplateTransfer.kt` | Codificación, lectura y validación de plantillas JSON |
| `data/AccentStore.kt` | Persistencia del acento y de la opacidad del widget |
| `ui/BlockNizeApp.kt` | Navegación, Inicio, calendario mensual, Registro, editor, formularios y Apariencia |
| `ui/WeeklyScheduleScreen.kt` | Pantalla y cuadrícula semanal gráfica |
| `ui/WeeklyScheduleLayout.kt` | Posiciones, distribución de solapes y cómputo de minutos ocupados |
| `ui/theme/Theme.kt` | Esquema AMOLED y adaptación del acento |
| `widget/CurrentTaskWidget.kt` | Selección de tarea, presentación, anillo, acciones y programación del widget |

### 3.3. Valoración

La separación entre persistencia, repositorio, estado y presentación es suficiente para seguir evolucionando el MVP. La vista semanal ya separa su cálculo de distribución de los componentes visuales, lo que permite probarlo sin Android.

No obstante, muchas pantallas siguen concentradas en `BlockNizeApp.kt`, el ViewModel es compartido y la navegación se resuelve con estados locales, no con un grafo de Navigation Compose. No existe una capa independiente de casos de uso ni un contenedor formal de inyección de dependencias. Son decisiones de simplicidad actuales, no fallos de compilación.

## 4. Funcionalidades disponibles

### 4.1. Inicio / Hoy

- Fecha actual en español y nombre de la semana activa.
- Lista cronológica de bloques semanales y tareas temporales del día.
- Horas de inicio y fin, título, descripción y etiqueta.
- Estados: próxima, en curso, finalizada pendiente y completada.
- Progreso temporal de las actividades en curso.
- Casilla para marcar o desmarcar una ocurrencia como completada.
- Acceso para crear tareas temporales y editar o eliminar las vigentes.
- Estado vacío cuando no hay actividades.
- Conservación de completados anteriores que ya no forman parte de la programación vigente.

El reloj del ViewModel se refresca cada 30 segundos y al reanudar la actividad. Este mecanismo pertenece a la app: no es el temporizador del widget.

### 4.2. Gestión de semanas y bloques

- Crear semanas independientes y listarlas con su número de bloques.
- Renombrar una semana.
- Seleccionar una semana para editar sin activarla automáticamente.
- Activar una semana mediante confirmación.
- Mantener una sola selección activa.
- Editar de lunes a domingo y seleccionar varios días para un bloque.
- Crear, editar y eliminar bloques.
- Introducir título, descripción opcional, etiqueta opcional y horario.
- Seleccionar horas con el selector de Material 3 en formato de 24 horas.
- Avisar de solapes y permitir conservar las actividades coincidentes.

Guardar una modificación de la semana activa tiene efecto inmediato y registra una nueva revisión cuando cambia el contenido. Editar una inactiva no modifica la agenda vigente.

**No disponibles actualmente en la interfaz:** duplicación directa, eliminación o archivado de semanas, y desactivación explícita para volver a un estado sin semana activa.

### 4.3. Tareas temporales

- Actividades para una fecha concreta, independientes de las semanas.
- Creación, edición y eliminación desde Inicio.
- Título, descripción, etiqueta y franja horaria.
- Fecha mediante entrada `AAAA-MM-DD`.
- Validación para no programar en fechas pasadas.
- Advertencia de coincidencia con otras actividades.
- Registro de eventos de modificación y eliminación para conservar el historial efectivo.

Una temporal no se incorpora a la plantilla semanal y no desaparece por activar otra semana. Actualmente una temporal coincidente no sustituye automáticamente al bloque semanal: ambos se conservan.

### 4.4. Calendario mensual

- Cuadrícula mensual, selección de día y navegación entre meses.
- Botón Hoy.
- Lista de actividades de la fecha elegida.
- Resumen de completadas, interrumpidas y total de actividades.
- Estados de carga, error y reintento.
- Reconstrucción histórica para hoy y fechas anteriores; proyección de la programación activa para fechas futuras.

La consulta mensual se carga al seleccionar fecha o reintentar. No dispone del mismo flujo reactivo de invalidación de toda la semana que la vista gráfica nueva.

### 4.5. Calendario semanal gráfico

Acceso: **Calendario → Semana**. La vista Mes sigue disponible.

Características implementadas:

- Semana de lunes a domingo.
- Navegación por semanas y regreso a Hoy.
- Rango de fechas visible, incluso al cruzar meses o años.
- Cuadrícula completa de 24 horas con referencias horarias.
- Columnas diarias y cabecera que se mantiene al desplazar verticalmente el horario.
- Desplazamiento horizontal sincronizado entre cabecera y bloques.
- Tres días previstos como visibles en anchos inferiores a 700 dp; siete en anchos superiores o iguales a ese umbral. Hay un ancho mínimo por columna de 92 dp.
- Altura proporcional a la duración: 1,2 dp por minuto, equivalente a 72 dp por hora.
- Distribución de actividades solapadas en carriles dentro del mismo día.
- Día actual destacado y línea de hora actual.
- Indicador de completada y estados textuales según el espacio disponible.
- Detalle al pulsar un bloque.
- Total diario de tiempo ocupado mediante unión de intervalos: los solapes no suman dos veces el mismo tiempo.
- Carga reactiva al invalidarse las tablas relevantes de Room.
- Estados de carga, error, reintento y semana vacía.

**Política temporal:**

| Fecha | Datos representados |
|---|---|
| Días anteriores a hoy | Historial reconstruido con intervalos efectivos |
| Hoy | Programación vigente, excluyendo los completados históricos ajenos al plan actual |
| Días futuros | Proyección de la selección activa más temporales |

La actividad anterior sustituida de hoy se consulta en Registro, evitando mezclar versiones en la columna actual.

La gráfica es de consulta: no permite crear por pulsación en un hueco, arrastrar, redimensionar ni editar o completar directamente desde el detalle. No incorpora cronómetro, zoom ni colores independientes por etiqueta.

### 4.6. Registro

- Selección de fecha mediante calendario mensual.
- Agrupación de completadas, interrumpidas, pendientes y en curso.
- Horarios originales y tramo efectivo cuando son diferentes.
- Referencia a la semana de origen cuando está disponible.
- Acción para deshacer una finalización.
- Mensaje específico para fechas futuras.

El registro no se limita a consultar la plantilla activa de hoy: utiliza eventos y revisiones para conservar la programación que estuvo vigente.

### 4.7. Etiquetas

Las etiquetas se introducen desde los formularios y se reutilizan mediante consultas por nombre. Se muestran en las actividades y se incluyen en las plantillas exportadas.

No hay una pantalla propia para administrar, renombrar o eliminar etiquetas, ni asignación de colores individuales por etiqueta.

## 5. Apariencia y personalización

### 5.1. Tema de la aplicación

- Tema oscuro fijo, sin depender del modo claro/oscuro del dispositivo.
- Fondo principal negro puro `#000000`.
- Superficies elevadas en grises muy oscuros.
- Texto principal blanco y secundario gris.
- Acento inicial violeta `#A78BFA`.
- Ocho muestras: violeta, celeste, turquesa, naranja, verde, rosa, amarillo y azul.
- Selector personalizado con matiz, saturación, brillo y entrada hexadecimal.
- Vista previa, Aplicar y Cancelar para el color personalizado.
- Restablecimiento del violeta predeterminado.

El color elegido se conserva opaco en DataStore. Para el tema de la app, los colores con luminancia baja se aclaran en los roles de interfaz; el valor persistido no se sustituye. Esta adaptación es una heurística y no una certificación exhaustiva de accesibilidad.

### 5.2. Opacidad del widget

Acceso: **Configuración → Apariencia → Widget**.

- Control de 0 % a 100 % con porcentaje visible.
- 0 % significa fondo totalmente transparente.
- 100 % significa fondo negro puro sólido.
- Valor inicial: 30 % de opacidad, equivalente a 70 % de transparencia.
- Vista previa sobre un fondo de ejemplo.
- Botón Aplicar; mover el control no guarda por sí solo.
- Preferencia `widget_background_opacity` en el DataStore `appearance`.
- Aplicación global a todas las instancias del widget.
- Solo el fondo recibe transparencia: el texto y el anillo mantienen su opacidad.

La vista previa es ilustrativa: no lee el fondo real del launcher ni sustituye la validación en pantalla de inicio.

## 6. Estado del widget

### 6.1. Presentación y selección de tarea

El widget utiliza Glance con `SizeMode.Exact`. Está declarado para pantalla de inicio, con tamaño objetivo de 4 × 2 celdas, mínimos de 250 × 110 dp y redimensionamiento horizontal y vertical.

Muestra:

- Marca BLOCKNIZE y acción Actualizar.
- Título de la tarea actual.
- Horario y etiqueta, si existe.
- Anillo con porcentaje temporal transcurrido.
- Aviso `+N más en curso` cuando coinciden actividades.
- Sin tarea ahora y próxima actividad de hoy cuando no hay una en curso.

Cuando hay varias actividades actuales, prioriza la de inicio más reciente y, a igualdad de inicio, ordena por título. Tocar el cuerpo abre la aplicación sin navegar a un bloque concreto.

El anillo se dibuja como un bitmap de 256 × 256 mediante Canvas. No es una animación autónoma: su porcentaje permanece en el último valor dibujado hasta el siguiente refresco.

### 6.2. Actualizaciones automáticas

| Mecanismo | Implementación |
|---|---|
| Periódico | WorkManager cada 15 minutos |
| Actualización de trabajo existente | `ExistingPeriodicWorkPolicy.UPDATE` |
| Cambios guardados desde la app | Solicitud de trabajo inmediato tras la operación |
| Botón Actualizar | Solicitud de trabajo inmediato |
| Inicio o fin de actividad | Trabajo de una sola ejecución para la siguiente transición encontrada entre hoy y mañana |
| Reanudación de la aplicación | Comprobación de widgets instalados, programación periódica y solicitud de refresco |
| Alta o actualización del widget | Registro del trabajo periódico y solicitud de refresco |

El trabajo de transición tiene una demora mínima de un minuto. Si una ejecución falla, el worker solicita reintento. El trabajo periódico también se asegura dentro del worker para migrar programaciones anteriores.

**Restricción esencial:** WorkManager no es un reloj exacto. Doze, restricciones del fabricante y ahorro de batería pueden retrasar tanto el refresco de 15 minutos como el de transición. El cambio de intervalo no demuestra por sí solo que se hayan eliminado todos los retrasos observados por el usuario.

### 6.3. Limitaciones detectables en el código

- La selección de tarea actual no excluye explícitamente las actividades completadas ni las ocurrencias históricas incorporadas por `getAgenda`.
- La próxima actividad se busca solo en el día actual, no en mañana.
- No hay configuración independiente por instancia.
- No se pueden completar tareas desde el widget.
- El acento se usa directamente, sin la adaptación de luminancia del tema Compose: colores muy oscuros pueden perder visibilidad.
- La transparencia baja puede reducir el contraste sobre fondos claros o con mucho detalle.
- No hay registro diagnóstico específico de la excepción que provoca `Result.retry()`.
- La puntualidad de actualización, comportamiento del launcher y recuperación ante restricciones requieren pruebas de larga duración.

## 7. Persistencia y modelo temporal

### 7.1. Base de datos

Nombre: `blocknize.db`. Versión de esquema: **3**, con exportación de esquemas y migraciones explícitas `1 → 2` y `2 → 3`.

| Tabla | Contenido |
|---|---|
| `routine_templates` | Semanas guardadas |
| `task_definitions` | Título, descripción y etiqueta de tareas semanales |
| `schedule_blocks` | Días e intervalos de las reglas semanales |
| `tags` | Etiquetas reutilizables |
| `active_template_selection` | Única selección activa y revisión asociada |
| `template_revisions` | Copias inmutables del contenido de las semanas |
| `schedule_change_events` | Activaciones y ediciones efectivas |
| `one_off_tasks` | Estado editable de las tareas temporales |
| `one_off_schedule_events` | Historial de modificaciones y bajas de temporales |
| `completion_records` | Finalizaciones por ocurrencia y datos de referencia |

Las operaciones principales de escritura y los cambios de programación utilizan transacciones. Las fechas de recurrencia se construyen con hora local; las activaciones y finalizaciones se registran como instantes y los eventos guardan zona horaria.

La selección activa tiene una clave singleton y claves foráneas. En la creación de la base se añaden triggers para impedir identificadores singleton distintos de 1.

### 7.2. Historial

El repositorio conserva revisiones y corta los intervalos en los instantes en que cambia la programación. Puede representar una actividad que estuvo vigente solo durante parte de su horario y diferenciarla de otra posterior, incluso al volver a una plantilla anterior.

Los completados se asocian a ocurrencias, no a toda la definición recurrente. Completar una actividad de un lunes no completa automáticamente su repetición del martes.

La migración de temporales antiguas a eventos toma como referencia el instante de migración porque no conoce su fecha real de creación. No inventa retroactivamente intervalos históricos previos; los completados con snapshots siguen siendo una fuente de conservación.

### 7.3. Preferencias y copias de seguridad

DataStore conserva `accent_argb` y `widget_background_opacity`, separados de los datos de agenda.

El manifiesto permite el backup del sistema Android y contiene referencias a sus reglas. Eso no equivale a una función propia de copia completa, restauración manual o sincronización entre dispositivos.

## 8. Importación y exportación JSON

### 8.1. Funciones disponibles

- Importar desde Bloques → Importar JSON mediante el selector Android.
- Exportar desde el menú de una semana.
- Previsualizar nombre, cantidad de bloques, días con actividad y solapes.
- Importar como copia independiente e inactiva, con identificadores nuevos.
- Reutilizar etiquetas existentes por nombre.
- Mantener la semana activa, las tareas temporales y el historial existentes.

Se exporta la plantilla semanal, no toda la aplicación. El archivo no incluye finalizaciones, temporales ni preferencias visuales.

### 8.2. Formato y validación

| Campo o límite | Valor |
|---|---|
| `format` | `blocknize-template` |
| `schemaVersion` | `1` |
| Tamaño máximo | 1.048.576 bytes |
| Cantidad máxima | 500 tareas y 500 entradas de programación |
| Nombre | Entre 1 y 80 caracteres |
| Título | Entre 1 y 200 caracteres |
| Descripción | Hasta 2.000 caracteres |
| Etiqueta | Hasta 80 caracteres |
| `daysMask` | Entero entre 1 y 127; bits de lunes a domingo |
| Inicio | Entre 0 y 1439 minutos |
| Fin | Entre 1 y 1440 minutos, posterior al inicio |

El formato actual asocia una entrada de programación por identificador de tarea; una entrada puede representar varios días. Se rechazan referencias inexistentes, identificadores duplicados y tareas sin bloque.

Ejemplo mínimo compatible:

```json
{
  "format": "blocknize-template",
  "schemaVersion": 1,
  "name": "Semana de ejemplo",
  "tasks": [
    {
      "taskId": "estudio-1",
      "title": "Estudiar",
      "description": "Repasar apuntes",
      "tag": "Estudio"
    }
  ],
  "schedule": [
    {
      "taskId": "estudio-1",
      "daysMask": 31,
      "startMinute": 540,
      "endMinute": 600
    }
  ]
}
```

En este ejemplo, `31` representa lunes a viernes, y `540–600` representa 09:00–10:00.

### 8.3. Uso con el emulador

El archivo puede colocarse en `/sdcard/Download/` y seleccionarse en Descargas desde el importador. Puede transferirse arrastrándolo al emulador o mediante Device Explorer de Android Studio.

Que un archivo esté en esa carpeta no garantiza que sea válido: debe respetar el formato de BlockNize.

## 9. Pruebas y verificación

### 9.1. Comprobación realizada para este informe

Comando ejecutado desde la raíz del proyecto:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin
```

Resultado: **BUILD SUCCESSFUL**.

- La aplicación Debug compila y genera su APK.
- Las tareas unitarias están correctas; en esta ejecución Gradle reutilizó sus resultados vigentes (`UP-TO-DATE`).
- Las pruebas instrumentadas compilan, pero este comando no las ejecuta.
- APK Debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Resultados unitarios XML: `app/build/test-results/testDebugUnitTest/`.
- Informe HTML unitario: `app/build/reports/tests/testDebugUnitTest/index.html`.

### 9.2. Resultados unitarios registrados

| Suite | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| `AccentHexTest` | 2 | 0 | 0 | 0 |
| `AgendaOccurrenceTest` | 1 | 0 | 0 | 0 |
| `WidgetOpacityTest` | 2 | 0 | 0 | 0 |
| `EffectiveRangeTest` | 2 | 0 | 0 | 0 |
| `WeeklyScheduleLayoutTest` | 6 | 0 | 0 | 0 |
| **Total** | **13** | **0** | **0** | **0** |

Cubren conversión HEX, progreso temporal, alfa del fondo negro, formatos de intervalos efectivos, inicio de semana, solapes, reutilización de carriles, intervalos cortos, fin del día y total ocupado sin duplicaciones.

### 9.3. Pruebas instrumentadas presentes

Hay **21 métodos de prueba instrumentada** en el código fuente:

| Suite | Métodos | Cobertura prevista |
|---|---:|---|
| `TemplateRepositoryTest` | 10 | Activación, revisiones, rollback, completados, temporales, historial, intercambio y flujo semanal |
| `WidgetRenderingTest` | 3 | Renderizado, anillo, márgenes, tamaños y uso de colores |
| `VisualRefinementTest` | 5 | Controles con texto grande, días, tarjetas, calendario y contraste |
| `AppDatabaseMigrationTest` | 1 | Migración de versión 2 a 3 |
| `WeeklyScheduleTest` | 1 | Bloques solapados, visibilidad y selección de actividad en la cuadrícula |
| `ExampleInstrumentedTest` | 1 | Comprobación básica del paquete |

**No se afirma que estos 21 tests hayan pasado en dispositivo en esta revisión.** Su compilación es correcta. Al consultar ADB se detectaron un emulador y un dispositivo físico; no se instalaron ni ejecutaron pruebas sobre ellos para elaborar el documento.

### 9.4. Comprobaciones pendientes

- Ejecutar la batería instrumentada sobre un dispositivo elegido explícitamente.
- Revisar la gráfica semanal en móvil, tablet, orientación horizontal y texto ampliado.
- Comprobar sincronización de desplazamientos y acceso a bloques muy cortos.
- Validar el widget con distintos fondos y launchers.
- Medir refrescos reales con pantalla apagada, Doze y ahorro de batería.
- Probar reinicio del dispositivo, cambio de hora y cambio de zona horaria.
- Verificar importación/exportación de extremo a extremo con documentos reales.
- Compilar, firmar y validar la variante Release antes de una distribución final.

## 10. Limitaciones, riesgos y deuda técnica

| Prioridad propuesta | Aspecto | Evaluación |
|---|---|---|
| Alta | Actualización real del widget | El intervalo nominal es 15 minutos, pero no hay evidencia aquí de puntualidad bajo restricciones de batería |
| Alta | Selección actual del widget | No filtra completadas e históricas explícitamente; puede diferir del criterio visual de Inicio |
| Alta | Validación de la vista semanal | Falta ejecución de pruebas instrumentadas y revisión visual en dispositivo |
| Media | Final de día a las 24:00 | El repositorio y el JSON admiten 1440; el parser del formulario y el selector de horas solo admiten 00:00–23:59 |
| Media | Bloques muy cortos o muchos solapes | Se conserva la escala, pero títulos y zonas de interacción pueden ser difíciles de utilizar |
| Media | Cambios de horario estacional | La cuadrícula usa horas locales; intervalos efectivos que retroceden en hora local pueden omitirse por la validación `fin <= inicio` del layout |
| Media | Consulta histórica creciente | La reconstrucción consulta eventos previos al fin del día; con años de historial conviene medir coste y optimizar |
| Media | Reactividad mensual y Registro | Sus cargas por fecha no tienen el mismo mecanismo reactivo semanal; revisar refresco tras modificaciones, incluido deshacer completados |
| Media | Diagnóstico | El worker reintenta sin exponer la excepción; faltan herramientas para investigar retrasos o fallos |
| Media | Accesibilidad | Hay semántica y pruebas parciales, no una auditoría completa de TalkBack, contraste y tamaños táctiles |
| Media | Concentración de UI | Conviene separar progresivamente las pantallas y formularios de `BlockNizeApp.kt` |
| Media | Límites de validación | Las restricciones de longitud del importador no son idénticas a las de creación y edición manual |
| Baja | Denominación de preferencias | `AccentStore` guarda también opacidad; un nombre de apariencia sería más representativo si crecen los ajustes |
| Baja | Organización de tests | Los directorios conservan `com/example/blocknize` aunque los paquetes Kotlin actuales son `com.blocknize.app` y subpaquetes |

En particular, el riesgo sobre horario estacional procede de lectura del algoritmo, no de una incidencia reproducida durante este informe. Debe concretarse con casos de prueba antes de decidir la corrección.

La configuración de compilación sigue mostrando la advertencia de opción experimental `android.disallowKotlinSourceSets=false`. No impide el build actual, pero debe revisarse al actualizar herramientas.

## 11. Funciones no implementadas o fuera del alcance actual

- Cuentas de usuario, backend y sincronización automática.
- Integración con Google Calendar u otros calendarios externos.
- Recordatorios y alarmas exactas.
- Cronómetro de actividades.
- Bloques que crucen medianoche.
- Planificación anticipada de qué plantilla se activará en una fecha futura.
- Duplicación, archivado y borrado de semanas mediante acciones propias.
- Administración completa de etiquetas.
- Excepciones individuales de una recurrencia sin editar la regla semanal.
- Copia completa manual y restauración de agenda, historial y preferencias.
- Edición por arrastre, zoom y creación desde huecos de la gráfica.
- Estadísticas avanzadas de cumplimiento o productividad.
- Configuración de apariencia por cada instancia de widget.
- Selector de frecuencia del widget: actualmente está fijada a 15 minutos en código.

Estos puntos no deben confundirse con requisitos ya aprobados para una próxima entrega: algunos son posibilidades de evolución.

## 12. Estado del repositorio y de entrega

La carpeta es un repositorio Git. En la revisión actual hay modificaciones sin confirmar en el repositorio y archivos nuevos correspondientes a la vista semanal y sus pruebas. Este informe no crea commits ni altera cambios de código existentes.

La entrega comprobada es Debug. No se ha comprobado en este trabajo una publicación en tienda, firma de producción, pipeline de integración continua ni batería completa sobre la variante Release.

El documento `Instrucciones/BlockNize_Plan_Desarrollo_v0.7.md` sigue siendo la referencia funcional inicial. No describe por sí solo todos los cambios recientes: este informe complementa esa referencia con el estado implementado.

## 13. Recomendaciones de siguiente iteración

1. **Validar en dispositivo lo recién añadido:** opacidad, refresco del widget y gráfica semanal.
2. **Diagnosticar la puntualidad del widget:** registrar ejecución, errores y siguiente transición, antes de prometer intervalos exactos.
3. **Unificar el criterio de actividad vigente:** decidir expresamente qué hacer con completadas, históricas y solapes en Inicio y widget.
4. **Cerrar los casos temporales de borde:** 24:00, cambios de fecha, zona horaria y horario estacional.
5. **Mejorar la reactividad del calendario mensual y Registro**, especialmente después de deshacer una finalización.
6. **Revisar usabilidad de la gráfica** con bloques pequeños, muchas coincidencias y fuente ampliada.
7. **Separar pantallas y formularios gradualmente**, sin reescribir el repositorio ni la base de datos innecesariamente.
8. **Preparar una validación Release** y registrar el estado estable en Git antes de distribución.

## 14. Conclusión

BlockNize ya cuenta con un núcleo de agenda local, recurrencias semanales, finalizaciones e historial que permite continuar desarrollando el producto sin rehacer su persistencia. El widget configurable y la gráfica semanal amplían la utilidad de ese núcleo.

La prioridad inmediata no es añadir infraestructura nueva, sino comprobar el comportamiento real en dispositivo, resolver diferencias de criterio y consolidar las pruebas temporales y visuales. La compilación correcta y las 13 pruebas unitarias registradas aportan una base de confianza, pero no sustituyen esa validación.
