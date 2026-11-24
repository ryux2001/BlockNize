# Fase 3 — Modos de uso, cuentas y sincronización Android

Fecha de actualización: 17 de noviembre de 2026.
Estado: implementación parcial; el recorrido local-first está integrado en Android, las migraciones
de sync están aplicadas en Neon de desarrollo y el backend compila y responde en local. H3 todavía
no está aceptada.

Punto de continuación para retomar el trabajo: [Fase3_Punto_de_continuacion.md](Fase3_Punto_de_continuacion.md).

### Avance implementado

- Android: uso local sin cuenta por omisión y espacio Room aislado por cuenta. La base local
  `blocknize.db` se conserva; los espacios nube usan archivos separados derivados de la API y la
  identidad autenticada. Room está en v9 con migraciones, outbox, conflictos, versiones locales
  y estado visible de última sincronización/error.
- Android: Ajustes permite registro/login, conexión, sincronización manual, volver al espacio
  local y resolver conflictos. La conexión muestra una confirmación antes de importar; las
  cuentas con datos se abren aparte y no fusionan automáticamente la agenda local.
- Android: Neon Auth, JWT renovable y cookie/token protegidos con Android Keystore. Las
  operaciones de agenda y plantilla se guardan en Room y en la outbox en una transacción.
  WorkManager envía al recuperar red, al volver a primer plano y periódicamente.
- Android: aplica snapshots consistentes a Room y refresca el widget. Las acciones offline
  transportan instante/zona de origen para conservar completados, excepciones y cambios cuya
  fecha haya pasado antes de subirlos. Hay resolución explícita de conflictos de versión.
- Backend: cursor y commits idempotentes, `GET /sync/state`, `GET /sync/changes`,
  `GET /sync/bootstrap` y `POST /sync/import`. La importación inicial es atómica y solo admite
  una cuenta vacía (máximo 100.000 filas/32 MB). Las acciones principales conservan la hora
  de origen offline.
- Verificación: `npm run build` y aceptación HTTP (2/2) pasan; `/health/ready` confirma conexión
  con PostgreSQL. En un AVD se verificaron login Android, bootstrap y subida de una semana. La
  convergencia entre dos dispositivos y los casos offline siguen pendientes.
- Limitaciones abiertas: el cliente detecta cambios mediante cursor y vuelve a descargar el
  snapshot completo; el feed de `/sync/changes` todavía no transporta entidades. Los nuevos
  comandos ya conservan recibos idempotentes por dispositivo sin caducidad, pero faltan registro,
  generaciones/revocación de dispositivos, paginación/retención del feed y aceptación H3 en dos móviles.
- Las migraciones de sync `0001_brown_anthem.sql` y `0002_flaky_enchantress.sql` están aplicadas
  en Neon de desarrollo; el backend no está enlazado desde una APK publicada.

## 1. Objetivo y resultado esperado

BlockNize ofrecerá dos formas de uso elegidas por la persona:

| Modo | Cuenta | Red | Datos |
|---|---|---|---|
| Solo en este dispositivo | No requerida | No requerida | Agenda, historial y copias locales |
| Usar con la nube | Requerida para vincular y sincronizar | Necesaria para intercambiar cambios | Almacenamiento local y sincronización automática con la cuenta |

Tener conexión no activa la sincronización ni obliga a registrarse. Perder conexión no cambia
el modo elegido. Una sesión caducada pausa la sincronización y permite seguir trabajando localmente.

**Requisito de producto confirmado:** el uso sin cuenta conserva el funcionamiento actual.
La versión nube guarda cada cambio primero en local y registra su envío pendiente en la misma
transacción. Con conexión, sincroniza automáticamente con el servidor y descarga cambios de
otros dispositivos; sin conexión, sigue trabajando sobre los datos locales y acumula los envíos.
Cuando vuelve la conexión, reanuda automáticamente y los dispositivos de la cuenta convergen.
El botón manual es complementario; no hace falta pulsarlo para guardar en la nube.

El modo offline es un estado temporal de la versión nube, no una tercera versión que haya
que elegir ni una razón para cerrar sesión o crear otro conjunto de datos. En un dispositivo
nuevo, el primer acceso y la descarga inicial de la cuenta sí necesitan conexión.

