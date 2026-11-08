package br.com.prumopdf.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.prumopdf.AppViewModel
import br.com.prumopdf.PageMode
import br.com.prumopdf.core.InsertPosition
import br.com.prumopdf.core.LibrarySnapshot
import br.com.prumopdf.core.PageEntry
import br.com.prumopdf.core.PageSelection
import br.com.prumopdf.core.PdfItem
import br.com.prumopdf.core.SplitPlan
import br.com.prumopdf.core.UserError
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.i18n.LN
import br.com.prumopdf.pdf.Pdfium
import kotlinx.coroutines.delay

// region Documento aberto para as telas de páginas

/** Abre o PDF (sem senha) para as telas de páginas; null enquanto abre. */
@Composable
private fun rememberPagesDoc(vm: AppViewModel, item: PdfItem): Pair<ReaderDoc?, Boolean> {
    val opened = rememberOpenedDoc(vm, item, 0)
    val doc = remember(opened) { (opened as? Pdfium.Opened.Ok)?.let { ReaderDoc(it.doc) } }
    DisposableEffect(doc) { onDispose { doc?.close() } }
    return doc to (opened != null && doc == null)
}

/** Miniatura de uma página, com o giro a mais aplicado (em quartos de volta). */
@Composable
fun PageThumb(doc: ReaderDoc, index: Int, quarters: Int, modifier: Modifier = Modifier, widthPx: Int = 260) {
    val bmp by produceState<Bitmap?>(doc.cached(index, widthPx), index, widthPx) {
        if (value == null) value = doc.page(index, widthPx)
    }
    val rotation by animateFloatAsState((quarters * 90).toFloat(), label = "giro")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Image(
                b.asImageBitmap(), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp).graphicsLayer { rotationZ = rotation }
                    .shadow(1.5.dp).background(Color.White),
            )
        }
    }
}

// endregion

// region Organizar páginas

@Composable
fun OrganizeScreen(vm: AppViewModel, snapshot: LibrarySnapshot, id: String, showBack: Boolean) {
    val item = remember(snapshot.revision, id) { vm.library.item(id) } ?: return
    if (item.isLocked) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(L("Organizar páginas"), showBack, { vm.back() }, large = false)
            EmptyState(L("Este PDF tem senha. Para fazer OCR, organizar ou dividir, crie antes uma cópia sem senha."))
        }
        return
    }
    val (doc, failed) = rememberPagesDoc(vm, item)
    if (doc == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(L("Organizar páginas"), showBack, { vm.back() }, large = false)
            if (failed) EmptyState(L("Não foi possível abrir este PDF.")) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    OrganizeContent(vm, item, doc, showBack)
}

