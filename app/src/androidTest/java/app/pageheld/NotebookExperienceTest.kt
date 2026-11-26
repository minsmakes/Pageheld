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
import kotlinx.coroutines.withTimeoutOrNull
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
                notebookLayout =
                    DiaryLayout(prints = mapOf(photo.name to PrintPosition(0))).encode(),
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
                                DiaryEditor(
                                    page,
                                    book,
                                    emptyList(),
                                    emptyList(),
                                    emptyMap(),
                                    false,
                                    "",
                                    {},
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
                assertTrue("Done" in visible)
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
                val imageDeadline = SystemClock.elapsedRealtime() + 8000
                var imageBounds = photoBounds(automation.rootInActiveWindow)
                while (
                    (imageBounds == null || imageBounds.isEmpty) &&
                        SystemClock.elapsedRealtime() < imageDeadline
                ) {
                    SystemClock.sleep(100)
                    imageBounds = photoBounds(automation.rootInActiveWindow)
                }
                val bounds =
                    requireNotNull(imageBounds) { "Attachment preview did not finish decoding" }
                val start = SystemClock.uptimeMillis()
                fun touch(action: Int, step: Int) {
                    val event =
                        MotionEvent.obtain(
                            start,
                            SystemClock.uptimeMillis(),
                            action,
                            bounds.exactCenterX() +
                                step *
                                    maxOf(
                                        bounds.width() / 30f,
                                        context.resources.displayMetrics.widthPixels * .009f,
                                    ),
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
                SystemClock.sleep(
                    android.view.ViewConfiguration.getLongPressTimeout().toLong() + 300
                )
                for (step in 1..5) {
                    touch(MotionEvent.ACTION_MOVE, step)
                    SystemClock.sleep(30)
                }
                touch(MotionEvent.ACTION_UP, 5)
                val saved = runBlocking {
                    withTimeoutOrNull(5_000) {
                        book.dao
                            .pages()
                            .first {
                                it.any { page ->
                                    DiaryLayout.read(page).prints[photo.name]?.x != .15f
                                }
                            }
                            .single()
                            .let { DiaryLayout.read(it).prints.getValue(photo.name) }
                    }
                }
                assertNotNull("Photo drag did not persist at $bounds", saved)
                assertTrue(saved!!.x > .15f)
                automation.waitForIdle(250, 5000)
                assertFalse(hint in texts(automation.rootInActiveWindow))

                fun pinch(expand: Boolean) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
                    val r = requireNotNull(photoBounds(automation.rootInActiveWindow))
                    val cx = r.exactCenterX()
                    val cy = r.exactCenterY()
                    val t = SystemClock.uptimeMillis()
                    fun event(action: Int, n: Int, radius: Float) {
                        val pointers =
                            Array(n) { i ->
                                MotionEvent.PointerProperties().apply {
                                    id = i
                                    toolType = MotionEvent.TOOL_TYPE_FINGER
                                }
                            }
                        val positions =
                            Array(n) { i ->
                                MotionEvent.PointerCoords().apply {
                                    x = cx + (if (i == 0) -radius else radius)
                                    y = cy
                                    pressure = 1f
                                    size = .1f
                                }
                            }
                        val e =
                            MotionEvent.obtain(
                                t,
                                SystemClock.uptimeMillis(),
                                action,
                                n,
                                pointers,
                                positions,
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
                            assertTrue(automation.injectInputEvent(e, true))
                        } finally {
                            e.recycle()
                        }
                        SystemClock.sleep(35)
                    }
                    val start = r.width() * (if (expand) .14f else .22f)
                    val end = r.width() * (if (expand) .22f else .14f)
                    event(MotionEvent.ACTION_DOWN, 1, start)
                    event(
                        MotionEvent.ACTION_POINTER_DOWN or
                            (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2,
                        start,
                    )
                    for (i in 1..5) event(MotionEvent.ACTION_MOVE, 2, start + (end - start) * i / 5)
                    automation.waitForIdle(100, 1500)
                    if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
                    val live = requireNotNull(photoBounds(automation.rootInActiveWindow))
                    event(
                        MotionEvent.ACTION_POINTER_UP or
                            (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2,
                        end,
                    )
                    event(MotionEvent.ACTION_UP, 1, end)
                    assertTrue(
                        "Print should resize before either finger lifts: $r -> $live",
                        if (expand) live.width() > r.width() else live.width() < r.width(),
                    )
                }
                pinch(true)
                val enlarged = runBlocking {
                    withTimeoutOrNull(5000) {
                        book.dao.pages().first {
                            DiaryLayout.read(it.single()).prints.getValue(photo.name).width >
                                saved.width + .02f
                        }
                    }
                }
                assertNotNull("Outward pinch enlarges the photograph", enlarged)
                val big = DiaryLayout.read(enlarged!!.single()).prints.getValue(photo.name).width
                pinch(false)
                assertNotNull(
                    runBlocking {
                        withTimeoutOrNull(5000) {
                            book.dao.pages().first {
                                DiaryLayout.read(it.single()).prints.getValue(photo.name).width <
                                    big - .02f
                            }
                        }
                    }
                )

                fun tap(bounds: Rect) {
                    automation
                        .executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
                        .use { descriptor ->
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                                .readBytes()
                        }
                    automation.waitForIdle(250, 5000)
                }
                fun tapLabel(label: String) {
                    fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                        if (node == null || node.text?.toString() == label) return node
                        return (0 until node.childCount).firstNotNullOfOrNull {
                            find(node.getChild(it))
                        }
                    }
                    val timeout = SystemClock.elapsedRealtime() + 5000
                    while (SystemClock.elapsedRealtime() < timeout) {
                        val node = find(automation.rootInActiveWindow)
                        if (node != null) {
                            // The modal can still be settling when its label first appears.
                            // Dispatch the labelled control's accessibility action instead of
                            // tapping a coordinate that the animation can move underneath us.
                            var action: AccessibilityNodeInfo? = node
                            while (action != null && !action.isClickable) action = action.parent
                            if (action?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                                automation.waitForIdle(300, 5000)
                                return
                            }
                        }
                        SystemClock.sleep(100)
                    }
                    fail(
                        "Missing button $label. Visible text: ${texts(automation.rootInActiveWindow)}"
                    )
                }
                tapLabel("Photo")
                tapLabel("View attachment")
                tapLabel("Remove attachment")
                tapLabel("Remove")
                val updated = runBlocking {
                    withTimeoutOrNull(5_000) {
                        book.dao.pages().first { it.single().files().isEmpty() }.single()
                    }
                }
                assertNotNull("Confirmed removal did not persist", updated)
                assertEquals(page.body, updated!!.body)
                assertTrue(updated.mediaPositionsByFile().isEmpty())
                assertEquals("", runBlocking { book.dao.locationsFor(page.id).single().media })
                val deletionDeadline = SystemClock.elapsedRealtime() + 5000
                while (photo.exists() && SystemClock.elapsedRealtime() < deletionDeadline) {
                    SystemClock.sleep(50)
                }
                assertFalse(photo.exists())
                tapLabel("Weather")
                tapLabel("Rainy")
                runBlocking {
                    assertNotNull(
                        "Rainy weather did not persist",
                        withTimeoutOrNull(5_000) {
                            book.dao.pages().first { it.single().weather == "Rainy" }
                        },
                    )
                }
                tapLabel("Weather")
                tapLabel("Clear")
                runBlocking {
                    assertNotNull(
                        "Cleared weather did not persist",
                        withTimeoutOrNull(5_000) {
                            book.dao.pages().first { it.single().weather.isEmpty() }
                        },
                    )
                }
            }
        } finally {
            photo.delete()
            db.close()
        }
    }
}
