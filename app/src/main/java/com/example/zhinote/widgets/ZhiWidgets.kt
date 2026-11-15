package com.example.zhinote.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.example.zhinote.MainActivity
import com.example.zhinote.R
import com.example.zhinote.ZhiNoteApplication
import com.example.zhinote.data.local.TodoEntity
import kotlinx.coroutines.*
import java.time.*

data class TodayCounts(val total: Int, val completed: Int) {
    val remaining get() = total - completed
    val progress get() = if (total == 0) 0f else completed.toFloat() / total
}
object WidgetData {
    fun today(todos: List<TodoEntity>, date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): TodayCounts {
        val today = todos.filter { it.deletedAt == null && Instant.ofEpochMilli(it.dueDate ?: it.createdAt).atZone(zone).toLocalDate() == date }
        return TodayCounts(today.size, today.count { it.isCompleted })
    }
    fun recent(todos: List<TodoEntity>, count: Int) = todos.filter { it.deletedAt == null }.sortedWith(
        compareBy<TodoEntity> { it.isCompleted }.thenBy { if (it.isCompleted) -(it.completedAt ?: 0) else it.dueDate ?: Long.MAX_VALUE }
            .thenByDescending { it.priority }.thenByDescending { it.createdAt }).take(count)
}

object ZhiWidgets {
    val providers = listOf(OverviewWidget::class.java, QuickAddWidget::class.java, TodoListWidget::class.java, ProgressWidget::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun launch(block: suspend () -> Unit) { scope.launch { try { block() } catch (_: Exception) { /* System retries its next update. */ } } }
    fun open(context: Context, action: String, id: String? = null): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setData(Uri.Builder().scheme("zhinote").authority("widget").appendPath(action).apply { id?.let { appendPath(it) } }.build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun completion(context: Context, item: TodoEntity) = PendingIntent.getBroadcast(context, 0,
        Intent(context, WidgetActionReceiver::class.java).setAction(if (item.isCompleted) "zhinote.widget.UNDO" else "zhinote.widget.COMPLETE")
            .setData(Uri.parse("zhinote://widget/todo/${item.id}")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val registered = providers.associateWith { manager.getAppWidgetIds(ComponentName(context, it)) }
        if (registered.values.all { it.isEmpty() }) return
        val todos = (context.applicationContext as ZhiNoteApplication).database.dao().allTodos()
        registered.forEach { (provider, ids) -> ids.forEach { id -> manager.updateAppWidget(id, render(context, provider, todos, manager.getAppWidgetOptions(id))) } }
    }
    fun showError(context: Context, message: String) {
        val manager = AppWidgetManager.getInstance(context)
        providers.filterNot { it == QuickAddWidget::class.java }.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) manager.partiallyUpdateAppWidget(ids, RemoteViews(context.packageName,
                if (provider == TodoListWidget::class.java) R.layout.widget_todos else R.layout.widget_overview).apply {
                setTextViewText(R.id.widget_footer, message)
            })
        }
    }
    fun render(context: Context, provider: Class<out AppWidgetProvider>, todos: List<TodoEntity>, options: Bundle): RemoteViews {
        if (provider == QuickAddWidget::class.java) return RemoteViews(context.packageName, R.layout.widget_quick).apply {
            setOnClickPendingIntent(R.id.widget_add_note, open(context, "add-note"))
            setOnClickPendingIntent(R.id.widget_add_todo, open(context, "add-todo"))
            setOnClickPendingIntent(R.id.widget_add_ai, open(context, "ai"))
        }
        if (provider == TodoListWidget::class.java) return RemoteViews(context.packageName, R.layout.widget_todos).apply {
            removeAllViews(R.id.widget_rows)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 210)
            val rows = ((height - 65) / (44 * context.resources.configuration.fontScale.coerceAtLeast(1f))).toInt().coerceIn(3, 5)
            val items = WidgetData.recent(todos, rows)
            items.forEach { item -> addView(R.id.widget_rows, RemoteViews(context.packageName, R.layout.widget_todo_row).apply {
                setTextViewText(R.id.widget_title, item.title.ifBlank { "未命名待办" })
                setTextViewText(R.id.widget_check, if (item.isCompleted) "✓" else "○")
                setContentDescription(R.id.widget_check, if (item.isCompleted) "撤销完成：${item.title}" else "完成：${item.title}")
                setTextViewText(R.id.widget_priority, listOf("低", "普通", "高")[item.priority.coerceIn(0, 2)])
                setOnClickPendingIntent(R.id.widget_title, open(context, "open-todo", item.id))
                setOnClickPendingIntent(R.id.widget_check, completion(context, item))
            }) }
            setTextViewText(R.id.widget_footer, if (items.isEmpty()) "还没有待办 · 轻触添加" else "轻触标题打开 · 圆圈完成 / 撤销")
            setOnClickPendingIntent(R.id.widget_footer, open(context, "add-todo"))
        }
        val today = WidgetData.today(todos)
        return RemoteViews(context.packageName, R.layout.widget_overview).apply {
            setTextViewText(R.id.widget_heading, context.getString(if (provider == ProgressWidget::class.java) R.string.widget_progress else R.string.widget_overview))
            setTextViewText(R.id.widget_counts, "今日 ${today.total} 项\n已完成 ${today.completed} · 剩余 ${today.remaining}")
            setImageViewBitmap(R.id.widget_ring, ring(context, today.progress))
            setContentDescription(R.id.widget_ring, "今日完成 ${(today.progress * 100).toInt()}%")
            setOnClickPendingIntent(R.id.widget_root, open(context, "today"))
        }
    }
    private fun ring(context: Context, progress: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 14f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = context.getColor(R.color.widget_secondary); paint.alpha = 35
        canvas.drawCircle(96f, 96f, 78f, paint)
        paint.color = context.getColor(R.color.widget_accent); paint.alpha = 255
        canvas.drawArc(RectF(18f, 18f, 174f, 174f), -90f, progress * 360f, false, paint)
        paint.style = Paint.Style.FILL; paint.color = context.getColor(R.color.widget_text); paint.textAlign = Paint.Align.CENTER
        paint.textSize = 36f; paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText("${(progress * 100).toInt()}%", 96f, 108f, paint)
        return bitmap
    }
}
open class ZhiWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { update(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { update(context) }
    private fun update(context: Context) {
        val pending = goAsync()
        ZhiWidgets.launch { try { ZhiWidgets.refresh(context) }
            catch (_: Exception) { ZhiWidgets.showError(context, "更新失败，请打开应用重试") }
            finally { pending.finish() } }
    }
}
class OverviewWidget : ZhiWidgetProvider()
class QuickAddWidget : ZhiWidgetProvider()
class TodoListWidget : ZhiWidgetProvider()
class ProgressWidget : ZhiWidgetProvider()
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf("zhinote.widget.COMPLETE", "zhinote.widget.UNDO")) return
        val id = intent.data?.lastPathSegment ?: return
        val pending = goAsync()
        ZhiWidgets.launch {
            try {
                val app = context.applicationContext as ZhiNoteApplication
                if (intent.action == "zhinote.widget.UNDO") app.repository.undoComplete(id) else app.repository.complete(id)
                try { app.reminders.reschedule() } finally { ZhiWidgets.refresh(context) }
            } catch (_: Exception) { ZhiWidgets.showError(context, "操作失败，请打开应用重试") }
            finally { pending.finish() }
        }
    }
}
class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_BOOT_COMPLETED)) return
        val pending = goAsync(); ZhiWidgets.launch { try { ZhiWidgets.refresh(context) } finally { pending.finish() } }
    }
}
