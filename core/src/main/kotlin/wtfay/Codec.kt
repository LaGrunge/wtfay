package wtfay

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
 * A cell index is scrambled with a [Feistel] permutation of [0, M), so
 * neighbouring cells get unrelated words, then split into (pair, argo).
 * Indices at or above the number of cells are unused, which catches most typos.
 *
 * [seed] and the dictionaries define every address: once published, never change them.
 */
class Codec(val dict: Dictionaries, val grid: Grid = Grid(), seed: Long = SEED) {
    private val p = dict.common.size.toLong()
    private val a = dict.argo.size.toLong()
    val pairs: Long = p * (p - 1) / 2
    val space: Long = Math.multiplyExact(pairs, a)

    private val scramble: Feistel

    init {
        require(space >= grid.totalCells) {
            "dictionaries too small: $space addresses for ${grid.totalCells} cells"
        }
        scramble = Feistel(space, seed)
    }

    fun encode(lat: Double, lon: Double): Address = encodeCell(grid.cellOf(lat, lon))

    fun encodeCell(cell: Long): Address {
        require(cell in 0 until grid.totalCells) { "cell out of range: $cell" }
        val s = scramble.permute(cell)
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
        val cell = scramble.unpermute(s)
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
        /** Fixed forever with the first release: changing it moves every address. */
        const val SEED = 0x7479_6764_6562_6c79L  // "tygdebly"

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
