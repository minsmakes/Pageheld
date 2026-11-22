package app.pageheld

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookTest {
    @Test
    fun encryptedRoundTripAndChapterDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDb::class.java).build()
        val book = Notebook(context, db)
        val chapter = Chapter(name = "Archive test", cover = 2)
        val original = File(mediaDir(book), uid() + ".photo")
        val archive = File(context.cacheDir, uid())
        val password = "test passphrase"
        var restoredFile: File? = null
        try {
            original.writeBytes(byteArrayOf(3, 2, 1))
            val entry =
                Page(
                    body = "First paragraph\n\nSecond paragraph",
                    paper = "Grid",
                    chapter = chapter.id,
                    media = original.name,
                )
            book.dao.save(chapter)
            book.dao.save(entry)
            backup(book, Uri.fromFile(archive), password)
            book.dao.deleteChapter(chapter.id)
            assertNull(book.dao.pages().first().single().chapter)
            book.dao.removePage(entry.id)
            assertTrue(
                runCatching { restore(book, Uri.fromFile(archive), "wrong password") }.isFailure
            )
            assertTrue(book.dao.pages().first().isEmpty())
            restore(book, Uri.fromFile(archive), password)
            val restored = book.dao.pages().first().single()
            assertEquals(entry.copy(media = restored.media), restored)
            restoredFile = File(mediaDir(book), restored.media)
            assertArrayEquals(original.readBytes(), restoredFile.readBytes())
            restore(book, Uri.fromFile(archive), password)
            assertEquals(listOf(restored), book.dao.pages().first())
            assertEquals(listOf(chapter), book.dao.chapters().first())
            val damaged = archive.readBytes()
            damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
            archive.writeBytes(damaged)
            assertTrue(runCatching { restore(book, Uri.fromFile(archive), password) }.isFailure)
        } finally {
            original.delete()
            archive.delete()
            restoredFile?.delete()
            db.close()
        }
        Unit
    }
}
