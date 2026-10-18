package com.kiroku.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LinkEntity::class, NoteEntity::class, CategoryEntity::class, AttachmentEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KirokuDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        fun create(context: Context): KirokuDatabase =
            Room.databaseBuilder(context, KirokuDatabase::class.java, "kiroku.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        /** v2 -> v3: file attachments on links and notes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` TEXT NOT NULL, `owner_type` TEXT NOT NULL, `owner_id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `mime_type` TEXT, `size` INTEGER NOT NULL, " +
                        "`file_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` " +
                        "ON `attachments` (`owner_type`, `owner_id`)"
                )
            }
        }
    }
}
