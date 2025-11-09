package io.github.chimio.searchmio.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class InfoCardItem(
    val icon: ImageVector? = null,
    val title: String,
    val subtitle: String = "",
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null
)
