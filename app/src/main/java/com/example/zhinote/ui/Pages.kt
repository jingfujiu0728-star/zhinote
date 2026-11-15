package com.example.zhinote.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zhinote.data.local.*
import com.example.zhinote.data.repository.UserSettings
import com.example.zhinote.drop.drawing.DropSpace
import java.time.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesPage(notes: List<NoteEntity>, categories: List<CategoryEntity>, layouts: List<DropLayoutEntity>, settings: UserSettings,
    selected: SpaceItem?, onSelect: (SpaceItem?) -> Unit, onPosition: (SpaceItem, Float, Float) -> Unit) {
    var drops by rememberSaveable { mutableStateOf(false) }; var favorites by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }; var tag by rememberSaveable { mutableStateOf("") }
    var createdSort by rememberSaveable { mutableStateOf(false) }; var filters by rememberSaveable { mutableStateOf(false) }
    val items = notes.filter { (!favorites || it.isFavorite) && (category == null || it.categoryId == category) && it.tags.contains(tag, true) }
        .sortedByDescending { if (createdSort) it.createdAt else it.updatedAt }.map { it.item() }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            FilterChip(favorites, { favorites = !favorites }, { Text("收藏") }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null, Modifier.size(16.dp)) })
            FilterChip(filters, { filters = !filters }, { Text("筛选") })
            FilterChip(drops, { drops = !drops }, { Text(if (drops) "水滴" else "列表") }, leadingIcon = { Icon(if (drops) Icons.Outlined.WaterDrop else Icons.Outlined.ViewList, null, Modifier.size(16.dp)) })
        }
        if (filters) Column(Modifier.padding(horizontal = 20.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                CategorySelector(categories, category, { category = it })
                FilterChip(createdSort, { createdSort = !createdSort }, { Text(if (createdSort) "按创建时间" else "按更新时间") })
            }
            OutlinedTextField(tag, { tag = it }, Modifier.fillMaxWidth(), label = { Text("筛选标签") }, singleLine = true, shape = RoundedCornerShape(18.dp))
        }
        if (drops && items.isNotEmpty()) {
            DropSpace(items.take(if (settings.performance) 20 else settings.maxDrops), layouts, settings, selected, emptyMap(), onSelect, onPosition, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
            Spacer(Modifier.height(90.dp))
        } else ItemList(items, { onSelect(it) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodosPage(todos: List<TodoEntity>, categories: List<CategoryEntity>, layouts: List<DropLayoutEntity>, settings: UserSettings,
    selected: SpaceItem?, dissolving: Map<String, SpaceItem>, onSelect: (SpaceItem?) -> Unit,
    onPosition: (SpaceItem, Float, Float) -> Unit, onComplete: (SpaceItem) -> Unit, onUndo: (SpaceItem) -> Unit) {
    var section by rememberSaveable { mutableStateOf("全部") }; var drops by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }; var category by rememberSaveable { mutableStateOf<String?>(null) }
    var tag by rememberSaveable { mutableStateOf("") }
    val now = System.currentTimeMillis(); val today = LocalDate.now()
    val items = todos.filter { item ->
        (category == null || item.categoryId == category) && item.tags.contains(tag, true) && when (section) {
            "今日" -> !item.isCompleted && (item.dueDate ?: item.createdAt).localDate() == today
            "即将到期" -> !item.isCompleted && item.dueDate != null && item.dueDate >= now && item.dueDate <= now + 7 * 86400000L
            "逾期" -> !item.isCompleted && item.dueDate != null && item.dueDate < now
            "高优先级" -> !item.isCompleted && item.priority == 2
            "已完成" -> item.isCompleted
            else -> !item.isCompleted
        }
    }.sortedWith(compareBy<TodoEntity> { it.dueDate ?: Long.MAX_VALUE }.thenByDescending { it.priority }).map { it.item() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "今日", "即将到期", "逾期", "高优先级", "已完成").forEach { name -> FilterChip(section == name, { section = name }, { Text(name) }) }
        }
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filters, { filters = !filters }, { Text("分类与标签") })
            FilterChip(drops, { drops = !drops }, { Text(if (drops) "水滴视图" else "列表视图") })
        }
        if (filters) Column(Modifier.padding(horizontal = 20.dp)) {
            CategorySelector(categories, category, { category = it })
            OutlinedTextField(tag, { tag = it }, Modifier.fillMaxWidth(), label = { Text("筛选标签") }, singleLine = true, shape = RoundedCornerShape(18.dp))
        }
        if (drops && items.isNotEmpty() && section != "已完成") {
            val visible = (items.filterNot { it.completed } + dissolving.values).distinctBy { it.key }
            DropSpace(visible.take(if (settings.performance) 20 else settings.maxDrops), layouts, settings, selected, dissolving, onSelect, onPosition,
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), onQuickComplete = onComplete)
            Spacer(Modifier.height(90.dp))
        } else ItemList(items, { onSelect(it) }, onComplete = onComplete, onUndo = onUndo)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(settings: UserSettings, vm: AppViewModel, notificationGranted: Boolean, exactGranted: Boolean,
    onPermission: () -> Unit, onExact: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onTrash: () -> Unit,
    categories: List<CategoryEntity>, onCategory: () -> Unit, onAiSettings: () -> Unit, onWidgets: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsHeading("色彩与光")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (value, title) -> FilterChip(settings.theme == value, { vm.operation { vm.app.settings.theme(value) } }, { Text(title) }) } }
        SettingsHeading("水滴的呼吸")
        SettingSwitch("自由漂浮", settings.floating) { vm.preference("floating", it) }
        SettingSwitch("碰撞与避让", settings.collisions) { vm.preference("collisions", it) }
        SettingSwitch("点击扩散", settings.ripple) { vm.preference("ripple", it) }
        SettingSwitch("完成消散", settings.dissolve) { vm.preference("dissolve", it) }
        SettingSwitch("拖动水滴", settings.dragging) { vm.preference("dragging", it) }
        SettingSwitch("减少动态效果", settings.reducedMotion) { vm.preference("reduced", it) }
        SettingSwitch("低性能模式", settings.performance, "最多 20 滴，降低更新频率与粒子数量") { vm.preference("performance", it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("轻柔", "自然", "活泼").forEachIndexed { i, title -> FilterChip(settings.intensity == i, { vm.operation { vm.app.settings.intensity(i) } }, { Text(title) }) } }
        Text("空间显示数量", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(20, 40, 60).forEach { count -> FilterChip(settings.maxDrops == count, { vm.operation { vm.app.settings.maxDrops(count) } }, { Text("$count 滴") }) } }
        Text("水滴较多时可降低显示数量，列表始终保留全部内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsHeading("提醒")
        SettingSwitch("待办提醒", settings.reminders) { vm.preference("reminders", it) }
        Text(if (notificationGranted) "系统通知权限已开启" else "通知权限未开启，提醒暂时无法显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!notificationGranted) OutlinedButton(onPermission) { Text("开启通知权限") }
        Text(if (exactGranted) "精确时间提醒可用" else "当前使用可延迟提醒，系统可能推迟通知。可按需授权精确闹钟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!exactGranted) OutlinedButton(onExact) { Text("配置精确时间提醒") }
        Text("若手机限制后台运行，可在系统应用设置中允许后台活动。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsHeading("分类")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { categories.forEach { AssistChip({}, { Text(it.name) }) } }
        OutlinedButton(onCategory) { Icon(Icons.Outlined.Add, null); Text("新建分类") }
        SettingsHeading("保存与找回")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onExport) { Text("导出备份") }; OutlinedButton(onImport) { Text("导入恢复") }; OutlinedButton(onTrash) { Text("回收站") } }
        SettingsHeading("AI")
        OutlinedButton(onAiSettings) { Icon(Icons.Outlined.AutoAwesome, null); Text("AI 接口配置") }
        SettingsHeading("桌面")
        OutlinedButton(onWidgets) { Icon(Icons.Outlined.Widgets, null); Text("添加桌面小部件") }
        SettingsHeading("ZhiNote 2.0.0")
        Text("水滴空间 · 把灵感与日常，轻轻安放。\n数据保存在这台设备上，备份由你选择保存位置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SettingsHeading(title: String) { Text(title, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
@Composable private fun SettingSwitch(title: String, value: Boolean, subtitle: String? = null, onValue: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Switch(value, onValue)
    }
}
