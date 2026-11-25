package app.pageheld

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.*

internal class WritingSession(
    initial: Page,
    val book: Notebook,
    private val scope: CoroutineScope,
) {
    var page by mutableStateOf(initial)
        private set

    var layout by mutableStateOf(DiaryLayout.read(initial))
        private set

    var status by mutableStateOf(if (initial.draft) "Draft" else "Saved")
        private set

    var locked by mutableStateOf(false)
    var error by mutableStateOf("")
    val past = mutableStateListOf<Pair<Page, DiaryLayout>>()
    val future = mutableStateListOf<Pair<Page, DiaryLayout>>()
    private var revision = 0L

    fun update(
        next: Page = page,
        art: DiaryLayout = layout,
        record: Boolean = true,
        edited: Boolean = record,
    ) {
        if (locked || (next == page && art == layout)) return
        if (record) {
            past.add(page to layout)
            if (past.size > 80) past.removeAt(0)
            future.clear()
        }
        layout = art
        page = next.copy(notebookLayout = art.encode(), draft = edited || next.draft)
        val ticket = ++revision
        val save = book.persist(page)
        status = "Saving…"
        scope.launch {
            try {
                save.await()
                if (ticket == revision) status = if (page.draft) "Draft saved" else "Saved"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (ticket == revision) status = "Save failed"
            }
        }
    }

    fun addPlace(place: DiaryLocation) {
        if (locked) return
        locked = true
        val snapshot = page.copy(draft = true, notebookLayout = layout.encode())
        val result = CompletableDeferred<Unit>()
        ++revision
        status = "Saving…"
        book.queue {
            try {
                book.db.withTransaction {
                    book.dao.save(snapshot)
                    book.dao.saveLocation(snapshot, place)
                }
                result.complete(Unit)
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }
        scope.launch {
            try {
                result.await()
                page = snapshot
                status = "Draft saved"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                status = "Save failed"
                error = "Couldn’t save the place. Please try again."
            } finally {
                locked = false
            }
        }
    }

    fun undo(redo: Boolean) {
        val from = if (redo) future else past
        val to = if (redo) past else future
        if (locked || from.isEmpty()) return
        to.add(page to layout)
        val (old, art) = from.removeAt(from.lastIndex)
        update(old, art, record = false, edited = true)
    }

    fun leave(complete: Boolean, destination: (Page) -> Unit) {
        if (locked) return
        locked = true
        ++revision
        val final =
            page.copy(draft = if (complete) false else page.draft, notebookLayout = layout.encode())
        val save = book.persist(final)
        status = "Saving…"
        scope.launch {
            try {
                save.await()
                page = final
                destination(final)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                status = "Save failed"
                error =
                    "Couldn’t save this entry. Your edits are still here. Check storage and try again."
            } finally {
                locked = false
            }
        }
    }
}

private val toolNames =
    listOf("Type", "Pen", "Eraser", "Photo", "Mood", "Weather", "Place", "Undo", "Redo", "More")
private val toolIcons =
    listOf(
        "type",
        "pencil",
        "eraser",
        "image",
        "smile",
        "sun",
        "map-pin",
        "undo-2",
        "redo-2",
        "ellipsis-vertical",
    )
private val koreanTools = listOf("글쓰기", "펜", "지우개", "사진", "기분", "날씨", "장소", "실행 취소", "다시 실행", "더보기")
private val inkColors =
    listOf(0xff434736, 0xffab7955, 0xffe8977b, 0xffe7b83d, 0xff819a63, 0xffa095cb, 0xff7f9ec1).map {
        it.toInt()
    }

@Composable
internal fun DiaryEditor(
    initial: Page,
    book: Notebook,
    chapters: List<Chapter>,
    locations: List<DiaryLocation>,
    prefs: Map<String, String>,
    motion: Boolean,
    attachment: String,
    navigate: (String) -> Unit,
    close: (Page) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val session = remember(initial.id) { WritingSession(initial, book, scope) }
    val page = session.page
    val art = session.layout
    val savingLabel =
        when (session.status) {
            "Draft saved" -> wording("✓ Draft saved", "✓ 임시저장됨")
            "Saved" -> wording("✓ Saved", "✓ 저장됨")
            "Saving…" -> wording("Saving…", "저장 중…")
            "Save failed" -> wording("Save failed", "저장 실패")
            else -> wording("Draft", "초안")
        }
    var panel by remember { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf("") }
    var viewing by rememberSaveable { mutableStateOf(attachment) }
    var selected by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(1) }
    var textPages by remember { mutableIntStateOf(1) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun leave(complete: Boolean = false, dest: (Page) -> Unit = close) {
        keyboard?.hide()
        focus.clearFocus()
        session.leave(complete, dest)
    }
    BackHandler { if (panel.isNotBlank()) panel = "" else leave() }
    fun mode(name: String) {
        focus.clearFocus()
        keyboard?.hide()
        session.update(art = art.copy(mode = name), record = false)
        if (name != "Photo") selected = ""
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null)
                scope.launch {
                    session.locked = true
                    val resolver = book.context.contentResolver
                    val sheet = session.layout.sheet
                    try {
                        val file =
                            withContext(Dispatchers.IO) {
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
                        session.locked = false
                        val layout = session.layout
                        session.update(
                            session.page.copy(
                                media = (session.page.files() + file.name).joinToString("\n")
                            ),
                            layout.copy(
                                mode = "Photo",
                                prints = layout.prints + (file.name to PrintPosition(sheet)),
                            ),
                        )
                        selected = file.name
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        session.error = "Couldn’t import the attachment."
                    } finally {
                        session.locked = false
                    }
                }
        }
    fun addPage() {
        focus.clearFocus()
        val body = page.body + "\u000c".repeat((pageCount - textPages + 1).coerceAtLeast(1))
        session.update(page.copy(body = body), art.copy(sheet = pageCount, cursor = body.length))
    }
    Column(Modifier.fillMaxSize().background(ivory)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
            val compact = maxWidth < 590.dp
            Row(
                Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Action("arrow-left", "Back to chapter", !session.locked) { leave() }
                Box {
                    Action("menu", "Navigate", !session.locked) { menu = true }
                    DropdownMenu(menu, { menu = false }, containerColor = ivory) {
                        tabs.forEach { (tab, _) ->
                            DropdownMenuItem(
                                text = { Text(tab) },
                                onClick = {
                                    menu = false
                                    leave { if (tab == "Diaries") close(it) else navigate(tab) }
                                },
                            )
                        }
                    }
                }
            }
            Text(
                page.title.ifBlank {
                    displayStamp(page.created, if (compact) "MMMd" else "yMMMEd")
                },
                Modifier.align(Alignment.Center).widthIn(max = maxWidth * .36f).clickable {
                    dialog = "Title"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = uiSp(if (compact) 17 else 22),
            )
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!compact)
                    Text(
                        savingLabel,
                        Modifier.padding(end = 10.dp),
                        fontSize = uiSp(14),
                        color =
                            if (session.status == "Save failed") MaterialTheme.colorScheme.error
                            else sage,
                    )
                Button(
                    onClick = { leave(true) },
                    enabled = !session.locked,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(wording("Done", "완료"))
                }
            }
        }
        if (
            with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } <
                590.dp
        )
            Note(savingLabel, Modifier.padding(start = 12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val toolWidth = ((maxWidth - 28.dp) / 10).coerceIn(54.dp, 160.dp)
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 3.dp)
                    .clip(round)
                    .border(1.dp, edge, round)
                    .background(Color(0xfffaf5ed))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val width = toolWidth
                toolNames.forEachIndexed { i, name ->
                    Box {
                        val active = art.mode == name || panel == name
                        Column(
                            Modifier.width(width)
                                .height(46.dp)
                                .clip(round)
                                .background(if (active) pale else Color.Transparent)
                                .clickable(
                                    enabled =
                                        !session.locked &&
                                            (name != "Undo" || session.past.isNotEmpty()) &&
                                            (name != "Redo" || session.future.isNotEmpty())
                                ) {
                                    panel = ""
                                    when (name) {
                                        "Type",
                                        "Eraser" -> mode(name)
                                        "Pen" -> {
                                            mode(name)
                                            panel = name
                                        }
                                        "Photo" -> {
                                            mode(name)
                                            panel = name
                                        }
                                        "Undo",
                                        "Redo" -> session.undo(name == "Redo")
                                        else -> {
                                            keyboard?.hide()
                                            panel = name
                                        }
                                    }
                                }
                                .semantics { contentDescription = name },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Glyph(toolIcons[i], size = 18)
                            Text(wording(name, koreanTools[i]), fontSize = uiSp(13), maxLines = 1)
                        }
                        DropdownMenu(
                            panel == name,
                            { panel = "" },
                            containerColor = ivory,
                            modifier = Modifier.widthIn(max = 320.dp),
                        ) {
                            when (name) {
                                "Pen" ->
                                    Column(Modifier.width(270.dp).padding(12.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("Fine", "Fountain", "Marker", "Highlight")
                                                .forEachIndexed { index, label ->
                                                    Column(
                                                        Modifier.weight(1f)
                                                            .clip(round)
                                                            .background(
                                                                if (index == art.nib) pale
                                                                else ivory
                                                            )
                                                            .clickable {
                                                                session.update(
                                                                    art = art.copy(nib = index),
                                                                    record = false,
                                                                )
                                                            }
                                                            .padding(3.dp),
                                                        horizontalAlignment =
                                                            Alignment.CenterHorizontally,
                                                    ) {
                                                        PenTip(index)
                                                        Text(
                                                            wording(
                                                                label,
                                                                listOf("가는 펜", "만년필", "마커", "형광펜")[
                                                                    index],
                                                            ),
                                                            Modifier.semantics {
                                                                contentDescription = label
                                                            },
                                                            fontSize = uiSp(12),
                                                        )
                                                    }
                                                }
                                        }
                                        Slider(
                                            art.thickness,
                                            {
                                                session.update(
                                                    art = session.layout.copy(thickness = it),
                                                    record = false,
                                                )
                                            },
                                            valueRange = 1f..12f,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            inkColors.forEachIndexed { i, c ->
                                                Box(
                                                    Modifier.size(29.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(c))
                                                        .border(
                                                            if (c == art.color) 2.dp else .5.dp,
                                                            if (c == art.color) ink else edge,
                                                            CircleShape,
                                                        )
                                                        .clickable {
                                                            session.update(
                                                                art =
                                                                    session.layout.copy(color = c),
                                                                record = false,
                                                            )
                                                        }
                                                        .semantics {
                                                            contentDescription = "Ink color ${i+1}"
                                                        }
                                                )
                                            }
                                        }
                                    }
                                "Mood",
                                "Weather" -> {
                                    val mood = name == "Mood"
                                    val values = if (mood) moods else diaryWeather
                                    val symbols =
                                        if (mood) listOf("😊", "🙂", "😕", "😔", "😢")
                                        else
                                            listOf(
                                                "☀️",
                                                "🌤️",
                                                "☁️",
                                                "🌧️",
                                                "⛈️",
                                                "❄️",
                                                "🌫️",
                                                "🍃",
                                            )
                                    Column(Modifier.width(236.dp).padding(8.dp)) {
                                        Text(wording(name, koreanTools[i]), fontSize = uiSp(17))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            values.forEachIndexed { n, v ->
                                                Column(
                                                    Modifier.width(69.dp)
                                                        .clip(round)
                                                        .background(
                                                            if (
                                                                v ==
                                                                    if (mood) page.mood
                                                                    else page.weather
                                                            )
                                                                pale
                                                            else ivory
                                                        )
                                                        .clickable {
                                                            session.update(
                                                                if (mood) page.copy(mood = v)
                                                                else page.copy(weather = v)
                                                            )
                                                            panel = ""
                                                        }
                                                        .padding(vertical = 5.dp),
                                                    horizontalAlignment =
                                                        Alignment.CenterHorizontally,
                                                ) {
                                                    Text(symbols[n], fontSize = 21.sp)
                                                    Text(
                                                        wording(
                                                            v,
                                                            if (mood)
                                                                listOf(
                                                                    "기쁨",
                                                                    "편안",
                                                                    "불안",
                                                                    "울적",
                                                                    "슬픔",
                                                                )[n]
                                                            else
                                                                listOf(
                                                                    "맑음",
                                                                    "구름 조금",
                                                                    "흐림",
                                                                    "비",
                                                                    "뇌우",
                                                                    "눈",
                                                                    "안개",
                                                                    "바람",
                                                                )[n],
                                                        ),
                                                        Modifier.semantics {
                                                            contentDescription = v
                                                        },
                                                        fontSize = uiSp(12),
                                                    )
                                                }
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                session.update(
                                                    if (mood) page.copy(mood = "")
                                                    else page.copy(weather = "")
                                                )
                                                panel = ""
                                            }
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }
                                "Place" ->
                                    Column(Modifier.width(264.dp).padding(10.dp)) {
                                        fun attach(place: DiaryLocation) {
                                            session.addPlace(
                                                place.copy(id = uid(), pageId = page.id)
                                            )
                                            panel = ""
                                        }
                                        PlaceSearch(book) {
                                            attach(
                                                DiaryLocation(
                                                    pageId = page.id,
                                                    position = 0,
                                                    name = it.name,
                                                    lat = it.lat,
                                                    lon = it.lon,
                                                )
                                            )
                                        }
                                        locations
                                            .distinctBy { Triple(it.name, it.lat, it.lon) }
                                            .take(5)
                                            .forEach { p ->
                                                TextButton(
                                                    onClick = { attach(p.copy(media = "")) }
                                                ) {
                                                    Glyph("map-pin", size = 16)
                                                    Text(p.label())
                                                }
                                            }
                                        TextButton(
                                            onClick = {
                                                panel = ""
                                                dialog = "Locations"
                                            }
                                        ) {
                                            Text(
                                                wording(
                                                    "Manage places · GPS & map",
                                                    "장소 관리 · GPS와 지도",
                                                ),
                                                Modifier.semantics {
                                                    contentDescription = "Manage places · GPS & map"
                                                },
                                            )
                                        }
                                    }
                                "Photo" -> {
                                    DropdownMenuItem(
                                        text = { Text("Add photo or video") },
                                        onClick = {
                                            panel = ""
                                            picker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia
                                                        .ImageAndVideo
                                                )
                                            )
                                        },
                                    )
                                    if (selected.isNotEmpty())
                                        DropdownMenuItem(
                                            text = { Text("View attachment") },
                                            onClick = {
                                                panel = ""
                                                viewing = selected
                                            },
                                        )
                                }
                                "More" -> {
                                    listOf(
                                            "Add page",
                                            "Title",
                                            "Template & chapter",
                                            "Appearance",
                                            if (page.starred) "Remove bookmark" else "Bookmark",
                                            "Delete entry",
                                        )
                                        .forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option) },
                                                onClick = {
                                                    panel = ""
                                                    when (option) {
                                                        "Add page" -> addPage()
                                                        "Bookmark",
                                                        "Remove bookmark" ->
                                                            session.update(
                                                                page.copy(starred = !page.starred)
                                                            )
                                                        else -> dialog = option
                                                    }
                                                },
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
        }
        WritingSpread(
            session,
            prefs,
            motion,
            selected,
            { selected = it },
            { viewing = it },
            ::addPage,
        ) { visible, written ->
            pageCount = visible
            textPages = written
        }
    }
    if (dialog == "Locations")
        LocationNotebook(page, book, locations.filter { it.pageId == page.id }) { dialog = "" }
    else if (dialog.isNotEmpty())
        EditorDialog(dialog, session, chapters, prefs, { dialog = "" }, close)
    if (viewing.isNotEmpty())
        AttachmentPreview(session, viewing) {
            viewing = ""
            selected = ""
        }
    if (session.error.isNotEmpty())
        AlertDialog(
            onDismissRequest = { session.error = "" },
            text = { Text(session.error) },
            confirmButton = { TextButton(onClick = { session.error = "" }) { Text("OK") } },
        )
}

