package app.pageheld

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary = sage,
                        onPrimary = Color.White,
                        background = ivory,
                        surface = ivory,
                        onSurface = ink,
                        secondaryContainer = pale,
                    ),
                typography = displayTypography(FontFamily(Font(R.font.ownglyph_mongmongdays)), 1f),
            ) {
                Pageheld(Notebook.get(this))
            }
        }
    }
}

val tabs =
    listOf(
        "Today" to "house",
        "Journal" to "book-open",
        "Memories" to "map-pin",
        "Chapters" to "layers",
        "You" to "user-round",
    )

@Composable
fun Pageheld(book: Notebook) {
    val data by remember(book) { book.dao.pages() }.collectAsState(initial = null)
    val chapters by remember(book) { book.dao.chapters() }.collectAsState(emptyList())
    val preferences by book.settings.collectAsState(initial = null)
    val prefs = preferences.orEmpty()
    val family = notebookFont(book.context, prefs["displayFont"] ?: "Sans")
    val scale = displaySize(prefs) / 100f
    val typography = remember(family, scale) { displayTypography(family, scale) }
    CompositionLocalProvider(LocalDisplayFont provides family, LocalDisplayScale provides scale) {
        MaterialTheme(typography = typography) {
            NotebookScreens(book, data, chapters, preferences)
        }
    }
}

