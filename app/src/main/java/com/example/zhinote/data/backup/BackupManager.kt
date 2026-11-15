package com.example.zhinote.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.zhinote.data.local.ZhiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class BackupManager(private val context: Context, private val db: ZhiDatabase) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val dao = db.dao()
        val data = db.withTransaction { BackupData(dao.allNotes(), dao.allTodos(), dao.allCategories(), dao.allLayouts()) }
        val bytes = BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
        require(bytes.size <= BackupCodec.MAX_BYTES) { "备份超过当前支持的 10 MB 上限" }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("无法写入文件")
    }
    suspend fun prepareImport(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(bytes.size() + count <= BackupCodec.MAX_BYTES) { "备份超过 10 MB" }
                bytes.write(buffer, 0, count)
            }
        } ?: error("无法读取文件")
        BackupCodec.decode(bytes.toString("UTF-8"))
    }
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        // Validate again before opening the write transaction. A parse failure leaves existing data untouched.
        BackupCodec.decode(BackupCodec.encode(data))
        db.withTransaction {
            val dao = db.dao()
            dao.clearLayouts(); dao.clearNotes(); dao.clearTodos(); dao.clearCategories()
            data.categories.forEach { dao.saveCategory(it) }; data.notes.forEach { dao.saveNote(it) }
            data.todos.forEach { dao.saveTodo(it) }; data.layouts.forEach { dao.saveLayout(it) }
        }
    }
}
