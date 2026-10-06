package com.oblutack.timenote.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import com.oblutack.timenote.core.newId

/** User preferences plus the running-session backup, stored in a (multiplatform) DataStore. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) : DefaultTagsState, DeviceIdSource {

    // --- KEYS ---
    private val USE_MONOCHROME_NODES = booleanPreferencesKey("use_monochrome_nodes")
    private val ENABLE_BACKGROUND_BLUR = booleanPreferencesKey("enable_background_blur")
    private val ENABLE_HAPTICS = booleanPreferencesKey("enable_haptics")
    private val CUSTOM_COLORS_JSON = stringPreferencesKey("custom_colors_json")

    // NEW: The key for our emergency timer backup
    private val ACTIVE_SESSION_BACKUP = stringPreferencesKey("active_session_backup")
    private val DEFAULT_TAGS_SEEDED = booleanPreferencesKey("default_tags_seeded")
    private val DEVICE_ID = stringPreferencesKey("device_id")

    // --- DEFAULT TAGS (see DefaultTagsState) ---
    override suspend fun isSeeded(): Boolean = dataStore.data
        .catch { emit(emptyPreferences()) }
        .first()[DEFAULT_TAGS_SEEDED] ?: false

    override suspend fun markSeeded() {
        dataStore.edit { it[DEFAULT_TAGS_SEEDED] = true }
    }

    // --- DEVICE ID (see DeviceIdSource) ---
    override suspend fun deviceId(): String {
        var id = ""
        // One atomic edit, so two callers can never end up with different ids
        dataStore.edit { prefs ->
            id = prefs[DEVICE_ID] ?: newId().also { prefs[DEVICE_ID] = it }
        }
        return id
    }

    // --- READ PREFERENCES ---
    val enableBackgroundBlurFlow: Flow<Boolean> // <-- NEW
        get() = dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[ENABLE_BACKGROUND_BLUR] ?: true }

    val enableHapticsFlow: Flow<Boolean> // <-- NEW
        get() = dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[ENABLE_HAPTICS] ?: true }
    val useMonochromeNodesFlow: Flow<Boolean>
        get() = dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[USE_MONOCHROME_NODES] ?: true }

    val customColorsFlow: Flow<List<Long>>
        get() = dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                val json = preferences[CUSTOM_COLORS_JSON] ?: ""
                if (json.isEmpty()) emptyList() else json.split(",").mapNotNull { it.toLongOrNull() }
            }

    // NEW: Flow to read the backup
    val activeSessionBackupFlow: Flow<String?>
        get() = dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[ACTIVE_SESSION_BACKUP] }

    // --- WRITE PREFERENCES ---
    suspend fun setBackgroundBlur(enabled: Boolean) { // <-- NEW
        dataStore.edit { it[ENABLE_BACKGROUND_BLUR] = enabled }
    }
    suspend fun setMonochromeNodes(enabled: Boolean) {
        dataStore.edit { it[USE_MONOCHROME_NODES] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) { // <-- NEW
        dataStore.edit { it[ENABLE_HAPTICS] = enabled }
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

    // NEW: Save or Clear the Backup
    suspend fun saveActiveSession(json: String?) {
        dataStore.edit { preferences ->
            if (json == null) {
                preferences.remove(ACTIVE_SESSION_BACKUP) // Clear it when timer ends
            } else {
                preferences[ACTIVE_SESSION_BACKUP] = json // Save it when ticking
            }
        }
    }
}