package app.tygdeblya

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlace(val name: String, val words: String, val geo: Geo, val at: Long)

/** Settings and saved places, kept in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tygde", Context.MODE_PRIVATE)

    var look: MapLook
        get() = MapLook.entries.firstOrNull { it.name == prefs.getString("look", null) } ?: MapLook.NIGHT
        set(v) = prefs.edit().putString("look", v.name).apply()

    var layers: Set<Layer>
        get() = prefs.getStringSet("layers", null)?.mapNotNull { n -> Layer.entries.firstOrNull { it.name == n } }?.toSet()
            ?: Layer.DEFAULT
        set(v) = prefs.edit().putStringSet("layers", v.map { it.name }.toSet()).apply()

    var saved: List<SavedPlace>
        get() {
            val arr = JSONArray(prefs.getString("saved", "[]"))
            return List(arr.length()) {
                val o = arr.getJSONObject(it)
                SavedPlace(o.getString("name"), o.getString("words"), Geo(o.getDouble("lat"), o.getDouble("lon")), o.getLong("at"))
            }
        }
        set(v) {
            val arr = JSONArray()
            for (p in v) arr.put(
                JSONObject().put("name", p.name).put("words", p.words)
                    .put("lat", p.geo.lat).put("lon", p.geo.lon).put("at", p.at)
            )
            prefs.edit().putString("saved", arr.toString()).apply()
        }
}
