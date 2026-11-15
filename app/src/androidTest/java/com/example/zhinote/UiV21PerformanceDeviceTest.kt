package com.example.zhinote

import android.content.Intent
import android.graphics.Rect
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zhinote.data.local.NoteEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.UUID

/** Real-time input and Choreographer, independent of Compose's virtual test clock. */
class UiV21PerformanceDeviceTest {
    @Test fun hundredItemsWithRealtimePanAndPinch() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val beforeNotes = app.database.dao().allNotes()
        val beforeTodos = app.database.dao().allTodos()
        val token = UUID.randomUUID().toString()
        val notes = List(100) { NoteEntity(title = "蜂窝性能${it + 1}-$token", color = it % 7) }
        // Write ownership before insertion so an interrupted test can recover its own rows.
        val ownedIds = File(app.filesDir, "v21-performance-owned-ids.txt")
        ownedIds.writeText(notes.joinToString("\n") { it.id })
        val samples = Collections.synchronizedList(mutableListOf<Long>())
        val seen = Collections.synchronizedSet(mutableSetOf<Long>())
        val handler = HandlerThread("v21-realtime-frames").apply { start() }
        var activity: MainActivity? = null
        var listenerAttached = false
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            if (vsync > 0 && seen.add(vsync)) samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        fun bounds(node: AccessibilityNodeInfo?): Rect? {
            if (node == null) return null
            if (node.contentDescription?.toString() == "蜂窝水滴空间") return Rect().also { node.getBoundsInScreen(it) }
            for (i in 0 until node.childCount) bounds(node.getChild(i))?.let { return it }
            return null
        }
        fun event(down: Long, action: Int, positions: List<Pair<Float, Float>>) {
            val properties = Array(positions.size) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coordinates = positions.map { (px, py) -> MotionEvent.PointerCoords().apply { x = px; y = py; pressure = 1f; size = 1f } }.toTypedArray()
            val input = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, positions.size, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(input, true)) } finally { input.recycle() }
        }
        try {
            notes.forEach { app.repository.save(it) }
            instrumentation.sendStatus(1, Bundle().apply { putString("stream", "Settings ready: V2.1 realtime performance\n") })
            activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            Thread.sleep(1200)
            val area = bounds(instrumentation.uiAutomation.rootInActiveWindow)
            assertNotNull("Honeycomb viewport must be visible", area)
            val region = area!!
            val cy = region.exactCenterY()
            instrumentation.runOnMainSync { activity!!.window.addOnFrameMetricsAvailableListener(listener, Handler(handler.looper)); listenerAttached = true }
            val start = SystemClock.elapsedRealtime()
            repeat(4) { index ->
                val x0 = if (index % 2 == 0) region.left + region.width() * .75f else region.left + region.width() * .25f
                val x1 = if (index % 2 == 0) region.left + region.width() * .25f else region.left + region.width() * .75f
                val down = SystemClock.uptimeMillis()
                event(down, MotionEvent.ACTION_DOWN, listOf(x0 to cy))
                repeat(30) { step -> Thread.sleep(16); event(down, MotionEvent.ACTION_MOVE, listOf((x0 + (x1 - x0) * (step + 1) / 30f) to cy)) }
                event(down, MotionEvent.ACTION_UP, listOf(x1 to cy))
                Thread.sleep(180)
            }
            repeat(2) { index ->
                val cx = region.exactCenterX()
                val from = region.width() * (if (index == 0) .08f else .24f)
                val to = region.width() * (if (index == 0) .24f else .08f)
                val down = SystemClock.uptimeMillis()
                event(down, MotionEvent.ACTION_DOWN, listOf((cx - from) to cy))
                event(down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf((cx - from) to cy, (cx + from) to cy))
                repeat(30) { step ->
                    Thread.sleep(16)
                    val spread = from + (to - from) * (step + 1) / 30f
                    event(down, MotionEvent.ACTION_MOVE, listOf((cx - spread) to cy, (cx + spread) to cy))
                }
                event(down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf((cx - to) to cy, (cx + to) to cy))
                event(down, MotionEvent.ACTION_UP, listOf((cx - to) to cy))
                Thread.sleep(180)
            }
            instrumentation.runOnMainSync { activity!!.window.removeOnFrameMetricsAvailableListener(listener); listenerAttached = false }
            val elapsed = SystemClock.elapsedRealtime() - start
            val sorted = synchronized(samples) { samples.sorted() }
            assertTrue("Realtime gestures must produce frame samples", sorted.size >= 30)
            val report = JSONObject().put("fixtureNotes", 100).put("sampleMs", elapsed)
                .put("uniqueFrameSamples", sorted.size).put("meanFrameMs", sorted.average() / 1_000_000.0)
                .put("p95FrameMs", sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)] / 1_000_000.0)
                .put("framesOver16_67ms", sorted.count { it > 16_666_667 })
                .put("note", "Debug, realtime touch input; FrameMetrics are not SurfaceFlinger presented FPS")
            File(app.filesDir, "v21-realtime-performance.json").writeText(report.toString(2))
        } finally {
            try {
                activity?.let { current -> instrumentation.runOnMainSync {
                    try { if (listenerAttached) current.window.removeOnFrameMetricsAvailableListener(listener) }
                    finally { current.finish() }
                } }
            } finally {
                handler.quitSafely()
                notes.forEach { app.repository.trash(it.id, "NOTE"); app.repository.purge(it.id, "NOTE") }
                ownedIds.delete()
            }
            assertEquals(beforeNotes.sortedBy { it.id }, app.database.dao().allNotes().sortedBy { it.id })
            assertEquals(beforeTodos.sortedBy { it.id }, app.database.dao().allTodos().sortedBy { it.id })
        }
    }
}
