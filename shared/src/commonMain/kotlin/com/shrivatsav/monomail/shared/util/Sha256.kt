package com.shrivatsav.monomail.shared.util

/**
 * Pure-Kotlin SHA-256 so PKCE works on every target without platform crypto cinterop.
 */
object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e
    )

    fun hash(message: ByteArray): ByteArray {
        var h0 = 0x6a09e667; var h1 = -0x4498517b; var h2 = 0x3c6ef372; var h3 = -0x5ab00ac6
        var h4 = 0x510e527f; var h5 = -0x64fa9774; var h6 = 0x1f83d9ab; var h7 = 0x5be0cd19

        val ml = message.size.toLong() * 8
        val withOne = message + byteArrayOf(0x80.toByte())
        val padLen = ((56 - withOne.size % 64) + 64) % 64
        val padded = withOne + ByteArray(padLen) + longToBytes(ml)

        val w = IntArray(64)
        var i = 0
        while (i < padded.size) {
            for (t in 0 until 16) {
                w[t] = ((padded[i + t * 4].toInt() and 0xff) shl 24) or
                    ((padded[i + t * 4 + 1].toInt() and 0xff) shl 16) or
                    ((padded[i + t * 4 + 2].toInt() and 0xff) shl 8) or
                    (padded[i + t * 4 + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = rotr(w[t - 15], 7) xor rotr(w[t - 15], 18) xor (w[t - 15] ushr 3)
                val s1 = rotr(w[t - 2], 17) xor rotr(w[t - 2], 19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (t in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K[t] + w[t]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
            i += 64
        }
        return intsToBytes(intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7))
    }

    private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

    private fun longToBytes(v: Long): ByteArray {
        val b = ByteArray(8)
        for (j in 0 until 8) b[7 - j] = ((v ushr (j * 8)) and 0xff).toByte()
        return b
    }

    private fun intsToBytes(ints: IntArray): ByteArray {
        val out = ByteArray(ints.size * 4)
        for (j in ints.indices) {
            out[j * 4] = (ints[j] ushr 24).toByte()
            out[j * 4 + 1] = (ints[j] ushr 16).toByte()
            out[j * 4 + 2] = (ints[j] ushr 8).toByte()
            out[j * 4 + 3] = ints[j].toByte()
        }
        return out
    }
}
