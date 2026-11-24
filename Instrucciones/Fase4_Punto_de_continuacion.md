# Punto de continuación — Fase 4

Última actualización: 18 de noviembre de 2026

Estado: Fase 4 aceptada para agenda esencial local/remota y convergencia básica Android ↔ desktop.
H3 de sincronización Android sigue abierto. El alcance, evidencia y límites están en
[Fase4_Plan_Escritorio.md](Fase4_Plan_Escritorio.md).

## Resultado alcanzado

- La agenda local se conserva con formato versionado, escritura atómica y permisos del usuario.
- La vista de cuenta usa Neon Auth, bootstrap y mutaciones esenciales con ETag e idempotencia.
- El outbox offline persiste cuerpos, cabeceras, identidad y clave idempotente. Se recupera al reabrir,
  reintenta al reconectar y conserva conflictos resolubles.
- Se corrigió el endpoint POST de bloques para aceptar IDs aportados por el cliente.
- Un test de aceptación desktop autenticó una cuenta Neon temporal, ejecutó cambios concurrentes y
  CRUD de bloques y verificó la limpieza de la cuenta/perfil temporal.
- El AVD `emulator-5554` probó Android → desktop (alta, edición y borrado) y desktop → Android
  (actualización del bloque). La cuenta sintética quedó desconectada del AVD y el backend local quedó
  detenido al terminar.
- La distribución portable y el MSI Windows `BlockNize-0.4.0.msi` se generan desde Gradle. Al probar
  el paquete se detectó y corrigió la ausencia de `java.net.http`; el runtime final incluye también
  `jdk.accessibility`.

## Verificaciones ejecutadas

```powershell
.\android\gradlew.bat -p desktop :desktopApp:compileKotlin
.\android\gradlew.bat -p desktop :desktopApp:test
npm.cmd run test:acceptance
.\android\gradlew.bat -p android :app:assembleDebug
.\android\gradlew.bat -p desktop :desktopApp:createDistributable
.\android\gradlew.bat -p desktop :desktopApp:packageMsi
```

El paquete se abrió con un perfil temporal, sin iniciar sesión ni leer datos del perfil personal; se
mostró la agenda local en 1000×700 dp. El MSI se construyó pero no se instaló. La prueba desktop con
Neon eliminó su usuario temporal. El AVD terminó desconectado de la cuenta de aceptación.
En la ventana de prueba, dos Tab y Enter abrieron «Nueva semana»; se cerró la instancia sin guardar.

## Límites pendientes fuera de la aceptación H4

- La sesión no se restaura tras reiniciar: se vuelve a pedir el inicio de sesión. Tokens, cookies y
  contraseñas no se guardan en disco.
- El contenido local tiene permisos de acceso restringidos pero no está cifrado.
- Faltan pruebas completas de expiración/revocación de sesión y de fecha de acción para H3; consultar
  [Fase3_Punto_de_continuacion.md](Fase3_Punto_de_continuacion.md).
- El alcance compartido no incluye historial, completados, temporales ni excepciones. El backend aún
  no ofrece borrado remoto de semanas ni feed incremental de entidades.
- Se incluye `jdk.accessibility` en el runtime, pero no se probó un lector de pantalla. En Windows,
  Compose entrega accesibilidad vía Java Access Bridge, que debe habilitarse en el sistema para usarla.
  Se probó una ruta básica de teclado, pero el orden completo, el foco visible y el uso con lector de
  pantalla aún necesitan revisión.
- No se verificó la instalación o actualización del MSI.

## Si se retoma el trabajo

1. No repetir los recorridos cerrados de F4; consultar primero el estado actual con `git status` y
   preservar los cambios preexistentes de las fases 1–3.
2. Si se quiere ampliar H4, comenzar por integración de historial y edición accesible con lector de
   pantalla, y luego validar instalación/actualización del MSI.
3. Si se trabaja en H3, retomar los casos abiertos de
   [Fase3_Punto_de_continuacion.md](Fase3_Punto_de_continuacion.md); no reutilizar la aceptación
   desktop/Android como prueba de sincronización entre dos Android.

## Archivos principales

- `desktop/desktopApp/src/main/kotlin/com/blocknize/desktop/Main.kt`: interfaz local/remota y resolución
  de conflictos.
- `desktop/desktopApp/src/main/kotlin/com/blocknize/desktop/DesktopCloudClient.kt`: auth, bootstrap,
  mutaciones y replay del outbox.
- `desktop/desktopApp/src/main/kotlin/com/blocknize/desktop/DesktopSyncStore.kt`: persistencia de
  operaciones offline y seguridad de archivos.
- `desktop/desktopApp/src/main/kotlin/com/blocknize/desktop/DesktopScheduleStore.kt`: persistencia de
  agenda local.
- `desktop/desktopApp/build.gradle.kts`: targets y módulos runtime de Windows.
- `desktop/desktopApp/src/test/kotlin/com/blocknize/desktop/`: persistencia, aceptación remota y
  convergencia con Android.
