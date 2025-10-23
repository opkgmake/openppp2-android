package supersocksr.ppp.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import supersocksr.ppp.android.c.libopenppp2.LIBOPENPPP2_LINK_STATE_CLIENT_UNINITIALIZED
import supersocksr.ppp.android.openppp2.IPAddressX
import supersocksr.ppp.android.openppp2.Macro
import supersocksr.ppp.android.openppp2.VPN
import supersocksr.ppp.android.openppp2.VPNLinkConfiguration
import supersocksr.ppp.android.ui.theme.Openppp2Theme
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

class MainActivity : PppVpnActivity() {
  private val userConfigListSerializer = ListSerializer(UserConfig.serializer())
  private lateinit var configPreferences: SharedPreferences
  private lateinit var settingsPreferences: SharedPreferences
  private lateinit var settings: Settings
  private val selectedUserConfig: MutableState<UserConfig?> = mutableStateOf(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configPreferences = getSharedPreferences("config_list", Context.MODE_PRIVATE)
    settingsPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
    settings = Settings(this, settingsPreferences)
    setContent {
      Openppp2Theme { App() }
    }
  }

  override fun vpn_load(): VPNLinkConfiguration? {
    val userConfig = selectedUserConfig.value ?: return null
    Log.i(TAG, "running using config: $userConfig")
    val rawReader = RawReader(resources)
    val linkConfig = VPNLinkConfiguration().apply {
      val ipv4Address = userConfig.tun_address?.toString()?.takeIf { it.isNotBlank() }
      val subnet = userConfig.tun_subnet.ifBlank { "255.255.255.0" }
      SubnetAddress = subnet
      StaticMode = ipv4Address != null
      if (ipv4Address != null) {
        IPAddress = ipv4Address
        GatewayServer = IPAddressX.address_calc_first_address(IPAddress, SubnetAddress)
        Log.d(TAG, "IPv4 address: $IPAddress, gateway: $GatewayServer")
      }

      VirtualSubnet = true
      BlockQUIC = false
      FlashMode = false
      AtomicHttpProxySet = false
      AllowNoActivityNetwork = true
      Mux = 0
      Mtu = userConfig.mtu

      DnsAddresses.clear()
      val dnsList = userConfig.dns_servers.filter { it.isNotBlank() }
      if (dnsList.isEmpty()) {
        DnsAddresses.add("8.8.8.8")
        DnsAddresses.add("1.1.1.1")
      } else {
        DnsAddresses.addAll(dnsList)
      }

      BypassIpList = rawReader.readRawResource(R.raw.ip)
      DNSRuleList = rawReader.readRawResource(R.raw.domain)

      AllowedApplicationPackageNames.clear()
      val allowed = userConfig.allow_apps.filter { it.isNotBlank() }.ifEmpty { listOf(packageName) }
      AllowedApplicationPackageNames.addAll(allowed)
      DisallowedApplicationPackageNames.clear()
      userConfig.disallow_apps.filter { it.isNotBlank() && !AllowedApplicationPackageNames.contains(it) }
        .forEach { DisallowedApplicationPackageNames.add(it) }

      IPv4Routes.clear()
      val routesV4 = userConfig.routes_v4.filter { it.isNotBlank() }
      if (routesV4.isEmpty()) {
        IPv4Routes.add("0.0.0.0/0")
        IPv4Routes.add("128.0.0.0/1")
      } else {
        IPv4Routes.addAll(routesV4)
      }

      IPv6Routes.clear()
      EnableIPv6 = !userConfig.tun_ipv6.isNullOrBlank()
      if (EnableIPv6) {
        IPv6Address = userConfig.tun_ipv6
        IPv6PrefixLength = userConfig.tun_ipv6_prefix
        val routesV6 = userConfig.routes_v6.filter { it.isNotBlank() }
        if (routesV6.isEmpty()) {
          IPv6Routes.add("::/0")
        } else {
          IPv6Routes.addAll(routesV6)
        }
      }

      VPNConfiguration.apply {
        key.apply {
          kf = 154543927
          kx = 128
          kl = 10
          kh = 12
          protocol = "aes-128-cfb"
          protocol_key = "N6HMzdUs7IUnYHwq"
          transport = "aes-256-cfb"
          transport_key = "HWFweXu2g5RVMEpy"
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
            servers.clear()
            servers.add(userConfig.static_server.toString())
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
              put(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0"
              )
            }
            response["Server"] = "Kestrel"
          }
        }

        client.apply {
          guid = if (userConfig.guid.equals("Random", true) || userConfig.guid.isBlank()) {
            UUID.randomUUID().toString()
          } else {
            userConfig.guid
          }
          Log.d(TAG, "client guid: $guid")
          val serverLink = userConfig.server.toString()
          val vpnLink = VPN.vpn_link_of(serverLink)
          server = vpnLink?.url ?: serverLink
          Log.d(TAG, "client server: $server")
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
    return linkConfig
  }

  private fun hideIME(view: View? = null) {
    val focusedView = currentFocus
    if (focusedView !is TextInputEditText) {
      return
    }
    focusedView.clearFocus()
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow((view ?: focusedView).windowToken, 0)
  }

  @Composable
  fun App() {
    val navController = rememberNavController()
    val items = remember {
      listOf(
        NavItem("home", getString(R.string.nav_home), Icons.Default.Home),
        NavItem("settings", getString(R.string.nav_settings), Icons.Default.Settings),
      )
    }
    val selectedTab = remember { mutableIntStateOf(0) }

    Scaffold(
      bottomBar = {
        BottomNavigationBar(
          navController = navController,
          selectedTab = selectedTab,
          items = items
        )
      }
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = items.first().route,
        modifier = Modifier.padding(innerPadding)
      ) {
        composable(items[0].route) { ConfigSelectionScreen() }
        composable(items[1].route) { settings.SettingsScreen() }
      }
    }
  }

