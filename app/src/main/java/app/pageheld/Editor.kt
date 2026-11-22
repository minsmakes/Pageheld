package app.pageheld

import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.*

fun mediaDir(book: Notebook) = File(book.context.filesDir, "attachments").apply { mkdirs() }

@Composable
fun Thumbnail(file: File, modifier: Modifier, large: Boolean = false) {
    val bitmap by
        produceState<android.graphics.Bitmap?>(null, file, large) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            if (file.extension == "video")
                                MediaMetadataRetriever().let { retriever ->
                                    try {
                                        retriever.setDataSource(file.path)
                                        retriever.getScaledFrameAtTime(
                                            -1,
                                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                            320,
                                            240,
                                        )
                                    } finally {
                                        retriever.release()
                                    }
                                }
                            else
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) {
                                    decoder,
                                    info,
                                    _ ->
                                    decoder.setTargetSampleSize(
                                        max(
                                            1,
                                            max(info.size.width, info.size.height) /
                                                (if (large) 1400 else 250),
                                        )
                                    )
                                }
                        }
                        .getOrNull()
                }
        }
    Box(
        modifier.clip(RoundedCornerShape(7.dp)).background(pale),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                if (file.extension == "video") "Attached video" else "Attached photograph",
                Modifier.fillMaxSize(),
                contentScale = if (large) ContentScale.Fit else ContentScale.Crop,
            )
        }
        if (bitmap == null || file.extension == "video")
            Glyph(if (file.extension == "video") "play" else "image")
    }
}

