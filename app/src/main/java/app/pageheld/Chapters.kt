package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*

@Composable
fun Chapters(
    book: Notebook,
    pages: List<Page>,
    chapters: List<Chapter>,
    selected: String?,
    select: (String?) -> Unit,
    open: (Page) -> Unit,
) {
    val current = chapters.find { it.id == selected }
    var dialog by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var cover by rememberSaveable { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(false) }
    fun form(edit: Boolean) {
        title = if (edit) current?.name.orEmpty() else ""
        cover = if (edit) current?.cover ?: 0 else chapters.size % 4
        dialog = if (edit) "Edit chapter" else "New chapter"
    }
    Column {
        if (current == null) Header("Chapters") { Action("plus", "New chapter") { form(false) } }
        else
            Header("Chapter", { Action("arrow-left", "All chapters") { select(null) } }) {
                Action("plus", "New chapter page") { open(Page(chapter = current.id)) }
                Box {
                    Action("ellipsis-vertical", "Chapter options") { options = true }
                    DropdownMenu(options, { options = false }) {
                        listOf("Edit chapter", "Move up", "Move down", "Delete chapter").forEach {
                            label ->
                            val i = chapters.indexOf(current)
                            val j = i + if (label == "Move up") -1 else 1
                            DropdownMenuItem(
                                text = { Text(label) },
                                enabled = !label.startsWith("Move") || j in chapters.indices,
                                onClick = {
                                    options = false
                                    when (label) {
                                        "Edit chapter" -> form(true)
                                        "Delete chapter" -> dialog = label
                                        else -> book.queue { book.dao.swap(current, chapters[j]) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (current == null) {
                if (chapters.isEmpty())
                    item {
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cover(0, Modifier.size(88.dp, 118.dp))
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Title("Your chapters", 22)
                                    Note("Gather related pages in a chapter.")
                                    TextButton(onClick = { form(false) }) { Text("Create chapter") }
                                }
                            }
                        }
                    }
                items(chapters, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .shadow(2.dp, round)
                            .clip(round)
                            .background(ivory.copy(alpha = .9f))
                            .border(.8.dp, edge, round)
                            .clickable { select(c.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(c.cover, Modifier.size(112.dp, 136.dp))
                        Spacer(Modifier.width(19.dp))
                        Column(Modifier.weight(1f)) {
                            Title(c.name, 23)
                            Spacer(Modifier.height(7.dp))
                            val n = pages.count { it.chapter == c.id }
                            Note("$n " + if (n == 1) "entry" else "entries")
                        }
                        Glyph("chevron-right", size = 20)
                        Spacer(Modifier.width(13.dp))
                    }
                }
            } else {
                item { Title(current.name, 27) }
                val entries = pages.filter { it.chapter == current.id }
                if (entries.isEmpty())
                    item {
                        Empty(
                            "An open chapter",
                            "Add a page here, or choose this chapter in an existing page’s notebook menu.",
                        )
                    }
                items(entries, key = { it.id }) { EntryRow(it, book, open, true) }
            }
        }
    }
    if (dialog.isNotBlank())
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Title(dialog) },
            text = {
                if (dialog == "Delete chapter") Note("Your pages will remain in Journal.")
                else
                    Column {
                        OutlinedTextField(
                            title,
                            { title = it },
                            label = { Text("Chapter title") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(15.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) { i ->
                                Cover(
                                    i,
                                    Modifier.size(46.dp, 62.dp)
                                        .border(
                                            if (i == cover) 2.dp else 0.dp,
                                            if (i == cover) sage else Color.Transparent,
                                            round,
                                        )
                                        .clickable { cover = i },
                                )
                            }
                        }
                    }
            },
            confirmButton = {
                TextButton(
                    enabled = dialog == "Delete chapter" || title.isNotBlank(),
                    onClick = {
                        if (dialog == "Delete chapter" && current != null) {
                            book.queue { book.dao.deleteChapter(current.id) }
                            select(null)
                        } else {
                            val c =
                                if (dialog == "Edit chapter" && current != null)
                                    current.copy(name = title.trim(), cover = cover)
                                else Chapter(name = title.trim(), cover = cover)
                            book.queue { book.dao.save(c) }
                        }
                        dialog = ""
                    },
                ) {
                    Text(if (dialog == "Delete chapter") "Delete" else "Save")
                }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } },
        )
}

@Composable
fun You(book: Notebook, settings: Map<String, String>) {
    Column {
        Header("You")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Panel {
                Title("Your notebook", 24)
                Note("Choose the look of your writing pages.")
            }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Appearance(
                        settings,
                        settings["paper"] ?: "Ruled",
                        { book.setting("paper", it) },
                        book::setting,
                    )
                }
            }
            Panel {
                Title("Mood journal", 20)
                val checkIns =
                    settings
                        .filterKeys { it.startsWith("mood.") }
                        .toSortedMap(reverseOrder())
                        .entries
                        .take(7)
                if (checkIns.isEmpty()) Note("Your daily check-ins appear here.")
                else
                    checkIns.forEach { (day, mood) ->
                        Note(day.removePrefix("mood.") + "   ·   " + mood)
                    }
            }
            Panel {
                Title("Privacy", 20)
                Note(
                    "Pages and imported media stay on this device. Opening the map connects to OpenFreeMap to load the area you view. Places are chosen by hand."
                )
            }
            BackupControls(book)
            Panel {
                Title("About Pageheld", 20)
                Note("A personal journal for everyday writing and memories.")
            }
        }
    }
}
