package com.example.zhinote

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zhinote.data.local.TodoEntity
import com.example.zhinote.widgets.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WidgetDeviceTest {
    @Test fun providersRemoteViewsAndCompletionPendingIntentWorkOnDevice() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
        val manager = AppWidgetManager.getInstance(app)
        ZhiWidgets.providers.forEach { assertNotNull(manager.installedProviders.firstOrNull { p -> p.provider == ComponentName(app, it) }) }
        val item = TodoEntity(title = "小部件设备测试-${System.nanoTime()}", priority = 2)
        app.repository.save(item)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 310) }
                val overview = ZhiWidgets.render(app, OverviewWidget::class.java, listOf(item), options).apply(app, LinearLayout(app))
                assertTrue(overview.findViewById<TextView>(R.id.widget_counts).text.toString().contains("今日 1 项"))
                val views = ZhiWidgets.render(app, TodoListWidget::class.java, listOf(item), options).apply(app, LinearLayout(app))
                assertEquals(item.title, views.findViewById<TextView>(R.id.widget_title).text.toString())
                assertEquals("高", views.findViewById<TextView>(R.id.widget_priority).text.toString())
                views.findViewById<TextView>(R.id.widget_check).performClick()
            }
            withTimeout(10000) { while (app.repository.todo(item.id)?.isCompleted != true) delay(20) }
            val completed = app.repository.todo(item.id)!!
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val views = ZhiWidgets.render(app, TodoListWidget::class.java, listOf(completed), Bundle()).apply(app, LinearLayout(app))
                assertEquals("✓", views.findViewById<TextView>(R.id.widget_check).text.toString())
                views.findViewById<TextView>(R.id.widget_check).performClick()
            }
            withTimeout(10000) { while (app.repository.todo(item.id)?.isCompleted != false) delay(20) }
        } finally { app.repository.trash(item.id, "TODO"); app.repository.purge(item.id, "TODO") }
    }
}
