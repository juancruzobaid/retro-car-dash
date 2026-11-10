package com.cpobaid.retrocardash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import java.net.HttpURLConnection
import java.net.URL

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
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

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
                                    layer.setProperties(
                                        PropertyFactory.textColor(Color.parseColor("#FF4500")),
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
            containerColor = androidx.compose.ui.graphics.Color(0xFFFF4500),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Toggle View Mode",
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
                color = androidx.compose.ui.graphics.Color(0xFFFF4500),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Text(
                text = "km/h",
                color = androidx.compose.ui.graphics.Color(0xFFFF4500),
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
                color = androidx.compose.ui.graphics.Color(0xFFFF4500),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
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
