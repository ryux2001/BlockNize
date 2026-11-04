# BlockNize — Informe del estado actual de la aplicación

> **Actualización posterior — Fase 1 (04/11/2026):** se han incorporado copia completa y
> restauración recuperable, Room v5 con migración 4 → 5, reglas explícitas de backup Android
> y correcciones temporales/widget. La nueva verificación supera 31 pruebas unitarias y
> 57 instrumentadas, Debug/Release y lint (0 errores). El cuerpo de este informe conserva
> la referencia previa de Room v4; consultar `Fase1_Consolidacion_Android.md` para el alcance
> actualizado y las validaciones físicas/sistema pendientes. H1 aún no está certificado.

**Fecha del informe:** 4 de noviembre de 2026\
**Versión declarada:** `1.0` (`versionCode = 1`)  
**Identificador Android:** `com.blocknize.app`  
**Alcance:** código fuente del proyecto, configuración, pruebas y funcionalidades disponibles.

> Este informe describe la implementación existente, no únicamente los objetivos del documento de desarrollo. Una función implementada o una prueba compilada no equivale a una función validada visualmente en un dispositivo. Las recomendaciones siguientes no implican cambios ya realizados.

## 1. Resumen ejecutivo

BlockNize dispone de una agenda local por bloques horarios. Permite gestionar semanas reutilizables, mantener una única semana activa, añadir tareas temporales, registrar finalizaciones, consultar programación e historial, concentrarse en la actividad actual y modificar una sola ocurrencia sin alterar su regla semanal.

La aplicación es Android nativa, está escrita en Kotlin y utiliza Jetpack Compose para sus pantallas. Los datos se almacenan localmente en Room/SQLite y las preferencias visuales en DataStore. No existe un backend propio ni se necesita una cuenta para utilizar las funciones implementadas.

Los cambios más recientes son:

1. Quinta sección Concentración: contador automático circular hasta el final del bloque, sin sesiones ni temporizadores manuales.
2. Navegación inferior en vertical y barra lateral derecha con iconos en horizontal.
3. Excepciones de ocurrencias: modificar, reprogramar, omitir y restaurar, con identidad estable e historial de eventos.
4. Room versión 4 y migración no destructiva desde las versiones anteriores.
5. Calendario mensual y Registro reactivos, además del flujo semanal existente.
6. Selector compartido de actividades actuales entre Inicio, Concentración y widget, con exclusión de completadas, históricas, interrumpidas y marcadores de excepción.

Se conservan el tema AMOLED, el widget con opacidad configurable y refresco nominal cada 15 minutos, el calendario mensual/semanal y el intercambio JSON de plantillas.

**Estado verificable:** compilación Debug correcta; última batería completa de esta sesión: **24 pruebas unitarias y 45 instrumentadas correctas**, estas últimas ejecutadas en `emulator-5554`. Para esta actualización se han revisado nuevamente código, configuración y resultados y se ha repetido la comprobación de compilación; los tests unitarios se reutilizaron como `UP-TO-DATE`. No equivale a certificación de producción ni a validación prolongada del widget.

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
| Gradle Wrapper | `9.4.1` |
| Lifecycle Compose / ViewModel Compose | `2.9.2` |
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
                 │    ├── Aplicación de excepciones e intervalos históricos
                 │    └── AppDatabase / AppDao / SQLite (v4)
                 └── AccentStore / DataStore

FocusRoute
  ├── AppViewModel / agenda compartida
  ├── FocusViewModel / SavedStateHandle (selección)
  └── Reloj civil visible cada segundo / FocusTime