**Entregable H3:** dos móviles con la misma cuenta convergen en agenda e historial después
de sincronizar, incluso tras trabajar sin red. Una instalación nueva puede completar todos
los flujos locales sin cuenta. Asociar, desconectar o cambiar de cuenta preserva los datos
y mantiene separados los datos y operaciones de cada cuenta.

## 2. Punto de partida y condiciones de entrada

- Punto de partida anterior a Fase 3: Android Kotlin/Compose, Room v5, DataStore, WorkManager,
  repositorio local y widget. La implementación actual conserva las doce tablas funcionales y
  añade estado/cola de sync, pero no incluye aislamiento remoto por fila ni sincronización por
  entidades incrementales.
- Las excepciones históricas locales tienen secuencia autoincremental sin `eventId` global.
  Las claves de ocurrencia contienen referencias a eventos; trasladar filas exige revisar esas claves.
- Backend: NestJS/Drizzle/PostgreSQL, identidad Neon Auth, UUID, versiones y escrituras
  transaccionales. La idempotencia HTTP actual conserva respuestas durante treinta días.
- Las secuencias PostgreSQL de eventos y la paginación de `/history` no son un cursor de sync.
- El servidor genera IDs en varias altas y usa la zona de cuenta; Android usa la del dispositivo.
- El cliente móvil aún no tiene pruebas de sesión caducada, recuperación del proceso ni
  convergencia real con dos dispositivos; el bootstrap es snapshot completo.

Se puede avanzar en diseño y prototipos mientras se cierran H1/H2. Antes de asociar datos
reales deben registrarse la aceptación física y recuperación de H1 y la aceptación restante
de H2: ciclo de sesión, aislamiento, reglas Android/servidor y persistencia/rollback. Conciliar
los informes de H2 que discrepan sobre la prueba de rollback; conservar la evidencia válida.

Las pruebas remotas usarán una rama de desarrollo y cuentas sintéticas. La cuenta reutilizable
de aceptación está descrita en `backend/test/Neon_Auth_Acceptance.md`; su existencia y acceso
deben comprobarse al ejecutar pruebas, sin copiar credenciales a documentos o logs.

## 3. Experiencia y transiciones

### 3.1 Inicio y ajustes

- En una instalación nueva, permitir entrar directamente en la agenda sin cuenta, tal como
  funciona actualmente. Ofrecer «Usar con la nube» desde Ajustes y como acceso opcional;
  esa elección abre registro/inicio de sesión y no bloquea el uso local si se omite.
- Cancelar el login vuelve al uso local. Una instalación existente conserva su agenda y permite
  activar la sincronización desde Ajustes, sin imponer de nuevo la elección inicial.
- Ajustes muestra modo, cuenta vinculada, última sincronización correcta, cambios pendientes,
  conflictos y acciones «Sincronizar ahora» y «Desconectar cuenta».
- Estados visibles: «Solo en este dispositivo», «Al día», «Cambios pendientes», «Sin conexión»,
  «Inicia sesión para sincronizar», «Hay cambios que revisar» y error con reintento.
- El estado de sincronización no bloquea Inicio, calendarios, Registro, Concentración ni widget.
  Los errores conservan la operación original y ofrecen recuperación comprensible.

### 3.2 Vincular datos locales

Primero autenticar y consultar el estado de la cuenta; la aplicación muestra una confirmación
antes de activar el espacio. Si está vacía, importa la agenda local; el inicio de sesión no sube
datos hasta que la persona confirma. Si la cuenta ya tiene datos, abre su espacio separado.

| Datos locales | Datos en cuenta | Acción prevista |
|---|---|---|
| Vacíos | Vacíos | Crear el espacio de cuenta y comenzar a sincronizar |
| Vacíos | Existentes | Descargar la cuenta a una base preparada y validada |
| Existentes | Vacíos | Ofrecer importar agenda e historial completos o conservarlos solo en el espacio local |
| Existentes | Existentes | Abrir los datos de la cuenta y conservar el espacio local separado; explicar que no se fusionarán automáticamente |

**Alcance de H3:** importación completa a una cuenta vacía y conservación separada cuando
ambos lados contienen datos. La fusión de dos historiales completos queda fuera de esta
primera entrega: requiere decidir entre líneas temporales y selecciones activas incompatibles.
No se presenta como una opción disponible. El usuario puede volver al espacio local conservado.

