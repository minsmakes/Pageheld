package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import kotlin.math.*

val ink = Color(0xff343a31)
val muted = Color(0xff63695e)
val sage = Color(0xff59664e)
val pale = Color(0xffeceddf)
val ivory = Color(0xfffaf6eb)
val edge = Color(0xffddd4c1)
val swatches =
    listOf(ivory, Color(0xfff5e9d6), Color(0xfff4e3de), Color(0xffe4ebef), Color(0xffe5eadc))
val serif = FontFamily(Font(R.font.caslon_regular,FontWeight.Normal), Font(R.font.caslon_italic,FontWeight.Normal,FontStyle.Italic))
internal const val PAGEHELD_WORDMARK = "Pageheld."
val round = RoundedCornerShape(9.dp)
val icons =
    mapOf(
        "house" to R.drawable.i_house,
        "book-open" to R.drawable.i_book_open,
        "map-pin" to R.drawable.i_map_pin,
        "layers" to R.drawable.i_layers,
        "user-round" to R.drawable.i_user_round,
        "menu" to R.drawable.i_menu,
        "bookmark" to R.drawable.i_bookmark,
        "bell" to R.drawable.i_bell,
        "pencil" to R.drawable.i_pencil,
        "chevron-right" to R.drawable.i_chevron_right,
        "plus" to R.drawable.i_plus,
        "search" to R.drawable.i_search,
        "calendar-days" to R.drawable.i_calendar_days,
        "arrow-left" to R.drawable.i_arrow_left,
        "undo-2" to R.drawable.i_undo_2,
        "redo-2" to R.drawable.i_redo_2,
        "ellipsis-vertical" to R.drawable.i_ellipsis_vertical,
        "image" to R.drawable.i_image,
        "import-media" to R.drawable.i_import_media,
        "video" to R.drawable.i_video,
        "smile" to R.drawable.i_smile,
        "notebook" to R.drawable.i_notebook,
        "trash-2" to R.drawable.i_trash_2,
        "sliders-horizontal" to R.drawable.i_sliders_horizontal,
        "locate-fixed" to R.drawable.i_locate_fixed,
        "x" to R.drawable.i_x,
        "leaf" to R.drawable.i_leaf,
        "check" to R.drawable.i_check,
        "heart" to R.drawable.i_heart,
        "mail" to R.drawable.i_mail,
        "feather" to R.drawable.i_feather,
        "dumbbell" to R.drawable.i_dumbbell,
        "sun" to R.drawable.i_sun,
        "cloud" to R.drawable.i_cloud,
        "mountain" to R.drawable.i_mountain,
        "sprout" to R.drawable.i_sprout,
        "list" to R.drawable.i_list,
        "arrow-up" to R.drawable.i_arrow_up,
        "arrow-down" to R.drawable.i_arrow_down,
        "archive" to R.drawable.i_archive,
        "lock" to R.drawable.i_lock,
        "palette" to R.drawable.i_palette,
        "frown" to R.drawable.i_frown,
        "meh" to R.drawable.i_meh,
        "laugh" to R.drawable.i_laugh,
        "book-marked" to R.drawable.i_book_marked,
        "type" to R.drawable.i_type,
        "play" to R.drawable.i_play,
    )

@Composable
fun Glyph(name: String, label: String? = null, tint: Color = ink, size: Int = 22) =
    Icon(
        painterResource(icons.getValue(name)),
        label,
        Modifier.size(size.dp),
        tint = if (name == "import-media") Color.Unspecified else tint,
    )

@Composable
fun Action(name: String, label: String, enabled: Boolean = true, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled) {
        Box(Modifier.alpha(if (name == "import-media" && !enabled) .35f else 1f)) {
            Glyph(name, label, if (enabled) ink else muted.copy(alpha = .35f),
                size = if (name == "import-media") 27 else 22)
        }
    }
}

@Composable
fun Title(text: String, size: Int = 20, italic: Boolean = false) {
    Text(
        text,
        modifier = if(size>=22) Modifier.semantics { heading() } else Modifier,
        color = ink,
        fontFamily = serif,
        fontSize = uiSp(size),
        lineHeight = uiSp(size + 8),
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    )
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = muted, fontSize = uiSp(13), lineHeight = uiSp(19), letterSpacing = .1.sp)

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ivory)
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun Tag(text: String, selected: Boolean = false, icon: String? = null, click: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp)
            .clickable(onClick = click)
            .padding(vertical = 5.dp)
            .clip(CircleShape)
            .background(if (selected) sage else ivory)
            .border(.7.dp, if (selected) sage else edge, CircleShape)
            .padding(horizontal = 15.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Glyph(icon, size = 16)
                Spacer(Modifier.width(5.dp))
            }
            Text(text, color = if (selected) Color.White else ink, fontSize = uiSp(13))
        }
    }
}

@Composable
fun Header(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).background(ivory).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { Title(title, if (title == PAGEHELD_WORDMARK) 26 else 23) }
        actions()
    }
    HorizontalDivider(thickness = .6.dp, color = edge)
}

