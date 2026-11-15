package com.example.zhinote

import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zhinote.data.local.*
import com.example.zhinote.data.repository.UserSettings
import com.example.zhinote.ui.CalendarPage
import com.example.zhinote.ui.ZhiTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.rules.ExternalResource
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class UiV21DeviceTest {
    private val activityRule = object : ExternalResource() {
        lateinit var activity: MainActivity
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Settings ready: V2.1 UI tests\n") })
            activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        }
        override fun after() { InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finish() } }
    }
    @get:Rule val compose = AndroidComposeTestRule(activityRule) { it.activity }
    private val app get() = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
    private val owned = mutableListOf<NoteEntity>()
    private val token = UUID.randomUUID().toString().take(8)
    private lateinit var baselineNotes: List<NoteEntity>
    private lateinit var baselineTodos: List<TodoEntity>
    private lateinit var baselineCategories: List<CategoryEntity>
    private lateinit var baselineLayouts: List<DropLayoutEntity>

    @Before fun baseline() = runBlocking {
        baselineNotes = app.database.dao().allNotes()
        baselineTodos = app.database.dao().allTodos()
        baselineCategories = app.database.dao().allCategories()
        baselineLayouts = app.database.dao().allLayouts()
    }
    @After fun cleanupAndCheckOriginalData() = runBlocking {
        owned.forEach { app.repository.trash(it.id, "NOTE"); app.repository.purge(it.id, "NOTE") }
        Assert.assertEquals(baselineNotes.sortedBy { it.id }, app.database.dao().allNotes().sortedBy { it.id })
        Assert.assertEquals(baselineTodos.sortedBy { it.id }, app.database.dao().allTodos().sortedBy { it.id })
        Assert.assertEquals(baselineCategories.sortedBy { it.id }, app.database.dao().allCategories().sortedBy { it.id })
        Assert.assertEquals(baselineLayouts.sortedBy { it.itemId }, app.database.dao().allLayouts().sortedBy { it.itemId })
    }
    private fun seed(count: Int) = runBlocking {
        val start = owned.size
        repeat(count) { index ->
            val note = NoteEntity(title = "蜂窝${start + index + 1}-$token", content = "UI专项临时记录 ${start + index + 1}-$token", color = (start + index) % 7)
            owned += note; app.repository.save(note)
        }
    }
    private fun state(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    private fun waitHomeCount() {
        val count = baselineNotes.count { it.deletedAt == null } + baselineTodos.count { it.deletedAt == null && !it.isCompleted } + owned.size
        compose.waitUntil(10000) { try { state("honeycomb-space").startsWith("${count}个水滴") } catch (_: AssertionError) { false } }
        compose.waitForIdle()
    }
    private fun evidence(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(app.filesDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun smallAndThirtyHoneycombItemsAppearWithoutGridDecoration() {
        seed(3); waitHomeCount(); evidence("v21-honeycomb-small.png")
        seed(27); waitHomeCount(); evidence("v21-honeycomb-30.png")
        compose.onNodeWithTag("honeycomb-space").assertIsDisplayed()
    }

    @Test fun hundredItemsPanPinchHitAndReflowWithoutChangingOriginalRecords() {
        seed(100); waitHomeCount(); evidence("v21-honeycomb-100.png")
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val vsyncs = Collections.synchronizedSet(mutableSetOf<Long>())
        val handler = HandlerThread("v21-ui-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            if (vsync > 0 && vsyncs.add(vsync)) frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        compose.runOnUiThread { activityRule.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(handler.looper)) }
        try {
            repeat(4) { index -> compose.onNodeWithTag("honeycomb-space").performTouchInput {
                if (index % 2 == 0) swipeLeft(durationMillis = 400) else swipeRight(durationMillis = 400)
            } }
            val before = state("honeycomb-space")
            compose.onNodeWithTag("honeycomb-space").performTouchInput {
                pinch(start0 = center + Offset(-45f, 0f), end0 = center + Offset(-120f, 0f),
                    start1 = center + Offset(45f, 0f), end1 = center + Offset(120f, 0f), durationMillis = 400)
            }
            compose.waitUntil(5000) { state("honeycomb-space") != before }
            evidence("v21-honeycomb-zoom.png")
            compose.onNodeWithTag("honeycomb-space").performTouchInput {
                pinch(start0 = center + Offset(-120f, 0f), end0 = center + Offset(-45f, 0f),
                    start1 = center + Offset(120f, 0f), end1 = center + Offset(45f, 0f), durationMillis = 400)
            }
            val actions = compose.onNodeWithTag("honeycomb-space").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { Assert.assertTrue(actions.first { it.label == "回到中心" }.action()) }
            val matcher = SemanticsMatcher("owned visible note") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("笔记：蜂窝") && it.contains(token) } == true
            }
            compose.waitUntil(5000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
            val description = compose.onAllNodes(matcher).onFirst().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
            val title = description.removePrefix("笔记：")
            compose.onNodeWithContentDescription(description).performTouchInput { click() }
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("收起详情").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText(title).onLast().assertIsDisplayed()
            compose.onNodeWithContentDescription("收起详情").performClick()
            val removed = owned.first { it.title == title }
            runBlocking { app.repository.trash(removed.id, "NOTE"); app.repository.purge(removed.id, "NOTE") }
            owned.remove(removed); waitHomeCount()
            seed(1); waitHomeCount()
        } finally {
            compose.runOnUiThread { activityRule.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            handler.quitSafely()
            val sorted = synchronized(frames) { frames.sorted() }
            val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
            val report = JSONObject().put("fixtureNotes", 100).put("uniqueFrameSamples", sorted.size)
                .put("meanFrameMs", if (sorted.isEmpty()) 0 else sorted.average() / 1_000_000.0)
                .put("p95FrameMs", if (sorted.isEmpty()) 0 else sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)] / 1_000_000.0)
                .put("framesOver16_67ms", sorted.count { it > 16_666_667 })
                .put("pssKb", memory.totalPss).put("note", "Debug UI gesture sample; FrameMetrics are not presented FPS")
            File(app.filesDir, "v21-performance.json").writeText(report.toString(2))
        }
    }

    @Test fun calendarCollapsesEntireHeaderExpandsAndPreservesDateModeAndFiltering() {
        seed(24)
        compose.onNodeWithText("日历", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { state("calendar-compact") == "日历已展开" }
        val label = compose.onNodeWithTag("calendar-compact").fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        val beforeHeight = compose.onNodeWithTag("calendar-records").fetchSemanticsNode().boundsInRoot.height
        evidence("v21-calendar-expanded.png")
        compose.onNodeWithTag("calendar-expanded").performTouchInput { swipeUp(durationMillis = 300) }
        compose.waitUntil(5000) { state("calendar-compact") == "日历已收起" }
        Assert.assertTrue(compose.onNodeWithTag("calendar-records").fetchSemanticsNode().boundsInRoot.height > beforeHeight + 200)
        Assert.assertEquals(label, compose.onNodeWithTag("calendar-compact").fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        compose.onNodeWithContentDescription("上一月").assertIsNotDisplayed()
        evidence("v21-calendar-collapsed.png")
        compose.onNodeWithTag("calendar-records").performTouchInput { swipeDown(durationMillis = 400) }
        compose.waitUntil(5000) { state("calendar-compact") == "日历已展开" }
        Assert.assertEquals(label, compose.onNodeWithTag("calendar-compact").fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        compose.onNodeWithTag("calendar-expanded").performTouchInput { swipeUp(durationMillis = 300) }
        compose.waitUntil(5000) { state("calendar-compact") == "日历已收起" }
        compose.onNodeWithTag("calendar-compact").performClick()
        compose.waitUntil(5000) { state("calendar-compact") == "日历已展开" }
        compose.onNodeWithText("周").performClick()
        val dates = SemanticsMatcher("calendar date") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("calendar-day-") == true }
        compose.onAllNodes(dates).assertCountEquals(7)
        compose.onNode(hasText("日") and hasClickAction()).performClick()
        compose.onAllNodes(dates).assertCountEquals(0)
        compose.onNodeWithText("月").performClick()
        Assert.assertTrue(compose.onAllNodes(dates).fetchSemanticsNodes().size >= 28)
        compose.onNodeWithTag("calendar-expanded").performTouchInput { swipeLeft(durationMillis = 350) }
        val swipeNext = LocalDate.now().plusMonths(1)
        compose.waitUntil(5000) {
            compose.onNodeWithTag("calendar-compact").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
                .startsWith("${swipeNext.year}年${swipeNext.monthValue}月${swipeNext.dayOfMonth}日")
        }
        compose.onNodeWithTag("calendar-expanded").performTouchInput { swipeRight(durationMillis = 350) }
        compose.waitUntil(5000) {
            compose.onNodeWithTag("calendar-compact").fetchSemanticsNode().config[SemanticsProperties.ContentDescription] == label
        }
        compose.onNodeWithContentDescription("下一月").performClick()
        val next = LocalDate.now().plusMonths(1)
        Assert.assertTrue(compose.onNodeWithTag("calendar-compact").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first().startsWith("${next.year}年${next.monthValue}月${next.dayOfMonth}日"))
        compose.onNodeWithContentDescription("上一月").performClick()
        val tomorrow = LocalDate.now().plusDays(1)
        compose.onNodeWithTag("calendar-day-${tomorrow.toEpochDay()}").performClick().assertIsSelected()
        compose.onNodeWithText("今天").performClick()
        compose.onNodeWithTag("calendar-records").performScrollToNode(hasText(owned.first().title))
        compose.onNodeWithText(owned.first().title).performTouchInput { click() }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("收起详情").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(owned.first().content).onLast().assertIsDisplayed()
        compose.onNodeWithContentDescription("收起详情").performClick()
    }

    @Test fun darkCalendarWithLargeFontStillSharesTheScrollHeader() {
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val notes = List(12) { NoteEntity(title = "字体适配记录${it + 1}", createdAt = today) }
        compose.runOnUiThread { activityRule.activity.setContent {
            val originalDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(originalDensity.density, 1.6f)) {
                ZhiTheme(UserSettings(theme = "DARK")) { CalendarPage(notes, emptyList()) {} }
            }
        } }
        compose.waitUntil(5000) { state("calendar-compact") == "日历已展开" }
        compose.onNodeWithTag("calendar-expanded").performTouchInput { swipeUp(durationMillis = 300) }
        compose.waitUntil(5000) { state("calendar-compact") == "日历已收起" }
        evidence("v21-calendar-dark-large-font.png")
        compose.onNodeWithTag("calendar-compact").performClick()
        compose.waitUntil(5000) { state("calendar-compact") == "日历已展开" }
    }
}
