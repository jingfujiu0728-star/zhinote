package com.example.zhinote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, TodoEntity::class, CategoryEntity::class, DropLayoutEntity::class], version = 2, exportSchema = true)
abstract class ZhiDatabase : RoomDatabase() {
    abstract fun dao(): ZhiDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context): ZhiDatabase = Room.databaseBuilder(context, ZhiDatabase::class.java, "zhinote.db")
            // No destructive migration: version upgrades must supply an explicit Migration.
            .addMigrations(MIGRATION_1_2).build()
    }
}
