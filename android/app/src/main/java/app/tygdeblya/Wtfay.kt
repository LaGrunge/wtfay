package app.tygdeblya

import wtfay.Address
import wtfay.AddressException
import wtfay.Codec
import wtfay.Dictionaries
import wtfay.Grid
import wtfay.WordList
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

data class Geo(val lat: Double, val lon: Double)

/** One 3×3 m square: its address and its corners. */
data class Square(
    val cell: Long,
    val address: Address,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val center get() = Geo((south + north) / 2, (west + east) / 2)
    val text get() = "${address.first}.${address.second}.${address.argo}"
}

/** What the user typed in the search box, read as a three-word address. */
sealed interface WordsInput {
    /** Not an address at all: go look for a place. */
    data object NotWords : WordsInput

    /** All three words are known and point to a square. */
    data class Found(val square: Square) : WordsInput

    /** Still typing or misspelt: ready-made completions of the whole address. */
    data class Partial(val completions: List<String>, val problem: String?) : WordsInput
}

/**
 * The address engine for the app: [Codec] from core plus what a UI needs on top
 * of it — square bounds, grid lines for the map, completion and typo fixing.
 */
class Wtfay(val codec: Codec) {
    val grid: Grid get() = codec.grid
    private val common = Lookup(codec.dict.common)
    private val argo = Lookup(codec.dict.argo)

    fun square(lat: Double, lon: Double): Square = squareOf(grid.cellOf(lat, lon))

    fun squareOf(cell: Long): Square {
        val (lat, lon) = grid.centerOf(cell)
        val rowH = 180.0 / grid.rows
        val row = floor((lat + 90.0) / rowH).toInt().coerceIn(0, grid.rows - 1)
        val colW = 360.0 / grid.cols(row)
        val col = floor((lon + 180.0) / colW)
        val south = -90.0 + row * rowH
        val west = -180.0 + col * colW
        return Square(cell, codec.encodeCell(cell), south, west, south + rowH, west + colW)
    }

    fun find(w1: String, w2: String, w3: String): Square? =
        try {
            squareOf(codec.decodeCell(w1, w2, w3))
        } catch (e: AddressException) {
            null
        }

    /**
     * Grid lines inside a box, as polylines of (lon, lat) pairs, or null if the
     * box holds more than [maxCells] squares and drawing them would be a mess.
     */
    fun gridLines(south: Double, west: Double, north: Double, east: Double, maxCells: Int = 6000): List<List<Geo>>? {
        if (east <= west) return null  // across the antimeridian: not worth it
        val rowH = 180.0 / grid.rows
        val r0 = floor((south + 90.0) / rowH).toInt().coerceIn(0, grid.rows - 1)
        val r1 = floor((north + 90.0) / rowH).toInt().coerceIn(0, grid.rows - 1)
        val midCols = grid.cols((r0 + r1) / 2)
        val approxCells = (r1 - r0 + 1).toLong() * ((east - west) / 360.0 * midCols + 1).toLong()
        if (approxCells > maxCells) return null

        val lines = ArrayList<List<Geo>>()
        for (r in r0..r1) {
            val s = -90.0 + r * rowH
            val n = s + rowH
            lines += listOf(Geo(s, west), Geo(s, east))
            val colW = 360.0 / grid.cols(r)
            val c0 = floor((west + 180.0) / colW).toLong()
            val c1 = floor((east + 180.0) / colW).toLong()
            for (c in c0..c1 + 1) {
                val x = -180.0 + c * colW
                if (x in west..east) lines += listOf(Geo(s, x), Geo(n, x))
            }
        }
        val top = -90.0 + (r1 + 1) * rowH
        lines += listOf(Geo(top, west), Geo(top, east))
        return lines
    }

