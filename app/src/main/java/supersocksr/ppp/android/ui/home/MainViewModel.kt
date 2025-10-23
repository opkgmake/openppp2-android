package supersocksr.ppp.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import supersocksr.ppp.android.data.ConfigRepository
import supersocksr.ppp.android.data.SettingsRepository
import supersocksr.ppp.android.utils.UserConfig

class MainViewModel(
  private val configRepository: ConfigRepository,
  private val settingsRepository: SettingsRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<Event>()
  val events: SharedFlow<Event> = _events.asSharedFlow()

  init {
    loadConfigs()
  }

  val selectedConfig: UserConfig?
    get() = _uiState.value.selectedConfig

  fun onSelectConfig(index: Int?) {
    _uiState.update { state ->
      state.copy(selectedIndex = index)
    }
  }

  fun onAddConfig() {
    _uiState.update { state ->
      val updated = state.configs.toMutableList().apply { add(UserConfig()) }
      persistConfigs(updated)
      state.copy(configs = updated)
    }
  }

  fun onEditConfig(index: Int) {
    _uiState.update { state ->
      val config = state.configs.getOrNull(index) ?: return
      state.copy(editorState = EditorState(index, config))
    }
  }

  fun onDismissEditor() {
    _uiState.update { state -> state.copy(editorState = null) }
  }

  fun onConfigSaved(config: UserConfig) {
    val editor = _uiState.value.editorState ?: return
    _uiState.update { state ->
      val updated = state.configs.toMutableList().apply { set(editor.index, config) }
      persistConfigs(updated)
      state.copy(
        configs = updated,
        editorState = null,
        selectedIndex = editor.index
      )
    }
  }

  fun onConfigDeleted() {
    val editor = _uiState.value.editorState ?: return
    _uiState.update { state ->
      val updated = state.configs.toMutableList().apply { removeAt(editor.index) }
      persistConfigs(updated)
      val newSelected = state.selectedIndex?.let { selected ->
        when {
          selected == editor.index -> null
          selected > editor.index -> selected - 1
          else -> selected
        }
      }
      state.copy(
        configs = updated,
        editorState = null,
        selectedIndex = newSelected
      )
    }
  }

  fun onStartVpn(): Boolean {
    val config = _uiState.value.selectedConfig
    return if (config == null) {
      viewModelScope.launch { _events.emit(Event.ConfigNotSelected) }
      false
    } else {
      _uiState.update { it.copy(vpnRunning = true, testStatus = "Test") }
      true
    }
  }

  fun onStopVpn() {
    _uiState.update { it.copy(vpnRunning = false) }
  }

  fun onTestConnection() {
    if (_uiState.value.isTestingConnection) {
      return
    }
    _uiState.update { it.copy(isTestingConnection = true, testStatus = "Testing...") }
    viewModelScope.launch(ioDispatcher) {
      val timeout = settingsRepository.testTimeout()
      val client = OkHttpClient.Builder()
        .connectTimeout(timeout, TimeUnit.MILLISECONDS)
        .readTimeout(timeout, TimeUnit.MILLISECONDS)
        .callTimeout(timeout, TimeUnit.MILLISECONDS)
        .build()
      val request = Request.Builder()
        .url(settingsRepository.testLink())
        .get()
        .build()
      val beginTime = System.currentTimeMillis()
      val result = runCatching {
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            (System.currentTimeMillis() - beginTime).toString() + "ms"
          } else {
            "-1 ms"
          }
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess { latency ->
          _uiState.update { it.copy(testStatus = latency) }
        }.onFailure { error ->
          _uiState.update { it.copy(testStatus = errorLabel(error)) }
          _events.emit(Event.ShowError(error))
        }
        _uiState.update { it.copy(isTestingConnection = false) }
      }
    }
  }

  fun onTestingFinished() {
    _uiState.update { it.copy(isTestingConnection = false) }
  }

  private fun errorLabel(error: Throwable): String {
    val cause = error.cause ?: error
    return when (cause) {
      is ConnectException -> "No Connection"
      is UnknownHostException -> "Unknown Host"
      is SocketTimeoutException -> "Timeout"
      else -> "Error"
    }
  }

  private fun persistConfigs(configs: List<UserConfig>) {
    viewModelScope.launch(ioDispatcher) {
      configRepository.persistConfigs(configs)
    }
  }

  private fun loadConfigs() {
    viewModelScope.launch(ioDispatcher) {
      val result = runCatching { configRepository.loadConfigs() }
      val configs = result.getOrElse { error ->
        if (error is ConfigRepository.ConfigSerializationException) {
          _events.emit(Event.DeserializationFailed)
        } else {
          _events.emit(Event.ShowError(error))
        }
        emptyList()
      }
      withContext(Dispatchers.Main) {
        _uiState.update { state ->
          val newSelected = state.selectedIndex?.takeIf { index -> index in configs.indices }
          state.copy(configs = configs, selectedIndex = newSelected)
        }
      }
    }
  }

  sealed interface Event {
    data object ConfigNotSelected : Event
    data object DeserializationFailed : Event
    data class ShowError(val throwable: Throwable) : Event
  }

  companion object {
    fun provideFactory(
      configRepository: ConfigRepository,
      settingsRepository: SettingsRepository,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
          @Suppress("UNCHECKED_CAST")
          return MainViewModel(configRepository, settingsRepository, dispatcher) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
      }
    }
  }
}
