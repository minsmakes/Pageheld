package app.pageheld

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.io.File
import kotlinx.coroutines.*

private data class StudioState(val title: String, val front: String, val back: String)

@Composable
internal fun NewBookDialog(cancel: () -> Unit, create: (String, CoverDesign) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf("linen") }
    var color by rememberSaveable { mutableIntStateOf(clothColor) }
    AlertDialog(
        onDismissRequest = cancel,
        title = { Title(wording("New Book", "새 책")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    title,
                    { title = it.take(180) },
                    label = { Text(wording("Book title", "책 제목")) },
                    singleLine = true,
                )
                MaterialChoices(material) { material = it }
                ColorChoices(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    create(
                        title.trim(),
                        CoverDesign.read("{}").copy(material = material, color = color),
                    )
                },
            ) {
                Text(wording("Create", "만들기"))
            }
        },
        dismissButton = { TextButton(onClick = cancel) { Text(wording("Cancel", "취소")) } },
    )
}

@Composable
private fun MaterialChoices(value: String, choose: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LocalArtwork.current
            .filter { it.category == "materials" }
            .forEach { art ->
                Column(
                    Modifier.width(58.dp).clickable { choose(art.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(52.dp)
                            .shadow(1.dp, round)
                            .clip(round)
                            .border(
                                if (art.id == value) 2.dp else .5.dp,
                                if (art.id == value) sage else edge,
                                round,
                            )
                    ) {
                        ArtImage(art.id, Modifier.fillMaxSize())
                        if (art.id == value)
                            Box(Modifier.align(Alignment.BottomEnd).background(sage, CircleShape)) {
                                Glyph("check", tint = ivory, size = 14)
                            }
                    }
                    Note(wording(art.en, art.ko))
                }
            }
    }
}

@Composable
private fun ColorChoices(value: Int, choose: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        coverColors.forEachIndexed { i, c ->
            val name =
                wording(
                    listOf("Sage", "Cream", "Tan", "Rose", "Lilac", "Mist", "Denim", "Umber")[i],
                    listOf("세이지", "크림", "황갈색", "장미", "라일락", "안개", "데님", "엄버")[i],
                )
            Box(
                Modifier.size(40.dp)
                    .semantics {
                        contentDescription = name
                        selected = c == value
                    }
                    .clickable { choose(c) }
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        if (value == c) 2.dp else .6.dp,
                        if (value == c) sage else edge,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (c == value) Glyph("check", size = 18)
            }
        }
    }
}

