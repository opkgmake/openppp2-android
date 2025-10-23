package supersocksr.ppp.android.ui.home

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import supersocksr.ppp.android.R
import supersocksr.ppp.android.ui.components.DeleteButton
import supersocksr.ppp.android.utils.Address
import supersocksr.ppp.android.utils.UserConfig

@Composable
fun OpenPppApp(
  state: MainUiState,
  onSelectConfig: (Int?) -> Unit,
  onAddConfig: () -> Unit,
  onEditConfig: (Int) -> Unit,
  onDismissEditor: () -> Unit,
  onSaveConfig: (UserConfig) -> Unit,
  onDeleteConfig: () -> Unit,
  onStartVpn: () -> Unit,
  onStopVpn: () -> Unit,
  onTestConnection: () -> Unit,
  settingsContent: @Composable () -> Unit
) {
  var showSettings by remember { mutableStateOf(false) }
  val backgroundColor = MaterialTheme.colorScheme.background

  Scaffold(
    containerColor = backgroundColor,
    topBar = {
      DashboardTopBar(
        state = state,
        onOpenSettings = { showSettings = true }
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onAddConfig,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.action_add_config))
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(backgroundColor)
        .padding(innerPadding)
        .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      ConnectionStatusPanel(
        state = state,
        onTestConnection = onTestConnection
      )

      ConfigList(
        state = state,
        modifier = Modifier.weight(1f, fill = true),
        onSelectConfig = onSelectConfig,
        onEditConfig = onEditConfig
      )

      ControlPanel(
        vpnRunning = state.vpnRunning,
        isTestingConnection = state.isTestingConnection,
        testStatus = state.testStatus,
        onStartVpn = onStartVpn,
        onStopVpn = onStopVpn,
        onTestConnection = onTestConnection
      )
    }
  }

  if (showSettings) {
    SettingsDialog(onDismiss = { showSettings = false }) {
      settingsContent()
    }
  }

  state.editorState?.let { editor ->
    EditConfigDialog(
      editor = editor,
      onDismiss = onDismissEditor,
      onSave = onSaveConfig,
      onDelete = onDeleteConfig
    )
  }
}

@Composable
private fun DashboardTopBar(
  state: MainUiState,
  onOpenSettings: () -> Unit
) {
  val titleColor by animateColorAsState(
    targetValue = if (state.vpnRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
    animationSpec = tween(450),
    label = "topBarTitle"
  )
  CenterAlignedTopAppBar(
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
      containerColor = MaterialTheme.colorScheme.background,
      titleContentColor = MaterialTheme.colorScheme.onBackground
    ),
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = stringResource(id = R.string.dashboard_title),
          style = MaterialTheme.typography.titleLarge,
          color = titleColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        val subtitle = state.selectedConfig?.let { config ->
          config.name.takeIf { it.isNotBlank() } ?: config.server.host.orEmpty()
        } ?: stringResource(id = R.string.status_no_config)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    },
    actions = {
      IconButton(onClick = onOpenSettings) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = stringResource(id = R.string.action_settings),
          tint = MaterialTheme.colorScheme.onBackground
        )
      }
    }
  )
}

@Composable
private fun ConnectionStatusPanel(
  state: MainUiState,
  onTestConnection: () -> Unit
) {
  val statusColor = if (state.vpnRunning) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.error
  }
  val statusLabel = if (state.vpnRunning) {
    stringResource(id = R.string.status_connected)
  } else {
    stringResource(id = R.string.status_disconnected)
  }
  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor
          )
          Text(
            text = state.selectedConfig?.server?.toString()
              ?: stringResource(id = R.string.status_select_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        StatusPill(text = statusLabel, color = statusColor)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.label_last_latency),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = if (state.vpnRunning) state.testStatus else stringResource(id = R.string.status_latency_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        FilledTonalButton(
          onClick = onTestConnection,
          enabled = state.vpnRunning && state.isTestingConnection.not(),
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Icon(Icons.Outlined.Speed, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (state.isTestingConnection) stringResource(id = R.string.testing) else stringResource(id = R.string.action_test)
          )
        }
      }
    }
  }
}

