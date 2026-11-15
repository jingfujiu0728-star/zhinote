package com.example.zhinote.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zhinote.widgets.*

@Composable fun WidgetSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onBack) { Text("返回设置") }
        Text("桌面上的水滴", style = MaterialTheme.typography.headlineSmall)
        Text("使用系统标准小部件。在 HyperOS 桌面长按空白处 → 添加小部件 → Android 小部件中也可以找到 ZhiNote。")
        listOf(OverviewWidget::class.java to "今日概览", QuickAddWidget::class.java to "快捷添加",
            TodoListWidget::class.java to "待办清单", ProgressWidget::class.java to "今日进度").forEach { (provider, title) ->
            OutlinedButton({
                status = try {
                    if (manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, provider), null, null)) "已向桌面发送「$title」添加请求。若未出现弹窗，请从桌面的安卓小部件列表添加。"
                    else "当前桌面不支持应用内固定，请从桌面的小部件列表添加。"
                } catch (_: Exception) { "桌面未接受添加请求，请从桌面的小部件列表添加。" }
            }, Modifier.fillMaxWidth()) { Text("添加$title") }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text("数据变化后自动更新；系统定期更新间隔为 30 分钟。桌面省电限制可能延迟更新，点击可打开应用。", style = MaterialTheme.typography.bodySmall)
    }
}
