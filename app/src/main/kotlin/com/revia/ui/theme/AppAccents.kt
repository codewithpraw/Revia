package com.revia.ui.theme

import androidx.compose.ui.graphics.Color

data class AppAccent(
    val containerLight: Color,
    val onContainerLight: Color,
    val containerDark: Color,
    val onContainerDark: Color
) {
    fun container(dark: Boolean) = if (dark) containerDark else containerLight
    fun onContainer(dark: Boolean) = if (dark) onContainerDark else onContainerLight
}

private val Blue = AppAccent(Color(0xFFE6F1FB), Color(0xFF0C447C), Color(0xFF0C447C), Color(0xFF85B7EB))
private val Green = AppAccent(Color(0xFFEAF3DE), Color(0xFF27500A), Color(0xFF27500A), Color(0xFF97C459))
private val Red = AppAccent(Color(0xFFFCEBEB), Color(0xFF791F1F), Color(0xFF791F1F), Color(0xFFF09595))
private val Purple = AppAccent(Color(0xFFEEEDFE), Color(0xFF3C3489), Color(0xFF3C3489), Color(0xFFAFA9EC))
private val Amber = AppAccent(Color(0xFFFAEEDA), Color(0xFF633806), Color(0xFF633806), Color(0xFFEF9F27))
private val Teal = AppAccent(Color(0xFFE1F5EE), Color(0xFF085041), Color(0xFF085041), Color(0xFF5DCAA5))
private val Gray = AppAccent(Color(0xFFF1EFE8), Color(0xFF444441), Color(0xFF444441), Color(0xFFB4B2A9))

private val palette = listOf(Blue, Green, Red, Purple, Amber, Teal)

// Keeps a given app the same color across sessions without hardcoding a package list.
fun accentForApp(appName: String): AppAccent {
    if (appName.isBlank()) return Gray
    val index = (appName.lowercase().hashCode().toLong() and 0xFFFFFFFFL) % palette.size
    return palette[index.toInt()]
}
