package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import java.io.File
import java.time.MonthDay

internal fun entryFolio(index: Int) = 4 + index.coerceAtLeast(0) * 2

@Stable
class JournalSearch {
    var query by mutableStateOf("")
    var filter by mutableStateOf("All")
    var day by mutableStateOf("")

    fun entries(pages: List<Page>, locations: List<DiaryLocation>, marked: Boolean) =
        pages.filter { p ->
            (!marked || p.starred) &&
                (filter == "All" ||
                    filter == p.template ||
                    filter == "Diary" && p.template in listOf("Daily Diary", "Free Writing")) &&
                (day.isEmpty() || date(p.created).toLocalDate().toString() == day) &&
                ((p.title + " " + p.body).contains(query, true) ||
                    locations.any { it.pageId == p.id && it.name.contains(query, true) })
        }

    companion object {
        val saver =
            listSaver<JournalSearch, String>(
                save = { listOf(it.query, it.filter, it.day) },
                restore = {
                    JournalSearch().apply {
                        query = it[0]
                        filter = it[1]
                        day = it[2]
                    }
                },
            )
    }
}

@Composable
internal fun Reflection(
    book: Notebook,
    pages: List<Page>,
    prefs: Map<String, String>,
    now: Long,
    compact: Boolean,
    write: () -> Unit,
    read: (Page) -> Unit,
) {
    val day = date(now).toLocalDate()
    val mood = prefs["mood.$day"].orEmpty()
    val anniversary =
        remember(day, pages) {
            pages.firstOrNull {
                val d = date(it.created).toLocalDate()
                d.year < day.year && MonthDay.from(d) == MonthDay.from(day)
            }
        }
    LazyColumn(
        contentPadding =
            PaddingValues(
                horizontal = if (LocalCompactPage.current) 14.dp else Binding.margin,
                vertical = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Imprint(stamp(now, "yMMMM"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stamp(now, "EEEE"),
                        fontFamily = serif,
                        color = ink,
                        fontSize = uiSp(31),
                        lineHeight = uiSp(40),
                    )
                    Text(
                        stamp(now, "d"),
                        fontFamily = serif,
                        color = ink,
                        fontSize = uiSp(58),
                        lineHeight = uiSp(70),
                    )
                }
                Botanical(Modifier.widthIn(max = 118.dp).weight(.65f).height(102.dp))
            }
            Text(
                when (date(now).hour) {
                    in 0..11 -> "Good morning."
                    in 12..17 -> "Good afternoon."
                    else -> "Good evening."
                },
                fontFamily = Binding.hand,
                color = sage,
                fontSize = uiSp(22),
            )
        }
        item {
            HorizontalDivider(color = edge)
            Spacer(Modifier.height(15.dp))
            Imprint("Mood check-in")
            Spacer(Modifier.height(6.dp))
            Title(mood.ifEmpty { "How are you feeling?" }, 20)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                moods.forEachIndexed { i, name ->
                    val active = name == mood
                    Box(
                        Modifier.size(48.dp)
                            .clickable(role = Role.RadioButton) { book.setting("mood.$day", name) }
                            .semantics { selected = active },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(33.dp)
                                .then(
                                    if (active)
                                        Modifier.background(
                                            pale,
                                            androidx.compose.foundation.shape.CircleShape,
                                        )
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(
                                listOf("laugh", "smile", "meh", "cloud", "frown")[i],
                                name,
                                if (active) sage else muted,
                                23,
                            )
                        }
                    }
                }
            }
        }
        item {
            Imprint("A reflection")
            Spacer(Modifier.height(6.dp))
            Title(
                listOf(
                    "What would you like to remember about today?",
                    "When did you feel most like yourself?",
                    "What small thing are you grateful for?",
                    "What caught your attention today?",
                )[day.dayOfYear % 4],
                20,
                true,
            )
            if (compact)
                TextButton(onClick = write) {
                    Glyph("pencil", size = 17)
                    Spacer(Modifier.width(8.dp))
                    Text("Write today’s page")
                }
        }
        if (anniversary != null)
            item {
                Imprint("On this day")
                EntryRow(anniversary, book, read)
            }
        if (pages.isNotEmpty()) item { Imprint("Recent pages") }
        items(pages.take(3), key = { it.id }) { EntryRow(it, book, read) }
    }
}

