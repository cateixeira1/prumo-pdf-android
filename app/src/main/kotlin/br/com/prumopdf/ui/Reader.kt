package br.com.prumopdf.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.prumopdf.AppViewModel
import br.com.prumopdf.core.LibrarySnapshot
import br.com.prumopdf.core.PdfItem
import br.com.prumopdf.core.TextFold
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.pdf.PdfDoc
import br.com.prumopdf.pdf.Pdfium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Um trecho achado pela busca: página (a partir de 0) e retângulos em frações da página. */
data class SearchHit(val page: Int, val rects: List<RectF>)

/**
 * PDF aberto no leitor, com as páginas desenhadas pelo PDFium (o mesmo motor do Windows). Guarda as
 * páginas já desenhadas (até 1/6 da memória do app).
 */
class ReaderDoc(val doc: PdfDoc) {
    val sizes: List<Pair<Float, Float>> = doc.pageSizes()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(index: Int, widthPx: Int): Bitmap? = cache.get("$index-$widthPx")

    suspend fun page(index: Int, widthPx: Int): Bitmap? {
        val key = "$index-$widthPx"
        cache.get(key)?.let { return it }
        return withContext(Pdfium.dispatcher) {
            cache.get(key) ?: doc.renderPage(index, widthPx)?.also { cache.put(key, it) }
        }
    }

    /** Todas as ocorrências, sem diferenciar maiúsculas e acentos. */
    suspend fun search(query: String): List<SearchHit> = withContext(Pdfium.dispatcher) {
        val out = mutableListOf<SearchHit>()
        for (i in sizes.indices) {
            val text = doc.text(i)
            for (range in TextFold.find(text, query)) {
                val rects = doc.textRects(i, range.first, range.last - range.first + 1)
                if (rects.isNotEmpty()) out += SearchHit(i, rects)
            }
        }
        out
    }

    fun close() {
        cache.evictAll()
        doc.close()
    }
}

/** Abre o PDF para o leitor (pede a senha quando precisa). */
@Composable
fun rememberOpenedDoc(vm: AppViewModel, item: PdfItem, attempt: Int): Pdfium.Opened? {
    val opened by produceState<Pdfium.Opened?>(null, item.id, item.version, attempt) {
        value = withContext(Dispatchers.IO) { Pdfium.open(item.file, vm.passwords[item.id]) }
    }
    return opened
}

@Composable
fun ReaderScreen(vm: AppViewModel, snapshot: LibrarySnapshot, id: String, showBack: Boolean) {
    val item = remember(snapshot.revision, id) { vm.library.item(id) } ?: return
    var attempt by remember { mutableIntStateOf(0) }
    val opened = rememberOpenedDoc(vm, item, attempt)
    when (opened) {
        null -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(item.name, showBack, { vm.back() }, large = false)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is Pdfium.Opened.Password -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(item.name, showBack, { vm.back() }, large = false)
            PasswordPrompt(wrong = opened.wrong) { pw ->
                vm.passwords[item.id] = pw
                attempt++
            }
        }
        is Pdfium.Opened.Failed -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(item.name, showBack, { vm.back() }, large = false)
            EmptyState(L("Não foi possível abrir este PDF."))
        }
        is Pdfium.Opened.Ok -> {
            val doc = remember(opened) { ReaderDoc(opened.doc) }
            DisposableEffect(doc) { onDispose { doc.close() } }
            ReaderContent(vm, item, doc, showBack)
        }
    }
}

