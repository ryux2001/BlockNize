# BlockNize — Especificación funcional y plan de desarrollo Android

> **Versión:** 0.7 (acento violeta inicial confirmado + autorización de arranque del MVP)  
> **Fecha:** 29 de octubre de 2026  
> **Plataforma:** Android nativo  
> **Lenguaje:** Kotlin  
> **Estado:** semana completa, múltiples plantillas, una sola activa, activación inmediata, edición activa efectiva al guardar, AMOLED negro/blanco y **dos formas de escoger acento (paleta + selector libre) y acento predeterminado violeta `#A78BFA`** **confirmados**; revisiones/historial, estados de sustitución y detalles UX son propuestas técnicas explícitas  
> **Propósito:** disponer de un documento de referencia para desarrollar la aplicación de forma incremental, mantener un registro de decisiones y resolver dudas con criterios claros.
> **Cambio v0.7:** **CONFIRMADO:** el acento inicial al instalar BlockNize será **violeta `#A78BFA`**. Sigue siendo personalizable mediante paleta o selector libre. **Estado de preparación:** ya existe especificación suficiente para comenzar el MVP en Kotlin; solapes, representación del historial, bloques nocturnos, dispositivos mínimos y precisión del widget siguen pendientes de cierre o validación, pero no bloquean la creación del proyecto, la navegación, el tema ni la persistencia inicial.

---

## 1. Procedencia de los requisitos y cómo leer este documento

Se parte de dos documentos aportados para el proyecto:

- `Explicacion.md`: describe una app de organización inspirada en TimeTune: importar/exportar plantillas, tareas para uno o varios días, etiquetas, tareas temporales, finalización, vista por bloques, historial diario y widget de tarea actual con barra circular de avance temporal.
- `Flujo de pantallas.md`: boceto Excalidraw cuyo texto contiene las pantallas **Inicio**, **Calendario**, **Registro**, **Bloques**, **Organizar días (bloques semanales)**, **Tareas temporales**, **Configuración**, formularios con título/descripción/franja horaria/etiqueta y un **Widget**.

Etiquetas empleadas:

- **[REQUISITO]**: aparece explícitamente en los archivos aportados.
- **[PROPUESTA]**: recomendación técnica o funcional para concretar lo anterior.
- **[PENDIENTE]**: no se puede inferir con seguridad; requiere decisión del responsable del proyecto.
- **[CONFIRMADO]**: decisión expresamente indicada en la conversación de planificación posterior a los archivos iniciales.

**Regla de trabajo:** no convertir una propuesta en requisito cerrado sin registrarlo en la sección de decisiones. El boceto describe elementos de pantalla, pero no especifica todos sus comportamientos ni relaciones.

## 2. Visión del producto

**BlockNize** es una aplicación Android para estructurar el tiempo del usuario mediante bloques horarios y tareas. La persona configura una rutina semanal, ve qué tiene que hacer hoy, puede insertar compromisos extraordinarios, marcar lo que ha realizado y consultar un registro. Puede activar otra de las semanas guardadas **en cualquier momento y con efecto inmediato**, y **guardar ediciones sobre la plantilla activa con aplicación inmediata**. Un widget en la pantalla de inicio muestra el bloque activo y una representación del tiempo transcurrido. La identidad visual elegida es **AMOLED**: superficies principales de negro puro, tipografía e iconografía blancas y un color de acento seleccionable por el usuario en **Configuración**.

### Objetivos principales

1. **[REQUISITO]** Organizar días en bloques horarios reutilizables.
2. **[REQUISITO]** Programar tareas para un día o varios días.
3. **[REQUISITO]** Categorizar tareas mediante etiquetas (comer, gimnasio, estudio, trabajo…).
4. **[REQUISITO]** Añadir tareas temporales no habituales.
5. **[REQUISITO]** Marcar tareas completadas y consultar cuáles se han realizado.
6. **[REQUISITO]** Mostrar el día como una sucesión de bloques, con horario, título y descripción.
7. **[REQUISITO]** Importar y exportar plantillas entre móviles.
8. **[REQUISITO]** Ofrecer un widget con la tarea correspondiente al momento y un círculo de avance temporal.
9. **[CONFIRMADO v0.5]** Aplicar inmediatamente los cambios de una plantilla activa **al pulsar Guardar**.
10. **[CONFIRMADO v0.5]** Estética AMOLED (negro/blanco) con color de énfasis elegido en Configuración.
11. **[CONFIRMADO v0.6]** Permitir elegir el acento mediante **paleta predefinida y selector libre de color personalizado**, con ambos mecanismos disponibles en Configuración.
12. **[CONFIRMADO v0.7]** Utilizar **violeta `#A78BFA`** como acento inicial de cualquier instalación nueva y como valor de restablecimiento del acento.

### Alcance inicial propuesto

**[PROPUESTA]** Aplicación personal, sin cuenta, sin servidor y operativa sin conexión. La agenda se generará a partir de **una plantilla semanal seleccionada para su uso** y tareas temporales por fecha. **[CONFIRMADO]** Cada plantilla representa el conjunto de bloques de lunes a domingo; un día puede quedar vacío. **[CONFIRMADO]** Se pueden guardar varias plantillas, pero **solo una puede estar activa a la vez**, y el cambio se aplica **inmediatamente**. Se podrán transferir plantillas mediante archivos JSON. La sincronización automática entre dispositivos, la colaboración y las notificaciones se valorarán después. **[CONFIRMADO v0.5]** Al guardar una edición de la semana activa, la programación nueva entra en vigor inmediatamente; editar una inactiva no modifica la agenda. La app usará un tema AMOLED fijo (no se ajusta automáticamente al modo claro del sistema), con acento configurable **desde muestras predefinidas o un selector libre**, ambos incluidos en el alcance inicial.

**No incluido de entrada:** cuentas, backend, IA, calendario de Google, recordatorios exactos, sincronización automática, estadísticas avanzadas, varias plantillas activas simultáneamente ni asignación anticipada de plantillas a semanas del calendario. Estos puntos son posibles futuras ampliaciones, no requisitos actuales.

## 3. Conceptos del dominio: evitar confusiones

| Concepto | Significado propuesto | Ejemplo |
|---|---|---|
| **Tarea** | Actividad con identidad, título, descripción y etiqueta. | «Estudiar Kotlin» |
| **Bloque horario** | Asignación de una tarea a un intervalo temporal. | 17:00–18:30 |
| **Regla semanal** | Bloque recurrente que pertenece a una plantilla semanal y se aplica en uno o varios días seleccionados. | L, X, V de 17:00 a 18:30 |
| **Tarea temporal** | Bloque que ocurre en una fecha concreta; opcionalmente utiliza una tarea existente. | Cita médica el 03/11 |
| **Ocurrencia** | Instancia de un bloque para una fecha específica. | «Estudiar Kotlin», miércoles 04/11 |
| **Finalización** | Estado de una **ocurrencia**, no de toda la tarea recurrente. | Se completó el miércoles, no automáticamente el viernes |
| **Etiqueta** | Clasificación visual reusable. | Estudio, trabajo, descanso |
| **Plantilla semanal** | **[CONFIRMADO]** Programación completa y reutilizable de lunes a domingo, con tareas, franjas horarias y etiquetas asociadas. Puede contener días sin actividades. Se pueden guardar varias. | «Semana de clases», «Vacaciones» |
| **Selección activa** | **[CONFIRMADO]** En un momento dado se utiliza **como máximo una** plantilla semanal para la agenda recurrente; activar otra hace efecto inmediatamente. Si no existe ninguna, estado «sin plantilla». | «Semana de clases» activa |
| **Plantilla inactiva** | Plantilla guardada y editable, pero que no aporta reglas semanales a Inicio ni al Widget mientras no esté activa. | «Vacaciones» guardada |
| **Excepción** | Cambio o cancelación de una ocurrencia sin alterar toda la recurrencia. | Cancelar solo el miércoles |
| **Revisión de plantilla** | **[PROPUESTA]** Copia inmutable de la programación de una semana en un momento concreto, necesaria para reproducir el historial aunque se edite la plantilla original. | «Clases», revisión 3 |
| **Evento de activación** | **[PROPUESTA]** Registro del instante exacto en el que una plantilla/revisión pasa a regir la agenda. | Cambio «Clases» → «Vacaciones» a las 12:00 |
| **Segmento histórico** | **[PROPUESTA]** Parte del intervalo de un bloque durante la que una revisión de la programación estaba efectivamente activa. | Bloque 11:00–13:00 válido de 11:00 a 12:00 antes del cambio |
| **Edición efectiva de la activa** | **[CONFIRMADO v0.5]** Guardar cambios sobre la plantilla activa hace que la nueva programación rija **inmediatamente**; el cambio no reescribe el pasado. | Guardar horario a las 12:15 → rige desde 12:15 |
| **Color de acento** | **[CONFIRMADO v0.5–v0.6]** Preferencia visual global seleccionada en Configuración mediante paleta o selector personalizado; independiente de etiquetas y de las plantillas. | Acento turquesa sobre negro y blanco |

> **Decisión de diseño clave [PROPUESTA]:** separar «qué actividad es» de «cuándo sucede». Guardar una sola tarea como completada para siempre daría errores en las recurrencias; el registro debe estar asociado a la fecha/ocurrencia.

## 4. Requisitos funcionales detallados

### RF-00. Gestión y activación de plantillas semanales

- **[CONFIRMADO]** Cada plantilla es una **semana completa de lunes a domingo**; uno o más días pueden estar vacíos.
- **[CONFIRMADO]** Se pueden **guardar varias plantillas independientes**: «Clases», «Exámenes», «Vacaciones», etc.
- **[CONFIRMADO]** Solo **una** de las guardadas puede estar **activa** a la vez. No se combinan bloques de dos plantillas en la rutina recurrente visible.
- **[CONFIRMADO]** Una plantilla inactiva se conserva y puede editarse sin alterar la agenda basada en la activa.
- **[PROPUESTA]** Funciones de gestión: crear, listar, renombrar, ver detalle, editar, duplicar, activar, archivar y exportar cada plantilla.
- **[PROPUESTA]** En la lista, mostrar nombre, número de bloques, última edición y etiqueta «Activa» para una única plantilla; otras muestran «Inactiva».
- **[PROPUESTA]** La pantalla **Bloques** edita la plantilla elegida por el usuario. **Plantilla seleccionada para editar** y **plantilla activa** son estados distintos: entrar a editar otra no la activa automáticamente.
- **[PROPUESTA]** Cambiar de plantilla activa solicita confirmación explícita; **[CONFIRMADO]** al confirmar aplica la nueva **en ese instante** a Inicio y al cálculo de agenda actual/futura. **[PROPUESTA]** Se registra evento de activación e historial y se solicita refrescar el Widget.
- **[PROPUESTA]** Al activar otra, la anterior pasa a inactiva automáticamente; no se borran sus tareas, reglas ni etiquetas.
- **[PROPUESTA]** Al importar una plantilla nueva, quedará **guardada e inactiva**, salvo decisión posterior del usuario de activarla explícitamente.
- **[PROPUESTA]** No se puede borrar/archivar la única activa sin elegir otra o confirmar que se dejará la aplicación sin rutina activa. Archivar es preferible a borrado físico cuando existe historial.
- **[CONFIRMADO]** El cambio entra en vigor **inmediatamente**, incluso a mitad del día: no esperar a mañana ni al lunes. La hora exacta de confirmación (`Instant`) es el corte. No implica asignar plantillas a semanas concretas.
- **[PROPUESTA TÉCNICA]** Para consultar días pasados sin falsearlos, persistir revisiones inmutables y eventos tanto de activación como de **edición guardada de la activa**. No basta con guardar cuál está activa ahora.

**Aceptación obligatoria:** crear A y B; activar A; a mitad del miércoles activar B y comprobar que **de inmediato** Inicio muestra B, que A sigue guardada, que las temporales persisten, que las finalizaciones de A siguen en Registro y que las fechas pasadas no se recalculan con B. Reiniciar mantiene B activa. **Además:** editar y guardar B activa modifica la agenda al instante, genera un corte histórico `ACTIVE_EDIT` cuando cambia su contenido y no altera los completados anteriores.


### RF-01. Pantalla Inicio / Hoy

- **[REQUISITO]** Mostrar el día actual y las tareas del día en bloques.
- **[REQUISITO]** Mostrar horas, títulos y descripciones, y acceso a tarea temporal.
- **[PROPUESTA]** Ordenar por hora de inicio; diferenciar tarea en curso, futura y pasada.
- **[PROPUESTA]** Cada tarjeta permite abrir detalle y marcar/desmarcar completada.
- **[PROPUESTA]** Si dos actividades se solapan, mostrar ambas y una advertencia en edición; no descartar datos. Los bloques recurrentes visibles se consultan de **una sola plantilla activa**, mientras las temporales son independientes. Una activación a mitad de un bloque puede dejarlo sustituido y mostrar inmediatamente un bloque de B que ya había empezado por su horario.
- **[PROPUESTA]** Estados vacíos: «No hay bloques programados» y «No hay una tarea en curso».

**Aceptación:** con una plantilla activa, Inicio muestra solo sus ocurrencias semanales y las temporales de la fecha, sin duplicados; con ninguna plantilla, se mantienen las temporales. Las finalizaciones sobreviven al cierre de la app.

### RF-02. Crear/editar tareas

- **[REQUISITO]** Editar título, descripción, horario, días y etiqueta (según los elementos del boceto y la explicación).
- **[PROPUESTA]** Título obligatorio; descripción y etiqueta opcionales; hora de fin posterior a inicio en el MVP.
- **[CONFIRMADO]** Cada tarea recurrente se programa **dentro de una plantilla semanal**; puede aparecer en uno o varios días de L a D según la selección del usuario.
- **[PROPUESTA]** Edición de tarea y de programación mediante formularios separables internamente, aunque visualmente se presenten juntos.
- **[PROPUESTA]** Confirmar antes de borrar una tarea que tenga bloques asignados. Editar una tarea de una plantilla inactiva **no modifica** la agenda recurrente activa.
- **[CONFIRMADO v0.5]** Si una edición de tarea, bloque, horario, día o etiqueta **de la plantilla activa** se valida y se pulsa **Guardar**, los cambios se aplican **en ese momento**. No hay estado «pendiente de publicar»; mientras el formulario está abierto, la edición todavía no altera la agenda.

**Aceptación:** crear una tarea lunes y jueves dentro de una plantilla hace que aparezca únicamente esos días cuando esa plantilla rija la agenda; editar su nombre se refleja en sus bloques de la misma plantilla sin afectar a otra plantilla independiente.

### RF-03. Bloques / Organizar días

- **[REQUISITO]** Vista para organizar días o bloques semanales; selector L M X J V S D; acción agregar y editar.
- **[CONFIRMADO]** Los siete días que se editan son los de **una misma plantilla semanal**, no siete plantillas independientes.
- **[PROPUESTA]** Mostrar una línea de tiempo vertical o lista horaria para el día elegido.
- **[PROPUESTA]** Añadir, editar y eliminar un bloque semanal; selección de varios días en el formulario.
- **[PROPUESTA]** El usuario selecciona explícitamente qué plantilla va a editar; una etiqueta indica si es activa. Inicialmente sin arrastrar y soltar: se añadirá cuando la estructura y persistencia funcionen.
- **[CONFIRMADO v0.5]** Guardar un bloque (crear, modificar o eliminar) de la plantilla **activa** actualiza Inicio y Calendario inmediatamente. Para una **inactiva**, el guardado modifica solo esa plantilla, hasta su futura activación.