CurrentTaskSelector
  └── Criterio común para Inicio, Concentración y widget

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
| `data/TemplateRepository.kt` | Semanas, bloques, temporales, excepciones, completados, flujos reactivos y reconstrucción histórica |
| `data/OccurrenceExceptions.kt` | Snapshots y aplicación de excepciones vigentes e históricas por intervalos |
| `data/TemplateTransfer.kt` | Codificación, lectura y validación de plantillas JSON |
| `data/AccentStore.kt` | Persistencia del acento y de la opacidad del widget |
| `ui/BlockNizeApp.kt` | Navegación, Inicio, calendario mensual, Registro, editor, formularios y Apariencia |
| `ui/WeeklyScheduleScreen.kt` | Pantalla y cuadrícula semanal gráfica |
| `ui/WeeklyScheduleLayout.kt` | Posiciones, distribución de solapes y cómputo de minutos ocupados |
| `ui/AppDestination.kt` | Identificadores, títulos e iconos de las cinco secciones |
| `ui/AppNavigationRail.kt` | Navegación horizontal a la derecha y acceso contextual a Configuración |
| `ui/focus/FocusScreen.kt` | Contador circular, composición adaptable, selección de tarea y pantalla encendida |
| `ui/focus/FocusViewModel.kt` | Conservación de la selección mediante `SavedStateHandle` |
| `ui/focus/FocusTime.kt` | Tiempo restante y formato `HH:mm:ss` |
| `ui/occurrence/OccurrenceActions.kt` | Menú, confirmaciones y formulario de excepciones |
| `domain/CurrentTaskSelector.kt` | Selección común de tareas actuales y próximas elegibles |
| `ui/theme/Theme.kt` | Esquema AMOLED y adaptación del acento |
| `widget/CurrentTaskWidget.kt` | Selección de tarea, presentación, anillo, acciones y programación del widget |

### 3.3. Valoración

La separación entre persistencia, repositorio, estado y presentación es suficiente para seguir evolucionando el MVP. La vista semanal ya separa su cálculo de distribución de los componentes visuales, lo que permite probarlo sin Android.

No obstante, muchas pantallas siguen concentradas en `BlockNizeApp.kt`. Hay un ViewModel compartido y otro específico de Concentración; la navegación usa destinos identificados y estado guardable, no un grafo de Navigation Compose. No existe una capa completa de casos de uso ni un contenedor formal de inyección de dependencias. Las operaciones de excepción se identifican mediante cadenas (`SKIP`, `REPLACE`, `RESTORE`) y sus snapshots se serializan en JSON.

### 3.4. Navegación y orientación

- Cinco secciones: Inicio, Calendario, Registro, Bloques y Concentración.
- Vertical: barra inferior con iconos y etiquetas según selección.
- Horizontal: barra lateral derecha con iconos sin títulos visibles y descripciones accesibles.
- La sección seleccionada se guarda al rotar o recrear la actividad.
- Apariencia y edición de semanas ocultan la navegación principal.
- Concentración no muestra cabecera. Configuración se integra en su barra lateral horizontal o en un icono compacto en vertical; el resto conserva la cabecera.

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
- Acciones «Solo esta ocurrencia» para actividades semanales elegibles.
- Marcadores de omisión y reprogramación, sin permitir completarlos como si fueran tareas pendientes.
- Las sustituciones aparecen en su fecha de destino y muestran su fecha de origen.

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
- Días pasados: reconstrucción histórica; futuro: proyección de la semana activa, temporales y sustituciones explícitas.
- Hoy: agenda vigente más tramos históricos interrumpidos que ya no están representados por la misma clave.
- Acciones de excepción desde las tarjetas semanales elegibles.

La consulta mensual recoge `observeCalendarAgenda()`: vuelve a emitir al invalidarse las tablas relevantes de Room. La pantalla también reinicia la consulta al cambiar la fecha seleccionada, reintentar o cambiar el día actual. Ya no depende únicamente de una carga puntual por fecha.

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
- Marcadores de omisión y traslado visibles, excluidos del cómputo de minutos ocupados.
- Acciones de excepción desde el detalle de una ocurrencia semanal elegible.
- Carga reactiva al invalidarse las tablas relevantes de Room.
- Estados de carga, error, reintento y semana vacía.

**Política temporal:**

| Fecha | Datos representados |
|---|---|
| Días anteriores a hoy | Historial reconstruido con intervalos efectivos |
| Hoy | Programación vigente, excluyendo los completados históricos ajenos al plan actual |
| Días futuros | Proyección de la selección activa más temporales y sustituciones explícitas |

La actividad anterior sustituida de hoy se consulta en Registro, evitando mezclar versiones en la columna actual.

La gráfica no permite crear por pulsación en un hueco, arrastrar ni redimensionar. Su detalle permite modificar, mover, omitir o restaurar una ocurrencia mediante formularios; no completar directamente. No incorpora cronómetro, zoom ni colores independientes por etiqueta. El detalle abierto se sincroniza con los datos y deja de mostrarse si su clave desaparece.

