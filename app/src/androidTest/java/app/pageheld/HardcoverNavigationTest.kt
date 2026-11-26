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

internal fun ActivityScenario<MainActivity>.writingSession(): WritingSession {
    var session: WritingSession? = null
    onActivity { host ->
        session = androidx.lifecycle.ViewModelProvider(host)[WritingEditorModel::class.java].session
    }
    return checkNotNull(session)
}

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
        stylus: Boolean = false,
        barrel: Boolean = false,
        cancel: Boolean = false,
        held: (Int) -> Unit = {},
    ) {
        val down = SystemClock.uptimeMillis()
        points.forEachIndexed { i, p ->
            val action =
                when (i) {
                    0 -> MotionEvent.ACTION_DOWN
                    points.lastIndex ->
                        if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
            val pointer =
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType =
                        if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER
                }
            val coords =
                MotionEvent.PointerCoords().apply {
                    x = p.first
                    y = p.second
                    pressure = if (stylus) .2f + .8f * i / points.lastIndex.coerceAtLeast(1) else 1f
                    if (stylus) setAxisValue(MotionEvent.AXIS_TILT, .45f)
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
                    if (barrel) MotionEvent.BUTTON_STYLUS_PRIMARY else 0,
                    1f,
                    1f,
                    0,
                    0,
                    if (stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN,
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
        // Wait for popup exit and toolbar scrolling before resolving screen coordinates.
        automation.waitForIdle(200, 4000)
        val tools =
            listOf(
                "Type",
                "Pen",
                "Eraser",
                "Photo",
                "Mood",
                "Weather",
                "Place",
                "Undo",
                "Redo",
                "More",
            )
        if (label in tools && find(label) == null) {
            val visible = tools.firstOrNull { find(it) != null }
            if (visible != null) {
                val y = bounds(visible).exactCenterY()
                val width = instrument.targetContext.resources.displayMetrics.widthPixels
                val towardEnd = tools.indexOf(label) > tools.indexOf(visible)
                stroke(
                    (0..10).map { i ->
                        width * (if (towardEnd) .85f - .07f * i else .15f + .07f * i) to y
                    }
                )
                automation.waitForIdle(150, 3000)
            }
        }
        val r = bounds(label)
        val at = r.exactCenterX() to r.exactCenterY()
        stroke(listOf(at, at))
    }

    @Test
    fun appearanceChangesTheSavedNotebookTheme() {
        val context = instrument.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build()
        val book = Notebook(context, db)
        val previous = runBlocking { book.settings.first()["notebookTheme"] ?: "Sage" }
        val target = if (previous == "Denim") "Rose" else "Denim"
        val seed = Page(title = "Theme preview")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { host ->
                    host.setContent {
                        val prefs by book.settings.collectAsState(initial = emptyMap())
                        PageheldTheme(book, prefs) {
                            DiaryEditor(seed, book, emptyList(), emptyList(), prefs, false, "", {}) {}
                        }
                    }
                }
                node(seed.title)
                tap("More")
                tap("Appearance")
                val chip = node(target)
                val control = if (chip.isClickable) chip else requireNotNull(chip.parent)
                assertTrue(control.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                runBlocking { kotlinx.coroutines.withTimeout(5000) { book.settings.first { it["notebookTheme"] == target } } }
                closeMenu()
                automation.waitForIdle(250, 5000)
                val y = bounds("Back to chapter").centerY()
                val expected = NotebookPalette.entries.single { it.id == target }.background
                val screenshot = requireNotNull(automation.takeScreenshot())
                val actual = android.graphics.Color.rgb(
                    kotlin.math.round(expected.red * 255).toInt(),
                    kotlin.math.round(expected.green * 255).toInt(),
                    kotlin.math.round(expected.blue * 255).toInt(),
                )
                try {
                    assertTrue("The editor header uses the selected background",
                        (0 until screenshot.width).any { screenshot.getPixel(it, y) == actual })
                } finally {
                    screenshot.recycle()
                }
                tap("More")
                tap("Appearance")
                assertTrue(node(target).isSelected || node(target).parent?.isSelected == true ||
                    node(target).isChecked || node(target).parent?.isChecked == true)
            }
        } finally {
            runBlocking { book.applySettings(mapOf("notebookTheme" to previous)) }
            db.close()
        }
    }

    @Test
    fun firstImportShowsOneResizeHintPerDiary() {
        val context = instrument.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build()
        val book = Notebook(context, db)
        val first = Page(title = "First entry")
        val second = Page(title = "Second entry")
        val source = java.io.File.createTempFile("picker", ".jpg", context.cacheDir)
        context.resources.openRawResource(R.drawable.diary_desk).use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val picker = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int,
                    contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                    input: I, options: androidx.core.app.ActivityOptionsCompat?) {
                    dispatchResult(requestCode, android.app.Activity.RESULT_OK,
                        android.content.Intent().setData(android.net.Uri.fromFile(source)))
                }
            }
        }
        val hint = "Pinch with two fingers to resize."
        val imported = mutableSetOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun open(page: Page) = scenario.onActivity { host ->
                    host.setContent {
                        key(page.id) {
                            CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides picker) {
                                PageheldTheme(book, emptyMap()) {
                                    DiaryEditor(page, book, emptyList(), emptyList(), emptyMap(), false, "", {}) {}
                                }
                            }
                        }
                    }
                }
                fun attach(page: Page, count: Int): Page {
                    node(page.title)
                    tap("Photo")
                    tap("Add photo or video")
                    val session = scenario.writingSession()
                    return runBlocking {
                        kotlinx.coroutines.withTimeout(5000) {
                            snapshotFlow { session.page.copy(notebookLayout = session.layout.encode()) }
                                .first { it.files().size == count }
                        }.also { current ->
                            imported.addAll(current.files())
                            assertTrue(DiaryLayout.read(current).resizeHintSeen)
                        }
                    }
                }
                open(first)
                attach(first, 1)
                node(hint)
                val timeout = SystemClock.uptimeMillis() + 8000
                while (find(hint) != null && SystemClock.uptimeMillis() < timeout) SystemClock.sleep(100)
                assertNull(find(hint))
                tap("Done")
                val saved = runBlocking {
                    kotlinx.coroutines.withTimeout(5000) { book.dao.pages().first { it.any { page -> page.id == first.id } }.single() }
                }
                open(second)
                attach(second, 1)
                node(hint)
                tap("Back to chapter")
                node("Discard unsaved changes?")
                tap("Keep editing")
                assertTrue(scenario.writingSession().page.files().isNotEmpty())
                tap("Back to chapter")
                tap("Discard")
                assertNull(runBlocking { book.dao.page(second.id) })
                open(saved)
                attach(first, 2)
                assertNull(find(hint))
            }
        } finally {
            imported.forEach { java.io.File(mediaDir(book), it).delete() }
            source.delete()
            db.close()
        }
    }

    @Test
    fun inkOnTemporarySheetCanBeEditedDeletedAndRestored() {
        val context = instrument.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build()
        val book = Notebook(context, db)
        val seed =
            Page(body = "On the first sheet", notebookLayout = DiaryLayout(mode = "Pen").encode())
        runBlocking { book.dao.save(seed) }
        lateinit var editor: WritingSession
        fun awaitPage(predicate: (DiaryLayout) -> Boolean): DiaryLayout = runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(8000) {
                snapshotFlow { editor.layout }.first(predicate)
            } ?: editor.layout.let {
                error("Timed out on sheet ${it.sheet}, mode ${it.mode}, ${it.lines.size} strokes")
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { host ->
                    host.setContent {
                        PageheldTheme(book, emptyMap()) {
                            DiaryEditor(
                                seed,
                                book,
                                emptyList(),
                                emptyList(),
                                emptyMap(),
                                true,
                                "",
                                {},
                            ) {}
                        }
                    }
                }
                val back = bounds("Back to chapter")
                editor = scenario.writingSession()
                val more = find("More")
                val pen = bounds("Pen")
                val paperBounds = bounds("Ink page 1")
                if (more != null && find("Ink page 2") != null &&
                    instrument.targetContext.resources.configuration.screenWidthDp >= 960) {
                    assertTrue("Toolbar and Back should share a row", pen.centerY() in back.top..back.bottom)
                    android.util.Log.i("PageheldLayout", "Header=$back tools=$pen paper=$paperBounds")
                }
                val spread =
                    if (find("Ink page 2") != null) 2
                    else {
                        node("Ink page 1")
                        if (find("Ink page 2") != null) 2 else 1
                    }
                val first = bounds("Ink page 1")
                fun swipe(distance: Float) {
                    val x = first.left + first.width() * .80f
                    val y = first.top + first.height() * .38f
                    stroke((0..12).map { i -> x - first.width() * distance * i / 12 to y }, 20)
                }
                swipe(.12f)
                SystemClock.sleep(550)
                node("Ink page 1")
                swipe(.65f)
                val paper = bounds("Ink page ${spread+1}")
                val x = paper.left + paper.width() * .24f
                val y = paper.top + paper.height() * .46f
                val length = paper.width() * .48f
                stroke((0..12).map { i -> x + length * i / 12 to y }, stylus = true)
                val drawn = awaitPage {
                    it.sheet == spread && it.lines.size == 1 && it.sheetIds.size >= spread + 1
                }.lines.single().points
                assertTrue("Increasing tip pressure should widen the stroke",
                    drawn.last().pressure > drawn.first().pressure)
                tap("Eraser")
                tap("Eraser")
                tap("Area eraser")
                awaitPage { it.mode == "Eraser" && it.areaEraser }
                closeMenu()
                node("Ink page ${spread+1}")
                automation.waitForIdle(200, 4000)
                stroke(listOf(x + length / 2 to y - 35, x + length / 2 to y + 35), stylus = true)
                awaitPage { it.lines.size == 2 }
                tap("Undo")
                awaitPage { it.lines.size == 1 }
                tap("Redo")
                awaitPage { it.lines.size == 2 }
                tap("Pen")
                val prior = awaitPage { it.mode == "Pen" && it.lines.size == 2 }
                stroke(listOf(x to y - 50, x + 20 to y - 50), stylus = true, cancel = true)
                stroke(
                    listOf(x + length / 4 to y - 30, x + length / 4 to y + 30),
                    stylus = true,
                    barrel = true,
                )
                val erased = awaitPage { it.lines.size == 3 && it.mode == "Pen" }
                assertEquals(prior.pen(), erased.pen())
                stroke(listOf(x to y - 60, x + 30 to y - 60), stylus = true)
                awaitPage { it.lines.size == 4 }
                tap("Undo")
                awaitPage { it.lines.size == 3 }
                tap("Undo")
                awaitPage { it.lines == prior.lines }
                tap("More")
                tap("Lasso selection")
                awaitPage { it.mode == "Lasso" }
                node("Ink page ${spread+1}")
                automation.waitForIdle(180, 3000)
                val left = x - 18
                val right = x + length + 18
                stroke(
                    listOf(
                        left to y - 25,
                        right to y - 25,
                        right to y + 25,
                        left to y + 25,
                        left to y - 25,
                    ),
                    stylus = true,
                )
                tap("Selection")
                tap("Duplicate")
                awaitPage { it.lines.size == 4 }
                tap("More")
                tap((if (spread == 2) "Delete left page" else "Delete page") + " · ${spread+1}")
                node("Delete page ${spread+1}?")
                tap("Cancel")
                tap("More")
                tap((if (spread == 2) "Delete left page" else "Delete page") + " · ${spread+1}")
                tap("Delete")
                awaitPage { it.lines.isEmpty() }
                tap("Undo")
                awaitPage { it.lines.size == 4 }
                assertEquals(seed.body, runBlocking { book.dao.pages().first().single().body })
            }
        } finally {
            db.close()
        }
    }

    private fun closeMenu() {
        automation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        )
        SystemClock.sleep(250)
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
        lateinit var editor: WritingSession
        fun working(match: (Page) -> Boolean): Page = runBlocking {
            kotlinx.coroutines.withTimeout(8000) {
                snapshotFlow { editor.page.copy(notebookLayout = editor.layout.encode()) }.first(match)
            }
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
                editor = activity.writingSession()
                capture("typed")
                val image = find("Photograph on page 2")
                if (image != null) {
                    tap("Photograph on page 2")
                    working { it.id == original.id && DiaryLayout.read(it).mode == "Photo" }
                    capture("photo")
                    tap("Type")
                }
                tap("Pen")
                tap("Pen")
                node("Highlight")
                capture("pen-settings")
                closePopup()
                val area = bounds("Ink page 1")
                val x = area.exactCenterX()
                val y = area.exactCenterY()
                stroke(
                    (0..16).map { i -> x + i * 4 to y + 14 * kotlin.math.sin(i * .45).toFloat() },
                    stylus = true,
                )
                working { DiaryLayout.read(it).lines.isNotEmpty() }
                capture("handwriting")
                tap("Undo")
                working { it.id == original.id && DiaryLayout.read(it).lines.isEmpty() }
                tap("Redo")
                working { DiaryLayout.read(it).lines.isNotEmpty() }
                tap("Eraser")
                stroke(listOf(x to y, (x + 24) to y))
                working { DiaryLayout.read(it).lines.isEmpty() }
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
                working { it.body == words && DiaryLayout.read(it).sheet >= 2 }
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
                val before = DiaryLayout.read(working { it.body == words }).sheet
                if (find("Previous pages") != null) {
                    var arrow: AccessibilityNodeInfo? = node("Previous pages")
                    while (arrow != null && !arrow.isClickable) arrow = arrow.parent
                    assertTrue(
                        "Previous spread button activates",
                        arrow?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true,
                    )
                    SystemClock.sleep(120)
                    // The button turn settles before the following controlled finger drag.
                    working { it.id == original.id && DiaryLayout.read(it).sheet < before }
                    capture("page-turn")
                    val fraction = find("5 / 8")
                    if (fraction != null) {
                        node("Writing page 5")
                        automation.waitForIdle(180, 3000)
                        tap("5 / 8")
                        node("Pages")
                        capture("page-picker")
                        closePopup()
                    }
                }
                val spreadBeforeDrag = DiaryLayout.read(working { it.id == original.id }).sheet
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
                    working { it.id == original.id && DiaryLayout.read(it).sheet > spreadBeforeDrag }
                    var previous: AccessibilityNodeInfo? = node("Previous pages")
                    while (previous != null && !previous.isClickable) previous = previous.parent
                    assertTrue(previous?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                    working { it.id == original.id && DiaryLayout.read(it).sheet == spreadBeforeDrag }
                }
                val activeBodyIndex = DiaryLayout.read(working { it.id == original.id }).sheet
                val writable = bounds("Writing page ${activeBodyIndex+1}")
                val editPoint = writable.exactCenterX() to (writable.top + 16f)
                stroke(listOf(editPoint, editPoint))
                SystemClock.sleep(400)
                instrument.sendStringSync("Revised ")
                working { it.id == original.id && "Revised" in it.body }
                activity.onActivity {
                    it.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(it.window.decorView.windowToken, 0)
                }
                SystemClock.sleep(400)
                tap("More")
                tap("Add page")
                working { '\u000c' in it.body }
                automation.waitForIdle(300, 4000)
                assertNull(find("Navigate"))
                assertEquals(original.body, runBlocking { book.dao.page(original.id)?.body })
                tap("Done")
                node("Chapter index")
                val finished = runBlocking {
                    kotlinx.coroutines.withTimeout(8000) {
                        book.dao.pages().first { entries ->
                            entries.any { it.id == original.id && !it.draft && "Revised" in it.body }
                        }.single { it.id == original.id }
                    }
                }
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
