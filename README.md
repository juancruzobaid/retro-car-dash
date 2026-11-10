# 🚗 Retro Car Dash

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-29%20(Android%2010%2B)-blue.svg)](https://developer.android.com/about/dashboards)
[![MapLibre](https://img.shields.io/badge/MapLibre-11.13.1-orange.svg)](https://maplibre.org/)

**Retro Car Dash** is an open-source, offline-friendly Android car dashboard application built for modern restoration projects and daily driving enthusiasts alike. Initially developed for a **BMW E36 323ti Compact** restoration project, it is architected to run smoothly on Android tablets (such as the Samsung Galaxy Tab A11) in any vehicle.

Designed with a high-contrast dark theme, zero Google Play Services dependencies, and real-time GPS navigation views, Retro Car Dash bridges the gap between classic car aesthetics and modern tech.

---

## ✨ Features (v0.1)

- 🗺️ **Custom MapLibre Native Integration**: Powered by OpenFreeMap's dark style with custom road rendering (wider highway/street lines, high-contrast orange street names with black halos).
- 📍 **Real-time GPS Tracking**: Precise user location with blue dot positioning offset to 65% from the top of the screen (35% from bottom) to maximize forward visibility while driving.
- 🔄 **Dual Camera Modes** (Toggled via top-left button):
  - **God View**: Top-down 2D view, zoom 16, north-up.
  - **Chase View (Default)**: 3D third-person driving view, zoom 18, 60° tilt, rotating smoothly with the user's heading.
- 🏎️ **Real-Time Speedometer**: Large high-contrast speed display in `km/h` at the bottom-left corner.
- 🛣️ **Live Street Name Bar**: Reverse geocoding via Nominatim OpenStreetMap API displaying the current street name (`"Por [Street Name]"`) at the bottom-center.
- 🖥️ **Car-Optimized UI**:
  - Immersive fullscreen landscape mode.
  - Screen stays always awake (`FLAG_KEEP_SCREEN_ON`).
  - No Google Services dependency (works seamlessly on any custom or de-Googled Android tablet).

---

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose & Material 3
- **Mapping**: MapLibre Native Android SDK v11.13.1
- **Geocoding**: Nominatim OpenStreetMap API
- **Concurrency**: Kotlin Coroutines & Flow
- **SDK Targets**: Minimum SDK 29 (Android 10), Compile SDK 37

---

## 💻 Hardware Requirements

- **Head Unit / Tablet**: Android tablet (tested on Samsung Galaxy Tab A11 / SM-X133) with built-in GPS and USB-C power.
- **Orientation**: Landscape mode.
- **Connectivity**: Internet access for initial map tiles and reverse geocoding (offline tile caching planned).

---

## 🚀 Installation & Getting Started

1. **Clone the repository**:
   ```bash
   git clone https://github.com/juancruzobaid/retro-car-dash.git
   ```
2. **Open in Android Studio**:
   - Open Android Studio (Rabbit 2 / Iguana or newer).
   - Select **Open** and choose the `RetroCarDash` folder.
3. **Sync & Run**:
   - Let Gradle sync dependencies.
   - Connect your Android tablet via USB (with USB Debugging enabled).
   - Click **Run** (`Shift + F10`) to deploy to your device.

---

## 🗺️ Roadmap / Planned Features

- [ ] Offline vector map tile caching (`.mbtiles` / PMTiles).
- [ ] Turn-by-turn routing engine integration (GraphHopper).
- [ ] OBD-II integration via OBDLink CX (Bluetooth LE) for live car telemetry (RPM, coolant temp, fuel rate).
- [ ] BMW-specific K-Line diagnostic data (oil temp, VANOS status, lambda feedback).
- [ ] Local SQLite database for trip logging and performance metrics.
- [ ] InfluxDB + Grafana server synchronization over local Wi-Fi.
- [ ] iDrive-style physical rotary controller support via USB HID.
- [ ] Blaupunkt Frankfurt RCM 82 stereo control integration.
- [ ] Rearview backup camera video stream support.

---

## 🤝 Contributing

Contributions, feature requests, and bug reports are welcome! Feel free to check the [issues page](https://github.com/juancruzobaid/retro-car-dash/issues) or submit a pull request.

---

## 📄 License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for more information.

---

## 🙏 Acknowledgments

- [MapLibre](https://maplibre.org/) for the incredible open-source mapping SDK.
- [OpenFreeMap](https://openfreemap.org/) for free open-source map styles and tiles.
- [Nominatim / OpenStreetMap](https://nominatim.openstreetmap.org/) for reverse geocoding services.

---

## 👤 Author

**Juan Cruz Obaid**
- GitHub: [@juancruzobaid](https://github.com/juancruzobaid)
- Repository: [retro-car-dash](https://github.com/juancruzobaid/retro-car-dash)
