# Retro Car Dash — Project Context

> CÓMO está construido el sistema (stack, arquitectura, convenciones).

## Propósito

**Retro Car Dash** es la app Android que corre en la tablet del tablero del BMW E36 323ti Compact de Juan (proyecto "Smart Car - BMW 323ti Pack M"). Combina navegación con mapa, voz turn-by-turn y, a futuro, datos del motor por OBD y servicios del auto.

Open source bajo MIT en https://github.com/juancruzobaid/retro-car-dash. Se diseña para ser portable a otros autos con tablet-based retrofits, no solo al E36.

### Filosofías rectoras

- **Estética retro / bitmap:** fondo negro, acento rojo (`#E60000`), tipografía monospace, todo en mayúsculas cuando corresponde.
- **Un solo widget visible a la vez:** overlay mínimo. El mapa domina la pantalla.
- **Pensado para usar con dedos manejando:** áreas táctiles generosas (64dp+), botones separados, nada apretado.
- **No Google Play Services:** la tablet corre sin cuenta Google por seguridad. Nada que dependa de GMS.
- **Offline-first eventual:** por ahora online (OpenFreeMap + Photon). El camino es PMTiles offline de Argentina entera.

## Stack

| Área | Herramienta | Versión |
|------|-------------|---------|
| Lenguaje | Kotlin | 2.x |
| UI | Jetpack Compose + Material 3 | — |
| Mapa | MapLibre Native Android SDK | 11.13.1 |
| Routing | Stadia Maps Valhalla API | v1 |
| Geocoding (search) | Photon by Komoot | público, sin API key |
| Reverse geocoding | Nominatim OSM | público |
| Voz salida | Android TextToSpeech | sistema |
| Voz entrada | Android RecognizerIntent | sistema |
| Build | Gradle Kotlin DSL | — |
| Min SDK | Android 10 (API 29) | — |
| Target SDK | API 37 | — |
| IDE | Android Studio Rabbit 2 | 2026.2.2 Patch 1 |

## Estructura del repo

```
retro-car-dash/
├── AGENTS.md
├── README.md
├── LICENSE                        MIT
├── build.gradle.kts               Root Gradle
├── settings.gradle.kts
├── gradle/
├── docs/
│   ├── PROJECT_CONTEXT.md        (este archivo)
│   ├── MASTER_PLAN.md            Roadmap + estado
│   └── IMPLEMENTATION_LOG.md     Log cronológico de cambios
└── app/
    ├── build.gradle.kts           Deps + BuildConfig
    └── src/main/
        ├── AndroidManifest.xml    Landscape, immersive, permisos
        ├── res/                   Recursos estándar
        └── java/com/cpobaid/retrocardash/
            └── MainActivity.kt    TODO el código (≈1500 líneas)
```

**Nota sobre el archivo único:** `MainActivity.kt` concentra todas las responsabilidades. Es intencional mientras el proyecto cabe cómodamente en un archivo; split cuando supere ~2500 líneas o cuando se agregue una segunda pantalla real.

## Organización interna de `MainActivity.kt`

En orden de aparición:

1. **Imports**
2. **`DashColors` object** — constante única de color (`ACCENT`, `ACCENT_HEX`)
3. **Clase `MainActivity`** — setup inicial (keep-screen-on, immersive, setContent)
4. **`ColorCalibrationScreen` composable** — pantalla de prueba de 8 colores, útil para iterar el color del acento en pantallas con calibración rara. Hoy no se usa; se activa manualmente en `setContent`.
5. **Data classes:** `SearchResult`, `SearchResponse`, `ManeuverPoint`, `RouteResult`
6. **`MapScreen` composable** — la pantalla principal. Contiene:
   - Estados (`isChaseView`, `routeGeometry`, `searchQuery`, etc.)
   - Launchers (`permissionLauncher`, `speechLauncher`)
   - `LaunchedEffect` y `DisposableEffect` para GPS, TTS, routing, reverse geocode
   - El `Box` con `AndroidView(MapView)` + overlays
   - El `ModalBottomSheet` del buscador con `CustomKeyboard`
