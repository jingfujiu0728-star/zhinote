package com.example.zhinote.data.repository

import androidx.room.withTransaction
import com.example.zhinote.data.local.*
import com.example.zhinote.domain.TodoRules

class ZhiRepository(private val db: ZhiDatabase) {
    private val dao = db.dao()
    val notes = dao.notes()
    val todos = dao.todos()
    val categories = dao.categories()
    val layouts = dao.layouts()
    suspend fun note(id: String): NoteEntity? = dao.note(id)
    suspend fun todo(id: String): TodoEntity? = dao.todo(id)

    suspend fun save(note: NoteEntity) = dao.saveNote(note.copy(updatedAt = System.currentTimeMillis()))
    suspend fun save(todo: TodoEntity) = db.withTransaction {
        require(todo.priority in 0..2 && todo.repeatRule in TodoRules.repeatRules)
        val old = dao.todo(todo.id)
        dao.saveTodo(todo.copy(updatedAt = System.currentTimeMillis(), reminderDeliveredAt =
            if (old?.reminderTime == todo.reminderTime) old?.reminderDeliveredAt else null))
    }
    suspend fun saveCategory(name: String, color: Int) {
        require(name.trim().isNotEmpty())
        dao.saveCategory(CategoryEntity(name = name.trim(), color = color))
    }
    suspend fun saveLayout(layout: DropLayoutEntity) {
        require(layout.normalizedX.isFinite() && layout.normalizedY.isFinite())
        dao.saveLayout(layout.copy(normalizedX = layout.normalizedX.coerceIn(0f, 1f), normalizedY = layout.normalizedY.coerceIn(0f, 1f)))
    }
    /** Only returns after the completion and next occurrence are durably committed. */
    suspend fun complete(id: String): TodoEntity? = db.withTransaction {
        val item = dao.todo(id) ?: return@withTransaction null
        if (item.isCompleted || item.deletedAt != null) return@withTransaction null
        val now = System.currentTimeMillis()
        val completed = item.copy(isCompleted = true, completedAt = now, updatedAt = now)
        dao.saveTodo(completed)
        TodoRules.nextOccurrence(item, now)?.let { dao.saveTodo(it) }
        completed
    }
    suspend fun undoComplete(id: String) = db.withTransaction {
        val item = dao.todo(id) ?: return@withTransaction
        if (!item.isCompleted) return@withTransaction
        // Keep an already edited/completed successor. Remove only the untouched generated copy.
        dao.allTodos().filter { it.recurrenceParentId == id && !it.isCompleted && it.updatedAt == it.createdAt }
            .forEach { dao.saveTodo(it.copy(deletedAt = System.currentTimeMillis())) }
        dao.saveTodo(item.copy(isCompleted = false, completedAt = null, updatedAt = System.currentTimeMillis()))
    }
    suspend fun trash(id: String, type: String, restore: Boolean = false) = db.withTransaction {
        val deletedAt = if (restore) null else System.currentTimeMillis()
        if (type == "NOTE") dao.note(id)?.let { dao.saveNote(it.copy(deletedAt = deletedAt)) }
        else dao.todo(id)?.let { dao.saveTodo(it.copy(deletedAt = deletedAt)) }
    }
    suspend fun purge(id: String, type: String) = db.withTransaction {
        val trashed = if (type == "NOTE") dao.note(id)?.deletedAt != null else dao.todo(id)?.deletedAt != null
        if (!trashed) return@withTransaction
        if (type == "NOTE") dao.purgeNote(id) else dao.purgeTodo(id)
        dao.removeLayout(id, type)
    }
}
