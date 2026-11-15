package com.example.zhinote

import androidx.test.core.app.ApplicationProvider
import com.example.zhinote.ai.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class AiDeviceTest {
    private val app get() = ApplicationProvider.getApplicationContext<ZhiNoteApplication>()
    @Test fun connectionTestLeavesBudgetForTheFinalReply() = runBlocking<Unit> {
        val failure = AtomicReference<Throwable?>()
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).apply { soTimeout = 10000 }
        val profile = AiProfile(name = "reasoning test", baseUrl = "http://127.0.0.1:${server.localPort}/v1",
            apiKey = java.util.UUID.randomUUID().toString(), models = listOf("reasoning-model"), model = "reasoning-model")
        val thread = Thread {
            try { server.accept().use { socket ->
                val reader = socket.getInputStream().buffered()
                fun line(): String {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (true) { val c = reader.read(); if (c < 0 || c == 10) break; if (c != 13) bytes.write(c) }
                    return bytes.toString("UTF-8")
                }
                line()
                var length = 0
                while (true) { val header = line(); if (header.isBlank()) break
                    if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                }
                val input = ByteArray(length); var read = 0
                while (read < length) { val n = reader.read(input, read, length - read); if (n < 0) break; read += n }
                val budget = JSONObject(String(input, Charsets.UTF_8)).getInt("max_tokens")
                // This provider spends 64 completion tokens reasoning before its answer.
                val response = if (budget < 64)
                    """{"choices":[{"finish_reason":"length","message":{"content":"","reasoning_content":"thinking"}}]}"""
                else """{"choices":[{"finish_reason":"stop","message":{"content":"OK","reasoning_content":"thinking"}}]}"""
                val bytes = response.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
            } } catch (e: Throwable) { failure.set(e) }
        }.apply { start() }
        try { assertTrue(AiClient().test(profile).startsWith("连接成功")) }
        finally { server.close(); thread.join(5000) }
        failure.get()?.let { throw it }
    }
    @Test fun configurationIsEncryptedAndSurvivesReopening() = runBlocking {
        val filename = "ai-test-${System.nanoTime()}.enc"
        val key = java.util.UUID.randomUUID().toString()
        val profile = AiProfile(name = "test", baseUrl = "https://example.com/v1", apiKey = key, models = listOf("m", "m2"), model = "m2")
        val store = AiConfigStore(app, filename)
        try {
            store.write(AiConfigs(listOf(profile), profile.id))
            assertFalse(java.io.File(app.noBackupFilesDir, filename).readText().contains(key))
            val reopened = AiConfigStore(app, filename).read().selected!!
            assertEquals(key, reopened.apiKey); assertEquals("m2", reopened.model)
            store.write(AiConfigs()); assertTrue(store.read().profiles.isEmpty())
        } finally { java.io.File(app.noBackupFilesDir, filename).delete() }
    }
    @Test fun compatibleTransportPreviewConfirmationErrorAndCancellation() = runBlocking<Unit> {
        val failure = AtomicReference<Throwable?>()
        val server = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        val body = """{"type":"NOTE","title":"AI设备测试-${System.nanoTime()}","content":"真实传输测试\n多行 🌿","summary":"测试摘要","tags":["测试"]}"""
        val title = JSONObject(body).getString("title")
        val profile = AiProfile(name = "local test", baseUrl = "http://127.0.0.1:${server.localPort}/v1", apiKey = java.util.UUID.randomUUID().toString(), models = listOf("test-model"), model = "test-model")
        val thread = Thread {
            try {
                repeat(3) { request -> server.accept().use { socket ->
                    val reader = socket.getInputStream().buffered()
                    fun line(): String { val bytes = java.io.ByteArrayOutputStream()
                        while (true) { val c = reader.read(); if (c < 0 || c == 10) break; if (c != 13) bytes.write(c) }
                        return bytes.toString("UTF-8")
                    }
                    assertEquals("POST /v1/chat/completions HTTP/1.1", line())
                    var length = 0; var authorized = false
                    while (true) { val line = line(); if (line.isBlank()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        if (line.startsWith("Authorization:", true)) authorized = line.substringAfter(':').trim() == "Bearer ${profile.apiKey}"
                    }
                    assertTrue("Expected authorization header", authorized)
                    val bytesIn = ByteArray(length); var read = 0
                    while (read < length) { val n = reader.read(bytesIn, read, length - read); if (n < 0) break; read += n }
                    assertEquals("test-model", JSONObject(String(bytesIn, Charsets.UTF_8)).getString("model"))
                    if (request == 2) { Thread.sleep(3000) }
                    else {
                        val response = if (request == 0) JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", body)))).toString() else "credential must never be surfaced"
                        val bytes = response.toByteArray()
                        socket.getOutputStream().write("HTTP/1.1 ${if (request == 0) "200 OK" else "401 Unauthorized"}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                    }
                } }
            } catch (e: Throwable) { failure.set(e) }
        }.apply { start() }
        try {
            val draft = AiClient().create(profile, "test input", "NOTE")
            assertEquals("测试摘要", draft.summary)
            assertFalse(app.database.dao().allNotes().any { it.title == title })
            val vm = com.example.zhinote.ui.AiViewModel(app)
            withContext(Dispatchers.Main) { vm.preview.value = draft; vm.confirm() }
            withTimeout(10000) { while (vm.busy.value) delay(20) }
            assertEquals(draft.content, app.database.dao().note(draft.id)?.content)
            assertEquals(draft.summary, app.database.dao().note(draft.id)?.summary)
            try { AiClient().test(profile); fail("Expected authentication error") }
            catch (e: AiException) { assertTrue(e.message!!.contains("401")); assertFalse(e.message!!.contains(profile.apiKey)) }
            val job = async { AiClient().test(profile) }; delay(300); job.cancelAndJoin(); assertTrue(job.isCancelled)
        } finally {
            thread.join(5000); server.close()
            app.database.dao().allNotes().filter { it.title == title }.forEach { app.repository.trash(it.id, "NOTE"); app.repository.purge(it.id, "NOTE") }
        }
        failure.get()?.let { throw it }
    }
}
