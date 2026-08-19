package com.pdv.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pdv.pos.auth.LoginScreen
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.config.ConfiguracionScreen
import com.pdv.pos.ui.HelloScreen
import com.pdv.pos.ui.theme.PdvTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PdvTheme {
                val session by sessionManager.session.collectAsState()
                if (session == null) {
                    LoginScreen()
                } else {
                    var showConfiguracion by rememberSaveable { mutableStateOf(false) }
                    if (showConfiguracion) {
                        ConfiguracionScreen(onBack = { showConfiguracion = false })
                    } else {
                        HelloScreen(onNavigateToConfiguracion = { showConfiguracion = true })
                    }
                }
            }
        }
    }
}
