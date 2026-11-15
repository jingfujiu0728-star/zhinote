package com.example.zhinote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zhinote.data.local.TodoEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.example.zhinote.data.repository.UserSettings
import org.junit.Before
import org.junit.After
import org.junit.rules.RuleChain
import org.junit.rules.ExternalResource
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Bundle
import android.content.Intent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AppDeviceTest {
    private val activityRule = object : ExternalResource() {
        lateinit var activity: MainActivity
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        }
        override fun after() { InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finish() } }
    }
    val compose = AndroidComposeTestRule(activityRule) { it.activity }
    private val app get() = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
    private lateinit var original: UserSettings
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            InstrumentationRegistry.getInstrumentation().sendStatus(1, Bundle().apply { putString("stream", "Preparing reduced-motion settings\n") })
            runBlocking {
                original = app.settings.settings.first()
                app.settings.boolean("floating", false); app.settings.boolean("reduced", true)
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(1, Bundle().apply { putString("stream", "Settings ready\n") })
        }
        override fun after() { runBlocking { app.settings.boolean("floating", original.floating); app.settings.boolean("reduced", original.reducedMotion) } }
    }).around(compose)
    @After fun cleanup() {
        runBlocking {
            app.database.dao().allNotes().filter { it.title.startsWith("设备测试笔记-") }.forEach { app.repository.trash(it.id, "NOTE"); app.repository.purge(it.id, "NOTE") }
            app.database.dao().allTodos().filter { it.title.startsWith("设备测试待办-") }.forEach { app.repository.trash(it.id, "TODO"); app.repository.purge(it.id, "TODO") }
        }
    }
    @Test fun editorAutoSaveAndNoteExpansionKeepContent() {
        InstrumentationRegistry.getInstrumentation().sendStatus(1, Bundle().apply { putString("stream", "Editor test body\n") })
        val title = "设备测试笔记-${System.currentTimeMillis()}"
        compose.onNodeWithContentDescription("新增笔记").performClick()
        compose.onNodeWithText("标题").performTextInput(title)
        compose.onNodeWithText("正文 · 灵感从这里开始").performTextInput("自动保存正文，点击水滴不会删除。")
        compose.onNodeWithText("保存并返回").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking { app.database.dao().allNotes().any { it.title == title && it.content.contains("自动保存正文") } } }
        compose.onNodeWithContentDescription("笔记：$title").performClick()
        try { compose.waitUntil(10000) { try { compose.onNodeWithText(title).assertIsDisplayed(); true } catch (_: AssertionError) { false } } }
        catch (error: Throwable) {
            println(compose.onRoot().printToString())
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(app.filesDir, "p0-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            throw error
        }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("自动保存正文，点击水滴不会删除。").assertIsDisplayed()
        val note = runBlocking { app.database.dao().allNotes().first { it.title == title } }
        assertNull(note.deletedAt)
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("标题").performTextReplacement("$title-编辑")
        compose.onNodeWithText("正文 · 灵感从这里开始").performTextReplacement("编辑后仍保留同一个 ID\n第二行 🌿 <>&")
        compose.onNodeWithText("保存并返回").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.note(note.id)?.content?.contains("同一个 ID") == true } }
        assertEquals("$title-编辑", runBlocking { app.repository.note(note.id)!!.title })
        assertEquals(1, runBlocking { app.database.dao().allNotes().count { it.title.startsWith(title) } })
        runBlocking { app.repository.trash(note.id, "NOTE"); app.repository.purge(note.id, "NOTE") }
    }
    @Test fun todoCompletionAndHistoryUndoPersistOnDevice() {
        val item = TodoEntity(title = "设备测试待办-${System.currentTimeMillis()}")
        runBlocking { app.repository.save(item) }
        compose.onNodeWithText("待办", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("完成：${item.title}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("完成：${item.title}").performClick()
        compose.waitUntil(10000) { runBlocking { app.database.dao().todo(item.id)?.isCompleted == true } }
        compose.onNodeWithText("已完成").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("撤销完成：${item.title}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("撤销完成：${item.title}").performClick()
        compose.waitUntil(10000) { runBlocking { app.database.dao().todo(item.id)?.isCompleted == false } }
        runBlocking { app.repository.trash(item.id, "TODO"); app.repository.purge(item.id, "TODO") }
    }
    @Test fun physicalDoubleTapCompletesOnceSnackbarUndoAndNoteSafety() {
        val todo = TodoEntity(title = "设备测试待办-${System.currentTimeMillis()}")
        val note = com.example.zhinote.data.local.NoteEntity(title = "设备测试笔记-${System.currentTimeMillis()}", content = "双击笔记保留正文")
        runBlocking { app.repository.save(todo); app.repository.save(note); app.settings.boolean("reduced", false) }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("待办：${todo.title}").fetchSemanticsNodes().isNotEmpty() }
        // Keep the test clock from fast-forwarding the four-second undo affordance while
        // waiting for the always-floating space to become idle.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("待办：${todo.title}").performTouchInput { doubleClick() }
        compose.waitUntil(10000) { runBlocking { app.repository.todo(todo.id)?.isCompleted == true } }
        compose.mainClock.advanceTimeBy(300)
        compose.onAllNodesWithContentDescription("收起详情").assertCountEquals(0)
        compose.onNodeWithText("撤销").assertIsDisplayed()
        evidence("double-completed.png")
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.todo(todo.id)?.isCompleted == false } }
        compose.mainClock.advanceTimeBy(300)
        compose.waitUntil(10000) { compose.mainClock.advanceTimeBy(32); compose.onAllNodesWithContentDescription("待办：${todo.title}").fetchSemanticsNodes().isNotEmpty() }
        evidence("double-undone.png")
        compose.onNodeWithContentDescription("笔记：${note.title}").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitUntil(10000) { compose.mainClock.advanceTimeBy(32); compose.onAllNodesWithText("双击笔记保留正文").fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking { app.repository.note(note.id)?.deletedAt })
        assertEquals(false, runBlocking { app.repository.todo(todo.id)?.isCompleted })
        compose.onNodeWithContentDescription("收起详情").performClick()
        compose.mainClock.autoAdvance = true
    }
    private fun evidence(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(app.filesDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
