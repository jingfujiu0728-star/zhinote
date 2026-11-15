package com.example.zhinote.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes", indices = [Index("deletedAt"), Index("updatedAt"), Index("categoryId")])
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "", val content: String = "", val color: Int = 0,
    val categoryId: String? = null, val tags: String = "", val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "''") val summary: String = "",
)

@Entity(tableName = "todos", indices = [Index("deletedAt"), Index("dueDate"), Index("reminderTime"), Index("isCompleted"), Index("categoryId")])
data class TodoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "", val description: String = "", val priority: Int = 1,
    val dueDate: Long? = null, val reminderTime: Long? = null,
    val isCompleted: Boolean = false, val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val repeatRule: String = "NONE", val deletedAt: Long? = null,
    val categoryId: String? = null, val tags: String = "",
    val recurrenceParentId: String? = null,
    val reminderDeliveredAt: Long? = null,
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
    val color: Int = 0, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "drop_layout", primaryKeys = ["itemId", "itemType"])
data class DropLayoutEntity(val itemId: String, val itemType: String,
    val normalizedX: Float, val normalizedY: Float, val size: Float = 64f,
    val colorOverride: Int? = null, val updatedAt: Long = System.currentTimeMillis())
