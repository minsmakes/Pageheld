package app.pageheld

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

const val LEGACY_BOOK = "legacy"

@Entity
data class LibraryBook(
    @PrimaryKey val id: String = uid(),
    val title: String,
    val created: Long = System.currentTimeMillis(),
    val modified: Long = created,
    val position: Long = 0,
    val front: String = "{}",
    val back: String = "{}",
    val readingPage: String = "",
)

fun dailyId(bookId: String, day: LocalDate) =
    if (bookId == LEGACY_BOOK) "daily-$day" else "daily-$bookId-$day"

internal val libraryMigration =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE LibraryBook (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
            created INTEGER NOT NULL, modified INTEGER NOT NULL, position INTEGER NOT NULL,
            front TEXT NOT NULL, back TEXT NOT NULL, readingPage TEXT NOT NULL)"""
            )
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO LibraryBook VALUES ('legacy','Memospine.',?,?,0,'{}','{}','')",
                arrayOf(now, now),
            )
            for (table in listOf("Page", "Chapter")) {
                db.execSQL("ALTER TABLE $table ADD COLUMN bookId TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL("CREATE INDEX index_${table}_bookId ON $table(bookId)")
            }
        }
    }

/** Rules apply to legacy DAO writes as well as library management and restore transactions. */
internal val libraryIntegrity =
    object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            for (table in listOf("Page", "Chapter")) {
                db.execSQL(
                    """CREATE TRIGGER IF NOT EXISTS ${table}_owner_insert BEFORE INSERT ON $table
                WHEN NOT EXISTS (SELECT 1 FROM LibraryBook WHERE id=NEW.bookId)
                OR EXISTS (SELECT 1 FROM $table WHERE id=NEW.id AND bookId<>NEW.bookId)
                BEGIN SELECT RAISE(ABORT, 'Book ownership conflict'); END"""
                )
                db.execSQL(
                    """CREATE TRIGGER IF NOT EXISTS ${table}_owner_update BEFORE UPDATE OF bookId ON $table
                WHEN NEW.bookId<>OLD.bookId BEGIN SELECT RAISE(ABORT, 'Book ownership conflict'); END"""
                )
            }
            for (event in listOf("INSERT", "UPDATE")) {
                db.execSQL(
                    """CREATE TRIGGER IF NOT EXISTS Page_chapter_$event BEFORE $event ON Page
                WHEN NEW.chapter IS NOT NULL AND NOT EXISTS
                (SELECT 1 FROM Chapter WHERE id=NEW.chapter AND bookId=NEW.bookId)
                BEGIN SELECT RAISE(ABORT,'Invalid chapter'); END"""
                )
            }
        }
    }

@Dao
interface LibraryDao {
    @Query("SELECT * FROM LibraryBook ORDER BY position, id") fun books(): Flow<List<LibraryBook>>

    @Query("SELECT * FROM Page WHERE bookId=:owner ORDER BY created DESC")
    fun pages(owner: String): Flow<List<Page>>

    @Query("SELECT * FROM Chapter WHERE bookId=:owner ORDER BY position,id")
    fun chapters(owner: String): Flow<List<Chapter>>

    @Query(
        "SELECT l.* FROM DiaryLocation l JOIN Page p ON l.pageId=p.id WHERE p.bookId=:owner ORDER BY pageId,position,l.id"
    )
    fun locations(owner: String): Flow<List<DiaryLocation>>

    @Insert suspend fun insert(book: LibraryBook)

    @Update suspend fun update(book: LibraryBook)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ensure(book: LibraryBook): Long

    @Query("UPDATE LibraryBook SET title=:title,front=:front,back=:back,modified=:now WHERE id=:id")
    suspend fun design(
        id: String,
        title: String,
        front: String,
        back: String,
        now: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE LibraryBook SET readingPage=:pageId WHERE id=:id")
    suspend fun rememberPage(id: String, pageId: String)

    @Query("SELECT COALESCE(MAX(position),-1)+1 FROM LibraryBook") suspend fun nextPosition(): Long

    @Transaction
    suspend fun append(book: LibraryBook) = insert(book.copy(position = nextPosition()))

    @Query("UPDATE LibraryBook SET position=:position WHERE id=:id")
    suspend fun position(id: String, position: Long)

    @Transaction
    suspend fun move(id: String, delta: Int) {
        val order = books().first().map { it.id }.toMutableList()
        val from = order.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(order.indices)
        order.removeAt(from)
        order.add(to, id)
        order.forEachIndexed { index, key -> position(key, index.toLong()) }
    }

    @Query("DELETE FROM Page WHERE bookId=:id") suspend fun removePages(id: String)

    @Query("DELETE FROM Chapter WHERE bookId=:id") suspend fun removeChapters(id: String)

    @Query("DELETE FROM LibraryBook WHERE id=:id") suspend fun removeBook(id: String)

    @Transaction
    suspend fun delete(id: String) {
        removePages(id)
        removeChapters(id)
        removeBook(id)
    }
}
