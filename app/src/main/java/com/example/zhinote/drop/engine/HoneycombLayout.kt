package com.example.zhinote.drop.engine

import kotlin.math.*

data class HoneycombCell(val key: String, val q: Int, val r: Int, val x: Float, val y: Float)
data class HoneycombCircle(val key: String, val x: Float, val y: Float, val radius: Float)

/** Axial hex coordinates, computed only when the item order changes. Units are dp. */
object HoneycombLayout {
    const val PITCH = 88f
    const val MIN_ZOOM = .8f
    const val MAX_ZOOM = 2f

    fun cells(keys: List<String>): List<HoneycombCell> {
        if (keys.isEmpty()) return emptyList()
        val coordinates = mutableListOf(0 to 0)
        var ring = 1
        while (coordinates.size < keys.size) {
            val perimeter = buildList {
                for (q in -ring..ring) for (r in -ring..ring) {
                    if (maxOf(abs(q), abs(r), abs(q + r)) == ring) add(q to r)
                }
            }.sortedBy { (q, r) ->
                val angle = atan2(r * sqrt(3f) / 2f, q + r / 2f) + PI.toFloat() / 2f
                (angle + 2 * PI.toFloat()) % (2 * PI.toFloat())
            }
            val remaining = minOf(keys.size - coordinates.size, perimeter.size)
            // A partially filled ring is distributed around the circle, rather than on one side.
            repeat(remaining) { coordinates += perimeter[it * perimeter.size / remaining] }
            ring++
        }
        return keys.mapIndexed { index, key ->
            val (q, r) = coordinates[index]
            HoneycombCell(key, q, r, (q + r / 2f) * PITCH, r * PITCH * sqrt(3f) / 2f)
        }
    }

    fun project(cells: List<HoneycombCell>, width: Float, height: Float, panX: Float, panY: Float,
        zoom: Float, selected: String? = null, selection: Float = 0f): List<HoneycombCircle> {
        val lens = min(width, height).coerceAtLeast(48f) * .65f
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val circles = cells.map { cell ->
            val x = cell.x * z + panX; val y = cell.y * z + panY
            val distance = hypot(x, y)
            val t = distance / lens
            val compressed = .82f * distance + .18f * lens * atan(t)
            val factor = if (distance < .001f) 1f else compressed / distance
            val radius = (36f * z * (.82f + .28f / (1f + t * t))).coerceAtLeast(24f)
            HoneycombCircle(cell.key, width / 2f + x * factor, height / 2f + y * factor, radius)
        }
        val source = circles.firstOrNull { it.key == selected } ?: return circles
        return circles.map { circle ->
            if (circle.key == selected) circle.copy(radius = circle.radius * (1f + .055f * selection))
            else {
                val dx = circle.x - source.x; val dy = circle.y - source.y
                val distance = hypot(dx, dy).coerceAtLeast(.001f)
                val shift = 6f * z * selection * (1f - distance / (PITCH * z * 2f)).coerceIn(0f, 1f)
                circle.copy(x = circle.x + dx / distance * shift, y = circle.y + dy / distance * shift)
            }
        }
    }

    /** Inverse of the radial projection, for keeping a pinch's focal point under the fingers. */
    fun unproject(x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> {
        val dx = x - width / 2f; val dy = y - height / 2f
        val distance = hypot(dx, dy)
        if (distance < .001f) return 0f to 0f
        val lens = min(width, height).coerceAtLeast(48f) * .65f
        var raw = distance
        repeat(5) {
            val t = raw / lens
            raw -= (.82f * raw + .18f * lens * atan(t) - distance) / (.82f + .18f / (1f + t * t))
        }
        return dx * raw / distance to dy * raw / distance
    }

    fun hit(circles: List<HoneycombCircle>, x: Float, y: Float): String? = circles.filter {
        hypot(x - it.x, y - it.y) <= it.radius
    }.minByOrNull { hypot(x - it.x, y - it.y) }?.key

    fun clampPan(cells: List<HoneycombCell>, width: Float, height: Float, zoom: Float,
        x: Float, y: Float): Pair<Float, Float> {
        if (cells.isEmpty()) return 0f to 0f
        val minX = -cells.maxOf { it.x } * zoom - width * .28f
        val maxX = -cells.minOf { it.x } * zoom + width * .28f
        val minY = -cells.maxOf { it.y } * zoom - height * .28f
        val maxY = -cells.minOf { it.y } * zoom + height * .28f
        return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
    }
}
