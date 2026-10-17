package app.tygdeblya

import androidx.compose.ui.graphics.Color

/**
 * Map styles. All are OpenFreeMap vector styles (OpenStreetMap data, no API key);
 * some get repainted or topped with satellite imagery once loaded, see [MapController].
 */
enum class MapLook(
    val title: String,
    val tagline: String,
    val styleUrl: String,
    /** Swatch for the picker: background, water, roads. */
    val swatch: List<Color>,
    val dark: Boolean,
) {
    NIGHT(
        "Ночь", "Для тёмных делишек", "https://tiles.openfreemap.org/styles/dark",
        listOf(Color(0xFF111114), Color(0xFF1B2433), Color(0xFF3A3A44)), dark = true,
    ),
    ACID(
        "Кислота", "Глаза вытекут", "https://tiles.openfreemap.org/styles/positron",
        listOf(Color(0xFF08080C), Color(0xFF3B0F7A), Color(0xFFC8FF2E)), dark = true,
    ),
    CITY(
        "Город", "Дома в 3D", "https://tiles.openfreemap.org/styles/liberty",
        listOf(Color(0xFFF2EFE9), Color(0xFF9EC7F0), Color(0xFFF7B267)), dark = false,
    ),
    LIGHT(
        "Бумага", "Чисто и строго", "https://tiles.openfreemap.org/styles/positron",
        listOf(Color(0xFFF8F8F6), Color(0xFFD4DADC), Color(0xFFFFFFFF)), dark = false,
    ),
    FJORD(
        "Фьорд", "Холодно, как в Мурманске", "https://tiles.openfreemap.org/styles/fiord",
        listOf(Color(0xFF45516E), Color(0xFF38435C), Color(0xFF6E7D9C)), dark = true,
    ),
    SATELLITE(
        "Спутник", "Вид сверху, бля", "https://tiles.openfreemap.org/styles/liberty",
        listOf(Color(0xFF2E3B22), Color(0xFF16324A), Color(0xFFB8A57C)), dark = true,
    );
}

/** Things drawn on top of the map that can be switched on and off. */
enum class Layer(val title: String, val hint: String) {
    GRID("Сетка 3×3 м", "Видна при сильном приближении"),
    BUILDINGS("3D-дома", "Наклони карту двумя пальцами"),
    LABELS("Подписи", "Улицы, города, всякое"),
    SAVED("Мои места", "Звёздочки на карте");

    companion object {
        val DEFAULT = setOf(GRID, BUILDINGS, LABELS, SAVED)
    }
}
