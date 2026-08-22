package com.pdv.pos.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal

object Converters {
    @TypeConverter
    @JvmStatic
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    @JvmStatic
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    // Claves de modulo (PLAN.md Parte 13): sin comas, un join simple alcanza.
    @TypeConverter
    @JvmStatic
    fun fromModulosPermitidos(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    @JvmStatic
    fun toModulosPermitidos(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(",")
}
