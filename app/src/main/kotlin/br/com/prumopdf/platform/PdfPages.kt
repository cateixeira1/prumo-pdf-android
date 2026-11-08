package br.com.prumopdf.platform

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import br.com.prumopdf.core.Analysis
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Páginas do PDF desenhadas pelo PdfRenderer do próprio Android. É provisório: na etapa 2 o desenho passa
 * para o PDFium (o mesmo motor do Windows), que abre PDFs com senha e tem texto, busca e destaques.
 */
object PdfPages {
    /** Número de páginas e se o PDF pede senha. */
    fun analyze(file: File): Analysis? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { Analysis(it.pageCount, locked = false) }
        }
    } catch (_: SecurityException) {
        Analysis(pageCount = null, locked = true)       // pede senha (ou usa uma proteção que o Android não abre)
    } catch (_: Throwable) {
        null
    }

    /** Tamanho das páginas em pontos (1/72 de polegada). */
    fun pageSizes(file: File): List<Pair<Int, Int>> = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** Desenha a página com a largura pedida (em pixels), em fundo branco. */
    fun render(file: File, page: Int, widthPx: Int): Bitmap? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r -> renderPage(r, page, widthPx) }
        }
    } catch (e: Throwable) {
        android.util.Log.w("Prumo", "página ${page + 1} de ${file.name} não foi desenhada", e)
        null
    }

    fun renderPage(r: PdfRenderer, page: Int, widthPx: Int): Bitmap? {
        if (page !in 0 until r.pageCount) return null
        return r.openPage(page).use { p ->
            val w = max(1, widthPx)
            val h = max(1, (w.toFloat() * p.height / p.width).roundToInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }
}

/**
 * Miniaturas da primeira página: na memória (até 1/16 da memória do app) e no disco, em
 * cache/thumbs/<id>_<versão>_<largura>.png. O nome começa pelo id do PDF para que tudo dele seja apagado
 * quando ele sai da biblioteca.
 */
class Thumbnails(private val dir: File) {
    private val renderLock = Any()
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        dir.mkdirs()
    }

    private fun key(id: String, version: String, width: Int) = "${id}_${version}_$width"

    fun cached(id: String, version: String, width: Int): Bitmap? = memory.get(key(id, version, width))

    fun load(id: String, version: String, file: File, width: Int): Bitmap? {
        val k = key(id, version, width)
        memory.get(k)?.let { return it }
        val disk = File(dir, "$k.png")
        if (disk.exists()) {
            android.graphics.BitmapFactory.decodeFile(disk.path)?.let {
                memory.put(k, it)
                return it
            }
        }
        // Um desenho por vez: a lista e a tela do documento podem pedir o mesmo PDF ao mesmo tempo
        // (duas colunas em tablet e dobrável), e o PdfRenderer não é feito para isso.
        val bmp = synchronized(renderLock) {
            memory.get(k) ?: (PdfPages.render(file, 0, width) ?: PdfPages.render(file, 0, width))
        } ?: return null
        memory.put(k, bmp)
        runCatching {
            // versões antigas deste PDF (o arquivo mudou); outros tamanhos da mesma versão ficam
            dir.listFiles { f -> f.name.startsWith("${id}_") && !f.name.startsWith("${id}_${version}_") }?.forEach { it.delete() }
            val tmp = File(dir, ".${disk.name}.tmp")
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(disk)
        }
        return bmp
    }

    /** Apaga as miniaturas dos PDFs que não estão mais na biblioteca. */
    fun prune(keepIds: Set<String>) {
        dir.listFiles()?.forEach { f ->
            val id = f.name.substringBefore('_')
            if (id !in keepIds) f.delete()
        }
    }
}
