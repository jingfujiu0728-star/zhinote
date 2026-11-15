package com.example.zhinote

import com.example.zhinote.data.local.TodoEntity
import com.example.zhinote.domain.TodoRules
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TodoRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun time(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    @Test fun ordinaryCompletionDoesNotCreateAnotherTodo() { assertNull(TodoRules.nextOccurrence(TodoEntity(), 0, zone)) }
    @Test fun monthlyRepeatClampsMonthEndAndRetainsHistory() {
        val todo = TodoEntity(title = "账单", dueDate = time("2026-01-31T09:00:00"), reminderTime = time("2026-01-31T08:00:00"), repeatRule = "MONTHLY")
        val next = TodoRules.nextOccurrence(todo, time("2026-02-01T00:00:00"), zone)!!
        assertEquals(time("2026-02-28T09:00:00"), next.dueDate)
        assertEquals(time("2026-02-28T08:00:00"), next.reminderTime)
        assertFalse(next.isCompleted); assertNotEquals(todo.id, next.id); assertEquals(todo.id, next.recurrenceParentId)
    }
    @Test fun overdueRecurrenceAdvancesBeyondNow() {
        val next = TodoRules.nextOccurrence(TodoEntity(dueDate = time("2026-11-01T10:00:00"), repeatRule = "DAILY"), time("2026-11-15T11:00:00"), zone)!!
        assertEquals(time("2026-11-16T10:00:00"), next.dueDate)
    }
}
