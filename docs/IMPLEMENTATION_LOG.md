# Implementation Log — Retro Car Dash

> Entradas más recientes al principio. Cada entrega real suma una entrada con `Qué / Hecho / Pendiente / Refs`.

## 2026-11-16 — Estética tipo tablero BMW: displays rojizos para datos, botones blancos para controles, labels blancos en el mapa

**Qué:** Shift estético grande. Inspirado en el tablero clásico BMW (foto que mandó Juan): los datos viven en "displays" con fondo rojo oscuro y texto rojo-acento (`#E60000`), los controles son icono blanco sobre negro sin fondo propio. En el mapa, los nombres de calles pasan a BLANCO para que la paleta roja del overlay no compita con la información del mapa; el acento rojo queda reservado para la línea de la ruta y los datos de la UI.

**Hecho:**
- **DashColors amplía:**
  - `DISPLAY_BG = Color(0xFF4A0000)` — fondo de los displays (rojizo oscuro, estilo instrument cluster BMW).
  - `BUTTON_ICON = Color.White` — tint para iconos de botones (X cancelar, volumen).
  - `ACCENT` queda igual, ahora usado para texto de displays y la flecha de maniobra.
- **Nuevo composable `DisplayBox { content }`** — wrapper que aplica fondo rojizo + rounded corners 4dp + padding interno configurable. Centra su contenido. Puede recibir `.clickable {}` externamente cuando el display también funciona como botón (ej. "Destino").
- **Mapa: street labels en blanco:**
  - En `setStyle` block: `PropertyFactory.textColor(Color.parseColor("#FFFFFF"))` (antes `DashColors.ACCENT_HEX`). Mantiene halo negro grueso 4.0f + blur 1.0f para contraste.
- **Bottom bar con ruta (96dp):**
  - Izquierda: flecha de maniobra grande (sin fondo, tint ACCENT) + DisplayBox pequeño con "A {d}m" + DisplayBox grande con la instrucción (2 líneas, centered).
  - Derecha superior: DisplayBox con "HH:mm · N min · X km" + botón X (icono blanco en 64dp clickable box, sin fondo).
  - Derecha inferior: DisplayBox con "Por {calle}" + botón volumen (icono blanco).
- **Bottom bar sin ruta (48dp):**
  - Izquierda: DisplayBox clickable con "Destino" + lupa (lupa sigue en ACCENT porque el display entero es el "botón" visualmente).
  - Derecha: DisplayBox con "Por {calle}" + botón volumen blanco.

**Reglas de color aplicadas:**
- "Dato" → DisplayBox (fondo rojizo) + texto `DashTextStyles.accent(size)` (rojo).
- "Control puro" (X, 🔊) → sin fondo, `tint = BUTTON_ICON` (blanco).
- "Control+dato híbrido" (Destino clickable) → DisplayBox + icono interno en ACCENT. El display en sí es el affordance clickable.
- Flecha de maniobra → ACCENT sin fondo (es el "dato principal" que se mira primero; grande + rojo = alta jerarquía visual).

**Pendiente:**
- Probar en la tablet y ajustar `DISPLAY_BG` si el `#4A0000` se ve muy oscuro o muy visible en el panel cálido.
- Evaluar si vale la pena dar más contraste al "A Xm" y al display de instrucción (padding/tamaños finos).
- Teclado custom NO fue tocado — iteración separada si querés aplicarle la misma estética.

**Refs:** commit `feat: BMW-inspired display aesthetic for bottom bar + white street labels`.

---

## 2026-11-16 — Centralizar TextStyle en `DashTextStyles`

**Qué:** Los `Text` de la UI repetían el mismo quinteto `color = ACCENT, fontSize = Xsp, fontWeight = Bold, fontFamily = Monospace` en 8 lugares distintos. Cambiar la tipografía global requería tocar cada call.

**Hecho:**
- Nuevo `object DashTextStyles` con 3 fábricas:
  - `accent(sizeSp: Int): TextStyle` — rojo acento + bold + mono (el "primary" de la app)
  - `gray(sizeSp: Int): TextStyle` — gris + mono, para textos secundarios tipo status
  - `white(sizeSp: Int): TextStyle` — blanco + mono, para contenido neutral (search results, input placeholder)
