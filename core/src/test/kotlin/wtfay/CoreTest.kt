package wtfay

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = Grid.EARTH_RADIUS_M
    fun rad(d: Double) = d * PI / 180
    val dLat = rad(lat2 - lat1)
    val dLon = rad(lon2 - lon1)
    val h = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h.coerceAtMost(1.0)))
}

class DictionaryTest {
    private val dict = Shared.dict

    @Test
    fun `lists are unique by key and do not overlap`() {
        for (list in listOf(dict.common, dict.argo)) {
            val keys = list.words.map { WordList.key(it) }
            assertEquals(keys.size, keys.toSet().size)
            assertTrue(list.words.all { Regex("[а-яё]+(-[а-яё]+)?").matches(it) })
        }
        val overlap = dict.common.words.map { WordList.key(it) }.toSet() intersect
            dict.argo.words.map { WordList.key(it) }.toSet()
        assertTrue(overlap.isEmpty(), "overlap: ${overlap.take(10)}")
    }

    @Test
    fun `lookup ignores case and yo`() {
        val w = dict.common.words.first { 'ё' in it }
        val i = dict.common.indexOf(w)
        assertEquals(i, dict.common.indexOf(w.replace('ё', 'е').uppercase()))
    }
}

class GridTest {
    private val grid = Shared.grid

    @Test
    fun `total cells match sphere area`() {
        val sphere = 4 * PI * Grid.EARTH_RADIUS_M.pow(2) / 9.0
        val ratio = grid.totalCells / sphere
        // floor() per band makes cells slightly wider than 3 m, so a bit fewer cells
        assertTrue(ratio in 0.999..1.0, "ratio $ratio")
    }

    @Test
    fun `random points round trip through their cell`() {
        val rnd = Random(1)
        repeat(20_000) {
            val lat = rnd.nextDouble(-90.0, 90.0)
            val lon = rnd.nextDouble(-180.0, 180.0)
            val cell = grid.cellOf(lat, lon)
            val (cLat, cLon) = grid.centerOf(cell)
            assertEquals(cell, grid.cellOf(cLat, cLon))
            // within half a diagonal of a 3 m cell (a bit more where bands are a few cells wide)
            if (kotlin.math.abs(lat) < 89.9) {
                val d = haversine(lat, lon, cLat, cLon)
                assertTrue(d <= 2.2, "point $lat,$lon is $d m from centre of its cell")
            }
        }
    }

    @Test
    fun `cells are about 3 by 3 metres away from the poles`() {
        for (row in listOf(grid.rows / 2, grid.rows / 4, grid.rows / 10, grid.rows - 500)) {
            val lat = -PI / 2 + (row + 0.5) * PI / grid.rows
            val width = 2 * PI * Grid.EARTH_RADIUS_M * cos(lat) / grid.cols(row)
            assertTrue(width in 3.0..3.1, "row $row width $width")
        }
        assertTrue(PI * Grid.EARTH_RADIUS_M / grid.rows in 2.99..3.0)
    }

    @Test
    fun `edges - poles and antimeridian`() {
        // the polar band is a ~9 m circle split into 3 cells
        assertEquals(3, grid.cols(0))
        assertEquals(0L, grid.cellOf(-90.0, -180.0))
        assertEquals(1L, grid.cellOf(-90.0, 0.0))
        assertEquals(grid.totalCells - 1, grid.cellOf(90.0, 179.9999999))
        assertEquals(grid.cellOf(10.0, -180.0), grid.cellOf(10.0, 180.0))
        assertEquals(grid.cellOf(10.0, 20.0), grid.cellOf(10.0, 380.0))
        assertFailsWith<IllegalArgumentException> { grid.cellOf(91.0, 0.0) }
        val (lat, _) = grid.centerOf(grid.totalCells - 1)
        // half a band (1.5 m) south of the pole
        assertTrue(lat > 89.9999 && lat < 90.0, "lat $lat")
    }

    @Test
    fun `cell indices are dense`() {
        // first and last cell of a few bands touch the neighbouring bands
        for (row in listOf(1, 1000, grid.rows / 2, grid.rows - 2)) {
            val start = grid.rowStart(row)
            assertEquals(start, grid.rowStart(row - 1) + grid.cols(row - 1))
        }
    }
}

class CodecTest {
    private val codec = Shared.codec

    @Test
    fun `address space covers the planet`() {
        assertTrue(codec.space >= codec.grid.totalCells)
    }

