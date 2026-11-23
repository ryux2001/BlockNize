# BlockNize Backend

API modular en NestJS con Drizzle ORM y PostgreSQL en Neon. La API valida permisos
y ejecuta las reglas; los clientes no reciben credenciales SQL.

## Requisitos y arranque

- Node.js 20.11+ y npm.
- Rama de desarrollo Neon con Managed Better Auth habilitado.
- Cadena PostgreSQL con TLS para runtime y, preferiblemente, un rol DDL separado.

```powershell
cd backend
npm ci
Copy-Item .env.example .env
# Completar .env con las credenciales de la rama de desarrollo.
npm run db:migrate
npm run start:dev
```

API: `http://localhost:3001/api/v1`. OpenAPI: `http://localhost:3001/docs`;
JSON: `http://localhost:3001/openapi.json`.

No se versiona `.env`. `NEON_AUTH_JWKS_URL` debe ser el `jwks_url` de la rama;
`NEON_AUTH_ISSUER` debe coincidir exactamente con el claim `iss` del JWT emitido
por esa rama. El audience es opcional porque los JWT de Managed Better Auth
documentados pueden no incluir `aud`; si el token real contiene un audience para
esta API, fija `NEON_AUTH_AUDIENCE`. La API verifica firma, emisor, expiración,
subject y algoritmo permitido. No acepta la cookie opaca de sesión como Bearer.

Neon Auth administra alta, inicio de sesión, renovación y cierre de sesión. El
cliente obtiene `session.access_token` mediante su SDK y lo envía como
`Authorization: Bearer`. La API no procesa contraseñas. Un JWT firmado sigue siendo
válido hasta su expiración aunque el usuario cierre sesión; la revocación inmediata
no está implementada. Este comportamiento debe considerarse al integrar clientes.

## Migraciones y comandos

```powershell
npm run db:generate
npm run db:migrate
npm run build
npm run test:unit
npm run start:prod
```

El servidor no sincroniza el esquema al arrancar. Usa `DATABASE_URL` para runtime
y `MIGRATION_DATABASE_URL` para DDL cuando haya un rol de migración. El rol runtime
debe tener acceso a datos y Neon debe exigir TLS.

`npm run test:unit` compila el proyecto y ejecuta las pruebas locales de tiempo
civil, incluidas las transiciones de horario estacional.

`npm run test:database` ejecuta en `DATABASE_URL` una prueba de restricciones de
propietario e intervalos. Inserta fixtures dentro de una transacción y los revierte
al terminar; ejecútala contra la rama de desarrollo después de aplicar migraciones.

`npm run test:acceptance` es el recorrido recomendado: compila y arranca la API si hace
falta, inicia sesión con la cuenta reutilizable documentada en
[Neon_Auth_Acceptance.md](test/Neon_Auth_Acceptance.md), crea una segunda cuenta
temporal y ejecuta la aceptación HTTP. Comprueba identidad, aislamiento, plantillas,
revisiones, agenda, temporales, completados, excepciones, historial, ETags e
idempotencia concurrente. Elimina la cuenta B y revierte los datos de prueba al
terminar. La contraseña de A queda solo en el `.env` local; los JWT viven en memoria.

También puedes ejecutar `npm run test:api` con un servidor activo y los dos JWT
temporales en `BLOCKNIZE_ACCEPTANCE_TOKEN_A` y `BLOCKNIZE_ACCEPTANCE_TOKEN_B` dentro
del `.env`. No guardes tokens en Git.

## Rutas implementadas

Todas las rutas bajo `/api/v1` requieren `Authorization: Bearer`, salvo salud.

