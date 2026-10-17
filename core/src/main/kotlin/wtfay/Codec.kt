package wtfay

/** Three-word address: an ordered pair of distinct common words plus one argo word. */
data class Address(val first: String, val second: String, val argo: String) {
    override fun toString() = "$first.$second.$argo"
}

class AddressException(message: String) : IllegalArgumentException(message)

/**
 * Maps grid cells to addresses and back.
 *
 * Address space: P·(P−1) ordered pairs of distinct common words × A argo words, M in total.
 * A cell index is scrambled with a [Feistel] permutation of [0, M), so
 * neighbouring cells get unrelated words, then split into (pair, argo).
 * Indices at or above the number of cells are unused, which catches most typos.
 *
 * [seed] and the dictionaries define every address: once published, never change them.
 */
class Codec(val dict: Dictionaries, val grid: Grid = Grid(), seed: Long = SEED) {
    private val p = dict.common.size
    private val a = dict.argo.size.toLong()
    val pairs: Long = p.toLong() * (p - 1)
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
        val (i, j) = unrankPair(s / a)
        return Address(dict.common[i], dict.common[j], dict.argo[(s % a).toInt()])
    }

    /** Any case, е for ё; the order of the first two words matters. */
    fun decodeCell(w1: String, w2: String, w3: String): Long {
        val i = dict.common.indexOf(w1) ?: throw AddressException("неизвестное слово: $w1")
        val j = dict.common.indexOf(w2) ?: throw AddressException("неизвестное слово: $w2")
        val k = dict.argo.indexOf(w3) ?: throw AddressException("неизвестное слово арго: $w3")
        if (i == j) throw AddressException("первые два слова совпадают: $w1")
        cellOf(i, j, k)?.let { return it }
        if (cellOf(j, i, k) != null) throw AddressException("такого адреса нет, может быть, $w2 $w1 $w3?")
        throw AddressException("такого адреса нет: $w1 $w2 $w3")
    }

    private fun cellOf(i: Int, j: Int, k: Int): Long? {
        val cell = scramble.unpermute(rankPair(i, j) * a + k)
        return if (cell < grid.totalCells) cell else null
    }

    fun decode(w1: String, w2: String, w3: String): Pair<Double, Double> = grid.centerOf(decodeCell(w1, w2, w3))

    fun decode(address: String): Pair<Double, Double> {
        val parts = address.split('.', ' ', ',', '/').filter { it.isNotBlank() }
        if (parts.size != 3) throw AddressException("нужно три слова: $address")
        return decode(parts[0], parts[1], parts[2])
    }

    /** Rank of the ordered pair (i, j), i ≠ j: i·(P−1) + j, skipping j = i. */
    fun rankPair(i: Int, j: Int): Long {
        require(i in 0 until p && j in 0 until p && i != j) { "need distinct indices below $p, got $i, $j" }
        return i.toLong() * (p - 1) + if (j < i) j else j - 1
    }

    fun unrankPair(r: Long): Pair<Int, Int> {
        require(r in 0 until pairs) { "pair rank out of range: $r" }
        val i = (r / (p - 1)).toInt()
        val j = (r % (p - 1)).toInt()
        return i to if (j < i) j else j + 1
    }

    companion object {
        /** Fixed forever with the first release: changing it moves every address. */
        const val SEED = 0x7479_6764_6562_6c79L  // "tygdebly"
    }
}
