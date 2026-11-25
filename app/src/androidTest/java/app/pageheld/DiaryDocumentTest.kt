package app.pageheld

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiaryDocumentTest {
    private inline fun <T> NotebookDb.useDatabase(block: (NotebookDb) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    @Test
    fun explicitBreaksAndAutomaticBreaksKeepEveryCharacter() {
        val segments = listOf("", "가나다 😊 English\n".repeat(400), "", "마지막", "")
        val body = segments.joinToString("\u000c")
        val slices =
            paginate(body) { text, start, end ->
                var count = minOf(21, end - start)
                if (start + count < end && text[start + count - 1].isHighSurrogate()) count--
                count
            }
        assertTrue(slices.size > 10)
        assertEquals(
            segments.joinToString(""),
            slices.joinToString("") { body.substring(it.start, it.end) },
        )
        assertEquals(3, slices.count { it.start == it.end })
        assertTrue(slices.none { it.end < body.length && body[it.end].isLowSurrogate() })
    }

    @Test
    fun failedWriteRetainsDraftAndDoneCanRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build().useDatabase { db ->
            val book = Notebook(context, db)
            val initial = Page(title = "Storage test", body = "Old text")
            db.journal().save(initial)
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_save BEFORE UPDATE ON Page BEGIN SELECT RAISE(FAIL,'Test storage failure'); END"
            )
            val editor = WritingSession(initial, book, this)
            editor.update(initial.copy(body = "Keep this edit"))
            kotlinx.coroutines.withTimeout(5000) {
                while (editor.status != "Save failed") kotlinx.coroutines.delay(20)
            }
            assertEquals("Old text", db.journal().pages().first().single().body)
            assertEquals("Keep this edit", editor.page.body)
            var completed = false
            editor.leave(true) { completed = true }
            kotlinx.coroutines.withTimeout(5000) {
                while (editor.locked) kotlinx.coroutines.delay(20)
            }
            assertFalse(completed)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_save")
            editor.leave(true) { completed = true }
            kotlinx.coroutines.withTimeout(5000) {
                while (editor.locked) kotlinx.coroutines.delay(20)
            }
            assertTrue(completed)
            assertEquals("Keep this edit", db.journal().pages().first().single().body)
            assertFalse(db.journal().pages().first().single().draft)
        }
    }

    @Test
    fun reopenedDraftKeepsItsIdentityAndEveryLayer() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "v17-draft-" + uid()
        fun open() = Room.databaseBuilder(ctx, NotebookDb::class.java, dbName).build()
        val layout =
            DiaryLayout(
                sheet = 9,
                cursor = 35,
                mode = "Pen",
                nib = 2,
                thickness = 4f,
                prints = mapOf("picture.photo" to PrintPosition(4, .2f, .65f, .41f)),
                lines =
                    listOf(InkLine(8, 0xff415745.toInt(), .005f, listOf(NibPoint(.3f, .5f, .72f)))),
            )
        val entry =
            Page(
                body = List(10) { "기록 $it" }.joinToString("\u000c"),
                draft = true,
                mood = "Joyful",
                weather = "Rainy",
                media = "picture.photo",
                notebookLayout = layout.encode(),
            )
        try {
            open().useDatabase { db ->
                db.journal()
                    .saveLocation(
                        entry,
                        DiaryLocation(
                            pageId = entry.id,
                            position = 0,
                            name = "Garden",
                            media = "picture.photo",
                        ),
                    )
            }
            open().useDatabase { db ->
                val stored = db.journal().pages().first().single()
                assertEquals(entry, stored)
                assertEquals(layout, DiaryLayout.read(stored))
                val done = stored.copy(draft = false)
                db.journal().save(done)
                db.journal().save(done)
                assertEquals(listOf(done), db.journal().pages().first())
                assertEquals("picture.photo", db.journal().locationsFor(entry.id).single().media)
            }
        } finally {
            ctx.deleteDatabase(dbName)
        }
        Unit
    }
}