Antes de importar se ofrece exportar una copia recuperable. La aplicación prepara una instantánea
local estable; durante la confirmación final y el cambio de espacio impide escrituras sobre
esa instantánea y explica el estado. Un fallo o cancelación conserva el espacio original.

### 3.3 Desconectar, cerrar sesión y cambiar de cuenta

Estas son acciones explícitas sobre la cuenta. Perder la conexión no ejecuta ninguna de ellas:
la misma cuenta y base local permanecen activas y reciben los cambios offline.

- Detener y esperar las operaciones de red del espacio antes de cambiarlo; invalidar su generación
  de ejecución para impedir que respuestas tardías se apliquen en el espacio nuevo.
- Cerrar sesión retira las credenciales y deja la base de esa cuenta conservada, sin enviar cambios.
  Mostrar los cambios pendientes; estos solo pueden reanudarse al autenticar la misma cuenta.
- El espacio local sin cuenta sigue disponible. Para continuar editando los datos de la cuenta
  sin ella, ofrecer crear una copia local independiente, con procedencia registrada y sin su outbox.
- Cambiar de A a B abre un espacio distinto. No hereda la base, cola, cursor, token ni conflictos de A.
  Reactivar A exige autenticar A; la app no ofrece su contenido en la sesión de B.
- Desconectar no borra datos remotos. Borrar una cuenta o su copia local es otra acción explícita.
- Para H3 se admite una cuenta activa y un espacio local activo a la vez, con espacios anteriores
  conservados. No se necesita mostrar varias agendas de cuentas simultáneamente.

## 4. Arquitectura y persistencia Android

### 4.1 Aislamiento por espacio

Propuesta: una base Room por espacio, en lugar de añadir `ownerId` a todas las consultas actuales.
Un proveedor central entrega la base y el repositorio activos a la aplicación y al widget.

- Conservar `blocknize.db` como espacio local existente; no reasignarlo al primer login.
- Nombrar las bases de cuenta mediante identificadores internos opacos y persistir la asociación
  con entorno, emisor y usuario interno devuelto por `/me`; no usar el correo como identidad.
- Sustituir el singleton global y las lecturas directas de base en AppViewModel, backup y widget.
- Cambiar el espacio como una operación coordinada: parar workers, cerrar observaciones,
  resolver restauraciones pendientes, validar destino, actualizar referencia y reconstruir flujos.
- Registrar el cambio de espacio de forma recuperable ante muerte de proceso. Un widget que
  despierta durante el cambio espera o reintenta; no mezcla repositorios de dos espacios.
- Acento y opacidad siguen siendo ajustes del dispositivo, no datos sincronizados.

### 4.2 Tablas técnicas propuestas

La siguiente versión de Room se decidirá al implementar, conservando migraciones desde v1–v5.

| Tabla/concepto | Responsabilidad |
|---|---|
| `sync_state` | Espacio, identidad remota, dispositivo, generación de base, cursor y versión de protocolo |
| `outbox_operations` | ID estable, secuencia local, agregado, comando, versión base, dependencias, estado y reintento |
| `remote_entity_state` | Versión y representación confirmada del agregado remoto para comparar/reconstruir |
| `sync_conflicts` | Comando local, estado base/remoto y motivo; conserva datos para revisión |
| `sync_identity_map` | Correspondencias de IDs, eventos y claves de ocurrencia cuando la importación lo exige |

Una mutación en modo conectado guarda datos y comando en **la misma transacción Room**.
Esto se aplica tanto con red como sin ella: todas las acciones de dominio, incluidas ediciones,
borrados, eventos y completados, siguen el mismo recorrido y se sincronizan automáticamente.
En modo local no crea operaciones remotas. Todas las rutas de escritura pasan por el
coordinador, incluidas importaciones de plantilla y futuras acciones del widget.

Las operaciones pendientes se proyectan sobre la última base confirmada. Al descargar una
versión nueva, actualizar la base confirmada y recalcular las pendientes en la misma transacción;
no sobrescribir una edición local silenciosamente. Una operación incompatible pasa a revisión.

### 4.3 Identidades y dominio

- UUID creados en cliente para nuevas entidades y operaciones, validados por el servidor.
  Las rutas actuales pueden mantenerse; el contrato sync añade IDs estables a sus comandos.
