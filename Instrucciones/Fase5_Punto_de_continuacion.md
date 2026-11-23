# Fase 5 web · Punto de continuación

**Última actualización:** 19/11/2026

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

- `npm run typecheck`: correcto.
- `npm run lint`: correcto.
- `npm run build`: correcto; Next 16.4.6 generó las rutas de aplicación.
- `npm run test:unit`: 8 correctas.
- `npm run test:e2e`: 16 correctas en Chromium, Firefox, WebKit y Chromium móvil.
- `/iniciar-sesion`: HTTP 200. Captura revisada a 1440×900 y 390×844.
- NestJS de desarrollo: `/health/live` y `/health/ready` respondieron HTTP 200.

## Bloqueo de integración actual

El usuario compartió el Auth URL exacto de la rama. Se probó en memoria, sin guardarlo en el
repositorio ni crear `web/.env.local`. El endpoint ya responde, pero la solicitud de login recibió
`403 Invalid origin` para `http://127.0.0.1:3000`. La configuración `backend/.env` declara
`CORS_ORIGINS=http://localhost:3000`; falta repetir el flujo con el web origin canónico
`http://localhost:3000` y confirmar que ese origen está admitido en Neon Auth.

No se completó todavía login → `/me` → logout. La aceptación de interoperabilidad entre web,
Android y escritorio, aislamiento entre cuentas, reintentos reales y estados de sesión sigue abierta.
La Fase 3 también conserva casos pendientes, tal como registra el plan de Fase 5. No marcar H5 como
aceptada hasta completar esas pruebas.

El emulador Android está disponible como `emulator-5554`; no se modificó la app Android ni se hizo
ningún cambio en la configuración del emulador.

## Continuar mañana

1. Copiar `web/.env.example` a `web/.env.local`. Configurar el Auth URL exacto compartido por el
   usuario, un cookie secret nuevo de 32 bytes, `BLOCKNIZE_API_URL=http://localhost:3001/api/v1` y
   `BLOCKNIZE_WEB_ORIGIN=http://localhost:3000`. No derivar el Auth URL del issuer.
2. Arrancar NestJS desde `backend/` con `npm run start:dev` y comprobar `/health/ready`.
3. Arrancar la web desde `web/` con `npm run dev` y abrir `http://localhost:3000`. Si Neon Auth
   devuelve de nuevo `Invalid origin`, revisar su lista de orígenes permitidos; no cambiar la
   configuración remota sin autorización explícita.
4. Cargar en memoria el usuario sintético de `backend/.env` y repetir `npm run test:integration`.
   El test valida la sesión, la identidad devuelta por `/me` y el cierre de sesión. No imprimir las
   variables ni guardar las credenciales en archivos web.
5. Si el flujo pasa, actualizar [la aceptación](../web/ACCEPTANCE.md). Después, continuar la matriz
   de tres clientes con datos sintéticos y limpieza documentada.

Playwright puede guardar estado de formulario al fallar. Se eliminó el directorio de resultados de
la prueba fallida al terminar; el test de integración desactiva las trazas y borra el campo de
contraseña tras enviar.

## Archivos de esta entrega

- `web/`: aplicación, BFF, configuración, lockfile, guía local y pruebas Playwright.
- `PRODUCT.md` y `DESIGN.md`: contexto de producto y sistema visual web.
- `web/ACCEPTANCE.md`: evidencia de pruebas y casos H5 aún pendientes.

Los cambios preexistentes de Android, backend, desktop y `README.md` se conservaron sin tocar.
