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
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Imprint(stamp(p.created, "MMM"))
                Title(stamp(p.created, "d"), 25)
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
            Note(
                listOf(stamp(p.created, "yMMMd"), p.weather)
                    .filter(String::isNotBlank)
                    .joinToString("  ·  ")
            )
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
fun Journal(
    book: Notebook,
    pages: List<Page>,
    locations: List<DiaryLocation>,
    bookmarks: Boolean,
    clear: () -> Unit,
    newPage: () -> Unit,
    search: JournalSearch,
    open: (Page, List<Page>) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(search.query.isNotEmpty()) }
    val ctx = LocalContext.current
    val found =
        remember(pages, locations, search.query, search.filter, search.day, bookmarks) {
            search.entries(pages, locations, bookmarks)
        }
    Column {
        Header(wording("All writings","전체 기록")) {
            Action("plus", "New page", click = newPage)
            Action("search", "Search journal") {
                searching = !searching
                if (!searching) search.query = ""
            }
            Action("calendar-days", "Filter by date") {
                val d = LocalDate.now()
                DatePickerDialog(
                        ctx,
                        { _, y, m, day -> search.day = LocalDate.of(y, m + 1, day).toString() },
                        d.year,
                        d.monthValue - 1,
                        d.dayOfMonth,
                    )
                    .show()
            }
        }
        if (searching)
            OutlinedTextField(
                search.query,
                { search.query = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Search pages") },
                singleLine = true,
            )
        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("All", "Diary", "Note", "Memory", "Gratitude").forEach {
                Tag(it, it == search.filter) { search.filter = it }
            }
        }
        if (bookmarks) Tag("Bookmarks ×", true, click = clear)
        if (search.day.isNotBlank())
            Tag(localDay(LocalDate.parse(search.day)) + " ×", true) { search.day = "" }
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
