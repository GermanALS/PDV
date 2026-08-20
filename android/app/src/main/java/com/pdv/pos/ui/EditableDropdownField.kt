package com.pdv.pos.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Campo de texto editable con sugerencias desplegables (PLAN.md Parte 9,
// evaluacion diferida desde la Parte 8): a diferencia del selector de
// Sucursal en Configuracion (solo lectura, catalogo cerrado), aqui el
// usuario puede escribir libremente - si lo que escribe no esta en
// "opciones", ese texto es el valor nuevo, sin un item "+ Agregar nuevo"
// especial en el menu.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableDropdownField(
    label: String,
    value: String,
    opciones: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sugerencias = if (value.isBlank()) opciones else opciones.filter { it.contains(value, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded && sugerencias.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded && sugerencias.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            sugerencias.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = {
                        onValueChange(opcion)
                        expanded = false
                    },
                )
            }
        }
    }
}
