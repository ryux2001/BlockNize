# BlockNize — Plan de implementación: Concentración y excepciones

**Fecha:** 3 de noviembre de 2026  
**Estado:** primera entrega de Concentración implementada; excepciones y reprogramación pendientes.  
**Ámbito:** modo Concentración, excepciones de ocurrencias y reprogramación individual.

> **Cambio de alcance posterior, ya implementado:** se eliminan cronómetro y cuenta atrás configurable. Concentración conserva un único contador automático hasta el fin del bloque. En horizontal, la navegación se sitúa a la derecha con iconos sin títulos; en vertical se mantiene la barra inferior anterior. Esta decisión sustituye las modalidades y sesiones manuales descritas en el plan original y en el registro de la primera entrega. Véase la sección 12.

> Este documento registra el plan discutido y las decisiones confirmadas. Los detalles señalados como propuestas técnicas no son decisiones adicionales del usuario. Guardar este documento no implica implementar las funciones.

## 1. Objetivos

1. Añadir una sección Concentración a la navegación inferior.
2. Mostrar únicamente la tarea actual, su tiempo restante y un temporizador de concentración, con una presentación inspirada en relojes StandBy y adaptada al tema AMOLED existente.
3. Conservar la información y el estado temporal al rotar la pantalla o volver desde segundo plano.
4. Permitir modificar, omitir o reprogramar una única ocurrencia sin alterar su plantilla semanal.
5. Mantener coherencia entre Inicio, Concentración, Calendario mensual y semanal, Registro y widget.
6. Conservar el historial y evitar duplicaciones de actividades o finalizaciones.

## 2. Decisiones confirmadas

| Tema | Decisión |
|---|---|
| Navegación | Concentración tendrá acceso desde el navbar |
| Presentación | Tarea actual, tiempo restante y temporizador; estética AMOLED actual |
| Orientación | Rotar no debe perder información ni reiniciar la sesión |
| Modalidades | Cuenta atrás y cronómetro, elegibles dentro de la pantalla |
| Segundo plano | Basta con conservar el estado y recalcular el tiempo al volver |
| Avisos | No se requiere un servicio permanente, alarma ni notificación de finalización en segundo plano |
| Reprogramación | La tarea reprogramada permanece aunque cambie la semana activa |
| Omisión | Solo afecta a la ocurrencia de la plantilla de origen |
| Plantilla | Las excepciones no modifican la programación semanal reutilizable |

**Propuesta adicional pendiente de validación en la implementación:** cambiar entre cuenta atrás y cronómetro conserva los valores de ambas modalidades y solo una puede estar ejecutándose a la vez. Al cambiar, se pausaría la modalidad anterior, sin borrar su estado.

## 3. Evaluación de la base existente

### Elementos reutilizables

- Agenda y operaciones de `TemplateRepository`.
- Persistencia Room con revisiones y eventos históricos.
- Identidad y estados de `AgendaOccurrence`.
- Tema AMOLED, acento configurable y componentes Compose.
- Consultas del calendario y distribución gráfica semanal.
- Solicitud de refresco y programación del widget.

### Ajustes previos necesarios

- El reloj compartido se actualiza cada 30 segundos: Concentración necesita actualización visual aproximadamente cada segundo mientras sea visible.
- El widget y la aplicación no aplican exactamente el mismo criterio para elegir la tarea actual. Es necesario centralizarlo.
- La identidad semanal actual contiene un evento de programación. Una excepción no debe depender exclusivamente de esa clave, porque una edición puede cambiarla.
- Las excepciones deben aplicarse en el repositorio, no mediante filtros independientes por pantalla.
- La base de datos está en versión 3: se prevé migración explícita a versión 4.

## 4. Modo Concentración

### 4.1. Navegación y aspecto

