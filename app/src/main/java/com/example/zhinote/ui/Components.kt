package com.example.zhinote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zhinote.data.local.*
import java.time.*

@Composable
fun EmptyState(title: String = "还没有水滴", subtitle: String = "给今天留下一点新的记录。", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.WaterDrop, null, Modifier.size(70.dp).padding(12.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .5f))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ItemList(items: List<SpaceItem>, onSelect: (SpaceItem) -> Unit, modifier: Modifier = Modifier,
    emptyText: String = "还没有水滴", onComplete: ((SpaceItem) -> Unit)? = null, onUndo: ((SpaceItem) -> Unit)? = null) {
    if (items.isEmpty()) EmptyState(emptyText, modifier = modifier.fillMaxSize())
    else LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.key }) { item ->
            Surface(onClick = { onSelect(item) }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(DropColors[item.color], CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (item.type == "NOTE") Icons.Outlined.EditNote else if (item.completed) Icons.Outlined.Check else Icons.Outlined.Checklist,
                            if (item.type == "NOTE") "笔记" else if (item.completed) "已完成待办" else "待办", tint = androidx.compose.ui.graphics.Color(0xFF304B4D), modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.content.isNotBlank()) Text(item.content, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${item.time.readableTime()}${if (item.priority == 2) " · 高优先级" else ""}${if (item.favorite) " · 收藏" else ""}${if (item.tags.isNotBlank()) " · ${item.tags}" else ""}",
                            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.type == "TODO" && !item.completed && onComplete != null) IconButton({ onComplete(item) }) { Icon(Icons.Outlined.RadioButtonUnchecked, "完成：${item.title}") }
                    if (item.completed && onUndo != null) IconButton({ onUndo(item) }) { Icon(Icons.Outlined.Undo, "撤销完成：${item.title}") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailPanel(item: SpaceItem, categories: List<CategoryEntity>, onEdit: () -> Unit, onTrash: () -> Unit,
    onComplete: () -> Unit, onUndo: () -> Unit, onFavorite: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (item.type == "NOTE") "一滴灵感" else if (item.completed) "完成的足迹" else "一件小事", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            IconButton(onClose) { Icon(Icons.Outlined.Close, "收起详情") }
        }
        Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Text(item.time.readableTime(), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.find { it.id == item.categoryId }?.let { AssistChip({}, { Text(it.name) }) }
            item.tags.split(',', '，').filter { it.isNotBlank() }.forEach { AssistChip({}, { Text(it.trim()) }) }
            if (item.type == "TODO") AssistChip({}, { Text(listOf("低优先级", "普通优先级", "高优先级")[item.priority]) })
        }
        if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        SelectionContainerCompat(item.content.ifBlank { "还没有写下正文。" })
        FlowRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.type == "TODO") Button(if (item.completed) onUndo else onComplete) { Text(if (item.completed) "撤销完成" else "完成这件事") }
            OutlinedButton(onEdit) { Text("编辑") }
            if (item.type == "NOTE") OutlinedButton(onFavorite) { Text(if (item.favorite) "取消收藏" else "收藏") }
            TextButton(onTrash) { Text("移入回收站") }
        }
    }
}
@Composable private fun SelectionContainerCompat(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge) } }

