# BlockNize Desktop

Cliente de escritorio Compose Multiplatform para Windows, con agenda semanal local y sincronización
de plantillas y bloques con el backend.

Desde la raíz del repositorio, en PowerShell:

```powershell
.\android\gradlew.bat -p desktop :desktopApp:compileKotlin
.\android\gradlew.bat -p desktop :desktopApp:test
.\android\gradlew.bat -p desktop :desktopApp:run
.\android\gradlew.bat -p desktop :desktopApp:createDistributable
.\android\gradlew.bat -p desktop :desktopApp:packageMsi
```

El MSI de Windows se genera en `desktop/desktopApp/build/compose/binaries/main/msi/` y contiene
un runtime propio. La distribución portable se genera en `desktop/desktopApp/build/compose/binaries/main/app/`.
La configuración de la fase 4 usa el nombre `BlockNize` y versión `0.4.0`.
El runtime incluye `java.net.http` y `jdk.accessibility`; en Windows el lector de pantalla usa Java
Access Bridge, que está deshabilitado por defecto en el sistema.

La agenda local permite crear varias semanas, activarlas, renombrarlas y gestionar bloques con
descripción, etiqueta y repetición semanal. Se conserva en
`%USERPROFILE%\.blocknize\desktop-schedule.properties`, con formato versionado, permisos de acceso
restringidos al usuario y reemplazo atómico. Funciona sin cuenta ni red.

La cuenta se conecta con Neon Auth. Se pueden leer el perfil y la agenda, crear y renombrar semanas,
activar una semana, y añadir, editar y eliminar bloques. Las escrituras usan ETags y claves de
idempotencia; tras cada mutación se recarga el snapshot completo del servidor. La API actual no ofrece
borrado remoto de semanas.

El outbox conserva operaciones remotas pendientes por cuenta en
`%USERPROFILE%\.blocknize\sync\`, con permisos locales restringidos. Sobrevive a cierres y reintenta
al reconectar. Un conflicto queda visible para resolverlo conservando el cambio local o usando la
versión de la cuenta. Los archivos no guardan contraseña, cookies ni tokens: la sesión dura mientras
la app está abierta y, tras reiniciarla, hay que iniciar sesión de nuevo. El archivo de agenda y el
outbox tienen permisos locales restringidos, pero no están cifrados.

La lógica compartida está en `shared/src/commonMain`. Android compila esa misma carpeta fuente desde
su build Gradle actual; el proyecto KMP independiente la compila como `:shared`.

El alcance validado de Fase 4 cubre la agenda esencial y su convergencia con Android. Historial,
completados, temporales y excepciones requieren ampliar el contrato compartido; la descarga incremental
espera un feed de entidades en el backend. H3 de sincronización Android y las pruebas de sesión caducada
siguen en su propio plan de Fase 3.
