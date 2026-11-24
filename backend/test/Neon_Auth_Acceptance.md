# Cuenta de aceptación de Neon Auth

Cuenta persistente para aceptar la API en la rama **de desarrollo** de Neon:

- Correo: `blocknize-backend-acceptance@example.test`
- Nombre visible: `BlockNize Backend Acceptance`
- Uso: pruebas de autenticación, agenda, tareas temporales, completados, excepciones,
  historial, idempotencia y aislamiento por propietario.

El dominio `.test` está reservado para pruebas y no representa un buzón real. No uses
esta identidad para datos personales ni en producción. La cuenta y su perfil local se
conservan entre ejecuciones para reutilizarlos.

## Ejecutar la aceptación

Desde `backend/`, ejecuta:

```powershell
npm run test:acceptance
```

El script compila la API, la arranca si no está disponible, inicia sesión en esta
cuenta y obtiene un JWT nuevo directamente de Neon Auth. Crea una segunda cuenta
temporal para probar el aislamiento y la elimina al terminar; también revierte los
datos de prueba del perfil persistente. No copia tokens a archivos.

La primera ejecución crea la cuenta y guarda su contraseña aleatoria en estas
variables de `backend/.env`:

```dotenv
BLOCKNIZE_ACCEPTANCE_EMAIL=...
BLOCKNIZE_ACCEPTANCE_PASSWORD=...
```

`.env` está excluido de Git. La contraseña y los JWT no se guardan en este documento,
en el código ni en los logs. Si se pierde o se elimina `.env`, no regeneres la cuenta
automáticamente: restaura la contraseña local o elimina esta identidad de Neon Auth
antes de volver a aprovisionarla.
