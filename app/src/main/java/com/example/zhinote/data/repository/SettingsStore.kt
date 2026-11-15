package com.example.zhinote.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore("settings")
data class UserSettings(
    val theme: String = "SYSTEM", val floating: Boolean = true, val collisions: Boolean = true,
    val ripple: Boolean = true, val dissolve: Boolean = true, val dragging: Boolean = true,
    val reducedMotion: Boolean = false, val performance: Boolean = false, val intensity: Int = 1,
    val reminders: Boolean = true, val maxDrops: Int = 40,
)
class SettingsStore(context: Context) {
    private val store = context.settingsDataStore
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        fun b(key: String, default: Boolean = true) = p[booleanPreferencesKey(key)] ?: default
        UserSettings(p[stringPreferencesKey("theme")] ?: "SYSTEM", b("floating"), b("collisions"),
            b("ripple"), b("dissolve"), b("dragging"), b("reduced", false), b("performance", false),
            (p[intPreferencesKey("intensity")] ?: 1).coerceIn(0, 2), b("reminders"), (p[intPreferencesKey("maxDrops")] ?: 40).coerceIn(20, 60))
    }
    suspend fun boolean(key: String, value: Boolean) { store.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun theme(value: String) { require(value in setOf("SYSTEM", "LIGHT", "DARK")); store.edit { it[stringPreferencesKey("theme")] = value } }
    suspend fun intensity(value: Int) { store.edit { it[intPreferencesKey("intensity")] = value.coerceIn(0, 2) } }
    suspend fun maxDrops(value: Int) { require(value in setOf(20, 40, 60)); store.edit { it[intPreferencesKey("maxDrops")] = value } }
}
