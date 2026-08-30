package com.contextswitch.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.contextswitch.ui.theme.ThemeMode
import com.contextswitch.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = Constants.PREFS_NAME)

class UserPreferences(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val CARDS_ENABLED = booleanPreferencesKey("cards_enabled")
        val AUTO_DISMISS_ENABLED = booleanPreferencesKey("auto_dismiss_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: Constants.DEFAULT_BASE_URL }
    val cardsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CARDS_ENABLED] ?: true }
    val autoDismissEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_DISMISS_ENABLED] ?: true }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromName(it[Keys.THEME_MODE]) }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url }
    }

    suspend fun setCardsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CARDS_ENABLED] = enabled }
    }

    suspend fun setAutoDismissEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DISMISS_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