- Un `BASE_MONO = TextStyle(fontFamily = FontFamily.Monospace)` interno + `.copy()` para no repetir el fontFamily en cada factory.
- Migrados los 8 `Text`:
  - "A {d}m" bajo el icono de maniobra → `accent(13)`
  - Instrucción de maniobra (2 líneas) → `accent(20).copy(lineHeight = 26.sp)` + `maxLines = 2`
  - "HH:mm · N min · X km" → `accent(16)` + `textAlign Center`
  - "Por {calle}" en bar con ruta y sin ruta → `accent(18)`
  - "Destino" (sin ruta) → `accent(20)`
  - Input display del teclado → `gray(18)` si blank, `white(18)` si tiene texto
  - Status del search → `gray(12)`
  - Rows del search result list → `white(14)`
- `KeyboardKey` NO migrado: su `fontSize`/`color` son parámetros del composable (por diseño) y la fábrica no encaja.

**Impacto:** cambiar fuente global = 1 línea. Textos consistentes por regla no por disciplina. Comportamiento idéntico (los pixels se renderizan igual).

**Refs:** commit `refactor: centralize TextStyle in DashTextStyles object`.

---

## 2026-11-16 — Backoff exponencial en el loop de reverse geocode

**Qué:** Si la conexión a Nominatim está rota (sin internet, servidor caído, timeout), el loop reintentaba cada 3 s indefinidamente. Esto consume batería (coroutine activa, request, parse) y es poco respetuoso con el servicio cuando el fallo se prolonga.

**Hecho:**
- Variable local `consecutiveFailures: Int` dentro del `LaunchedEffect`.
- Delay efectivo: `3000L shl consecutiveFailures` → 3 s, 6 s, 12 s, 24 s, 48 s (capped en 48 s con `coerceAtMost(4)` sobre el exponente).
- Se distingue "intentó y falló" (incrementa) vs "no intentó porque no se movió" (resetea el contador, parked = estado saludable).
- Se resetea en el primer éxito, volviendo a 3 s inmediatamente.

**Notas:**
- `fetchCurrentStreet` retorna `null` tanto en falla de red como en "no hay calle acá" (ej. ruta sin OSM). Para simplicidad, ambos casos cuentan como falla y disparan backoff. No es ideal pero es benigno — en zonas sin calles cercanas, retrás los retries lejos de ser malo.
- Aplicable solo a este loop. Los otros loops activos (speed polling cada 500 ms, TTS loop cada 2 s, next maneuver cada 500 ms) no hacen red.

**Pendiente:** E.

**Refs:** commit `perf: exponential backoff on reverse geocode failures`.

---

## 2026-11-16 — Skip reverse geocode cuando la posición no cambió significativamente

**Qué:** El loop de reverse geocode (Nominatim) corría cada 3 s incondicionalmente, incluso con el auto parado (semáforos, tráfico lento, estacionado). Nominatim tiene una política de uso razonable (<1 req/s por usuario) y además es trabajo desperdiciado porque el nombre de la calle no cambia si no te moviste.

**Hecho:**
- Variables locales `lastGeoLat`, `lastGeoLon` dentro del `LaunchedEffect` (se descartan si la Composition se desmonta).
- Antes de llamar `fetchCurrentStreet`, se calcula `haversineMeters(lastGeoLat, lastGeoLon, currentLat, currentLon)` y solo se llama si > 30 m.
- Cuando el geocode responde con éxito (`street != null`), se actualizan `lastGeoLat`/`lastGeoLon` como "última posición geocodeada".
- Inicio: `Double.NaN` como marcador de "nunca geocodeado todavía" — primera iteración siempre dispara.

**Impacto esperado:** con el auto parado, ~0 requests a Nominatim en lugar de 20/minuto. Con auto en movimiento lento (<10 m/s), request cada ~3-6 segundos efectivos. Con auto rápido, cada 3 s como antes. Mejora especialmente la vida de la data móvil y el respeto a los términos de Nominatim.

**Pendiente:** D, E.

**Refs:** commit `perf: skip reverse geocode when user hasn't moved 30m+`.

---

## 2026-11-16 — Sliding window para búsqueda de closestShapeIdx

**Qué:** El loop de "próxima maniobra" (cada 500 ms) recorría TODA la geometría de la ruta para encontrar el punto más cercano al usuario. En rutas largas (SF→Rosario tiene cientos de puntos, SF→BA miles) esto es trabajo redundante porque el usuario normalmente avanza linealmente.

