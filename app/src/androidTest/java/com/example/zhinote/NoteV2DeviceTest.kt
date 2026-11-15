package com.example.zhinote

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zhinote.data.local.NoteEntity
import com.example.zhinote.ui.AppViewModel
import com.example.zhinote.ui.item
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteV2DeviceTest {
    @Test fun openingExistingNoteDoesNotDependOnListSubscription() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val note = NoteEntity(title = "V2 回归：原有笔记", content = "原正文")
        app.repository.save(note)
        try {
            val vm = AppViewModel(app)
            withContext(Dispatchers.Main) { vm.edit(note.item()) }
            withTimeout(10000) { while (vm.editorLoading.value) delay(20) }
            assertEquals("Editing must keep the stored ID", note.id, vm.editorNote.value?.id)
            assertEquals("原正文", vm.editorNote.value?.content)
        } finally { app.repository.trash(note.id, "NOTE"); app.repository.purge(note.id, "NOTE") }
    }
    @Test fun consecutiveTitleAndBodyEventsKeepBothFields() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val vm = AppViewModel(app)
        lateinit var note: NoteEntity
        withContext(Dispatchers.Main) {
            vm.edit(type = "NOTE")
            note = vm.editorNote.value!!
            vm.patchNote { it.copy(title = "保留标题") }
            vm.patchNote { it.copy(content = "保留正文\n特殊字符 🌿 <>&\"") }
        }
        try {
            assertEquals("保留标题", vm.editorNote.value?.title)
            withContext(Dispatchers.Main) { vm.closeEditor() }
            withTimeout(10000) { while (vm.editorNote.value != null) delay(20) }
        } finally { app.repository.trash(note.id, "NOTE"); app.repository.purge(note.id, "NOTE") }
    }
    @Test fun titleOnlyBodyOnlyLongTextAndFailureRetainDraft() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val values = listOf("标题" to "", "" to "只有正文", "标题 🌿 <>&\"" to ("多行\n特殊字符中文\n".repeat(5000)))
        for ((title, body) in values) {
            val vm = AppViewModel(app)
            lateinit var note: NoteEntity
            withContext(Dispatchers.Main) {
                vm.edit(); vm.patchNote { it.copy(title = title) }; vm.patchNote { it.copy(content = body) }
                note = vm.editorNote.value!!; vm.closeEditor()
            }
            withTimeout(15000) { while (vm.editorNote.value != null) delay(20) }
            try {
                assertEquals(title, app.repository.note(note.id)?.title); assertEquals(body, app.repository.note(note.id)?.content)
                val reopened = com.example.zhinote.data.local.ZhiDatabase.open(app)
                try { assertEquals(body, reopened.dao().note(note.id)?.content) } finally { reopened.close() }
            } finally { app.repository.trash(note.id, "NOTE"); app.repository.purge(note.id, "NOTE") }
        }
        val failing = AppViewModel(app) { throw java.io.IOException("测试写入失败") }
        withContext(Dispatchers.Main) { failing.edit(); failing.patchNote { it.copy(content = "不能清空的输入") }; failing.closeEditor() }
        withTimeout(10000) { while (failing.editorError.value == null) delay(20) }
        assertEquals("不能清空的输入", failing.editorNote.value?.content)
        assertTrue(failing.editorError.value!!.contains("保存失败"))
    }
}
