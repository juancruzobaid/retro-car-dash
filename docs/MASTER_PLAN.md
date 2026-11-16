# Master Plan — Retro Car Dash

## Visión

Dashboard de navegación completo para auto, open source, pensado para una tablet dedicada en el torpedo. Tres capas:

1. **Navegación con mapa + voz** — hoy
2. **Datos del motor por OBD** — próxima ola (hardware pendiente)
3. **Servicios del auto** — favoritos, calendario, tráfico, radares, música, etc.

## Estado actual (v0.2)

Fase 1 **completa en escritorio/emulador**. Pendiente de validar en calle.

- ✅ Mapa MapLibre con OpenFreeMap dark + custom style
- ✅ GPS tracking con dot, Chase View 80-20%, God View centrado
- ✅ Toggle de vista tocando el mapa
- ✅ Barra inferior unificada con dos sub-paneles 50/50 (adaptable sin/con ruta)
- ✅ Buscador de destinos con Photon (fuzzy matching sobre OSM)
- ✅ Teclado custom retro con 🎙️ voice input
- ✅ Routing con Stadia Valhalla, ruta dibujada + ETA + tiempo + distancia
- ✅ Voz turn-by-turn en es-AR (TextToSpeech) con mute toggle
- ✅ Iconos de maniobra vectoriales (Material Icons)
- ✅ Próxima maniobra con metros que faltan y texto hasta 2 líneas
- ✅ Reverse geocoding de la calle actual (Nominatim)
- ✅ Chase View forzada al arrancar y al setear ruta nueva

## Roadmap

| Etapa | Alcance | Estado |
|-------|---------|--------|
| **v0.1** | Prototipo: mapa + GPS + reverse geocode + Chase/God toggle | ✅ hecha (commit 11-10) |
| **v0.2 UX pulida** | Teclado custom, voz TTS, voice input, layout unificado, color final E60000 | ✅ hecha (commit 11-11) |
| **v0.2.1 Docs + cleanup** | Documentación (AGENTS/docs), refactor de eficiencia, cleanup código muerto | 🚧 en curso |
| **v0.3 Favoritos** | Destinos guardados (casa, trabajo, abuela) con nombre custom | pendiente |
| **v0.4 Mapas offline** | Toggle online/offline + PMTiles Argentina generado y copiado a tablet | pendiente |
| **v0.5 Lane guidance** | Flechitas de carril en la barra de próxima maniobra (datos ya en Valhalla) | pendiente |
| **v0.6 OBD integration** | OBDLink CX BLE → datos del motor en pantalla (RPM, temp, voltaje) | pendiente hardware |
| **v0.7 Camera rear** | Soporte cámara trasera USB cuando engrana marcha atrás | pendiente hardware |
| **v0.8 Blaupunkt integration** | Control del estéreo Blaupunkt Frankfurt RCM 82 | pendiente hardware |
| **v1.0 Install day** | App 100% confiable en la calle + instalación final en auto | pendiente |

## Backlog menor

- Dirección contínua en ruta larga → probar con dirección de instrucción muy larga para validar 2 líneas de texto
- iDrive-style controller (ESP32 USB HID) — mucho futuro
- Calendario como fuente de destinos sugeridos — futuro
- Alertas de tráfico / radares (HERE Traffic API o TomTom) — futuro
- Sincronización con servidor casa (InfluxDB + Grafana + PostGIS) — futuro

## Decisiones de arquitectura

- **Tablet-based, no RPi**: el "cerebro" es una Samsung Galaxy Tab A11. Un split RPi+ESP32 anterior fue descartado.
- **Sin cuenta Google en la tablet**: F-Droid + APKs via ADB. Objetivo: si roban la tablet no comprometen credenciales.
- **Mapa online por default + offline manual**: no se intenta auto-fallback inteligente. Simple y predecible.
- **Geocoder siempre online**: Photon offline requiere Elasticsearch + 2-4GB RAM, inviable en la Tab A11. Se consulta via BT tethering del celular.
- **Archivo único mientras cabe**: `MainActivity.kt` se mantiene como monolito hasta ~2500 líneas. Split cuando aparezca una segunda pantalla real.
- **Material Icons**: iconos de maniobra son vectoriales de Material (TurnLeft, TurnRight, Straight, Flag, etc.). No unicode arrows.
- **Color centralizado**: `DashColors.ACCENT` único. Cambiar color = 2 líneas.
- **80-20% para Chase View**: el centro visual del mapa está al 80% desde arriba del área visible (no 65% ni 50%). Da más espacio para ver la calle adelante.
- **Áreas táctiles 64dp**: todos los botones principales tienen área clickeable ≥48dp alto × 64dp ancho, para uso en auto con dedos.

## Dependencias externas

| Servicio | Rol | Plan |
|----------|-----|------|
| Stadia Maps Valhalla | Routing | Free tier (API key en `local.properties`) |
| Photon by Komoot | Geocoding | Público, sin key |
| Nominatim OSM | Reverse geocoding | Público, con User-Agent |
| OpenFreeMap | Map tiles | Público, sin key |

**Migración futura:** servidor casa propio corriendo Photon + Valhalla + tiles, consumido via WiFi cuando la tablet esté en casa.
