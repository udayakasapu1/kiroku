package com.kiroku.app.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Updating from 0.6.x (database v4) keeps every link and opens cleanly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    @Test fun v4DatabaseUpgradesToV5() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "kiroku.db"
        context.deleteDatabase(name)

        // Make a v5 database with a link, then turn it back into the v4 shape.
        KirokuDatabase.create(context).apply {
            runBlocking {
                linkDao().insert(
                    LinkEntity(id = "l1", url = "https://example.com/a", title = "Kept title", notes = "kept note", createdAt = 1, updatedAt = 1)
                )
            }
            close()
        }
        val path = context.getDatabasePath(name).path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            fun strings(sql: String) = db.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            val create = strings("SELECT sql FROM sqlite_master WHERE type='table' AND name='knowledge_items'").single()
            val v4Create = create.replace(Regex("""`title_custom` INTEGER NOT NULL DEFAULT 0,\s*"""), "")
            assertNotEquals("v5 table should have title_custom", create, v4Create)
            val indexes = strings("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='knowledge_items' AND sql IS NOT NULL")
            val columns = db.rawQuery("PRAGMA table_info(knowledge_items)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }.filter { it != "title_custom" }.joinToString(", ") { "`$it`" }
            db.execSQL("ALTER TABLE knowledge_items RENAME TO k_old")
            db.execSQL(v4Create)
            db.execSQL("INSERT INTO knowledge_items ($columns) SELECT $columns FROM k_old")
            db.execSQL("DROP TABLE k_old")
            indexes.forEach { db.execSQL(it) }
            db.version = 4
        }

        // Open as the app does: runs MIGRATION_4_5 and checks the schema matches.
        val db = KirokuDatabase.create(context)
        val link = runBlocking { db.linkDao().getById("l1") }!!
        assertEquals("Kept title", link.title)
        assertEquals("kept note", link.notes)
        assertFalse(link.titleCustom)
        db.close()
    }
}
