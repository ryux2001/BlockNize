# BlockNize Web

Cliente web de BlockNize con Next.js App Router. Comparte la cuenta, la agenda y las semanas con
Android y escritorio mediante el backend NestJS.

## Requisitos

- Node.js 20.19 o posterior y npm.
- Una rama de desarrollo Neon Auth y la API local de BlockNize configuradas para esa rama.
- PowerShell en Windows para los comandos de esta guía.

## Arranque local

Desde la raíz del repositorio:

```powershell
cd web
npm ci
Copy-Item .env.example .env.local
```

Edita `web/.env.local` con la URL de la rama Neon y una clave aleatoria propia de al menos 32
caracteres en `NEON_AUTH_COOKIE_SECRET`. Puedes generarla sin instalar herramientas adicionales:

```powershell
node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"
```

Configura `BLOCKNIZE_API_URL` para NestJS. En desarrollo local suele ser
`http://localhost:3001/api/v1`. `BLOCKNIZE_WEB_ORIGIN` debe coincidir con el origen que abre el
navegador, normalmente `http://localhost:3000`. Copia `NEON_AUTH_BASE_URL` como **Auth URL exacta**
desde Neon Console → Project → Branch → Auth → Configuration. No la derives de `NEON_AUTH_ISSUER`;
el issuer solo sirve para validar el JWT en NestJS. La [guía oficial de Neon para Next.js](https://neon.com/docs/auth/quick-start/nextjs-api-only)
muestra el formato del Auth URL y la cookie del servidor.

En otra terminal, arranca la API conforme a su [guía de desarrollo](../backend/README.md). Después,
desde `web/`:

```powershell
npm run dev
```

Abre `http://localhost:3000`. El inicio de sesión debe usar la misma cuenta Neon que Android y la
API. Si Neon acepta las credenciales pero NestJS devuelve 401, compara el issuer y el audience de
la rama con la configuración JWT del backend.

## Secciones

- `/iniciar-sesion` y `/crear-cuenta` usan Neon Auth. La sesión web queda en cookie `HttpOnly`.
- `/hoy` y `/calendario` muestran la agenda en la zona horaria de la cuenta y permiten navegar por
  fechas.
- `/semanas` permite crear y renombrar semanas, activarlas y añadir, editar o eliminar bloques con
  ETags y control de versiones.
- `/registro` permite consultar hasta 31 días por ventana y continuar el historial paginado.
- `/cuenta` muestra identidad, verificación del correo y zona horaria, y permite cerrar sesión.
- Las tareas temporales se pueden crear, editar y eliminar; las ocurrencias se pueden completar y
  deshacer.

El navegador llama a rutas del mismo origen. Next valida la cookie, aplica una lista de rutas y
métodos permitidos y reenvía el JWT a NestJS desde el servidor. No pongas JWT, claves de Neon ni
credenciales de base de datos en variables `NEXT_PUBLIC_*`. Las escrituras también validan el origen,
limitan el cuerpo JSON y preservan `ETag`, `requestId` e idempotencia.

La web necesita conexión para guardar. Si una respuesta de guardado se pierde, conserva el borrador
en la pestaña y permite reintentar la misma intención. No implementa cola duradera, PWA, edición de
excepciones ni borrado de semanas porque la API no ofrece esa operación.

## Comprobaciones y pruebas

```powershell
npm run typecheck
npm run lint
npm run build
npx playwright install chromium firefox webkit
npm run test:unit
npm run test:e2e
npm run test:integration
```

`test:unit` cubre fechas civiles, zona horaria, `24:00`, ETags y reintentos idempotentes. `test:e2e`
recorre las pantallas de acceso y alta en Chromium, Firefox, WebKit y Chromium móvil. Los pasos
reproducibles y los casos de Neon y de los tres clientes están en [ACCEPTANCE.md](ACCEPTANCE.md).
`test:integration` ejecuta el cliente web local contra Neon y una API NestJS ya iniciada, si
`NEON_AUTH_BASE_URL` está configurado y
`BLOCKNIZE_ACCEPTANCE_EMAIL` / `BLOCKNIZE_ACCEPTANCE_PASSWORD` contienen la cuenta sintética.

## Entorno compartido y reversión

Usa runtime de servidor con HTTPS. Define `NEON_AUTH_BASE_URL`, `NEON_AUTH_COOKIE_SECRET`,
`BLOCKNIZE_API_URL` y `BLOCKNIZE_WEB_ORIGIN` como variables privadas del entorno. Revisa además
`CORS_ORIGINS` de NestJS cuando otros clientes deban acceder a la API desde un origen web.

Para revertir, vuelve a desplegar la versión web anterior y conserva la rama y los datos de Neon.
La web no realiza migraciones de esquema ni modifica el almacenamiento local Android.
