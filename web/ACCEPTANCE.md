# Fase 5 · Aceptación web

Este documento separa la verificación del código local de la aceptación H5 contra Neon y los tres
clientes. La aceptación final requiere una rama de desarrollo compartida y cuentas sintéticas.

## Comprobación local

Entorno de esta entrega: Windows, Node.js 22.19.0, npm 11.5.2 y Next.js 16.4.6. Usa el lockfile de
`web/` para repetir la instalación.

| Comprobación | Resultado |
|---|---|
| TypeScript (`npm run typecheck`) | Correcto. |
| ESLint (`npm run lint`) | Correcto. |
| Build de producción (`npm run build`) | Correcto. Next compiló y generó las rutas de aplicación. |
| Respuesta de `/iniciar-sesion` local | HTTP 200. |
| Captura de acceso en escritorio y móvil | Revisada a 1440×900 y 390×844. |
| Pruebas unitarias Playwright | 8 correctas. |
| E2E Playwright en Chromium, Firefox, WebKit y móvil | 16 correctas. |
| NestJS `health/live` y `health/ready` | HTTP 200 en la rama de desarrollo. |
| Login web → Neon → NestJS `/me` → logout | Pendiente. Con el Auth URL exacto, Neon devolvió `403 Invalid origin` para `http://127.0.0.1:3000`; repetir en `http://localhost:3000`. |

## Aceptación de integración

El login se intentó primero con un URL provisional derivado del issuer y respondió HTTP 404. Después,
el usuario compartió el Auth URL exacto y la solicitud llegó a Neon, que respondió `403 Invalid
origin` para el origen local `http://127.0.0.1:3000`. No se guardaron credenciales en archivos del
cliente. Para continuar, prueba desde `http://localhost:3000` y confirma que ese origen esté
permitido en Neon Auth. Si aún falla, registra el error sin incluir campos del formulario.

| Caso | Estado | Evidencia que debe registrarse |
|---|---|---|
| Alta, login, `/me`, recarga y logout | Pendiente | Cuenta sintética, rama Neon y `requestId` sin guardar JWT. |
| Renovación, expiración y revocación de sesión | Pendiente | Resultado en pestaña activa, atrás y otras pestañas. |
| Aislamiento entre cuentas A y B | Pendiente | Cada identidad ve solo sus recursos y borradores. |
| Semana y bloque entre web, Android y escritorio | Pendiente | IDs, versiones, selección activa y lectura tras refrescar. |
| ETag antiguo y reintento de respuesta perdida | Pendiente | Conflicto conserva borrador y reintento no duplica evento. |
| Temporales, completar, deshacer e historial | Pendiente | Comparación web y Android en la zona horaria de la cuenta. |
| Desconexión y reconexión | Pendiente | Aviso, borrador en pestaña y estado tras reintentar. |
| Chromium, Firefox, WebKit, móvil, teclado y zoom | Pendiente | Resultado Playwright y revisión manual de accesibilidad. |

La Fase 3 mantiene pendientes casos de expiración o revocación y operaciones encoladas cuyo día ya
pasó. No se debe marcar H5 como aceptada ni conectar cuentas con datos personales hasta registrar
la evidencia anterior y cerrar las dependencias de Fase 3 aplicables.
