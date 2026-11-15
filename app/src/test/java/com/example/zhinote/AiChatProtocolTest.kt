package com.example.zhinote

import com.example.zhinote.ai.AiChatProtocol
import com.example.zhinote.ai.AiException
import com.example.zhinote.ai.AiProfile
import org.junit.Assert.*
import org.junit.Test

class AiChatProtocolTest {
    @Test fun reasoningBudgetExhaustionAndNullContentAreNotReportedAsSuccess() {
        val error = assertThrows(AiException::class.java) {
            AiChatProtocol.answer("""{"choices":[{"finish_reason":"length","message":{"content":null,"reasoning_content":"private reasoning"}}]}""")
        }
        assertTrue(error.message!!.contains("输出额度"))
        assertFalse(error.message!!.contains("private reasoning"))
        assertThrows(AiException::class.java) {
            AiChatProtocol.answer("""{"choices":[{"finish_reason":"length","message":{"content":"{\"type\":"}}]}""")
        }
    }

    @Test fun thinkingOnlyAndEmptyRepliesNeverBecomeNoteContent() {
        val thinking = assertThrows(AiException::class.java) {
            AiChatProtocol.answer("""{"choices":[{"message":{"content":"","reasoning_content":"secret thought"}}]}""")
        }
        assertTrue(thinking.message!!.contains("思考内容"))
        assertFalse(thinking.message!!.contains("secret thought"))
        assertThrows(AiException::class.java) {
            AiChatProtocol.answer("""{"choices":[{"message":{"content":null}}]}""")
        }
        assertThrows(AiException::class.java) {
            AiChatProtocol.answer("""{"choices":[{"message":{"content":"  "}}]}""")
        }
    }

    @Test fun stringAndTextBlockRepliesAreParsedWithoutReasoning() {
        assertEquals("OK", AiChatProtocol.answer("""{"choices":[{"finish_reason":"stop","message":{"content":"OK","reasoning_content":"hidden"}}]}"""))
        assertEquals("hello world", AiChatProtocol.answer("""{"choices":[{"message":{"content":[{"type":"text","text":"hello "},{"type":"thinking","text":"hidden"},{"type":"text","text":"world"}]}}]}"""))
    }

    @Test fun refusalAndProviderErrorsDoNotExposeRemoteDetails() {
        val errors = listOf(
            """{"error":{"message":"credential secret","code":"x"}}""",
            """{"choices":[{"message":{"content":null,"refusal":"secret response"}}]}""",
            """{"choices":[{"finish_reason":"content_filter","message":{"content":"secret response"}}]}"""
        )
        errors.forEach { body ->
            val error = assertThrows(AiException::class.java) { AiChatProtocol.answer(body) }
            assertFalse(error.message!!.contains("secret"))
        }
    }
}
