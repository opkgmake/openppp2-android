package supersocksr.ppp.android

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import supersocksr.ppp.android.openppp2.PackageX
import supersocksr.ppp.android.openppp2.i.PackageInformation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import android.os.Build


const val TEST_LINK_KEY = "test_link"
const val TEST_LINK_DEFAULT = "http://cp.cloudflare.com"
const val TIMEOUT_KEY = "timeout"
const val TIMEOUT_DEFAULT = 3000L

const val ENCRYPTION_KF_KEY = "encryption_kf"
const val ENCRYPTION_KX_KEY = "encryption_kx"
const val ENCRYPTION_KL_KEY = "encryption_kl"
const val ENCRYPTION_KH_KEY = "encryption_kh"
const val ENCRYPTION_PROTOCOL_KEY = "encryption_protocol"
const val ENCRYPTION_PROTOCOL_SECRET_KEY = "encryption_protocol_secret"
const val ENCRYPTION_TRANSPORT_KEY = "encryption_transport"
const val ENCRYPTION_TRANSPORT_SECRET_KEY = "encryption_transport_secret"
const val SERVER_PROXY_KEY = "server_proxy"
const val ROUTING_MODE_KEY = "routing_mode"
const val ROUTING_ALLOWED_PACKAGES_KEY = "routing_allowed_packages"
const val ROUTING_DISALLOWED_PACKAGES_KEY = "routing_disallowed_packages"
const val ROUTING_FULL_TUNNEL_KEY = "routing_full_tunnel"
const val ROUTING_FORCE_REMOTE_DNS_KEY = "routing_force_remote_dns"

const val DEFAULT_ENCRYPTION_KF = 0x09362737
const val DEFAULT_ENCRYPTION_KX = 128
const val DEFAULT_ENCRYPTION_KL = 10
const val DEFAULT_ENCRYPTION_KH = 12
const val DEFAULT_ENCRYPTION_PROTOCOL = "aes-128-cfb"
const val DEFAULT_ENCRYPTION_PROTOCOL_SECRET = "N6HMzdUs7IUnYHwq"
const val DEFAULT_ENCRYPTION_TRANSPORT = "aes-256-cfb"
const val DEFAULT_ENCRYPTION_TRANSPORT_SECRET = "HWFweXu2g5RVMEpy"
const val DEFAULT_SERVER_PROXY = ""
const val DEFAULT_ROUTING_MODE = "global"

enum class RoutingMode(val storageValue: String) {
  GLOBAL("global"),
  WHITELIST("whitelist"),
  BLACKLIST("blacklist");

  companion object {
    fun fromStorage(value: String?): RoutingMode {
      return values().firstOrNull { it.storageValue == value } ?: GLOBAL
    }
  }
}

data class RoutingPreferences(
  val mode: RoutingMode = RoutingMode.GLOBAL,
  val whitelist: Set<String> = emptySet(),
  val blacklist: Set<String> = emptySet(),
  val fullTunnel: Boolean = false,
  val forceRemoteDns: Boolean = false,
)

data class EncryptionPreferences(
  val kf: Int = DEFAULT_ENCRYPTION_KF,
  val kx: Int = DEFAULT_ENCRYPTION_KX,
  val kl: Int = DEFAULT_ENCRYPTION_KL,
  val kh: Int = DEFAULT_ENCRYPTION_KH,
  val protocol: String = DEFAULT_ENCRYPTION_PROTOCOL,
  val protocolKey: String = DEFAULT_ENCRYPTION_PROTOCOL_SECRET,
  val transport: String = DEFAULT_ENCRYPTION_TRANSPORT,
  val transportKey: String = DEFAULT_ENCRYPTION_TRANSPORT_SECRET,
)


class Settings(val context: Context, private val preferences: SharedPreferences) {
  private val groupPaddingValues = PaddingValues(8.dp)