@Composable
private fun OrganizeContent(vm: AppViewModel, item: PdfItem, doc: ReaderDoc, showBack: Boolean) {
    val entries = remember(doc) { mutableStateListOf<PageEntry>().apply { addAll(doc.sizes.indices.map { PageEntry(it) }) } }
    val history = remember(doc) { mutableStateListOf<List<PageEntry>>() }
    val selected = remember(doc) { mutableStateListOf<Long>() }
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val dirty = history.isNotEmpty()
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    var gridHeight by remember { mutableIntStateOf(0) }

    fun saveHistory() {
        history.add(entries.toList())
    }

    fun moveDragged() {
        val key = dragKey ?: return
        val target = grid.indexAt(dragPos) ?: return
        val from = entries.indexOfFirst { it.key == key }
        if (from < 0 || from == target || target !in entries.indices) return
        entries.add(target, entries.removeAt(from))
    }

    // Rolagem automática enquanto arrasta perto do alto ou do pé da tela.
    LaunchedEffect(dragKey) {
        while (dragKey != null) {
            val v = edgeSpeed(dragPos.y, gridHeight, edge, 26f)
            if (v != 0f) {
                grid.scrollBy(v)
                moveDragged()
            }
            delay(16)
        }
    }

    BackHandler(enabled = dirty && !saving) { confirmLeave = true }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(L("Organizar páginas"), showBack, { if (dirty) confirmLeave = true else vm.back() }, large = false) {
            IconButton(onClick = {
                history.removeLastOrNull()?.let {
                    entries.clear()
                    entries.addAll(it)
                    selected.retainAll(it.map { e -> e.key }.toSet())
                }
            }, enabled = dirty, modifier = Modifier.testTag("organizeUndo")) {
                Icon(Icons.AutoMirrored.Filled.Undo, L("Desfazer"), tint = if (dirty) Palette.blue else Prumo.colors.placeholder)
            }
            TextButton(onClick = {
                saving = true
                vm.organize(item, entries.toList()) { vm.back() }
            }, enabled = dirty && !saving, modifier = Modifier.testTag("organizeSave")) { Text(L("Salvar")) }
        }
        Text(
            L("Toque para selecionar; toque e segure para arrastar. Perto do alto ou do pé da tela, a lista rola sozinha."),
            fontSize = 13.sp, color = Prumo.colors.secondaryText, modifier = Modifier.padding(horizontal = Dimens.margin),
        )
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                state = grid,
                contentPadding = PaddingValues(Dimens.margin),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().testTag("organizeGrid")
                    .pointerInput(doc) { gridHeight = size.height }
                    .pointerInput(doc) {
                        gridHeight = size.height
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                val i = grid.indexAt(pos)
                                if (i != null) {
                                    saveHistory()
                                    dragKey = entries[i].key
                                    dragPos = pos
                                }
                            },
                            onDrag = { change, amount ->
                                if (dragKey != null) {
                                    change.consume()
                                    dragPos += amount
                                    moveDragged()
                                }
                            },
                            onDragEnd = {
                                dragKey = null
                                // arraste sem mudar nada não conta como alteração
                                if (history.lastOrNull() == entries.toList()) history.removeLastOrNull()
                            },
                            onDragCancel = { dragKey = null },
                        )
                    },
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { index, e ->
                    val isSel = e.key in selected
                    val isDrag = e.key == dragKey
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.animateItem().testTag("cell-$index")
                            .combinedClickable(
                                onClick = { if (e.key in selected) selected.remove(e.key) else selected.add(e.key) },
                                onDoubleClick = { viewing = index },
                            )
                            .graphicsLayer {
                                scaleX = if (isDrag) 1.08f else 1f
                                scaleY = if (isDrag) 1.08f else 1f
                                alpha = if (isDrag) 0.85f else 1f
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Palette.blue.copy(alpha = 0.16f) else Color.Transparent)
                            .border(if (isSel) 2.dp else 0.dp, if (isSel) Palette.blue else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                    ) {
                        PageThumb(doc, e.source, e.extraQuarters, Modifier.fillMaxWidth())
                        Text(
                            "${index + 1}", fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Palette.blue else Prumo.colors.secondaryText,
                        )
                    }
                }
            }
        }
        // Barra de ações
        Row(
            Modifier.fillMaxWidth().background(Prumo.colors.tile.copy(alpha = 0.6f)).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val n = selected.size
            Text(
                if (n == 0) LN(entries.size, "1 página", "{} páginas") else LN(n, "1 página selecionada", "{} páginas selecionadas"),
                fontSize = 13.sp, color = Prumo.colors.text, modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            fun rotate(q: Int) {
                saveHistory()
                for (i in entries.indices) {
                    val e = entries[i]
                    if (e.key in selected) entries[i] = e.copy(extraQuarters = ((e.extraQuarters + q) % 4 + 4) % 4)
                }
            }
            IconButton(onClick = {
                if (n == entries.size) selected.clear() else {
                    selected.clear()
                    selected.addAll(entries.map { it.key })
                }
            }) { Icon(Icons.Filled.SelectAll, L("Selecionar todas"), tint = Palette.blue) }
            IconButton(onClick = { rotate(-1) }, enabled = n > 0) { Icon(Icons.Filled.RotateLeft, L("Girar para a esquerda")) }
            IconButton(onClick = { rotate(1) }, enabled = n > 0, modifier = Modifier.testTag("rotateRight")) {
                Icon(Icons.Filled.RotateRight, L("Girar para a direita"))
            }
            IconButton(onClick = {
                val first = entries.indexOfFirst { it.key in selected }
                if (first >= 0) viewing = first
            }, enabled = n == 1) { Icon(Icons.Filled.ZoomIn, L("Ver página")) }
            IconButton(onClick = {
                if (n >= entries.size) {
                    vm.say(L("O documento precisa ter pelo menos uma página."))
                } else {
                    saveHistory()
                    entries.removeAll { it.key in selected }
                    selected.clear()
                }
            }, enabled = n > 0, modifier = Modifier.testTag("deletePages")) {
                Icon(Icons.Filled.Delete, L("Excluir páginas selecionadas"), tint = if (n > 0) Palette.red else Prumo.colors.placeholder)
            }
        }
    }

    viewing?.let { start ->
        PageViewer(doc, entries.map { it.source to it.extraQuarters }, start) { viewing = null }
    }
    if (confirmLeave) {
        ConfirmDialog(
            L("Sair sem salvar?"), L("A nova ordem das páginas não foi salva."), L("Descartar"), destructive = true,
            onDismiss = { confirmLeave = false },
        ) {
            confirmLeave = false
            history.clear()
            vm.back()
        }
    }
}

