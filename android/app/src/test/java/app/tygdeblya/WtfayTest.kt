package app.tygdeblya

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WtfayTest {
    private val w = Shared.w
    private val sq = w.square(55.75393, 37.62079)
    private val a = sq.address

    @Test
    fun `full address is found with or without slashes and in any case`() {
        for (text in listOf("///${sq.text}", sq.text, "${a.first} ${a.second} ${a.argo}", sq.text.uppercase())) {
            val r = w.read(text)
            assertIs<WordsInput.Found>(r, text)
            assertEquals(sq.cell, r.square.cell)
        }
    }

    @Test
    fun `place names are not words`() {
        assertEquals(WordsInput.NotWords, w.read("Тверская 7"))
        assertEquals(WordsInput.NotWords, w.read("Москва"))
        assertEquals(WordsInput.NotWords, w.read("Red Square"))
        assertEquals(WordsInput.NotWords, w.read("Красная площадь"))
        assertEquals(WordsInput.NotWords, w.read("кафе пушкинъ москва"))
    }

    @Test
    fun `typing the last word offers completions`() {
        val r = w.read("${a.first}.${a.second}.${a.argo.take(2)}")
        assertIs<WordsInput.Partial>(r)
        assertTrue(r.completions.isNotEmpty())
        assertTrue(r.completions.all { it.startsWith("///${a.first}.${a.second}.${a.argo.take(2)}") }, r.completions.toString())
    }

    @Test
    fun `a typo in a word is fixed`() {
        val typo = a.first.dropLast(1) + (if (a.first.last() == 'а') 'о' else 'а')
        val r = w.read("$typo.${a.second}.${a.argo}")
        assertIs<WordsInput.Partial>(r)
        assertNotNull(r.problem)
        assertTrue(r.completions.any { it == "///${sq.text}" }, r.completions.toString())
    }

    @Test
    fun `square bounds contain its centre and are about 3 m`() {
        val c = sq.center
        assertTrue(c.lat in sq.south..sq.north && c.lon in sq.west..sq.east)
        assertEquals(sq.cell, w.square(c.lat, c.lon).cell)
        val h = (sq.north - sq.south) * 111_195
        assertTrue(h in 2.9..3.1, "height $h")
    }

    @Test
    fun `grid lines only when zoomed in`() {
        assertNotNull(w.gridLines(55.7535, 37.6200, 55.7545, 37.6215))
        assertNull(w.gridLines(55.0, 37.0, 56.0, 38.0))
    }
}

class SeaTest {
    @Test
    fun `secret address is read in either order, any case, with or without slashes`() {
        for (text in listOf("///ветер.свобода.кайф", "свобода ветер кайф", "ВЕТЕР.СВОБОДА.КАЙФ", " ///свобода.ветер.кайф ")) {
            assertTrue(Sea.isSecret(text), text)
        }
        assertTrue(!Sea.isSecret("ветер.свобода.улёт"))
        assertTrue(!Sea.isSecret("ветер.свобода"))
    }

    @Test
    fun `secret address steals no real square`() {
        // if the dictionaries or the seed change, pick other words for Sea.SECRET
        assertNull(Shared.w.find("ветер", "свобода", "кайф"))
        assertNull(Shared.w.find("свобода", "ветер", "кайф"))
    }

    @Test
    fun `point Nemo is a real square in the south Pacific`() {
        val sq = Shared.w.square(Sea.NEMO.lat, Sea.NEMO.lon)
        assertTrue(sq.center.lat in -49.0..-48.7 && sq.center.lon in -123.5..-123.3)
    }
}

object Shared {
    val w: Wtfay by lazy { Wtfay.load() }
}
