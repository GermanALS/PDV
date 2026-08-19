package com.pdv.pos.sync

data class Versioned<T>(val value: T, val updatedAt: Long)
