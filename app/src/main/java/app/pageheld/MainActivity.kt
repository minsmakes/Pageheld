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
    val prefs by book.settings.collectAsState(emptyMap())
    LaunchedEffect(book) {
        book.errors.collect { Toast.makeText(book.context, it, Toast.LENGTH_LONG).show() }
    }
    var tab by rememberSaveable { mutableStateOf("Today") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var created by rememberSaveable { mutableLongStateOf(0L) }
    var initialChapter by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarks by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf("") }
    val pages = data.orEmpty()
    fun open(p: Page) {
        created = p.created
        initialChapter = p.chapter
        editor = p.id
    }
    fun go(t: String) {
        tab = t
        selection = null
        bookmarks = false
        menu = false
    }
    Box(
        Modifier.fillMaxSize()
            .background(ivory)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (data == null) CircularProgressIndicator()
        else if (editor != null)
            key(editor) {
                val seed = remember {
                    pages.find { it.id == editor }
                        ?: Page(id = editor!!, created = created, chapter = initialChapter)
                }
                Editor(seed, book, chapters, prefs) { editor = null }
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
                                { menu = true },
                                {
                                    go("Journal")
                                    bookmarks = true
                                },
                                { info = "Reminders" },
                            )
                        "Journal" -> Journal(book, pages, bookmarks, { bookmarks = false }, ::open)
                        "Memories" -> Memories(book, pages, ::open)
                        "Chapters" ->
                            Chapters(book, pages, chapters, selection, { selection = it }, ::open)
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
                    Modifier.fillMaxWidth().background(ivory).height(68.dp),
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
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Glyph(icon, tint = if (tab == name) sage else ink, size = 22)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    name,
                                    fontSize = 11.sp,
                                    color = if (tab == name) sage else ink,
                                    fontWeight =
                                        if (tab == name) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
    }
    BackHandler(editor == null && (tab != "Today" || selection != null)) {
        if (selection != null) selection = null else go("Today")
    }
    if (menu)
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Title("pageheld.", 27) },
            text = {
                Column {
                    tabs.forEach {
                        TextButton(onClick = { go(it.first) }) {
                            Glyph(it.second)
                            Spacer(Modifier.width(12.dp))
                            Title(it.first, 18)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("Close") } },
        )
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
