package com.pdv.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdv.pos.auth.LoginScreen
import com.pdv.pos.auth.SessionManager
import com.pdv.pos.caja.CajaScreen
import com.pdv.pos.config.ConfiguracionScreen
import com.pdv.pos.conflictos.RevisionConflictosScreen
import com.pdv.pos.devolucion.DevolucionScreen
import com.pdv.pos.entrada.EntradaScreen
import com.pdv.pos.ia.AsistenteIaWidget
import com.pdv.pos.inventario.InventarioScreen
import com.pdv.pos.inventario.importacion.ImportarCatalogoScreen
import com.pdv.pos.rol.RolScreen
import com.pdv.pos.ui.HelloScreen
import com.pdv.pos.ui.theme.PdvTheme
import com.pdv.pos.usuario.UsuarioScreen
import com.pdv.pos.venta.VentaScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private enum class Pantalla {
    HELLO, CONFIGURACION, VENTA, ENTRADA, INVENTARIO, CAJA, DEVOLUCIONES, USUARIOS, ROLES, IMPORTAR_CATALOGO, CONFLICTOS,
}

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
                    val navController = rememberNavController()
                    // Box en vez de renderizar cada pantalla suelta: el widget de
                    // IA (PLAN.md Parte 16) se monta una sola vez aca, fuera del
                    // NavHost, para quedar accesible desde cualquier pantalla sin
                    // agregarlo a cada una.
                    Box(modifier = Modifier.fillMaxSize()) {
                        PdvNavHost(navController)
                        AsistenteIaWidget()
                    }
                }
            }
        }
    }
}

@Composable
private fun PdvNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Pantalla.HELLO.name) {
        composable(Pantalla.HELLO.name) {
            HelloScreen(
                onNavigateToConfiguracion = { navController.navigate(Pantalla.CONFIGURACION.name) },
                onNavigateToVenta = { navController.navigate(Pantalla.VENTA.name) },
                onNavigateToEntrada = { navController.navigate(Pantalla.ENTRADA.name) },
                onNavigateToInventario = { navController.navigate(Pantalla.INVENTARIO.name) },
                onNavigateToCaja = { navController.navigate(Pantalla.CAJA.name) },
                onNavigateToDevoluciones = { navController.navigate(Pantalla.DEVOLUCIONES.name) },
                onNavigateToUsuarios = { navController.navigate(Pantalla.USUARIOS.name) },
            )
        }
        composable(Pantalla.CONFIGURACION.name) {
            ConfiguracionScreen(
                onBack = { navController.popBackStack() },
                onNavigateToImportarCatalogo = { navController.navigate(Pantalla.IMPORTAR_CATALOGO.name) },
                onNavigateToConflictos = { navController.navigate(Pantalla.CONFLICTOS.name) },
            )
        }
        composable(Pantalla.VENTA.name) {
            VentaScreen(onBack = { navController.popBackStack() })
        }
        composable(Pantalla.ENTRADA.name) {
            EntradaScreen(onBack = { navController.popBackStack() })
        }
        composable(Pantalla.INVENTARIO.name) {
            InventarioScreen(onBack = { navController.popBackStack() })
        }
        composable(Pantalla.CAJA.name) {
            CajaScreen(onBack = { navController.popBackStack() })
        }
        composable(Pantalla.DEVOLUCIONES.name) {
            DevolucionScreen(onBack = { navController.popBackStack() })
        }
        composable(Pantalla.USUARIOS.name) {
            UsuarioScreen(
                onBack = { navController.popBackStack() },
                onNavigateToRoles = { navController.navigate(Pantalla.ROLES.name) },
            )
        }
        composable(Pantalla.ROLES.name) {
            RolScreen(onBack = { navController.popBackStack() })
        }
        composable(Pantalla.IMPORTAR_CATALOGO.name) {
            ImportarCatalogoScreen(onBack = { navController.popBackStack() })
        }
        composable(Pantalla.CONFLICTOS.name) {
            RevisionConflictosScreen(onBack = { navController.popBackStack() })
        }
    }
}
