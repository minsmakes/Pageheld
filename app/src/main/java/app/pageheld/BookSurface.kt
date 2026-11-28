package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.io.File

@Composable
internal fun BookSurface(
    book: Notebook,
    title: String,
    design: CoverDesign,
    modifier: Modifier,
    selected: String? = null,
    select: ((String) -> Unit)? = null,
    change: ((CoverLayer) -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier.shadow(4.dp, Binding.corner).clip(Binding.corner).background(Color(design.color))
    ) {
        val w = maxWidth
        val h = maxHeight
        ArtImage(design.material, Modifier.fillMaxSize(), Color(design.color))
        Canvas(Modifier.fillMaxSize()) {
            val spine = size.width * (design.thickness + .045f)
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(.33f),
                        Color.White.copy(.22f),
                        Color.Transparent,
                        Color.Black.copy(.32f),
                        Color.White.copy(.16f),
                        Color.Transparent,
                    ),
                    endX = spine,
                )
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(.12f), Color.Transparent, Color.Black.copy(.17f)),
                    startY = size.height * .95f,
                )
            )
            if (design.border != "None") {
                val inset = size.minDimension * .041f
                val origin = Offset(spine + inset, inset)
                val bounds = Size(size.width - origin.x - inset, size.height - 2 * inset)
                val foil = Color(0xffd6bc80)
                drawRoundRect(
                    foil,
                    origin,
                    bounds,
                    CornerRadius(inset * .48f),
                    style = Stroke(size.minDimension * .004f),
                )
                if (design.border == "Classic" || design.border == "Botanical") {
                    val gap = size.minDimension * .009f
                    drawRoundRect(
                        foil.copy(.8f),
                        origin + Offset(gap, gap),
                        Size(bounds.width - gap * 2, bounds.height - gap * 2),
                        CornerRadius(inset * .65f),
                        style = Stroke(size.minDimension * .002f),
                    )
                }
            }
        }
        if (design.border == "Botanical") {
            ArtImage("leaf", Modifier.align(Alignment.TopEnd).size(w * .24f, h * .25f).rotate(90f))
            ArtImage("leaf", Modifier.align(Alignment.BottomStart).size(w * .24f, h * .25f))
        }
        if (design.ribbon != 0)
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = w * .09f)
                    .width(w * .034f)
                    .fillMaxHeight()
                    .shadow(1.dp)
                    .linen(Color(design.ribbon))
            )
        design.layers.forEach { layer ->
            key(layer.id) {
                ArtworkLayer(book, title, layer, w, h, selected == layer.id, select, change)
            }
        }
    }
}

@Composable
internal fun ArtworkLayer(
    book: Notebook,
    title: String,
    layer: CoverLayer,
    w: Dp,
    h: Dp,
    selected: Boolean,
    select: ((String) -> Unit)? = null,
    change: ((CoverLayer) -> Unit)? = null,
) {
    val width = w * layer.width
    val height = width / layer.ratio
    val density = LocalDensity.current
    val latest by rememberUpdatedState(layer)
    val update by rememberUpdatedState(change)
    val interactive =
        if (select != null && change != null)
            Modifier.clickable { select(layer.id) }
                .pointerInput(layer.id, w, h) {
                    detectTransformGestures { _, pan, zoom, turn ->
                        select(layer.id)
                        val old = latest
                        update?.invoke(
                            old.copy(
                                x = (old.x + pan.x / w.toPx()).coerceIn(0f, 1f),
                                y = (old.y + pan.y / h.toPx()).coerceIn(0f, 1f),
                                width = (old.width * zoom).coerceIn(.04f, 2f),
                                angle = (old.angle + turn) % 360f,
                            )
                        )
                    }
                }
        else Modifier
    Box(
        Modifier.offset(w * layer.x - width / 2, h * layer.y - height / 2)
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(width, height)
            .rotate(layer.angle)
            .alpha(layer.opacity)
            .then(interactive)
            .then(if (selected) Modifier.border(1.dp, sage, Binding.corner) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (layer.kind) {
            "title",
            "text" -> {
                val size = with(density) { (height * .43f).toSp() }
                Text(
                    if (layer.kind == "title") title else layer.asset,
                    Modifier.fillMaxWidth(),
                    color = Color(layer.color),
                    textAlign =
                        when (layer.align) {
                            "Left" -> TextAlign.Left
                            "Right" -> TextAlign.Right
                            else -> TextAlign.Center
                        },
                    fontFamily = notebookFont(book.context, layer.font),
                    fontSize = size,
                    lineHeight = size * 1.06f,
                    maxLines = 2,
                )
            }
            "photo" -> {
                val file =
                    remember(layer.asset) {
                        File(book.context.filesDir, "attachments/" + layer.asset)
                    }
                val bitmap by
                    produceState<android.graphics.Bitmap?>(null, file) {
                        value = loadPrint(file, 960)
                    }
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = if(layer.crop) ContentScale.Crop else ContentScale.Fit,
                    )
                }
            }
            else -> ArtImage(layer.asset, Modifier.fillMaxSize())
        }
    }
}
