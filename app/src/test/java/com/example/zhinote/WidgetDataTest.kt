package com.example.zhinote

import com.example.zhinote.data.local.TodoEntity
import com.example.zhinote.widgets.WidgetData
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WidgetDataTest {
    @Test fun todayCountsRespectLocalDayDeletedItemsAndCompletion() {
        val zone = ZoneId.of("Asia/Shanghai"); val date = LocalDate.of(2026, 11, 15)
        val time = date.atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val first = TodoEntity(id = "a", title = "today", dueDate = time)
        val all = listOf(first, first.copy(id = "b", isCompleted = true, completedAt = time),
            first.copy(id = "c", deletedAt = time), first.copy(id = "d", dueDate = time + 86400000))
        val counts = WidgetData.today(all, date, zone)
        assertEquals(2, counts.total); assertEquals(1, counts.completed); assertEquals(1, counts.remaining)
        assertEquals(.5f, counts.progress)
        assertEquals(0f, WidgetData.today(emptyList(), date, zone).progress)
        assertEquals(3, WidgetData.recent(all, 5).size)
    }
}
