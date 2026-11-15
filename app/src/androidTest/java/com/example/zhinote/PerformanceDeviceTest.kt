package com.example.zhinote

import android.os.*
import android.view.FrameMetrics
import android.view.Window
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zhinote.data.local.NoteEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Short on-device samples, not a sustained thermal or release-build benchmark. */
@RunWith(AndroidJUnit4::class)
class PerformanceDeviceTest {
    @Test fun measure10_20_40_60Drops() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val original = app.settings.settings.first()
        val output = JSONArray()
        val baseline = app.database.dao().allNotes().count { it.deletedAt == null } + app.database.dao().allTodos().count { it.deletedAt == null && !it.isCompleted }
        app.settings.boolean("floating", true); app.settings.boolean("reduced", false); app.settings.boolean("performance", false)
        app.settings.maxDrops(60)
        try {
            for (count in listOf(10, 20, 40, 60)) {
                InstrumentationRegistry.getInstrumentation().sendStatus(1, Bundle().apply { putString("stream", "Settings ready: performance $count drops\n") })
                val notes = List(count) { index -> NoteEntity(title = listOf("一滴灵感", "花园里的风", "周末计划", "读书随记", "湖面的光")[index % 5], color = index % 7) }
                notes.forEach { app.repository.save(it) }
                val duration = Collections.synchronizedList(mutableListOf<Long>())
                val gpu = Collections.synchronizedList(mutableListOf<Long>())
                val seenVsync = Collections.synchronizedSet(HashSet<Long>())
                val rawCallbacks = AtomicInteger(0)
                val handlerThread = HandlerThread("zhinote-frame-metrics").apply { start() }
                var refreshRate = 0f
                val measuring = AtomicBoolean(false)
                val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                    if (measuring.get()) {
                        rawCallbacks.incrementAndGet()
                        val vsync = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
                        if (vsync > 0 && !seenVsync.add(vsync)) return@OnFrameMetricsAvailableListener
                        duration.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                        if (Build.VERSION.SDK_INT >= 31) gpu.add(metrics.getMetric(FrameMetrics.GPU_DURATION))
                    }
                }
                try {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
                    try {
                        instrumentation.runOnMainSync {
                            @Suppress("DEPRECATION")
                            refreshRate = activity.windowManager.defaultDisplay.refreshRate
                            activity.window.addOnFrameMetricsAvailableListener(listener, Handler(handlerThread.looper))
                        }
                        Thread.sleep(1500)
                        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                            File(app.filesDir, "constellation-requested$count.png").outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }; bitmap.recycle()
                        }
                        val start = SystemClock.elapsedRealtime()
                        val cpuStart = android.os.Process.getElapsedCpuTime()
                        measuring.set(true)
                        Thread.sleep(10000)
                        measuring.set(false)
                        val elapsed = SystemClock.elapsedRealtime() - start
                        val cpuMs = android.os.Process.getElapsedCpuTime() - cpuStart
                        instrumentation.runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener) }
                        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                        val sorted = synchronized(duration) { duration.sorted() }
                        val gpuSamples = synchronized(gpu) { gpu.filter { it >= 0 } }
                        val budgetNs = 1_000_000_000.0 / refreshRate.coerceAtLeast(1f)
                        val row = JSONObject().put("requestedDrops", count).put("baselineItems", baseline).put("renderedDrops", (baseline + count).coerceAtMost(60))
                            .put("displayHz", refreshRate).put("sampleMs", elapsed).put("frameCallbacks", sorted.size)
                            .put("rawFrameCallbacks", rawCallbacks.get()).put("uniqueVsyncTimestamps", seenVsync.size)
                            .put("frameCallbacksPerSecond", sorted.size * 1000.0 / elapsed)
                            .put("meanFrameMs", if (sorted.isEmpty()) 0.0 else sorted.average() / 1_000_000.0)
                            .put("p95FrameMs", if (sorted.isEmpty()) 0.0 else sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)] / 1_000_000.0)
                            .put("framesOverDisplayBudget", sorted.count { it > budgetNs })
                            .put("gpuMeanMs", if (gpuSamples.isEmpty()) JSONObject.NULL else gpuSamples.average() / 1_000_000.0)
                            .put("totalPssKb", memory.totalPss).put("processCpuMs", cpuMs)
                        output.put(row)
                    } finally { instrumentation.runOnMainSync { activity.finish() } }
                } finally {
                    handlerThread.quitSafely()
                    notes.forEach { app.repository.trash(it.id, "NOTE"); app.repository.purge(it.id, "NOTE") }
                }
            }
            File(app.filesDir, "performance.json").writeText(JSONObject().put("build", "debug").put("device", Build.MODEL)
                .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT).put("scenarios", output).toString(2))
        } finally {
            app.settings.boolean("floating", original.floating); app.settings.boolean("reduced", original.reducedMotion); app.settings.boolean("performance", original.performance)
            app.settings.maxDrops(original.maxDrops)
        }
    }
}
