package app.tygdeblya

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(val title: String, val subtitle: String, val geo: Geo)

/**
 * Place search and reverse geocoding through Photon (photon.komoot.io), an open
 * geocoder over OpenStreetMap data. Light use only, as its terms ask.
 */
object Places {
    private const val BASE = "https://photon.komoot.io"

    suspend fun search(query: String, near: Geo?): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val bias = near?.let { "&lat=${it.lat}&lon=${it.lon}" } ?: ""
        features(get("$BASE/api/?q=$q&limit=7$bias")).mapNotNull(::toPlace)
    }

    /** A short human description of what is at [geo], like «Тверская ул., 7, Москва». */
    suspend fun describe(geo: Geo): String? = withContext(Dispatchers.IO) {
        runCatching {
            features(get("$BASE/reverse?lat=${geo.lat}&lon=${geo.lon}&limit=1")).firstOrNull()?.let { p ->
                val street = listOfNotNull(p.str("street"), p.str("housenumber")).joinToString(", ")
                listOfNotNull(
                    p.str("name").takeIf { it != street },
                    street.ifBlank { null },
                    p.str("city") ?: p.str("county"),
                ).distinct().joinToString(", ").ifBlank { null }
            }
        }.getOrNull()
    }

    private fun toPlace(p: JSONObject): Place? {
        val geom = p.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val props = p.optJSONObject("properties") ?: return null
        val street = listOfNotNull(props.str("street"), props.str("housenumber")).joinToString(" ")
        val title = props.str("name") ?: street.ifBlank { null } ?: return null
        val subtitle = listOfNotNull(
            street.takeIf { it.isNotBlank() && it != title },
            props.str("city"),
            props.str("state"),
            props.str("country"),
        ).distinct().joinToString(", ")
        return Place(title, subtitle, Geo(geom.getDouble(1), geom.getDouble(0)))
    }

    private fun features(json: String): List<JSONObject> {
        val arr = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }
    }

    /** Properties of a feature, flattened so callers can read them by name. */
    private fun JSONObject.str(key: String): String? {
        val props = optJSONObject("properties") ?: this
        return props.optString(key).takeIf { it.isNotBlank() }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "TyGdeBlya/0.1 (Android)")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}
