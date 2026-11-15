package com.example.zhinote

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zhinote.data.local.*
import com.example.zhinote.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickCompleteDeviceTest {
    @Test fun completionIsDurableIdempotentUndoableAndNoteSafe() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val todo = TodoEntity(title = "V2 双击事务验证", repeatRule = "DAILY")
        val note = NoteEntity(title = "V2 笔记不能完成")
        app.repository.save(todo); app.repository.save(note)
        try {
            val vm = AppViewModel(app)
            withContext(Dispatchers.Main) { vm.complete(todo.item()); vm.complete(todo.item()); vm.complete(note.item()) }
            withTimeout(10000) { while (app.repository.todo(todo.id)?.isCompleted != true) delay(10) }
            assertNull(app.repository.note(note.id)?.deletedAt)
            assertEquals(1, app.database.dao().allTodos().count { it.recurrenceParentId == todo.id && it.deletedAt == null })
            withContext(Dispatchers.Main) { vm.undo(todo.item()) }
            withTimeout(10000) { while (app.repository.todo(todo.id)?.isCompleted != false) delay(10) }
            assertFalse(app.repository.todo(todo.id)!!.isCompleted)
        } finally {
            delay(300)
            (app.database.dao().allTodos().filter { it.id == todo.id || it.recurrenceParentId == todo.id }).forEach { app.repository.trash(it.id, "TODO"); app.repository.purge(it.id, "TODO") }
            app.repository.trash(note.id, "NOTE"); app.repository.purge(note.id, "NOTE")
        }
    }
}
