package com.yansproject.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.yansproject.app.ui.about.AboutScreen

@Composable
fun AppInfoScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    AboutScreen(
        onBack = { navController.popBackStack() }
    )
}
