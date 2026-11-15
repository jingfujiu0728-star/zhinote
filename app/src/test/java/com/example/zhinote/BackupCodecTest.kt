package com.example.zhinote

import com.example.zhinote.data.backup.*
import com.example.zhinote.data.local.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val category = CategoryEntity(id = "work", name = "工作")
    private val note = NoteEntity(id = "n", title = "莫奈的花园 🌿", content = "长文本\n第二行", categoryId = "work", tags = "灵感,绘画")
    private val todo = TodoEntity(id = "t", title = "喝水", isCompleted = true, completedAt = 42, reminderDeliveredAt = 24)
    private val data = BackupData(listOf(note), listOf(todo), listOf(category), listOf(DropLayoutEntity("n", "NOTE", .2f, .9f)))
    @Test fun allBusinessAndLayoutFieldsRoundTrip() { assertEquals(data, BackupCodec.decode(BackupCodec.encode(data))) }
    @Test fun corruptVersionDuplicatesAndInvalidCoordinatesAreRejected() {
        val version = JSONObject(BackupCodec.encode(data)).put("version", 3)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(version.toString()) }
        version.put("version", 1.5)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(version.toString()) }
        val duplicated = JSONObject(BackupCodec.encode(data))
        duplicated.getJSONArray("notes").put(duplicated.getJSONArray("notes").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(duplicated.toString()) }
        val invalid = JSONObject(BackupCodec.encode(data))
        invalid.getJSONArray("layouts").getJSONObject(0).put("normalizedX", 1.1)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(invalid.toString()) }
    }
    @Test fun orphanCategoryAndInconsistentCompletionAreRejected() {
        val orphan = data.copy(categories = emptyList())
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(orphan)) }
        val inconsistent = data.copy(todos = listOf(todo.copy(completedAt = null)))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(inconsistent)) }
    }
    @Test fun summaryRoundTripsAndVersionOneRemainsReadable() {
        val updated = data.copy(notes = listOf(note.copy(summary = "摘要")))
        assertEquals(updated, BackupCodec.decode(BackupCodec.encode(updated)))
        val old = JSONObject(BackupCodec.encode(data)).put("version", 1)
        old.getJSONArray("notes").getJSONObject(0).remove("summary")
        assertEquals(data, BackupCodec.decode(old.toString()))
    }
}
