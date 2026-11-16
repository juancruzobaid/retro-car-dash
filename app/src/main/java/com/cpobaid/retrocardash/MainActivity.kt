package com.cpobaid.retrocardash

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cpobaid.retrocardash.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DashColors {
    // Primary accent: text and icons inside data "displays" and the drawn route on the map.
    val ACCENT = androidx.compose.ui.graphics.Color(0xFFE60000)
    const val ACCENT_HEX = "#E60000"

    // Dark reddish background used for "display" tiles (BMW-style instrument panel).
    val DISPLAY_BG = androidx.compose.ui.graphics.Color(0xFF4A0000)

    // Icon color for pure "control" buttons (cancel X, volume toggle) — kept visually
    // separate from the data displays so the eye distinguishes controls from readouts.
    val BUTTON_ICON = androidx.compose.ui.graphics.Color.White
}

/**
 * Centralized Compose TextStyle factories for the dashboard.
 * All text is monospace (retro bitmap aesthetic). Three families:
 *   - accent: DashColors.ACCENT, bold — primary UI text (speed, ETA, instructions...)
 *   - gray:   gray, normal         — status/subtle text (search status message)
 *   - white:  white, normal        — neutral text (search result rows, input placeholder)
 * Pass `.copy(...)` when you need to tweak a one-off property on top.
 */
object DashTextStyles {
    private val BASE_MONO = TextStyle(fontFamily = FontFamily.Monospace)

    fun accent(sizeSp: Int): TextStyle = BASE_MONO.copy(
        color = DashColors.ACCENT,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Bold
    )

    fun gray(sizeSp: Int): TextStyle = BASE_MONO.copy(
        color = androidx.compose.ui.graphics.Color.Gray,
        fontSize = sizeSp.sp
    )

    fun white(sizeSp: Int): TextStyle = BASE_MONO.copy(
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = sizeSp.sp
    )
}

// Valhalla maneuver type constants.
// See: https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/#maneuver-type
private val START_MANEUVER_TYPES = setOf(1, 2, 3)         // Start / StartRight / StartLeft
private val DESTINATION_MANEUVER_TYPES = setOf(4, 5, 6)   // Destination / DestinationRight / DestinationLeft

// Bottom bar heights and chase-view top padding ratio
private const val BAR_HEIGHT_NO_ROUTE_DP = 48
private const val BAR_HEIGHT_WITH_ROUTE_DP = 96
private const val CHASE_TOP_PADDING_RATIO = 0.6

/**
 * Compute MapLibre padding [left, top, right, bottom] so the user dot
 * is positioned correctly within the VISIBLE map area (above the bottom bar).
 * Chase View: dot at 80% from top of visible area (more road ahead).
 * God View: dot centered in visible area.
 */
