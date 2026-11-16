# 🚗 Retro Car Dash

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-29%20(Android%2010%2B)-blue.svg)](https://developer.android.com/about/dashboards)
[![MapLibre](https://img.shields.io/badge/MapLibre-11.13.1-orange.svg)](https://maplibre.org/)

**Retro Car Dash** is an open-source Android car dashboard app with retro-futuristic styling (black background, monospace bitmap typography, deep-red accent), designed for a dedicated tablet mounted on the dashboard of a classic car. Developed for a **BMW E36 323ti Compact** restoration, portable to any vehicle.

Zero Google Play Services dependency. Works on a de-Googled Android tablet (F-Droid + ADB install).

---

## ✨ Features (v0.2)

### Map & navigation
- 🗺️ **MapLibre Native** with OpenFreeMap dark style, custom styling:
  - Street names as "floating signs" (viewport-aligned, line-center placement, thick black halo).
  - Wider roads at high zoom for readability while driving.
- 📍 **GPS tracking** with blue dot, two camera modes:
  - **Chase View (default):** 3D third-person, dot at 80% from top of visible map area (80-20% split for seeing more road ahead).
  - **God View:** 2D top-down, dot centered.
  - Toggle by tapping the map.
  - Chase View auto-applied on first GPS fix and when a new route is set.
- 🧭 **Destination search** with Photon by Komoot (fuzzy matching over OpenStreetMap — "zuviria" finds "José María Zuviría").
- 🛣️ **Turn-by-turn routing** via Stadia Maps Valhalla:
  - Route polyline drawn on map.
  - ETA + total time + distance displayed.
  - Next maneuver with vector icons (TurnLeft, TurnRight, Straight, Flag, etc.), distance to it ("A 250 m"), and instruction text up to 2 lines.
  - Correct "next maneuver" logic using Valhalla's `begin_shape_index` relative to user's closest point on the route (skips "start" maneuver types).

### Voice
- 🔊 **Text-to-Speech turn-by-turn** in Argentine Spanish (es-AR):
  - Announces "In X meters, {instruction}" at 200-300m.
  - Announces "{instruction}" alone at <50m.
  - Mute toggle button in bottom bar.
- 🎙️ **Voice input for destination search** via `RecognizerIntent` (es-AR).

### UI
- 🎨 **Retro aesthetic**: pure-black background, single accent color (`#E60000` tuned for warm-cast LCD panels like the Tab A11), monospace typography.
- 📱 **Immersive fullscreen landscape**, screen always-on while app is running.
- ⌨️ **Custom QWERTY keyboard** replaces the Android system IME (eliminates the system navigation bar, cleaner UX, voice input button integrated).
- 📏 **Generous touch targets** (64dp wide, full-height clickable areas) designed for use while driving.
- 🧱 **Unified bottom bar** with two 50/50 sub-panels:
  - **Without route (48dp):** "Destino 🔍" clickable + current street name + volume toggle.
  - **With route (96dp):** large maneuver icon + distance + instruction text (left) | ETA + distance + cancel X + volume (right).
- 🙈 **Current street name hidden from map** (avoids duplication with the "Por {street}" widget).

### Backend / data
- 🗺️ **OpenFreeMap** for map tiles (no API key).
- 📍 **Photon by Komoot** for forward geocoding (no API key).
- 🏠 **Nominatim OSM** for reverse geocoding (public, with User-Agent).
- 🛣️ **Stadia Maps Valhalla** for routing (free tier API key in `local.properties`).

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 + Material Icons Extended
- **Map:** MapLibre Native Android SDK 11.13.1
- **Build:** Gradle Kotlin DSL
- **SDK Targets:** Min SDK 29 (Android 10), Compile SDK 37

---

## 💻 Hardware

- **Head unit:** Android tablet with built-in GPS (tested on Samsung Galaxy Tab A11 WiFi / SM-X133).
- **Orientation:** Landscape only (`android:screenOrientation="landscape"`).
- **Internet:** Required for Photon, Nominatim, Valhalla, and OpenFreeMap tiles. Typically provided by the driver's phone hotspot (WiFi or Bluetooth tethering). Offline PMTiles of Argentina planned for v0.4.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio **Rabbit 2** (2026.2.2 Patch 1) or newer.
- Android SDK 37.
- A Stadia Maps API key (free tier at https://stadiamaps.com) for routing.

### Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/juancruzobaid/retro-car-dash.git
   ```
2. **Add your Stadia Maps API key** to `local.properties` (gitignored):
   ```properties
   STADIA_MAPS_API_KEY=your-api-key-here
   ```
3. **Open in Android Studio** and let Gradle sync.
4. **Connect your Android tablet** via USB (with USB Debugging enabled).
5. **Run** with `Shift + F10`.

---

## 📚 Documentation

- `AGENTS.md` — Rules, invariants, conventions for working on this repo.
- `docs/PROJECT_CONTEXT.md` — Architecture and stack in detail.
- `docs/MASTER_PLAN.md` — Roadmap and current state.
- `docs/IMPLEMENTATION_LOG.md` — Chronological log of changes.

---

## 🗺️ Roadmap

| Version | Scope | Status |
|---------|-------|--------|
| v0.1 | Prototype: map + GPS + Chase/God | ✅ |
| v0.2 | UX pulida: custom keyboard, voice, layout unificado | ✅ |
| v0.3 | Favoritos / saved destinations | ⏳ |
| v0.4 | Mapas offline PMTiles + toggle online/offline | ⏳ |
| v0.5 | Lane guidance visual | ⏳ |
| v0.6 | OBD-II via OBDLink CX (BLE) — engine telemetry | ⏳ hardware |
| v0.7 | Rear camera support | ⏳ hardware |
| v0.8 | Blaupunkt Frankfurt RCM 82 stereo integration | ⏳ hardware |

See `docs/MASTER_PLAN.md` for full details.

---

## 🤝 Contributing

Issues and PRs welcome at https://github.com/juancruzobaid/retro-car-dash/issues.

Before contributing, read `AGENTS.md` for invariants and conventions.

---

## 📄 License

MIT. See [LICENSE](LICENSE).

---

## 🙏 Acknowledgments

- [MapLibre](https://maplibre.org/) — open-source mapping SDK.
- [OpenFreeMap](https://openfreemap.org/) — free map styles and tiles.
- [Photon by Komoot](https://photon.komoot.io/) — OSM geocoder with fuzzy search.
- [Nominatim / OpenStreetMap](https://nominatim.openstreetmap.org/) — reverse geocoding.
- [Stadia Maps](https://stadiamaps.com/) — Valhalla routing API.

---

## 👤 Author

**Juan Cruz Obaid** · [@juancruzobaid](https://github.com/juancruzobaid)
