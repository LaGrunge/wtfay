package app.tygdeblya

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wtfay.WordList
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

// Пасхалки про море, яхтинг и свободу.

/** What kind of water a point is in, from the OpenMapTiles `water` layer. */
enum class Water {
    OCEAN, LAKE, RIVER, POOL;

    /** Water you can sail on. */
    val sailable get() = this != POOL

    companion object {
        fun of(cls: String?): Water = when (cls) {
            "ocean" -> OCEAN
            "lake", "pond", "dock" -> LAKE
            "swimming_pool" -> POOL
            else -> RIVER
        }
    }
}

object Sea {
    /** Point Nemo, the oceanic pole of inaccessibility (Lukatela, 1992). */
    val NEMO = Geo(-48.876667, -123.393333)

    /**
     * The secret address, in either order. Neither ordering is a real square
     * (checked against the dictionaries and Codec.SEED of 0.2), so the joke
     * does not steal anyone's address.
     */
    private val SECRET = setOf("ветер.свобода.кайф", "свобода.ветер.кайф")

    fun isSecret(text: String): Boolean {
        val words = text.trim().removePrefix("///").split('.', ' ', ',', '/', '·').filter { it.isNotBlank() }
        return words.size == 3 && words.joinToString(".") { WordList.key(it) } in SECRET
    }

    /** Shown instead of the street name for a square in the open sea. */
    val OPEN_SEA = listOf(
        "Нейтральные воды. Тут ты никому ничего не должен",
        "Ни ментов, ни начальника, ни сигнала",
        "Только ветер, чайки и ты",
        "Здесь даже прописки нет",
        "Берег где-то там. Наверное",
        "Свобода, бля. Солёная",
    )

    fun openSea(sq: Square) = OPEN_SEA[(sq.cell % OPEN_SEA.size).toInt()]

    val SAYINGS = listOf(
        "Хвали море, а сиди на берегу",
        "Пьяному море по колено",
        "Семь футов под килем!",
        "Свистать всех наверх!",
        "Полундра! Шторм девять баллов",
        "Не можешь изменить ветер — переставь паруса",
        "Корабль в гавани в безопасности, но не для этого строят корабли",
        "Свобода — это когда ветер в паруса, а начальник на берегу",
        "Кто в море не бывал, тот Богу не маливался",
    )
}

// ── Point Nemo ──────────────────────────────────────────────────────────────

@Composable
fun NemoOverlay(visible: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(700)) + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xCC05101C)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).padding(24.dp).glass().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Sailboat(Modifier.fillMaxWidth().height(120.dp))
                Spacer(Modifier.height(10.dp))
                Text("ОТДАТЬ ШВАРТОВЫ!", style = MaterialTheme.typography.headlineSmall, color = Brand.Acid)
                Text("Точка Немо · 48°52′ ю. ш. 123°23′ з. д.", style = MaterialTheme.typography.labelMedium, color = Brand.Muted)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Самое далёкое от суши место на планете, океанский полюс недоступности. " +
                        "До ближайшей земли 2 688 км во все стороны: остров Дюси, Моту-Нуи у острова Пасхи " +
                        "и остров Махер у Антарктиды.\n\n" +
                        "Ближе всего к тебе здесь обычно космонавты: МКС пролетает всего в 400 км над головой.\n\n" +
                        "Назвали в честь капитана Немо, а nemo по-латыни значит «никто». " +
                        "Точку вычислил в 1992 году инженер Хрвое Лукатела. " +
                        "Рядом кладбище космических кораблей: сюда сводят с орбиты старые спутники, тут затонула станция «Мир». " +
                        "А в паре сотен километров Лавкрафт поселил затонувший город Р’льех.\n\n" +
                        "Ни берегов, ни начальников, ни сигнала. Свобода.",
                    style = MaterialTheme.typography.bodyMedium, color = Brand.Text, textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Полный вперёд",
                    style = MaterialTheme.typography.labelLarge, color = Brand.Ink,
                    modifier = Modifier.clip(Brand.PillShape).background(Brand.Acid).clickable(onClick = onClose)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** A little sailboat rocking on waves. */
@Composable
fun Sailboat(modifier: Modifier, color: Color = Brand.Acid) {
    val t by rememberInfiniteTransition(label = "sea").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier) {
        val phase = t * 2 * PI.toFloat()
        val w = size.width
        val h = size.height
        val sea = h * 0.72f
        val boatX = w / 2
        val bob = sin(phase) * h * 0.035f
        rotate(sin(phase + 0.8f) * 6f, Offset(boatX, sea + bob)) { boat(boatX, sea + bob, h * 0.62f, color) }
        for (k in 0..1) wave(sea + h * (0.06f + k * 0.12f), phase + k * 1.3f, if (k == 0) Brand.Faint else Brand.Stroke)
    }
}

private fun DrawScope.boat(x: Float, waterline: Float, height: Float, color: Color) {
    val hullW = height * 0.95f
    val hull = Path().apply {
        moveTo(x - hullW / 2, waterline - height * 0.1f)
        lineTo(x + hullW / 2, waterline - height * 0.1f)
        lineTo(x + hullW * 0.32f, waterline + height * 0.06f)
        lineTo(x - hullW * 0.32f, waterline + height * 0.06f)
        close()
    }
    drawPath(hull, color)
    val mastTop = waterline - height * 0.95f
    drawLine(color, Offset(x, waterline - height * 0.1f), Offset(x, mastTop), height * 0.025f)
    drawPath(Path().apply {
        moveTo(x + height * 0.03f, mastTop); lineTo(x + height * 0.42f, waterline - height * 0.16f)
        lineTo(x + height * 0.03f, waterline - height * 0.16f); close()
    }, color)
    drawPath(Path().apply {
        moveTo(x - height * 0.03f, mastTop + height * 0.1f); lineTo(x - height * 0.3f, waterline - height * 0.16f)
        lineTo(x - height * 0.03f, waterline - height * 0.16f); close()
    }, Brand.Pink)
}

private fun DrawScope.wave(y: Float, phase: Float, color: Color) {
    val w = size.width
    val amp = size.height * 0.03f
    val path = Path().apply {
        moveTo(0f, y)
        var x = 0f
        while (x <= w) {
            lineTo(x, y + sin(x / w * 4 * PI.toFloat() + phase) * amp); x += 6f
        }
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
}

// ── shake ───────────────────────────────────────────────────────────────────

/** Calls [onShake] when the phone is shaken hard, at most once every two seconds. */
@Composable
fun ShakeListener(onShake: () -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onShake)
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return@DisposableEffect onDispose {}
        var last = 0L
        var hits = 0
        var firstHit = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = e.values
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (g < 2.4f) return
                val now = System.currentTimeMillis()
                if (now - firstHit > 700) { hits = 0; firstHit = now }
                // a real shake is a few hard jolts in quick succession, not one bump
                if (++hits >= 3 && now - last > 2000) {
                    last = now; hits = 0
                    callback()
                }
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
}
