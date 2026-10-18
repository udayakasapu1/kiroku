package com.kiroku.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LinkEntity::class, NoteEntity::class, CategoryEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KirokuDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun create(context: Context): KirokuDatabase =
            Room.databaseBuilder(context, KirokuDatabase::class.java, "kiroku.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * v1 -> v2: folders become user-editable (new `categories` table, seeded with the six
         * folders v1 had built in, same ids so existing links keep their folder), and notes
         * no longer belong to folders.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                val now = System.currentTimeMillis()
                FolderSuggestions.legacy.forEachIndexed { index, (id, name, icon) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories (id, name, icon, position, created_at) VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any>(id, name, icon, index, now),
                    )
                }
                db.execSQL("UPDATE notes SET category = NULL")
            }
        }
    }
}
