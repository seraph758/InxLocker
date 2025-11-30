package io.github.chimio.inxlocker.ui.widget

import androidx.compose.ui.graphics.vector.ImageVector

data class SwitchItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String = "",
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)
