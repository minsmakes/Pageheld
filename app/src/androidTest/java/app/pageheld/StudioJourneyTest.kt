package app.pageheld

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class StudioJourneyTest {
    private val runner = InstrumentationRegistry.getInstrumentation()

    private fun click(label: String, long: Boolean = false) {
        val action =
            if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK
            else AccessibilityNodeInfo.ACTION_CLICK
        val deadline = SystemClock.elapsedRealtime() + 10000
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (
                node.isVisibleToUser &&
                    (node.text?.toString() == label || node.contentDescription?.toString() == label)
            )
                return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let {
                return it
            }
            return null
        }
        while (SystemClock.elapsedRealtime() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 33) runner.uiAutomation.clearCache()
            var node = find(runner.uiAutomation.rootInActiveWindow)
            while (node != null) {
                if (node.performAction(action)) {
                    SystemClock.sleep(220)
                    return
                }
                node = node.parent
            }
            SystemClock.sleep(80)
        }
        error("Missing control: $label")
    }

    @Test
    fun thirdShelfRestoresAndDesignDuplicationKeepsEntriesPrivate() {
        val ctx = runner.targetContext
        assumeTrue(BuildConfig.FLAVOR == "tablet" && ctx.resources.configuration.smallestScreenWidthDp >= 600)
        val db = Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val journal = Notebook(ctx, db)
        val originalPage = runBlocking { journal.settings.first()["shelfPage"].orEmpty() }
        val books = (1..19).map { LibraryBook(title = "Shelf book $it") }
        try {
            runBlocking {
                journal.applySettings(mapOf("shelfPage" to "0"))
                books.forEach { journal.library.append(it) }
                journal.dao.save(Page(title = "Private entry", bookId = books.last().id))
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { Pageheld(journal) } }
                for (page in 1..2) {
                    click("Next shelf")
                    runBlocking {
                        withTimeout(10000) { journal.settings.first { it["shelfPage"] == page.toString() } }
                    }
                }
                click(books.last().title, true)
                click("Duplicate design")
                click("Shelf book 19 (2)", true)
                click("Previous shelf page")
                runBlocking {
                    val order = withTimeout(5000) {
                        journal.library.books().first { it.size == 20 && it[10].title == "Shelf book 19 (2)" }
                    }
                    assertTrue(journal.library.pages(order[10].id).first().isEmpty())
                    assertEquals("Private entry", journal.library.pages(books.last().id).first().single().title)
                }
                // Rebuild from the fixture's persisted settings in the same host. Launching
                // MainActivity again briefly opens the unrelated installed library first.
                scenario.onActivity { it.setContent { androidx.compose.runtime.key("reopen") { Pageheld(journal) } } }
                click(books.last().title, true)
                click("Delete book")
                click("Cancel")
                click("Delete book")
                click("Delete")
                runBlocking {
                    withTimeout(5000) { journal.library.books().first { list -> list.none { it.id == books.last().id } } }
                    assertTrue(journal.library.pages(books.last().id).first().isEmpty())
                }
            }
        } finally {
            runBlocking { journal.applySettings(mapOf("shelfPage" to originalPage)) }
            db.close()
        }
    }

    @Test
    fun editableFrontAndBackSurviveDrawingUndoAndReopen() {
        val ctx = runner.targetContext
        assumeTrue(
            BuildConfig.FLAVOR == "tablet" &&
                ctx.resources.configuration.smallestScreenWidthDp >= 600
        )
        val db = Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val book = Notebook(ctx, db)
        val initial = LibraryBook(title = "Studio test")
        runBlocking { book.library.insert(initial) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { host -> host.setContent { Pageheld(book) } }
                click(initial.title, true)
                click("Customize or rename")
                click("Leather")
                click("Stickers")
                click("Flower")
                click("Flower")
                click("Undo")
                click("Redo")
                click("Back")
                click("Cover")
                click("Paper")
                click("Done")
                val saved = runBlocking {
                    withTimeout(5000) {
                        book.library.books().first { it.single().front != initial.front }.single()
                    }
                }
                assertEquals("leather", CoverDesign.read(saved.front).material)
                assertEquals(2, CoverDesign.read(saved.front).layers.count { it.asset == "flower" })
                assertEquals("paper", CoverDesign.read(saved.back, true).material)
                click(initial.title, true)
                click("Customize or rename")
                click("Paper")
                click("‹ Library")
                click("Discard")
                assertEquals(saved, runBlocking { book.library.books().first().single() })
            }
        } finally {
            db.close()
        }
    }
}
