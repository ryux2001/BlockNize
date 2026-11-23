# BlockNize

Organizador local-first de actividades por bloques horarios. Actualmente dispone de una
aplicación Android en Kotlin, Jetpack Compose, Room y DataStore.

## Organización del repositorio

```text
BlockNize/
├── android/         # Proyecto Android y Gradle completos
│   ├── app/         # Código, recursos, esquemas de Room y pruebas
│   ├── gradle/      # Wrapper, catálogo de versiones y configuración del daemon
│   ├── gradlew
│   └── gradlew.bat
├── backend/         # API NestJS, Drizzle y migraciones Neon/PostgreSQL
├── Instrucciones/   # Plan general, estado y documentación funcional
├── .gitignore
└── README.md
```

La raíz es la del producto y del repositorio Git, no la del proyecto Gradle.
El backend NestJS se desarrolla por separado en `backend/`. Android ya tiene una integración
local-first de autenticación y sincronización en desarrollo; las migraciones de sync están
aplicadas en Neon de desarrollo. Para conectar una cuenta real hace falta que la API sea accesible
desde el dispositivo y configurar sus URLs en Ajustes. `desktop/` y `web/` se incorporarán
en sus fases correspondientes.

## Backend (fase 2)

La API usa NestJS, Drizzle y PostgreSQL de Neon. Consulta la [guía de configuración
y rutas](backend/README.md). Completa `backend/.env` desde `.env.example`, aplica
las migraciones y arranca NestJS desde `backend/`. La integración Android se configura
desde Ajustes con las URLs del backend, Neon Auth y el origen permitido.

## Abrir Android

En Android Studio, abrir **`android/`**, no la raíz general. La aplicación mantiene
`com.blocknize.app` y su estructura interna; este cambio no altera la base de datos instalada.

Se requiere el SDK Android correspondiente a `compileSdk` (actualmente 36.1) y acceso
a un JDK compatible. El daemon declara Java 21 en
`android/gradle/gradle-daemon-jvm.properties`; Gradle puede provisionarlo mediante Foojay.
Android Studio puede crear `android/local.properties` con la ruta del SDK de cada equipo.
Ese archivo no se versiona.

### Comandos desde la raíz general (PowerShell)

```powershell
.\android\gradlew.bat -p android :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin
.\android\gradlew.bat -p android :app:connectedDebugAndroidTest
.\android\gradlew.bat -p android :app:lintDebug :app:assembleRelease
```

Las pruebas instrumentadas requieren un dispositivo o emulador conectado. Si se abre una
terminal dentro de `android/`, se puede usar directamente `.\gradlew.bat`, sin `-p android`.
En Linux/macOS: `./android/gradlew -p android` desde la raíz general.

### APK y resultados

- Debug instalable: `android/app/build/outputs/apk/debug/app-debug.apk`.
- Release sin firma de producción: `android/app/build/outputs/apk/release/app-release-unsigned.apk`.
- Pruebas unitarias: `android/app/build/reports/tests/testDebugUnitTest/index.html`.
- Pruebas instrumentadas: `android/app/build/reports/androidTests/connected/debug/index.html`.
- Lint: `android/app/build/reports/lint-results-debug.html`.

Los APK y resultados se generan localmente: no se suben al repositorio.
Las cachés y configuración de IDE que queden en la antigua raíz se mantienen ignoradas;
no se trasladan ciegamente ni son necesarias para compilar desde `android/`.

## Documentación

- [Plan general de desarrollo](Instrucciones/BlockNize_Plan_General_Desarrollo.md).
- [Estado y referencia funcional](Instrucciones/Estado_actual_aplicacion.md).
- [Fase 1: persistencia, copias y validación](Instrucciones/Fase1_Consolidacion_Android.md).
- [Fase 1: pruebas de aceptación pendientes](Instrucciones/Fase1_Pruebas_Aceptacion.md).
- [Fase 2: plan de backend](Instrucciones/Fase2_Plan_Backend.md).
- [Fase 2: implementación y aceptación](Instrucciones/Fase2_Implementacion.md).
- [Fase 3: modos de uso, cuentas y sincronización Android](Instrucciones/Fase3_Plan_Sincronizacion.md).
- [Fase 3: punto de continuación](Instrucciones/Fase3_Punto_de_continuacion.md).
- [Fase 4: plan y aceptación de escritorio](Instrucciones/Fase4_Plan_Escritorio.md).
- [Fase 4: punto de continuación](Instrucciones/Fase4_Punto_de_continuacion.md).
- [Cliente BlockNize Desktop](desktop/README.md).
- [Fase 5: plan detallado de la aplicación web](Instrucciones/Fase5_Plan_Web.md).
- [Backend: instalación y rutas](backend/README.md).
- [Concentración y excepciones](Instrucciones/Plan_Concentracion_y_Excepciones.md).

H1 aún requiere validación física y restauración real del backup Android. H2 tiene
la API y los flujos HTTP principales comprobados con dos identidades Neon Auth en desarrollo.
H4 quedó aceptado el 18/11/2026 para la agenda esencial desktop y su convergencia básica con Android;
el MSI y la distribución portable se construyen con Gradle. H3 sigue abierto para la aceptación
completa Android ↔ Android, la expiración de sesión y la recuperación offline.
