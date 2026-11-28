package app.pageheld

import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LibraryAppearanceTest {
    @Test
    fun themeChangesLeaveLibraryDesignsIntact() {
        val runner = InstrumentationRegistry.getInstrumentation()
        val ctx = runner.targetContext
        assumeTrue(
            BuildConfig.FLAVOR == "tablet" &&
                ctx.resources.configuration.smallestScreenWidthDp >= 600
        )
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val book = Notebook(ctx, db)
        val original = runBlocking { book.settings.first()["notebookTheme"] ?: "Sage" }
        val before =
            LibraryBook(
                title = "Quiet garden",
                front =
                    CoverDesign.read("{}")
                        .copy(
                            color = 0xff93adbf.toInt(),
                            layers = listOf(CoverLayer(kind = "art", asset = "leaf", width = .34f)),
                        )
                        .encode(),
            )
        try {
            runBlocking { book.library.append(before) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { host -> host.setContent { Pageheld(book) } }
                for (palette in NotebookPalette.entries) {
                    runBlocking { book.applySettings(mapOf("notebookTheme" to palette.id)) }
                    val end = SystemClock.elapsedRealtime() + 10000
                    var matched = false
                    while (!matched && SystemClock.elapsedRealtime() < end) {
                        runner.uiAutomation.waitForIdle(100, 1200)
                        val image = runner.uiAutomation.takeScreenshot() ?: continue
                        try {
                            fun contains(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                                if(node==null) return false
                                return node.contentDescription?.toString()==before.title ||
                                    (0 until node.childCount).any {contains(node.getChild(it))}
                            }
                            if(android.os.Build.VERSION.SDK_INT>=33) runner.uiAutomation.clearCache()
                            val hasBook=contains(runner.uiAutomation.rootInActiveWindow)
                            val rgb=palette.background.toArgb()
                            matched=hasBook &&
                                    (0 until image.height / 3).any { y ->
                                        image.getPixel(image.width / 4, y) == rgb
                                    }
                            if (matched && runner.argumentsForCapture())
                                File(ctx.cacheDir, "library-" + palette.id + ".png")
                                    .outputStream()
                                    .use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } finally {
                            image.recycle()
                        }
                        if (!matched) SystemClock.sleep(100)
                    }
                    assertTrue("Rendered library with ${palette.id} header", matched)
                    assertEquals(
                        before.front,
                        runBlocking { book.library.books().first().single().front },
                    )
                }
            }
        } finally {
            runBlocking { book.applySettings(mapOf("notebookTheme" to original)) }
            db.close()
        }
    }

    private fun android.app.Instrumentation.argumentsForCapture() =
        InstrumentationRegistry.getArguments().getString("captureLibrary") == "true"
}
