package br.com.prumopdf.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.Closeable
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/** Funções em C++ (app/src/main/cpp/prumo_pdfium.cpp). Só devem ser chamadas pela linha de execução do [Pdfium]. */
internal object PdfiumNative {
    external fun init()
    external fun open(path: String, password: String?): Long
    external fun lastError(): Int
    external fun newDocument(): Long
    external fun close(doc: Long)
    external fun pageCount(doc: Long): Int
    external fun pageSize(doc: Long, index: Int): FloatArray
    external fun rotation(doc: Long, index: Int): Int
    external fun render(doc: Long, index: Int, bitmap: Bitmap, startX: Int, startY: Int, sizeX: Int, sizeY: Int, annotations: Boolean): Boolean
    external fun pageText(doc: Long, index: Int): String
    external fun textRects(doc: Long, index: Int, start: Int, count: Int): FloatArray
    external fun pageStats(doc: Long, index: Int): LongArray
    external fun deletePage(doc: Long, index: Int): Boolean
    external fun movePages(doc: Long, indices: IntArray, dest: Int): Boolean
    external fun setRotation(doc: Long, index: Int, rotation: Int): Boolean
    external fun importPages(dest: Long, src: Long, indices: IntArray, at: Int): Boolean
    external fun save(doc: Long, path: String, removeSecurity: Boolean): Boolean
    external fun securityRevision(doc: Long): Int
}

/**
 * PDFium, o motor de PDF do Prumo (o mesmo do Windows). O PDFium não aceita duas chamadas ao mesmo tempo,
 * então tudo passa por uma linha de execução só ("pdfium").
 */
object Pdfium {
    private const val THREAD = "pdfium"
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, THREAD).apply { isDaemon = true } }
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    // Códigos de erro do PDFium (fpdfview.h).
    const val ERR_FILE = 2
    const val ERR_FORMAT = 3
    const val ERR_PASSWORD = 4
    const val ERR_SECURITY = 5

    init {
        System.loadLibrary("prumo")
        // O PDFium é iniciado na primeira chamada, já na linha de execução dele (ver [ready]). Iniciar aqui,
        // esperando a outra linha de execução enquanto esta classe ainda está sendo carregada, travava o app:
        // o código da outra linha precisa da classe pronta (a JVM faz ela esperar) e as duas ficavam paradas.
    }

    private var initialized = false   // só lido e mudado na linha de execução do PDFium

    private fun ready() {
        if (!initialized) {
            PdfiumNative.init()
            initialized = true
        }
    }

    /** Roda [block] na linha de execução do PDFium e espera o resultado. */
    fun <T> call(block: () -> T): T {
        if (Thread.currentThread().name == THREAD) {
            ready()
            return block()
        }
        val asked = System.nanoTime()
        var started = 0L
        try {
            return executor.submit(Callable {
                started = System.nanoTime()
                ready()
                block()
            }).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } finally {
            val now = System.nanoTime()
            val waited = (started - asked) / 1_000_000
            val ran = (now - started) / 1_000_000
            if (waited > 300 || ran > 300) android.util.Log.i("Prumo", "PDFium: esperou $waited ms, levou $ran ms")
        }
    }

    sealed interface Opened {
        data class Ok(val doc: PdfDoc) : Opened
        /** Pede senha (sem senha) ou a senha está errada (com senha). */
        data class Password(val wrong: Boolean) : Opened
        data class Failed(val code: Int) : Opened
    }

    fun open(file: File, password: String? = null): Opened = call {
        val h = PdfiumNative.open(file.absolutePath, password)
        if (h != 0L) {
            Opened.Ok(PdfDoc(h))
        } else {
            val code = PdfiumNative.lastError()
            if (code == ERR_PASSWORD) Opened.Password(wrong = password != null) else Opened.Failed(code)
        }
    }

    /** Abre o PDF ou devolve null (senha, arquivo corrompido). */
    fun openOrNull(file: File, password: String? = null): PdfDoc? = (open(file, password) as? Opened.Ok)?.doc

    fun newDocument(): PdfDoc = call { PdfDoc(PdfiumNative.newDocument()) }
}

/** Um PDF aberto no PDFium. Fechar com [close] (ou `use`). */
class PdfDoc internal constructor(internal val handle: Long) : Closeable {
    @Volatile private var closed = false