| Ruta | Uso |
|---|---|
| `GET /health/live`, `GET /health/ready` | Proceso y conexión PostgreSQL |
| `GET /me`, `PATCH /me` | Perfil y zona horaria IANA |
| `GET /tags` | Etiquetas de la cuenta |
| `GET/POST /templates` | Listar y crear plantillas |
| `GET/PATCH /templates/:id` | Consultar y renombrar plantilla |
| `POST/PATCH/DELETE /templates/:id/blocks/...` | Cambiar bloques semanales |
| `GET/PUT /active-selection` | Selección activa y snapshots |
| `GET /templates/:id/revisions/...` | Revisiones inmutables |
| `GET /agenda?date=YYYY-MM-DD` | Agenda diaria por fecha civil |
| `GET /agenda/week?startDate=YYYY-MM-DD` | Siete días consecutivos |
| `GET/POST/PATCH/DELETE /one-off-tasks` | Tareas temporales |
| `POST /completions` | Completar o deshacer finalización |
| `POST /occurrence-exceptions` | SKIP, REPLACE y RESTORE |
| `GET /history?from=...&to=...&limit=...` | Historial paginado por fecha |
| `GET /sync/state` | Cursor confirmado de la cuenta |
| `GET /sync/changes?cursor=...&limit=...` | Cambios de dominio ordenados por cuenta |
| `GET /sync/bootstrap` | Snapshot consistente de datos e historial de la cuenta |
| `POST /sync/import` | Importación lógica única a una cuenta vacía; conserva IDs e historial |

Las escrituras de dominio requieren `Idempotency-Key` de 8–128 caracteres y
guardan resultado y cambios en la misma transacción. Las claves HTTP normales conservan el
resultado 30 días; las claves nuevas `bn-sync:<deviceId>:<operationId>` de la outbox se guardan
sin caducidad, con el dispositivo y el hash de contenido. Reutilizar cualquier clave con otro
contenido da conflicto. Las ediciones usan
`If-Match` con el ETag numérico leído; para activar sin selección previa se usa
`"0"`. Los errores incluyen código estable y `requestId`.

Las escrituras idempotentes avanzan un cursor por propietario y registran una entrada
de cambio en la misma transacción. La revisión es por cuenta y el bloqueo de propietario
serializa la confirmación para que el cursor no dependa del orden de una secuencia SQL.
`GET /sync/changes` pagina esas confirmaciones; no incluye contenido de agenda. El cliente
usa el cursor como señal para actualizar el snapshot. `GET /sync/bootstrap` ofrece actualmente
un snapshot completo en una transacción de lectura consistente; todavía no está paginado y no
sustituye la futura descarga incremental de entidades.

Esta base de servidor no completa por sí sola la sincronización móvil: quedan por implementar
registro/generaciones/revocación de dispositivos, retención y caducidad de cursor, paginación de bootstrap y
un feed que transporte entidades (el cliente actual usa el cursor para decidir cuándo descargar
un snapshot completo). El import inicial es atómico, admite hasta 100.000 filas/32 MB y solo
acepta cuentas sin actividad. Las rutas actuales admiten metadatos de instante/zona de acción
offline, pendiente de aceptación con fixtures y dispositivos.

El propietario se deriva de la identidad verificada. Consultas e integridad SQL
quedan acotadas por propietario, con claves foráneas compuestas para evitar
relaciones entre cuentas. La rama Neon de desarrollo usada para la aceptación tiene
aplicadas las migraciones de Fase 2 y las migraciones de sync `0001_brown_anthem.sql` y
`0002_flaky_enchantress.sql` (aplicadas el 17 de noviembre de 2026). Antes de usar las rutas
desde clientes, arranca o despliega esta versión de la API.

## Alcance pendiente

- Renovación, expiración y cierre de sesión con Neon Auth; un JWT emitido sigue
  aceptándose hasta que expire, aunque la sesión se cierre.
- Comparar reglas históricas con fixtures de Android y extender el aislamiento
  entre propietarios a toda la matriz de rutas.
- Completar la aceptación Android entre dos dispositivos; el cliente Android ya implementa login,
  outbox, bootstrap e importación, y se verificó una subida desde un AVD.
- El historial limita cada petición a 31 días; su paginación no es cursor de sync.
- Antes de exponer la API públicamente, configurar rate limiting y decidir el acceso a OpenAPI.
- La aceptación HTTP completa descrita en `test:acceptance` se ejecutó con una cuenta
  reutilizable y otra temporal, eliminada al terminar.
