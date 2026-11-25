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
    prefs: Map<String, String> = emptyMap(),
) {
    var page by mutableStateOf(initial)
        private set

    var layout by
        mutableStateOf(
            DiaryLayout.read(initial).let { art ->
                DiaryPen.parse(prefs["lastPen"])?.applyTo(art) ?: art
            }
        )
        private set

    var status by mutableStateOf(if (initial.draft) "Draft" else "Saved")
        private set

    var locked by mutableStateOf(false)
    var error by mutableStateOf("")
    val past = mutableStateListOf<Pair<Page, DiaryLayout>>()
    val future = mutableStateListOf<Pair<Page, DiaryLayout>>()
    val favoritePens =
        mutableStateListOf<DiaryPen>().apply {
            addAll(prefs["favoritePens"].orEmpty().split("|").mapNotNull(DiaryPen::parse))
        }
    var fingerInk by mutableStateOf(prefs["fingerInk"] == "true")
    var clipboard: List<InkLine>
        get() = book.inkClipboard.value
        set(value) {
            book.inkClipboard.value = value
        }

    var rawInput: android.view.MotionEvent? = null
        private set

    fun observeInput(event: android.view.MotionEvent?) {
        rawInput?.recycle()
        rawInput = event?.let(android.view.MotionEvent::obtain)
    }

    fun broadContact(density: Float) =
        rawInput?.let { event ->
            (0 until event.pointerCount).any {
                event.getToolType(it) == android.view.MotionEvent.TOOL_TYPE_FINGER &&
                    event.getTouchMajor(it) > 30f * density
            }
        } ?: false

    var fingerOwner: (() -> Unit)? = null
    var photoGesture = false
    var finishInput: (() -> Unit)? = null
    // Ignore late IME acknowledgements from a sheet replaced by navigation, undo or deletion.
    var textGeneration by mutableIntStateOf(0)
    var sheets: List<TextSlice> = emptyList()
    var span by mutableIntStateOf(1)
    var savedCount = 1

    fun persistedLayout(art: DiaryLayout = layout) =
        art.copy(
            sheet = art.sheet.coerceAtMost(art.contentCount(maxOf(sheets.size, savedCount)) - 1)
        )

    fun turnTo(index: Int) {
        flushInput()
        textGeneration++
        val next = layout.copy(sheet = index)
        if (index >= savedCount) layout = next else update(art = next, record = false)
    }

    fun flushInput() {
        finishInput?.invoke()
    }

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
        val hasEdits =
            next.body != page.body || art.lines !== layout.lines || art.prints !== layout.prints
        val stored =
            if (record && hasEdits && art.sheetIds === layout.sheetIds)
                art.withSheetCount(maxOf(art.contentCount(sheets.size), art.sheet + 1))
            else art
        layout = stored
        page = next.copy(draft = edited || next.draft)
        val snapshot = page
        val document = persistedLayout(stored)
        val ticket = ++revision
        val save = book.persist { snapshot.copy(notebookLayout = document.encode()) }
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
        val snapshot = page.copy(draft = true, notebookLayout = persistedLayout().encode())
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
        flushInput()
        val from = if (redo) future else past
        val to = if (redo) past else future
        if (locked || from.isEmpty()) return
        textGeneration++
        to.add(page to layout)
        val (old, art) = from.removeAt(from.lastIndex)
        update(
            old,
            layout
                .pen()
                .applyTo(art)
                .copy(
                    mode = layout.mode,
                    areaEraser = layout.areaEraser,
                    eraserSize = layout.eraserSize,
                ),
            record = false,
            edited = true,
        )
    }

    fun leave(complete: Boolean, destination: (Page) -> Unit) {
        flushInput()
        if (locked) return
        locked = true
        ++revision
        val final =
            page.copy(
                draft = if (complete) false else page.draft,
                notebookLayout = persistedLayout().encode(),
            )
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
private val koreanTools =
    listOf("글쓰기", "펜", "지우개", "미디어", "기분", "날씨", "장소", "실행 취소", "다시 실행", "더보기")

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
    val session = remember(initial.id) { WritingSession(initial, book, scope, prefs) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) session.flushInput()
            }
        lifecycle.addObserver(observer)
        onDispose {
            session.flushInput()
            session.observeInput(null)
            lifecycle.removeObserver(observer)
        }
    }
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
        session.update(art = session.layout.copy(mode = name), record = false)
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
    // Keep a confirmation tied to the exact sheets the user saw. A recreated viewport
    // dismisses it, since reflow can move a paragraph across a numbered boundary.
    var deletion by remember { mutableStateOf<SheetRemoval?>(null) }
    fun deleteSheet(request: SheetRemoval) {
        session.flushInput()
        deletion = null
        if (session.page != request.page || session.layout != request.layout) return
        val index = request.index
        val current = request.layout
        if (index >= current.contentCount(request.slices.size)) {
            session.turnTo(((index / session.span - 1) * session.span).coerceAtLeast(0))
        } else {
            val linked =
                locations.filter { it.pageId == page.id }.flatMap { it.media.lines() }.toSet()
            val (next, layout) = removeSheet(request.page, current, request.slices, index, linked)
            session.textGeneration++
            session.update(next, layout)
        }
        selected = ""
    }

    fun requestDeletion(index: Int) {
        session.flushInput()
        if (session.sheets.isEmpty()) return
        val request = SheetRemoval(index, session.page, session.layout, session.sheets)
        if (hasSheetContent(request.page, request.layout, request.slices, index)) deletion = request
        else deleteSheet(request)
    }

    fun addPage() {
        session.flushInput()
        session.textGeneration++
        focus.clearFocus()
        val body =
            page.body +
                "\u000c"
                    .repeat(
                        (maxOf(pageCount, session.layout.sheet + 1) - textPages + 1).coerceAtLeast(
                            1
                        )
                    )
        session.update(
            page.copy(body = body),
            session.layout.copy(
                sheet = maxOf(pageCount, session.layout.sheet + 1),
                cursor = body.length,
            ),
        )
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
                                    session.flushInput()
                                    panel = ""
                                    when (name) {
                                        "Type" -> mode(name)
                                        "Pen",
                                        "Eraser" -> {
                                            if (session.layout.mode == name) panel = name
                                            else mode(name)
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
                                "Pen" -> PenPanel(session)
                                "Eraser" ->
                                    Column(Modifier.width(245.dp).padding(10.dp)) {
                                        listOf(false, true).forEach { area ->
                                            val label =
                                                if (area) wording("Area eraser", "영역 지우개")
                                                else wording("Stroke eraser", "획 지우개")
                                            TextButton(
                                                onClick = {
                                                    session.update(
                                                        art =
                                                            session.layout.copy(areaEraser = area),
                                                        record = false,
                                                    )
                                                }
                                            ) {
                                                Text(
                                                    (if (area == art.areaEraser) "✓ " else "") +
                                                        label
                                                )
                                            }
                                        }
                                        var diameter by remember {
                                            mutableFloatStateOf(art.eraserSize)
                                        }
                                        PenSlider(
                                            wording("Diameter", "지름"),
                                            diameter,
                                            4f..72f,
                                            { diameter = it },
                                        ) {
                                            session.update(
                                                art = session.layout.copy(eraserSize = diameter),
                                                record = false,
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                panel = ""
                                                dialog = "Clear ink"
                                            }
                                        ) {
                                            Text(wording("Clear page handwriting", "필기 전체 지우기"))
                                        }
                                    }
                                "Mood",
                                "Weather" -> {
                                    val mood = name == "Mood"
                                    val values =
                                        if (mood) moods else weatherIllustrations.map { it.code }
                                    val symbols = listOf("😊", "🙂", "😕", "😔", "😢")
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
                                                    if (mood) Text(symbols[n], fontSize = 21.sp)
                                                    else
                                                        Image(
                                                            androidx.compose.ui.res.painterResource(
                                                                weatherFor(v).icon
                                                            ),
                                                            null,
                                                            Modifier.size(28.dp),
                                                        )
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
                                                            else weatherFor(v).korean,
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
                                    DropdownMenuItem(
                                        text = { Text(wording("Lasso selection", "올가미 선택")) },
                                        onClick = {
                                            panel = ""
                                            mode("Lasso")
                                        },
                                    )
                                    val first = art.sheet / session.span * session.span
                                    repeat(session.span) { side ->
                                        val index = first + side
                                        val label =
                                            when {
                                                session.span == 1 ->
                                                    wording("Delete page", "페이지 삭제")
                                                side == 0 ->
                                                    wording("Delete left page", "왼쪽 페이지 삭제")
                                                else -> wording("Delete right page", "오른쪽 페이지 삭제")
                                            }
                                        DropdownMenuItem(
                                            text = { Text("$label · ${index+1}") },
                                            onClick = {
                                                panel = ""
                                                focus.clearFocus()
                                                requestDeletion(index)
                                            },
                                        )
                                    }
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
    val requested = deletion
    if (requested != null) {
        val index = requested.index
        AlertDialog(
            onDismissRequest = { deletion = null },
            containerColor = ivory,
            title = { Text(wording("Delete page ${index+1}?", "${index+1}쪽을 삭제할까요?")) },
            text = {
                Text(
                    wording(
                        "This removes this page’s text, handwriting and attachments.",
                        "이 페이지의 글, 필기와 첨부물을 삭제합니다.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteSheet(requested) }) { Text(wording("Delete", "삭제")) }
            },
            dismissButton = {
                TextButton(onClick = { deletion = null }) { Text(wording("Cancel", "취소")) }
            },
        )
    } else if (dialog == "Clear ink") {
        val index = art.sheet
        AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = ivory,
            title = {
                Text(wording("Clear handwriting on page ${index+1}?", "${index+1}쪽의 필기를 모두 지울까요?"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        session.update(
                            art = art.copy(lines = art.lines.filterNot { it.sheet == index })
                        )
                        dialog = ""
                    }
                ) {
                    Text(wording("Clear", "지우기"))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = "" }) { Text(wording("Cancel", "취소")) }
            },
        )
    } else if (dialog == "Locations")
        LocationNotebook(
            page.copy(notebookLayout = session.persistedLayout().encode()),
            book,
            locations.filter { it.pageId == page.id },
        ) {
            dialog = ""
        }
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
