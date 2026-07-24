package com.yansproject.app.ui.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.yansproject.app.data.FirebaseSyncManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val currentUserState by FirebaseSyncManager.currentUser.collectAsState()
    ProfileDetailModule(
        navController = navController,
        modifier = modifier,
        user = currentUserState
    )
}
