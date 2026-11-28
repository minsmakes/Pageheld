package app.pageheld

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.room.withTransaction
import java.io.*
import java.security.SecureRandom
import java.util.zip.*
import javax.crypto.*
import javax.crypto.spec.*
import kotlinx.coroutines.flow.first
import org.json.*

private val legacySignature = "PAGEHELD1".toByteArray()
private val signature = "MEMOSPNE2".toByteArray()
private const val limit = 256L * 1024 * 1024
private val fontName = Regex("[0-9a-fA-F-]{36}--[\\p{L}\\p{N} ._()-]{1,65}\\.(ttf|otf)")
private val filename = Regex("[0-9a-fA-F-]{36}\\.(photo|video)")

private fun archiveCipher(password: String, salt: ByteArray): com.google.crypto.tink.StreamingAead {
    val spec = PBEKeySpec(password.toCharArray(), salt, 310000, 256)
    val key =
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    return try {
        com.google.crypto.tink.subtle.AesGcmHkdfStreaming(key, "HmacSha256", 32, 1024 * 1024, 0)
    } finally {
        key.fill(0)
    }
}

private fun Page.json(locations: List<DiaryLocation>) =
    JSONObject()
        .put("id", id)
        .put("bookId", bookId)
        .put("created", created)
        .put("title", title)
        .put("body", body)
        .put("template", template)
        .put("paper", paper)
        .put("mood", mood)
        .put("place", locations.firstOrNull()?.name.orEmpty())
        .put("lat", locations.firstOrNull()?.lat)
        .put("lon", locations.firstOrNull()?.lon)
        .put("chapter", chapter)
        .put("media", media)
        .put("starred", starred)
        .put("mediaPositions", mediaPositions)
        .put("weather", weather)
        .put("notebookLayout", notebookLayout)
        .put("draft", draft)
        .put(
            "locations",
            JSONArray(
                locations.map { l ->
                    JSONObject()
                        .put("id", l.id)
                        .put("name", l.name)
                        .put("lat", l.lat)
                        .put("lon", l.lon)
                        .put("visited", l.visited)
                        .put("notes", l.notes)
                        .put("media", l.media)
                }
            ),
        )

private fun JSONObject.page() =
    Page(
        getString("id"),
        getLong("created"),
        getString("title"),
        getString("body"),
        getString("template"),
        getString("paper"),
        getString("mood"),
        getString("place"),
        if (has("lat")) getDouble("lat") else null,
        if (has("lon")) getDouble("lon") else null,
        if (has("chapter")) getString("chapter") else null,
        getString("media"),
        getBoolean("starred"),
        optString("mediaPositions"),
        optString("weather"),
        optString("notebookLayout"),
        optBoolean("draft"),
        optString("bookId", LEGACY_BOOK),
    )

