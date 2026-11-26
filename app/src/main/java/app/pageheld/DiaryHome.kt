package app.pageheld

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import java.io.File
import java.time.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun wording(en: String, ko: String) =
    if (LocalConfiguration.current.locales[0].language == "ko") ko else en

@Composable
internal fun displayStamp(ms: Long, skeleton: String) =
    stamp(ms, skeleton, LocalConfiguration.current.locales[0])

@Composable
private fun localToday(): LocalDate {
    val context = LocalContext.current
    var day by remember { mutableStateOf(LocalDate.now()) }
    var clock by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    day = LocalDate.now()
                    clock++
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(day, clock) {
        val zone = ZoneId.systemDefault()
        delay(
            (day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() -
                    System.currentTimeMillis())
                .coerceAtLeast(1)
        )
        day = LocalDate.now()
    }
    return day
}

@Composable
internal fun DiaryHome(
    book: Notebook,
    pages: List<Page>,
    chapters: List<Chapter>,
    places: List<DiaryLocation>,
    prefs: Map<String, String>,
) {
    val day = localToday()
    var section by rememberSaveable { mutableStateOf("Today") }
    var shelf by rememberSaveable {
        mutableStateOf(if (prefs["openingCover"] == "false") "Index" else "Cover")
    }
    var chapter by rememberSaveable { mutableStateOf("") }
    var entry by rememberSaveable { mutableStateOf(pages.firstOrNull { it.id == prefs["resumeEntry"] && it.draft }?.id.orEmpty()) }
    var newTemplate by rememberSaveable { mutableStateOf("Free Writing") }
    var entryTime by rememberSaveable { mutableLongStateOf(0L) }
    var attachment by rememberSaveable { mutableStateOf("") }
    var bookmarks by rememberSaveable { mutableStateOf(false) }
    var profileReturn by rememberSaveable { mutableStateOf("Today") }
    val search = rememberSaveable(saver = JournalSearch.saver) { JournalSearch() }
    val editing = section == "Diaries" && entry.isNotEmpty()
    val motion = systemMotionEnabled() && prefs["reduceMotion"] != "true"
    fun edit(page: Page, file: String = "") {
        book.setting("resumeEntry",page.id)
        entry = page.id
        newTemplate = page.template
        entryTime = page.created
        attachment = file
        section = "Diaries"
    }
    fun navigate(tab: String) {
        if (tab == "Profile") profileReturn = section
        section = tab
    }
    fun back() {
        when {
            section == "Profile" -> section = profileReturn
            section == "Diaries" && shelf in listOf("All", "Manage", "Chapter") -> shelf = "Index"
            section == "Diaries" && shelf == "Index" -> shelf = "Cover"
            else -> section = "Today"
        }
    }
    BackHandler(!editing && section != "Today") { back() }
    Column(
        Modifier.fillMaxSize()
            .background(ivory)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
    ) {
        if (editing) {
            val seed =
                pages.find { it.id == entry }
                    ?: Page(
                        id = entry,
                        created = entryTime,
                        draft = true,
                        template = newTemplate,
                        chapter = chapter.takeIf { id -> chapters.any { it.id == id } },
                    )
            key(entry) {
                // A rotation can recreate the editor before Room emits the preceding pen-up save.
                val entryId=entry
                val restored by produceState<Page?>(null,entryId) {
                    val result=kotlinx.coroutines.CompletableDeferred<Page?>()
                    book.queue {
                        try { result.complete(book.dao.page(entryId)) }
                        catch(e: Exception) { result.complete(seed);book.errors.emit("Couldn’t reload the entry. Showing the current copy.") }
                    }
                    value=result.await() ?: seed
                }
                val ready=restored
                if(ready!=null) DiaryEditor(
                    ready,
                    book,
                    chapters,
                    places,
                    prefs,
                    motion,
                    attachment,
                    navigate = { navigate(it) },
                    close = { saved ->
                        book.setting("resumeEntry","")
                        entry = ""
                        attachment = ""
                        section = "Diaries"
                        chapter = saved.chapter ?: monthKey(saved)
                        shelf = "Chapter"
                    },
                )
            }
        } else {
            NavigationHeader(section, ::navigate)
            when (section) {
                "Today" ->
                    TodaySheet(
                        book,
                        pages,
                        prefs["owner"].orEmpty(),
                        day,
                        { p ->
                            shelf = "All"
                            chapter = ""
                            edit(p)
                        },
                        {
                            entry = ""
                            book.setting("resumeEntry","")
                            bookmarks = false
                            chapter = ""
                            shelf = "All"
                            section = "Diaries"
                        },
                        {
                            val current =
                                pages.firstOrNull {
                                    date(it.created).toLocalDate() == day &&
                                        it.template == "Daily Diary"
                                }
                                    ?: pages.firstOrNull {
                                        date(it.created).toLocalDate() == day &&
                                            it.template == "Free Writing"
                                    }
                            chapter = ""
                            edit(
                                current
                                    ?: Page(
                                        id = "daily-$day",
                                        template = "Daily Diary",
                                        draft = true,
                                    )
                            )
                        },
                    )
                "Moments" ->
                    Box(Modifier.fillMaxSize().padding(10.dp).clip(round).paper("Blank")) {
                        Memories(
                            book,
                            pages,
                            places,
                            open = { p -> edit(p) },
                            openMedia = { p, f -> edit(p, f) },
                        )
                    }
                "Profile" -> You(book, prefs, entries = pages)
                else -> {
                    val scope = rememberCoroutineScope()
                    val turn = remember { BookTurn(scope) { shelf = it.section } }
                    SideEffect { turn.motion = motion }
                    fun move(to: String, sign: Int = 1) {
                        if (shelf != to) turn.jump(Leaf(to), sign)
                    }
                    TurningLeaves(Leaf(shelf), turn, true, false, { null }, inset = 0.dp) { leaf, _
                        ->
                        when (leaf.section) {
                            "Cover" ->
                                DiaryCover(prefs["owner"].orEmpty(), day.year, motion) {
                                    shelf = "Index"
                                }
                            "Index" ->
                                ChapterIndex(
                                    book,
                                    pages,
                                    chapters,
                                    select = {
                                        chapter = it
                                        move("Chapter")
                                    },
                                    manage = {
                                        chapter = ""
                                        move("Manage")
                                    },
                                    all = {
                                        bookmarks = it
                                        move("All")
                                    },
                                    newStory = {
                                        chapter = ""
                                        edit(Page(draft = true))
                                    },
                                )
                            "All" ->
                                Journal(
                                    book,
                                    pages,
                                    places,
                                    bookmarks,
                                    { bookmarks = false },
                                    {
                                        chapter = ""
                                        edit(Page(draft = true))
                                    },
                                    search,
                                ) { p, _ ->
                                    edit(p)
                                }
                            "Manage" ->
                                Chapters(
                                    book,
                                    pages,
                                    chapters,
                                    chapter.ifEmpty { null },
                                    { chapter = it.orEmpty() },
                                    { edit(it) },
                                )
                            else ->
                                Column(Modifier.fillMaxSize().paper("Blank")) {
                                    Header(
                                        chapters.find { it.id == chapter }?.name
                                            ?: monthTitle(pages, chapter),
                                        {
                                            Action("arrow-left", "Chapter index") {
                                                move("Index", -1)
                                            }
                                        },
                                    ) {
                                        Action("plus", "New story") {
                                            edit(
                                                Page(
                                                    draft = true,
                                                    chapter =
                                                        chapter.takeIf { id ->
                                                            chapters.any { it.id == id }
                                                        },
                                                )
                                            )
                                        }
                                    }
                                    LazyColumn(contentPadding = PaddingValues(18.dp)) {
                                        items(
                                            pages.filter {
                                                it.chapter == chapter ||
                                                    it.chapter == null && monthKey(it) == chapter
                                            },
                                            key = { it.id },
                                        ) {
                                            RecentRow(book, it) { edit(it) }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

private fun monthKey(p: Page) = "month:" + YearMonth.from(date(p.created))

private fun monthTitle(pages: List<Page>, key: String) =
    pages.firstOrNull { monthKey(it) == key }?.let { stamp(it.created, "yMMMM") } ?: "My chapters"

@Composable
private fun NavigationHeader(section: String, select: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(57.dp).background(ivory)) {
        val compact = maxWidth < 610.dp
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = if (compact) 8.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Botanical(Modifier.size(if (compact) 23.dp else 31.dp))
            if (!compact)
                Text("Pageheld.", Modifier.padding(start = 6.dp), fontSize = uiSp(25), color = ink)
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 16.dp),
        ) {
            tabs.forEach { (name, icon) ->
                val active = name == section
                Row(
                    Modifier.height(42.dp)
                        .clip(CircleShape)
                        .clickable { select(name) }
                        .padding(vertical = 4.dp)
                        .background(
                            if (active && name == "Today") pale
                            else if (active) sage else Color.Transparent,
                            CircleShape,
                        )
                        .padding(horizontal = if (compact) 8.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(icon, tint = if (active && name != "Today") ivory else ink, size = 15)
                    Text(
                        name,
                        Modifier.padding(start = 4.dp),
                        color = if (active && name != "Today") ivory else ink,
                        fontSize = uiSp(if (compact) 14 else 18),
                    )
                }
            }
        }
        Box(
            Modifier.align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(38.dp)
                .border(.6.dp, edge, CircleShape)
                .background(Color(0xfff3eadb), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Action("user-round", "Profile") { select("Profile") }
        }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = edge)
    }
}

@Composable
private fun RecentRow(book: Notebook, p: Page, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = click)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = Modifier.size(62.dp, 40.dp).clip(RoundedCornerShape(5.dp))
        val f = p.files().firstOrNull()
        if (f != null) Thumbnail(File(mediaDir(book), f), thumb)
        else
            Box(thumb.background(pale), contentAlignment = Alignment.Center) {
                Botanical(Modifier.size(23.dp))
            }
        Text(
            p.heading(),
            Modifier.weight(1f).padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = uiSp(19),
        )
        Note(displayStamp(p.created, "MMMdEEE"))
        Glyph("chevron-right", size = 17)
    }
    HorizontalDivider(color = edge, thickness = .6.dp)
}

@Composable
private fun TodaySheet(
    book: Notebook,
    pages: List<Page>,
    owner: String,
    day: LocalDate,
    open: (Page) -> Unit,
    all: () -> Unit,
    write: () -> Unit,
) {
    val accent = sage
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Color(0xffe9e0cf))
            .drawBehind {
                val step = 18.dp.toPx()
                var x = 0f
                var y = size.height * .72f
                while (x < size.width) {
                    drawRect(
                        accent.copy(alpha = .13f),
                        Offset(x, y),
                        androidx.compose.ui.geometry.Size(step / 2, size.height - y),
                    )
                    x += step
                }
                while (y < size.height) {
                    drawRect(
                        accent.copy(alpha = .13f),
                        Offset(0f, y),
                        androidx.compose.ui.geometry.Size(size.width, step / 2),
                    )
                    y += step
                }
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < 560.dp
        Column(
            Modifier.fillMaxSize()
                .shadow(4.dp, round)
                .clip(round)
                .paper("Blank")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (compact) 100.dp else 114.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Title(
                        if (owner.isBlank())
                            wording("What story will\nyou keep today?", "오늘도,\n나만의 이야기를\n남겨볼까요?")
                        else
                            wording(
                                "Hello, $owner.\nWhat will you keep today?",
                                "$owner 님,\n오늘은 어떤 하루였나요?",
                            ),
                        if (compact) 28 else 38,
                    )
                    TextButton(onClick = write) {
                        Glyph("pencil", size = 16)
                        Text(wording(" Today’s diary", " 오늘의 일기"))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        displayStamp(
                            day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            "yMMMEd",
                        ),
                        Modifier.shadow(1.dp, round).background(pale, round).padding(9.dp),
                        fontSize = uiSp(17),
                    )
                    Botanical(Modifier.padding(top = 8.dp).size(if (compact) 54.dp else 85.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxWidth().border(1.dp, edge, round).clip(round).background(ivory)
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xffefe6d4)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph("book-open", size = 20)
                    Text(
                        wording(" Recent writings", " 최근 기록"),
                        Modifier.weight(1f),
                        fontSize = uiSp(20),
                    )
                    TextButton(onClick = all) { Text(wording("View all  ›", "전체 보기  ›")) }
                }
                if (pages.isEmpty())
                    Empty(
                        wording("Your first page", "첫 번째 기록"),
                        wording("Begin with today’s diary.", "오늘의 일기를 써 보세요."),
                    )
                pages.take(3).forEach { RecentRow(book, it) { open(it) } }
            }
            pages
                .firstOrNull {
                    date(it.created).let { d ->
                        d.year < day.year && d.month == day.month && d.dayOfMonth == day.dayOfMonth
                    }
                }
                ?.let { memory ->
                    TextButton(onClick = { open(memory) }) {
                        Text(wording("On this day · ", "지난 오늘 · ") + memory.heading())
                    }
                }
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(2) {
                Box(
                    Modifier.offset(y = (-4).dp)
                        .size(6.dp, 15.dp)
                        .shadow(1.dp, CircleShape)
                        .background(Color(0xffd1b38a), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun DiaryCover(owner: String, year: Int, motion: Boolean, open: () -> Unit) {
    val palette = LocalNotebookPalette.current
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    fun start() {
        if (opening) return
        opening = true
        scope.launch {
            if (motion) rotation.animateTo(-88f, tween(Binding.coverMillis))
            open()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Desk()
        val h = minOf(maxHeight * .92f, maxWidth * 1.05f)
        Box(Modifier.size(h * .85f, h)) {
            Stack(Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        rotationY = rotation.value
                        transformOrigin = TransformOrigin(0f, .5f)
                        cameraDistance = 18f * density
                    }
                    .shadow(5.dp, round)
                    .clip(round)
                    .linen(palette.cloth)
                    .clickable(onClickLabel = "Open cover") { start() }
                    .drawBehind {
                        val inset = 10.dp.toPx()
                        drawRoundRect(
                            palette.accent,
                            Offset(inset, inset),
                            androidx.compose.ui.geometry.Size(
                                size.width - 2 * inset,
                                size.height - 2 * inset,
                            ),
                            androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                            style =
                                Stroke(
                                    1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f)),
                                ),
                        )
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(.19f),
                                    Color.Transparent,
                                    Color.Black.copy(.18f),
                                    Color.Transparent,
                                ),
                                endX = size.width * .10f,
                            )
                        )
                    }
            ) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Botanical(Modifier.size(h * .14f), palette.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Pageheld.",
                        fontSize = (h.value * .088f).coerceIn(26f, 54f).sp,
                        color = ink,
                    )
                    Text(
                        owner.ifBlank { wording("A collection of my stories", "나의 이야기 모음") },
                        fontSize = uiSp(16),
                    )
                }
                Text(
                    year.toString(),
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                    fontSize = uiSp(22),
                )
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = h * .068f)
                        .width(h * .034f)
                        .fillMaxHeight()
                        .shadow(2.dp)
                        .linen(palette.accent)
                )
            }
        }
        Button(
            onClick = ::start,
            colors = ButtonDefaults.buttonColors(containerColor = pale, contentColor = ink),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Text(wording("Open cover  ›", "표지 열기  ›"))
        }
    }
}

@Composable
internal fun Desk() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.diary_desk),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color(0xfff7e7cb).copy(alpha = .22f)))
    }
}

