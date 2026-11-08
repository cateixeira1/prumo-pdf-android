package br.com.prumopdf.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.prumopdf.AppViewModel
import br.com.prumopdf.core.LibrarySnapshot
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * PDF aberto no leitor. O PdfRenderer só desenha uma página de cada vez, então tudo roda numa linha de
 * execução própria. Provisório até o leitor com PDFium (etapa 2).
 */
private class ReaderDoc(file: File) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    val sizes: List<Pair<Int, Int>>
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        sizes = executor.submit<List<Pair<Int, Int>>> {
            try {
                val f = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fd = f
                val r = PdfRenderer(f)
                renderer = r
                (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
            } catch (_: Throwable) {
                emptyList()
            }
        }.get()
    }

    suspend fun page(index: Int, widthPx: Int): Bitmap? {
        val key = "$index-$widthPx"
        cache.get(key)?.let { return it }
        return withContext(dispatcher) {
            cache.get(key) ?: renderer?.let { r ->
                br.com.prumopdf.platform.PdfPages.renderPage(r, index, widthPx)?.also { cache.put(key, it) }
            }
        }
    }

    override fun close() {
        executor.execute {
            runCatching { renderer?.close() }
            runCatching { fd?.close() }
            cache.evictAll()
        }
        executor.shutdown()
    }
}

@Composable
fun ReaderScreen(vm: AppViewModel, snapshot: LibrarySnapshot, id: String, showBack: Boolean) {
    val item = remember(snapshot.revision, id) { vm.library.item(id) } ?: return
    val doc = remember(item.id, item.version) { ReaderDoc(item.file) }
    DisposableEffect(doc) { onDispose { doc.close() } }
    val list = rememberLazyListState()
    val current by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    Column(Modifier.fillMaxSize().background(Prumo.colors.background)) {
        ScreenHeader(item.name, showBack, { vm.back() }, large = false) {
            if (doc.sizes.isNotEmpty()) {
                Text(
                    LF("Página {} de {}", current, doc.sizes.size), fontSize = 13.sp, color = Prumo.colors.secondaryText,
                    modifier = Modifier.padding(end = 12.dp).testTag("pageIndicator"),
                )
            }
        }
        if (doc.sizes.isEmpty()) {
            EmptyState(if (item.isLocked) L("Este PDF tem senha. A leitura de PDFs com senha chega na próxima versão.") else L("Não foi possível abrir este PDF."))
            return@Column
        }
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        val dens = LocalDensity.current.density
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val widthPx = constraints.maxWidth
            // A página é desenhada de novo com mais pixels quando o zoom passa de 1,5× e 3×.
            val quality = when {
                scale > 3f -> 3
                scale > 1.5f -> 2
                else -> 1
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
                                    offsetX = (offsetX * factor + cx * (1 - factor) + pan.x)
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
                        val px = ((widthPx - 24 * dens).roundToInt() * quality).coerceAtLeast(1)
                        val bmp by produceState<Bitmap?>(null, i, px) { value = doc.page(i, px) }
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h.toFloat())
                                .shadow(2.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            bmp?.let { Image(it.asImageBitmap(), LF("Página {} de {}", i + 1, doc.sizes.size), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        }
                    }
                }
            }
        }
    }
}
