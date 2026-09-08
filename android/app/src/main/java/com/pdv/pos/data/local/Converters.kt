package com.pdv.pos.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal

object Converters {

    // Claves de modulo (PLAN.md Parte 13). Separador Unit Separator
    // (U+001F): un caracter de control que no puede aparecer en una clave de
    // modulo ni en texto ingresado por el usuario, a diferencia de la coma
    // que se usaba antes (PLAN.md Parte 29, B-6).
    private const val MODULO_SEPARATOR = "\u001F"

    @TypeConverter
    @JvmStatic
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    @JvmStatic
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    @JvmStatic
    fun fromModulosPermitidos(value: List<String>): String = value.joinToString(MODULO_SEPARATOR)

    @TypeConverter
    @JvmStatic
    fun toModulosPermitidos(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(MODULO_SEPARATOR)
}
