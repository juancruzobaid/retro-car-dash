# Implementation Log — Retro Car Dash

> Entradas más recientes al principio. Cada entrega real suma una entrada con `Qué / Hecho / Pendiente / Refs`.

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
