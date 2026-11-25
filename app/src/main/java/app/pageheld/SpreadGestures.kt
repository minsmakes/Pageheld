package app.pageheld

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.*

internal class PaperViewport {
    var scale by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    fun adjust(factor: Float, move: Offset, center: Offset, w: Float, h: Float) {
        val previous = scale
        scale = (previous * factor).coerceIn(1f, 4f)
        if (scale < 1.015f) scale = 1f
        val anchor = center - Offset(w / 2, h / 2)
        val delta = anchor + (pan - anchor) * (scale / previous) + move
        pan =
            Offset(
                delta.x.coerceIn(-w * (scale - 1) / 2, w * (scale - 1) / 2),
                delta.y.coerceIn(-h * (scale - 1) / 2, h * (scale - 1) / 2),
            )
    }
}

internal fun Modifier.diaryGestures(
    turn: BookTurn,
    viewport: PaperViewport,
    session: WritingSession,
    span: Int,
    margin: Float,
    enabled: Boolean,
    destination: (Int) -> Leaf?,
    onBegin: () -> Unit,
) =
    pointerInput(turn, viewport, span, margin, enabled, session.layout.mode, session.fingerInk) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(false, PointerEventPass.Initial)
            if (down.type != PointerType.Touch || turn.target != null || session.locked)
                return@awaitEachGesture
            val rail = down.position.x < margin || down.position.x > size.width - margin
            val mode = session.layout.mode
            val canTurn =
                rail || mode in listOf("Type", "Photo") || mode == "Pen" && !session.fingerInk
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var turning = false
            var multi = false
            var sign = 0
            val travel = (size.width - 2 * margin) / span
            try {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val p = e.changes.firstOrNull { it.id == down.id } ?: break
                    val native = session.rawInput
                    if (
                        e.cancelled(native) ||
                            e.changes.any { it.type != PointerType.Touch } ||
                            session.broadContact(density)
                    )
                        break
                    if (session.photoGesture) break
                    if (e.changes.size > 1) {
                        if (turning) {
                            turn.finish(false)
                            turning = false
                        }
                        multi = true
                        viewport.adjust(
                            e.calculateZoom(),
                            e.calculatePan(),
                            e.calculateCentroid(false).let {
                                if (it.isSpecified) it else down.position
                            },
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                        e.changes.forEach { it.consume() }
                        continue
                    }
                    if (multi) break
                    velocity.addPosition(p.uptimeMillis, p.position)
                    if (!p.pressed) {
                        if (turning) {
                            val speed = -sign * velocity.calculateVelocity().x / travel
                            turn.finish(
                                turn.progress >= .40f || turn.progress > .035f && speed > 1.1f
                            )
                            turning = false
                            p.consume()
                        }
                        break
                    }
                    val delta = p.position - down.position
                    if (viewport.scale > 1f) {
                        val pan =
                            rail ||
                                mode in listOf("Type", "Photo") ||
                                mode == "Pen" && !session.fingerInk
                        if (pan && delta.getDistance() > viewConfiguration.touchSlop) {
                            viewport.adjust(
                                1f,
                                p.positionChange(),
                                p.position,
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                            p.consume()
                        }
                        continue
                    }
                    if (!canTurn || session.finishInput != null || p.isConsumed) continue
                    if (!turning) {
                        if (
                            abs(delta.y) > viewConfiguration.touchSlop &&
                                abs(delta.y) >= abs(delta.x)
                        )
                            break
                        if (
                            abs(delta.x) < viewConfiguration.touchSlop ||
                                abs(delta.x) < abs(delta.y) * 1.4f
                        )
                            continue
                        if (
                            p.uptimeMillis - down.uptimeMillis >
                                viewConfiguration.longPressTimeoutMillis
                        )
                            break
                        sign = if (delta.x < 0) 1 else -1
                        val next = destination(sign) ?: break
                        if (!turn.begin(next, sign, down.position.y / size.height)) break
                        onBegin()
                        turning = true
                        turn.drag(-sign * delta.x / travel)
                    } else turn.drag(-sign * p.positionChange().x / travel)
                    p.consume()
                }
            } finally {
                if (turning) turn.finish(false)
            }
        }
    }
