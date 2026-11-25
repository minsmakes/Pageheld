package app.pageheld

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

internal data class DiaryPen(
    val nib: Int,
    val color: Int,
    val width: Float,
    val opacity: Float,
    val sensitivity: Float,
) {
    fun token() = listOf(nib, color, width, opacity, sensitivity).joinToString(",")

    fun applyTo(art: DiaryLayout) =
        art.copy(
            nib = nib,
            color = color,
            thickness = width,
            opacity = opacity,
            sensitivity = sensitivity,
        )

    fun line(sheet: Int, density: Float, frame: InkFrame, points: List<NibPoint>): InkLine {
        val alpha =
            opacity *
                when (nib) {
                    3 -> .40f
                    4 -> .74f
                    else -> 1f
                }
        val factor =
            when (nib) {
                0 -> .72f
                2 -> 2.2f
                3 -> 4f
                else -> 1f
            }
        return InkLine(
            sheet,
            (color and 0xffffff) or ((255 * alpha).roundToInt() shl 24),
            width * density * factor / frame.w,
            points,
            nib,
        )
    }

    fun weight(pressure: Float, tilt: Float, direction: Float): Float {
        val p = 1f + (pressure.coerceIn(.04f, 1.5f) - 1f) * sensitivity
        return when (nib) {
            0 -> .65f + .35f * p
            1 -> (.45f + .8f * abs(sin(direction - .65f))) * (.25f + .95f * p)
            2 -> .12f + 1.4f * p * p + .2f * sin(tilt)
            3 -> 1f
            else -> (.5f + .5f * p) * (1f + 1.8f * sin(tilt))
        }
    }

    companion object {
        fun parse(token: String?): DiaryPen? =
            runCatching {
                    val v = requireNotNull(token).split(",")
                    DiaryPen(
                        v[0].toInt().coerceIn(0, 4),
                        v[1].toInt(),
                        v[2].toFloat().coerceIn(1f, 12f),
                        v[3].toFloat().coerceIn(.05f, 1f),
                        v[4].toFloat().coerceIn(0f, 1f),
                    )
                }
                .getOrNull()
    }
}

internal fun DiaryLayout.pen() = DiaryPen(nib, color, thickness, opacity, sensitivity)

// Each translucent stroke is composited once. Shared joints therefore have the same tone as its
// body.
private val graphite =
    object : android.util.LruCache<Int, android.graphics.Shader>(12) {
        override fun create(key: Int): android.graphics.Shader {
            val random = java.util.Random(71L)
            val pixels =
                IntArray(24 * 24) { ((115 + random.nextInt(141)) shl 24) or (key and 0xffffff) }
            val tile =
                android.graphics.Bitmap.createBitmap(
                    pixels,
                    24,
                    24,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            return android.graphics.BitmapShader(
                tile,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT,
            )
        }
    }

internal class InkShape(private val line: InkLine, private val frame: InkFrame) {
    private val path = Path()
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line.color
            if (line.instrument == 4) shader = graphite.get(line.color and 0xffffff)
        }
    private var count = 0

    fun extend() {
        while (count < line.points.size) {
            val p = line.points[count]
            val b = frame.pixel(p)
            val rb = (line.width * frame.w * p.pressure * .5f).coerceAtLeast(.12f)
            path.addCircle(b.x, b.y, rb, Path.Direction.CW)
            if (count > 0) {
                val prev = line.points[count - 1]
                val a = frame.pixel(prev)
                val dx = b.x - a.x
                val dy = b.y - a.y
                val length = hypot(dx, dy)
                if (length > .001f) {
                    val nx = -dy / length
                    val ny = dx / length
                    val ra = (line.width * frame.w * prev.pressure * .5f).coerceAtLeast(.12f)
                    path.moveTo(a.x + nx * ra, a.y + ny * ra)
                    path.lineTo(a.x - nx * ra, a.y - ny * ra)
                    path.lineTo(b.x - nx * rb, b.y - ny * rb)
                    path.lineTo(b.x + nx * rb, b.y + ny * rb)
                    path.close()
                }
            }
            count++
        }
    }

    fun draw(canvas: Canvas) {
        if (line.instrument >= 0) {
            extend()
            canvas.drawPath(path, paint)
        } else {
            paint.strokeCap = Paint.Cap.ROUND
            var prev: androidx.compose.ui.geometry.Offset? = null
            for (p in line.points) {
                val b = frame.pixel(p)
                paint.strokeWidth = line.width * frame.w * p.pressure
                val a = prev
                if (a == null) canvas.drawCircle(b.x, b.y, paint.strokeWidth / 2, paint)
                else canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                prev = b
            }
        }
    }
}

