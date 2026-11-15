package com.example.zhinote.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ZhiDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun notes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM todos ORDER BY createdAt DESC") fun todos(): Flow<List<TodoEntity>>
    @Query("SELECT * FROM categories ORDER BY name") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM drop_layout") fun layouts(): Flow<List<DropLayoutEntity>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun note(id: String): NoteEntity?
    @Query("SELECT * FROM todos WHERE id = :id") suspend fun todo(id: String): TodoEntity?
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<NoteEntity>
    @Query("SELECT * FROM todos") suspend fun allTodos(): List<TodoEntity>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM drop_layout") suspend fun allLayouts(): List<DropLayoutEntity>
    @Upsert suspend fun saveNote(note: NoteEntity)
    @Upsert suspend fun saveTodo(todo: TodoEntity)
    @Upsert suspend fun saveCategory(category: CategoryEntity)
    @Upsert suspend fun saveLayout(layout: DropLayoutEntity)
    @Query("DELETE FROM notes WHERE id = :id AND deletedAt IS NOT NULL") suspend fun purgeNote(id: String)
    @Query("DELETE FROM todos WHERE id = :id AND deletedAt IS NOT NULL") suspend fun purgeTodo(id: String)
    @Query("DELETE FROM drop_layout WHERE itemId = :id AND itemType = :type") suspend fun removeLayout(id: String, type: String)
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM todos") suspend fun clearTodos()
    @Query("DELETE FROM drop_layout") suspend fun clearLayouts()
    @Query("UPDATE todos SET reminderDeliveredAt = :now WHERE id = :id AND reminderTime = :time AND reminderDeliveredAt IS NULL AND isCompleted = 0 AND deletedAt IS NULL")
    suspend fun claimReminder(id: String, time: Long, now: Long): Int
    @Query("UPDATE todos SET reminderDeliveredAt = NULL WHERE id = :id AND reminderTime = :time")
    suspend fun releaseReminder(id: String, time: Long)
}
