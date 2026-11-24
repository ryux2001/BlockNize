# Fase 2 — Plan detallado de backend, identidad y persistencia remota

Fecha: 12 de noviembre de 2026. Estado: plan de referencia; la implementación local
está entregada y el cierre de aceptación 2.7 sigue pendiente.

## 1. Objetivo y límites

Entregar una API NestJS autenticada, documentada y probada sobre PostgreSQL en
Neon, con las funciones de organización que Android ya implementa y aislamiento
entre cuentas. Se probará desde una colección HTTP y OpenAPI antes de conectar Android.

Esta fase no incorpora sincronización Room, outbox, importación de copias locales
al servidor, inicio de sesión en Android, web, escritorio ni IA. Los datos locales
actuales permanecen en Android. La fase 3 decidirá su asociación inicial a una cuenta.

H1 sigue pendiente de aceptación física. Se puede preparar y probar el backend en
paralelo, pero no considerar resuelta esa validación ni habilitar migraciones de datos
reales por el hecho de terminar H2.

## 2. Arquitectura propuesta

- Monolito modular en `backend/`, independiente del proyecto Gradle `android/`.
- NestJS y TypeScript estricto; REST versionada bajo `/api/v1`.
- PostgreSQL en Neon. Propuesta: Drizzle con `pg`, migraciones SQL revisables y
  transacciones sobre una conexión reservada durante cada operación compuesta.
- Node.js LTS y versiones compatibles fijadas en el manifiesto y lockfile al iniciar.
- Neon Auth como proveedor de identidad, encapsulado tras un adaptador propio.
- OpenAPI generado desde DTO y controladores; colección de peticiones de aceptación.
- Pruebas unitarias de reglas temporales y pruebas integradas/E2E con PostgreSQL real.

Drizzle es una decisión propuesta para conservar control explícito sobre restricciones,
consultas históricas y bloqueos. Confirmar compatibilidad y versiones antes de instalar.
No introducir Redis, colas externas ni microservicios en esta fase.

Estructura prevista:

```text
backend/
  src/
    config/          # Validación de entorno
    database/        # Esquema, conexión y transacciones
    auth/            # Adaptador Neon y guard global
    users/           # Perfil y contexto de cuenta
    templates/       # Plantillas, bloques, revisiones y activación
    tags/            # Etiquetas por propietario
    agenda/          # Proyección de ocurrencias y reglas temporales
    one-off-tasks/   # Tareas temporales y eventos
    completions/     # Completar y deshacer
    exceptions/      # Omitir, sustituir y restaurar ocurrencias
    history/         # Registro y consultas históricas
    common/          # Errores, reloj y contexto de petición
    health/
  migrations/
  test/fixtures/
  test/e2e/
  requests/
  .env.example
  README.md
```

Los controladores validan y traducen HTTP; los servicios ejecutan reglas y transacciones;
los repositorios reciben siempre un contexto de propietario. Ninguna ruta permite
escribir directamente revisiones o eventos históricos arbitrarios.

## 3. Modelo de datos propuesto

Room v5 contiene 12 tablas funcionales y una tabla técnica de recuperación. El modelo
remoto conserva sus conceptos, sin trasladar `pending_restore_preferences` ni preferencias
del widget, WorkManager o estado de pantallas.

| Concepto remoto | Diseño y restricciones principales |
|---|---|
| `users` | UUID interno; identidad externa única por proveedor y subject; zona IANA de cuenta |
| `templates` | Propietario, nombre, versión y marcas de tiempo |
| `tags` | Propietario y nombre normalizado único dentro de la cuenta |
| `task_definitions` | Propietario, plantilla y etiqueta opcional |
| `schedule_blocks` | Propietario, plantilla, definición, máscara de días e intervalo civil |
| `template_revisions` | Snapshot JSONB validado y versionado; inmutable |
| `active_selections` | Una fila por propietario; plantilla, revisión, versión e instante efectivo |
| `schedule_change_events` | Activación/edición activa; evento estable, instante, zona y orden determinista |
| `one_off_tasks` | Fecha civil, horario, contenido actual, versión y marca de borrado |
| `one_off_schedule_events` | UPSERT/DELETE con snapshot histórico inmutable |
| `completion_records` | Unicidad por propietario y clave de completado; snapshot y referencias de origen |
| `occurrence_exceptions` | Unicidad por propietario, plantilla, bloque y fecha original; versión y estado |
| `occurrence_exception_events` | ID global propio y snapshot de cada transición de excepción |
| `idempotency_requests` | Propietario, clave, operación, hash de petición y respuesta; retención definida |

