package com.example.zhinote

import android.app.Application
import com.example.zhinote.data.local.ZhiDatabase
import com.example.zhinote.data.repository.*
import com.example.zhinote.reminders.ReminderScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import com.example.zhinote.widgets.ZhiWidgets

class ZhiNoteApplication : Application() {
    val database by lazy { ZhiDatabase.open(this) }
    val repository by lazy { ZhiRepository(database) }
    val settings by lazy { SettingsStore(this) }
    val aiConfigs by lazy { com.example.zhinote.ai.AiConfigStore(this) }
    val reminders by lazy { ReminderScheduler(this, database.dao()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate(); reminders.createChannel()
        scope.launch { database.dao().todos().debounce(700).collect {
            try { ZhiWidgets.refresh(this@ZhiNoteApplication) } catch (_: Exception) { ZhiWidgets.showError(this@ZhiNoteApplication, "更新失败，请打开应用重试") }
        } }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        scope.launch { try { ZhiWidgets.refresh(this@ZhiNoteApplication) } catch (_: Exception) { } }
    }
}
