package app.pageheld

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

internal data class PlaceResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
)

internal fun interface PlaceSearchProvider {
    suspend fun search(query: String): List<PlaceResult>
}

internal class DevicePlaceSearch(private val context: Context) : PlaceSearchProvider {
    @Suppress("DEPRECATION")
    override suspend fun search(query: String): List<PlaceResult> {
        if (!Geocoder.isPresent()) throw UnsupportedOperationException()
        val geocoder = Geocoder(context)
        val results: List<Address> =
            if (Build.VERSION.SDK_INT >= 33)
                suspendCancellableCoroutine { c ->
                    geocoder.getFromLocationName(
                        query,
                        6,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (c.isActive) c.resume(addresses)
                            }

                            override fun onError(message: String?) {
                                if (c.isActive) c.resumeWithException(IOException())
                            }
                        },
                    )
                }
            else withContext(Dispatchers.IO) { geocoder.getFromLocationName(query, 6).orEmpty() }
        return searchResults(results, query)
    }
}

internal fun searchResults(results: List<Address>, query: String): List<PlaceResult> {
    return results
        .filter { it.hasLatitude() && it.hasLongitude() }
        .map { address ->
            val line = address.getAddressLine(0).orEmpty()
            val name =
                listOf(address.featureName, address.locality, address.adminArea, line, query)
                    .first { !it.isNullOrBlank() }
                    .orEmpty()
            PlaceResult(
                name,
                line.ifBlank {
                    listOfNotNull(address.locality, address.countryName).joinToString(", ")
                },
                address.latitude,
                address.longitude,
            )
        }
        .distinctBy { Triple(it.name, it.lat, it.lon) }
}

@Composable
internal fun PlaceSearch(
    book: Notebook,
    provider: PlaceSearchProvider = remember(book) { DevicePlaceSearch(book.context) },
    select: (PlaceResult) -> Unit,
) {
    val ctx = LocalContext.current
    val saved by remember(book) { book.dao.locations() }.collectAsState(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<PlaceResult>()) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val local =
        remember(query, saved) {
            if (query.isBlank()) emptyList()
            else
                saved
                    .filter {
                        it.lat != null && it.lon != null && it.name.contains(query.trim(), true)
                    }
                    .distinctBy { Triple(it.name, it.lat, it.lon) }
                    .take(4)
                    .map { PlaceResult(it.name, "Saved place", it.lat!!, it.lon!!) }
        }
    fun clear() {
        job?.cancel()
        matches = emptyList()
        status = ""
        loading = false
    }
    fun submit() {
        clear()
        val term = query.trim()
        if (term.isEmpty()) return
        keyboard?.hide()
        if (ctx.getSystemService(ConnectivityManager::class.java).activeNetwork == null) {
            status = "Offline. Choose a saved place or drop a pin."
            return
        }
        loading = true
        job =
            scope.launch {
                try {
                    matches = withTimeout(12000) { provider.search(term) }
                    if (matches.isEmpty())
                        status = "No results. Try a city or a more specific address."
                } catch (e: TimeoutCancellationException) {
                    status = "Search timed out. Try again or drop a pin."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UnsupportedOperationException) {
                    status = "Device search unavailable. Choose a saved place or drop a pin."
                } catch (e: Exception) {
                    status = "Search unavailable. Check your connection or drop a pin."
                } finally {
                    if (isActive) loading = false
                }
            }
    }
    OutlinedTextField(
        query,
        {
            clear()
            query = it
        },
        Modifier.fillMaxWidth(),
        label = { Text("Search places or addresses") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        trailingIcon = { Action("search", "Search places", query.isNotBlank()) { submit() } },
    )
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (status.isNotBlank()) Note(status)
    (matches + local)
        .distinctBy { it.lat to it.lon }
        .forEach { result ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable {
                        clear()
                        query = ""
                        keyboard?.hide()
                        select(result)
                    }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 5.dp)
            ) {
                Text(result.name)
                if (result.address.isNotBlank() && result.address != result.name)
                    Note(result.address)
            }
        }
}
