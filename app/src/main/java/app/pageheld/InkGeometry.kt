package app.pageheld

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/** The v1.7 coordinate frame was inset by 7dp. Extend it through the trim for new edge strokes. */
internal data class InkFrame(val w: Float, val h: Float, val inset: Float) {
    fun pixel(p: NibPoint) = Offset(inset + p.x * w, inset + p.y * h)

    fun point(p: Offset, width: Float) = NibPoint((p.x - inset) / w, (p.y - inset) / h, width)
}

// Insert a lightly curved midpoint only on long sampling gaps. Every measured tip position
// is retained, and the interpolation is bounded to half a dp from the measured segment.
internal fun appendSample(
    points: MutableList<NibPoint>,
    frame: InkFrame,
    dot: NibPoint,
    density: Float,
) {
    if (points.size >= 2) {
        val a = frame.pixel(points.last())
        val edge = frame.pixel(dot) - a
        val prior = a - frame.pixel(points[points.lastIndex - 1])
        val length = edge.getDistance()
        if (length > 3 * density && prior.dot(edge) > 0f) {
            val normal = Offset(-edge.y, edge.x) / length
            val bend = (prior.dot(normal) * .15f).coerceIn(-.5f * density, .5f * density)
            if (abs(bend) > .05f)
                points.add(
                    frame.point(
                        a + edge * .5f + normal * bend,
                        (dot.pressure + points.last().pressure) * .5f,
                    )
                )
        }
    }
    points.add(dot)
}

private fun Offset.dot(p: Offset) = x * p.x + y * p.y

private fun Offset.cross(p: Offset) = x * p.y - y * p.x

private fun NibPoint.interpolate(b: NibPoint, t: Float) =
    NibPoint(x + (b.x - x) * t, y + (b.y - y) * t, pressure + (b.pressure - pressure) * t)

// Intersect a stroke segment with the capsule swept out between two eraser samples.
// The capsule is convex, so its intersection is one continuous parameter interval.
private fun capsule(
    a: Offset,
    b: Offset,
    from: Offset,
    to: Offset,
    r: Float,
): ClosedFloatingPointRange<Float>? {
    if (
        max(a.x, b.x) < min(from.x, to.x) - r ||
            min(a.x, b.x) > max(from.x, to.x) + r ||
            max(a.y, b.y) < min(from.y, to.y) - r ||
            min(a.y, b.y) > max(from.y, to.y) + r
    )
        return null
    val d = b - a
    var first = 1f
    var last = 0f
    fun include(lo: Float, hi: Float) {
        if (lo <= hi && hi >= 0 && lo <= 1) {
            first = min(first, lo.coerceAtLeast(0f))
            last = max(last, hi.coerceAtMost(1f))
        }
    }
    fun circle(c: Offset) {
        val v = a - c
        val dd = d.dot(d)
        if (dd == 0f) {
            if (v.dot(v) <= r * r) include(0f, 1f)
            return
        }
        val mid = -v.dot(d) / dd
        val dist = (v + d * mid).getDistanceSquared()
        if (dist <= r * r) {
            val span = sqrt((r * r - dist) / dd)
            include(mid - span, mid + span)
        }
    }
    circle(from)
    val axis = to - from
    val len = axis.getDistance()
    if (len > .001f) {
        circle(to)
        val u = axis / len
        val normal = Offset(-u.y, u.x)
        var lo = 0f
        var hi = 1f
        fun slab(v: Float, delta: Float, lower: Float, upper: Float) {
            if (abs(delta) < .00001f) {
                if (v !in lower..upper) hi = -1f
            } else {
                val t = (lower - v) / delta
                val t2 = (upper - v) / delta
                lo = max(lo, min(t, t2))
                hi = min(hi, max(t, t2))
            }
        }
        slab((a - from).dot(u), d.dot(u), 0f, len)
        slab((a - from).dot(normal), d.dot(normal), -r, r)
        include(lo, hi)
    }
    return if (first <= last) first..last else null
}

internal class EraserBounds(private val frame: InkFrame) {
    private val cache = java.util.IdentityHashMap<InkLine, androidx.compose.ui.geometry.Rect>()