@Composable
private fun PenTip(index: Int) {
    Canvas(Modifier.size(27.dp)) {
        val w = size.width
        val h = size.height
        val half = listOf(.12f, .18f, .15f, .22f)[index] * w
        val body =
            Path().apply {
                moveTo(w * .5f - half, h)
                lineTo(w * .5f - half, h * .60f)
                lineTo(w * .5f - half * .58f, h * .48f)
                lineTo(w * .49f, h * .12f)
                lineTo(w * .5f + half * .58f, h * .48f)
                lineTo(w * .5f + half, h * .60f)
                lineTo(w * .5f + half, h)
                close()
            }
        drawPath(
            body,
            listOf(Color(0xff93a177), Color(0xffa0866b), Color(0xffd6ae72), Color(0xffe8c365))[
                index],
        )
        drawPath(body, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(.9.dp.toPx()))
        drawLine(
            ink,
            androidx.compose.ui.geometry.Offset(w * .5f - half, h * .61f),
            androidx.compose.ui.geometry.Offset(w * .5f + half, h * .61f),
            .8.dp.toPx(),
        )
        drawLine(
            ink,
            androidx.compose.ui.geometry.Offset(w * .49f, h * .14f),
            androidx.compose.ui.geometry.Offset(w * .50f, h * .43f),
            .7.dp.toPx(),
        )
    }
}
