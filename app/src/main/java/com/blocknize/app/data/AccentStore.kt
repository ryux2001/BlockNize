package com.blocknize.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

class AccentStore(private val context: Context) {
    private val key = intPreferencesKey("accent_argb")
    private val widgetOpacityKey = intPreferencesKey("widget_background_opacity")
    val widgetOpacity: Flow<Int> = context.appearanceDataStore.data.map { preferences ->
        (preferences[widgetOpacityKey] ?: DEFAULT_WIDGET_OPACITY).coerceIn(0, 100)
    }.catch { error -> if (error is IOException) emit(DEFAULT_WIDGET_OPACITY) else throw error }
    val accent: Flow<Int> = context.appearanceDataStore.data.map { preferences ->
        preferences[key] ?: DEFAULT_ACCENT
    }.catch { error -> if (error is IOException) emit(DEFAULT_ACCENT) else throw error }

    suspend fun setAccent(argb: Int) {
        context.appearanceDataStore.edit { it[key] = argb or (0xFF shl 24) }
    }

    suspend fun setWidgetOpacity(percent: Int) {
        require(percent in 0..100) { "La opacidad debe estar entre 0 y 100 %." }
        context.appearanceDataStore.edit { it[widgetOpacityKey] = percent }
    }

    companion object {
        const val DEFAULT_ACCENT: Int = 0xFFA78BFA.toInt()
        const val DEFAULT_WIDGET_OPACITY: Int = 30
    }
}

fun widgetBackgroundArgb(opacityPercent: Int): Int =
    ((opacityPercent.coerceIn(0, 100) * 255 + 50) / 100) shl 24

fun parseAccentHex(input: String): Int? {
    val value = input.trim().removePrefix("#")
    if (!value.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return (0xFF000000L or value.toLong(16)).toInt()
}