internal suspend fun backup(book: Notebook, uri: Uri, password: String) {
    val (books, content) =
        book.db.withTransaction {
            book.library.books().first() to
                Triple(
                    book.dao.pages().first(),
                    book.dao.chapters().first(),
                    book.dao.locations().first().groupBy { it.pageId },
                )
        }
    val (pages, chapters, locations) = content
    val allBooks =
        if (
            books.none { it.id == LEGACY_BOOK } &&
                (pages.any { it.bookId == LEGACY_BOOK } ||
                    chapters.any { it.bookId == LEGACY_BOOK })
        )
            books + LibraryBook(id = LEGACY_BOOK, title = "Memospine.")
        else books
    val fonts = book.context.importedFonts().filter { fontName.matches(it.name) }
    val manifest =
        JSONObject()
            .put("fonts", JSONArray(fonts.map { it.name }))
            .put("version", 2)
            .put(
                "books",
                JSONArray(
                    allBooks.map { b ->
                        JSONObject()
                            .put("id", b.id)
                            .put("title", b.title)
                            .put("position", b.position)
                            .put("created", b.created)
                            .put("modified", b.modified)
                            .put("front", JSONObject(b.front))
                            .put("back", JSONObject(b.back))
                            .put("readingPage", b.readingPage)
                    }
                ),
            )
            .put("pages", JSONArray(pages.map { it.json(locations[it.id].orEmpty()) }))
            .put(
                "chapters",
                JSONArray(
                    chapters.map {
                        JSONObject()
                            .put("id", it.id)
                            .put("bookId", it.bookId)
                            .put("name", it.name)
                            .put("cover", it.cover)
                            .put("position", it.position)
                    }
                ),
            )
            .put("settings", JSONObject(book.settings.first()))
            .toString()
            .toByteArray()
    val files =
        (pages.flatMap { p ->
                p.files() +
                    DiaryLayout.read(p)
                        .decorations
                        .values
                        .flatten()
                        .filter { it.kind == "photo" }
                        .map { it.asset }
            } +
                allBooks.flatMap { b ->
                    listOf(b.front, b.back).flatMap { raw ->
                        CoverDesign.read(raw).layers.filter { it.kind == "photo" }.map { it.asset }
                    }
                })
            .distinct()
            .map {
                require(filename.matches(it))
                File(mediaDir(book), it)
            }
    require(manifest.size + fonts.sumOf { it.length() } + files.sumOf { it.length() } <= limit) {
        "Backup exceeds 256 MB"
    }
    val random = SecureRandom()
    val salt = ByteArray(16).also(random::nextBytes)
    require(manifest.size <= 32 * 1024 * 1024)
    require(files.all { it.isFile })
    requireNotNull(book.context.contentResolver.openOutputStream(uri, "wt")).use { out ->
        out.write(signature)
        out.write(salt)
        ZipOutputStream(archiveCipher(password, salt).newEncryptingStream(out, signature)).use { zip
            ->
            zip.putNextEntry(ZipEntry("journal.json"))
            zip.write(manifest)
            zip.closeEntry()
            (files + fonts).forEach {
                zip.putNextEntry(ZipEntry(it.name))
                it.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

internal suspend fun restore(book: Notebook, uri: Uri, password: String) {
    val staging = File(book.context.cacheDir, uid()).apply { mkdirs() }
    val moved = mutableListOf<File>()
    try {
        val archive = File(staging, "archive")
        var legacy = false
        requireNotNull(book.context.contentResolver.openInputStream(uri)).use { stream ->
            val input = DataInputStream(stream)
            fun bytes(n: Int) = ByteArray(n).also(input::readFully)
            val header = bytes(signature.size)
            legacy = header.contentEquals(legacySignature)
            require(legacy || header.contentEquals(signature))
            archiveCipher(password, bytes(16)).newDecryptingStream(input, header).use { decrypted ->
                archive.outputStream().use { out ->
                    val block = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        val n = decrypted.read(block)
                        if (n < 0) break
                        total += n
                        require(total <= limit + 1024 * 1024)
                        out.write(block, 0, n)
                    }
                }
            }
        }
        var total = 0L
        var members = 0
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val member = zip.nextEntry ?: break
                require(++members <= 65536)
                require(
                    member.name == "journal.json" ||
                        filename.matches(member.name) ||
                        !legacy && fontName.matches(member.name)
                )
                val f = File(staging, member.name)
                require(!f.exists())
                f.outputStream().use { out ->
                    val block = ByteArray(32768)
                    while (true) {
                        val n = zip.read(block)
                        if (n < 0) break
                        total += n
                        require(total <= limit)
                        out.write(block, 0, n)
                    }
                }
            }
        }
        val manifest = File(staging, "journal.json")
        require(manifest.length() <= 32 * 1024 * 1024)
        val obj = JSONObject(manifest.readText())
        require(if (legacy) !obj.has("version") else obj.getInt("version") == 2)
        val expectedFonts = obj.optJSONArray("fonts")
        val fonts =
            List(expectedFonts?.length() ?: 0) { index -> expectedFonts!!.getString(index) }
                .distinct()
        val catalog = readCatalog(book.context).associateBy { it.id }
        val chapterArray = obj.getJSONArray("chapters")
        val pageArray = obj.getJSONArray("pages")
        val preferences =
            obj.optJSONObject("settings")
                ?.let { s ->
                    s.keys()
                        .asSequence()
                        .filter {
                            it in
                                listOf(
                                    "paper",
                                    "tone",
                                    "notebookTheme",
                                    "font",
                                    "spacing",
                                    "margin",
                                    "openingCover",
                                    "reduceMotion",
                                    "writingSize",
                                    "displaySize",
                                    "displayFont",
                                    "owner",
                                    "shelfPage",
                                    "activeBook",
                                    "favoritePens",
                                    "fingerInk",
                                    "lastPen",
                                ) || Regex("mood[.][0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it)
                        }
                        .associateWith { s.getString(it) }
                }
                .orEmpty()
        val mapped = mutableMapOf<String, String>()
        fun copyFile(name: String): String =
            mapped.getOrPut(name) {
                require(filename.matches(name))
                val source = File(staging, name)
                require(source.isFile)
                val dest = File(mediaDir(book), uid() + "." + source.extension)
                moved.add(dest)
                source.copyTo(dest)
                dest.name
            }
        fun cover(raw: String, reverse: Boolean): String {
            val design = CoverDesign.read(raw, reverse)
            require(catalog[design.material]?.category == "materials")
            return design
                .copy(
                    layers =
                        design.layers.map { layer ->
                            when (layer.kind) {
                                "photo" -> layer.copy(asset = copyFile(layer.asset))
                                "art" -> layer.also { require(catalog.containsKey(it.asset)) }
                                else -> layer
                            }
                        }
                )
                .encode()
        }
        book.db.withTransaction {
            for (name in fonts) {
                require(fontName.matches(name))
                val source = File(staging, name)
                require(source.isFile && source.length() in 1..(32L * 1024 * 1024))
                val dest = File(book.context.fontDirectory(), name)
                if (!dest.exists()) {
                    require(android.graphics.Typeface.Builder(source).build() != null)
                    dest.parentFile?.mkdirs()
                    moved.add(dest)
                    source.copyTo(dest)
                }
            }
            val seenPages = mutableSetOf<String>()
            val seenChapters = mutableSetOf<String>()
            val existing = book.dao.pages().first().associate { it.id to it.bookId }.toMutableMap()
            val chapters =
                book.dao.chapters().first().associate { it.id to it.bookId }.toMutableMap()
            val localBooks = book.library.books().first().associateBy { it.id }
            val owners = mutableSetOf<String>()
            val locationIds = book.dao.locations().first().map { it.id }.toMutableSet()
            if (legacy) {
                owners.add(LEGACY_BOOK)
                book.library.ensure(LibraryBook(id = LEGACY_BOOK, title = "Memospine."))
            } else {
                val array = obj.getJSONArray("books")
                for (i in 0 until array.length()) {
                    val b = array.getJSONObject(i)
                    val id = b.getString("id")
                    require(id.isNotBlank() && owners.add(id))
                    val local = localBooks[id]
                    if (
                        local == null ||
                            id == LEGACY_BOOK &&
                                local.front == "{}" &&
                                local.back == "{}" &&
                                local.title == "Memospine."
                    ) {
                        val f = cover(b.getJSONObject("front").toString(), false)
                        val r = cover(b.getJSONObject("back").toString(), true)
                        if (local == null)
                            book.library.insert(
                                LibraryBook(
                                    id,
                                    b.getString("title"),
                                    b.getLong("created"),
                                    b.getLong("modified"),
                                    b.getLong("position"),
                                    f,
                                    r,
                                    b.optString("readingPage"),
                                )
                            )
                        else
                            book.library.design(
                                id,
                                b.getString("title"),
                                f,
                                r,
                                b.getLong("modified"),
                            )
                    }
                }
            }
            for (i in 0 until chapterArray.length()) {
                val c = chapterArray.getJSONObject(i)
                val id = c.getString("id")
                require(seenChapters.add(id))
                val owner = c.optString("bookId", LEGACY_BOOK)
                require(owner in owners && (id !in chapters || chapters[id] == owner))
                if (chapters.put(id, owner) == null)
                    book.dao.save(
                        Chapter(
                            id,
                            c.getString("name"),
                            c.getInt("cover"),
                            c.getLong("position"),
                            owner,
                        )
                    )
            }
            for (i in 0 until pageArray.length()) {
                val row = pageArray.getJSONObject(i)
                val p = row.page()
                require(seenPages.add(p.id))
                val layout = DiaryLayout.read(p)
                require(layout.decorations.keys.all { it in layout.sheetIds })
                layout.decorations.values.flatten().forEach { layer ->
                    if (layer.kind == "art") require(catalog.containsKey(layer.asset))
                    if (layer.kind == "photo") require(File(staging, layer.asset).isFile)
                }
                require(p.files().all { filename.matches(it) && File(staging, it).isFile })
                require(p.bookId in owners && (p.id !in existing || existing[p.id] == p.bookId))
                require(p.chapter == null || chapters[p.chapter] == p.bookId)
                if (existing.put(p.id, p.bookId) != null) continue
                val names = p.files().map(::copyFile)
                val remap =
                    p.files().zip(names).toMap() +
                        DiaryLayout.read(p)
                            .decorations
                            .values
                            .flatten()
                            .filter { it.kind == "photo" }
                            .associate { it.asset to copyFile(it.asset) }
                val placements = p.mediaPositionsByFile()
                book.dao.save(
                    p.copy(
                        media = names.joinToString("\n"),
                        notebookLayout = remapLayout(p.notebookLayout, remap),
                        mediaPositions =
                            remap
                                .mapNotNull { (old, new) -> placements[old]?.let { new to it } }
                                .toMap()
                                .encodeMediaPositions(),
                        chapter = p.chapter,
                    )
                )
                val places = row.optJSONArray("locations")
                if (places != null) {
                    for (index in 0 until places.length()) {
                        val v = places.getJSONObject(index)
                        val linked =
                            v.optString("media").lines().filter(String::isNotBlank).map {
                                remap.getValue(it)
                            }
                        book.dao.writeLocation(
                            DiaryLocation(
                                id =
                                    v.optString("id")
                                        .ifBlank { uid() }
                                        .also { require(locationIds.add(it)) },
                                pageId = p.id,
                                position = index,
                                name = v.getString("name"),
                                lat = if (v.has("lat")) v.getDouble("lat") else null,
                                lon = if (v.has("lon")) v.getDouble("lon") else null,
                                visited = if (v.has("visited")) v.getLong("visited") else null,
                                notes = v.optString("notes"),
                                media = linked.joinToString("\n"),
                            )
                        )
                    }
                } else if (p.place.isNotBlank() || (p.lat != null && p.lon != null)) {
                    book.dao.writeLocation(
                        DiaryLocation(
                            pageId = p.id,
                            position = 0,
                            name = p.place,
                            lat = p.lat,
                            lon = p.lon,
                            media = names.joinToString("\n"),
                        )
                    )
                }
            }
            if (preferences.isNotEmpty()) book.applySettings(preferences)
        }
        moved.clear()
    } finally {
        moved.forEach { it.delete() }
        staging.deleteRecursively()
    }
}

@Composable
fun BackupControls(book: Notebook) {
    var mode by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var result by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) {
            uri = it?.toString()
            mode = if (it == null) "" else "Export"
        }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            uri = it?.toString()
            mode = if (it == null) "" else "Import"
        }
    Panel {
        Title("Local backup", 20)
        Note(
            "Encrypted pages, chapters, appearance and attachments. Import adds missing pages and chapters."
        )
        Row {
            TextButton(enabled = !busy, onClick = { export.launch("Memospine.phb") }) {
                Text("Export")
            }
            TextButton(enabled = !busy, onClick = { importFile.launch(arrayOf("*/*")) }) {
                Text("Import")
            }
        }
        if (result.isNotEmpty()) Note(result)
    }
    if (mode.isNotBlank() && uri != null)
        AlertDialog(
            onDismissRequest = {
                mode = ""
                pass = ""
            },
            title = { Title("$mode notebook") },
            text = {
                Column {
                    Note(
                        "Use a passphrase of at least 10 characters. Keep it to reopen this backup."
                    )
                    OutlinedTextField(
                        pass,
                        { pass = it },
                        singleLine = true,
                        label = { Text("Backup passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pass.length >= 10,
                    onClick = {
                        val secret = pass
                        val destination = Uri.parse(uri)
                        val write = mode == "Export"
                        pass = ""
                        mode = ""
                        busy = true
                        result = "Working…"
                        book.queue {
                            val message =
                                try {
                                    if (write) backup(book, destination, secret)
                                    else restore(book, destination, secret)
                                    if (write) "Backup saved." else "Import complete."
                                } catch (e: Exception) {
                                    if (write)
                                        "Export failed. Check available space and the 256 MB limit."
                                    else "Import failed. Check the file and passphrase."
                                }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                result = message
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(mode)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mode = ""
                        pass = ""
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
}
