package br.com.prumopdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.prumopdf.core.Library
import java.io.File

/** PDFs de exemplo para os testes e as capturas de tela (desenhados aqui, sem nada de fora). */
object DemoData {
    /** [scan] = página só com imagem (sem texto selecionável), como uma foto de documento. */
    data class Doc(val path: String, val title: String, val pages: Int, val favorite: Boolean = false, val scan: Boolean = false)

    val docs = listOf(
        Doc("Contrato de locação.pdf", "Contrato de locação", 6, favorite = true),
        Doc("Clientes/Ana Souza/Laudo médico.pdf", "Laudo médico", 3),
        Doc("Clientes/Ana Souza/Prontuário.pdf", "Prontuário", 12),
        Doc("Clientes/Bruno Lima/Petição inicial.pdf", "Petição inicial", 18, favorite = true),
        Doc("Contratos/Prestação de serviços.pdf", "Prestação de serviços", 4),
        Doc("Recibo de aluguel.pdf", "Recibo de aluguel", 1),
        Doc("Relatório anual 2026.pdf", "Relatório anual 2026", 9),
        Doc("Foto do recibo.pdf", "Foto do recibo", 2, scan = true),
    )

    /** Palavra que só aparece na página 3 do contrato (para os testes da busca). */
    const val SEARCH_WORD = "Cláusula décima"

    private val LINES = listOf(
        "As partes acima identificadas têm entre si justo e acertado o presente instrumento,",
        "que se regerá pelas cláusulas seguintes e pelas condições descritas neste documento.",
        "O pagamento será feito até o quinto dia útil de cada mês, por transferência bancária.",
        "Em caso de atraso, incidirá multa de dois por cento e juros de um por cento ao mês.",
        "As despesas de condomínio e os impostos sobre o imóvel ficam a cargo do locatário.",
        "Qualquer alteração deste contrato deverá ser feita por escrito e assinada pelas partes.",
    )

    fun reset(library: Library) {
        library.root.listFiles()?.forEach { it.deleteRecursively() }
        File(library.supportDir, "library.json").delete()
        library.reload()
        for (d in docs) {
            val f = File(library.root, d.path)
            // O contrato vem pronto (texto em Helvetica, que o PDFium extrai), para a busca no documento.
            if (d.path == "Contrato de locação.pdf") asset("contrato.pdf", f) else write(f, d.title, d.pages, d.scan)
        }
        library.reload()
        val ids = docs.filter { it.favorite }.mapNotNull { library.itemAt(it.path)?.id }
        library.setFavorite(ids, true)
    }

    /** Copia um PDF de teste (app/src/androidTest/assets, gerados por tools/make_test_pdfs.py). */
    fun asset(name: String, dest: File) {
        dest.parentFile?.mkdirs()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { i ->
            dest.outputStream().use { i.copyTo(it) }
        }
    }

    fun write(file: File, title: String, pages: Int, scan: Boolean = false) {
        file.parentFile?.mkdirs()
        val doc = PdfDocument()
        val head = Paint().apply { color = Color.rgb(30, 30, 30); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { color = Color.rgb(70, 70, 70); textSize = 10.5f; isAntiAlias = true }
        val bar = Paint().apply { color = Color.rgb(14, 124, 116) }
        for (p in 1..pages) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, p).create())
            val c = page.canvas
            if (scan) {
                // Imagem da página inteira, sem texto: o que uma foto ou um escaneamento produz.
                val bmp = android.graphics.Bitmap.createBitmap(595 * 2, 842 * 2, android.graphics.Bitmap.Config.ARGB_8888)
                val bc = android.graphics.Canvas(bmp)
                bc.drawColor(Color.rgb(246, 244, 238))
                bc.scale(2f, 2f)
                bc.drawText(title, 56f, 90f, head)
                var y = 130f
                for (line in 0 until 40) {
                    bc.drawText(LINES[(line + p) % LINES.size], 56f, y, body)
                    y += 16f
                }
                c.drawBitmap(bmp, null, android.graphics.Rect(0, 0, 595, 842), null)
                bmp.recycle()
            } else {
                c.drawRect(0f, 0f, 595f, 10f, bar)
                c.drawText(title, 56f, 90f, head)
                var y = 130f
                for (line in 0 until 40) {
                    val text = if (p == 3 && line == 5) "$SEARCH_WORD: o contrato vale por trinta meses." else LINES[(line + p) % LINES.size]
                    c.drawText(text, 56f, y, body)
                    y += 16f
                }
                c.drawText("$p / $pages", 280f, 810f, body)
            }
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
