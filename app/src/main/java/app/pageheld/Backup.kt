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

private val signature = "PAGEHELD1".toByteArray()
private const val limit = 256L * 1024 * 1024
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
    )

internal suspend fun backup(book: Notebook, uri: Uri, password: String) {
    val (pages, chapters, locations) =
        book.db.withTransaction {
            Triple(
                book.dao.pages().first(),
                book.dao.chapters().first(),
                book.dao.locations().first().groupBy { it.pageId },
            )
        }
    val manifest =
        JSONObject()
            .put("pages", JSONArray(pages.map { it.json(locations[it.id].orEmpty()) }))
            .put(
                "chapters",
                JSONArray(
                    chapters.map {
                        JSONObject()
                            .put("id", it.id)
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
        pages
            .flatMap { it.files() }
            .distinct()
            .map {
                require(filename.matches(it))
                File(mediaDir(book), it)
            }
    require(manifest.size + files.sumOf { it.length() } <= limit) { "Backup exceeds 256 MB" }
    val random = SecureRandom()
    val salt = ByteArray(16).also(random::nextBytes)
    require(manifest.size <= 32 * 1024 * 1024)
    requireNotNull(book.context.contentResolver.openOutputStream(uri, "wt")).use { out ->
        out.write(signature)
        out.write(salt)
        ZipOutputStream(archiveCipher(password, salt).newEncryptingStream(out, signature)).use { zip
            ->
            zip.putNextEntry(ZipEntry("journal.json"))
            zip.write(manifest)
            zip.closeEntry()
            files.forEach {
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
        requireNotNull(book.context.contentResolver.openInputStream(uri)).use { stream ->
            val input = DataInputStream(stream)
            fun bytes(n: Int) = ByteArray(n).also(input::readFully)
            require(bytes(signature.size).contentEquals(signature))
            archiveCipher(password, bytes(16)).newDecryptingStream(input, signature).use { decrypted
                ->
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
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val member = zip.nextEntry ?: break
                require(member.name == "journal.json" || filename.matches(member.name))
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
        val chapterArray = obj.getJSONArray("chapters")
        val pageArray = obj.getJSONArray("pages")
        val existing = book.dao.pages().first().map { it.id }.toSet()
        val chapterIds = book.dao.chapters().first().map { it.id }.toMutableSet()
        book.db.withTransaction {
            for (i in 0 until chapterArray.length()) {
                val c = chapterArray.getJSONObject(i)
                val id = c.getString("id")
                if (chapterIds.add(id))
                    book.dao.save(
                        Chapter(id, c.getString("name"), c.getInt("cover"), c.getLong("position"))
                    )
            }
            for (i in 0 until pageArray.length()) {
                val row = pageArray.getJSONObject(i)
                val p = row.page()
                if (p.id in existing) continue
                val names =
                    p.files().map { name ->
                        require(filename.matches(name))
                        val source = File(staging, name)
                        require(source.isFile)
                        val dest = File(mediaDir(book), uid() + "." + source.extension)
                        moved.add(dest)
                        source.copyTo(dest)
                        dest.name
                    }
                val remap = p.files().zip(names).toMap()
                val placements = p.mediaPositionsByFile()
                book.dao.save(
                    p.copy(
                        media = names.joinToString("\n"),
                        notebookLayout = remapLayout(p.notebookLayout, remap),
                        mediaPositions = remap.mapNotNull { (old, new) ->
                            placements[old]?.let { new to it }
                        }.toMap().encodeMediaPositions(),
                        chapter = p.chapter?.takeIf { it in chapterIds },
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
                                id = v.optString("id").ifBlank { uid() },
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
        }
        moved.clear()
        obj.optJSONObject("settings")?.let { s ->
            val settings =
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
                            ) || Regex("mood[.][0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it)
                    }
                    .associateWith { s.getString(it) }
            book.applySettings(settings)
        }
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
            TextButton(enabled = !busy, onClick = { export.launch("Pageheld.phb") }) {
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