### 4.6. Registro

- Selección de fecha mediante calendario mensual.
- Agrupación de completadas, interrumpidas, pendientes y en curso.
- Horarios originales y tramo efectivo cuando son diferentes.
- Referencia a la semana de origen cuando está disponible.
- Acción para deshacer una finalización.
- Mensaje específico para fechas futuras.
- Grupo «Omitidas y reprogramadas», separado de pendientes e interrumpidas.
- Observación reactiva mediante `observeHistory()`, incluida la actualización después de deshacer una finalización.
- Referencia estable al completado aunque la clave visual del tramo histórico sea diferente.

El registro no se limita a consultar la plantilla activa de hoy: utiliza eventos y revisiones para conservar la programación que estuvo vigente.

### 4.7. Etiquetas

Las etiquetas se introducen desde los formularios y se reutilizan mediante consultas por nombre. Se muestran en las actividades y se incluyen en las plantillas exportadas.

No hay una pantalla propia para administrar, renombrar o eliminar etiquetas, ni asignación de colores individuales por etiqueta.

Las etiquetas de las excepciones son texto dentro de sus snapshots: no se insertan automáticamente en la tabla reutilizable `tags`.

### 4.8. Concentración

- Muestra una única actividad actual, su horario efectivo y el tiempo restante del bloque en formato `HH:mm:ss`.
- Contador dentro de un anillo fino que representa el tiempo transcurrido mediante el acento del tema.
- Tipografía monoespaciada y tamaño ajustado al diámetro y al escalado de fuente.
- Composición principal sin scroll: círculo a la izquierda e información/opciones a la derecha cuando hay espacio horizontal; distribución vertical en el resto de casos.
- Selector discreto entre tareas simultáneas. La clave elegida se guarda con `FocusViewModel` y `SavedStateHandle`.
- Si la selección deja de ser candidata, se muestra la primera tarea elegible; no existe una sesión manual que transferir.
- Estados «Cargando agenda…» y «Sin tarea en curso», sin contador ficticio.
- Actualización visual aproximadamente cada segundo mientras el ciclo de vida está al menos en `STARTED`; se suspende al pasar a segundo plano y se recalcula al volver.
- El tiempo se calcula desde el reloj civil y el fin programado, limitado a cero. No depende de sumar o restar ticks.
- La rotación o recreación no reinician un temporizador independiente, porque no existe.
- Opción de mantener pantalla encendida mientras la pantalla está activa, restaurando el valor previo al salir.

**No incluye:** cronómetro, duración configurable, iniciar/pausar/reanudar/reiniciar, Pomodoro, estadísticas de sesiones, alarmas, servicio permanente ni integración con la pantalla de bloqueo. Los cambios manuales del reloj afectan deliberadamente al tiempo restante del horario.

### 4.9. Excepciones y reprogramación individual

Acceso: **Solo esta ocurrencia**, en Inicio, tarjetas del calendario mensual y detalle semanal.

| Acción | Comportamiento implementado |
|---|---|
| Modificar solo esta vez | Cambiar título, descripción, etiqueta y horario manteniendo la fecha en el formulario |
| Reprogramar | Cambiar también la fecha; entrada manual `AAAA-MM-DD` y horas `HH:mm` |
| Omitir esta vez | Retirar la actividad realizable y conservar un marcador de omisión |
| Restaurar programación original | Retirar el efecto vigente de la excepción desde ese instante, sin borrar eventos ni tramos anteriores |

- La plantilla semanal no se modifica y los formularios lo indican expresamente.
- Identidad de origen: plantilla, bloque y fecha original; unicidad impuesta por índice en Room.
- La sustitución usa una clave estable `exception:<id>` aunque vuelva a editarse o trasladarse.
- Una reprogramación aparece aunque el destino no tenga programación semanal propia.
- Se conserva al activar otra semana y al editar o eliminar el bloque de origen; no se reasigna por coincidencia de nombre.
- La omisión solo suprime el origen correspondiente, no los bloques de otra plantilla.
- La sustitución coexiste con otras actividades en destino; los solapes se advierten y requieren continuar antes de guardar.
- Título obligatorio, inicio anterior al fin y sin cruce de medianoche. El formulario de excepciones admite `24:00` como fin.
- Al cambiar fecha o inicio, se rechaza un destino que ya haya comenzado. Cambiar únicamente datos o fin manteniendo el inicio permite modificar una actividad en curso.
- Se exige deshacer la finalización antes de modificar, mover, omitir o restaurar. El repositorio vuelve a leer la ocurrencia para rechazar formularios desactualizados.
- Estado vigente y evento se guardan en una transacción; después, el ViewModel solicita refresco del widget.
- Los cambios durante un bloque conservan los intervalos anteriores; una finalización posterior a restaurar pertenece al tramo restaurado y puede deshacerse desde Registro.

