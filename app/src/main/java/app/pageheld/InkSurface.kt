package app.pageheld

import android.view.MotionEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import java.util.IdentityHashMap
import kotlin.math.*

internal fun PointerType.isPen() = this == PointerType.Stylus || this == PointerType.Eraser

internal fun PointerEvent.cancelled(
    native: MotionEvent?,
    owner: PointerInputChange? = null,
): Boolean {
    if (native?.actionMasked == MotionEvent.ACTION_CANCEL) return true
    val up =
        native?.actionMasked == MotionEvent.ACTION_UP ||
            native?.actionMasked == MotionEvent.ACTION_POINTER_UP
    if (up && native != null && native.flags and MotionEvent.FLAG_CANCELED != 0) {
        val tool = native.getToolType(native.actionIndex)
        val pen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
        return owner == null || owner.type.isPen() == pen
    }
    return !up && type == PointerEventType.Release && changes.all { it.isConsumed }
}

private fun bounds(lines: List<InkLine>, frame: InkFrame): Rect? {
    var box: Rect? = null
    for (line in lines) for (p in line.points) {
        val v = frame.pixel(p)
        val b = box
        box =
            if (b == null) Rect(v, v)
            else Rect(min(b.left, v.x), min(b.top, v.y), max(b.right, v.x), max(b.bottom, v.y))
    }
    return box
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun InkSurface(sheet: Int, session: WritingSession, interactive: Boolean, zoom: Float) {
    val art = session.layout
    val enabled = interactive && art.mode in listOf("Pen", "Eraser", "Lasso")
    val density = LocalDensity.current.density
    val inset = 7f * density
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    val frame =
        remember(dimensions, inset) {
            InkFrame(
                (dimensions.width - 2 * inset).coerceAtLeast(1f),
                (dimensions.height - 2 * inset).coerceAtLeast(1f),
                inset,
            )
        }
    var preview by remember { mutableStateOf<List<InkLine>?>(null) }
    var activeShape by remember { mutableStateOf<InkShape?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var eraser by remember { mutableStateOf<Offset?>(null) }
    val trace = remember { mutableListOf<Offset>() }
    var chosen by remember(sheet, art.mode) { mutableStateOf<List<InkLine>>(emptyList()) }
    val selection = chosen.filter { item -> art.lines.any { it === item } }
    val selectedBounds = remember(selection, frame) { bounds(selection, frame) }
    val cache = remember(frame) { IdentityHashMap<InkLine, InkShape>() }
    val radius = art.eraserSize * density / (2 * zoom)
    val updatedZoom by rememberUpdatedState(zoom)
    fun changeSelection(replacement: List<InkLine>, duplicate: Boolean = false) {
        val lines = session.layout.lines
        val after =
            if (duplicate) lines + replacement
            else
                buildList {
                    var n = 0
                    for (line in lines) {
                        if (selection.none { it === line }) add(line)
                        else replacement.getOrNull(n++)?.let(::add)
                    }
                }
        session.update(art = session.layout.copy(sheet = sheet, lines = after))
        chosen = replacement
    }
    Box(Modifier.fillMaxSize().onSizeChanged { dimensions = it }) {
        // Hardware-cache the committed layer; pen movement invalidates only the live overlay.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val keep = java.util.Collections.newSetFromMap(IdentityHashMap<InkLine, Boolean>())
            for (line in preview ?: art.lines) if (line.sheet == sheet) {
                keep.add(line)
                cache.getOrPut(line) { InkShape(line, frame) }.draw(drawContext.canvas.nativeCanvas)
            }
            cache.keys.retainAll(keep)
        }
        Canvas(
            Modifier.fillMaxSize()
                .then(
                    if (!enabled) Modifier
                    else
                        Modifier.pointerInput(sheet, art.mode, frame, session.fingerInk) {
                            awaitEachGesture {
                                var down = awaitFirstDown(requireUnconsumed = false)
                                var event = currentEvent
                                val mode = session.layout.mode
                                fun allowed(p: PointerInputChange) =
                                    p.type.isPen() ||
                                        (p.type == PointerType.Touch &&
                                            (mode != "Pen" || session.fingerInk) &&
                                            !session.broadContact(density))
                                while (!allowed(down)) {
                                    if (!event.changes.any { it.pressed }) return@awaitEachGesture
                                    event = awaitPointerEvent()
                                    down =
                                        event.changes.firstOrNull { it.pressed && it.type.isPen() }
                                            ?: down
                                }
                                if (down.type.isPen()) session.fingerOwner?.invoke()
                                if (
                                    session.locked || down.isConsumed || session.finishInput != null
                                )
                                    return@awaitEachGesture
                                val settings = session.layout
                                val pen = settings.pen()
                                val boundsCache = EraserBounds(frame)
                                val original = settings.lines
                                var working = original
                                var points = mutableListOf<NibPoint>()
                                var stroke: InkLine? = null
                                var last: Offset? = null
                                var previousErase = false
                                var pointer = down.id
                                var stylus = down.type.isPen()
                                val selectionNow = chosen.filter { l -> original.any { it === l } }
                                val moving =
                                    mode == "Lasso" &&
                                        bounds(selectionNow, frame)
                                            ?.inflate(10.dp.toPx())
                                            ?.contains(down.position) == true
                                val origin = down.position
                                var moved = selectionNow
                                var finished = false
                                fun closeStroke() {
                                    stroke?.let {
                                        working = working + it.copy(points = points.toList())
                                    }
                                    activeShape = null
                                    stroke = null
                                    points = mutableListOf()
                                }
                                fun finish(commit: Boolean) {
                                    if (finished) return
                                    finished = true
                                    session.finishInput = null
                                    session.fingerOwner = null
                                    if (commit) {
                                        closeStroke()
                                        if (mode == "Lasso") {
                                            if (moving) chosen = moved
                                            else
                                                chosen =
                                                    original.filter {
                                                        it.sheet == sheet &&
                                                            lassoHits(it, trace, frame)
                                                    }
                                        }
                                        if (working !== original || sheet != settings.sheet)
                                            session.update(
                                                art =
                                                    session.layout.copy(
                                                        sheet = sheet,
                                                        lines = working,
                                                    ),
                                                record = working != original,
                                            )
                                    }
                                    trace.clear()
                                    preview = null
                                    activeShape = null
                                    eraser = null
                                    tick++
                                }
                                session.finishInput = { finish(true) }
                                if (!stylus) session.fingerOwner = { finish(false) }
                                fun sample(
                                    pos: Offset,
                                    pressure: Float,
                                    tilt: Float,
                                    erase: Boolean,
                                ) {
                                    val location =
                                        Offset(
                                            pos.x.coerceIn(0f, size.width.toFloat()),
                                            pos.y.coerceIn(0f, size.height.toFloat()),
                                        )
                                    if (mode == "Lasso") {
                                        if (moving) {
                                            moved =
                                                transformInk(
                                                    selectionNow,
                                                    frame,
                                                    origin,
                                                    location - origin,
                                                )
                                            var n = 0
                                            working =
                                                original.map { l ->
                                                    if (selectionNow.any { it === l }) moved[n++]
                                                    else l
                                                }
                                            preview = working
                                        } else trace.add(location)
                                    } else if (erase) {
                                        if (!previousErase) {
                                            closeStroke()
                                            last = null
                                        }
                                        eraser = location
                                        working =
                                            eraseInk(
                                                working,
                                                sheet,
                                                frame,
                                                last ?: location,
                                                location,
                                                settings.eraserSize * density / (2 * updatedZoom),
                                                settings.areaEraser,
                                                boundsCache,
                                            )
                                        preview = working
                                    } else {
                                        if (previousErase) last = null
                                        eraser = null
                                        val delta = location - (last ?: location)
                                        if (delta != Offset.Zero || points.isEmpty()) {
                                            val direction = atan2(delta.y, delta.x)
                                            appendSample(
                                                points,
                                                frame,
                                                frame.point(
                                                    location,
                                                    pen.weight(pressure, tilt, direction),
                                                ),
                                                density,
                                            )
                                            if (stroke == null) {
                                                stroke = pen.line(sheet, density, frame, points)
                                                activeShape = InkShape(stroke!!, frame)
                                            }
                                        }
                                    }
                                    last = location
                                    previousErase = erase
                                    tick++
                                }
                                try {
                                    while (!finished) {
                                        val penChange =
                                            event.changes.firstOrNull {
                                                it.pressed && it.type.isPen()
                                            }
                                        if (!stylus && penChange != null) {
                                            working = original
                                            preview = null
                                            stroke = null
                                            activeShape = null
                                            points = mutableListOf()
                                            trace.clear()
                                            last = null
                                            pointer = penChange.id
                                            stylus = true
                                            session.fingerOwner = null
                                        }
                                        val p =
                                            event.changes.firstOrNull { it.id == pointer } ?: break
                                        if (
                                            event.cancelled(session.rawInput, p) ||
                                                !stylus &&
                                                    (event.changes.size > 1 ||
                                                        p.isConsumed ||
                                                        session.broadContact(density))
                                        )
                                            break
                                        val native = session.rawInput
                                        val ni =
                                            if (stylus && native != null)
                                                (0 until native.pointerCount).firstOrNull {
                                                    native.getToolType(it) ==
                                                        MotionEvent.TOOL_TYPE_STYLUS ||
                                                        native.getToolType(it) ==
                                                            MotionEvent.TOOL_TYPE_ERASER
                                                } ?: 0
                                            else 0
                                        val erase =
                                            if (!p.pressed) previousErase
                                            else
                                                mode == "Eraser" ||
                                                    p.type == PointerType.Eraser ||
                                                    stylus &&
                                                        ((native?.buttonState ?: 0) and
                                                            MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                                        val tilt =
                                            native?.getAxisValue(MotionEvent.AXIS_TILT, ni) ?: 0f
                                        for ((j, h) in p.historical.withIndex()) {
                                            val hasHistory =
                                                native != null && j < native.historySize
                                            sample(
                                                h.position,
                                                if (hasHistory && stylus)
                                                    native!!.getHistoricalPressure(ni, j)
                                                else p.pressure,
                                                if (hasHistory)
                                                    native!!.getHistoricalAxisValue(
                                                        MotionEvent.AXIS_TILT,
                                                        ni,
                                                        j,
                                                    )
                                                else tilt,
                                                erase,
                                            )
                                        }
                                        if (p.pressed || p.position != last)
                                            sample(
                                                p.position,
                                                if (stylus && p.pressure > 0f) p.pressure else 1f,
                                                tilt,
                                                erase,
                                            )
                                        p.consume()
                                        if (!p.pressed) {
                                            finish(true)
                                            break
                                        }
                                        event = awaitPointerEvent()
                                    }
                                } finally {
                                    finish(false)
                                }
                            }
                        }
                )
                .semantics { if (enabled) contentDescription = "Ink page ${sheet+1}" }
        ) {
            tick
            activeShape?.draw(drawContext.canvas.nativeCanvas)
            eraser?.let {
                drawCircle(
                    ink.copy(alpha = .48f),
                    radius,
                    it,
                    style = Stroke(1.dp.toPx() / updatedZoom),
                )
            }
            if (trace.size > 1)
                for (i in 1 until trace.size) drawLine(sage, trace[i - 1], trace[i], 1.4.dp.toPx())
            val box = if (preview != null && art.mode == "Lasso") null else selectedBounds
            box?.inflate(4.dp.toPx())?.let {
                drawRect(
                    sage,
                    it.topLeft,
                    it.size,
                    style =
                        Stroke(
                            1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                        ),
                )
            }
        }
        if (
            enabled &&
                art.mode == "Lasso" &&
                (selection.isNotEmpty() || session.clipboard.isNotEmpty())
        ) {
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.align(Alignment.TopEnd)) {
                TextButton(onClick = { menu = true }) { Text(wording("Selection", "선택 편집")) }
                DropdownMenu(menu, { menu = false }, containerColor = ivory) {
                    val pivot = selectedBounds?.center ?: Offset.Zero
                    if (selection.isNotEmpty()) {
                        listOf(
                                "Copy" to "복사",
                                "Duplicate" to "복제",
                                "Enlarge" to "확대",
                                "Shrink" to "축소",
                                "Rotate left" to "왼쪽 회전",
                                "Rotate right" to "오른쪽 회전",
                                "Delete" to "삭제",
                            )
                            .forEach { (en, ko) ->
                                DropdownMenuItem(
                                    text = { Text(wording(en, ko)) },
                                    onClick = {
                                        menu = false
                                        when (en) {
                                            "Copy" -> session.clipboard = selection
                                            "Duplicate" ->
                                                changeSelection(
                                                    transformInk(
                                                        selection,
                                                        frame,
                                                        pivot,
                                                        Offset(12 * density, 12 * density),
                                                    ),
                                                    true,
                                                )
                                            "Delete" -> changeSelection(emptyList())
                                            else ->
                                                changeSelection(
                                                    transformInk(
                                                        selection,
                                                        frame,
                                                        pivot,
                                                        Offset.Zero,
                                                        when (en) {
                                                            "Enlarge" -> 1.15f
                                                            "Shrink" -> 1 / 1.15f
                                                            else -> 1f
                                                        },
                                                        when (en) {
                                                            "Rotate left" -> -.2617994f
                                                            "Rotate right" -> .2617994f
                                                            else -> 0f
                                                        },
                                                    )
                                                )
                                        }
                                    },
                                )
                            }
                    }
                    if (session.clipboard.isNotEmpty())
                        DropdownMenuItem(
                            text = { Text(wording("Paste", "붙여넣기")) },
                            onClick = {
                                menu = false
                                changeSelection(
                                    transformInk(
                                        session.clipboard.map { it.copy(sheet = sheet) },
                                        frame,
                                        Offset.Zero,
                                        Offset(10 * density, 10 * density),
                                    ),
                                    true,
                                )
                            },
                        )
                }
            }
        }
    }
}
