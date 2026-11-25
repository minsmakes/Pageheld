package app.pageheld

import org.json.JSONArray
import org.json.JSONObject

internal data class NibPoint(val x: Float, val y: Float, val pressure: Float)

internal data class InkLine(
    val sheet: Int,
    val color: Int,
    val width: Float,
    val points: List<NibPoint>,
)

internal data class PrintPosition(
    val sheet: Int,
    val x: Float = .15f,
    val y: Float = .14f,
    val width: Float = .64f,
)

// Page.body owns the text. Automatic breaks are derived from text layout; form feeds mark explicit
// breaks.
// Ink and prints use page-local coordinates and never move into another entry during reflow.
internal data class DiaryLayout(
    val sheet: Int = 0,
    val cursor: Int = 0,
    val mode: String = "Type",
    val lines: List<InkLine> = emptyList(),
    val prints: Map<String, PrintPosition> = emptyMap(),
    val nib: Int = 0,
    val thickness: Float = 3f,
    val color: Int = 0xff434736.toInt(),
) {
    fun encode(): String {
        val ink = JSONArray()
        for (line in lines) {
            val points = JSONArray()
            for (p in line.points) points.put(p.x).put(p.y).put(p.pressure)
            ink.put(JSONArray().put(line.sheet).put(line.color).put(line.width).put(points))
        }
        val photos = JSONObject()
        prints.forEach { (name, p) ->
            photos.put(name, JSONArray().put(p.sheet).put(p.x).put(p.y).put(p.width))
        }
        return JSONObject()
            .put("version", 1)
            .put("sheet", sheet)
            .put("cursor", cursor)
            .put("mode", mode)
            .put("ink", ink)
            .put("prints", photos)
            .put("nib", nib)
            .put("width", thickness)
            .put("color", color)
            .toString()
    }

    companion object {
        fun read(page: Page): DiaryLayout {
            if (page.notebookLayout.isBlank()) {
                val legacy = page.mediaPositionsByFile()
                return DiaryLayout(
                    prints =
                        page
                            .files()
                            .mapIndexed { index, file ->
                                val p = legacy[file]
                                file to
                                    PrintPosition(
                                        index + 1,
                                        p?.across ?: .15f,
                                        p?.down?.let { (it / 600f).coerceIn(0f, .85f) } ?: .14f,
                                    )
                            }
                            .toMap()
                )
            }
            val json = JSONObject(page.notebookLayout)
            require(json.getInt("version") == 1) { "Unsupported diary layout" }
            val ink = json.getJSONArray("ink")
            val lines =
                List(ink.length()) { i ->
                    val a = ink.getJSONArray(i)
                    val p = a.getJSONArray(3)
                    InkLine(
                        a.getInt(0),
                        a.getInt(1),
                        a.getDouble(2).toFloat(),
                        List(p.length() / 3) {
                            NibPoint(
                                p.getDouble(it * 3).toFloat(),
                                p.getDouble(it * 3 + 1).toFloat(),
                                p.getDouble(it * 3 + 2).toFloat(),
                            )
                        },
                    )
                }
            val photos = json.getJSONObject("prints")
            return DiaryLayout(
                json.optInt("sheet"),
                json.optInt("cursor"),
                json.optString("mode", "Type"),
                lines,
                photos.keys().asSequence().associateWith {
                    val p = photos.getJSONArray(it)
                    PrintPosition(
                        p.getInt(0),
                        p.getDouble(1).toFloat(),
                        p.getDouble(2).toFloat(),
                        p.getDouble(3).toFloat(),
                    )
                },
                json.optInt("nib"),
                json.optDouble("width", 3.0).toFloat(),
                json.optInt("color", 0xff434736.toInt()),
            )
        }
    }
}

internal fun remapLayout(raw: String, files: Map<String, String>): String {
    if (raw.isBlank()) return raw
    val layout = DiaryLayout.read(Page(notebookLayout = raw))
    return layout
        .copy(prints = layout.prints.mapNotNull { (old, p) -> files[old]?.let { it to p } }.toMap())
        .encode()
}

internal data class TextSlice(val start: Int, val end: Int)

// The measuring callback returns the number of UTF-16 units fitting on one physical sheet.
internal fun paginate(body: String, fits: (String, Int, Int) -> Int): List<TextSlice> = buildList {
    var start = 0
    while (true) {
        val breakAt = body.indexOf('\u000c', start)
        val end = if (breakAt < 0) body.length else breakAt
        do {
            val count = if (start == end) 0 else fits(body, start, end).coerceIn(1, end - start)
            add(TextSlice(start, start + count))
            start += count
        } while (start < end)
        if (breakAt < 0) break
        start = end + 1
    }
}
