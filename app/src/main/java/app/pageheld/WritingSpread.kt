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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WritingSpread(
    session: WritingSession,
    prefs: Map<String, String>,
    motion: Boolean,
    selected: String,
    showResizeHint: Boolean,
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
    BoxWithConstraints(
        Modifier.fillMaxSize().motionEventSpy(session::observeInput).background(Binding.desk)
    ) {
        val wide = maxWidth >= 580.dp
        val span = if (wide) 2 else 1
        val inset = if (wide) 20.dp else 8.dp
        val trim = 4.dp
        val verticalInset = 3.dp
        val textTop = 20.dp
        val textBottom = 36.dp
        val margin =
            when (prefs["margin"]) {
                "Narrow" -> 16.dp
                "Wide" -> 40.dp
                else -> if (wide) 28.dp else 18.dp
            }
        // Measure against the full sheet. Only the outer viewport consumes the IME inset.
        val bookHeight = maxHeight.coerceAtLeast(206.dp)
        val sheetHeight = bookHeight - 2 * (verticalInset + trim)
        val w = (maxWidth - 2 * (inset + trim)) / span
        val textWidth = with(density) { (w - 2 * margin).roundToPx().coerceAtLeast(40) }
        val textHeight =
            with(density) { (sheetHeight - textTop - textBottom).roundToPx().coerceAtLeast(40) }
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
        val generation = session.textGeneration
        var pinned by remember(generation) { mutableStateOf<List<TextSlice>?>(null) }
        var composingText by remember(generation) { mutableStateOf(false) }
        LaunchedEffect(page.body, composingText, generation, art.mode) {
            if (!composingText && pinned != null) {
                // A changing suffix resets Android's edit buffer. Keep it intact across a
                // burst of keys and settle the measured boundary once input becomes idle.
                kotlinx.coroutines.delay(250)
                pinned = null
            }
        }
        val slices = pinned ?: computed
        val actual = art.contentCount(slices.size)
        val active = art.sheet.coerceAtLeast(0)
        val total = ((maxOf(actual, active + 1) + span - 1) / span) * span
        val start = active / span * span
        SideEffect {
            session.sheets = slices
            session.savedCount = actual
            session.span = span
            count(actual, slices.size)
        }
        LaunchedEffect(page.id, start, span, art.prints) {
            for ((name, pos) in art.prints) if (
                pos.sheet in (start - span) until (start + 2 * span)
            )
                loadPrint(File(mediaDir(session.book), name))
        }
        var pagePicker by remember { mutableStateOf(false) }
        var keepKeyboard by remember { mutableStateOf(false) }
        var focusPage by remember { mutableIntStateOf(-1) }
        val turn = remember { BookTurn(scope) { target -> session.turnTo(target.created.toInt()) } }
        SideEffect { turn.motion = motion }
        fun target(sign: Int): Leaf? {
            val dest = start + sign * span
            return if (dest >= 0) Leaf("Sheet", created = dest.toLong()) else null
        }
        fun turnPage(sign: Int) {
            target(sign)?.let {
                focus.clearFocus()
                pinned = null
                turn.jump(it, sign)
            }
        }
        val viewport = remember(start, wide) { PaperViewport() }
        val lookup by rememberUpdatedState<(Int) -> Leaf?> { target(it) }
        val begin by rememberUpdatedState {
            focus.clearFocus()
            keyboard?.hide()
            pinned = null
        }
        val scroll = rememberScrollState()
        val ime = WindowInsets.ime.getBottom(density)
        LaunchedEffect(ime) { if (ime == 0) scroll.scrollTo(0) }
        Box(
            Modifier.fillMaxSize()
                .imePadding()
                .clipToBounds()
                .diaryGestures(
                    turn,
                    viewport,
                    session,
                    span,
                    with(density) { inset.toPx() },
                    ime == 0,
                    { lookup(it) },
                    { begin() },
                )
                .verticalScroll(scroll, enabled = ime > 0)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .height(bookHeight)
                    .padding(horizontal = inset, vertical = verticalInset)
            ) {
                Stack(Modifier.fillMaxSize(), covered = false)
                Box(
                    Modifier.fillMaxSize()
                        .padding(trim)
                        .clip(if (wide) FoldedTrim else round)
                        .graphicsLayer {
                            scaleX = viewport.scale
                            scaleY = viewport.scale
                            translationX = viewport.pan.x
                            translationY = viewport.pan.y
                        }
                        .bindingShading(wide)
                ) {
                    TurningLeaves(
                        Leaf("Sheet", created = start.toLong()),
                        turn,
                        wide,
                        false,
                        { direction -> target(direction) },
                        inset = 0.dp,
                    ) { leaf, isCurrent ->
                        Row(Modifier.fillMaxSize()) {
                            repeat(span) { offset ->
                                val index = leaf.created.toInt() / span * span + offset
                                val sl =
                                    slices.getOrNull(index)
                                        ?: TextSlice(page.body.length, page.body.length)
                                val raw =
                                    page.body.substring(
                                        sl.start.coerceAtMost(page.body.length),
                                        sl.end.coerceAtMost(page.body.length),
                                    )
                                val request = remember { FocusRequester() }
                                var field by
                                    remember(generation) {
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
                                                top = textTop,
                                                bottom = textBottom,
                                            )
                                    ) {
                                        if (raw.isEmpty() && index == 0 && art.mode == "Type")
                                            Note(wording("Begin your story…", "오늘의 이야기를 써 보세요…"))
                                        if (art.mode == "Type" && isCurrent)
                                            BasicTextField(
                                                shown,
                                                { value ->
                                                    if (
                                                        session.locked ||
                                                            turn.target != null ||
                                                            generation != session.textGeneration
                                                    )
                                                        return@BasicTextField
                                                    if (
                                                        index >= slices.size && value.text.isEmpty()
                                                    )
                                                        return@BasicTextField
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
                                                        if (index >= slices.size)
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
                                                    val measured =
                                                        if (composing) slices
                                                        else paginate(body, fit)
                                                    val dest =
                                                        if (
                                                            composing ||
                                                                value.text == shown.text &&
                                                                    value.composition ==
                                                                        shown.composition
                                                        )
                                                            index
                                                        else
                                                            measured
                                                                .indexOfLast { it.start <= cursor }
                                                                .coerceAtLeast(0)
                                                    composingText = composing
                                                    pinned =
                                                        if (
                                                            composing ||
                                                                dest == index &&
                                                                    (pinned != null ||
                                                                        value.text != shown.text)
                                                        )
                                                            pinTextSlices(
                                                                slices,
                                                                index,
                                                                sl,
                                                                prefix.length,
                                                                delta,
                                                            )
                                                        else null
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
                                                        isCurrent &&
                                                            art.mode in listOf("Photo", "Type"),
                                                        gesture = { session.photoGesture = it },
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
                                    }
                                    InkSurface(index, session, isCurrent, viewport.scale)
                                    Text(
                                        "${index+1} / ${maxOf(total,(index/span+1)*span)}",
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
        if (showResizeHint) {
            Text(
                wording("Pinch with two fingers to resize.", "두 손가락을 오므리거나 벌려 크기를 조절하세요."),
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 36.dp)
                    .shadow(2.dp, CircleShape).background(ink, CircleShape)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = ivory, fontSize = uiSp(14),
            )
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
            for (direction in listOf(-1, 1)) {
                if (target(direction) != null)
                    Box(
                        Modifier.align(
                                if (direction < 0) Alignment.CenterStart else Alignment.CenterEnd
                            )
                            .fillMaxHeight()
                            .width(inset)
                            .systemGestureExclusion()
                            .semantics {
                                contentDescription =
                                    if (direction < 0) "Left page margin" else "Right page margin"
                            }
                    )
            }
            listOf(-1, 1).forEach { sign ->
                if (target(sign) != null && (sign < 0 || start + span < actual))
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
    gesture: (Boolean) -> Unit,
    choose: () -> Unit,
    commit: (PrintPosition) -> Unit,
    view: () -> Unit,
) {
    val bitmap by produceState(cachedPrint(file), file) { value = loadPrint(file) }
    val ratio = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1.4f
    val onChoose by rememberUpdatedState(choose)
    val onCommit by rememberUpdatedState(commit)
    val onView by rememberUpdatedState(view)
    var preview by remember(file, position) { mutableStateOf<PrintPosition?>(null) }
    val working = preview ?: position
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
        val outline = sage
        Box(
            Modifier.offset(left, top)
                .size(width, height)
                .then(
                    if (!interactive) Modifier
                    else
                        Modifier.pointerInput(pw, ph, ratio, position) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                gesture(true)
                                try {
                                    var elapsed = 0L
                                    do {
                                        val ev = awaitPointerEvent()
                                        if (ev.cancelled(null)) return@awaitEachGesture
                                        val pan = ev.calculatePan()
                                        val zoom = ev.calculateZoom()
                                        elapsed =
                                            ev.changes.first().uptimeMillis - down.uptimeMillis
                                        if (pan != Offset.Zero || zoom != 1f) {
                                            val old = preview ?: position
                                            val before = widthFor(old)
                                            val after =
                                                minOf(
                                                    (before * zoom).coerceAtLeast(minOf(44.dp, pw)),
                                                    pw,
                                                    (ph - foot) * ratio + border,
                                                )
                                            val bh = (before - border) / ratio + foot
                                            val ah = (after - border) / ratio + foot
                                            val anchor = ev.calculateCentroid(useCurrent = false)
                                            val fx = (anchor.x / before.toPx()).coerceIn(0f, 1f)
                                            val fy = (anchor.y / bh.toPx()).coerceIn(0f, 1f)
                                            val x =
                                                (pw - before) * old.x +
                                                    (before - after) * fx +
                                                    with(density) { pan.x.toDp() }
                                            val y =
                                                (ph - bh) * old.y +
                                                    (bh - ah) * fy +
                                                    with(density) { pan.y.toDp() }
                                            preview =
                                                old.copy(
                                                    width = after / pw,
                                                    x =
                                                        if (pw > after)
                                                            (x / (pw - after)).coerceIn(0f, 1f)
                                                        else 0f,
                                                    y =
                                                        if (ph > ah)
                                                            (y / (ph - ah)).coerceIn(0f, 1f)
                                                        else 0f,
                                                )
                                        }
                                        ev.changes.forEach { it.consume() }
                                    } while (ev.changes.any { it.pressed })
                                    onChoose()
                                    val final = preview
                                    if (final != null) onCommit(final) else if (elapsed > 500) onView()
                                } finally {
                                    preview = null
                                    gesture(false)
                                }
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

                }
        }
    }
}
