// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun temaEscuro(tema: String): Boolean = when (tema) {
    "escuro" -> true
    "claro" -> false
    else -> isSystemInDarkTheme()
}

/** Tema claro/escuro/sistema com a cor de destaque escolhida (mesma ideia do app de computador). */
@Composable
fun HshTema(tema: String, acento: Int, content: @Composable () -> Unit) {
    val cor = Color(acento)
    val esquema = if (temaEscuro(tema)) {
        darkColorScheme(
            primary = cor, onPrimary = Color.White, secondary = cor,
            background = Color(0xFF1F2024), surface = Color(0xFF2A2B30),
            onBackground = Color(0xFFE8E8EA), onSurface = Color(0xFFE8E8EA),
            surfaceVariant = Color(0xFF3A3B42), onSurfaceVariant = Color(0xFFB8B8C0)
        )
    } else {
        lightColorScheme(
            primary = cor, onPrimary = Color.White, secondary = cor,
            background = Color(0xFFE9EBEF), surface = Color.White,
            onBackground = Color(0xFF1D1F24), onSurface = Color(0xFF1D1F24),
            surfaceVariant = Color(0xFFDDE1E8), onSurfaceVariant = Color(0xFF555B66)
        )
    }
    MaterialTheme(colorScheme = esquema, content = content)
}
