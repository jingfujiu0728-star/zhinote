package com.example.zhinote.domain

import com.example.zhinote.data.local.TodoEntity
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object TodoRules {
    val repeatRules = setOf("NONE", "DAILY", "WEEKLY", "MONTHLY")
    fun nextOccurrence(todo: TodoEntity, now: Long, zone: ZoneId = ZoneId.systemDefault()): TodoEntity? {
        if (todo.repeatRule == "NONE") return null
        require(todo.repeatRule in repeatRules)
        fun advance(value: Long): Long {
            var date = Instant.ofEpochMilli(value).atZone(zone)
            do {
                date = when (todo.repeatRule) {
                    "DAILY" -> date.plusDays(1)
                    "WEEKLY" -> date.plusWeeks(1)
                    else -> date.plusMonths(1)
                }
            } while (date.toInstant().toEpochMilli() <= now)
            return date.toInstant().toEpochMilli()
        }
        val newDue = todo.dueDate?.let(::advance)
        val newReminder = todo.reminderTime?.let {
            if (todo.dueDate != null && newDue != null) it + (newDue - todo.dueDate) else advance(it)
        }
        return todo.copy(id = UUID.randomUUID().toString(), isCompleted = false, completedAt = null,
            dueDate = newDue, reminderTime = newReminder, createdAt = now, updatedAt = now,
            deletedAt = null, recurrenceParentId = todo.id, reminderDeliveredAt = null)
    }
}
