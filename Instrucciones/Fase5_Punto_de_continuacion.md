# Fase 5 web · Punto de continuación

**Última actualización:** 24/11/2026

## Estado

Está implementada la aplicación web local en `web/`, siguiendo la dirección visual elegida por el
usuario: tema oscuro y acento verde de Android. Los recorridos de acceso, registro, agenda, calendario,
semanas, bloques, historial, cuenta, tareas temporales y completados están construidos.

La capa web usa Neon Auth en servidor y una API BFF del mismo origen. Las escrituras validan origen,
ruta y método, preservan ETags e `Idempotency-Key`, limitan el cuerpo JSON y mantienen los borradores
ante errores inciertos o conflictos. La sesión usa cookie `HttpOnly`; el JWT de NestJS no llega al
navegador. El cursor de sincronización, los avisos de datos desactualizados y los estados de sesión
también están implementados.

## Verificación ejecutada

Actualización del 24/11/2026: se creó una cuenta sintética en el Auth URL de la rama compartida,
se alinearon `NEON_AUTH_ISSUER`, `NEON_AUTH_JWKS_URL` y `NEON_AUTH_AUDIENCE` en `backend/.env`,
y pasó `npm run test:integration`: login web → NestJS `/me` → logout. Se corrigió el BFF para
obtener el JWT desde Neon en el servidor usando la cookie HttpOnly de la sesión; el JWT no llega
al navegador. Playwright usa `http://localhost:3000`.

- `npm run typecheck`: correcto.
- `npm run lint`: correcto.
- `npm run build`: correcto; Next 16.4.6 generó las rutas de aplicación.
- `npm run test:unit`: 8 correctas.
- `npm run test:e2e`: 16 correctas en Chromium, Firefox, WebKit y Chromium móvil.
- `npm run test:integration`: 2 correctas: autenticación y recorrido H5 web.
- Aceptación API/backend: 2 correctas; prueba automatizada de nube desktop correcta.
- Recorrido H5 cruzado: web creó semana y bloque; desktop leyó y renombró; Android sincronizó el
  cambio; web restauró el nombre y Android sincronizó la restauración. Cola pendiente en cero.
- `/iniciar-sesion`: HTTP 200. Captura revisada a 1440×900 y 390×844.
- NestJS de desarrollo: `/health/live` y `/health/ready` respondieron HTTP 200.

## Aceptación restante

El recorrido básico entre los tres clientes pasó, pero la fase aún no está cerrada. Siguen pendientes
alta y persistencia de sesión tras recarga, expiración/revocación, aislamiento entre cuentas,
conflictos y reintentos reales, agenda e historial, desconexión/reconexión, revisión manual de
accesibilidad y ampliar la comparación de IDs/versiones. La Fase 3 conserva dependencias pendientes.
La app desktop se lanzó; el entorno sin pantalla impidió manipular o revisar visualmente su ventana,
por lo que su aceptación se hizo mediante la prueba automatizada de nube.

El detalle de los casos pendientes está en [la matriz de aceptación](../web/ACCEPTANCE.md). Las
credenciales de la cuenta sintética permanecen únicamente en `.env` locales ignorados por Git.

Playwright puede guardar estado de formulario al fallar. Se eliminó el directorio de resultados de
la prueba fallida al terminar; el test de integración desactiva las trazas y borra el campo de
contraseña tras enviar.

## Archivos de esta entrega

- `web/`: aplicación, BFF, configuración, lockfile, guía local y pruebas Playwright.
- `PRODUCT.md` y `DESIGN.md`: contexto de producto y sistema visual web.
- `web/ACCEPTANCE.md`: evidencia de pruebas y casos H5 aún pendientes.

Para la aceptación cruzada se agregó una prueba de integración web y una prueba condicionada de
aceptación desktop. No se modificó el código de producción Android.
