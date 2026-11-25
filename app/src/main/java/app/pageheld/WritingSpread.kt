package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import java.io.File
import kotlin.math.*

@Composable
internal fun WritingSpread(
    session: WritingSession,
    prefs: Map<String, String>,
    motion: Boolean,
    selected: String,
    select: (String) -> Unit,
    view: (String) -> Unit,
    addPage: () -> Unit,
    count: (Int, Int) -> Unit,
) {
    val art = session.layout
    val page = session.page
    val density = LocalDensity.current
    val font = notebookFont(session.book.context, prefs["font"])
    val size = writingSize(prefs)
    val lineHeight =
        (size *
                when (prefs["spacing"]) {
                    "Close" -> 1.35f
                    "Airy" -> 1.9f
                    else -> 1.58f
                })
            .sp
    val style =
        TextStyle(color = ink, fontFamily = font, fontSize = size.sp, lineHeight = lineHeight)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xffe8dece))) {
        val wide = maxWidth >= 580.dp
        val span = if (wide) 2 else 1
        val inset = if (wide) 36.dp else 15.dp
        val margin =
            when (prefs["margin"]) {
                "Narrow" -> 16.dp
                "Wide" -> 40.dp
                else -> if (wide) 28.dp else 18.dp
            }
        // Measure against the full sheet. Only the outer viewport consumes the IME inset.
        val h = (maxHeight - 16.dp).coerceAtLeast(190.dp)
        val w = (maxWidth - 2 * inset - 12.dp) / span
        val textWidth = with(density) { (w - 2 * margin).roundToPx().coerceAtLeast(40) }
        val textHeight = with(density) { (h - 72.dp).roundToPx().coerceAtLeast(40) }
        val fit =
            remember(measurer, style, textWidth, textHeight) {
                { text: String, start: Int, limit: Int ->
                    // Bound each layout to a small prefix, expanding only for unusually small
                    // glyphs.
                    var end = minOf(start + 1024, limit)
                    var length: Int
                    while (true) {
                        if (end < limit && Character.isHighSurrogate(text[end - 1])) end++
                        val measured =
                            measurer.measure(
                                AnnotatedString(text.substring(start, end)),
                                style = style,
                                constraints = Constraints(maxWidth = textWidth),
                                maxLines = (textHeight / 4 + 2),
                            )
                        var lines = 1
                        while (
                            lines < measured.lineCount &&
                                measured.getLineBottom(lines) <= textHeight
                        ) lines++
                        if (
                            lines < measured.lineCount || measured.hasVisualOverflow || end == limit
                        ) {
                            length = measured.getLineEnd(lines - 1, visibleEnd = false)
                            break
                        }
                        end = minOf(limit.toLong(), start.toLong() + 2L * (end - start)).toInt()
                    }
                    length
                }
            }
        val computed = remember(page.body, fit) { paginate(page.body, fit) }
        var pinned by remember { mutableStateOf<List<TextSlice>?>(null) }
        val slices = pinned ?: computed
        val actual =
            maxOf(
                slices.size,
                1 + (art.prints.values.maxOfOrNull { it.sheet } ?: -1),
                1 + (art.lines.maxOfOrNull { it.sheet } ?: -1),
            )
        val total = ((actual + span - 1) / span) * span
        val active = art.sheet.coerceIn(0, total - 1)
        val start = active / span * span
        SideEffect { count(actual, slices.size) }
        var pagePicker by remember { mutableStateOf(false) }
        var keepKeyboard by remember { mutableStateOf(false) }
        var focusPage by remember { mutableIntStateOf(-1) }
        val turn = remember {
            BookTurn(scope) { target ->
                session.update(
                    art = session.layout.copy(sheet = target.created.toInt()),
                    record = false,
                )
            }
        }
        SideEffect { turn.motion = motion }
        fun target(sign: Int): Leaf? {
            val dest = start + sign * span
            return if (dest in 0 until total) Leaf("Sheet", created = dest.toLong()) else null
        }
        fun turnPage(sign: Int) {
            target(sign)?.let {
                focus.clearFocus()
                pinned = null
                turn.jump(it, sign)
            }
        }
        val scroll = rememberScrollState()
        val ime = WindowInsets.ime.getBottom(density)
        LaunchedEffect(ime) { if (ime == 0) scroll.scrollTo(0) }
        Box(Modifier.fillMaxSize().imePadding().clipToBounds().verticalScroll(scroll)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(h + 16.dp)
                    .padding(horizontal = inset, vertical = 6.dp)
            ) {
                Stack(Modifier.fillMaxSize(), covered = false)
                Box(
                    Modifier.fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clip(if (wide) FoldedTrim else round)
                        .bindingShading(wide)
                ) {
                    TurningLeaves(
                        Leaf("Sheet", created = start.toLong()),
                        turn,
                        wide,
                        !session.locked && ime == 0 && art.mode == "Type",
                        { direction -> target(direction) },
                        inset = 0.dp,
                    ) { leaf, isCurrent ->
                        Row(Modifier.fillMaxSize()) {
                            repeat(span) { offset ->
                                val index = leaf.created.toInt() + offset
                                val sl =
                                    slices.getOrNull(index)
                                        ?: TextSlice(page.body.length, page.body.length)
                                val raw =
                                    page.body.substring(
                                        sl.start.coerceAtMost(page.body.length),
                                        sl.end.coerceAtMost(page.body.length),
                                    )
                                val request = remember { FocusRequester() }
                                var field by remember {
                                    mutableStateOf(
                                        TextFieldValue(
                                            raw,
                                            TextRange(
                                                (art.cursor - sl.start).coerceIn(0, raw.length)
                                            ),
                                        )
                                    )
                                }
                                val shown =
                                    if (field.text == raw) field
                                    else
                                        TextFieldValue(
                                            raw,
                                            TextRange(
                                                (art.cursor - sl.start).coerceIn(0, raw.length)
                                            ),
                                        )
                                LaunchedEffect(focusPage, index, isCurrent) {
                                    if (focusPage == index && isCurrent && art.mode == "Type") {
                                        field =
                                            TextFieldValue(
                                                raw,
                                                TextRange(
                                                    (session.layout.cursor - sl.start).coerceIn(
                                                        0,
                                                        raw.length,
                                                    )
                                                ),
                                            )
                                        request.requestFocus()
                                        if (keepKeyboard) {
                                            withFrameNanos {}
                                            keyboard?.show()
                                            keepKeyboard = false
                                        }
                                        focusPage = -1
                                    }
                                }
                                val paper = page.paper.ifBlank { prefs["paper"] ?: "Blank" }
                                Box(
                                    Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .clipToBounds()
                                        .paper(
                                            paper,
                                            swatches.getOrElse(prefs["tone"]?.toIntOrNull() ?: 0) {
                                                ivory
                                            },
                                            with(density) { lineHeight.toDp() },
                                        )
                                ) {
                                    Box(
                                        Modifier.fillMaxSize()
                                            .padding(
                                                start = margin,
                                                end = margin,
                                                top = 20.dp,
                                                bottom = 36.dp,
                                            )
                                    ) {
                                        if (raw.isEmpty() && index == 0 && art.mode == "Type")
                                            Note(wording("Begin your story…", "오늘의 이야기를 써 보세요…"))
                                        if (art.mode == "Type" && isCurrent)
                                            BasicTextField(
                                                shown,
                                                { value ->
                                                    if (session.locked) return@BasicTextField
                                                    val current = session.page
                                                    // IMEs can finish a composing region after its
                                                    // old sheet has left composition.
                                                    // A late selection acknowledgement must never
                                                    // restore the outgoing slice.
                                                    val stale = current.body != page.body
                                                    if (
                                                        stale &&
                                                            (session.layout.sheet != index ||
                                                                value.text == shown.text)
                                                    )
                                                        return@BasicTextField
                                                    field = value
                                                    val prefix =
                                                        if (index >= slices.size && !stale)
                                                            "\u000c".repeat(index - slices.size + 1)
                                                        else ""
                                                    val body =
                                                        current.body.replaceRange(
                                                            sl.start,
                                                            (sl.end + current.body.length -
                                                                    page.body.length)
                                                                .coerceIn(
                                                                    sl.start,
                                                                    current.body.length,
                                                                ),
                                                            prefix + value.text,
                                                        )
                                                    val cursor =
                                                        sl.start +
                                                            prefix.length +
                                                            value.selection.end
                                                    val composing = value.composition != null
                                                    val delta = body.length - page.body.length
                                                    val after =
                                                        if (composing)
                                                            slices.toMutableList().apply {
                                                                while (size <= index) add(
                                                                    TextSlice(sl.start, sl.start)
                                                                )
                                                                for (i in index until size) this[
                                                                    i] =
                                                                    if (i == index)
                                                                        TextSlice(
                                                                            sl.start +
                                                                                prefix.length,
                                                                            sl.end + delta,
                                                                        )
                                                                    else
                                                                        TextSlice(
                                                                            this[i].start + delta,
                                                                            this[i].end + delta,
                                                                        )
                                                            }
                                                        else paginate(body, fit)
                                                    pinned = if (composing) after else null
                                                    val dest =
                                                        if (
                                                            composing ||
                                                                value.text == shown.text &&
                                                                    value.composition ==
                                                                        shown.composition
                                                        )
                                                            index
                                                        else
                                                            after
                                                                .indexOfLast { it.start <= cursor }
                                                                .coerceAtLeast(0)
                                                    session.update(
                                                        current.copy(body = body),
                                                        session.layout.copy(
                                                            sheet = dest,
                                                            cursor = cursor,
                                                        ),
                                                        record = body != page.body,
                                                    )
                                                    if (dest != index) {
                                                        keepKeyboard = ime > 0
                                                        focusPage = dest
                                                    }
                                                },
                                                Modifier.fillMaxSize()
                                                    .focusRequester(request)
                                                    .onFocusChanged {
                                                        if (it.isFocused) {
                                                            select("")
                                                            if (index != session.layout.sheet)
                                                                session.update(
                                                                    art =
                                                                        session.layout.copy(
                                                                            sheet = index
                                                                        ),
                                                                    record = false,
                                                                )
                                                        }
                                                    }
                                                    .semantics {
                                                        contentDescription =
                                                            "Writing page ${index+1}"
                                                    },
                                                textStyle = style,
                                                cursorBrush = SolidColor(sage),
                                            )
                                        else Text(raw, style = style)
                                    }
                                    Box(
                                        Modifier.fillMaxSize()
                                            .padding(7.dp)
                                            .then(
                                                if (art.mode == "Photo")
                                                    Modifier.pointerInput(Unit) {
                                                        detectTapGestures {
                                                            select("")
                                                            session.update(
                                                                art =
                                                                    session.layout.copy(
                                                                        sheet = index
                                                                    ),
                                                                record = false,
                                                            )
                                                        }
                                                    }
                                                else Modifier
                                            )
                                    ) {
                                        art.prints
                                            .filterValues { it.sheet == index }
                                            .forEach { (name, pos) ->
                                                key(name) {
                                                    PhotoPrint(
                                                        File(mediaDir(session.book), name),
                                                        pos,
                                                        selected == name,
                                                        art.mode != "Pen" && art.mode != "Eraser",
                                                        choose = {
                                                            select(name)
                                                            session.update(
                                                                art =
                                                                    session.layout.copy(
                                                                        sheet = index,
                                                                        mode = "Photo",
                                                                    ),
                                                                record = false,
                                                            )
                                                        },
                                                        commit = { p ->
                                                            session.update(
                                                                art =
                                                                    session.layout.copy(
                                                                        prints =
                                                                            session.layout.prints +
                                                                                (name to p)
                                                                    )
                                                            )
                                                        },
                                                        view = { view(name) },
                                                    )
                                                }
                                            }
                                        InkSurface(index, session)
                                    }
                                    Text(
                                        "${index+1} / $total",
                                        Modifier.align(Alignment.BottomCenter)
                                            .clickable(onClickLabel = "Choose page") {
                                                pagePicker = true
                                            }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        color = muted,
                                        fontSize = uiSp(15),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pagePicker) {
            AlertDialog(
                onDismissRequest = { pagePicker = false },
                containerColor = ivory,
                title = { Title(wording("Pages", "페이지")) },
                text = {
                    LazyRow(
                        state = rememberLazyListState(initialFirstVisibleItemIndex = active),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(total) { index ->
                            Box(
                                Modifier.size(52.dp, 68.dp)
                                    .clip(round)
                                    .paper("Blank")
                                    .border(
                                        if (index == active) 2.dp else 1.dp,
                                        if (index == active) sage else edge,
                                        round,
                                    )
                                    .clickable {
                                        pagePicker = false
                                        focus.clearFocus()
                                        pinned = null
                                        turn.jump(
                                            Leaf("Sheet", created = index.toLong()),
                                            if (index >= start) 1 else -1,
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text((index + 1).toString())
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pagePicker = false
                            addPage()
                        }
                    ) {
                        Text(wording("+ Add page", "+ 페이지 추가"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pagePicker = false }) { Text(wording("Close", "닫기")) }
                },
            )
        }
        if (ime == 0) {
            val travel = with(density) { (maxWidth - inset * 2).toPx() / span }
            for (direction in listOf(-1, 1)) {
                val destination = target(direction)
                if (art.mode == "Type" && destination != null) {
                    Box(
                        Modifier.align(
                                if (direction < 0) Alignment.CenterStart else Alignment.CenterEnd
                            )
                            .fillMaxHeight()
                            .width(inset + 8.dp)
                            .systemGestureExclusion()
                            .semantics {
                                contentDescription =
                                    if (direction < 0) "Left page margin" else "Right page margin"
                            }
                            .pointerInput(turn, destination, direction, travel) {
                                var tracking = false
                                detectHorizontalDragGestures(
                                    onDragStart = { tracking = false },
                                    onHorizontalDrag = { change, delta ->
                                        if (
                                            !tracking &&
                                                -direction * delta > 0 &&
                                                change.type == PointerType.Touch
                                        ) {
                                            tracking =
                                                turn.begin(
                                                    destination,
                                                    direction,
                                                    change.position.y / this.size.height,
                                                )
                                        }
                                        if (tracking) {
                                            change.consume()
                                            turn.drag(-direction * delta / travel)
                                        }
                                    },
                                    onDragEnd = {
                                        if (tracking) turn.finish(turn.progress >= .36f)
                                        tracking = false
                                    },
                                    onDragCancel = {
                                        if (tracking) turn.finish(false)
                                        tracking = false
                                    },
                                )
                            }
                    )
                }
            }
            listOf(-1, 1).forEach { sign ->
                if (target(sign) != null)
                    Box(
                        Modifier.align(if (sign < 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(horizontal = 2.dp)
                            .size(if (wide) 34.dp else 28.dp)
                            .shadow(2.dp, CircleShape)
                            .background(ivory, CircleShape)
                            .clickable { turnPage(sign) }
                            .semantics {
                                contentDescription =
                                    if (sign < 0) "Previous pages" else "Next pages"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.graphicsLayer { scaleX = sign.toFloat() }) {
                            Glyph("chevron-right", size = 18)
                        }
                    }
            }
        }
    }
}

@Composable
private fun PhotoPrint(
    file: File,
    position: PrintPosition,
    selected: Boolean,
    interactive: Boolean,
    choose: () -> Unit,
    commit: (PrintPosition) -> Unit,
    view: () -> Unit,
) {
    val ratio by
        produceState(1.4f, file) {
            value =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                            if (file.extension == "video") return@runCatching 1.6f
                            var aspect = 1f
                            android.graphics.ImageDecoder.decodeBitmap(
                                    android.graphics.ImageDecoder.createSource(file)
                                ) { decoder, info, _ ->
                                    aspect = info.size.width.toFloat() / info.size.height
                                    decoder.setTargetSize(1, 1)
                                }
                                .recycle()
                            aspect
                        }
                        .getOrDefault(1.4f)
                }
        }
    val onChoose by rememberUpdatedState(choose)
    val onCommit by rememberUpdatedState(commit)
    val onView by rememberUpdatedState(view)
    var working by remember(position) { mutableStateOf(position) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pw = maxWidth
        val ph = maxHeight
        val border = 12.dp
        val foot = 20.dp
        fun widthFor(p: PrintPosition) =
            minOf(pw * p.width, (ph - foot) * ratio + border, pw).coerceAtLeast(1.dp)
        val width = widthFor(working)
        val height = ((width - border).coerceAtLeast(1.dp) / ratio + foot).coerceAtMost(ph)
        val left = (pw - width) * working.x.coerceIn(0f, 1f)
        val top = (ph - height) * working.y.coerceIn(0f, 1f)
        val density = LocalDensity.current
        val outline = Color(0xff76954c)
        Box(
            Modifier.offset(left, top)
                .size(width, height)
                .then(
                    if (!interactive) Modifier
                    else
                        Modifier.pointerInput(pw, ph, ratio) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                var moved = false
                                var elapsed = 0L
                                do {
                                    val ev = awaitPointerEvent()
                                    val pan = ev.calculatePan()
                                    val zoom = ev.calculateZoom()
                                    elapsed = ev.changes.first().uptimeMillis - down.uptimeMillis
                                    if (pan != Offset.Zero || zoom != 1f) {
                                        val old = working
                                        val before = widthFor(old)
                                        val after =
                                            minOf(
                                                (before * zoom).coerceAtLeast(minOf(44.dp, pw)),
                                                pw,
                                                (ph - foot) * ratio + border,
                                            )
                                        val bh = (before - border) / ratio + foot
                                        val ah = (after - border) / ratio + foot
                                        val x =
                                            (pw - before) * old.x +
                                                (before - after) / 2 +
                                                with(density) { pan.x.toDp() }
                                        val y =
                                            (ph - bh) * old.y +
                                                (bh - ah) / 2 +
                                                with(density) { pan.y.toDp() }
                                        working =
                                            old.copy(
                                                width = after / pw,
                                                x =
                                                    if (pw > after)
                                                        (x / (pw - after)).coerceIn(0f, 1f)
                                                    else 0f,
                                                y =
                                                    if (ph > ah) (y / (ph - ah)).coerceIn(0f, 1f)
                                                    else 0f,
                                            )
                                        moved = true
                                    }
                                    ev.changes.forEach { it.consume() }
                                } while (ev.changes.any { it.pressed })
                                onChoose()
                                if (moved) onCommit(working) else if (elapsed > 500) onView()
                            }
                        }
                )
                .semantics { contentDescription = "Photograph on page ${position.sheet+1}" }
        ) {
            MediaMount(Modifier.fillMaxSize(), taped = !selected) {
                Thumbnail(file, Modifier.fillMaxSize(), large = true)
            }
            if (selected)
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        outline,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                    for (x in listOf(0f, .5f, 1f)) for (y in listOf(0f, .5f, 1f)) if (
                        x != .5f || y != .5f
                    ) {
                        val o = Offset(size.width * x, size.height * y)
                        drawCircle(outline, 5.dp.toPx(), o)
                        drawCircle(ivory, 2.5.dp.toPx(), o)
                    }
                }
        }
    }
}

@Composable
private fun InkSurface(sheet: Int, session: WritingSession) {
    val art = session.layout
    var live by remember { mutableStateOf<InkLine?>(null) }
    val enabled = art.mode in listOf("Pen", "Eraser")
    val drawing =
        if (!enabled) Modifier
        else
            Modifier.pointerInput(sheet, art.mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (session.locked) return@awaitEachGesture
                    val brush = session.layout
                    val erase = brush.mode == "Eraser" || down.type == PointerType.Eraser
                    val points = mutableListOf<NibPoint>()
                    var remaining = brush.lines
                    fun sample(p: PointerInputChange) {
                        val dot =
                            NibPoint(
                                (p.position.x / size.width).coerceIn(0f, 1f),
                                (p.position.y / size.height).coerceIn(0f, 1f),
                                if (p.type == PointerType.Stylus) p.pressure.coerceIn(.15f, 1.5f)
                                else 1f,
                            )
                        points += dot
                        if (erase) {
                            val radius = 16.dp.toPx()
                            remaining =
                                remaining.filterNot { line ->
                                    line.sheet == sheet &&
                                        line.points.withIndex().any { (i, a) ->
                                            val b = line.points.getOrElse(i + 1) { a }
                                            val from = Offset(a.x * size.width, a.y * size.height)
                                            val to = Offset(b.x * size.width, b.y * size.height)
                                            val segment = to - from
                                            val d = p.position - from
                                            val t =
                                                if (segment.getDistanceSquared() == 0f) 0f
                                                else
                                                    ((d.x * segment.x + d.y * segment.y) /
                                                            segment.getDistanceSquared())
                                                        .coerceIn(0f, 1f)
                                            (d - segment * t).getDistance() < radius
                                        }
                                }
                        } else {
                            val thickness =
                                brush.thickness *
                                    listOf(.65f, 1f, 1.8f, 4f)[brush.nib.coerceIn(0, 3)]
                            val color =
                                if (brush.nib == 3) (brush.color and 0x00ffffff) or 0x60000000
                                else brush.color
                            live =
                                InkLine(
                                    sheet,
                                    color,
                                    thickness.dp.toPx() / size.width,
                                    points.toList(),
                                )
                        }
                    }
                    var pointer = down.id
                    var stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    down.consume()
                    sample(down)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pen =
                                event.changes.firstOrNull {
                                    it.pressed && it.type == PointerType.Stylus
                                }
                            if (!stylus && pen != null) {
                                pointer = pen.id
                                stylus = true
                                points.clear()
                                remaining = brush.lines
                            }
                            val p = event.changes.firstOrNull { it.id == pointer } ?: break
                            if (!p.pressed) break
                            if (p.positionChanged()) sample(p)
                            event.changes.forEach { it.consume() }
                        }
                        val lines =
                            if (erase) remaining else session.layout.lines + listOfNotNull(live)
                        session.update(art = session.layout.copy(sheet = sheet, lines = lines))
                    } finally {
                        live = null
                    }
                }
            }
    Canvas(
        Modifier.fillMaxSize().then(drawing).semantics {
            if (enabled) contentDescription = "Ink page ${sheet+1}"
        }
    ) {
        (art.lines.filter { it.sheet == sheet } + listOfNotNull(live)).forEach { stroke ->
            val base = stroke.width * size.width
            val color = Color(stroke.color)
            var previous: Offset? = null
            for (p in stroke.points) {
                val xy = Offset(p.x * size.width, p.y * size.height)
                val width = base * p.pressure
                if (previous == null) drawCircle(color, width / 2, xy)
                else drawLine(color, previous, xy, width, StrokeCap.Round)
                previous = xy
            }
        }
    }
}