private fun computeMapPadding(
    displayMetrics: android.util.DisplayMetrics,
    hasRoute: Boolean,
    isChaseView: Boolean
): IntArray {
    val barHeightDp = if (hasRoute) BAR_HEIGHT_WITH_ROUTE_DP else BAR_HEIGHT_NO_ROUTE_DP
    val barHeightPx = (barHeightDp * displayMetrics.density).toInt()
    val visibleHeightPx = displayMetrics.heightPixels - barHeightPx
    val topPaddingPx = if (isChaseView) (CHASE_TOP_PADDING_RATIO * visibleHeightPx).toInt() else 0
    return intArrayOf(0, topPaddingPx, 0, barHeightPx)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen always on while the app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize MapLibre
        MapLibre.getInstance(this)

        // Hide system status bar and navigation bar (fullscreen immersive mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            MapScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

data class SearchResult(val displayName: String, val lat: Double, val lon: Double)

data class SearchResponse(val results: List<SearchResult>, val error: String?)

data class ManeuverPoint(
    val instruction: String,
    val lat: Double,
    val lon: Double,
    val distanceToNextMeters: Double,
    val type: Int,
    val beginShapeIndex: Int
)

data class RouteResult(
    val geometry: List<LatLng>,
    val distanceKm: Double,
    val durationSeconds: Int,
    val instructions: List<String>,
    val maneuvers: List<ManeuverPoint>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isChaseView by remember { mutableStateOf(true) }
    var initialChaseApplied by remember { mutableStateOf(false) }
    var currentSpeedKmh by remember { mutableIntStateOf(0) }
    var currentStreetName by remember { mutableStateOf<String?>(null) }
    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var streetLabelLayers by remember { mutableStateOf<List<SymbolLayer>>(emptyList()) }

    // Routing & Search state
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var routeGeometry by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeDistance by remember { mutableStateOf(0.0) } // km
    var routeDuration by remember { mutableStateOf(0) } // minutes
    var routeInstructions by remember { mutableStateOf<List<String>>(emptyList()) }
    var routeManeuvers by remember { mutableStateOf<List<ManeuverPoint>>(emptyList()) }
    var nextManeuverIndex by remember { mutableIntStateOf(0) }
    var nextManeuverDistance by remember { mutableIntStateOf(0) }
    var ttsEnabled by remember { mutableStateOf(true) }
    // Thread-safe: UI thread may .clear() it while a LaunchedEffect adds to it
    val spokenManeuvers = remember { java.util.Collections.synchronizedSet(mutableSetOf<String>()) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    // Hint to the next-maneuver loop: start searching the route shape around this index.
    // Reset to 0 when a new route is set or cancelled.
    var lastClosestShapeIdx by remember { mutableIntStateOf(0) }

    var isSearching by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchStatus by remember { mutableStateOf("Esperando entrada...") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            results?.firstOrNull()?.let { text ->
                searchQuery = text
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Initialize TTS.
    // Note: the init callback may fire BEFORE the outer `tts.value = ttsInstance`
    // assignment completes, so we capture a local `var` reference via closure
    // (Kotlin closures capture vars by reference).
    DisposableEffect(Unit) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale.forLanguageTag("es-AR")
            }
        }
        tts.value = ttsInstance
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
        }
    }

    // Apply Chase View on first GPS fix at startup
    LaunchedEffect(mapLibreMapInstance) {
        while (!initialChaseApplied) {
            val map = mapLibreMapInstance
            if (map != null &&
                map.locationComponent.isLocationComponentActivated
            ) {
                val location = map.locationComponent.lastKnownLocation
                if (location != null) {
                    isChaseView = true
                    applyChaseView(map, context, routeGeometry.isNotEmpty())
                    initialChaseApplied = true
                }
            }
            delay(500L)
        }
    }

    // Debounced search effect using Photon by Komoot with proximity bias & status updates
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            searchStatus = "Preparando búsqueda..."
            delay(400)
            searchStatus = "Buscando: $searchQuery"
            isSearching = true
            val map = mapLibreMapInstance
            val loc = if (map != null && map.locationComponent.isLocationComponentActivated) {
                map.locationComponent.lastKnownLocation
            } else {
                null
            }
            val searchResponse = searchPlaces(
                query = searchQuery,
                focusLat = loc?.latitude,
                focusLon = loc?.longitude,
                apiKey = BuildConfig.STADIA_MAPS_API_KEY
            )
            searchResults = searchResponse.results
            searchStatus = when {
                searchResponse.error != null && searchResponse.results.isEmpty() -> {
                    if (searchResponse.error == "No features in response") "Sin resultados"
                    else "Error: ${searchResponse.error}"
                }
                searchResults.isEmpty() -> "Sin resultados"
                else -> "Encontrados ${searchResults.size} resultados"
            }
            isSearching = false
        } else {
            searchResults = emptyList()
            searchStatus = "Esperando entrada..."
        }
    }

    // Hide street label matching currentStreetName from map layer
    LaunchedEffect(currentStreetName, streetLabelLayers) {
        val currentName = currentStreetName
        streetLabelLayers.forEach { layer ->
            try {
                if (!currentName.isNullOrBlank()) {
                    layer.setFilter(
                        Expression.not(
                            Expression.eq(
                                Expression.get("name"),
                                Expression.literal(currentName)
                            )
                        )
                    )
                } else {
                    layer.setFilter(
                        Expression.literal(true)
                    )
                }
            } catch (e: Exception) {
                Log.e("RetroCarDash", "Error updating street filter", e)
            }
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    // Poll speed in real time every 500ms from lastKnownLocation
    LaunchedEffect(mapLibreMapInstance) {
        val map = mapLibreMapInstance
        if (map != null) {
            while (true) {
                try {
                    if (map.locationComponent.isLocationComponentActivated) {
                        val location = map.locationComponent.lastKnownLocation
                        if (location != null) {
                            val speedMs = location.speed
                            currentSpeedKmh = (speedMs * 3.6f).toInt()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore if location component is not yet ready
                }
                delay(500L)
            }
        }
    }

    // Voice turn-by-turn announcement loop
    LaunchedEffect(mapLibreMapInstance) {
        while (true) {
            try {
                val map = mapLibreMapInstance
                if (map != null &&
                    map.locationComponent.isLocationComponentActivated &&
                    ttsEnabled &&
                    routeManeuvers.isNotEmpty()
                ) {
                    val location = map.locationComponent.lastKnownLocation
                    if (location != null) {
                        routeManeuvers.forEach { maneuver ->
                            val distanceMeters = haversineMeters(
                                location.latitude, location.longitude,
                                maneuver.lat, maneuver.lon
                            )
                            val id250 = "${maneuver.instruction}_250"
                            val id50 = "${maneuver.instruction}_50"

                            if (distanceMeters in 200.0..300.0 && !spokenManeuvers.contains(id250)) {
                                val roundedDist = (distanceMeters / 50.0).toInt() * 50
                                tts.value?.speak(
                                    "En $roundedDist metros, ${maneuver.instruction}",
                                    TextToSpeech.QUEUE_FLUSH, null, id250
                                )
                                spokenManeuvers.add(id250)
                            }
                            if (distanceMeters < 50.0 && !spokenManeuvers.contains(id50)) {
                                tts.value?.speak(
                                    maneuver.instruction,
                                    TextToSpeech.QUEUE_FLUSH, null, id50
                                )
                                spokenManeuvers.add(id50)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RetroCarDash", "TTS loop error", e)
            }
            delay(2000L)
        }
    }

    // LaunchedEffect for distance to next maneuver
    LaunchedEffect(mapLibreMapInstance) {
        while (true) {
            try {
                val map = mapLibreMapInstance
                if (map != null &&
                    map.locationComponent.isLocationComponentActivated &&
                    routeManeuvers.isNotEmpty() &&
                    routeGeometry.isNotEmpty()
                ) {
                    val location = map.locationComponent.lastKnownLocation
                    if (location != null) {
                        // Sliding window: start around last known closest idx to avoid
                        // scanning the whole route every 500 ms. If the window's best
                        // distance is suspiciously large (user off-route or re-route),
                        // fall back to a full scan.
                        val geomSize = routeGeometry.size
                        val windowStart = (lastClosestShapeIdx - 5).coerceAtLeast(0)
                        val windowEnd = (lastClosestShapeIdx + 50).coerceAtMost(geomSize - 1)

                        var closestShapeIdx = windowStart
                        var closestDist = Double.MAX_VALUE
                        for (idx in windowStart..windowEnd) {
                            val point = routeGeometry[idx]
                            val d = haversineMeters(
                                location.latitude, location.longitude,
                                point.latitude, point.longitude
                            )
                            if (d < closestDist) {
                                closestDist = d
                                closestShapeIdx = idx
                            }
                        }

                        // Fallback to full scan if the user is far from the window
                        // (e.g. just set a new route, or wandered off the shape).
                        if (closestDist > 100.0) {
                            closestShapeIdx = 0
                            closestDist = Double.MAX_VALUE
                            routeGeometry.forEachIndexed { idx, point ->
                                val d = haversineMeters(
                                    location.latitude, location.longitude,
                                    point.latitude, point.longitude
                                )
                                if (d < closestDist) {
                                    closestDist = d
                                    closestShapeIdx = idx
                                }
                            }
                        }
                        lastClosestShapeIdx = closestShapeIdx

                        val upcomingIdx = routeManeuvers.indexOfFirst { m ->
                            m.beginShapeIndex > closestShapeIdx &&
                            m.type !in START_MANEUVER_TYPES
                        }

                        if (upcomingIdx >= 0) {
                            nextManeuverIndex = upcomingIdx
                            val upcoming = routeManeuvers[upcomingIdx]
                            nextManeuverDistance = haversineMeters(
                                location.latitude, location.longitude,
                                upcoming.lat, upcoming.lon
                            ).toInt()
                        } else {
                            val destIdx = routeManeuvers.indexOfLast {
                                it.type in DESTINATION_MANEUVER_TYPES
                            }
                            if (destIdx >= 0) {
                                nextManeuverIndex = destIdx
                                nextManeuverDistance = haversineMeters(
                                    location.latitude, location.longitude,
                                    routeManeuvers[destIdx].lat,
                                    routeManeuvers[destIdx].lon
                                ).toInt()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RetroCarDash", "Next maneuver loop error", e)
            }
            delay(500L)
        }
    }

    // Unified LaunchedEffect for map padding relative to visible area
    LaunchedEffect(isChaseView, routeGeometry.isNotEmpty(), mapLibreMapInstance) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        val p = computeMapPadding(
            context.resources.displayMetrics,
            hasRoute = routeGeometry.isNotEmpty(),
            isChaseView = isChaseView
        )
        map.setPadding(p[0], p[1], p[2], p[3])
    }

    // Reverse geocode current street every 3 seconds.
    // - Skip the network call if the user hasn't moved more than 30m since the
    //   last successful geocode (parked, slow traffic, etc.) — the street name
    //   can't have changed, so we save Nominatim requests.
    // - Exponential backoff on repeated network failures to avoid hammering
    //   Nominatim when the connection is bad: 3s, 6s, 12s, 24s, 48s (capped).
    //   A successful response or a successful skip (no movement) resets the
    //   backoff.
    LaunchedEffect(mapLibreMapInstance) {
        val map = mapLibreMapInstance
        if (map != null) {
            var lastGeoLat = Double.NaN
            var lastGeoLon = Double.NaN
            var consecutiveFailures = 0
            while (true) {
                var didAttemptRequest = false
                var requestSucceeded = false
                try {
                    if (map.locationComponent.isLocationComponentActivated) {
                        val location = map.locationComponent.lastKnownLocation
                        if (location != null) {
                            val movedEnough = lastGeoLat.isNaN() || haversineMeters(
                                lastGeoLat, lastGeoLon,
                                location.latitude, location.longitude
                            ) > 30.0
                            if (movedEnough) {
                                didAttemptRequest = true
                                val street = fetchCurrentStreet(location.latitude, location.longitude)
                                if (street != null) {
                                    currentStreetName = street
                                    lastGeoLat = location.latitude
                                    lastGeoLon = location.longitude
                                    requestSucceeded = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Silently ignore network failures
                }
                // Only backoff when we actually tried and failed; skipping
                // (parked) is a healthy state and resets the failure counter.
                consecutiveFailures = if (didAttemptRequest && !requestSucceeded) {
                    (consecutiveFailures + 1).coerceAtMost(4)
                } else {
                    0
                }
                // 3s * 2^failures: 3, 6, 12, 24, 48 seconds
                val backoffDelay = 3000L shl consecutiveFailures
                delay(backoffDelay)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            mapLibreMapInstance?.let { mapLibreMap ->
                mapLibreMap.style?.let { style ->
                    if (!mapLibreMap.locationComponent.isLocationComponentActivated) {
                        enableLocation(context, mapLibreMap, style, isChaseView)
                    } else {
                        mapLibreMap.locationComponent.isLocationComponentEnabled = true
                        mapLibreMap.locationComponent.cameraMode = if (isChaseView) CameraMode.TRACKING_GPS else CameraMode.TRACKING
                        mapLibreMap.locationComponent.renderMode = RenderMode.COMPASS
                        val zoom = if (isChaseView) 18.0 else 16.0
                        mapLibreMap.moveCamera(CameraUpdateFactory.zoomTo(zoom))
                    }
                }
            }
        }
    }

    // Draw route on map when routeGeometry updates
    LaunchedEffect(routeGeometry) {
        val map = mapLibreMapInstance
        map?.style?.let { style ->
            val sourceId = "route-source"
            val layerId = "route-layer"

            val points = routeGeometry.map { Point.fromLngLat(it.longitude, it.latitude) }
            val lineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)

            var source = style.getSourceAs<GeoJsonSource>(sourceId)
            if (source == null) {
                source = GeoJsonSource(sourceId, feature)
                style.addSource(source)
                val lineLayer = LineLayer(layerId, sourceId).withProperties(
                    PropertyFactory.lineColor(Color.parseColor(DashColors.ACCENT_HEX)),
                    PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
                style.addLayer(lineLayer)
            } else {
                source.setGeoJson(feature)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.getMapAsync { mapLibreMap ->
                    mapLibreMapInstance = mapLibreMap
                    mapLibreMap.uiSettings.isLogoEnabled = false
                    mapLibreMap.uiSettings.isAttributionEnabled = false
                    mapLibreMap.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        // Initial camera position (Santa Fe, zoom 13)
                        mapLibreMap.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(-31.6333, -60.7000))
                            .zoom(13.0)
                            .build()

                        val matchedSymbolLayers = mutableListOf<SymbolLayer>()
                        // Customize map style layers for in-car navigation as floating signs over roads
                        for (layer in style.layers) {
                            val id = layer.id.lowercase()
                            if (layer is LineLayer) {
                                val roadKeywords = listOf("road", "street", "highway", "motorway", "trunk", "primary", "secondary", "tertiary", "residential", "service")
                                if (roadKeywords.any { id.contains(it) }) {
                                    val widthExpr = Expression.interpolate(
                                        Expression.linear(),
                                        Expression.zoom(),
                                        Expression.stop(5f, 1f),
                                        Expression.stop(10f, 4f),
                                        Expression.stop(14f, 8f),
                                        Expression.stop(18f, 16f)
                                    )
                                    layer.setProperties(PropertyFactory.lineWidth(widthExpr))
                                }
                            } else if (layer is SymbolLayer) {
                                val labelKeywords = listOf("road", "street", "highway", "label")
                                if (labelKeywords.any { id.contains(it) } && !id.contains("place") && !id.contains("settlement") && !id.contains("country")) {
                                    matchedSymbolLayers.add(layer)
                                    layer.setProperties(
                                        // Street names in WHITE so they read as "map data", not as part of
                                        // the red-accent UI of the dashboard overlay. Thick halo keeps them
                                        // readable against the dark base map.
                                        PropertyFactory.textColor(Color.parseColor("#FFFFFF")),
                                        PropertyFactory.textHaloColor(Color.parseColor("#000000")),
                                        PropertyFactory.textHaloWidth(4.0f),
                                        PropertyFactory.textHaloBlur(1.0f),
                                        PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE_CENTER),
                                        PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
                                        PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                                        PropertyFactory.textSize(
                                            Expression.interpolate(
                                                Expression.linear(),
                                                Expression.zoom(),
                                                Expression.stop(10f, 12f),
                                                Expression.stop(14f, 18f),
                                                Expression.stop(18f, 24f)
                                            )
                                        )
                                    )
                                }
                            }
                        }
                        streetLabelLayers = matchedSymbolLayers

                        // Add move listener to switch CameraMode to NONE when user pans/drags manually
                        mapLibreMap.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                if (mapLibreMap.locationComponent.isLocationComponentActivated &&
                                    mapLibreMap.locationComponent.cameraMode != CameraMode.NONE
                                ) {
                                    mapLibreMap.locationComponent.cameraMode = CameraMode.NONE
                                }
                            }

                            override fun onMove(detector: MoveGestureDetector) {}
                            override fun onMoveEnd(detector: MoveGestureDetector) {}
                        })

                        // Add map click listener to toggle camera mode (Chase vs God View)
                        mapLibreMap.addOnMapClickListener { _ ->
                            if (mapLibreMap.locationComponent.isLocationComponentActivated) {
                                isChaseView = !isChaseView
                                mapLibreMap.locationComponent.cameraMode =
                                    if (isChaseView) CameraMode.TRACKING_GPS else CameraMode.TRACKING

                                // Apply padding synchronously so animateCamera uses the new value
                                val p = computeMapPadding(
                                    context.resources.displayMetrics,
                                    hasRoute = routeGeometry.isNotEmpty(),
                                    isChaseView = isChaseView
                                )
                                mapLibreMap.setPadding(p[0], p[1], p[2], p[3])

                                val location = mapLibreMap.locationComponent.lastKnownLocation
                                val target = if (location != null)
                                    LatLng(location.latitude, location.longitude)
                                    else mapLibreMap.cameraPosition.target
                                val bearing = if (isChaseView && location != null && location.hasBearing())
                                    location.bearing.toDouble() else 0.0
                                val tilt = if (isChaseView) 60.0 else 0.0
                                val zoom = if (isChaseView) 18.0 else 16.0

                                val cameraPosition = CameraPosition.Builder()
                                    .target(target).zoom(zoom).tilt(tilt).bearing(bearing).build()
                                mapLibreMap.animateCamera(
                                    CameraUpdateFactory.newCameraPosition(cameraPosition), 1000
                                )
                            }
                            true
                        }

                        if (hasLocationPermission) {
                            enableLocation(context, mapLibreMap, style, isChaseView)
                        }
                    }
                }
            }
        )

        // Unified Bottom Panel (conditional structure based on active route)
        if (routeGeometry.isNotEmpty() && nextManeuverIndex < routeManeuvers.size) {
            // ====================================================
            // ACTIVE ROUTE: 96dp panel, 50/50 split, no dividers
            // ====================================================
            val maneuver = routeManeuvers[nextManeuverIndex]
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(androidx.compose.ui.graphics.Color.Black),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT 50%: maneuver icon + distance display + instruction display
                Row(
                    modifier = Modifier.weight(0.5f).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Maneuver arrow: data readout, so accent color (no button box)
                        Icon(
                            imageVector = maneuverIcon(maneuver.type),
                            contentDescription = null,
                            tint = DashColors.ACCENT,
                            modifier = Modifier.weight(0.75f).fillMaxWidth()
                        )
                        DisplayBox(
                            modifier = Modifier.weight(0.25f).wrapContentHeight(),
                            verticalPadding = 2
                        ) {
                            Text(
                                text = "A ${nextManeuverDistance}m",
                                style = DashTextStyles.accent(13)
                            )
                        }
                    }
                    DisplayBox(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = maneuver.instruction.trimEnd('.', ' '),
                            style = DashTextStyles.accent(20).copy(lineHeight = 26.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // RIGHT 50%: top line (route info display + cancel button),
                //           bottom line (street display + volume button)
                Column(
                    modifier = Modifier.weight(0.5f).fillMaxHeight()
                ) {
                    // TOP LINE
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val arrivalTimeMillis = System.currentTimeMillis() +
                            routeDuration * 60 * 1000L
                        val arrivalTimeStr = SimpleDateFormat(
                            "HH:mm", Locale.getDefault()
                        ).format(Date(arrivalTimeMillis))
                        DisplayBox(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Text(
                                text = "$arrivalTimeStr · $routeDuration min · ${String.format(Locale.getDefault(), "%.1f", routeDistance)} km",
                                style = DashTextStyles.accent(16),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(64.dp)
                                .clickable {
                                    routeGeometry = emptyList()
                                    destination = null
                                    routeInstructions = emptyList()
                                    routeManeuvers = emptyList()
                                    nextManeuverIndex = 0
                                    nextManeuverDistance = 0
                                    // Clear so a re-navigated route with the same instruction text speaks again
                                    spokenManeuvers.clear()
                                    // Reset the sliding-window hint for the next maneuver loop
                                    lastClosestShapeIdx = 0
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel Route",
                                tint = DashColors.BUTTON_ICON,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    // BOTTOM LINE
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DisplayBox(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Text(
                                text = if (currentStreetName.isNullOrBlank()) ""
                                       else "Por $currentStreetName",
                                style = DashTextStyles.accent(18),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(64.dp)
                                .clickable {
                                    ttsEnabled = !ttsEnabled
                                    // Stop any speech in progress when muting
                                    if (!ttsEnabled) tts.value?.stop()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp
                                             else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = if (ttsEnabled) "Silenciar"
                                                    else "Activar voz",
                                tint = DashColors.BUTTON_ICON,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ====================================================
            // NO ROUTE: single 48dp line, 50/50 split, no dividers
            // ====================================================
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(androidx.compose.ui.graphics.Color.Black),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT 50%: "Destino 🔍" — hybrid display-styled clickable box
                Row(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DisplayBox(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showSearchSheet = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Destino",
                                style = DashTextStyles.accent(20)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search Destination",
                                tint = DashColors.ACCENT,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                // RIGHT 50%: street display + volume button (white icon)
                Row(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DisplayBox(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text(
                            text = if (currentStreetName.isNullOrBlank()) ""
                                   else "Por $currentStreetName",
                            style = DashTextStyles.accent(18),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(64.dp)
                            .clickable {
                                ttsEnabled = !ttsEnabled
                                // Stop any speech in progress when muting
                                if (!ttsEnabled) tts.value?.stop()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp
                                         else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (ttsEnabled) "Silenciar"
                                                else "Activar voz",
                            tint = DashColors.BUTTON_ICON,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // Search Bottom Sheet with custom in-app keyboard
        if (showSearchSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSearchSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .padding(12.dp)
                ) {
                    // Input display (no system keyboard, just shows the query)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = DashColors.ACCENT,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "Buscar destino..." else searchQuery,
                            style = if (searchQuery.isBlank())
                                DashTextStyles.gray(18)
                            else
                                DashTextStyles.white(18)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Status text
                    Text(
                        text = searchStatus,
                        style = DashTextStyles.gray(12),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Results list (compact)
                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = DashColors.ACCENT)
                        }
                    } else if (searchResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .background(androidx.compose.ui.graphics.Color.Black)
                        ) {
                            items(searchResults) { result ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSearchSheet = false
                                            searchQuery = ""
                                            destination = LatLng(result.lat, result.lon)
                                            coroutineScope.launch {
                                                val map = mapLibreMapInstance
                                                if (map != null && map.locationComponent.isLocationComponentActivated) {
                                                    val loc = map.locationComponent.lastKnownLocation
                                                    if (loc != null) {
                                                        val route = fetchRoute(
                                                            loc.latitude, loc.longitude,
                                                            result.lat, result.lon,
                                                            BuildConfig.STADIA_MAPS_API_KEY
                                                        )
                                                        if (route != null) {
                                                            routeGeometry = route.geometry
                                                            routeDistance = route.distanceKm
                                                            routeDuration = route.durationSeconds / 60
                                                            routeInstructions = route.instructions
                                                            routeManeuvers = route.maneuvers
                                                            nextManeuverIndex = 0
                                                            nextManeuverDistance = 0
                                                            spokenManeuvers.clear()
                                                            // Reset the sliding-window hint for the new route
                                                            lastClosestShapeIdx = 0

                                                            isChaseView = true
                                                            mapLibreMapInstance?.let { m ->
                                                                applyChaseView(m, context, hasRoute = true)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = result.displayName,
                                        style = DashTextStyles.white(14)
                                    )
                                    HorizontalDivider(
                                        color = androidx.compose.ui.graphics.Color.DarkGray,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom keyboard with speech recognition input
                    CustomKeyboard(
                        onKeyPress = { key -> searchQuery += key },
                        onBackspace = {
                            if (searchQuery.isNotEmpty())
                                searchQuery = searchQuery.dropLast(1)
                        },
                        onSpace = { searchQuery += " " },
                        onMic = {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                                    putExtra(
                                        RecognizerIntent.EXTRA_PROMPT,
                                        "Decí la dirección"
                                    )
                                }
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                Log.e("RetroCarDash", "Speech recognition not available", e)
                            }
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun applyChaseView(
    mapLibreMap: MapLibreMap,
    context: Context,
    hasRoute: Boolean
) {
    if (!mapLibreMap.locationComponent.isLocationComponentActivated) return

    mapLibreMap.locationComponent.cameraMode = CameraMode.TRACKING_GPS

    val p = computeMapPadding(
        context.resources.displayMetrics,
        hasRoute = hasRoute,
        isChaseView = true
    )
    mapLibreMap.setPadding(p[0], p[1], p[2], p[3])

    val location = mapLibreMap.locationComponent.lastKnownLocation
    val target = if (location != null)
        LatLng(location.latitude, location.longitude)
        else mapLibreMap.cameraPosition.target
    val bearing = if (location != null && location.hasBearing())
        location.bearing.toDouble() else 0.0

    val cameraPosition = CameraPosition.Builder()
        .target(target)
        .zoom(18.0)
        .tilt(60.0)
        .bearing(bearing)
        .build()
    mapLibreMap.animateCamera(
        CameraUpdateFactory.newCameraPosition(cameraPosition), 1000
    )
}

private suspend fun searchPlaces(
    query: String,
    focusLat: Double?,
    focusLon: Double?,
    apiKey: String
): SearchResponse {
    return withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        var connection: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("https://photon.komoot.io/api/?q=$encoded&limit=8")
            if (focusLat != null && focusLon != null) {
                urlBuilder.append("&lat=$focusLat&lon=$focusLon")
            }
            val urlString = urlBuilder.toString()
            if (BuildConfig.DEBUG) Log.d("RetroCarDash", "Search URL: $urlString")

            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (BuildConfig.DEBUG) Log.d("RetroCarDash", "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                if (BuildConfig.DEBUG) Log.d("RetroCarDash", "Response body: $response")

                val json = JSONObject(response)
                if (json.has("features")) {
                    val features = json.getJSONArray("features")
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val properties = feature.optJSONObject("properties")
                        val geometry = feature.optJSONObject("geometry")
                        if (properties != null && geometry != null) {
                            val countryCode = properties.optString("countrycode", "")
                            val country = properties.optString("country", "")
                            if (countryCode.equals("AR", ignoreCase = true) || country.equals("Argentina", ignoreCase = true)) {
                                val name = properties.optString("name", "")
                                val street = properties.optString("street", "")
                                val housenumber = properties.optString("housenumber", "")
                                val city = properties.optString("city", "")
                                val state = properties.optString("state", "")

                                val mainPart = if (street.isNotBlank() && housenumber.isNotBlank()) "$street $housenumber"
                                               else if (name.isNotBlank()) name
                                               else if (street.isNotBlank()) street
                                               else ""

                                val locationPart = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
                                val displayName = if (mainPart.isNotBlank() && locationPart.isNotBlank()) "$mainPart, $locationPart"
                                                  else if (mainPart.isNotBlank()) mainPart
                                                  else locationPart

                                if (geometry.has("coordinates")) {
                                    val coords = geometry.getJSONArray("coordinates")
                                    if (coords.length() >= 2) {
                                        val lon = coords.getDouble(0)
                                        val lat = coords.getDouble(1)
                                        if (displayName.isNotBlank()) {
                                            results.add(SearchResult(displayName, lat, lon))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (results.isEmpty()) {
                        return@withContext SearchResponse(emptyList(), "No features in response")
                    }
                    return@withContext SearchResponse(results, null)
                } else {
                    return@withContext SearchResponse(emptyList(), "No features in response")
                }
            } else {
                return@withContext SearchResponse(emptyList(), "HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.e("RetroCarDash", "Search error", e)
            return@withContext SearchResponse(emptyList(), e.localizedMessage ?: "Unknown error")
        } finally {
            connection?.disconnect()
        }
    }
}

private suspend fun fetchRoute(
    startLat: Double,
    startLon: Double,
    destLat: Double,
    destLon: Double,
    apiKey: String
): RouteResult? {
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlString = "https://api.stadiamaps.com/route/v1?api_key=$apiKey"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val requestBody = JSONObject().apply {
                put("locations", JSONArray().apply {
                    put(JSONObject().put("lat", startLat).put("lon", startLon))
                    put(JSONObject().put("lat", destLat).put("lon", destLon))
                })
                put("costing", "auto")
                put("directions_options", JSONObject().apply {
                    put("units", "kilometers")
                    put("language", "es-ES")
                })
            }

            connection.outputStream.write(requestBody.toString().toByteArray(Charsets.UTF_8))

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (json.has("trip")) {
                    val trip = json.getJSONObject("trip")
                    val summary = trip.getJSONObject("summary")
                    val length = summary.optDouble("length", 0.0)
                    val time = summary.optInt("time", 0)

                    val legs = trip.getJSONArray("legs")
                    if (legs.length() > 0) {
                        val leg = legs.getJSONObject(0)
                        val shape = leg.optString("shape", "")
                        val geometry = decodePolyline(shape, 1e6)

                        val instructions = mutableListOf<String>()
                        val maneuverPoints = mutableListOf<ManeuverPoint>()
                        if (leg.has("maneuvers")) {
                            val maneuvers = leg.getJSONArray("maneuvers")
                            for (i in 0 until maneuvers.length()) {
                                val maneuver = maneuvers.getJSONObject(i)
                                val instruction = maneuver.optString("instruction", "")
                                val beginShapeIndex = maneuver.optInt("begin_shape_index", -1)
                                val lengthManeuver = maneuver.optDouble("length", 0.0) // km to next maneuver
                                val type = maneuver.optInt("type", 0)
                                if (instruction.isNotBlank()) {
                                    instructions.add(instruction)
                                    if (beginShapeIndex >= 0 && beginShapeIndex < geometry.size) {
                                        val point = geometry[beginShapeIndex]
                                        maneuverPoints.add(
                                            ManeuverPoint(
                                                instruction = instruction,
                                                lat = point.latitude,
                                                lon = point.longitude,
                                                distanceToNextMeters = lengthManeuver * 1000.0,
                                                type = type,
                                                beginShapeIndex = beginShapeIndex
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        return@withContext RouteResult(geometry, length, time, instructions, maneuverPoints)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            connection?.disconnect()
        }
        null
    }
}

// Precision 6 polyline decoding for Valhalla
private fun decodePolyline(encoded: String, precision: Double = 1e6): List<LatLng> {
    val poly = mutableListOf<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lng += dlng

        val p = LatLng(lat.toDouble() / precision, lng.toDouble() / precision)
        poly.add(p)
    }
    return poly
}

// Maps Valhalla maneuver type to a Material icon.
// See: https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/#maneuver-type
private fun maneuverIcon(type: Int): ImageVector = when (type) {
    1, 7, 8, 17, 22 -> Icons.Filled.Straight             // start / continue / straight / ramp / stay straight
    2, 3, 9, 16 -> Icons.Filled.Navigation               // slight right / slight left (incl. starts)
    10, 11, 18, 20, 23 -> Icons.Filled.TurnRight         // right / sharp right / ramp right / exit right / stay right
    14, 15, 19, 21, 24 -> Icons.Filled.TurnLeft          // left / sharp left / ramp left / exit left / stay left
    12, 13, 26, 27 -> Icons.Filled.Refresh               // u-turn / roundabout enter / roundabout exit
    25 -> Icons.Filled.SwapHoriz                         // merge
    4, 5, 6 -> Icons.Filled.Flag                         // destination variants
    else -> Icons.Filled.Straight
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2).let { it * it }
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return R * c
}

private suspend fun fetchCurrentStreet(lat: Double, lon: Double): String? {
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=17&addressdetails=1"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "RetroCarDash/1.0 (personal-use)")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (json.has("address")) {
                    val address = json.getJSONObject("address")
                    val fields = listOf("road", "pedestrian", "cycleway", "footway")
                    for (field in fields) {
                        if (address.has(field)) {
                            val value = address.optString(field)
                            if (!value.isNullOrBlank()) {
                                return@withContext value
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore network failures
        } finally {
            connection?.disconnect()
        }
        null
    }
}

@SuppressLint("MissingPermission")
private fun enableLocation(
    context: Context,
    mapLibreMap: MapLibreMap,
    style: Style,
    isChaseView: Boolean = false
) {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        val locationComponent = mapLibreMap.locationComponent

        val locationEngineRequest = LocationEngineRequest.Builder(1000L)
            .setFastestInterval(500L)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()

        val activationOptions = LocationComponentActivationOptions.builder(context, style)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(locationEngineRequest)
            .build()

        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = if (isChaseView) CameraMode.TRACKING_GPS else CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS

        val zoom = if (isChaseView) 18.0 else 16.0
        val tilt = if (isChaseView) 60.0 else 0.0
        val location = locationComponent.lastKnownLocation
        val target = if (location != null) LatLng(location.latitude, location.longitude) else mapLibreMap.cameraPosition.target
        val bearing = if (isChaseView && location != null && location.hasBearing()) location.bearing.toDouble() else 0.0

        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(zoom)
            .tilt(tilt)
            .bearing(bearing)
            .build()

        mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000)
    }
}

@Composable
fun KeyboardKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF333333),
    textColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    fontSize: Int = 18
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CustomKeyboard(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l","ñ"),
        listOf("z","x","c","v","b","n","m",",",".","-")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.Black)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    KeyboardKey(
                        text = key,
                        onClick = { onKeyPress(key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        // Bottom action row: mic, space (wide), backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyboardKey(
                text = "🎙️",
                onClick = onMic,
                modifier = Modifier.weight(1.5f),
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF555555),
                fontSize = 20
            )
            KeyboardKey(
                text = "ESPACIO",
                onClick = onSpace,
                modifier = Modifier.weight(7f),
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF555555),
                fontSize = 14
            )
            KeyboardKey(
                text = "⌫",
                onClick = onBackspace,
                modifier = Modifier.weight(1.5f),
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF555555),
                fontSize = 22
            )
        }
    }
}

/**
 * A "display" tile for a single data value, styled after a classic BMW
 * instrument cluster: dark reddish background, accent-color text inside.
 * Used to visually separate data readouts (hour, distance, street name, ...)
 * from plain control buttons (cancel X, volume toggle) which stay black
 * with white icons.
 */
@Composable
fun DisplayBox(
    modifier: Modifier = Modifier,
    horizontalPadding: Int = 10,
    verticalPadding: Int = 6,
    cornerRadius: Int = 4,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(DashColors.DISPLAY_BG, RoundedCornerShape(cornerRadius.dp))
            .padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