/** Página grande, com setas para as vizinhas (toque duplo ou "Ver página" no organizador). */
@Composable
fun PageViewer(doc: ReaderDoc, pages: List<Pair<Int, Int>>, start: Int, onClose: () -> Unit) {
    var i by remember { mutableIntStateOf(start.coerceIn(0, pages.size - 1)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Prumo.colors.background).testTag("pageViewer")) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, L("Fechar")) }
                Text(
                    LF("Página {} de {}", i + 1, pages.size), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = Prumo.colors.text, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { i-- }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowLeft, L("Página anterior")) }
                IconButton(onClick = { i++ }, enabled = i < pages.size - 1) { Icon(Icons.Filled.KeyboardArrowRight, L("Próxima página")) }
            }
            val (src, q) = pages[i]
            val px = with(LocalDensity.current) { 900.dp.roundToPx() }.coerceAtMost(2400)
            val bmp by produceState<Bitmap?>(doc.cached(src, px), src, px) { value = doc.page(src, px) }
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                bmp?.let {
                    Image(
                        it.asImageBitmap(), LF("Página {} de {}", i + 1, pages.size), contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = q * 90f },
                    )
                } ?: CircularProgressIndicator()
            }
        }
    }
}

// endregion

// region Escolher páginas (remover, extrair)

