package br.com.prumopdf.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Cores e medidas do Prumo, as mesmas do iPhone e do Windows (cartões cinza sobre fundo cinza-claro). */
@Immutable
data class PrumoColors(
    val background: Color,
    val tile: Color,
    val search: Color,
    val text: Color,
    val secondaryText: Color,
    val placeholder: Color,
    val dark: Boolean,
)

private val Light = PrumoColors(
    background = Color(233, 233, 233),
    tile = Color(208, 208, 208),
    search = Color(220, 220, 220),
    text = Color(60, 60, 60),
    secondaryText = Color(0.42f, 0.42f, 0.42f),
    placeholder = Color(0.55f, 0.55f, 0.55f),
    dark = false,
)

private val Dark = PrumoColors(
    background = Color(0.07f, 0.07f, 0.07f),
    tile = Color(0.17f, 0.17f, 0.17f),
    search = Color(0.14f, 0.14f, 0.14f),
    text = Color(0.92f, 0.92f, 0.92f),
    secondaryText = Color(0.62f, 0.62f, 0.62f),
    placeholder = Color(0.5f, 0.5f, 0.5f),
    dark = true,
)

object Palette {
    val yellow = Color(246, 207, 70)
    val red = Color(0.89f, 0.27f, 0.23f)
    val blue = Color(0.16f, 0.47f, 0.96f)
    val green = Color(0.25f, 0.72f, 0.36f)
    val orange = Color(0.96f, 0.58f, 0.16f)
    val purple = Color(0.55f, 0.38f, 0.86f)
    val indigo = Color(0.33f, 0.36f, 0.82f)
    val teal = Color(0.05f, 0.55f, 0.53f)
    val gray = Color(0.36f, 0.39f, 0.43f)
    val pink = Color(0.92f, 0.33f, 0.53f)
}

object Dimens {
    val tileRadius = 14.dp
    val tileHeight = 86.dp
    val spacing = 12.dp
    val margin = 16.dp
}

val LocalPrumoColors = staticCompositionLocalOf { Light }

object Prumo {
    val colors: PrumoColors @Composable get() = LocalPrumoColors.current
}

@Composable
fun PrumoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = Palette.blue, background = c.background, surface = c.background, onBackground = c.text,
            onSurface = c.text, surfaceVariant = c.tile, onSurfaceVariant = c.secondaryText,
            surfaceContainer = c.tile, surfaceContainerHigh = c.tile, surfaceContainerHighest = c.search,
        )
    } else {
        lightColorScheme(
            primary = Palette.blue, background = c.background, surface = c.background, onBackground = c.text,
            onSurface = c.text, surfaceVariant = c.tile, onSurfaceVariant = c.secondaryText,
            surfaceContainer = Color(242, 242, 242), surfaceContainerHigh = Color(245, 245, 245),
            surfaceContainerHighest = c.search,
        )
    }
    CompositionLocalProvider(LocalPrumoColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
