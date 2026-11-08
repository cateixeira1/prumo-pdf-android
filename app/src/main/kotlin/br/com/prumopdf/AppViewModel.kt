package br.com.prumopdf

import android.app.Application
import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.prumopdf.core.DocList
import br.com.prumopdf.core.Library
import br.com.prumopdf.core.PdfItem
import br.com.prumopdf.core.TrashBatch
import br.com.prumopdf.core.UserError
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.i18n.LN
import br.com.prumopdf.platform.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Telas do app. As de "lista" ficam à esquerda em tela larga; as de documento, à direita. */
sealed interface Route {
    val isDetail: Boolean get() = false

    data object Home : Route
    data class List(val list: DocList) : Route
    data object Folders : Route
    data class Folder(val path: String) : Route
    data object Settings : Route
    data class Document(val id: String) : Route {
        override val isDetail get() = true
    }
    data class Reader(val id: String) : Route {
        override val isDetail get() = true
    }
}

/** Ferramentas da tela inicial (as mesmas do iPhone e do Windows). As perguntas passam por L() na hora de mostrar. */
enum class Tool(val question: String, val ready: Boolean = false) {
    READ("Ler qual PDF?", ready = true), // i18n
    OCR("OCR de quais PDFs?"), // i18n
    ORGANIZE("Organizar qual PDF?"), // i18n
    INSERT("Incluir páginas em qual PDF?"), // i18n
    REMOVE("Remover páginas de qual?"), // i18n
    EXTRACT("Extrair páginas de qual?"), // i18n
    SPLIT("Dividir qual PDF?"), // i18n
    MERGE("Combinar PDFs"), // i18n
    CROP("Cortar ou girar qual PDF?"), // i18n
    NUMBER("Numerar qual PDF?"), // i18n
    SIGN("Assinar qual PDF?"), // i18n
    COMPRESS("Comprimir qual PDF?"), // i18n
    CONVERT("Converter em PDF"), // i18n
}

