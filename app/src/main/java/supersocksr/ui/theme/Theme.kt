package supersocksr.ppp.android.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = ElectricBlue,
  onPrimary = Color(0xFF001018),
  secondary = NeonGreen,
  onSecondary = Color(0xFF00210E),
  tertiary = Ember,
  onTertiary = Color(0xFF230800),
  background = Midnight,
  onBackground = Snow,
  surface = Obsidian,
  onSurface = Snow,
  surfaceVariant = Graphite,
  onSurfaceVariant = Mist,
  outline = Steel,
  error = SignalRed,
  onError = Color(0xFF180003)
)

private val LightColorScheme = lightColorScheme(
  primary = ElectricBlue,
  onPrimary = Color.Black,
  secondary = NeonGreen,
  onSecondary = Color.Black,
  tertiary = Ember,
  onTertiary = Color.Black,
  background = Snow,
  onBackground = Color(0xFF101217),
  surface = Color(0xFFF8F9FA),
  onSurface = Color(0xFF101217)
)

@Composable
fun Openppp2Theme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
