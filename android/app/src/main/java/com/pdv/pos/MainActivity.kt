package com.pdv.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                    var pantalla by rememberSaveable { mutableStateOf(Pantalla.HELLO) }
                    // Box en vez de renderizar cada pantalla suelta: el widget de
                    // IA (PLAN.md Parte 16) se monta una sola vez aca, fuera del
                    // `when`, para quedar accesible desde cualquier pantalla sin
                    // agregarlo a cada una.
                    Box(modifier = Modifier.fillMaxSize()) {
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
                            Pantalla.CONFIGURACION -> ConfiguracionScreen(
                                onBack = { pantalla = Pantalla.HELLO },
                                onNavigateToImportarCatalogo = { pantalla = Pantalla.IMPORTAR_CATALOGO },
                                onNavigateToConflictos = { pantalla = Pantalla.CONFLICTOS },
                            )
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
                            Pantalla.IMPORTAR_CATALOGO -> ImportarCatalogoScreen(onBack = { pantalla = Pantalla.CONFIGURACION })
                            Pantalla.CONFLICTOS -> RevisionConflictosScreen(onBack = { pantalla = Pantalla.CONFIGURACION })
                        }
                        AsistenteIaWidget()
                    }
                }
            }
        }
    }
}
