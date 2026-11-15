package com.example.zhinote

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zhinote.data.backup.*
import com.example.zhinote.data.local.*
import com.example.zhinote.data.repository.ZhiRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryDeviceTest {
    private lateinit var db: ZhiDatabase
    private lateinit var repo: ZhiRepository
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZhiDatabase::class.java).build(); repo = ZhiRepository(db) }
    @After fun cleanup() { db.close() }
    @Test fun noteCrudTrashRestoreAndPurge() = runBlocking {
        val note = NoteEntity(title = "原文", content = "草稿")
        repo.save(note); repo.save(note.copy(content = "自动保存的长正文"))
        assertEquals("自动保存的长正文", db.dao().note(note.id)!!.content)
        repo.trash(note.id, "NOTE"); assertNotNull(db.dao().note(note.id)!!.deletedAt)
        repo.trash(note.id, "NOTE", restore = true); assertNull(db.dao().note(note.id)!!.deletedAt)
        repo.purge(note.id, "NOTE"); assertNotNull(db.dao().note(note.id)) // active items cannot be purged
        repo.trash(note.id, "NOTE"); repo.purge(note.id, "NOTE"); assertNull(db.dao().note(note.id))
    }
    @Test fun completionIsIdempotentAndRepeatHistoryCanBeUndone() = runBlocking {
        val todo = TodoEntity(title = "每天读书", dueDate = System.currentTimeMillis(), repeatRule = "DAILY")
        repo.save(todo); assertNotNull(repo.complete(todo.id)); assertNull(repo.complete(todo.id))
        val all = db.dao().allTodos()
        assertEquals(2, all.size); assertTrue(db.dao().todo(todo.id)!!.isCompleted)
        assertEquals(1, all.count { !it.isCompleted && it.deletedAt == null })
        repo.undoComplete(todo.id)
        assertFalse(db.dao().todo(todo.id)!!.isCompleted)
        assertEquals(1, db.dao().allTodos().count { !it.isCompleted && it.deletedAt == null })
    }
    @Test fun layoutDragDoesNotChangeBusinessFieldsAndBackupRestoreIsValidated() = runBlocking {
        val note = NoteEntity(title = "保留我", content = "正文")
        repo.save(note)
        val before = db.dao().note(note.id)
        repo.saveLayout(DropLayoutEntity(note.id, "NOTE", .3f, .7f))
        assertEquals(before, db.dao().note(note.id))
        val backup = BackupManager(ApplicationProvider.getApplicationContext(), db)
        val invalid = BackupData(listOf(note.copy(color = 99)), emptyList(), emptyList(), emptyList())
        try { backup.restore(invalid); fail("Invalid data must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(before, db.dao().note(note.id))
        val valid = BackupData(listOf(note.copy(content = "从备份恢复")), emptyList(), emptyList(), listOf(DropLayoutEntity(note.id, "NOTE", .5f, .5f)))
        backup.restore(valid); assertEquals("从备份恢复", db.dao().note(note.id)!!.content)
        assertEquals(.5f, db.dao().allLayouts().single().normalizedX)
    }
}
