# BlockNize — Plan general de desarrollo

**Versión del plan:** 0.1  
**Fecha:** 4 de noviembre de 2026  
**Estado:** Propuesta de hoja de ruta; no implica tareas ejecutadas ni decisiones de implementación cerradas.

## 1. Visión y objetivo

Convertir BlockNize, actualmente una aplicación Android **local-first** de organización por bloques horarios, en un producto multiplataforma con datos sincronizados, sin perder el funcionamiento sin conexión ni el historial de las actividades. La inteligencia artificial se integrará **al final** como otra forma de consultar y operar sobre las funcionalidades existentes, no como una vía alternativa de acceso directo a los datos.

**Orden de desarrollo acordado:** consolidar Android → implementar backend y persistencia remota → estabilizar sincronización → construir escritorio y web → integrar IA.

### Criterios generales de éxito

- Una misma cuenta puede utilizar BlockNize en varios dispositivos sin perder datos ni duplicar operaciones.
- Android sigue siendo útil sin conexión y conserva Room como almacenamiento local.
- Las operaciones sobre tareas, plantillas, revisiones, completados y excepciones producen resultados coherentes en todas las plataformas.
- Escritorio y web reutilizan el contrato funcional del backend, evitando duplicar reglas de negocio.
- La IA ejecuta acciones únicamente mediante herramientas controladas y servicios autorizados del backend.

## 2. Stack tecnológico previsto

| Componente | Tecnología | Responsabilidad |
|---|---|---|
| Android | Kotlin + Jetpack Compose | Aplicación principal y primera referencia funcional |
| Persistencia local Android | Room / SQLite + DataStore | Agenda offline y preferencias |
| Escritorio | Kotlin Multiplatform; valorar Compose Multiplatform Desktop | Cliente de escritorio, con lógica reutilizable cuando sea viable |
| Web | Next.js | Cliente web |
| Backend | NestJS + TypeScript | API, autorización, reglas de negocio, sincronización y herramientas de IA |
| Datos remotos | Neon PostgreSQL | Persistencia central, relaciones, revisiones e historial |
| Identidad | Neon Auth | Identificación y sesiones; integración exacta por validar al diseñar el backend |
| IA en desarrollo | llama.cpp + modelo local compatible | Ensayos sin depender de un servicio externo |
| IA en producción | OpenRouter mediante API key **solo en el backend** | Acceso configurable a modelos externos |

**Decisión de arquitectura:** backend modular inicialmente; no introducir microservicios ni una base de datos adicional sin una necesidad demostrada. El backend es la autoridad para cambios sincronizados y validaciones. Los clientes mantienen su estado local y usan contratos API estables.

**Organización del repositorio:** monorepositorio con `android/` como raíz del proyecto
Android e `Instrucciones/` para documentación compartida. NestJS se incorporará en
`backend/`, al mismo nivel; `desktop/` y `web/` se añadirán cuando comience su desarrollo.
Ver `README.md` para apertura en Android Studio, comandos y rutas de los artefactos.

## 3. Punto de partida (Android existente)

Según el informe del 04/11/2026, BlockNize Android utiliza Jetpack Compose, **Room v4**, DataStore, WorkManager y Glance. Dispone de plantillas semanales, tareas temporales, completados, calendario, registro, concentración, widget y excepciones por ocurrencia con eventos históricos. No existe todavía un backend ni un sistema de cuentas.

La base local incluye 12 tablas, entre ellas revisiones, eventos, finalizaciones y excepciones. El informe registra **24 pruebas unitarias y 45 instrumentadas superadas** en su última batería, pero aún quedan comprobaciones en dispositivos físicos, recuperación de backup y casos temporales de borde.

**Implicación:** no migrar datos reemplazando Room de golpe; preservar el esquema, el historial y la identidad de las ocurrencias mientras se introduce la sincronización.

## 4. Fase 1 — Consolidación de Android y persistencia local

**Objetivo:** tener una base Android fiable antes de repartirla entre dispositivos.

**Trabajo principal:**

1. Registrar una versión estable del código y mantener migraciones de Room no destructivas.
2. Incorporar **exportación y restauración completas** de los datos relevantes (no solo plantillas JSON), y probar la política de backup Android.
3. Corregir o delimitar casos sensibles: cambios de hora/zona horaria, excepciones con origen pasado, final del día, reconstrucción histórica y refrescos del widget.
4. Separar gradualmente interfaces, repositorios y reglas de dominio, sin reescribir toda la aplicación.
5. Ampliar pruebas de flujos completos y probar en un dispositivo físico.

