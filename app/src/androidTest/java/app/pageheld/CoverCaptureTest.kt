package app.pageheld

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Window
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
            scenario.onActivity { window = it.window }
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
            capture("phone-notebook-cover", Rect(0, 0, w, h))
            val down = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float) {
                val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, h * .22f, 0)
                e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                instrument.uiAutomation.injectInputEvent(e, true)
                e.recycle()
            }
            touch(MotionEvent.ACTION_DOWN, w * .85f)
            for (i in 1..8) {
                touch(MotionEvent.ACTION_MOVE, w * (.85f - .075f * i))
                SystemClock.sleep(15)
            }
            touch(MotionEvent.ACTION_UP, w * .25f)
            SystemClock.sleep(550)
            capture("phone-today-header", Rect(0, 0, w, window.decorView.rootWindowInsets.systemWindowInsetTop + (56 * window.context.resources.displayMetrics.density).toInt()))
        }
    }
}
