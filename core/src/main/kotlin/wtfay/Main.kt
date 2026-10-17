package wtfay

import kotlin.system.exitProcess

data class Place(val name: String, val lat: Double, val lon: Double)

/** Rough coordinates of well-known spots, for the demo. */
val DEMO_PLACES = listOf(
    Place("Москва, Красная площадь", 55.75393, 37.62079),
    Place("Москва, МГУ", 55.70311, 37.53083),
    Place("Санкт-Петербург, Дворцовая площадь", 59.93895, 30.31564),
    Place("Новосибирск, оперный театр", 55.03020, 82.92340),
    Place("Екатеринбург, Плотинка", 56.83800, 60.60390),
    Place("Казань, Кремль", 55.79880, 49.10560),
    Place("Нижний Новгород, Кремль", 56.32870, 44.00200),
    Place("Самара, набережная", 53.19590, 50.10020),
    Place("Ростов-на-Дону, Большая Садовая", 47.22250, 39.71880),
    Place("Волгоград, Мамаев курган", 48.74240, 44.53700),
    Place("Сочи, морской вокзал", 43.58550, 39.72310),
    Place("Калининград, Кафедральный собор", 54.70650, 20.51100),
    Place("Мурманск, Ленин (ледокол)", 68.97070, 33.07490),
    Place("Владивосток, Золотой мост", 43.11550, 131.88550),
    Place("Иркутск, 130-й квартал", 52.28690, 104.30500),
    Place("Якутск, площадь Ленина", 62.02780, 129.73220),
    Place("Красноярск, часовня Параскевы", 56.01530, 92.89320),
    Place("Минск, площадь Независимости", 53.89350, 27.54750),
    Place("Брест, крепость", 52.08330, 23.65530),
    Place("Алматы, Медеу", 43.15720, 77.05920),
    Place("Астана, Байтерек", 51.12830, 71.43050),
    Place("Шымкент, центр", 42.34170, 69.59010),
    Place("Ташкент, площадь Амира Тимура", 41.31110, 69.27970),
    Place("Самарканд, Регистан", 39.65470, 66.97580),
    Place("Бухара, Арк", 39.77470, 64.42860),
    Place("Бишкек, площадь Ала-Тоо", 42.87460, 74.61220),
    Place("Ош, Сулейман-Тоо", 40.52830, 72.79850),
    Place("Душанбе, площадь Дусти", 38.55980, 68.78700),
    Place("Ереван, площадь Республики", 40.17770, 44.51260),
    Place("Баку, Девичья башня", 40.36670, 49.83720),
)

fun main(args: Array<String>) {
    val t0 = System.nanoTime()
    val dict = Dictionaries.load()
    val t1 = System.nanoTime()
    val grid = Grid()
    val t2 = System.nanoTime()
    val codec = Codec(dict, grid)

    when (args.firstOrNull()) {
        "encode" -> {
            if (args.size != 3) usage()
            val a = codec.encode(args[1].toDouble(), args[2].toDouble())
            println(a)
        }
        "decode" -> {
            val words = args.drop(1).flatMap { it.split('.', ' ') }.filter { it.isNotBlank() }
            if (words.size != 3) usage()
            try {
                val (lat, lon) = codec.decode(words[0], words[1], words[2])
                println("%.6f, %.6f".format(lat, lon))
            } catch (e: AddressException) {
                System.err.println(e.message); exitProcess(1)
            }
        }
        null, "demo" -> {
            println("словари: common=${dict.common.size}, argo=${dict.argo.size}, загрузка ${(t1 - t0) / 1_000_000} мс")
            println("сетка: ${grid.rows} полос, ${"%.4g".format(grid.totalCells.toDouble())} ячеек, построение ${(t2 - t1) / 1_000_000} мс")
            println("адресов: ${"%.4g".format(codec.space.toDouble())}, запас ×${"%.3f".format(codec.space.toDouble() / grid.totalCells)}")
            println()
            val w = DEMO_PLACES.maxOf { it.name.length }
            for (p in DEMO_PLACES) {
                val a = codec.encode(p.lat, p.lon)
                println("${p.name.padEnd(w)}  ${a.first} ${a.second} ${a.argo}")
            }
        }
        else -> usage()
    }
}

private fun usage(): Nothing {
    System.err.println(
        """
        usage:
          demo                       адреса для городов СНГ
          encode <lat> <lon>         координаты -> три слова
          decode <w1> <w2> <w3>      три слова -> координаты центра ячейки
        """.trimIndent()
    )
    exitProcess(2)
}
