package com.yansproject.app.ui.theme

import androidx.compose.runtime.Composable

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MyApplicationTheme(
        darkTheme = true,
        content = content
    )
}