  @Composable
  fun BottomNavigationBar(
    navController: NavHostController,
    selectedTab: MutableState<Int>,
    items: List<NavItem>
  ) {
    NavigationBar(
      tonalElevation = 8.dp
    ) {
      items.forEachIndexed { index, item ->
        val isSelected = index == selectedTab.value

        NavigationBarItem(
          icon = {
            Icon(
              imageVector = item.icon,
              contentDescription = item.label
            )
          },
          label = { Text(item.label) },
          selected = isSelected,
          onClick = {
            selectedTab.value = index
            navController.navigate(item.route) {
              launchSingleTop = true
            }
          },
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
  fun ConfigSelectionScreen() {
    var selectedConfig by remember { mutableStateOf<Int?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    val originalAllConfig = configPreferences.getString(ALL_CONFIGS_KEY, "[]")!!
      .let {
        try {
          Json.decodeFromString(userConfigListSerializer, it)
        } catch (e: Exception) {
          Toast.makeText(
            applicationContext,
            getString(R.string.warn_deserialize),
            Toast.LENGTH_LONG
          ).show()
          emptyList()
        }
      }.toMutableList()
    val configListState = remember { mutableStateListOf(*originalAllConfig.toTypedArray()) }
    var vpnRunning by remember { mutableStateOf(vpn_state() != LIBOPENPPP2_LINK_STATE_CLIENT_UNINITIALIZED) }
    val defaultTestLabel = getString(R.string.vpn_test_action)
    val testingLabel = getString(R.string.vpn_testing)
    val testText = remember { mutableStateOf(defaultTestLabel) }

    val listState = rememberLazyListState()

    val select = { index: Int? ->
      Log.d(TAG, "selected config index: $index")
      selectedConfig = index
      selectedUserConfig.value = index?.let { configListState[it] }
    }
    val persistAllConfig = {
      Log.d(TAG, "persistAllConfig")
      configPreferences.edit()
        .putString(
          ALL_CONFIGS_KEY,
          Json.encodeToString(userConfigListSerializer, configListState)
        ).apply()
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      StatusCard(
        vpnRunning = vpnRunning,
        selectedProfile = selectedUserConfig.value,
        latencyLabel = testText.value,
        defaultLatencyLabel = defaultTestLabel,
        testingLabel = testingLabel,
        onStart = {
          if (selectedConfig == null) {
            Toast.makeText(
              this@MainActivity,
              getString(R.string.warn_config_not_select),
              Toast.LENGTH_SHORT
            ).show()
            return@StatusCard
          }
          vpn_run()
          vpnRunning = true
          testText.value = defaultTestLabel
        },
        onStop = {
          vpn_stop()
          vpnRunning = false
          testText.value = defaultTestLabel
        },
        onTest = {
          if (!vpnRunning) {
            Toast.makeText(
              this@MainActivity,
              getString(R.string.vpn_test_requires_connection),
              Toast.LENGTH_SHORT
            ).show()
            return@StatusCard
          }
          if (testText.value == testingLabel) {
            return@StatusCard
          }
          testText.value = testingLabel
          testConnection(testText)
        },
        startEnabled = !vpnRunning,
        testEnabled = vpnRunning && testText.value != testingLabel
      )

      Spacer(modifier = Modifier.height(16.dp))

      LazyColumn(
        modifier = Modifier
          .weight(1f),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp)
      ) {
        itemsIndexed(configListState) { index, config ->
          ConfigCard(
            config = config,
            isSelected = index == selectedConfig,
            onSelect = { select(index) },
            onEdit = {
              select(index)
              isEditing = true
            }
          )
        }
        item {
          AddConfigCard {
            Log.i(TAG, "Add a new config")
            val newConfig = UserConfig()
            configListState.add(newConfig)
            persistAllConfig()
            val newIndex = configListState.lastIndex
            select(newIndex)
            isEditing = true
          }
        }
      }

      if (isEditing && selectedConfig != null) {
        EditConfigDialog(
          config = configListState[selectedConfig!!],
          onDismiss = { isEditing = false },
          onSave = { newConfigValue ->
            Log.d(TAG, "Save config: $newConfigValue")
            configListState[selectedConfig!!] = newConfigValue
            selectedUserConfig.value = newConfigValue
            persistAllConfig()
            isEditing = false
          },
          onDelete = {
            configListState.removeAt(selectedConfig!!)
            persistAllConfig()
            isEditing = false
            select(null)
          }
        )
      }
    }
  }

  @Composable
  fun StatusCard(
    vpnRunning: Boolean,
    selectedProfile: UserConfig?,
    latencyLabel: String,
    defaultLatencyLabel: String,
    testingLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTest: () -> Unit,
    startEnabled: Boolean,
    testEnabled: Boolean
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = if (vpnRunning) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceVariant
        }
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        Text(
          text = if (vpnRunning) {
            getString(R.string.vpn_status_connected)
          } else {
            getString(R.string.vpn_status_disconnected)
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        if (selectedProfile != null) {
          val profileName = selectedProfile.name.ifBlank { selectedProfile.server.host ?: getString(R.string.vpn_profile_unnamed) }
          Text(
            text = getString(R.string.vpn_active_profile, profileName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
          )
        } else {
          Text(
            text = getString(R.string.vpn_no_profile),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
        if (latencyLabel != defaultLatencyLabel && latencyLabel != testingLabel) {
          Text(
            text = getString(R.string.vpn_last_latency, latencyLabel),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Button(
            onClick = onStart,
            enabled = startEnabled,
            modifier = Modifier.weight(1f)
          ) {
            Text(getString(R.string.vpn_start))
          }
          OutlinedButton(
            onClick = onTest,
            enabled = testEnabled,
            modifier = Modifier.weight(1f)
          ) {
            Text(latencyLabel)
          }
          Button(
            onClick = onStop,
            enabled = vpnRunning,
            modifier = Modifier.weight(1f)
          ) {
            Text(getString(R.string.vpn_stop))
          }
        }
      }
    }
  }

  @Composable
  fun ConfigCard(
    config: UserConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
        .clickable { onSelect() },
      colors = CardDefaults.cardColors(
        containerColor = if (isSelected) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surface
        }
      ),
      shape = RoundedCornerShape(12.dp),
      border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
      } else {
        null
      }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = config.name.ifBlank { getString(R.string.vpn_profile_unnamed) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          IconButton(onClick = onEdit) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = getString(R.string.config_title)
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = getString(R.string.config_server_value, config.server.toString()),
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = getString(
            R.string.config_static_value,
            config.static_server.toString()
          ),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 2.dp)
        )
        config.tun_address?.toString()?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = getString(R.string.config_ipv4_value, it, config.tun_subnet),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
        if (!config.tun_ipv6.isNullOrBlank()) {
          Text(
            text = getString(R.string.config_ipv6_value, config.tun_ipv6, config.tun_ipv6_prefix),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
        val dnsSummary = config.dns_servers.joinToString(", ")
        if (dnsSummary.isNotBlank()) {
          Text(
            text = getString(R.string.config_dns_value, dnsSummary),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
          )
        }
      }
    }
  }

  @Composable
  fun AddConfigCard(onClick: () -> Unit) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
        .clickable { onClick() },
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      shape = RoundedCornerShape(12.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(imageVector = Icons.Default.Add, contentDescription = getString(R.string.vpn_add_profile))
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = getString(R.string.vpn_add_profile), style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }

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
    var tun_address by remember {
      mutableStateOf(
        TextFieldValue(
          config.tun_address?.toString() ?: ""
        )
      )
    }
    var tunSubnet by remember { mutableStateOf(TextFieldValue(config.tun_subnet)) }
    var tunIpv6 by remember { mutableStateOf(TextFieldValue(config.tun_ipv6.orEmpty())) }
    var tunIpv6Prefix by remember { mutableStateOf(TextFieldValue(config.tun_ipv6_prefix.toString())) }
    var dnsServers by remember { mutableStateOf(TextFieldValue(formatListForInput(config.dns_servers))) }
    var routesV4 by remember { mutableStateOf(TextFieldValue(formatListForInput(config.routes_v4))) }
    var routesV6 by remember { mutableStateOf(TextFieldValue(formatListForInput(config.routes_v6))) }
    var allowApps by remember { mutableStateOf(TextFieldValue(formatListForInput(config.allow_apps))) }
    var disallowApps by remember { mutableStateOf(TextFieldValue(formatListForInput(config.disallow_apps))) }
    var mtu by remember { mutableStateOf(TextFieldValue(config.mtu.toString())) }

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
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = guid,
              onValueChange = { guid = it },
              label = { Text(getString(R.string.config_guid)) },
              placeholder = { Text("Random") },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = static_server,
              onValueChange = { static_server = it },
              label = { Text(getString(R.string.config_static)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = tun_address,
              onValueChange = { tun_address = it },
              label = { Text(getString(R.string.config_tun)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = tunSubnet,
              onValueChange = { tunSubnet = it },
              label = { Text(getString(R.string.config_tun_subnet)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = tunIpv6,
              onValueChange = { tunIpv6 = it },
              label = { Text(getString(R.string.config_tun_ipv6)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = tunIpv6Prefix,
              onValueChange = { tunIpv6Prefix = it },
              label = { Text(getString(R.string.config_tun_ipv6_prefix)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = mtu,
              onValueChange = { mtu = it },
              label = { Text(getString(R.string.config_mtu)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = dnsServers,
              onValueChange = { dnsServers = it },
              label = { Text(getString(R.string.config_dns)) },
              supportingText = { Text(getString(R.string.config_list_hint)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              maxLines = 3
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = routesV4,
              onValueChange = { routesV4 = it },
              label = { Text(getString(R.string.config_routes_v4)) },
              supportingText = { Text(getString(R.string.config_list_hint)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              maxLines = 3
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = routesV6,
              onValueChange = { routesV6 = it },
              label = { Text(getString(R.string.config_routes_v6)) },
              supportingText = { Text(getString(R.string.config_list_hint)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              maxLines = 3
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = allowApps,
              onValueChange = { allowApps = it },
              label = { Text(getString(R.string.config_allow_apps)) },
              supportingText = { Text(getString(R.string.config_list_hint)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              maxLines = 3
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = disallowApps,
              onValueChange = { disallowApps = it },
              label = { Text(getString(R.string.config_disallow_apps)) },
              supportingText = { Text(getString(R.string.config_list_hint)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions,
              maxLines = 3
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            try {
              val cfg = UserConfig(
                name = name.text.trim(),
                server = Address.unsafeParse(server.text.trim()),
                static_server = Address.unsafeParse(static_server.text.trim()),
                guid = guid.text.trim().ifBlank { "Random" },
                tun_address = tun_address.text.trim().let { input ->
                  if (input.isBlank()) {
                    null
                  } else {
                    Address.parse(input) ?: throw IllegalArgumentException("Invalid supersocksr.ppp.android.utils.Address: $input")
                  }
                },
                tun_subnet = tunSubnet.text.trim().ifBlank { config.tun_subnet },
                tun_ipv6 = tunIpv6.text.trim().ifBlank { null },
                tun_ipv6_prefix = tunIpv6Prefix.text.trim().toIntOrNull() ?: config.tun_ipv6_prefix,
                dns_servers = parseUserList(dnsServers.text),
                routes_v4 = parseUserList(routesV4.text),
                routes_v6 = parseUserList(routesV6.text),
                allow_apps = parseUserList(allowApps.text),
                disallow_apps = parseUserList(disallowApps.text),
                mtu = mtu.text.trim().toIntOrNull() ?: config.mtu
              )
              cfg.validate()
              onSave(cfg)
            } catch (e: Exception) {
              e.printStackTrace()
              Toast.makeText(
                this@MainActivity,
                e.message ?: e.toString(),
                Toast.LENGTH_LONG
              ).show()
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

  private fun parseUserList(text: String): List<String> {
    return text.split(',', ';', '\n')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }

  private fun formatListForInput(values: List<String>): String {
    return values.joinToString(separator = "\n")
  }

  private fun testConnection(state: MutableState<String>) {
    Log.i(TAG, "testConnection starting..")
    lifecycleScope.launch(Dispatchers.IO) {
      val url = URL(settingsPreferences.getString(TEST_LINK_KEY, TEST_LINK_DEFAULT)!!)
      val timeout = settingsPreferences.getLong(TIMEOUT_KEY, TIMEOUT_DEFAULT)
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
          val result = if (response.isSuccessful) {
            (System.currentTimeMillis() - beginTime).toString() + "ms"
          } else {
            "-1 ms"
          }
          withContext(Dispatchers.Main) {
            state.value = result
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "${e.cause}: ${e.message}")
        val tx = when (e.cause) {
          is ConnectException -> {
            "No Connection"
          }

          is UnknownHostException -> {
            "Unknown Host"
          }

          is SocketTimeoutException -> {
            "Timeout"
          }

          else -> {
            "Error"
          }
        }
        withContext(Dispatchers.Main) {
          state.value = tx
          Toast.makeText(this@MainActivity, "${e.cause}: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }
}

@Composable
fun DeleteButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
  IconButton(onClick = onClick) {
    Icon(
      imageVector = Icons.Filled.Delete,
      contentDescription = "Delete",
      tint = MaterialTheme.colorScheme.primary
    )
  }
}

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