    /** Reads search box text as an address: complete, half-typed or not one at all. */
    fun read(text: String): WordsInput {
        val trimmed = text.trim()
        val raw = trimmed.removePrefix("///").trim()
        if (raw.isEmpty() || !raw.all { it in CYRILLIC || it in SEPARATORS }) return WordsInput.NotWords
        val trailingSep = raw.last() in SEPARATORS
        val tokens = raw.split(*SEPARATORS).filter { it.isNotBlank() }.map { WordList.key(it) }
        if (tokens.isEmpty() || tokens.size > 3) return WordsInput.NotWords
        val lists = listOf(common, common, argo)
        // without a dot or slashes it is probably a place name, unless it is exactly three of our words
        val marked = '.' in raw || trimmed.startsWith("/")
        if (!marked && !(tokens.size == 3 && tokens.indices.all { lists[it].contains(tokens[it]) })) return WordsInput.NotWords

        val done = if (trailingSep) tokens else tokens.dropLast(1)
        val partial = if (trailingSep) null else tokens.last()

        // fix every finished word that is not in its list
        val fixed = ArrayList<List<String>>()
        var problem: String? = null
        for ((i, w) in done.withIndex()) {
            val list = lists[i]
            if (list.contains(w)) {
                fixed += listOf(list.display(w))
            } else {
                val near = list.closest(w, 3)
                if (problem == null) problem = if (near.isEmpty()) "Не знаю слова «$w»" else "Нет слова «$w». Может, так?"
                if (near.isEmpty()) return WordsInput.Partial(emptyList(), problem)
                fixed += near
            }
        }

        if (partial != null) {
            val list = lists[done.size]
            if (done.size == 2 && problem == null && list.contains(partial)) {
                find(fixed[0][0], fixed[1][0], partial)?.let { return WordsInput.Found(it) }
                return WordsInput.Partial(emptyList(), "Такого адреса нет, проверь слова")
            }
            val starts = list.withPrefix(partial, 6)
            fixed += starts.ifEmpty { list.closest(partial, 3).also { if (it.isEmpty()) problem = "Не знаю слова «$partial»" } }
            if (fixed.last().isEmpty()) return WordsInput.Partial(emptyList(), problem)
        }

        // all finished and correct
        if (fixed.size == 3 && fixed.all { it.size == 1 } && problem == null) {
            find(fixed[0][0], fixed[1][0], fixed[2][0])?.let { return WordsInput.Found(it) }
            return WordsInput.Partial(emptyList(), "Такого адреса нет, проверь слова")
        }

        // spell out up to 6 candidates; an unfinished address ends with a dot to continue typing
        val combos = fixed.fold(listOf("")) { acc, options ->
            acc.flatMap { prefix -> options.map { if (prefix.isEmpty()) it else "$prefix.$it" } }.take(6)
        }
        val tail = if (fixed.size < 3) "." else ""
        return WordsInput.Partial(combos.map { "///$it$tail" }, problem)
    }

    /** Word lookup by [WordList.key], with prefix search and edit-distance fixes. */
    private class Lookup(val list: WordList) {
        private val keys: Array<String> = list.words.map { WordList.key(it) }.toTypedArray().also { it.sort() }

        fun contains(key: String) = list.indexOf(key) != null
        fun display(key: String) = list[list.indexOf(key)!!]

        fun withPrefix(prefix: String, limit: Int): List<String> {
            var lo = keys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
            val out = ArrayList<String>(limit)
            while (lo < keys.size && out.size < limit && keys[lo].startsWith(prefix)) out += display(keys[lo++])
            return out
        }

        fun closest(word: String, limit: Int): List<String> {
            val maxD = if (word.length <= 4) 1 else 2
            val hits = ArrayList<Pair<Int, String>>()
            for (k in keys) {
                if (abs(k.length - word.length) > maxD) continue
                val d = distance(word, k, maxD)
                if (d <= maxD) hits += d to k
            }
            return hits.sortedWith(compareBy({ it.first }, { it.second.length })).take(limit).map { display(it.second) }
        }

        /** Levenshtein distance, giving up early once it exceeds [max]. */
        private fun distance(a: String, b: String, max: Int): Int {
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                var rowMin = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                    if (cur[j] < rowMin) rowMin = cur[j]
                }
                if (rowMin > max) return max + 1
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }

    companion object {
        private val SEPARATORS = charArrayOf('.', ' ', ',', '/', '·', '\n')
        private val CYRILLIC = ('а'..'я') + ('А'..'Я') + listOf('ё', 'Ё', '-')

        fun load(): Wtfay = Wtfay(Codec(Dictionaries.load(), Grid()))
    }
}