    private fun <T> call(block: () -> T): T {
        check(!closed) { "documento fechado" }
        return Pdfium.call(block)
    }

    val pageCount: Int get() = call { PdfiumNative.pageCount(handle) }

    /** Largura e altura da página como aparece (com o giro), em pontos. */
    fun pageSize(index: Int): Pair<Float, Float> = call {
        val s = PdfiumNative.pageSize(handle, index)
        s[0] to s[1]
    }

    fun pageSizes(): List<Pair<Float, Float>> = call { (0 until PdfiumNative.pageCount(handle)).map { pageSize(it) } }

    fun rotation(index: Int): Int = call { PdfiumNative.rotation(handle, index) }

    /**
     * Desenha a página no [bitmap] (ARGB_8888). A página inteira teria [sizeX] x [sizeY] pixels; o bitmap
     * mostra o trecho a partir de ([startX], [startY]).
     */
    fun render(index: Int, bitmap: Bitmap, startX: Int = 0, startY: Int = 0, sizeX: Int = bitmap.width, sizeY: Int = bitmap.height): Boolean =
        call { PdfiumNative.render(handle, index, bitmap, startX, startY, sizeX, sizeY, true) }

    /** Página inteira com a largura pedida, em fundo branco. */
    fun renderPage(index: Int, widthPx: Int): Bitmap? = call {
        if (index !in 0 until PdfiumNative.pageCount(handle)) return@call null
        val (w, h) = pageSize(index)
        if (w <= 0f || h <= 0f) return@call null
        val bw = widthPx.coerceAtLeast(1)
        val bh = (bw * h / w).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        if (PdfiumNative.render(handle, index, bmp, 0, 0, bw, bh, true)) bmp else null
    }

    /** Texto da página: cada caractere corresponde a um índice do PDFium (para [textRects]). */
    fun text(index: Int): String = call { PdfiumNative.pageText(handle, index) }

    /** Retângulos de um trecho do texto, em frações da página (0 a 1, y para baixo). */
    fun textRects(index: Int, start: Int, count: Int): List<RectF> = call {
        val a = PdfiumNative.textRects(handle, index, start, count)
        (0 until a.size / 4).map { RectF(a[it * 4], a[it * 4 + 1], a[it * 4 + 2], a[it * 4 + 3]) }
    }

    /** Para a análise: caracteres que não são espaço e pixels da maior imagem da página. */
    fun pageStats(index: Int): Pair<Long, Long> = call {
        val a = PdfiumNative.pageStats(handle, index)
        a[0] to a[1]
    }

    // region Edição (o arquivo só muda em [save])

    fun deletePage(index: Int) = call { check(PdfiumNative.deletePage(handle, index)) { "página $index" } }

    /** Leva as páginas (na ordem dada) para começar na posição [dest]. */
    fun movePages(indices: IntArray, dest: Int) = call { check(PdfiumNative.movePages(handle, indices, dest)) { "mover páginas" } }

    /** Giro absoluto da página, em quartos de volta no sentido horário (0 a 3). */
    fun setRotation(index: Int, quarters: Int) = call { check(PdfiumNative.setRotation(handle, index, quarters)) { "girar" } }

    /** Copia páginas de [src] (índices a partir de 0) para este documento, a partir de [at]. */
    fun importPages(src: PdfDoc, indices: IntArray, at: Int) = call {
        check(PdfiumNative.importPages(handle, src.handle, indices, at)) { "incluir páginas" }
    }

    fun save(file: File) = call { check(PdfiumNative.save(handle, file.absolutePath, false)) { "gravar ${file.name}" } }

    /** Grava uma cópia sem a senha (o documento foi aberto com a senha certa). */
    fun saveWithoutPassword(file: File) = call { check(PdfiumNative.save(handle, file.absolutePath, true)) { "gravar ${file.name}" } }

    /** Se o PDF tem criptografia (/Encrypt): revisão do método, ou -1 se não tem. */
    val securityRevision: Int get() = call { PdfiumNative.securityRevision(handle) }

    // endregion

    override fun close() {
        if (closed) return
        closed = true
        Pdfium.call { PdfiumNative.close(handle) }
    }
}