**Aceptación:** los bloques del lunes permanecen asociados al lunes y a su plantilla después de navegar, reiniciar la app o cambiar de fecha. Una plantilla nueva puede tener siete días vacíos y sigue siendo una plantilla semanal válida.

### RF-04. Tareas temporales

- **[REQUISITO]** Pantalla o acceso rápido para una tarea no habitual, con título, descripción y franja horaria.
- **[PROPUESTA]** Fecha concreta obligatoria, intervalo de horas y etiqueta opcional.
- **[PROPUESTA]** La tarea temporal aparece en Inicio y Calendario, pero **no se incorpora automáticamente a ningún día de la plantilla semanal**. Cambiar la plantilla activa **no** elimina, mueve ni oculta automáticamente tareas temporales.
- **[PENDIENTE]** Definir si sustituye un bloque recurrente que coincida o si ambos se muestran.

**Aceptación:** una tarea temporal del 06/11 no aparece en otros días.

### RF-05. Etiquetas

- **[REQUISITO]** Asignar una etiqueta a las tareas.
- **[PROPUESTA]** CRUD de etiquetas (crear, editar, eliminar), nombre y color **opcional como metadato**, sin introducir múltiples colores de acento visual por defecto; al borrar, no borrar tareas, sino dejar su etiqueta vacía o reasignarla. En la interfaz AMOLED las etiquetas serán principalmente monocromas, con acento global para selección/énfasis; los colores propios por etiqueta quedarían para una decisión posterior.
- **[PROPUESTA]** Proporcionar ejemplos iniciales, editables por el usuario.

### RF-06. Calendario / Resumen

- **[REQUISITO]** Existe una pantalla de Calendario y resumen.
- **[PROPUESTA]** Calendario mensual con selección de fecha y resumen de bloques programados/completados.
- **[PROPUESTA]** Para fechas futuras, previsualizar la plantilla activa **en ese momento y su revisión vigente** más tareas temporales. Para hoy, mostrar la agenda vigente de la nueva plantilla y ofrecer acceso a los bloques previos del día. Para fechas anteriores, reconstruir el histórico desde el registro temporal de **activaciones, ediciones efectivas** y revisiones: **nunca** aplicar retrospectivamente la semana hoy activa. El formato final del resumen (porcentaje, duración o lista) queda **[PENDIENTE]**.

**Aceptación:** seleccionar una fecha pasada muestra sus ocurrencias y completados históricos, no solo la programación actual sin considerar cambios.

### RF-07. Registro

- **[REQUISITO]** Consultar las tareas completadas al final del día.
- **[PROPUESTA]** Lista por fecha con completadas y pendientes; número completado/total. Tras **editar y guardar** una plantilla activa, presentar el histórico previo del día separado de la programación actualmente vigente.
- **[PROPUESTA]** Permitir deshacer una finalización accidental.
- **[CONFIRMADO por la decisión temporal]** Cambiar de plantilla **no debe hacer que desaparezcan las finalizaciones anteriores**. **[PROPUESTA]** Conservar también los bloques anteriores que estuvieron vigentes, aunque no se completaran, usando revisiones y eventos de activación. Las tareas de A que aún no habían comenzado cuando se activó B **no cuentan como fallidas**; las que estaban en curso se distinguen como «interrumpidas por cambio» si no estaban completadas. Definir métricas y posibilidad de marcar pendientes históricos más adelante.

### RF-08. Importar/exportar plantillas

- **[REQUISITO]** Poder llevar plantillas a otro teléfono mediante importación y exportación.
- **[CONFIRMADO]** Lo que se exporta como «plantilla» es **la semana completa**, de lunes a domingo, con sus bloques, datos de las tareas y etiquetas requeridas. No es una colección arbitraria de tareas sueltas ni un único día.
- **[PROPUESTA]** Un archivo JSON de plantilla contiene una unidad autocontenida: nombre, metadatos, tareas, etiquetas referenciadas y reglas semanales. Los siete días forman parte del ámbito aunque alguno esté vacío.
- **[PROPUESTA]** Archivos JSON versionados, seleccionados por el usuario desde el selector del sistema; permitir exportar solo plantilla o copia completa.
- **[PROPUESTA]** Previsualizar contenido y detectar conflictos de IDs antes de importar; nunca borrar datos actuales sin confirmación.
- **[PROPUESTA]** Las tareas temporales y el historial **no** se incluyen en una exportación de plantilla; pertenecen, si se aprueba, a una copia de seguridad completa.
- **[PROPUESTA]** Tras importar, guardar la plantilla como **inactiva** y ofrecer «Activar» como acción separada; no reemplazar la activa automáticamente. Esta regla es una recomendación UX, no una confirmación del usuario.

### RF-09. Widget Android

- **[REQUISITO]** Mostrar la tarea del momento y un indicador circular de progreso temporal.
- **[PROPUESTA]** Mostrar título, horario, etiqueta y un anillo de porcentaje `transcurrido / duración`, limitado entre 0 y 100 %.
- **[PROPUESTA]** Tocar el widget abre la app en Inicio o en el detalle del bloque.
- **Importante:** los widgets no tienen garantía de repintado por segundo/minuto cuando la app está cerrada. Glance permite actualizar por acciones y mediante trabajo programado, pero Android recomienda evitar actualizaciones continuas por batería. Por ello, el **MVP debe aceptar progreso aproximado** en el widget y calcular progreso fluido dentro de la app; la viabilidad de un anillo visual realmente continuo exige un prototipo técnico y pruebas en dispositivo. Referencia: https://developer.android.com/develop/ui/compose/glance/glance-app-widget
- **[PROPUESTA]** Mostrar «Sin tarea ahora» si no hay ningún bloque activo. Para el momento actual, consultar la **única plantilla activa (última revisión aplicada)** más las tareas temporales de la fecha; **una activación, edición guardada de la activa o cambio de acento** debe solicitar actualización del widget. La consulta histórica por fecha depende de una política separada.

### RF-10. Edición de plantilla activa con efecto inmediato — **[CONFIRMADO v0.5]**

- La acción **Guardar** en un formulario que modifica la plantilla activa aplica los datos validados en cuanto la operación de persistencia termina correctamente; no existe acción adicional «Aplicar» o «Publicar» ni cambio diferido.
- Se incluyen altas, modificaciones y eliminaciones de bloques, horarios, días y tareas vinculados a la plantilla activa. **[PROPUESTA]** Renombrar o cambiar sus metadatos visibles también se refleja al guardar.
- Un cambio que altera el contenido efectivo publica una **nueva revisión inmutable** y un evento `ACTIVE_EDIT` con el instante exacto. La selección sigue apuntando a la **misma plantilla**, pero a su **nueva revisión**.
- Inicio, calendario actual/futuro y cálculo de bloque en curso leen esa nueva revisión. Las tareas temporales no se tocan. Los completados y los intervalos históricos anteriores se preservan.
- Guardar una plantilla **inactiva** persiste sus cambios, pero **no** crea un evento efectivo ni modifica la programación vigente hasta que esa plantilla se active.
- **[PROPUESTA]** Validar formularios antes de guardar, ofrecer estado de éxito/error y evitar dos guardados simultáneos; no generar eventos nuevos para un guardado sin diferencias de contenido.

**Aceptación:** al editar el miércoles a las 12:15 la hora de un bloque de la plantilla activa y pulsar Guardar, Inicio refleja la hora nueva tras el guardado; al consultar el pasado se conserva el tramo previo a las 12:15. Si el guardado falla, la programación y el historial no cambian.

### RF-11. Tema AMOLED y acento en Configuración — **[CONFIRMADO v0.5 y v0.6]**

- Fondo general **negro puro (`#000000`)**; textos e iconos principales **blancos (`#FFFFFF`)**. Se permiten grises neutros en texto secundario, bordes y superficies elevadas para jerarquía y legibilidad, sin sustituir el fondo principal AMOLED.
- **Un solo color de acento global** elegible desde **Configuración → Apariencia → Color de acento**, aplicable a controles activos, selección, botones destacados, chips de estado y progreso circular. Las etiquetas de tareas son una dimensión separada.
- **[CONFIRMADO v0.6]** La pantalla ofrece simultáneamente **paleta de opciones predefinidas** y acceso a un **selector de color libre/personalizado**. Ninguna de estas dos funciones queda relegada a una versión futura.
- La preferencia debe **persistir** tras reiniciar la aplicación; cambiarla recompone las pantallas sin reiniciar ni modificar datos de tareas. Tema AMOLED fijo, no colores dinámicos de Material You.
- El widget recibe el mismo color persistido y mantiene la identidad visual dentro de las restricciones de Glance/launcher; su repintado no está garantizado al milisegundo.
- Indicadores de tarea completada/activa, errores, avisos y navegación deben ser comprensibles sin depender exclusivamente del color. El acento personalizado puede tener poco contraste, por lo que los componentes deben utilizar roles de color y recursos visuales accesibles.
- **[CONFIRMADO v0.7]** El acento inicial es **violeta `#A78BFA`**. **[PROPUESTA]** Ofrecer una cuadrícula de muestras con nombres/HEX accesibles; vista previa en tiempo real del selector libre, confirmación para aplicar, cancelación reversible y opción de recuperar el violeta predeterminado. Permanecen pendientes los demás valores exactos de la paleta.

**Aceptación:** el usuario puede elegir una muestra y también un color personalizado que no exista en ella; ambos se reflejan en la app y persisten tras reinicio; todos los fondos principales siguen siendo `#000000`, y cambiar el acento no cambia tareas, plantillas, calendarios ni completados.

### RF-12. Selector mixto de acento — **[CONFIRMADO v0.6: ambos métodos / PROPUESTA: detalles UX]**

1. **Paleta predefinida [CONFIRMADO]:** se muestra una serie de opciones visibles que permiten escoger rápidamente un color de acento; cada opción tiene nombre legible, muestra cromática, estado seleccionado y etiqueta accesible. **[PROPUESTA]** Tocar una muestra guarda el nuevo acento inmediatamente (con indicación breve de éxito o error).
2. **Selector personalizado [CONFIRMADO]:** se puede escoger libremente un color distinto de los de la paleta. **[PROPUESTA]** Usar un diálogo o pantalla con control de matiz/saturación/brillo o una superficie 2D + control de matiz, vista previa y entrada manual opcional de `#RRGGBB`. No es obligatorio que sea un selector del sistema operativo; puede implementarse en Compose.
3. **Aplicación y cancelación [PROPUESTA]:** en el selector personalizado los cambios de los controles solo afectan a un `draft` de vista previa; pulsar **Aplicar** valida y persiste el color una vez, **Cancelar** conserva el acento anterior. Al elegir una muestra de la paleta, el guardado se hace directamente.
4. **Formato [PROPUESTA]:** color opaco sRGB de 24 bits `#RRGGBB` para entrada/intercambio interno, convertido a `0xFFRRGGBB` (ARGB) para renderizado. No permitir canal alfa/transparencia en el acento ni introducir códigos inválidos; normalizar a mayúsculas. Aceptar `RRGGBB` y `#RRGGBB` por comodidad si se incorpora campo HEX.
5. **Consistencia [PROPUESTA]:** en cualquier momento hay un único color elegido y persistido; se puede deducir si coincide con una muestra conocida para dibujar el borde de selección o marcar «Personalizado». No almacenar simultáneamente varias preferencias contradictorias ni guardar revisiones del calendario ante un mero cambio visual.
6. **Contraste [REQUISITO derivado de legibilidad + PROPUESTA técnica]:** cualquier color válido se puede escoger y conservar **sin cambiarlo silenciosamente**. Si es poco visible sobre negro, mostrar aviso en la previsualización y usar una variante accesible *derivada solo en componentes críticos* (texto pequeño, iconos, bordes finos, indicadores), explicando la diferencia entre color elegido y color adaptado. Las superficies grandes pueden representar fielmente el color elegido, con `onPrimary` blanco/negro calculado por contraste. No usar color como única señal.
7. **Widget [PROPUESTA]:** actualizar su apariencia tras la escritura persistida (no en cada movimiento de un control de color). Glance consulta el valor real en `SettingsRepository` y genera sus colores, respetando el negro y el blanco fijos en la medida que permita el launcher.
8. **Reset [PROPUESTA]:** acción «Restablecer color» que vuelve a la muestra predeterminada. No se restablece ningún dato funcional de la agenda.

**Criterios de aceptación específicos:** el selector debe admitir una muestra, un HEX arbitrario válido que no esté en la paleta, cancelar sin guardar, restaurar tras cerrar la app, mostrar errores de entrada, soportar colores oscuros con salida accesible y actualizar el widget de forma solicitada tras un cambio confirmado.

## 5. Flujo de pantallas propuesto

```text
App BlockNize
│
├── Inicio (Hoy)
│   ├── Cabecera: fecha + nombre de plantilla activa (o «Sin plantilla»)
│   ├── Bloque en curso / Próximo bloque SOLO de la activa
│   ├── Lista cronológica: activa + temporales de hoy
│   └── + Tarea temporal → Guardar → Inicio
│
├── Calendario
│   ├── Calendario mensual → elegir fecha
│   └── Resumen de fecha (pasado = eventos/revisiones; presente/futuro = activa vigente)
│
├── Registro
│   └── Elegir fecha → Completadas / Pendientes históricas / Interrumpidas
│
├── Bloques
│   ├── Selector de plantilla a EDITAR (no activa al seleccionarla)
│   ├── Indicador «Activa» / «Inactiva»
│   ├── Selector L M X J V S D
│   ├── + Nuevo bloque → Formulario tarea/horario/días/etiqueta
│   └── Guardar sobre activa → nueva revisión efectiva inmediatamente
│       (editar otra inactiva no cambia Inicio)
│
└── Gestión de plantillas (desde Bloques o Configuración)
    ├── Lista de todas las plantillas guardadas
    ├── Crear / Renombrar / Duplicar / Editar / Archivar
    ├── Activar → Confirmación → Cambio INMEDIATO y registro temporal
    ├── Exportar una plantilla → JSON
    └── Importar una plantilla → JSON → Guardada inactiva (propuesta)

Configuración (acceso propuesto desde Inicio o menú)
    └── Apariencia → Tema AMOLED fijo + Color de acento
        ├── Cuadrícula de colores predefinidos (CONFIRMADO)
        ├── Selector de color personalizado (CONFIRMADO)
        │   └── Vista previa → Aplicar / Cancelar (PROPUESTA)
        └── Restablecer acento (PROPUESTA)

Widget del launcher → Datos del momento según plantilla activa + temporales;
refresco solicitado al activar, guardar edición efectiva o cambiar el acento
```

**Navegación inferior [REQUISITO del boceto]:** Inicio · Calendario · Registro · Bloques. **[CONFIRMADO v0.6]** Configuración incluirá «Apariencia / Color de acento», con **paleta predefinida y selector libre**, ambos accesibles (el punto exacto de acceso a Configuración sigue propuesto). La ubicación exacta de «Gestión de plantillas» se mantiene como [PROPUESTA]. **Invariante visual:** seleccionar una plantilla para editar no equivale a activarla; solo una acción explícita lo hace.


## 6. Investigación y recomendación de tecnologías

