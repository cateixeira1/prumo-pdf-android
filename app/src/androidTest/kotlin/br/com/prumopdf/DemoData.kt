package br.com.prumopdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.prumopdf.core.Library
import java.io.File

/** PDFs de exemplo para os testes e as capturas de tela (desenhados aqui, sem nada de fora). */
object DemoData {
    data class Doc(val path: String, val title: String, val pages: Int, val favorite: Boolean = false)

    val docs = listOf(
        Doc("Contrato de locação.pdf", "Contrato de locação", 6, favorite = true),
        Doc("Clientes/Ana Souza/Laudo médico.pdf", "Laudo médico", 3),
        Doc("Clientes/Ana Souza/Prontuário.pdf", "Prontuário", 12),
        Doc("Clientes/Bruno Lima/Petição inicial.pdf", "Petição inicial", 18, favorite = true),
        Doc("Contratos/Prestação de serviços.pdf", "Prestação de serviços", 4),
        Doc("Recibo de aluguel.pdf", "Recibo de aluguel", 1),
        Doc("Relatório anual 2026.pdf", "Relatório anual 2026", 9),
    )

    fun reset(library: Library) {
        library.root.listFiles()?.forEach { it.deleteRecursively() }
        File(library.supportDir, "library.json").delete()
        library.reload()
        for (d in docs) write(File(library.root, d.path), d.title, d.pages)
        library.reload()
        val ids = docs.filter { it.favorite }.mapNotNull { library.itemAt(it.path)?.id }
        library.setFavorite(ids, true)
    }

    fun write(file: File, title: String, pages: Int) {
        file.parentFile?.mkdirs()
        val doc = PdfDocument()
        val head = Paint().apply { color = Color.rgb(30, 30, 30); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { color = Color.rgb(90, 90, 90); textSize = 10f; isAntiAlias = true }
        val bar = Paint().apply { color = Color.rgb(14, 124, 116) }
        for (p in 1..pages) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, p).create())
            val c = page.canvas
            c.drawRect(0f, 0f, 595f, 10f, bar)
            c.drawText(title, 56f, 90f, head)
            var y = 130f
            for (line in 0 until 46) {
                val w = 420f + ((line * 37 + p * 11) % 60)
                c.drawRect(56f, y, 56f + w, y + 5f, Paint().apply { color = Color.rgb(200, 200, 200) })
                y += 14f
            }
            c.drawText("$p / $pages", 280f, 810f, body)
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
