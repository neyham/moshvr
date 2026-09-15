package dev.neyham.moshvr.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Matrix-inspired dark palette.
object MoshVrColors {
    val Background = Color(0xFF0B0F0C)
    val Surface = Color(0xFF111712)
    val SurfaceBright = Color(0xFF1A231C)
    val Green = Color(0xFF00FF66)
    val GreenDim = Color(0xFF00B34A)
    val TextPrimary = Color(0xFFD9FFE8)
    val TextSecondary = Color(0xFF7BA98C)
    val Error = Color(0xFFFF5555)
}

private val MoshVrDarkScheme = darkColorScheme(
    primary = MoshVrColors.Green,
    onPrimary = Color.Black,
    secondary = MoshVrColors.GreenDim,
    onSecondary = Color.Black,
    background = MoshVrColors.Background,
    onBackground = MoshVrColors.TextPrimary,
    surface = MoshVrColors.Surface,
    onSurface = MoshVrColors.TextPrimary,
    surfaceVariant = MoshVrColors.SurfaceBright,
    onSurfaceVariant = MoshVrColors.TextSecondary,
    error = MoshVrColors.Error,
)

val PanelShape = RoundedCornerShape(24.dp)

@Composable
fun MoshVrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MoshVrDarkScheme,
        content = content,
    )
}
