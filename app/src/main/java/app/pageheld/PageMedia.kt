package app.pageheld

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import java.io.File
import kotlin.math.roundToInt

internal data class MediaPosition(val across: Float, val down: Float)

internal fun Page.mediaPositionsByFile(): Map<String, MediaPosition> =
    mediaPositions.lineSequence().mapNotNull { row ->
        val parts = row.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val across = parts[1].toFloatOrNull()?.takeIf { it in 0f..1f } ?: return@mapNotNull null
        val down = parts[2].toFloatOrNull()?.takeIf { it in 0f..100_000f } ?: return@mapNotNull null
        parts[0] to MediaPosition(across, down)
    }.toMap()

internal fun Map<String, MediaPosition>.encodeMediaPositions() =
    entries.joinToString("\n") { (file, position) -> "$file\t${position.across}\t${position.down}" }

internal fun Page.withMediaPosition(name: String, position: MediaPosition?): String =
    mediaPositionsByFile().toMutableMap().apply {
        if (position == null) remove(name) else put(name, position)
    }.encodeMediaPositions()

@Composable
internal fun PageMediaSurface(
    page: Page,
    book: Notebook,
    minimum: Dp,
    scroll: ScrollState,
    showDragHint: Boolean,
    view: (String) -> Unit,
    place: (String, MediaPosition?) -> Unit,
    writing: @Composable ColumnScope.() -> Unit,
) {
    var writingHeight by remember { mutableIntStateOf(0) }
    var hintHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val textBottom = with(density) { writingHeight.toDp() }
    val files = page.files()
    val positions = remember(page.mediaPositions) { page.mediaPositionsByFile() }
    val hintVisible = showDragHint && files.isNotEmpty() && positions.isEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tile = minOf(104.dp, maxWidth)
        val padding = minOf(16.dp, (maxWidth - tile) / 2)
        val stride = tile + 8.dp
        val columns = ((maxWidth - padding * 2 + 8.dp) / stride).toInt().coerceAtLeast(1)
        val travel = maxWidth - tile
        val parking = textBottom + if (hintVisible) {
            maxOf(42.dp, with(density) { hintHeight.toDp() } + 16.dp)
        } else 12.dp
        val unplaced = files.count { it !in positions }
        val rows = (unplaced + columns - 1) / columns
        val positionedBottom = files.mapNotNull { positions[it]?.down }.maxOrNull()?.dp ?: 0.dp
        val height = maxOf(minimum, parking + stride * rows, positionedBottom + tile + 24.dp)
        val maxY = height - tile
        Box(Modifier.fillMaxWidth().heightIn(min = height)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { writingHeight = it.height }, content = writing)
            if (hintVisible) {
                Note(
                    "Hold and drag a photo or video to place it on the page.",
                    Modifier.offset(y = textBottom + 8.dp)
                        .padding(horizontal = padding)
                        .onSizeChanged { hintHeight = it.height },
                )
            }
            var parkedIndex = 0
            files.forEach { file ->
                val saved = positions[file]
                val origin = if (saved == null) {
                    val index = parkedIndex++
                    Offset(
                        with(density) { (padding + stride * (index % columns)).toPx() },
                        with(density) { (parking + stride * (index / columns)).toPx() },
                    )
                } else with(density) { Offset((travel * saved.across).toPx(), saved.down.dp.toPx()) }
                key(file) {
                    val currentOrigin by rememberUpdatedState(origin)
                    val bounds by rememberUpdatedState(with(density) { Offset(travel.toPx(), maxY.toPx()) })
                    val update by rememberUpdatedState(place)
                    var dragging by remember { mutableStateOf<Offset?>(null) }
                    var gripY by remember { mutableFloatStateOf(0f) }
                    fun clamp(point: Offset) = Offset(
                        point.x.coerceIn(0f, bounds.x),
                        point.y.coerceIn(0f, bounds.y),
                    )
                    fun commit(point: Offset) {
                        val target = clamp(point)
                        update(file, MediaPosition(
                            if (bounds.x > 0f) target.x / bounds.x else 0f,
                            with(density) { target.y.toDp().value },
                        ))
                    }
                    fun nudge(label: String, x: Int, y: Int) = CustomAccessibilityAction(label) {
                        val step = with(density) { 32.dp.toPx() }
                        commit(currentOrigin + Offset(x * step, y * step))
                        true
                    }
                    LaunchedEffect(dragging != null, scroll, density) {
                        if (dragging == null) return@LaunchedEffect
                        val edge = with(density) { 52.dp.toPx() }
                        var lastFrame = withFrameNanos { it }
                        while (dragging != null) {
                            val frame = withFrameNanos { it }
                            val elapsed = ((frame - lastFrame) / 1e9f).coerceAtMost(.05f)
                            lastFrame = frame
                            val point = dragging ?: break
                            val viewport = scroll.viewportSize
                            if (viewport <= 0) continue
                            val y = point.y + gripY - scroll.value
                            val speed = when {
                                y < edge -> ((y - edge) / edge).coerceAtLeast(-1f)
                                y > viewport - edge -> ((y - viewport + edge) / edge).coerceAtMost(1f)
                                else -> 0f
                            }
                            if (speed != 0f) {
                                val delta = scroll.dispatchRawDelta(speed * edge * 10f * elapsed)
                                dragging = clamp(point + Offset(0f, delta))
                            }
                        }
                    }
                    Thumbnail(
                        File(mediaDir(book), file),
                        Modifier.offset {
                            val point = dragging ?: currentOrigin
                            IntOffset(point.x.roundToInt(), point.y.roundToInt())
                        }.zIndex(if (dragging == null) 0f else 1f)
                            .size(tile)
                            .pointerInput(file, density) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { touch ->
                                        gripY = touch.y
                                        dragging = currentOrigin
                                    },
                                    onDragEnd = {
                                        dragging?.takeIf { it != currentOrigin }?.let(::commit)
                                        dragging = null
                                    },
                                    onDragCancel = { dragging = null },
                                ) { change, amount ->
                                    change.consume()
                                    dragging = clamp((dragging ?: currentOrigin) + amount)
                                }
                            }
                            .clickable { view(file) }
                            .semantics {
                                customActions = listOf(
                                    nudge("Move up", 0, -1),
                                    nudge("Move down", 0, 1),
                                    nudge("Move left", -1, 0),
                                    nudge("Move right", 1, 0),
                                    CustomAccessibilityAction("Return to attachment area") {
                                        update(file, null)
                                        true
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}
