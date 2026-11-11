package com.cpobaid.retrocardash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

object DashColors {
    val ACCENT = androidx.compose.ui.graphics.Color(0xFFE60000)
    const val ACCENT_HEX = "#E60000"
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

@Composable
fun ColorCalibrationScreen(modifier: Modifier = Modifier) {
    val colors = listOf(
        Pair(androidx.compose.ui.graphics.Color(0xFFFF0000), "#FF0000"),
        Pair(androidx.compose.ui.graphics.Color(0xFFFF0030), "#FF0030"),
        Pair(androidx.compose.ui.graphics.Color(0xFFFF0050), "#FF0050"),
        Pair(androidx.compose.ui.graphics.Color(0xFFFF0080), "#FF0080"),
        Pair(androidx.compose.ui.graphics.Color(0xFFE60000), "#E60000"),
        Pair(androidx.compose.ui.graphics.Color(0xFFDC143C), "#DC143C"),
        Pair(androidx.compose.ui.graphics.Color(0xFFB22222), "#B22222"),
        Pair(androidx.compose.ui.graphics.Color(0xFFFF1500), "#FF1500")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        colors.forEach { (color, hex) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hex,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = androidx.compose.ui.graphics.Color.Black,
                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }
    }
}

data class SearchResult(val displayName: String, val lat: Double, val lon: Double)

data class SearchResponse(val results: List<SearchResult>, val error: String?)

data class RouteResult(
    val geometry: List<LatLng>,
    val distanceKm: Double,
    val durationSeconds: Int,
    val instructions: List<String>
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

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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

    // Reverse geocode current street every 3 seconds
    LaunchedEffect(mapLibreMapInstance) {
        val map = mapLibreMapInstance
        if (map != null) {
            while (true) {
                try {
                    if (map.locationComponent.isLocationComponentActivated) {
                        val location = map.locationComponent.lastKnownLocation
                        if (location != null) {
                            val street = fetchCurrentStreet(location.latitude, location.longitude)
                            currentStreetName = street
                        }
                    }
                } catch (e: Exception) {
                    // Silently ignore network failures
                }
                delay(3000L)
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
                    val displayMetrics = context.resources.displayMetrics
                    val topPaddingPx = (displayMetrics.heightPixels * 0.30).toInt()
                    mapLibreMap.setPadding(0, topPaddingPx, 0, 0)
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

                        // Apply map padding using DisplayMetrics (30% from top, shifting center down)
                        val displayMetrics = context.resources.displayMetrics
                        val topPaddingPx = (displayMetrics.heightPixels * 0.30).toInt()
                        mapLibreMap.setPadding(0, topPaddingPx, 0, 0)

                        val matchedSymbolLayers = mutableListOf<SymbolLayer>()
                        // Customize map style layers for in-car navigation
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
                                        PropertyFactory.textColor(Color.parseColor(DashColors.ACCENT_HEX)),
                                        PropertyFactory.textHaloColor(Color.parseColor("#000000")),
                                        PropertyFactory.textHaloWidth(2.0f),
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

                        if (hasLocationPermission) {
                            enableLocation(context, mapLibreMap, style, isChaseView)
                            mapLibreMap.setPadding(0, topPaddingPx, 0, 0)
                        }
                    }
                }
            }
        )

