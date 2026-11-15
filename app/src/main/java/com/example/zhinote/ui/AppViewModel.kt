package com.example.zhinote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhinote.ZhiNoteApplication
import com.example.zhinote.data.local.*
import com.example.zhinote.data.repository.UserSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SpaceItem(val id: String, val type: String, val title: String, val content: String,
    val color: Int, val priority: Int, val tags: String, val categoryId: String?, val time: Long,
    val completed: Boolean = false, val favorite: Boolean = false, val summary: String = "") {
    val key get() = "$type:$id"
    val size get() = if (priority == 2 || favorite) 88f else if (type == "NOTE") 72f else 64f
}
fun NoteEntity.item() = SpaceItem(id, "NOTE", title.ifBlank { "未命名笔记" }, content, color, 1, tags, categoryId, createdAt, favorite = isFavorite, summary = summary)
fun TodoEntity.item() = SpaceItem(id, "TODO", title.ifBlank { "未命名待办" }, description, if (priority == 2) 3 else 1, priority, tags, categoryId, dueDate ?: createdAt, isCompleted)

class AppViewModel(val app: ZhiNoteApplication,
    private val persistNote: suspend (NoteEntity) -> Unit = { app.repository.save(it) }) : ViewModel() {
    private val repository = app.repository
    val notes = repository.notes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todos = repository.todos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val layouts = repository.layouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings(floating = false, reducedMotion = true))
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val completionEvents = MutableSharedFlow<SpaceItem>(extraBufferCapacity = 8)
    private val completing = mutableSetOf<String>()
    val selected = MutableStateFlow<SpaceItem?>(null)
    val dissolving = MutableStateFlow<Map<String, SpaceItem>>(emptyMap())
    val editorNote = MutableStateFlow<NoteEntity?>(null)
    val editorTodo = MutableStateFlow<TodoEntity?>(null)
    val saving = MutableStateFlow(false)
    val editorError = MutableStateFlow<String?>(null)
    val editorLoading = MutableStateFlow(false)
    private var editorLoad: Job? = null
    private val saveMutex = Mutex()
    private var pendingSaves = 0
    init { operation { app.reminders.reschedule() } }
    fun operation(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.emit(e.message ?: "操作失败，请重试") }
    } }
    fun edit(item: SpaceItem? = null, type: String = "NOTE") {
        selected.value = null
        editorLoad?.cancel(); editorError.value = null
        if (item == null) {
            editorLoading.value = false
            if (type == "NOTE") { editorTodo.value = null; editorNote.value = NoteEntity() }
            else { editorNote.value = null; editorTodo.value = TodoEntity() }
            return
        }
        editorLoading.value = true
        editorLoad = viewModelScope.launch {
            try {
                if (item.type == "NOTE") {
                    val stored = repository.note(item.id) ?: error("笔记已不存在，请刷新列表")
                    require(stored.deletedAt == null) { "笔记在回收站中，请先恢复" }
                    editorTodo.value = null; editorNote.value = stored
                } else {
                    val stored = repository.todo(item.id) ?: error("待办已不存在，请刷新列表")
                    editorNote.value = null; editorTodo.value = stored
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { messages.emit(e.message ?: "读取失败") }
            finally { editorLoading.value = false }
        }
    }
    fun patchNote(transform: (NoteEntity) -> NoteEntity) { editorNote.value?.let { update(transform(it)) } }
    fun patchTodo(transform: (TodoEntity) -> TodoEntity) { editorTodo.value?.let { update(transform(it)) } }
    fun update(note: NoteEntity) { editorNote.value = note; queueSave { persistNote(note) } }
    fun update(todo: TodoEntity) {
        val old = editorTodo.value
        val value = if (old?.reminderTime != todo.reminderTime) todo.copy(reminderDeliveredAt = null) else todo
        editorTodo.value = value
        queueSave { repository.save(value); app.reminders.schedule(value, settings.value.reminders) }
    }
    private fun queueSave(block: suspend () -> Unit) {
        pendingSaves++; saving.value = true
        viewModelScope.launch {
            try { saveMutex.withLock { block(); editorError.value = null } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { editorError.value = "保存失败：${e.message ?: "请重试"}。输入内容已保留。"; messages.emit(editorError.value!!) }
            finally { pendingSaves--; saving.value = pendingSaves > 0 }
        }
    }
    fun closeEditor() = operation {
        saveMutex.withLock {
            try {
                editorNote.value?.let { persistNote(it) }
                editorTodo.value?.let { repository.save(it); app.reminders.schedule(it, settings.value.reminders) }
                editorError.value = null; editorNote.value = null; editorTodo.value = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { editorError.value = "保存失败：${e.message ?: "请重试"}。输入内容已保留。"; messages.emit(editorError.value!!) }
        }
    }
    fun complete(item: SpaceItem) = operation {
        if (item.type != "TODO" || !completing.add(item.key)) return@operation
        try {
            repository.complete(item.id) ?: return@operation
            app.reminders.cancel(item.id)
            selected.value = null
            if (settings.value.dissolve && !settings.value.reducedMotion) dissolving.value = dissolving.value + (item.key to item)
            completionEvents.emit(item)
            delay(240)
            dissolving.value = dissolving.value - item.key
            app.reminders.reschedule()
        } finally { completing.remove(item.key) }
    }
    fun undo(item: SpaceItem) = operation { repository.undoComplete(item.id); dissolving.value = dissolving.value - item.key; app.reminders.reschedule() }
    fun trash(item: SpaceItem, restore: Boolean = false) = operation {
        repository.trash(item.id, item.type, restore); selected.value = null
        if (item.type == "TODO") app.reminders.reschedule()
    }
    fun purge(item: SpaceItem) = operation { repository.purge(item.id, item.type) }
    fun favorite(item: SpaceItem) = operation { notes.value.find { it.id == item.id }?.let { repository.save(it.copy(isFavorite = !it.isFavorite)) } }
    fun savePosition(item: SpaceItem, x: Float, y: Float) = operation { repository.saveLayout(DropLayoutEntity(item.id, item.type, x, y, item.size)) }
    fun category(name: String, color: Int) = operation { repository.saveCategory(name, color) }
    fun preference(key: String, value: Boolean) = operation { app.settings.boolean(key, value); if (key == "reminders") app.reminders.reschedule() }
}
