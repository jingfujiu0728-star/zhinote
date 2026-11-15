package com.example.zhinote.drop.drawing

import android.animation.ValueAnimator
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.zhinote.data.repository.UserSettings
import com.example.zhinote.drop.engine.*
import com.example.zhinote.ui.DropColors
import com.example.zhinote.ui.SpaceItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun HoneycombSpace(items: List<SpaceItem>, settings: UserSettings, selected: SpaceItem?,
    dissolving: Map<String, SpaceItem>, onSelect: (SpaceItem?) -> Unit, onQuickComplete: (SpaceItem) -> Unit,
    modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val reduced = settings.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
    var area by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var inertia by remember { mutableStateOf<Job?>(null) }
    val cells = remember(items.map { it.key }) { HoneycombLayout.cells(items.map { it.key }) }
    var oldCells by remember { mutableStateOf<List<HoneycombCell>>(emptyList()) }
    var from by remember { mutableStateOf(emptyMap<String, HoneycombCell>()) }
    val reflow = remember { Animatable(1f) }
    var feedbackKey by remember { mutableStateOf<String?>(null) }
    var pressing by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressing && !reduced) 1f else 0f, spring(stiffness = 650f), label = "honeycomb-press")
    val selection by animateFloatAsState(if (selected != null && !reduced) 1f else 0f, spring(stiffness = 450f), label = "honeycomb-selection")
    var ripple by remember { mutableStateOf<Offset?>(null) }
    val rippleProgress = remember { Animatable(1f) }
    val dissolves = remember { mutableStateMapOf<String, Float>() }
    val latestItems by rememberUpdatedState(items)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestComplete by rememberUpdatedState(onQuickComplete)
    val latestCells by rememberUpdatedState(cells)
    val latestReduced by rememberUpdatedState(reduced)
    val interpolated by remember(cells) { derivedStateOf {
        cells.map { cell -> from[cell.key]?.let { old -> cell.copy(
            x = old.x + (cell.x - old.x) * reflow.value,
            y = old.y + (cell.y - old.y) * reflow.value) } ?: cell }
    } }
    // Gesture offsets invalidate drawing and placement, without recomposing every icon each frame.
    val selectedKey = selected?.key
    val circles by remember(cells, density.density, selectedKey) { derivedStateOf {
        HoneycombLayout.project(interpolated, area.width / density.density, area.height / density.density,
            pan.x, pan.y, zoom, selectedKey, selection)
    } }
    val itemsByKey = remember(items) { items.associateBy { it.key } }
    val material = remember { Brush.radialGradient(listOf(Color.White.copy(alpha = .82f), Color.White.copy(alpha = .15f), Color.Transparent), Offset(-.35f, -.4f), 1.9f) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    } }

    fun bounded(value: Offset): Offset {
        val (x, y) = HoneycombLayout.clampPan(latestCells, area.width / density.density,
            area.height / density.density, zoom, value.x, value.y)
        return Offset(x, y)
    }
    LaunchedEffect(cells, reduced) {
        inertia?.cancel()
        val progress = reflow.value
        from = oldCells.map { cell -> from[cell.key]?.let { start -> cell.copy(
            x = start.x + (cell.x - start.x) * progress,
            y = start.y + (cell.y - start.y) * progress) } ?: cell }.associateBy { it.key }
        val animate = oldCells.isNotEmpty() && !reduced
        oldCells = cells
        reflow.snapTo(if (animate) 0f else 1f)
        pan = bounded(pan)
        if (animate) reflow.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(dissolving.keys, reduced) {
        dissolves.keys.retainAll(dissolving.keys)
        if (dissolving.isEmpty()) return@LaunchedEffect
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(if (reduced) 0 else 240)) {
            dissolving.keys.forEach { dissolves[it] = value }
        }
    }
    LaunchedEffect(active) { if (!active) inertia?.cancel() }
    DisposableEffect(Unit) { onDispose { inertia?.cancel() } }

    Box(modifier.clipToBounds().testTag("honeycomb-space").onSizeChanged { area = it }
        .semantics {
            contentDescription = "蜂窝水滴空间"
            stateDescription = "${items.size}个水滴，缩放 ${(zoom * 100).roundToInt()}%"
            customActions = listOf(
                CustomAccessibilityAction("放大") { zoom = (zoom * 1.2f).coerceAtMost(HoneycombLayout.MAX_ZOOM); pan = bounded(pan); true },
                CustomAccessibilityAction("缩小") { zoom = (zoom / 1.2f).coerceAtLeast(HoneycombLayout.MIN_ZOOM); pan = bounded(pan); true },
                CustomAccessibilityAction("回到中心") { inertia?.cancel(); zoom = 1f; pan = Offset.Zero; true })
        }
        .pointerInput(settings.ripple, reduced) {
            var firstTapKey: String? = null
            var lastDown = 0L
            detectTapGestures(onPress = { point ->
                feedbackKey = HoneycombLayout.hit(circles, point.x / density.density, point.y / density.density)
                pressing = feedbackKey != null
                val time = android.os.SystemClock.uptimeMillis()
                if (time - lastDown > viewConfiguration.doubleTapTimeoutMillis || firstTapKey == null) firstTapKey = feedbackKey
                lastDown = time
                tryAwaitRelease(); pressing = false
            }, onTap = { point ->
                val key = HoneycombLayout.hit(circles, point.x / density.density, point.y / density.density)
                latestSelect(latestItems.find { it.key == key })
                if (settings.ripple && !reduced) { ripple = point; scope.launch { rippleProgress.snapTo(0f); rippleProgress.animateTo(1f, tween(650)) } }
                firstTapKey = null
            }, onDoubleTap = { point ->
                val key = HoneycombLayout.hit(circles, point.x / density.density, point.y / density.density)
                val item = latestItems.find { it.key == key }
                if (item != null && item.type == "TODO" && key == firstTapKey) latestComplete(item) else latestSelect(item)
                if (settings.ripple && !reduced) { ripple = point; scope.launch { rippleProgress.snapTo(0f); rippleProgress.animateTo(1f, tween(650)) } }
                firstTapKey = null
            })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                inertia?.cancel()
                val tracker = VelocityTracker()
                var moved = false
                var multiTouch = false
                var totalPan = Offset.Zero
                var totalZoom = 1f
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.any { it.pressed }
                    if (!pressed) break
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val delta = event.calculatePan()
                    val factor = event.calculateZoom()
                    totalPan += delta; totalZoom *= factor
                    if (!moved && (totalPan.getDistance() > viewConfiguration.touchSlop || abs(totalZoom - 1f) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop)) moved = true
                    if (event.changes.count { it.pressed } > 1) { moved = true; multiTouch = true }
                    tracker.addPosition(event.changes.first().uptimeMillis, event.calculateCentroid())
                    if (moved) {
                        val w = area.width / density.density; val h = area.height / density.density
                        val focal = HoneycombLayout.unproject(centroid.x / density.density, centroid.y / density.density, w, h)
                        val shifted = HoneycombLayout.unproject((centroid.x + delta.x) / density.density, (centroid.y + delta.y) / density.density, w, h)
                        val oldZoom = zoom
                        zoom = (zoom * factor).coerceIn(HoneycombLayout.MIN_ZOOM, HoneycombLayout.MAX_ZOOM)
                        val anchor = Offset(focal.first, focal.second)
                        pan = bounded(anchor + (pan - anchor) * (zoom / oldZoom) + Offset(shifted.first - focal.first, shifted.second - focal.second))
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (moved && !multiTouch && !latestReduced && active) {
                    val velocity = tracker.calculateVelocity()
                    inertia = scope.launch {
                        var speed = Offset(velocity.x / density.density, velocity.y / density.density)
                        var previous = 0L
                        val start = withFrameNanos { it }
                        while (speed.getDistance() > 10f) {
                            val tick = withFrameNanos { it }
                            val dt = if (previous == 0L) 1f / 60f else ((tick - previous) / 1_000_000_000f).coerceAtMost(.04f)
                            previous = tick
                            val next = bounded(pan + speed * dt)
                            if ((next - pan).getDistance() < .02f || tick - start > 1_500_000_000L) break
                            pan = next
                            speed *= exp(-5.5f * dt)
                        }
                    }
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            ripple?.let { point -> if (rippleProgress.value < 1f) drawCircle(
                Color(0xFF86AAA8).copy(alpha = (1f - rippleProgress.value) * .35f),
                rippleProgress.value * 120.dp.toPx(), point, style = Stroke(1.dp.toPx())) }
            circles.forEach { circle ->
                val item = itemsByKey[circle.key] ?: return@forEach
                val progress = dissolves[circle.key] ?: 0f
                val radius = circle.radius * density.density * (1f - progress) * (if (feedbackKey == circle.key) 1f - .055f * press else 1f)
                val center = Offset(circle.x, circle.y) * density.density
                if (radius < 1f || center.x + radius < 0 || center.y + radius < 0 || center.x - radius > size.width || center.y - radius > size.height) return@forEach
                val color = DropColors[item.color.coerceIn(0, 6)]
                drawCircle(Color(0xFF233F42).copy(alpha = .07f * (1f - progress)), radius, center + Offset(0f, 4.dp.toPx()))
                drawCircle(color.copy(alpha = .74f * (1f - progress)), radius, center)
                // Share the gradient shader; axial coordinates are cached across gesture frames.
                withTransform({ translate(center.x, center.y); scale(radius, radius, Offset.Zero) }) {
                    drawCircle(material, 1f, Offset.Zero)
                }
                drawCircle(Color.White.copy(alpha = .68f * (1f - progress)), radius, center, style = Stroke(1.dp.toPx()))
                drawArc(Color.White.copy(alpha = .65f * (1f - progress)), 208f, 74f, false,
                    center - Offset(radius * .72f, radius * .75f), androidx.compose.ui.geometry.Size(radius * 1.44f, radius * 1.4f), style = Stroke(1.7.dp.toPx()))
                paint.color = android.graphics.Color.argb(((1f - progress) * 255).roundToInt(), 39, 64, 66)
                paint.textSize = (11f * density.fontScale.coerceAtMost(1.5f)).dp.toPx()
                val prefix = if (item.type == "TODO") (if (item.priority == 2) "! " else "□ ") else if (item.favorite) "☆ " else ""
                val title = prefix + item.title
                val count = paint.breakText(title, true, radius * 1.65f, null)
                val visible = if (count >= title.length) title else title.take((count - 1).coerceAtLeast(0)) + "…"
                drawContext.canvas.nativeCanvas.drawText(visible, center.x, center.y + paint.textSize / 3f, paint)
            }
        }
        Layout(modifier = Modifier.fillMaxSize(), content = {
            items.forEach { item -> key(item.key) { Box(Modifier.semantics {
                    contentDescription = "${if (item.type == "NOTE") "笔记" else "待办"}：${item.title}${if (item.priority == 2) "，高优先级" else ""}"
                    role = Role.Button
                    onClick("展开内容") { latestSelect(item); true }
                    if (item.type == "TODO") customActions = listOf(CustomAccessibilityAction("快速完成待办") { latestComplete(item); true })
                }) } }
        }) { measurables, constraints ->
            val projected = circles
            val placements = measurables.mapIndexedNotNull { index, measurable ->
                val circle = projected.getOrNull(index) ?: return@mapIndexedNotNull null
                val radius = circle.radius * density.density
                val x = circle.x * density.density; val y = circle.y * density.density
                if (circle.key in dissolving || x + radius < 0 || y + radius < 0 || x - radius > constraints.maxWidth || y - radius > constraints.maxHeight) return@mapIndexedNotNull null
                val diameter = (radius * 2f).roundToInt()
                Triple(measurable.measure(Constraints.fixed(diameter, diameter)), (x - radius).roundToInt(), (y - radius).roundToInt())
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placements.forEach { (placeable, x, y) -> placeable.place(x, y) }
            }
        }
    }
}
