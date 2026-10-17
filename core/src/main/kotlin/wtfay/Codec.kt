package wtfay

import java.math.BigInteger
import kotlin.math.sqrt

/** Three-word address: an unordered pair of common words plus one argo word. */
data class Address(val first: String, val second: String, val argo: String) {
    override fun toString() = "$first.$second.$argo"
}

class AddressException(message: String) : IllegalArgumentException(message)

/**
 * Maps grid cells to addresses and back.
 *
 * Address space: C(P, 2) unordered common pairs × A argo words, M in total.
 * A cell index is scrambled with an affine bijection x → (x·MUL + ADD) mod M,
 * so neighbouring cells get unrelated words, then split into (pair, argo).
 */
class Codec(val dict: Dictionaries, val grid: Grid = Grid()) {
    private val p = dict.common.size.toLong()
    private val a = dict.argo.size.toLong()
    val pairs: Long = p * (p - 1) / 2
    val space: Long = Math.multiplyExact(pairs, a)

    private val m = BigInteger.valueOf(space)
    private val mul: BigInteger
    private val inv: BigInteger
    private val add: BigInteger = BigInteger.valueOf((space * 0.381966).toLong())  // 2 - φ

    init {
        require(space >= grid.totalCells) {
            "dictionaries too small: $space addresses for ${grid.totalCells} cells"
        }
        // a multiplier near M/φ that is coprime with M
        var c = BigInteger.valueOf((space * 0.6180339887).toLong()) or BigInteger.ONE
        while (c.gcd(m) != BigInteger.ONE) c += BigInteger.TWO
        mul = c
        inv = c.modInverse(m)
    }

    fun encode(lat: Double, lon: Double): Address = encodeCell(grid.cellOf(lat, lon))

    fun encodeCell(cell: Long): Address {
        val s = BigInteger.valueOf(cell).multiply(mul).add(add).mod(m).toLong()
        val pair = s / a
        val argo = (s % a).toInt()
        val (i, j) = unrankPair(pair)
        return Address(dict.common[i], dict.common[j], dict.argo[argo])
    }

    /** Words in any order of the first two, any case, е for ё. */
    fun decodeCell(w1: String, w2: String, w3: String): Long {
        val i = dict.common.indexOf(w1) ?: throw AddressException("неизвестное слово: $w1")
        val j = dict.common.indexOf(w2) ?: throw AddressException("неизвестное слово: $w2")
        val k = dict.argo.indexOf(w3) ?: throw AddressException("неизвестное слово арго: $w3")
        if (i == j) throw AddressException("первые два слова совпадают: $w1")
        val s = rankPair(minOf(i, j), maxOf(i, j)) * a + k
        val cell = BigInteger.valueOf(s).subtract(add).multiply(inv).mod(m).toLong()
        if (cell >= grid.totalCells) throw AddressException("такого адреса нет: $w1 $w2 $w3")
        return cell
    }

    fun decode(w1: String, w2: String, w3: String): Pair<Double, Double> = grid.centerOf(decodeCell(w1, w2, w3))

    fun decode(address: String): Pair<Double, Double> {
        val parts = address.split('.', ' ', ',', '/').filter { it.isNotBlank() }
        if (parts.size != 3) throw AddressException("нужно три слова: $address")
        return decode(parts[0], parts[1], parts[2])
    }

    companion object {
        /** Rank of the pair (i < j) in colex order: j·(j−1)/2 + i. */
        fun rankPair(i: Int, j: Int): Long {
            require(i in 0 until j) { "need 0 <= i < j, got $i, $j" }
            return j.toLong() * (j - 1) / 2 + i
        }

        fun unrankPair(r: Long): Pair<Int, Int> {
            var j = ((1 + sqrt(1.0 + 8.0 * r)) / 2).toLong()
            // fix floating point error in either direction
            while (j * (j - 1) / 2 > r) j--
            while ((j + 1) * j / 2 <= r) j++
            val i = r - j * (j - 1) / 2
            return i.toInt() to j.toInt()
        }
    }
}
