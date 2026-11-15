package com.example.zhinote

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zhinote.ai.AiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicitly opted-in checks only; never sends requests during ordinary regression. */
class AiLiveDeviceTest {
    @Test fun savedProviderCanConnectAndGenerateWithoutWritingData() = runBlocking {
        assumeTrue("Live provider checks require -e liveAi true",
            InstrumentationRegistry.getArguments().getString("liveAi") == "true")
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val profile = app.aiConfigs.read().selected
        assertNotNull("Save and select an AI provider before the live check", profile)
        println("Saved encrypted AI configuration loaded; credentials stay on device.")
        assertTrue(AiClient().test(profile!!).startsWith("连接成功"))
        println("Live connection test passed.")
        val noteIdsBefore = app.database.dao().allNotes().map { it.id }.toSet()
        val todoIdsBefore = app.database.dao().allTodos().map { it.id }.toSet()
        val preview = AiClient().create(profile, "记录一条测试笔记：AI连接验证。正文为：接口已连通。", "NOTE")
        assertEquals("NOTE", preview.type)
        assertTrue(preview.title.isNotBlank() || preview.content.isNotBlank())
        assertEquals(noteIdsBefore, app.database.dao().allNotes().map { it.id }.toSet())
        assertEquals(todoIdsBefore, app.database.dao().allTodos().map { it.id }.toSet())
        println("Live note preview parsed; no note or todo was written.")
    }
}