        // Recenter Button in top-left corner (toggles God View vs Chase View)
        FloatingActionButton(
            onClick = {
                mapLibreMapInstance?.let { map ->
                    val displayMetrics = context.resources.displayMetrics
                    val topPaddingPx = (displayMetrics.heightPixels * 0.30).toInt()
                    map.setPadding(0, topPaddingPx, 0, 0)

                    if (map.locationComponent.isLocationComponentActivated) {
                        isChaseView = !isChaseView
                        map.locationComponent.cameraMode = if (isChaseView) CameraMode.TRACKING_GPS else CameraMode.TRACKING

                        val location = map.locationComponent.lastKnownLocation
                        val target = if (location != null) {
                            LatLng(location.latitude, location.longitude)
                        } else {
                            map.cameraPosition.target
                        }
                        val bearing = if (isChaseView && location != null && location.hasBearing()) {
                            location.bearing.toDouble()
                        } else {
                            0.0
                        }
                        val tilt = if (isChaseView) 60.0 else 0.0
                        val zoom = if (isChaseView) 18.0 else 16.0

                        val cameraPosition = CameraPosition.Builder()
                            .target(target)
                            .zoom(zoom)
                            .tilt(tilt)
                            .bearing(bearing)
                            .build()

                        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000)
                    } else if (hasLocationPermission) {
                        map.style?.let { style ->
                            isChaseView = !isChaseView
                            enableLocation(context, map, style, isChaseView)
                            map.setPadding(0, topPaddingPx, 0, 0)
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(56.dp),
            shape = CircleShape,
            containerColor = DashColors.ACCENT,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Toggle View Mode",
                tint = androidx.compose.ui.graphics.Color.Black
            )
        }

        // Destination Search Button in top-left corner, below recenter button (spaced 80dp down)
        FloatingActionButton(
            onClick = { showSearchSheet = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 88.dp)
                .size(56.dp),
            shape = CircleShape,
            containerColor = DashColors.ACCENT,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search Destination",
                tint = androidx.compose.ui.graphics.Color.Black
            )
        }

        // Speed Display in bottom-left corner
        SpeedDisplay(
            speedKmh = currentSpeedKmh,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Current Street Display at bottom-center
        CurrentStreetDisplay(
            streetName = currentStreetName,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // Route Info & Turn-by-Turn Instructions at top-center
        if (routeGeometry.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ETA & Distance Box
                Box(
                    modifier = Modifier
                        .background(
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "$routeDuration min · ${String.format(java.util.Locale.getDefault(), "%.1f", routeDistance)} km",
                            color = DashColors.ACCENT,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = {
                                routeGeometry = emptyList()
                                destination = null
                                routeInstructions = emptyList()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel Route",
                                tint = DashColors.ACCENT,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Next Maneuver Instruction Box
                val nextInstruction = routeInstructions.firstOrNull()
                if (!nextInstruction.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nextInstruction,
                            color = DashColors.ACCENT,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
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
                            color = if (searchQuery.isBlank())
                                androidx.compose.ui.graphics.Color.Gray
                            else
                                androidx.compose.ui.graphics.Color.White,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Status text
                    Text(
                        text = searchStatus,
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray,
                        fontFamily = FontFamily.Monospace,
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
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = result.displayName,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
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

                    // Custom keyboard
                    CustomKeyboard(
                        onKeyPress = { key -> searchQuery += key },
                        onBackspace = {
                            if (searchQuery.isNotEmpty())
                                searchQuery = searchQuery.dropLast(1)
                        },
                        onSpace = { searchQuery += " " },
                        onSearch = {
                            // Search already happens automatically via LaunchedEffect
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedDisplay(speedKmh: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(min = 100.dp)
            .background(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = speedKmh.toString(),
                color = DashColors.ACCENT,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Text(
                text = "km/h",
                color = DashColors.ACCENT,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CurrentStreetDisplay(streetName: String?, modifier: Modifier = Modifier) {
    if (!streetName.isNullOrBlank()) {
        Box(
            modifier = modifier
                .background(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Por $streetName",
                color = DashColors.ACCENT,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

private suspend fun searchPlaces(
    query: String,
    focusLat: Double?,
    focusLon: Double?,
    apiKey: String
): SearchResponse {
    return withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("https://photon.komoot.io/api/?q=$encoded&limit=8")
            if (focusLat != null && focusLon != null) {
                urlBuilder.append("&lat=$focusLat&lon=$focusLon")
            }
            val urlString = urlBuilder.toString()
            Log.d("RetroCarDash", "Search URL: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            Log.d("RetroCarDash", "Response code: $responseCode")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("RetroCarDash", "Response body: $response")

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
        try {
            val urlString = "https://api.stadiamaps.com/route/v1?api_key=$apiKey"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
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
                        if (leg.has("maneuvers")) {
                            val maneuvers = leg.getJSONArray("maneuvers")
                            for (i in 0 until maneuvers.length()) {
                                val maneuver = maneuvers.getJSONObject(i)
                                val instruction = maneuver.optString("instruction", "")
                                if (instruction.isNotBlank()) {
                                    instructions.add(instruction)
                                }
                            }
                        }

                        return@withContext RouteResult(geometry, length, time, instructions)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
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

private suspend fun fetchCurrentStreet(lat: Double, lon: Double): String? {
    return withContext(Dispatchers.IO) {
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=17&addressdetails=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
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

        // Apply display metrics padding right before animating camera
        val displayMetrics = context.resources.displayMetrics
        val topPaddingPx = (displayMetrics.heightPixels * 0.30).toInt()
        mapLibreMap.setPadding(0, topPaddingPx, 0, 0)

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
    onSearch: () -> Unit,
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
        // Bottom action row: backspace, space (wide), search
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyboardKey(
                text = "⌫",
                onClick = onBackspace,
                modifier = Modifier.weight(1.5f),
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF555555),
                fontSize = 22
            )
            KeyboardKey(
                text = "ESPACIO",
                onClick = onSpace,
                modifier = Modifier.weight(5f),
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF555555),
                fontSize = 14
            )
            KeyboardKey(
                text = "BUSCAR",
                onClick = onSearch,
                modifier = Modifier.weight(1.5f),
                backgroundColor = DashColors.ACCENT,
                textColor = androidx.compose.ui.graphics.Color.Black,
                fontSize = 14
            )
        }
    }
}
