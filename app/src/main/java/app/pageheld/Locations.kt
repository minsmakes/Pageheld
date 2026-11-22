package app.pageheld

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.location.LocationManagerCompat
import java.io.File
import java.time.*
import kotlinx.coroutines.*

@Composable
fun LocationNotebook(
    page: Page,
    book: Notebook,
    locations: List<DiaryLocation>,
    close: () -> Unit,
) {
    var editing by rememberSaveable {
        mutableStateOf<String?>(if (locations.isEmpty()) uid() else null)
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<DiaryLocation?>(null) }
    val scope = rememberCoroutineScope()
    fun write(done: () -> Unit = {}, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                operation()
                done()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "Couldn’t save the location. Please try again."
            } finally {
                busy = false
            }
        }
    }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    fun leaveForm() {
        dirty = false
        if (locations.isEmpty()) close() else editing = null
    }
    fun back() {
        if (!busy) {
            if (editing != null) {
                if (dirty) discard = true else leaveForm()
            } else close()
        }
    }
    Dialog(
        onDismissRequest = ::back,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(ivory).safeDrawingPadding().imePadding()) {
            Header(
                if (editing == null) "Places on this page" else "Location",
                { Action("arrow-left", "Back", !busy, ::back) },
            ) {
                if (editing == null) Action("plus", "Add location", !busy) { editing = uid() }
            }
            if (error.isNotBlank()) Note(error, Modifier.padding(16.dp))
            if (editing != null)
                key(editing) {
                    val seed = remember {
                        locations.find { it.id == editing }
                            ?: DiaryLocation(id = editing!!, pageId = page.id, position = 0)
                    }
                    LocationForm(
                        seed,
                        page,
                        book,
                        locations.none { it.id == seed.id },
                        busy,
                        { dirty = it },
                    ) { value ->
                        write({
                            dirty = false
                            close()
                        }) {
                            book.dao.saveLocation(page, value)
                        }
                    }
                }
            else
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (locations.isEmpty())
                        Note("Add a place by name, map pin or current location.")
                    locations.forEachIndexed { index, loc ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f)
                                    .clickable(enabled = !busy) { editing = loc.id }
                                    .padding(vertical = 8.dp)
                            ) {
                                Title(loc.label(), 19)
                                loc.visited?.let {
                                    Note(
                                        stamp(it, "yMMMd") +
                                            " · " +
                                            android.text.format.DateFormat.getTimeFormat(
                                                    book.context
                                                )
                                                .format(java.util.Date(it))
                                    )
                                }
                                if (loc.notes.isNotBlank()) Note(loc.notes)
                            }
                            Action("arrow-up", "Move ${loc.label()} earlier", !busy && index > 0) {
                                write { book.dao.moveLocation(page.id, loc.id, -1) }
                            }
                            Action(
                                "arrow-down",
                                "Move ${loc.label()} later",
                                !busy && index < locations.lastIndex,
                            ) {
                                write { book.dao.moveLocation(page.id, loc.id, 1) }
                            }
                            Action("trash-2", "Remove ${loc.label()}", !busy) { removing = loc }
                        }
                        HorizontalDivider(color = edge)
                    }
                    TextButton(onClick = { editing = uid() }, enabled = !busy) {
                        Text("Add location")
                    }
                    Button(onClick = close, enabled = !busy) { Text("Return to page") }
                }
        }
        if (discard)
            AlertDialog(
                onDismissRequest = { discard = false },
                title = { Title("Discard location edits?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            discard = false
                            leaveForm()
                        }
                    ) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { discard = false }) { Text("Keep editing") }
                },
            )
        removing?.let { loc ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Title("Remove this location?") },
                text = { Note(loc.label()) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            removing = null
                            write { book.dao.removeLocation(page.id, loc.id) }
                        }
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ColumnScope.LocationForm(
    seed: DiaryLocation,
    page: Page,
    book: Notebook,
    isNew: Boolean,
    busy: Boolean,
    onDirty: (Boolean) -> Unit,
    save: (DiaryLocation) -> Unit,
) {
    val ctx = LocalContext.current
    var name by rememberSaveable { mutableStateOf(seed.name) }
    var lat by rememberSaveable { mutableStateOf(seed.lat) }
    var lon by rememberSaveable { mutableStateOf(seed.lon) }
    var notes by rememberSaveable { mutableStateOf(seed.notes) }
    var visited by rememberSaveable { mutableStateOf(seed.visited) }
    var media by rememberSaveable { mutableStateOf(seed.media) }
    var camera by remember { mutableIntStateOf(0) }
    val draft =
        seed.copy(
            name = name,
            lat = lat,
            lon = lon,
            visited = visited,
            notes = notes,
            media = media,
        )
    SideEffect { onDirty(draft != seed) }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaceSearch(book) { result ->
            name = result.name
            lat = result.lat
            lon = result.lon
            camera++
        }
        OutlinedTextField(
            name,
            { name = it },
            Modifier.fillMaxWidth(),
            label = { Text("Place name") },
            singleLine = true,
        )
        Note("Tap the map to drop a pin.")
        PlacesMap(
            if (lat != null && lon != null) listOf(Pin(name, lat!!, lon!!)) else emptyList(),
            Modifier.fillMaxWidth().height(194.dp),
            recenter = camera,
            choose = { a, b ->
                lat = a
                lon = b
            },
        )
        lat?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Note("%.5f, %.5f".format(it, lon))
                Action("x", "Remove coordinates") {
                    lat = null
                    lon = null
                }
            }
        }
        CaptureLocation { a, b ->
            lat = a
            lon = b
            camera++
        }
        OutlinedTextField(
            notes,
            { notes = it },
            Modifier.fillMaxWidth(),
            label = { Text("Place notes") },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    val d = date(visited ?: System.currentTimeMillis())
                    DatePickerDialog(
                            ctx,
                            { _, y, m, day ->
                                TimePickerDialog(
                                        ctx,
                                        { _, hour, min ->
                                            visited =
                                                LocalDateTime.of(y, m + 1, day, hour, min)
                                                    .atZone(ZoneId.systemDefault())
                                                    .toInstant()
                                                    .toEpochMilli()
                                        },
                                        d.hour,
                                        d.minute,
                                        android.text.format.DateFormat.is24HourFormat(ctx),
                                    )
                                    .show()
                            },
                            d.year,
                            d.monthValue - 1,
                            d.dayOfMonth,
                        )
                        .show()
                }
            ) {
                Text(
                    visited?.let {
                        stamp(it, "yMMMd") +
                            " · " +
                            android.text.format.DateFormat.getTimeFormat(ctx)
                                .format(java.util.Date(it))
                    } ?: "Add date and time"
                )
            }
            if (visited != null) Action("x", "Clear date and time") { visited = null }
        }
        if (page.files().isNotEmpty()) {
            Title("Link page attachments", 18)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                page.files().forEachIndexed { i, file ->
                    val files = media.lines().filter(String::isNotBlank)
                    val checked = file in files
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Thumbnail(File(mediaDir(book), file), Modifier.size(60.dp))
                        Checkbox(
                            checked,
                            {
                                media =
                                    (if (checked) files - file else files + file).joinToString("\n")
                            },
                            modifier =
                                Modifier.semantics { contentDescription = "Link attachment ${i+1}" },
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = edge)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            enabled = !busy && (name.isNotBlank() || (lat != null && lon != null)),
            onClick = { save(draft.copy(name = name.trim())) },
        ) {
            Text(if (isNew) "Add this place" else "Save changes")
        }
    }
}