**Límites actuales importantes:** no se ofrecen estas acciones en ocurrencias históricas ni si la fecha visible **o la fecha original** es anterior a hoy. Una sustitución trasladada a futuro sigue existiendo cuando pasa su origen, pero entonces ya no se puede modificar ni restaurar mediante estas acciones. Restaurar retira la excepción; no recrea un bloque eliminado ni reactiva una plantilla anterior. Los marcadores de origen ofrecen restauración, no un nuevo traslado directo.

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

Utiliza `currentTasks()` y `nextTask()`, compartidos con la aplicación. Excluye completadas, históricas, interrumpidas y marcadores de omisión/reprogramación. En empates de título e inicio se ordena por clave. Concentración puede elegir manualmente otra candidata: esa preferencia no cambia la elección automática del widget.

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

Las transiciones se calculan con la agenda efectiva del repositorio y excluyen marcadores, históricas y completadas; por ello utilizan los horarios resultantes de las excepciones, no solo la regla semanal original. El XML declara `android:updatePeriodMillis="0"`: la periodicidad se delega a WorkManager.

**Restricción esencial:** WorkManager no es un reloj exacto. Doze, restricciones del fabricante y ahorro de batería pueden retrasar tanto el refresco de 15 minutos como el de transición. El cambio de intervalo no demuestra por sí solo que se hayan eliminado todos los retrasos observados por el usuario.

### 6.3. Limitaciones detectables en el código

- La próxima actividad se busca solo en el día actual, no en mañana.
- No hay configuración independiente por instancia.
- No se pueden completar tareas desde el widget.
- El acento se usa directamente, sin la adaptación de luminancia del tema Compose: colores muy oscuros pueden perder visibilidad.
- La transparencia baja puede reducir el contraste sobre fondos claros o con mucho detalle.
- No hay registro diagnóstico específico de la excepción que provoca `Result.retry()`.
- La puntualidad de actualización, comportamiento del launcher y recuperación ante restricciones requieren pruebas de larga duración.

## 7. Persistencia y modelo temporal

### 7.1. Base de datos

Nombre: `blocknize.db`. Versión de esquema: **4**, con exportación de esquemas `1.json` a `4.json` y migraciones explícitas `1 → 2`, `2 → 3` y `3 → 4`. No se configura recreación destructiva como alternativa.

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
| `occurrence_exceptions` | Estado vigente, origen único, fecha de destino, acción, snapshot JSON, zona y marcas temporales |
| `occurrence_exception_events` | Eventos ordenados con copia del estado de la excepción en cada cambio |

Las operaciones principales de escritura y los cambios de programación utilizan transacciones. Las fechas de recurrencia se construyen con hora local; las activaciones y finalizaciones se registran como instantes y los eventos guardan zona horaria.

La selección activa tiene una clave singleton y claves foráneas. En la creación de la base se añaden triggers para impedir identificadores singleton distintos de 1.

Las excepciones no tienen borrado en cascada desde los bloques editables: sobreviven con sus snapshots. El estado restaurado se conserva con acción `RESTORE`, en lugar de borrar la fila. Los eventos se insertan sin una operación DAO de actualización o borrado, pero no hay triggers que impidan alterarlos fuera de ese DAO.

### 7.2. Historial

El repositorio conserva revisiones y corta los intervalos en los instantes en que cambia la programación. Puede representar una actividad que estuvo vigente solo durante parte de su horario y diferenciarla de otra posterior, incluso al volver a una plantilla anterior.

Los completados se asocian a ocurrencias, no a toda la definición recurrente. Completar una actividad de un lunes no completa automáticamente su repetición del martes.

