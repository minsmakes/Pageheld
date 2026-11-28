package app.pageheld

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject

internal data class NibPoint(val x: Float, val y: Float, val pressure: Float)

internal data class InkLine(
    val sheet: Int,
    val color: Int,
    val width: Float,
    val points: List<NibPoint>,
    val instrument: Int = -1, // Legacy polylines retain their original rendering.
)

// Three IEEE floats per sample keep the exact captured geometry without expanding
// each coordinate into a decimal Double inside Room's single-entry record.
private fun packPoints(points: List<NibPoint>): String {
    val buffer = ByteBuffer.allocate(points.size * 12).order(ByteOrder.LITTLE_ENDIAN)
    for (p in points) buffer.putFloat(p.x).putFloat(p.y).putFloat(p.pressure)
    return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
}

private fun unpackPoints(value: Any): List<NibPoint> {
    if (value is JSONArray)
        return List(value.length() / 3) { i ->
            NibPoint(
                value.getDouble(3 * i).toFloat(),
                value.getDouble(3 * i + 1).toFloat(),
                value.getDouble(3 * i + 2).toFloat(),
            )
        }
    val bytes = Base64.decode(value as String, Base64.DEFAULT)
    require(bytes.size % 12 == 0) { "Incomplete ink sample" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return List(bytes.size / 12) { NibPoint(buffer.float, buffer.float, buffer.float) }
}

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
    val opacity: Float = 1f,
    val sensitivity: Float = .8f,
    val areaEraser: Boolean = false,
    val eraserSize: Float = 24f,
    val sheetIds: List<String> = emptyList(),
    val resizeHintSeen: Boolean = false,
    val decorations: Map<String,List<CoverLayer>> = emptyMap(),
) {
    fun encode(): String {
        val ink = JSONArray()
        for (line in lines) {
            ink.put(
                JSONArray()
                    .put(line.sheet)
                    .put(line.color)
                    .put(line.width)
                    .put(packPoints(line.points))
                    .put(line.instrument)
            )
        }
        val photos = JSONObject()
        prints.forEach { (name, p) ->
            photos.put(name, JSONArray().put(p.sheet).put(p.x).put(p.y).put(p.width))
        }
        return JSONObject()
            .put("version", if(decorations.isEmpty()) 2 else 3)
            .put("decorations",JSONObject().also { d -> decorations.forEach { (id,layers) -> d.put(id,JSONArray(layers.map { it.encode() })) } })
            .put("sheets", JSONArray(sheetIds))
            .put("resizeHintSeen", resizeHintSeen)
            .put("opacity", opacity)
            .put("sensitivity", sensitivity)
            .put("areaEraser", areaEraser)
            .put("eraserSize", eraserSize)
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
            require(json.getInt("version") in 1..3) { "Unsupported diary layout" }
            val ink = json.getJSONArray("ink")
            val lines =
                List(ink.length()) { i ->
                    val a = ink.getJSONArray(i)
                    InkLine(
                        a.getInt(0),
                        a.getInt(1),
                        a.getDouble(2).toFloat(),
                        unpackPoints(a.get(3)),
                        a.optInt(4, -1),
                    )
                }
            val photos = json.getJSONObject("prints")
            return DiaryLayout(
                json.optInt("sheet"),
                json.optInt("cursor"),
                json.optString("mode", "Type"),
                lines,
                photos
                    .keys()
                    .asSequence()
                    .associateWith {
                        val p = photos.getJSONArray(it)
                        PrintPosition(
                            p.getInt(0),
                            p.getDouble(1).toFloat(),
                            p.getDouble(2).toFloat(),
                            p.getDouble(3).toFloat(),
                        )
                    }
                    .toMutableMap()
                    .apply {
                        if (json.getInt("version") == 1)
                            page.files().forEachIndexed { i, name ->
                                putIfAbsent(name, PrintPosition(i + 1))
                            }
                    },
                json.optInt("nib"),
                json.optDouble("width", 3.0).toFloat(),
                json.optInt("color", 0xff434736.toInt()),
                json.optDouble("opacity", 1.0).toFloat(),
                json.optDouble("sensitivity", .8).toFloat(),
                json.optBoolean("areaEraser"),
                json.optDouble("eraserSize", 24.0).toFloat(),
                json
                    .optJSONArray("sheets")
                    ?.let { ids -> List(ids.length()) { ids.getString(it) } }
                    .orEmpty(),
                json.optBoolean("resizeHintSeen", false),
                json.optJSONObject("decorations")?.let { d -> d.keys().asSequence().associateWith { id ->
                    val a=d.getJSONArray(id); require(a.length()<=200); List(a.length()) { CoverLayer.read(a.getJSONObject(it)) }
                } }.orEmpty(),
            )
        }
    }
}

internal fun remapLayout(raw: String, files: Map<String, String>): String {
    if (raw.isBlank()) return raw
    val layout = DiaryLayout.read(Page(notebookLayout = raw))
    return layout
        .copy(decorations=layout.decorations.mapValues { (_,layers) -> layers.map { if(it.kind=="photo") it.copy(asset=files.getValue(it.asset)) else it } }, prints = layout.prints.mapNotNull { (old, p) -> files[old]?.let { it to p } }.toMap())
        .encode()
}

internal data class TextSlice(val start: Int, val end: Int)

// Preserve the active IME buffer and shift the subsequent slices by the document edit.
internal fun pinTextSlices(
    slices: List<TextSlice>,
    index: Int,
    active: TextSlice,
    prefix: Int,
    delta: Int,
): List<TextSlice> =
    List(maxOf(slices.size, index + 1)) { i ->
        when {
            i == index -> TextSlice(active.start + prefix, active.end + delta)
            i >= slices.size ->
                TextSlice(active.start + i - slices.size + 1, active.start + i - slices.size + 1)
            i > index -> TextSlice(slices[i].start + delta, slices[i].end + delta)
            else -> slices[i]
        }
    }

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
