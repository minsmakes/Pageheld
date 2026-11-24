package app.pageheld

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class Leaf(
    val section: String = "Today",
    val entry: String = "",
    val editing: Boolean = false,
    val chapter: String = "",
    val bookmarks: Boolean = false,
    val created: Long = 0,
    val attachment: String = "",
    val daily: Boolean = false,
) {
    companion object {
        val saver =
            listSaver<Leaf, Any>(
                save = {
                    listOf(
                        it.section,
                        it.entry,
                        it.editing,
                        it.chapter,
                        it.bookmarks,
                        it.created,
                        it.attachment,
                        it.daily,
                    )
                },
                restore = {
                    Leaf(
                        it[0] as String,
                        it[1] as String,
                        it[2] as Boolean,
                        it[3] as String,
                        it[4] as Boolean,
                        it[5] as Long,
                        it[6] as String,
                        it[7] as Boolean,
                    )
                },
            )
    }
}

@Stable
internal class BookTurn(private val scope: CoroutineScope, private val commit: (Leaf) -> Unit) {
    var target by mutableStateOf<Leaf?>(null)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var direction by mutableIntStateOf(1)
        private set

    var grip by mutableFloatStateOf(.7f)
        private set

    private var settling = false
    var motion = true

    fun begin(next: Leaf, sign: Int, height: Float = .7f): Boolean {
        if (target != null) return false
        grip = height.coerceIn(0f, 1f)
        direction = sign
        progress = 0f
        target = next
        return true
    }

    fun drag(delta: Float) {
        if (!settling) progress = (progress + delta).coerceIn(0f, 1f)
    }

    fun finish(complete: Boolean) {
        val next = target ?: return
        if (settling) return
        settling = true
        scope.launch {
            try {
                val end = if (complete) 1f else 0f
                if (motion)
                    animate(
                        progress,
                        end,
                        animationSpec =
                            tween(
                                (Binding.turnMillis * abs(end - progress))
                                    .toInt()
                                    .coerceAtLeast(80),
                                easing = FastOutSlowInEasing,
                            ),
                    ) { value, _ ->
                        progress = value
                    }
                if (complete) commit(next)
            } finally {
                target = null
                progress = 0f
                settling = false
            }
        }
    }

    fun jump(next: Leaf, sign: Int) {
        if (begin(next, sign)) finish(true)
    }
}

// Observe only finger strokes that begin in the fore-edge margin. A child that consumes
// down/movement (text handle, media drag, map) retains the entire gesture.
internal fun Modifier.turnEdges(turn: BookTurn, wide: Boolean, adjacent: (Int) -> Leaf?) =
    pointerInput(turn, wide, adjacent) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Touch || down.isConsumed || turn.target != null)
                return@awaitEachGesture
            val edge = 22.dp.toPx()
            val sign =
                when {
                    down.position.x <= edge -> -1
                    down.position.x >= size.width - edge -> 1
                    else -> return@awaitEachGesture
                }
            val next = adjacent(sign) ?: return@awaitEachGesture
            val travel = size.width * if (wide) .50f else .86f
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var started = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.size != 1 || change.isConsumed) break
                    velocity.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (started) {
                            val speed = -sign * velocity.calculateVelocity().x / travel
                            turn.finish(turn.progress + speed.coerceIn(-3f, 3f) * .14f >= .42f)
                            started = false
                        }
                        break
                    }
                    val total = change.position - down.position
                    if (!started) {
                        if (
                            abs(total.y) > viewConfiguration.touchSlop &&
                                abs(total.y) >= abs(total.x)
                        )
                            break
                        if (-sign * total.x < viewConfiguration.touchSlop) continue
                        if (!turn.begin(next, sign, down.position.y / size.height)) break
                        started = true
                        turn.drag(-sign * total.x / travel)
                    } else turn.drag(-sign * change.positionChange().x / travel)
                    change.consume()
                }
            } finally {
                if (started) turn.finish(false)
            }
        }
    }

@Composable
internal fun TurningLeaves(
    current: Leaf,
    turn: BookTurn,
    wide: Boolean,
    gestures: Boolean,
    adjacent: (Int) -> Leaf?,
    content: @Composable (Leaf, Boolean) -> Unit,
) {
    val front = rememberGraphicsLayer()
    val back = rememberGraphicsLayer()
    SideEffect {
        val strategy =
            if (turn.target == null) androidx.compose.ui.graphics.layer.CompositingStrategy.Auto
            else androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        front.compositingStrategy = strategy
        back.compositingStrategy = strategy
    }
    val lookup by rememberUpdatedState(adjacent)
    val stableLookup = remember { { step: Int -> lookup(step) } }
    val fold = remember { PaperFold() }
    Box(
        Modifier.fillMaxSize()
            .clipToBounds()
            .then(if (gestures) Modifier.turnEdges(turn, wide, stableLookup) else Modifier)
            .drawWithContent {
                drawContent()
                if (turn.target != null) {
                    if (turn.motion)
                        with(fold) {
                            render(front, back, turn.progress, turn.direction, wide, turn.grip)
                        }
                    else drawLayer(front)
                }
            }
    ) {
        val next = turn.target
        for (leaf in listOfNotNull(current, next).distinct()) key(leaf) {
            val active = leaf == current
            val layer = if (active) front else back
            Box(
                Modifier.fillMaxSize()
                    .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
                    .drawWithCache {
                        var recorded = false
                        onDrawWithContent {
                            if (next == null) drawContent()
                            else if (!recorded) {
                                layer.record { this@onDrawWithContent.drawContent() }
                                recorded = true
                            }
                        }
                    }
                    .paper("Blank")
                    .padding(horizontal = Binding.grip)
            ) {
                content(leaf, active)
            }
        }
        if (next != null)
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            )
    }
}

