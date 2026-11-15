package com.example.zhinote.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiConfigs(val profiles: List<AiProfile> = emptyList(), val defaultId: String? = null) {
    val selected get() = profiles.firstOrNull { it.id == defaultId } ?: profiles.firstOrNull()
}

class AiConfigStore(context: Context, filename: String = "ai-profiles.enc") {
    private val file = AtomicFile(File(context.noBackupFilesDir, filename))
    private val mutex = Mutex()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "zhinote.ai.v2"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun read(): AiConfigs = withContext(Dispatchers.IO) { mutex.withLock {
        if (!file.baseFile.exists()) return@withLock AiConfigs()
        try {
            val bytes = file.readFully()
            require(bytes.size in 30..200000 && bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            val root = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
            val array = root.getJSONArray("profiles")
            val profiles = List(array.length()) { i ->
                val p = array.getJSONObject(i); val m = p.getJSONArray("models")
                AiProfile(p.getString("id"), p.getString("name"), p.getString("url"), p.getString("key"),
                    List(m.length()) { m.getString(it) }, p.getString("model")).also { it.validate() }
            }
            AiConfigs(profiles, root.optString("defaultId").ifBlank { null })
        } catch (_: Exception) { error("AI 配置无法解密。请重新填写并保存配置；笔记和待办不受影响。") }
    } }
    suspend fun write(configs: AiConfigs) = withContext(Dispatchers.IO) { mutex.withLock {
        require(configs.profiles.size <= 30)
        configs.profiles.forEach { it.validate() }
        val array = JSONArray(configs.profiles.map { p -> JSONObject().put("id", p.id).put("name", p.name)
            .put("url", p.baseUrl).put("key", p.apiKey).put("models", JSONArray(p.models)).put("model", p.model) })
        val plain = JSONObject().put("profiles", array).put("defaultId", configs.defaultId ?: "").toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    } }
}