**Hecho:**
- Nuevo state `lastClosestShapeIdx: Int` (mutableIntStateOf) que persiste el índice encontrado la vez anterior.
- El loop ahora busca primero en una ventana `[max(0, last-5), min(size-1, last+50)]`: 5 puntos hacia atrás (por si el fix GPS retrocedió un poco) y 50 adelante (cubre varios cientos de metros de ruta en avance normal).
- Si la distancia mínima en la ventana es > 100 m, se asume que el usuario se desvió de la ruta o está en un reroute y cae a búsqueda completa como fallback.
- El state se resetea a 0 en los dos puntos donde la ruta cambia: al setear una ruta nueva (en el handler del search result) y al cancelar la ruta activa (en el botón X).

**Impacto esperado:** en navegación normal, la búsqueda baja de O(n) a O(55) por tick, independiente del largo de la ruta. En rutas largas de ~1000+ puntos es 20× más rápido. Comportamiento visible = idéntico.

**Pendiente:** C, D, E.

**Refs:** commit `perf: sliding window for closestShapeIdx lookup`.

---

## 2026-11-16 — Remover `ColorCalibrationScreen` muerto

**Qué:** Borrado del composable `ColorCalibrationScreen` que no se usa desde que decidimos el color final (`#E60000`). Ocupaba 45 líneas al principio del archivo y confundía al lector.

**Hecho:**
- Eliminada la función `@Composable fun ColorCalibrationScreen(modifier: Modifier)` completa (de línea 122 a ~166 pre-refactor).
- Si en el futuro hace falta volver a calibrar un color, se re-crea localmente — la lógica es trivial (una `Column` con 8 `Box` de 70dp cada uno).

**Pendiente:** B, C, D, E del plan de mejoras con impacto.

**Refs:** commit `chore: remove unused ColorCalibrationScreen`.

---

## 2026-11-16 — Refactor de eficiencia y bug fixes seguros (sin cambio funcional)

**Qué:** Barrido de 10 mejoras al código que NO cambian la funcionalidad visible pero arreglan bugs sutiles, eliminan allocations en loops hot, cierran conexiones HTTP correctamente y reducen duplicación.

**Hecho:**
- **TTS init fix:** la referencia dentro del callback del `TextToSpeech` constructor usaba `tts.value` (null en ese momento) → ahora captura un `var ttsInstance` local por closure (Kotlin captura por referencia), garantizando que `.language = es-AR` se setee cuando el init callback dispara.
- **Constantes de tipos de maniobra:** `listOf(1,2,3)` y `listOf(4,5,6)` creados en cada tick del loop de maniobra (cada 500ms) → `private val START_MANEUVER_TYPES`, `DESTINATION_MANEUVER_TYPES` top-level, zero allocations.
- **Logs envueltos en `BuildConfig.DEBUG`:** `Log.d("RetroCarDash", "Search URL: ...")`, `"Response code: ..."`, `"Response body: ..."` → solo se emiten en builds debug. En release no interpolan strings ni imprimen el body completo.
- **`connection.responseCode` leído dos veces** en `searchPlaces` → reemplazado por la variable `responseCode` ya guardada.
- **`maneuverIcon` con casos duplicados:** `11 → TurnRight` y `10, 18, 20, 23 → TurnRight` en líneas separadas → agrupado en `10, 11, 18, 20, 23 → TurnRight`. Idem para TurnLeft, Navigation (slight), Refresh (u-turn + roundabout).
- **Helper `computeMapPadding(displayMetrics, hasRoute, isChaseView): IntArray`** + constantes `BAR_HEIGHT_NO_ROUTE_DP`, `BAR_HEIGHT_WITH_ROUTE_DP`, `CHASE_TOP_PADDING_RATIO` → reemplaza lógica duplicada en 3 lugares (`LaunchedEffect` de padding, `addOnMapClickListener`, `applyChaseView`).
- **`HttpURLConnection.disconnect()` en `finally`** en las 3 funciones de red (`searchPlaces`, `fetchRoute`, `fetchCurrentStreet`) → evita conexiones colgantes en el pool interno.
- **`spokenManeuvers.clear()` al cancelar ruta:** antes el set de IDs ya hablados nunca se limpiaba, por lo que una ruta nueva con la misma instrucción textual (ej. "Girar a la derecha") no era anunciada. Ahora se limpia junto con el reset del resto del estado de ruta.
- **`tts.value?.stop()` al desactivar `ttsEnabled`:** el botón de mute dejaba de disparar nuevos `speak()` pero el que estaba en curso seguía sonando hasta el final. Ahora se detiene inmediatamente.
- **`spokenManeuvers` thread-safe:** era un `mutableSetOf<String>()` ordinario que el LaunchedEffect podía `.add()` mientras el handler del botón cancelar lo `.clear()` desde el UI thread → `Collections.synchronizedSet(mutableSetOf())`.