@Composable
fun CategorySelector(categories: List<CategoryEntity>, selected: String?, onSelect: (String?) -> Unit, allLabel: String = "全部分类") {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }) { Text(categories.find { it.id == selected }?.name ?: allLabel); Icon(Icons.Outlined.ExpandMore, null) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(allLabel) }, { onSelect(null); expanded = false })
            categories.forEach { category -> DropdownMenuItem({ Text(category.name) }, { onSelect(category.id); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorDialog(note: NoteEntity?, todo: TodoEntity?, categories: List<CategoryEntity>, saving: Boolean,
    onNote: ((NoteEntity) -> NoteEntity) -> Unit, onTodo: ((TodoEntity) -> TodoEntity) -> Unit, onClose: () -> Unit, error: String? = null) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val saveAndClose = { keyboard?.hide(); focus.clearFocus(); onClose() }
    Dialog(onDismissRequest = saveAndClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = saveAndClose)
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(saveAndClose) { Icon(Icons.Outlined.ArrowBack, "保存并返回") }
                    Text(if (note != null) "写下一滴灵感" else "添一件小待办", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Text(if (saving) "保存中…" else "自动保存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(note?.title ?: todo!!.title, { value -> if (note != null) onNote { it.copy(title = value.take(500)) } else onTodo { it.copy(title = value.take(500)) } },
                    Modifier.fillMaxWidth(), label = { Text("标题") }, shape = RoundedCornerShape(20.dp), maxLines = 4)
                OutlinedTextField(note?.content ?: todo!!.description, { value -> if (note != null) onNote { it.copy(content = value.take(200000)) } else onTodo { it.copy(description = value.take(200000)) } },
                    Modifier.fillMaxWidth().heightIn(min = 220.dp), label = { Text(if (note != null) "正文 · 灵感从这里开始" else "描述") }, minLines = 8, shape = RoundedCornerShape(20.dp))
                CategorySelector(categories, note?.categoryId ?: todo?.categoryId, { id -> if (note != null) onNote { it.copy(categoryId = id) } else onTodo { it.copy(categoryId = id) } }, "未分类")
                OutlinedTextField(note?.tags ?: todo!!.tags, { value -> if (note != null) onNote { it.copy(tags = value.take(2000)) } else onTodo { it.copy(tags = value.take(2000)) } },
                    Modifier.fillMaxWidth(), label = { Text("标签，用逗号分隔") }, shape = RoundedCornerShape(20.dp))
                if (note != null) {
                    if (note.summary.isNotBlank()) OutlinedTextField(note.summary, { value -> onNote { it.copy(summary = value.take(10000)) } }, Modifier.fillMaxWidth(), label = { Text("内容摘要") }, shape = RoundedCornerShape(20.dp))
                    Text("给灵感一种颜色", style = MaterialTheme.typography.titleSmall)
                    ColorSelector(note.color) { color -> onNote { it.copy(color = color) } }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("收藏这滴灵感", Modifier.weight(1f)); Switch(note.isFavorite, { favorite -> onNote { it.copy(isFavorite = favorite) } }) }
                }
                if (todo != null) {
                    Text("优先级", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("低", "普通", "高").forEachIndexed { i, title -> FilterChip(todo.priority == i, { onTodo { it.copy(priority = i) } }, { Text(title) }) } }
                    DateTimeField("截止时间", todo.dueDate) { time -> onTodo { it.copy(dueDate = time) } }
                    DateTimeField("提醒时间", todo.reminderTime) { time -> onTodo { it.copy(reminderTime = time) } }
                    Text("重复", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NONE" to "不重复", "DAILY" to "每天", "WEEKLY" to "每周", "MONTHLY" to "每月").forEach { (rule, title) -> FilterChip(todo.repeatRule == rule, { onTodo { it.copy(repeatRule = rule) } }, { Text(title) }) }
                    }
                }
                Button(saveAndClose, Modifier.fillMaxWidth()) { Text("保存并返回") }
            }
        }
    }
}

@Composable
fun DateTimeField(label: String, value: Long?, onValue: (Long?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton({
            val initial = Instant.ofEpochMilli(value ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault())
            android.app.DatePickerDialog(context, { _, year, month, day ->
                android.app.TimePickerDialog(context, { _, hour, minute ->
                    onValue(LocalDateTime.of(year, month + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                }, initial.hour, initial.minute, true).show()
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }, Modifier.weight(1f)) { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("$label · ${value?.readableTime() ?: "未设置"}") }
        if (value != null) IconButton({ onValue(null) }) { Icon(Icons.Outlined.Close, "清除$label") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSelector(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DropColors.forEachIndexed { i, color -> Box(Modifier.size(48.dp).clickable { onSelect(i) }.semantics { contentDescription = "颜色 ${i + 1}${if (i == selected) "，已选中" else ""}" }.padding(6.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            if (i == selected) Icon(Icons.Outlined.Check, null, tint = androidx.compose.ui.graphics.Color(0xFF304B4D), modifier = Modifier.size(20.dp))
        } }
    }
}

@Composable
fun CategoryDialog(onClose: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var color by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onClose, title = { Text("新建分类") }, text = {
        Column { OutlinedTextField(name, { name = it.take(100) }, label = { Text("分类名称") }); Spacer(Modifier.height(12.dp)); ColorSelector(color) { color = it } }
    }, confirmButton = { TextButton({ onCreate(name, color); onClose() }, enabled = name.isNotBlank()) { Text("创建") } }, dismissButton = { TextButton(onClose) { Text("取消") } })
}

@Composable
fun TrashDialog(items: List<SpaceItem>, onClose: () -> Unit, onRestore: (SpaceItem) -> Unit, onPurge: (SpaceItem) -> Unit) {
    var pending by remember { mutableStateOf<SpaceItem?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("回收站", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall); IconButton(onClose) { Icon(Icons.Outlined.Close, "关闭回收站") } }
                if (items.isEmpty()) EmptyState("回收站是空的", modifier = Modifier.weight(1f))
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.key }) { item -> Card {
                        Column(Modifier.padding(16.dp)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Row {
                            TextButton({ onRestore(item) }) { Text("恢复") }; TextButton({ pending = item }) { Text("永久删除") }
                        } }
                    } }
                }
            }
        }
        pending?.let { item -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("永久删除？") }, text = { Text("「${item.title}」将无法恢复。") },
            confirmButton = { TextButton({ onPurge(item); pending = null }) { Text("永久删除") } }, dismissButton = { TextButton({ pending = null }) { Text("取消") } }) }
    }
}