- Añadir una quinta sección a la navegación inferior, con icono identificable de concentración o temporizador.
- Mostrar título de la tarea y horario secundario.
- Destacar el tiempo restante del bloque.
- Incluir selector Cuenta atrás / Cronómetro y controles mínimos.
- No mostrar listas de otras tareas, estadísticas ni controles de gestión general.
- Mantener fondo negro puro, texto blanco y acento puntual.
- Usar cifras de ancho uniforme para evitar desplazamientos al cambiar los segundos.
- Adaptar la composición a vertical y horizontal, sin bloquear la orientación ni reiniciar el estado.

La inspiración StandBy se limita a la presentación dentro de la aplicación. No se contempla modificar la pantalla de bloqueo ni activar un modo del sistema operativo.

### 4.2. Dos tiempos independientes

| Tiempo | Definición |
|---|---|
| Restante del bloque | Diferencia entre la hora actual y el final programado de la actividad |
| Cuenta atrás | Duración configurable que el usuario inicia, pausa y reinicia |
| Cronómetro | Tiempo acumulado desde el inicio, descontando pausas |

Pausar el temporizador no pausa la agenda, no cambia el horario y no congela el tiempo restante del bloque.

No se incluyen ciclos Pomodoro, descansos automáticos ni estadísticas de sesiones en esta entrega.

### 4.3. Estado y cálculo temporal

Crear un `FocusViewModel` específico, con un estado de interfaz explícito:

- Identificador de la ocurrencia asociada a la sesión.
- Modalidad seleccionada.
- Duración configurada de la cuenta atrás.
- Estado por modalidad: preparado, ejecutándose, pausado o terminado, cuando corresponda.
- Referencias temporales de inicio y tiempo acumulado.
- Tiempo restante al pausar la cuenta atrás.

Principios de implementación:

1. Usar reloj civil para el horario programado de la tarea.
2. Usar una referencia monotónica para medir la duración de la sesión y evitar saltos por cambios manuales de hora.
3. Calcular el valor mostrado a partir de esas referencias; no depender de restar o sumar un segundo a una variable por cada repintado.
4. Actualizar visualmente aproximadamente una vez por segundo mientras Concentración esté visible y activa.
5. Suspender el repintado cuando la app no esté en primer plano, conservando el estado necesario para recalcular al volver.
6. Mantener la cuenta atrás y el tiempo restante del bloque limitados a cero, sin valores negativos.

### 4.4. Rotación y restauración

- Usar `ViewModel` para sobrevivir a cambios de configuración.
- Usar `SavedStateHandle` para los datos recuperables tras recreación del proceso por Android, dentro de sus garantías.
- No almacenar información esencial solo en un `remember` local de la pantalla.
- Separar completamente la distribución visual de las referencias temporales.

No se promete recuperación después de forzar el cierre, reiniciar el dispositivo o borrar datos. Una recuperación duradera entre reinicios requeriría persistencia adicional y queda fuera de la garantía confirmada.

### 4.5. Tarea actual y casos especiales

| Situación | Comportamiento propuesto |
|---|---|
| No hay actividad actual | Mostrar “Sin tarea en curso”, sin iniciar una sesión automáticamente |
| Existen solapes | Mostrar una tarea y ofrecer selección discreta entre las actuales |
| Actividad completada | Excluirla de candidatas actuales |
| Termina el horario | Mostrar cero en el tiempo restante del bloque |
| Cambia la actividad con una sesión en ejecución | No transferir silenciosamente la sesión a otra tarea; conservar su referencia y ofrecer finalizar o continuar |
| Se omite o reprograma la tarea asociada | Actualizar la agenda y resolver explícitamente la sesión activa |
| Se vuelve desde segundo plano | Recalcular los valores, sin iniciar desde cero |

**Propuesta de comodidad:** opción Mantener pantalla encendida solo mientras Concentración esté en primer plano, restaurando el comportamiento normal al salir. No usar un bloqueo permanente del dispositivo.

## 5. Excepciones y reprogramación

### 5.1. Acciones de usuario

En ocurrencias semanales de Inicio y Calendario, incorporar:

- Modificar solo esta vez.
- Reprogramar.
- Omitir esta vez.
- Restaurar programación original, si existe una excepción.

