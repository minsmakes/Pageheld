package app.pageheld

internal fun DiaryLayout.contentCount(textSheets: Int) =
    maxOf(
        1,
        textSheets,
        sheetIds.size,
        1 + (lines.maxOfOrNull { it.sheet } ?: -1),
        1 + (prints.values.maxOfOrNull { it.sheet } ?: -1),
    )

internal fun DiaryLayout.withSheetCount(count: Int): DiaryLayout {
    if (count <= sheetIds.size) return this
    return copy(sheetIds = sheetIds + List(count - sheetIds.size) { uid() })
}

internal data class SheetRemoval(
    val index: Int,
    val page: Page,
    val layout: DiaryLayout,
    val slices: List<TextSlice>,
)

internal fun hasSheetContent(
    page: Page,
    art: DiaryLayout,
    slices: List<TextSlice>,
    index: Int,
): Boolean {
    val text = slices.getOrNull(index)
    return text != null && page.body.substring(text.start, text.end).isNotEmpty() ||
        art.lines.any { it.sheet == index } ||
        art.prints.values.any { it.sheet == index } || !art.decorations[art.sheetIds.getOrNull(index)].isNullOrEmpty()
}

// Reify the surviving measured boundaries once. This also handles a sheet cut from a
// continuous v1.7 paragraph, where removing a form feed alone would target the wrong text.
internal fun removeSheet(
    page: Page,
    art: DiaryLayout,
    slices: List<TextSlice>,
    index: Int,
    linkedFiles: Set<String>,
): Pair<Page, DiaryLayout> {
    val size = art.contentCount(slices.size)
    require(index in 0 until size)
    val ids = art.withSheetCount(size).sheetIds.toMutableList().apply { removeAt(index) }
    val parts =
        slices.mapIndexedNotNull { i, sl ->
            if (i == index) null else page.body.substring(sl.start, sl.end)
        }
    val body = parts.joinToString("\u000c")
    val removed = art.prints.filterValues { it.sheet == index }.keys
    val remaining =
        art.prints
            .filterKeys { it !in removed }
            .mapValues { (_, p) -> if (p.sheet > index) p.copy(sheet = p.sheet - 1) else p }
    val lines =
        art.lines.mapNotNull { l ->
            when {
                l.sheet == index -> null
                l.sheet > index -> l.copy(sheet = l.sheet - 1)
                else -> l
            }
        }
    val next =
        page.copy(
            body = body,
            media = page.files().filter { it !in removed || it in linkedFiles }.joinToString("\n"),
            mediaPositions =
                page.mediaPositionsByFile().filterKeys { it !in removed }.encodeMediaPositions(),
        )
    val dest = index.coerceAtMost((size - 2).coerceAtLeast(0))
    val cursor = parts.take(dest).sumOf { it.length + 1 }.coerceAtMost(body.length)
    return next to
        art.copy(
            decorations = art.decorations - listOfNotNull(art.sheetIds.getOrNull(index)).toSet(),
            sheetIds = ids.ifEmpty { listOf(uid()) },
            sheet = dest,
            cursor = cursor,
            lines = lines,
            prints = remaining,
        )
}
