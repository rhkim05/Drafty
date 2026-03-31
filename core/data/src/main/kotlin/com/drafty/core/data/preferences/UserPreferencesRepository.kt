package com.drafty.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User preferences stored via Jetpack DataStore.
 * Provides Flow-based reads and suspend writes for app settings.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

    val penColor: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_PEN_COLOR] ?: DEFAULT_PEN_COLOR
    }

    val penThickness: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_PEN_THICKNESS] ?: DEFAULT_PEN_THICKNESS
    }

    val defaultTemplate: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TEMPLATE] ?: DEFAULT_TEMPLATE
    }

    val palmRejectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PALM_REJECTION] ?: true
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: THEME_SYSTEM
    }

    suspend fun setPenColor(color: Long) {
        context.dataStore.edit { it[KEY_PEN_COLOR] = color }
    }

    suspend fun setPenThickness(thickness: Float) {
        context.dataStore.edit { it[KEY_PEN_THICKNESS] = thickness }
    }

    suspend fun setDefaultTemplate(template: String) {
        context.dataStore.edit { it[KEY_DEFAULT_TEMPLATE] = template }
    }

    suspend fun setPalmRejectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PALM_REJECTION] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    companion object {
        private val KEY_PEN_COLOR = longPreferencesKey("pen_color")
        private val KEY_PEN_THICKNESS = floatPreferencesKey("pen_thickness")
        private val KEY_DEFAULT_TEMPLATE = stringPreferencesKey("default_template")
        private val KEY_PALM_REJECTION = booleanPreferencesKey("palm_rejection")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        const val DEFAULT_PEN_COLOR = 0xFF000000L
        const val DEFAULT_PEN_THICKNESS = 4f
        const val DEFAULT_TEMPLATE = "BLANK"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