@Composable
fun PickPagesScreen(vm: AppViewModel, snapshot: LibrarySnapshot, id: String, mode: PageMode, showBack: Boolean) {
    val item = remember(snapshot.revision, id) { vm.library.item(id) } ?: return
    val title = if (mode == PageMode.REMOVE) L("Remover páginas") else L("Extrair páginas")
    if (item.isLocked) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title, showBack, { vm.back() }, large = false)
            EmptyState(L("Este PDF tem senha. Para fazer OCR, organizar ou dividir, crie antes uma cópia sem senha."))
        }
        return
    }
    val (doc, failed) = rememberPagesDoc(vm, item)
    if (doc == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title, showBack, { vm.back() }, large = false)
            if (failed) EmptyState(L("Não foi possível abrir este PDF.")) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    val count = doc.sizes.size
    val selected = remember(doc) { mutableStateListOf<Int>() }   // índices a partir de 0
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val ranges = remember(doc) { RangeSelection({ (0 until count).toList() }, selected) }
    LaunchedEffect(selected.toList()) {
        text = PageSelection.format(selected.map { it + 1 })
        error = null
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, showBack, { vm.back() }, large = false)
        Column(Modifier.padding(horizontal = Dimens.margin)) {
            OutlinedTextField(
                text, { text = it }, singleLine = true, label = { Text(L("Páginas")) },
                placeholder = { Text("1, 3-5, 10-${L("fim")}") },
                isError = error != null, supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    try {
                        val pages = PageSelection.parse(text, count)
                        selected.clear()
                        selected.addAll(pages.map { it - 1 })
                    } catch (e: UserError) {
                        error = e.message
                    }
                }),
                modifier = Modifier.fillMaxWidth().testTag("pagesField"),
            )
            Text(
                L("Toque nas páginas, ou deslize o dedo de lado sobre elas para marcar várias."),
                fontSize = 13.sp, color = Prumo.colors.secondaryText,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            state = grid,
            contentPadding = PaddingValues(Dimens.margin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).testTag("pickGrid").dragSelect(grid, ranges::start, ranges::range),
        ) {
            itemsIndexed((0 until count).toList(), key = { _, i -> i }) { _, i ->
                val isSel = i in selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.testTag("cell-$i").clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) Palette.blue.copy(alpha = 0.16f) else Color.Transparent)
                        .border(if (isSel) 2.dp else 0.dp, if (isSel) Palette.blue else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { if (isSel) selected.remove(i) else selected.add(i) }
                        .padding(4.dp),
                ) {
                    Box {
                        PageThumb(doc, i, 0, Modifier.fillMaxWidth())
                        if (isSel) {
                            Icon(
                                Icons.Filled.Check, null, tint = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Palette.blue).padding(3.dp),
                            )
                        }
                    }
                    Text("${i + 1}", fontSize = 12.sp, color = if (isSel) Palette.blue else Prumo.colors.secondaryText)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Prumo.colors.tile.copy(alpha = 0.6f)).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp),
        ) {
            if (mode == PageMode.REMOVE) {
                Text(
                    L("As páginas saem deste PDF. Você pode usar “Desfazer última alteração” logo depois, se precisar."),
                    fontSize = 12.sp, color = Prumo.colors.secondaryText, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val n = selected.size
            Button(
                onClick = {
                    busy = true
                    val pages = selected.map { it + 1 }.sorted()
                    if (mode == PageMode.REMOVE) vm.removePages(item, pages) { vm.back() }
                    else vm.extractPages(item, pages) { vm.back() }
                },
                enabled = n > 0 && !busy && !(mode == PageMode.REMOVE && n >= count),
                modifier = Modifier.fillMaxWidth().testTag("pickConfirm"),
            ) {
                Text(
                    when {
                        mode == PageMode.REMOVE && n >= count -> L("Não é possível remover todas as páginas. Para isso, exclua o documento.")
                        mode == PageMode.REMOVE -> LN(n, "Remover 1 página", "Remover {} páginas")
                        else -> LN(n, "Extrair 1 página", "Extrair {} páginas")
                    },
                )
            }
        }
    }
}

// endregion

// region Diálogos: dividir, incluir, combinar, senha