**Pendiente:**
- Parte 3: 5 mejoras con impacto en comportamiento (borrar `ColorCalibrationScreen`, optimizar closest-shape-idx con ventana, skip reverse geocode en reposo, backoff exponencial, centralizar `TextStyle`).

**Refs:** commit `refactor: 10 safe efficiency and bug fixes (no functional changes)`.

---

## 2026-11-16 — Setup de documentación y workflow

**Qué:** Estandarización de documentación del repo siguiendo el patrón del proyecto `robert`: `AGENTS.md` en raíz + `docs/PROJECT_CONTEXT.md` + `docs/MASTER_PLAN.md` + `docs/IMPLEMENTATION_LOG.md`.

**Hecho:**
- `AGENTS.md`: reglas de arranque, invariantes (color único, sin Google Services, landscape fijo, teclado custom), reglas de seguridad (secrets en `local.properties`, logs con `BuildConfig.DEBUG`, `HttpURLConnection.disconnect()`), convenciones, scripts.
- `docs/PROJECT_CONTEXT.md`: stack completo con versiones, estructura del repo, organización interna de `MainActivity.kt` (16 bloques en orden), convenciones, dependencias críticas, variables de entorno, target físico (Tab A11, panel cálido), permisos, flujos clave (búsqueda, voz, próxima maniobra, Chase vs God).
- `docs/MASTER_PLAN.md`: visión por capas (navegación → OBD → servicios), estado actual v0.2 con checklist, roadmap v0.1 → v1.0, decisiones de arquitectura, dependencias externas.
- `docs/IMPLEMENTATION_LOG.md`: este archivo.
- `README.md`: actualizado a v0.2 con features reales (voz, voice input, Valhalla, Photon, layout unificado) — antes era v0.1 desactualizado.
- Workflow permanente: cada task nueva suma una entrada arriba de este archivo + commit + push a `main`.

**Pendiente:**
- Refactor de eficiencia y bug fixes (10 items identificados, próximo commit).
- Mejoras con impacto (A-E: borrar `ColorCalibrationScreen`, optimizar closest-shape-idx, skip reverse geocode en reposo, backoff en loops, centralizar `TextStyle`).

**Refs:** commit `docs: add AGENTS, docs/ structure and update README to v0.2`.

---

## 2026-11-11 — UI unificada, voz turn-by-turn, dictado y navegación mejorada

**Qué:** Rediseño completo del layout inferior, integración de voz (turn-by-turn + dictado), nombres de calles como carteles flotantes, Chase View automática al arrancar y al setear ruta. Centrado del mapa 80-20% relativo al área visible real.

**Hecho:**
- **Layout unificado:** panel inferior único de 50/50 (izquierda/derecha), altura condicional 48dp (sin ruta) / 96dp (con ruta). Sin ruta: "Destino 🔍" clickeable + "Por {calle}" + 🔊. Con ruta: icono grande de maniobra (75% alto) + "A {d}m" (25% alto) + instrucción hasta 2 líneas; a la derecha: hora de llegada + tiempo + distancia centrado, calle actual centrada, X y 🔊 alineados verticalmente contra la derecha.
- **Áreas táctiles:** botones (X, 🔊) son `Box` fillMaxHeight × 64dp con `.clickable`, no `IconButton` apretados.
- **Voz turn-by-turn TTS** (es-AR): avisa a 200-300m ("En X metros...") y a <50m ("doblar..."), con anti-repetición en set de ids.
- **Dictado por voz** con `RecognizerIntent` (es-AR): botón 🎙️ en fila inferior del teclado reemplaza a "BUSCAR". Texto reconocido va a `searchQuery` y Photon dispara con el mismo debounce.
- **Teclado custom reorganizado:** fila final `[🎙️] [ESPACIO wide] [⌫]`.
- **Lógica correcta de próxima maniobra:** usa `begin_shape_index` sobre el `closestShapeIdx` del usuario en la ruta (ya no la maniobra más cercana en línea recta, que mostraba la de inicio). Saltea tipos Start (1/2/3). Fallback a destino (4/5/6) al final de ruta.
- **Nombres de calles como carteles flotantes:** `SYMBOL_PLACEMENT_LINE_CENTER` (un label por calle), `TEXT_PITCH_ALIGNMENT_VIEWPORT` + `TEXT_ROTATION_ALIGNMENT_VIEWPORT` (siempre horizontales en pantalla), `textHaloWidth = 4.0f` + `textHaloBlur = 1.0f` (se despegan visualmente de las líneas).
- **Chase View forzada:** `applyChaseView()` función compartida. Llamada en: primer GPS fix al arrancar (`initialChaseApplied`), al setear ruta nueva (después de `fetchRoute` exitoso), al tap manual del mapa.
- **Centrado 80-20% sobre área visible:** `padding top = 0.6 × (screenHeight - barHeight)`, `padding bottom = barHeight`. God View: `padding top = 0` (dot centrado en área visible). Aplicado sincrónicamente en `map click listener` y en `applyChaseView` para evitar race con animateCamera.
- **Icono flechas:** vector `ImageVector` de Material (TurnLeft, TurnRight, TurnSharp, Straight, Flag, Refresh para rotonda/u-turn, SwapHoriz para merge), reemplazan los unicode arrows.
- **Color final:** `#E60000` (compensando el panel cálido de la Tab A11). Centralizado en `DashColors`.
- **Oculto nombre calle actual del mapa:** filtro con `Expression.not(Expression.eq(Expression.get("name"), currentStreetName))` para evitar duplicado con el widget inferior.
- **Tap en el mapa toggle Chase/God**: elimina el FAB de recenter.
- **Botón cancelar ruta más grande** (36dp touch, 24dp icon).

