package com.findora.app.feature.savedsearch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Wraps "save this search" so POST_NOTIFICATIONS (Android 13+) is asked first, since a saved search exists to
 * alert. The search is saved whatever the answer: alerts still reach the in-app notifications center.
 */
@Composable
fun rememberSaveWithNotificationPermission(save: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val currentSave by rememberUpdatedState(save)
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingName?.let(currentSave)
            pendingName = null
        }
    return { name ->
        val needsPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingName = name
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            currentSave(name)
        }
    }
}
