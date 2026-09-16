package com.revia.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.revia.ui.theme.ThemeMode
import com.revia.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = Constants.PREFS_NAME)

class UserPreferences(private val context: Context) {

    private object Keys {
        val CARDS_ENABLED = booleanPreferencesKey("cards_enabled")
        val AUTO_DISMISS_ENABLED = booleanPreferencesKey("auto_dismiss_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OBSERVED_APPS = stringSetPreferencesKey("observed_apps")
    }

    val cardsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CARDS_ENABLED] ?: true }
    val autoDismissEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_DISMISS_ENABLED] ?: true }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromName(it[Keys.THEME_MODE]) }

    /**
     * Apps the user has chosen to let Revia read. Empty by default: nothing is read
     * until it is picked, so an app the user never considered is never observed.
     */
    val observedApps: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.OBSERVED_APPS] ?: emptySet() }

    suspend fun setCardsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CARDS_ENABLED] = enabled }
    }

    suspend fun setAutoDismissEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DISMISS_ENABLED] = enabled }
    }

    suspend fun setAppObserved(packageName: String, observed: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.OBSERVED_APPS] ?: emptySet()
            prefs[Keys.OBSERVED_APPS] =
                if (observed) current + packageName else current - packageName
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
