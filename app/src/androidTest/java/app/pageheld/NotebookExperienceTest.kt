package app.pageheld

import android.graphics.Bitmap
import android.graphics.Rect
import android.location.Address
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    fun bilingualPageSupportsMovingAndRemovingAttachments() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build()
        val book = Notebook(context, db)
        val photo = File(mediaDir(book), uid() + ".photo")
        Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).let { image ->
            photo.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        val page =
            Page(
                title = "산책 · A quiet walk",
                body = "바람이 부는 오후, 천천히 걸었다.\n\nA little time to write and remember.",
                media = photo.name,
            )
        try {
            runBlocking {
                book.dao.saveLocation(
                    page,
                    DiaryLocation(pageId = page.id, position = 0, name = "Park", media = photo.name),
                )
            }
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
                val hint = "Hold and drag a photo or video to place it on the page."
                assertTrue(hint in visible)
                if (InstrumentationRegistry.getArguments().getString("capture") == "true") {
                    val screenshot = requireNotNull(automation.takeScreenshot())
                    File(context.cacheDir, "bilingual-review.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
                fun photoBounds(node: AccessibilityNodeInfo?): Rect? {
                    if (node == null) return null
                    if (node.contentDescription?.toString() == "Attached photograph")
                        return Rect().also(node::getBoundsInScreen)
                    return (0 until node.childCount).firstNotNullOfOrNull {
                        photoBounds(node.getChild(it))
                    }
                }
                val bounds = requireNotNull(photoBounds(automation.rootInActiveWindow))
                val start = SystemClock.uptimeMillis()
                fun touch(action: Int, step: Int) {
                    val event = MotionEvent.obtain(
                        start, SystemClock.uptimeMillis(), action,
                        bounds.exactCenterX() + step * bounds.width() / 7f,
                        bounds.exactCenterY() - step * bounds.height() / 9f,
                        0,
                    )
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        assertTrue(automation.injectInputEvent(event, true))
                    } finally {
                        event.recycle()
                    }
                }
                touch(MotionEvent.ACTION_DOWN, 0)
                SystemClock.sleep(700)
                for (step in 1..5) {
                    touch(MotionEvent.ACTION_MOVE, step)
                    SystemClock.sleep(30)
                }
                touch(MotionEvent.ACTION_UP, 5)
                val saved = runBlocking {
                    withTimeout(5_000) {
                        book.dao.pages().first { it.any { page -> page.mediaPositions.isNotBlank() } }
                            .single().mediaPositionsByFile().getValue(photo.name)
                    }
                }
                assertTrue(saved.across > .08f)
                automation.waitForIdle(250, 5000)
                assertFalse(hint in texts(automation.rootInActiveWindow))

                fun tap(bounds: Rect) {
                    automation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
                        .use { descriptor ->
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
                        }
                    automation.waitForIdle(250, 5000)
                }
                fun tapLabel(label: String) {
                    val node = requireNotNull(automation.rootInActiveWindow)
                        .findAccessibilityNodeInfosByText(label)
                        .first { it.text?.toString() == label }
                    tap(Rect().also(node::getBoundsInScreen))
                }
                tap(requireNotNull(photoBounds(automation.rootInActiveWindow)))
                tapLabel("Remove attachment")
                tapLabel("Remove")
                val updated = runBlocking {
                    withTimeout(5_000) {
                        book.dao.pages().first { it.single().files().isEmpty() }.single()
                    }
                }
                assertEquals(page.body, updated.body)
                assertTrue(updated.mediaPositionsByFile().isEmpty())
                assertEquals("", runBlocking { book.dao.locationsFor(page.id).single().media })
                val deletionDeadline = SystemClock.elapsedRealtime() + 5000
                while (photo.exists() && SystemClock.elapsedRealtime() < deletionDeadline) {
                    SystemClock.sleep(50)
                }
                assertFalse(photo.exists())
            }
        } finally {
            photo.delete()
            db.close()
        }
    }
}
