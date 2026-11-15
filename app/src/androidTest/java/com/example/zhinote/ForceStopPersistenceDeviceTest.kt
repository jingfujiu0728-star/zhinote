package com.example.zhinote

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zhinote.data.local.NoteEntity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** The external v2-regression script runs this across an actual am force-stop. */
class ForceStopPersistenceDeviceTest {
    @Test fun savedNoteSurvivesProcessRestart() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val phase = InstrumentationRegistry.getArguments().getString("persistPhase", "roundtrip")
        val note = NoteEntity(id = "v2-force-stop-owned-test", title = "V2强制关闭持久化测试", content = "第一行\n第二行 🌿 <>& \"长文本\"".repeat(3000), tags = "回归", createdAt = 123, updatedAt = 123)
        if (phase != "verify") {
            val vm = com.example.zhinote.ui.AppViewModel(app)
            withContext(Dispatchers.Main) { vm.edit(); vm.update(note); vm.closeEditor() }
            withTimeout(15000) { while (vm.editorNote.value != null || vm.saving.value) delay(20) }
            assertEquals(note.content, app.repository.note(note.id)?.content)
        }
        if (phase != "seed") try {
            val actual = app.repository.note(note.id)
            assertNotNull("Note must survive the external process force-stop", actual)
            assertEquals(note.title, actual!!.title); assertEquals(note.content, actual.content); assertEquals(note.tags, actual.tags)
        } finally { app.repository.trash(note.id, "NOTE"); app.repository.purge(note.id, "NOTE") }
    }
}
