package com.pdv.pos.inventario

import java.math.BigDecimal
import java.math.RoundingMode

// Cantidades en existencia siempre con 2 decimales visibles (PLAN.md Parte 9,
// hallazgo de verificacion en el Xiaomi) - relevante para unidades como
// kg/litros. Sin esto, Room muestra el BigDecimal crudo tal como se
// almaceno (ej. "1.5" o "24", segun como se haya escrito) mientras que el
// backend (columnas Numeric(14, 3)) siempre devuelve 3 decimales fijos (ej.
// "24.000") - esta funcion normaliza ambos casos a 2 decimales, solo para
// mostrar/prellenar campos, nunca para los calculos de delta.
fun BigDecimal.formatoCantidad(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()
