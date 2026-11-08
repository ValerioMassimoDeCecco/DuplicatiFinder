package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DuplicateScreen
import com.example.ui.ScannerUiEvent
import com.example.ui.ScannerViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MyApplicationTheme(darkTheme = true) {
                val viewModel: ScannerViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Re-check MANAGE_EXTERNAL_STORAGE when returning from system settings
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.onEvent(ScannerUiEvent.RefreshPermissionState)
                }

                DuplicateScreen(
                    state = uiState,
                    onEvent = viewModel::onEvent
                )
            }
        }
    }
}