| Necesidad | Recomendación | Motivo | Prioridad |
|---|---|---|---|
| Lenguaje y SDK | Kotlin, Android Studio, Gradle Kotlin DSL | Desarrollo nativo, mantenimiento y ecosistema Android. | Base |
| Interfaz | Jetpack Compose + Material 3 + `BlockNizeTheme` | Permite tema AMOLED de negro puro y blanco, con roles de acento personalizados. **No** adoptar el color dinámico Material You automáticamente. | Base |
| Navegación | Navigation Compose o Navigation 3 compatible con el proyecto | Pantallas tipadas, navegación centralizada; decidir API al crear el proyecto. | Base |
| Persistencia principal | Room sobre SQLite | Consultas por fecha, relaciones, integridad y transacciones; **guardar también la selección activa** junto a las plantillas. | Base |
| Preferencias pequeñas | Preferences DataStore | Guardar **un único color de acento opaco elegido por paleta o selector personalizado** (y otras opciones visuales futuras); exponer `Flow`. **NO** guardar aquí el ID de plantilla activa, que sigue en Room. | Base |
| Asincronía | Kotlin Coroutines + Flow/StateFlow | Datos reactivos entre repositorios y pantallas. | Base |
| Estado de pantalla | ViewModel + `collectAsStateWithLifecycle()` | UI resistente a recreaciones y estados de carga/error. | Base |
| Inyección de dependencias | Inicialmente manual o Hilt cuando crezca el proyecto | Evitar complejidad prematura; Hilt aporta testabilidad al aumentar capas. | Opcional |
| Formato de intercambio | `kotlinx.serialization` + JSON | Modelo legible, validable, portable y versionable. | Alta |
| Acceso a archivos | Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) | El usuario decide dónde guardar/abrir sin solicitar acceso general al almacenamiento. | Alta |
| Widget | Jetpack Glance + `GlanceAppWidgetReceiver` | API oficial declarativa para widgets. | Alta, después del núcleo |
| Trabajo diferible | WorkManager | Copias diferidas o refresco moderado del widget, sin precisión de reloj. | Condicional |
| Alarmas exactas | AlarmManager, solo si se aprueban recordatorios precisos | Permisos y restricciones de Android; no introducir en MVP por un simple progreso visual. | Fuera del MVP |
| Pruebas | JUnit + pruebas instrumentadas Compose/Room | Verificación de recurrencias, consultas, formularios, exportación y widget. | Continua |
| Control de código | Git, repositorio privado o público según preferencia | Historial, ramas cortas, recuperación y control de cambios. | Base |
| Gestión | Kanban + entregas verticales pequeñas | Facilita aprender Kotlin, implementar una función completa y revisarla. | Base |

### Decisión de almacenamiento: por qué no solo `.md` o `.json`

- **Markdown (`.md`)** es excelente para requisitos, decisiones, tareas de desarrollo y manuales. No conviene como base de datos editable por la app: no facilita integridad referencial, búsqueda de ocurrencias o escrituras concurrentes.
- **JSON** es excelente para transferencia, plantillas, exportaciones, datos iniciales o configuración estructurada pequeña. Usarlo como único archivo maestro requeriría leer y reescribir colecciones, desarrollar validación/consistencia y evitar corrupción por escrituras incompletas.
- **Room/SQLite** guarda de forma interna las tareas, horarios, estados y etiquetas; es la **fuente única de verdad**. La app genera/lee JSON cuando se importa/exporta; no trabaja permanentemente sobre esos archivos.
- **DataStore** guarda preferencias pequeñas. La plantilla activa es una referencia de dominio a una fila de Room, no una preferencia cosmética: se guarda en Room para validar claves foráneas y cambio transaccional. Android recomienda Room para datos grandes/complejos o que requieran integridad referencial.

**Propuesta definitiva para evaluación:** `Room + DataStore + JSON + Markdown` con responsabilidades separadas.

Referencias técnicas oficiales:

1. Arquitectura Android: https://developer.android.com/topic/architecture/recommendations
2. Jetpack Compose / Material 3: https://developer.android.com/develop/ui/compose/designsystems/material3
3. Room: https://developer.android.com/training/data-storage/room
4. Migraciones Room: https://developer.android.com/training/data-storage/room/migrating-db-versions
5. DataStore: https://developer.android.com/topic/libraries/architecture/datastore
6. Kotlin Serialization: https://kotlinlang.org/docs/serialization.html
7. Acceso a documentos: https://developer.android.com/training/data-storage/shared/documents-files
8. Widgets con Glance: https://developer.android.com/develop/ui/compose/glance/create-app-widget
9. Actualización de widgets: https://developer.android.com/develop/ui/compose/glance/glance-app-widget
10. WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent
11. Alarmas y permisos: https://developer.android.com/develop/background-work/services/alarms
12. Navegación Android: https://developer.android.com/guide/navigation

**Sobre las versiones:** consultar en el momento de crear el proyecto la versión estable compatible de Android Gradle Plugin, Kotlin, Compose BOM, Room, KSP y Glance. No copiar versiones incompatibles ni combinar APIs de Room 2/3 sin adaptar ejemplos. Guardar versiones en `gradle/libs.versions.toml`.

## 6.1 Sistema de diseño AMOLED — especificación de implementación v0.7

### 6.1.1. Principios visuales confirmados

| Rol UI | Valor/criterio | Estado |
|---|---|---|
| Fondo de todas las pantallas principales | `#000000` negro puro | **Confirmado** |
| Texto e iconos principales | `#FFFFFF` blanco | **Confirmado** |
| Color de acento | Elegido por el usuario desde Configuración | **Confirmado** |
| Dos maneras de elegir acento | Muestras predefinidas **y** selector de color personalizado | **Confirmado v0.6** |
| Tarjetas / diálogos | Negro o gris casi negro (por ejemplo `#101010`) + borde sutil | Propuesto |
| Texto secundario | Gris neutro con contraste apropiado (por ejemplo `#B8B8B8`) | Propuesto |
| Divisores | Gris muy oscuro (por ejemplo `#292929`) | Propuesto |
| Color inicial | Violeta `#A78BFA` | **Confirmado v0.7** |
| Cantidad y demás colores predefinidos | Propuesta inicial más abajo | Pendiente |
| Selector de color libre | Puede elegir cualquier color opaco sRGB | **Confirmado** |
| Tipo de control del selector | HSV/HSB o equivalente, preview + Aplicar/Cancelar | Propuesto |

**Regla:** AMOLED no obliga a que todos los contenedores sean negro puro; usar superficies discretas permite jerarquía, siempre manteniendo el lienzo principal `#000000`. El acento será un elemento puntual, no el fondo de toda la pantalla. No introducir Material You dinámico ni sobrescribir la personalización con el tema del móvil.

### 6.1.2. Paleta de muestras — valores de partida, NO definitivos

**[PROPUESTA]** Ocho opciones para el primer prototipo. No son decisiones confirmadas del usuario; se pueden modificar sin tocar el modelo de datos. Todas tienen contraste superior a 4.5:1 frente a `#000000` cuando se usan en su forma opaca (verificar con cálculo automatizado, no solo observación).

| Identificador | Nombre visible | HEX |
|---|---|---|
| `violet` | Violeta | `#A78BFA` |
| `sky` | Celeste | `#38BDF8` |
| `teal` | Turquesa | `#2DD4BF` |
| `orange` | Naranja | `#FB923C` |
| `green` | Verde | `#4ADE80` |
| `rose` | Rosa coral | `#FB7185` |
| `yellow` | Amarillo | `#FACC15` |
| `blue` | Azul | `#60A5FA` |

**[CONFIRMADO v0.7]** Violeta (`#A78BFA`) es el valor inicial para nuevas instalaciones y para «Restablecer». **[PROPUESTA]** La UI señala la muestra vigente con un aro blanco y un icono `check`, no solo un cambio de tonalidad. El control «Color personalizado» está al mismo nivel que las muestras y muestra el último valor si no coincide con una muestra. Cada muestra expone descripción accesible como «Turquesa, seleccionado».

### 6.1.3. Flujo del selector personalizado

1. En **Configuración → Apariencia**, tocar «Color personalizado».
2. Se abre un diálogo/pantalla con el **color actualmente persistido** como valor inicial y un borrador visual **no persistido**.
3. El usuario puede desplazar el selector de color (por ejemplo HSV, control de matiz + saturación/luminosidad) y ver el resultado en una tarjeta de muestra AMOLED con botón, etiqueta y anillo de progreso. **[PROPUESTA]** Mostrar y permitir introducir `#RRGGBB` directamente; esta entrada manual es una comodidad añadida, no el sustituto del selector libre.
4. Si el texto HEX tiene longitud incorrecta o caracteres ajenos a `[0-9A-F]`, mostrar error y deshabilitar **Aplicar**; los controles visuales nunca generan valores inválidos.
5. **Aplicar** valida, normaliza a `#RRGGBB` y ejecuta `SettingsRepository.setAccentColor()`. Solo entonces cambia el tema global y se solicita un refresco de widget.
6. **Cancelar** o cerrar sin aplicar descarta el borrador: el acento persistido, las otras pantallas y el widget permanecen intactos. **[PROPUESTA]** Cerrar mediante atrás equivale a cancelar.
7. La paleta de muestras fuera del selector usa una ruta de acción rápida: tocar → persistir → actualizar sin reinicio. Se indica visualmente cuál está seleccionada y si se produce un error de guardado.
8. **[PROPUESTA]** «Restablecer» selecciona el violeta `#A78BFA` **[CONFIRMADO v0.7]**, sin tocar la agenda ni el historial.

**No confundir** la vista previa local del diálogo con un cambio global confirmado. De lo contrario, cancelar no restauraría de forma coherente el widget y las pantallas.

### 6.1.4. Persistencia de datos y estrategia de tema

- Mantener `AppAppearance(accentColorArgb: Long)` y la preferencia **única** de DataStore. Si no hay valor almacenado, utilizar **`0xFFA78BFA`** (**violeta confirmado**), sin escribir un valor redundante en cada arranque. Normalizar cualquier entrada a ARGB opaco de 32 bits: `0xFFRRGGBB`. Proponer nombre de clave `accent_argb`; si ya existe en código, migrar o reutilizarla, no duplicarla.
- No almacenar en Room colores personales ni la fuente de elección: no afectan a rutinas, completados, revisiones ni eventos. El JSON de **plantilla semanal** tampoco contiene el acento global.
- Para marcar la selección, comparar el HEX persistido con el listado de colores predefinidos; si no coincide, marcar «Personalizado». No es necesario persistir `selectedPresetId`. Si se eligió manualmente un color idéntico a una muestra, se mostrará dicha muestra como seleccionada (comportamiento propuesto).
- `SettingsRepository.observeAppearance(): Flow<AppAppearance>` permite que la composición raíz observe cambios de DataStore. `SettingsViewModel` expone preferencias, estados de validación/error y acciones `choosePreset`, `applyCustom`, `cancelCustom` (cancelar solo modifica estado local).
- `BlockNizeTheme` recibe el valor persistido y construye un `darkColorScheme` con `background=Color.Black`, `surface=Color.Black`, `onBackground=Color.White`, `onSurface=Color.White`. Completar expresamente `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `surfaceContainer*`, `surfaceTint`, `outline`, `scrim`, estados deshabilitados, diálogos y barras del sistema.
- Evitar usar `dynamicDarkColorScheme(context)` o `isSystemInDarkTheme()` para seleccionar el esquema. Ajustar barra de estado/navegación y edge-to-edge por versión de Android sin sustituir la estética acordada.
- Componentes compartidos: `AccentSwatch`, `CustomAccentPicker`, `AccentPreviewCard`, `ColorHexField`, `TimeProgressRing`, `BlockCard` y `BlockNizeTheme` (nombres orientativos).
- Evitar dependencias nuevas solo para un selector de color; evaluar componentes compatibles con Compose, mantenimiento y licencias antes de agregar librería. Si se implementa selector HSV manual, probar conversión HSV↔RGB y eventos táctiles/accesibilidad.

**Código orientativo (no es una implementación completa del tema ni del selector):**

```kotlin
// Ejemplo de modelo de UI/dominio, no entidad Room.
data class AppAppearance(val accentColorArgb: Long)

data class AccentPreset(val id: String, val label: String, val argb: Long)

interface SettingsRepository {
    fun observeAppearance(): Flow<AppAppearance>
    suspend fun setAccentColor(argb: Long) // valida 0xFFRRGGBB
}

// Validación de un HEX personalizado de seis dígitos sin alfa.
fun parseOpaqueRgb(input: String): Long? {
    val normalized = input.trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(normalized)) return null
    return 0xFF000000L or normalized.toLong(16)
}

