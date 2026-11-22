package app.pageheld

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.io.File
import java.time.*

@Composable
fun EntryRow(p: Page, book: Notebook, open: (Page) -> Unit, menu: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open(p) }.padding(vertical = if (menu) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = if (menu) Modifier.size(94.dp, 104.dp) else Modifier.size(71.dp, 62.dp)
        val file = p.files().firstOrNull()
        if (file != null) Thumbnail(File(mediaDir(book), file), thumb)
        else
            Box(
                thumb
                    .clip(RoundedCornerShape(8.dp))
                    .paper(p.paper.ifBlank { "Ruled" }, Color(0xffebe9db), 12.dp, 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Glyph("feather", tint = sage)
            }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.heading(),
                fontFamily = LocalDisplayFont.current,
                color = ink,
                fontSize = uiSp(17),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Note(stamp(p.created, "yMMMd"))
            Spacer(Modifier.height(3.dp))
            Text(
                p.body.ifBlank { p.template },
                fontSize = uiSp(14),
                lineHeight = uiSp(19),
                maxLines = 2,
                color = muted,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (menu)
            Box {
                Action("ellipsis-vertical", "Entry actions") { expanded = true }
                DropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Open page") },
                        onClick = {
                            expanded = false
                            open(p)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (p.starred) "Remove bookmark" else "Bookmark") },
                        onClick = {
                            expanded = false
                            book.queue { book.dao.bookmark(p.id, !p.starred) }
                        },
                    )
                }
            }
    }
}

@Composable
fun Today(
    book: Notebook,
    pages: List<Page>,
    prefs: Map<String, String>,
    open: (Page) -> Unit,
    bookmarks: () -> Unit,
    checkIn: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val day = date(now).toLocalDate()
    val anniversary =
        pages.firstOrNull {
            val d = date(it.created).toLocalDate()
            d.year < day.year && MonthDay.from(d) == MonthDay.from(day)
        }
    Column {
        Header(PAGEHELD_WORDMARK) {
            Action("bookmark", "Bookmarked pages", click = bookmarks)
            Action("bell", "Daily check-in", click = checkIn)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 24.dp, bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                Column(Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                    Note(
                        when (date(now).hour) {
                            in 0..11 -> "Good morning,"
                            in 12..17 -> "Good afternoon,"
                            else -> "Good evening,"
                        }
                    )
                    Title("Your day, your story.", 27, true)
                    Spacer(Modifier.height(5.dp))
                    Title(stamp(now, "yMMMEd"), 17)
                }
            }
            item {
                Panel(Modifier.clickable { open(Page()) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).background(pale, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph("pencil", tint = sage)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Title("Write today’s page", 20)
                            Note("A little space for whatever is on your mind.")
                        }
                    }
                }
            }
            item {
                Panel {
                    Title("Mood check-in", 18)
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        val colors =
                            listOf(0xffffdfa3, 0xfff5e3be, 0xfff1d5c6, 0xffd3dddd, 0xffdfd4df)
                        moods.forEachIndexed { i, m ->
                            val active = prefs["mood.$day"] == m
                            Box(
                                Modifier.size(46.dp)
                                    .clickable { book.setting("mood.$day", m) }
                                    .semantics { selected = active },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(34.dp)
                                        .background(Color(colors[i]), CircleShape)
                                        .then(
                                            if (active) Modifier.border(2.dp, sage, CircleShape)
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Glyph(
                                        listOf("laugh", "smile", "meh", "cloud", "frown")[i],
                                        m,
                                        ink,
                                        23,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Panel(
                    Modifier.then(
                        if (anniversary != null) Modifier.clickable { open(anniversary) }
                        else Modifier
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pic = anniversary?.files()?.firstOrNull()
                        if (pic != null) Thumbnail(File(mediaDir(book), pic), Modifier.size(66.dp))
                        else Cover(1, Modifier.size(65.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Title("On This Day", 19)
                            Note(
                                if (anniversary == null)
                                    "Past pages from this date will appear here."
                                else "A memory from " + stamp(anniversary.created, "yMMMd")
                            )
                        }
                        if (anniversary != null) Glyph("chevron-right", size = 18)
                    }
                }
            }
            item { Title("Recent Entries", 20) }
            if (pages.isEmpty())
                item { Empty("Your first page", "Start writing to see your recent entries here.") }
            items(pages.take(4), key = { it.id }) { p -> Panel { EntryRow(p, book, open) } }
        }
    }
}

@Composable
fun Journal(
    book: Notebook,
    pages: List<Page>,
    locations: List<DiaryLocation>,
    bookmarks: Boolean,
    clear: () -> Unit,
    open: (Page, List<Page>) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var selectedDate by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val found =
        pages.filter { p ->
            (!bookmarks || p.starred) &&
                (filter == "All" ||
                    (filter == "Diary" && p.template in listOf("Daily Diary", "Free Writing")) ||
                    filter == p.template) &&
                ((p.title + " " + p.body).contains(query, true) ||
                    locations.any { it.pageId == p.id && it.name.contains(query, true) }) &&
                (selectedDate.isBlank() || date(p.created).toLocalDate().toString() == selectedDate)
        }
    Column {
        Header("Journal") {
            Action("search", "Search journal") {
                searching = !searching
                if (!searching) query = ""
            }
            Action("calendar-days", "Filter by date") {
                val d = LocalDate.now()
                DatePickerDialog(
                        ctx,
                        { _, y, m, day -> selectedDate = LocalDate.of(y, m + 1, day).toString() },
                        d.year,
                        d.monthValue - 1,
                        d.dayOfMonth,
                    )
                    .show()
            }
        }
        if (searching)
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Search pages") },
                singleLine = true,
            )
        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("All", "Diary", "Memory", "Gratitude").forEach {
                Tag(it, it == filter) { filter = it }
            }
        }
        if (bookmarks) Tag("Bookmarks ×", true, click = clear)
        if (selectedDate.isNotBlank())
            Tag(localDay(LocalDate.parse(selectedDate)) + " ×", true) { selectedDate = "" }
        LazyColumn(
            contentPadding = PaddingValues(start = 17.dp, end = 14.dp, top = 6.dp, bottom = 84.dp)
        ) {
            if (found.isEmpty())
                item {
                    Empty(
                        if (pages.isEmpty()) "Your journal begins here" else "No matching pages",
                        if (pages.isEmpty()) "Use + to write your first page."
                        else "Try another date, filter or search.",
                    )
                }
            found
                .groupBy { stamp(it.created, "yMMMM") }
                .forEach { (month, entries) ->
                    item { Box(Modifier.padding(vertical = 12.dp)) { Title(month, 19) } }
                    items(entries, key = { it.id }) {
                        EntryRow(it, book, { page -> open(page, found) }, true)
                        HorizontalDivider(color = edge, thickness = .6.dp)
                    }
                }
        }
    }
}
