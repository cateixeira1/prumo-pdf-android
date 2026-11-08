package br.com.prumopdf.core

import java.io.File

/** Informações que o app guarda sobre cada PDF (o arquivo em si fica na pasta da biblioteca). */
data class DocMeta(
    val id: String,
    val path: String,                    // caminho relativo, ex.: "Clientes/Petição.pdf"
    val favorite: Boolean = false,
    val pendingOCR: Boolean = false,     // o usuário escolheu "Mais tarde"
    val isOCRResult: Boolean = false,    // arquivo criado pelo OCR do app
    val ocrCopyId: String? = null,       // cópia com OCR gerada a partir deste arquivo
    val sourceId: String? = null,        // arquivo de origem (para os arquivos com OCR)
    val pageCount: Int? = null,
    val locked: Boolean? = null,         // PDF protegido por senha
    val signature: String? = null,       // tamanho + data; quando muda, o arquivo é analisado de novo
    val lastOpened: Long? = null,        // milissegundos desde 1970
    val added: Long? = null,
)

data class PdfItem(
    val id: String,
    val file: File,
    val relativePath: String,
    val size: Long,
    val modified: Long,
    val meta: DocMeta,
) {
    val name: String get() = relativePath.substringAfterLast('/').let { it.substring(0, it.length - 4) }
    val folder: String get() = if ('/' in relativePath) relativePath.substringBeforeLast('/') else ""

    /** Muda sempre que o arquivo muda (usado no cache de miniaturas). */
    val version: String get() = "$size-$modified"
    val pageCount: Int? get() = meta.pageCount
    val lastActivity: Long get() = maxOf(meta.lastOpened ?: 0L, modified)
    val isLocked: Boolean get() = meta.locked == true
}

data class FolderItem(
    val path: String,
    val docCount: Int,        // PDFs diretamente nesta pasta
    val totalDocCount: Int,   // incluindo subpastas
    val subfolderCount: Int,
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = if ('/' in path) path.substringBeforeLast('/') else ""
    val depth: Int get() = path.count { it == '/' }
}

enum class DocList { ALL, RECENT, PENDING_OCR, OCR, FAVORITES }

data class LibrarySnapshot(
    val items: List<PdfItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val analyzing: Boolean = false,
    val revision: Long = 0,
)

/** O que a análise de um PDF descobriu (número de páginas, senha). */
data class Analysis(val pageCount: Int?, val locked: Boolean)

/** Erro com mensagem para mostrar ao usuário (já traduzida). */
class UserError(message: String) : Exception(message)

/** PDFs tirados da biblioteca por "Excluir", guardados para o "Desfazer". */
data class TrashBatch(val id: String, val entries: List<Pair<String, DocMeta?>>, val folders: List<String>)
