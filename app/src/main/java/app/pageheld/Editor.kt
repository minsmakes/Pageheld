package app.pageheld

import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.widget.VideoView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.*

fun mediaDir(book: Notebook) = File(book.context.filesDir, "attachments").apply { mkdirs() }

private val photoCache =
    object : android.util.LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.allocationByteCount
    }

private fun photoKey(file: File, target: Int) = "${file.absolutePath}:$target"

internal fun cachedPrint(file: File) = photoCache.get(photoKey(file, 1600))

internal suspend fun loadPrint(file: File, target: Int = 1600): android.graphics.Bitmap? {
    val key = photoKey(file, target)
    photoCache.get(key)?.let {
        return it
    }
    return withContext(Dispatchers.IO) {
        photoCache.get(key)
            ?: (runCatching {
                        if (file.extension == "video")
                            MediaMetadataRetriever().let { retriever ->
                                try {
                                    retriever.setDataSource(file.path)
                                    retriever.getScaledFrameAtTime(
                                        -1,
                                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                        320,
                                        240,
                                    )
                                } finally {
                                    retriever.release()
                                }
                            }
                        else
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) {
                                decoder,
                                info,
                                _ ->
                                decoder.setTargetSampleSize(
                                    max(1, max(info.size.width, info.size.height) / target)
                                )
                            }
                    }
                    .getOrNull())
                ?.also { photoCache.put(key, it) }
    }
}

@Composable
fun Thumbnail(file: File, modifier: Modifier, large: Boolean = false, print: Boolean = false) {
    val target = if (large) 1600 else if (print) 960 else 320
    val bitmap by
        produceState(photoCache.get(photoKey(file, target)), file, target) {
            value = loadPrint(file, target)
        }
    Box(
        modifier
            .then(
                if (print)
                    Modifier.aspectRatio(
                        bitmap?.let { (it.width.toFloat() / it.height).coerceIn(.7f, 2.1f) } ?: 1.5f
                    )
                else Modifier
            )
            .clip(RoundedCornerShape(if (large || print) 1.dp else 4.dp))
            .background(if (large) Color(0xffeee8db) else pale),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                if (file.extension == "video") "Attached video" else "Attached photograph",
                Modifier.fillMaxSize(),
                contentScale = if (large || print) ContentScale.Fit else ContentScale.Crop,
            )
        }
        if (file.extension == "video") {
            Box(
                Modifier.size(32.dp).background(ink.copy(alpha = .78f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Glyph("play", tint = ivory, size = 17)
            }
        } else if (bitmap == null) Glyph("image")
    }
}

@Composable
fun Appearance(
    settings: Map<String, String>,
    paper: String,
    setPaper: (String) -> Unit,
    setting: (String, String) -> Unit,
) {
    Title(wording("Notebook theme", "다이어리 테마"), 21)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NotebookPalette.entries.forEach { palette ->
            val active = LocalNotebookPalette.current == palette
            FilterChip(
                selected = active,
                onClick = { setting("notebookTheme", palette.id) },
                label = { Text(wording(palette.id, palette.korean)) },
                leadingIcon = {
                    Box(Modifier.size(16.dp).background(palette.accent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (active) Glyph("check", tint = Color.White, size = 12)
                    }
                },
            )
        }
    }
    Title("Paper styles", 21)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 410.dp) 6 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            papers.chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { name ->
                        Column(
                            Modifier.weight(1f).clickable { setPaper(name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(67.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .paper(
                                        name,
                                        interval = 8.dp,
                                        margin = if (name == "Letter") 9.dp else 0.dp,
                                    )
                                    .border(
                                        if (name == paper) 2.dp else .7.dp,
                                        if (name == paper) sage else edge,
                                        RoundedCornerShape(8.dp),
                                    )
                            ) {
                                if (name == paper)
                                    Box(Modifier.padding(3.dp)) {
                                        Glyph("check", "Selected", sage, 17)
                                    }
                                if (name == "Letter")
                                    Text(
                                        "Dear me,",
                                        Modifier.padding(7.dp),
                                        fontFamily = LocalDisplayFont.current,
                                        fontSize = uiSp(9),
                                    )
                                if (name == "Scrapbook") Glyph("image", tint = sage)
                            }
                            Text(name, fontSize = uiSp(12))
                        }
                    }
                }
            }
        }
    }
    Title("Paper tone", 18)
    Row {
        swatches.forEachIndexed { i, c ->
            IconButton(onClick = { setting("tone", i.toString()) }) {
                Box(
                    Modifier.size(32.dp)
                        .background(c, CircleShape)
                        .border(.8.dp, edge, CircleShape)
                        .semantics {
                            contentDescription = listOf("Ivory", "Sand", "Blush", "Mist", "Sage")[i]
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if ((settings["tone"] ?: "0") == i.toString()) Glyph("check", size = 16)
                }
            }
        }
    }
    listOf(
            Triple("Line spacing", "spacing", listOf("Classic", "Close", "Airy")),
            Triple("Margins", "margin", listOf("Classic", "Narrow", "Wide")),
        )
        .forEach { (label, key, choices) ->
            Title(label, 18)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                choices.forEach {
                    Tag(it, it == (settings[key] ?: choices.first())) { setting(key, it) }
                }
            }
        }
}

