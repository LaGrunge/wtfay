package wtfay

import java.io.InputStream

/**
 * A word list addressed by position. Lookups are by [key]: lower case with ё → е,
 * since nobody types ё on a phone.
 */
class WordList(val words: Array<String>) {
    private val index: HashMap<String, Int> by lazy {
        HashMap<String, Int>(words.size * 2).also { m -> words.forEachIndexed { i, w -> m[key(w)] = i } }
    }

    val size: Int get() = words.size
    operator fun get(i: Int): String = words[i]
    fun indexOf(word: String): Int? = index[key(word)]

    companion object {
        fun key(w: String): String = w.trim().lowercase().replace('ё', 'е')

        fun load(input: InputStream): WordList {
            val words = ArrayList<String>(130_000)
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (l in lines) if (l.isNotBlank()) words += l.trim()
            }
            return WordList(words.toTypedArray())
        }

        fun fromResource(path: String): WordList {
            val stream = WordList::class.java.getResourceAsStream(path)
                ?: error("missing resource $path")
            return stream.use { load(it) }
        }
    }
}

class Dictionaries(val common: WordList, val argo: WordList) {
    companion object {
        fun load(): Dictionaries =
            Dictionaries(WordList.fromResource("/dict/common.txt"), WordList.fromResource("/dict/argo.txt"))
    }
}
