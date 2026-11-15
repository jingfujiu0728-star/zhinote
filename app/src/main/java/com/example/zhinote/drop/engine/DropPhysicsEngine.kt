package com.example.zhinote.drop.engine

import kotlin.math.*

data class DropSpec(val id: String, val radius: Float, val x: Float? = null, val y: Float? = null)
class DropBody(val id: String, var x: Float, var y: Float, val radius: Float,
    var targetX: Float, var targetY: Float, val manual: Boolean) {
    var vx = 0f; var vy = 0f
    var selected = false; var dragging = false
    val mass = (radius / 32f).coerceAtLeast(0.5f)
}

/** Pixel-independent, deterministic simulation. Contains no Android, database or UI code.
 * All access is synchronized so the simulation may run on Dispatchers.Default. */
class DropPhysicsEngine {
    private val bodies = mutableListOf<DropBody>()
    private val buckets = HashMap<Long, MutableList<Int>>()
    private var width = 1f; private var height = 1f
    private var clock = 0f; private var accumulator = 0f
    private var clusterX = 1f; private var clusterY = 1f
    private var targetClusterX = 1f; private var targetClusterY = 1f
    private val fixedStep = 1f / 120f

    @Synchronized fun reset(specs: List<DropSpec>, width: Float, height: Float) {
        val oldWidth = this.width; val oldHeight = this.height
        this.width = width.coerceAtLeast(1f); this.height = height.coerceAtLeast(1f)
        val previous = bodies.associateBy { it.id }
        bodies.clear()
        val growth = (.64f + sqrt(specs.size.toFloat() / 60f) * .34f).coerceAtMost(.98f)
        targetClusterX = (this.width / 2f - 5f) * growth
        targetClusterY = (this.height / 2f - 5f) * growth
        if (previous.isEmpty() || oldWidth != this.width || oldHeight != this.height) { clusterX = targetClusterX; clusterY = targetClusterY }
        // Fit each independent drop into the ellipse; density scaling avoids a pile of overlaps.
        val requestedArea = specs.sumOf { (PI * it.radius * it.radius) }.toFloat().coerceAtLeast(1f)
        val scale = sqrt(PI.toFloat() * targetClusterX * targetClusterY * .53f / requestedArea).coerceAtMost(1f)
        val goldenAngle = PI.toFloat() * (3f - sqrt(5f))
        specs.forEachIndexed { i, spec ->
            val radius = (spec.radius * scale).coerceAtLeast(16f).coerceAtMost(min(targetClusterX, targetClusterY) * .45f)
            val orbit = sqrt((i + .45f) / specs.size.coerceAtLeast(1))
            val angle = i * goldenAngle - .65f
            val tx = spec.x?.times(this.width) ?: (this.width / 2f + cos(angle) * orbit * (targetClusterX - radius - 5f))
            val ty = spec.y?.times(this.height) ?: (this.height / 2f + sin(angle) * orbit * (targetClusterY - radius - 5f))
            val old = previous[spec.id]
            bodies += DropBody(spec.id, if (old == null) tx else old.x / oldWidth * this.width,
                if (old == null) ty else old.y / oldHeight * this.height, radius, tx, ty, spec.x != null)
        }
        // Packing is initialization work on the simulation dispatcher, never a per-frame all-pairs loop.
        if (previous.isEmpty()) repeat(100) {
            for (i in bodies.indices) for (j in i + 1 until bodies.size) {
                val a = bodies[i]; val b = bodies[j]
                val dx = b.x - a.x; val dy = b.y - a.y; val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(.001f)
                val overlap = a.radius + b.radius + 5f - distance
                if (overlap > 0) {
                    val correction = overlap * .5f
                    if (!a.manual) { a.x -= dx / distance * correction; a.y -= dy / distance * correction }
                    if (!b.manual) { b.x += dx / distance * correction; b.y += dy / distance * correction }
                }
            }
            bodies.forEach { constrain(it, targetClusterX, targetClusterY) }
        }
        bodies.forEach { b ->
            if (!b.manual && previous.isEmpty()) { b.targetX = b.x; b.targetY = b.y }
            constrain(b, max(clusterX, targetClusterX), max(clusterY, targetClusterY))
        }
        accumulator = 0f
    }
    @Synchronized fun advance(seconds: Float, floating: Boolean = true, collisions: Boolean = true, intensity: Float = 1f) {
        if (!seconds.isFinite() || seconds <= 0) return
        accumulator += seconds.coerceAtMost(0.06f)
        while (accumulator >= fixedStep) {
            tick(floating, collisions, intensity)
            accumulator -= fixedStep
        }
    }
    private fun tick(floating: Boolean, collisions: Boolean, intensity: Float) {
        clock += fixedStep
        clusterX += (targetClusterX - clusterX) * .035f
        clusterY += (targetClusterY - clusterY) * .035f
        for ((index, b) in bodies.withIndex()) {
            if (b.dragging) continue
            val amplitude = if (floating && !b.selected) 7f * intensity else 0f
            val tx = b.targetX + sin(clock * 0.65f + index * 2.4f) * amplitude
            val ty = b.targetY + cos(clock * 0.55f + index * 1.7f) * amplitude
            b.vx += (tx - b.x) * 9f * fixedStep / b.mass
            b.vy += (ty - b.y) * 9f * fixedStep / b.mass
            if (!b.manual) { b.vx += (width / 2f - b.x) * .12f * fixedStep; b.vy += (height / 2f - b.y) * .12f * fixedStep }
        }
        if (collisions) repel()
        for (b in bodies) {
            if (!b.dragging) {
                b.vx *= 0.975f; b.vy *= 0.975f
                b.x += b.vx.coerceIn(-350f, 350f) * fixedStep
                b.y += b.vy.coerceIn(-350f, 350f) * fixedStep
            }
            if (b.x < b.radius) { b.x = b.radius; b.vx = abs(b.vx) * 0.4f }
            if (b.x > width - b.radius) { b.x = width - b.radius; b.vx = -abs(b.vx) * 0.4f }
            if (b.y < b.radius) { b.y = b.radius; b.vy = abs(b.vy) * 0.4f }
            if (b.y > height - b.radius) { b.y = height - b.radius; b.vy = -abs(b.vy) * 0.4f }
            constrain(b, clusterX, clusterY)
        }
    }
    private fun constrain(b: DropBody, rx: Float, ry: Float) {
        val ax = (rx - b.radius - 2f).coerceAtLeast(1f); val ay = (ry - b.radius - 2f).coerceAtLeast(1f)
        val dx = b.x - width / 2f; val dy = b.y - height / 2f
        val q = sqrt((dx / ax).pow(2) + (dy / ay).pow(2))
        if (q > .92f && !b.dragging) { b.vx -= dx * (q - .92f) * .025f; b.vy -= dy * (q - .92f) * .025f }
        if (q > 1f) { b.x = width / 2f + dx / q; b.y = height / 2f + dy / q; b.vx *= .6f; b.vy *= .6f }
    }
    @Synchronized fun clusterBounds(): Pair<Float, Float> = clusterX to clusterY
    private fun key(x: Int, y: Int) = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
    private fun repel() {
        val cellSize = (bodies.maxOfOrNull { it.radius } ?: 32f) * 2.5f
        buckets.values.forEach { it.clear() }
        bodies.forEachIndexed { index, body -> buckets.getOrPut(key((body.x / cellSize).toInt(), (body.y / cellSize).toInt())) { mutableListOf() }.add(index) }
        bodies.forEachIndexed { i, a ->
            val cx = (a.x / cellSize).toInt(); val cy = (a.y / cellSize).toInt()
            for (gx in cx - 1..cx + 1) for (gy in cy - 1..cy + 1) {
                for (j in buckets[key(gx, gy)].orEmpty()) {
                    if (j <= i) continue
                    val b = bodies[j]
                    var dx = b.x - a.x; var dy = b.y - a.y
                    var distance = sqrt(dx * dx + dy * dy)
                    val limit = a.radius + b.radius + 5f
                    if (distance >= limit) continue
                    if (distance < 0.001f) { dx = 1f; dy = 0f; distance = 1f }
                    val force = (limit - distance) * 55f * fixedStep
                    val fx = dx / distance * force; val fy = dy / distance * force
                    if (!a.dragging) { a.vx -= fx / a.mass; a.vy -= fy / a.mass }
                    if (!b.dragging) { b.vx += fx / b.mass; b.vy += fy / b.mass }
                }
            }
        }
    }
    @Synchronized fun hit(x: Float, y: Float): String? = bodies.filter {
        val dx = x - it.x; val dy = y - it.y
        dx * dx + dy * dy <= (it.radius + 4f).pow(2)
    }.minByOrNull { (x - it.x).pow(2) + (y - it.y).pow(2) }?.id
    @Synchronized fun select(id: String?, impulse: Boolean = true) {
        bodies.forEach { it.selected = it.id == id }
        val source = bodies.firstOrNull { it.id == id } ?: return
        if (!impulse) return
        for (body in bodies) {
            if (body === source || body.dragging) continue
            val dx = body.x - source.x; val dy = body.y - source.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (distance < 230f) {
                val force = 150f * (1 - distance / 230f)
                body.vx += dx / distance * force; body.vy += dy / distance * force
            }
        }
    }
    @Synchronized fun drag(id: String, x: Float, y: Float) {
        bodies.firstOrNull { it.id == id }?.let {
            it.dragging = true; it.vx = 0f; it.vy = 0f
            it.x = x.coerceIn(it.radius, width - it.radius); it.y = y.coerceIn(it.radius, height - it.radius)
            constrain(it, clusterX, clusterY)
        }
    }
    @Synchronized fun release(id: String): Pair<Float, Float>? {
        val b = bodies.firstOrNull { it.id == id } ?: return null
        b.dragging = false; b.targetX = b.x; b.targetY = b.y
        return b.x / width to b.y / height
    }
    @Synchronized fun snapshot(): FloatArray = FloatArray(bodies.size * 3).also { result ->
        bodies.forEachIndexed { i, b -> result[i * 3] = b.x; result[i * 3 + 1] = b.y; result[i * 3 + 2] = b.radius }
    }
    @Synchronized fun positions(): List<Pair<Float, Float>> = bodies.map { it.x to it.y }
}
