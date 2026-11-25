package app.pageheld

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
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

    private fun find(request: String): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
        val label =
            if (InstrumentationRegistry.getArguments().getString("korean") == "true")
                mapOf("Open cover  ›" to "표지 열기  ›", "Done" to "완료")[request] ?: request
            else request
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

    private fun stroke(
        points: List<Pair<Float, Float>>,
        pause: Long = 28,
        held: (Int) -> Unit = {},
    ) {
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
            held(i)
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
        val photo = java.io.File(mediaDir(book), uid() + ".photo")
        val fixture = InstrumentationRegistry.getArguments().getString("fixturePhoto")
        if (fixture != null && java.io.File(mediaDir(book), fixture).isFile)
            java.io.File(mediaDir(book), fixture).copyTo(photo, overwrite = true)
        else
            context.resources.openRawResource(R.drawable.diary_desk).use { input ->
                photo.outputStream().use { input.copyTo(it) }
            }
        fun capture(label: String, settle: Boolean = true) {
            if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
            if (settle) {
                instrument.waitForIdleSync()
                SystemClock.sleep(420)
                automation.waitForIdle(250, 5000)
            }
            val bitmap = automation.takeScreenshot() ?: error("Screenshot unavailable")
            java.io.File(context.cacheDir, "v17-$label.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        fun saved(match: (Page) -> Boolean): Page = runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(8000) {
                book.dao.pages().first { it.any(match) }.first(match)
            }
                ?: error(
                    "Entry check failed: " +
                        book.dao.pages().first().map {
                            listOf(
                                it.body.length,
                                it.body.contains("Revised"),
                                DiaryLayout.read(it).sheet,
                                it.draft,
                            )
                        }
                )
        }
        fun closePopup() {
            automation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            )
            SystemClock.sleep(250)
        }
        val previousLocale = java.util.Locale.getDefault()
        val korean = InstrumentationRegistry.getArguments().getString("korean") == "true"
        if (korean) java.util.Locale.setDefault(java.util.Locale.KOREA)
        try {
            val original =
                Page(
                    title = if (korean) "산책을 마치고 적은 하루" else "An afternoon at the writing desk",
                    body = "창가에서 잠시 쉬었다.\n\nSunlight found its way across the wood.",
                    media = photo.name,
                )
            runBlocking {
                book.dao.save(
                    Page(
                        title = if (korean) "아침 산책에서 만난 풍경" else "A walk before breakfast",
                        created = original.created - 86400000L,
                        chapter = null,
                    )
                )
                book.dao.save(
                    Page(
                        title = if (korean) "봄이 오면 읽을 메모" else "A note for next spring",
                        created = original.created - 32L * 86400000,
                        template = "Note",
                    )
                )
                book.dao.saveLocation(
                    original,
                    DiaryLocation(
                        pageId = original.id,
                        position = 0,
                        name = "Writing desk",
                        lat = 52.52,
                        lon = 13.41,
                        media = photo.name,
                    ),
                )
            }
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity { host ->
                    host.setContent {
                        val config =
                            android.content.res.Configuration(
                                androidx.compose.ui.platform.LocalConfiguration.current
                            )
                        if (korean) config.setLocale(java.util.Locale.KOREA)
                        CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalConfiguration provides config
                        ) {
                            val data by remember { book.dao.pages() }.collectAsState(initial = null)
                            PageheldTheme(book, emptyMap()) {
                                NotebookScreens(book, data, emptyList(), emptyMap())
                            }
                        }
                    }
                }
                node(original.title)
                capture("today")
                assertNull(find("Mood"))
                tap("Diaries")
                node("Open cover  ›")
                capture("cover")
                tap("Open cover  ›")
                node("Manage chapters")
                capture("chapters")
                tap("Moments")
                node("Search memories")
                SystemClock.sleep(8500)
                capture("moments")
                tap("Today")
                tap(original.title)
                node("Done")
                capture("typed")
                val image = find("Photograph on page 2")
                if (image != null) {
                    tap("Photograph on page 2")
                    saved { it.id == original.id && DiaryLayout.read(it).mode == "Photo" }
                    capture("photo")
                    tap("Type")
                }
                tap("Pen")
                node("Highlight")
                capture("pen-settings")
                closePopup()
                val area = bounds("Ink page 1")
                val x = area.exactCenterX()
                val y = area.exactCenterY()
                stroke(
                    (0..16).map { i -> x + i * 4 to y + 14 * kotlin.math.sin(i * .45).toFloat() }
                )
                saved { DiaryLayout.read(it).lines.isNotEmpty() }
                capture("handwriting")
                tap("Undo")
                saved { it.id == original.id && DiaryLayout.read(it).lines.isEmpty() }
                tap("Redo")
                saved { DiaryLayout.read(it).lines.isNotEmpty() }
                tap("Eraser")
                stroke(listOf(x to y, (x + 24) to y))
                saved { DiaryLayout.read(it).lines.isEmpty() }
                tap("Mood")
                node("Joyful")
                capture("mood")
                tap("Joyful")
                tap("Weather")
                node("Rainy")
                capture("weather")
                tap("Rainy")
                tap("Place")
                node("Manage places · GPS & map")
                capture("place")
                closePopup()
                tap("Type")
                val words = ("한글과 English. A long afternoon of writing.\n").repeat(80)
                val args =
                    android.os.Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            words,
                        )
                    }
                val toolbarBefore = bounds("Weather")
                val sheetBefore = bounds("Writing page 1")
                tap("Writing page 1")
                activity.onActivity { host ->
                    val target = host.currentFocus ?: host.window.decorView
                    host
                        .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .showSoftInput(
                            target,
                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT,
                        )
                }
                var keyboardVisible = false
                val deadline = SystemClock.uptimeMillis() + 5000
                while (!keyboardVisible && SystemClock.uptimeMillis() < deadline) {
                    activity.onActivity { host ->
                        keyboardVisible =
                            androidx.core.view.ViewCompat.getRootWindowInsets(host.window.decorView)
                                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) ==
                                true
                    }
                    SystemClock.sleep(100)
                }
                assertTrue("Software keyboard is visible", keyboardVisible)
                assertEquals("Toolbar bounds while IME is open", toolbarBefore, bounds("Weather"))
                assertEquals(
                    "Writable sheet width while IME is open",
                    sheetBefore.width(),
                    bounds("Writing page 1").width(),
                )
                capture("keyboard")
                val input =
                    automation.rootInActiveWindow.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                assertNotNull("Writing field must receive focus", input)
                activity.onActivity { host ->
                    host
                        .getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Diary test", words))
                }
                fun key(code: Int, meta: Int = 0) {
                    val time = SystemClock.uptimeMillis()
                    for (a in
                        listOf(
                            android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.ACTION_UP,
                        )) instrument.sendKeySync(
                        android.view.KeyEvent(time, SystemClock.uptimeMillis(), a, code, 0, meta)
                    )
                }
                key(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.META_CTRL_ON)
                key(android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.META_CTRL_ON)
                saved { it.body == words && it.draft && DiaryLayout.read(it).sheet >= 2 }
                activity.onActivity { host ->
                    host
                        .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(host.window.decorView.windowToken, 0)
                }
                SystemClock.sleep(
                    6200
                ) // Allow Android’s clipboard preview to leave the edge controls.
                node("Done")
                capture("continuous")
                val before = DiaryLayout.read(saved { it.body == words }).sheet
                if (find("Previous pages") != null) {
                    var arrow: AccessibilityNodeInfo? = node("Previous pages")
                    while (arrow != null && !arrow.isClickable) arrow = arrow.parent
                    assertTrue(
                        "Previous spread button activates",
                        arrow?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true,
                    )
                    SystemClock.sleep(120)
                    // The button turn settles before the following controlled finger drag.
                    saved { it.id == original.id && DiaryLayout.read(it).sheet < before }
                    capture("page-turn")
                    val fraction = find("5 / 8")
                    if (fraction != null) {
                        tap("5 / 8")
                        capture("page-picker")
                        closePopup()
                    }
                }
                val spreadBeforeDrag = DiaryLayout.read(saved { it.id == original.id }).sheet
                if (spreadBeforeDrag >= 2) {
                    SystemClock.sleep(650)
                    val inputBounds = bounds("Writing page ${spreadBeforeDrag+1}")
                    val distance = inputBounds.width() * .95f
                    val rail = bounds("Right page margin")
                    val edgeX = rail.right - rail.width() * .85f
                    val y = inputBounds.top + inputBounds.height() * .24f
                    stroke((0..12).map { i -> edgeX - i * distance / 12 to y }) { i ->
                        if (i == 6) {
                            SystemClock.sleep(150)
                            capture("fold", settle = false)
                        }
                    }
                    saved { it.id == original.id && DiaryLayout.read(it).sheet > spreadBeforeDrag }
                    var previous: AccessibilityNodeInfo? = node("Previous pages")
                    while (previous != null && !previous.isClickable) previous = previous.parent
                    assertTrue(previous?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                    saved { it.id == original.id && DiaryLayout.read(it).sheet == spreadBeforeDrag }
                }
                val activeBodyIndex = DiaryLayout.read(saved { it.id == original.id }).sheet
                val writable = bounds("Writing page ${activeBodyIndex+1}")
                val editPoint = writable.exactCenterX() to (writable.top + 16f)
                stroke(listOf(editPoint, editPoint))
                SystemClock.sleep(400)
                instrument.sendStringSync("Revised ")
                saved { it.id == original.id && "Revised" in it.body }
                activity.onActivity {
                    it.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
                }
                SystemClock.sleep(400)
                tap("More")
                tap("Add page")
                saved { '\u000c' in it.body }
                tap("Navigate")
                tap("Today")
                node("Diaries")
                tap("Diaries")
                node("Done")
                tap("Done")
                node("Chapter index")
                val finished = saved { it.id == original.id && !it.draft && "Revised" in it.body }
                assertEquals(words, finished.body.replace("Revised ", "").trimEnd(12.toChar()))
                assertEquals(original.id, finished.id)
                assertEquals("Joyful", finished.mood)
                assertEquals("Rainy", finished.weather)
                assertEquals(3, runBlocking { book.dao.pages().first().size })
                tap(original.title)
                node("Done")
            }
        } finally {
            java.util.Locale.setDefault(previousLocale)
            photo.delete()
            db.close()
        }
    }
}
