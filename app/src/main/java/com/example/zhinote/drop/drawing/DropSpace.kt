package com.example.zhinote.drop.drawing

import android.animation.ValueAnimator
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.zhinote.data.local.DropLayoutEntity
import com.example.zhinote.data.repository.UserSettings
import com.example.zhinote.drop.engine.*
import com.example.zhinote.ui.DropColors
import com.example.zhinote.ui.SpaceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable
fun DropSpace(items: List<SpaceItem>, layouts: List<DropLayoutEntity>, settings: UserSettings,
    selected: SpaceItem?, dissolving: Map<String, SpaceItem>, onSelect: (SpaceItem?) -> Unit,
    onPosition: (SpaceItem, Float, Float) -> Unit, modifier: Modifier = Modifier, onQuickComplete: (SpaceItem) -> Unit = {}) {
    val density = LocalDensity.current
    val engine = remember { DropPhysicsEngine() }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(FloatArray(0)) }
    var frameKeys by remember { mutableStateOf(emptyList<String>()) }
    var cluster by remember { mutableStateOf(1f to 1f) }
    var pressed by remember { mutableStateOf<String?>(null) }
    var ripple by remember { mutableStateOf<Offset?>(null) }
    var rippleStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val dissolveStarts = remember { mutableMapOf<String, Long>() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    val reduced = settings.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestPosition by rememberUpdatedState(onPosition)
    val latestItems by rememberUpdatedState(items)
    val latestComplete by rememberUpdatedState(onQuickComplete)
    val path = remember { Path() }
    val materials = remember(density.density, items.map { it.color to it.size }) {
        items.map { item ->
            val color = DropColors[item.color.coerceIn(0, 6)]
            val radius = item.size / 2f * density.density
            Brush.radialGradient(listOf(Color.White.copy(alpha = .85f), color.copy(alpha = .88f), color.copy(alpha = .60f)),
                Offset(-radius * .38f, -radius * .4f), radius * 1.8f)
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) } }
    LaunchedEffect(dissolving.keys) {
        dissolveStarts.keys.retainAll(dissolving.keys)
        dissolving.keys.forEach { dissolveStarts.putIfAbsent(it, System.currentTimeMillis()) }
    }
    LaunchedEffect(items.map { it.key to it.size }, layouts, area) {
        if (area.width == 0 || area.height == 0) return@LaunchedEffect
        val specs = items.map { item ->
            val saved = layouts.find { it.itemId == item.id && it.itemType == item.type }
            DropSpec(item.key, item.size / 2f, saved?.normalizedX, saved?.normalizedY)
        }
        withContext(Dispatchers.Default) { engine.reset(specs, area.width / density.density, area.height / density.density) }
        frame = engine.snapshot()
        cluster = engine.clusterBounds()
        frameKeys = items.map { it.key }
    }
    LaunchedEffect(selected?.key, reduced, settings.ripple) { engine.select(selected?.key, settings.ripple && !reduced) }
    LaunchedEffect(active, items.map { it.key }, area, settings, rippleStart, dissolving.keys, dragged) {
        if (!active || area.width == 0) return@LaunchedEffect
        if (reduced) { frame = engine.snapshot(); return@LaunchedEffect }
        var last = 0L; var lastRender = 0L
        var settlingFrames = 0
        while (true) {
            val tick = withFrameNanos { it }
                val interval = if (settings.performance) 33_333_333L else 0L
                if (tick - lastRender >= interval) {
                    val delta = if (last == 0L) 1f / 120f else (tick - last) / 1_000_000_000f
                    last = tick; lastRender = tick
                    frame = withContext(Dispatchers.Default) {
                        engine.advance(delta, settings.floating && !reduced, settings.collisions && !reduced,
                            listOf(.45f, 1f, 1.5f)[settings.intensity])
                        engine.snapshot()
                    }
                    now = System.currentTimeMillis()
                    cluster = engine.clusterBounds()
                    settlingFrames++
                }
            if ((!settings.floating || reduced) && settlingFrames > 120 && dragged == null && now - rippleStart > 1000 && dissolving.isEmpty()) break
        }
    }
    Box(modifier.onSizeChanged { area = it }
        .pointerInput(settings.ripple, reduced) {
            var firstTapKey: String? = null
            var lastDown = 0L
            detectTapGestures(onPress = { point ->
                pressed = engine.hit(point.x / density.density, point.y / density.density)
                val downTime = android.os.SystemClock.uptimeMillis()
                if (downTime - lastDown > viewConfiguration.doubleTapTimeoutMillis || firstTapKey == null) firstTapKey = pressed
                lastDown = downTime
                tryAwaitRelease(); pressed = null
            }, onTap = { point ->
                val key = engine.hit(point.x / density.density, point.y / density.density)
                latestSelect(latestItems.find { it.key == key })
                if (settings.ripple && !reduced) { ripple = point; rippleStart = System.currentTimeMillis() }
                firstTapKey = null
            }, onDoubleTap = { point ->
                val key = engine.hit(point.x / density.density, point.y / density.density)
                val item = latestItems.find { it.key == key }
                if (item != null && item.type == "TODO" && key == firstTapKey) latestComplete(item) else latestSelect(item)
                if (settings.ripple && !reduced) { ripple = point; rippleStart = System.currentTimeMillis() }
                firstTapKey = null
            })
        }
        .pointerInput(settings.dragging, reduced) {
            if (settings.dragging) detectDragGestures(onDragStart = { point ->
                dragged = engine.hit(point.x / density.density, point.y / density.density)
                val i = latestItems.indexOfFirst { it.key == dragged }
                dragPosition = if (i >= 0 && frame.size > i * 3 + 1) Offset(frame[i * 3], frame[i * 3 + 1]) else point / density.density
            }, onDrag = { change, amount ->
                dragged?.let { key ->
                    change.consume(); dragPosition += amount / density.density
                    engine.drag(key, dragPosition.x, dragPosition.y); frame = engine.snapshot()
                }
            }, onDragEnd = {
                dragged?.let { key -> engine.release(key)?.let { (x, y) -> latestItems.find { it.key == key }?.let { latestPosition(it, x, y) } } }
                dragged = null
            }, onDragCancel = { dragged?.let { engine.release(it) }; dragged = null })
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rx = cluster.first * density.density; val ry = cluster.second * density.density
            drawOval(Brush.radialGradient(listOf(Color(0xFFDDECE7).copy(alpha = .36f), Color(0xFFC4CEDF).copy(alpha = .12f), Color.Transparent), center, max(rx, ry)),
                center - Offset(rx, ry), androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f))
            drawOval(Color(0xFF9DBBB7).copy(alpha = .22f), center - Offset(rx, ry), androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f), style = Stroke(1.dp.toPx()))
            ripple?.let { center ->
                val progress = ((now - rippleStart) / 850f).coerceIn(0f, 1f)
                if (progress < 1f) drawCircle(Color(0xFF86AAA8).copy(alpha = (1f - progress) * .45f), progress * 210.dp.toPx(), center, style = Stroke(1.3.dp.toPx()))
            }
            items.forEachIndexed { index, item ->
                if (frame.size <= index * 3 + 2 || frameKeys.getOrNull(index) != item.key) return@forEachIndexed
                val center = Offset(frame[index * 3], frame[index * 3 + 1]) * density.density
                val progress = dissolveStarts[item.key]?.let { ((now - it) / 240f).coerceIn(0f, 1f) } ?: 0f
                val scale = (if (pressed == item.key) .92f else if (selected?.key == item.key) 1.07f else 1f) * (1f - progress) * (frame[index * 3 + 2] / (item.size / 2f))
                val r = item.size / 2f * density.density
                if (r * scale < 1f) return@forEachIndexed
                val color = DropColors[item.color.coerceIn(0, 6)]
                withTransform({ translate(center.x, center.y); scale(scale, scale, Offset.Zero) }) {
                // Four cubic segments yield a subtle organic silhouette rather than identical circles.
                val variation = .035f * sin(index * 1.9f)
                path.reset()
                path.moveTo(0f, -r)
                path.cubicTo(r * .65f, -r * (1f + variation), r, -r * .55f, r, 0f)
                path.cubicTo(r, r * .6f, r * .5f, r, 0f, r)
                path.cubicTo(-r * .6f, r, -r * (1f - variation), r * .5f, -r, 0f)
                path.cubicTo(-r, -r * .6f, -r * .55f, -r, 0f, -r)
                path.close()
                drawCircle(Color(0xFF233F42).copy(alpha = .06f * (1f - progress)), r * .98f, Offset(0f, 5.dp.toPx()))
                drawPath(path, materials[index])
                drawPath(path, Color.White.copy(alpha = .75f * (1f - progress)), style = Stroke(1.1.dp.toPx()))
                drawArc(Color.White.copy(alpha = .70f), 205f, 78f, false, Offset(-r * .73f, -r * .78f), androidx.compose.ui.geometry.Size(r * 1.5f, r * 1.4f), style = Stroke(2.dp.toPx()))
                paint.color = android.graphics.Color.argb(((1f - progress) * 255).toInt(), 39, 64, 66)
                paint.textSize = (11.5f * density.fontScale.coerceAtMost(1.5f)).dp.toPx()
                val prefix = if (item.type == "TODO") "${if (item.priority == 2) "! " else "□ "}" else if (item.favorite) "☆ " else ""
                val title = prefix + item.title
                var visible = title
                while (paint.measureText(visible) > r * 1.65f && visible.length > 1) visible = visible.dropLast(1)
                if (visible != title) visible = visible.dropLast(1) + "…"
                drawContext.canvas.nativeCanvas.drawText(visible, 0f, paint.textSize / 3, paint)
                }
                if (progress > 0f && !settings.performance && !reduced) repeat(6) { particle ->
                    val angle = particle * PI.toFloat() / 3
                    val distance = frame[index * 3 + 2] * density.density * progress * 1.9f
                    drawCircle(color.copy(alpha = (1 - progress) * .65f), 2.5.dp.toPx() * (1 - progress), center + Offset(cos(angle) * distance, sin(angle) * distance))
                }
            }
        }
        items.forEachIndexed { i, item ->
            if (frameKeys.getOrNull(i) == item.key && !dissolving.containsKey(item.key)) {
                val targetSize = item.size.coerceAtLeast(48f)
                Box(Modifier.offset {
                    if (frame.size <= i * 3 + 1) IntOffset.Zero else IntOffset(((frame[i * 3] - targetSize / 2) * density.density).roundToInt(), ((frame[i * 3 + 1] - targetSize / 2) * density.density).roundToInt())
                }
                    .size(targetSize.dp).semantics {
                        contentDescription = "${if (item.type == "NOTE") "笔记" else "待办"}：${item.title}${if (item.priority == 2) "，高优先级" else ""}"
                        role = Role.Button
                        onClick("展开内容") { latestSelect(item); true }
                        if (item.type == "TODO") customActions = listOf(CustomAccessibilityAction("快速完成待办") { latestComplete(item); true })
                    })
            }
        }
    }
}