@Composable
private fun ConfigList(
  state: MainUiState,
  modifier: Modifier = Modifier,
  onSelectConfig: (Int?) -> Unit,
  onEditConfig: (Int) -> Unit
) {
  val configs = state.configs
  if (configs.isEmpty()) {
    Surface(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      tonalElevation = 4.dp,
      color = MaterialTheme.colorScheme.surfaceVariant
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = stringResource(id = R.string.empty_configs_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = stringResource(id = R.string.empty_configs_message),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    return
  }

  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    contentPadding = PaddingValues(bottom = 12.dp)
  ) {
    itemsIndexed(configs, key = { index, config -> "$index-${config.guid}" }) { index, config ->
      val isSelected = index == state.selectedIndex
      ConfigCard(
        index = index,
        config = config,
        selected = isSelected,
        onSelect = {
          if (isSelected) {
            onSelectConfig(null)
          } else {
            onSelectConfig(index)
          }
        },
        onEdit = {
          onEditConfig(index)
        }
      )
    }
  }
}

@Composable
private fun ConfigCard(
  index: Int,
  config: UserConfig,
  selected: Boolean,
  onSelect: () -> Unit,
  onEdit: () -> Unit
) {
  val targetBorderWidth by animateDpAsState(
    targetValue = if (selected) 2.dp else 1.dp,
    animationSpec = tween(durationMillis = 220),
    label = "border"
  )
  val borderColor by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    animationSpec = tween(durationMillis = 220),
    label = "borderColor"
  )
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect),
    shape = RoundedCornerShape(20.dp),
    tonalElevation = if (selected) 6.dp else 2.dp,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(targetBorderWidth, borderColor)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp, horizontal = 20.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = config.name.takeIf { it.isNotBlank() }
              ?: stringResource(id = R.string.config_fallback_title, index + 1),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = config.server.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (selected) {
          StatusPill(text = stringResource(id = R.string.label_selected), color = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(onClick = onEdit) {
          Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(id = R.string.action_edit),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        InfoColumn(title = stringResource(id = R.string.label_server_ip), value = config.server.host ?: config.server.ip ?: "-")
        InfoColumn(title = stringResource(id = R.string.label_static_ip), value = config.static_server.host ?: config.static_server.ip ?: "-")
        InfoColumn(title = stringResource(id = R.string.label_tun_ip), value = config.tun_address?.toString() ?: "-")
      }
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedButton(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
      ) {
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.action_choose_config))
      }
    }
  }
}

@Composable
private fun InfoColumn(title: String, value: String) {
  Column(modifier = Modifier.weight(1f)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value.ifBlank { "-" },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun ControlPanel(
  vpnRunning: Boolean,
  isTestingConnection: Boolean,
  testStatus: String,
  onStartVpn: () -> Unit,
  onStopVpn: () -> Unit,
  onTestConnection: () -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Button(
        onClick = onStartVpn,
        enabled = vpnRunning.not(),
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.vpn_start))
      }
      Button(
        onClick = onStopVpn,
        enabled = vpnRunning,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError
        )
      ) {
        Icon(Icons.Filled.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.vpn_stop))
      }
    }
    OutlinedButton(
      onClick = onTestConnection,
      enabled = vpnRunning && isTestingConnection.not(),
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
      Icon(Icons.Outlined.Speed, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = if (vpnRunning) testStatus else stringResource(id = R.string.action_test))
    }
  }
}

@Composable
private fun StatusPill(text: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(50),
    color = color.copy(alpha = 0.18f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
      style = MaterialTheme.typography.labelMedium,
      color = color
    )
  }
}

@Composable
private fun SettingsDialog(
  onDismiss: () -> Unit,
  content: @Composable () -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 10.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(id = R.string.button_close),
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 520.dp)
        ) {
          content()
        }
      }
    }
  }
}

@Composable
private fun EditConfigDialog(
  editor: EditorState,
  onDismiss: () -> Unit,
  onSave: (UserConfig) -> Unit,
  onDelete: () -> Unit
) {
  var name by remember { mutableStateOf(TextFieldValue(editor.config.name)) }
  var server by remember { mutableStateOf(TextFieldValue(editor.config.server.toString())) }
  var guid by remember { mutableStateOf(TextFieldValue(editor.config.guid)) }
  var staticServer by remember { mutableStateOf(TextFieldValue(editor.config.static_server.toString())) }
  var tunAddress by remember { mutableStateOf(TextFieldValue(editor.config.tun_address?.toString() ?: "")) }
  val context = LocalContext.current
  val keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
  val keyboardActions = KeyboardActions(onDone = { /* handled by buttons */ })

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      shape = RoundedCornerShape(24.dp),
      color = AlertDialogDefaults.containerColor,
      tonalElevation = 8.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = stringResource(id = R.string.config_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
          DeleteButton(onClick = onDelete)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(id = R.string.config_name)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
          OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text(stringResource(id = R.string.config_server)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
          OutlinedTextField(
            value = guid,
            onValueChange = { guid = it },
            label = { Text(stringResource(id = R.string.config_guid)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true
          )
          OutlinedTextField(
            value = staticServer,
            onValueChange = { staticServer = it },
            label = { Text(stringResource(id = R.string.config_static)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
          OutlinedTextField(
            value = tunAddress,
            onValueChange = { tunAddress = it },
            label = { Text(stringResource(id = R.string.config_tun)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
          ) {
            Text(text = stringResource(id = R.string.cancel))
          }
          Button(
            onClick = {
              runCatching {
                val config = UserConfig(
                  name = name.text.trim(),
                  server = Address.unsafeParse(server.text.trim()),
                  static_server = Address.unsafeParse(staticServer.text.trim()),
                  guid = guid.text.trim(),
                  tun_address = tunAddress.text.trim().let { input ->
                    if (input.isBlank()) null else Address.parse(input)
                  }
                ).also { it.validate() }
                onSave(config)
              }.onFailure { throwable ->
                Toast.makeText(context, "Invalid Address: ${throwable.message}", Toast.LENGTH_LONG).show()
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text(text = stringResource(id = R.string.save))
          }
        }
      }
    }
  }
}
