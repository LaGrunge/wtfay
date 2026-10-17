package app.tygdeblya

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ── map style and layers ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    look: MapLook,
    layers: Set<Layer>,
    onLook: (MapLook) -> Unit,
    onToggle: (Layer) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.Panel,
        contentColor = Brand.Text,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Карта", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            MapLook.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    row.forEach { l -> LookTile(l, l == look, Modifier.weight(1f)) { onLook(l) } }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Слои", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Layer.entries.forEach { l ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onToggle(l) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.title, style = MaterialTheme.typography.titleMedium)
                        Text(l.hint, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
                    }
                    Switch(
                        checked = l in layers, onCheckedChange = { onToggle(l) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Brand.Ink, checkedTrackColor = Brand.Acid,
                            uncheckedThumbColor = Brand.Muted, uncheckedTrackColor = Brand.Ink, uncheckedBorderColor = Brand.Stroke,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Карты © участники OpenStreetMap, OpenFreeMap, OpenMapTiles. " +
                    "Спутник: Sentinel-2 cloudless by EOX (CC BY-NC-SA 4.0). Поиск: Photon by Komoot.",
                style = MaterialTheme.typography.bodyMedium, color = Brand.Faint,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_HASH})",
                style = MaterialTheme.typography.bodyMedium, color = Brand.Faint,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LookTile(look: MapLook, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Brand.Acid else Brand.Stroke, RoundedCornerShape(20.dp)),
        ) {
            val (land, water, road) = look.swatch
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawRect(land)
                drawPath(Path().apply {
                    moveTo(0f, h * 0.62f)
                    cubicTo(w * 0.3f, h * 0.5f, w * 0.55f, h * 0.85f, w, h * 0.7f)
                    lineTo(w, h); lineTo(0f, h); close()
                }, water)
                drawLine(road, Offset(-w * 0.1f, h * 0.2f), Offset(w * 1.1f, h * 0.45f), w * 0.07f, StrokeCap.Round)
                drawLine(road, Offset(w * 0.62f, -h * 0.1f), Offset(w * 0.38f, h * 1.1f), w * 0.045f, StrokeCap.Round)
                // a tiny highlighted square
                drawRect(Brand.Acid, Offset(w * 0.24f, h * 0.27f), androidx.compose.ui.geometry.Size(w * 0.14f, w * 0.14f))
            }
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(Brand.Acid),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = Brand.Ink, modifier = Modifier.size(16.dp)) }
        }
        Text(look.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        Text(look.tagline, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── saved places ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSheet(
    saved: List<SavedPlace>,
    me: Location?,
    onOpen: (SavedPlace) -> Unit,
    onDelete: (SavedPlace) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Brand.Panel, contentColor = Brand.Text) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Мои места", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            if (saved.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Star, null, tint = Brand.Gold, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Пока пусто", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Найди место и жми ☆ — хату, бар, где припарковался",
                        style = MaterialTheme.typography.bodyMedium, color = Brand.Muted,
                    )
                }
            } else {
                LazyColumn {
                    items(saved, key = { it.words }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpen(p) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Star, null, tint = Brand.Gold)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "///${p.words}", style = MaterialTheme.typography.bodyMedium, color = Brand.Acid,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            me?.let {
                                Text(formatDistance(distanceTo(it, p.geo)), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                            }
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable { onDelete(p) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = Brand.Faint) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SaveDialog(suggested: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.Panel,
        title = { Text("Как назовём?", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                placeholder = { Text("Хата, бар, где бухали…", color = Brand.Faint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand.Acid, cursorColor = Brand.Acid,
                    focusedTextColor = Brand.Text, unfocusedTextColor = Brand.Text,
                ),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Запомнить", color = Brand.Acid, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Brand.Muted, style = MaterialTheme.typography.labelLarge) } },
    )
}