@Composable
internal fun NotebookScreens(
    book: Notebook,
    data: List<Page>?,
    chapters: List<Chapter>,
    preferences: Map<String, String>?,
) {
    val prefs = preferences.orEmpty()
    val motion = systemMotionEnabled() && prefs["reduceMotion"] != "true"
    val locations by remember(book) { book.dao.locations() }.collectAsState(initial = null)
    LaunchedEffect(book) {
        book.errors.collect { Toast.makeText(book.context, it, Toast.LENGTH_LONG).show() }
    }
    if (data == null || locations == null || preferences == null) {
        Box(Modifier.fillMaxSize().background(Binding.desk), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = sage)
        }
        return
    }
    val places = locations.orEmpty()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000)
        }
    }
    val day = date(now).toLocalDate()
    var dailyId by
        rememberSaveable(day.toString()) {
            mutableStateOf(data.firstOrNull { date(it.created).toLocalDate() == day }?.id ?: uid())
        }
    val dailyTime = rememberSaveable(day.toString()) { now }
    val dailyPage =
        data.find { it.id == dailyId }
            ?: Page(id = dailyId, created = dailyTime, mood = prefs["mood.$day"].orEmpty())
    var leaf by rememberSaveable(stateSaver = Leaf.saver) { mutableStateOf(Leaf()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val turn = remember(scope) { BookTurn(scope) { leaf = it } }
    SideEffect { turn.motion = motion }
    val search = rememberSaveable(saver = JournalSearch.saver) { JournalSearch() }
    val results =
        remember(data, places, search.query, search.day, search.filter, leaf.bookmarks) {
            search.entries(data, places, leaf.bookmarks)
        }
    fun sequence(at: Leaf): List<Page> =
        when {
            at.section == "Chapters" && at.chapter.isNotEmpty() ->
                data.filter { it.chapter == at.chapter }
            at.section == "Journal" ->
                if (at.bookmarks == leaf.bookmarks) results
                else search.entries(data, places, at.bookmarks)
            else -> data
        }
    fun move(next: Leaf, sign: Int = 1) {
        if (next == leaf || busy || turn.target != null) return
        focus.clearFocus()
        turn.jump(next, sign)
    }
    fun chapter(name: String) =
        move(
            Leaf(name),
            if (
                tabs.indexOfFirst { it.first == name } >=
                    tabs.indexOfFirst { it.first == leaf.section }
            )
                1
            else -1,
        )
    fun adjacent(at: Leaf, sign: Int, wide: Boolean): Leaf? {
        val sequence = sequence(at)
        val reading = at.entry.isNotEmpty() || wide && at.section == "Journal"
        if (reading) {
            val i = if (at.entry.isEmpty()) 0 else sequence.indexOfFirst { it.id == at.entry }
            if (i >= 0)
                sequence.getOrNull(i + sign)?.let {
                    return at.copy(entry = it.id, created = it.created, attachment = "")
                }
        }
        if (at.editing) return null
        if (at.section == "Memories" && at.entry.isEmpty() && sign < 0) {
            search.entries(data, places, false).lastOrNull()?.let {
                return Leaf("Journal", entry = it.id, created = it.created)
            }
        }
        val sectionIndex = tabs.indexOfFirst { it.first == at.section }
        return tabs.getOrNull(sectionIndex + sign)?.let { Leaf(it.first) }
    }
    fun back(at: Leaf) {
        when {
            at.editing && at.entry.isNotEmpty() && data.any { it.id == at.entry } ->
                move(at.copy(editing = false, attachment = ""), -1)
            at.entry.isNotEmpty() || at.daily ->
                move(at.copy(entry = "", editing = false, attachment = "", daily = false), -1)
            at.chapter.isNotEmpty() || at.bookmarks -> move(Leaf(at.section), -1)
            else -> chapter("Today")
        }
    }
    NotebookSpread(
        prefs["openingCover"] == "false",
        motion,
        prefs["coverLearned"] == "true",
        leaf.section,
        busy || turn.target != null,
        { book.setting("coverLearned", "true") },
        ::chapter,
        {
            val marked = data.filter { it.starred }
            search.query = ""
            search.day = ""
            search.filter = "All"
            move(Leaf("Journal", entry = marked.singleOrNull()?.id.orEmpty(), bookmarks = true))
        },
    ) { wide, opened ->
        val ime = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        BackHandler(opened && (leaf != Leaf() || turn.target != null)) {
            if (!busy && turn.target == null) back(leaf)
        }
        TurningLeaves(
            leaf,
            turn,
            wide,
            opened && !busy && !ime,
            { sign -> adjacent(leaf, sign, wide) },
        ) { at, active ->
            val saved = data.firstOrNull { it.id == at.entry }
            val sequence = sequence(at)
            val current =
                saved
                    ?: if (at.entry.isEmpty() && at.section == "Journal") sequence.firstOrNull()
                    else null
            val number =
                when (at.section) {
                    "Today" -> if (at.entry.isEmpty()) 2 else entryFolio(data.indexOf(saved))
                    "Journal" -> entryFolio(data.indexOf(current))
                    "Memories" -> entryFolio(data.size)
                    "Chapters" -> entryFolio(data.size) + 2
                    else -> entryFolio(data.size) + 4
                }
            fun read(p: Page) = move(at.copy(entry = p.id, created = p.created, editing = false))
            fun edit(p: Page, file: String = "") =
                move(at.copy(entry = p.id, created = p.created, editing = true, attachment = file))
            fun newPage() = edit(Page(chapter = at.chapter.ifEmpty { null }))
            fun next(sign: Int): (() -> Unit)? {
                val dest = adjacent(at, sign, wide) ?: return null
                return { move(dest, sign) }
            }
            val editor: @Composable (Page, Boolean) -> Unit = { seed, daily ->
                key(seed.id) {
                    Editor(
                        seed,
                        book,
                        chapters,
                        prefs,
                        places.filter { it.pageId == seed.id },
                        if (daily) null else next(-1),
                        if (daily) null else next(1),
                        daily = daily,
                        active = active && opened,
                        initialAttachment = at.attachment,
                        onImporting = { busy = it },
                        onDeleted = {
                            if (seed.id == dailyId) dailyId = uid()
                            if (!daily || !wide) move(Leaf(at.section, chapter = at.chapter), -1)
                        },
                    ) {
                        if (daily) {
                            dailyId = uid()
                            if (!wide) move(Leaf(), -1)
                        } else back(at)
                    }
                }
            }
            val reflection: @Composable () -> Unit = {
                Reflection(
                    book,
                    data,
                    prefs,
                    now,
                    !wide,
                    { move(Leaf(daily = true)) },
                    { p -> move(Leaf("Journal", p.id)) },
                )
            }
            val journal: @Composable () -> Unit = {
                Journal(
                    book,
                    data,
                    places,
                    at.bookmarks,
                    { move(Leaf("Journal")) },
                    ::newPage,
                    search,
                ) { p, _ ->
                    read(p)
                }
            }
            val reader: @Composable () -> Unit = {
                ReadingPage(
                    book,
                    current,
                    ::newPage,
                    { file -> current?.let { edit(it, file) } },
                    if (!wide && at.entry.isNotEmpty()) ({ back(at) }) else null,
                )
            }
            val hasEntry = at.entry.isNotEmpty()
            Row(Modifier.fillMaxSize()) {
                if (wide)
                    Box(Modifier.weight(1f)) {
                        Folio(at.section, number - 1, true, true) {
                            when {
                                at.section == "Journal" -> journal()
                                hasEntry -> {
                                    val previous =
                                        sequence.getOrNull(
                                            sequence.indexOfFirst { it.id == at.entry } + 1
                                        )
                                    if (previous != null && previous.id != at.entry)
                                        ReadingPage(
                                            book,
                                            previous,
                                            ::newPage,
                                            { file -> edit(previous, file) },
                                        )
                                    else Contents(::chapter)
                                }
                                at.section == "Today" -> reflection()
                                at.section == "Memories" -> Memories(book, data, places, ::read)
                                at.section == "You" ->
                                    Column(Modifier.verticalScroll(rememberScrollState())) {
                                        Bookplate(book, prefs)
                                    }
                                else -> Contents(::chapter)
                            }
                        }
                    }
                Box(Modifier.weight(1f)) {
                    Folio(
                        at.section,
                        number,
                        wide,
                        false,
                        previous = if (ime || at.editing) null else next(-1),
                        next = if (ime || at.editing) null else next(1),
                    ) {
                        when {
                            at.editing ->
                                editor(
                                    saved
                                        ?: Page(
                                            id = at.entry,
                                            created = at.created,
                                            chapter = at.chapter.ifEmpty { null },
                                            mood =
                                                prefs["mood.${date(at.created).toLocalDate()}"]
                                                    .orEmpty(),
                                        ),
                                    false,
                                )
                            hasEntry -> reader()
                            at.section == "Today" ->
                                if (wide || at.daily) editor(dailyPage, true) else reflection()
                            at.section == "Journal" -> if (wide) reader() else journal()
                            at.section == "Memories" ->
                                if (wide) Album(book, data, places, ::edit)
                                else CompactMemories(book, data, places, ::read, ::edit)
                            at.section == "Chapters" ->
                                Chapters(
                                    book,
                                    data,
                                    chapters,
                                    at.chapter.ifEmpty { null },
                                    { move(at.copy(chapter = it.orEmpty())) },
                                    { p -> if (data.any { it.id == p.id }) read(p) else edit(p) },
                                )
                            else -> You(book, prefs, showPlate = !wide)
                        }
                    }
                }
            }
        }
    }
}