- No usar `sequence` local como ID global ni como versión del servidor.
- Añadir identidad global persistente a los eventos locales que carecen de ella. Migrar de forma
  determinista y conservar el mapa al restaurar o reintentar una importación.
- Versionar la identidad canónica de ocurrencias y completados. Auditar todas sus referencias
  antes de convertir IDs o eventos, incluyendo snapshots y segmentos visuales.
- Las revisiones/eventos confirmados son inmutables. El servidor devuelve sus IDs canónicos;
  reconciliar referencias optimistas y operaciones dependientes con esos IDs.

## 5. Autenticación Android

1. Validar en un prototipo el flujo móvil soportado por la configuración Neon Auth actual:
   registro, login, retorno a la app si procede, renovación, expiración y cierre de sesión.
2. Encapsularlo en `AuthRepository`; evitar depender de que exista un SDK Kotlin equivalente al web.
3. Elegir el mecanismo con la documentación oficial vigente al implementar. No incrustar claves
   privilegiadas ni credenciales SQL en el APK. Restringir retornos al flujo autenticado esperado.
4. Mantener el JWT de acceso en memoria cuando sea posible y el material renovable en almacenamiento
   privado protegido con Android Keystore; excluirlo de copias y logs.
5. Coordinar una sola renovación ante peticiones simultáneas. Un 401 permite un intento de renovar;
   si falla, pausar la red y pedir sesión conservando datos y cola. No entrar en bucles de reintentos.
6. Verificar `/me` y entorno antes de abrir el espacio o enviar su primera operación. Usar TLS;
   permitir configuración de desarrollo separada sin debilitar la de distribución.

**Salida del prototipo:** evidencia con una cuenta sintética en Android y decisión registrada
del método de login/renovación. Si el proveedor exige interacción para renovar, la app debe
explicarlo y conservar su funcionamiento local. Esta decisión precede a la integración completa.

## 6. Contrato de sincronización en backend

### 6.1 Rutas propuestas bajo `/api/v1/sync`

Estos nombres son un contrato propuesto, no rutas existentes. Publicar DTO y OpenAPI antes
de implementar el cliente completo; no sincronizar a través de `/agenda` ni `/history`.

| Operación | Contrato previsto |
|---|---|
| `POST /devices` | Registrar instalación autorizada y generación de cliente |
| `POST /bootstrap` | Crear snapshot consistente de la cuenta y punto de inicio incremental |
| `GET /bootstrap/:id` | Descargar páginas de ese snapshot, con TTL y esquema explícitos |
| `GET /changes?cursor=...` | Cambios confirmados, paginados y acotados por cuenta |
| `POST /operations` | Lote ordenado de comandos con ID estable, secuencia y versión base |
| `GET /operations/:id` | Consultar resultado de una operación con respuesta perdida |
| `POST /imports` y subrutas | Preparar, cargar, validar, confirmar o cancelar una importación inicial |

Cada comando incluye `protocolVersion`, `deviceId`, generación, `operationId`, secuencia,
tipo, agregado, versión base, datos y dependencias. El propietario se obtiene del JWT.
Los resultados distinguen aplicado, ya aplicado, conflicto, rechazo y dependencia pendiente.
Limitar cuerpos, lotes y páginas; los límites concretos se fijan con las pruebas de volumen.

### 6.2 Orden de cambios sin pérdidas

Propuesta inicial: contador transaccional por cuenta y bloqueo de la fila de esa cuenta,
aprovechando la serialización que ya usa `IdempotencyService`.

- Cada escritura adquiere el bloqueo, actualiza dominio, asigna una revisión de sync e inserta
  un sobre de cambios en una misma transacción. La siguiente escritura de la cuenta espera el commit.
- Una revisión contiene todos los cambios relacionados, incluidos revisiones históricas,
  eventos, borrados y recibo de operación. No partirla de forma visible entre páginas.
- Todas las rutas que escriben datos sincronizados participan: también las rutas HTTP previas,
  perfil/zona y cualquier tarea administrativa. Auditar esas rutas antes de activar el feed.
- El cursor identifica protocolo, cuenta, generación y posición; es opaco, validado por servidor
  y no admite reutilización entre cuentas. Al paginar, fijar un límite superior confirmado.