Mostrar siempre una aclaración equivalente a:

> Este cambio afecta únicamente a esta ocurrencia. La plantilla semanal no se modificará.

Modificar solo esta vez permite cambiar título, descripción, etiqueta u horario. Reprogramar permite cambiar horario y, si se desea, fecha. Omitir retira la actividad de las pendientes a realizar, conservando su registro como omitida.

Mover una ocurrencia a un día que ya tiene otro bloque habitual no elimina ese otro bloque. Si coinciden, se avisa del solape.

### 5.2. Modelo de persistencia propuesto

No implementar una reprogramación como una tarea temporal desconectada del origen. Debe existir identidad propia y trazabilidad.

#### `OccurrenceExceptionEntity`

Estado vigente de la excepción:

- Identificador estable.
- Referencia a plantilla y bloque de origen.
- Fecha original de la ocurrencia.
- Tipo de acción: omitir o sustituir.
- Fecha y horario de destino para una sustitución.
- Datos específicos de título, descripción y etiqueta.
- Zona horaria.
- Fechas de creación y modificación.

Una modificación puntual y un traslado a otra fecha pueden compartir la operación interna de sustitución.

#### `OccurrenceExceptionEventEntity`

Eventos inmutables de creación, modificación y restauración:

- Identificador y orden del evento.
- Referencia a la excepción y al origen.
- Instante efectivo.
- Snapshot suficiente para reconstruir el estado histórico.
- Zona horaria y tipo de cambio.

#### Identidad y migración

- Asociar el origen mediante plantilla, bloque y fecha original, con su contexto histórico separado.
- No utilizar únicamente el `occurrenceKey` actual como vínculo permanente.
- Garantizar una sola excepción vigente por ocurrencia original.
- Definir identidad estable de la sustitución para evitar completados duplicados al editarla.
- Añadir migración Room `3 → 4`, sin recrear destructivamente la base.
- Actualizar esquema exportado, DAO y observación de tablas relevantes.

### 5.3. Aplicación en el motor de agenda

```text
Programación semanal base
    ↓
Excepciones de las ocurrencias de origen
    ↓
Sustituciones que llegan a la fecha consultada
    ↓
Tareas temporales
    ↓
Completados y estados
    ↓
Agenda final compartida
```

Consultar una fecha debe buscar también las sustituciones trasladadas desde otros días, aunque esa fecha no tenga bloques semanales propios.

El resultado debe alimentar Inicio, Concentración, Calendario mensual, Calendario semanal, Registro y widget. Las transiciones programadas del widget deben utilizar igualmente los horarios efectivos resultantes.

### 5.4. Reglas de historial y finalización propuestas

- Una omisión se registra como Omitida, no como tarea fallida.
- Una reprogramación conserva en el origen una referencia al destino.
- La actividad trasladada se completa una sola vez.
- Editar o restaurar una excepción no borra los registros históricos ya efectivos.
- No permitir editar retroactivamente días pasados en la primera versión.
- Si una actividad está en curso, el cambio entra en vigor al guardar y conserva el tramo anterior.
- Exigir deshacer la finalización antes de omitir o mover una actividad ya completada.
- Restaurar afecta a la programación efectiva desde ese momento; no reescribe los intervalos transcurridos.

### 5.5. Relación con las plantillas

**Confirmado:**

- La actividad reprogramada se conserva como compromiso explícito aunque se active otra semana.
- La omisión no afecta a una actividad de otra plantilla.
- Ninguna excepción modifica la regla semanal original.

**Propuestas para completar el contrato técnico:**

- La excepción sobrevive a ediciones del mismo bloque; la elección explícita para esa fecha tiene prioridad.
- Si se elimina el bloque original, no reasignar la excepción por coincidencia de nombre.
- Preservar snapshots y relaciones históricas aunque deje de existir la programación editable original.
- Mostrar claramente el origen de las actividades reprogramadas para evitar resultados aparentemente huérfanos.
- Una excepción de una ocurrencia no debe convertirse accidentalmente en una regla para todas las futuras repeticiones.

