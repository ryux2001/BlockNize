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
| Login web → Neon → NestJS `/me` → logout | Correcto el 24/11/2026 en `http://localhost:3000`, con cuenta sintética de la rama de desarrollo. Playwright completó el inicio de sesión, leyó `/me` y cerró la sesión. |
| Integración Playwright | 2 correctas: autenticación y recorrido H5 compartido. |
| Aceptación API/backend | 2 correctas; la prueba de nube de escritorio también pasó. |

## Aceptación de integración

El Auth URL de desarrollo es el proporcionado para la rama Neon de aceptación. Playwright usa ahora
`http://localhost:3000`, que está incluido en los orígenes permitidos. El BFF obtiene el JWT en el
servidor a partir de la cookie HttpOnly de Neon y lo reenvía a NestJS; no expone el JWT al navegador.
El flujo login → `/me` → logout pasó con una cuenta sintética. Las credenciales viven solo en los
archivos `.env` locales ignorados por Git.

| Caso | Estado | Evidencia que debe registrarse |
|---|---|---|
| Alta, login, `/me`, recarga y logout | Parcial: login, `/me` y logout pasaron; alta y persistencia tras recarga pendientes. | Cuenta sintética, rama Neon y `requestId` sin guardar JWT. |
| Renovación, expiración y revocación de sesión | Pendiente; dependencia abierta de Fase 3. | Resultado en pestaña activa, atrás y otras pestañas. |
| Aislamiento entre cuentas A y B | Parcial: aceptación API confirmó tokens de propietarios separados y rechazo de lectura/escritura cruzada. Falta cambiar de identidad en el navegador y verificar que se limpian vistas y borradores. | `npm run test:acceptance`; falta recorrido multiusuario web. |
| Semana y bloque entre web, Android y escritorio | Parcial: web creó semana/bloque; escritorio leyó y renombró; Android recibió el cambio al sincronizar; web restauró el nombre y Android recibió la restauración. Pendiente ampliar a IDs/versiones y más casos de refresco. | Prueba Playwright web, prueba de aceptación desktop y sincronización observada en `emulator-5554`; cola pendiente en cero. |
| ETag antiguo y reintento de respuesta perdida | Parcial: backend rechazó ETag obsoleto con 412 y probó repetición idempotente; falta simular respuesta perdida en la web y comprobar que conserva el borrador. | `npm run test:acceptance`; falta recorrido UI con fallo de red. |
| Temporales, completar, deshacer e historial | Parcial: aceptación API recorrió temporales, completar/deshacer, excepciones e historial. Falta comparación visual/funcional desde web y Android, zona horaria y paginación. | `npm run test:acceptance`; falta matriz cruzada de agenda. |
| Desconexión y reconexión | Pendiente | Aviso, borrador en pestaña y estado tras reintentar. |
| Chromium, Firefox, WebKit y Chromium móvil | Correcto: 16 pruebas E2E. Teclado, zoom y revisión manual de accesibilidad pendientes. | Resultado Playwright y revisión manual. |

La app de escritorio se lanzó, pero el ejecutor no ofrece una pantalla/ventana manejable para hacer
una revisión manual de su interfaz. Su lectura y escritura en la nube se validaron con la prueba de
aceptación automatizada. La Fase 3 mantiene pendientes casos de expiración o revocación y operaciones
encoladas cuyo día ya pasó. H5 sigue parcialmente aceptada hasta cerrar los casos pendientes de esta
matriz y las dependencias de Fase 3 aplicables.
