package com.example.zhinote.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.zhinote.ZhiNoteApplication
import com.example.zhinote.data.backup.*
import com.example.zhinote.drop.drawing.HoneycombSpace
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val tabs = listOf(Tab("home", "空间", Icons.Outlined.WaterDrop), Tab("notes", "笔记", Icons.Outlined.EditNote),
    Tab("todos", "待办", Icons.Outlined.Checklist), Tab("calendar", "日历", Icons.Outlined.CalendarMonth), Tab("settings", "我的", Icons.Outlined.PersonOutline))
fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun Long.readableTime(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhiApp(app: ZhiNoteApplication, launchRequest: Uri? = null, consumeRequest: () -> Unit = {}) {
    val factory = remember { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
    } }
    val vm: AppViewModel = viewModel(factory = factory)
    val aiFactory = remember { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AiViewModel(app) as T
    } }
    val ai: AiViewModel = viewModel(factory = aiFactory)
    var aiOpen by rememberSaveable { mutableStateOf(false) }
    val notes by vm.notes.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val layouts by vm.layouts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val thermalThrottle = rememberThermalThrottle(app)
    val motionSettings = if (thermalThrottle) settings.copy(performance = true, intensity = 0) else settings
    val selected by vm.selected.collectAsStateWithLifecycle()
    val dissolving by vm.dissolving.collectAsStateWithLifecycle()
    val editorNote by vm.editorNote.collectAsStateWithLifecycle()
    val editorTodo by vm.editorTodo.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val editorError by vm.editorError.collectAsStateWithLifecycle()
    val editorLoading by vm.editorLoading.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    LaunchedEffect(launchRequest) {
        if (launchRequest?.scheme == "zhinote" && launchRequest.host == "widget") {
            val parts = launchRequest.pathSegments
            when (parts.firstOrNull()) {
                "add-note" -> vm.edit(type = "NOTE")
                "add-todo" -> vm.edit(type = "TODO")
                "ai" -> aiOpen = true
                "widgets" -> nav.navigate("widgets")
                "ai-settings" -> nav.navigate("ai-settings")
                "today" -> nav.navigate("todos")
                "open-todo" -> parts.getOrNull(1)?.let { id -> vm.operation {
                    app.repository.todo(id)?.takeIf { it.deletedAt == null }?.let { vm.selected.value = it.item() }
                        ?: vm.messages.emit("待办已不存在或已移入回收站")
                } }
            }
        }
        consumeRequest()
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var listHome by rememberSaveable { mutableStateOf(false) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }
    var categoryOpen by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val backup = remember { BackupManager(app, app.database) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.operation { backup.export(it); vm.messages.emit("备份已导出") } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.operation { pendingImport = backup.prepareImport(it) } }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.operation { if (granted) { app.reminders.reschedule(); vm.messages.emit("通知权限已开启") } else vm.messages.emit("通知权限未开启，可以在系统设置中授权") }
    }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(ai) { ai.saved.collect { aiOpen = false; snack.showSnackbar(ai.status.value ?: "AI 内容已保存") } }
    LaunchedEffect(vm, snack) { vm.completionEvents.collect { item ->
        val result = snack.showSnackbar("已完成：${item.title}", actionLabel = "撤销", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undo(item)
    } }
    val activeNotes = notes.filter { it.deletedAt == null }
    val activeTodos = todos.filter { it.deletedAt == null }
    val allItems = activeNotes.map { it.item() } + activeTodos.map { it.item() }
    val spaceItems = (activeNotes.map { it.item() } + activeTodos.filterNot { it.isCompleted }.map { it.item() } + dissolving.values)
        .distinctBy { it.key }.sortedByDescending { it.priority == 2 || it.favorite }
    val searchResults = allItems.filter { item ->
        val category = categories.find { it.id == item.categoryId }?.name.orEmpty()
        listOf(item.title, item.content, item.summary, item.tags, category).any { it.contains(query.trim(), ignoreCase = true) }
    }
    ZhiTheme(settings) {
        val colors = MaterialTheme.colorScheme
        Scaffold(containerColor = colors.background, snackbarHost = { SnackbarHost(snack) },
            topBar = {
                Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (route == "home") LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINESE)) else "ZhiNote · 水滴空间",
                                style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            Text(when (route) { "home" -> "让灵感，自由漂浮"; "notes" -> "留住每一滴灵感"; "todos" -> "轻轻完成，慢慢向前"; "calendar" -> "日子里的点点滴滴"; else -> "属于你的水滴空间" },
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                        }
                        IconButton({ searchOpen = !searchOpen; if (!searchOpen) query = "" }) { Icon(Icons.Outlined.Search, "全局搜索") }
                        if (route == "home") IconButton({ nav.navigate("settings") }) { Icon(Icons.Outlined.Tune, "设置") }
                    }
                    if (searchOpen) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("搜索正文、标签或分类") }, shape = RoundedCornerShape(20.dp),
                        trailingIcon = { IconButton({ query = ""; searchOpen = false }) { Icon(Icons.Outlined.Close, "关闭搜索") } })
                    if (thermalThrottle) Text("设备温度偏高，暂时减少水滴与动效", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }, bottomBar = {
                Surface(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(32.dp),
                    color = colors.surface.copy(alpha = .9f), tonalElevation = 2.dp, shadowElevation = 6.dp) {
                    NavigationBar(containerColor = Color.Transparent, modifier = Modifier.height(76.dp)) {
                        tabs.forEach { tab -> NavigationBarItem(selected = route == tab.route, onClick = {
                            vm.selected.value = null; searchOpen = false; query = ""
                            nav.navigate(tab.route) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
                        }, icon = { Icon(tab.icon, tab.label) }, label = { Text(tab.label) }) }
                    }
                }
            }, floatingActionButton = {
                if (route in listOf("home", "notes", "todos", "calendar") && !searchOpen) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallFloatingActionButton({ aiOpen = true }, containerColor = colors.secondaryContainer) { Icon(Icons.Outlined.AutoAwesome, "AI 创建") }
                    SmallFloatingActionButton({ vm.edit(type = "NOTE") }, containerColor = colors.surfaceVariant) { Icon(Icons.Outlined.EditNote, "新增笔记") }
                    ExtendedFloatingActionButton(onClick = { vm.edit(type = "TODO") }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("新待办") }, containerColor = colors.primaryContainer)
                }
            }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(colors.background, colors.surfaceVariant.copy(alpha = .35f), colors.background)))) {
                if (searchOpen && query.isNotBlank()) ItemList(searchResults, { vm.selected.value = it }, emptyText = "没有找到这滴灵感")
                else NavHost(nav, startDestination = "home") {
                    composable("home") {
                        Column(Modifier.fillMaxSize()) {
                            val todayTodos = activeTodos.filter { (it.dueDate ?: it.createdAt).localDate() == LocalDate.now() }
                            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("今日 ${todayTodos.count { it.isCompleted }} / ${todayTodos.size} 已完成", style = MaterialTheme.typography.titleSmall)
                                    Text("${activeNotes.size} 滴灵感 · ${activeTodos.count { !it.isCompleted }} 件待办", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                IconButton({ listHome = !listHome }) { Icon(if (listHome) Icons.Outlined.WaterDrop else Icons.Outlined.ViewList, if (listHome) "切换水滴视图" else "切换列表视图") }
                            }
                            if (listHome) ItemList(spaceItems.filterNot { dissolving.containsKey(it.key) }, { vm.selected.value = it })
                            else {
                                if (spaceItems.isEmpty()) EmptyState("水面很安静", "写下第一滴灵感，或添一件小待办。", Modifier.weight(1f))
                                else HoneycombSpace(spaceItems, motionSettings, selected, dissolving, { vm.selected.value = it }, vm::complete,
                                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
                                Text("轻触展开 · 双击完成待办 · 拖动浏览 · 双指缩放",
                                    Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 88.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    composable("notes") { NotesPage(activeNotes, categories, layouts, motionSettings, selected, { vm.selected.value = it }, vm::savePosition) }
                    composable("todos") { TodosPage(activeTodos, categories, layouts, motionSettings, selected, dissolving, { vm.selected.value = it }, vm::savePosition, vm::complete, vm::undo) }
                    composable("calendar") { CalendarPage(activeNotes, activeTodos) { vm.selected.value = it } }
                    composable("settings") { SettingsPage(settings, vm, app.reminders.permissionGranted(), app.reminders.exactAvailable(),
                        { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        { if (Build.VERSION.SDK_INT >= 31) {
                            try { app.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            catch (_: Exception) { vm.messages.tryEmit("此系统不提供精确闹钟授权入口，将使用可延迟提醒") }
                        } },
                        { exportLauncher.launch("ZhiNote-${LocalDate.now()}.json") }, { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        { trashOpen = true }, categories, { categoryOpen = true }, { nav.navigate("ai-settings") }, { nav.navigate("widgets") }) }
                    composable("ai-settings") { AiSettingsPage(ai) { nav.popBackStack() } }
                    composable("widgets") { WidgetSettingsPage { nav.popBackStack() } }
                }
            }
        }
        selected?.let { item ->
            BackHandler { vm.selected.value = null }
            ModalBottomSheet(onDismissRequest = { vm.selected.value = null }, containerColor = colors.surface) {
                DetailPanel(item, categories, { vm.edit(item) }, { vm.trash(item) }, { vm.complete(item) }, { vm.undo(item) }, { vm.favorite(item) }, { vm.selected.value = null })
            }
        }
        if (editorLoading) AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row { CircularProgressIndicator(); Text("正在读取内容…", Modifier.padding(12.dp)) } })
        if (editorNote != null || editorTodo != null) EditorDialog(editorNote, editorTodo, categories, saving, vm::patchNote, vm::patchTodo, vm::closeEditor, editorError)
        if (aiOpen) AiCreateDialog(ai, { aiOpen = false }, { nav.navigate("ai-settings") })
        if (trashOpen) TrashDialog(notes.filter { it.deletedAt != null }.map { it.item() } + todos.filter { it.deletedAt != null }.map { it.item() },
            { trashOpen = false }, { vm.trash(it, restore = true) }, vm::purge)
        if (categoryOpen) CategoryDialog({ categoryOpen = false }, vm::category)
        pendingImport?.let { data -> AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("恢复这份备份？") },
            text = { Text("已校验 ${data.notes.size} 条笔记和 ${data.todos.size} 条待办。恢复会替换当前内容与布局，请先导出当前数据。主题设置会保留。") },
            confirmButton = { TextButton({ vm.operation {
                todos.forEach { app.reminders.cancel(it.id) }
                try { backup.restore(data) } finally { app.reminders.reschedule() }
                pendingImport = null; vm.selected.value = null; vm.messages.emit("备份恢复完成")
            } }) { Text("替换并恢复") } }, dismissButton = { TextButton({ pendingImport = null }) { Text("取消") } }) }
    }
}