    @Test
    fun `pair ranking round trips`() {
        val rnd = Random(2)
        val p = Shared.dict.common.size
        val samples = listOf(0 to 1, 1 to 0, 0 to p - 1, p - 1 to 0, p - 2 to p - 1, p - 1 to p - 2) +
            List(10_000) { val i = rnd.nextInt(p); val j = rnd.nextInt(p - 1); i to if (j < i) j else j + 1 }
        for ((i, j) in samples) {
            assertEquals(i to j, codec.unrankPair(codec.rankPair(i, j)))
        }
        assertEquals(0L, codec.rankPair(0, 1))
        assertEquals(codec.pairs - 1, codec.rankPair(p - 1, p - 2))
        assertNotEquals(codec.rankPair(3, 5), codec.rankPair(5, 3))
    }

    @Test
    fun `cells round trip through words`() {
        val rnd = Random(3)
        val cells = listOf(0L, 1L, codec.grid.totalCells - 1) + List(20_000) { rnd.nextLong(codec.grid.totalCells) }
        for (cell in cells) {
            val a = codec.encodeCell(cell)
            assertEquals(cell, codec.decodeCell(a.first, a.second, a.argo))
        }
    }

    @Test
    fun `words can be typed in caps or without yo`() {
        val a = codec.encode(55.75393, 37.62079)
        val cell = codec.grid.cellOf(55.75393, 37.62079)
        assertEquals(cell, codec.decodeCell(a.first.uppercase(), a.second.replace('ё', 'е'), a.argo.replace('ё', 'е')))
    }

    @Test
    fun `swapped first words are another place or a hint`() {
        val rnd = Random(5)
        var hints = 0
        repeat(2_000) {
            val cell = rnd.nextLong(codec.grid.totalCells)
            val a = codec.encodeCell(cell)
            try {
                assertNotEquals(cell, codec.decodeCell(a.second, a.first, a.argo))
            } catch (e: AddressException) {
                assertTrue("${a.first} ${a.second} ${a.argo}?" in e.message!!, e.message)
                hints++
            }
        }
        assertTrue(hints > 0)
    }

    @Test
    fun `neighbouring cells get unrelated addresses`() {
        val base = codec.grid.cellOf(55.75393, 37.62079)
        val a = codec.encodeCell(base)
        for (d in listOf(1L, -1L, 2L)) {
            val b = codec.encodeCell(base + d)
            assertTrue(setOf(a.first, a.second, a.argo).intersect(setOf(b.first, b.second, b.argo)).size <= 1, "$a vs $b")
        }
        // a step north (next band) too
        val (lat, lon) = codec.grid.centerOf(base)
        assertNotEquals(a, codec.encode(lat + 3.0 / 111_195, lon))
    }

    @Test
    fun `bad input is rejected`() {
        val a = codec.encodeCell(42)
        assertFailsWith<AddressException> { codec.decodeCell("абракадабрище", a.second, a.argo) }
        assertFailsWith<AddressException> { codec.decodeCell(a.first, a.first, a.argo) }
        assertFailsWith<AddressException> { codec.decodeCell(a.first, a.second, a.first) }
    }

    @Test
    fun `some word combinations point nowhere`() {
        // the address space is larger than the planet; find an unused combination and check it is refused
        val d = Shared.dict
        var found = 0
        val rnd = Random(4)
        repeat(2_000) {
            val i = rnd.nextInt(d.common.size); var j = rnd.nextInt(d.common.size)
            if (i == j) j = (j + 1) % d.common.size
            val k = rnd.nextInt(d.argo.size)
            try {
                codec.decodeCell(d.common[i], d.common[j], d.argo[k])
            } catch (e: AddressException) {
                found++
            }
        }
        val expected = 2_000 * (1 - codec.grid.totalCells.toDouble() / codec.space)
        assertTrue(kotlin.math.abs(found - expected) < 100, "unused combinations: $found of 2000, expected $expected")
    }

    @Test
    fun `demo places are stable and decode back`() {
        for (p in DEMO_PLACES) {
            val a = codec.encode(p.lat, p.lon)
            val (lat, lon) = codec.decode("${a.first}.${a.second}.${a.argo}")
            assertTrue(haversine(p.lat, p.lon, lat, lon) < 2.2, "${p.name}: $a")
        }
    }
}

object Shared {
    val dict: Dictionaries by lazy { Dictionaries.load() }
    val grid: Grid by lazy { Grid() }
    val codec: Codec by lazy { Codec(dict, grid) }
}
