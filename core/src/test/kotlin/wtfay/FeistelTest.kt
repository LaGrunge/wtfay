package wtfay

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FeistelTest {
    @Test
    fun `small sizes are exact bijections`() {
        for (size in listOf(2L, 3L, 7L, 100L, 1000L, 4096L, 4097L, 65_521L)) {
            val f = Feistel(size, seed = 42)
            val seen = BooleanArray(size.toInt())
            for (x in 0 until size) {
                val y = f.permute(x)
                assertTrue(y in 0 until size, "size $size: $x -> $y")
                assertTrue(!seen[y.toInt()], "size $size: $y hit twice")
                seen[y.toInt()] = true
                assertEquals(x, f.unpermute(y))
            }
        }
    }

    @Test
    fun `codec-sized space round trips`() {
        val space = Shared.codec.space
        val f = Feistel(space, Codec.SEED)
        val rnd = Random(5)
        val xs = listOf(0L, 1L, space - 1) + List(50_000) { rnd.nextLong(space) }
        for (x in xs) {
            val y = f.permute(x)
            assertTrue(y in 0 until space)
            assertEquals(x, f.unpermute(y))
        }
    }

    @Test
    fun `neighbours scatter over the whole range`() {
        val space = Shared.codec.space
        val f = Feistel(space, Codec.SEED)
        val start = 12_345_678_901L
        val ys = (start until start + 10_000).map { f.permute(it) }
        // consecutive outputs are far apart, and differences are not a constant step
        val gaps = ys.zipWithNext { a, b -> b - a }
        assertTrue(gaps.toSet().size > 9_900, "too many equal steps")
        val near = gaps.count { kotlin.math.abs(it) < space / 1000 }
        assertTrue(near < 50, "$near of 9999 neighbours stay close")
        // outputs fill the range evenly: ten equal buckets get ~1000 each
        val buckets = IntArray(10).also { b -> ys.forEach { b[(it / (space / 10 + 1)).toInt()]++ } }
        assertTrue(buckets.all { it in 850..1150 }, buckets.joinToString())
    }

    @Test
    fun `seed changes the permutation`() {
        val a = Feistel(1_000_000L, 1)
        val b = Feistel(1_000_000L, 2)
        val same = (0L until 1000L).count { a.permute(it) == b.permute(it) }
        assertTrue(same < 5, "$same of 1000 equal")
        assertNotEquals(a.permute(0), b.permute(0))
    }
}