@Composable
internal fun ReadingPage(
    book: Notebook,
    page: Page?,
    write: () -> Unit,
    edit: (String) -> Unit,
    index: (() -> Unit)? = null,
) {
    val paragraphs =
        remember(page?.body) {
            page
                ?.body
                ?.split('\n')
                ?.flatMap { line -> if (line.length <= 2000) listOf(line) else line.chunked(2000) }
                .orEmpty()
        }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Binding.margin, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Imprint(
                    if (page == null) "First entry" else stamp(page.created, "yMMMMd"),
                    Modifier.weight(1f),
                )
                if (index != null) Action("list", "Journal index", click = index)
                if (page != null) Action("pencil", "Edit entry") { edit("") }
            }
            Title(page?.heading() ?: "Your first page", 28)
        }
        if (page == null) item { TextButton(onClick = write) { Text("Write a page") } }
        else {
            item {
                val details = listOf(page.mood, page.weather).filter(String::isNotBlank)
                if (details.isNotEmpty()) Note(details.joinToString("  ·  "))
                HorizontalDivider(Modifier.padding(top = 8.dp), color = edge)
            }
            // Paragraphs are separate lazy items so long entries can continue down the leaf.
            items(paragraphs.size) { i ->
                Text(
                    paragraphs[i],
                    fontFamily = serif,
                    fontSize = uiSp(18),
                    lineHeight = uiSp(29),
                    color = ink,
                )
            }
            items(page.files()) { file ->
                Thumbnail(
                    File(mediaDir(book), file),
                    Modifier.fillMaxWidth().clickable { edit(file) },
                    print = true,
                )
            }
        }
    }
}

@Composable
internal fun Album(
    book: Notebook,
    pages: List<Page>,
    locations: List<DiaryLocation>,
    open: (Page, String) -> Unit,
) {
    val prints = remember(pages) { pages.flatMap { p -> p.files().map { p to it } } }
    LazyColumn(
        contentPadding =
            PaddingValues(
                horizontal = if (LocalCompactPage.current) 14.dp else Binding.margin,
                vertical = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Imprint("Photographs & film")
            Title("Memory album", 30)
        }
        if (prints.isEmpty())
            item { Note("Attach a photograph or film to a journal page to keep a print here.") }
        items(prints, key = { it.first.id + it.second }) { (page, file) ->
            Column(
                Modifier.fillMaxWidth().clickable(
                    role = Role.Button,
                    onClickLabel = "View attachment",
                ) {
                    open(page, file)
                }
            ) {
                Box(
                    Modifier.fillMaxWidth().shadow(2.dp).background(Color(0xfffffcf4)).padding(6.dp)
                ) {
                    Thumbnail(File(mediaDir(book), file), Modifier.fillMaxWidth(), print = true)
                }
                Spacer(Modifier.height(10.dp))
                Note(
                    listOf(
                            stamp(page.created, "yMMMd"),
                            locations.firstOrNull { it.pageId == page.id }?.label().orEmpty(),
                        )
                        .filter(String::isNotEmpty)
                        .joinToString("  ·  ")
                )
                Title(page.heading(), 19)
            }
        }
    }
}

@Composable
internal fun Contents(chapter: (String) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = Binding.margin, vertical = 16.dp)
    ) {
        Imprint("Notebook index")
        Title("Contents", 32)
        Spacer(Modifier.height(20.dp))
        tabs.forEachIndexed { i, (name, _) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { chapter(name) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Note(listOf("I", "II", "III", "IV", "V")[i])
                Spacer(Modifier.width(18.dp))
                Title(name, 20)
                Spacer(Modifier.width(12.dp))
                HorizontalDivider(Modifier.weight(1f), color = edge)
                Spacer(Modifier.width(10.dp))
                Glyph("chevron-right", size = 15)
            }
        }
    }
}

@Composable
internal fun Bookplate(book: Notebook, prefs: Map<String, String>) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth()
            .padding(Binding.margin)
            .border(1.dp, edge)
            .padding(5.dp)
            .border(.5.dp, edge)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Imprint("Ex libris")
        Botanical(Modifier.size(112.dp, 88.dp))
        Title("This book belongs to", 21, true)
        TextButton(
            onClick = {
                draft = prefs["owner"].orEmpty()
                dialog = true
            }
        ) {
            Text(
                prefs["owner"].orEmpty().ifEmpty { "Write your name" },
                fontFamily = LocalDisplayFont.current,
                fontSize = uiSp(24),
                color = sage,
            )
        }
    }
    if (dialog)
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Title("Bookplate") },
            text = {
                OutlinedTextField(
                    draft,
                    { draft = it },
                    label = { Text("Your name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        book.setting("owner", draft.trim())
                        dialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
}