7. **`applyChaseView` helper** — fuerza Chase View con padding 80-20%
8. **`searchPlaces`** — llama a Photon
9. **`fetchRoute`** — llama a Valhalla
10. **`decodePolyline`** — decode precision 6 de la geometry de Valhalla
11. **`maneuverIcon`** — mapea tipo Valhalla a `ImageVector` de Material
12. **`haversineMeters`** — distancia GPS en metros
13. **`fetchCurrentStreet`** — llama a Nominatim reverse
14. **`enableLocation`** — activa el location component de MapLibre
15. **`KeyboardKey` composable** — una tecla
16. **`CustomKeyboard` composable** — el teclado QWERTY completo

## Convenciones

- **Nombres:** `camelCase` para variables y funciones; `PascalCase` para composables, clases y enums; `UPPER_SNAKE_CASE` para constantes top-level.
- **Tags de log:** siempre `"RetroCarDash"`.
- **Thread safety:** las llamadas de red van a `Dispatchers.IO`. Cualquier estado compartido entre coroutines debe ser thread-safe (ver `spokenManeuvers` como caso particular).
- **`HttpURLConnection`:** siempre cerrar con `.disconnect()` en `finally`.
- **`BuildConfig.DEBUG`:** envolver logs verbosos (response bodies, URLs sensibles).
- **Compose state:** `remember { mutableStateOf(...) }` para estado UI; `remember { mutableSetOf(...) }` para colecciones que no participan en recomposición pero deben persistir.

## Dependencias críticas

| Paquete | Rol |
|---------|-----|
| `org.maplibre.gl:android-sdk` | Renderizado del mapa + localización |
| `androidx.compose.material3` | UI primitives |
| `androidx.compose.material-icons-extended` | Iconos de maniobra (TurnLeft, TurnRight, etc.) |
| `androidx.lifecycle:lifecycle-runtime-compose` | `LocalLifecycleOwner` para el MapView |
| `kotlinx.coroutines` | Red async + loops de polling |

## Variables de entorno / secrets

Stored in `local.properties` (gitignored):

```properties
STADIA_MAPS_API_KEY=...
```

Inyectada en `app/build.gradle.kts` → `BuildConfig.STADIA_MAPS_API_KEY`.

## Target físico

- **Tablet:** Samsung Galaxy Tab A11 WiFi (SM-X133)
  - Android 15, 4GB RAM, 64GB storage, GPS integrado, USB-C OTG
  - **Panel LCD con cast cálido:** por eso el acento es `#E60000` en lugar de rojos más saturados; el panel satura el canal rojo y lo que se ve en pantalla es significativamente más naranja que el hex real.
- **Orientación:** landscape fija (`android:screenOrientation="landscape"`).
- **Internet:** por tethering del celular del conductor (WiFi hotspot o BT). No hay SIM en la tablet.

## Permisos declarados (hoy)

En `AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_COARSE_LOCATION`

Voice input (`RecognizerIntent`) **no requiere** `RECORD_AUDIO` porque la Activity del sistema maneja el permiso de micrófono sola.

## Flujos clave

### Búsqueda de destino
`CustomKeyboard` o 🎙️ → `searchQuery` → debounce 400ms → `searchPlaces` (Photon) → `searchResults` → usuario tapea → `fetchRoute` (Valhalla) → `routeGeometry` + `routeManeuvers` → se dibuja ruta + `applyChaseView` fuerza modo 3ra persona.

### Voz turn-by-turn
Loop cada 2s revisa todas las maniobras:
- A 200-300m: habla "En X metros, {instrucción}"
- A <50m: habla solo "{instrucción}"
- Anti-repetición con `spokenManeuvers` (set de IDs ya hablados).

### Próxima maniobra
Loop cada 500ms:
1. Encuentra el índice del punto más cercano del usuario en la geometry.
2. La próxima maniobra es la primera cuyo `beginShapeIndex > closestShapeIdx` y cuyo tipo no sea 1/2/3 (Start).
3. Si no hay ninguna adelante, muestra el destino (tipos 4/5/6).
4. Distancia por haversine al punto de la maniobra.

### Chase vs God View
- **Chase (default):** `padding top = 0.6 × visibleHeight`, `padding bottom = barHeight` → dot al 80% desde arriba del área visible, tilt 60°, zoom 18.
- **God:** `padding top = 0`, `padding bottom = barHeight` → dot centrado en área visible, tilt 0°, zoom 16.
- Toggle: tap en el mapa.
- Forzado automático a Chase: al arrancar (primer GPS fix) y al setear ruta nueva.