internal suspend fun importCoverPhoto(book: Notebook, uri: Uri): CoverLayer =
    withContext(Dispatchers.IO) {
        val file = File(mediaDir(book), uid() + ".photo")
        try {
            val bmp =
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(book.context.contentResolver, uri)
                ) { decoder, info, _ ->
                    val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(
                        maxOf(1, (info.size.width * scale).toInt()),
                        maxOf(1, (info.size.height * scale).toInt()),
                    )
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            try {
                file.outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                CoverLayer(
                    kind = "photo",
                    asset = file.name,
                    width = .52f,
                    ratio = (bmp.width.toFloat() / bmp.height).coerceIn(.05f, 20f),
                )
            } finally {
                bmp.recycle()
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

@Composable
internal fun BookStudio(
    book: Notebook,
    identity: LibraryBook,
    onClose: () -> Unit,
    navigate: (String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(identity.title) }
    var front by rememberSaveable { mutableStateOf(identity.front) }
    var back by rememberSaveable { mutableStateOf(identity.back) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var imported by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var destination by rememberSaveable { mutableStateOf("Diaries") }
    fun finish() {
        val candidates = imported + identity.coverFiles()
        book.queue { book.releaseUnused(candidates) }
        if (destination == "Diaries") onClose() else navigate(destination)
    }
    val past = remember { mutableStateListOf<StudioState>() }
    val future = remember { mutableStateListOf<StudioState>() }
    val raw = if (reversed) back else front
    val design = remember(raw, reversed) { CoverDesign.read(raw, reversed) }
    val obj = design.layers.find { it.id == selected }
    fun snapshot() = StudioState(title, front, back)
    fun apply(state: StudioState) {
        title = state.title
        front = state.front
        back = state.back
    }
    fun record() {
        if (past.size >= 70) past.removeAt(0)
        past.add(snapshot())
        future.clear()
    }
    fun update(next: CoverDesign) {
        record()
        if (reversed) back = next.encode() else front = next.encode()
    }
    fun edit(layer: CoverLayer) {
        update(design.copy(layers = design.layers.map { if (it.id == layer.id) layer else it }))
    }
    fun add(layer: CoverLayer) {
        if (design.layers.size >= 200) {
            error = "This cover already has 200 layers."
            return
        }
        update(design.copy(layers = design.layers + layer))
        selected = layer.id
    }
    fun leave() {
        if (title != identity.title || front != identity.front || back != identity.back)
            discard = true
        else finish()
    }
    BackHandler { if (!busy) leave() }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        val photo = importCoverPhoto(book, uri)
                        imported = ArrayList(imported + photo.asset)
                        add(photo)
                    } catch (e: Exception) {
                        error = "Could not import this image."
                    } finally {
                        busy = false
                    }
                }
        }
    NavigationHeader("Library") { tab ->
        if (!busy) {
            destination = tab
            leave()
        }
    }
    Box(
        Modifier.fillMaxWidth().height(46.dp).paper("Blank", notebookBackground),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = {
                destination = "Diaries"
                leave()
            },
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Text(wording("‹ Library", "‹ 서재"))
        }
        Text(
            title,
            Modifier.fillMaxWidth(.60f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = uiSp(25),
        )
        Button(
            enabled = title.isNotBlank() && !busy,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        book.library.design(identity.id, title.trim(), front, back)
                        finish()
                    } catch (e: Exception) {
                        error = "Could not save this design."
                        busy = false
                    }
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 1.dp),
        ) {
            Text(wording("Done", "완료"))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .linen(
                    androidx.compose.ui.graphics.lerp(notebookBackground, Color(0xffc9baa7), .58f)
                )
        )
        Row(Modifier.fillMaxSize().padding(8.dp)) {
            BoxWithConstraints(
                Modifier.fillMaxHeight().weight(1.08f),
                contentAlignment = Alignment.Center,
            ) {
                val h = minOf(maxHeight * .82f, maxWidth * 1.01f)
                BookSurface(
                    book,
                    title,
                    design,
                    Modifier.offset(y = (-8).dp).size(h * .82f, h),
                    selected,
                    { selected = it },
                    ::edit,
                )
                Row(
                    Modifier.align(Alignment.BottomCenter)
                        .background(notebookBackground, CircleShape),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Action("undo-2", wording("Undo", "실행 취소"), past.isNotEmpty()) {
                        future.add(snapshot())
                        apply(past.removeAt(past.lastIndex))
                    }
                    TextButton(
                        onClick = {
                            reversed = false
                            selected = ""
                        }
                    ) {
                        Text(
                            if (!reversed) wording("✓ Front", "✓ 앞표지") else wording("Front", "앞표지")
                        )
                    }
                    TextButton(
                        onClick = {
                            reversed = true
                            selected = ""
                        }
                    ) {
                        Text(if (reversed) wording("✓ Back", "✓ 뒷표지") else wording("Back", "뒷표지"))
                    }
                    Action("redo-2", wording("Redo", "다시 실행"), future.isNotEmpty()) {
                        past.add(snapshot())
                        apply(future.removeAt(future.lastIndex))
                    }
                }
            }
            Column(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .shadow(3.dp, round)
                    .clip(round)
                    .paper("Blank", notebookBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                            wording("Cover", "표지"),
                            wording("Text", "글자"),
                            wording("Stickers", "스티커"),
                            wording("Photos", "사진"),
                        )
                        .forEachIndexed { i, label ->
                            Box(
                                Modifier.weight(1f)
                                    .padding(vertical = 5.dp)
                                    .height(32.dp)
                                    .clip(CircleShape)
                                    .background(if (tab == i) sage else ivory)
                                    .border(.6.dp, edge, CircleShape)
                                    .clickable { tab = i },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (tab == i) Color.White else ink)
                            }
                        }
                }
                HorizontalDivider(color = edge)
                key(tab, reversed) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when (tab) {
                            0 -> {
                                Title(wording("Material", "재질"), 17)
                                MaterialChoices(design.material) {
                                    update(design.copy(material = it))
                                }
                                HorizontalDivider(color = edge)
                                Title(wording("Color", "색상"), 17)
                                ColorChoices(design.color) { update(design.copy(color = it)) }
                                HorizontalDivider(color = edge)
                                Row {
                                    Column(Modifier.weight(1f)) {
                                        Title(wording("Border", "테두리"), 17)
                                        FlowRow {
                                            listOf("None", "Classic", "Simple", "Botanical")
                                                .forEachIndexed { i, b ->
                                                    val label =
                                                        wording(
                                                            b,
                                                            listOf("없음", "클래식", "심플", "보태니컬")[i],
                                                        )
                                                    Column(
                                                        Modifier.width(43.dp).clickable {
                                                            update(design.copy(border = b))
                                                        },
                                                        horizontalAlignment =
                                                            Alignment.CenterHorizontally,
                                                    ) {
                                                        Box(
                                                            Modifier.size(34.dp, 39.dp)
                                                                .clip(round)
                                                                .background(ivory)
                                                                .border(
                                                                    if (design.border == b) 1.5.dp
                                                                    else .5.dp,
                                                                    if (design.border == b) sage
                                                                    else edge,
                                                                    round,
                                                                )
                                                                .padding(4.dp)
                                                        ) {
                                                            if (b != "None")
                                                                Box(
                                                                    Modifier.fillMaxSize()
                                                                        .border(
                                                                            1.dp,
                                                                            Color(0xffac9363),
                                                                            Binding.corner,
                                                                        )
                                                                )
                                                            if (b == "Classic")
                                                                Box(
                                                                    Modifier.fillMaxSize()
                                                                        .padding(2.dp)
                                                                        .border(
                                                                            .6.dp,
                                                                            Color(0xffac9363),
                                                                            Binding.corner,
                                                                        )
                                                                )
                                                            if (b == "Botanical")
                                                                ArtImage(
                                                                    "leaf",
                                                                    Modifier.align(
                                                                            Alignment.BottomStart
                                                                        )
                                                                        .size(14.dp),
                                                                )
                                                            if (design.border == b)
                                                                Glyph("check", size = 13)
                                                        }
                                                        Text(
                                                            label,
                                                            fontSize = uiSp(12),
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Title(wording("Ribbon", "리본"), 17)
                                        FlowRow {
                                            (listOf(0) +
                                                    listOf(
                                                        coverColors[0],
                                                        coverColors[1],
                                                        coverColors[3],
                                                        coverColors[6],
                                                    ))
                                                .forEachIndexed { i, c ->
                                                    val label =
                                                        wording(
                                                            listOf(
                                                                "None",
                                                                "Sage",
                                                                "Cream",
                                                                "Pink",
                                                                "Blue",
                                                            )[i],
                                                            listOf("없음", "세이지", "크림", "핑크", "블루")[i],
                                                        )
                                                    Box(
                                                        Modifier.size(31.dp)
                                                            .clickable {
                                                                update(design.copy(ribbon = c))
                                                            }
                                                            .semantics {
                                                                contentDescription = label
                                                            },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Canvas(Modifier.size(16.dp, 26.dp)) {
                                                            val flag =
                                                                androidx.compose.ui.graphics
                                                                    .Path()
                                                                    .apply {
                                                                        moveTo(0f, 0f)
                                                                        lineTo(size.width, 0f)
                                                                        lineTo(
                                                                            size.width,
                                                                            size.height,
                                                                        )
                                                                        lineTo(
                                                                            size.width * .5f,
                                                                            size.height * .78f,
                                                                        )
                                                                        lineTo(0f, size.height)
                                                                        this.close()
                                                                    }
                                                            drawPath(
                                                                flag,
                                                                if (c == 0) ivory else Color(c),
                                                            )
                                                            drawPath(
                                                                flag,
                                                                edge,
                                                                style =
                                                                    androidx.compose.ui.graphics
                                                                        .drawscope
                                                                        .Stroke(1f),
                                                            )
                                                        }
                                                        if (c == design.ribbon)
                                                            Glyph("check", size = 14)
                                                    }
                                                }
                                        }
                                    }
                                }
                                Title(wording("Book proportions", "책 비율"), 16)
                                Slider(
                                    design.proportion,
                                    { update(design.copy(proportion = it)) },
                                    valueRange = .65f..1.9f,
                                )
                                Title(wording("Binding thickness", "제본 두께"), 16)
                                Slider(
                                    design.thickness,
                                    { update(design.copy(thickness = it)) },
                                    valueRange = 0.015f..0.09f,
                                )
                            }
                            1 -> {
                                OutlinedTextField(
                                    title,
                                    {
                                        record()
                                        title = it.take(180)
                                    },
                                    label = { Text(wording("Book title", "책 제목")) },
                                    singleLine = true,
                                )
                                TextButton(
                                    onClick = {
                                        add(
                                            CoverLayer(
                                                kind = "text",
                                                asset = title,
                                                width = .66f,
                                                ratio = 3.2f,
                                            )
                                        )
                                    }
                                ) {
                                    Text(wording("Add lettering", "글자 추가"))
                                }
                                if (obj != null && obj.kind in listOf("text", "title")) {
                                    if (obj.kind == "text")
                                        OutlinedTextField(
                                            obj.asset,
                                            { edit(obj.copy(asset = it.take(500))) },
                                            label = { Text(wording("Lettering", "글자")) },
                                        )
                                    FlowRow {
                                        listOf("몽글동심", "Handwriting", "Serif", "Sans").forEach { f
                                            ->
                                            StudioChoice(f, obj.font == f) {
                                                edit(obj.copy(font = f))
                                            }
                                        }
                                    }
                                    ColorChoices(obj.color) { edit(obj.copy(color = it)) }
                                    Row {
                                        listOf("Left", "Center", "Right").forEach { a ->
                                            StudioChoice(a, obj.align == a) {
                                                edit(obj.copy(align = a))
                                            }
                                        }
                                    }
                                }
                            }
                            2,
                            3 -> {
                                if (tab == 3)
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { picker.launch("image/*") },
                                    ) {
                                        Text(wording("Import photograph", "사진 가져오기"))
                                    }
                                FlowRow {
                                    LocalArtwork.current
                                        .filter {
                                            if (tab == 2) it.category in listOf("stickers", "tapes")
                                            else it.category == "frames"
                                        }
                                        .forEach { a ->
                                            Box(
                                                Modifier.size(66.dp)
                                                    .semantics { contentDescription = a.en }
                                                    .clickable {
                                                        add(
                                                            CoverLayer(
                                                                kind = "art",
                                                                asset = a.id,
                                                                ratio = a.ratio,
                                                            )
                                                        )
                                                    }
                                                    .padding(4.dp)
                                            ) {
                                                ArtImage(a.id, Modifier.fillMaxSize())
                                            }
                                        }
                                }
                            }
                        }
                        if (tab != 0 || obj != null) {
                            Title(wording("Layers", "레이어"), 16)
                            FlowRow {
                                design.layers.forEachIndexed { i, l ->
                                    StudioChoice(
                                        "${i+1} ${if(l.kind=="title") title else l.asset.take(14)}",
                                        l.id == selected,
                                    ) {
                                        selected = l.id
                                    }
                                }
                            }
                            if (obj != null) {
                                LayerControls(obj, ::edit)
                                FlowRow {
                                    TextButton(
                                        onClick = {
                                            add(
                                                obj.copy(
                                                    id = uid(),
                                                    x = (obj.x + .04f).coerceAtMost(1f),
                                                )
                                            )
                                        }
                                    ) {
                                        Text(wording("Duplicate", "복제"))
                                    }
                                    for (delta in listOf(-1, 1)) {
                                        val index = design.layers.indexOf(obj)
                                        TextButton(
                                            enabled = index + delta in design.layers.indices,
                                            onClick = {
                                                val layers = design.layers.toMutableList()
                                                layers.removeAt(index)
                                                layers.add(index + delta, obj)
                                                update(design.copy(layers = layers))
                                            },
                                        ) {
                                            Text(
                                                if (delta < 0) wording("Backward", "뒤로")
                                                else wording("Forward", "앞으로")
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            update(design.copy(layers = design.layers - obj))
                                            selected = ""
                                        }
                                    ) {
                                        Text(wording("Delete", "삭제"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = {
                discard = false
                destination = "Diaries"
            },
            title = { Title(wording("Discard cover changes?", "표지 변경을 취소할까요?")) },
            confirmButton = { TextButton(onClick = ::finish) { Text(wording("Discard", "버리기")) } },
            dismissButton = {
                TextButton(
                    onClick = {
                        discard = false
                        destination = "Diaries"
                    }
                ) {
                    Text(wording("Keep editing", "계속 편집"))
                }
            },
        )
    if (error.isNotEmpty())
        AlertDialog(
            onDismissRequest = { error = "" },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { error = "" }) { Text("OK") } },
        )
}

@Composable
internal fun StudioChoice(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
    ) {
        Text(if (active) "✓ $label" else label)
    }
}

@Composable
internal fun LayerControls(layer: CoverLayer, change: (CoverLayer) -> Unit) {
    listOf(
            wording("Horizontal position", "가로 위치") to layer.x,
            wording("Vertical position", "세로 위치") to layer.y,
            wording("Size", "크기") to layer.width,
            wording("Rotation", "회전") to layer.angle,
            wording("Opacity", "불투명도") to layer.opacity,
        )
        .forEachIndexed { i, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(.45f))
                Slider(
                    value,
                    { v ->
                        change(
                            when (i) {
                                0 -> layer.copy(x = v)
                                1 -> layer.copy(y = v)
                                2 -> layer.copy(width = v)
                                3 -> layer.copy(angle = v)
                                else -> layer.copy(opacity = v)
                            }
                        )
                    },
                    Modifier.weight(1f).semantics { contentDescription = label },
                    valueRange =
                        when (i) {
                            2 -> .04f..2f
                            3 -> -360f..360f
                            else -> 0f..1f
                        },
                )
            }
        }
}
