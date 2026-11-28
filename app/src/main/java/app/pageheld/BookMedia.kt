package app.pageheld

import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.flow.first

internal fun DiaryLayout.photoDecorations() =
    decorations.values.flatten().filter { it.kind == "photo" }.map { it.asset }

internal fun LibraryBook.coverFiles() =
    listOf(front, back).flatMap { raw ->
        CoverDesign.read(raw).layers.filter { it.kind == "photo" }.map { it.asset }
    }

internal suspend fun Notebook.releaseUnused(names: Collection<String>) {
    if (names.isEmpty()) return
    db.withTransaction {
        val unused =
            names.filter { it.matches(Regex("[0-9a-fA-F-]{36}\\.(photo|video)")) }.toMutableSet()
        library.books().first().forEach { unused.removeAll(it.coverFiles().toSet()) }
        dao.pages().first().forEach { p ->
            unused.removeAll(p.files().toSet())
            if (unused.isNotEmpty())
                unused.removeAll(DiaryLayout.read(p).photoDecorations().toSet())
        }
        dao.locations().first().forEach { unused.removeAll(it.media.lines().toSet()) }
        unused.forEach { File(mediaDir(this@releaseUnused), it).delete() }
    }
}

internal suspend fun Notebook.deleteBook(id: String) {
    val candidates =
        db.withTransaction {
            val covers = library.books().first().find { it.id == id }?.coverFiles().orEmpty()
            val files =
                library.pages(id).first().flatMap {
                    it.files() + DiaryLayout.read(it).photoDecorations()
                }
            val places = library.locations(id).first().flatMap { it.media.lines() }
            library.delete(id)
            covers + files + places
        }
    releaseUnused(candidates)
}
