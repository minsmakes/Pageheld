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

fun DiaryLocation.pin(): Pin? =
    if (lat != null && lon != null)
        Pin(label(), lat, lon, media.lines().firstOrNull { it.endsWith(".photo") })
    else null

@Composable
fun PlacesMap(
    pins: List<Pin>,
    modifier: Modifier,
    recenter: Int = 0,
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
            m.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                failed = false
                map = m
            }
            m.addOnMapClickListener { point ->
                onChoose?.invoke(point.latitude, point.longitude)
                onChoose != null
            }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, recenter) {
        if (recenter > 0)
            pins.firstOrNull()?.let { pin ->
                map?.moveCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        LatLng(pin.lat, pin.lon),
                        15.0,
                    )
                )
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
fun Memories(
    book: Notebook,
    pages: List<Page>,
    locations: List<DiaryLocation>,
    open: (Page) -> Unit,
) {
    var list by rememberSaveable { mutableStateOf(true) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var scopePlace by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var photos by rememberSaveable { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    val byId = remember(pages) { pages.associateBy { it.id } }
    val found =
        locations
            .filter { location ->
                val page = byId[location.pageId]
                page != null &&
                    (location.label() + " " + location.notes + " " + page.heading()).contains(
                        q,
                        true,
                    ) &&
                    (!photos || location.media.isNotBlank())
            }
            .sortedByDescending { byId[it.pageId]?.created }
    fun groupKey(location: DiaryLocation) =
        listOf(location.label(), location.lat, location.lon).joinToString("\u0000")
    val places = found.groupBy(::groupKey)
    val pins = places.values.mapNotNull { it.first().pin() }
    val preview = places[selected]?.firstOrNull() ?: found.firstOrNull()
    Column {
        Header("Places") {
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
                    .forEach { (key, entries) ->
                        item { Title(entries.first().label(), 20) }
                        items(entries.distinctBy { it.pageId }, key = { it.id }) {
                            byId[it.pageId]?.let { page -> EntryRow(page, book, open) }
                        }
                    }
            }
        else
            Box(Modifier.fillMaxSize()) {
                PlacesMap(
                    pins,
                    Modifier.fillMaxSize(),
                    select = { pin ->
                        selected =
                            found
                                .firstOrNull {
                                    it.label() == pin.name && it.lat == pin.lat && it.lon == pin.lon
                                }
                                ?.let(::groupKey)
                    },
                )
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 35.dp)
                ) {
                    Panel(
                        Modifier.clickable {
                            scopePlace = preview?.let(::groupKey)
                            list = true
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val photo = preview?.media?.lines()?.firstOrNull { it.isNotBlank() }
                            if (photo != null)
                                Thumbnail(File(mediaDir(book), photo), Modifier.size(62.dp))
                            else Glyph("map-pin", tint = sage)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Title(preview?.label() ?: "Saved places", 19)
                                val count =
                                    places[preview?.let(::groupKey)]?.distinctBy { it.pageId }?.size
                                        ?: 0
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

@Composable
internal fun CompactMemories(book: Notebook,pages: List<Page>,locations: List<DiaryLocation>,
    read: (Page)->Unit,view: (Page,String)->Unit) {
    var places by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
            TextButton(onClick={places=false}) { Text("Album") }
            TextButton(onClick={places=true}) { Text("Places") }
        }
        if(places) Memories(book,pages,locations,read) else Album(book,pages,locations,view)
    }
}
