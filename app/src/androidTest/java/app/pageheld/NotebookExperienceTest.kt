package app.pageheld

import android.graphics.Bitmap
import android.location.Address
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookExperienceTest {
    @Test
    fun addressResultsRetainNamesAndCoordinates() {
        val city =
            Address(Locale.KOREAN).apply {
                locality = "서울"
                latitude = 37.5665
                longitude = 126.9780
                setAddressLine(0, "서울특별시, 대한민국")
            }
        assertEquals(
            listOf(PlaceResult("서울", "서울특별시, 대한민국", 37.5665, 126.9780)),
            searchResults(listOf(city, city, Address(Locale.KOREAN)), "Seoul"),
        )
    }

    @Test
    fun bilingualWritingPageRendersInTheEditor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build()
        val book = Notebook(context, db)
        val page =
            Page(
                title = "산책 · A quiet walk",
                body = "바람이 부는 오후, 천천히 걸었다.\n\nA little time to write and remember.",
            )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity { host ->
                    host.setContent {
                        MaterialTheme {
                            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                Editor(
                                    page,
                                    book,
                                    emptyList(),
                                    emptyMap(),
                                    emptyList(),
                                    null,
                                    null,
                                ) {}
                            }
                        }
                    }
                }
                val automation = instrumentation.uiAutomation
                automation.waitForIdle(250, 5000)
                fun texts(node: AccessibilityNodeInfo?): List<String> {
                    if (node == null) return emptyList()
                    return buildList {
                        node.text?.let { add(it.toString()) }
                        for (i in 0 until node.childCount) addAll(texts(node.getChild(i)))
                    }
                }
                val deadline = SystemClock.elapsedRealtime() + 5000
                var visible = texts(automation.rootInActiveWindow)
                while (page.body !in visible && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(100)
                    visible = texts(automation.rootInActiveWindow)
                }
                assertTrue("Bilingual title is visible", page.title in visible)
                assertTrue("Bilingual body is visible", page.body in visible)
                if (InstrumentationRegistry.getArguments().getString("capture") == "true") {
                    val screenshot = requireNotNull(automation.takeScreenshot())
                    File(context.cacheDir, "bilingual-review.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
            }
        } finally {
            db.close()
        }
    }
}
