package com.example.zhinote.ai

import org.json.JSONObject
import java.net.URI
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

// Intentionally not a data class: a profile must never include its credential in toString().
class AiProfile(val id: String = UUID.randomUUID().toString(), val name: String,
    val baseUrl: String, val apiKey: String, val models: List<String>, val model: String) {
    override fun toString() = "AiProfile($id)"
    fun validate() {
        require(name.isNotBlank() && name.length <= 100) { "请填写服务商名称" }
        endpoint(baseUrl)
        require(apiKey.isNotBlank() && apiKey.length <= 4096 && !apiKey.any { it == '\r' || it == '\n' }) { "请填写有效的 API Key" }
        require(models.isNotEmpty() && models.size <= 30 && models.all { it.isNotBlank() && it.length <= 200 } && model in models) { "请填写并选择模型" }
    }
    companion object {
        fun endpoint(base: String): URI {
            val uri = try { URI(base.trim()) } catch (_: Exception) { error("Base URL 格式不正确") }
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.scheme == "https" || (uri.scheme == "http" && uri.host in listOf("localhost", "127.0.0.1", "::1")))) {
                "请使用 HTTPS Base URL（例如 https://api.deepseek.com/v1）"
            }
            return URI(base.trim().trimEnd('/') + if (uri.path.endsWith("/chat/completions")) "" else "/chat/completions")
        }
    }
}

data class AiDraft(val type: String, val title: String, val content: String, val summary: String = "",
    val tags: String = "", val priority: Int = 1, val dueDate: Long? = null, val reminderTime: Long? = null,
    val id: String = UUID.randomUUID().toString()) {
    fun validate() {
        require(type in listOf("NOTE", "TODO") && (title.isNotBlank() || content.isNotBlank())) { "AI 未返回有效内容，请重试" }
        require(title.length <= 500 && content.length <= 200000 && summary.length <= 10000 && tags.length <= 2000 && priority in 0..2) { "AI 返回的字段超出允许范围" }
        require(dueDate == null || dueDate >= 0) { "截止时间无效" }
        require(reminderTime == null || (reminderTime >= 0 && (dueDate == null || reminderTime <= dueDate))) { "提醒时间应早于截止时间" }
    }
    companion object {
        fun parse(text: String, requestedType: String, zone: ZoneId): AiDraft {
            val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(clean)
            fun time(key: String): Long? {
                if (!json.has(key) || json.isNull(key) || json.optString(key).isBlank()) return null
                val value = json.getString(key)
                return try { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
                catch (_: java.time.format.DateTimeParseException) { LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli() }
            }
            val type = json.getString("type").uppercase()
            require(requestedType == "AUTO" || type == requestedType) { "AI 返回类型与所选模式不一致" }
            val tags = if (json.opt("tags") is org.json.JSONArray) {
                val array = json.getJSONArray("tags"); List(array.length()) { array.getString(it) }.joinToString(",")
            } else json.optString("tags", "")
            return AiDraft(type, json.optString("title", ""), json.optString("content", ""), json.optString("summary", ""),
                tags, json.optInt("priority", 1), time("dueDate"), time("reminderTime")).also { it.validate() }
        }
    }
}