**Entregable:** Android estable, datos recuperables, dominio documentado y batería de regresión fiable.

**Criterio de salida:** una actualización o restauración no pierde tareas, excepciones, completados ni historial; los flujos principales se validan de extremo a extremo.

## 5. Fase 2 — Base de datos remota, identidad y API NestJS

**Objetivo:** establecer un servidor funcional antes de sincronizar clientes.

**Trabajo principal:**

1. Diseñar el esquema PostgreSQL de Neon a partir de las entidades y reglas reales de Room, sin copiar automáticamente todas las tablas tal cual.
2. Definir identificadores globales estables, propietario de los registros, versiones y restricciones de integridad.
3. Integrar **Neon Auth** con NestJS; verificar de forma concreta el mecanismo de sesiones/tokens y la validación de identidad en el backend.
4. Crear módulos de NestJS para usuarios/contexto, plantillas, agenda, temporales, completados, excepciones e historial.
5. Publicar una API documentada (por ejemplo, OpenAPI), con validación de entradas, permisos por propietario y migraciones de BD versionadas.
6. Garantizar transacciones para operaciones compuestas, especialmente edición de excepciones y escritura de eventos.

**Entregable:** API autenticada y PostgreSQL con operaciones funcionales probadas, todavía sin exigir sincronización completa.

**Criterio de salida:** un usuario no puede acceder a los datos de otro; las reglas históricas importantes se mantienen; no se exponen credenciales de Neon a clientes.

## 6. Fase 3 — Sincronización Room ↔ backend ↔ Neon

**Objetivo:** sincronización bidireccional robusta entre dos dispositivos Android, conservando el uso offline.

**Diseño previsto:**

- Room sigue siendo la fuente de lectura de la interfaz Android y del widget.
- Las escrituras offline se registran en Room junto con una **cola transaccional de operaciones pendientes** (*outbox*).
- NestJS valida, aplica y ordena operaciones remotas de forma idempotente.
- Cada cliente descarga cambios mediante un **cursor de sincronización**, en lugar de reconstruir toda la base cada vez.
- Se contemplan altas, modificaciones, eliminaciones, cambios de cuenta, restauración inicial, reintentos y operaciones duplicadas.
- Los conflictos se detectan mediante versiones o precondiciones; no se usa indiscriminadamente «última escritura gana» para operaciones históricas.
- WorkManager programa reintentos; la señal en tiempo real, si se añade, servirá para acelerar la descarga, no para sustituirla.

**Orden de incorporación:** plantillas y bloques → selección activa → tareas temporales → completados → revisiones, eventos y excepciones por ocurrencia.

**Entregable:** dos móviles con la misma cuenta muestran la misma agenda y el mismo historial tras sincronizar.

**Criterio de salida:** pruebas superadas de edición offline, conexión intermitente, conflictos, reinstalación, reintentos duplicados y recuperación sin pérdida de datos.

## 7. Fase 4 — Aplicación de escritorio (Kotlin Multiplatform)

**Objetivo:** ofrecer una experiencia de escritorio equivalente en las funciones esenciales.

**Trabajo principal:**

1. Extraer y compartir **modelos, contratos API y lógica de dominio que realmente sean portables**, evitando mover a KMP código dependiente de Android.
2. Construir la interfaz de escritorio, preferentemente con Compose Multiplatform si encaja con los requisitos.
3. Elegir y validar la persistencia local de escritorio; no dar por supuesto que Room Android y todas sus integraciones se trasladan sin cambios.
4. Incorporar inicio de sesión, sincronización y pruebas cruzadas con Android.
5. Adaptar navegación, teclado, tamaño de ventanas y comportamiento en segundo plano.

**Entregable:** aplicación de escritorio con agenda, edición y sincronización de funciones prioritarias.

**Criterio de salida:** cambios realizados en escritorio y Android convergen sin alterar el historial.

## 8. Fase 5 — Aplicación web (Next.js)

**Objetivo:** permitir utilizar BlockNize desde el navegador mediante la misma API.

**Trabajo principal:**

1. Integrar sesiones y autorización con el sistema de identidad decidido.
2. Implementar las vistas principales: inicio, calendario, bloques, registro y edición.
3. Reutilizar contratos y validaciones de API; NestJS sigue siendo la autoridad para escrituras.
4. Añadir actualización de datos, gestión de errores, diseño responsive y pruebas de navegación/accesibilidad.
5. Definir explícitamente el alcance offline de la web: **no prometer paridad offline con Room** hasta diseñar almacenamiento y colas locales para navegador.

