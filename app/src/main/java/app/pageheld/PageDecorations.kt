package app.pageheld

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

private fun WritingSession.setDecorations(
    index: Int,
    transform: (List<CoverLayer>) -> List<CoverLayer>,
) {
    val art = layout.withSheetCount(index + 1)
    val id = art.sheetIds[index]
    val next = transform(art.decorations[id].orEmpty())
    require(next.size <= 200)
    update(
        art =
            art.copy(
                sheet = index,
                decorations =
                    if (next.isEmpty()) art.decorations - id else art.decorations + (id to next),
            )
    )
}

@Composable
internal fun SheetDecorations(session: WritingSession, index: Int, editable: Boolean) {
    val art = session.layout
    val layers = art.decorations[art.sheetIds.getOrNull(index)].orEmpty()
    if (layers.isEmpty()) return
    var selected by remember { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        layers.forEach { layer ->
            key(layer.id) {
                ArtworkLayer(
                    session.book,
                    "",
                    layer,
                    maxWidth,
                    maxHeight,
                    editable && selected == layer.id,
                    if (editable) { id -> selected = id } else null,
                    if (editable)
                        { next ->
                            session.setDecorations(index) { list ->
                                list.map { if (it.id == next.id) next else it }
                            }
                        }
                    else null,
                )
            }
        }
    }
}

@Composable
internal fun PageDecorationPanel(session: WritingSession, close: () -> Unit) {
    val index = session.layout.sheet
    val layers = session.layout.decorations[session.layout.sheetIds.getOrNull(index)].orEmpty()
    var selection by remember(index) { mutableStateOf("") }
    val selected = layers.find { it.id == selection }
    val scope = rememberCoroutineScope()
    val full = layers.size >= 200
    fun add(obj: CoverLayer) {
        session.setDecorations(index) { it + obj }
        selection = obj.id
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val photo = importCoverPhoto(session.book, uri)
                        session.trackImport(photo.asset)
                        add(photo)
                    } catch (e: Exception) {
                        session.error = "Could not import image."
                    }
                }
        }
    AlertDialog(
        onDismissRequest = close,
        title = { Title(wording("Decorate page ${index+1}", "${index+1}쪽 꾸미기")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow {
                    LocalArtwork.current
                        .filter { it.category != "materials" }
                        .forEach { a ->
                            Box(
                                Modifier.size(48.dp)
                                    .clickable(enabled = !full) {
                                        add(CoverLayer(kind = "art", asset = a.id, ratio = a.ratio))
                                    }
                                    .semantics { contentDescription = a.en }
                                    .padding(2.dp)
                            ) {
                                ArtImage(a.id, Modifier.fillMaxSize())
                            }
                        }
                }
                TextButton(enabled = !full, onClick = { picker.launch("image/*") }) {
                    Text(wording("Import photograph", "사진 가져오기"))
                }
                FlowRow {
                    layers.forEachIndexed { i, l ->
                        StudioChoice(
                            "${i+1} ${if(l.kind=="photo") wording("Photo","사진") else l.asset}",
                            l.id == selection,
                        ) {
                            selection = l.id
                        }
                    }
                }
                if (selected != null) {
                    LayerControls(selected) { next ->
                        session.setDecorations(index) { list ->
                            list.map { if (it.id == next.id) next else it }
                        }
                    }
                    TextButton(
                        onClick = {
                            session.setDecorations(index) { it - selected }
                            selection = ""
                        }
                    ) {
                        Text(wording("Delete decoration", "장식 삭제"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(wording("Done", "완료")) } },
    )
}