### 5.6. Validaciones e intercambio

- Título obligatorio cuando corresponda.
- Inicio anterior al fin.
- Sin cruce de medianoche, manteniendo el alcance actual.
- Sin destino en una fecha pasada.
- Definir el comportamiento de destinos ya transcurridos dentro del día actual; se recomienda impedir reprogramaciones hacia un instante pasado.
- Advertir de solapes sin eliminar automáticamente otras tareas.
- Guardar operaciones relacionadas en una sola transacción.
- Solicitar refresco del widget después del commit correcto.
- No incluir excepciones en el JSON de plantilla semanal: son ajustes por fecha, no reglas reutilizables.

## 6. Organización del código

Archivos orientativos, relativos a `app/src/main/java/com/blocknize/app/`:

```text
ui/focus/
  FocusScreen.kt
  FocusViewModel.kt
  FocusUiState.kt

ui/occurrence/
  OccurrenceActions.kt
  OccurrenceExceptionDialog.kt

data/
  Entidades y consultas de excepciones
  Integración con TemplateRepository y AppDatabase

domain/
  CurrentTaskSelector.kt
  Cálculo temporal independiente de la interfaz
```

- Evitar seguir concentrando pantallas y formularios en `BlockNizeApp.kt`.
- Reemplazar progresivamente los índices numéricos de navegación por destinos identificados al incorporar la quinta pestaña.
- Mantener un criterio compartido para seleccionar actividades actuales, excluyendo las que no sean vigentes, estén omitidas o estén completadas.
- Reutilizar el repositorio existente y extraer componentes solo cuando mejore su claridad y testabilidad.
- Unificar la actualización reactiva de las vistas afectadas, incluyendo calendario mensual y Registro.

## 7. Fases de ejecución

| Fase | Trabajo | Criterio de finalización |
|---|---|---|
| 1. Contrato funcional | Incorporar decisiones confirmadas y concretar detalles propuestos | Reglas temporales y de identidad explícitas |
| 2. Base compartida | Selector de tarea actual y destinos de navegación | Inicio, widget y Concentración comparten criterio |
| 3. Concentración | Nueva pestaña, composición adaptable, cuenta atrás, cronómetro y conservación de estado | Rotar y volver desde segundo plano no reinicia la sesión |
| 4. Persistencia | Entidades, DAO, eventos y migración 3 → 4 | Datos existentes preservados y migración probada |
| 5. Motor de agenda | Omitir, sustituir, trasladar y restaurar | Agenda efectiva coherente, sin duplicados y con historial |
| 6. Interfaz de excepciones | Acciones, formularios y estados de Registro | Operaciones accesibles desde Inicio y Calendario |
| 7. Integración | Concentración, widget, transiciones y observación reactiva | Las modificaciones se propagan sin recarga manual |
| 8. Validación | Pruebas unitarias, instrumentadas y revisión visual | Casos normales, rotaciones y límites temporales verificados |

**Entregas recomendadas:** primero Concentración estable; después excepciones completas e integración final con Concentración. No entregar excepciones solo visuales sin persistencia e historial coherentes.

## 8. Pruebas y criterios de aceptación

### 8.1. Concentración

- Iniciar, pausar, reanudar y reiniciar ambas modalidades.
- Elegir cuenta atrás o cronómetro dentro de la pantalla.
- Cambiar de modalidad según la política acordada, sin perder sus valores.
- Rotar repetidamente con el temporizador en marcha y pausado.
- Navegar a otra pestaña y regresar.
- Pasar a segundo plano durante varios minutos y volver.
- Recrear la actividad y comprobar el estado restaurado.
- Cambiar la hora del dispositivo y verificar que no altera la duración monotónica de la sesión.
- Terminar el horario, completar la tarea o cambiar la semana activa.
- Resolver solapes y ausencia de actividad actual.
- Omitir o reprogramar una tarea asociada a una sesión activa.
- Probar fuente ampliada, vertical y horizontal.
- Comprobar que mantener la pantalla encendida, si se incluye, no permanece activo fuera de Concentración.

