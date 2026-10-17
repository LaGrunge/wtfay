package app.tygdeblya

import android.app.Application
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class Origin { ME, TAP, SEARCH, SAVED, LINK }

data class Selection(val square: Square, val origin: Origin, val near: String? = null, val savedName: String? = null)

sealed interface SearchItem {
    data class Address(val square: Square) : SearchItem
    data class Completion(val text: String) : SearchItem
    data class PlaceHit(val place: Place) : SearchItem
}

data class SearchState(
    val query: String = "",
    val items: List<SearchItem> = emptyList(),
    val hint: String? = null,
    val busy: Boolean = false,
)

data class CameraMove(val geo: Geo, val zoom: Double?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val locator = Locator(app)

    private val _wtfay = MutableStateFlow<Wtfay?>(null)
    val wtfay = _wtfay.asStateFlow()

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection = _selection.asStateFlow()

    private val _me = MutableStateFlow<Location?>(null)
    val me = _me.asStateFlow()

    private val _look = MutableStateFlow(store.look)
    val look = _look.asStateFlow()

    private val _layers = MutableStateFlow(store.layers)
    val layers = _layers.asStateFlow()

    private val _saved = MutableStateFlow(store.saved)
    val saved = _saved.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating = _locating.asStateFlow()

    private val _camera = MutableSharedFlow<CameraMove>(extraBufferCapacity = 4)
    val camera = _camera.asSharedFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast = _toast.asSharedFlow()

    /** Where the map is looking; biases place search. */
    var mapCenter: Geo? = null

    private var searchJob: Job? = null
    private var describeJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        viewModelScope.launch(Dispatchers.Default) { _wtfay.value = Wtfay.load() }
    }

    // ── map ─────────────────────────────────────────────────────────────────

    fun onMapTap(geo: Geo) {
        val w = _wtfay.value ?: return
        if (_search.value.query.isNotEmpty()) clearSearch()
        select(w.square(geo.lat, geo.lon), Origin.TAP, move = false)
    }

    fun setLook(l: MapLook) {
        _look.value = l
        store.look = l
    }

    fun toggleLayer(l: Layer) {
        _layers.update { if (l in it) it - l else it + l }
        store.layers = _layers.value
    }

    fun toastLater(text: String) {
        _toast.tryEmit(text)
    }

    fun clearSelection() {
        _selection.value = null
    }

    private fun select(sq: Square, origin: Origin, move: Boolean = true, zoom: Double? = 19.0, savedName: String? = null) {
        _selection.value = Selection(sq, origin, savedName = savedName)
        if (move) _camera.tryEmit(CameraMove(sq.center, zoom))
        describeJob?.cancel()
        describeJob = viewModelScope.launch {
            delay(250)
            val near = Places.describe(sq.center) ?: return@launch
            _selection.update { if (it?.square == sq) it.copy(near = near) else it }
        }
    }

    // ── «Я тут, бля!» ───────────────────────────────────────────────────────

    fun onLocation(l: Location) {
        _me.value = l
    }

    fun locateMe() {
        if (_locating.value) return
        if (!locator.enabled()) {
            _toast.tryEmit("Включи геолокацию, бля")
            return
        }
        _locating.value = true
        viewModelScope.launch {
            try {
                val w = _wtfay.filterNotNull().first()
                val loc = locator.current()
                if (loc == null) {
                    _toast.tryEmit("Спутники тебя не видят. Выйди на улицу")
                    return@launch
                }
                _me.value = loc
                select(w.square(loc.latitude, loc.longitude), Origin.ME)
            } finally {
                _locating.value = false
            }
        }
    }

    // ── search ──────────────────────────────────────────────────────────────

    fun onQuery(text: String) {
        _search.value = _search.value.copy(query = text)
        searchJob?.cancel()
        if (text.isBlank()) {
            _search.value = SearchState()
            return
        }
        searchJob = viewModelScope.launch {
            val w = _wtfay.filterNotNull().first()
            when (val input = withContext(Dispatchers.Default) { w.read(text) }) {
                is WordsInput.Found ->
                    _search.value = SearchState(text, listOf(SearchItem.Address(input.square)))
                is WordsInput.Partial ->
                    _search.value = SearchState(text, input.completions.map { SearchItem.Completion(it) }, input.problem)
                WordsInput.NotWords -> {
                    _search.value = _search.value.copy(busy = true, hint = null)
                    delay(350)  // debounce: Photon is a shared free service
                    val places = runCatching { Places.search(text, mapCenter) }
                    _search.value = SearchState(
                        text,
                        places.getOrDefault(emptyList()).map { SearchItem.PlaceHit(it) },
                        when {
                            places.isFailure -> "Нет интернета — ищу только по трём словам"
                            places.getOrNull().isNullOrEmpty() -> "Ничего не нашлось. Попробуй три слова: ///слово.слово.слово"
                            else -> null
                        },
                    )
                }
            }
        }
    }

    fun pick(item: SearchItem) {
        when (item) {
            is SearchItem.Address -> {
                select(item.square, Origin.SEARCH); clearSearch()
            }
            is SearchItem.Completion -> onQuery(item.text)
            is SearchItem.PlaceHit -> {
                val w = _wtfay.value ?: return
                select(w.square(item.place.geo.lat, item.place.geo.lon), Origin.SEARCH, zoom = 18.0)
                clearSearch()
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    // ── saved places ────────────────────────────────────────────────────────

    fun isSaved(sq: Square) = _saved.value.any { it.words == sq.text }

    fun save(sq: Square, name: String) {
        val clean = name.trim().ifEmpty { "Место №${_saved.value.size + 1}" }
        _saved.update { list -> listOf(SavedPlace(clean, sq.text, sq.center, System.currentTimeMillis())) + list.filter { it.words != sq.text } }
        store.saved = _saved.value
        _selection.update { if (it?.square == sq) it.copy(savedName = clean) else it }
        _toast.tryEmit("Запомнил: $clean")
    }

    fun unsave(words: String) {
        _saved.update { list -> list.filter { it.words != words } }
        store.saved = _saved.value
        _selection.update { if (it?.square?.text == words) it.copy(savedName = null) else it }
    }

    fun open(place: SavedPlace) {
        val w = _wtfay.value ?: return
        select(w.square(place.geo.lat, place.geo.lon), Origin.SAVED, savedName = place.name)
    }

    // ── sharing, voice, links ───────────────────────────────────────────────

    fun shareText(sel: Selection): String {
        val sq = sel.square
        val head = when (sel.origin) {
            Origin.ME -> "Я тут, бля!"
            else -> "Ты где, бля? Давай тут:"
        }
        val c = sq.center
        val lat = "%.6f".format(Locale.US, c.lat)
        val lon = "%.6f".format(Locale.US, c.lon)
        return "$head ///${sq.text}\n" +
            "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=19/$lat/$lon"
    }

    fun speak(sq: Square) {
        val words = with(sq.address) { "$first. $second. $argo." }
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(words, TextToSpeech.QUEUE_FLUSH, null, "addr")
            return
        }
        tts = TextToSpeech(getApplication()) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.forLanguageTag("ru-RU")
                tts?.setSpeechRate(0.9f)
                tts?.speak(words, TextToSpeech.QUEUE_FLUSH, null, "addr")
            } else {
                _toast.tryEmit("Озвучка не завелась")
            }
        }
    }

    /** tygde://слово.слово.слово, geo:lat,lon or shared text with ///слово.слово.слово. */
    fun handleIntent(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        } ?: return
        viewModelScope.launch {
            val w = _wtfay.filterNotNull().first()
            val data = intent.data
            if (data?.scheme == "geo") {
                parseGeo(data)?.let { select(w.square(it.lat, it.lon), Origin.LINK) }
                    ?: _toast.tryEmit("Не понял координаты")
                return@launch
            }
            val words = WORDS.find(Uri.decode(text))?.groupValues
            val sq = words?.let { w.find(it[1], it[2], it[3]) }
            if (sq != null) select(sq, Origin.LINK) else _toast.tryEmit("Тут нет трёх слов, бля")
        }
    }

    private fun parseGeo(uri: Uri): Geo? {
        // geo:lat,lon  or  geo:0,0?q=lat,lon(label); geo: URIs are opaque, so no getQueryParameter
        val ssp = uri.schemeSpecificPart ?: return null
        val q = ssp.substringAfter("q=", "").substringBefore('&')
        val raw = q.takeIf { COORDS.containsMatchIn(it) } ?: ssp.substringBefore('?')
        val m = COORDS.find(raw) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lon = m.groupValues[2].toDouble()
        if (lat == 0.0 && lon == 0.0) return null
        return Geo(lat, lon)
    }

    override fun onCleared() {
        tts?.shutdown()
    }

    companion object {
        private val WORDS = Regex("""([а-яёА-ЯЁ]+)\.([а-яёА-ЯЁ]+)\.([а-яёА-ЯЁ]+)""")
        private val COORDS = Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")
    }
}
