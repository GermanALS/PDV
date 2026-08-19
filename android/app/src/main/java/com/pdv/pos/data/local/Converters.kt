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
}