Decisiones de integridad:

1. UUID estables para entidades y eventos. Auditar los UUID locales antes de fijar
   el futuro contrato de importación; no regenerar identidades al sincronizar.
2. La identidad del proveedor se trata como texto externo, sin asumir que su formato
   sea siempre UUID ni acoplar las migraciones de negocio al esquema de Neon Auth.
3. `owner_id` procede de la identidad verificada, nunca del cuerpo enviado por el cliente.
4. Claves foráneas compuestas con propietario para impedir relaciones entre cuentas.
5. Versión entera en agregados mutables y precondiciones obligatorias en sus cambios.
   Una edición de bloque modifica la versión del agregado plantilla.
6. Snapshots históricos sin cascadas desde entidades editables. JSONB incluye versión
   de esquema y validación estructural; no es un contenedor libre de datos del cliente.
7. Fechas civiles como `date`, horarios como minutos enteros e instantes como
   `timestamptz`; API con fechas ISO e instantes UTC. Zona IANA explícita.
8. `CHECK`: inicio 0..1439, fin 1..1440, inicio menor que fin, máscara 1..127.
9. Borrado lógico para recursos eliminables, conservando historia y versión. No añadir
   borrado de plantillas si no existe una regla funcional acordada para ello.
10. Las secuencias locales no son identificadores globales. El orden del servidor
    desempata eventos, pero no constituye todavía un cursor de sincronización:
    una secuencia PostgreSQL no garantiza orden de commit.

## 4. Identidad, sesiones y autorización

La documentación actual de Neon describe un servicio administrado basado en Better
Auth, sesiones opacas y JWT obtenidos por el SDK. No tratar una cookie de sesión como
si fuera un JWT. Las URL de Auth y JWKS dependen de la configuración de la rama.

La aceptación real en la rama de desarrollo ya creó e inició sesión con dos cuentas,
obtuvo JWT de Neon Auth y confirmó que `Authorization: Bearer <token>` funciona con
NestJS. También comprobó firma/emisor/subject, perfiles distintos y aislamiento de
plantillas entre cuentas, junto con idempotencia y ETags. Las dos cuentas temporales
se eliminaron al terminar.

Queda por probar expiración, renovación, cierre de sesión, revocación y rotación de
claves. Como el guard valida el JWT sin consultar la sesión, un token emitido sigue
siendo válido hasta expirar después del logout. Confirmar también cómo obtendrá y
renovará credenciales el futuro cliente Android; no asumir un SDK Kotlin equivalente
al SDK web. El backend no almacena contraseñas.

Guard global con acceso público explícito para salud. Consultas por propietario y
404 para recursos ajenos; no revelar su existencia. Pruebas de aislamiento en cada
familia de endpoints, incluidas revisiones, snapshots y relaciones anidadas.

## 5. Contrato HTTP inicial

Todas las rutas siguientes llevan el prefijo `/api/v1`. Son una propuesta que se
cerrará al publicar el primer OpenAPI, sin prometer aún compatibilidad con sincronización.

