package com.example.zhinote.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.zhinote.MainActivity
import com.example.zhinote.R
import com.example.zhinote.ZhiNoteApplication
import com.example.zhinote.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context, private val dao: ZhiDao) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("todos", "待办提醒", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun exactAvailable() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun permissionGranted() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun pending(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("zhinote://reminder/$id")).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(id: String) {
        alarms.cancel(pending(id))
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$id")
    }
    fun schedule(todo: TodoEntity, enabled: Boolean = true) {
        cancel(todo.id)
        val time = todo.reminderTime ?: return
        if (!enabled || todo.isCompleted || todo.deletedAt != null || todo.reminderDeliveredAt != null) return
        if (exactAvailable() && time > System.currentTimeMillis()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(todo.id)); return }
            catch (_: SecurityException) { /* permission may change between checking and setting */ }
        }
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf("id" to todo.id))
            .setInitialDelay((time - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-${todo.id}", ExistingWorkPolicy.REPLACE, work)
    }
    suspend fun reschedule() {
        val enabled = (context.applicationContext as ZhiNoteApplication).settings.settings.first().reminders
        dao.allTodos().forEach { schedule(it, enabled) }
    }
    suspend fun deliver(id: String) {
        val app = context.applicationContext as ZhiNoteApplication
        if (!app.settings.settings.first().reminders || !permissionGranted() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val item = dao.todo(id) ?: return
        val time = item.reminderTime ?: return
        if (item.isCompleted || item.deletedAt != null || time > System.currentTimeMillis()) return
        if (dao.claimReminder(id, time, System.currentTimeMillis()) != 1) return
        val launch = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "todos").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title.ifBlank { "待办提醒" }).setContentText(item.description.ifBlank { "水滴空间里，有一件事等你完成。" })
            .setContentIntent(launch).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
        catch (_: SecurityException) { dao.releaseReminder(id, time) }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ZhiNoteApplication
        return try { app.reminders.deliver(inputData.getString("id") ?: return Result.failure()); Result.success() }
        catch (_: Exception) { Result.retry() }
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as ZhiNoteApplication).reminders.deliver(intent.getStringExtra("id") ?: return@launch) }
            finally { pending.finish() }
        }
    }
}
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as ZhiNoteApplication).reminders.reschedule() }
            finally { pending.finish() }
        }
    }
}