- Un rollback no deja un sobre publicado. Una secuencia `bigserial` por sí sola no sirve:
  obtener un número no implica haber confirmado antes que otra transacción.
- Conservar tombstones de borrado; deshacer un completado también emite un cambio aunque
  el servicio actual elimine su fila. Ningún cliente puede resucitarlo por omisión de la descarga.

Una transacción de importación grande puede obligar a usar una generación/snapshot nuevo en
lugar de un sobre excesivo; definirlo en el contrato y probarlo antes de fijar límites.

### 6.3 Bootstrap, retención y recuperación

- Materializar un snapshot desde una lectura PostgreSQL consistente, incluyendo posición de sync.
  Descargar todas sus páginas a una base temporal y validar relaciones antes de activarla.
- Los cambios posteriores se descargan desde la posición del snapshot. No calcular el cursor
  con una consulta independiente que pueda avanzar más que los datos del snapshot.
- Si el proceso muere, reanudar o reiniciar la preparación; nunca sustituir la base activa con
  una descarga parcial. En una resincronización, preservar primero outbox y conflictos.
- Propuesta inicial: noventa días de retención del feed y tombstones asociados, configurable.
  Eliminar solo revisiones anteriores al horizonte admitido y mantener la coherencia del bootstrap.
- Un cursor caducado devuelve un código estable que exige bootstrap. Recuperar la base confirmada
  nueva y revisar comandos pendientes; no vaciar la cola ni reenviarlos como altas nuevas.
- La retención de datos históricos de producto es independiente de la retención técnica del feed.

### 6.4 Idempotencia duradera

Los treinta días de idempotencia HTTP existentes no cubren un móvil desconectado durante meses.
**Implementado parcialmente:** las claves nuevas de la outbox usan el formato estable
`bn-sync:<deviceId>:<operationId>`; el backend conserva hash, dispositivo y respuesta sin fecha
de caducidad en la misma transacción que el cambio de dominio. Repetir ID y contenido devuelve
la respuesta previa; cambiar el contenido da conflicto. El import inicial usa el mismo contrato.

Para completar 6.4 aún falta registrar dispositivos, asociar generación y secuencia local y
permitir revocarlos. El recibo durable ya está preparado para que una cola offline de meses no
recree una mutación; las operaciones históricas de versiones anteriores mantienen su clave previa.
Un dispositivo retirado deberá hacer bootstrap y revisión de pendientes antes de recibir una
generación nueva: no obtiene permiso para reejecutarlos.

## 7. Operaciones sin conexión, conflictos y tiempo

### 7.1 Reglas de aplicación

- Enviar operaciones en orden por dispositivo y respetar dependencias entre agregados.
  Un comando se aplica atómicamente; el lote puede devolver resultados parciales documentados.
- Una versión remota nueva no cambia la versión base de un comando enviado. Si hay conflicto,
  conservarlo y presentar el estado actual. Resolverlo genera otra operación con otro ID.
- Mantener las operaciones posteriores del agregado bloqueadas hasta resolver su dependencia;
  permitir avanzar agregados independientes cuando no alteren el orden histórico requerido.
- Una corrección del servidor recalcula la proyección local, agenda y widget. El usuario ve los
  conflictos pendientes y puede recuperar los datos de la propuesta rechazada.

| Caso | Política inicial |
|---|---|
| Ediciones simultáneas de una plantilla | Comprobar versión del agregado; pedir revisión si divergen |
| Dos activaciones distintas | Conflicto de selección activa; nunca activar por la hora del teléfono |
| Editar y borrar la misma temporal | Conflicto explícito; no resucitarla automáticamente |
| Completar/deshacer la misma ocurrencia | Añadir versión de estado, incluso para estado no completado, y cambio descargable |
| Dos excepciones del mismo origen | Unicidad de origen y versión; revisar si ya cambió la ocurrencia |
| Mismo comando repetido | Devolver recibo sin nuevos eventos ni duplicados |
| Etiquetas equivalentes | Normalizar en servidor y devolver ID canónico; actualizar referencias locales |

### 7.2 Fechas y eventos históricos

Al vincular, inicializar la zona de cuenta con la del dispositivo y mostrarla. Mientras se
sincroniza, todos los clientes proyectan la agenda compartida con la zona de cuenta; viajar
no la cambia automáticamente. En modo puramente local se conserva la zona del dispositivo.
Un cambio explícito de zona de cuenta es versionado, se sincroniza y no reescribe eventos pasados.

