package com.example.zhinote

import com.example.zhinote.drop.engine.HoneycombLayout
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class HoneycombLayoutTest {
    @Test fun smallThirtyAndHundredItemsUseDistinctHexagonalCoordinates() {
        for (count in listOf(1, 3, 7, 30, 100, 300)) {
            val cells = HoneycombLayout.cells(List(count) { "item-$it" })
            assertEquals(count, cells.size)
            assertEquals(count, cells.map { it.q to it.r }.toSet().size)
            cells.forEach {
                assertEquals((it.q + it.r / 2f) * HoneycombLayout.PITCH, it.x, .001f)
                assertEquals(it.r * HoneycombLayout.PITCH * sqrt(3f) / 2f, it.y, .001f)
            }
        }
        val ring = HoneycombLayout.cells(List(7) { "$it" }).drop(1)
        ring.forEach { assertEquals(HoneycombLayout.PITCH, hypot(it.x, it.y), .001f) }
    }
    @Test fun projectedIconsRemainTouchableSeparatedAndHitAtTheirDrawnCenters() {
        val cells = HoneycombLayout.cells(List(100) { "$it" })
        for (width in listOf(280f, 360f, 600f)) for (zoom in listOf(.8f, 1f, 2f)) {
            val frame = HoneycombLayout.project(cells, width, 420f, -120f, 160f, zoom, "8", 1f)
            frame.forEach { circle ->
                assertTrue(circle.radius >= 24f)
                assertTrue(circle.x.isFinite() && circle.y.isFinite())
                assertEquals(circle.key, HoneycombLayout.hit(frame, circle.x, circle.y))
            }
            for (i in frame.indices) for (j in i + 1 until frame.size) {
                val a = frame[i]; val b = frame[j]
                assertTrue("Overlapping circles $i/$j at width=$width zoom=$zoom",
                    hypot(a.x - b.x, a.y - b.y) >= a.radius + b.radius - .1f)
            }
        }
    }
    @Test fun radialProjectionHasAnAccurateInverseAndPanReachesEveryIcon() {
        val cells = HoneycombLayout.cells(List(100) { "$it" })
        val frame = HoneycombLayout.project(cells, 360f, 500f, 0f, 0f, 1f)
        frame.forEachIndexed { index, circle ->
            val raw = HoneycombLayout.unproject(circle.x, circle.y, 360f, 500f)
            assertEquals(cells[index].x, raw.first, .01f)
            assertEquals(cells[index].y, raw.second, .01f)
            val pan = HoneycombLayout.clampPan(cells, 360f, 500f, 1f, -cells[index].x, -cells[index].y)
            val centered = HoneycombLayout.project(cells, 360f, 500f, pan.first, pan.second, 1f)[index]
            assertEquals(180f, centered.x, .01f); assertEquals(250f, centered.y, .01f)
        }
    }
}
