package app.tygdeblya

import android.graphics.Color as AColor
import android.view.Gravity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer as MlLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.sin

/**
 * Everything drawn on the MapLibre map. The UI pushes state in through the
 * setters; after a style switch all our sources and layers are rebuilt from it.
 */
class MapController(
    private val map: MapLibreMap,
    private val scope: CoroutineScope,
    private val onTap: (Geo) -> Unit,
) {
    private var style: Style? = null
    private var look: MapLook? = null
    private var layers: Set<Layer> = Layer.DEFAULT
    private var selection: Square? = null
    private var me: Geo? = null
    private var meAccuracy = 0f
    private var saved: List<SavedPlace> = emptyList()
    private var wtfay: Wtfay? = null
    private var gridJob: Job? = null

    init {
        map.uiSettings.apply {
            isLogoEnabled = false
            isCompassEnabled = true
            isAttributionEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
        }
        map.addOnMapClickListener { onTap(Geo(it.latitude, it.longitude)); true }
        map.addOnCameraIdleListener { refreshGrid() }
    }

    fun setInsets(top: Int, bottom: Int, density: Float) {
        val d = density
        map.uiSettings.setCompassMargins(0, top + (84 * d).toInt(), (16 * d).toInt(), 0)
        // bottom-left is taken by the address card, so attribution sits under the search bar
        map.uiSettings.attributionGravity = Gravity.TOP or Gravity.START
        map.uiSettings.setAttributionMargins((20 * d).toInt(), top + (84 * d).toInt(), 0, 0)
    }

    fun setWtfay(w: Wtfay?) {
        if (w === wtfay) return
        wtfay = w
        refreshGrid()
    }

    fun setLook(l: MapLook) {
        if (l == look) return
        look = l
        style = null
        map.setStyle(Style.Builder().fromUri(l.styleUrl)) { s ->
            if (look != l) return@setStyle
            when (l) {
                MapLook.ACID -> paintAcid(s)
                MapLook.SATELLITE -> addSatellite(s)
                else -> {}
            }
            addOurLayers(s, l)
            style = s
            applyLayers()
            pushSelection(); pushMe(); pushSaved(); refreshGrid()
        }
    }

    fun setLayers(set: Set<Layer>) {
        if (set == layers) return
        layers = set
        applyLayers()
        refreshGrid()
    }

    fun setSelection(sq: Square?) {
        if (sq == selection) return
        selection = sq
        pushSelection()
    }

    fun setMe(geo: Geo?, accuracy: Float) {
        me = geo; meAccuracy = accuracy
        pushMe()
    }

    fun setSaved(list: List<SavedPlace>) {
        if (list == saved) return
        saved = list
        pushSaved()
    }

    fun flyTo(geo: Geo, zoom: Double?) {
        val pos = CameraPosition.Builder()
            .target(LatLng(geo.lat, geo.lon))
            .zoom(zoom ?: map.cameraPosition.zoom.coerceAtLeast(17.5))
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 1400)
    }

    private var storming = false

    /** Rocks the camera like a boat in a storm for a couple of seconds, then puts it back. */
    suspend fun storm(durationMs: Long = 2600) {
        if (storming) return
        storming = true
        val base = map.cameraPosition
        val start = System.currentTimeMillis()
        try {
            while (true) {
                val t = (System.currentTimeMillis() - start) / 1000.0
                val a = 1 - t * 1000 / durationMs
                if (a <= 0) break
                map.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(base)
                        .bearing(base.bearing + sin(t * 7) * 7 * a)
                        .tilt((base.tilt + 14 * a * (1 + sin(t * 5)) / 2).coerceIn(0.0, 60.0))
                        .build()
                ))
                delay(16)
            }
        } finally {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(base))
            storming = false
        }
    }

    fun resetNorth() {
        map.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(map.cameraPosition).bearing(0.0).tilt(0.0).build()), 600)
    }

    /**
     * What water is under [geo], read from the rendered map: null if dry land,
     * or if the point is off screen or the style is still loading.
     */
    fun waterAt(geo: Geo): Water? {
        val s = style ?: return null
        if (!onScreen(geo)) return null
        val p = map.projection.toScreenLocation(LatLng(geo.lat, geo.lon))
        val ids = s.layers.filter { it is FillLayer && "water" in it.id && !it.id.startsWith("tg-") }.map { it.id }
        if (ids.isEmpty()) return null
        val hit = map.queryRenderedFeatures(p, *ids.toTypedArray()).firstOrNull() ?: return null
        return Water.of(hit.getStringProperty("class"))
    }

    fun onScreen(geo: Geo) = map.projection.visibleRegion.latLngBounds.contains(LatLng(geo.lat, geo.lon))

    val bearing: Double get() = map.cameraPosition.bearing

    val center: Geo get() = map.cameraPosition.target?.let { Geo(it.latitude, it.longitude) } ?: Geo(0.0, 0.0)

    // ── style tweaks ────────────────────────────────────────────────────────

    /** Positron repainted in black, violet and acid green. */
    private fun paintAcid(s: Style) {
        for (layer in s.layers) {
            val id = layer.id
            when (layer) {
                is BackgroundLayer -> layer.setProperties(backgroundColor(ACID_BG))
                is FillLayer -> layer.setProperties(
                    when {
                        "water" in id -> fillColor("#2A0D63")
                        "park" in id || "wood" in id || "grass" in id || "landcover" in id -> fillColor("#0F1F12")
                        "building" in id -> fillColor("#2A0F2E")
                        else -> fillColor("#0D0D14")
                    },
                    fillOutlineColor(if ("building" in id) "#FF2E88" else "#00000000"),
                )
                is LineLayer -> layer.setProperties(
                    when {
                        "water" in id -> lineColor("#5B2BD6")
                        "boundary" in id || "admin" in id -> lineColor("#FF2E88")
                        "casing" in id -> lineColor("#1B2600")
                        "rail" in id -> lineColor("#7A2E5C")
                        "path" in id || "track" in id -> lineColor("#2F3D12")
                        "minor" in id || "service" in id -> lineColor("#5C7A14")
                        else -> lineColor("#C8FF2E")
                    },
                )
                is SymbolLayer -> layer.setProperties(
                    textColor(if ("water" in id) "#B69CFF" else "#F2F2F2"),
                    textHaloColor("#000000"),
                    textHaloWidth(1.4f),
                )
            }
        }
    }

    /** Sentinel-2 cloudless imagery under the roads and labels of Liberty. */
    private fun addSatellite(s: Style) {
        val tiles = TileSet("2.2.0", SATELLITE_TILES).apply {
            maxZoom = 15f
            attribution = SATELLITE_ATTRIBUTION
        }
        s.addSource(RasterSource("tg-sat", tiles, 256))
        val firstLine = s.layers.firstOrNull { it is LineLayer && ("road" in it.id || "highway" in it.id || "tunnel" in it.id) }?.id
        val raster = RasterLayer("tg-sat", "tg-sat").withProperties(rasterSaturation(0.1f), rasterContrast(0.1f))
        if (firstLine != null) s.addLayerBelow(raster, firstLine) else s.addLayer(raster)
        for (layer in s.layers) {
            if (layer is FillExtrusionLayer) layer.setProperties(visibility(Property.NONE))
            if (layer is LineLayer && ("road" in layer.id || "highway" in layer.id || "bridge" in layer.id))
                layer.setProperties(lineOpacity(0.55f))
            if (layer is SymbolLayer) layer.setProperties(textColor("#FFFFFF"), textHaloColor("#000000"), textHaloWidth(1.5f))
        }
    }

    // ── our layers ──────────────────────────────────────────────────────────

    private fun addOurLayers(s: Style, l: MapLook) {
        val ink = if (l.dark) "#FFFFFF" else "#14141A"
        val firstSymbol = s.layers.firstOrNull { it is SymbolLayer }?.id

        fun below(layer: MlLayer) = if (firstSymbol != null) s.addLayerBelow(layer, firstSymbol) else s.addLayer(layer)

        // our own 3D buildings so every style can have them
        s.layers.filterIsInstance<FillExtrusionLayer>().forEach { s.removeLayer(it) }
        val openMapTiles = s.getSource("openmaptiles") != null
        if (openMapTiles) below(
            FillExtrusionLayer(ID_3D, "openmaptiles").withSourceLayer("building").withProperties(
                fillExtrusionColor(when (l) {
                    MapLook.ACID -> "#24102A"
                    MapLook.FJORD -> "#56627F"
                    MapLook.SATELLITE -> "#B8B0A0"
                    else -> if (l.dark) "#23232C" else "#E4DED3"
                }),
                fillExtrusionHeight(Expression.coalesce(Expression.get("render_height"), Expression.literal(6))),
                fillExtrusionBase(Expression.coalesce(Expression.get("render_min_height"), Expression.literal(0))),
                fillExtrusionOpacity(0.85f),
            ).also { it.minZoom = 14f }
        )

        s.addSource(GeoJsonSource(SRC_GRID))
        below(LineLayer(ID_GRID, SRC_GRID).withProperties(
            lineColor(ink), lineOpacity(Expression.interpolate(Expression.linear(), Expression.zoom(),
                Expression.stop(17.5, 0f), Expression.stop(18.5, if (l.dark) 0.14f else 0.1f),
                Expression.stop(20, if (l.dark) 0.32f else 0.24f))),
            lineWidth(0.7f),
        ))

        s.addSource(GeoJsonSource(SRC_SEL))
        s.addLayer(FillLayer(ID_SEL_FILL, SRC_SEL).withProperties(fillColor(ACID), fillOpacity(0.28f))
            .withFilter(Expression.eq(Expression.geometryType(), "Polygon")))
        s.addLayer(LineLayer(ID_SEL_LINE, SRC_SEL).withProperties(lineColor(ACID), lineWidth(2.5f))
            .withFilter(Expression.eq(Expression.geometryType(), "Polygon")))
        s.addLayer(CircleLayer(ID_SEL_PIN, SRC_SEL).withProperties(
            circleColor(ACID), circleRadius(7f), circleStrokeColor("#0E0E12"), circleStrokeWidth(3f),
            circleOpacity(Expression.interpolate(Expression.linear(), Expression.zoom(),
                Expression.stop(17, 1f), Expression.stop(18.5, 0f))),
            circleStrokeOpacity(Expression.interpolate(Expression.linear(), Expression.zoom(),
                Expression.stop(17, 1f), Expression.stop(18.5, 0f))),
        ).withFilter(Expression.eq(Expression.geometryType(), "Point")))

        s.addSource(GeoJsonSource(SRC_SAVED))
        s.addLayer(CircleLayer(ID_SAVED, SRC_SAVED).withProperties(
            circleColor("#FFD23F"), circleRadius(6f), circleStrokeColor("#0E0E12"), circleStrokeWidth(2f),
        ))
        s.addLayer(SymbolLayer(ID_SAVED_TEXT, SRC_SAVED).withProperties(
            textField(Expression.get("name")), textFont(arrayOf("Noto Sans Bold")), textSize(12f),
            textOffset(arrayOf(0f, 1.3f)), textAnchor(Property.TEXT_ANCHOR_TOP),
            textColor(if (l.dark) "#FFD23F" else "#5A4500"), textHaloColor(if (l.dark) "#000000" else "#FFFFFF"),
            textHaloWidth(1.5f), textOptional(true),
        ))

        s.addSource(GeoJsonSource(SRC_ME))
        s.addLayer(CircleLayer(ID_ME_HALO, SRC_ME).withProperties(
            circleColor(PINK), circleOpacity(0.18f), circleRadius(22f), circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        ))
        s.addLayer(CircleLayer(ID_ME, SRC_ME).withProperties(
            circleColor(PINK), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
        ))
    }

    private fun applyLayers() {
        val s = style ?: return
        fun show(id: String, on: Boolean) = s.getLayer(id)?.setProperties(visibility(if (on) Property.VISIBLE else Property.NONE))
        show(ID_3D, Layer.BUILDINGS in layers)
        show(ID_GRID, Layer.GRID in layers)
        show(ID_SAVED, Layer.SAVED in layers)
        show(ID_SAVED_TEXT, Layer.SAVED in layers)
        for (layer in s.layers) if (layer is SymbolLayer && !layer.id.startsWith("tg-")) {
            layer.setProperties(visibility(if (Layer.LABELS in layers) Property.VISIBLE else Property.NONE))
        }
    }

    private fun source(id: String) = style?.getSource(id) as? GeoJsonSource

    private fun pushSelection() {
        val src = source(SRC_SEL) ?: return
        val sq = selection
        if (sq == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList())); return
        }
        val ring = listOf(
            Point.fromLngLat(sq.west, sq.south), Point.fromLngLat(sq.east, sq.south),
            Point.fromLngLat(sq.east, sq.north), Point.fromLngLat(sq.west, sq.north),
            Point.fromLngLat(sq.west, sq.south),
        )
        src.setGeoJson(FeatureCollection.fromFeatures(listOf(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))),
            Feature.fromGeometry(Point.fromLngLat(sq.center.lon, sq.center.lat)),
        )))
    }

    private fun pushMe() {
        val src = source(SRC_ME) ?: return
        val g = me
        src.setGeoJson(FeatureCollection.fromFeatures(
            if (g == null) emptyList() else listOf(Feature.fromGeometry(Point.fromLngLat(g.lon, g.lat)))
        ))
    }

    private fun pushSaved() {
        val src = source(SRC_SAVED) ?: return
        src.setGeoJson(FeatureCollection.fromFeatures(saved.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.geo.lon, p.geo.lat)).apply { addStringProperty("name", p.name) }
        }))
    }

    private fun refreshGrid() {
        val w = wtfay ?: return
        val src = source(SRC_GRID) ?: return
        gridJob?.cancel()
        if (Layer.GRID !in layers || map.cameraPosition.zoom < 17.5) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList())); return
        }
        val b = map.projection.visibleRegion.latLngBounds
        gridJob = scope.launch {
            val lines = withContext(Dispatchers.Default) {
                w.gridLines(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast)?.map { seg ->
                    LineString.fromLngLats(seg.map { Point.fromLngLat(it.lon, it.lat) })
                }
            }
            src.setGeoJson(FeatureCollection.fromFeatures(
                if (lines == null) emptyList() else listOf(Feature.fromGeometry(MultiLineString.fromLineStrings(lines)))
            ))
        }
    }

    companion object {
        const val ACID = "#C8FF2E"
        const val PINK = "#FF2E88"
        private val ACID_BG = AColor.parseColor("#08080C")

        private const val SATELLITE_TILES =
            "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2024_3857/default/g/{z}/{y}/{x}.jpg"
        private const val SATELLITE_ATTRIBUTION =
            "<a href=\"https://s2maps.eu\">Sentinel-2 cloudless – https://s2maps.eu by EOX IT Services GmbH</a> " +
                "(Contains modified Copernicus Sentinel data 2024)"

        private const val SRC_GRID = "tg-grid"
        private const val SRC_SEL = "tg-sel"
        private const val SRC_ME = "tg-me"
        private const val SRC_SAVED = "tg-saved"
        private const val ID_3D = "tg-3d"
        private const val ID_GRID = "tg-grid"
        private const val ID_SEL_FILL = "tg-sel-fill"
        private const val ID_SEL_LINE = "tg-sel-line"
        private const val ID_SEL_PIN = "tg-sel-pin"
        private const val ID_SAVED = "tg-saved"
        private const val ID_SAVED_TEXT = "tg-saved-text"
        private const val ID_ME_HALO = "tg-me-halo"
        private const val ID_ME = "tg-me"
    }
}