    fun intersects(line: InkLine, sweep: androidx.compose.ui.geometry.Rect): Boolean {
        val extent =
            cache.getOrPut(line) {
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                var top = Float.POSITIVE_INFINITY
                var bottom = Float.NEGATIVE_INFINITY
                for (p in line.points) {
                    val v = frame.pixel(p)
                    val radius = p.pressure * line.width * frame.w * .5f
                    left = min(left, v.x - radius)
                    right = max(right, v.x + radius)
                    top = min(top, v.y - radius)
                    bottom = max(bottom, v.y + radius)
                }
                androidx.compose.ui.geometry.Rect(left, top, right, bottom)
            }
        return extent.overlaps(sweep)
    }
}

internal fun eraseInk(
    lines: List<InkLine>,
    sheet: Int,
    frame: InkFrame,
    from: Offset,
    to: Offset,
    radius: Float,
    partial: Boolean,
    bounds: EraserBounds? = null,
): List<InkLine> = buildList {
    val sweep =
        androidx.compose.ui.geometry.Rect(
            min(from.x, to.x) - radius,
            min(from.y, to.y) - radius,
            max(from.x, to.x) + radius,
            max(from.y, to.y) + radius,
        )
    for (line in lines) {
        val points = line.points
        if (line.sheet != sheet || points.isEmpty() || bounds?.intersects(line, sweep) == false) {
            add(line)
            continue
        }
        var hit = false
        var part = mutableListOf<NibPoint>()
        val fragments = mutableListOf<InkLine>()
        fun finish() {
            if (part.isNotEmpty()) fragments += line.copy(points = part)
            part = mutableListOf()
        }
        for (i in 0 until max(1, points.lastIndex)) {
            val a = points[i]
            val b = points.getOrElse(i + 1) { a }
            val width = line.width * frame.w * (a.pressure + b.pressure) / 4f
            val cut = capsule(frame.pixel(a), frame.pixel(b), from, to, radius + width)
            if (cut == null) {
                if (partial) {
                    if (part.isEmpty()) part.add(a)
                    part.add(b)
                }
                continue
            }
            hit = true
            if (!partial) break
            if (cut.start > 0f) {
                if (part.isEmpty()) part.add(a)
                part.add(a.interpolate(b, cut.start))
            }
            finish()
            if (cut.endInclusive < 1f) {
                part.add(a.interpolate(b, cut.endInclusive))
                part.add(b)
            }
        }
        if (!hit) add(line)
        else if (partial) {
            finish()
            addAll(fragments)
        }
    }
}

internal fun lassoHits(line: InkLine, polygon: List<Offset>, frame: InkFrame): Boolean {
    if (polygon.size < 3) return false
    fun inside(p: Offset): Boolean {
        var result = false
        var a = polygon.last()
        for (b in polygon) {
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x)
                result = !result
            a = b
        }
        return result
    }
    var previous: Offset? = null
    for (point in line.points) {
        val p = frame.pixel(point)
        if (inside(p)) return true
        val q = previous
        if (q != null) {
            var a = polygon.last()
            for (b in polygon) {
                val d = p - q
                val edge = b - a
                val denom = d.cross(edge)
                if (
                    abs(denom) > .0001f &&
                        (a - q).cross(edge) / denom in 0f..1f &&
                        (a - q).cross(d) / denom in 0f..1f
                )
                    return true
                a = b
            }
        }
        previous = p
    }
    return false
}

internal fun transformInk(
    lines: List<InkLine>,
    frame: InkFrame,
    pivot: Offset,
    move: Offset,
    scale: Float = 1f,
    radians: Float = 0f,
): List<InkLine> {
    if (lines.isEmpty()) return lines
    val c = cos(radians)
    val s = sin(radians)
    fun rotate(p: NibPoint): Offset {
        val v = frame.pixel(p) - pivot
        return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
    }
    val points = lines.flatMap { it.points }.map(::rotate)
    if (points.isEmpty()) return lines
    val xmin = points.minOf { it.x }
    val xmax = points.maxOf { it.x }
    val ymin = points.minOf { it.y }
    val ymax = points.maxOf { it.y }
    val w = frame.w + 2 * frame.inset
    val h = frame.h + 2 * frame.inset
    val k =
        minOf(scale, w / (xmax - xmin).coerceAtLeast(.01f), h / (ymax - ymin).coerceAtLeast(.01f))
    val x = (pivot.x + move.x).coerceIn(-xmin * k, max(-xmin * k, w - xmax * k))
    val y = (pivot.y + move.y).coerceIn(-ymin * k, max(-ymin * k, h - ymax * k))
    return lines.map { line ->
        line.copy(
            width = line.width * k,
            points = line.points.map { frame.point(Offset(x, y) + rotate(it) * k, it.pressure) },
        )
    }
}
