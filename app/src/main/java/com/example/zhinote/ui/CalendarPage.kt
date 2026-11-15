package com.example.zhinote.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.zhinote.data.local.NoteEntity
import com.example.zhinote.data.local.TodoEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

@Composable
fun CalendarPage(notes: List<NoteEntity>, todos: List<TodoEntity>, onSelect: (SpaceItem) -> Unit) {
    var epoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var mode by rememberSaveable { mutableStateOf("月") }
    val selected = LocalDate.ofEpochDay(epoch)
    val month = YearMonth.from(selected)
    val dayItems = remember(notes, todos, epoch) {
        notes.filter { it.createdAt.localDate() == selected }.map { it.item() } +
            todos.filter { (it.dueDate ?: it.createdAt).localDate() == selected }.map { it.item() }
    }
    val hasContent = remember(notes, todos) {
        (notes.map { it.createdAt.localDate() } + todos.map { (it.dueDate ?: it.createdAt).localDate() }).toSet()
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var revealJob by remember { mutableStateOf<Job?>(null) }
    val label = selected.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE)) + " · ${dayItems.size}条记录"
    val expanded by remember { derivedStateOf { offset >= -1f } }

    fun consumeHeader(delta: Float): Float {
        if (delta != 0f) revealJob?.cancel()
        val previous = offset
        offset = (offset + delta).coerceIn(-headerHeight.toFloat(), 0f)
        return offset - previous
    }
    suspend fun reveal() {
        animate(offset, 0f, animationSpec = tween(220)) { value, _ -> offset = value }
    }
    suspend fun settle(velocity: Float) {
        if (offset >= -1f || offset <= -headerHeight + 1f) return
        val target = if (abs(velocity) > 50f) {
            if (velocity < 0) -headerHeight.toFloat() else 0f
        } else if (offset < -headerHeight / 2f) -headerHeight.toFloat() else 0f
        animate(offset, target, animationSpec = tween(180)) { value, _ -> offset = value }
    }
    val connection = remember { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, consumeHeader(available.y))
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset < -1f && offset > -headerHeight + 1f) {
                settle(available.y)
                return Velocity(0f, available.y)
            }
            return Velocity.Zero
        }
    } }
    fun shift(direction: Int) {
        epoch = when (mode) {
            "月" -> selected.plusMonths(direction.toLong())
            "周" -> selected.plusWeeks(direction.toLong())
            else -> selected.plusDays(direction.toLong())
        }.toEpochDay()
    }

    BoxWithConstraints(Modifier.fillMaxSize().nestedScroll(connection).testTag("calendar-page")) {
        val availableHeader = with(density) { (maxHeight - 48.dp).coerceAtLeast(0.dp).roundToPx() }
        Column(Modifier.fillMaxSize()) {
            // One shared offset moves the title, switchers, weekday row and date grid together.
            // The header drag is a gesture on that same offset, not a second scroll container.
            Layout(modifier = Modifier.fillMaxWidth().clipToBounds().testTag("calendar-expanded")
                .draggable(rememberDraggableState { delta ->
                    val remaining = delta - consumeHeader(delta)
                    if (remaining != 0f) listState.dispatchRawDelta(-remaining)
                }, Orientation.Vertical, onDragStopped = { velocity -> settle(velocity) }), content = {
                Column(Modifier.fillMaxWidth().onSizeChanged { size ->
                    val wasCollapsed = headerHeight > 0 && offset <= -headerHeight + 1f
                    headerHeight = size.height
                    offset = if (wasCollapsed) -size.height.toFloat() else offset.coerceIn(-size.height.toFloat(), 0f)
                }.pointerInput(mode, epoch) {
                    var horizontal = 0f
                    detectHorizontalDragGestures(onDragStart = { horizontal = 0f }, onHorizontalDrag = { change, amount ->
                        horizontal += amount; change.consume()
                    }, onDragEnd = { if (abs(horizontal) > 56.dp.toPx()) shift(if (horizontal < 0) 1 else -1) })
                }) {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ shift(-1) }) { Icon(Icons.Outlined.ChevronLeft, "上一${mode}") }
                        Text("${selected.year} 年 ${selected.monthValue} 月", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton({ epoch = LocalDate.now().toEpochDay() }) { Text("今天") }
                        IconButton({ shift(1) }) { Icon(Icons.Outlined.ChevronRight, "下一${mode}") }
                    }
                    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("月", "周", "日").forEach { title -> FilterChip(mode == title, { mode = title }, { Text(title) }) }
                    }
                    if (mode != "日") {
                        Row(Modifier.padding(horizontal = 16.dp)) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                Text(it, Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val first = if (mode == "周") selected.minusDays((selected.dayOfWeek.value - 1).toLong())
                            else month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
                        val rows = if (mode == "周") 1 else ((month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7)
                        repeat(rows) { row ->
                            Row(Modifier.padding(horizontal = 16.dp)) {
                                repeat(7) { column ->
                                    val day = first.plusDays((row * 7 + column).toLong())
                                    Surface(onClick = { epoch = day.toEpochDay() }, shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("calendar-day-${day.toEpochDay()}")
                                            .semantics { this.selected = day == selected; contentDescription = day.toString() },
                                        color = if (day == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text("${day.dayOfMonth}", color = if (day.monthValue != selected.monthValue)
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f) else MaterialTheme.colorScheme.onSurface)
                                            if (day in hasContent) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }) { measurables, constraints ->
                val header = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val visible = (header.height + offset).roundToInt().coerceIn(0, min(constraints.maxHeight, availableHeader))
                layout(constraints.maxWidth, visible) { header.placeRelative(0, offset.roundToInt()) }
            }
            Surface(onClick = { revealJob?.cancel(); revealJob = scope.launch { reveal() } }, color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("calendar-compact").semantics {
                    contentDescription = "$label，点击展开日历"
                    stateDescription = if (expanded) "日历已展开" else if (offset <= -headerHeight + 1f) "日历已收起" else "日历收缩中"
                }) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!expanded) Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 8.dp).size(20.dp))
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("calendar-records"), state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dayItems.isEmpty()) item { EmptyState("这一天，水面很安静", modifier = Modifier.fillParentMaxHeight()) }
                items(dayItems, key = { it.key }) { item -> CalendarRecord(item, onSelect) }
            }
        }
    }
}

@Composable
private fun CalendarRecord(item: SpaceItem, onSelect: (SpaceItem) -> Unit) {
    Surface(onClick = { onSelect(item) }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(DropColors[item.color], CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (item.type == "NOTE") Icons.Outlined.EditNote else if (item.completed) Icons.Outlined.Check else Icons.Outlined.Checklist,
                    if (item.type == "NOTE") "笔记" else if (item.completed) "已完成待办" else "待办", tint = Color(0xFF304B4D), modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.content.isNotBlank()) Text(item.content, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${item.time.readableTime()}${if (item.priority == 2) " · 高优先级" else ""}${if (item.favorite) " · 收藏" else ""}${if (item.tags.isNotBlank()) " · ${item.tags}" else ""}",
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
