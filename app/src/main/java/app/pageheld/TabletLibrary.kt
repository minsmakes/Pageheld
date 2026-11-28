package app.pageheld

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.sin
import kotlinx.coroutines.*

internal val LocalBook = staticCompositionLocalOf<LibraryBook?> { null }
internal val LocalTabletLibrary = staticCompositionLocalOf { false }

internal fun shelfCount(count: Int) = ((count + 8) / 9).coerceAtLeast(1)

@Composable
internal fun TabletLibrary(book: Notebook, prefs: Map<String, String>) {
    val books by remember(book) { book.library.books() }.collectAsState(initial = null)
    val catalog by
        produceState<List<Artwork>>(emptyList(), book) {
            value = withContext(Dispatchers.IO) { readCatalog(book.context) }
        }
    val all = books ?: return
    var active by rememberSaveable { mutableStateOf(prefs["activeBook"].orEmpty()) }
    var screen by rememberSaveable { mutableStateOf("Library") }
    var create by rememberSaveable { mutableStateOf(false) }
    var management by rememberSaveable { mutableStateOf(false) }
    var managedId by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val current = all.find { it.id == active } ?: all.firstOrNull()
    val managed = all.find { it.id == managedId }
    fun select(b: LibraryBook, next: String) {
        active = b.id
        book.setting("activeBook", b.id)
        screen = next
    }
    fun library() {
        screen = "Library"
    }
    fun navigate(tab: String) {
        when {
            tab == "Diaries" -> library()
            current == null && tab != "Profile" -> create = true
            else -> screen = tab
        }
    }
    BackHandler(screen != "Library") { library() }
    LaunchedEffect(book) {
        book.errors.collect {
            android.widget.Toast.makeText(book.context, it, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    CompositionLocalProvider(
        LocalTabletLibrary provides true,
        LocalArtwork provides catalog,
        LocalBook provides current,
    ) {
        if (screen in listOf("Library", "Studio") || current == null) {
            Column(
                Modifier.fillMaxSize()
                    .background(notebookBackground)
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            ) {
                if (screen != "Studio")
                    NavigationHeader(if (screen == "Profile") "Profile" else "Library", ::navigate)
                when {
                    screen == "Studio" && current != null ->
                        key(current.id) { BookStudio(book, current, ::library, ::navigate) }
                    screen == "Profile" -> You(book, prefs)
                    else ->
                        Bookshelf(
                            book,
                            all,
                            prefs,
                            { create = true },
                            { management = true },
                            { select(it, "Diaries") },
                            { managedId = it.id },
                        )
                }
            }
        } else {
            key(current.id, screen) {
                val pages by
                    remember { book.library.pages(current.id) }.collectAsState(initial = null)
                val chapters by
                    remember { book.library.chapters(current.id) }.collectAsState(initial = null)
                val places by
                    remember { book.library.locations(current.id) }.collectAsState(initial = null)
                if (pages != null && chapters != null && places != null)
                    DiaryHome(
                        book,
                        pages.orEmpty(),
                        chapters.orEmpty(),
                        places.orEmpty(),
                        prefs,
                        screen,
                        ::library,
                    )
            }
        }
        if (create)
            NewBookDialog({ create = false }) { title, design ->
                val b = LibraryBook(title = title, front = design.encode())
                book.queue {
                    book.library.append(b)
                    withContext(Dispatchers.Main) {
                        create = false
                        select(b, "Studio")
                    }
                }
            }
        if (management)
            AlertDialog(
                onDismissRequest = { management = false },
                title = { Title(wording("My books", "나의 책")) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        all.forEach { b ->
                            TextButton(
                                onClick = {
                                    managedId = b.id
                                    management = false
                                }
                            ) {
                                Text(b.title)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { management = false }) { Text(wording("Close", "닫기")) }
                },
            )
        if (managed != null) {
            if (deleting)
                AlertDialog(
                    onDismissRequest = { deleting = false },
                    title = { Title(wording("Delete book?", "책을 삭제할까요?")) },
                    text = {
                        Text(
                            wording(
                                "Delete “${managed.title}” with its entries, chapters, places and cover designs?",
                                "‘${managed.title}’의 일기, 챕터, 장소와 표지를 삭제할까요?",
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                book.queue { book.deleteBook(managed.id) }
                                managedId = ""
                                deleting = false
                            }
                        ) {
                            Text(wording("Delete", "삭제"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleting = false }) { Text(wording("Cancel", "취소")) }
                    },
                )
            else
                AlertDialog(
                    onDismissRequest = { managedId = "" },
                    title = { Title(managed.title) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            TextButton(
                                onClick = {
                                    managedId = ""
                                    select(managed, "Studio")
                                }
                            ) {
                                Text(wording("Customize or rename", "표지 꾸미기 및 이름 변경"))
                            }
                            TextButton(
                                onClick = {
                                    book.queue {
                                        book.library.append(
                                            managed.copy(
                                                id = uid(),
                                                title = managed.title + " (2)",
                                                created = System.currentTimeMillis(),
                                                modified = System.currentTimeMillis(),
                                                readingPage = "",
                                            )
                                        )
                                    }
                                    managedId = ""
                                }
                            ) {
                                Text(wording("Duplicate design", "디자인 복제"))
                            }
                            listOf(
                                    -9 to wording("Previous shelf page", "이전 책장"),
                                    -1 to wording("Move earlier", "앞으로 이동"),
                                    1 to wording("Move later", "뒤로 이동"),
                                    9 to wording("Next shelf page", "다음 책장"),
                                )
                                .forEach { (delta, label) ->
                                    val i = all.indexOf(managed)
                                    TextButton(
                                        enabled = if (delta < 0) i > 0 else i < all.lastIndex,
                                        onClick = {
                                            book.queue { book.library.move(managed.id, delta) }
                                            managedId = ""
                                        },
                                    ) {
                                        Text(label)
                                    }
                                }
                            TextButton(onClick = { deleting = true }) {
                                Text(wording("Delete book", "책 삭제"))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { managedId = "" }) { Text(wording("Close", "닫기")) }
                    },
                )
        }
    }
}

@Composable
private fun Bookshelf(
    book: Notebook,
    books: List<LibraryBook>,
    prefs: Map<String, String>,
    create: () -> Unit,
    manage: () -> Unit,
    open: (LibraryBook) -> Unit,
    options: (LibraryBook) -> Unit,
) {
    val count = shelfCount(books.size)
    val pager =
        rememberPagerState(
            initialPage = (prefs["shelfPage"]?.toIntOrNull() ?: 0).coerceIn(0, count - 1)
        ) {
            count
        }
    val motion = systemMotionEnabled() && prefs["reduceMotion"] != "true"
    val scope = rememberCoroutineScope()
    fun turn(to: Int) {
        scope.launch { if (motion) pager.animateScrollToPage(to) else pager.scrollToPage(to) }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { book.setting("shelfPage", it.toString()) }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .paper("Blank", androidx.compose.ui.graphics.lerp(notebookBackground, ivory, .53f))
    ) {
        val w = maxWidth
        val h = maxHeight
        Box(
            Modifier.align(Alignment.TopCenter).height(h * .135f),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(wording("My Library", "나의 서재"), 29)
                Botanical(Modifier.size(20.dp))
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(end = w * .07f).height(h * .135f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (books.isNotEmpty())
                Action("ellipsis-vertical", wording("Manage books", "책 관리"), click = manage)
            Button(
                onClick = create,
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 2.dp),
            ) {
                Text(wording("+ New Book", "+ 새 책"))
            }
        }
        HorizontalPager(
            pager,
            Modifier.fillMaxSize().padding(top = h * .125f, bottom = h * .055f),
        ) { page ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(3) { row ->
                    BoxWithConstraints(Modifier.fillMaxWidth(.78f).weight(1f)) {
                        val height = maxHeight
                        val lip = height * .095f
                        Row(
                            Modifier.fillMaxSize()
                                .padding(horizontal = maxWidth * .022f)
                                .padding(bottom = lip),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            repeat(3) { col ->
                                val b = books.getOrNull(page * 9 + row * 3 + col)
                                BoxWithConstraints(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    if (b != null) {
                                        val design = remember(b.front) { CoverDesign.read(b.front) }
                                        val width =
                                            minOf(
                                                maxWidth * .82f,
                                                maxHeight * .99f * design.proportion,
                                            )
                                        BookSurface(
                                            book,
                                            b.title,
                                            design,
                                            Modifier.size(width, width / design.proportion)
                                                .combinedClickable(
                                                    onLongClickLabel =
                                                        wording("Manage book", "책 관리"),
                                                    onLongClick = { options(b) },
                                                    onClick = { open(b) },
                                                )
                                                .semantics { contentDescription = b.title },
                                        )
                                    }
                                }
                            }
                        }
                        Shelf(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(lip))
                    }
                }
            }
        }
        if (books.isEmpty())
            Text(
                wording("Create your first book", "첫 번째 책을 만들어 보세요"),
                Modifier.align(Alignment.Center),
                color = ink,
            )
        for (direction in listOf(-1, 1)) {
            val previous = direction < 0
            val label =
                if (previous) wording("Previous shelf", "이전 책장") else wording("Next shelf", "다음 책장")
            val enabled = pager.currentPage + direction in 0 until count
            Box(
                Modifier.align(if (previous) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = w * .035f)
                    .size(48.dp)
                    .clickable(enabled = enabled, onClickLabel = label) {
                        turn(pager.currentPage + direction)
                    }
                    .semantics { contentDescription = label }
                    .padding(7.dp)
                    .shadow(2.dp, CircleShape)
                    .background(ivory, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(
                    if (previous) "arrow-left" else "chevron-right",
                    size = 19,
                    tint = if (enabled) ink else muted.copy(.55f),
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).height(h * .055f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (count <= 9)
                repeat(count) {
                    Box(
                        Modifier.size(if (it == pager.currentPage) 8.dp else 6.dp)
                            .background(if (it == pager.currentPage) sage else edge, CircleShape)
                    )
                }
            else Note("${pager.currentPage+1} / $count")
        }
    }
}

@Composable
private fun Shelf(modifier: Modifier) {
    Canvas(modifier.shadow(3.dp, RoundedCornerShape(2.dp))) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xffe6c9a2), Color(0xffefd4ac), Color(0xffc99e70), Color(0xffa57850))
            )
        )
        repeat(26) { i ->
            val path = Path()
            for (j in 0..60) {
                val x = j / 60f * size.width
                val y = (i + .65f * sin(j * .18f + i * 1.7f)) / 26f * size.height
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                Color(0xff805935).copy(.17f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(.75f),
            )
        }
    }
}

@Composable
internal fun OpeningBook(book: Notebook, identity: LibraryBook, motion: Boolean, open: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val complete by rememberUpdatedState(open)
    LaunchedEffect(identity.id) {
        if (motion) {
            delay(220)
            rotation.animateTo(-88f, tween(Binding.coverMillis))
        }
        complete()
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Desk()
        val h = minOf(maxHeight * .89f, maxWidth * .9f)
        Box(Modifier.size(h * .82f, h)) {
            Stack(Modifier.fillMaxSize())
            BookSurface(
                book,
                identity.title,
                remember(identity.front) { CoverDesign.read(identity.front) },
                Modifier.fillMaxSize().graphicsLayer {
                    rotationY = rotation.value
                    transformOrigin = TransformOrigin(0f, .5f)
                    cameraDistance = 18f * density
                },
            )
        }
    }
}