data class Notice(val text: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prumo = app as PrumoApplication
    val library: Library = prumo.library
    val thumbnails = prumo.thumbnails
    val state = library.state

    val stack = mutableStateListOf<Route>(Route.Home)
    var notice by mutableStateOf<Notice?>(null)
        private set

    /** Pasta onde o próximo "Importar" vai pôr os PDFs (a pasta aberta, ou a raiz). */
    var importFolder by mutableStateOf("")

    private var analysisJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            library.purgeTrash()
            library.reload()
            analyze()
        }
    }

    // region Navegação

    val current: Route get() = stack.last()

    fun open(route: Route, wide: Boolean = false) {
        if (wide) {
            // Em tela larga só existe um documento aberto à direita; abrir outra lista fecha o documento.
            if (route.isDetail) {
                if (route is Route.Document) stack.removeAll { it.isDetail }
            } else {
                stack.removeAll { it.isDetail }
            }
        }
        if (stack.lastOrNull() != route) stack.add(route)
        if (route is Route.Reader) viewModelScope.launch(Dispatchers.IO) { library.markOpened(route.id) }
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() {
        stack.clear()
        stack.add(Route.Home)
    }

    /** Fecha as telas de um PDF que não existe mais (excluído aqui ou em outro aparelho). */
    private fun dropMissing() {
        val ids = library.items.map { it.id }.toSet()
        stack.removeAll { (it is Route.Document && it.id !in ids) || (it is Route.Reader && it.id !in ids) }
        val folders = library.folders.map { it.path }.toSet()
        stack.removeAll { it is Route.Folder && it.path !in folders }
        if (stack.isEmpty()) stack.add(Route.Home)
    }

    // endregion

    // region Mensagens

    fun say(text: String, undo: (() -> Unit)? = null) {
        notice = Notice(text, undo)
    }

    fun clearNotice(n: Notice) {
        if (notice?.id == n.id) notice = null
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: UserError) {
                say(e.message ?: "")
            } catch (e: Exception) {
                say(sentences(L("Não foi possível concluir."), e.localizedMessage ?: ""))
            }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // endregion

    // region Análise

    fun analyze() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.IO) {
            val pending = library.pendingAnalysis()
            if (pending.isEmpty()) return@launch
            library.setAnalyzing(true)
            for (item in pending) {
                val a = PdfPages.analyze(item.file)
                library.applyAnalysis(item.id, Library.signature(item.size, item.modified), a)
            }
            library.setAnalyzing(false)
            thumbnails.prune(library.items.map { it.id }.toSet())
        }
    }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        library.reload()
        withContext(Dispatchers.Main) { dropMissing() }
        analyze()
    }

    // endregion

    // region Ações

    fun toggleFavorite(item: PdfItem) = run { io { library.toggleFavorite(item.id) } }

    fun rename(item: PdfItem, name: String) = run {
        io { library.rename(item.id, name) }
    }

    fun move(items: Collection<PdfItem>, folder: String) = run {
        val n = io { library.move(items.map { it.id }, folder) }
        if (n > 0) say(LN(n, "1 PDF movido.", "{} PDFs movidos."))
    }

    fun copy(items: Collection<PdfItem>, folder: String) = run {
        val out = io { library.copy(items.map { it.id }, folder) }
        if (out.isNotEmpty()) say(LN(out.size, "1 PDF copiado.", "{} PDFs copiados."))
        analyze()
    }

    fun duplicate(item: PdfItem) = run {
        io { library.duplicate(item.id) }
        analyze()
    }

    fun delete(items: Collection<PdfItem>) = run {
        val batch: TrashBatch = io { library.delete(items.map { it.id }) }
        dropMissing()
        val n = batch.entries.size
        if (n > 0) {
            say(LN(n, "1 PDF excluído.", "{} PDFs excluídos.")) { undoDelete(batch) }
        }
    }

    private fun undoDelete(batch: TrashBatch) = run {
        io { library.undoDelete(batch) }
        analyze()
    }

    fun createFolder(parent: String, name: String, then: (String) -> Unit = {}) = run {
        val path = io { library.createFolder(parent, name) }
        then(path)
    }

    fun renameFolder(path: String, name: String) = run {
        val newPath = io { library.renameFolder(path, name) }
        val i = stack.indexOfFirst { it is Route.Folder && it.path == path }
        if (i >= 0) stack[i] = Route.Folder(newPath)
    }

    fun deleteFolder(path: String) = run {
        val batch = io { library.deleteFolder(path) }
        dropMissing()
        say(LF("Pasta “{}” excluída.", path.substringAfterLast('/'))) { undoDelete(batch) }
    }

    /** Copia para a biblioteca os PDFs que vieram do seletor do Android, de "Abrir com" ou de "Compartilhar". */
    fun importUris(uris: kotlin.collections.List<Uri>, folder: String = importFolder, openSingle: Boolean = false) = run {
        val resolver = getApplication<Application>().contentResolver
        val result = io {
            val ok = mutableListOf<PdfItem>()
            var failed = 0
            for (uri in uris) {
                val name = displayName(resolver, uri) ?: L("Documento")
                val item = runCatching {
                    resolver.openInputStream(uri)?.let { library.import(name, it, folder) }
                }.getOrNull()
                if (item == null) failed++ else ok += item
            }
            ok to failed
        }
        val (ok, failed) = result
        val parts = mutableListOf<String>()
        if (ok.isNotEmpty()) {
            parts += if (folder.isEmpty()) LN(ok.size, "1 PDF importado.", "{} PDFs importados.")
            else LN(ok.size, "1 PDF importado para {}.", "{} PDFs importados para {}.", folder.substringAfterLast('/'))
        }
        if (failed > 0) parts += LN(failed, "1 arquivo não é PDF e não foi importado.", "{} arquivos não são PDF e não foram importados.")
        if (parts.isNotEmpty()) say(sentences(*parts.toTypedArray()))
        analyze()
        if (openSingle && ok.size == 1) open(Route.Document(ok[0].id))
    }

    fun shareIntent(items: Collection<PdfItem>): Intent {
        val ctx = getApplication<Application>()
        val uris = items.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it.file) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "application/pdf"
        intent.clipData = ClipData.newRawUri("", uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, null)
    }

    // endregion

    companion object {
        fun displayName(resolver: ContentResolver, uri: Uri): String? {
            if (uri.scheme == "file") return uri.lastPathSegment
            return runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
        }

        fun DocList.title(): String = when (this) {
            DocList.ALL -> L("Todos os PDFs")
            DocList.RECENT -> L("Recentes")
            DocList.PENDING_OCR -> L("Sem OCR")
            DocList.OCR -> L("Com OCR")
            DocList.FAVORITES -> L("Favoritos")
        }
    }
}

fun sentences(vararg parts: String) = br.com.prumopdf.i18n.sentences(*parts)