// Integrate an arc of changing tangent angle along the sheet, then project it in
// perspective. Each strip samples a recorded Compose display list, including live text.
// Neither surface is read back to a bitmap. Coordinates on the verso run in reverse.
private class PaperFold {
    private val transform = android.graphics.Matrix()
    private val from = FloatArray(8)
    private val to = FloatArray(8)

    private val triangle = android.graphics.Path()
    private val columns = 16
    private val rows = 2
    private val xs = FloatArray((columns + 1) * (rows + 1))
    private val ys = FloatArray(xs.size)

    fun DrawScope.render(
        front: GraphicsLayer,
        back: GraphicsLayer,
        p: Float,
        sign: Int,
        wide: Boolean,
        grip: Float,
    ) {
        val w = size.width
        val h = size.height
        val sheet = if (wide) w / 2 else w
        val hinge = if (wide) w / 2 else if (sign > 0) 0f else w
        if (wide)
            clipRect(left = if (sign > 0) 0f else hinge, right = if (sign > 0) hinge else w) {
                drawLayer(front)
            }
        clipRect(left = if (sign > 0) hinge else 0f, right = if (sign > 0) w else hinge) {
            drawLayer(back)
        }
        val lift = sin(PI.toFloat() * p)
        val step = sheet / columns
        val camera = sheet * 5
        val shadowX = (hinge + sign * sheet * cos(p * PI).toFloat()).coerceIn(0f, w)
        val reach = sheet * .18f * lift + 1f
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, ink.copy(alpha = .23f * lift), Color.Transparent),
                startX = shadowX - reach,
                endX = shadowX + reach,
            ),
            Offset(shadowX - reach, 0f),
            Size(2 * reach, h),
        )
        // The grasped corner leads the opposite corner. All mesh nodes approach a flat
        // recto/verso at the endpoints regardless of where the finger touched the edge.
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            val start = row * (columns + 1)
            var x = 0f
            var z = 0f
            xs[start] = hinge
            ys[start] = h * v
            for (col in 0 until columns) {
                val u = (col + .5f) / columns
                val twist = (grip - .5f) * (v - .5f) * 2.3f * u
                val angle = p * PI.toFloat() + lift * (.7f - 1.65f * u + twist)
                x += step * cos(angle)
                z += step * sin(angle)
                val scale = camera / (camera - z)
                val node = start + col + 1
                xs[node] = hinge + sign * x * scale
                ys[node] =
                    h * (.5f + (v - .5f) * scale) + sheet * .045f * lift * u * u * (.5f - grip)
            }
        }
        for (col in 0 until columns) for (row in 0 until rows) {
            val tl = row * (columns + 1) + col
            val tr = tl + 1
            val bl = tl + columns + 1
            val br = bl + 1
            val slope = (xs[tr] - xs[tl] + xs[br] - xs[bl]) * sign / (2 * step)
            val recto = slope >= 0f
            val side = if (recto) sign else -sign
            val origin = if (!wide && !recto) (if (sign > 0) w else 0f) else hinge
            val a = origin + side * step * col
            val b = origin + side * step * (col + 1)
            val top = h * row / rows
            val bottom = h * (row + 1) / rows
            from[0] = a
            from[1] = top
            from[2] = b
            from[3] = top
            from[4] = b
            from[5] = bottom
            from[6] = a
            from[7] = bottom
            to[0] = xs[tl]
            to[1] = ys[tl]
            to[2] = xs[tr]
            to[3] = ys[tr]
            to[4] = xs[br]
            to[5] = ys[br]
            to[6] = xs[bl]
            to[7] = ys[bl]
            if (abs(slope) < .004f) continue
            val layer = if (recto) front else if (wide) back else null
            val shade = (1f - abs(slope).coerceAtMost(1f)) * .16f
            val tint =
                if (recto) ink.copy(alpha = shade)
                else Binding.ribbon.copy(alpha = shade + .04f * lift)
            facet(layer, tint)
            // Affine triangles interpolate the shared diagonal identically on both
            // sides. Independent perspective quads introduce steps in text baselines.
            from[2] = from[4]
            from[3] = from[5]
            from[4] = from[6]
            from[5] = from[7]
            to[2] = to[4]
            to[3] = to[5]
            to[4] = to[6]
            to[5] = to[7]
            facet(layer, tint)
        }
    }

    private fun DrawScope.facet(layer: GraphicsLayer?, tint: Color) {
        if (!transform.setPolyToPoly(from, 0, to, 0, 3)) return
        triangle.rewind()
        val cx = (to[0] + to[2] + to[4]) / 3
        val cy = (to[1] + to[3] + to[5]) / 3
        for (i in 0..2) {
            val x = to[2 * i]
            val y = to[2 * i + 1]
            val dx = x - cx
            val dy = y - cy
            val px = x + dx.sign * .5f
            val py = y + dy.sign * .5f
            if (i == 0) triangle.moveTo(px, py) else triangle.lineTo(px, py)
        }
        triangle.close()
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.save()
        try {
            canvas.clipPath(triangle)
            canvas.concat(transform)
            if (layer == null) drawRect(ivory) else drawLayer(layer)
            drawRect(tint)
        } finally {
            canvas.restoreToCount(save)
        }
    }
}