**Pendiente:**
- Nada bloqueante. La app está lista para probar en la calle.

**Refs:** commit `47a2382` ("feat: UI unificada, voz turn-by-turn, dictado y navegacion mejorada").

---

## 2026-11-10 — Teclado custom, Photon geocoder, color del tablero y UX del mapa

**Qué:** Primera ola de pulidos: reemplazo del geocoder Stadia Pelias (sin cobertura de Santa Fe) por Photon by Komoot, teclado custom que reemplaza el IME de Android, color del acento centralizado tras varias iteraciones, UX del mapa afinada.

**Hecho:**
- **Photon by Komoot** reemplaza Stadia Maps Pelias para geocoding. Fuzzy matching sobre OSM, cobertura completa de Argentina, sin API key. Encuentra "zuviria" → "José María Zuviría".
- `local.properties` + `BuildConfig` para inyectar `STADIA_MAPS_API_KEY` (gitignored, nunca committeada).
- **`DashColors` object** centraliza el acento. Cambiar color = 2 líneas.
- Iteración de color: `#FF4500` (muy naranja en la tablet) → `#FF3300` → `#FF1500` → `#FF0000` → `#E60000` (final, panel LCD de la Tab A11 es cálido).
- **`ColorCalibrationScreen`** composable para calibración visual futura (8 opciones lado a lado).
- **Nombre de calle actual oculto del mapa** (evita duplicado con widget "Por {calle}").
- **Botón cancelar ruta más visible** (36dp touch target).
- **Teclado custom Compose** (QWERTY + números + ñ + espacio + backspace + BUSCAR). Elimina la barra negra inferior de Android.
- **MapLibre 11.13.1** (16KB page alignment correcto para Android nuevos).

**Pendiente:**
- Voz turn-by-turn (próxima entrega).
- Dictado por voz (próxima entrega).
- Rediseño del layout inferior (próxima entrega).

**Refs:** commit `48bbe11` ("feat: teclado custom, Photon geocoder, color del tablero y UX del mapa").

---

## 2026-11-10 — v0.1 Prototipo inicial

**Qué:** Primera versión funcional de la app con mapa + GPS + Chase/God view + reverse geocode + display de velocidad.

**Hecho:**
- MapLibre 11.13.1 integrado con OpenFreeMap dark style.
- GPS tracking con dot blue, offset al 65% del top de la pantalla.
- Dos modos de cámara: Chase View (default, 3D, zoom 18, tilt 60°) y God View (2D, zoom 16, norte arriba).
- Toggle de modo con FAB arriba-izquierda.
- Display de velocidad en km/h abajo-izquierda.
- Nombre de la calle actual via Nominatim OSM reverse geocoding ("Por {calle}") abajo-centro.
- Fullscreen landscape immersive. `FLAG_KEEP_SCREEN_ON`.
- Sin Google Play Services.

**Pendiente:**
- Routing, buscar destino, voz, teclado custom, etc. (vienen en entregas siguientes).

**Refs:** commit inicial del repo.