Las ocurrencias semanales ordinarias siguen incluyendo el evento de programación en su clave. Las excepciones no dependen exclusivamente de ella: enlazan plantilla, bloque y fecha original. Su sustitución mantiene una identidad única para completados. `completionKey` separa esa referencia de las claves visuales de los tramos históricos.

La reconstrucción aplica los intervalos de vigencia de las excepciones sobre las revisiones base y añade sus sustituciones en destino. Puede conservar varios tramos de una misma actividad y marcadores sucesivos de sus cambios. El número de filas históricas no equivale necesariamente al número de compromisos únicos.

No todas las consultas tienen la misma presentación: `getCalendarAgenda()` reconstruye también hoy como historial; `observeCalendarAgenda()` combina hoy la agenda vigente con tramos interrumpidos; `observeWeek()` usa para hoy la programación vigente; Registro consume `observeHistory()`. Estas diferencias son explícitas en el repositorio y deben respetarse al añadir consumidores.

La migración de temporales antiguas a eventos toma como referencia el instante de migración porque no conoce su fecha real de creación. No inventa retroactivamente intervalos históricos previos; los completados con snapshots siguen siendo una fuente de conservación.

### 7.3. Preferencias y copias de seguridad

DataStore conserva `accent_argb` y `widget_background_opacity`, separados de los datos de agenda.

El manifiesto permite el backup del sistema Android y contiene referencias a sus reglas. Eso no equivale a una función propia de copia completa, restauración manual o sincronización entre dispositivos.

`backup_rules.xml` y `data_extraction_rules.xml` siguen siendo configuraciones de ejemplo, sin una política propia detallada de inclusiones y exclusiones. No se ha validado aquí una restauración real del backup del sistema.

## 8. Importación y exportación JSON

### 8.1. Funciones disponibles

- Importar desde Bloques → Importar JSON mediante el selector Android.
- Exportar desde el menú de una semana.
- Previsualizar nombre, cantidad de bloques, días con actividad y solapes.
- Importar como copia independiente e inactiva, con identificadores nuevos.
- Reutilizar etiquetas existentes por nombre.
- Mantener la semana activa, las tareas temporales y el historial existentes.

Se exporta la plantilla semanal, no toda la aplicación. El archivo no incluye finalizaciones, temporales, excepciones por fecha, eventos históricos ni preferencias visuales.

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

### 9.1. Evidencia y comandos

Durante esta actualización se ha ejecutado desde la raíz:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

Resultado: **BUILD SUCCESSFUL**.

- La aplicación Debug compila y genera su APK.
- Las tareas unitarias reutilizaron resultados vigentes (`UP-TO-DATE`); sus XML registran 24 pruebas correctas.
- Las pruebas instrumentadas compilan; este comando adicional no las vuelve a ejecutar.
- APK Debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Resultados unitarios XML: `app/build/test-results/testDebugUnitTest/`.
- Informe HTML unitario: `app/build/reports/tests/testDebugUnitTest/index.html`.

