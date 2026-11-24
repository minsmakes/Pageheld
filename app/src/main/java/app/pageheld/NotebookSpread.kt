package app.pageheld

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val LocalCompactPage = staticCompositionLocalOf { false }

@Composable
internal fun NotebookSpread(
    startOpen: Boolean,
    motion: Boolean,
    familiar: Boolean,
    section: String,
    locked: Boolean,
    onOpened: () -> Unit,
    chapter: (String) -> Unit,
    bookmark: () -> Unit,
    content: @Composable (wide: Boolean, open: Boolean) -> Unit,
) {
    var opened by rememberSaveable { mutableStateOf(startOpen) }
    var progress by remember { mutableFloatStateOf(if (opened) 1f else 0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun settle(open: Boolean) {
        job?.cancel()
        job =
            scope.launch {
                val end = if (open) 1f else 0f
                if (motion)
                    animate(
                        progress,
                        end,
                        animationSpec =
                            tween(
                                (Binding.coverMillis * abs(end - progress))
                                    .toInt()
                                    .coerceAtLeast(90),
                                easing = FastOutSlowInEasing,
                            ),
                    ) { v, _ ->
                        progress = v
                    }
                progress = end
                if (open && !opened) onOpened()
                opened = open
            }
    }
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density) > 0
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Binding.desk).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        val wide =
            maxWidth >= 750.dp &&
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                maxWidth > maxHeight * 1.12f &&
                maxWidth.value / density.fontScale >= 580
        val rail = if (wide) 76.dp else Binding.rail
        val outer = if (wide) Binding.outer else 6.dp
        val vertical = if (keyboard) 2.dp else if (wide) 29.dp else 14.dp
        val h = (maxHeight - vertical * 2).coerceAtLeast(64.dp)
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val systemEdge =
            with(density) {
                maxOf(
                        WindowInsets.systemGestures.getLeft(this, layoutDirection),
                        WindowInsets.systemGestures.getRight(this, layoutDirection),
                    )
                    .toDp()
            }
        val available = (maxWidth - maxOf(outer, systemEdge) * 2 - rail).coerceAtLeast(80.dp)
        val w =
            if (wide) minOf(available, h * 2.05f).coerceAtLeast(minOf(available, 700.dp))
            else minOf(available, 620.dp)
        val page = if (wide) w / 2 else w
        val closedW = minOf(page, h * Binding.pageRatio) * .94f
        val closedH = closedW / Binding.pageRatio
        val hinge = if (wide) page else 0.dp
        val interactive = opened && progress == 1f
        val surface = rememberGraphicsLayer()
        val visual = if (motion) progress else if (opened) 1f else 0f
        Box(
            Modifier.size(w, h).graphicsLayer {
                val t = visual
                scaleX = closedW / page + (1f - closedW / page) * t
                scaleY = closedH / h + (1f - closedH / h) * t
                translationX =
                    -(if (wide) closedW.toPx() / 2 else 0f) * (1f - t) - rail.toPx() / 2 * t
            }
        ) {
            Box(
                Modifier.fillMaxSize()
                    .drawWithContent {
                        if (progress >= 1f || !wide) drawContent()
                        else {
                            val canvas = drawContext.canvas
                            canvas.save()
                            canvas.clipRect(
                                hinge.toPx(),
                                -32.dp.toPx(),
                                size.width + 36.dp.toPx(),
                                size.height + 36.dp.toPx(),
                            )
                            drawContent()
                            canvas.restore()
                        }
                    }
                    .then(if (!interactive) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                Stack(Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomEnd).offset(x = (-34).dp, y = 12.dp)) {
                    Ribbon(Modifier.size(13.dp, 24.dp))
                }
                Box(
                    Modifier.fillMaxSize()
                        .padding(start = 4.dp, top = 3.dp, end = 8.dp, bottom = 9.dp)
                        .clip(if (wide) FoldedTrim else Binding.corner)
                        .drawWithContent {
                            if (!interactive && wide && visual > 0f) {
                                surface.record { this@drawWithContent.drawContent() }
                                drawLayer(surface)
                            } else drawContent()
                            if (visual > 0f && visual < 1f) {
                                // Project the contact shadow onto the paper, within its trim.
                                val lift = sin(visual * PI).toFloat()
                                val joint = if (wide) size.width / 2 else 0f
                                val extent = if (wide) size.width / 2 else size.width
                                val tip =
                                    joint + extent * cos(visual * PI).toFloat().coerceAtLeast(0f)
                                val softness = 1f + lift * extent * .14f
                                drawRect(
                                    Brush.horizontalGradient(
                                        listOf(ink.copy(alpha = .20f * lift), Color.Transparent),
                                        startX = tip,
                                        endX = tip + softness,
                                    ),
                                    Offset(tip, 0f),
                                    Size(softness, size.height),
                                )
                            }
                        }
                        .paper("Blank")
                        .bindingShading(wide)
                ) {
                    if (progress > 0f || opened)
                        CompositionLocalProvider(LocalCompactPage provides !wide) {
                            content(wide, interactive)
                        }
                }
            }
            if (interactive && !keyboard) {
                // Both fittings sit in the running head, above the writing and scrolling areas.
                Box(Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 2.dp)) {
                    Action("book-marked", "Close book", !locked) { settle(false) }
                }
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(end = 15.dp)
                        .size(48.dp)
                        .clickable(
                            enabled = !locked,
                            role = Role.Button,
                            onClickLabel = "Bookmarked pages",
                            onClick = bookmark,
                        )
                        .semantics { contentDescription = "Bookmarked pages" },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Ribbon(Modifier.size(18.dp, 35.dp))
                }
            }
            if (!interactive) {
                val turn = visual
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset(x = hinge)
                        .size(page, h)
                        .padding(end = 5.dp, bottom = 6.dp)
                        .graphicsLayer {
                            rotationY = -180f * turn
                            transformOrigin = TransformOrigin(0f, .5f)
                            // Android's native camera projects from 72 screen units per camera unit.
                            // Keep its vanishing distance proportional to this leaf at every density.
                            cameraDistance = (page.toPx() * 4.5f / 72f).coerceAtLeast(12f)
                            shape = Binding.corner
                        }
                        .then(if (turn > .5f) Modifier.graphicsLayer { scaleX = -1f } else Modifier)
                ) {
                    BoardFace(
                        turn > .5f,
                        (closedW / page + (1 - closedW / page) * turn) /
                            (closedH / h + (1 - closedH / h) * turn),
                    )
                    if (turn > .5f && wide)
                        Box(
                            Modifier.fillMaxSize().padding(3.dp).clip(Binding.corner).drawBehind {
                                drawLayer(surface)
                            }
                        )
                }
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset(x = hinge)
                        .size(page, h)
                        .clickable(role = Role.Button, onClickLabel = "Open notebook") {
                            settle(true)
                        }
                        .semantics { contentDescription = "Open notebook" }
                        .draggable(
                            rememberDraggableState { dx ->
                                progress =
                                    (progress - dx / with(density) { page.toPx() }).coerceIn(0f, 1f)
                            },
                            Orientation.Horizontal,
                            onDragStarted = { job?.cancel() },
                            onDragStopped = { velocity ->
                                settle(
                                    progress - velocity / with(density) { page.toPx() } * .14f >
                                        .38f
                                )
                            },
                        )
                )
            }
        }
        if (interactive && !keyboard) {
            // The touch area occupies the reserved desk margin beyond the fore-edge.
            Column(
                Modifier.align(Alignment.Center)
                    .offset(x = w / 2 - 2.dp)
                    .width(rail)
                    .heightIn(max = h)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                tabs.forEachIndexed { i, (name, icon) ->
                    val active = name == section
                    Box(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !locked, role = Role.Tab) { chapter(name) }
                            .semantics {
                                selected = active
                                contentDescription = name
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier.fillMaxWidth(if (active) 1f else .94f)
                                .shadow(1.dp, RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                                .linen(Binding.tabs[i])
                                .drawBehind {
                                    if (active)
                                        drawLine(
                                            Binding.board,
                                            Offset(2.dp.toPx(), 0f),
                                            Offset(2.dp.toPx(), size.height),
                                            2.dp.toPx(),
                                        )
                                }
                                .padding(horizontal = if (wide) 9.dp else 5.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (wide)
                                Text(name, fontFamily = serif, color = ink, fontSize = uiSp(12))
                            else Glyph(icon, tint = ink, size = 18)
                        }
                    }
                }
            }
        }
        if (!familiar && !opened && progress == 0f && !keyboard) {
            Text(
                "Tap or pull the cover to open",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                color = muted,
                fontSize = uiSp(12),
            )
        }
    }
}

@Composable
internal fun Folio(
    section: String,
    number: Int,
    wide: Boolean,
    left: Boolean,
    previous: (() -> Unit)? = null,
    next: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val writing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(Modifier.fillMaxSize()) {
        if (!writing)
            Row(
                Modifier.fillMaxWidth()
                    .height(48.dp)
                    .padding(
                        start = if (left || !wide) 54.dp else 24.dp,
                        end = if (!left || !wide) 56.dp else 24.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Imprint(if (left && wide) PAGEHELD_WORDMARK else section)
                Spacer(Modifier.width(12.dp))
                HorizontalDivider(Modifier.weight(1f), thickness = .6.dp, color = edge)
            }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
        val hasTurns = previous != null || next != null
        if (!writing)
            Row(
                Modifier.fillMaxWidth()
                    .height(if (hasTurns || wide) 48.dp else 26.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(48.dp)) {
                    if (previous != null) Action("arrow-left", "Turn back", click = previous)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(number.toString(), fontFamily = serif, fontSize = uiSp(12), color = muted)
                }
                Box(Modifier.width(48.dp)) {
                    if (next != null) Action("chevron-right", "Turn forward", click = next)
                }
            }
    }
}
