package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import kotlin.math.*

internal object Binding {
    val cloth = Color(0xff78866b)
    val board = Color(0xff59664e)
    val desk = Color(0xffe5e2d9)
    val foil = Color(0xfff4ebcd)
    val ribbon = Color(0xffa78b72)
    val tabs =
        listOf(
            Color(0xffbcc5aa),
            Color(0xffc8b99f),
            Color(0xffabbdb7),
            Color(0xffc9b3a4),
            Color(0xffbdbaaa),
        )
    val hand = FontFamily(Font(R.font.kalam))
    val corner = RoundedCornerShape(5.dp)
    val margin = 28.dp
    val outer = 24.dp
    val rail = 48.dp
    val grip = 14.dp
    val elevation = 9.dp
    const val pageRatio = .76f
    const val coverMillis = 620
    const val turnMillis = 460
}

// A seamless woven tile. Threads vary along the warp and weft as well as between fibres.
private val linen by lazy {
    val random = java.util.Random(73)
    val pixels =
        IntArray(96 * 96) { i ->
            val x = i % 96
            val y = i / 96
            val light = (x % 4 == 0) xor (y % 3 == 0)
            android.graphics.Color.argb(
                random.nextInt(13) + if (light) 16 else 8,
                if (light) 245 else 39,
                if (light) 241 else 47,
                if (light) 219 else 35,
            )
        }
    android.graphics.Paint().apply {
        shader =
            android.graphics.BitmapShader(
                android.graphics.Bitmap.createBitmap(
                    pixels,
                    96,
                    96,
                    android.graphics.Bitmap.Config.ARGB_8888,
                ),
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT,
            )
    }
}

internal fun Modifier.linen(color: Color) =
    background(color).drawBehind {
        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, linen)
    }

@Composable
internal fun Imprint(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(java.util.Locale.getDefault()),
        modifier,
        color = muted,
        fontFamily = FontFamily.SansSerif,
        fontSize = uiSp(11),
        lineHeight = uiSp(17),
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.7.sp,
    )
}

// A pressed olive cutting, drawn as paper-size-independent fine ink contours.
@Composable
internal fun Botanical(modifier: Modifier, color: Color = sage) {
    Canvas(modifier) {
        val unit = min(size.width / 154f, size.height / 118f)
        withTransform({
            translate((size.width - 154 * unit) / 2, (size.height - 118 * unit) / 2)
            scale(unit, unit, Offset.Zero)
        }) {
            val path = Path()
            fun stem(
                x: Float,
                y: Float,
                a: Float,
                b: Float,
                c: Float,
                d: Float,
                ex: Float,
                ey: Float,
            ) {
                path.reset()
                path.moveTo(x, y)
                path.cubicTo(a, b, c, d, ex, ey)
                drawPath(path, color, style = Stroke(.95f))
            }
            fun leaf(x: Float, y: Float, dx: Float, dy: Float, breadth: Float) {
                val len = hypot(dx, dy)
                val nx = -dy / len * breadth
                val ny = dx / len * breadth
                path.reset()
                path.moveTo(x, y)
                path.quadraticTo(x + dx * .46f + nx, y + dy * .46f + ny, x + dx, y + dy)
                path.quadraticTo(x + dx * .6f - nx, y + dy * .6f - ny, x, y)
                drawPath(path, color.copy(alpha = .09f))
                drawPath(path, color, style = Stroke(.85f))
                drawLine(
                    color.copy(alpha = .64f),
                    Offset(x, y),
                    Offset(x + dx * .88f, y + dy * .88f),
                    .6f,
                )
            }
            stem(25f, 111f, 65f, 90f, 87f, 60f, 124f, 13f)
            stem(59f, 86f, 58f, 66f, 63f, 47f, 67f, 30f)
            stem(79f, 66f, 92f, 65f, 107f, 59f, 127f, 56f)
            leaf(49f, 94f, -23f, -18f, 5f)
            leaf(62f, 84f, 34f, -1f, 6f)
            leaf(59f, 74f, -17f, -22f, 4.7f)
            leaf(62f, 53f, 16f, -26f, 4.6f)
            leaf(65f, 43f, -7f, -25f, 4f)
            leaf(86f, 58f, -6f, -31f, 5f)
            leaf(100f, 43f, 29f, -11f, 5f)
            leaf(110f, 31f, 1f, -22f, 4f)
            leaf(117f, 23f, 18f, -16f, 3.5f)
            leaf(103f, 61f, 19f, 9f, 4f)
            leaf(117f, 58f, 24f, -10f, 4f)
        }
    }
}

