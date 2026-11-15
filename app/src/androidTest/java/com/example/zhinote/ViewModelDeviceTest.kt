package com.example.zhinote

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zhinote.data.local.NoteEntity
import com.example.zhinote.ui.AppViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewModelDeviceTest {
    @Test fun rapidAutoSaveKeepsLastDraftAndCloseFlushes() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val vm = AppViewModel(app)
        val note = NoteEntity(title = "ViewModel 队列测试")
        try {
            withContext(Dispatchers.Main) {
                repeat(30) { vm.update(note.copy(content = "最新草稿 $it")) }
                vm.closeEditor()
            }
            withTimeout(10000) { while (app.database.dao().note(note.id)?.content != "最新草稿 29" || vm.editorNote.value != null) delay(20) }
            assertEquals("最新草稿 29", app.database.dao().note(note.id)!!.content)
            assertNull(vm.editorNote.value)
        } finally { app.repository.trash(note.id, "NOTE"); app.repository.purge(note.id, "NOTE") }
    }
}
