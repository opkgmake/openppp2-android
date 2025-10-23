package supersocksr.ppp.android.ui.home

import supersocksr.ppp.android.utils.UserConfig

data class MainUiState(
  val configs: List<UserConfig> = emptyList(),
  val selectedIndex: Int? = null,
  val editorState: EditorState? = null,
  val vpnRunning: Boolean = false,
  val testStatus: String = "Test",
  val isTestingConnection: Boolean = false
) {
  val selectedConfig: UserConfig?
    get() = selectedIndex?.let { index -> configs.getOrNull(index) }
}

data class EditorState(
  val index: Int,
  val config: UserConfig
)