@Composable
fun SplitDialog(item: PdfItem, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val pages = item.pageCount ?: 0
    var parts by remember { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Dividir em partes")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L("Em quantas partes você quer dividir?"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { parts-- }, enabled = parts > 2) { Icon(Icons.Filled.KeyboardArrowDown, "−") }
                    Text("$parts", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("splitParts"))
                    IconButton(onClick = { parts++ }, enabled = parts < pages.coerceAtMost(200), modifier = Modifier.testTag("splitMore")) {
                        Icon(Icons.Filled.KeyboardArrowUp, "+")
                    }
                }
                SplitPlan.summary(pages, parts)?.let { Text(it, color = Prumo.colors.secondaryText) }
                Text(
                    L("Os novos arquivos ficam na mesma pasta, com o nome do original e o número da parte. O original não muda."),
                    fontSize = 13.sp, color = Prumo.colors.secondaryText,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parts) }, enabled = pages >= parts, modifier = Modifier.testTag("splitOk")) {
                Text(L("Dividir"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}

@Composable
fun InsertDialog(target: PdfItem, source: PdfItem, onDismiss: () -> Unit, onConfirm: (List<Int>, InsertPosition) -> Unit) {
    val srcCount = source.pageCount ?: 0
    val tgtCount = target.pageCount ?: 0
    var pagesText by remember { mutableStateOf("1-${L("fim")}") }
    var where by remember { mutableIntStateOf(1) }   // 0 início, 1 fim, 2 depois da página
    var after by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Incluir páginas")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(LF("De “{}” para “{}”.", source.name, target.name), fontSize = 14.sp, color = Prumo.colors.secondaryText)
                OutlinedTextField(
                    pagesText, { pagesText = it }, singleLine = true, label = { Text(L("Páginas")) },
                    supportingText = { Text(LN(srcCount, "1 página", "{} páginas")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(L("Posição"), fontWeight = FontWeight.SemiBold)
                listOf(L("No início"), L("No fim"), L("Depois da página")).forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().clickable { where = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(where == i, { where = i })
                        Text(label)
                        if (i == 2) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                after, { after = it.filter(Char::isDigit) }, singleLine = true, enabled = where == 2,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(88.dp),
                            )
                        }
                    }
                }
                error?.let { Text(it, color = Palette.red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val pages = PageSelection.parse(pagesText, srcCount)
                    val position = when (where) {
                        0 -> InsertPosition.Start
                        1 -> InsertPosition.End
                        else -> {
                            val n = after.toIntOrNull() ?: 0
                            if (n !in 1..tgtCount) throw UserError(LF("O documento tem {}; a página {} não existe.", LN(tgtCount, "1 página", "{} páginas"), n))
                            InsertPosition.After(n)
                        }
                    }
                    onConfirm(pages, position)
                } catch (e: UserError) {
                    error = e.message
                }
            }, modifier = Modifier.testTag("insertOk")) { Text(L("Incluir")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}

@Composable
fun MergeDialog(initial: List<PdfItem>, onDismiss: () -> Unit, onConfirm: (List<PdfItem>, String) -> Unit) {
    val order: SnapshotStateList<PdfItem> = remember { mutableStateListOf<PdfItem>().apply { addAll(initial) } }
    var name by remember { mutableStateOf(L("PDF combinado")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Combinar PDFs")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(L("Nome do novo PDF")) }, modifier = Modifier.fillMaxWidth())
                Text(L("Ordem"), fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(order, key = { _, it -> it.id }) { i, it ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${i + 1}.", modifier = Modifier.width(28.dp), color = Prumo.colors.secondaryText)
                            Text(it.name, modifier = Modifier.weight(1f), maxLines = 2)
                            IconButton(onClick = { order.add(i - 1, order.removeAt(i)) }, enabled = i > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, L("Mover para cima"))
                            }
                            IconButton(onClick = { order.add(i + 1, order.removeAt(i)) }, enabled = i < order.size - 1) {
                                Icon(Icons.Filled.KeyboardArrowDown, L("Mover para baixo"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(order.toList(), name.trim()) }, modifier = Modifier.testTag("mergeOk")) { Text(L("Combinar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}

@Composable
fun RemovePasswordDialog(item: PdfItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Remover senha")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L("Cria uma cópia sem senha, para fazer OCR, organizar ou dividir. O PDF com senha continua guardado."))
                OutlinedTextField(
                    value, { value = it }, singleLine = true, label = { Text(L("Senha do PDF")) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(LF("“{}” tem senha", item.name), fontSize = 12.sp, color = Prumo.colors.secondaryText)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotEmpty()) { Text(L("Remover senha")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}

// endregion
