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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 410.dp) 6 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            papers.chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { name ->
                        Column(
                            Modifier.weight(1f).clickable { setPaper(name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(67.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .paper(
                                        name,
                                        interval = 8.dp,
                                        margin = if (name == "Letter") 9.dp else 0.dp,
                                    )
                                    .border(
                                        if (name == paper) 2.dp else .7.dp,
                                        if (name == paper) sage else edge,
                                        RoundedCornerShape(8.dp),
                                    )
                            ) {
                                if (name == paper)
                                    Box(Modifier.padding(3.dp)) {
                                        Glyph("check", "Selected", sage, 17)
                                    }
                                if (name == "Letter")
                                    Text(
                                        "Dear me,",
                                        Modifier.padding(7.dp),
                                        fontFamily = LocalDisplayFont.current,
                                        fontSize = uiSp(9),
                                    )
                                if (name == "Scrapbook") Glyph("image", tint = sage)
                            }
                            Text(name, fontSize = uiSp(12))
                        }
                    }
                }
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
            Triple("Line spacing", "spacing", listOf("Classic", "Close", "Airy")),
            Triple("Margins", "margin", listOf("Classic", "Narrow", "Wide")),
        )
        .forEach { (label, key, choices) ->
            Title(label, 18)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
    locations: List<DiaryLocation>,
    previous: (() -> Unit)?,
    next: (() -> Unit)?,
    back: () -> Unit,
) {
    var page by remember(initial.id) { mutableStateOf(initial) }
    var dragHintDismissed by rememberSaveable { mutableStateOf(false) }
    val past = remember { mutableStateListOf<Pair<String, String>>() }
    val future = remember { mutableStateListOf<Pair<String, String>>() }
    fun dismissDragHint() {
        if (dragHintDismissed || settings["attachmentDragHintSeen"] == "true") return
        dragHintDismissed = true
        book.setting("attachmentDragHintSeen", "true")
    }
    fun change(next: Page, record: Boolean = false) {
        if (record) {
            past.add(page.title to page.body)
            if (past.size > 60) past.removeAt(0)
            future.clear()
        }
        if (page.media.isNotBlank() && (next.title != page.title || next.body != page.body)) {
            dismissDragHint()
        }
        page = next
        book.save(next)
    }
    var sheet by rememberSaveable { mutableStateOf("") }
    var viewing by rememberSaveable { mutableStateOf("") }
    var removing by rememberSaveable { mutableStateOf("") }
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
    val paper = page.paper.ifBlank { settings["paper"] ?: "Ruled" }
    val color = swatches.getOrElse(settings["tone"]?.toIntOrNull() ?: 0) { ivory }
    val bodySize = writingSize(settings)
    val titleSize = (bodySize + 6).sp
    val line =
        when (settings["spacing"]) {
            "Close" -> 27
            "Airy" -> 38
            else -> 32
        }.let { (it * bodySize / 20f).sp }
    val density = LocalDensity.current
    var measured by remember(line, density) { mutableFloatStateOf(0f) }
    val interval = with(density) { if (measured > 0f) measured.toDp() else line.toDp() }
    val margin =
        when (settings["margin"]) {
            "Narrow" -> 16
            "Wide" -> 40
            else -> 25
        }.dp
    val font = notebookFont(book.context, settings["font"])
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
            if (previous != null || next != null) {
                Box(Modifier.graphicsLayer { scaleX = -1f }) {
                    Action("chevron-right", "Previous page", previous != null && !importing) {
                        previous?.invoke()
                    }
                }
                Action("chevron-right", "Next page", next != null && !importing) { next?.invoke() }
            }
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
            val scroll = rememberScrollState()
            Column(
                Modifier.verticalScroll(scroll)
                    .fillMaxWidth()
                    .heightIn(min = minimum)
                    .paper(
                        paper,
                        color,
                        interval,
                        margin,
                        bodyY + baseline + with(density) { 2.dp.toPx() },
                    )
            ) {
                PageMediaSurface(
                    page,
                    book,
                    minimum,
                    scroll,
                    showDragHint = !dragHintDismissed && settings["attachmentDragHintSeen"] != "true",
                    view = { viewing = it },
                    place = { name, position ->
                        if (position != null) dismissDragHint()
                        change(page.copy(mediaPositions = page.withMediaPosition(name, position)))
                    },
                ) {
                    Column(Modifier.padding(start = margin + 12.dp, end = 18.dp, top = 18.dp)) {
                        Note(
                            stamp(page.created, "yMMMEd") +
                                "  ·  " +
                                android.text.format.DateFormat.getTimeFormat(book.context)
                                    .format(java.util.Date(page.created))
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                page.title,
                                { change(page.copy(title = it), true) },
                                Modifier.width(IntrinsicSize.Max)
                                    .widthIn(min = 130.dp, max = 245.dp)
                                    .weight(1f, fill = false)
                                    .semantics { contentDescription = "Page title" },
                                textStyle =
                                    TextStyle(
                                        fontSynthesis = FontSynthesis.None,
                                        fontFamily = font,
                                        fontSize = titleSize,
                                        color = ink,
                                    ),
                                singleLine = true,
                                cursorBrush = SolidColor(sage),
                                decorationBox = { inner ->
                                    Box {
                                        if (page.title.isEmpty())
                                            Text(
                                                "Untitled page",
                                                maxLines = 1,
                                                color = muted,
                                                fontSize = titleSize,
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
                            Tag(
                                when (locations.size) {
                                    0 -> "Places"
                                    1 -> locations.first().label()
                                    else -> "${locations.size} places"
                                },
                                icon = "map-pin",
                            ) {
                                tools("Place")
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    BasicTextField(
                        page.body,
                        { change(page.copy(body = it), true) },
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { bodyY = it.positionInParent().y }
                            .padding(start = margin + 10.dp, end = 18.dp)
                            .heightIn(min = interval * 8)
                            .semantics { contentDescription = "Page body" },
                        textStyle =
                            TextStyle(
                                fontFamily = font,
                                fontSynthesis = FontSynthesis.None,
                                fontSize = bodySize.sp,
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
                            measured =
                                if (it.lineCount > 1) it.getLineBaseline(1) - it.getLineBaseline(0)
                                else 0f
                        },
                        cursorBrush = SolidColor(sage),
                        decorationBox = { inner ->
                            Box {
                                if (page.body.isEmpty())
                                    Text(
                                        "Begin your page…",
                                        color = muted,
                                        fontFamily = font,
                                        fontSize = bodySize.sp,
                                        lineHeight = line,
                                    )
                                inner()
                            }
                        },
                    )
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
            Action("image", "Attach photo or video", !importing) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            Action("notebook", "Template and chapter") { tools("Organize page") }
        }
    }
    if (sheet == "Place") LocationNotebook(page, book, locations) { sheet = "" }
    else if (sheet.isNotEmpty())
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
                    "Appearance" -> {
                        FontOptions(book, settings, includeDisplay = false)
                        Appearance(
                            settings,
                            paper,
                            { change(page.copy(paper = it)) },
                            book::setting,
                        )
                    }
                    "Mood" ->
                        (moods + "Clear mood").forEach {
                            Tag(it, it == page.mood) {
                                change(page.copy(mood = if (it == "Clear mood") "" else it))
                                sheet = ""
                            }
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
                                        fontFamily = LocalDisplayFont.current,
                                        textAlign = TextAlign.Center,
                                        lineHeight = uiSp(18),
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
                        Tag("Delete page") {
                            sheet = ""
                            error = "Delete this page?"
                        }
                    }
                }
            }
        }
    if (viewing.isNotBlank() && viewing in page.files())
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        removing = viewing
                        viewing = ""
                    }) { Text("Remove attachment", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { viewing = "" }) { Text("Close") }
                }
            }
        }
    if (removing.isNotBlank())
        AlertDialog(
            onDismissRequest = { removing = "" },
            title = { Title("Remove this attachment?") },
            text = { Text("This removes the notebook’s copy and any links to it from places on this page.") },
            confirmButton = {
                TextButton(onClick = {
                    val filename = removing
                    removing = ""
                    if (filename in page.files()) {
                        val updated = page.copy(
                            media = page.files().filter { it != filename }.joinToString("\n"),
                            mediaPositions = page.withMediaPosition(filename, null),
                        )
                        page = updated
                        book.queue {
                            book.dao.saveAfterRemovingAttachment(updated, filename)
                            File(mediaDir(book), filename).delete()
                        }
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = "" }) { Text("Cancel") } },
        )
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