| Área | Operaciones propuestas |
|---|---|
| Salud | `GET /health/live`, `GET /health/ready` |
| Cuenta | `GET /me`, `PATCH /me` para zona horaria |
| Etiquetas | `GET /tags`; creación/resolución dentro de los comandos de negocio |
| Plantillas | `GET/POST /templates`, `GET/PATCH /templates/:id` |
| Bloques | `GET/POST /templates/:id/blocks`, `PATCH/DELETE /templates/:id/blocks/:blockId` |
| Activación | `GET /active-selection`, `PUT /active-selection` |
| Revisiones | `GET /templates/:id/revisions`, `GET /templates/:id/revisions/:revisionId` |
| Temporales | `GET/POST /one-off-tasks`, `GET/PATCH/DELETE /one-off-tasks/:id` |
| Agenda | `GET /agenda?date=YYYY-MM-DD&view=day|calendar`, `GET /agenda/week?startDate=YYYY-MM-DD` |
| Completados | `PUT/DELETE /completions/:completionKey` |
| Excepciones | `PUT /occurrence-exceptions` con acción SKIP, REPLACE o RESTORE e identidad del origen |
| Historial | `GET /history?from=...&to=...`, con paginación |

Las claves opacas de ocurrencia/completado se codifican correctamente en URL; no se
intercambian entre sí. Auditar `occurrenceKey`, `completionKey`, revisiones y segmentos
históricos actuales antes de cerrar los DTO. Los snapshots se calculan en servidor.

Convenciones:

- DTO estrictos, rechazo de campos desconocidos, límites de longitud y tamaño.
- Límites explícitos para rangos de fechas y páginas, orden estable y cursores opacos
  de paginación que no se confunden con un cursor de sincronización.
- `If-Match`/ETag para cambios de recursos existentes: 428 si falta la precondición,
  412 si la versión está obsoleta; 409 para conflicto de dominio o clave idempotente.
- `Idempotency-Key` para comandos que crean recursos o producen eventos. Repetir una
  petición devuelve el resultado original; misma clave con otro contenido da 409.
  Reservar clave, efectuar cambio y persistir respuesta en la misma transacción.
- Definir retención de claves y comportamiento tras caducar; no prometer deduplicación
  indefinida. El contrato duradero de operaciones offline se diseña en la fase 3.
- Error uniforme: código estable, mensaje, detalles de validación y `requestId`.
- 400 para entrada inválida, 401 sin identidad válida, 404 para recurso no accesible.

## 6. Reglas de negocio que deben conservarse

- Activar una plantilla crea revisión, evento y selección activa atómicamente.
- Editar una plantilla activa crea una revisión/evento si cambia su snapshot;
  editar una inactiva no altera la agenda activa. No producir eventos por cambios nulos.
- Las temporales conservan eventos UPSERT/DELETE y su contenido histórico.
- Completar valida la ocurrencia vigente y conserva snapshot e identidad original.
  Deshacer el completado debe tener una representación de cambio futura definida,
  sin introducir todavía un protocolo de descarga.
- SKIP/REPLACE/RESTORE conserva el origen de la ocurrencia. No editar si el origen
  o la fecha de la ocurrencia están en un día pasado; no modificar una completada
  antes de deshacer su finalización. Restaurar requiere una excepción previa válida.
- Reprogramar no permite llevar el inicio a un instante pasado. Una restauración
  no recrea bloques eliminados ni activa por sí misma una plantilla.
- Una operación sobre la ocurrencia debe detectar si cambió desde que se consultó;
  comprobar versión del origen y estado de excepción dentro de la transacción.
- Fin 24:00 significa siguiente medianoche local. No aceptar cruces de medianoche.
- Conservar política Android para horas inexistentes/ambiguas y rechazar intervalos
  nuevos sin duración efectiva. Usar reloj inyectable en pruebas.
- Zona inicial de cuenta explícita, propuesta `Europe/Madrid` solo para los usuarios
  de prueba. El servidor usa esa zona para «hoy» y programación; cambiarla no reescribe
  instantes o zonas del historial. Documentar esta decisión frente al comportamiento
  Android actual, que usa la zona del dispositivo; conciliación antes de la fase 3.
- Reconstruir Registro desde revisiones/eventos y completados; no desde filas actuales.

Concurrencia: bloquear el agregado afectado y validar la versión dentro de la misma
transacción. Definir un orden fijo de bloqueos y reintentos acotados de deadlocks.
Dos cambios concurrentes sobre selección o excepción no deben sobrescribirse en silencio.

## 7. Hitos de implementación y entregables

