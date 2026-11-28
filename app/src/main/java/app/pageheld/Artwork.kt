package app.pageheld

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class Artwork(
    val id: String,
    val category: String,
    val en: String,
    val ko: String,
    val path: String,
    val neutral: String?,
    val ratio: Float,
)

internal fun readCatalog(context: Context): List<Artwork> {
    val a =
        context.assets.open("artwork/vol01/catalog.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("assets")
        }
    return List(a.length()) { index ->
        val j = a.getJSONObject(index)
        Artwork(
            j.getString("id"),
            j.getString("category"),
            j.getString("name"),
            j.getString("nameKo"),
            j.getString("path"),
            j.optString("tintSource").ifBlank { null },
            j.getInt("width").toFloat() / j.getInt("height"),
        )
    }
}

internal val LocalArtwork = staticCompositionLocalOf { emptyList<Artwork>() }
private val cache =
    object : LruCache<String, android.graphics.Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.allocationByteCount
    }

@Composable
internal fun ArtImage(id: String, modifier: Modifier, tint: Color? = null) {
    val asset = LocalArtwork.current.find { it.id == id } ?: return
    val path = (if (tint != null) asset.neutral else null) ?: asset.path
    val context = LocalContext.current
    val bitmap by
        produceState(cache.get(path), path) {
            value =
                withContext(Dispatchers.IO) {
                    cache.get(path)
                        ?: context.assets
                            .open("artwork/vol01/$path")
                            .use { BitmapFactory.decodeStream(it) }
                            ?.also { cache.put(path, it) }
                }
        }
    val filter =
        remember(tint, id) {
            tint?.let { color ->
                val midpoint =
                    when (id) {
                        "linen" -> 125f
                        "leather" -> 130f
                        else -> 127f
                    }
                val contrast = if (id == "linen") .43f else .65f
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            contrast,
                            0f,
                            0f,
                            0f,
                            color.red * 255f - midpoint * contrast,
                            0f,
                            contrast,
                            0f,
                            0f,
                            color.green * 255f - midpoint * contrast,
                            0f,
                            0f,
                            contrast,
                            0f,
                            color.blue * 255f - midpoint * contrast,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        )
                    )
                )
            }
        }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            null,
            modifier,
            contentScale = if (tint == null) ContentScale.Fit else ContentScale.Crop,
            colorFilter = filter,
        )
    }
}
