package com.logie.persona.backend.mods

import java.io.ByteArrayOutputStream

/** A tiny VCDIFF encoder for tests: the common prefix and suffix are copied from the source, the middle is added. */
object TestVcdiff {
    fun varint(v: Int): ByteArray {
        val parts = ArrayList<Int>(); var x = v; parts += x and 0x7f; x = x shr 7
        while (x > 0) { parts += (x and 0x7f) or 0x80; x = x shr 7 }
        return parts.reversed().map { it.toByte() }.toByteArray()
    }

    fun encode(source: ByteArray, target: ByteArray, appHeader: ByteArray? = null, hdrExtra: Int = 0, secondaryIndicator: Int = 0): ByteArray {
        var pre = 0; while (pre < source.size && pre < target.size && source[pre] == target[pre]) pre++
        var suf = 0; while (suf < source.size - pre && suf < target.size - pre && source[source.size - 1 - suf] == target[target.size - 1 - suf]) suf++
        val mid = target.copyOfRange(pre, target.size - suf)
        val data = ByteArrayOutputStream(); val inst = ByteArrayOutputStream(); val addr = ByteArrayOutputStream()
        if (pre > 0) { inst.write(19); inst.write(varint(pre)); addr.write(varint(0)) }
        if (mid.isNotEmpty()) { inst.write(1); inst.write(varint(mid.size)); data.write(mid) }
        if (suf > 0) { inst.write(19); inst.write(varint(suf)); addr.write(varint(source.size - suf)) }
        val delta = ByteArrayOutputStream()
        delta.write(varint(target.size)); delta.write(secondaryIndicator); delta.write(varint(data.size())); delta.write(varint(inst.size())); delta.write(varint(addr.size()))
        delta.write(data.toByteArray()); delta.write(inst.toByteArray()); delta.write(addr.toByteArray())
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xD6.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0)); out.write((if (appHeader != null) 4 else 0) or hdrExtra)
        if (appHeader != null) { out.write(varint(appHeader.size)); out.write(appHeader) }
        out.write(1); out.write(varint(source.size)); out.write(varint(0)); out.write(varint(delta.size())); out.write(delta.toByteArray())
        return out.toByteArray()
    }
}
