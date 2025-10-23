package supersocksr.ppp.android.data

import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import supersocksr.ppp.android.ALL_CONFIGS_KEY
import supersocksr.ppp.android.utils.UserConfig

private const val DEFAULT_CONFIG_LIST = "[]"

class ConfigRepository(
  private val preferences: SharedPreferences,
  private val json: Json = Json
) {
  private val serializer = ListSerializer(UserConfig.serializer())

  class ConfigSerializationException(cause: Throwable) : Exception(cause)

  fun loadConfigs(): List<UserConfig> {
    val raw = preferences.getString(ALL_CONFIGS_KEY, DEFAULT_CONFIG_LIST) ?: DEFAULT_CONFIG_LIST
    if (raw.isBlank()) {
      return emptyList()
    }
    return try {
      json.decodeFromString(serializer, raw)
    } catch (error: Exception) {
      throw ConfigSerializationException(error)
    }
  }

  fun persistConfigs(configs: List<UserConfig>) {
    preferences.edit()
      .putString(ALL_CONFIGS_KEY, json.encodeToString(serializer, configs))
      .apply()
  }
}