// A tiny repeating grain is shared by all paper surfaces and stays fixed as the page scrolls.
internal val paperGrain by lazy {
    val random = java.util.Random(412)
    val pixels = IntArray(64 * 64) { android.graphics.Color.argb(random.nextInt(9), 126, 113, 86) }
    val tile =
        android.graphics.Bitmap.createBitmap(
            pixels,
            64,
            64,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
    android.graphics.Paint().apply {
        shader =
            android.graphics.BitmapShader(
                tile,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT,
            )
    }
}

fun Modifier.paper(
    style: String = "Ruled",
    color: Color = ivory,
    interval: Dp = 26.dp,
    margin: Dp = 0.dp,
    phase: Float = 0f,
    ruleFrom: Float = 0f,
): Modifier = drawBehind {
    drawRect(
        Brush.horizontalGradient(
            listOf(
                lerp(color, Color(0xffe9d7bd), .09f),
                color,
                lerp(color, Color.White, .22f),
                color,
            )
        )
    )
    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paperGrain)
    val step = interval.toPx().coerceAtLeast(4f)
    val rule = Color(0xffaca99f).copy(alpha = if (margin.value > 0) .52f else .32f)
    var y = ((phase % step) + step) % step
    if (y < ruleFrom) y += ceil((ruleFrom - y) / step) * step
    while (y < size.height && style != "Blank" && style != "Scrapbook") {
        if (style == "Dot Grid") {
            var x = step / 2
            while (x < size.width) {
                drawCircle(rule, 1.dp.toPx(), Offset(x, y))
                x += step
            }
        } else drawLine(rule, Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
        y += step
    }
    if (style == "Grid") {
        var x = 0f
        while (x < size.width) {
            drawLine(rule, Offset(x, 0f), Offset(x, size.height), .5.dp.toPx())
            x += step
        }
    }
    if (style == "Scrapbook") {
        var y0 = 3f
        while (y0 < size.height) {
            var x = (y0 % 11) * 2
            while (x < size.width) {
                drawCircle(rule.copy(alpha = .15f), .6.dp.toPx(), Offset(x, y0))
                x += 9.dp.toPx()
            }
            y0 += 8.dp.toPx()
        }
    }
    if (style == "Letter") {
        val x = (margin + 4.dp).toPx()
        drawLine(Color(0xffdfbdb1), Offset(x, 0f), Offset(x, size.height), .55.dp.toPx())
    }
    if (margin.value > 0 && style != "Blank")
        drawLine(
            Color(0xffdca69a),
            Offset(margin.toPx(), 0f),
            Offset(margin.toPx(), size.height),
            .8.dp.toPx(),
        )
}

@Composable
fun Cover(index: Int, modifier: Modifier) {
    val palette = listOf(Color(0xffd5d8bd), Color(0xffe7ccbb), Color(0xffc6d9e1), Color(0xffdbcedc))
    Canvas(modifier.clip(RoundedCornerShape(6.dp))) {
        val w = size.width
        val h = size.height
        val n = Math.floorMod(index, 4)
        drawRect(
            Brush.linearGradient(
                listOf(palette[n], lerp(palette[n], Color.White, .35f), palette[n])
            )
        )
        if (n == 0 || n == 3) {
            val stem = if (n == 0) Color(0xff6d805d) else Color(0xff87778c)
            val path =
                Path().apply {
                    moveTo(.32f * w, h)
                    quadraticTo(.45f * w, .59f * h, .7f * w, .28f * h)
                }
            drawPath(
                path,
                stem,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
            )
            for (i in 0..6) {
                val x = w * (.35f + i * .05f)
                val y = h * (.91f - i * .09f)
                val side = if (i % 2 == 0) 1 else -1
                val leaf =
                    Path().apply {
                        moveTo(x, y)
                        quadraticTo(
                            x + w * .13f * side,
                            y - h * .03f,
                            x + w * .18f * side,
                            y - h * .16f,
                        )
                        quadraticTo(x + w * .03f * side, y - h * .13f, x, y)
                    }
                drawPath(leaf, stem.copy(alpha = .8f))
            }
        } else {
            drawCircle(Color(0xffffedcf), w * .13f, Offset(w * .66f, h * .5f))
            for (i in 0..2) {
                val p =
                    Path().apply {
                        moveTo(0f, h)
                        lineTo(0f, h * (.62f + i * .1f))
                        for (j in 1..7) lineTo(
                            w * j / 7,
                            h * (.61f + i * .09f + .075f * sin(j * 1.25f + i)),
                        )
                        lineTo(w, h)
                        close()
                    }
                drawPath(
                    p,
                    lerp(
                        palette[n],
                        if (n == 1) Color(0xff816865) else Color(0xff658797),
                        .24f + i * .22f,
                    ),
                )
            }
        }
        if (n == 0) {
            val ribbon =
                Path().apply {
                    moveTo(w * .28f, 0f)
                    lineTo(w * .47f, 0f)
                    lineTo(w * .47f, h * .26f)
                    lineTo(w * .38f, h * .2f)
                    lineTo(w * .28f, h * .26f)
                    close()
                }
            drawPath(ribbon, Color(0xff96846d))
        }
        drawRect(
            Brush.horizontalGradient(
                listOf(
                    Color.Black.copy(alpha = .18f),
                    Color.Transparent,
                    Color.Black.copy(alpha = .13f),
                    Color.Transparent,
                ),
                endX = w * .13f,
            )
        )
    }
}

@Composable
fun Empty(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)) {
        Glyph("feather", tint = sage, size = 20)
        Spacer(Modifier.height(7.dp))
        Title(title, 19)
        Spacer(Modifier.height(5.dp))
        Note(detail)
    }
}