La hora del teléfono no decide el orden de transporte remoto. Guardar por separado el instante
de la acción declarado por el cliente, su zona, la secuencia y dependencias del dispositivo y
el instante de aceptación del servidor. Distinguir ese orden de recepción de la vigencia de los
eventos de dominio: recibir un cambio tarde no debe desplazar automáticamente su efecto al
momento de la subida. Conservar el historial realizado offline y validar su coherencia con las
versiones conocidas y los cambios concurrentes; registrar cómo se resuelven relojes desviados.
La importación histórica inicial de la sección 8 sigue siendo un contrato separado.

**Caso obligatorio de aceptación:** crear, mover, completar u omitir una actividad offline y
sincronizar cuando su fecha ya pasó. Las rutas principales reciben instante/zona del cliente y
validan la acción según cuándo se hizo; falta demostrar toda la matriz con fixtures y dispositivos.
Un conflicto real con cambios de otro dispositivo conserva la propuesta para resolución.

Comparar fixtures Android/servidor para 24:00, cambios estacionales, revisiones activas,
temporales eliminadas, excepciones y completados históricos. Resolver y probar el contrato
temporal en 3.0 antes de implementar el motor; trasladar las rutas HTTP actuales no basta
para cumplir este requisito de trabajo offline con subida posterior.

## 8. Importación inicial de datos existentes

No reproducir años de historial llamando a «crear», «activar» o «completar» actuales:
eso generaría IDs, fechas y eventos diferentes. Implementar un importador inicial específico.

1. Exportar una instantánea lógica compatible con la versión Room de origen, sin credenciales,
   WorkManager, tabla de recuperación ni metadatos de transporte.
2. Crear un manifiesto con versión, ID de importación, tablas, recuentos y hash. Reutilizar los
   validadores del backup donde sea posible, separando el contrato remoto del archivo de backup.
3. Cargar en staging por fragmentos reintentables. La cuenta no ve datos parciales.
4. Validar propietarios, UUID, referencias, etiquetas, horarios, revisiones, snapshots y eventos
   históricos huérfanos admitidos por Android. Crear mapa explícito cuando haya que convertir IDs.
5. Preservar instantes, zonas, selección activa y completados; no convertir secuencias locales
   en orden de commit remoto. Emitir informe de incompatibilidades sin modificar el origen.
6. Comprobar de nuevo que la cuenta está vacía bajo el bloqueo de escritura. Si otro móvil
   escribió durante la carga, cancelar la confirmación con conflicto.
7. Confirmar dominio, registro de importación y posición inicial de sync atómicamente.
   Misma importación y hash devuelve el resultado ya confirmado; otro contenido exige otro ID.
8. Hacer bootstrap de lo confirmado, comparar agenda/historial y activar el espacio de cuenta.
   Conservar el espacio local original; no cargar además su historial como comandos de outbox.

Registrar procedencia en la cuenta para detectar la repetición del mismo conjunto local desde
otro móvil. Si hay colisión de IDs o contenidos, detener y mostrar diagnóstico; no reasignar
identidades históricas silenciosamente. Limpiar staging cancelado/caducado sin tocar producto.

## 9. Motor de sync y tareas programadas

Implementar `SyncCoordinator`, `SyncRepository`, `OutboxDispatcher`, `RemoteChangeApplier`
y el cliente de API detrás de interfaces. Un único ciclo por espacio/generación:

1. Verificar espacio activo, sesión e identidad; recuperar operaciones enviadas sin respuesta.
2. Descargar cambios confirmados y aplicar datos/cursor en transacción, preservando pendientes.
3. Enviar comandos elegibles, registrar resultados y bloquear conflictos/dependencias.
4. Descargar hasta alcanzar la posición confirmada anunciada por servidor; repetir si quedan
   trabajos elegibles, con límites de tiempo/lote para no monopolizar el proceso.
5. Persistir estado, actualizar flujos Room y refrescar widget; reprogramar si quedan reintentos.

Activadores: mutación local, vuelta a primer plano, login, recuperación de conexión y botón
manual. WorkManager aporta trabajo único por espacio con requisito de red y retroceso
exponencial con variación. No garantiza ejecución inmediata en Doze.

