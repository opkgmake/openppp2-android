package supersocksr.ppp.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.collectLatest
import supersocksr.ppp.android.data.ConfigRepository
import supersocksr.ppp.android.data.SettingsRepository
import supersocksr.ppp.android.openppp2.IPAddressX
import supersocksr.ppp.android.openppp2.Macro
import supersocksr.ppp.android.openppp2.VPN
import supersocksr.ppp.android.openppp2.VPNLinkConfiguration
import supersocksr.ppp.android.ui.home.MainViewModel
import supersocksr.ppp.android.ui.home.OpenPppApp
import supersocksr.ppp.android.ui.theme.Openppp2Theme
import supersocksr.ppp.android.utils.RawReader

const val TAG = "MainActivity"
const val ALL_CONFIGS_KEY = "all_configs"

class MainActivity : PppVpnActivity() {
  private lateinit var configPreferences: SharedPreferences
  private lateinit var settingsPreferences: SharedPreferences
  private lateinit var settings: Settings
  private lateinit var viewModel: MainViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configPreferences = getSharedPreferences("config_list", Context.MODE_PRIVATE)
    settingsPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
    settings = Settings(this, settingsPreferences)

    val configRepository = ConfigRepository(configPreferences)
    val settingsRepository = SettingsRepository(settingsPreferences)
    viewModel = ViewModelProvider(
      this,
      MainViewModel.provideFactory(configRepository, settingsRepository)
    )[MainViewModel::class.java]

    setContent {
      val uiState by viewModel.uiState.collectAsState()
      val context = LocalContext.current

      LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
          when (event) {
            MainViewModel.Event.ConfigNotSelected -> {
              Toast.makeText(context, context.getString(R.string.warn_config_not_select), Toast.LENGTH_SHORT).show()
            }

            MainViewModel.Event.DeserializationFailed -> {
              Toast.makeText(context, context.getString(R.string.warn_deserialize), Toast.LENGTH_LONG).show()
            }

            is MainViewModel.Event.ShowError -> {
              val throwable = (event as MainViewModel.Event.ShowError).throwable
              Toast.makeText(context, throwable.localizedMessage ?: throwable.message ?: throwable.toString(), Toast.LENGTH_LONG)
                .show()
            }
          }
        }
      }

      Openppp2Theme {
        OpenPppApp(
          state = uiState,
          onSelectConfig = viewModel::onSelectConfig,
          onAddConfig = viewModel::onAddConfig,
          onEditConfig = viewModel::onEditConfig,
          onDismissEditor = viewModel::onDismissEditor,
          onSaveConfig = viewModel::onConfigSaved,
          onDeleteConfig = viewModel::onConfigDeleted,
          onStartVpn = {
            if (viewModel.onStartVpn()) {
              vpn_run()
            }
          },
          onStopVpn = {
            vpn_stop()
            viewModel.onStopVpn()
          },
          onTestConnection = { viewModel.onTestConnection() },
          settingsContent = { settings.SettingsScreen() }
        )
      }
    }
  }

  override fun vpn_load(): VPNLinkConfiguration? {
    val config = viewModel.selectedConfig ?: return null
    Log.i(TAG, "running using config: $config")
    val rawReader = RawReader(resources)
    return VPNLinkConfiguration().apply {
      SubnetAddress = "255.255.255.0"
      IPAddress = config.tun_address.toString()
      Log.d(TAG, "IPAddress: $IPAddress")
      GatewayServer = IPAddressX.address_calc_first_address(IPAddress, SubnetAddress)

      VirtualSubnet = true
      BlockQUIC = false
      StaticMode = config.tun_address != null
      Log.d(TAG, "StaticMode: $StaticMode")
      FlashMode = false
      AtomicHttpProxySet = false
      DnsAddresses.apply {
        add("8.8.8.8")
        add("8.8.4.4")
      }

      BypassIpList = rawReader.readRawResource(R.raw.ip)
      DNSRuleList = rawReader.readRawResource(R.raw.domain)
      AllowedApplicationPackageNames.add(packageName)
      DisallowedApplicationPackageNames.add(packageName)

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
            servers.add(config.static_server.toString())
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
          guid = config.guid
          Log.d(TAG, "client guid: $guid")
          server = VPN.vpn_link_of(config.server.toString())!!.url
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
  }
}
