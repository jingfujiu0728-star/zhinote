package com.example.zhinote

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zhinote.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MigrationV2DeviceTest {
    @Test fun originalSchemaOneMigratesWithoutLosingAnyTable() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val filename = "migration-v2-test-${System.nanoTime()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.zhinote.data.local.ZhiDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(app).name(filename)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        val note = NoteEntity(id = "old-note", title = "原有标题 🌿", content = "原有正文\n多行", color = 4,
            categoryId = "old-category", tags = "PLC,工作", isFavorite = true, createdAt = 42, updatedAt = 43, deletedAt = 44)
        val todo = TodoEntity(id = "old-todo", title = "保留待办", description = "原有描述", priority = 2, dueDate = 100,
            reminderTime = 90, isCompleted = true, completedAt = 99, createdAt = 42, updatedAt = 43, repeatRule = "WEEKLY",
            categoryId = "old-category", tags = "提醒", recurrenceParentId = "parent", reminderDeliveredAt = 91)
        val category = CategoryEntity("old-category", "工作", 2, 42)
        val layout = DropLayoutEntity("old-note", "NOTE", .2f, .7f, 72f, 4, 42)
        helper.writableDatabase.apply {
            execSQL("INSERT INTO notes (id,title,content,color,categoryId,tags,isFavorite,createdAt,updatedAt,deletedAt) VALUES (?,?,?,?,?,?,?,?,?,?)", arrayOf(note.id, note.title, note.content, note.color, note.categoryId, note.tags, 1, 42, 43, 44))
        }
        helper.close()
        // Insert through explicit columns to stay independent of exported field order.
        val legacy = android.database.sqlite.SQLiteDatabase.openDatabase(app.getDatabasePath(filename).path, null, 0)
        try {
            legacy.execSQL("INSERT INTO categories (id,name,color,createdAt) VALUES (?,?,?,?)", arrayOf(category.id, category.name, 2, 42))
            legacy.execSQL("INSERT INTO todos (id,title,description,priority,dueDate,reminderTime,isCompleted,completedAt,createdAt,updatedAt,repeatRule,deletedAt,categoryId,tags,recurrenceParentId,reminderDeliveredAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(todo.id, todo.title, todo.description, 2, 100, 90, 1, 99, 42, 43, "WEEKLY", null, todo.categoryId, todo.tags, "parent", 91))
            legacy.execSQL("INSERT INTO drop_layout (itemId,itemType,normalizedX,normalizedY,size,colorOverride,updatedAt) VALUES (?,?,?,?,?,?,?)", arrayOf(layout.itemId,"NOTE", .2f,.7f,72f,4,42))
        } finally { legacy.close() }
        val db = Room.databaseBuilder(app, ZhiDatabase::class.java, filename).addMigrations(ZhiDatabase.MIGRATION_1_2).build()
        try {
            assertEquals(note, db.dao().note(note.id)); assertEquals(todo, db.dao().todo(todo.id))
            assertEquals(listOf(category), db.dao().allCategories()); assertEquals(listOf(layout), db.dao().allLayouts())
            db.dao().saveNote(note.copy(summary = "新摘要")); assertEquals("新摘要", db.dao().note(note.id)?.summary)
        } finally { db.close(); app.deleteDatabase(filename) }
    }
}
