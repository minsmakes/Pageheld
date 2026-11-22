package app.pageheld

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun open(name: String) =
        Room.databaseBuilder(context, NotebookDb::class.java, name)
            .addMigrations(locationMigration)
            .build()

    @Test
    fun orderedPlacesSurviveReopenAndIndependentPageWrites() = runBlocking {
        val name = "location-test-" + uid()
        var db = open(name)
        try {
            val page = Page(title = "A draft", body = "First line\nSecond line")
            db.journal().save(page)
            assertTrue(db.journal().locationsFor(page.id).isEmpty())
            val first =
                DiaryLocation(
                    pageId = page.id,
                    position = 0,
                    name = "Park",
                    lat = 52.5,
                    lon = 13.4,
                    visited = 100,
                    notes = "At dusk",
                )
            val second =
                DiaryLocation(
                    pageId = page.id,
                    position = 1,
                    name = "Station",
                    lat = 52.6,
                    lon = 13.5,
                )
            val dao = db.journal()
            dao.saveLocation(page, first)
            assertEquals(listOf(first), dao.locationsFor(page.id))
            dao.saveLocation(page, second)
            dao.moveLocation(page.id, second.id, -1)
            dao.saveLocation(page, first.copy(notes = "After sunset"))
            dao.save(page.copy(body = page.body + "\nHome"))
            dao.bookmark(page.id, true)
            db.close()
            db = open(name)
            val saved = db.journal().locationsFor(page.id)
            assertEquals(
                listOf(second.copy(position = 0), first.copy(position = 1, notes = "After sunset")),
                saved,
            )
            assertEquals(
                listOf(Pin("Station", 52.6, 13.5), Pin("Park", 52.5, 13.4)),
                saved.map { it.pin() },
            )
            assertEquals(page.body + "\nHome", db.journal().pages().first().single().body)
            db.journal().removeLocation(page.id, second.id)
            assertEquals(first.id, db.journal().locationsFor(page.id).single().id)
            db.journal().removePage(page.id)
            assertTrue(db.journal().locations().first().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrateVersionOneWithoutLosingJournalData() = runBlocking {
        val name = "migration-test-" + uid()
        context.openOrCreateDatabase(name, 0, null).use { legacy ->
            legacy.execSQL(
                """CREATE TABLE Page (id TEXT NOT NULL PRIMARY KEY,created INTEGER NOT NULL,title TEXT NOT NULL,
                body TEXT NOT NULL,template TEXT NOT NULL,paper TEXT NOT NULL,mood TEXT NOT NULL,place TEXT NOT NULL,
                lat REAL,lon REAL,chapter TEXT,media TEXT NOT NULL,starred INTEGER NOT NULL)"""
            )
            legacy.execSQL(
                "CREATE TABLE Chapter (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,cover INTEGER NOT NULL,position INTEGER NOT NULL)"
            )
            legacy.execSQL("INSERT INTO Chapter VALUES ('c','Walks',2,3)")
            legacy.execSQL(
                "INSERT INTO Page VALUES ('p',12,'Old page','Original body','Memory','Grid','Content','Park',52.5,13.4,'c','sample.photo',1)"
            )
            legacy.execSQL(
                "INSERT INTO Page VALUES ('empty',13,'No places','Text','Memory','','','',NULL,NULL,NULL,'',0)"
            )
            legacy.execSQL(
                "INSERT INTO Page VALUES ('named',14,'Name only','Text','Memory','','','Cafe',NULL,NULL,NULL,'',0)"
            )
            legacy.version = 1
        }
        val db = open(name)
        try {
            val dao = db.journal()
            val page = dao.pages().first().single { it.id == "p" }
            assertEquals("Original body", page.body)
            assertEquals("Grid", page.paper)
            assertTrue(page.starred)
            assertEquals("Walks", dao.chapters().first().single { it.id == page.chapter }.name)
            assertEquals(
                Pin("Park", 52.5, 13.4, "sample.photo"),
                dao.locationsFor("p").single().pin(),
            )
            assertTrue(dao.locationsFor("empty").isEmpty())
            assertEquals("Cafe", dao.locationsFor("named").single().name)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
