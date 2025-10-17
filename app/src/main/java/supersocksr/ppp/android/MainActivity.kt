package supersocksr.ppp.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import supersocksr.ppp.android.c.libopenppp2.LIBOPENPPP2_LINK_STATE_CLIENT_UNINITIALIZED
import supersocksr.ppp.android.openppp2.IPAddressX
import supersocksr.ppp.android.openppp2.Macro
import supersocksr.ppp.android.openppp2.VPN
import supersocksr.ppp.android.openppp2.VPNLinkConfiguration
import supersocksr.ppp.android.ui.theme.Openppp2Theme
import supersocksr.ppp.android.ui.theme.Pink500
import supersocksr.ppp.android.utils.Address
import supersocksr.ppp.android.utils.RawReader
import supersocksr.ppp.android.utils.UserConfig
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

const val TAG = "MainActivity"
const val ALL_CONFIGS_KEY = "all_configs"

private enum class MainDestination(val route: String, val icon: ImageVector, @StringRes val labelRes: Int) {
  HOME("home", Icons.Default.Home, R.string.nav_home),
  SETTINGS("settings", Icons.Default.Settings, R.string.nav_settings)
}

class MainActivity : PppVpnActivity() {
  private val userConfigListSerializer = ListSerializer(UserConfig.serializer())
  private lateinit var configPreferences: SharedPreferences
  private lateinit var settingsPreferences: SharedPreferences
  private lateinit var settingsRepository: SettingsRepository
  private val selectedUserConfig: MutableState<UserConfig?> = mutableStateOf(null)
  private val userConfigJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  private val configList = mutableStateListOf<UserConfig>()
  private var selectedConfigIndex by mutableIntStateOf(-1)
  private var vpnRunning by mutableStateOf(false)
  private val defaultPrimaryDns = "8.8.8.8"
  private val defaultSecondaryDns = "8.8.4.4"
  private var cachedBypassIpRules: String? = null
  private var cachedDnsRules: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configPreferences = getSharedPreferences("config_list", Context.MODE_PRIVATE)
    settingsPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
    settingsRepository = SettingsRepository(this, settingsPreferences)
    restoreConfigs()
    vpnRunning = vpn_state() != LIBOPENPPP2_LINK_STATE_CLIENT_UNINITIALIZED
    setContent { Openppp2Theme { OpenpppApp() } }
  }

  private fun restoreConfigs() {
    val serialized = configPreferences.getString(ALL_CONFIGS_KEY, "[]") ?: "[]"
    val storedConfigs = try {
      userConfigJson.decodeFromString(userConfigListSerializer, serialized)
    } catch (error: Exception) {
      Toast.makeText(
        applicationContext,
        getString(R.string.warn_deserialize),
        Toast.LENGTH_LONG
      ).show()
      emptyList()
    }

    configList.clear()
    configList.addAll(storedConfigs)
    if (selectedConfigIndex >= configList.size) {
      selectedConfigIndex = -1
    }
    selectedUserConfig.value = selectedConfigIndex.takeIf { it in configList.indices }
      ?.let { configList[it] }
  }

  private fun persistConfigs() {
    val serialized = userConfigJson.encodeToString(
      userConfigListSerializer,
      configList.toList()
    )
    configPreferences.edit().putString(ALL_CONFIGS_KEY, serialized).apply()
  }

  private fun selectConfig(index: Int?) {
    val normalized = index?.takeIf { it in configList.indices } ?: -1
    selectedConfigIndex = normalized
    selectedUserConfig.value = normalized.takeIf { it >= 0 }?.let { configList[it] }
    Log.d(TAG, "selected config index: $normalized")
  }

  private fun appendEmptyConfig() {
    configList.add(UserConfig())
    persistConfigs()
  }

  private fun updateConfig(index: Int, config: UserConfig) {
    if (index !in configList.indices) {
      return
    }
    configList[index] = config
    if (selectedConfigIndex == index) {
      selectedUserConfig.value = config
    }
    persistConfigs()
  }

  private fun removeConfig(index: Int) {
    if (index !in configList.indices) {
      return
    }
    val previousSelection = selectedConfigIndex
    configList.removeAt(index)
    persistConfigs()

    if (configList.isEmpty()) {
      selectConfig(null)
      return
    }

    val newSelection = when {
      previousSelection == -1 -> null
      index == previousSelection -> minOf(index, configList.lastIndex)
      index < previousSelection -> previousSelection - 1
      else -> previousSelection
    }
    selectConfig(newSelection)
  }

  private fun startVpn() {
    vpn_run()
    vpnRunning = true
  }

  private fun stopVpn() {
    vpn_stop()
    vpnRunning = false
  }

  override fun vpn_load(): VPNLinkConfiguration? {
    if (selectedUserConfig.value == null) {
      return null
    }
    Log.i(TAG, "running using config: ${selectedUserConfig.value!!}")
    val encryptionPreferences = settingsRepository.readEncryptionPreferences()
    val serverProxy = settingsRepository.readServerProxy()
    val routingPreferences = settingsRepository.readRoutingPreferences()
    val rawReader = RawReader(resources)
    val bypassIpRules = cachedBypassIpRules ?: rawReader
      .readRawResource(R.raw.ip)
      .also { cachedBypassIpRules = it }
    val rawDnsRules = cachedDnsRules ?: rawReader
      .readRawResource(R.raw.domain)
      .also { cachedDnsRules = it }
    val effectiveDnsRules = rawDnsRules

    val config = VPNLinkConfiguration().apply {
      SubnetAddress = "255.255.255.0"
      IPAddress = selectedUserConfig.value!!.tun_address.toString()
      Log.d(TAG, "IPAddress: $IPAddress")
      GatewayServer = IPAddressX.address_calc_first_address(IPAddress, SubnetAddress)

      VirtualSubnet = true
      BlockQUIC = false
      StaticMode = selectedUserConfig.value!!.tun_address != null
      Log.d(TAG, "StaticMode: $StaticMode")
      FlashMode = false
      AtomicHttpProxySet = false
      DnsAddresses.apply {
        clear()
        add(defaultPrimaryDns)
        add(defaultSecondaryDns)
      }

      if (routingPreferences.fullTunnel) {
        BypassIpList = ""
      } else {
        BypassIpList = bypassIpRules
      }
      // Keep feeding the curated DNS rules even in full-tunnel mode so lookups do not
      // fall back to high-latency upstream discovery.
      DNSRuleList = effectiveDnsRules
      AllowedApplicationPackageNames.clear()
      DisallowedApplicationPackageNames.clear()
      when (routingPreferences.mode) {
        RoutingMode.GLOBAL -> {
          // No explicit package routing; all apps use VPN by default.
        }

        RoutingMode.WHITELIST -> {
          routingPreferences.whitelist
            .filter { it.isNotBlank() }
            .forEach { AllowedApplicationPackageNames.add(it) }
        }

        RoutingMode.BLACKLIST -> {
          routingPreferences.blacklist
            .filter { it.isNotBlank() }
            .forEach { DisallowedApplicationPackageNames.add(it) }
        }
      }

      VPNConfiguration.apply {
        key.apply {
          kf = encryptionPreferences.kf
          kx = encryptionPreferences.kx
          kl = encryptionPreferences.kl
          kh = encryptionPreferences.kh
          protocol = encryptionPreferences.protocol
          protocol_key = encryptionPreferences.protocolKey
          transport = encryptionPreferences.transport
          transport_key = encryptionPreferences.transportKey
          masked = false
          plaintext = false
          delta_encode = false
          shuffle_data = false
        }

        tcp.apply {
          inactive.timeout = Macro.PPP_TCP_INACTIVE_TIMEOUT
          connect.timeout = Macro.PPP_TCP_CONNECT_TIMEOUT
          connect.nexcept = Macro.PPP_TCP_CONNECT_NEXCEPT
          turbo = true
          backlog = Macro.PPP_LISTEN_BACKLOG
          fast_open = true
        }

        udp.apply {
          inactive.timeout = Macro.PPP_UDP_INACTIVE_TIMEOUT
          dns.apply {
            timeout = Macro.PPP_DEFAULT_DNS_TIMEOUT
            ttl = Macro.PPP_DEFAULT_DNS_TTL
            cache = true
          }

          static_.apply {
            quic = true
            dns = true
            icmp = true
            keep_alived[0] = 0
            keep_alived[1] = 0
            aggligator = 0
            servers.add(selectedUserConfig.value!!.static_server.toString())
            Log.d(TAG, "udp static servers: $servers")
          }
        }

        websocket.apply {
          verify_peer = true
          http.apply {
            error = "Status Code: 404; Not Found"
            request.apply {
              put("Cache-Control", "no-cache")
              put("Pragma", "no-cache")
              put("Accept-Encoding", "gzip, deflate")
              put("Accept-Language", "zh-CN,zh;q=0.9")
              put("Origin", "http://www.websocket-test.com")
              put("Sec-WebSocket-Extensions", "permessage-deflate; client_max_window_bits")
              put(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0"
              )
            }
            response["Server"] = "Kestrel"
          }
        }

        client.apply {
          guid = UUID.randomUUID().toString()
          Log.d(TAG, "client guid: $guid")
          server = VPN.vpn_link_of(selectedUserConfig.value!!.server.toString())!!.url
          Log.d(TAG, "client server: $server")
          server_proxy = serverProxy
          bandwidth = 0
          reconnections.timeout = Macro.PPP_TCP_CONNECT_TIMEOUT

          http_proxy.apply {
            bind = "127.0.0.1"
            port = Macro.PPP_DEFAULT_HTTP_PROXY_PORT
          }

          socks_proxy.apply {
            bind = "127.0.0.1"
            port = Macro.PPP_DEFAULT_SOCKS_PROXY_PORT
          }
        }
      }

    }
    return config
  }

  // FIXME: this function actually cannot hide ime.
  private fun hideIME(view: View? = null) {
    val focusedView = currentFocus
    if (focusedView !is TextInputEditText) {
      return
    }
    focusedView.clearFocus()
    // hide keyboard
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow((view ?: focusedView).windowToken, 0)
  }

  @Composable
  private fun OpenpppApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MainDestination.HOME.route

    Scaffold(
      bottomBar = {
        BottomNavigationBar(
          currentRoute = currentRoute,
          onNavigate = { destination ->
            navController.navigate(destination.route) {
              popUpTo(navController.graph.startDestinationId) { saveState = true }
              launchSingleTop = true
              restoreState = true
            }
          }
        )
      }
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = MainDestination.HOME.route,
        modifier = Modifier.padding(innerPadding)
      ) {
        composable(MainDestination.HOME.route) {
          ConfigSelectionScreen(
            configs = configList,
            selectedIndex = selectedConfigIndex.takeIf { it >= 0 },
            onSelectConfig = ::selectConfig,
            onAddConfig = ::appendEmptyConfig,
            onUpdateConfig = ::updateConfig,
            onDeleteConfig = ::removeConfig,
            vpnRunning = vpnRunning,
            onStartVpn = ::startVpn,
            onStopVpn = ::stopVpn,
            onRequestTest = ::testConnection
          )
        }
        composable(MainDestination.SETTINGS.route) {
          SettingsScreen(settingsRepository)
        }
      }
    }
  }

  @Composable
  private fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (MainDestination) -> Unit
  ) {
    NavigationBar(tonalElevation = 8.dp) {
      MainDestination.values().forEach { destination ->
        val label = stringResource(destination.labelRes)
        NavigationBarItem(
          icon = {
            Icon(
              imageVector = destination.icon,
              contentDescription = label
            )
          },
          label = { Text(label) },
          selected = currentRoute == destination.route,
          onClick = { onNavigate(destination) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = MaterialTheme.colorScheme.primary
          ),
          alwaysShowLabel = true
        )
      }
    }
  }

  @Composable
  private fun ConfigSelectionScreen(
    configs: SnapshotStateList<UserConfig>,
    selectedIndex: Int?,
    onSelectConfig: (Int?) -> Unit,
    onAddConfig: () -> Unit,
    onUpdateConfig: (Int, UserConfig) -> Unit,
    onDeleteConfig: (Int) -> Unit,
    vpnRunning: Boolean,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onRequestTest: (MutableState<String>) -> Unit
  ) {
    val context = LocalContext.current
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val itemModifier = Modifier
      .aspectRatio(1.85f, true)
      .padding(20.dp)

    Column {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = Modifier
          .weight(1f)
          .clickable(interactionSource = null, indication = null) {
            if (editingIndex != null) {
              editingIndex = null
            } else {
              onSelectConfig(null)
            }
          },
        contentPadding = PaddingValues(8.dp)
      ) {
        itemsIndexed(configs) { index, config ->
          val isSelected = selectedIndex == index
          val borderWidth by animateDpAsState(
            targetValue = if (isSelected) 4.dp else 0.dp,
            animationSpec = tween(durationMillis = 200)
          )
          Box(
            modifier = itemModifier
              .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
              )
              .border(
                BorderStroke(
                  borderWidth,
                  if (isSelected) Pink500 else MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(8.dp)
              )
              .clickable {
                if (isSelected) {
                  editingIndex = index
                } else {
                  editingIndex = null
                  onSelectConfig(index)
                }
              },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = config.name.ifEmpty { config.server.host.orEmpty() },
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onPrimary,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        item {
          Box(
            modifier = itemModifier
              .background(Color.Gray, RoundedCornerShape(8.dp))
              .clickable {
                onAddConfig()
              },
            contentAlignment = Alignment.Center
          ) {
            Text(text = "+", fontSize = 36.sp, color = Color.White)
          }
        }
      }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          val testLabel = stringResource(R.string.vpn_test)
          val testingLabel = stringResource(R.string.vpn_testing)
          val testText = remember { mutableStateOf(testLabel) }
          val startLabel = stringResource(R.string.vpn_start)
          var startText by remember { mutableStateOf(startLabel) }

        Button(
          onClick = {
            if (selectedIndex == null) {
              Toast.makeText(
                context,
                context.getString(R.string.warn_config_not_select),
                Toast.LENGTH_SHORT
              ).show()
              return@Button
            }
            onStartVpn()
            testText.value = testLabel
            configs[selectedIndex].name.takeIf { it.isNotBlank() }?.let { startText = it }
          },
          enabled = !vpnRunning
        ) {
          Text(startText)
        }

        if (vpnRunning) {
          Button(
            onClick = {
              if (testText.value != testingLabel) {
                onRequestTest(testText)
              }
              testText.value = testingLabel
            }
          ) {
            Text(testText.value)
          }
        }

        Button(
          onClick = {
            onStopVpn()
            startText = startLabel
          }
        ) {
          Text(stringResource(R.string.vpn_stop))
        }
      }

      val editing = editingIndex
      if (editing != null) {
        EditConfigDialog(
          config = configs[editing],
          onDismiss = { editingIndex = null },
          onSave = { updated ->
            onUpdateConfig(editing, updated)
            editingIndex = null
          },
          onDelete = {
            onDeleteConfig(editing)
            editingIndex = null
          }
        )
      }
    }
  }

  // 编辑配置框
  @Composable
  fun EditConfigDialog(
    config: UserConfig,
    onDismiss: () -> Unit,
    onSave: (UserConfig) -> Unit,
    onDelete: () -> Unit
  ) {
    var name by remember { mutableStateOf(TextFieldValue(config.name)) }
    var server by remember {
      mutableStateOf(
        TextFieldValue(
          config.server.toString()
        )
      )
    }
    var guid by remember { mutableStateOf(TextFieldValue(config.guid)) }
    var static_server by remember { mutableStateOf(TextFieldValue(config.static_server.toString())) }
    // null 则填入空字符串
    var tun_address by remember {
      mutableStateOf(
        TextFieldValue(
          config.tun_address?.toString() ?: ""
        )
      )
    }
    val lazyListState = rememberLazyListState()
    val dialogKeyboardActions = KeyboardActions(onDone = {
      hideIME()
    })
    val dialogKeyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
    val dialogTextFieldModifier = Modifier
      .fillMaxWidth()
      .padding(8.dp)

    AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = getString(R.string.config_title),
            modifier = Modifier
              .weight(1f)
              .padding(end = 8.dp),
            fontSize = 22.sp
          )
          DeleteButton { onDelete() }
        }
      },
      modifier = Modifier.clickable(interactionSource = null, indication = null) { hideIME() },
      text = {
        LazyColumn(state = lazyListState, contentPadding = PaddingValues(4.dp)) {
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = name,
              onValueChange = { name = it },
              label = { Text(getString(R.string.config_name)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = KeyboardActions(onDone = { hideIME() })
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = server,
              onValueChange = { server = it },
              label = { Text(getString(R.string.config_server)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = guid,
              readOnly = true,
              onValueChange = { guid = it },
              label = { Text(getString(R.string.config_guid)) },
              placeholder = { Text(getString(R.string.placeholder_random)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = static_server,
              onValueChange = { static_server = it },
              label = { Text(getString(R.string.config_static)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = tun_address,
              onValueChange = { tun_address = it },
              label = { Text(getString(R.string.config_tun)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            // 保存配置
            try {
              val cfg = UserConfig(
                name = name.text.trim(),
                server = Address.unsafeParse(server.text.trim()),
                static_server = Address.unsafeParse(static_server.text.trim()),
                guid = guid.text.trim(),
                tun_address = tun_address.text.trim()
                  .let { if (it.isBlank()) null else Address.parse(it) },
              )
              cfg.validate()
              onSave(cfg)
            } catch (e: Exception) {
              e.printStackTrace()
              Toast.makeText(
                this,
                getString(R.string.toast_invalid_address, e.message ?: ""),
                Toast.LENGTH_LONG
              )
                .show()
            }
          }
        ) {
          Text(getString(R.string.save))
        }
      },
      dismissButton = {
        Button(
          onClick = onDismiss
        ) {
          Text(getString(R.string.cancel))
        }
      }
    )
  }

  // 测试连接
  private fun testConnection(state: MutableState<String>) {
    Log.i(TAG, "testConnection starting..")
    lifecycleScope.launch(Dispatchers.IO) {
      val options = settingsRepository.readTestOptions()
      val url = URL(options.link)
      val timeout = options.timeoutMs
      val client = OkHttpClient.Builder()
        .connectTimeout(timeout, TimeUnit.MILLISECONDS)
        .readTimeout(timeout, TimeUnit.MILLISECONDS)
        .callTimeout(timeout, TimeUnit.MILLISECONDS)
        .build()

      val request = Request.Builder()
        .url(url)
        .get()
        .build()

      val beginTime = System.currentTimeMillis()
      Log.d(TAG, "beginTime: $beginTime")
      try {
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val duration = System.currentTimeMillis() - beginTime
            withContext(Dispatchers.Main) {
              state.value = getString(R.string.test_result_latency, duration)
            }
          } else {
            withContext(Dispatchers.Main) {
              state.value = getString(R.string.test_result_http_error)
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "${e.cause}: ${e.message}")
        val tx = when (e.cause) {
          is ConnectException -> {
            getString(R.string.test_result_no_connection)
          }

          is UnknownHostException -> {
            getString(R.string.test_result_unknown_host)
          }

          is SocketTimeoutException -> {
            getString(R.string.test_result_timeout)
          }

          else -> {
            getString(R.string.test_result_error)
          }
        }
        withContext(Dispatchers.Main) {
          state.value = tx
          Toast.makeText(
            this@MainActivity,
            getString(
              R.string.toast_error_message,
              e.cause?.toString() ?: getString(R.string.test_result_error),
              e.message ?: ""
            ),
            Toast.LENGTH_LONG
          ).show()
        }
      }
    }
  }
}


@Composable
fun DeleteButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
  IconButton(onClick = onClick) {
    Icon(
      imageVector = Icons.Filled.Delete, // 使用 Material Design 的删除图标
      contentDescription = stringResource(id = R.string.delete_content_description),
      tint = MaterialTheme.colorScheme.primary // 设置图标颜色
    )
  }
}