@Composable
fun PasswordPrompt(wrong: Boolean, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(24.dp).testTag("passwordPrompt"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Lock, null, tint = Palette.purple, modifier = Modifier.size(40.dp))
        Text(L("Este PDF está protegido. Digite a senha para abrir."), color = Prumo.colors.text, fontSize = 16.sp)
        OutlinedTextField(
            value, { value = it }, singleLine = true, label = { Text(L("Senha")) },
            isError = wrong,
            supportingText = if (wrong) ({ Text(L("Senha incorreta. Tente de novo (maiúsculas e minúsculas fazem diferença).")) }) else null,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotEmpty()) onSubmit(value) }),
            modifier = Modifier.fillMaxWidth().testTag("passwordField"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(show, { show = it })
            Text(L("Mostrar a senha"), color = Prumo.colors.text)
        }
        Button(onClick = { onSubmit(value) }, enabled = value.isNotEmpty(), modifier = Modifier.testTag("passwordOk")) {
            Text(L("Abrir"))
        }
        Text(
            L("A senha fica só na memória enquanto o Prumo está aberto; ela não é gravada."),
            color = Prumo.colors.secondaryText, fontSize = 13.sp,
        )
    }
}

@Composable
private fun ReaderContent(vm: AppViewModel, item: PdfItem, doc: ReaderDoc, showBack: Boolean) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val current by remember { derivedStateOf { list.firstVisibleItemIndex } }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }
    var hitIndex by remember { mutableIntStateOf(0) }
    var thumbs by remember { mutableStateOf(false) }
    val count = doc.sizes.size

    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(250)
        val found = doc.search(query)
        hits = found
        hitIndex = 0
        found.firstOrNull()?.let { list.animateScrollToItem(it.page) }
    }

    fun goTo(i: Int) {
        val h = hits ?: return
        if (h.isEmpty()) return
        hitIndex = (i + h.size) % h.size
        scope.launch { list.animateScrollToItem(h[hitIndex].page) }
    }

    Column(Modifier.fillMaxSize().background(Prumo.colors.background)) {
        ScreenHeader(item.name, showBack, { vm.back() }, large = false) {
            Text(
                LF("Página {} de {}", current + 1, count), fontSize = 13.sp, color = Prumo.colors.secondaryText,
                modifier = Modifier.padding(end = 4.dp).testTag("pageIndicator"),
            )
            IconButton(onClick = { thumbs = !thumbs }, modifier = Modifier.testTag("thumbsToggle")) {
                Icon(Icons.Filled.ViewSidebar, L("Miniaturas das páginas"), tint = if (thumbs) Palette.blue else Prumo.colors.secondaryText)
            }
            IconButton(onClick = {
                searching = !searching
                if (!searching) query = ""
            }, modifier = Modifier.testTag("searchToggle")) {
                Icon(Icons.Filled.Search, L("Buscar no documento"), tint = Palette.blue)
            }
        }
        if (searching) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.margin, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).testTag("readerSearch")) {
                    SearchField(query, { query = it }, L("Buscar no documento (sem precisar de acentos)"))
                }
                val h = hits
                Text(
                    when {
                        h == null -> ""
                        h.isEmpty() -> L("Não encontrado")
                        else -> LF("{} de {}", hitIndex + 1, h.size)
                    },
                    fontSize = 13.sp, color = Prumo.colors.secondaryText,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("searchCount"),
                )
                IconButton(onClick = { goTo(hitIndex - 1) }, enabled = !h.isNullOrEmpty()) {
                    Icon(Icons.Filled.KeyboardArrowUp, L("Resultado anterior"))
                }
                IconButton(onClick = { goTo(hitIndex + 1) }, enabled = !h.isNullOrEmpty()) {
                    Icon(Icons.Filled.KeyboardArrowDown, L("Próximo resultado"))
                }
                IconButton(onClick = {
                    searching = false
                    query = ""
                }) { Icon(Icons.Filled.Close, L("Fechar")) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize()) {
                if (thumbs && side) {
                    ThumbStrip(doc, current, vertical = true, Modifier.width(104.dp).fillMaxHeight()) { scope.launch { list.scrollToItem(it) } }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PagesView(doc, list, hits, hits?.getOrNull(hitIndex))
                    if (thumbs && !side) {
                        ThumbStrip(
                            doc, current, vertical = false,
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                                .background(Prumo.colors.background.copy(alpha = 0.96f)).windowInsetsPadding(WindowInsets.navigationBars),
                        ) { scope.launch { list.scrollToItem(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbStrip(doc: ReaderDoc, current: Int, vertical: Boolean, modifier: Modifier, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        val visible = state.layoutInfo.visibleItemsInfo.map { it.index }
        if (current !in visible) state.scrollToItem(current)
    }
    val px = with(LocalDensity.current) { 72.dp.roundToPx() }
    val cell: @Composable (Int) -> Unit = { i ->
        val (w, h) = doc.sizes[i]
        val bmp by produceState(doc.cached(i, px), i) { if (value == null) value = doc.page(i, px) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(i) }.padding(6.dp)) {
            Box(
                Modifier.width(72.dp).aspectRatio(w / h).background(Color.White)
                    .border(if (i == current) 2.dp else 0.5.dp, if (i == current) Palette.blue else Color.Black.copy(alpha = 0.2f)),
            ) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            }
            Text("${i + 1}", fontSize = 11.sp, color = if (i == current) Palette.blue else Prumo.colors.secondaryText)
        }
    }
    if (vertical) {
        LazyColumn(state = state, modifier = modifier.testTag("thumbStrip")) { items(doc.sizes.size) { cell(it) } }
    } else {
        LazyRow(state = state, modifier = modifier.testTag("thumbStrip")) { items(doc.sizes.size) { cell(it) } }
    }
}

/** Páginas uma embaixo da outra, com pinça para zoom (redesenhadas com mais pixels ao aproximar) e toque duplo. */
@Composable
private fun PagesView(doc: ReaderDoc, list: LazyListState, hits: List<SearchHit>?, currentHit: SearchHit?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val dens = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val widthPx = constraints.maxWidth
        val quality = when {
            scale > 3.5f -> 4f
            scale > 2.5f -> 3f
            scale > 1.5f -> 2f
            else -> 1f
        }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offsetX = 0f
                        } else {
                            scale = 2.5f
                            val limit = size.width * (scale - 1f) / 2f
                            offsetX = ((size.width / 2f - it.x) * (scale - 1f)).coerceIn(-limit, limit)
                        }
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                val factor = newScale / scale
                                val cx = centroid.x - size.width / 2f
                                offsetX = offsetX * factor + cx * (1 - factor) + pan.x
                                scale = newScale
                                list.dispatchRawDelta(-pan.y / scale)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (fingers == 1 && scale > 1.01f) {
                                offsetX += event.calculatePan().x
                            }
                            val limit = size.width * (scale - 1f) / 2f
                            offsetX = offsetX.coerceIn(-limit, limit)
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).testTag("reader"),
            ) {
                items(doc.sizes.size) { i ->
                    val (w, h) = doc.sizes[i]
                    val base = (widthPx - 24 * dens).roundToInt().coerceAtLeast(1)
                    val px = (base * quality).roundToInt().coerceAtMost(4096)
                    // Mostra logo a versão já desenhada (menor) enquanto a mais nítida fica pronta.
                    val preview = (base / 4).coerceAtLeast(64)
                    val bmp by produceState(doc.cached(i, px) ?: doc.cached(i, base) ?: doc.cached(i, preview), i, px) {
                        // Primeiro uma versão pequena (rápida), depois a nítida.
                        if (value == null) doc.page(i, preview)?.let { value = it }
                        val sharp = doc.page(i, px)
                        if (sharp != null) value = sharp
                    }
                    val pageHits = hits?.filter { it.page == i }.orEmpty()
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(w / h)
                            .shadow(2.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val b = bmp
                        if (b != null) {
                            Image(b.asImageBitmap(), LF("Página {} de {}", i + 1, doc.sizes.size), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        } else {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                        if (pageHits.isNotEmpty()) {
                            Canvas(Modifier.fillMaxSize()) {
                                for (hit in pageHits) {
                                    val color = if (hit == currentHit) Color(0xFFFF9800).copy(alpha = 0.45f) else Palette.yellow.copy(alpha = 0.45f)
                                    for (r in hit.rects) {
                                        drawRect(
                                            color, Offset(r.left * size.width, r.top * size.height),
                                            Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}
