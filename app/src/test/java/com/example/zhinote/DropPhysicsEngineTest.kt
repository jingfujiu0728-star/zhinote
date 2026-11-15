package com.example.zhinote

import com.example.zhinote.drop.engine.*
import org.junit.Assert.*
import org.junit.Test

class DropPhysicsEngineTest {
    @Test fun pressureAndBoundariesAtAllRequestedCounts() {
        for (count in listOf(10, 20, 40, 60)) {
            val engine = DropPhysicsEngine()
            engine.reset(List(count) { DropSpec("$it", 24f) }, 400f, 650f)
            val start = System.nanoTime()
            repeat(1200) { frame ->
                if (frame % 120 == 0) engine.select("${frame / 120 % count}")
                engine.advance(if (frame % 50 == 0) 0.5f else 1f / 120f)
                engine.positions().forEach { (x, y) ->
                    assertTrue(x.isFinite() && y.isFinite()); assertTrue(x in 24f..376f && y in 24f..626f)
                }
            }
            println("Physics $count drops: 1200 frames in ${(System.nanoTime() - start) / 1_000_000.0} ms (JVM, not device FPS)")
        }
    }
    @Test fun dragIsClampedAndSavedInNormalizedCoordinates() {
        val engine = DropPhysicsEngine()
        engine.reset(listOf(DropSpec("note", 30f)), 300f, 500f)
        engine.drag("note", -999f, 999f)
        val position = engine.release("note")!!
        assertTrue(position.first in 0f..1f && position.second in 0f..1f)
        engine.reset(listOf(DropSpec("note", 30f, position.first, position.second)), 600f, 1000f)
        repeat(600) { engine.advance(1f / 120f, floating = false) }
        assertEquals(position.first * 600, engine.positions().single().first, 6f)
    }
    @Test fun goldenClusterContainsEveryDropWithoutRows() {
        val engine = DropPhysicsEngine()
        engine.reset(List(60) { DropSpec("star-$it", 32f) }, 400f, 650f)
        repeat(1200) { engine.advance(1f / 120f) }
        val bounds = engine.clusterBounds(); val frame = engine.snapshot()
        for (i in 0 until 60) {
            val q = ((frame[i * 3] - 200) / (bounds.first - frame[i * 3 + 2] - 2)).let { it * it } +
                ((frame[i * 3 + 1] - 325) / (bounds.second - frame[i * 3 + 2] - 2)).let { it * it }
            assertTrue(q <= 1.001f)
        }
        assertTrue(engine.positions().map { (it.second / 10).toInt() }.distinct().size > 12)
    }
    @Test fun overlappingCentersStayFiniteAndSelectionDoesNotRemoveNotes() {
        val engine = DropPhysicsEngine()
        engine.reset(List(20) { DropSpec("$it", 30f, .5f, .5f) }, 400f, 600f)
        engine.select("0")
        repeat(1000) { engine.advance(1f / 60f) }
        assertEquals(20, engine.positions().size)
        assertTrue(engine.snapshot().all { it.isFinite() })
    }
    @Test fun constellationAvoidsSubstantialOverlapOnCompactPhoneSpace() {
        for (count in listOf(10, 20, 40, 60)) {
            val engine = DropPhysicsEngine()
            engine.reset(List(count) { DropSpec("$it", if (it % 4 == 0) 44f else 32f) }, 360f, 460f)
            repeat(1200) { engine.advance(1f / 120f) }
            val frame = engine.snapshot()
            var substantial = 0
            for (i in 0 until count) for (j in i + 1 until count) {
                val dx = frame[i * 3] - frame[j * 3]; val dy = frame[i * 3 + 1] - frame[j * 3 + 1]
                val radii = frame[i * 3 + 2] + frame[j * 3 + 2]
                if (dx * dx + dy * dy < radii * radii * .81f) substantial++
            }
            assertEquals("Substantial overlaps at $count drops", 0, substantial)
        }
    }
}
