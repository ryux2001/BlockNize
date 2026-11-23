# Fase 2 — Estado de implementación y aceptación

Fecha: 13 de noviembre de 2026. Estado: backend implementado; migración inicial
aplicada y aceptación HTTP real superada en la rama Neon de desarrollo para los
flujos descritos abajo. Quedan pendientes el ciclo de sesión y la comparación con
las reglas locales de Android.

## Código entregado

- `backend/`: NestJS + TypeScript, Drizzle y PostgreSQL (`pg`). Lockfile npm incluido.
- 14 tablas de producto, migración inicial versionada y restricciones SQL para
  propietario, plantillas, bloques, revisiones, etiquetas y eventos históricos.
- Guard Bearer que comprueba firma con JWKS remoto de Neon Auth, `iss`, caducidad,
  `sub` y algoritmos admitidos. Crea el perfil local del usuario al primer acceso.
- Rutas para cuenta, etiquetas, plantillas, bloques, selección activa, agenda,
  tareas temporales, completados, excepciones e historial.
- Revisiones y snapshots de agenda; escritura de estados/eventos en transacciones.
- `If-Match`, claves de idempotencia transaccionales (30 días), validación de DTO,
  errores con `requestId`, CORS configurable y OpenAPI.
- Guía de arranque: [backend/README.md](../backend/README.md).
- Colección manual HTTP: [BlockNize.http](../backend/requests/BlockNize.http).

## Comprobaciones locales

- `npm install` completó e instaló dependencias y generó/actualizó el lockfile.
- `npm run build` completó correctamente.
- `npm run test:unit` compila el backend y pasa 8 pruebas de tiempo civil, zonas
  horarias, medianoche 24:00 y máscara de días de semana.
- Arranque local con `backend/.env`: `/api/v1/health/live`, `/api/v1/health/ready`
  y `/openapi.json` respondieron HTTP 200. La readiness confirma conexión PostgreSQL.
- `GET /api/v1/me` sin token respondió 401, como exige el guard.
- El endpoint JWKS configurado respondió HTTP 200 con una clave pública.
- `npm exec drizzle-kit check` confirmó que el journal de migraciones local es coherente.
- `npm run start:prod` arrancó correctamente tras corregir la ruta compilada del entrypoint.
- `npm run db:migrate` aplicó la migración inicial a la rama Neon confirmada como
  desarrollo; una segunda ejecución también terminó correctamente.
- `npm run test:database` pasó contra PostgreSQL: rechazó relaciones entre cuentas
  e intervalos/máscaras inválidos y aceptó fin 24:00. Los fixtures se revirtieron.
- `npm run test:acceptance` pasó con JWT reales de Neon Auth (2 pruebas HTTP):
  identidad, aislamiento, creación/reintento concurrente, conflicto de clave/cuerpo,
  ETag obsoleto, activación y edición activa con revisión nueva, agenda semanal,
  tareas temporales (crear/editar/borrar), completar/deshacer, SKIP/REPLACE/RESTORE
  e historial.
- Se creó la cuenta reutilizable `blocknize-backend-acceptance@example.test` en Neon
  Auth. Su contraseña aleatoria se guarda solo en el `.env` ignorado por Git; los
  tokens se obtienen en cada ejecución y no se persisten.
- La segunda cuenta temporal y su perfil local se eliminaron y verificaron tras la
  prueba. También se revirtieron las plantillas, tareas y eventos de aceptación.
- Tras actualizar `drizzle-orm` a 0.45.4, `npm audit --omit=dev` informa cero
  vulnerabilidades de producción. `npm audit` aún informa 9 vulnerabilidades de
  severidad baja/moderada en herramientas de desarrollo (Nest CLI y drizzle-kit).
- `npm exec drizzle-kit check` confirma que el journal local es coherente.

## Aceptación real y cuenta reutilizable

La migración inicial está aplicada en la rama Neon de desarrollo. El recorrido
automatizado está documentado en [Neon_Auth_Acceptance.md](../backend/test/Neon_Auth_Acceptance.md)
y se repite con `cd backend; npm run test:acceptance`. Conserva una cuenta de prueba,
crea una segunda cuenta desechable para verificar separación de propietarios y la
elimina junto con sus datos locales al finalizar.

La documentación vigente de Neon expone `session.access_token` como JWT y la URL
JWKS para verificar su firma; el flujo real se confirmó en esta rama durante la
aceptación: [flujo de autenticación Neon](https://neon.com/docs/auth/authentication-flow),
[gestión Auth por rama](https://neon.com/docs/auth/guides/manage-auth-api).

## Pendiente para declarar H2

- Falta probar renovación, expiración y cierre de sesión de Neon Auth. La API no
  consulta la sesión y acepta un JWT firmado hasta su expiración.
- La prueba cubre los flujos principales y accesos cruzados de plantilla, agenda,
  tarea temporal e historial; falta extender la matriz negativa a todas las rutas y
  validar tokens inválidos, expirados y rotación de claves.
- La revocación inmediata de JWT no está implementada; se acepta el token firmado
  hasta su expiración. Documentar esto antes de conectar una interfaz pública.
- Antes de exponer un despliegue público, añadir rate limiting y decidir si OpenAPI
  queda protegido; la configuración CORS no sustituye esas medidas de hosting.
- El historial admite ventanas de hasta 31 días por llamada, no define cursor de sync.
- Falta comparar fixtures de reglas e historial con Android y probar un rollback
  forzado entre escritura de estado y evento sobre PostgreSQL real.
- Android sigue local-first. Login, asociación/migración de datos y sincronización
  pertenecen a la fase 3.