@Composable
internal fun EditorDialog(
    name: String,
    session: WritingSession,
    chapters: List<Chapter>,
    settings: Map<String, String>,
    dismiss: () -> Unit,
    close: (Page) -> Unit,
) {
    val page = session.page
    var title by remember { mutableStateOf(page.title) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = ivory,
        title = { Title(name) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (name) {
                    "Title" ->
                        OutlinedTextField(
                            title,
                            { title = it },
                            label = { Text("Entry title") },
                            singleLine = true,
                        )
                    "Appearance" -> {
                        Appearance(
                            settings,
                            page.paper.ifBlank { settings["paper"] ?: "Blank" },
                            { session.update(page.copy(paper = it)) },
                            session.book::setting,
                        )
                        FontOptions(session.book, settings, includeDisplay = false)
                    }
                    "Template & chapter" -> {
                        Title("Entry template")
                        FlowRow {
                            templates.forEach { (name, prompt) ->
                                Tag(name, name == page.template) {
                                    session.update(
                                        page.copy(
                                            template = name,
                                            body =
                                                if (
                                                    page.body.isBlank() ||
                                                        page.body == templates[page.template]
                                                )
                                                    prompt
                                                else page.body,
                                        )
                                    )
                                }
                            }
                        }
                        Title("Chapter")
                        Tag("Month of entry", page.chapter == null) {
                            session.update(page.copy(chapter = null))
                        }
                        chapters.forEach { c ->
                            Tag(c.name, page.chapter == c.id) {
                                session.update(page.copy(chapter = c.id))
                            }
                        }
                    }
                    "Delete entry" ->
                        Text("Delete this entry and its notebook copies of attachments?")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !session.locked,
                onClick = {
                    when (name) {
                        "Title" -> {
                            session.update(page.copy(title = title))
                            dismiss()
                        }
                        "Delete entry" -> {
                            session.locked = true
                            val result = kotlinx.coroutines.CompletableDeferred<Unit>()
                            session.book.queue {
                                try {
                                    session.book.dao.removePage(page.id)
                                    page.files().forEach {
                                        File(mediaDir(session.book), it).delete()
                                    }
                                    result.complete(Unit)
                                } catch (e: Exception) {
                                    result.completeExceptionally(e)
                                }
                            }
                            scope.launch {
                                try {
                                    result.await()
                                    dismiss()
                                    close(page)
                                } catch (e: Exception) {
                                    session.error = "Couldn’t delete this entry."
                                } finally {
                                    session.locked = false
                                }
                            }
                        }
                        else -> dismiss()
                    }
                },
            ) {
                Text(if (name == "Delete entry") "Delete" else "Done")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
internal fun AttachmentPreview(session: WritingSession, name: String, dismiss: () -> Unit) {
    val file = File(mediaDir(session.book), name)
    var removing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = dismiss) {
        Column(Modifier.background(ivory, round).padding(12.dp)) {
            MediaMount(Modifier.fillMaxWidth().heightIn(max = 360.dp).fillMaxHeight(.55f)) {
                if (file.extension == "video")
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(file.path)
                                setMediaController(
                                    android.widget.MediaController(ctx).also {
                                        it.setAnchorView(this)
                                    }
                                )
                                start()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                else Thumbnail(file, Modifier.fillMaxSize(), large = true)
            }
            Row {
                TextButton(onClick = { removing = true }) { Text("Remove attachment") }
                TextButton(onClick = dismiss) { Text("Close") }
            }
        }
    }
    if (removing)
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Title("Remove this attachment?") },
            text = { Text("Remove the notebook copy and its place links?") },
            confirmButton = {
                TextButton(
                    enabled = !session.locked,
                    onClick = {
                        session.locked = true
                        val art = session.layout.copy(prints = session.layout.prints - name)
                        val updated =
                            session.page.copy(
                                media =
                                    session.page.files().filter { it != name }.joinToString("\n"),
                                mediaPositions = session.page.withMediaPosition(name, null),
                                notebookLayout = art.encode(),
                                draft = true,
                            )
                        val result = CompletableDeferred<Unit>()
                        session.book.queue {
                            try {
                                session.book.dao.saveAfterRemovingAttachment(updated, name)
                                file.delete()
                                result.complete(Unit)
                            } catch (e: Exception) {
                                result.completeExceptionally(e)
                            }
                        }
                        scope.launch {
                            try {
                                result.await()
                                session.locked = false
                                session.past.clear()
                                session.future.clear()
                                session.update(updated, art, record = false)
                                dismiss()
                            } catch (e: Exception) {
                                session.error = "Couldn’t remove the attachment."
                            } finally {
                                session.locked = false
                            }
                        }
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
        )
}
