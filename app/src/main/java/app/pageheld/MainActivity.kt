package app.pageheld

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resources.configuration.smallestScreenWidthDp >= 600) {
            requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        enableEdgeToEdge()
        setContent { Pageheld(Notebook.get(this)) }
    }
}

val tabs = listOf("Today" to "sun", "Diaries" to "book-open", "Moments" to "heart")

@Composable
fun Pageheld(book: Notebook) {
    val preferences by book.settings.collectAsState(initial = null)
    PageheldTheme(book, preferences.orEmpty()) {
        if (preferences != null) {
            if (BuildConfig.FLAVOR == "tablet" && androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600) {
                TabletLibrary(book, preferences.orEmpty())
            } else {
                LaunchedEffect(book) { book.library.ensure(LibraryBook(id=LEGACY_BOOK,title="Memospine.")) }
                val data by remember(book) { book.library.pages(LEGACY_BOOK) }.collectAsState(initial = null)
                val chapters by remember(book) { book.library.chapters(LEGACY_BOOK) }.collectAsState(emptyList())
                NotebookScreens(book, data, chapters, preferences)
            }
        }
    }
}

@Composable
internal fun PageheldTheme(
    book: Notebook,
    prefs: Map<String, String>,
    content: @Composable () -> Unit,
) {
    val family = notebookFont(book.context, prefs["displayFont"])
    val scale = displaySize(prefs) / 100f
    val typography = remember(family, scale) { displayTypography(family, scale) }
    val palette = NotebookPalette.entries.find { it.id == prefs["notebookTheme"] } ?: NotebookPalette.Sage
    CompositionLocalProvider(LocalDisplayFont provides family, LocalDisplayScale provides scale,
        LocalNotebookPalette provides palette) {
        MaterialTheme(
            typography = typography,
            colorScheme =
                lightColorScheme(
                    primary = sage,
                    onPrimary = Color.White,
                    secondary = sage,
                    primaryContainer = pale,
                    background = palette.background,
                    surface = palette.background,
                    onSurface = ink,
                    secondaryContainer = pale,
                ),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val base = LocalDensity.current
                val zoom =
                    if (maxWidth >= 700.dp && maxWidth > maxHeight)
                        minOf(maxWidth / 820.dp, maxHeight / 530.dp).coerceIn(1f, 1.8f)
                    else 1f
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density * zoom, base.fontScale)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun NotebookScreens(
    book: Notebook,
    data: List<Page>?,
    chapters: List<Chapter>,
    preferences: Map<String, String>?,
) {
    val places by remember(book) { book.library.locations(LEGACY_BOOK) }.collectAsState(initial = null)
    LaunchedEffect(book) {
        book.errors.collect { Toast.makeText(book.context, it, Toast.LENGTH_LONG).show() }
    }
    if (data == null || places == null || preferences == null) {
        Box(Modifier.fillMaxSize().background(notebookBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = sage)
        }
        return
    }
    DiaryHome(book, data, chapters, places.orEmpty(), preferences)
}
