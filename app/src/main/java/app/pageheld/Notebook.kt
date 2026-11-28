package app.pageheld

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

val papers = listOf("Ruled", "Blank", "Grid", "Dot Grid", "Letter", "Scrapbook")
val templates =
    linkedMapOf(
        "Free Writing" to "",
        "Note" to "",
        "Daily Diary" to "Today I…\n\nOne moment to remember…",
        "Memory" to "I want to remember…",
        "Gratitude" to "Today I’m grateful for…\n\nIt mattered because…",
        "Letter" to "Dear ,\n\n",
        "Reflection" to "What happened?\n\nWhat did I learn?",
        "Workout Reflection" to "Today’s movement\n\nHow I felt afterwards",
    )
val moods = listOf("Joyful", "Content", "Uneasy", "Low", "Sad")

fun uid() = UUID.randomUUID().toString()

fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

fun stamp(ms: Long, skeleton: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return date(ms).format(DateTimeFormatter.ofPattern(pattern, locale))
}

fun localDay(day: LocalDate) =
    stamp(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), "yMMMd")

@Entity(indices = [Index("bookId")])
data class Page(
    @PrimaryKey val id: String = uid(),
    val created: Long = System.currentTimeMillis(),
    val title: String = "",
    val body: String = "",
    val template: String = "Free Writing",
    val paper: String = "",
    val mood: String = "",
    val place: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val chapter: String? = null,
    val media: String = "",
    val starred: Boolean = false,
    @ColumnInfo(defaultValue = "''") val mediaPositions: String = "",
    @ColumnInfo(defaultValue = "''") val weather: String = "",
    @ColumnInfo(defaultValue = "''") val notebookLayout: String = "",
    @ColumnInfo(defaultValue = "0") val draft: Boolean = false,
    @ColumnInfo(defaultValue = "'legacy'") val bookId: String = LEGACY_BOOK,
)

fun Page.heading() =
    title.ifBlank {
        body.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: "Untitled page"
    }

fun Page.files() = media.lines().filter { it.isNotBlank() }

@Entity(indices = [Index("bookId")])
data class Chapter(
    @PrimaryKey val id: String = uid(),
    val name: String,
    val cover: Int,
    val position: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'legacy'") val bookId: String = LEGACY_BOOK,
)

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Page::class,
                parentColumns = ["id"],
                childColumns = ["pageId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["pageId", "position"])],
)
data class DiaryLocation(
    @PrimaryKey val id: String = uid(),
    val pageId: String,
    val position: Int,
    val name: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val visited: Long? = null,
    val notes: String = "",
    val media: String = "",
) {
    fun label() = name.ifBlank { "Pinned place" }
}