@Composable
fun BlockNizeTheme(appearance: AppAppearance, content: @Composable () -> Unit) {
    val selected = Color(appearance.accentColorArgb.toInt())
    val scheme = darkColorScheme(
        primary = selected, // solo para rol/uso que mantenga contraste adecuado
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
```

**Aviso importante:** para colores oscuros escogidos libremente, `primary=selected` no basta. La implementación final debe resolver **contraste** y completar roles de texto de primer plano. El código solo muestra el flujo de estado y conversión de datos; no es código de producción listo para pegar.

### 6.1.5. Contraste y colores de acento muy oscuros

- **[PROPUESTA]** Garantizar una relación de contraste ≥ 4.5:1 para texto normal y ≥ 3:1 para indicadores/componentes no textuales significativos, cuando sea aplicable. Las muestras sugeridas se diseñan para destacar sobre negro.
- En controles rellenos por el acento, calcular `onPrimary` en blanco o negro según el mejor contraste. **Nunca** usar siempre texto blanco sobre colores claros de la paleta.
- Para colores personalizados oscuros como `#101010` o `#000000`, guardar exactamente la elección del usuario (no «corregir» DataStore sin avisar). Mostrar la muestra fiel en una superficie contrastada mediante borde blanco; enseñar un aviso «Puede tener poco contraste sobre negro».
- Cuando el acento elegido no resulte visible en un rol crítico, usar un `accessibleAccent` o borde/icono blanco **derivado para ese componente**, no alterar la preferencia ni convertir toda la app a otro tema. Las comprobaciones de contraste deben ser automáticas y acompañarse de revisión visual. Texto esencial sigue blanco/gris legible.
- Los estados «completada», «activa», «error», «seleccionada» y «sin actividad» incluyen iconos, texto o forma; no dependen solo de la tonalidad.
- Respetar tamaños táctiles, lector de pantalla, alto contraste y escalado de fuente; probar acceso a muestras y control libre con TalkBack.

### 6.1.6. Widget AMOLED + acento elegido

- Glance no comparte directamente `MaterialTheme` de Compose; construir sus colores con APIs propias (`GlanceTheme` / `ColorProviders` o colores Glance explícitos) usando el **mismo ARGB persistido** de `SettingsRepository`.
- Al **confirmar** una muestra o un personalizado, tras persistir en DataStore solicitar `MyAppWidget().updateAll(context)`. **No** solicitar actualización por cada movimiento del borrador HSV ni por cada letra introducida en el HEX.
- El widget consulta la tarea actual como antes, no altera su lógica al cambiar acento. Si el color es oscuro, emplear iconografía blanca y/o un indicador adaptado para visibilidad.
- El launcher y Android pueden imponer tratamiento de esquinas/colores y diferir el refresco. No prometer reproducción perfecta en todos los launchers ni progreso por segundo.
- La importación/exportación de **plantillas** no modifica la preferencia de acento. **[PROPUESTA futura]** Una copia de seguridad *completa* sí podría incorporar preferencias globales si se define expresamente.

### 6.1.7. Referencias técnicas oficiales

- Material 3 / customización de `ColorScheme`: https://developer.android.com/develop/ui/compose/designsystems/material3
- Preferencias con DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Glance y tema: https://developer.android.com/develop/ui/compose/glance/theme
- Actualización de widgets: https://developer.android.com/develop/ui/compose/glance/glance-app-widget

**Distinción:** Android proporciona Compose, las APIs de temas, DataStore y Glance; **la combinación de paleta + selector libre es requisito propio de BlockNize**, mientras que la paleta exacta, el control HSV, el campo HEX y Aplicar/Cancelar son propuestas de diseño concretas.

## 7. Arquitectura de código

**[PROPUESTA]** Una única `Activity`, Compose y una arquitectura por capas con flujo unidireccional:

```text
Pantalla Compose
      │ eventos (guardar, completar, cambiar día)
      ▼
ViewModel (UiState + StateFlow)
      │
      ▼
Casos de uso de dominio (cuando aportan valor)
      │
      ▼
Repository (contrato + implementación)
      ├── Room DAO / Base de datos
      ├── DataStore
      └── Import/Export JSON con SAF

Widget ──► caso de uso de bloque actual ──► Repository
```

**Reglas:**

1. La UI nunca ejecuta SQL ni lee archivos directamente.
2. Los ViewModels no almacenan `Activity` ni `Context` como estado permanente.
3. Cada pantalla expone `UiState` (cargando / datos / error) y eventos concretos.
4. Los casos de uso de recurrencias se prueban sin interfaz ni Android cuando sea posible.
5. Se reutiliza un mismo motor de agenda para Inicio, Calendario, Registro y Widget: vista actual/futura a partir de la única activa; vista histórica a partir de eventos temporales + revisiones inmutables. **No consultar la selección actual para inventar la programación de ayer**.
6. Las operaciones de importación son transaccionales o se validan completamente antes de aplicar cambios.
7. Se guarda la zona horaria con cuidado: reglas semanales usan **hora local**, y los registros de finalización guardan un instante UTC.
8. La **selección activa vive en Room**, en **una única fila** referida por clave foránea a `RoutineTemplate`; no duplicarla como `isActive` en muchas plantillas ni como ID en DataStore.
9. La acción **activar** pasa por `ActivateTemplateUseCase` y una operación atómica del repositorio: validar existencia/no archivada → obtener un `Instant` único de `Clock` → persistir revisión inmutable del contenido aplicado (reutilizar la existente si coincide) → actualizar selección → registrar evento obligatorio de activación → confirmar. Si falla, la plantilla anterior sigue activa y no queda evento incompleto.
10. `ObserveActiveTemplateUseCase` expone `Flow` al ViewModel de Inicio/Calendario; el Widget consulta el mismo repositorio y se solicita su actualización **después del commit** (sin garantía de repintado instantáneo del launcher). Un único `Clock` inyectable controla pruebas de corte temporal.
11. Separa **plantilla seleccionada para editar** (estado de la UI) de **plantilla activa** (estado persistido en Room). Nunca activar por entrar a un editor.
12. **Guardado inmediato** de una activa: `saveTemplateChanges` actualiza datos editables + snapshot + evento `ACTIVE_EDIT` + revisión vigente en **una sola transacción**; después refresca widget y pantallas. No guardar instantáneamente mientras se teclea.
13. `SettingsRepository` usa **DataStore**, independiente de Room, para observar **un único acento AMOLED**, seleccionado por muestras o selector libre. `BlockNizeTheme` recompone la UI tras persistir; solicitar aparte actualización del widget con Glance. El borrador del selector libre permanece en `SettingsViewModel`/estado local y **no** se emite a DataStore antes de «Aplicar».

### Estructura sugerida

```text
BlockNize/
├── README.md
├── docs/
│   ├── 01-requisitos.md
│   ├── 02-arquitectura.md
│   ├── 03-modelo-datos.md
│   ├── 04-flujos-pantallas.md
│   ├── 05-decisiones-ADR.md
│   ├── 06-plan-pruebas.md
│   └── backlog.md
├── app/
│   └── src/
│       ├── main/java/com/blocknize/app/
│       │   ├── MainActivity.kt
│       │   ├── core/          # utilidades, calendario/reloj
│       │   ├── data/
│       │   │   ├── local/     # Room: entities, dao, database
│       │   │   ├── settings/  # DataStore
│       │   │   ├── transfer/  # importación/exportación JSON
│       │   │   └── repository/
│       │   ├── domain/
│       │   │   ├── model/
│       │   │   └── usecase/
│       │   ├── ui/
│       │   │   ├── theme/      # BlockNizeTheme (AMOLED)
│       │   │   ├── settings/   # Apariencia, paleta, selector libre, preview y HEX
│       │   │   ├── navigation/
│       │   │   ├── today/
│       │   │   ├── calendar/
│       │   │   ├── history/
│       │   │   ├── templates/  # lista, editor, duplicación y activación
│       │   │   ├── blocks/
│       │   │   ├── taskeditor/
│       │   │   ├── quicktask/
│       │   └── widget/
│       ├── test/              # pruebas de lógica, serialización
│       └── androidTest/       # Room, Compose y flujos reales
├── gradle/libs.versions.toml
└── .gitignore
```

Se recomienda **un solo módulo `app` al empezar**, no varios módulos Gradle por defecto. La separación por paquetes será suficiente para una primera versión.

## 8. Propuesta de modelo de datos

> **Esquema conceptual, no código de Room final.** Los campos exactos se validarán al resolver las preguntas abiertas. Usar UUID en entidades exportables para facilitar importaciones entre dispositivos.

### 8.1 `Tag`

`id: String (UUID)`, `name: String`, `colorArgb: Long` o representación hexadecimal, `createdAt: Instant`.

### 8.2 `TaskDefinition`

`id`, `templateId?`, `title`, `description?`, `tagId?`, `createdAt`, `updatedAt`, `archived`.

La tarea describe la actividad, no su periodicidad. Una misma tarea puede tener varias programaciones **dentro de su plantilla**. **[PROPUESTA]** Las definiciones de tareas recurrentes pertenecen a una única plantilla (`templateId` obligatorio); las temporales pueden tener `templateId = null`. Si se duplica una semana, también se copian sus tareas y referencias, evitando que editar una tarea de una plantilla cambie otra por accidente. Las etiquetas sí pueden compartirse globalmente.

### 8.3 `ScheduleBlock`

`id`, `templateId?`, `taskId`, `type: WEEKLY | ONE_OFF`, `startLocalTime`, `endLocalTime`, `weekDaysMask?`, `specificDate?`, `activeFrom?`, `activeUntil?`, `enabled`, `createdAt`, `updatedAt`.

**Invariantes:**

- `WEEKLY` exige **`templateId` válido**, uno o más días de semana y no necesita fecha específica. Su tarea debe pertenecer a esa misma plantilla.
- `ONE_OFF` exige fecha específica y **`templateId = null`**: una tarea temporal no edita la semana tipo.
- La plantilla abarca los siete días, pero cada bloque puede recurrir solo en los días deseados. Un día sin bloques es válido.
- Para el MVP: intervalos dentro del mismo día (`start < end`); los nocturnos se aplazan hasta decidir cómo deben visualizarse.
- Las programaciones pueden solaparse; la interfaz debe informar, no modificar silenciosamente.

### 8.4 `OccurrenceOverride` (excepciones)

`id`, `blockId`, `date`, `type: CANCEL | TIME_CHANGE`, `newStart?`, `newEnd?`.

**[PROPUESTA para fase posterior]** Permite saltar un día o mover solo una ocurrencia sin alterar la semana recurrente. No imprescindible para la primera pantalla, pero conviene reservarlo en el diseño.

### 8.5 `CompletionRecord`

`id`, `occurrenceKey`, `sourceBlockId`, `occurrenceDate`, `status: COMPLETED`, `completedAt: Instant`, `taskTitleSnapshot`, `startTimeSnapshot`, `endTimeSnapshot`, `sourceTemplateId?`, `sourceRevisionId?`, `sourceEventId?`. Para temporales, la identidad usa su ID de bloque de fecha concreta.

Añadir índice único sobre `occurrenceKey`, no solo `(blockId, date)`: el usuario podría activar A → B → A el mismo día y aparecer **dos tramos distintos** del mismo bloque. Propuesta de clave reproducible: `eventId + blockId + occurrenceDate` para semanales, y `oneOffId + occurrenceDate` para temporales. Desmarcar puede borrar el registro o registrar reversión según política futura. No confundir «pendiente», «interrumpida» ni «aún no iniciada».

**Advertencia:** las finalizaciones y revisiones históricas son independientes del estado de edición actual. Evitar `ON DELETE CASCADE` desde plantillas/bloques a registros históricos. **[PROPUESTA]** Archivar o usar borrado lógico para plantillas ya usadas; no reescribir versiones pasadas al editar nombres, horas o etiquetas.

### 8.6 `RoutineTemplate` (semana completa)

**[CONFIRMADO]** Una plantilla representa los siete días L–D y pueden existir **varias**. Campos **[PROPUESTA]**: `id: UUID`, `name`, `description?`, `createdAt`, `updatedAt`, `archived`. Un nombre puede repetirse si se desea; los UUID son la identidad real.

Relaciones: `RoutineTemplate (1) → (N) TaskDefinition → (N) ScheduleBlock WEEKLY`. Las definiciones de tareas y sus bloques son propias de cada plantilla. Al duplicar una plantilla, crear nuevos IDs de plantilla/tareas/bloques para impedir modificaciones cruzadas. Las etiquetas pueden ser globales. No se necesitan siete filas vacías «día».

**No añadir `isActive` a cada `RoutineTemplate` como primera opción**: podrían existir dos filas marcadas como activas o ninguna por error si se actualizan separadamente. La selección activa se modela como relación singular.

### 8.6.1 `ActiveTemplateSelection` — **[PROPUESTA TÉCNICA]**

Una tabla Room con **una única fila lógica**, que representa la plantilla actualmente seleccionada para generar la agenda recurrente:

| Campo | Tipo / regla | Uso |
|---|---|---|
| `singletonId` | `Int`, PK fija `1` | Solo una selección activa para toda la app |
| `templateId` | `String`, FK a `RoutineTemplate.id`, no nulo | Plantilla activa |
| `activatedAt` | `Instant` / epoch UTC | Instante exacto del último cambio de activa |
| `revisionId` | `String`, FK a revisión inmutable | Contenido semanal exacto aplicado en la selección actual |

**Invariantes:**

1. Cero filas solo cuando todavía no existe selección/plantilla: estado «Sin plantilla activa»; nunca dos selecciones.
2. `templateId` debe corresponder a una plantilla existente y no archivada.
3. Al activar B se reemplaza **el valor de esa fila**, no se crea una segunda fila.
4. No archivar/borrar físicamente una activa sin antes resolver la selección.
5. **Room es la fuente única de verdad** de esa selección; DataStore se reserva para tema y preferencias independientes.
6. Para blindar el invariante incluso frente a escrituras manuales, añadir restricción `CHECK(singletonId = 1)` en el SQL de creación/migración cuando se cierre el esquema, además de encapsular escrituras en el repositorio.

La tabla singleton representa **lo activo AHORA**, no demuestra qué plantilla estuvo activa ayer. No confundir esa tabla con un historial de activación.

### 8.6.2 `TemplateRevision` — **[PROPUESTA TÉCNICA v0.4]**

Una **revisión inmutable** copia todo el contenido necesario para reconstruir la semana como era cuando entró en vigor: reglas L–D, franjas, títulos/descripciones, etiquetas (nombre/color), identificadores de origen y zona horaria/semántica de tiempo. Los datos editables de la plantilla actual no sustituyen esta copia. **No es un archivo de exportación**: es un registro interno en Room; puede serializarse con `kotlinx.serialization` a JSON versionado como *snapshot*.

| Campo | Tipo | Regla |
|---|---|---|
| `revisionId` | UUID, PK | Identificador único e inmutable |
| `templateId` | UUID, FK sin borrado en cascada | De qué plantilla procede |
| `createdAtUtc` | epoch millis | Momento de generación |
| `schemaVersion` | entero | Evolución de estructura |
| `payloadJson` | JSON validado | Copia autosuficiente de reglas y descripciones de los 7 días |
| `contentHash` | String | Permite evitar duplicados idénticos (optimización, no requisito) |

**Regla esencial:** editar una plantilla crea o prepara una revisión nueva; **nunca** modifica `payloadJson` de una revisión referenciada por el histórico. **[CONFIRMADO v0.5]** Cuando una edición de la plantilla **activa** se guarda, su nueva revisión se publica **inmediatamente** y se registra el evento de corte `ACTIVE_EDIT`. Nunca se reescribe una revisión previa. Las plantillas inactivas pueden editarse sin evento efectivo. Un guardado sin cambio semántico no necesita crear revisión/evento. No utilizar borradores pendientes de publicar para la plantilla activa.

### 8.6.3 `TemplateActivationEvent` / `ScheduleChangeEvent` — **[PROPUESTA TÉCNICA v0.4]**

Tabla append-only de cambios **efectivos**, no de cambios programados para después. Cada registro define el inicio de un intervalo temporal durante el que una revisión regía la rutina semanal.

| Campo | Tipo | Regla |
|---|---|---|
| `eventId` | UUID, `UNIQUE NOT NULL` | Identidad estable para vincular ocurrencias históricas |
| `sequence` | `INTEGER PRIMARY KEY AUTOINCREMENT` | Orden total de inserción; desempata cambios con igual milisegundo |
| `effectiveAtUtc` | Instant (epoch millis) | Corte **inmediato**; límite inferior inclusivo |
| `previousTemplateId` | UUID nullable | Trazabilidad |
| `templateId` | UUID, requerido en `ACTIVATION` | Nueva plantilla aplicada; nullable solo si se incorpora `DEACTIVATION` futura |
| `revisionId` | UUID FK, requerido en `ACTIVATION` | Versión exacta aplicada; nullable solo para `DEACTIVATION` futura |
| `zoneIdAtChange` | String (`Europe/Madrid`, etc.) | Interpretación histórica de horas locales |
| `changeType` | `ACTIVATION` / `ACTIVE_EDIT` / `DEACTIVATION` | `ACTIVATION` y aplicación inmediata de ediciones `ACTIVE_EDIT` **confirmados funcionalmente**; esquema de eventos propuesto; desactivación futura pendiente |

**Invariante:** a un mismo `Instant` no se solapan dos revisiones activas; se ordenan eventos por `(effectiveAtUtc, sequence)`. Un evento de A termina al comenzar el siguiente evento válido. **El registro no se borra ni edita**. Registrar **obligatoriamente** el primer cambio y cada cambio posterior, incluso si la app está cerrada el resto del día: permite reconstruir días pasados sin depender de haber visitado Calendario.

### 8.6.4 Activación atómica con efecto inmediato — **[CONFIRMADO: cuándo / PROPUESTA: implementación]**

```text
ActivateTemplateUseCase(templateId)
  → repositorio.activateTemplate(templateId)
  → Room.transaction {
      now = Clock.instant() (exactamente una vez)
      target = buscar plantilla; validar existente y no archivada
      active = leer selección singleton con revisión
      si target.id == active.templateId → salir sin cambios ni nuevo evento
      revision = obtener/crear snapshot inmutable del contenido actual de target
      guardar TemplateActivationEvent(effectiveAtUtc=now, revisionId=revision.id,
                                     previousTemplateId=active?.templateId)
      upsert ActiveTemplateSelection(id=1, templateId=target.id,
                                     revisionId=revision.id, activatedAt=now)
    } // commit atómico
  → notificar cambio a los Flows y recalcular Inicio/Calendario/Registro
  → solicitar updateAll() del widget FUERA de la transacción
```

La acción debe ser **idempotente**: activar de nuevo A si A ya está activa no crea otro tramo histórico. La activación no modifica ni destruye reglas de otras plantillas ni tareas temporales. Una transacción fallida revierte selección, revisión y evento. Si `Room` reemite `Flow`, la UI se vuelve a calcular; el **paso del tiempo** también exige un reloj/temporizador mientras la app está visible y una actualización al regresar a primer plano. No basta con escuchar cambios de base de datos. Para Room, el mecanismo de transacción concreto se escogerá según la versión estable usada (p. ej. `withTransaction` o transacción del driver), sin mezclar APIs incompatibles.

**Primera ejecución [PROPUESTA]:** si no hay plantillas, ofrecer «Crear mi primera semana»; si ninguna está activa, pedir elegir. No simular una activación en el pasado.

### 8.6.5 Política de calendario e historial tras el cambio

**Al instante `T` de activar B en sustitución de A (o de guardar una revisión nueva de la misma A):**

1. **Pantalla Inicio / Widget:** se elimina A del **plan recurrente vigente** y entra B para la fecha de hoy. Si una regla de B cubre el instante T aunque empezara por reloj antes de T, se considera **en curso** desde T; el porcentaje del anillo se calcula respecto a su **franja original** (no desde T). Las temporales permanecen.
2. **Calendario, fecha futura:** consulta la B activa **en el momento de consultar**. No se crean asignaciones fijas de plantilla a semanas futuras. Si mañana se activa C, el futuro se previsualiza con C. No se altera la información ya consolidada del pasado.
3. **Calendario, hoy:** mostrar agenda recurrente actual de B + temporales. **[PROPUESTA UX]** indicar «La plantilla cambió a las HH:mm» y ofrecer «Ver actividad anterior de hoy»; no mezclar A como rutina actual ni ocultar sus completados.
4. **Calendario, fecha pasada:** reconstruir los **tramos en que A, B, etc. estuvieron realmente activas** ese día, usando eventos y revisiones, además de temporales. No aplicar la plantilla vigente hoy ni el contenido editado posteriormente.
5. **Registro:** cada finalización se conserva por `occurrenceKey`, con copia del título y la franja que vio el usuario. Para el resumen de ese día, generar también **ocurrencias históricas no completadas** desde la línea temporal: registrar solo completados sería insuficiente.
6. **Bloques de A previstos tras T y que no comenzaron:** se consideran **sustituidos**, no incumplidos; no se suman al denominador de tareas esperadas. Si excepcionalmente ya fueron marcados completados antes de T, se mantiene esa acción en Registro sin atribuirla a B.
7. **Bloques de A finalizados antes de T:** permanecen en el historial del día, tanto completados como pendientes previos. **[PROPUESTA]** Los que se encontraban en curso al llegar T y no estaban completados figuran como «interrumpidos por cambio», no como fallidos. Los bloques A sustituidos por B no siguen visibles como agenda activa.
8. **Cambios múltiples A → B → A el mismo día:** se crean segmentos distintos por evento; una finalización del primer tramo A no marca automáticamente el segundo. Si se desea cambiar este comportamiento se necesitará una decisión explícita.

**Ejemplo con miércoles 28/10/2026:** A = «Clases» con «Estudiar» 11:00–13:00 y «Gimnasio» 17:00–18:00; B = «Vacaciones» con «Pasear» 12:00–13:00 y «Leer» 16:00–17:00; cambio a las **12:15**.

| Elemento | Inicio / agenda vigente a las 12:15 | Registro / histórico de hoy |
|---|---|---|
| «Estudiar» A 11–13 | Ya no es tarea actual | Se conserva su tramo 11:00–12:15; si no completada, «interrumpida» [propuesta] |
| «Gimnasio» A 17–18 | No aparece | Sustituida antes de empezar; no cuenta como incumplida |
| «Pasear» B 12–13 | Aparece **en curso**; avance horario 25 % | Ocurrencia B desde 12:15 hasta 13:00, con franja original 12–13 |
| «Leer» B 16–17 | Aparece como futura | Queda programada para hoy |
| Temporal «Dentista» 18–18:30 | Sigue apareciendo | Su registro permanece independiente |

**Cálculo histórico propuesto:** para cada bloque semanal de una revisión, expandir su intervalo local a `Instant` y calcular la intersección con el intervalo de vigencia `[effectiveAtUtc, nextEffectiveAtUtc)`. Conservar únicamente intersecciones de duración positiva. Las reglas que no llegaron a entrar en vigor (empezaban después de la desactivación) no generan un incumplimiento. Para solapes internos de la misma plantilla se muestran ambos bloques, sujeto a la política de solapes todavía pendiente.

**Zona horaria/DST:** conservar zona del evento; transformar `LocalDate + LocalTime + ZoneId` a instantes reales con pruebas de cambios de hora. La política de viajes/cambio de zona durante un día y las franjas nocturnas aún requiere decisión; no presentar ese caso como totalmente resuelto.

**Alcance de las decisiones del usuario:** confirmados tanto el efecto inmediato de **activar otra plantilla** como el de **guardar una edición de la activa**. El diseño concreto de revisión/eventos y las etiquetas «interrumpida»/«sustituida» son **recomendaciones** para lograr el resultado sin perder datos. Deben validarse en pruebas y se pueden simplificar en UI si se acuerda otra presentación, manteniendo integridad histórica.

### 8.6.6 Guardado inmediato de la plantilla activa — **[CONFIRMADO comportamiento / PROPUESTA técnica v0.5]**

**Regla exacta:** «inmediatamente» significa **después de pulsar Guardar y de que termine correctamente la transacción**, no mientras el usuario escribe. No existe un paso adicional de activación o publicación si la plantilla editada ya está activa.

```text
SaveTemplateChangesUseCase(templateId, cambiosValidados)
  → Validar campos, IDs, horario y pertenencia a plantilla
  → Room.transaction {
      actual = consultar plantilla editable y selección activa
      instante = Clock.instant() (una vez por operación efectiva)
      si no hay diferencias relevantes → no crear evento
      persistir las nuevas tareas/bloques/metadatos (misma plantilla)
      si templateId == selecciónActiva.templateId:
          snapshot = construir revisión inmutable COMPLETA de la semana resultante
          guardar TemplateRevision(snapshot)
          guardar TemplateActivationEvent(changeType=ACTIVE_EDIT,
                effectiveAtUtc=instante, revisionId=snapshot.id,
                templateId=templateId)
          actualizar ActiveTemplateSelection.revisionId = snapshot.id
          actualizar ActiveTemplateSelection.activatedAt = instante (*ver nota)
      si está inactiva:
          NO modificar selección ni crear evento efectivo
    } // commit
  → observar Room/Flow: refrescar Inicio, Hoy, Calendario y Registro
  → si estaba activa: solicitar updateAll() al widget fuera de transacción
```

*Nota de modelado:* en v0.5 es más preciso renombrar `ActiveTemplateSelection.activatedAt` como `effectiveAtUtc` o incorporar `lastEffectiveAtUtc`, porque una edición **no activa otra plantilla**, pero **sí** cambia cuándo empezó su revisión vigente. Conservar `lastActivatedAtUtc` por separado si se quiere conocer la activación original. El diagrama y las entidades de Room definitivas deben elegir una sola semántica sin sobrecargar el campo antiguo.

**Invariantes y casos:**

1. Si falla cualquier paso (validación, FK, revisión, evento, actualización de selección), rollback total: no se observa agenda parcialmente editada.
2. La revisión antigua y su evento permanecen intactos; el calendario pasado reconstruye la programación anterior incluso si se cambian horas, títulos o días de la semana.
3. Si el usuario edita a las **12:15** un bloque antes previsto de 12:00–13:00 para que pase a 12:30–13:30, desde 12:15 rige **la regla nueva**; a las 12:15 aún no está en curso, empezará a las 12:30. El tramo 12:00–12:15 original sigue en el histórico, sujeto a la política propuesta de interrumpidas.
4. Si el nuevo horario ya terminó antes de 12:15, no reescribir retrospectivamente los eventos del día. El registro anterior, incluidas finalizaciones, permanece.
5. Las finalizaciones se identifican por ocurrencia/evento histórico, para que guardar una edición no convierta automáticamente en completados todos los bloques equivalentes de la nueva revisión. El Registro del día puede mostrar completados anteriores como «actividad anterior» sin duplicarlos indebidamente en la agenda vigente.
6. Una edición en inactiva solo modifica su definición editable. Cuando se active más adelante, se generará un snapshot de su contenido entonces vigente.
7. Si el guardado no cambia el contenido efectivo (por ejemplo, pulsar Guardar sin editar), no insertar `ACTIVE_EDIT`. Para cambios de metadatos visibles, como renombrar la plantilla, **[PROPUESTA]** incluirlos en el snapshot y aplicar el mismo corte si afecta a cómo se representa la agenda histórica.
8. El widget se refresca **tras commit** de guardados efectivos y el guardado puede mostrar éxito aunque el launcher tarde en repintar; eso no altera la validez transaccional de Room.

Esta política no convierte todas las entradas del editor en cambios en vivo: el usuario puede cancelar el formulario antes de pulsar Guardar sin alterar nada.

### 8.7 Consultas / casos de uso importantes

- `observeTemplates(): Flow<List<RoutineTemplate>>` lista plantillas guardadas.
- `observeActiveTemplate(): Flow<RoutineTemplate?>` resuelve la selección singleton.
- `getTemplateForEditing(templateId)` carga cualquier plantilla, esté o no activa, sin cambiar estado.
- `activateTemplate(templateId)` hace el cambio transaccional, validando existencia, estado archivado y selección anterior.
- `saveTemplateChanges(templateId, changes)` valida y persiste la edición; si `templateId` está activa, publica revisión `ACTIVE_EDIT` y actualiza el plan **en ese mismo guardado**; si no, guarda inactiva sin evento efectivo.
- `duplicateTemplate(templateId)` crea nueva plantilla y nuevos IDs de tareas/bloques en una transacción; la copia nace **inactiva** [PROPUESTA].
- `getOccurrencesForDate(date, now)` devuelve: pasado → segmentos reconstruidos desde eventos/revisiones; hoy → agenda vigente + acceso a tramos anteriores; futuro → plantilla activa actual y temporales. Se usa `Clock` inyectable.
- `getCurrentOccurrence(now)` filtra `start <= now < end` y usa la selección activa; resuelve solapes según la política pendiente.
- `completeOccurrence(blockId, date, now)` escribe de forma idempotente; la finalización pertenece a la ocurrencia, no a la selección global.
- `getDaySummary(date)` distingue completadas, pendientes efectivas e interrumpidas y excluye de los incumplimientos las sustituidas antes de comenzar; usa revisiones/eventos y no solo la plantilla actual.
- `exportTemplate(templateId)` produce JSON de **una plantilla elegida** (activa o inactiva), sin guardar el estado de activación.
- `importTemplate(uri)` valida y crea **otra plantilla guardada** sin activar por sorpresa [PROPUESTA]; `importBackup(uri, mode)` sigue siendo independiente.

**Persistencia eficiente:** expandir reglas semanales a demanda, sin generar cientos de ocurrencias futuras ni una fila por día; guardar en cambio una revisión por cambio efectivo (cuando difiera el contenido) y eventos de **activación o edición activa**. Así se reconstruyen también días no abiertos en la app. Las consultas pueden cachearse sin reemplazar la fuente de verdad histórica.


## 9. Ejemplo de estructura JSON exportable

Este ejemplo es **ilustrativo de un contrato propuesto**, no del formato actual de la app ni de un esquema aprobado. Los identificadores son simbólicos para facilitar lectura.

```json
{
  "format": "blocknize",
  "schemaVersion": 1,
  "exportType": "template",
  "exportedAt": "2026-10-28T12:00:00Z",
  "template": {
    "id": "tpl-demo",
    "name": "Semana de estudio",
    "description": "Rutina semanal de ejemplo",
    "scope": "FULL_WEEK",
    "weekStartsOn": "MONDAY",
    "tags": [
      { "id": "tag-estudio", "name": "Estudio", "color": "#4F46E5" }
    ],
    "tasks": [
      {
        "id": "task-kotlin",
        "title": "Estudiar Kotlin",
        "description": "Repasar Compose",
        "tagId": "tag-estudio"
      }
    ],
    "blocks": [
      {
        "id": "block-1",
        "taskId": "task-kotlin",
        "kind": "WEEKLY",
        "weekDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
        "startTime": "17:00",
        "endTime": "18:30"
      }
    ]
  }
}
```

**Regla del formato propuesto:** `scope = FULL_WEEK` indica que la plantilla es semanal completa. El array `blocks` solo contiene bloques con sus días recurrentes; no es necesario incluir siete entradas de día si alguno está vacío. **Esto es una propuesta de contrato JSON**, no un esquema final aprobado. **El campo `active` no debe formar parte de una exportación individual**: se exporta contenido reutilizable, no el estado global del móvil de origen.

**Validaciones de importación obligatorias:**

1. `format` y `schemaVersion` conocidos; rechazo o migración explicada de versiones futuras.
2. Tamaño máximo de archivo y límites de elementos para evitar consumo excesivo de memoria.
3. Campos obligatorios (`scope = FULL_WEEK` para plantillas), formatos de fecha/hora, duración válida, días válidos sin duplicados, IDs únicos y referencias a tareas/etiquetas existentes en el paquete.
4. Validación **antes de guardar**; transacción atómica al aplicar.
5. Resolver conflictos: importar como copia (nuevos UUID), reemplazar con confirmación, o cancelar. Propuesta MVP: **importar como copia**, sin sobreescritura.
6. No importar rutas arbitrarias ni ejecutar contenido; el JSON se trata como datos no confiables.
7. Exportación de copia completa **por separado** de plantilla; incluir completados solo en copia completa si el usuario así lo decide.
8. Mostrar cuántas tareas, etiquetas y bloques se importarán.
9. Importar la plantilla sin cambiar el singleton de selección activa salvo activación explícita posterior [PROPUESTA].

**Implementación Android:** `ActivityResultContracts.CreateDocument("application/json")` y `OpenDocument()`, con `ContentResolver` para el URI recibido. No depender de una ruta física del archivo y no solicitar permisos amplios de almacenamiento para esta función.

## 9.1 Operaciones de plantilla (matriz de alcance)

| Operación | Estado actualizado a v0.5 | Comportamiento diseñado |
|---|---|---|
| Crear semana completa | **Confirmado** | L–D, días vacíos admitidos |
| Guardar múltiples semanas | **Confirmado** | Varias plantillas independientes en Room |
| Mantener solo una activa | **Confirmado** | Selector singleton; nunca mezcla de rutinas |
| Editar plantilla inactiva | **Confirmado por implicación directa** | Sin activar ni afectar a Inicio |
| Activar/cambiar plantilla | **Confirmado v0.4** | Cambio **inmediato** a la hora exacta de confirmación, también hoy |
| Añadir/editar bloque | Requisito + propuesta de implementación | Solo dentro de la plantilla editada |
| Guardar cambios sobre plantilla **activa** | **Confirmado v0.5** | Aplicar **inmediatamente** al guardar; snapshot/revisión y evento `ACTIVE_EDIT` propuestos para proteger historial |
| Guardar cambios sobre plantilla **inactiva** | **Confirmado por implicación de su estado** | Persistir edición sin modificar la programación vigente |
| Cambiar color de acento | **Confirmado v0.5** | Configuración → Apariencia; persiste en DataStore, separado de Room y del JSON de plantilla |
| Duplicar plantilla | Propuesta | Copia profunda con nuevos IDs; no activa automáticamente |
| Exportar plantilla elegida | Requisito | JSON con semana completa, tareas y etiquetas; sin estado `active` |
| Importar plantilla | Requisito + propuesta | Guardar como inactiva; activar solo tras acción explícita |
| Archivar plantilla | Propuesta | Proteger seleccionada activa e historial |
| Tarea temporal | **Requisito** | Independiente de cualquier plantilla; sigue visible al cambiar |
| Copia de seguridad completa | Propuesta | Incluye eventualmente activa e historial; formato distinto |

**Ejemplo:** guardadas «Clases» (activa), «Vacaciones» y «Exámenes» (inactivas). Hoy aparecen **solo** los bloques de «Clases» más la tarea temporal «Dentista». Al activar «Vacaciones», «Clases» sigue intacta. La tarea «Dentista» sigue en su fecha; la rutina de «Vacaciones» aparece **en ese momento**, mientras lo ya realizado bajo «Clases» queda registrado.


## 10. Lógica temporal, recurrencias y progreso

### 10.1 Recurrencia semanal

- Cada bloque recurrente pertenece a **una plantilla semanal** y tiene un conjunto de días y horas locales.
- Para hoy/futuro: leer **selección activa de Room**, filtrar reglas de esa plantilla, comprobar día/horario y excepciones, añadir temporales. Para fechas pasadas: reconstruir versiones/activaciones; **no** usar la activa de hoy. Un cambio de plantilla es un corte temporal exacto, no una sustitución retrospectiva del día entero.
- Comparar mediante `java.time` (`LocalDate`, `LocalTime`, `ZonedDateTime`, `Instant`, `ZoneId`) según la semántica del dato; no guardar todas las horas como cadenas arbitrarias.
- Actualizar la UI al entrar/salir de un bloque y al cambiar de día, sin depender únicamente de eventos de la base de datos.

### 10.2 Progreso temporal

```text
progreso = clamp((ahora - inicio) / (fin - inicio), 0, 1)
porcentaje = round(progreso * 100)
```

Ejemplo: un bloque de 10:00 a 11:00 a las 10:15 lleva **25 % de tiempo transcurrido**, independientemente de si se ha completado manualmente. **No confundir progreso horario con progreso de ejecución de la tarea.**

### 10.3 Casos límite para acordar

- Solapamientos: varios bloques activos; proponer seleccionar el que empezó más recientemente o mostrar un aviso. No decidir sin validación.
- Cruce de medianoche: aplazado en MVP, decidir si un bloque pertenece al día en que comienza.
- Cambio de horario de verano/invierno y zona horaria: recurrencias se interpretan en hora local de la zona elegida; registros de finalización en UTC.
- Si la hora del móvil cambia mientras la app está abierta, recalcular bloque y progreso.
- El pasado **no** se reescribe al cambiar la plantilla: el corte de activación es **inmediato** y se conserva la línea temporal de revisiones. - **[CONFIRMADO v0.5]** Toda edición guardada de la plantilla activa se aplica inmediatamente y genera una revisión/evento efectivo si hay cambios de contenido.
- Si el usuario completa una actividad antes de terminar su intervalo, el anillo **sigue mostrando el tiempo transcurrido** salvo que se acuerde otra regla.

## 11. Estrategia realista del widget

### Fase 1: widget funcional

- `GlanceAppWidget` y receiver declarado en `AndroidManifest.xml`.
- Cargar la actividad actual a través de un caso de uso compartido con la app.
- Mostrar título, horas, etiqueta y un círculo/anillo estático representando el porcentaje en el último refresco (evaluar si Glance permite el estilo deseado; si no, usar una representación compatible o `RemoteViews` como alternativa).
- Actualización inmediata al editar/completar desde la app y al pulsar una acción del widget.
- Actualización periódica moderada o cuando cambie el bloque; no asegurar exactitud al segundo.
- Registrar comportamiento al reiniciar teléfono, cambiar hora y activar ahorro de batería.

### Fase 2: investigación de precisión

Crear una prueba mínima en dispositivo real para medir refresco con la app cerrada y comprobar el dibujo del anillo. Los widgets no comparten el bucle de renderizado de Compose dentro de la Activity. `updatePeriodMillis` tiene un mínimo práctico de **30 minutos** y WorkManager periódico permite solicitar desde **15 minutos**, pero no garantiza ejecución a una hora exacta. Si el producto exige actualizaciones visuales muy frecuentes, reconsiderar UX/consumo y validar restricciones antes de implementarlo. **No asumir que AlarmManager concede actualizaciones permanentes ni permisos automáticamente.**

### Apunte para el widget tras activación, edición y tema v0.6

La selección y su revisión efectiva se persisten en Room y se notifican a la UI de inmediato. Tras el commit de activación o edición de la activa, solicitar `MyAppWidget().updateAll(context)`. Cuando el usuario **confirma** un color de acento (muestra o selector personalizado) en Configuración, guardarlo en DataStore y pedir también `updateAll(context)` para que Glance lea la preferencia nueva. **Previsualizar o cancelar un color personalizado no debe solicitar actualización**, porque no cambia la preferencia global. El **dato que lee el widget** corresponde a la revisión vigente inmediatamente después del commit; el **momento en que el launcher pinta de nuevo** puede depender del sistema. No hacer actualizaciones por segundo: no son una garantía de Android y consumen batería. Comprobar cambio con la app abierta y cerrada en pruebas reales.

## 12. Metodología y reglas de colaboración

**[PROPUESTA] Kanban ligero con desarrollo incremental, priorización MoSCoW y pequeños cortes funcionales.** En vez de programar todas las pantallas vacías y conectar los datos al final, completar cada funcionalidad de extremo a extremo: entidad → DAO → repositorio → caso de uso → ViewModel → pantalla → pruebas.

Estados del tablero:

`Ideas → Por definir → Preparado → En desarrollo → En pruebas → Terminado`

### Tarjeta de trabajo

Cada tarea técnica debería tener:

```md
## BNZ-XX — Título
- Objetivo:
- Requisito relacionado: RF-XX
- Dependencias:
- Criterios de aceptación:
- Pruebas previstas:
- Estado:
- Archivos afectados:
- Notas/decisiones:
```

### Definition of Done (terminado)

- Compila y ejecuta en emulador/dispositivo compatible.
- La funcionalidad tiene una ruta completa desde UI hasta persistencia, si corresponde.
- Maneja entradas inválidas, estados vacíos y fallos previsibles.
- Incluye pruebas relevantes (unitarias o instrumentadas).
- No rompe funciones anteriores y se verifica el historial de tareas.
- Documentación y decisiones actualizadas; cambios guardados en Git.

### Convenciones iniciales

- Kotlin idiomático, nombres de clases y funciones descriptivos; elegir nombres técnicos en inglés de manera consistente, textos visibles en `strings.xml` localizados en español.
- Un commit lógico por cambio; ramas cortas opcionales y revisión de diffs.
- No meter lógica de negocio en composables.
- No hacer llamadas a base de datos en el hilo principal.
- No añadir librerías por moda; justificar cada dependencia.
- No almacenar datos personales de prueba en el repositorio.

## 12.1 Estado de preparación para empezar a programar — v0.7

**[EVALUACIÓN DE PLANIFICACIÓN]** Hay suficiente definición funcional y técnica para crear el proyecto Kotlin y desarrollar el MVP por incrementos; no es necesario decidir toda la UX antes de implementar la estructura.

**Primer incremento (vertical y verificable):** proyecto Android Studio con Git y Compose; `BlockNizeTheme` negro puro/blanco y acento `#A78BFA`; navegación Inicio, Calendario, Registro, Bloques; Configuración con DataStore; pruebas iniciales y ejecución en emulador. A continuación se implementan Room, plantillas semanales, selección activa transaccional y calendario diario.

**No bloquear la entrega 0 por decisiones posteriores:** los solapes entre una tarea temporal y otra recurrente, los bloques que atraviesan medianoche, los detalles de visualización de historial y la animación/precisión del widget se definen al entrar en esas funcionalidades. Antes de publicar el MVP sí habrá que resolverlos o establecer límites explícitos. El identificador de paquete, versión de Android mínima soportada, SDK de compilación y versiones de dependencias se fijarán al crear el proyecto y se registrarán sin suponer que ya están confirmados.

**Prueba de diseño esencial:** primera apertura sin preferencias guardadas → color violeta; seleccionar muestra → guardar; seleccionar color personalizado → aplicar/cancelar; pulsar restablecer → vuelve a `#A78BFA`. El cambio de apariencia no crea eventos históricos ni altera plantillas.

## 13. Hoja de ruta ordenada por entregables

### Entrega 0 — Definición y base

**Objetivo:** reglas de producto claras y proyecto ejecutable.

- [x] Resolver concepto de plantilla (P1) y gestión de múltiples semanas con una sola activa (P1b).
- [x] Resolver **fecha efectiva**: activación inmediata (P1c).
- [ ] Validar la **propuesta técnica** para conservar historial y estados al sustituir tareas (P4); resolver solapes temporales (P3).
- [ ] Crear proyecto Android Studio en Kotlin con Compose y Material 3.
- [ ] Elegir `minSdk` y configuración de compilación según dispositivos objetivo (no fijados aún).
- [ ] Configurar Git, `.gitignore`, Version Catalog y estructura básica.
- [ ] Crear **BlockNizeTheme AMOLED** con fondo `#000000`, blanco y acento centralizado; Configuración con **paleta de muestras + selector libre**, vista previa, aplicar/cancelar, validación y persistencia por DataStore.
- [ ] Crear barra inferior Inicio / Calendario / Registro / Bloques.
- [ ] Crear modelo de dominio y primeras pruebas de fechas.

**Criterio de salida:** abre las cuatro pantallas vacías y pasan las pruebas iniciales.

### Entrega 1 — Base de datos, plantillas y tareas

- [ ] Entidades Room: plantillas, selección activa singleton, tareas, etiquetas, bloques, **TemplateRevision** y **TemplateActivationEvent**.
- [ ] DAO y repositories; migraciones versionadas desde el inicio.
- [ ] Gestión de plantillas: crear, listar, elegir para editar y **activar inmediatamente** mediante evento + revisión + selección en una única transacción.
- [ ] **Guardar ediciones de la activa con efecto inmediato**: transacción de edición + revisión + `ACTIVE_EDIT` + actualización de selección; guardados inactivos sin evento efectivo.
- [ ] Pruebas del invariante **máximo una activa**, cambio a mitad de bloque, reinicio, reversión transaccional, no contaminación entre plantillas.
- [ ] CRUD de tareas y etiquetas.
- [ ] CRUD de bloques semanales por días L–D.
- [ ] Formulario validado de tarea y programación.
- [ ] Pruebas de inserción, consulta, actualización y eliminación sin pérdida histórica.

**Criterio de salida:** se crean al menos dos semanas distintas, se editan sin interferencias, se activa una y solo sus bloques llegan al motor de agenda; el estado activo persiste tras reiniciar.

### Entrega 2 — Inicio y tareas temporales

- [ ] Generador de ocurrencias de una fecha.
- [ ] Pantalla Hoy ordenada por horario.
- [ ] Formulario y persistencia de tareas únicas por fecha.
- [ ] Marcar y desmarcar completado por ocurrencia.
- [ ] Estados «en curso» / «siguiente» y solapamientos.

**Criterio de salida:** hoy muestra semanales y temporales y permite completarlas.

### Entrega 3 — Calendario, Registro e historial

- [ ] Selección de fecha en Calendario.
- [ ] Resumen de programadas/completadas.
- [ ] Registro por día y detalle de finalizaciones.
- [ ] Implementar reconstrucción de fechas anteriores desde revisiones/eventos y preservar completados, pendientes históricos e interrupciones; aprobar la presentación final de estados con el responsable del proyecto.

**Criterio de salida:** consultar un día pasado mantiene un historial coherente.

### Entrega 4 — Portabilidad de plantillas

- [ ] Esquema JSON v1 de **plantilla semanal completa**, serializadores y validadores.
- [ ] Exportar archivo JSON mediante el selector de Android.
- [ ] Importar con previsualización y control de conflictos; verificar que no cambia la activa por accidente.
- [ ] Prueba de ida/vuelta: exportar → borrar en dispositivo de prueba → importar → comparar.
- [ ] Duplicación y archivado de plantillas si se aprueban en este alcance.
- [ ] Decidir si habrá además copia completa con registros.

**Criterio de salida:** una rutina de ejemplo pasa a otra instalación sin pérdidas.

### Entrega 5 — Widget y acabado

- [ ] Prototipo Glance para bloque actual y anillo de progreso.
- [ ] Sincronizar cambios relevantes de la base de datos con el widget, incluidos **guardados de la activa y cambios del color de acento**.
- [ ] Evaluar refresco periódico y consumo, con pruebas en móvil.
- [ ] Mejorar accesibilidad, tamaños de pantalla, contraste del **tema AMOLED**, consistencia del acento tanto de **muestras como personalizados**, avisos por tonos oscuros y mensajes de error.
- [ ] Ensayar pruebas de regresión y respaldo.

**Criterio de salida:** widget muestra datos correctos según las limitaciones acordadas.

## 14. Matriz mínima de pruebas

| ID | Escenario | Resultado esperado |
|---|---|---|
| T-01 | Crear tarea L/X/V en una plantilla semanal | Solo aparece esos tres días cuando se consulta esa plantilla |
| T-02 | Marcar completada el lunes | El miércoles sigue pendiente |
| T-03 | Desmarcar una ocurrencia | Registro e Inicio quedan sincronizados |
| T-04 | Crear temporal solo el jueves | No modifica la semana tipo |
| T-05 | Guardar una tarea con `fin <= inicio` | Error de formulario en MVP |
| T-06 | Dos bloques se solapan | Ambos persisten; regla de visualización explícita |
| T-07 | Cerrar/reabrir app | Tareas, etiquetas y completados persisten |
| T-08 | Exportar e importar JSON válido | Datos y relaciones se reconstruyen |
| T-09 | Importar JSON corrupto o versión desconocida | Error controlado; datos existentes intactos |
| T-10 | Importar dos veces el mismo archivo | No se sobrescribe sin aviso; copia o cancelación |
| T-11 | Editar un bloque con completados históricos | No se borran registros inadvertidamente |
| T-12 | A las 10:15 en bloque 10:00–11:00 | Progreso temporal 25 % |
| T-13 | Sin tarea en curso | Estado vacío correcto en Inicio y Widget |
| T-14 | Cambio de día en medianoche | Se recalcula fecha y ocurrencias |
| T-15 | Cambio de zona horaria / DST | No duplica ni pierde completados |
| T-16 | Reiniciar móvil con widget colocado | Widget recupera estado según refresco permitido |
| T-17 | Configuración y accesibilidad | Texto legible, controles etiquetados, contraste adecuado |
| T-18 | Crear plantilla vacía | Se reconocen los siete días sin exigir bloques en cada uno |
| T-19 | Copiar una plantilla y editar su tarea | No modifica la tarea de la plantilla original |
| T-20 | Exportar/importar plantilla semanal | Se recuperan sus bloques, tareas y etiquetas; no incluye temporales ni completados |
| T-21 | Activar A, luego B | Solo B rige la rutina recurrente; A continúa guardada |
| T-22 | Editar B estando A activa | Cambiar bloques de B no altera Inicio ni Widget basados en A |
| T-23 | Seleccionar B para editar | B no se activa sin acción explícita |
| T-24 | Reiniciar aplicación con B activa | B sigue activa y se renderiza al reabrir |
| T-25 | Intentar activar plantilla archivada/no existente | Error controlado; no cambia la activa |
| T-26 | Fallo en medio de la activación | Transacción revierte; la activa anterior se conserva |
| T-27 | Activar la que ya está activa | No crea filas duplicadas ni eventos redundantes |
| T-28 | Duplicar A | Copia con IDs propios y estado inactivo; A no cambia |
| T-29 | Importar plantilla mientras A está activa | A sigue activa; importada guardada e inactiva [propuesta] |
| T-30 | Exportar plantilla B inactiva | Se exporta íntegra sin estado `active` |
| T-31 | Cambiar A por B con temporal existente | Temporal conserva fecha y contenido |
| T-32 | Sin plantillas ni selección activa | Estado vacío estable, widget y Hoy sin rutina recurrente |
| T-33 | Archivar la activa | Obligación de elegir sustituta o confirmar «Sin plantilla»; no referencia inválida [propuesta] |
| T-34 | Consultar fecha anterior al cambio | Misma agenda histórica previa, aunque hoy esté activa otra plantilla |
| T-35 | Cambio de plantilla con widget instalado | Widget solicita refresco y usa la nueva selección activa |
| T-36 | Consultas simultáneas durante activación | Nunca se observa una mezcla incoherente de selecciones persistidas |

| T-37 | A activa, miércoles 12:15 cambiar a B | Inicio muestra B **inmediatamente**; bloques A dejan la agenda vigente |
| T-38 | Completar bloque A a las 10:00 y activar B a las 12:15 | Registro conserva finalización A con título/horario originales |
| T-39 | Bloque A 11–13 sin completar, activar B a las 12:15 | Se reconstruye tramo histórico de A 11–12:15, propuesto «interrumpido» |
| T-40 | A tenía bloque 17–18 no iniciado, activar B 12:15 | A 17–18 no cuenta como incumplida del día |
| T-41 | B tiene bloque 12–13, activarlo a las 12:15 | Aparece «en curso», progreso = 25 %, no 0 % |
| T-42 | Activar A → B → A el mismo día | Se reconstruyen tramos por evento, sin mezclar estados de completado |
| T-43 | Cerrar app tres días y reabrir calendario | Días anteriores reconstruibles a partir de eventos y revisiones, sin snapshots diarios obligatorios |
| T-44 | Editar plantilla inactiva tras haberla usado | Calendario histórico conserva el título/horario originales |
| T-45 | Fallar transacción tras insertar evento | Ni activa ni evento ni revisión quedan a medias |
| T-46 | Cambiar A por B con temporal y fecha futura | Temporal persiste; mañana/futuro proyecta B; ayer conserva A |
| T-47 | Mismo milisegundo en dos activaciones distintas | Orden temporal determinista mediante `sequence` |
| T-48 | Cambiar A por B con widget colocado | Se solicita actualización tras commit; widget no muestra A por nueva consulta, sujeto al refresco del launcher |
| T-49 | Completar anticipadamente una tarea A de esta tarde y cambiar a B al mediodía | El completado manual anticipado sigue en Registro pese a que su bloque se sustituya |

| T-50 | Guardar edición en B activa | Inicio/Calendario muestran B editada inmediatamente tras commit, sin «Publicar» |
| T-51 | Modificar bloque en curso en activa | Se conserva tramo histórico previo; nueva franja rige desde el instante de guardar |
| T-52 | Editar horario/días/título de activa, consultar ayer | Ayer conserva el contenido de revisión anterior; no se reescribe |
| T-53 | Guardar edición inactiva | No cambia revisión vigente, evento efectivo ni widget de agenda |
| T-54 | Guardar activa sin cambios | No se generan evento/revisión redundantes |
| T-55 | Fallar guardado entre modificar bloque y registrar `ACTIVE_EDIT` | Rollback de bloque, revisión, evento y selección; UI conserva estado anterior |
| T-56 | Guardar activa y reiniciar aplicación | La modificación persiste; se reconstruye agenda actual e historial |
| T-57 | Guardar activa con completados previos del día | Se conservan finalizaciones originales y no aparecen duplicadas |
| T-58 | Guardar nueva franja que ya acabó antes del instante de guardado | No aparece como tarea vigente ni se modifica retrospectivamente el pasado |
| T-59 | Cancelar formulario sin guardar | No hay cambios en Room, evento ni calendario |
| T-60 | Usar app con sistema en modo claro | La superficie principal sigue `#000000` y los textos principales blancos |
| T-61 | Cambiar color de acento en Configuración | Se actualizan pantallas inmediatamente sin modificar tareas |
| T-62 | Reiniciar aplicación tras elegir acento | Se restaura acento de DataStore correctamente |
| T-63 | Elegir acento con contraste insuficiente | Se evita contenido ilegible mediante color derivado/validación |
| T-64 | Mostrar etiqueta/completado/error con acento personalizado | No depende solo del color; hay texto o icono reconocible |
| T-65 | Cambiar acento con widget instalado | Se solicita refresco, conserva bloque correcto y aplica el color cuando Android redibuje |
| T-66 | Exportar una plantilla tras elegir acento | El JSON de plantilla no cambia por el ajuste visual global |
| T-67 | Editar activa mientras hay temporal | Temporal conserva fecha y estado; edición afecta solo a la rutina |
| T-68 | Guardar dos cambios efectivos muy próximos | Orden de eventos determinista; nunca aparece mezcla parcial en consultas |

| T-69 | Seleccionar un color de la paleta predefinida | Se aplica y persiste sin reinicio; muestra marcada por borde/icono además del color |
| T-70 | Abrir selector personalizado | Aparece con el color guardado y preview local; paleta no desaparece de Configuración |
| T-71 | Elegir un color personalizado que no pertenezca a la paleta y pulsar Aplicar | Se guarda su ARGB opaco y se utiliza en toda la app |
| T-72 | Cambiar el borrador y pulsar Cancelar / Atrás | La preferencia, las pantallas externas y el widget conservan el acento anterior |
| T-73 | Introducir `#a1b2c3` o `A1B2C3` en campo HEX propuesto | Se normaliza a `#A1B2C3`, se guarda con alfa `FF` y se renderiza |
| T-74 | Introducir HEX de 3 caracteres, 8 con alfa o caracteres no hexadecimales | Error visible; Aplicar deshabilitado; persistencia intacta |
| T-75 | Seleccionar personalizado `#000000` | El valor queda guardado; elementos críticos mantienen contraste mediante bordes/texto/variantes accesibles |
| T-76 | Seleccionar muestra clara `#FACC15` y mostrar botón relleno | Etiqueta del botón legible con `onPrimary` apropiado; no blanco ilegible por defecto |
| T-77 | Reiniciar aplicación con acento personalizado | Se recupera el color exacto desde DataStore; si no coincide con preset se marca Personalizado |
| T-78 | Aplicar color que coincide exactamente con una muestra | La muestra correspondiente aparece seleccionada; no se duplica una segunda preferencia |
| T-79 | Repetir un color ya seleccionado | No se genera refresco innecesario del widget ni edición de datos de agenda |
| T-80 | Cambiar varias veces controles del selector sin aplicar | No se escriben preferencias ni se pide `updateAll` hasta confirmar |
| T-81 | Confirmar acento con widget colocado | Se solicita `updateAll` tras persistencia; el launcher puede redibujar más tarde |
| T-82 | Elegir paleta/selector con lector de pantalla | TalkBack anuncia nombres, estado seleccionado, preview, aplicar/cancelar y errores |
| T-83 | Cambiar acento y consultar Agenda/Registro/JSON exportado | Las tareas, revisiones, completados y contenido de plantilla JSON permanecen idénticos |
| T-84 | Fallo de DataStore al aplicar color | Se informa del error y se conserva el color anterior, sin apariencia final falsa |
| T-85 | Cambiar tema claro/oscuro del sistema con acento personalizado | La app sigue usando negro puro y blanco; no aparece color dinámico inesperado |

**Pruebas unitarias:** expansión semanal, progreso, validadores y JSON.  
**Pruebas instrumentadas:** DAOs Room, navegación Compose, formularios, selector de archivos y widget.  
**Prueba manual:** al menos un dispositivo real, especialmente para el widget y su comportamiento en segundo plano.

## 15. Riesgos y mitigaciones

| Riesgo | Consecuencia | Mitigación |
|---|---|---|
| Modelar finalización en la tarea en vez de en la ocurrencia | Todas las repeticiones aparecen completadas | Clave `(blockId, date)` y pruebas |
| Cambiar recurrencias, editar la activa **o cambiar de plantilla** y perder el pasado | Registro histórico incorrecto | Guardar revisiones inmutables + eventos con instante exacto; finalizaciones independientes |
| Usar solo JSON como base de datos | Reescrituras y consistencia difíciles | Room como fuente de verdad |
| Guardar `isActive` en muchas filas o duplicar selección en DataStore | Dos activas, referencias huérfanas o inconsistencia | Tabla singleton con FK, transacción y lectura reactiva |
| Confundir «editar» con «activar» | Se cambia la agenda al abrir otra plantilla | Estado de editor distinto de selección activa; botón explícito |
| Importación que cambia la activa automáticamente | Agenda alterada sin intención | Importar inactiva [propuesta]; activación separada |
| Acento personalizado extremadamente oscuro o Material You sobrescribiendo AMOLED | Controles invisibles o estética acordada rota | Mantener valor elegido, derivados accesibles/bordes blancos, roles `onPrimary` calculados, `BlockNizeTheme` fijo y pruebas en ambos modos del sistema |
| Selector personalizado persiste mientras se arrastra o permite cancelación con guardado accidental | Preferencia inconsistente, widget repinta innecesariamente | Distinguir borrador vs. guardado, confirmar explícitamente, tests de cancelar y errores DataStore |
| Widget con progreso «en tiempo real» no viable | Batería, límites del sistema, expectativas incorrectas | Prototipo, refresco moderado y comportamiento documentado |
| Importar archivos inválidos | Datos corruptos o pérdida de información | Validación previa y transacciones |
| Demasiadas funciones simultáneas | Proyecto incompleto | MVP y entregas verticales |
| Uso confuso de «tarea» y «bloque» | UI/modelo inconsistentes | Glosario y ejemplos acordados |
| Horarios nocturnos o DST | Cálculo incorrecto de ocurrencias | Semántica explícita y tests antes de habilitarlos |

## 16. Preguntas pendientes, priorizadas

**P1. Alcance de plantilla [RESUELTO].** **[CONFIRMADO]** Una plantilla = semana completa de lunes a domingo; permite días vacíos.

**P1b. Cantidad y concurrencia [RESUELTO].** **[CONFIRMADO]** Se pueden guardar varias plantillas semanales, pero **solo una está activa a la vez**. No se combinan automáticamente ni se asignan plantillas distintas a semanas concretas del calendario.

**P1c. Momento de activación [RESUELTO v0.4].** **[CONFIRMADO]** Inmediatamente al activar, también en el día actual. Se conserva el instante exacto UTC y se recalcula la agenda sin retroactividad.

**P2. Relación tarea/bloque [ALTA].** ¿La misma tarea puede aparecer en varios bloques y horas diferentes? Propuesta: sí.

**P3. Temporal frente a rutina [ALTA].** Si coincide una cita temporal con «gimnasio», ¿ambas aparecen, se oculta el gimnasio o se pregunta? **Confirmado por alcance:** cambiar plantilla no afecta a temporales.

**P4. Edición y conservación histórica [ALTA; PARCIALMENTE RESUELTO].** **[CONFIRMADO v0.5]** Guardar cambios de una plantilla activa los aplica **inmediatamente**. **[PROPUESTA TÉCNICA]** `ACTIVE_EDIT` + revisión inmutable + selección actualizada en transacción conservan el pasado. **[PENDIENTE]** Aprobar únicamente la presentación UX de «interrumpida»/«sustituida» y las métricas de Registro; la fecha de aplicación de ediciones ya está resuelta.

**P5. Widget [ALTA].** ¿Se acepta progreso circular aproximado o se necesita animación frecuente cuando la app está cerrada? ¿Qué bloque gana en solapes?

**P6. Datos entre móviles [ALTA].** ¿Solo importación/exportación de plantillas o también historial y configuración mediante copia total?

**P7. Horarios nocturnos [MEDIA].** ¿Bloques como 23:00–07:00?

**P8. Registro [MEDIA].** ¿Solo completadas o también pendientes y estadísticas?

**P9. Notificaciones [MEDIA].** ¿Avisos al inicio de tareas?

**P10. Prioridad visual [PARCIALMENTE RESUELTO v0.6].** **[CONFIRMADO]** AMOLED negro puro/blanco; acento elegible tanto por paleta como por selector personalizado. **[PENDIENTE]** Si Inicio usa lista de tarjetas y Bloques cronograma, tamaños, iconografía y densidad visual.

**P11. Plataforma [MEDIA].** ¿Android mínimo objetivo?

**P12. Etiquetas [BAJA].** ¿Una sola por tarea o varias? Propuesta: una. **[CONFIRMADO]** Solo un acento visual global configurable; colores individuales de etiquetas no son obligatorios.

**P13. Acento [PARCIALMENTE RESUELTO v0.7].** **[CONFIRMADO]** Elegible desde Configuración mediante **paleta predefinida y selector libre personalizado**, ambos desde la primera versión; **valor inicial violeta `#A78BFA`**. **[PENDIENTE]** Cantidad/colores definitivos de las otras muestras y si el selector libre incorporará además campo HEX editable. **[PROPUESTA]** Ocho colores accesibles, selector HSV con vista previa, Aplicar/Cancelar y entrada HEX complementaria.

### Registro de decisiones (ADR)

| ID | Cuestión | Decisión / propuesta | Estado |
|---|---|---|---|
| ADR-001 | Framework UI | Jetpack Compose + Material 3 | Propuesto |
| ADR-002 | Base de datos | Room / SQLite | Propuesto |
| ADR-003 | Documentación / transporte | `.md` para docs, JSON para exportar | Propuesto |
| ADR-004 | Arquitectura | MVVM + repositorio + casos de uso selectivos | Propuesto |
| ADR-005 | Metodología | Kanban + entregas verticales | Propuesto |
| ADR-006 | Trabajo sin internet | Offline-first, sin backend inicial | Propuesto |
| ADR-007 | Widget | Glance, progreso aproximado a validar | Propuesto |
| ADR-008 | Superposiciones | Pendiente | Pendiente |
| ADR-009 | Unidad de plantilla | Semana completa L–D | **Confirmado v0.2** |
| ADR-010 | Persistencia del pasado | Historial de eventos + revisiones semanales inmutables + completados por ocurrencia | **Propuesto v0.4** |
| ADR-011 | Multiplicidad y estado activo | **Varias guardadas; una sola activa a la vez** | **Confirmado v0.3** |
| ADR-012 | Persistencia selección activa | Tabla Room singleton con FK y activación transaccional | **Propuesto v0.3** |
| ADR-013 | Importación | Importar como nueva plantilla inactiva | **Propuesto v0.3** |
| ADR-014 | Fecha efectiva de activación | **Inmediata desde el instante de confirmación**, incluso hoy | **Confirmado v0.4** |
| ADR-015 | Distinción editor/activa | Seleccionar para editar no activa | **Propuesto v0.3** |
| ADR-016 | Historial de activación | `TemplateActivationEvent` obligatorio + `TemplateRevision` inmutable en Room | **Propuesto v0.4** |
| ADR-017 | Corte temporal durante bloque | A en curso pasa a histórico interrumpido; B vigente aparece inmediatamente | **Propuesto v0.4** |
| ADR-018 | Calendario pasado/futuro | Pasado con eventos y revisiones; futuro con la activa actual | **Propuesto v0.4** |
| ADR-019 | Editar plantilla activa | **Se aplica inmediatamente al guardar**; revisión nueva + corte `ACTIVE_EDIT` para historial (implementación propuesta) | **Confirmado v0.5** (comportamiento) |
| ADR-020 | Identidad visual | AMOLED negro puro + blanco; acento global elegible desde Configuración | **Confirmado v0.5** |
| ADR-021 | Persistencia del acento | DataStore Preferences + `Flow` + `BlockNizeTheme` y Glance personalizado | **Propuesto v0.5** |
| ADR-022 | Color dinámico Material You | No sobrescribir negro puro/acento elegido con colores del sistema | **Propuesto para cumplir estética confirmada** |
| ADR-023 | Métodos de elección de acento | **Paleta predefinida Y selector libre personalizado** accesibles desde Configuración | **Confirmado v0.6** |
| ADR-024 | Flujo de selección de color | Paleta = aplicar al tocar; selector libre = borrador/preview → Aplicar o Cancelar | **Propuesto v0.6** |
| ADR-025 | Modelo y validación del color | Un ARGB opaco en DataStore, representación `#RRGGBB`, accesibilidad de variantes sin sobrescribir selección | **Propuesto v0.6** |
| ADR-026 | Acento predeterminado | **Violeta `#A78BFA`** en primera instalación y al restablecer apariencia | **Confirmado v0.7** |


## 17. Primeras instrucciones concretas de implementación

1. Mantener cerradas **ADR-009** (semana completa), **ADR-011** (múltiples, una activa) y **ADR-014** (cambio **inmediato**). No implementar combinaciones ni programación de plantillas por semanas concretas.
2. Modelar desde el inicio `TemplateRevision` y `TemplateActivationEvent` para que el pasado siga siendo fiable **sin abrir la app cada día**. **La edición activa se aplica al guardar** y crea `ACTIVE_EDIT`; queda por validar solo la UX de «interrumpida» y los solapes.
3. Crear proyecto Android Studio Kotlin + Compose, elegir identificador de paquete y `minSdk` con justificación, configurar Git y Version Catalog.
4. Montar las cuatro pantallas de navegación, gestión de plantillas y **Configuración → Apariencia → Color de acento (paleta y selector personalizado)**; usar **tema AMOLED negro/blanco con acento de DataStore** desde el inicio, no como parche al final. Implementar borrador/preview y validación HEX si se aprueba.
5. Modelar `RoutineTemplate`, `TaskDefinition`, `ScheduleBlock`, `Tag`, `TemplateRevision`, `TemplateActivationEvent` y selección singleton de Room; comprobar claves foráneas, integridad histórica e idempotencia.
6. Crear `TemplateRepository` con `observeTemplates`, `observeActiveTemplate`, `activateTemplate` y **`saveTemplateChanges`** (operaciones atómicas con revisión/evento `ACTIVE_EDIT` al guardar sobre la activa), `getTemplateForEditing` y `duplicateTemplate` (cuando se apruebe). Añadir `SettingsRepository` para el acento, totalmente independiente de la selección activa.
7. Probar primero **A activa → B activa a las 12:15 → reinicio → historial del mismo día**; después **editar B activa → guardar → efecto inmediato + corte histórico**, y comprobar que editar A inactiva no altera B. Después conectar formulario L–D.
8. Implementar generador de ocurrencias de hoy, tareas temporales independientes y completados por fecha.
9. Implementar motor de segmentos históricos con eventos/revisiones y pruebas de cambios múltiples, tareas incompletas y días sin abrir la app. Integrar Calendario y Registro con ese motor; diferenciar agenda vigente de pasado del día.
10. Implementar JSON de **plantilla individual**, previsualización, importación transaccional sin cambio automático de activa y exportación por SAF.
11. Construir widget reutilizando `getCurrentOccurrence`; pedir refresco después de activar otra plantilla, guardar edición efectiva o **confirmar** un nuevo acento desde muestras/selector, nunca durante un borrador. Medir limitaciones en dispositivo real.
12. Mantener backlog/ADR/pruebas y commits pequeños; no subir datos personales al repositorio.

**Criterio para empezar a programar:** confirmados semanas completas, múltiples plantillas, una única activa, **activación inmediata, edición activa efectiva al Guardar y estética AMOLED con paleta predefinida Y selector libre de acento**. Se puede implementar la ruta de guardado y el tema sin esperar más decisiones; quedan abiertos estados de historial, solapes, otros valores de paleta y detalles UX del selector. **El acento predeterminado violeta `#A78BFA` ya está confirmado.**


## 18. Resumen ejecutivo de decisiones recomendadas

**Producto confirmado (v0.7):** cada plantilla es una **semana completa L–D**. Se pueden guardar muchas, pero **solo una está activa a la vez**, **el cambio tiene efecto inmediato y guardar ediciones de la activa también**. Las restantes quedan disponibles para editar, activar y exportar; sus reglas no se mezclan con la agenda activa. Las tareas temporales son independientes.

**Arquitectura propuesta:** Kotlin + Compose + Material 3 con tema AMOLED fijo y **paleta + selector personalizado**; Room para tareas, bloques, plantillas, **selección singleton, revisiones inmutables y eventos `ACTIVATION`/`ACTIVE_EDIT`**; DataStore para **un único ARGB opaco del acento**; borrador de preview solo en UI; JSON para intercambio y Markdown para documentación. Una única transacción cambia la selección y escribe la revisión/evento; `Flow` propaga el resultado a la UI; se solicita refresco del widget tras el commit.

**Importante:** activar otra plantilla **no** equivale a borrarla, y elegir una plantilla en el editor **no** equivale a activarla. Importar una se recomienda como inactiva.

**Método:** Kanban, iteraciones verticales y pruebas automáticas de selección, continuidad de datos, portabilidad e historial.

**Decisiones abiertas prioritarias:** validar estados de tareas interrumpidas/sustituidas, coexistencia/solapes de temporales, precisión realista del widget, colores exactos por defecto y acabado del selector libre. **La presencia de los dos métodos de selección NO está pendiente.** **La fecha de activación y la aplicación de ediciones activas ya NO son pendientes.**


> **Documento v0.6:** decisiones confirmadas: **plantillas semanales completas**, **múltiples guardadas con una única activa**, **cambio inmediato**, **edición activa aplicada inmediatamente al Guardar** y **tema AMOLED negro/blanco con acento configurable por paleta predefinida Y selector personalizado**. El modelo de revisiones, eventos y estados de sustitución es una **propuesta de arquitectura** para proteger el historial. No se atribuyen al usuario decisiones que aún no ha tomado.

## 18.1 Contratos mínimos para implementar la activación y el historial (propuesta)

```kotlin
// Modelos de dominio ilustrativos, NO entidades Room listas para copiar.
data class ActivePlan(
    val templateId: String,
    val revisionId: String,
    val activatedAt: Instant
)

data class ActivationTransition(
    val eventId: String,
    val templateId: String,
    val revisionId: String,
    val effectiveAt: Instant,
    val sequence: Long
)

interface TemplateRepository {
    fun observeActivePlan(): Flow<ActivePlan?>
    suspend fun activateNow(templateId: String): ActivationTransition?
    suspend fun saveChangesNow(templateId: String, changes: TemplateChanges): ActivationTransition?
    suspend fun transitionsIntersecting(date: LocalDate): List<ActivationTransition>
    suspend fun loadRevision(revisionId: String): WeeklyPlanSnapshot
}

interface AgendaRepository {
    fun observeToday(): Flow<DayAgenda>
    suspend fun getProjectedAgenda(date: LocalDate): DayAgenda
    suspend fun getHistoricalAgenda(date: LocalDate): HistoricalDayAgenda
}
```

`activateNow()` devuelve `null` cuando la plantilla solicitada ya estaba activa. `saveChangesNow()` devuelve transición `ACTIVE_EDIT` si hubo cambios efectivos sobre la activa; para una inactiva guarda su contenido sin evento y devuelve `null`. El tipo `TemplateChanges` y el tipo concreto de transición se definirán al crear el repositorio. `WeeklyPlanSnapshot` es un DTO inmutable con los **siete días** y todos los datos necesarios para interpretar las reglas. Implementar después las entidades `@Entity`, los mapeadores y los DAO específicos, manteniendo consistentes los tipos de tiempo en `Instant` y los horarios civiles en `LocalTime`.

**Algoritmo orientativo de `getHistoricalAgenda(date)`:**

1. Determinar el intervalo `[inicioDelDía, inicioDelDíaSiguiente)` para la zona de consulta, sin asumir que siempre dura 24 h. Tratar tanto cambios `ACTIVATION` como **`ACTIVE_EDIT`** como cortes de vigencia.
2. Obtener el evento vigente al inicio del día y todos los cambios del intervalo, ordenados por instante y secuencia. Si no existe, el estado anterior es «sin plantilla».
3. Para cada tramo de vigencia, cargar su revisión (cacheable), expandir las reglas semanales que correspondan y recortar cada bloque por intersección temporal con el tramo.
4. Añadir tareas temporales de la fecha de manera independiente, sin duplicarlas en cada tramo.
5. Incorporar finalizaciones por `occurrenceKey` y sus snapshots. Conservar completados anticipados si existen aunque el bloque correspondiente no llegase a comenzar bajo su revisión.
6. Calcular el resumen sin contar como pendientes las reglas cuya intersección es vacía (p. ej. sustituidas antes de empezar); separar «interrumpidas» si tienen tramo parcial y quedaron sin finalizar.
7. Presentar ordenado por hora y origen de plantilla. Los eventos históricos no se reescriben por renombrar/editar la plantilla editable.

**Implicación para copias de seguridad:** exportar una **plantilla** sigue siendo JSON de la semana actual y no incluye historia **ni el color de acento**, ya provenga de una muestra o un selector personalizado, porque es una preferencia de la app. Exportar una **copia completa** (función futura) deberá incluir también selecciones, revisiones, eventos, completados, tareas temporales y zonas horarias, preservando sus IDs y orden de vigencia; importar una plantilla individual nunca restaura por accidente una plantilla activa.

## 19. Registro de cambios y referencias

### Cambios introducidos en v0.4 (histórico de versiones)

- Confirmado ADR-014: activar otra plantilla la hace efectiva **al instante**, incluso durante el día en curso.
- Reformulados RF-00, RF-01, RF-06, RF-07, lógica temporal, calendario y flujo de pantallas para distinguir **agenda vigente** de **histórico**.
- Definidos `TemplateRevision`, `TemplateActivationEvent` y extensión de `CompletionRecord` para no perder el pasado al cambiar o editar plantillas.
- Diseñada la transacción única selección + revisión + evento y el refresco poscommit del widget.
- Especificados estados propuestos «interrumpida»/«sustituida», tramos temporales, múltiples activaciones en un día, cambios de plantilla durante una franja, tareas temporales y fechas futuras.
- Ampliada la matriz de pruebas hasta **T-49**, incluyendo reconstrucción de fechas sin haber abierto la app.
- **En la versión 0.4** quedaban pendientes la edición de una plantilla activa, los solapes, horarios nocturnos, presentación final del Registro y precisión del widget. **La edición activa se resolvió en v0.5; no sigue pendiente.**

### Referencias oficiales para implementar el plan

- Room / API transaccional (revisar versión elegida): https://developer.android.com/reference/kotlin/androidx/room/RoomDatabase
- Room y consultas observables con `Flow`: https://developer.android.com/training/data-storage/room/async-queries
- Room / relaciones y migraciones: https://developer.android.com/training/data-storage/room/migrating-db-versions
- API del widget Glance y actualizaciones: https://developer.android.com/develop/ui/compose/glance/glance-app-widget
- Kotlin `java.time` / Android: https://developer.android.com/studio/write/java8-support
- DataStore (solo preferencias, no reemplaza a Room): https://developer.android.com/topic/libraries/architecture/datastore

### Cambios introducidos en v0.5 — 28/10/2026

- **Confirmado** ADR-019: cualquier modificación **guardada** en la plantilla activa se aplica de inmediato; sin publicar cambios posteriormente. Editar una inactiva no afecta al plan vigente.
- Definido el contrato `saveTemplateChanges` y la persistencia transaccional edición + revisión inmutable + `ACTIVE_EDIT` + cambio de revisión activa, conservando completados e historial.
- Distinguido guardar de «modificar mientras se escribe»; guardados sin cambios no producen eventos redundantes; rollback completo si hay error.
- **Confirmado** ADR-020: estética AMOLED con fondo negro puro, texto blanco y color de acento elegible en **Configuración**.
- Añadidos RF-10/RF-11, sistema de diseño `BlockNizeTheme`, `SettingsRepository` con DataStore, y política de actualización de Glance al cambiar acento.
- Actualizadas navegación, guía de implementación, hoja de ruta, matriz de pruebas **T-50 a T-68**, riesgos y preguntas pendientes; corregido que editar una activa ya no sea una decisión abierta.

**Distinguir decisión y propuesta (estado al cierre de v0.5):** en v0.5 no se había confirmado aún si habría selector libre. **Resuelto en v0.6:** paleta y selector libre son obligatorios; siguen sujetos a definición la paleta concreta, acento por defecto, campo de texto HEX adicional, estados de interrupción y detalles de implementación Room/Glance.

**Documentación oficial adicional:**

- Compose Material 3 / `ColorScheme`: https://developer.android.com/develop/ui/compose/designsystems/material3
- Preferences DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Temas de Glance: https://developer.android.com/develop/ui/compose/glance/theme
- Actualización de GlanceAppWidget: https://developer.android.com/develop/ui/compose/glance/glance-app-widget

### Cambios introducidos en v0.6 — 29/10/2026

- **Confirmado ADR-023 y RF-12:** selector de acento **mixto**, con **paleta predefinida y color personalizado libre**. Las dos opciones deben existir desde la primera implementación de Configuración; queda descartada la idea anterior de posponer el selector libre.
- Revisados RF-11, flujo de pantallas, principios AMOLED, arquitectura de `SettingsRepository`/`SettingsViewModel`, integración con DataStore/Compose y actualización de widget Glance.
- Definidos **como propuestas** ocho valores de muestras, HEX opcional, selector HSV o equivalente, vista previa local con «Aplicar/Cancelar», normalización a ARGB opaco, restablecimiento y política de contraste para acentos personalizados oscuros.
- Ampliada matriz de pruebas con **T-69 a T-85** para paleta, selector libre, validaciones, cancelación, persistencia, contraste, accesibilidad, widget y ausencia de efectos sobre plantillas/historial.
- Actualizados riesgos, hoja de ruta, instrucciones concretas, glosario, decisiones ADR y preguntas pendientes. La elección **paleta + selector libre** ya no es una cuestión abierta; sí lo son los colores definitivos y la UX precisa.

**Fuentes oficiales consultadas para los detalles técnicos de v0.6** (estas fuentes avalan APIs y capacidades de Android, no las decisiones específicas del producto):

- Material 3 en Compose, temas y `ColorScheme`: https://developer.android.com/develop/ui/compose/designsystems/material3
- DataStore y observación por `Flow`: https://developer.android.com/topic/libraries/architecture/datastore
- Tema de Glance: https://developer.android.com/develop/ui/compose/glance/theme
- Restricciones y refresco de App Widgets: https://developer.android.com/develop/ui/compose/glance/glance-app-widget

### Cambios introducidos en v0.7 — 29/10/2026

- **Confirmado ADR-026:** el color de acento por defecto es **violeta `#A78BFA`**; valor ARGB de fallback `0xFFA78BFA` en DataStore cuando no hay preferencia. La opción Restablecer devuelve este color.
- Retirado «acento inicial» de las preguntas pendientes; solo quedan por definir las otras muestras y detalles de la interacción del selector.
- Registrado el estado **listo para comenzar la implementación del MVP** y una ruta inicial verificable, separando decisiones todavía abiertas de bloqueos reales.
- Se mantiene que la precisión del widget, el tratamiento final de solapes, los bloques nocturnos y la UX exacta del historial deben validarse durante el desarrollo, antes de considerar el producto terminado.
