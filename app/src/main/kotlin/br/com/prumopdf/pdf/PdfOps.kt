package br.com.prumopdf.pdf

import br.com.prumopdf.core.Analysis
import br.com.prumopdf.core.InsertPosition
import br.com.prumopdf.core.PageEntry
import br.com.prumopdf.core.SplitPlan
import br.com.prumopdf.core.UserError
import br.com.prumopdf.i18n.L
import java.io.File

/**
 * Operações com páginas, sem interface. As que mudam o próprio PDF (organizar, remover, incluir) abrem o
 * arquivo e o regravam inteiro em [out]: marcadores, campos de formulário e informações do documento
 * continuam. As que criam um PDF novo (extrair, dividir, combinar) copiam só as páginas.
 */
object PdfOps {
    fun open(file: File): PdfDoc = when (val r = Pdfium.open(file)) {
        is Pdfium.Opened.Ok -> r.doc
        is Pdfium.Opened.Password -> throw UserError(L("Este PDF está protegido por senha. Remova a senha antes de editar."))
        is Pdfium.Opened.Failed -> throw UserError(L("Não foi possível abrir o PDF. O arquivo pode estar corrompido."))
    }

    /** Número de páginas de um PDF gravado (para conferir antes de trocar o arquivo), ou null. */
    fun pageCount(file: File): Int? = Pdfium.openOrNull(file)?.use { it.pageCount }

    /**
     * Reorganiza: a lista diz, na nova ordem, de qual página (índice a partir de 0) vem cada uma e quanto
     * girar a mais. Páginas que não estão na lista são excluídas.
     */
    fun arrange(src: File, entries: List<PageEntry>, out: File) {
        if (entries.isEmpty()) throw UserError(L("O documento precisa ter pelo menos uma página."))
        open(src).use { doc ->
            val n = doc.pageCount
            if (entries.any { it.source !in 0 until n } || entries.map { it.source }.toSet().size != entries.size) {
                throw UserError(L("Página inválida na organização."))
            }
            for (e in entries) {
                if (e.extraQuarters % 4 != 0) doc.setRotation(e.source, doc.rotation(e.source) + e.extraQuarters)
            }
            val kept = entries.map { it.source }.toSet()
            for (i in (0 until n).reversed()) if (i !in kept) doc.deletePage(i)
            // índice de cada página que ficou, depois das exclusões
            val newIndex = (0 until n).filter { it in kept }.withIndex().associate { (pos, original) -> original to pos }
            val order = entries.map { newIndex.getValue(it.source) }
            if (order != order.indices.toList()) doc.movePages(order.toIntArray(), 0)
            doc.save(out)
        }
    }

    /** Remove as páginas indicadas (começando em 1). Sempre fica pelo menos uma página. */
    fun remove(src: File, pages: Collection<Int>, out: File) {
        val count = pageCount(src) ?: throw UserError(L("Não foi possível abrir o PDF. O arquivo pode estar corrompido."))
        val drop = pages.filter { it in 1..count }.toSet()
        if (drop.size >= count) throw UserError(L("Não é possível remover todas as páginas. Para isso, exclua o documento."))
        arrange(src, (0 until count).filter { it + 1 !in drop }.map { PageEntry(it) }, out)
    }

    /** Novo PDF só com as páginas indicadas (começando em 1), na ordem dada. */
    fun extract(src: File, pages: List<Int>, out: File) {
        open(src).use { doc ->
            val idx = pages.filter { it in 1..doc.pageCount }.map { it - 1 }
            if (idx.isEmpty()) throw UserError(L("Informe ao menos uma página."))
            Pdfium.newDocument().use { dest ->
                dest.importPages(doc, idx.toIntArray(), 0)
                dest.save(out)
            }
        }
    }

    /** Divide em [parts] PDFs de tamanho o mais igual possível; [out] recebe o número da parte (1, 2…). */
    fun split(src: File, parts: Int, out: (Int) -> File): List<File> {
        open(src).use { doc ->
            val ranges = SplitPlan.ranges(doc.pageCount, parts)
            return ranges.mapIndexed { i, range ->
                val f = out(i + 1)
                Pdfium.newDocument().use { dest ->
                    dest.importPages(doc, range.map { it - 1 }.toIntArray(), 0)
                    dest.save(f)
                }
                f
            }
        }
    }

    /** Junta os PDFs, na ordem dada. */
    fun merge(sources: List<File>, out: File) {
        if (sources.size < 2) throw UserError(L("Escolha pelo menos 2 PDFs."))
        Pdfium.newDocument().use { dest ->
            for (s in sources) {
                open(s).use { doc ->
                    dest.importPages(doc, IntArray(doc.pageCount) { it }, dest.pageCount)
                }
            }
            dest.save(out)
        }
    }

    /** Inclui páginas de [source] (começando em 1) em [target], na posição indicada. */
    fun insert(target: File, source: File, pages: List<Int>, position: InsertPosition, out: File) {
        open(target).use { doc ->
            open(source).use { src ->
                val idx = pages.filter { it in 1..src.pageCount }.map { it - 1 }
                if (idx.isEmpty()) throw UserError(L("Informe ao menos uma página."))
                doc.importPages(src, idx.toIntArray(), position.index(doc.pageCount))
            }
            doc.save(out)
        }
    }

    /** Cópia sem senha: abre com a senha e grava sem a criptografia. */
    fun removePassword(src: File, password: String, out: File) {
        when (val r = Pdfium.open(src, password)) {
            is Pdfium.Opened.Ok -> r.doc.use { it.saveWithoutPassword(out) }
            is Pdfium.Opened.Password -> throw UserError(L("Senha incorreta. Tente de novo (maiúsculas e minúsculas fazem diferença)."))
            is Pdfium.Opened.Failed -> throw UserError(L("Não foi possível abrir o PDF. O arquivo pode estar corrompido."))
        }
    }

    /** Abaixo disso a página é tratada como imagem (sem texto selecionável). Mesmas regras do iPhone. */
    private const val MIN_TEXT_CHARS = 20L
    /** Página com imagem grande e menos texto que isso: imagem com carimbo digital (ex.: rodapé do PJe). */
    private const val STAMP_MAX_CHARS = 1500L
    private const val LARGE_IMAGE_PIXELS = 400_000L

    /** Número de páginas, senha, páginas sem texto (precisam de OCR) e o texto todo, para a busca. */
    fun analyze(file: File, password: String? = null): Analysis? = when (val r = Pdfium.open(file, password)) {
        is Pdfium.Opened.Password -> Analysis(pageCount = null, locked = true)
        is Pdfium.Opened.Failed -> null
        is Pdfium.Opened.Ok -> r.doc.use { doc ->
            val ocr = mutableListOf<Int>()
            val text = StringBuilder()
            for (i in 0 until doc.pageCount) {
                val (chars, image) = doc.pageStats(i)
                if (chars < MIN_TEXT_CHARS || (chars < STAMP_MAX_CHARS && image >= LARGE_IMAGE_PIXELS)) ocr += i + 1
                text.append(doc.text(i)).append('\n')
            }
            Analysis(doc.pageCount, locked = false, ocrPages = ocr, text = text.toString())
        }
    }
}
