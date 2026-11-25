package app.pageheld

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CoverCaptureTest {
    @Test
    fun capturePhysicalCoverAndHeader() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("phoneCover") == "true")
        val instrument = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var window: Window
            scenario.onActivity { host ->
                window = host.window
                val book = Notebook.get(host)
                host.setContent {
                    val entries by remember { book.dao.pages() }.collectAsState(initial = null)
                    val chapters by remember { book.dao.chapters() }.collectAsState(emptyList())
                    PageheldTheme(book, emptyMap()) {
                        NotebookScreens(book, entries, chapters, emptyMap())
                    }
                }
            }
            instrument.uiAutomation.waitForIdle(400, 5000)
            val w = window.decorView.width
            val h = window.decorView.height
            fun capture(name: String, rect: Rect) {
                val bitmap =
                    Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
                val latch = CountDownLatch(1)
                var result = -1
                PixelCopy.request(
                    window,
                    rect,
                    bitmap,
                    {
                        result = it
                        latch.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                )
                assertTrue(latch.await(4, TimeUnit.SECONDS))
                assertEquals(PixelCopy.SUCCESS, result)
                File(instrument.targetContext.cacheDir, name + ".png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            fun awaitButton(label: String, click: Boolean = true) {
                fun find(
                    n: android.view.accessibility.AccessibilityNodeInfo?
                ): android.view.accessibility.AccessibilityNodeInfo? {
                    if (n == null) return null
                    if (n.text?.toString() == label || n.contentDescription?.toString() == label)
                        return n
                    return (0 until n.childCount).firstNotNullOfOrNull { find(n.getChild(it)) }
                }
                val expires = SystemClock.uptimeMillis() + 6500
                while (SystemClock.uptimeMillis() < expires) {
                    var target = find(instrument.uiAutomation.rootInActiveWindow)
                    while (target != null && !target.isClickable) target = target.parent
                    if (
                        target != null &&
                            (!click ||
                                target.performAction(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
                                ))
                    ) {
                        SystemClock.sleep(650)
                        return
                    }
                    SystemClock.sleep(80)
                }
                fail("Missing $label")
            }
            awaitButton("View all  ›", click = false)
            capture("v17-phone-today", Rect(0, 0, w, h))
            awaitButton("Diaries")
            capture("v17-phone-cover", Rect(0, 0, w, h))
            awaitButton("Open cover  ›")
            capture("v17-phone-chapters", Rect(0, 0, w, h))
            assertTrue(
                "Compact portrait stays portrait",
                window.decorView.height > window.decorView.width,
            )
        }
    }
}
