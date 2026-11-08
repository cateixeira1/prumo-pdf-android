package br.com.prumopdf

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.prumopdf.core.InsertPosition
import br.com.prumopdf.core.PageEntry
import br.com.prumopdf.core.UserError
import br.com.prumopdf.pdf.PdfOps
import br.com.prumopdf.pdf.Pdfium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Operações com páginas no PDFium (o mesmo motor do Windows), conferidas pelo texto de cada página. */
@RunWith(AndroidJUnit4::class)
class PdfOpsTest {
    private val app get() = ApplicationProvider.getApplicationContext<PrumoApplication>()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(app.cacheDir, "ops-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    /** PDF com [n] páginas cujo título é "<nome> <número>". */
    private fun numbered(name: String, n: Int): File {
        val f = File(dir, "$name.pdf")
        val doc = android.graphics.pdf.PdfDocument()
        val paint = android.graphics.Paint().apply { textSize = 24f }
        for (p in 1..n) {
            val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, p).create())
            page.canvas.drawText("$name $p fim da linha com texto suficiente", 60f, 100f, paint)
            doc.finishPage(page)
        }
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    /** Título de cada página ("A 1", "B 3"…). */
    private fun titles(file: File): List<String> = Pdfium.openOrNull(file)!!.use { doc ->
        (0 until doc.pageCount).map { doc.text(it).trim().split(" ").take(2).joinToString(" ") }
    }

    private fun out(name: String) = File(dir, "$name.pdf")

    @Test
    fun arrangeReordersRotatesAndDeletes() {
        val src = numbered("A", 5)
        val dest = out("arranjado")
        PdfOps.arrange(src, listOf(PageEntry(4), PageEntry(0, extraQuarters = 1), PageEntry(2)), dest)
        assertEquals(listOf("A 5", "A 1", "A 3"), titles(dest))
        Pdfium.openOrNull(dest)!!.use { doc ->
            assertEquals(90, doc.rotation(1) * 90)
            assertEquals(0, doc.rotation(0))
            // página girada: largura e altura trocadas
            val (w, h) = doc.pageSize(1)
            assertTrue(w > h)
        }
    }

    @Test
    fun removeExtractSplitMergeInsert() {
        val a = numbered("A", 6)
        val b = numbered("B", 3)
        PdfOps.remove(a, listOf(2, 4), out("removido"))
        assertEquals(listOf("A 1", "A 3", "A 5", "A 6"), titles(out("removido")))

        PdfOps.extract(a, listOf(5, 1), out("extraido"))
        assertEquals(listOf("A 5", "A 1"), titles(out("extraido")))

        val parts = PdfOps.split(a, 4) { out("parte$it") }
        assertEquals(listOf(listOf("A 1", "A 2"), listOf("A 3", "A 4"), listOf("A 5"), listOf("A 6")), parts.map { titles(it) })

        PdfOps.merge(listOf(b, a), out("combinado"))
        assertEquals(9, PdfOps.pageCount(out("combinado")))
        assertEquals(listOf("B 1", "B 2", "B 3", "A 1"), titles(out("combinado")).take(4))

        PdfOps.insert(a, b, listOf(1, 3), InsertPosition.After(2), out("incluido"))
        assertEquals(listOf("A 1", "A 2", "B 1", "B 3", "A 3"), titles(out("incluido")).take(5))
        PdfOps.insert(a, b, listOf(2), InsertPosition.Start, out("inicio"))
        assertEquals("B 2", titles(out("inicio")).first())
    }

    @Test
    fun refusesToRemoveEveryPage() {
        val a = numbered("A", 2)
        try {
            PdfOps.remove(a, listOf(1, 2), out("nada"))
            throw AssertionError("deveria recusar")
        } catch (_: UserError) {
        }
        assertFalse(out("nada").exists())
    }

    @Test
    fun analysisFindsTextAndScannedPages() {
        val a = numbered("Texto", 2)
        val analysis = PdfOps.analyze(a)!!
        assertEquals(2, analysis.pageCount)
        assertTrue(analysis.ocrPages.isEmpty())
        assertTrue(analysis.text.contains("Texto 2"))
        // página só com imagem
        val scan = File(dir, "foto.pdf")
        DemoData.write(scan, "Foto", 2, scan = true)
        assertEquals(listOf(1, 2), PdfOps.analyze(scan)!!.ocrPages)
    }

    @Test
    fun passwordProtectedPdf() {
        val f = File(dir, "protegido.pdf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("protegido.pdf").use { i -> f.outputStream().use { i.copyTo(it) } }
        assertTrue(Pdfium.open(f) is Pdfium.Opened.Password)
        assertTrue((Pdfium.open(f, "errada") as Pdfium.Opened.Password).wrong)
        val ok = Pdfium.open(f, "prumo123")
        assertTrue(ok is Pdfium.Opened.Ok)
        (ok as Pdfium.Opened.Ok).doc.use { assertEquals(2, it.pageCount) }
        assertTrue(PdfOps.analyze(f)!!.locked)
        // cópia sem senha
        PdfOps.removePassword(f, "prumo123", out("sem-senha"))
        val copy = Pdfium.openOrNull(out("sem-senha"))
        assertNotNull(copy)
        copy!!.use {
            assertEquals(2, it.pageCount)
            assertTrue(it.text(0).contains("Documento protegido"))
        }
        try {
            PdfOps.removePassword(f, "errada", out("x"))
            throw AssertionError("deveria recusar")
        } catch (_: UserError) {
        }
    }

    @Test
    fun searchFindsTheWordWithRectangles() {
        val f = File(dir, "contrato.pdf")
        DemoData.write(f, "Contrato", 4)
        Pdfium.openOrNull(f)!!.use { doc ->
            val text = doc.text(2)
            val hits = br.com.prumopdf.core.TextFold.find(text, "clausula decima")
            assertEquals(1, hits.size)
            val rects = doc.textRects(2, hits[0].first, hits[0].last - hits[0].first + 1)
            assertTrue(rects.isNotEmpty())
            val r = rects[0]
            assertTrue(r.left in 0f..1f && r.right in 0f..1f && r.top < r.bottom)
            assertTrue(br.com.prumopdf.core.TextFold.find(doc.text(0), "clausula decima").isEmpty())
        }
    }
}
