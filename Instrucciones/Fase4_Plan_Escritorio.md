# Fase 4 — Aplicación de escritorio

Fecha de inicio: 17 de noviembre de 2026

Aceptada para el alcance funcional esencial: 18 de noviembre de 2026. H4 queda cerrado para agenda
semanal, operaciones de cuenta verificadas y convergencia básica con Android. No implica paridad con
historial, completados, temporales o excepciones; H3 de Android sigue en su propio plan.

El detalle de la siguiente continuación está en [Fase4_Punto_de_continuacion.md](Fase4_Punto_de_continuacion.md).

## Objetivo

Ofrecer en escritorio una agenda semanal local que funcione sin cuenta ni red y que pueda sincronizar
con Android las plantillas y los bloques incluidos en el contrato actual del backend.

## Decisiones de arquitectura

- Compose Multiplatform sobre JVM. El build de `desktop/` se mantiene independiente para no acoplar
  esta fase a una migración de las herramientas Android.
- `ScheduleTemplate`, `ScheduleBlock`, `ScheduleWindow` y `parseScheduleMinute` viven en
  `desktop/shared/src/commonMain`; Android compila esa misma fuente.
- El archivo local versionado se reemplaza atómicamente. El horario, configuración y outbox se guardan
  en el perfil del usuario con permisos locales restringidos. El contenido no está cifrado.
- Contraseña, cookies y tokens solo permanecen en memoria. Al reiniciar hay que iniciar sesión de nuevo;
  no se restaura una sesión automáticamente.
- La API actual no expone borrado remoto de semanas ni un feed incremental de entidades. Por eso la
  vista remota no ofrece ese borrado y recarga el snapshot completo tras las mutaciones.
- El build Windows incorpora `java.net.http` para el cliente API y `jdk.accessibility` para Java Access
  Bridge. La distribución MSI se identifica como `BlockNize` versión `0.4.0`.

## Resultado implementado

- Build de escritorio JVM y módulo KMP compartido.
- Agenda local con varias semanas, selección y activación, renombre, creación y borrado de semanas, y
  alta, edición y borrado de bloques con descripción, etiqueta y repetición semanal.
- Persistencia local versionada, migración del formato inicial, reemplazo atómico y bloqueo de escrituras
  si el archivo existente no se puede leer.
- Inicio/cierre de sesión Neon Auth, renovación de token mientras la app está abierta, perfil y lectura
  de `/sync/bootstrap`. Las mutaciones remotas envían `If-Match`, `Idempotency-Key` y metadatos de acción.
- Outbox duradero por identidad de cuenta: conserva las operaciones exactas y su clave idempotente,
  reintenta tras reconectar o reiniciar, avanza versiones por agregado y permite conservar la propuesta
  local o adoptar la versión de la cuenta en un conflicto.
- La interfaz muestra la cuenta local/remota, actividad de sincronización, cambios pendientes, errores y
  conflictos. Los formularios se desplazan cuando no caben en la ventana.
- El backend acepta la creación de bloques que traen un ID generado por el cliente; se corrigió el POST
  para tratar esa operación como alta y preservar el ID.
- Empaquetado Windows con distribución portable y MSI autocontenido. El runtime incluye red y
  accesibilidad; iniciar el primer paquete encontró la ausencia de `java.net.http`, que se corrigió y se
  volvió a verificar en el runtime final.

## Comprobaciones y evidencia

- `:desktopApp:compileKotlin` y `:desktopApp:test`: compilación y pruebas de persistencia local, outbox,
  permisos de archivo, recuperación del outbox tras reabrir y avance de versiones.
- `npm.cmd run test:acceptance`: 2/2 recorridos backend y aceptación desktop real con Neon. El recorrido
  desktop probó login, bootstrap, edición concurrente, conflicto, conservación del cambio local, alta,
  edición, borrado y activación; la cuenta temporal y su perfil se eliminaron y se comprobó la limpieza.
- `:desktopApp:createDistributable` y `:desktopApp:packageMsi`: `BUILD SUCCESSFUL`. Se abrió el
  ejecutable portable final con un perfil temporal; la agenda local apareció en una ventana de
  1000×700 dp tras corregir el runtime recortado. No se instaló el MSI en Windows.
- `:app:assembleDebug`: `BUILD SUCCESSFUL`.
- En el AVD `emulator-5554` se creó un bloque desde Android, se cambió desde desktop, se volvió a editar
  desde Android y se borró desde Android. Los clientes confirmaron la convergencia en ambos sentidos;
  desktop confirmó también la ausencia remota tras el borrado. Se usó únicamente la cuenta sintética.
- La distribución final contiene los módulos `java.net.http` y `jdk.accessibility`. Compose en Windows
  ofrece accesibilidad mediante Java Access Bridge, deshabilitado por defecto en el sistema; el módulo
  está empaquetado, pero no se ejecutó una auditoría con lector de pantalla.
- Se comprobó visualmente la agenda local con el paquete a 1000×700 dp y que la ventana se podía
  redimensionar. Con la instancia aislada enfocada, dos Tab y Enter abrieron «Nueva semana»; el
  diálogo no se guardó. No se recorrió el orden completo ni se auditó el foco con lector de pantalla.

## Límites aceptados y trabajo fuera de H4

1. H3 sigue abierto: falta la aceptación completa Android ↔ Android, el caso de fecha de acción y la
   expiración/revocación completa de sesión documentados en
   [Fase3_Punto_de_continuacion.md](Fase3_Punto_de_continuacion.md). La cuenta sintética permitió
   verificar F4; no usar esto como aceptación de H3 ni de datos personales.
2. La app solicita reautenticación después de reiniciarse; no persiste credenciales ni restaura tokens.
3. Las operaciones y datos locales tienen ACL/permisos restringidos, no cifrado local.
4. La sincronización remota cubre semanas y bloques. Historial, completados, temporales y excepciones
   requieren extender el modelo/contrato compartido.
5. El servidor ofrece bootstrap completo y todavía no ofrece cambios incrementales de entidades.
6. La eliminación de semanas remotas y la instalación/actualización MSI no se probaron.
7. La ruta esencial de teclado se comprobó abriendo el diálogo «Nueva semana». Falta la revisión
   exhaustiva del orden de Tab, el foco visible y el uso con lector de pantalla.

## Criterio de aceptación de este alcance

- La agenda local se guarda de forma durable y se puede seguir usando sin red ni cuenta.
- La autenticación, lectura y mutaciones esenciales respetan ETags, autorización e idempotencia del
  backend; un conflicto conserva una opción explícita del usuario.
- Las operaciones esenciales desktop ↔ Android convergen en el AVD validado.
- Existe un paquete portable de Windows y un MSI autocontenido con el runtime requerido.
- La ventana funciona a 1000×700 dp y la acción de abrir un formulario se pudo completar con teclado.

La fase queda aceptada con este alcance funcional esencial. Las limitaciones enumeradas arriba no se
presentan como paridad total y se deben resolver en sus fases correspondientes.

## Referencias de compatibilidad

- [Compatibilidad de Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Compatibilidad de Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Distribuciones nativas de Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)
- [Accesibilidad de escritorio de Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html)
