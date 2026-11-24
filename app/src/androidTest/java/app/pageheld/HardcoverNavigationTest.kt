package app.pageheld

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HardcoverNavigationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val automation
        get() = instrument.uiAutomation

    private fun find(label: String): AccessibilityNodeInfo? {
        fun visit(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            n ?: return null
            if (
                n.isVisibleToUser &&
                    (n.contentDescription?.toString() == label || n.text?.toString() == label)
            )
                return n
            for (i in 0 until n.childCount) visit(n.getChild(i))?.let {
                return it
            }
            return null
        }
        return visit(automation.rootInActiveWindow)
    }

    private fun node(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            find(label)?.let {
                return it
            }
            SystemClock.sleep(70)
        }
        val labels = mutableListOf<String>()
        fun collect(n: AccessibilityNodeInfo?) {
            n ?: return
            (n.contentDescription ?: n.text)?.let { labels.add(it.toString()) }
            for (i in 0 until n.childCount) collect(n.getChild(i))
        }
        collect(automation.rootInActiveWindow)
        throw AssertionError("Missing control: $label. Visible: " + labels.joinToString())
    }

    private fun bounds(label: String) = Rect().also { node(label).getBoundsInScreen(it) }

    private fun stroke(points: List<Pair<Float, Float>>, pause: Long = 28) {
        val down = SystemClock.uptimeMillis()
        points.forEachIndexed { i, p ->
            val action =
                when (i) {
                    0 -> MotionEvent.ACTION_DOWN
                    points.lastIndex -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
            val pointer =
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            val coords =
                MotionEvent.PointerCoords().apply {
                    x = p.first
                    y = p.second
                    pressure = 1f
                    size = .1f
                }
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    1,
                    arrayOf(pointer),
                    arrayOf(coords),
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    InputDevice.SOURCE_TOUCHSCREEN,
                    0,
                )
            try {
                assertTrue(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            SystemClock.sleep(pause)
        }
    }

    private fun tap(label: String) {
        val r = bounds(label)
        val at = r.exactCenterX() to r.exactCenterY()
        stroke(listOf(at, at))
    }

    @Test
    fun reversibleTurnsAndEditorAutosave() {
        val context = instrument.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build()
        val book = Notebook(context, db)
        try {
            val original = Page(title = "A saved leaf", body = "Original words")
            runBlocking { book.dao.save(original) }
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity { host ->
                    host.setContent {
                        val pages by remember { book.dao.pages() }.collectAsState(initial = null)
                        MaterialTheme {
                            NotebookScreens(
                                book,
                                pages,
                                emptyList(),
                                mapOf("coverLearned" to "true"),
                            )
                        }
                    }
                }
                tap("Open notebook")
                val ribbon = bounds("Bookmarked pages")
                val close = bounds("Close book")
                val density = context.resources.displayMetrics.density
                val x = ribbon.exactCenterX() + 25 * density
                val y = ribbon.bottom + 95 * density
                val width = x - close.exactCenterX()
                stroke(
                    listOf(
                        x to y,
                        (x - width * .12f) to y,
                        (x - width * .20f) to y,
                        (x - 4) to y,
                        (x - 4) to y,
                    )
                )
                SystemClock.sleep(700)
                assertNotNull(node("Mood check-in".uppercase()))
                SystemClock.sleep(600)
                stroke((0..12).map { x - width * .075f * it to y })
                node("Search journal")
                tap("New page")
                val text = "A draft saved from the hardcover."
                SystemClock.sleep(850)
                automation.waitForIdle(200, 4000)
                tap("Page body")
                SystemClock.sleep(600)
                instrument.sendStringSync(text)
                val saved = runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(8_000) {
                        book.dao.pages().first { list -> list.any { it.body == text } }
                    }
                }
                assertNotNull(
                    "Stored bodies: " +
                        runBlocking { book.dao.pages().first() }.map { it.body } +
                        " focused: " +
                        automation.rootInActiveWindow
                            .findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            ?.text,
                    saved,
                )
                tap("Close page")
                assertEquals(
                    original,
                    runBlocking { book.dao.pages().first() }.single { it.id == original.id },
                )
                tap("Close book")
                SystemClock.sleep(750)
                tap("Open notebook")
                node("Close book")
            }
        } finally {
            db.close()
        }
    }
}
