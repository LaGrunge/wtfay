package wtfay

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * Near-equal-area grid of ~3x3 m cells over a spherical Earth.
 *
 * The sphere is cut into latitude bands exactly [cellSize] high (or a hair less).
 * Band `row` holds as many cells of at least [cellSize] width as fit along its
 * centre parallel, so cells near the poles do not shrink and there are no
 * wasted indices. Cell index = number of cells in all bands to the south + column.
 *
 * Prefix sums are kept only for every [BLOCK]-th band (~1.6k longs); the rest
 * is summed on the fly, so the table is tiny and cheap to build.
 */
class Grid(val cellSize: Double = 3.0, val radius: Double = EARTH_RADIUS_M) {
    /** Number of latitude bands. */
    val rows: Int = kotlin.math.ceil(PI * radius / cellSize).toInt()

    private val bandRad = PI / rows
    private val blockStart: LongArray

    init {
        val blocks = (rows + BLOCK - 1) / BLOCK
        blockStart = LongArray(blocks + 1)
        var acc = 0L
        for (r in 0 until rows) {
            if (r % BLOCK == 0) blockStart[r / BLOCK] = acc
            acc += cols(r)
        }
        blockStart[blocks] = acc
    }

    /** Total number of cells on the planet. */
    val totalCells: Long get() = blockStart.last()

    /** Cells in latitude band [row]. */
    fun cols(row: Int): Int {
        val lat = -PI / 2 + (row + 0.5) * bandRad
        val circumference = 2 * PI * radius * cos(lat)
        return max(1, floor(circumference / cellSize).toInt())
    }

    /** Index of the first cell of band [row]. */
    fun rowStart(row: Int): Long {
        val block = row / BLOCK
        var acc = blockStart[block]
        for (r in block * BLOCK until row) acc += cols(r)
        return acc
    }

    fun cellOf(lat: Double, lon: Double): Long {
        require(lat in -90.0..90.0) { "latitude out of range: $lat" }
        require(lon.isFinite()) { "longitude must be finite: $lon" }
        val row = floor((lat + 90.0) / 180.0 * rows).toInt().coerceIn(0, rows - 1)
        val n = cols(row)
        val col = floor((normalizeLon(lon) + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1L)
        return rowStart(row) + col
    }

    /** Centre of the cell as (lat, lon) in degrees. */
    fun centerOf(cell: Long): Pair<Double, Double> {
        require(cell in 0 until totalCells) { "cell out of range: $cell" }
        // binary search the block, then walk the bands inside it
        var lo = 0
        var hi = blockStart.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (blockStart[mid] <= cell) lo = mid else hi = mid - 1
        }
        var row = lo * BLOCK
        var start = blockStart[lo]
        while (true) {
            val n = cols(row)
            if (cell < start + n) {
                val col = cell - start
                val lat = -90.0 + (row + 0.5) * 180.0 / rows
                val lon = -180.0 + (col + 0.5) * 360.0 / n
                return lat to lon
            }
            start += n
            row++
        }
    }

    companion object {
        const val EARTH_RADIUS_M = 6_371_008.8   // IUGG mean radius
        const val BLOCK = 4096

        fun normalizeLon(lon: Double): Double {
            var x = (lon + 180.0) % 360.0
            if (x < 0) x += 360.0
            return x - 180.0
        }
    }
}