  @Composable
  fun SettingsScreen() {
    val context = LocalContext.current
    val state = rememberLazyListState()
    var encryptionDialogOpened by remember { mutableStateOf(false) }
    var serverProxyDialogOpened by remember { mutableStateOf(false) }
    var routingDialogOpened by remember { mutableStateOf(false) }
    var testOptionsDialogOpened by remember { mutableStateOf(false) }
    var logDialogOpened by remember { mutableStateOf(false) }

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .padding(0.dp),
      state = state
    ) {
      item {
        SettingsGroup(
          modifier = Modifier,
          title = { Text(context.getString(R.string.settings_group_vpn)) },
          contentPadding = groupPaddingValues,
          enabled = true
        ) {
          SettingsMenuLink(
            title = { Text(context.getString(R.string.encryption_settings)) },
            subtitle = { Text(context.getString(R.string.encryption_settings_subtitle)) }
          ) {
            encryptionDialogOpened = true
          }

          SettingsMenuLink(
            title = { Text(context.getString(R.string.server_proxy_settings)) },
            subtitle = { Text(context.getString(R.string.server_proxy_settings_subtitle)) }
          ) {
            serverProxyDialogOpened = true
          }

          SettingsMenuLink(
            title = { Text(context.getString(R.string.routing_settings)) },
            subtitle = { Text(context.getString(R.string.routing_settings_subtitle)) }
          ) {
            routingDialogOpened = true
          }
        }

        SettingsGroup(
          modifier = Modifier,
          title = { Text(context.getString(R.string.settings_group_other)) },
          contentPadding = groupPaddingValues,
          enabled = true
        ) {
          SettingsMenuLink(
            title = { Text(context.getString(R.string.test_options)) },
            subtitle = { Text(context.getString(R.string.test_options_subtitle)) }
          ) {
            testOptionsDialogOpened = true
          }

          SettingsMenuLink(
            title = { Text(context.getString(R.string.show_logs)) },
          ) {
            logDialogOpened = true
          }
        }
        if (encryptionDialogOpened) {
          EncryptionDialog { encryptionDialogOpened = false }
        }
        if (serverProxyDialogOpened) {
          ServerProxyDialog { serverProxyDialogOpened = false }
        }
        if (routingDialogOpened) {
          RoutingDialog { routingDialogOpened = false }
        }
        if (testOptionsDialogOpened) {
          TestOptionsDialog { testOptionsDialogOpened = false }
        }
        if (logDialogOpened) {
          LogDialog { logDialogOpened = false }
        }
      }
    }
  }

  fun getEncryptionPreferences(): EncryptionPreferences {
    return EncryptionPreferences(
      kf = preferences.getInt(ENCRYPTION_KF_KEY, DEFAULT_ENCRYPTION_KF),
      kx = preferences.getInt(ENCRYPTION_KX_KEY, DEFAULT_ENCRYPTION_KX),
      kl = preferences.getInt(ENCRYPTION_KL_KEY, DEFAULT_ENCRYPTION_KL),
      kh = preferences.getInt(ENCRYPTION_KH_KEY, DEFAULT_ENCRYPTION_KH),
      protocol = preferences.getString(ENCRYPTION_PROTOCOL_KEY, DEFAULT_ENCRYPTION_PROTOCOL)
        ?: DEFAULT_ENCRYPTION_PROTOCOL,
      protocolKey = preferences.getString(
        ENCRYPTION_PROTOCOL_SECRET_KEY,
        DEFAULT_ENCRYPTION_PROTOCOL_SECRET
      ) ?: DEFAULT_ENCRYPTION_PROTOCOL_SECRET,
      transport = preferences.getString(ENCRYPTION_TRANSPORT_KEY, DEFAULT_ENCRYPTION_TRANSPORT)
        ?: DEFAULT_ENCRYPTION_TRANSPORT,
      transportKey = preferences.getString(
        ENCRYPTION_TRANSPORT_SECRET_KEY,
        DEFAULT_ENCRYPTION_TRANSPORT_SECRET
      ) ?: DEFAULT_ENCRYPTION_TRANSPORT_SECRET
    )
  }

  fun saveEncryptionPreferences(prefs: EncryptionPreferences) {
    preferences.edit()
      .putInt(ENCRYPTION_KF_KEY, prefs.kf)
      .putInt(ENCRYPTION_KX_KEY, prefs.kx)
      .putInt(ENCRYPTION_KL_KEY, prefs.kl)
      .putInt(ENCRYPTION_KH_KEY, prefs.kh)
      .putString(ENCRYPTION_PROTOCOL_KEY, prefs.protocol)
      .putString(ENCRYPTION_PROTOCOL_SECRET_KEY, prefs.protocolKey)
      .putString(ENCRYPTION_TRANSPORT_KEY, prefs.transport)
      .putString(ENCRYPTION_TRANSPORT_SECRET_KEY, prefs.transportKey)
      .apply()
  }

  fun getServerProxy(): String {
    return preferences.getString(SERVER_PROXY_KEY, DEFAULT_SERVER_PROXY) ?: DEFAULT_SERVER_PROXY
  }

  fun saveServerProxy(value: String) {
    preferences.edit().putString(SERVER_PROXY_KEY, value).apply()
  }

  fun getRoutingPreferences(): RoutingPreferences {
    val mode = RoutingMode.fromStorage(preferences.getString(ROUTING_MODE_KEY, DEFAULT_ROUTING_MODE))
    val whitelist = preferences.getStringSet(ROUTING_ALLOWED_PACKAGES_KEY, emptySet())?.toSet() ?: emptySet()
    val blacklist = preferences.getStringSet(ROUTING_DISALLOWED_PACKAGES_KEY, emptySet())?.toSet() ?: emptySet()
    val fullTunnel = if (mode == RoutingMode.GLOBAL) {
      preferences.getBoolean(ROUTING_FULL_TUNNEL_KEY, false)
    } else {
      false
    }
    val forceRemoteDns = preferences.getBoolean(ROUTING_FORCE_REMOTE_DNS_KEY, false)
    return RoutingPreferences(
      mode = mode,
      whitelist = whitelist,
      blacklist = blacklist,
      fullTunnel = fullTunnel,
      forceRemoteDns = forceRemoteDns,
    )
  }

  fun saveRoutingPreferences(routingPreferences: RoutingPreferences) {
    preferences.edit()
      .putString(ROUTING_MODE_KEY, routingPreferences.mode.storageValue)
      .putStringSet(ROUTING_ALLOWED_PACKAGES_KEY, routingPreferences.whitelist.toSet())
      .putStringSet(ROUTING_DISALLOWED_PACKAGES_KEY, routingPreferences.blacklist.toSet())
      .putBoolean(
        ROUTING_FULL_TUNNEL_KEY,
        if (routingPreferences.mode == RoutingMode.GLOBAL) routingPreferences.fullTunnel else false
      )
      .putBoolean(ROUTING_FORCE_REMOTE_DNS_KEY, routingPreferences.forceRemoteDns)
      .apply()
  }

  private fun parseFlexibleInt(text: String): Int {
    val value = text.trim()
    if (value.isEmpty()) {
      throw IllegalArgumentException(context.getString(R.string.toast_encryption_empty))
    }
    return if (value.startsWith("0x", ignoreCase = true)) {
      value.substring(2).toLong(16).toInt()
    } else {
      value.toInt()
    }
  }

  private fun formatAsHex(value: Int): String {
    return String.format("0x%08X", value)
  }

  @Composable
  fun EncryptionDialog(onDismiss: () -> Unit) {
    val current = getEncryptionPreferences()
    var kf by remember { mutableStateOf(TextFieldValue(formatAsHex(current.kf))) }
    var kx by remember { mutableStateOf(TextFieldValue(formatAsHex(current.kx))) }
    var kl by remember { mutableStateOf(TextFieldValue(formatAsHex(current.kl))) }
    var kh by remember { mutableStateOf(TextFieldValue(formatAsHex(current.kh))) }
    var protocol by remember { mutableStateOf(TextFieldValue(current.protocol)) }
    var protocolKey by remember { mutableStateOf(TextFieldValue(current.protocolKey)) }
    var transport by remember { mutableStateOf(TextFieldValue(current.transport)) }
    var transportKey by remember { mutableStateOf(TextFieldValue(current.transportKey)) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
    val lazyListState = rememberLazyListState()

    AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
          text = context.getString(R.string.encryption_dialog_title),
          fontSize = 22.sp
        )
      },
      text = {
        LazyColumn(state = lazyListState, contentPadding = PaddingValues(4.dp)) {
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = kf,
              onValueChange = { kf = it },
              label = { Text(context.getString(R.string.encryption_kf)) },
              keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Ascii
              ),
              keyboardActions = keyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = kx,
              onValueChange = { kx = it },
              label = { Text(context.getString(R.string.encryption_kx)) },
              keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Ascii
              ),
              keyboardActions = keyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = kl,
              onValueChange = { kl = it },
              label = { Text(context.getString(R.string.encryption_kl)) },
              keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Ascii
              ),
              keyboardActions = keyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = kh,
              onValueChange = { kh = it },
              label = { Text(context.getString(R.string.encryption_kh)) },
              keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Ascii
              ),
              keyboardActions = keyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = protocol,
              onValueChange = { protocol = it },
              label = { Text(context.getString(R.string.encryption_protocol)) },
              keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
              keyboardActions = keyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = protocolKey,
              onValueChange = { protocolKey = it },
              label = { Text(context.getString(R.string.encryption_protocol_key)) },
              keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
              keyboardActions = keyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = transport,
              onValueChange = { transport = it },
              label = { Text(context.getString(R.string.encryption_transport)) },
              keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
              keyboardActions = keyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              value = transportKey,
              onValueChange = { transportKey = it },
              label = { Text(context.getString(R.string.encryption_transport_key)) },
              keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
              keyboardActions = keyboardActions
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            try {
              val prefs = EncryptionPreferences(
                kf = parseFlexibleInt(kf.text),
                kx = parseFlexibleInt(kx.text),
                kl = parseFlexibleInt(kl.text),
                kh = parseFlexibleInt(kh.text),
                protocol = protocol.text.trim(),
                protocolKey = protocolKey.text.trim(),
                transport = transport.text.trim(),
                transportKey = transportKey.text.trim()
              )
              saveEncryptionPreferences(prefs)
              Toast.makeText(context, context.getString(R.string.toast_saved_success), Toast.LENGTH_SHORT)
                .show()
              onDismiss()
            } catch (e: NumberFormatException) {
              Toast.makeText(context, context.getString(R.string.toast_encryption_invalid_number), Toast.LENGTH_SHORT)
                .show()
            } catch (e: IllegalArgumentException) {
              Toast.makeText(context, e.message ?: context.getString(R.string.toast_encryption_invalid_number), Toast.LENGTH_SHORT)
                .show()
            } catch (e: Exception) {
              Toast.makeText(context, context.getString(R.string.toast_encryption_invalid_number), Toast.LENGTH_SHORT)
                .show()
            }
          }
        ) {
          Text(context.getString(R.string.save))
        }
      },
      dismissButton = {
        Button(onClick = onDismiss) {
          Text(context.getString(R.string.cancel))
        }
      }
    )
  }

  @Composable
  fun ServerProxyDialog(onDismiss: () -> Unit) {
    var serverProxy by remember { mutableStateOf(TextFieldValue(getServerProxy())) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
          text = context.getString(R.string.server_proxy_dialog_title),
          fontSize = 22.sp
        )
      },
      text = {
        OutlinedTextField(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          value = serverProxy,
          onValueChange = { serverProxy = it },
          label = { Text(context.getString(R.string.server_proxy_label)) },
          placeholder = { Text(context.getString(R.string.server_proxy_placeholder)) },
          keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Uri
          ),
          keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
        )
      },
      confirmButton = {
        Button(
          onClick = {
            saveServerProxy(serverProxy.text.trim())
            Toast.makeText(context, context.getString(R.string.toast_saved_success), Toast.LENGTH_SHORT).show()
            onDismiss()
          }
        ) {
          Text(context.getString(R.string.save))
        }
      },
      dismissButton = {
        Button(onClick = onDismiss) {
          Text(context.getString(R.string.cancel))
        }
      }
    )
  }

  @Composable
  fun RoutingDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initialPreferences = remember { getRoutingPreferences() }
    var mode by remember { mutableStateOf(initialPreferences.mode) }
    var whitelist by remember { mutableStateOf(initialPreferences.whitelist) }
    var blacklist by remember { mutableStateOf(initialPreferences.blacklist) }
    var apps by remember { mutableStateOf(listOf<PackageInformation>()) }
    var loadingApps by remember { mutableStateOf(true) }
    var fullTunnel by remember { mutableStateOf(initialPreferences.fullTunnel) }
    var forceRemoteDns by remember { mutableStateOf(initialPreferences.forceRemoteDns) }

    LaunchedEffect(Unit) {
      loadingApps = true
      val loadedApps = withContext(Dispatchers.IO) {
        loadRoutingPackages(context)
      }
      apps = loadedApps.sortedBy { (it.applicationName ?: it.packageName ?: "").lowercase() }
      loadingApps = false
    }

    AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
          text = context.getString(R.string.routing_dialog_title),
          fontSize = 22.sp
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          RoutingMode.values().forEach { option ->
            val labelRes = when (option) {
              RoutingMode.GLOBAL -> R.string.routing_mode_global
              RoutingMode.WHITELIST -> R.string.routing_mode_whitelist
              RoutingMode.BLACKLIST -> R.string.routing_mode_blacklist
            }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  mode = option
                  if (option != RoutingMode.GLOBAL) {
                    fullTunnel = false
                  }
                },
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = mode == option,
                onClick = {
                  mode = option
                  if (option != RoutingMode.GLOBAL) {
                    fullTunnel = false
                  }
                }
              )
              Text(
                text = context.getString(labelRes),
                modifier = Modifier
                  .padding(start = 8.dp)
                  .weight(1f)
              )
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { forceRemoteDns = !forceRemoteDns },
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = forceRemoteDns,
              onCheckedChange = { checked -> forceRemoteDns = checked }
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
              Text(text = context.getString(R.string.routing_force_remote_dns_label))
              Text(
                text = context.getString(R.string.routing_force_remote_dns_description),
                fontSize = 12.sp,
                color = Color.Gray
              )
            }
          }

          when (mode) {
            RoutingMode.GLOBAL -> {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { fullTunnel = !fullTunnel },
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = fullTunnel,
                  onCheckedChange = { checked -> fullTunnel = checked }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                  Text(text = context.getString(R.string.routing_full_tunnel_label))
                  Text(
                    text = context.getString(R.string.routing_full_tunnel_description),
                    fontSize = 12.sp,
                    color = Color.Gray
                  )
                }
              }
              val hintRes = if (fullTunnel) {
                R.string.routing_full_tunnel_hint_enabled
              } else {
                R.string.routing_mode_global_hint
              }
              Text(text = context.getString(hintRes))
            }

            RoutingMode.WHITELIST, RoutingMode.BLACKLIST -> {
              val hintRes = if (mode == RoutingMode.WHITELIST) {
                R.string.routing_app_list_whitelist
              } else {
                R.string.routing_app_list_blacklist
              }
              Text(
                text = context.getString(hintRes),
                fontWeight = FontWeight.Medium
              )
              if (loadingApps) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center
                ) {
                  CircularProgressIndicator()
                }
              } else if (apps.isEmpty()) {
                Text(text = context.getString(R.string.routing_empty_apps))
              } else {
                LazyColumn(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                ) {
                  items(apps) { app ->
                    val packageName = app.packageName ?: return@items
                    val isChecked = when (mode) {
                      RoutingMode.WHITELIST -> whitelist.contains(packageName)
                      RoutingMode.BLACKLIST -> blacklist.contains(packageName)
                      else -> false
                    }
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                          when (mode) {
                            RoutingMode.WHITELIST -> {
                              whitelist = whitelist.toMutableSet().also {
                                if (!isChecked) {
                                  it.add(packageName)
                                } else {
                                  it.remove(packageName)
                                }
                              }
                            }

                            RoutingMode.BLACKLIST -> {
                              blacklist = blacklist.toMutableSet().also {
                                if (!isChecked) {
                                  it.add(packageName)
                                } else {
                                  it.remove(packageName)
                                }
                              }
                            }

                            else -> {}
                          }
                        }
                        .padding(vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                          when (mode) {
                            RoutingMode.WHITELIST -> {
                              whitelist = whitelist.toMutableSet().also {
                                if (checked) {
                                  it.add(packageName)
                                } else {
                                  it.remove(packageName)
                                }
                              }
                            }

                            RoutingMode.BLACKLIST -> {
                              blacklist = blacklist.toMutableSet().also {
                                if (checked) {
                                  it.add(packageName)
                                } else {
                                  it.remove(packageName)
                                }
                              }
                            }

                            else -> {}
                          }
                        }
                      )
                      Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                          text = app.applicationName ?: packageName,
                          fontSize = 16.sp,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        Text(
                          text = packageName,
                          fontSize = 12.sp,
                          color = Color.Gray
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            saveRoutingPreferences(
              RoutingPreferences(
                mode = mode,
                whitelist = whitelist,
                blacklist = blacklist,
                fullTunnel = if (mode == RoutingMode.GLOBAL) fullTunnel else false,
                forceRemoteDns = forceRemoteDns
              )
            )
            Toast.makeText(context, context.getString(R.string.toast_saved_success), Toast.LENGTH_SHORT).show()
            onDismiss()
          }
        ) {
          Text(context.getString(R.string.save))
        }
      },
      dismissButton = {
        Button(onClick = onDismiss) {
          Text(context.getString(R.string.cancel))
        }
      }
    )
  }

  private fun loadRoutingPackages(context: Context): List<PackageInformation> {
    val combined = LinkedHashMap<String, PackageInformation>()

    fun addPackage(info: PackageInformation?) {
      val packageName = info?.packageName?.takeIf { it.isNotBlank() } ?: return
      if (info.applicationName.isNullOrBlank()) {
        info.applicationName = packageName
      }
      combined[packageName] = info
    }

    val defaultApps = try {
      PackageX.package_get_all_network_application(context)
    } catch (e: Exception) {
      emptyList()
    }
    defaultApps.forEach(::addPackage)

    val fallbackApps = loadInstalledPackages(context)
    fallbackApps.forEach(::addPackage)

    return combined.values.toList()
  }

  private fun loadInstalledPackages(context: Context): List<PackageInformation> {
    val packageManager = context.packageManager ?: return emptyList()
    val installedPackages: List<PackageInfo> = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getInstalledPackages(
          PackageManager.PackageInfoFlags.of(
            (PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS).toLong()
          )
        )
      } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS)
      }
    } catch (e: Exception) {
      return emptyList()
    }

    val selfPackageName = context.packageName
    val packages = mutableListOf<PackageInformation>()

    installedPackages.forEach { packageInfo ->
      val packageName = packageInfo.packageName ?: return@forEach
      if (packageName == selfPackageName) {
        return@forEach
      }

      val applicationInfo = packageInfo.applicationInfo ?: return@forEach

      try {
        val label = applicationInfo.loadLabel(packageManager)?.toString()
        val info = PackageInformation().apply {
          this.packageName = packageName
          this.applicationName = if (!label.isNullOrBlank()) label else packageName
          this.applicationIcon = applicationInfo.loadIcon(packageManager)
        }
        packages.add(info)
      } catch (ignored: Exception) {
        // Skip apps that cannot provide label or icon
      }
    }

    return packages
  }

  @Composable
  fun TestOptionsDialog(onDismiss: () -> Unit) {
    var link by remember {
      mutableStateOf(
        TextFieldValue(
          preferences.getString(TEST_LINK_KEY, TEST_LINK_DEFAULT)!!
        )
      )
    }
    var timeout by remember {
      mutableStateOf(
        TextFieldValue(
          preferences.getLong(TIMEOUT_KEY, TIMEOUT_DEFAULT).toString()
        )
      )
    }
    val context = LocalContext.current
    val localSoftwareKeyboardController = LocalSoftwareKeyboardController.current
    val dialogKeyboardActions =
      KeyboardActions(onDone = { localSoftwareKeyboardController?.hide() })
    val dialogKeyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
    val dialogTextFieldModifier = Modifier
      .fillMaxWidth()
      .padding(8.dp)

    AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = context.getString(R.string.test_options),
            modifier = Modifier
              .weight(1f)
              .padding(end = 8.dp),
            fontSize = 22.sp
          )
        }
      },
      text = {
        val lazyListState = rememberLazyListState()
        LazyColumn(state = lazyListState, contentPadding = PaddingValues(4.dp)) {
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = link,
              onValueChange = { link = it },
              label = { Text(context.getString(R.string.test_link)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions
            )
          }
          item {
            OutlinedTextField(
              modifier = dialogTextFieldModifier,
              value = timeout,
              onValueChange = { timeout = it },
              label = { Text(context.getString(R.string.test_timeout)) },
              keyboardOptions = dialogKeyboardOptions,
              keyboardActions = dialogKeyboardActions
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            try {
              preferences.edit().putString(TEST_LINK_KEY, link.text.trim()).apply()
              preferences.edit().putInt(TIMEOUT_KEY, timeout.text.trim().toInt()).apply()
              onDismiss()
            } catch (e: Exception) {
              Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
          }
        ) {
          Text(context.getString(R.string.save))
        }
      },
      dismissButton = {
        Button(
          onClick = onDismiss
        ) {
          Text(context.getString(R.string.cancel))
        }
      }
    )
  }

  @Composable
  fun LogDialog(onDismiss: () -> Unit) {
    var logLines by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = { onDismiss() }) {
      Column(
        modifier = Modifier
          .padding(8.dp)
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        SelectionContainer { Text(text = logLines) }

        LaunchedEffect(Unit) {
          try {
            val process = Runtime.getRuntime().exec("logcat -d *:E")
            val bufferedReader = process.inputStream.bufferedReader()
            while (true) {
              val line = bufferedReader.readLine() ?: break
              logLines += "$line\n"
              scrollState.scrollTo(scrollState.maxValue)
            }
            bufferedReader.close()
          } catch (e: Exception) {
            Log.e("LogActivity", "Error reading logs", e)
          }
        }
      }
    }
  }
}