@Composable
fun Appearance(
    settings: Map<String, String>,
    paper: String,
    setPaper: (String) -> Unit,
    setting: (String, String) -> Unit,
) {
    Title("Paper styles", 21)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        papers.forEach { name ->
            Column(
                Modifier.clickable { setPaper(name) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(60.dp, 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .paper(name, interval = 8.dp, margin = if (name == "Letter") 9.dp else 0.dp)
                        .border(
                            if (name == paper) 2.dp else .7.dp,
                            if (name == paper) sage else edge,
                            RoundedCornerShape(8.dp),
                        )
                ) {
                    if (name == paper) Glyph("check", "Selected", sage, 17)
                    if (name == "Letter")
                        Text(
                            "Dear me,",
                            Modifier.padding(7.dp),
                            fontFamily = serif,
                            fontSize = 9.sp,
                        )
                    if (name == "Scrapbook") Glyph("image", tint = sage)
                }
                Text(name, fontSize = 12.sp)
            }
        }
    }
    Title("Paper tone", 18)
    Row {
        swatches.forEachIndexed { i, c ->
            IconButton(onClick = { setting("tone", i.toString()) }) {
                Box(
                    Modifier.size(32.dp)
                        .background(c, CircleShape)
                        .border(.8.dp, edge, CircleShape)
                        .semantics {
                            contentDescription = listOf("Ivory", "Sand", "Blush", "Mist", "Sage")[i]
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if ((settings["tone"] ?: "0") == i.toString()) Glyph("check", size = 16)
                }
            }
        }
    }
    listOf(
            Triple("Font", "font", listOf("Handwriting", "Serif", "Sans")),
            Triple("Line spacing", "spacing", listOf("Classic", "Close", "Airy")),
            Triple("Margins", "margin", listOf("Classic", "Narrow", "Wide")),
        )
        .forEach { (label, key, choices) ->
            Title(label, 18)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                choices.forEach {
                    Tag(it, it == (settings[key] ?: choices.first())) { setting(key, it) }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(
    initial: Page,
    book: Notebook,
    chapters: List<Chapter>,
    settings: Map<String, String>,
    back: () -> Unit,
) {
    var page by remember(initial.id) { mutableStateOf(initial) }
    val past = remember { mutableStateListOf<Pair<String, String>>() }
    val future = remember { mutableStateListOf<Pair<String, String>>() }
    fun change(next: Page, record: Boolean = false) {
        if (record) {
            past.add(page.title to page.body)
            if (past.size > 60) past.removeAt(0)
            future.clear()
        }
        page = next
        book.save(next)
    }
    var sheet by rememberSaveable { mutableStateOf("") }
    var viewing by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null)
                scope.launch {
                    importing = true
                    try {
                        val file =
                            withContext(Dispatchers.IO) {
                                val resolver = book.context.contentResolver
                                val dest =
                                    File(
                                        mediaDir(book),
                                        uid() +
                                            if (resolver.getType(uri)?.startsWith("video/") == true)
                                                ".video"
                                            else ".photo",
                                    )
                                try {
                                    requireNotNull(resolver.openInputStream(uri)).use { input ->
                                        dest.outputStream().use { input.copyTo(it) }
                                    }
                                    dest
                                } catch (e: Exception) {
                                    dest.delete()
                                    throw e
                                }
                            }
                        change(page.copy(media = (page.files() + file.name).joinToString("\n")))
                    } catch (e: Exception) {
                        error = "Couldn’t import this attachment."
                    } finally {
                        importing = false
                    }
                }
        }
    fun pick(video: Boolean) {
        picker.launch(
            PickVisualMediaRequest(
                if (video) ActivityResultContracts.PickVisualMedia.VideoOnly
                else ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }
    val paper = page.paper.ifBlank { settings["paper"] ?: "Ruled" }
    val color = swatches.getOrElse(settings["tone"]?.toIntOrNull() ?: 0) { ivory }
    val line =
        when (settings["spacing"]) {
            "Close" -> 27
            "Airy" -> 38
            else -> 32
        }.sp
    val density = LocalDensity.current
    var measured by remember(line, density) { mutableFloatStateOf(0f) }
    val interval = with(density) { if (measured > 0f) measured.toDp() else line.toDp() }
    val margin =
        when (settings["margin"]) {
            "Narrow" -> 16
            "Wide" -> 40
            else -> 25
        }.dp
    val font =
        when (settings["font"]) {
            "Sans" -> FontFamily.SansSerif
            "Serif" -> serif
            else -> FontFamily(Font(R.font.kalam))
        }
    var bodyY by remember { mutableFloatStateOf(0f) }
    var baseline by remember { mutableFloatStateOf(0f) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun tools(name: String) {
        keyboard?.hide()
        sheet = name
    }
    androidx.activity.compose.BackHandler { if (!importing) back() }
    Column(Modifier.fillMaxSize().background(color)) {
        Header("", { Action("arrow-left", "Close page", !importing, back) }) {
            Action("undo-2", "Undo", past.isNotEmpty()) {
                future.add(page.title to page.body)
                val p = past.removeAt(past.lastIndex)
                change(page.copy(title = p.first, body = p.second))
            }
            Action("redo-2", "Redo", future.isNotEmpty()) {
                past.add(page.title to page.body)
                val p = future.removeAt(future.lastIndex)
                change(page.copy(title = p.first, body = p.second))
            }
            Action("ellipsis-vertical", "Page options") { tools("Page options") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minimum = maxHeight
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .heightIn(min = minimum)
                    .paper(
                        paper,
                        color,
                        interval,
                        margin,
                        bodyY + baseline + with(density) { 3.dp.toPx() },
                    )
            ) {
                Column(Modifier.padding(start = margin + 12.dp, end = 18.dp, top = 18.dp)) {
                    Note(stamp(page.created, "EEE, d MMM yyyy  ·  HH:mm"))
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            page.title,
                            { change(page.copy(title = it), true) },
                            Modifier.width(IntrinsicSize.Max)
                                .widthIn(min = 130.dp, max = 245.dp)
                                .weight(1f, fill = false)
                                .semantics { contentDescription = "Page title" },
                            textStyle = TextStyle(fontFamily = font, fontSize = 35.sp, color = ink),
                            singleLine = true,
                            cursorBrush = SolidColor(sage),
                            decorationBox = { inner ->
                                Box {
                                    if (page.title.isEmpty())
                                        Text(
                                            "Untitled page",
                                            maxLines = 1,
                                            color = muted,
                                            fontSize = 35.sp,
                                            fontFamily = font,
                                        )
                                    inner()
                                }
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        Glyph("sprout", tint = sage, size = 25)
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Tag(page.mood.ifBlank { "Mood" }, icon = "smile") { tools("Mood") }
                        Tag(page.place.ifBlank { "Place" }, icon = "map-pin") { tools("Place") }
                    }
                }
                Spacer(Modifier.height(33.dp))
                BasicTextField(
                    page.body,
                    { change(page.copy(body = it), true) },
                    Modifier.fillMaxWidth()
                        .onPlaced { bodyY = it.positionInParent().y }
                        .padding(start = margin + 10.dp, end = 18.dp)
                        .heightIn(min = interval * 8)
                        .semantics { contentDescription = "Page body" },
                    textStyle =
                        TextStyle(
                            fontFamily = font,
                            fontSize = 21.sp,
                            lineHeight = line,
                            color = ink,
                            lineHeightStyle =
                                LineHeightStyle(
                                    LineHeightStyle.Alignment.Center,
                                    LineHeightStyle.Trim.None,
                                ),
                        ),
                    onTextLayout = {
                        baseline = it.getLineBaseline(0)
                        if (it.lineCount > 1)
                            measured = it.getLineBaseline(1) - it.getLineBaseline(0)
                    },
                    cursorBrush = SolidColor(sage),
                    decorationBox = { inner ->
                        Box {
                            if (page.body.isEmpty())
                                Text(
                                    "Begin your page…",
                                    color = muted,
                                    fontFamily = font,
                                    fontSize = 21.sp,
                                    lineHeight = line,
                                )
                            inner()
                        }
                    },
                )
                Row(
                    Modifier.padding(start = margin, end = 12.dp, top = 30.dp, bottom = 24.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    page.files().forEach { name ->
                        Thumbnail(
                            File(mediaDir(book), name),
                            Modifier.size(102.dp).clickable { viewing = name },
                        )
                    }
                    Box(
                        Modifier.size(76.dp, 100.dp)
                            .drawBehind {
                                drawRoundRect(
                                    edge,
                                    cornerRadius =
                                        androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                                    style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                            1.dp.toPx(),
                                            pathEffect =
                                                PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f),
                                        ),
                                )
                            }
                            .clickable(enabled = !importing) { pick(false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (importing) CircularProgressIndicator()
                        else Glyph("plus", "Attach photograph")
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(2.dp, round)
                .background(ivory, round),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Action("type", "Paper and text") { tools("Appearance") }
            Action("image", "Add photo", !importing) { pick(false) }
            Action("video", "Add video", !importing) { pick(true) }
            Action("map-pin", "Choose place") { tools("Place") }
            Action("smile", "Choose mood") { tools("Mood") }
            Action("notebook", "Template and chapter") { tools("Organize page") }
        }
    }
    if (sheet.isNotEmpty())
        ModalBottomSheet(
            onDismissRequest = { sheet = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ivory,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Title(sheet, 25)
                when (sheet) {
                    "Appearance" ->
                        Appearance(
                            settings,
                            paper,
                            { change(page.copy(paper = it)) },
                            book::setting,
                        )
                    "Mood" ->
                        (moods + "Clear mood").forEach {
                            Tag(it, it == page.mood) {
                                change(page.copy(mood = if (it == "Clear mood") "" else it))
                                sheet = ""
                            }
                        }
                    "Place" ->
                        PlaceEditor(page) { name, lat, lon ->
                            change(page.copy(place = name, lat = lat, lon = lon))
                            sheet = ""
                        }
                    "Organize page" -> {
                        Title("Entry template", 19)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            templates.entries.forEachIndexed { index, (name, prompt) ->
                                Column(
                                    Modifier.width(88.dp)
                                        .heightIn(min = 104.dp)
                                        .clip(round)
                                        .background(if (page.template == name) pale else ivory)
                                        .border(
                                            1.dp,
                                            if (page.template == name) sage else edge,
                                            round,
                                        )
                                        .clickable {
                                            change(
                                                page.copy(
                                                    template = name,
                                                    body =
                                                        if (
                                                            page.body.isBlank() ||
                                                                page.body ==
                                                                    templates[page.template]
                                                        )
                                                            prompt
                                                        else page.body,
                                                ),
                                                true,
                                            )
                                        }
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Glyph(
                                        listOf(
                                            "pencil",
                                            "calendar-days",
                                            "image",
                                            "heart",
                                            "mail",
                                            "leaf",
                                            "dumbbell",
                                        )[index],
                                        tint = sage,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        name,
                                        fontFamily = serif,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp,
                                    )
                                }
                            }
                        }
                        Title("Chapter", 19)
                        Tag("Unfiled", page.chapter == null) { change(page.copy(chapter = null)) }
                        chapters.forEach {
                            Tag(it.name, page.chapter == it.id) {
                                change(page.copy(chapter = it.id))
                            }
                        }
                    }
                    else -> {
                        Tag(if (page.starred) "Remove bookmark" else "Bookmark page") {
                            change(page.copy(starred = !page.starred))
                            sheet = ""
                        }
                        Tag("Appearance") { sheet = "Appearance" }
                        Tag("Template and chapter") { sheet = "Organize page" }
                        Tag("Delete page") {
                            sheet = ""
                            error = "Delete this page?"
                        }
                    }
                }
            }
        }
    if (viewing.isNotBlank())
        Dialog(onDismissRequest = { viewing = "" }) {
            Column(Modifier.background(ivory, round).padding(12.dp)) {
                val file = File(mediaDir(book), viewing)
                if (file.extension == "video")
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(file.path)
                                setMediaController(
                                    android.widget.MediaController(ctx).also {
                                        it.setAnchorView(this)
                                    }
                                )
                                start()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                    )
                else Thumbnail(file, Modifier.fillMaxWidth().height(360.dp), true)
                TextButton(onClick = { viewing = "" }) { Text("Close") }
            }
        }
    if (error.isNotBlank())
        AlertDialog(
            onDismissRequest = { error = "" },
            title = { Title(error) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (error == "Delete this page?") {
                            val removed = page
                            book.queue {
                                book.dao.removePage(removed.id)
                                removed.files().forEach { File(mediaDir(book), it).delete() }
                            }
                            back()
                        }
                        error = ""
                    }
                ) {
                    Text(if (error == "Delete this page?") "Delete" else "OK")
                }
            },
            dismissButton = { TextButton(onClick = { error = "" }) { Text("Cancel") } },
        )
}
