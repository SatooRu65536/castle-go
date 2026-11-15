package dev.satooru.castles.go.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences に保存する設定．端末のみで完結する． */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_TRAVEL, next.travelMode.name)
            .putString(KEY_NEARBY_MODE, next.nearbyMode.name)
            .putInt(KEY_TOP_N, next.topN)
            .apply {
                if (next.radiusKm == null) remove(KEY_RADIUS) else putFloat(KEY_RADIUS, next.radiusKm.toFloat())
            }
            .putBoolean(KEY_CAR_NOTIFY, next.carNotifyEnabled)
            .putFloat(KEY_CAR_DISTANCE, next.carNotifyDistanceKm.toFloat())
            .putInt(KEY_CAR_MIN_SCALE, next.carNotifyMinScale)
            .putBoolean(KEY_CAR_UNVISITED, next.carNotifyUnvisitedOnly)
            .putBoolean(KEY_BACKGROUND_NOTIFY, next.backgroundNotifyEnabled)
            .apply()
        _settings.value = next
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            travelMode = enumOr(prefs.getString(KEY_TRAVEL, null), d.travelMode),
            nearbyMode = enumOr(prefs.getString(KEY_NEARBY_MODE, null), d.nearbyMode),
            topN = prefs.getInt(KEY_TOP_N, d.topN),
            radiusKm = if (prefs.contains(KEY_RADIUS)) prefs.getFloat(KEY_RADIUS, 0f).toDouble() else null,
            carNotifyEnabled = prefs.getBoolean(KEY_CAR_NOTIFY, d.carNotifyEnabled),
            carNotifyDistanceKm = prefs.getFloat(KEY_CAR_DISTANCE, d.carNotifyDistanceKm.toFloat()).toDouble(),
            carNotifyMinScale = prefs.getInt(KEY_CAR_MIN_SCALE, d.carNotifyMinScale),
            carNotifyUnvisitedOnly = prefs.getBoolean(KEY_CAR_UNVISITED, d.carNotifyUnvisitedOnly),
            backgroundNotifyEnabled = prefs.getBoolean(KEY_BACKGROUND_NOTIFY, d.backgroundNotifyEnabled),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_TRAVEL = "travelMode"
        const val KEY_NEARBY_MODE = "nearbyMode"
        const val KEY_TOP_N = "topN"
        const val KEY_RADIUS = "radiusKm"
        const val KEY_CAR_NOTIFY = "carNotifyEnabled"
        const val KEY_CAR_DISTANCE = "carNotifyDistanceKm"
        const val KEY_CAR_MIN_SCALE = "carNotifyMinScale"
        const val KEY_CAR_UNVISITED = "carNotifyUnvisitedOnly"
        const val KEY_BACKGROUND_NOTIFY = "backgroundNotifyEnabled"
    }
}