@Composable
private fun ChapterIndex(
    book: Notebook,
    pages: List<Page>,
    chapters: List<Chapter>,
    select: (String) -> Unit,
    manage: () -> Unit,
    all: (Boolean) -> Unit,
    newStory: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(9.dp)) {
        val wide = maxWidth >= 580.dp
        val locale = LocalConfiguration.current.locales[0]
        Stack(Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxSize()
                .padding(8.dp)
                .clip(FoldedTrim)
                .paper("Blank")
                .bindingShading(wide)
        ) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.rotate(-2f)
                            .shadow(1.dp, round)
                            .paper("Blank")
                            .padding(horizontal = 15.dp, vertical = 6.dp)
                    ) {
                        Title(wording("My chapters", "나의 챕터들"), 26)
                    }
                    Spacer(Modifier.weight(1f))
                    Action("layers", "Manage chapters", click = manage)
                }
                LazyColumn(Modifier.weight(1f)) {
                    val groups =
                        chapters.map {
                            Triple(it.id, it.name, pages.count { p -> p.chapter == it.id })
                        } +
                            pages
                                .filter { it.chapter == null }
                                .groupBy(::monthKey)
                                .map { (key, rows) ->
                                    Triple(
                                        key,
                                        stamp(rows.first().created, "yMMMM", locale),
                                        rows.size,
                                    )
                                }
                    if (groups.isEmpty())
                        item {
                            Empty(
                                wording("An open chapter", "열린 챕터"),
                                wording(
                                    "Your first story starts this month’s chapter.",
                                    "새 이야기를 쓰면 이달의 챕터가 열려요.",
                                ),
                            )
                        }
                    items(groups, key = { it.first }) { (id, name, count) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { select(id) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("📁")
                            Text(
                                name,
                                Modifier.weight(1f).padding(start = 9.dp),
                                fontSize = uiSp(21),
                            )
                            Note("($count)")
                            Glyph("chevron-right", size = 17)
                        }
                        HorizontalDivider(color = edge)
                    }
                }
                Row {
                    TextButton(onClick = { all(false) }) { Text(wording("All writings", "전체 기록")) }
                    Action("bookmark", "Bookmarks") { all(true) }
                }
                if (!wide) Button(onClick = newStory) { Text(wording("+ New story", "+ 새 이야기")) }
            }
            if (wide)
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(18.dp)) {
                    val printSize = minOf(maxWidth * .63f, maxHeight * .50f)
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val photo =
                            pages.firstNotNullOfOrNull { p ->
                                p.files().firstOrNull { it.endsWith(".photo") }
                            }
                        Box(Modifier.size(printSize)) {
                            MediaMount(Modifier.fillMaxSize().rotate(5f), taped = true) {
                                if (photo != null)
                                    Thumbnail(File(mediaDir(book), photo), Modifier.fillMaxSize())
                                else
                                    Box(
                                        Modifier.fillMaxSize().linen(pale),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Botanical(Modifier.fillMaxSize(.56f))
                                    }
                            }
                            Botanical(
                                Modifier.align(Alignment.BottomEnd).offset(12.dp, 9.dp).size(48.dp),
                                sage,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            wording(
                                "Even the ordinary days\nare stories worth keeping. ♡",
                                "지나온 하루들도\n소중한 이야기니까 ♡",
                            ),
                            fontSize = uiSp(19),
                        )
                    }
                    Button(onClick = newStory, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Text(wording("+ New story", "+ 새 이야기"))
                    }
                }
        }
    }
}
