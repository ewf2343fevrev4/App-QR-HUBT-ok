package com.smartattendance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.smartattendance.app.core.AppViewModel
import com.smartattendance.app.navigation.AppNavigation
import com.smartattendance.app.ui.theme.SmartTheme

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val vm: AppViewModel=viewModel(); val state by vm.state.collectAsStateWithLifecycle()
            SmartTheme(state.theme) { AppNavigation(vm,state) }
        }
    }
}
