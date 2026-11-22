package app.pageheld

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun systemMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    enabled = ValueAnimator.areAnimatorsEnabled()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
internal fun BookCover(motion: Boolean, onOpen: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val screenDensity = androidx.compose.ui.platform.LocalDensity.current
    val topInset = WindowInsets.safeDrawing.getTop(screenDensity).toFloat()
    val bottomInset = WindowInsets.safeDrawing.getBottom(screenDensity).toFloat()
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun settle(open: Boolean, speed: Float = 0f) {
        settling?.cancel()
        if (!motion) {
            progress = 0f
            if (open) onOpen()
            return
        }
        settling =
            scope.launch {
                animate(
                    progress,
                    if (open) 1f else 0f,
                    initialVelocity = speed.coerceIn(-3f, 3f),
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 240f,
                            visibilityThreshold = .003f,
                        ),
                ) { v, _ ->
                    progress = v.coerceIn(0f, 1f)
                }
                if (open) onOpen()
            }
    }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .clickable(role = Role.Button, onClickLabel = "Open notebook") { settle(true) }
            .draggable(
                rememberDraggableState { delta ->
                    progress = (progress - delta / width).coerceIn(0f, 1f)
                },
                Orientation.Horizontal,
                onDragStarted = { settling?.cancel() },
                onDragStopped = { velocity ->
                    val speed = -velocity / width
                    val projected = progress + speed * .14f
                    settle(projected >= .36f, speed)
                },
            )
    ) {
        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    val travel = if (motion) progress else 0f
                    drawRect(ink.copy(alpha = .16f * (1f - travel)))
                }
        )
        Box(
            Modifier.fillMaxSize()
                .drawWithCache {
                    val projection = android.graphics.Matrix()
                    val coefficients = FloatArray(9)
                    val board = 10.dp.toPx()
                    val camera = size.width * 4f
                    onDrawWithContent {
                        val travel = if (motion) progress else 0f
                        if (travel <= 0f) {
                            drawContent()
                            return@onDrawWithContent
                        }
                        if (travel >= 1f) return@onDrawWithContent

                        // The front cloth and inner lining turn around the same rear hinge.
                        val cosine = 1f - travel
                        val sine = sqrt(1f - cosine * cosine)
                        val perspective = -sine / camera
                        val frontDepth = 1f + board * travel / camera
                        val rearDepth = 1f + board / camera
                        val tip = size.width * cosine
                        val outerX = (tip - board * sine) / (frontDepth + perspective * size.width)
                        val innerX = tip / (rearDepth + perspective * size.width)
                        val edgeWidth = innerX - outerX

                        if (edgeWidth > 0f) {
                            // The fore-edge extends beyond the viewport vertically as it tilts forward.
                            drawRect(
                                Brush.horizontalGradient(
                                    0f to Color(0xff869478), .18f to Color(0xffbac3a9),
                                    .34f to Color(0xffa1aa91), .83f to Color(0xff97a287),
                                    1f to Color(0xff768369),
                                    startX = outerX, endX = innerX,
                                ),
                                Offset(outerX, 0f),
                                androidx.compose.ui.geometry.Size(edgeWidth, size.height),
                            )
                            val shadow = 18.dp.toPx() * sine
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(ink.copy(alpha = .22f * sine * cosine), Color.Transparent),
                                    startX = innerX, endX = innerX + shadow,
                                ),
                                Offset(innerX, 0f),
                                androidx.compose.ui.geometry.Size(shadow, size.height),
                            )
                        }

                        // Project the live Compose artwork using the same geometry as its edge.
                        coefficients[0] = cosine
                        coefficients[2] = -board * sine
                        coefficients[3] = center.y * perspective
                        coefficients[4] = 1f
                        coefficients[5] = center.y * (frontDepth - 1f)
                        coefficients[6] = perspective
                        coefficients[8] = frontDepth
                        projection.setValues(coefficients)
                        val canvas = drawContext.canvas.nativeCanvas
                        val checkpoint = canvas.save()
                        try {
                            canvas.concat(projection)
                            canvas.clipRect(0f, 0f, size.width, size.height)
                            drawContent()
                        } finally {
                            canvas.restoreToCount(checkpoint)
                        }
                    }
                }
                .background(Color(0xffd9dfc9))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xffa8b39a),
                                Color(0xffc6cfb6),
                                Color(0xffa7b597),
                                Color.Transparent,
                            ),
                            endX = 26.dp.toPx(),
                        )
                    )
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, w, h, paperGrain)
                    val inset = 13.dp.toPx()
                    drawRoundRect(
                        Color(0xffa3af96),
                        Offset(inset + 10.dp.toPx(), topInset + inset),
                        androidx.compose.ui.geometry.Size(
                            w - 2 * inset - 10.dp.toPx(),
                            (h - topInset - bottomInset - 2 * inset).coerceAtLeast(0f),
                        ),
                        androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                        style = Stroke(.7.dp.toPx()),
                    )
                    val ribbon =
                        Path().apply {
                            moveTo(w * .78f, topInset)
                            lineTo(w * .85f, topInset)
                            lineTo(w * .85f, topInset + h * .09f)
                            lineTo(w * .815f, topInset + h * .075f)
                            lineTo(w * .78f, topInset + h * .09f)
                            close()
                        }
                    drawPath(ribbon, Color(0xff907d60))
                }
                .drawWithContent {
                    drawContent()
                    if (motion && progress > 0f) {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(ink.copy(alpha = progress * .24f), Color.Transparent)
                            )
                        )
                    }
                }
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Glyph("sprout", tint = sage, size = 44)
                Spacer(Modifier.height(14.dp))
                Text(
                    PAGEHELD_WORDMARK,
                    fontFamily = LocalDisplayFont.current,
                    fontWeight = FontWeight.Normal,
                    fontSize = uiSp(38),
                    color = ink,
                    letterSpacing = (-1).sp,
                )
            }
            Text(
                "Click or Swipe to open",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 42.dp),
                fontSize = uiSp(14),
                color = ink,
                letterSpacing = .5.sp,
            )
        }
    }
}

@Composable
internal fun TurningPage(direction: Int, motion: Boolean, content: @Composable () -> Unit) {
    val fraction = remember { Animatable(if (direction != 0 && motion) 1f else 0f) }
    LaunchedEffect(motion) {
        if (motion) fraction.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        else fraction.snapTo(0f)
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val t = fraction.value
                transformOrigin = TransformOrigin(if (direction > 0) 0f else 1f, .5f)
                rotationY = direction * 13f * t
                translationX = direction * size.width * .09f * t
                cameraDistance = 18 * density
                shadowElevation = t * 5 * density
            }
            .drawWithContent {
                drawContent()
                if (fraction.value > 0)
                    drawRect(
                        Brush.horizontalGradient(
                            if (direction > 0)
                                listOf(ink.copy(alpha = .12f * fraction.value), Color.Transparent)
                            else listOf(Color.Transparent, ink.copy(alpha = .12f * fraction.value))
                        )
                    )
            }
    ) {
        content()
    }
}
