package app.pageheld

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiaryDocumentTest {
    @Test
    fun composingOnReflowedAndVirtualSheetsKeepsAllBoundaries() {
        val first = TextSlice(0, 4)
        val second = TextSlice(4, 8)
        assertEquals(
            listOf(TextSlice(0, 6), TextSlice(6, 10)),
            pinTextSlices(listOf(first, second), 0, first, 0, 2),
        )
        assertEquals(
            listOf(first, TextSlice(5, 5), TextSlice(6, 6), TextSlice(7, 9)),
            pinTextSlices(listOf(first), 3, TextSlice(4, 4), 3, 5),
        )
    }

    @Test
    fun densePageKeepsEditableSamplesAfterContinuousErasingAndReopen() {
        val lines =
            List(300) { row ->
                InkLine(
                    0,
                    0xff404538.toInt(),
                    .001f,
                    List(100) { column ->
                        NibPoint(.05f + column * .009f, .035f + row * .003f, .3f + column * .006f)
                    },
                    2,
                )
            }
        val frame = InkFrame(1000f, 1400f, 7f)
        val bounds = EraserBounds(frame)
        var working = lines
        val durations = LongArray(64)
        for (i in durations.indices) {
            val from = androidx.compose.ui.geometry.Offset(507f, 27f + i * 20)
            val to = from + androidx.compose.ui.geometry.Offset(0f, 20f)
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            working = eraseInk(working, 0, frame, from, to, 8f, true, bounds)
            durations[i] = android.os.SystemClock.elapsedRealtimeNanos() - start
        }
        assertTrue(working.size > lines.size)
        val art = DiaryLayout(lines = working).withSheetCount(1)
        val encoded = art.encode()
        assertEquals(art, DiaryLayout.read(Page(notebookLayout = encoded)))
        durations.sort()
        android.util.Log.i(
            "PageheldPerf",
            "300 strokes / 30,000 samples. Eraser CPU path, 64 moves: median " +
                durations[32] / 1_000_000.0 +
                " ms, p95 " +
                durations[60] / 1_000_000.0 +
                " ms. Layout " +
                encoded.length +
                " bytes.",
        )
    }

    @Test
    fun legacyInkKeepsItsAppearanceAndSurvivesSweptEraser() {
        val legacy =
            """{"version":1,"ink":[[0,-16777216,0.01,[0,0.5,0.5,1,0.5,1.0]]],"prints":{}}"""
        val art = DiaryLayout.read(Page(notebookLayout = legacy))
        val line = art.lines.single()
        assertEquals(-1, line.instrument)
        val frame = InkFrame(1000f, 300f, 7f)
        val from = androidx.compose.ui.geometry.Offset(507f, 20f)
        val to = androidx.compose.ui.geometry.Offset(507f, 280f)
        val other = line.copy(sheet = 1)
        val pieces = eraseInk(listOf(line, other), 0, frame, from, to, 10f, true)
        assertEquals(3, pieces.size)
        assertSame(other, pieces.last())
        assertEquals(line.points.first(), pieces[0].points.first())
        assertEquals(line.points.last(), pieces[1].points.last())
        assertTrue(pieces[0].points.last().x in 0.48f..0.50f)
        assertTrue(pieces[1].points.first().pressure in 0.75f..0.78f)
        assertEquals(listOf(other), eraseInk(listOf(line, other), 0, frame, from, to, 10f, false))
        val updated = art.copy(lines = pieces)
        assertEquals(updated, DiaryLayout.read(Page(notebookLayout = updated.encode())))
    }

    @Test
    fun individualDeletionRetainsReflowedTextIdsAndLocationMedia() {
        val page = Page(body = "abcdef\u000c\u000clast", media = "linked.photo\nright.video")
        val art =
            DiaryLayout(
                sheetIds = listOf("a", "b", "c", "d"),
                prints =
                    mapOf("linked.photo" to PrintPosition(1), "right.video" to PrintPosition(3)),
            )
        val slices = paginate(page.body) { _, start, end -> minOf(3, end - start) }
        assertTrue(hasSheetContent(page, art, slices, 1))
        assertFalse(hasSheetContent(page, art, slices, 2))
        val (next, layout) = removeSheet(page, art, slices, 1, setOf("linked.photo"))
        assertEquals(page.id, next.id)
        assertEquals("abc\u000c\u000clas\u000ct", next.body)
        assertEquals(listOf("a", "c", "d"), layout.sheetIds.take(3))
        assertEquals(setOf("linked.photo", "right.video"), next.files().toSet())
        assertEquals(mapOf("right.video" to PrintPosition(2)), layout.prints)
    }

    @Test
    fun stylusPressureTracksContactWithoutThickeningAtLiftOff() {
        val tip = StylusPressure()
        assertEquals(.2f, tip.sample(.2f, 100), .0001f)
        val moving = tip.sample(.8f, 105)
        assertTrue(moving > .2f && moving < .8f)
        assertEquals(moving, tip.sample(0f, 110), 0f)
        assertEquals(.8f, tip.sample(.8f, 200), .001f)
    }

    @Test
    fun penPresetsAndWeatherAssetsAreDistinctAndRoundTrip() {
        val pen = DiaryPen(4, 0xff645b47.toInt(), 4f, .55f, .75f)
        assertEquals(pen, DiaryPen.parse(pen.token()))
        assertTrue(pen.weight(.8f, 1f, 0f) > pen.weight(.8f, 0f, 0f))
        assertTrue(pen.copy(nib = 2).weight(1f, 0f, 0f) > pen.copy(nib = 2).weight(.1f, 0f, 0f))
        assertEquals(13, weatherIllustrations.map { it.icon }.toSet().size)
        assertEquals(R.drawable.weather_light_rain, weatherFor("Rainy").icon)
        assertEquals(R.drawable.weather_unknown, weatherFor("unrecognized").icon)
    }

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