### 8.2. Excepciones

- Omitir un lunes concreto y conservar intactos los demás lunes.
- Cambiar únicamente título, descripción, etiqueta u horario de una ocurrencia.
- Trasladar a otro día, mes o año.
- Consultar un destino sin programación semanal propia.
- Reprogramar varias veces sin crear duplicados.
- Completar la sustitución una sola vez.
- Restaurar sin borrar el historial previo.
- Conservar una reprogramación al cambiar de semana activa.
- Limitar la omisión a su plantilla y ocurrencia de origen.
- Editar o eliminar el bloque original y comprobar la trazabilidad.
- Aplicar un cambio a mitad de una actividad y conservar sus tramos efectivos.
- Validar avisos de solape y rechazo de fechas u horarios inválidos.
- Verificar rollback completo si falla una operación.
- Probar la migración 3 → 4 y las rutas de actualización desde versiones anteriores.
- Verificar que la exportación semanal no incorpora excepciones por fecha.

### 8.3. Integración

- Comprobar la misma agenda efectiva en Inicio, Concentración, Calendario mensual y semanal, Registro y widget.
- Confirmar que las acciones invalidan las consultas observadas y actualizan los detalles abiertos.
- Comprobar que el widget solicita refresco y programa las transiciones de los horarios modificados.
- Mantener explícita la restricción Android: el refresco del widget no tiene garantía de puntualidad exacta.

## 9. Fuera del alcance de esta entrega

- Alarmas o notificaciones puntuales con la app cerrada.
- Servicio de concentración permanente en segundo plano.
- Integración con pantalla de bloqueo o un modo StandBy del sistema.
- Garantía de recuperación de la sesión tras reinicio del teléfono o cierre forzado.
- Estadísticas de sesiones, ciclos Pomodoro y descansos automáticos.
- Bloques que atraviesen medianoche.
- Exportación de copia completa de excepciones e historial.
- Edición de tareas mediante arrastre en la gráfica semanal.

## 10. Resultado esperado

Una sección Concentración minimalista y resistente a rotaciones, con cuenta atrás y cronómetro seleccionables, y un sistema de excepciones por ocurrencia que permita adaptar un día sin cambiar toda la semana. Ambas funciones compartirán una agenda efectiva consistente y conservarán la trazabilidad del historial.

## 11. Registro histórico de implementación — primera entrega

### Implementado

- Quinta pestaña Concentración, con destinos de navegación identificados en lugar de índices numéricos.
- Selector compartido de actividades actuales para Inicio, Concentración y widget; excluye completadas, históricas e interrumpidas.
- Tiempo restante del bloque independiente de la sesión.
- Cuenta atrás configurable entre 1 y 1440 minutos, inicialmente 25 minutos, y cronómetro.
- Inicio, pausa, reanudación y reinicio de la modalidad elegida.
- Al alternar modalidad se pausa la anterior y se conservan ambos valores.
- Cálculo de sesión con reloj monotónico; no depende de contar repintados.
- `FocusViewModel` y `SavedStateHandle` para conservar referencias temporales, modalidad, configuración e identidad de la sesión.
- Composición adaptable a vertical y horizontal.
- Selección discreta cuando existen actividades solapadas.
- Confirmación antes de iniciar una sesión para otra actividad, sin reasignación silenciosa del tiempo acumulado.
- Pausa y decisión explícita si la actividad asociada deja de estar en curso mientras se consulta Concentración.
- Opción de mantener la pantalla encendida, limitada al primer plano de esta pantalla.
- Sin alarmas, servicios permanentes ni notificaciones en segundo plano.

### Verificación realizada

