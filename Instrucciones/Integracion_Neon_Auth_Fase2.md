# Integración de Neon Auth — Fase 2

- Fecha de registro: 12 de noviembre de 2026
- Entorno probado: branch Neon `desarrollo`
Estado: identidad y aceptación funcional verificadas; cierre completo de H2 pendiente de las comprobaciones indicadas al final.

## Resumen

BlockNize usa Neon Auth (Managed Better Auth) para registrar e iniciar sesión. NestJS no recibe contraseñas ni administra sesiones: los clientes obtienen un JWT de sesión con el SDK de Neon y lo envían a la API en `Authorization: Bearer <JWT>`.

La integración se verificó con dos cuentas sintéticas en `desarrollo`. Ambas obtuvieron una sesión real, sus claims `iss` y `aud` coincidieron con la configuración del backend y `GET /api/v1/me` respondió `200`. Las cuentas y filas creadas para la aceptación se limpiaron al terminar. No se guardaron tokens, contraseñas ni URLs privadas en este documento.

## Configuración local

Completar `backend/.env` con valores de la misma branch Neon de desarrollo:

| Variable | Valor que corresponde |
|---|---|
| `DATABASE_URL` | Conexión PostgreSQL de runtime de `desarrollo`, con TLS. |
| `MIGRATION_DATABASE_URL` | Conexión usada para aplicar migraciones; también debe apuntar a `desarrollo`. |
| `NEON_AUTH_JWKS_URL` | `jwks_url` publicado para Neon Auth en esa branch. |
| `NEON_AUTH_ISSUER` | Valor exacto del claim `iss` del JWT emitido por esa branch. |
| `NEON_AUTH_AUDIENCE` | Valor exacto de `aud` observado en el JWT. En la prueba de `desarrollo` estaba presente y se configuró; el esquema permite dejarlo vacío solo si el proveedor no emite `aud`. |
| `CORS_ORIGINS` | Orígenes locales autorizados por la API. |

No copiar estos valores a documentación versionada. `backend/.env` está excluido del repositorio; usar `backend/.env.example` como plantilla sin sustituir sus placeholders por secretos.

## Flujo de inicio de sesión

El cliente debe crear un cliente Auth con la URL de Neon Auth de la branch correspondiente. Después del inicio de sesión, toma el token de la sesión y lo mantiene en memoria para llamar a la API:

```ts
const result = await authClient.signIn.email({ email, password });
const session = result.data?.session
  ?? (await authClient.getSession()).data?.session;
const token = session?.access_token ?? session?.token;

const response = await fetch(`${API_URL}/api/v1/me`, {
  headers: { Authorization: `Bearer ${token}` },
});
```

El SDK puede exponer el JWT como `session.access_token` o `session.token`, según el cliente. La cookie opaca de Neon Auth no sirve como Bearer. La página local de prueba solo debe mostrar claims no sensibles (`iss` y `aud`) y el estado HTTP; no debe imprimir ni persistir el JWT o la contraseña.

## Validación en NestJS

El guard global de `backend/src/auth/neon-jwt.guard.ts`:

1. Lee `NEON_AUTH_JWKS_URL`, `NEON_AUTH_ISSUER` y `NEON_AUTH_AUDIENCE` desde la configuración.
2. Verifica la firma con las claves JWKS remotas, el emisor, la audiencia cuando está configurada, la expiración y la presencia de `sub`.
3. Acepta los algoritmos configurados (`RS256`, `ES256` y `EdDSA`).
4. Resuelve o crea el perfil BlockNize identificado por la pareja `issuer` + `subject`.
5. Inyecta esa identidad en la petición; los servicios consultan y modifican recursos usando el propietario autenticado.

Las rutas de salud son públicas. Las rutas de cuenta y producto requieren un Bearer válido. La autenticación se realiza en Neon Auth; NestJS no ofrece endpoints para recibir contraseñas.

## Resultado de la aceptación

En la última ejecución pasaron **59 de 59 comprobaciones** contra la API local conectada a la branch `desarrollo`. Se validó, entre otros puntos:

- Health, OpenAPI, alta de dos identidades, claims JWT y perfiles `/me`.
- Rechazo de solicitudes sin Bearer y de JWT malformados.
- Creación de plantillas y bloques, activación, agenda diaria/semanal y snapshots de revisión.
- Aislamiento entre A y B para plantillas, bloques, etiquetas, selección activa, agenda, historial, completados, excepciones y tareas puntuales.
- Idempotencia en reintentos, rechazo de reutilización de clave con otro cuerpo y solicitudes concurrentes.
- `If-Match`: precondición obligatoria (`428`), versiones obsoletas (`412`) y escrituras concurrentes.
- Completar/deshacer ocurrencias; excepciones futuras `SKIP`, `REPLACE` y `RESTORE`.
- Crear, editar y borrar tareas puntuales, incluidos sus eventos históricos.
- Rollback PostgreSQL al forzar un fallo entre la escritura de una tarea y su evento. El trigger temporal usado para la prueba se retiró.

La limpieza final retiró las cuentas sintéticas, sus perfiles y los datos de producto de prueba. La comprobación de destino se hizo en memoria; no se mostró la cadena de conexión ni el host.

## Pendiente para cerrar H2

- Reiniciar NestJS y volver a comprobar una lectura autenticada y persistencia después del reinicio. Se dejó el proceso actual intacto.
- Validar renovación y expiración de sesión con el cliente real. La matriz comprobó tokens válidos y malformados, pero no esperó la expiración de un JWT.
- La API valida JWT de forma stateless: un JWT firmado puede seguir siendo aceptado hasta expirar después de cerrar la sesión en Neon Auth; la revocación inmediata no está implementada.
- Comparar reglas de fechas e historial con fixtures de Android. La integración Android, asociación de cuenta y sincronización pertenecen a la fase 3.
- Actualizar el informe anterior de Fase 2, que todavía puede indicar que la aceptación Neon está pendiente.

No declarar H2 completamente cerrada hasta realizar las comprobaciones pendientes y registrar su resultado.
