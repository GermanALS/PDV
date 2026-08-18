package com.pdv.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pdv.pos.ui.HelloScreen
import com.pdv.pos.ui.theme.PdvTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PdvTheme {
                HelloScreen()
            }
        }
    }
}
