package app.pageheld

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrationPreservesVersionFiveJournal() = runBlocking {
        val name = "v5-" + uid()
        val layout =
            DiaryLayout(
                    sheetIds = listOf("sheet"),
                    lines = listOf(InkLine(0, 12, .04f, listOf(NibPoint(.2f, .3f, .8f)))),
                )
                .encode()
        ctx.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL(
                """CREATE TABLE Page (id TEXT NOT NULL PRIMARY KEY,created INTEGER NOT NULL,title TEXT NOT NULL,
                body TEXT NOT NULL,template TEXT NOT NULL,paper TEXT NOT NULL,mood TEXT NOT NULL,place TEXT NOT NULL,
                lat REAL,lon REAL,chapter TEXT,media TEXT NOT NULL,starred INTEGER NOT NULL,
                mediaPositions TEXT NOT NULL DEFAULT '',weather TEXT NOT NULL DEFAULT '',notebookLayout TEXT NOT NULL DEFAULT '',draft INTEGER NOT NULL DEFAULT 0)"""
            )
            db.execSQL(
                "CREATE TABLE Chapter (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,cover INTEGER NOT NULL,position INTEGER NOT NULL)"
            )
            db.execSQL(
                """CREATE TABLE DiaryLocation (id TEXT NOT NULL PRIMARY KEY,pageId TEXT NOT NULL,position INTEGER NOT NULL,
                name TEXT NOT NULL,lat REAL,lon REAL,visited INTEGER,notes TEXT NOT NULL,media TEXT NOT NULL,
                FOREIGN KEY(pageId) REFERENCES Page(id) ON UPDATE NO ACTION ON DELETE CASCADE)"""
            )
            db.execSQL(
                "CREATE INDEX index_DiaryLocation_pageId_position ON DiaryLocation(pageId,position)"
            )
            db.execSQL("INSERT INTO Chapter VALUES ('chapter','Autumn',2,11)")
            db.execSQL(
                "INSERT INTO Page VALUES ('daily-2026-11-28',123,'My title','Old body','Daily Diary','Ruled','Content','Park',52,13,'chapter','test.photo',1,'placement','Rainy',?,1)",
                arrayOf(layout),
            )
            db.execSQL(
                "INSERT INTO DiaryLocation VALUES ('place','daily-2026-11-28',0,'Park',52,13,456,'notes','test.photo')"
            )
            db.version = 5
        }
        val db =
            Room.databaseBuilder(ctx, NotebookDb::class.java, name)
                .addMigrations(libraryMigration)
                .build()
        try {
            val owner = db.library().books().first().single()
            val p = db.library().pages(owner.id).first().single()
            assertEquals(LEGACY_BOOK, owner.id)
            assertEquals("daily-2026-11-28", p.id)
            assertEquals("Old body", p.body)
            assertEquals(123L, p.created)
            assertEquals(layout, p.notebookLayout)
            assertEquals("placement", p.mediaPositions)
            assertEquals("test.photo", p.media)
            assertTrue(p.draft && p.starred)
            assertEquals(p.chapter, db.library().chapters(owner.id).first().single().id)
            assertEquals("notes", db.library().locations(owner.id).first().single().notes)
        } finally {
            db.close()
            ctx.deleteDatabase(name)
        }
        Unit
    }

    @Test
    fun restoreLegacyEncryptedArchive() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val target = Notebook(ctx, db)
        val archive = File(ctx.cacheDir, uid())
        val header = "PAGEHELD1".toByteArray()
        val salt = ByteArray(16) { it.toByte() }
        val password = "fixture passphrase"
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 310000, 256)
        val secret =
            javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        val cipher =
            com.google.crypto.tink.subtle.AesGcmHkdfStreaming(
                secret,
                "HmacSha256",
                32,
                1024 * 1024,
                0,
            )
        val json =
            """{"chapters":[],"pages":[{"id":"daily-2026-11-28","created":10,"title":"Saturday","body":"An older page","template":"Daily Diary","paper":"Ruled","mood":"","place":"Berlin","media":"","starred":true}],"settings":{}}"""
        try {
            archive.outputStream().use { out ->
                out.write(header)
                out.write(salt)
                java.util.zip.ZipOutputStream(cipher.newEncryptingStream(out, header)).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry("journal.json"))
                    zip.write(json.toByteArray())
                    zip.closeEntry()
                }
            }
            val other = LibraryBook(title = "Separate journal")
            target.library.append(other)
            restore(target, Uri.fromFile(archive), password)
            val page = target.library.pages(LEGACY_BOOK).first().single()
            assertEquals("An older page", page.body)
            assertEquals("daily-2026-11-28", page.id)
            assertTrue(page.starred)
            assertEquals("Berlin", target.library.locations(LEGACY_BOOK).first().single().name)
            assertTrue(target.library.pages(other.id).first().isEmpty())
        } finally {
            archive.delete()
            db.close()
        }
        Unit
    }

    @Test
    fun archiveRestoresImportedFontBytesAndCoverLettering() = runBlocking {
        val source = Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val journal = Notebook(ctx, source)
        val directory = ctx.fontDirectory().apply { mkdirs() }
        val font = File(directory, uid() + "--Review lettering.ttf")
        val archive = File(ctx.cacheDir, uid())
        try {
            val bytes = ctx.resources.openRawResource(R.font.ownglyph_mongmongdays).use { it.readBytes() }
            font.writeBytes(bytes)
            val title = CoverLayer(kind = "title", asset = "", font = font.name)
            val front = CoverDesign(layers = listOf(title)).encode()
            val original = LibraryBook(
                id = LEGACY_BOOK, title = "Lettering", front = front, created = 10,
                modified = 30, position = 2, readingPage = "daily-2026-11-28",
            )
            journal.library.insert(original)
            journal.dao.save(Page(id = original.readingPage, bookId = original.id, title = "Last opened"))
            backup(journal, Uri.fromFile(archive), "review-only")
            assertTrue(font.delete())
            val restored = Notebook(ctx, target)
            restored.library.ensure(LibraryBook(id = LEGACY_BOOK, title = "Memospine."))
            restore(restored, Uri.fromFile(archive), "review-only")
            val result = restored.library.books().first().single()
            assertEquals(original.created, result.created)
            assertEquals(original.position, result.position)
            assertEquals(original.readingPage, result.readingPage)
            val cover = CoverDesign.read(result.front)
            assertEquals(font.name, cover.layers.single().font)
            assertArrayEquals(bytes, font.readBytes())
            assertNotNull(android.graphics.Typeface.Builder(font).build())
        } finally {
            font.delete()
            archive.delete()
            source.close()
            target.close()
        }
        Unit
    }

    @Test
    fun catalogRoundTripAndIsolation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val dest = Room.inMemoryDatabaseBuilder(ctx, NotebookDb::class.java).build()
        val journal = Notebook(ctx, db)
        val archive = File(ctx.cacheDir, uid())
        val original = File(mediaDir(journal), uid() + ".photo")
        var copy: File? = null
        try {
            val catalog = readCatalog(ctx)
            assertEquals(12, catalog.map { it.id }.toSet().size)
            catalog.forEach { a ->
                for (path in listOfNotNull(a.path, a.neutral)) ctx.assets
                    .open("artwork/vol01/$path")
                    .use { assertNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
            }
            original.writeBytes(byteArrayOf(1, 7, 9))
            val photo = CoverLayer(kind = "photo", asset = original.name)
            val flower = CoverLayer(kind = "art", asset = "flower", x = .1f, y = .2f, angle = 72f)
            val frame=CoverLayer(kind="art",asset="polaroid",ratio=512f/436)
            val framed=listOf(photo).placeArtwork(frame,photo.id)
            assertEquals(listOf(photo.id,frame.id),framed.map {it.id})
            assertTrue(framed.first().crop)
            assertEquals(photo.x,framed.last().x)
            assertEquals(framed,CoverDesign.read(CoverDesign(layers=framed).encode()).layers)
            val design = CoverDesign(layers = listOf(flower, flower.copy(id = uid()), photo))
            val a =
                LibraryBook(
                    title = "Journal",
                    front = design.encode(),
                    back = design.copy(material = "leather").encode(),
                )
            val b = LibraryBook(title = "Other")
            journal.library.append(a)
            journal.library.append(b)
            val day = LocalDate.of(2026, 11, 28)
            val page =
                Page(
                    id = dailyId(a.id, day),
                    bookId = a.id,
                    body = "First",
                    notebookLayout =
                        DiaryLayout(
                                sheetIds = listOf("leaf"),
                                decorations = mapOf("leaf" to listOf(photo, flower)),
                            )
                            .encode(),
                )
            journal.dao.save(page)
            journal.dao.save(Page(id = dailyId(b.id, day), bookId = b.id, body = "Second"))
            journal.library.move(b.id, -1)
            assertEquals(listOf(b.id, a.id), journal.library.books().first().map { it.id })
            assertEquals("First", journal.library.pages(a.id).first().single().body)
            backup(journal, Uri.fromFile(archive), "test-only")
            val target = Notebook(ctx, dest)
            restore(target, Uri.fromFile(archive), "test-only")
            val restored = target.library.books().first()
            assertEquals(listOf(b.id, a.id), restored.map { it.id })
            val front = CoverDesign.read(restored.last().front)
            assertEquals(design.layers.take(2), front.layers.take(2))
            assertEquals("leather", CoverDesign.read(restored.last().back).material)
            copy = File(mediaDir(target), front.layers.last().asset)
            assertNotEquals(original.name, copy.name)
            assertArrayEquals(original.readBytes(), copy.readBytes())
            val layer =
                DiaryLayout.read(target.library.pages(a.id).first().single())
                    .decorations
                    .getValue("leaf")
                    .first()
            assertEquals(copy.name, layer.asset)
            target.deleteBook(b.id)
            assertTrue(copy.exists())
            assertTrue(target.library.pages(b.id).first().isEmpty())
            target.deleteBook(a.id)
            assertFalse(copy.exists())
            assertEquals(listOf(1, 1, 1, 2, 2, 3), listOf(0, 1, 9, 10, 18, 19).map(::shelfCount))
        } finally {
            db.close()
            dest.close()
            archive.delete()
            original.delete()
            copy?.delete()
        }
        Unit
    }
}