| Hito | Trabajo | Entregable para probar | Criterio de aceptación |
|---|---|---|---|
| 2.0 Contrato y decisiones | Matriz Room/API, claves de ocurrencia, tiempos y borrados; ADR de persistencia y Auth | Documento de esquema, ejemplos JSON y reglas | Todas las entidades y operaciones actuales tienen correspondencia o exclusión explicada |
| 2.1 Esqueleto | Proyecto NestJS, entorno validado, conexión, salud, OpenAPI y comandos de desarrollo | Servidor local y `/health`, `/docs` | Arranca con configuración válida; falla claramente si falta una variable; readiness detecta BD caída |
| 2.2 Identidad real | Prueba Neon Auth, guard y `/me`; dos cuentas | Peticiones de alta/login documentadas y consulta autenticada | Token válido funciona; inválido/caducado falla; perfiles aislados; renovación/logout documentados |
| 2.3 Persistencia | Esquema SQL, restricciones, índices y migraciones | Base vacía migrable y pruebas de integridad | Migraciones reproducibles; rechaza relaciones entre cuentas e intervalos inválidos |
| 2.4 Plantillas | Etiquetas, definiciones, bloques, activación y revisiones | Colección crear/editar/activar/consultar | Transacciones y versiones correctas; historia conservada; usuario B no accede a A |
| 2.5 Agenda y temporales | Proyección diaria/semanal, temporales, reglas civiles y eventos | Agenda consultable y fixtures equivalentes a Android | Coincidencia funcional en días normales, 24:00 y cambios estacionales |
| 2.6 Completados y excepciones | Completar/deshacer, omitir/mover/restaurar e historial | Flujo completo por HTTP | Reglas históricas, snapshots y rollback; rechaza estado obsoleto y origen pasado |
| 2.7 Cierre H2 | Idempotencia transversal, concurrencia, seguridad, documentación y aceptación | API completa, OpenAPI exportado y colección con dos usuarios | Batería sobre PostgreSQL real y aceptación manual superadas |

Dependencias: 2.0 → 2.1 → 2.2 → 2.3 → 2.4 → 2.5 → 2.6 → 2.7.
La validación de entradas, aislamiento, versiones, transacciones e idempotencia se
implementan junto a cada módulo; 2.7 comprueba su cobertura completa.
Cada hito debe dejar comandos, resultado esperado y evidencia para repetir la prueba.

## 8. Estrategia de pruebas

1. Reglas puras con reloj controlado: recurrencias, identidad, excepciones y snapshots.
2. Fixtures de paridad extraídos de casos Android: activación a mitad del día, edición
   activa/inactiva, temporal eliminada, ocurrencia sustituida/completada y restauración.
3. Tiempo: 24:00 y transiciones Europe/Madrid, zona sin horario estacional y cambio
   de zona de cuenta con historia existente. Comparar instantes y contenido semántico.
4. PostgreSQL real: restricciones, migraciones desde vacío y versión anterior, rollback
   forzado entre escritura de estado y evento, bloqueos y escrituras concurrentes.
5. E2E con dos cuentas: recursos propios/ajenos, IDs anidados de otra cuenta,
   entradas malformadas, tokens inválidos y paginación aislada.
6. Reintentos: creación duplicada, finalización duplicada, activación y excepción con
   misma clave; solicitudes concurrentes con la misma clave y contenido distinto.
7. Integración real con Neon Auth separada de tests con tokens de prueba. Documentar
   qué depende de red/credenciales; un test simulado no acredita al proveedor real.

Usar PostgreSQL local de pruebas o una rama Neon exclusiva y desechable, nunca datos
personales. Si se incorpora Docker, hacerlo como herramienta opcional documentada.

## 9. Configuración y operación

- `.env.example` con placeholders; secretos fuera de Git y logs.
- Variables previstas: `DATABASE_URL`, `MIGRATION_DATABASE_URL` si la herramienta
  necesita conexión distinta, URL Auth/JWKS, emisor/audiencia según prueba, puerto,
  orígenes CORS y entorno.
- Conexión TLS y pool acotado; rol de ejecución sin permisos DDL y rol de migración
  separado. No exponer credenciales PostgreSQL a clientes.
