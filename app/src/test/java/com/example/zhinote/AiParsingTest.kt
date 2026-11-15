package com.example.zhinote

import com.example.zhinote.ai.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AiParsingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    @Test fun structuredReminderTagsAndModeAreValidated() {
        val json = """{"type":"TODO","title":"提交实验报告","content":"","priority":2,"tags":["学习"],"dueDate":"2026-11-16T15:00:00+08:00","reminderTime":"2026-11-16T14:30:00+08:00"}"""
        val draft = AiDraft.parse(json, "AUTO", zone)
        assertEquals(ZonedDateTime.parse("2026-11-16T15:00:00+08:00").toInstant().toEpochMilli(), draft.dueDate)
        assertEquals(30 * 60 * 1000L, draft.dueDate!! - draft.reminderTime!!)
        assertEquals("学习", draft.tags)
        assertThrows(IllegalArgumentException::class.java) { AiDraft.parse(json, "NOTE", zone) }
        assertThrows(IllegalArgumentException::class.java) { draft.copy(reminderTime = draft.dueDate!! + 1).validate() }
    }
    @Test fun unsafeUrlsAndInvalidModelAreRejected() {
        listOf("http://api.example.com", "https://user:secret@api.example.com", "https://api.example.com?key=x").forEach {
            assertThrows(IllegalArgumentException::class.java) { AiProfile.endpoint(it) }
        }
        assertEquals("https://api.deepseek.com/v1/chat/completions", AiProfile.endpoint("https://api.deepseek.com/v1/").toString())
        val p = AiProfile(name = "service", baseUrl = "https://example.com/v1", apiKey = "private-test-value", models = listOf("m"), model = "other")
        assertThrows(IllegalArgumentException::class.java) { p.validate() }
        assertFalse(p.toString().contains(p.apiKey))
    }
}
