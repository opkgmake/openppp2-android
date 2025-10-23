package supersocksr.ppp.android.ui.home

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import supersocksr.ppp.android.R
import supersocksr.ppp.android.ui.components.DeleteButton
import supersocksr.ppp.android.ui.theme.Pink500
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
  val navController = rememberNavController()
  val selectedTab = remember { mutableIntStateOf(0) }

  Scaffold(
    bottomBar = {
      BottomNavigationBar(
        navController = navController,
        selectedTab = selectedTab
      )
    }
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = Route.HOME,
      modifier = Modifier.padding(innerPadding)
    ) {
      composable(Route.HOME) {
        ConfigSelectionScreen(
          state = state,
          onSelectConfig = onSelectConfig,
          onAddConfig = onAddConfig,
          onEditConfig = onEditConfig,
          onDismissEditor = onDismissEditor,
          onSaveConfig = onSaveConfig,
          onDeleteConfig = onDeleteConfig,
          onStartVpn = onStartVpn,
          onStopVpn = onStopVpn,
          onTestConnection = onTestConnection
        )
      }
      composable(Route.SETTINGS) {
        settingsContent()
      }
    }
  }
}

private object Route {
  const val HOME = "home"
  const val SETTINGS = "settings"
}

@Composable
private fun BottomNavigationBar(
  navController: NavHostController,
  selectedTab: MutableIntState
) {
  data class NavItem(val route: String, val label: String, val icon: ImageVector)

  val items = listOf(
    NavItem(Route.HOME, stringResource(R.string.nav_home), Icons.Default.Home),
    NavItem(Route.SETTINGS, stringResource(R.string.nav_settings), Icons.Default.Settings)
  )

  NavigationBar(tonalElevation = 8.dp) {
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
            restoreState = true
            popUpTo(Route.HOME) { saveState = true }
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
private fun ConfigSelectionScreen(
  state: MainUiState,
  onSelectConfig: (Int?) -> Unit,
  onAddConfig: () -> Unit,
  onEditConfig: (Int) -> Unit,
  onDismissEditor: () -> Unit,
  onSaveConfig: (UserConfig) -> Unit,
  onDeleteConfig: () -> Unit,
  onStartVpn: () -> Unit,
  onStopVpn: () -> Unit,
  onTestConnection: () -> Unit
) {
  val itemModifier = Modifier
    .aspectRatio(1.85f, true)
    .padding(20.dp)

  Column {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(120.dp),
      modifier = Modifier
        .weight(1f)
        .clickable(interactionSource = null, indication = null) {
          if (state.editorState != null) {
            onDismissEditor()
          } else {
            onSelectConfig(null)
          }
        },
      contentPadding = PaddingValues(8.dp)
    ) {
      itemsIndexed(state.configs) { index, config ->
        val isSelected = index == state.selectedIndex
        val borderWidth by animateDpAsState(
          targetValue = if (isSelected) 4.dp else 0.dp,
          animationSpec = tween(durationMillis = 200),
          label = "border"
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
                onEditConfig(index)
              } else {
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
            .clickable { onAddConfig() },
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
      val context = LocalContext.current
      val startButtonLabel = if (state.vpnRunning) {
        state.selectedConfig?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.vpn_start)
      } else {
        context.getString(R.string.vpn_start)
      }

      Button(
        onClick = onStartVpn,
        enabled = state.vpnRunning.not()
      ) {
        Text(startButtonLabel)
      }

      if (state.vpnRunning) {
        Button(
          onClick = onTestConnection,
          enabled = state.isTestingConnection.not()
        ) {
          Text(state.testStatus)
        }
      }

      Button(onClick = onStopVpn) {
        Text(stringResource(R.string.vpn_stop))
      }
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
  val keyboardActions = KeyboardActions(onDone = { /* handled by dialog */ })
  val textFieldModifier = Modifier
    .fillMaxWidth()
    .padding(8.dp)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.config_title),
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
          fontSize = 22.sp
        )
        DeleteButton { onDelete() }
      }
    },
    text = {
      val lazyListState = rememberLazyListState()
      LazyColumn(state = lazyListState, contentPadding = PaddingValues(4.dp)) {
        item {
          OutlinedTextField(
            modifier = textFieldModifier,
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.config_name)) },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
        }
        item {
          OutlinedTextField(
            modifier = textFieldModifier,
            value = server,
            onValueChange = { server = it },
            label = { Text(stringResource(R.string.config_server)) },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
        }
        item {
          OutlinedTextField(
            modifier = textFieldModifier,
            value = guid,
            onValueChange = { guid = it },
            label = { Text(stringResource(R.string.config_guid)) },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            readOnly = true
          )
        }
        item {
          OutlinedTextField(
            modifier = textFieldModifier,
            value = staticServer,
            onValueChange = { staticServer = it },
            label = { Text(stringResource(R.string.config_static)) },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
        }
        item {
          OutlinedTextField(
            modifier = textFieldModifier,
            value = tunAddress,
            onValueChange = { tunAddress = it },
            label = { Text(stringResource(R.string.config_tun)) },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
          )
        }
      }
    },
    confirmButton = {
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
        }
      ) {
        Text(stringResource(R.string.save))
      }
    },
    dismissButton = {
      Button(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    }
  )
}
