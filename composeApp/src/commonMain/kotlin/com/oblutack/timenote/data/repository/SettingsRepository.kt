package com.oblutack.timenote.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

object SettingsRepository {

    // The DataStore Instance (We will inject this from MainActivity later)
    private lateinit var dataStore: DataStore<Preferences>

    // --- KEYS ---
    private val USE_MONOCHROME_NODES = booleanPreferencesKey("use_monochrome_nodes")
    private val CUSTOM_COLORS_JSON = stringPreferencesKey("custom_colors_json") // We'll save a comma-separated list of hex strings

    // --- INITIALIZATION ---
    fun initialize(ds: DataStore<Preferences>) {
        dataStore = ds
    }

    // --- READ PREFERENCES (Flows) ---
    val useMonochromeNodesFlow: Flow<Boolean>
        get() = dataStore.data
            .catch { exception ->
                emit(emptyPreferences())
            }
            .map { preferences ->
                preferences[USE_MONOCHROME_NODES] ?: true // Default to true
            }

    val customColorsFlow: Flow<List<Long>>
        get() = dataStore.data
            .catch { exception ->
                emit(emptyPreferences())
            }
            .map { preferences ->
                val json = preferences[CUSTOM_COLORS_JSON] ?: ""
                if (json.isEmpty()) emptyList() else json.split(",").mapNotNull { it.toLongOrNull() }
            }

    // --- WRITE PREFERENCES ---
    suspend fun setMonochromeNodes(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_MONOCHROME_NODES] = enabled
        }
    }

    suspend fun addCustomColor(colorLong: Long) {
        dataStore.edit { preferences ->
            val currentListStr = preferences[CUSTOM_COLORS_JSON] ?: ""
            val currentList = if (currentListStr.isEmpty()) mutableListOf() else currentListStr.split(",").toMutableList()

            if (!currentList.contains(colorLong.toString())) {
                currentList.add(colorLong.toString())
                preferences[CUSTOM_COLORS_JSON] = currentList.joinToString(",")
            }
        }
    }

    suspend fun removeCustomColor(colorLong: Long) {
        dataStore.edit { preferences ->
            val currentListStr = preferences[CUSTOM_COLORS_JSON] ?: ""
            val currentList = currentListStr.split(",").toMutableList()
            currentList.remove(colorLong.toString())
            preferences[CUSTOM_COLORS_JSON] = currentList.joinToString(",")
        }
    }
}