private val names = listOf("Ballpoint", "Fountain", "Brush", "Highlight", "Pencil")
private val labels = listOf("볼펜", "만년필", "붓펜", "형광펜", "연필")
private val colors =
    listOf(0xff434736, 0xffab7955, 0xffe8977b, 0xffe7b83d, 0xff819a63, 0xffa095cb, 0xff7f9ec1)

@Composable
internal fun PenPanel(session: WritingSession) {
    var pen by remember { mutableStateOf(session.layout.pen()) }
    val favorites = session.favoritePens
    var finger by remember { mutableStateOf(session.fingerInk) }
    fun save() {
        session.update(art = pen.applyTo(session.layout), record = false)
        session.book.setting("lastPen", pen.token())
    }
    Column(Modifier.width(286.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row {
            listOf(0, 1, 4, 2, 3).forEach { n ->
                Text(
                    wording(names[n], labels[n]),
                    Modifier.weight(1f)
                        .clip(round)
                        .background(if (n == pen.nib) pale else ivory)
                        .clickable {
                            pen = pen.copy(nib = n)
                            save()
                        }
                        .semantics { contentDescription = names[n] }
                        .padding(vertical = 7.dp),
                    fontSize = uiSp(12),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(37.dp).background(Color(0xfffaf5ed), round)) {
            val frame = InkFrame(size.width, size.height, 0f)
            val pts =
                (0..80).map { i ->
                    val t = i / 80f
                    NibPoint(
                        .06f + .88f * t,
                        .52f + .21f * sin(t * 14),
                        pen.weight(.2f + .75f * sin(t * Math.PI).toFloat(), .4f, cos(t * 14)),
                    )
                }
            InkShape(pen.line(0, density, frame, pts), frame).draw(drawContext.canvas.nativeCanvas)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            colors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(28.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (c.toInt() == pen.color) 2.dp else .5.dp, edge, CircleShape)
                        .clickable {
                            pen = pen.copy(color = c.toInt())
                            save()
                        }
                        .semantics { contentDescription = "Ink color ${i+1}" }
                )
            }
        }
        PenSlider(
            wording("Width", "굵기"),
            pen.width,
            1f..12f,
            { pen = pen.copy(width = it) },
            ::save,
        )
        PenSlider(
            wording("Opacity", "불투명도"),
            pen.opacity,
            .05f..1f,
            { pen = pen.copy(opacity = it) },
            ::save,
        )
        if (pen.nib != 3)
            PenSlider(
                wording("Pressure", "필압"),
                pen.sensitivity,
                0f..1f,
                { pen = pen.copy(sensitivity = it) },
                ::save,
            )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                finger,
                {
                    finger = it
                    session.fingerInk = it
                    session.book.setting("fingerInk", it.toString())
                },
            )
            Text(wording("Draw with finger", "손가락으로 쓰기"), fontSize = uiSp(13))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val saved = pen in favorites
            TextButton(
                onClick = {
                    if (saved) favorites.remove(pen) else favorites.add(pen)
                    session.book.setting("favoritePens", favorites.joinToString("|") { it.token() })
                }
            ) {
                Text(wording(if (saved) "★ Remove" else "☆ Save", if (saved) "★ 해제" else "☆ 저장"))
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                favorites.forEachIndexed { i, f ->
                    TextButton(
                        onClick = {
                            pen = f
                            save()
                        }
                    ) {
                        Text("★ ${i+1}", color = Color(f.color))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PenSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (Float) -> Unit,
    finished: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(68.dp), fontSize = uiSp(13))
        Slider(
            value,
            change,
            Modifier.weight(1f).height(36.dp).semantics { contentDescription = label },
            onValueChangeFinished = finished,
            valueRange = range,
        )
    }
}