val locationMigration =
    object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE DiaryLocation (id TEXT NOT NULL PRIMARY KEY, pageId TEXT NOT NULL,
            position INTEGER NOT NULL, name TEXT NOT NULL, lat REAL, lon REAL, visited INTEGER,
            notes TEXT NOT NULL, media TEXT NOT NULL, FOREIGN KEY(pageId) REFERENCES Page(id) ON UPDATE NO ACTION ON DELETE CASCADE)"""
            )
            db.execSQL(
                "CREATE INDEX index_DiaryLocation_pageId_position ON DiaryLocation(pageId,position)"
            )
            db.execSQL(
                """INSERT INTO DiaryLocation SELECT id,id,0,place,lat,lon,NULL,'',media FROM Page
            WHERE trim(place) != '' OR (lat IS NOT NULL AND lon IS NOT NULL)"""
            )
        }
    }

@Dao
interface JournalDao {
    @Query("SELECT * FROM Page ORDER BY created DESC") fun pages(): Flow<List<Page>>

    @Query("SELECT * FROM Page WHERE id=:id") suspend fun page(id: String): Page?

    @Query("SELECT * FROM Chapter ORDER BY position, id") fun chapters(): Flow<List<Chapter>>

    @Query("SELECT * FROM DiaryLocation ORDER BY pageId, position, id")
    fun locations(): Flow<List<DiaryLocation>>

    @Query("SELECT * FROM DiaryLocation WHERE pageId=:pageId ORDER BY position,id")
    suspend fun locationsFor(pageId: String): List<DiaryLocation>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ensurePage(page: Page): Long

    @Upsert suspend fun writeLocation(location: DiaryLocation)

    @Query("DELETE FROM DiaryLocation WHERE pageId=:pageId AND id=:id")
    suspend fun removeLocation(pageId: String, id: String)

    @Query("UPDATE Page SET starred=:value WHERE id=:id")
    suspend fun bookmark(id: String, value: Boolean)

    @Transaction
    suspend fun saveLocation(page: Page, location: DiaryLocation) {
        require(location.pageId == page.id)
        ensurePage(page)
        val places = locationsFor(page.id)
        val order =
            places.find { it.id == location.id }?.position
                ?: ((places.lastOrNull()?.position ?: -1) + 1)
        writeLocation(location.copy(position = order))
    }

    @Transaction
    suspend fun moveLocation(pageId: String, id: String, delta: Int) {
        val places = locationsFor(pageId)
        val i = places.indexOfFirst { it.id == id }
        if (i < 0 || i + delta !in places.indices) return
        val other = places[i + delta]
        writeLocation(places[i].copy(position = other.position))
        writeLocation(other.copy(position = places[i].position))
    }

    @Upsert suspend fun save(page: Page)

    @Upsert suspend fun save(chapter: Chapter)

    @Query("DELETE FROM Page WHERE id=:id") suspend fun removePage(id: String)

    @Query("UPDATE Page SET chapter=NULL WHERE chapter=:id") suspend fun detach(id: String)

    @Query("DELETE FROM Chapter WHERE id=:id") suspend fun removeChapter(id: String)

    @Transaction
    suspend fun deleteChapter(id: String) {
        detach(id)
        removeChapter(id)
    }

    @Transaction
    suspend fun swap(a: Chapter, b: Chapter) {
        save(a.copy(position = b.position))
        save(b.copy(position = a.position))
    }
}

internal val mediaPlacementMigration =
    object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Page ADD COLUMN mediaPositions TEXT NOT NULL DEFAULT ''")
        }
    }

internal val weatherMigration =
    object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Page ADD COLUMN weather TEXT NOT NULL DEFAULT ''")
        }
    }

internal val documentMigration = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Page ADD COLUMN notebookLayout TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Page ADD COLUMN draft INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [Page::class, Chapter::class, DiaryLocation::class, LibraryBook::class],
    version = 6,
    exportSchema = false,
)
abstract class NotebookDb : RoomDatabase() {
    abstract fun journal(): JournalDao
    abstract fun library(): LibraryDao
}

private val Context.preferences by preferencesDataStore("appearance")

class Notebook
internal constructor(
    val context: Context,
    val db: NotebookDb =
        Room.databaseBuilder(context, NotebookDb::class.java, "pageheld.db")
            .addCallback(libraryIntegrity)
            .addMigrations(locationMigration, mediaPlacementMigration, weatherMigration, documentMigration, libraryMigration)
            .build(),
) {
    val dao = db.journal()
    val library = db.library()
    internal val inkClipboard = androidx.compose.runtime.mutableStateOf<List<InkLine>>(emptyList())
    val settings =
        context.preferences.data.map { p ->
            p.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }
        }
    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    val errors = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (job in jobs) try {
                job()
            } catch (e: Exception) {
                errors.emit("Couldn’t save changes. Check device storage.")
            }
        }
    }

    fun queue(job: suspend () -> Unit) {
        jobs.trySend(job).getOrThrow()
    }

    fun save(page: Page) = queue { dao.save(page) }

    fun persist(page: Page): Deferred<Unit> = persist { page }

    internal fun persist(snapshot: () -> Page): Deferred<Unit> {
        val result = CompletableDeferred<Unit>()
        queue {
            try {
                dao.save(snapshot())
                result.complete(Unit)
            } catch (e: Exception) {
                result.completeExceptionally(e)
                throw e
            }
        }
        return result
    }

    fun setting(key: String, value: String) = queue { applySettings(mapOf(key to value)) }

    suspend fun applySettings(values: Map<String, String>) {
        context.preferences.edit { prefs ->
            values.forEach { (key, value) -> prefs[stringPreferencesKey(key)] = value }
        }
    }

    companion object {
        @Volatile private var instance: Notebook? = null

        fun get(context: Context) =
            instance
                ?: synchronized(this) {
                    instance ?: Notebook(context.applicationContext).also { instance = it }
                }
    }
}
