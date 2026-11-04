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
├── Instrucciones/   # Plan general, estado y documentación funcional
├── .gitignore
└── README.md
```

La raíz es la del producto y del repositorio Git, no la del proyecto Gradle.
`backend/` (NestJS), `desktop/` y `web/` se incorporarán cuando comience cada desarrollo;
todavía no existen ni se han generado proyectos vacíos.

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
- [Concentración y excepciones](Instrucciones/Plan_Concentracion_y_Excepciones.md).

H1 aún requiere validación física y restauración real del backup Android. La existencia
del backend o de la sincronización no debe deducirse de esta reorganización.
