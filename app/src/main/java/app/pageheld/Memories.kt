@file:Suppress("DEPRECATION")

package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.*

data class Pin(val name: String, val lat: Double, val lon: Double, val photo: String? = null)

fun Page.pin() =
    if (lat != null && lon != null)
        Pin(place, lat, lon, files().firstOrNull { it.endsWith(".photo") })
    else null

@Composable
fun PlacesMap(
    pins: List<Pin>,
    modifier: Modifier,
    choose: ((Double, Double) -> Unit)? = null,
    select: (Pin) -> Unit = {},
) {
    val ctx = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val view = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).also { it.onCreate(null) }
    }
    val owner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val onSelect by rememberUpdatedState(select)
    val onChoose by rememberUpdatedState(choose)
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        view.addOnDidFailLoadingMapListener { failed = true }
        view.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            val first = pins.firstOrNull()
            m.cameraPosition =
                CameraPosition.Builder()
                    .target(LatLng(first?.lat ?: 52.514, first?.lon ?: 13.354))
                    .zoom(13.0)
                    .build()
            m.setStyle("https://tiles.openfreemap.org/styles/liberty") { failed = false }
            m.addOnMapClickListener { point ->
                onChoose?.invoke(point.latitude, point.longitude)
                onChoose != null
            }
            map = m
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, pins) {
        map?.let { m ->
            m.markers.toList().forEach { m.removeMarker(it) }
            val markers = mutableMapOf<Long, Pin>()
            for (pin in pins) {
                val options = MarkerOptions().position(LatLng(pin.lat, pin.lon)).title(pin.name)
                val picture = pin.photo?.let { File(ctx.filesDir, "attachments/$it") }
                if (picture != null) {
                    val bitmap =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            photoMarker(picture, density)
                        }
                    if (bitmap != null)
                        options.icon(
                            org.maplibre.android.annotations.IconFactory.getInstance(ctx)
                                .fromBitmap(bitmap)
                        )
                }
                markers[m.addMarker(options).id] = pin
            }
            m.setOnMarkerClickListener { marker ->
                markers[marker.id]?.let { onSelect(it) }
                true
            }
        }
    }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if (failed)
            Surface(color = ivory, modifier = Modifier.align(Alignment.TopCenter)) {
                Note("Map unavailable · Saved places are in the list.")
            }
    }
}

@Composable
fun PlaceEditor(page: Page, save: (String, Double?, Double?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(page.place) }
    var lat by rememberSaveable { mutableStateOf(page.lat) }
    var lon by rememberSaveable { mutableStateOf(page.lon) }
    OutlinedTextField(name, { name = it }, label = { Text("Place name") }, singleLine = true)
    Note("Tap the map to drop a pin, or save just the place name.")
    PlacesMap(
        if (lat != null && lon != null) listOf(Pin(name, lat!!, lon!!)) else emptyList(),
        Modifier.fillMaxWidth().height(240.dp),
        choose = { a, b ->
            lat = a
            lon = b
        },
    )
    if (lat != null) Note("%.4f, %.4f".format(lat, lon))
    Row {
        Button(onClick = { save(name.trim(), lat, lon) }, enabled = name.isNotBlank()) {
            Text("Save place")
        }
        TextButton(onClick = { save("", null, null) }) { Text("Remove place") }
    }
}

@Composable
fun Memories(book: Notebook, pages: List<Page>, open: (Page) -> Unit) {
    var list by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var scopePlace by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var photos by rememberSaveable { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    val found =
        pages.filter {
            it.place.isNotBlank() &&
                (it.place + " " + it.heading()).contains(q, true) &&
                (!photos || it.media.isNotBlank())
        }
    val places = found.groupBy { it.place }
    val pins = found.mapNotNull { it.pin() }.distinctBy { Triple(it.name, it.lat, it.lon) }
    val preview = places[selected]?.firstOrNull() ?: found.firstOrNull()
    Column {
        Header("Memories") {
            Action("search", "Search memories") {
                searching = !searching
                if (!searching) q = ""
            }
            Box {
                Action("sliders-horizontal", "Memory filters") { filters = true }
                DropdownMenu(filters, { filters = false }) {
                    DropdownMenuItem(
                        text = { Text(if (list) "Show map" else "Saved places list") },
                        onClick = {
                            list = !list
                            scopePlace = null
                            filters = false
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (photos) "Show all entries" else "With attachments only")
                        },
                        onClick = {
                            photos = !photos
                            filters = false
                        },
                    )
                }
            }
        }
        if (searching)
            OutlinedTextField(
                q,
                { q = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Search places and pages") },
            )
        if (list)
            LazyColumn(
                contentPadding =
                    PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 86.dp)
            ) {
                if (found.isEmpty())
                    item {
                        Empty("No saved places", "Add a place from the pin tool in a journal page.")
                    }
                if (scopePlace != null) item { Tag("All saved places") { scopePlace = null } }
                places
                    .filterKeys { scopePlace == null || it == scopePlace }
                    .forEach { (name, entries) ->
                        item { Title(name, 20) }
                        items(entries, key = { it.id }) { EntryRow(it, book, open) }
                    }
            }
        else
            Box(Modifier.fillMaxSize()) {
                PlacesMap(pins, Modifier.fillMaxSize(), select = { selected = it.name })
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 35.dp)
                ) {
                    Panel(
                        Modifier.clickable {
                            scopePlace = preview?.place
                            list = true
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val photo = preview?.files()?.firstOrNull()
                            if (photo != null)
                                Thumbnail(File(mediaDir(book), photo), Modifier.size(62.dp))
                            else Glyph("map-pin", tint = sage)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Title(preview?.place ?: "Saved places", 19)
                                val count = places[preview?.place]?.size ?: 0
                                Note(
                                    if (count == 0) "Add a place to a page to remember it here."
                                    else "$count " + if (count == 1) "memory" else "memories"
                                )
                            }
                            Glyph("chevron-right", size = 18)
                        }
                    }
                }
            }
    }
}

private fun photoMarker(file: File, density: Float): android.graphics.Bitmap? =
    runCatching {
            val diameter = (50 * density).toInt()
            val source =
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(file)
                ) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(
                        (minOf(info.size.width, info.size.height) / diameter).coerceAtLeast(1)
                    )
                }
            val radius = diameter / 2f
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    diameter,
                    diameter + (10 * density).toInt(),
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.rgb(99, 115, 82)
            canvas.drawCircle(radius, diameter + 4 * density, 4 * density, paint)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(radius, radius, radius, paint)
            val scale = diameter.toFloat() / minOf(source.width, source.height)
            val shader =
                android.graphics.BitmapShader(
                    source,
                    android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP,
                )
            shader.setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(
                        (diameter - source.width * scale) / 2,
                        (diameter - source.height * scale) / 2,
                    )
                }
            )
            paint.shader = shader
            canvas.drawCircle(radius, radius, radius - 2 * density, paint)
            bitmap
        }
        .getOrNull()
