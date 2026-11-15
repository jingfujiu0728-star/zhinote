package com.example.zhinote.ai

import org.json.JSONArray
import org.json.JSONObject

internal object AiChatProtocol {
    // Reasoning models spend the completion budget on thinking before producing content.
    const val CONNECTION_TOKENS = 4096
    const val CREATION_TOKENS = 8192

    fun request(profile: AiProfile, system: String, input: String, tokens: Int): JSONObject =
        JSONObject().put("model", profile.model).put("stream", false).put("max_tokens", tokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", input)))

    fun answer(body: String): String {
        val root = JSONObject(body)
        if (root.optJSONObject("error") != null) throw AiException("AI 服务返回错误，请检查模型、权限与额度。")
        val choice = root.getJSONArray("choices").getJSONObject(0)
        val message = choice.getJSONObject("message")
        when (choice.optString("finish_reason")) {
            "length", "max_tokens" -> throw AiException("模型耗尽输出额度，正文未生成完整。请重试或切换更快的模型。")
            "content_filter" -> throw AiException("服务未返回可用正文，请调整输入后重试。")
        }
        if ((message.opt("refusal") as? String)?.isNotBlank() == true) {
            throw AiException("模型拒绝了本次请求，请调整输入后重试。")
        }
        val content = when (val value = message.opt("content")) {
            is String -> value
            is JSONArray -> buildString {
                for (index in 0 until value.length()) {
                    val part = value.optJSONObject(index) ?: continue
                    if (part.optString("type") in listOf("text", "output_text")) {
                        (part.opt("text") as? String)?.let { append(it) }
                    }
                }
            }
            else -> ""
        }
        if (content.isNotBlank()) return content
        val reasoningTokens = root.optJSONObject("usage")?.optJSONObject("completion_tokens_details")
            ?.optInt("reasoning_tokens", 0) ?: 0
        if ((message.opt("reasoning_content") as? String)?.isNotBlank() == true || reasoningTokens > 0) {
            throw AiException("模型只返回了思考内容，尚未生成正文。请重试或切换更快的模型。")
        }
        throw AiException("服务返回了空正文，请检查模型兼容性或重试。")
    }
}
