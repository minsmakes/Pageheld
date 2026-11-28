package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
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
    val ownerId = LocalBook.current?.id ?: LEGACY_BOOK
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
                        Column(Modifier.padding(12.dp)) {
                            Title("Your chapters", 24)
                            Note("Gather related pages under a chapter title.")
                            TextButton(onClick = { form(false) }) { Text("Create chapter") }
                        }
                    }
                items(chapters, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { select(c.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(c.cover, Modifier.size(38.dp, 50.dp))
                        Spacer(Modifier.width(19.dp))
                        Column(Modifier.weight(1f)) {
                            Title(c.name, 23)
                            Spacer(Modifier.height(7.dp))
                            val n = pages.count { it.chapter == c.id }
                            Note("$n " + if (n == 1) "entry" else "entries")
                        }
                        val first = pages.indexOfFirst { it.chapter == c.id }
                        if(first >= 0) Note("p. " + entryFolio(first)) else Glyph("chevron-right", size=16)
                        Spacer(Modifier.width(13.dp))
                    }
                    HorizontalDivider(color=edge,thickness=.6.dp)
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
                                else Chapter(name = title.trim(), cover = cover, bookId = ownerId)
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
fun You(book: Notebook, settings: Map<String, String>, showPlate: Boolean = true, entries:List<Page> = emptyList()) {
    Column {
        Header("Personal settings")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            if(showPlate) Bookplate(book,settings)
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Title("Fonts", 22)
                    FontOptions(book, settings)
                }
            }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Appearance(
                        settings,
                        settings["paper"] ?: "Blank",
                        { book.setting("paper", it) },
                        book::setting,
                    )
                }
            }
            Panel {
                listOf("openingCover" to "Opening cover", "reduceMotion" to "Reduce motion")
                    .forEach { (key, label) ->
                        val checked =
                            settings[key]?.toBooleanStrictOrNull() ?: (key == "openingCover")
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    role = androidx.compose.ui.semantics.Role.Switch,
                                    onValueChange = { book.setting(key, it.toString()) },
                                )
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, Modifier.weight(1f))
                            Switch(checked = checked, onCheckedChange = null)
                        }
                    }
            }
            Panel {
                Title("Mood journal", 20)
                val checkIns =
                    (settings.filterKeys { it.startsWith("mood.") } + entries.asReversed().filter { it.mood.isNotBlank() }.associate { "mood."+date(it.created).toLocalDate() to it.mood })
                        .toSortedMap(reverseOrder())
                        .entries
                        .take(7)
                if (checkIns.isEmpty()) Note("Your daily check-ins appear here.")
                else
                    checkIns.forEach { (day, mood) ->
                        Note(
                            runCatching {
                                    localDay(java.time.LocalDate.parse(day.removePrefix("mood.")))
                                }
                                .getOrDefault(day.removePrefix("mood.")) + "   ·   " + mood
                        )
                    }
            }
            Panel {
                Title("Privacy", 20)
                Note(
                    "Pages and imported media stay on this device. Opening the map connects to OpenFreeMap to load the area you view. Place search sends your submitted query to the device geocoder. GPS is used once when you request it."
                )
            }
            BackupControls(book)
            Panel {
                Title("Memospine. v" + BuildConfig.VERSION_NAME, 20)
                Note("A personal journal for everyday writing and memories.")
                Note("온글잎 몽몽데이즈몽글동심체 © 2022 VoyagerX and Song Sumin.")
            }
        }
    }
}
