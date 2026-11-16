# AGENTS.md — Retro Car Dash

> Arranque, reglas de seguridad e invariantes. Leelo antes de tocar código.

## Al iniciar una sesión

Leer, en este orden, antes de tocar código:
1. `docs/PROJECT_CONTEXT.md` — stack, arquitectura y convenciones.
2. `docs/MASTER_PLAN.md` — roadmap y estado actual.
3. Las últimas entradas de `docs/IMPLEMENTATION_LOG.md`.

Después de cada tarea desplegada: actualizar `IMPLEMENTATION_LOG.md` (entrada nueva al principio con formato `Qué / Hecho / Pendiente / Refs`) y, si corresponde, `MASTER_PLAN.md`. Commit + push a `main`.

## Arquitectura (resumen — ver PROJECT_CONTEXT)

App Android nativa Kotlin + Jetpack Compose, archivo único.

```
app/src/main/
├── java/com/cpobaid/retrocardash/
│   └── MainActivity.kt    Todo el código: data classes, UI composables, helpers
├── res/                   Recursos estándar Android
└── AndroidManifest.xml    Landscape only, immersive fullscreen, permisos
```

El target físico es **Samsung Galaxy Tab A11 WiFi (SM-X133)** montada en el torpedo de un BMW E36 323ti, landscape, sin cuenta Google.

## Invariantes (NO romper)

- **Color de acento:** único, centralizado en `DashColors.ACCENT` (`#E60000`) y `DashColors.ACCENT_HEX`. Cambiar el color = 2 líneas. NUNCA inline otro color de acento.
- **Tipografía:** `FontFamily.Monospace` en toda la UI (estética retro bitmap).
- **Fondo:** `Color.Black` sólido. Sin transparencias en overlays principales.
- **Orientación:** `landscape` fijo. La app nunca rota.
- **Pantalla siempre encendida:** `FLAG_KEEP_SCREEN_ON` obligatorio.
- **Immersive fullscreen:** status bar y nav bar ocultas. No agregar system bars.
- **Teclado:** custom in-app, NUNCA el IME de Android (la navigation bar inferior del teclado es ruido visual).
- **Idioma:** UI en español argentino. TTS con `Locale("es", "AR")`. Voice input con `EXTRA_LANGUAGE = "es-AR"`.
- **Sin cuenta Google:** la tablet corre sin Google Services. No usar APIs que dependan de Google Play Services (Google Maps, Firebase, etc.). Usar equivalentes OSS.
- **GPS como única fuente de ubicación:** no WiFi localization, no cell tower. Solo `ACCESS_FINE_LOCATION`.

## Reglas de seguridad

### Secrets

- **`local.properties` es GITIGNORED.** Nunca commitear la Stadia Maps API key.
- Las claves se inyectan al build via `BuildConfig` en `app/build.gradle.kts`.
- Si agregás un nuevo servicio con API key, replicá el patrón de `STADIA_MAPS_API_KEY`.

### Código

- Sin llamadas de red en el main thread. Siempre `withContext(Dispatchers.IO)` o similar.
- Toda `HttpURLConnection` debe cerrarse con `.disconnect()` en `finally`.
- Logs de debug envueltos con `if (BuildConfig.DEBUG)` — no imprimir response bodies en release.
- `try/catch` ancho en los loops de `LaunchedEffect` para que un fallo de red no mate la corutina.

### Permisos

- Permisos declarados en `AndroidManifest.xml`. Hoy: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- Si agregás uno nuevo, documentarlo acá y en `PROJECT_CONTEXT.md`.

## Convenciones

- **Archivos Kotlin:** `PascalCase.kt`. Hasta hoy, todo vive en `MainActivity.kt` (acepta refactor a múltiples archivos cuando crezca).
- **Composables:** `PascalCase` (e.g. `MapScreen`, `CustomKeyboard`).
- **Variables de estado:** `camelCase` (e.g. `isChaseView`, `routeGeometry`).
- **Constantes top-level:** `UPPER_SNAKE_CASE` (e.g. `BAR_HEIGHT_WITH_ROUTE_DP`).
- **Enums de tipo maniobra, etc.:** si los introducís, `UpperCamelCase` con `enum class`.
- **Commits atómicos y descriptivos** en español. Prefijo convencional: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`.
- **Mensaje de commit:** título corto imperativo + cuerpo con bullets de qué cambió.

## Stack rápido

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Mapa:** MapLibre Native Android SDK 11.13.1
- **Geocoding (buscar destino):** Photon by Komoot (sin API key)
- **Reverse geocoding (calle actual):** Nominatim OSM
- **Routing:** Stadia Maps Valhalla API (requiere `STADIA_MAPS_API_KEY`)
- **Voz turn-by-turn:** Android TextToSpeech (es-AR)
- **Voice input:** Android RecognizerIntent (es-AR)
- **Build:** Gradle Kotlin DSL. Min SDK 29. Target SDK 37.

## Scripts útiles

Local en Mac:
```bash
# Build debug APK
./gradlew assembleDebug

# Install en tablet (con USB conectado + USB debug ON)
./gradlew installDebug

# Correr tests unit
./gradlew test
```

En Android Studio: `Shift + F10` para build + run.

## Flujo de trabajo típico

1. Cambio de código → commit + push a `main`.
2. Actualizar `docs/IMPLEMENTATION_LOG.md` (nueva entrada al principio) en el mismo commit o uno chico de docs.
3. En la Mac del dev: `git pull` + `Shift+F10` en Android Studio → instala en la tablet.
4. Probar en la tablet (desconectada del USB, con hotspot del celu si necesita red).
