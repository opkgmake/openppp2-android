package supersocksr.ppp.android.data

import android.content.SharedPreferences
import supersocksr.ppp.android.TEST_LINK_DEFAULT
import supersocksr.ppp.android.TEST_LINK_KEY
import supersocksr.ppp.android.TIMEOUT_DEFAULT
import supersocksr.ppp.android.TIMEOUT_KEY

class SettingsRepository(private val preferences: SharedPreferences) {
  fun testLink(): String = preferences.getString(TEST_LINK_KEY, TEST_LINK_DEFAULT) ?: TEST_LINK_DEFAULT

  fun testTimeout(): Long = preferences.getLong(TIMEOUT_KEY, TIMEOUT_DEFAULT)
}
