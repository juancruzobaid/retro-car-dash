# Implementation Log — Retro Car Dash

> Entradas más recientes al principio. Cada entrega real suma una entrada con `Qué / Hecho / Pendiente / Refs`.

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