**Entregable:** cliente web con las operaciones esenciales y datos sincronizados con Android y escritorio.

**Criterio de salida:** consistencia entre las tres plataformas y control correcto de sesiones y permisos.

## 9. Fase 6 — Chatbot y agente con herramientas

**Objetivo:** añadir una interfaz conversacional que consulte y ejecute operaciones de BlockNize sin saltarse las reglas existentes.

**Trabajo principal:**

1. Diseñar en NestJS un módulo de IA con un **adaptador de proveedor**: llama.cpp para desarrollo y OpenRouter para producción.
2. Mantener claves de API, cuotas y credenciales exclusivamente en el servidor.
3. Definir herramientas acotadas, por ejemplo: `get_day_agenda`, `find_free_slots`, `create_one_off_task`, `move_occurrence`, `complete_occurrence` y `get_week_summary`.
4. Hacer que cada herramienta llame a los **mismos servicios de negocio** que la API normal, aplicando identidad, permisos, validación, transacciones e idempotencia.
5. Introducir vista previa/confirmación para modificaciones importantes y medidas frente a instrucciones maliciosas, errores del modelo, abuso y costes inesperados.
6. Probar conversaciones y ejecuciones reales con casos reproducibles antes de habilitar funciones de escritura ampliamente.

**Entregable:** chatbot capaz de consultar y proponer acciones, y de ejecutar las autorizadas con trazabilidad.

**Criterio de salida:** ninguna herramienta puede modificar datos fuera de la cuenta autenticada; los cambios quedan reflejados en todos los clientes mediante la sincronización normal.

## 10. Requisitos transversales

- **Seguridad:** HTTPS, gestión segura de sesiones, autorización por recurso, secretos solo en servidor y principio de mínimo privilegio.
- **Integridad temporal:** conservar identidad de ocurrencias, zonas horarias, revisiones, eventos y finalizaciones al migrar y sincronizar.
- **Calidad:** pruebas unitarias, integradas y de extremo a extremo; entornos separados de desarrollo y producción; migraciones con rollback planificado.
- **Observabilidad:** errores de sincronización, tiempos de respuesta, conflictos, operaciones de IA y consumo de recursos, sin registrar datos sensibles innecesariamente.
- **Recuperación:** exportación/restauración, copias de seguridad, procedimiento de recuperación y pruebas de fallo.
- **Costes:** revisar cuotas y límites reales de Neon, hosting de NestJS y proveedor de IA antes de publicar.
- **Documentación:** contratos API, modelo de datos, reglas de negocio, decisiones de arquitectura y guía de despliegue.

## 11. Dependencias, prioridades y asuntos pendientes

| Decisión pendiente | Momento adecuado |
|---|---|
| Esquema PostgreSQL, IDs, representación de eventos y borrados | Fase 2 |
| Flujo exacto de Neon Auth con Android, escritorio, web y NestJS | Fase 2 |
| Reglas de conflicto y protocolo de cambios | Antes de iniciar Fase 3 |
| Estrategia de código compartido y almacenamiento en escritorio | Antes de Fase 4 |
| Política offline en navegador | Antes de Fase 5 |
| Modelos, herramientas permitidas, confirmaciones y límites de coste | Antes de Fase 6 |

**No objetivos de las primeras fases:** chatbot, automatizaciones de IA, microservicios, tiempo real obligatorio, migración inmediata de la interfaz a KMP o reescritura completa del Android existente.

## 12. Hitos de la hoja de ruta

| Hito | Resultado verificable |
|---|---|
| H1 | Android estable y datos completos restaurables |
| H2 | NestJS + Neon + Neon Auth operativos con reglas y permisos |
| H3 | Sincronización fiable entre dos Android |
| H4 | Cliente escritorio interoperable |
| H5 | Cliente web interoperable |
| H6 | IA con tool calling seguro y compartiendo reglas de negocio |

> **Principio rector:** priorizar corrección e integridad de los datos sobre velocidad de entrega. No pasar a una nueva plataforma o a IA hasta que el hito anterior esté validado.

---

**Fuentes de partida:** decisiones de stack y secuencia acordadas en la conversación; `Estado_actual_aplicacion(1).md` (informe del 04/11/2026). Las decisiones futuras identificadas expresamente como «por validar» no se presentan como funcionalidades ya implementadas.
