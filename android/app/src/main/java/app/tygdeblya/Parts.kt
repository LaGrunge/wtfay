package app.tygdeblya

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ── small pieces ────────────────────────────────────────────────────────────

fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape = Brand.PanelShape) =
    this.clip(shape).background(Brand.Glass).border(Brand.Border, shape)

@Composable
fun GlassIconButton(icon: ImageVector, label: String, tint: Color = Brand.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint) }
}

/** The address as «///слово.слово.хрень», with the swear word in pink. */
fun addressText(sq: Square) = buildAnnotatedString {
    // word joiner keeps «///» on the first word; zero-width spaces let long addresses wrap at dots
    withStyle(SpanStyle(color = Brand.Acid)) { append("///\u2060") }
    withStyle(SpanStyle(color = Brand.Text)) { append(sq.address.first) }
    withStyle(SpanStyle(color = Brand.Faint)) { append(".\u200B") }
    withStyle(SpanStyle(color = Brand.Text)) { append(sq.address.second) }
    withStyle(SpanStyle(color = Brand.Faint)) { append(".\u200B") }
    withStyle(SpanStyle(color = Brand.Pink)) { append(sq.address.argo) }
}

// ── «Я тут, бля!» ───────────────────────────────────────────────────────────

private val LOCATING = listOf("Ищу тебя…", "Пинаю спутники…", "Где ж ты, бля…", "Почти нашёл…", "Режу на квадратики…")

@Composable
fun HereButton(locating: Boolean, onWater: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        0.92f, 1.12f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    var phrase by remember { mutableIntStateOf(0) }
    LaunchedEffect(locating) {
        while (locating) {
            delay(1100); phrase = (phrase + 1) % LOCATING.size
        }
    }
    val pressScale by animateFloatAsState(if (locating) 0.96f else 1f, label = "press")

    Box(Modifier.padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        // soft acid halo
        Box(
            Modifier.matchParentSize().scale(glow).alpha(0.35f)
                .background(Brush.radialGradient(listOf(Brand.Acid, Color.Transparent)), Brand.PillShape)
        )
        Row(
            Modifier.scale(pressScale).clip(Brand.PillShape)
                .background(Brush.horizontalGradient(listOf(Brand.Acid, Color(0xFFE4FF6B))))
                .clickable(enabled = !locating, onClick = onClick)
                .padding(horizontal = 30.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (locating) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Brand.Ink, strokeWidth = 3.dp)
            } else {
                Icon(Icons.Rounded.NearMe, null, tint = Brand.Ink, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                if (locating) LOCATING[phrase] else if (onWater) "Я ТУТ, ПОЛУНДРА!" else "Я ТУТ, БЛЯ!",
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "here",
            ) { text ->
                Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight(900)), color = Brand.Ink)
            }
        }
    }
}

@Composable
fun SmallHereButton(locating: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(56.dp).clip(CircleShape).background(Brand.Acid).clickable(enabled = !locating, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (locating) CircularProgressIndicator(Modifier.size(22.dp), color = Brand.Ink, strokeWidth = 3.dp)
        else Icon(Icons.Rounded.NearMe, "Я тут", tint = Brand.Ink)
    }
}

// ── address card ────────────────────────────────────────────────────────────