- Compilación de aplicación y APK de pruebas: correcta.
- 29 pruebas unitarias: correctas, incluyendo 16 nuevas de selección de tarea, motor temporal y ViewModel.
- Batería instrumentada completa ejecutada en `emulator-5554`: 27 pruebas correctas.
- Se incluyen pruebas de selección de modalidad, estados sin tarea, asociación de sesión anterior, recreación de actividad y rotación real a horizontal.
- No se ejecutaron pruebas en el dispositivo físico conectado ni se borraron los datos de la aplicación.

### Pendiente

- Entidades, eventos y migración Room 3 → 4 para excepciones.
- Motor de omisión, sustitución, reprogramación y restauración.
- Acciones de usuario en Inicio y Calendario, estados de Registro e integración de excepciones con Concentración y widget.
- Pruebas de migración e historial de excepciones.

La base de datos permanece en versión 3 en esta entrega. La existencia de la pantalla Concentración no implica que las excepciones estén implementadas.

## 12. Simplificación de Concentración y navegación adaptada a orientación

### Alcance vigente implementado

- Se retiran el cronómetro, la cuenta atrás configurable, el selector de modalidad y los controles de iniciar, pausar, reanudar y reiniciar.
- Se elimina el estado de sesiones manuales y sus confirmaciones.
- Solo se muestra un contador `HH:mm:ss`, calculado a partir de la hora actual y el final del bloque vigente.
- Al volver desde segundo plano o recrear la actividad se recalcula el tiempo; no existe un temporizador independiente que pueda reiniciarse.
- Se conserva la selección de actividad cuando hay solapes, la estética AMOLED y la opción de mantener la pantalla encendida.
- En horizontal, las secciones principales usan una barra lateral derecha con iconos sin texto y selección destacada.
- En vertical se mantiene el diseño anterior de la barra inferior.
- Se conserva la sección seleccionada al rotar. Apariencia y edición de semanas siguen ocultando la navegación como antes.
- Las descripciones accesibles de los iconos se mantienen aunque no haya etiquetas visibles en la barra lateral.

### Verificación

- Aplicación y APK de pruebas compilados correctamente.
- 23 pruebas unitarias correctas; las pruebas de las modalidades eliminadas se sustituyen por pruebas del tiempo restante y la selección guardada.
- 27 pruebas instrumentadas correctas en `emulator-5554`.
- Verificada la posición derecha de la barra lateral, ausencia de títulos en sus elementos, navegación horizontal y restauración de la barra inferior al volver a vertical.
- Verificada la recreación de la actividad y la ausencia de controles de temporizadores manuales.

Las excepciones y la reprogramación siguen pendientes. Este cambio no altera el esquema de la base de datos.

## 13. Presentación circular sin cabecera ni desplazamiento

- Se elimina la cabecera superior únicamente cuando se muestra Concentración; las otras pantallas y Apariencia conservan sus cabeceras.
- El tiempo restante se sitúa dentro de un anillo fino cuyo avance representa el tiempo transcurrido del bloque, utilizando el acento de la aplicación.
- El diámetro se calcula con el ancho y el alto disponibles. El texto interior se adapta al diámetro y al escalado de fuente.
- El contenido principal no utiliza scroll. En horizontal, el círculo se sitúa a la izquierda y la tarea, horario y opciones a la derecha. En vertical, el círculo ocupa el espacio entre la información y las opciones.
- Configuración se integra en la barra lateral derecha solo para Concentración en horizontal. En vertical continúa accesible desde un icono compacto dentro de Concentración, sin recuperar la cabecera.
- Se mantienen la selección entre tareas simultáneas y la opción de mantener la pantalla encendida.
- No se añaden controles de pausa, cancelación ni temporizadores independientes.

**Verificación:** compilación correcta, 23 pruebas unitarias y 29 instrumentadas correctas en el emulador. Se comprueban el progreso del anillo, ausencia de desplazamiento en Concentración, límites del círculo en un espacio de 640 × 220 dp con fuente al 130 %, acceso a Configuración y conservación de las cabeceras de otras pantallas. Para la batería completa se fijó temporalmente la orientación inicial del emulador a vertical y se restauró su rotación automática al terminar.
