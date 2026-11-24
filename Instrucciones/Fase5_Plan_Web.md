# Fase 5 — Aplicación web de BlockNize

Plan propuesto para desarrollar la aplicación en `web/` con Next.js App Router y TypeScript.

## Objetivo y punto de partida

Permitir que una persona abra BlockNize desde el navegador, consulte su agenda y su historial, y
edite semanas y bloques de la misma cuenta que usa en Android y escritorio. El hito H5 se verificará
contra NestJS y Neon de desarrollo, con datos sintéticos y los clientes reales.

- H4 ya verificó cambios de bloques en ambos sentidos entre escritorio y un AVD. La prueba de tres
  clientes reutilizará las funciones que comparten: plantillas, bloques y selección activa.
  [Evidencia de Fase 4](Fase4_Plan_Escritorio.md).
- H3 mantiene casos abiertos de expiración o revocación de sesión de renovación y acciones
  encoladas cuyo día ya pasó según el reloj del servidor. El trabajo web puede avanzar en desarrollo
  con cuentas sintéticas. La salida a cuentas con datos personales conserva esa dependencia.
  [Continuación de Fase 3](Fase3_Punto_de_continuacion.md).
- El servidor ya ofrece agenda civil, historial, plantillas, temporales y completados. El cursor de
  sincronización señala que hubo cambios y el feed actual devuelve marcadores de confirmación.
  [Contrato del backend](../backend/README.md).

## Alcance y rutas propuestas

| Vista | Entrega | API de referencia |
|---|---|---|
| Acceso | Inicio y cierre de sesión. Alta con correo y contraseña y estado de verificación según la configuración de Neon. | SDK oficial de Neon Auth y `GET /me` |
| Hoy | Agenda del día y navegación a la semana en la zona horaria de la cuenta. | `GET /agenda?date=…` |
| Calendario | Semana de lunes a domingo, navegación por fecha y vista diaria en pantallas estrechas. | `GET /agenda/week?startDate=…`, `GET /agenda` |
| Semanas | Listar, crear y renombrar plantillas, elegir la activa, crear, editar y eliminar bloques. | `/templates`, `/templates/:id/blocks`, `/active-selection` |
| Registro | Consultar historial por rango con paginación y mostrar el estado de las ocurrencias. | `GET /history` |
| Cuenta | Mostrar la identidad y zona horaria de la cuenta. Volver a identificar la cuenta al reautenticar. | `GET /me` |
| Acciones de agenda | Crear, editar y borrar tareas temporales, completar y deshacer. Entrega posterior al primer recorrido con semanas y bloques, dentro de H5. | `/one-off-tasks`, `POST /completions` |

La navegación usará `/hoy`, `/calendario`, `/semanas`, `/registro` y `/cuenta`, además de las
rutas de autenticación del SDK. «Registro» designa el historial de actividad. «Crear cuenta» designa
el alta.

El modo web de esta fase requiere conexión para guardar. Si se pierde la red, la pestaña abierta
puede mostrar la última lectura en memoria con aviso de desactualización y mantener el formulario
en curso. La edición local duradera, IndexedDB, service worker, PWA y cola offline quedan para una
fase posterior. También quedan fuera el borrado de plantillas remotas, que carece de ruta en la API,
la edición de excepciones, la importación desde dispositivos y el chatbot.

## Arquitectura propuesta

