package com.example.zhinote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zhinote.ai.AiProfile
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsPage(vm: AiViewModel, onBack: () -> Unit) {
    val configs by vm.configs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf("DeepSeek") }
    var url by remember { mutableStateOf("https://api.deepseek.com/v1") }
    var key by remember { mutableStateOf("") } // Never saved to Bundle / saved state.
    var models by remember { mutableStateOf("deepseek-chat,deepseek-reasoner") }
    var model by remember { mutableStateOf("deepseek-chat") }
    var deleting by remember { mutableStateOf<AiProfile?>(null) }
    fun profile() = AiProfile(editId, name.trim(), url.trim(), key.trim(), models.split(',', '，', '\n').map { it.trim() }.filter { it.isNotBlank() }.distinct(), model.trim())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onBack) { Text("返回设置") }
        Text("AI 接口", style = MaterialTheme.typography.headlineSmall)
        Text("兼容 OpenAI / DeepSeek。内容仅在点击生成时发送到所选服务商。", style = MaterialTheme.typography.bodySmall)
        configs.profiles.forEach { p ->
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("${p.name}${if (configs.selected?.id == p.id) " · 默认" else ""}", style = MaterialTheme.typography.titleMedium)
                    Text(p.baseUrl, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.models.forEach { m -> FilterChip(configs.selected?.id == p.id && p.model == m, { vm.select(p, m) }, { Text(m) }, enabled = !busy) }
                    }
                    FlowRow {
                        TextButton({ editId = p.id; name = p.name; url = p.baseUrl; key = p.apiKey; models = p.models.joinToString(","); model = p.model }, enabled = !busy) { Text("编辑配置") }
                        TextButton({ vm.test(p) }, enabled = !busy) { Text("测试连接") }
                        TextButton({ deleting = p }, enabled = !busy) { Text("删除配置") }
                    }
                }
            }
        }
        FlowRow {
            TextButton({ editId = UUID.randomUUID().toString(); name = "DeepSeek"; url = "https://api.deepseek.com/v1"; key = ""; models = "deepseek-chat,deepseek-reasoner"; model = "deepseek-chat" }) { Text("新增 DeepSeek") }
            TextButton({ editId = UUID.randomUUID().toString(); name = "OpenAI"; url = "https://api.openai.com/v1"; key = ""; models = ""; model = "" }) { Text("新增其他服务") }
        }
        OutlinedTextField(name, { name = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("服务商名称") }, enabled = !busy)
        OutlinedTextField(url, { url = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, enabled = !busy, singleLine = true)
        OutlinedTextField(key, { key = it.take(4096) }, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
        OutlinedTextField(models, { models = it.take(6000) }, Modifier.fillMaxWidth(), label = { Text("模型名称，用逗号分隔") }, enabled = !busy)
        OutlinedTextField(model, { model = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("默认模型（需在模型列表中）") }, enabled = !busy, singleLine = true)
        AiFeedback(vm)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.saveProfile(profile()) }, enabled = !busy) { Text("保存配置") }
            OutlinedButton({ vm.test(profile()) }, enabled = !busy) { Text("测试当前配置") }
            if (busy) TextButton(vm::cancel) { Text("取消请求") }
        }
        Spacer(Modifier.height(24.dp))
    }
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除 ${p.name}？") },
        text = { Text("仅删除这份接口配置。") }, confirmButton = { TextButton({ vm.delete(p); if (editId == p.id) { key = ""; editId = UUID.randomUUID().toString() }; deleting = null }) { Text("删除") } },
        dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
}

@Composable private fun AiFeedback(vm: AiViewModel) {
    val error by vm.error.collectAsStateWithLifecycle(); val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiCreateDialog(vm: AiViewModel, onClose: () -> Unit, onSettings: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle(); val mode by vm.mode.collectAsStateWithLifecycle()
    val draft by vm.preview.collectAsStateWithLifecycle(); val configs by vm.configs.collectAsStateWithLifecycle()
    val close = { if (!busy) onClose() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { if (busy) vm.cancel() else onClose() }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row { Text("AI 快速创建", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall); TextButton(close, enabled = !busy) { Text("返回") } }
                Text(configs.selected?.let { "${it.name} · ${it.model}" } ?: "尚未配置 AI 服务", style = MaterialTheme.typography.bodySmall)
                TextButton({ onClose(); onSettings() }, enabled = !busy) { Text("AI 接口设置") }
                if (draft == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NOTE" to "笔记", "TODO" to "待办", "AUTO" to "自动识别").forEach { (value, label) ->
                            FilterChip(mode == value, { vm.mode.value = value }, { Text(label) }, enabled = !busy)
                        }
                    }
                    OutlinedTextField(input, { vm.input.value = it.take(20000) }, Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        label = { Text("写下想记录或安排的内容") }, minLines = 6, enabled = !busy, shape = RoundedCornerShape(24.dp))
                    AiFeedback(vm)
                    Button(vm::generate, Modifier.fillMaxWidth(), enabled = !busy && input.isNotBlank() && configs.selected != null) { Text("生成预览") }
                } else {
                    val d = draft!!
                    Text("${if (d.type == "NOTE") "笔记" else "待办"}预览 · 确认后保存", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(d.title, { vm.preview.value = vm.preview.value?.copy(title = it.take(500)) }, Modifier.fillMaxWidth(), label = { Text("标题") }, enabled = !busy)
                    OutlinedTextField(d.content, { vm.preview.value = vm.preview.value?.copy(content = it.take(200000)) }, Modifier.fillMaxWidth(), label = { Text("正文 / 描述") }, minLines = 6, enabled = !busy)
                    if (d.type == "NOTE") OutlinedTextField(d.summary, { vm.preview.value = vm.preview.value?.copy(summary = it.take(10000)) }, Modifier.fillMaxWidth(), label = { Text("内容摘要") }, enabled = !busy)
                    OutlinedTextField(d.tags, { vm.preview.value = vm.preview.value?.copy(tags = it.take(2000)) }, Modifier.fillMaxWidth(), label = { Text("标签") }, enabled = !busy)
                    if (d.type == "TODO") {
                        FlowRow { listOf("低", "普通", "高").forEachIndexed { i, text -> FilterChip(d.priority == i, { vm.preview.value = vm.preview.value?.copy(priority = i) }, { Text(text) }, enabled = !busy) } }
                        DateTimeField("截止时间", d.dueDate) { vm.preview.value = vm.preview.value?.copy(dueDate = it) }
                        DateTimeField("提醒时间", d.reminderTime) { vm.preview.value = vm.preview.value?.copy(reminderTime = it) }
                    }
                    AiFeedback(vm)
                    Button(vm::confirm, Modifier.fillMaxWidth(), enabled = !busy) { Text("确认并保存") }
                    OutlinedButton({ vm.preview.value = null }, enabled = !busy) { Text("返回修改输入") }
                }
                if (busy) TextButton(vm::cancel) { Text("取消请求") }
                Text("生成内容请核对日期与事实。未确认的预览不会写入笔记或待办。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