@Composable
internal fun BoardFace(inner: Boolean, letteringScale: Float) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .clip(Binding.corner)
            .linen(if (inner) Color(0xffc5ccb5) else Binding.cloth)
            .drawWithCache {
                val joint =
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = .17f),
                            Color.White.copy(alpha = .1f),
                            Color.Black.copy(alpha = .14f),
                            Color.Transparent,
                        ),
                        endX = 20.dp.toPx(),
                    )
                val inset = size.minDimension * .067f
                onDrawBehind {
                    drawRect(joint)
                    if (!inner) {
                        val corner = CornerRadius(3.dp.toPx())
                        for (extra in listOf(0f, 3.dp.toPx())) {
                            val p = inset + extra
                            drawRoundRect(
                                Binding.foil.copy(alpha = if (extra == 0f) .48f else .22f),
                                Offset(p, p),
                                Size(size.width - 2 * p, size.height - 2 * p),
                                corner,
                                style = Stroke(.7.dp.toPx()),
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val width = maxWidth
        val height = maxHeight
        if (!inner)
            Column(
                Modifier.graphicsLayer { scaleY = letteringScale },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Botanical(Modifier.width(width * .42f).height(height * .18f), Binding.foil)
                Spacer(Modifier.height(12.dp))
                Text(
                    PAGEHELD_WORDMARK,
                    fontFamily = Binding.hand,
                    color = Binding.foil,
                    fontSize = (width.value * .145f).coerceIn(34f, 80f).sp,
                    style =
                        androidx.compose.ui.text.TextStyle(
                            shadow = Shadow(Color.Black.copy(alpha = .34f), Offset(0f, 1.5f), 1f)
                        ),
                )
                Glyph("sprout", tint = Binding.foil, size = 18)
            }
    }
}

@Composable
internal fun Ribbon(modifier: Modifier) {
    Canvas(
        modifier.drawWithCache {
            val path =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width * .5f, size.height - 5.dp.toPx())
                    lineTo(0f, size.height)
                    close()
                }
            onDrawBehind {
                clipPath(path) {
                    drawRect(Binding.ribbon)
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, linen)
                    for (fraction in listOf(.14f, .86f)) drawLine(
                        Binding.foil.copy(alpha = .55f),
                        Offset(size.width * fraction, 0f),
                        Offset(size.width * fraction, size.height),
                        .6.dp.toPx(),
                    )
                }
            }
        }
    ) {}
}

internal fun Modifier.bindingShading(wide: Boolean) = drawWithCache {
    val x = if (wide) size.width / 2 else 0f
    val reach = minOf(size.width * .065f, 38.dp.toPx())
    val shadow =
        Brush.horizontalGradient(
            0f to Color.Transparent,
            .27f to Color.White.copy(alpha = .14f),
            .47f to ink.copy(alpha = .10f),
            .5f to ink.copy(alpha = .31f),
            .54f to ink.copy(alpha = .14f),
            .8f to Color.White.copy(alpha = .20f),
            1f to Color.Transparent,
            startX = x - reach,
            endX = x + reach,
        )
    onDrawWithContent {
        drawContent()
        drawRect(shadow, Offset(x - reach, 0f), Size(2 * reach, size.height))
    }
}

@Composable
internal fun Stack(modifier: Modifier) {
    Box(
        modifier
            .shadow(Binding.elevation, Binding.corner)
            .clip(Binding.corner)
            .linen(Binding.board)
            .padding(start = 3.dp, end = 4.dp, top = 3.dp, bottom = 4.dp)
            .background(edge, Binding.corner)
            .drawWithCache {
                val glint = Color.White.copy(alpha = .66f)
                onDrawBehind {
                    for (i in 1..3) {
                        val inset = (i * 1.4f).dp.toPx()
                        drawLine(
                            glint,
                            Offset(1f, size.height - inset),
                            Offset(size.width - inset, size.height - inset),
                            1f,
                        )
                        drawLine(
                            glint,
                            Offset(size.width - inset, inset),
                            Offset(size.width - inset, size.height - inset),
                            1f,
                        )
                    }
                }
            }
    )
}

internal object FoldedTrim : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val x = w / 2
        val rise = with(density) { 3.dp.toPx() }
        val reach = minOf(w * .12f, with(density) { 58.dp.toPx() })
        val path =
            Path().apply {
                moveTo(0f, rise)
                quadraticTo(0f, 0f, rise, 0f)
                lineTo(x - reach, 0f)
                quadraticTo(x - reach * .36f, 0f, x, rise)
                quadraticTo(x + reach * .32f, 0f, x + reach, 0f)
                lineTo(w - rise, 0f)
                quadraticTo(w, 0f, w, rise)
                lineTo(w, h - rise)
                quadraticTo(w, h, w - rise, h)
                lineTo(x + reach, h)
                quadraticTo(x + reach * .26f, h, x, h - rise)
                quadraticTo(x - reach * .3f, h, x - reach, h)
                lineTo(rise, h)
                quadraticTo(0f, h, 0f, h - rise)
                close()
            }
        return Outline.Generic(path)
    }
}