1. **Identidad y API.** Usar el SDK oficial de Neon Auth para Next.js con sesión por cookie
   `HttpOnly` en el origen de la web y HTTPS en el entorno compartido. Probar primero que la
   capa de servidor obtiene un JWT aceptado por `GET /me` en esta rama de Neon. Confirmar el
   emisor, el sujeto y el audience cuando aplique, y comprobar que la cuenta web corresponde al
   mismo propietario que en Android y desktop. La cookie de sesión y el JWT cumplen funciones
   distintas. [Guía de Neon para Next.js](https://neon.com/docs/auth/quick-start/nextjs-api-only),
   [SDK de servidor](https://neon.com/docs/auth/reference/nextjs-server),
   [JWT de Neon](https://neon.com/docs/auth/guides/plugins/jwt).
2. **Capa web del mismo origen.** Proponer Route Handlers de Next.js para las interacciones del
   navegador y reutilizar el mismo cliente servidor en las lecturas de Server Components. La capa
   web valida sesión en cada operación, remite el JWT a NestJS y conserva códigos de error,
   `ETag` y `requestId`. Usar un destino fijo y rutas y métodos expresamente admitidos. Aplicar
   validación de origen o protección CSRF en escrituras autenticadas por cookie. NestJS decide
   permisos, restricciones y escrituras. [Guía de Next.js para Backend for Frontend](https://nextjs.org/docs/app/guides/backend-for-frontend).
3. **Aislamiento.** Servir vistas y respuestas privadas con caché compartida desactivada. Mantener
   secretos y JWT de acceso a NestJS en el servidor. Limpiar datos y borradores en memoria al
   cerrar sesión o cambiar de cuenta, e invalidar peticiones en curso para ignorar respuestas de la
   sesión anterior. Verificar también volver atrás, restaurar una pestaña y el cierre desde otra pestaña.
4. **Contrato.** Partir de `/openapi.json` y contrastar ejemplos y cabeceras con pruebas y código de
   la API. Mantener tipos TypeScript para los DTO que consume la web y un adaptador común para
   versiones, errores y autenticación. Validar en el formulario para dar respuesta rápida. NestJS
   sigue calculando ocurrencias, aplica las reglas de negocio y reconstruye el historial.
5. **Tiempo civil.** Leer la zona horaria IANA desde `/me`. Calcular «hoy» en esa zona y tratar las
   fechas `YYYY-MM-DD` como fechas civiles. Usar `occurrenceKey` y los intervalos que devuelve la
   API. En edición de bloques, respetar la máscara semanal y el contrato de minutos, incluido
   `1440` como final del día. En historial, limitar ventanas a 31 días y continuar con `nextDate`.

## Escrituras, actualización y estados

- **Guardar.** Una intención de usuario genera una `Idempotency-Key`. Un reenvío de la misma
  petición tras timeout, pérdida de respuesta o renovación de autenticación reutiliza clave, cuerpo
  y precondición. Deshabilitar el doble envío mientras la respuesta está pendiente. Una edición
  nueva o un reenvío revisado tras conflicto usa otra clave.
- **Versiones.** Usar el ETag de la plantilla para editar su nombre y sus bloques, el de la
  selección para activación, y el de la temporal en sus rutas versionadas. La primera activación
  usa `If-Match: "0"`. En un HTTP 412 con `STALE_VERSION`, conservar el formulario, mostrar qué
  cambió y ofrecer recargar o reaplicar explícitamente sobre la versión vigente. Tratar 428 como
  un fallo de integración y evitar el reenvío automático de 409.
- **Actualizar.** Recargar las lecturas afectadas tras una escritura confirmada. Consultar
  `/sync/state` al recuperar conexión, al volver a primer plano, al pulsar «Actualizar» y, como
  propuesta inicial, cada 30 segundos con la pestaña visible. Si cambió el cursor, volver a
  consultar los recursos visibles. Asociar el cursor a lecturas correctas y volver a comprobarlo
  si cambia mientras se carga la vista. Refrescar datos confirmados sin sustituir formularios abiertos.
- **Recuperar sesión.** Ante un 401, intentar recuperar la sesión una vez. Si la sesión terminó,
  solicitar nuevo inicio y comprobar la misma identidad antes de recuperar un intento pendiente.
  Diferenciar 401, 403/404, validación, conflicto, 429, incidencia de red y 5xx. El cierre web debe
  impedir el acceso por cookie. La API actual puede seguir aceptando un JWT ya emitido hasta su
  expiración, tal como documenta el backend.
- **Sin conexión.** Marcar las lecturas como desactualizadas y deshabilitar nuevas escrituras
  mientras se confirme que la API no está disponible. Si se pierde la respuesta de un guardado ya
  enviado, marcarlo como «Por confirmar». Permitir comprobar el estado o reintentar con la misma
  clave en la pestaña actual. Tras recargar, la agenda se reconstruye desde el servidor.

## Secuencia de trabajo

| Entrega | Trabajo | Se considera lista cuando |
|---|---|---|
| 5.1 · Base y prueba de autenticación | Crear `web/`, fijar Node y dependencias compatibles, añadir configuración de ejemplo y scripts de desarrollo y build. Conectar la rama Neon de desarrollo y levantar NestJS. | Cuenta sintética existente → login web → `GET /me` autenticado → logout. El build se reproduce desde el lockfile. |
| 5.2 · Lecturas y navegación | Implementar Hoy, Calendario, lista de semanas, Cuenta y Registro. Usar estados vacíos, carga, error y navegación por fecha. | Coinciden día, zona y ocurrencias con el backend en cuentas vacías y con datos. La navegación por fechas y el paginado funcionan. |
| 5.3 · Edición e interoperabilidad básica | Crear y renombrar semana, activar, editar bloques, mostrar confirmaciones y resolver versiones desactualizadas. | Una semana y un bloque se leen desde los tres clientes, y sus cambios conservan identidad y versiones. |
| 5.4 · Acciones y recuperación | Añadir temporales y completados. Implementar refresco por cursor, recuperación de sesión, separación de cuentas y recuperación de respuesta perdida. | La web y Android muestran los mismos resultados de estas acciones. Las pruebas de sesiones, reintentos y conflictos pasan. |
| 5.5 · Aceptación y entrega | Probar build de producción en desarrollo o staging con cuenta sintética, ejecutar matriz de navegadores y documentar operación, configuración y reversión. | Se registra evidencia reproducible de H5, con versión de cada cliente, entorno y resultados por caso. |

Si 5.1 falla, identificar si el problema está en el SDK, las cookies, el origen permitido o el JWT
de la rama antes de extender la capa web. Validar el flujo real con NestJS antes de cerrar la
decisión de autenticación.

## Matriz de aceptación H5

| Caso | Procedimiento y resultado esperado |
|---|---|
| Identidad y ciclo de sesión | Probar alta conforme a las reglas del entorno, login, recarga, renovación de JWT y expiración completa o revocación de sesión. Confirmar salida en la pestaña principal y respuesta protegida al volver atrás, cambiar de pestaña o recuperar una sesión caducada. |
| Aislamiento | Abrir A en los tres clientes y B en otro contexto de navegador. B conserva su propia agenda y el servidor rechaza lectura o cambio sobre recursos de A. Cambiar de A a B en la misma pestaña y comprobar que desaparecen datos y formularios de A. |
| Tres clientes | Crear y activar una semana desde la web. Crear un bloque desde la web, editarlo desde Android, editarlo desde desktop y borrarlo desde la web. Tras actualizar cada cliente, comprobar misma selección, mismo ID antes del borrado, versiones coherentes y ausencia del bloque en los tres. |
| Conflicto y reintento | Editar la misma plantilla en web y otro cliente. El intento con ETag antiguo muestra el conflicto y conserva el borrador. Simular pérdida de respuesta tras un guardado y reintentar con la misma clave. Comprobar un solo cambio confirmado y ninguna revisión o evento duplicado. |
| Agenda e historial | Comprobar temporales, completar y deshacer desde web y Android. Comparar historial antes y después de renombrar o activar una plantilla para verificar que se conserva la agenda pasada. Incluir cambio de mes, una zona distinta de la del navegador, transición de horario estacional y paginación. |
| Desconexión | Cortar red con un formulario abierto. Confirmar aviso, borrador en memoria y ausencia de mensaje de éxito. Reconectar y actualizar. Recargar sin red y comprobar que la interfaz comunica la indisponibilidad. |
| Interfaz y accesibilidad | Revisar Chromium, Firefox y WebKit con Playwright, en escritorio y con viewport móvil. Revisar teclado, foco visible, lectura de campos y errores, zoom al 200 % y un recorrido con lector de pantalla. Validar calendario diario, formularios y diálogos sin depender del color ni de arrastrar. [Guía de Playwright en Next.js](https://nextjs.org/docs/app/guides/testing/playwright). |

Automatizar tipos, reglas de presentación de fechas y errores, recorridos web con Playwright y
regresión de contrato. Mantener los recorridos con Neon y los clientes reales como aceptación
explícita de desarrollo. Usar la cuenta de prueba prevista y una segunda identidad temporal para
aislamiento. Registrar IDs de prueba y retirar los datos por las vías de limpieza de aceptación.
Para plantillas sin ruta de borrado, preparar una cuenta temporal con limpieza de perfil verificada
antes de repetir el escenario de tres clientes.

## Entrega y paso a producción

- Repositorio: `web/` compilable, configuración de ejemplo sin secretos, guía de arranque desde
  PowerShell y pruebas reproducibles. Registrar commit o build, rama Neon, versiones de clientes,
  resultado por caso y limpieza de cuentas en el informe de aceptación.
- Entorno compartido: Next.js con runtime de servidor, HTTPS y cuenta de desarrollo aislada.
  Configurar `NEON_AUTH_BASE_URL`, `NEON_AUTH_COOKIE_SECRET`, URL privada de NestJS y origen
  web por entorno. Comprobar disponibilidad de NestJS y Neon, tiempos de espera, logs con
  `requestId` y rutas de configuración para revertir a la versión web previa.
- Antes de publicar: cerrar los casos abiertos de H3 aplicables al uso de datos personales,
  verificar dominios y correo de autenticación, política de alta, límites de peticiones y exposición
  de OpenAPI. Si se habilitan altas abiertas, verificar la recuperación de contraseña y los enlaces
  de correo en el dominio final. El proveedor de hosting y el dominio pueden decidirse al preparar
  el despliegue.

H5 se da por verificado en desarrollo cuando pasan los casos de la matriz, la API del proyecto
mantiene su aceptación existente y el informe prueba la convergencia de los tres clientes en su
alcance común. La publicación con datos personales requiere completar las condiciones anteriores.
