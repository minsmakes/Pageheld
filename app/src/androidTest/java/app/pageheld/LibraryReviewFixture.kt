package app.pageheld

import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in fixture for a fresh, disposable emulator. No fixtures ship in either APK. */
class LibraryReviewFixture {
    @Test
    fun populateEmptyReviewEmulator() = runBlocking {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("emptyReviewEmulator") == "true"
        )
        assumeTrue(
            android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("sdk_gphone")
        )
        val book = Notebook.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val catalog = readCatalog(book.context)
        val titles =
            listOf(
                "Days in Jeju",
                "Reading journal",
                "Travel notes",
                "Dream journal",
                "Quiet moments",
                "Growing season",
                "Little favorites",
                "Wishes",
                "Collected days",
                "Weekend walks",
            )
        val colors =
            listOf(
                0xff899669,
                0xff956342,
                0xffe8dbb8,
                0xff5b6651,
                0xffbec2a0,
                0xffd9aaa1,
                0xffd6c3a4,
                0xff5d566e,
                0xffe1cdae,
                0xff94b1bf,
            )
        val art =
            listOf(
                "flower",
                "clover",
                "leaf",
                "moon",
                "gingham",
                "flower",
                "polaroid",
                "star",
                "floral",
                "leaf",
            )
        book.db.withTransaction {
            check(
                book.library.books().first().isEmpty() &&
                    book.dao.pages().first().isEmpty() &&
                    book.dao.chapters().first().isEmpty()
            )
            titles.forEachIndexed { i, title ->
                val a = catalog.single { it.id == art[i] }
                val layer =
                    CoverLayer(
                        kind = "art",
                        asset = a.id,
                        ratio = a.ratio,
                        x = if (i % 2 == 0) .25f else .75f,
                        y = .73f,
                        width = .31f,
                        angle = if (i % 2 == 0) -9f else 6f,
                    )
                val design =
                    CoverDesign.read("{}")
                        .copy(
                            material = listOf("linen", "leather", "paper")[i % 3],
                            color = colors[i].toInt(),
                            ribbon = if (i % 3 == 0) clothColor else 0,
                            border = listOf("Classic", "Simple", "Botanical")[i % 3],
                            layers =
                                listOf(
                                    CoverLayer(
                                        "title",
                                        "title",
                                        "",
                                        .52f,
                                        .38f,
                                        .73f,
                                        ratio = 3.8f,
                                    ),
                                    layer,
                                ),
                        )
                val b =
                    LibraryBook(
                        title = title,
                        front = design.encode(),
                        readingPage = if (i == 0) "review-day" else "",
                    )
                book.library.append(b)
                if (i == 0)
                    book.dao.save(
                        Page(
                            id = "review-day",
                            bookId = b.id,
                            title = "An afternoon by the sea",
                            body =
                                "Saturday, November 28\n\nI took the long way home today. The path curved around the shore and the air smelled of pine.\n\nAt the bend I stopped to write down the names of the birds.\u000cThe light stayed on the water long after the sun had gone.\n\nA small moment to remember.",
                            notebookLayout =
                                DiaryLayout(
                                        sheetIds = listOf("morning", "evening"),
                                        decorations =
                                            mapOf(
                                                "morning" to listOf(layer.copy(x = .74f, y = .74f)),
                                                "evening" to
                                                    listOf(
                                                        CoverLayer(
                                                            kind = "art",
                                                            asset = "cat",
                                                            ratio = 1.56f,
                                                            x = .72f,
                                                            y = .78f,
                                                            width = .29f,
                                                        )
                                                    ),
                                            ),
                                    )
                                    .encode(),
                        )
                    )
            }
        }
    }
}