- Rama Neon de desarrollo y entorno de pruebas separados. No copiar identidades
  reales a tests sin un procedimiento explícito.
- Migraciones por comando explícito; no sincronización automática de esquema al
  arrancar. Recuperación por corrección hacia delante y backup probado antes de
  cambios destructivos; no prometer rollback SQL universal.
- Logs estructurados de latencia, estado y requestId sin tokens, contraseñas,
  títulos de tareas o snapshots. Respuestas de salud sin información sensible.
- CORS restringido, tamaño de cuerpo limitado, rate limiting y OpenAPI protegido
  o deshabilitado en un futuro entorno público. HTTPS en el despliegue.
- Entrega inicial local contra Neon. Hosting público y firma de clientes no son
  requisitos para probar H2; documentar despliegue sin contratar servicios todavía.

## 10. Recorrido final de aceptación H2

1. Configurar una base de desarrollo vacía, aplicar migraciones y arrancar el servidor.
2. Entrar con A y B mediante Neon Auth real y obtener sus identidades en `/me`.
3. Con A, crear una plantilla, añadir bloques, activarla y consultar agenda.
4. Editar un bloque activo y comprobar revisión nueva e historial anterior conservado.
5. Crear una temporal, modificarla y eliminarla; comprobar su historia.
6. Completar/deshacer una ocurrencia; omitir, reprogramar y restaurar otra futura.
7. Repetir comandos con su clave idempotente y verificar ausencia de duplicados.
8. Enviar una versión antigua y comprobar que no sobrescribe el cambio reciente.
9. Con B, intentar leer y modificar cada recurso de A, también a través de rutas
   anidadas, agenda e historial: ninguna operación debe filtrar o alterar sus datos.
10. Reiniciar NestJS y confirmar persistencia. Ejecutar prueba de rollback transaccional.

Entregable final: código en `backend/`, lockfile, migraciones, OpenAPI, colección HTTP,
guía de configuración y pruebas, informe de resultados y limitaciones conocidas.
La prueba real de identidad y PostgreSQL ya pasó para el alcance descrito en el
informe de implementación. H2 se declara completo después de aceptar los flujos de
agenda, temporales, completados, excepciones e historial, además de rollback,
concurrencia e idempotencia paralela. Android seguirá funcionando localmente hasta
implementar y aceptar la fase 3.

## 11. Dependencias externas y decisiones pendientes

- Para 2.1–2.3: proyecto/rama Neon de desarrollo, conexión privada y Auth habilitado.
  Configurar secretos en el entorno local, sin pegarlos en documentos versionados.
- Renovación, expiración, revocación y compatibilidad futura móvil siguen pendientes
  de aceptación en 2.2; la autenticación Bearer con dos identidades ya se probó.
- Cerrar normalización de etiquetas, claves de ocurrencia, zona de cuenta, retención
  de idempotencia y límites de API en 2.0–2.2, antes de congelar el contrato.
- El esquema puede prepararse sin credenciales; la integración real ya se aceptó en
  alcance básico y requiere credenciales para repetir la matriz ampliada.
- La fase 3 deberá diseñar cursor con orden de commit, protocolo offline, outbox,
  conflictos, borrados y asociación de datos locales. Las versiones y los IDs aquí
  previstos son preparación, no una implementación de sincronización.

## Fuentes y referencia

- `android/app/src/main/java/com/blocknize/app/data/AppDatabase.kt` (Room v5).
- `android/app/src/main/java/com/blocknize/app/data/TemplateRepository.kt`.
- `android/app/src/main/java/com/blocknize/app/data/OccurrenceExceptions.kt`.
- [Plan general](BlockNize_Plan_General_Desarrollo.md).
- [Flujo de autenticación de Neon](https://neon.com/docs/auth/authentication-flow).
- [Configuración Auth por rama y JWKS](https://neon.com/docs/auth/guides/manage-auth-api).

Las fuentes externas se consultaron para planificar. La prueba sobre la configuración
real del proyecto prevalece sobre ejemplos genéricos de documentación.
