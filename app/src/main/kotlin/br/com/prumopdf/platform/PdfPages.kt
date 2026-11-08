package br.com.prumopdf.platform

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import br.com.prumopdf.pdf.Pdfium
import java.io.File
import java.io.FileOutputStream

/** Páginas desenhadas pelo PDFium (o mesmo motor do Windows). */
object PdfPages {
    /** Desenha a página com a largura pedida (em pixels), em fundo branco. */
    fun render(file: File, page: Int, widthPx: Int, password: String? = null): Bitmap? = try {
        Pdfium.openOrNull(file, password)?.use { it.renderPage(page, widthPx) }
    } catch (e: Throwable) {
        Log.w("Prumo", "página ${page + 1} de ${file.name} não foi desenhada", e)
        null
    }
}

/**
 * Miniaturas da primeira página: na memória (até 1/16 da memória do app) e no disco, em
 * cache/thumbs/<id>_<versão>_<largura>.png. O nome começa pelo id do PDF para que tudo dele seja apagado
 * quando ele sai da biblioteca.
 */
class Thumbnails(private val dir: File) {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        dir.mkdirs()
    }

    private fun key(id: String, version: String, width: Int) = "${id}_${version}_$width"

    fun cached(id: String, version: String, width: Int): Bitmap? = memory.get(key(id, version, width))

    fun load(id: String, version: String, file: File, width: Int, password: String? = null): Bitmap? {
        val k = key(id, version, width)
        memory.get(k)?.let { return it }
        val disk = File(dir, "$k.png")
        // Miniatura de PDF com senha não fica no disco (mostraria a página a quem abrir a pasta do app).
        if (password == null && disk.exists()) {
            android.graphics.BitmapFactory.decodeFile(disk.path)?.let {
                memory.put(k, it)
                return it
            }
        }
        val bmp = PdfPages.render(file, 0, width, password) ?: return null
        memory.put(k, bmp)
        if (password == null) {
            runCatching {
                // versões antigas deste PDF (o arquivo mudou); outros tamanhos da mesma versão ficam
                dir.listFiles { f -> f.name.startsWith("${id}_") && !f.name.startsWith("${id}_${version}_") }?.forEach { it.delete() }
                val tmp = File(dir, ".${disk.name}.tmp")
                FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tmp.renameTo(disk)
            }
        }
        return bmp
    }

    /** Apaga as miniaturas dos PDFs que não estão mais na biblioteca. */
    fun prune(keepIds: Set<String>) {
        dir.listFiles()?.forEach { f ->
            val id = f.name.removePrefix(".").substringBefore('_')
            if (id !in keepIds) f.delete()
        }
    }

    /** Esquece as miniaturas de um PDF (depois de mudar as páginas). */
    fun forget(id: String) {
        memory.snapshot().keys.filter { it.startsWith("${id}_") }.forEach { memory.remove(it) }
        dir.listFiles { f -> f.name.startsWith("${id}_") }?.forEach { it.delete() }
    }
}
