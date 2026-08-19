package com.pdv.pos.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LastWriteWinsResolverTest {

    @Test
    fun `the more recently updated version wins`() {
        val local = Versioned(value = "local-value", updatedAt = 1_000L)
        val remote = Versioned(value = "remote-value", updatedAt = 2_000L)

        val result = LastWriteWinsResolver.resolve(local, remote)

        assertEquals("remote-value", result.value)
    }

    @Test
    fun `local wins when it is more recent than remote`() {
        val local = Versioned(value = "local-value", updatedAt = 5_000L)
        val remote = Versioned(value = "remote-value", updatedAt = 1_000L)

        val result = LastWriteWinsResolver.resolve(local, remote)

        assertEquals("local-value", result.value)
    }
}
