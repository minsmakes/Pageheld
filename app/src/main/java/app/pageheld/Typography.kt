package app.pageheld

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*

private const val preferredFont = "몽글동심"
private val builtInFonts = linkedMapOf(
    preferredFont to FontFamily(Font(R.font.ownglyph_mongmongdays)),
    "Handwriting" to FontFamily(Font(R.font.kalam)),
    "Serif" to serif,
    "Sans" to FontFamily.SansSerif,
)
private val fontLabel = mapOf(preferredFont to "몽글동심", "Handwriting" to "Kalam", "Serif" to "Libre Caslon", "Sans" to "System sans")
internal val LocalDisplayFont = staticCompositionLocalOf { builtInFonts.getValue(preferredFont) }
internal val LocalDisplayScale = staticCompositionLocalOf { .9f }

@Composable
internal fun uiSp(size: Number) = (size.toFloat() * LocalDisplayScale.current).sp

internal fun writingSize(settings: Map<String, String>) =
    settings["writingSize"]?.toIntOrNull()?.coerceIn(12, 34) ?: 18

internal fun displaySize(settings: Map<String, String>) =
    settings["displaySize"]?.toIntOrNull()?.coerceIn(75, 125) ?: 100

private fun Context.fontDirectory() = File(filesDir, "fonts")

private fun Context.importedFonts() = fontDirectory().listFiles().orEmpty()
    .filter { it.isFile && "--" in it.name && it.extension in listOf("ttf", "otf") }
    .sortedBy { it.name.substringAfter("--").lowercase() }

@Composable
internal fun notebookFont(context: Context, selection: String?): FontFamily =
    remember(context, selection) {
        builtInFonts[selection] ?: context.importedFonts()
            .firstOrNull { it.name == selection }?.let { file ->
                runCatching { FontFamily(Font(file)) }.getOrNull()
            } ?: builtInFonts.getValue(preferredFont)
    }

internal fun displayTypography(family: FontFamily, scale: Float): Typography {
    fun TextStyle.applyFont() = copy(
        fontFamily = family,
        fontSynthesis = FontSynthesis.None,
        fontWeight = FontWeight.Normal,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
    val defaults = Typography()
    return Typography(
        displayLarge = defaults.displayLarge.applyFont(), displayMedium = defaults.displayMedium.applyFont(),
        displaySmall = defaults.displaySmall.applyFont(), headlineLarge = defaults.headlineLarge.applyFont(),
        headlineMedium = defaults.headlineMedium.applyFont(), headlineSmall = defaults.headlineSmall.applyFont(),
        titleLarge = defaults.titleLarge.applyFont(), titleMedium = defaults.titleMedium.applyFont(),
        titleSmall = defaults.titleSmall.applyFont(), bodyLarge = defaults.bodyLarge.applyFont(),
        bodyMedium = defaults.bodyMedium.applyFont(), bodySmall = defaults.bodySmall.applyFont(),
        labelLarge = defaults.labelLarge.applyFont(), labelMedium = defaults.labelMedium.applyFont(),
        labelSmall = defaults.labelSmall.applyFont(),
    )
}

internal fun importFont(context: Context, uri: Uri): File {
    val resolver = context.contentResolver
    val originalName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
    val name = originalName.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} ._()-]"), " ")
        .trim().take(65).ifBlank { "Imported font" }
    val directory = context.fontDirectory()
    directory.mkdirs()
    val pending = File.createTempFile("font-", ".tmp", directory)
    try {
        val extension = requireNotNull(resolver.openInputStream(uri)).buffered().use { input ->
            val header = ByteArray(4)
            java.io.DataInputStream(input).readFully(header)
            val format = when {
                header.contentEquals(byteArrayOf(0, 1, 0, 0)) -> "ttf"
                header.contentEquals("OTTO".toByteArray(Charsets.US_ASCII)) -> "otf"
                else -> throw IOException("Unsupported font format")
            }
            pending.outputStream().buffered().use { output ->
                output.write(header)
                val buffer = ByteArray(8192)
                var count = header.size
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    count += read
                    if (count > 32 * 1024 * 1024) throw IOException("Font exceeds size limit")
                    output.write(buffer, 0, read)
                }
            }
            format
        }
        if (android.graphics.Typeface.Builder(pending).build() == null) throw IOException("Unreadable font")
        val destination = File(directory, "${UUID.randomUUID()}--$name.$extension")
        if (!pending.renameTo(destination)) throw IOException("Could not store font")
        return destination
    } finally {
        pending.delete()
    }
}

@Composable
internal fun FontOptions(book: Notebook, settings: Map<String, String>, includeDisplay: Boolean = true) {
    var imported by remember(book) { mutableStateOf(book.context.importedFonts()) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            message = ""
            try {
                val file = withContext(Dispatchers.IO) { importFont(book.context, uri) }
                imported = book.context.importedFonts()
                message = "${file.name.substringAfter("--").substringBeforeLast('.')} added. Select it below."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn’t open that font. Choose a valid TTF or OTF file up to 32 MB."
            } finally {
                importing = false
            }
        }
    }

    TextButton(enabled = !importing, onClick = { picker.launch(arrayOf("*/*")) }) {
        Text(if (importing) "Importing font…" else "Add font file")
    }
    if (message.isNotBlank()) Note(message)
    val options = fontLabel + imported.associate { it.name to it.name.substringAfter("--").substringBeforeLast('.') }
    if (includeDisplay) {
        FontChoice("Display font", settings["displayFont"] ?: preferredFont, options) { book.setting("displayFont", it) }
        SizeChoice("Display text", displaySize(settings), 75..125, 5, "%") {
            book.setting("displaySize", it.toString())
        }
    }
    FontChoice("Writing font", settings["font"] ?: preferredFont, options) { book.setting("font", it) }
    SizeChoice("Writing text", writingSize(settings), 12..34, 1, " sp") {
        book.setting("writingSize", it.toString())
    }
    Text(
        "오늘의 기록 · A quiet page",
        fontFamily = notebookFont(book.context, settings["font"] ?: preferredFont),
        style = LocalTextStyle.current.copy(fontSynthesis = FontSynthesis.None),
        fontSize = writingSize(settings).sp,
        lineHeight = (writingSize(settings) * 1.5f).sp,
    )
}

@Composable
private fun FontChoice(label: String, selection: String?, options: Map<String, String>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label · ${options[selection] ?: preferredFont}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = ivory) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; select(id) })
            }
        }
    }
}

@Composable
private fun SizeChoice(label: String, current: Int, range: IntRange, step: Int, suffix: String, save: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("$label · $current$suffix", Modifier.weight(1f))
        OutlinedButton(onClick = { save((current - step).coerceIn(range)) }, enabled = current > range.first,
            modifier = Modifier.padding(end = 6.dp).sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text("−", Modifier.sizeControl("Decrease $label size"))
        }
        OutlinedButton(onClick = { save((current + step).coerceIn(range)) }, enabled = current < range.last,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text("+", Modifier.sizeControl("Increase $label size"))
        }
    }
}

private fun Modifier.sizeControl(label: String) = semantics { contentDescription = label }

