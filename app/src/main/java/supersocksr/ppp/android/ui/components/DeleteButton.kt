package supersocksr.ppp.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DeleteButton(
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  IconButton(modifier = modifier, onClick = onClick) {
    Icon(
      imageVector = Icons.Filled.Delete,
      contentDescription = "Delete",
      tint = MaterialTheme.colorScheme.error
    )
  }
}
