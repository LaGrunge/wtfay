package wtfay

/**
 * Keyed bijection on [0, [size]) built from a balanced Feistel network.
 *
 * The network permutes all numbers of 2·half bits, where 2^(2·half) is the
 * smallest even power of two not below [size]. Results that land outside
 * [0, size) are fed through the network again (cycle walking); since the walk
 * follows a cycle of a permutation and started inside the range, it always
 * comes back into it, and the restriction stays a bijection.
 *
 * Not a cipher: the round function is splitmix64, good enough to make
 * neighbouring inputs look unrelated, nothing more.
 */
class Feistel(val size: Long, seed: Long, rounds: Int = 4) {
    private val half: Int
    private val mask: Long
    private val keys: LongArray

    init {
        require(size >= 2) { "size must be at least 2: $size" }
        require(rounds >= 3) { "need at least 3 rounds: $rounds" }
        val bits = 64 - java.lang.Long.numberOfLeadingZeros(size - 1)
        half = (bits + 1) / 2
        require(half <= 31) { "size too large: $size" }
        mask = (1L shl half) - 1
        keys = LongArray(rounds) { mix(seed + it) }
    }

    fun permute(x: Long): Long {
        require(x in 0 until size) { "out of range: $x" }
        var y = x
        do y = forward(y) while (y >= size)
        return y
    }

    fun unpermute(x: Long): Long {
        require(x in 0 until size) { "out of range: $x" }
        var y = x
        do y = backward(y) while (y >= size)
        return y
    }

    private fun forward(x: Long): Long {
        var l = x ushr half
        var r = x and mask
        for (k in keys) {
            val t = r
            r = l xor (mix(r xor k) and mask)
            l = t
        }
        return (l shl half) or r
    }

    private fun backward(x: Long): Long {
        var l = x ushr half
        var r = x and mask
        for (i in keys.indices.reversed()) {
            val t = l
            l = r xor (mix(l xor keys[i]) and mask)
            r = t
        }
        return (l shl half) or r
    }

    companion object {
        /** splitmix64 finaliser. */
        fun mix(z0: Long): Long {
            var z = z0 + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