La ejecución completa anterior, realizada el mismo 4 de noviembre de 2026 en esta sesión, utilizó:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug
```

Resultado: **BUILD SUCCESSFUL**, con **45 instrumentadas correctas**, sin fallos, errores ni omitidas, en `emulator-5554`, AVD `Medium_Phone_API_36.1` (Android 16). Se ha comprobado el XML `TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`, situado en `app/build/outputs/androidTest-results/connected/debug/`. Informe HTML: `app/build/reports/androidTests/connected/debug/index.html`.

Para esa batería se fijó temporalmente la orientación inicial vertical del emulador y después se restauró su rotación automática. En la consulta ADB de esta revisión aparece únicamente dicho emulador. No se ha ejecutado esta validación en un dispositivo físico ni se ha realizado un borrado manual de los datos de la aplicación.

### 9.2. Resultados unitarios registrados

| Suite | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| `AccentHexTest` | 2 | 0 | 0 | 0 |
| `AgendaOccurrenceTest` | 1 | 0 | 0 | 0 |
| `WidgetOpacityTest` | 2 | 0 | 0 | 0 |
| `EffectiveRangeTest` | 2 | 0 | 0 | 0 |
| `WeeklyScheduleLayoutTest` | 6 | 0 | 0 | 0 |
| `CurrentTaskSelectorTest` | 5 | 0 | 0 | 0 |
| `FocusTimeTest` | 4 | 0 | 0 | 0 |
| `FocusViewModelTest` | 2 | 0 | 0 | 0 |
| **Total** | **24** | **0** | **0** | **0** |

Cubren conversión HEX, progreso, alfa del fondo negro, formatos de intervalos efectivos, distribución semanal, solapes, total ocupado, elegibilidad y orden de tareas, exclusión de marcadores de excepción, tiempo restante no negativo y restauración de la selección de Concentración.

### 9.3. Pruebas instrumentadas ejecutadas

Los **45 métodos** presentes coinciden con la última batería completa aprobada:

| Suite | Métodos | Cobertura ejercitada |
|---|---:|---|
| `TemplateRepositoryTest` | 10 | Activación, revisiones, rollback, completados, temporales, historial, intercambio y flujo semanal |
| `WidgetRenderingTest` | 3 | Renderizado, anillo, márgenes, tamaños y uso de colores |
| `VisualRefinementTest` | 5 | Controles con texto grande, días, tarjetas, calendario y contraste |
| `AppDatabaseMigrationTest` | 2 | Migración 2 → 3 y rutas 1/2/3 → 4, preservando datos y validando el esquema |
| `WeeklyScheduleTest` | 1 | Bloques solapados, visibilidad y selección de actividad en la cuadrícula |
| `PackageNameTest` (`ExampleInstrumentedTest.kt`) | 1 | Comprobación básica del paquete |
| `FocusScreenTest` | 4 | Contador único, ausencia de tarea, cambio de reloj, anillo y espacio horizontal reducido con fuente ampliada |
| `FocusNavigationTest` | 3 | Recreación, barra derecha sin etiquetas, rotación y acceso a Configuración sin cabecera |
| `FocusActivityRecreationTest` | 1 | Conservación de ViewModel y selección al recrear actividad |
| `OccurrenceActionsTest` | 4 | Menús, alcance explícito, fecha editable al mover, bloqueo de completadas y confirmación de restauración |
| `OccurrenceExceptionsTest` | 11 | Motor de excepciones, identidad, intervalos, completados, rollback y reactividad |
| **Total** | **45** | **Sin fallos, errores ni omitidas** |

Las pruebas de excepciones incluyen omisión aislada, traslado a día vacío y a otro año, movimientos sucesivos con identidad estable, finalización única, supervivencia al cambio de plantilla y eliminación del bloque, prioridad de la edición puntual, exclusión del JSON semanal, restauración con tramos efectivos, deshacer el completado del tramo restaurado, rechazo de datos inválidos y formularios obsoletos, rollback de estado/evento y emisiones de mensual/Registro sin cambiar de fecha.

**Alcance de esta evidencia:** las pruebas de acciones ejercitan componentes de menú y formulario, no un recorrido completo de guardado de las cuatro acciones desde cada pantalla. Tampoco hay una prueba integral que mueva una tarea desde la UI y compruebe después el launcher real. La recreación de actividad no equivale a matar el proceso o reiniciar el dispositivo.

### 9.4. Comprobaciones pendientes

- Ejecutar la batería y recorridos reales sobre dispositivo físico y otras versiones de Android.
- Revisar la gráfica semanal en móvil, tablet, orientación horizontal y texto ampliado.
- Comprobar sincronización de desplazamientos y acceso a bloques muy cortos.
- Validar el widget con distintos fondos y launchers.
- Medir refrescos reales con pantalla apagada, Doze y ahorro de batería.
- Probar reinicio del dispositivo, cambio de hora y cambio de zona horaria.
- Verificar importación/exportación de extremo a extremo con documentos reales.
- Verificar las cuatro acciones desde Inicio y ambos calendarios, incluidos diálogos abiertos durante cambios concurrentes.
- Comprobar la propagación de excepciones al widget colocado en el launcher, no solo las consultas del repositorio.
- Probar selección de tareas solapadas, salida de Concentración y liberación de pantalla encendida en ciclos de vida reales.
- Compilar, firmar y validar la variante Release antes de una distribución final.

## 10. Limitaciones, riesgos y deuda técnica

| Prioridad propuesta | Aspecto | Evaluación |
|---|---|---|
| Alta | Actualización real del widget | El intervalo nominal es 15 minutos, pero no hay evidencia aquí de puntualidad bajo restricciones de batería |
| Alta | Cobertura de integración real | La batería del emulador pasa, pero faltan recorridos completos de excepciones desde cada pantalla hasta el widget del launcher y pruebas físicas |
| Media | Origen pasado de una sustitución futura | Se conserva el compromiso, pero UI y repositorio bloquean su edición/restauración cuando pasa la fecha original; revisar si esta restricción es la deseada |
| Media | Restauración con origen eliminado o inactivo | Retirar la excepción no recrea el bloque ni activa su semana; conviene explicarlo antes de confirmar |
| Media | Final de día a las 24:00 | Repositorio y JSON admiten 1440 y el formulario de excepciones acepta 24:00 como fin; los formularios semanales/temporales y su selector Material no lo permiten de forma equivalente |
| Media | Bloques muy cortos o muchos solapes | Se conserva la escala, pero títulos y zonas de interacción pueden ser difíciles de utilizar |
| Media | Cambios de horario estacional | La cuadrícula usa horas locales; intervalos efectivos que retroceden en hora local pueden omitirse por la validación `fin <= inicio` del layout |
| Media | Cambio de zona con proceso vivo | El repositorio de la app conserva el Clock creado con una zona; las pantallas consultan la zona actual y el widget crea un Clock nuevo. Falta validar y unificar el comportamiento si la zona cambia sin recrear el ViewModel |
| Media | Consulta histórica creciente | Se consultan eventos previos al fin del día; la agenda vigente carga todas las excepciones. Las invalidaciones vuelven a construir agendas y semanas; medir con años de datos |
| Media | Presentación de excepciones | Los marcadores ocupan espacio en la gráfica aunque no suman minutos; el total mensual cuenta filas, incluidos marcadores y tramos, no compromisos únicos |
| Media | Etiquetado de sustituciones | La operación interna REPLACE muestra el origen como «Reprogramada» incluso si solo se cambió el título; falta distinguir visualmente modificación de traslado |
| Media | Avisos de solape en temporales | El formulario antiguo no excluye explícitamente los marcadores de excepción; puede advertir coincidencias con actividades ya omitidas o trasladadas |
| Media | Diagnóstico | El worker reintenta sin exponer la excepción; faltan herramientas para investigar retrasos o fallos |
| Media | Accesibilidad | Hay semántica y pruebas parciales, no una auditoría completa de TalkBack, contraste y tamaños táctiles |
| Media | Concentración de UI | Conviene separar progresivamente las pantallas y formularios de `BlockNizeApp.kt` |
| Media | Límites de validación | Las restricciones de longitud del importador no son idénticas a las de creación y edición manual |
| Media | Recuperación de formularios | Varios editores y diálogos antiguos dependen de `remember`; no hay garantía general de conservar todos los borradores al rotar. Concentración y el formulario de excepciones usan estado guardable para sus datos principales |
| Media | Política de backup | Se permite el backup Android, pero sus XML de reglas siguen siendo ejemplos; falta probar el conjunto de agenda, eventos y preferencias restaurado |
| Baja | Denominación de preferencias | `AccentStore` guarda también opacidad; un nombre de apariencia sería más representativo si crecen los ajustes |
| Baja | Organización de tests | Los directorios conservan `com/example/blocknize` aunque los paquetes Kotlin actuales son `com.blocknize.app` y subpaquetes |

Los riesgos de horario estacional, cambio de zona, coste creciente y solapes de temporales proceden de lectura del código, no de incidencias reproducidas durante esta actualización. Deben concretarse con pruebas antes de presentar una corrección como validada. La reactividad mensual/Registro y el filtrado de candidatas del widget del informe anterior ya están implementados y no se mantienen aquí como trabajo pendiente.

La configuración de compilación sigue mostrando la advertencia de opción experimental `android.disallowKotlinSourceSets=false`. No impide el build actual, pero debe revisarse al actualizar herramientas.

KSP también advirtió en la compilación completa que los índices y la clave primaria de la entidad de excepción se ignoran al incrustarla como snapshot en la entidad de evento. Los eventos tienen su propia clave e índices; las migraciones y el esquema v4 se validaron correctamente. No se debe confundir esa advertencia con un fallo de migración.

## 11. Funciones no implementadas o fuera del alcance actual

- Cuentas de usuario, backend y sincronización automática.
- Integración con Google Calendar u otros calendarios externos.
- Recordatorios y alarmas exactas.
- Cronómetro, cuenta atrás configurable y sesiones manuales: retirados del alcance de Concentración, no requisitos pendientes de su versión actual.
- Bloques que crucen medianoche.
- Planificación anticipada de qué plantilla se activará en una fecha futura.
- Duplicación, archivado y borrado de semanas mediante acciones propias.
- Administración completa de etiquetas.
- Copia completa manual y restauración de agenda, historial y preferencias.
- Edición por arrastre, zoom y creación desde huecos de la gráfica.
- Estadísticas avanzadas de cumplimiento o productividad.
- Configuración de apariencia por cada instancia de widget.
- Selector de frecuencia del widget: actualmente está fijada a 15 minutos en código.

Estos puntos no deben confundirse con requisitos ya aprobados para una próxima entrega: algunos son posibilidades de evolución.

## 12. Estado del repositorio y de entrega

La carpeta es un repositorio Git con cambios sin confirmar y archivos nuevos de Concentración, navegación, excepciones, esquema v4 y pruebas, además de modificaciones de integración. Se ha contrastado el árbol de trabajo real, no solo el último commit. Esta tarea actualiza únicamente este informe; no modifica código fuente ni crea commits.

La entrega comprobada es Debug. No se ha comprobado en este trabajo una publicación en tienda, firma de producción, pipeline de integración continua ni batería completa sobre la variante Release.

No se ha encontrado una configuración de GitHub Actions en `.github/`. La variante Release declara la optimización desactivada y no hay una configuración propia de firma de producción en `app/build.gradle.kts`; no se ha validado aquí el proceso de publicación.

`Instrucciones/BlockNize_Plan_Desarrollo_v0.7.md` es la referencia funcional inicial. `Instrucciones/Plan_Concentracion_y_Excepciones.md` registra el plan y cambios de alcance; sus apartados históricos que indican excepciones pendientes no describen ya el código actual. Este informe documenta lo existente, sin actualizar esos otros documentos.

## 13. Recomendaciones de siguiente iteración

1. **Validar recorridos completos en dispositivo físico:** excepciones desde Inicio y ambos calendarios, Registro, Concentración y widget real.
2. **Revisar el contrato de edición de sustituciones futuras con origen pasado** y explicar el resultado de restaurar si el bloque ya no existe o su plantilla está inactiva.
3. **Diagnosticar la puntualidad del widget:** registrar ejecución, errores y siguiente transición, sin prometer horarios exactos.
4. **Cerrar casos temporales:** 24:00 homogéneo entre formularios, cambios de fecha/zona y horario estacional.
5. **Pulir la representación de excepciones:** diferenciar modificación de traslado, aclarar recuentos y excluir marcadores de los avisos antiguos de solape.
6. **Completar accesibilidad y pruebas de interfaz:** muchos solapes, bloques pequeños, fuente ampliada, TalkBack, rotaciones y borradores.
7. **Medir reconstrucción histórica y observación reactiva con grandes volúmenes**, antes de optimizar consultas.
8. **Separar pantallas y formularios gradualmente** y revisar las advertencias de herramientas.
9. **Preparar Release y política de backup**, verificar firma y registrar un estado estable en Git antes de distribución.

## 14. Conclusión

BlockNize cuenta con agenda local, recurrencias semanales, temporales, finalizaciones, historial, calendario mensual/semanal, widget configurable, Concentración circular y excepciones por ocurrencia con trazabilidad. La base está en versión 4 y se han validado las rutas de migración desde las tres versiones anteriores.

La compilación correcta, las **24 pruebas unitarias** y las **45 instrumentadas** aprobadas aportan evidencia concreta del estado Debug. Las prioridades son consolidar recorridos completos en dispositivo físico, comportamiento real del widget y bordes temporales y de usabilidad. No corresponde seguir listando Concentración, excepciones, filtrado compartido o reactividad mensual como funciones sin implementar; sí documentar sus límites actuales y la validación todavía pendiente.
