package com.example

import com.example.util.MemoryFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryFormatterTest {

    @Test
    fun testFormatMb() {
        assertEquals("500 MB", MemoryFormatter.formatMb(500))
        assertEquals("2.00 GB", MemoryFormatter.formatMb(2048))
    }

    @Test
    fun testFormatBytes() {
        val bytesMb = 500 * 1024 * 1024L
        assertEquals("500.0 MB", MemoryFormatter.formatBytes(bytesMb))

        val bytesGb = (2.5 * 1024 * 1024 * 1024).toLong()
        assertEquals("2.50 GB", MemoryFormatter.formatBytes(bytesGb))
    }
}
