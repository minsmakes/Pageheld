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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import java.time.LocalDate

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
                typography = displayTypography(
                    FontFamily(Font(R.font.ownglyph_mongmongdays)),
                    1f
                )
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
    val family = notebookFont(book.context, prefs["displayFont"])
    val scale = displaySize(prefs) / 100f
    val typography = remember(family, scale) { displayTypography(family, scale) }
    CompositionLocalProvider(LocalDisplayFont provides family, LocalDisplayScale provides scale) {
        MaterialTheme(typography = typography) {
            NotebookScreens(book, data, chapters, preferences)
        }
    }
}

@Composable
private fun NotebookScreens(
    book: Notebook,
    data: List<Page>?,
    chapters: List<Chapter>,
    preferences: Map<String, String>?,
) {
    val prefs = preferences.orEmpty()
    var opened by rememberSaveable { mutableStateOf(false) }
    val showCover = preferences != null && !opened && prefs["openingCover"] != "false"
    val motion = systemMotionEnabled() && prefs["reduceMotion"] != "true"
    LaunchedEffect(preferences != null) {
        if (preferences != null && prefs["openingCover"] == "false") opened = true
    }
    var turn by remember { mutableIntStateOf(0) }
    LaunchedEffect(book) {
        book.errors.collect { Toast.makeText(book.context, it, Toast.LENGTH_LONG).show() }
    }
    var tab by rememberSaveable { mutableStateOf("Today") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var created by rememberSaveable { mutableLongStateOf(0L) }
    var initialChapter by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarks by rememberSaveable { mutableStateOf(false) }
    val locations by remember(book) { book.dao.locations() }.collectAsState(initial = null)
    var info by rememberSaveable { mutableStateOf("") }
    val pages = data.orEmpty()
    var journalIds by remember { mutableStateOf<Set<String>?>(null) }
    fun open(p: Page) {
        journalIds = null
        turn = 0
        created = p.created
        initialChapter = p.chapter
        editor = p.id
    }
    fun go(t: String) {
        tab = t
        selection = null
        bookmarks = false
    }
    Box(
        Modifier.fillMaxSize().background(ivory),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .then(if (showCover) Modifier.clearAndSetSemantics {} else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (data == null || locations == null || preferences == null)
                CircularProgressIndicator()
            else if (editor != null)
                key(editor) {
                    val seed = remember {
                        pages.find { it.id == editor }
                            ?: Page(id = editor!!, created = created, chapter = initialChapter)
                    }
                    val sequence =
                        if (tab == "Chapters" && selection != null)
                            pages.filter { it.chapter == selection }
                        else pages.filter { journalIds?.contains(it.id) != false }
                    val index = sequence.indexOfFirst { it.id == editor }
                    fun adjacent(offset: Int): (() -> Unit)? {
                        if (index < 0) return null
                        val target = sequence.getOrNull(index + offset) ?: return null
                        return {
                            created = target.created
                            initialChapter = target.chapter
                            editor = target.id
                            turn = offset
                        }
                    }
                    TurningPage(turn, motion) {
                        Editor(
                            seed,
                            book,
                            chapters,
                            prefs,
                            locations.orEmpty().filter { it.pageId == seed.id },
                            adjacent(-1),
                            adjacent(1),
                        ) {
                            editor = null
                        }
                    }
                }
            else
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize().paper()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            "Today" ->
                                Today(
                                    book,
                                    pages,
                                    prefs,
                                    ::open,
                                    {
                                        go("Journal")
                                        bookmarks = true
                                    },
                                    { info = "Reminders" },
                                )
                            "Journal" ->
                                Journal(
                                    book,
                                    pages,
                                    locations.orEmpty(),
                                    bookmarks,
                                    { bookmarks = false },
                                    { page, sequence ->
                                        open(page)
                                        journalIds = sequence.map { it.id }.toSet()
                                    },
                                )
                            "Memories" -> Memories(book, pages, locations.orEmpty(), ::open)
                            "Chapters" ->
                                Chapters(
                                    book,
                                    pages,
                                    chapters,
                                    selection,
                                    { selection = it },
                                    ::open,
                                )
                            else -> You(book, prefs)
                        }
                        if (tab == "Today" || tab == "Journal" || tab == "Memories")
                            FloatingActionButton(
                                onClick = { open(Page()) },
                                modifier =
                                    Modifier.align(Alignment.BottomEnd).padding(16.dp).size(58.dp),
                                shape = CircleShape,
                                containerColor = sage,
                                contentColor = Color.White,
                            ) {
                                Glyph("plus", "New page", Color.White, 26)
                            }
                    }
                    HorizontalDivider(thickness = .7.dp, color = edge)
                    Row(
                        Modifier.fillMaxWidth().background(ivory).heightIn(min = 61.dp).height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEach { (name, icon) ->
                            Box(
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .clickable { go(name) }
                                    .semantics { selected = name == tab },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                        .background(if (tab == name) pale else Color.Transparent)
                                        .padding(horizontal = 7.dp, vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Glyph(icon, tint = if (tab == name) sage else ink, size = 22)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        name,
                                        fontSize = uiSp(11),
                                        lineHeight = uiSp(14),
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (tab == name) sage else ink,
                                        fontWeight =
                                            if (tab == name) FontWeight.Medium
                                            else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
        }
        if (showCover) BookCover(motion) { opened = true }
    }
    BackHandler(editor == null && (tab != "Today" || selection != null)) {
        if (selection != null) selection = null else go("Today")
    }
    if (info.isNotBlank())
        AlertDialog(
            onDismissRequest = { info = "" },
            title = { Title("Today’s check-in") },
            text = {
                Note(
                    if (prefs["mood." + LocalDate.now()] != null)
                        "Your mood today is " + prefs["mood." + LocalDate.now()]?.lowercase() + "."
                    else "Take a moment to notice how you feel. Your daily mood is saved in Today."
                )
            },
            confirmButton = { TextButton(onClick = { info = "" }) { Text("Done") } },
        )
}
