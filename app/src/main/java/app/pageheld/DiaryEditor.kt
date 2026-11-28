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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.LocalActivity
import java.io.File
import kotlinx.coroutines.*

internal class WritingSession(
    val initial: Page,
    val book: Notebook,
    private val scope: CoroutineScope,
    prefs: Map<String, String> = emptyMap(),
    private val initialPlaces: List<DiaryLocation> = emptyList(),
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

    var places by mutableStateOf(initialPlaces)
        private set
    private val imported = mutableSetOf<String>()
    val hasUnsavedChanges: Boolean
        get() = page != initial || layout.lines != originalLayout.lines ||
            layout.decorations != originalLayout.decorations || layout.prints != originalLayout.prints || layout.sheetIds != originalLayout.sheetIds ||
            places != initialPlaces
    private val originalLayout = layout

    var completedPage by mutableStateOf<Page?>(null)
        private set

    var status by mutableStateOf("Saved")
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

    fun update(
        next: Page = page,
        art: DiaryLayout = layout,
        record: Boolean = true,
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
        page = next
        if (status == "Save failed") status = "Saved"
    }

    fun trackImport(name: String) {
        imported.add(name)
    }

    fun addPlace(place: DiaryLocation) {
        val index = places.indexOfFirst { it.id == place.id }
        places = if (index >= 0) places.toMutableList().apply {
            this[index] = place.copy(position = this[index].position)
        } else places + place.copy(position = (places.lastOrNull()?.position ?: -1) + 1)
    }

    fun movePlace(id: String, delta: Int) {
        val index = places.indexOfFirst { it.id == id }
        val other = index + delta
        if (index < 0 || other !in places.indices) return
        places = places.toMutableList().apply {
            val place = this[index]
            this[index] = this[other].copy(position = place.position)
            this[other] = place.copy(position = places[other].position)
        }
    }

    fun removePlace(id: String) {
        places = places.filterNot { it.id == id }
    }

    fun removeAttachment(name: String) {
        val next = page.copy(
            media = page.files().filterNot { it == name }.joinToString("\n"),
            mediaPositions = page.withMediaPosition(name, null),
        )
        places = places.map { place ->
            place.copy(media = place.media.lines().filterNot { it == name }.joinToString("\n"))
        }
        past.clear()
        future.clear()
        update(next, layout.copy(prints = layout.prints - name), record = false)
    }

    fun discardImports() {
        val files = imported.toSet()
        imported.clear()
        if (files.isNotEmpty()) book.queue { files.forEach { File(mediaDir(book), it).delete() } }
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
                    resizeHintSeen = layout.resizeHintSeen,
                ),
            record = false,
        )
    }

    fun leave(complete: Boolean, destination: (Page) -> Unit) {
        flushInput()
        if (locked) return
        if (!complete) {
            discardImports()
            destination(initial)
            return
        }
        locked = true
        val final = page.copy(draft = false, notebookLayout = persistedLayout().encode())
        val finalPlaces = places
        val retainedFiles = layout.decorations.values.flatten().filter { it.kind=="photo" }.map { it.asset }.toSet() + final.files().toSet() + finalPlaces.flatMap { it.media.lines() }
        val removedFiles = (initial.files().toSet() + originalLayout.photoDecorations() + imported) - retainedFiles
        val save = CompletableDeferred<Unit>()
        book.queue {
            try {
                book.db.withTransaction {
                    book.dao.save(final)
                    initialPlaces.filter { previous -> finalPlaces.none { it.id == previous.id } }
                        .forEach { book.dao.removeLocation(final.id, it.id) }
                    finalPlaces.forEach { book.dao.writeLocation(it) }
                }
                runCatching { book.releaseUnused(removedFiles) }
                save.complete(Unit)
            } catch (e: Exception) {
                save.completeExceptionally(e)
            }
        }
        status = "Saving…"
        scope.launch {
            try {
                save.await()
                imported.clear()
                page = final
                completedPage = final
                status = "Saved"
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

internal class WritingEditorModel : ViewModel() {
    var session: WritingSession? = null
        private set

    fun open(page: Page, book: Notebook, prefs: Map<String, String>, places: List<DiaryLocation>): WritingSession {
        session?.takeIf { it.initial.id == page.id && it.book === book }?.let { return it }
        clear()
        return WritingSession(page, book, viewModelScope, prefs, places).also { session = it }
    }

    fun clear() {
        session?.takeUnless { it.locked }?.discardImports()
        session = null
    }

    override fun onCleared() = clear()
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
    val host = LocalActivity.current as androidx.activity.ComponentActivity
    val model = remember(host) { ViewModelProvider(host)[WritingEditorModel::class.java] }
    val session = remember(initial.id, book) {
        model.open(initial, book, prefs, locations.filter { it.pageId == initial.id })
    }
    fun exit(saved: Page) {
        model.clear()
        close(saved)
    }
    val onExit by rememberUpdatedState<(Page) -> Unit>(::exit)
    LaunchedEffect(session.completedPage) {
        session.completedPage?.let(onExit)
    }
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
            "Saved" -> if (session.hasUnsavedChanges || page.draft)
                wording("Unsaved", "저장되지 않음") else wording("✓ Saved", "✓ 저장됨")
            "Saving…" -> wording("Saving…", "저장 중…")
            "Save failed" -> wording("Save failed", "저장 실패")
            else -> ""
        }
    val tablet = LocalTabletLibrary.current
    var panel by remember { mutableStateOf("") }
    var decorate by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var viewing by rememberSaveable { mutableStateOf(attachment) }
    var selected by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(1) }
    var textPages by remember { mutableIntStateOf(1) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var confirmExit by remember { mutableStateOf<((Page) -> Unit)?>(null) }
    fun leave(complete: Boolean = false, dest: (Page) -> Unit = ::exit) {
        if (session.locked) return
        keyboard?.hide()
        focus.clearFocus()
        session.flushInput()
        if (complete) session.leave(true) {}
        else if (session.hasUnsavedChanges) confirmExit = dest
        else session.leave(false, dest)
    }
    BackHandler { if (panel.isNotBlank()) panel = "" else leave() }
    fun mode(name: String) {
        focus.clearFocus()
        keyboard?.hide()
        session.update(art = session.layout.copy(mode = name), record = false)
        if (name != "Photo") selected = ""
    }
    var hintedPrint by remember(page.id) { mutableStateOf<String?>(null) }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(hintedPrint) {
        if (hintedPrint != null) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(5500, containsText = true) ?: 5500)
            hintedPrint = null
        }
    }
    fun attach(file: File, sheet: Int) {
        session.trackImport(file.name)
        val layout=session.layout
        session.update(session.page.copy(media=(session.page.files()+file.name).joinToString("\n")),
            layout.copy(mode="Photo",resizeHintSeen=true,prints=layout.prints+(file.name to PrintPosition(sheet))))
        selected=file.name
        hintedPrint=if(layout.resizeHintSeen) null else file.name
    }
    var cameraName by rememberSaveable { mutableStateOf("") }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if(cameraName.isNotEmpty()) {
            val file=File(mediaDir(book),cameraName)
            if(saved && file.length()>0) attach(file,session.layout.sheet) else file.delete()
        }
        cameraName=""
    }
    fun takePhoto() {
        val file=File(mediaDir(book),uid()+".photo")
        try {
            cameraName=file.name
            camera.launch(androidx.core.content.FileProvider.getUriForFile(book.context,"app.pageheld.camera",file))
        } catch(e:Exception) { cameraName=""; file.delete(); session.error="Camera is unavailable." }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null)
                scope.launch {
                    session.locked = true
                    val resolver = book.context.contentResolver
                    val sheet = session.layout.sheet
                    var copied: File? = null
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
                                copied = dest
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
                        attach(file,sheet)
                    } catch (e: Exception) {
                        copied?.delete()
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
                session.places.flatMap { it.media.lines() }.toSet()
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
    val header: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
            val compact = maxWidth < 590.dp
            val statusColor = if (session.status == "Save failed") MaterialTheme.colorScheme.error else sage
            Box(Modifier.align(Alignment.CenterStart)) {
                Action("arrow-left", "Back to chapter", !session.locked) { leave() }
            }
            Column(
                Modifier.align(Alignment.Center).padding(end = 18.dp)
                    .widthIn(max = (maxWidth - 144.dp).coerceAtLeast(44.dp)).clickable { dialog = "Title" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    page.title.ifBlank { displayStamp(page.created, if (compact) "MMMd" else "yMMMEd") },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = uiSp(if (compact) 16 else 22),
                    lineHeight = uiSp(if (compact) 19 else 26),
                )
                if (compact) Text(savingLabel, color = statusColor, fontSize = uiSp(11), lineHeight = uiSp(14), maxLines = 1)
            }
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!compact)
                    Text(
                        savingLabel,
                        Modifier.padding(end = 10.dp),
                        fontSize = uiSp(14),
                        color = statusColor,
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
    }
    val tools: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val toolWidth = if(tablet) 41.dp else ((maxWidth - 26.dp) / 10).coerceIn(48.dp, 160.dp)
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 3.dp)
                    .clip(round)
                    .border(1.dp, edge, round)
                    .background(notebookBackground)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val width = toolWidth
                (if(tablet) listOf("Pen","Type","Eraser","Lasso","Photo","Camera","Video","Stickers","Place","Undo","Redo","Pages","More","Mood","Weather") else toolNames).forEachIndexed { i, name ->
                    Box {
                        val active = art.mode == name || panel == name
                        Column(
                            Modifier.width(width)
                                .height(if(tablet) 40.dp else 46.dp)
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
                                        "Stickers" -> { mode("Decorate"); decorate=true }
                                        "Camera" -> takePhoto()
                                        "Video" -> picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                        "Pages" -> dialog="Page navigation"
                                        "Type","Lasso" -> mode(name)
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
                            val toolIndex=toolNames.indexOf(name)
                            Glyph(toolIcons.getOrNull(toolIndex) ?: if(name=="Camera") "camera" else if(name=="Video") "video" else if(name=="Pages") "book-open" else if(name=="Lasso") "layers" else "leaf", size = 18)
                            if(!tablet) Text(wording(name, koreanTools.getOrNull(toolIndex) ?: when(name) {"Camera"->"카메라";"Video"->"비디오";"Pages"->"페이지";"Lasso"->"올가미";else->"스티커"}), fontSize = uiSp(13), maxLines = 1)
                        }
                        DropdownMenu(
                            panel == name,
                            { panel = "" },
                            containerColor = notebookBackground,
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
                                        if(!tablet) Text(wording(name, koreanTools[i]), fontSize = uiSp(17))
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
                                        text = { Text(wording("Navigate", "이동")) },
                                        onClick = { panel = ""; menu = true },
                                    )
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
    }
    Column(Modifier.fillMaxSize().background(notebookBackground)) {
        if(tablet) NavigationHeader("Library") { tab -> leave { model.clear(); navigate(tab) } }
        EditorControls(header, tools)
        WritingSpread(
            session,
            prefs,
            motion,
            selected,
            hintedPrint != null && hintedPrint == selected && art.mode == "Photo",
            { selected = it; hintedPrint = null },
            { viewing = it },
            ::addPage,
        ) { visible, written ->
            pageCount = visible
            textPages = written
        }
    }
    if(decorate) PageDecorationPanel(session) { decorate=false }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            containerColor = notebookBackground,
            title = { Title(wording("Navigate", "이동")) },
            text = {
                Column {
                    tabs.forEach { (tab, _) ->
                        TextButton(onClick = {
                            menu = false
                            leave { if (tab == "Diaries") exit(it) else { model.clear(); navigate(tab) } }
                        }) { Text(tab) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text(wording("Close", "닫기")) } },
        )
    }
    confirmExit?.let { destination ->
        AlertDialog(
            onDismissRequest = { confirmExit = null },
            title = { Text(wording("Discard unsaved changes?", "저장하지 않은 변경사항을 버릴까요?")) },
            text = { Text(wording("Choose Done in the editor to save this diary entry.", "일기를 저장하려면 편집 화면에서 완료를 누르세요.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = null
                    session.leave(false, destination)
                }) { Text(wording("Discard", "버리기")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = null }) { Text(wording("Keep editing", "계속 편집")) }
            },
        )
    }
    val requested = deletion
    if (requested != null) {
        val index = requested.index
        AlertDialog(
            onDismissRequest = { deletion = null },
            containerColor = notebookBackground,
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
    } else if (dialog == "Page navigation") {
        AlertDialog(onDismissRequest={dialog=""},title={Title(wording("Pages","페이지"))},text={
            androidx.compose.foundation.lazy.LazyRow { items(maxOf(pageCount,1)) { i -> TextButton(onClick={session.turnTo(i);dialog=""}) {Text((i+1).toString())} } }
        },confirmButton={TextButton(onClick={addPage();dialog=""}){Text(wording("+ Add page","+ 페이지 추가"))}})
    } else if (dialog == "Clear ink") {
        val index = art.sheet
        AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = notebookBackground,
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
            session.places,
            session::addPlace,
            session::movePlace,
            session::removePlace,
        ) {
            dialog = ""
        }
    else if (dialog.isNotEmpty())
        EditorDialog(dialog, session, chapters, prefs, { dialog = "" }, ::exit)
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
private fun EditorControls(header: @Composable () -> Unit, tools: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 740.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(284.dp)) { header() }
                Box(Modifier.weight(1f)) { tools() }
            }
        } else {
            Column { header(); tools() }
        }
    }
}
