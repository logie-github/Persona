package com.logie.persona.backend.mods

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class VcdiffTest {
    private fun varint(v: Int) = TestVcdiff.varint(v)
    private fun encode(source: ByteArray, target: ByteArray, appHeader: ByteArray? = null, hdrExtra: Int = 0, secondaryIndicator: Int = 0) = TestVcdiff.encode(source, target, appHeader, hdrExtra, secondaryIndicator)

    @Test fun rebuildsAPatchedFile() {
        val src = ByteArray(5000) { (it * 7).toByte() }
        val tgt = src.copyOf().also { "PATCHED!!".toByteArray().copyInto(it, 1000) }
        assertContentEquals(tgt, Vcdiff.apply(src, encode(src, tgt)))
    }

    @Test fun anInsertionAndAnAppHeaderAreFine() {
        val src = ByteArray(3000) { it.toByte() }
        val tgt = src.copyOfRange(0, 1500) + "INSERTED-TEXT".toByteArray() + src.copyOfRange(1500, 3000)
        assertContentEquals(tgt, Vcdiff.apply(src, encode(src, tgt, appHeader = "C:\\tools\\xdelta.exe".toByteArray())))
    }

    @Test fun theSmallestCodesAndTheTableAreRight() {
        // ADD 3 (index 4) then RUN 5 (index 0 + size), COPY size 4 mode 0 + ADD 1 (index 247): all from the default table
        val src = "ABCDEFGH".toByteArray()
        val data = byteArrayOf('x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte(), '.'.code.toByte(), 'Q'.code.toByte())
        val inst = byteArrayOf(4, 0, 5, 247.toByte()); val addr = byteArrayOf(2)                                   // COPY 4 bytes from source offset 2
        val expected = "xyz".toByteArray() + ByteArray(5) { '.'.code.toByte() } + "CDEF".toByteArray() + "Q".toByteArray()
        val delta = ByteArrayOutputStream().apply { write(varint(expected.size)); write(0); write(varint(data.size)); write(varint(inst.size)); write(varint(addr.size)); write(data); write(inst); write(addr) }
        val patch = ByteArrayOutputStream().apply { write(byteArrayOf(0xD6.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0, 0, 1)); write(varint(8)); write(varint(0)); write(varint(delta.size())); write(delta.toByteArray()) }.toByteArray()
        assertContentEquals(expected, Vcdiff.apply(src, patch))
    }

    @Test fun aPatchForAnotherFileIsRefusedClearly() {
        val src = ByteArray(5000) { it.toByte() }; val tgt = src.copyOf().also { it[10] = 1 }
        assertFailsWith<IOException> { Vcdiff.apply(ByteArray(100), encode(src, tgt)) }
    }

    @Test fun compressedPatchesAndOtherFilesAreSaidSo() {
        val src = ByteArray(100) { it.toByte() }; val tgt = src.copyOf().also { it[10] = 1 }
        val e1 = assertFailsWith<IOException> { Vcdiff.apply(src, encode(src, tgt, hdrExtra = 1)) }; assert("compressed" in e1.message!!)
        val e2 = assertFailsWith<IOException> { Vcdiff.apply(src, "not a patch".toByteArray()) }; assert("xdelta" in e2.message!!)
    }
}