@Composable
private fun CaptureLocation(onFix: (Double, Double) -> Unit) {
    val ctx = LocalContext.current
    var requesting by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    val currentFix by rememberUpdatedState(onFix)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants ->
            requesting = grants.values.any { it }
            if (!requesting) message = "Location access was declined. You can still pin the map."
        }
    DisposableEffect(requesting) {
        val cancel = CancellationSignal()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        if (requesting) {
            val manager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val precise = ctx.checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
            val provider =
                listOfNotNull(
                        LocationManager.GPS_PROVIDER.takeIf { precise },
                        LocationManager.NETWORK_PROVIDER,
                    )
                    .firstOrNull { manager.isProviderEnabled(it) }
            if (provider == null) {
                message = "Turn on device location or choose a map pin."
                requesting = false
            } else
                try {
                    message = "Getting current location…"
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        cancel,
                        ctx.mainExecutor,
                    ) { loc ->
                        if (!cancel.isCanceled) {
                            requesting = false
                            if (loc != null) {
                                currentFix(loc.latitude, loc.longitude)
                                message = "Location captured."
                            } else message = "No location fix. Try again or tap the map."
                        }
                    }
                    handler.postDelayed(
                        {
                            cancel.cancel()
                            requesting = false
                            message = "No location fix. Try again or tap the map."
                        },
                        25000,
                    )
                } catch (e: SecurityException) {
                    requesting = false
                    message = "Location permission is unavailable."
                }
        }
        onDispose {
            cancel.cancel()
            handler.removeCallbacksAndMessages(null)
        }
    }
    TextButton(
        enabled = !requesting,
        onClick = {
            if (ctx.checkSelfPermission(ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED)
                requesting = true
            else permission.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        },
    ) {
        Glyph("locate-fixed", tint = sage)
        Spacer(Modifier.width(6.dp))
        Text("Use current location")
    }
    if (message.isNotBlank()) Note(message)
}
