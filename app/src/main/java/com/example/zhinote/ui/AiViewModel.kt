package com.example.zhinote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhinote.ZhiNoteApplication
import com.example.zhinote.ai.*
import com.example.zhinote.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

class AiViewModel(private val app: ZhiNoteApplication) : ViewModel() {
    val configs = MutableStateFlow(AiConfigs())
    val error = MutableStateFlow<String?>(null)
    val status = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val input = MutableStateFlow("")
    val mode = MutableStateFlow("AUTO")
    val preview = MutableStateFlow<AiDraft?>(null)
    val saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var request: Job? = null
    private val client = AiClient()
    init { viewModelScope.launch { try { configs.value = app.aiConfigs.read() }
        catch (e: Exception) { error.value = e.message } } }
    private fun work(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null; status.value = null
        request = viewModelScope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) { error.value = "请求超时，请重试。输入内容已保留。" }
            catch (e: CancellationException) { throw e }
            catch (e: AiException) { error.value = e.message }
            catch (e: IllegalArgumentException) { error.value = e.message ?: "配置或内容无效" }
            catch (_: Exception) { error.value = "操作失败，请检查配置或存储后重试。输入内容已保留。" }
            finally { busy.value = false }
        }
    }
    fun saveProfile(profile: AiProfile) = work {
        profile.validate()
        val old = configs.value
        val updated = old.copy(profiles = old.profiles.filterNot { it.id == profile.id } + profile,
            defaultId = old.defaultId ?: profile.id)
        app.aiConfigs.write(updated); configs.value = updated; status.value = "配置已加密保存"
    }
    fun select(profile: AiProfile, model: String = profile.model) = work {
        val replacement = AiProfile(profile.id, profile.name, profile.baseUrl, profile.apiKey, profile.models, model)
        val updated = configs.value.copy(profiles = configs.value.profiles.map { if (it.id == profile.id) replacement else it }, defaultId = profile.id)
        app.aiConfigs.write(updated); configs.value = updated
    }
    fun delete(profile: AiProfile) = work {
        val remaining = configs.value.profiles.filterNot { it.id == profile.id }
        val updated = AiConfigs(remaining, configs.value.defaultId.takeIf { it != profile.id } ?: remaining.firstOrNull()?.id)
        app.aiConfigs.write(updated); configs.value = updated; status.value = "配置已删除"
    }
    fun test(profile: AiProfile) = work { status.value = client.test(profile) }
    fun generate() = work {
        val profile = configs.value.selected ?: throw AiException("请先在 AI 设置中配置接口。手动创建始终可用。")
        preview.value = client.create(profile, input.value, mode.value)
    }
    fun cancel() { request?.cancel(); status.value = "请求已取消，输入内容已保留" }
    fun confirm() = work {
        val draft = preview.value ?: return@work
        draft.validate()
        if (draft.type == "NOTE") app.repository.save(NoteEntity(id = draft.id, title = draft.title,
            content = draft.content, summary = draft.summary, tags = draft.tags))
        else {
            val todo = TodoEntity(id = draft.id, title = draft.title, description = draft.content,
                priority = draft.priority, tags = draft.tags, dueDate = draft.dueDate, reminderTime = draft.reminderTime)
            app.repository.save(todo)
            try { app.reminders.schedule(todo, app.settings.settings.first().reminders) }
            catch (_: Exception) { status.value = "内容已保存，提醒调度失败，请检查系统提醒权限。" }
        }
        preview.value = null; input.value = ""; saved.emit(Unit)
    }
}