Tratar por separado errores de red/5xx y 429, autenticación, validación y conflictos.
Respetar `Retry-After`; limitar reintentos automáticos y conservar el diagnóstico. Registrar
IDs de operación/petición, duración y estado, sin contenido de agenda ni tokens.

## 10. Copias, restauración y reinstalación

- Modo local: mantener exportación/restauración completa y migrar copias v5 al esquema nuevo
  con conversión explícita. Actualizar las reglas de backup Android para el nuevo proveedor de bases.
- No exportar tokens, sesiones, recibos de transporte, cursores ni outbox como comandos ejecutables
  en otro dispositivo. Excluir los espacios autenticados y sus metadatos del backup automático;
  recuperarlos mediante login/bootstrap. La interfaz debe explicar los cambios locales aún no enviados.
- Una copia lógica del contenido de cuenta puede exportarse para recuperación local; al restaurarla,
  crear un espacio local separado. No reemplazar la base sincronizada ni enviar borrados masivos.
- Reinstalación con cuenta: autenticar, registrar instalación nueva y hacer bootstrap. La copia del
  servidor no incluye operaciones que nunca llegaron a él; una copia manual puede conservar su contenido.
- La retirada de un dispositivo impide seguir enviando su cola. Su recuperación requiere verificar
  identidad, descargar estado nuevo y revisar propuestas, conservando el material local recuperable.

## 11. Secuencia de implementación

| Hito | Trabajo | Entregable revisable | Criterio de aceptación |
|---|---|---|---|
| 3.0 Contratos y equivalencia | Cierre de dependencias H1/H2, fixtures compartidos, identidades, fechas, conflictos y prototipo Auth móvil | Decisiones, fixtures, OpenAPI propuesto y login demostrable | Explicados y probados los casos temporales y el ciclo de sesión; contrato versionado |
| 3.1 Espacios y modos locales | Proveedor Room, migración, elección de modo, separación de cuentas y widget | APK que conserva agenda anterior y cambia entre espacios de prueba | Sin cuenta/red funciona todo lo local; cambio o muerte de proceso no mezcla datos |
| 3.2 Transporte servidor | Registro de dispositivo, feed ordenado, recibos, bootstrap y tombstones | API de sync con colección de aceptación de dos clientes | Sin saltos de commit, duplicados ni páginas parciales; aislamiento y recuperación verificados |
| 3.3 Cliente y primer recorrido | AuthRepository, cliente HTTP, outbox y motor; plantillas/bloques/etiquetas | Dos móviles crean y editan una plantilla offline y convergen | Respuesta perdida, reinicio, token caducado y conflicto no pierden propuestas |
| 3.4 Selección y temporales | Activación, revisiones asociadas, ciclo de vida de temporales y conflictos | Agenda compartida con cambios desde ambos móviles | Edición activa y borrados convergen; conflictos temporales visibles y recuperables |
| 3.5 Completados e historial | Completar/deshacer, excepciones, revisiones/eventos completos y claves canónicas | Registro y agenda equivalentes tras sync | SKIP/REPLACE/RESTORE, segmentos y concurrencia conservan identidades e historial |
| 3.6 Asociación y recuperación | Staging/importador inicial, bootstrap seguro, logout/cambio de cuenta y copias | Recorrido completo desde instalación existente | Importación repetida/fallida no duplica; cuenta con datos no destruye agenda local |
| 3.7 Aceptación H3 | Dos dispositivos, fallos de red/proceso, volumen, Doze y documentación | Informe reproducible, APK y resultados backend/Android | Matriz crítica superada y limitaciones conocidas registradas |

Las revisiones necesarias para plantillas se incorporan desde 3.3; 3.5 completa la cobertura
histórica. El importador de 3.6 no se habilita para usuarios mientras falten tipos de dominio.
Cada hito incluye las pruebas de su entrega. No dejar toda la verificación para 3.7.

## 12. Pruebas y aceptación H3

