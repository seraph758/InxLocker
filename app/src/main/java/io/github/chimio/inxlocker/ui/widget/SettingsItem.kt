package io.github.chimio.inxlocker.ui.widget

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsItem(
    val icon: ImageVector? = null,
    val drawableIcon: Drawable? = null,
    val title: String,
    val subtitle: String = "",
    val onClick: () -> Unit
)