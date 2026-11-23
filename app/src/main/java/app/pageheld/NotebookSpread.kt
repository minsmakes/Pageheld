package app.pageheld

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun NotebookSpread(
    pages: List<Page>, editor: String?, open: (Page) -> Unit,
    modifier: Modifier, content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        if (BuildConfig.FLAVOR != "tablet" || maxWidth < 840.dp) {
            content()
        } else {
            Row(Modifier.widthIn(max = 1480.dp).fillMaxSize().padding(16.dp)
                .background(sage, RoundedCornerShape(12.dp)).padding(5.dp)
                .clip(RoundedCornerShape(8.dp)).background(ivory)) {
                LazyColumn(
                    Modifier.weight(.85f).fillMaxHeight().paper(),
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (editor == null) {
                        item { Title("Notebook index", 26) }
                        if (pages.isEmpty()) item { Note("Your saved pages will appear here.") }
                        items(pages, key = { it.id }) { page ->
                            Column(Modifier.fillMaxWidth().clickable { open(page) }.padding(vertical = 6.dp)) {
                                Note(stamp(page.created, "yMMMd"))
                                Title(page.heading(), 18)
                                HorizontalDivider(color = edge)
                            }
                        }
                    } else {
                        val index = pages.indexOfFirst { it.id == editor }
                        val facing = pages.getOrNull(index + 1)
                        item { Note("Facing page") }
                        if (facing != null) {
                            item { Note(stamp(facing.created, "yMMMd")); Title(facing.heading(), 24) }
                            item { Text(facing.body, fontSize = uiSp(18), lineHeight = uiSp(30), color = ink) }
                        }
                    }
                }
                Spacer(Modifier.fillMaxHeight().width(15.dp).background(Brush.horizontalGradient(
                    listOf(ivory, Color(0xffc8bfab), Color(0xffe6decd), ivory))))
                Box(Modifier.weight(1.15f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    content()
                }
            }
        }
    }
}