| Área | Casos mínimos y resultado exigido |
|---|---|
| Uso sin cuenta | Instalación nueva y actualización en modo avión; todos los flujos actuales disponibles; ninguna solicitud de datos de producto ni registro obligado |
| Identidad | Registro/login/renovación/logout, JWT caducado, rechazo de identidad distinta y cancelación; datos locales siguen disponibles |
| Aislamiento | A y B con IDs manipulados en devices, imports, bootstrap, cursores, operaciones y recibos; ninguna lectura/escritura cruzada |
| Migración Android | Datos y claves de v1–v5 conservados; Room/backup sin pérdida; widget y flujos observan solo el espacio activo |
| Importación | Agenda con las doce tablas, historial sin entidad vigente, etiquetas equivalentes, fallos de staging/commit y reintento; mismo resultado sin duplicados |
| Dos dispositivos | Altas/ediciones/borrados y cambios de selección desde ambos lados; misma agenda e historial tras vaciar colas |
| Sin conexión | Varias operaciones dependientes, días sin red, fecha ya pasada y cambio de zona; subida automática al reconectar y conservación de tareas/eventos sin rechazo por el mero retraso |
| Concurrencia | Dos activaciones, editar/borrar, completar/deshacer y excepciones simultáneas; versiones detectan divergencia |
| Transporte | Cortar antes/después de commit, respuesta perdida, doble envío, 429/5xx, cierre de proceso; una sola aplicación |
| Cursor | Transacción lenta y rápida, rollback y paginación con escrituras nuevas; ningún cambio confirmado queda omitido |
| Recuperación | Cursor caducado, bootstrap parcial, recibo antiguo y dispositivo retirado; no descartar ni repetir operaciones pendientes |
| Cambio de cuenta | Respuesta/worker tardío de A al abrir B, logout offline y copia local; ninguna operación de A viaja como B |
| Tiempo civil | 24:00, hora inexistente/repetida, reloj desviado y zona compartida; fixtures de agenda e historial equivalentes |
| Restauración | Copia v5 y copia nueva a espacio local; nunca generar borrados remotos ni restaurar credenciales |
| Dispositivo real | Reinicio, pantalla apagada, Doze, renovación y launcher real; sync converge al recuperar ejecución/red |
| Volumen | Dataset pequeño y cercano al máximo actual de backup; medir memoria, latencia y tamaño; fijar límites publicables |

Automatizar reglas, migraciones, transacciones, cursor y fallos reproducibles; usar PostgreSQL
real de desarrollo para integridad/concurrencia y dos dispositivos Android para aceptación.
Registrar en cada ejecución versión/commit, dispositivos, zona, esquema, comandos y resultados.
Las credenciales de aceptación permanecen fuera de Git; limpiar los datos sintéticos generados.

**H3 se declara completado cuando:** funcionan los dos modos; login opcional; datos existentes
recuperables; dos dispositivos convergen; los reintentos no duplican acciones; no hay pérdidas
silenciosas ni cruces de cuenta; los casos críticos anteriores pasan y se documentan las
reglas temporales y la ausencia de fusión automática de historiales. Una acción válida realizada
offline debe reflejarse en la cuenta al reconectar aunque entonces su fecha ya haya pasado.

## 13. Alcance y documentos de entrega

H3 no incluye escritorio, web, IA, sincronización instantánea por sockets ni fusión automática
de historiales de dos conjuntos poblados. La API, las identidades y el cursor se diseñan para
que esos clientes puedan incorporarse después.

Entregar y enlazar desde README:

- Contrato versionado de sync y OpenAPI con errores, límites, compatibilidad y retención.
- Guía de modos, asociación, conflictos, desconexión y recuperación de copias.
- Migraciones Android/PostgreSQL y procedimiento de actualización recuperable.
- Informe de implementación/aceptación H3 con resultados, limitaciones y cuentas de prueba usadas.

No se fija una fecha de entrega sin validar 3.0. Ese hito reduce las incertidumbres del flujo
Auth móvil, las claves históricas y las reglas temporales; después se estima el trabajo restante
por entregables y se actualiza este plan con los resultados reales.

## Referencias del proyecto

- [Plan general, Fase 3](BlockNize_Plan_General_Desarrollo.md#6-fase-3--sincronización-room--backend--neon).
- [Consolidación Android](Fase1_Consolidacion_Android.md) y [aceptación H1](Fase1_Pruebas_Aceptacion.md).
- [Plan backend](Fase2_Plan_Backend.md), [implementación H2](Fase2_Implementacion.md)
  y [integración Neon Auth](Integracion_Neon_Auth_Fase2.md).
- [Backend y rutas actuales](../backend/README.md).