@Composable
fun AddressCard(
    sel: Selection,
    saved: Boolean,
    distance: Float?,
    arrow: Float?,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRoute: () -> Unit,
    onSpeak: () -> Unit,
    onStar: () -> Unit,
    onFocus: () -> Unit,
) {
    val sailing = sel.water?.sailable == true
    val openSea = sel.water == Water.OCEAN && sel.origin != Origin.ME
    val label = when {
        sel.origin == Origin.NEMO -> "ТОЧКА НЕМО"
        sel.origin == Origin.ME && sailing -> "ТЫ НА ВОДЕ, ПОЛУНДРА"
        openSea -> "НЕЙТРАЛЬНЫЕ ВОДЫ"
        else -> when (sel.origin) {
            Origin.ME -> "ТЫ ТУТ, БЛЯ"
            Origin.TAP -> "ЭТОТ КВАДРАТ"
            Origin.SEARCH -> "НАШЁЛ"
            Origin.SAVED -> "МОЁ МЕСТО"
            Origin.LINK -> "ТЕБЕ ПРИСЛАЛИ"
            Origin.NEMO -> "ТОЧКА НЕМО"
        }
    }
    val near = when {
        sel.origin == Origin.NEMO -> "2 688 км до ближайшей земли. Свобода"
        openSea -> Sea.openSea(sel.square)
        sel.water == Water.POOL -> "Бассейн. Не писай в него"
        else -> sel.near ?: "Квадрат 3×3 метра"
    }
    Column(Modifier.fillMaxWidth().glass().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (openSea || sailing || sel.origin == Origin.NEMO) {
                Sailboat(Modifier.size(width = 34.dp, height = 22.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = if (sel.origin == Origin.ME || sel.origin == Origin.NEMO || openSea) Brand.Acid else Brand.Muted,
            )
            sel.savedName?.let {
                Text("  ★ $it", style = MaterialTheme.typography.labelMedium, color = Brand.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Закрыть", tint = Brand.Muted, modifier = Modifier.size(20.dp))
            }
        }

        AnimatedContent(sel.square, label = "addr", transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
        }) { sq ->
            BasicText(
                addressText(sq),
                style = MaterialTheme.typography.headlineSmall,
                autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 28.sp),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 2.dp).clickable(onClick = onCopy),
            )
        }

        Row(Modifier.padding(top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, null, tint = Brand.Faint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                near,
                style = MaterialTheme.typography.bodyMedium, color = Brand.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable(onClick = onFocus),
            )
            if (distance != null && sel.origin != Origin.ME && distance > 4f) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Rounded.Navigation, "Направление", tint = Brand.Acid,
                    modifier = Modifier.size(18.dp).rotate(arrow ?: 0f),
                )
                Spacer(Modifier.width(4.dp))
                Text(formatDistance(distance), style = MaterialTheme.typography.labelLarge, color = Brand.Text)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(52.dp).clip(Brand.PillShape).background(Brand.Acid).clickable(onClick = onShare),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Share, null, tint = Brand.Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Кинуть", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = Brand.Ink)
            }
            RoundAction(Icons.Rounded.ContentCopy, "Копировать", onClick = onCopy)
            RoundAction(Icons.Rounded.Directions, "Маршрут", onClick = onRoute)
            RoundAction(Icons.Rounded.VolumeUp, "Озвучить", onClick = onSpeak)
            RoundAction(
                if (saved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                "Запомнить", tint = if (saved) Brand.Gold else Brand.Text, onClick = onStar,
            )
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, tint: Color = Brand.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color(0x14FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
}

// ── search ──────────────────────────────────────────────────────────────────

private val PLACEHOLDERS = listOf(
    "Три слова или место",
    "///слово.слово.хрень",
    "Тверская, 7",
    "///куда.ты.пропал",
)

@Composable
fun SearchPanel(
    state: SearchState,
    ready: Boolean,
    onQuery: (String) -> Unit,
    onPick: (SearchItem) -> Unit,
    onClear: () -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var ph by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3200); ph = (ph + 1) % PLACEHOLDERS.size
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).glass(Brand.PillShape).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (focused || state.query.isNotEmpty()) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Search,
                null, tint = Brand.Acid,
                modifier = Modifier.clip(CircleShape).clickable(enabled = state.query.isNotEmpty()) { onClear() },
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.query.isEmpty()) {
                    AnimatedContent(PLACEHOLDERS[ph], label = "ph", transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    }) { Text(it, style = MaterialTheme.typography.bodyLarge, color = Brand.Faint) }
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    enabled = ready,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Brand.Text),
                    cursorBrush = SolidColor(Brand.Acid),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { state.items.firstOrNull()?.let(onPick) }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
                )
            }
            if (state.query.isNotEmpty()) {
                Icon(Icons.Rounded.Close, "Очистить", tint = Brand.Muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onClear))
            }
        }

        AnimatedVisibility(state.busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(2.dp),
                color = Brand.Acid, trackColor = Color.Transparent,
            )
        }

        AnimatedVisibility(
            visible = state.items.isNotEmpty() || state.hint != null,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit = fadeOut(),
        ) {
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp))) {
                state.hint?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Brand.Pink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
                    if (state.items.isNotEmpty()) HorizontalDivider(color = Brand.Stroke)
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(state.items) { item -> SearchRow(item) { onPick(item) } }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(item: SearchItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item) {
            is SearchItem.Address -> {
                Icon(Icons.Rounded.Explore, null, tint = Brand.Acid)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(addressText(item.square), style = MaterialTheme.typography.titleMedium)
                    Text("Есть такой квадрат. Погнали!", style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
                }
            }
            SearchItem.Secret -> {
                Sailboat(Modifier.size(width = 36.dp, height = 26.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("///ветер.свобода.кайф", style = MaterialTheme.typography.titleMedium, color = Brand.Acid)
                    Text("Такого квадрата нет. Но кое-что есть", style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
                }
            }
            is SearchItem.Completion -> {
                Text("///", style = MaterialTheme.typography.titleMedium, color = Brand.Acid)
                Spacer(Modifier.width(10.dp))
                Text(item.text.removePrefix("///"), style = MaterialTheme.typography.titleMedium, color = Brand.Text, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.SubdirectoryArrowLeft, "Подставить", tint = Brand.Faint)
            }
            is SearchItem.PlaceHit -> {
                Icon(Icons.Rounded.Place, null, tint = Brand.Pink)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.place.title, style = MaterialTheme.typography.titleMedium, color = Brand.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.place.subtitle.isNotBlank()) Text(
                        item.place.subtitle, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── toast and loading ───────────────────────────────────────────────────────

@Composable
fun Toast(text: String?, modifier: Modifier) {
    var last by remember { mutableStateOf("") }
    if (text != null) last = text
    AnimatedVisibility(text != null, modifier, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
        Text(
            last, style = MaterialTheme.typography.labelLarge, color = Brand.Ink,
            modifier = Modifier.clip(Brand.PillShape).background(Brand.Acid).padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

private val LOADING = listOf(
    "Режу планету на 56 триллионов квадратиков…",
    "Учу матерные слова…",
    "Считаю, где ты, бля…",
)

@Composable
fun LoadingOverlay(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(tween(600))) {
        var i by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1400); i = (i + 1) % LOADING.size
            }
        }
        val spin = rememberInfiniteTransition(label = "spin")
        val t by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
        Box(Modifier.fillMaxSize().background(Brand.Ink).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Pin(Modifier.size(96.dp).scale(1f + 0.06f * kotlin.math.sin(t * 2 * Math.PI).toFloat()))
                Spacer(Modifier.height(28.dp))
                Text("ТЫ ГДЕ,", style = MaterialTheme.typography.displaySmall, color = Brand.Text)
                Text("БЛЯ!", style = MaterialTheme.typography.displaySmall, color = Brand.Acid)
                Spacer(Modifier.height(20.dp))
                AnimatedContent(LOADING[i], label = "load") {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
                }
            }
        }
    }
}

/** The app mark: an acid map pin with three slashes. */
@Composable
fun Pin(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pin = Path().apply {
            moveTo(w * 0.5f, h * 0.98f)
            cubicTo(w * 0.5f, h * 0.98f, w * 0.06f, h * 0.6f, w * 0.06f, h * 0.42f)
            cubicTo(w * 0.06f, h * 0.17f, w * 0.26f, h * 0.0f, w * 0.5f, h * 0.0f)
            cubicTo(w * 0.74f, h * 0.0f, w * 0.94f, h * 0.17f, w * 0.94f, h * 0.42f)
            cubicTo(w * 0.94f, h * 0.6f, w * 0.5f, h * 0.98f, w * 0.5f, h * 0.98f)
            close()
        }
        drawPath(pin, Brand.Acid)
        val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
        for (k in 0..2) {
            val x = w * (0.30f + k * 0.15f)
            drawLine(Brand.Ink, Offset(x, h * 0.58f), Offset(x + w * 0.11f, h * 0.26f), stroke.width, StrokeCap.Round)
        }
    }
}
