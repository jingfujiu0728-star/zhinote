package com.example.zhinote.data.backup

import com.example.zhinote.data.local.*
import com.example.zhinote.domain.TodoRules
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(val notes: List<NoteEntity>, val todos: List<TodoEntity>,
    val categories: List<CategoryEntity>, val layouts: List<DropLayoutEntity>)

object BackupCodec {
    const val VERSION = 2
    const val MAX_BYTES = 10 * 1024 * 1024
    private fun JSONObject.value(key: String, value: Any?) = put(key, value ?: JSONObject.NULL)
    fun encode(data: BackupData): String {
        val notes = data.notes.map { n -> JSONObject().put("id", n.id).put("title", n.title).put("content", n.content)
            .put("color", n.color).value("categoryId", n.categoryId).put("tags", n.tags).put("isFavorite", n.isFavorite)
            .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt).value("deletedAt", n.deletedAt).put("summary", n.summary) }
        val todos = data.todos.map { t -> JSONObject().put("id", t.id).put("title", t.title).put("description", t.description)
            .put("priority", t.priority).value("dueDate", t.dueDate).value("reminderTime", t.reminderTime)
            .put("isCompleted", t.isCompleted).value("completedAt", t.completedAt).put("createdAt", t.createdAt)
            .put("updatedAt", t.updatedAt).put("repeatRule", t.repeatRule).value("deletedAt", t.deletedAt)
            .value("categoryId", t.categoryId).put("tags", t.tags).value("recurrenceParentId", t.recurrenceParentId)
            .value("reminderDeliveredAt", t.reminderDeliveredAt) }
        val categories = data.categories.map { c -> JSONObject().put("id", c.id).put("name", c.name).put("color", c.color).put("createdAt", c.createdAt) }
        val layouts = data.layouts.map { l -> JSONObject().put("itemId", l.itemId).put("itemType", l.itemType)
            .put("normalizedX", l.normalizedX).put("normalizedY", l.normalizedY).put("size", l.size)
            .value("colorOverride", l.colorOverride).put("updatedAt", l.updatedAt) }
        return JSONObject().put("format", "ZhiNote").put("version", VERSION).put("exportedAt", System.currentTimeMillis())
            .put("notes", JSONArray(notes)).put("todos", JSONArray(todos)).put("categories", JSONArray(categories))
            .put("layouts", JSONArray(layouts)).toString(2)
    }
    fun decode(text: String): BackupData {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 10 MB" }
        val root = JSONObject(text)
        require(root.getString("format") == "ZhiNote" && root.get("version") is Number && root.getDouble("version") in listOf(1.0, VERSION.toDouble())) { "不支持的备份格式或版本" }
        fun array(key: String): List<JSONObject> {
            val array = root.getJSONArray(key)
            require(array.length() <= 20000) { "条目数量过多" }
            return List(array.length()) { array.getJSONObject(it) }
        }
        fun JSONObject.string(key: String, max: Int = 200000): String {
            val value = get(key); require(value is String && value.length <= max) { "无效字段：$key" }; return value
        }
        fun JSONObject.id(key: String = "id") = string(key, 128).also { require(it.isNotBlank()) }
        fun JSONObject.number(key: String): Long {
            val value = get(key); require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble()) { "无效整数：$key" }; return value.toLong().also { require(it >= 0) }
        }
        fun JSONObject.optionalTime(key: String): Long? = if (isNull(key)) null else number(key)
        fun JSONObject.optionalId(key: String): String? = if (isNull(key)) null else id(key)
        fun JSONObject.bool(key: String): Boolean { val value = get(key); require(value is Boolean); return value }
        val notes = array("notes").map { n -> with(n) {
            NoteEntity(id(), string("title", 500), string("content"), number("color").also { require(it in 0..6) }.toInt(), optionalId("categoryId"), string("tags", 2000),
                bool("isFavorite"), number("createdAt"), number("updatedAt"), optionalTime("deletedAt"), if (has("summary")) string("summary", 10000) else "")
        } }
        val todos = array("todos").map { t -> with(t) {
            TodoEntity(id(), string("title", 500), string("description"), number("priority").also { require(it in 0..2) }.toInt(), optionalTime("dueDate"), optionalTime("reminderTime"),
                bool("isCompleted"), optionalTime("completedAt"), number("createdAt"), number("updatedAt"), string("repeatRule", 16),
                optionalTime("deletedAt"), optionalId("categoryId"), string("tags", 2000), optionalId("recurrenceParentId"), optionalTime("reminderDeliveredAt"))
        } }
        val categories = array("categories").map { c -> with(c) { CategoryEntity(id(), string("name", 100), number("color").also { require(it in 0..6) }.toInt(), number("createdAt")) } }
        val layouts = array("layouts").map { l -> with(l) { DropLayoutEntity(id("itemId"), string("itemType", 4),
            getDouble("normalizedX").toFloat(), getDouble("normalizedY").toFloat(), getDouble("size").toFloat(),
            if (isNull("colorOverride")) null else getInt("colorOverride"), number("updatedAt")) } }
        fun <T> unique(items: List<T>, key: (T) -> String) { require(items.map(key).toSet().size == items.size) { "备份包含重复条目" } }
        unique(notes) { it.id }; unique(todos) { it.id }; unique(categories) { it.id }; unique(categories) { it.name }
        unique(layouts) { "${it.itemType}:${it.itemId}" }
        val categoryIds = categories.map { it.id }.toSet()
        require(categories.all { it.name.isNotBlank() && it.color in 0..6 })
        require(notes.all { it.color in 0..6 && (it.categoryId == null || it.categoryId in categoryIds) })
        require(todos.all { it.priority in 0..2 && it.repeatRule in TodoRules.repeatRules &&
            (it.categoryId == null || it.categoryId in categoryIds) && it.isCompleted == (it.completedAt != null) })
        val noteIds = notes.map { it.id }.toSet(); val todoIds = todos.map { it.id }.toSet()
        require(layouts.all { it.normalizedX.isFinite() && it.normalizedY.isFinite() && it.normalizedX in 0f..1f && it.normalizedY in 0f..1f &&
            it.size.isFinite() && it.size in 42f..100f && (it.colorOverride == null || it.colorOverride in 0..6) &&
            ((it.itemType == "NOTE" && it.itemId in noteIds) || (it.itemType == "TODO" && it.itemId in todoIds)) }) { "备份布局数据无效" }
        return BackupData(notes, todos, categories, layouts)
    }
}
