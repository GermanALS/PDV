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
import com.pdv.pos.caja.CajaScreen
import com.pdv.pos.config.ConfiguracionScreen
import com.pdv.pos.devolucion.DevolucionScreen
import com.pdv.pos.entrada.EntradaScreen
import com.pdv.pos.inventario.InventarioScreen
import com.pdv.pos.rol.RolScreen
import com.pdv.pos.ui.HelloScreen
import com.pdv.pos.ui.theme.PdvTheme
import com.pdv.pos.usuario.UsuarioScreen
import com.pdv.pos.venta.VentaScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private enum class Pantalla { HELLO, CONFIGURACION, VENTA, ENTRADA, INVENTARIO, CAJA, DEVOLUCIONES, USUARIOS, ROLES }

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
                    var pantalla by rememberSaveable { mutableStateOf(Pantalla.HELLO) }
                    when (pantalla) {
                        Pantalla.HELLO -> HelloScreen(
                            onNavigateToConfiguracion = { pantalla = Pantalla.CONFIGURACION },
                            onNavigateToVenta = { pantalla = Pantalla.VENTA },
                            onNavigateToEntrada = { pantalla = Pantalla.ENTRADA },
                            onNavigateToInventario = { pantalla = Pantalla.INVENTARIO },
                            onNavigateToCaja = { pantalla = Pantalla.CAJA },
                            onNavigateToDevoluciones = { pantalla = Pantalla.DEVOLUCIONES },
                            onNavigateToUsuarios = { pantalla = Pantalla.USUARIOS },
                        )
                        Pantalla.CONFIGURACION -> ConfiguracionScreen(onBack = { pantalla = Pantalla.HELLO })
                        Pantalla.VENTA -> VentaScreen(onBack = { pantalla = Pantalla.HELLO })
                        Pantalla.ENTRADA -> EntradaScreen(onBack = { pantalla = Pantalla.HELLO })
                        Pantalla.INVENTARIO -> InventarioScreen(onBack = { pantalla = Pantalla.HELLO })
                        Pantalla.CAJA -> CajaScreen(onBack = { pantalla = Pantalla.HELLO })
                        Pantalla.DEVOLUCIONES -> DevolucionScreen(onBack = { pantalla = Pantalla.HELLO })
                        Pantalla.USUARIOS -> UsuarioScreen(
                            onBack = { pantalla = Pantalla.HELLO },
                            onNavigateToRoles = { pantalla = Pantalla.ROLES },
                        )
                        Pantalla.ROLES -> RolScreen(onBack = { pantalla = Pantalla.USUARIOS })
                    }
                }
            }
        }
    }
}
