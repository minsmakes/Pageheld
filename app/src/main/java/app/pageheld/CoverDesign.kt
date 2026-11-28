package app.pageheld

import org.json.JSONArray
import org.json.JSONObject

internal const val clothColor = 0xffa4ad80.toInt()
internal val coverColors =
    listOf(
        clothColor,
        0xfff4e4ca.toInt(),
        0xffc7a888.toInt(),
        0xffd9a7a5.toInt(),
        0xffb9a6bf.toInt(),
        0xff9bb8c2.toInt(),
        0xff557086.toInt(),
        0xff806a59.toInt(),
    )
internal val photoName = Regex("[0-9a-fA-F-]{36}\\.photo")

internal data class CoverLayer(
    val id: String = uid(),
    val kind: String,
    val asset: String,
    val x: Float = .5f,
    val y: Float = .58f,
    val width: Float = .3f,
    val angle: Float = 0f,
    val opacity: Float = 1f,
    val ratio: Float = 1f,
    val font: String = "몽글동심",
    val color: Int = 0xff433d30.toInt(),
    val align: String = "Center",
) {
    fun encode() =
        JSONObject()
            .put("id", id)
            .put("kind", kind)
            .put("asset", asset)
            .put("x", x)
            .put("y", y)
            .put("width", width)
            .put("angle", angle)
            .put("opacity", opacity)
            .put("ratio", ratio)
            .put("font", font)
            .put("color", color)
            .put("align", align)

    companion object {
        fun read(j: JSONObject): CoverLayer {
            fun number(key: String, range: ClosedFloatingPointRange<Float>) =
                j.getDouble(key).toFloat().also { require(it in range) }
            return CoverLayer(
                    j.getString("id"),
                    j.getString("kind"),
                    j.getString("asset"),
                    number("x", 0f..1f),
                    number("y", 0f..1f),
                    number("width", .04f..2f),
                    number("angle", -360f..360f),
                    number("opacity", 0f..1f),
                    number("ratio", .05f..20f),
                    j.optString("font", "몽글동심"),
                    j.optInt("color", 0xff433d30.toInt()),
                    j.optString("align", "Center"),
                )
                .also {
                    require(it.kind in setOf("title", "text", "art", "photo"))
                    if (it.kind == "photo") require(photoName.matches(it.asset))
                }
        }
    }
}

internal data class CoverDesign(
    val material: String = "linen",
    val color: Int = clothColor,
    val border: String = "Classic",
    val ribbon: Int = clothColor,
    val layers: List<CoverLayer> = emptyList(),
    val proportion: Float = 1.58f,
    val thickness: Float = .04f,
) {
    fun encode() =
        JSONObject()
            .put("material", material)
            .put("color", color)
            .put("border", border)
            .put("ribbon", ribbon)
            .put("proportion", proportion)
            .put("thickness", thickness)
            .put("layers", JSONArray(layers.map { it.encode() }))
            .toString()

    companion object {
        fun read(raw: String, back: Boolean = false): CoverDesign {
            val j = JSONObject(raw)
            val a = j.optJSONArray("layers")
            val layers =
                if (a == null) {
                    if (back) emptyList()
                    else listOf(CoverLayer("title", "title", "", .52f, .39f, .76f, ratio = 3.2f))
                } else List(a.length()) { CoverLayer.read(a.getJSONObject(it)) }
            require(layers.size <= 200 && layers.distinctBy { it.id }.size == layers.size)
            return CoverDesign(
                j.optString("material", "linen"),
                j.optInt("color", clothColor),
                j.optString("border", "Classic"),
                j.optInt("ribbon", clothColor),
                layers,
                j.optDouble("proportion", 1.58).toFloat().coerceIn(.65f, 1.9f),
                j.optDouble("thickness", .04).toFloat().coerceIn(.015f, .09f),
            )
        }
    }
}
