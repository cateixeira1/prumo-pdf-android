package br.com.prumopdf.core

import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.util.UUID

/**
 * Biblioteca de PDFs: arquivos e pastas reais dentro de [root] e metadados guardados à parte, em
 * [supportDir]/library.json. O que vale para outros aparelhos (favorito, OCR) também vai para
 * [root]/.prumo/library.json, no mesmo formato do Prumo para iPhone e Windows (ver LibrarySync.kt).
 *
 * Não depende do Android: os testes rodam no computador.
 */
class Library(
    val root: File,
    val supportDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val metaFile = File(supportDir, "library.json")
    val trashDir = File(supportDir, "Trash")
    val versionsDir = File(supportDir, "Versions")
    val textDir = File(supportDir, "Text")
    private val texts = HashMap<String, String>()
    internal var metas: MutableMap<String, DocMeta> = LinkedHashMap()
    internal val sync = SharedSync(this)

    private val _state = MutableStateFlow(LibrarySnapshot())
    val state: StateFlow<LibrarySnapshot> = _state
    val items: List<PdfItem> get() = _state.value.items
    val folders: List<FolderItem> get() = _state.value.folders

    init {
        root.mkdirs()
        supportDir.mkdirs()
        versionsDir.mkdirs()
        textDir.mkdirs()
        loadMeta()
        reload()
    }

    // region Metadados

    private fun loadMeta() {
        val text = runCatching { metaFile.readText() }.getOrNull() ?: return
        val arr = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return
        val out = LinkedHashMap<String, DocMeta>()
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val m = metaFromJson(o) ?: continue
            out[m.id] = m
        }
        metas = out
    }

    private fun saveMeta() {
        val arr = JsonArray(metas.values.sortedBy { it.path }.map { metaToJson(it) })
        writeAtomic(metaFile, json.encodeToString(JsonArray.serializer(), arr))
        sync.writeShared()
    }

    // endregion

    // region Leitura da pasta

    fun reload() = synchronized(lock) {
        sync.syncFromDisk()
        val files = mutableListOf<Triple<File, String, Pair<Long, Long>>>()
        val dirs = mutableListOf<String>()
        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles() ?: return
            for (f in children) {
                val name = f.name
                if (name.startsWith(".")) continue
                val rel = if (prefix.isEmpty()) name else "$prefix/$name"
                if (f.isDirectory) {
                    dirs += rel
                    walk(f, rel)
                } else if (name.lowercase().endsWith(".pdf") && name.length > 4) {
                    files += Triple(f, rel, f.length() to f.lastModified())
                }
            }
        }
        walk(root, "")

        val byPath = metas.values.associateBy { it.path }
        val newMetas = LinkedHashMap<String, DocMeta>()
        val usedIds = metas.keys.toMutableSet()
        val newItems = mutableListOf<PdfItem>()
        for ((file, rel, sm) in files) {
            val meta = byPath[rel] ?: sync.newSharedMeta(rel, usedIds, clock())
            newMetas[meta.id] = meta
            newItems += PdfItem(meta.id, file, rel, sm.first, sm.second, meta)
        }
        val changed = newMetas != metas
        for (id in metas.keys) if (id !in newMetas) forgetFiles(id)
        metas = newMetas
        sync.knownPaths += newMetas.values.map { it.path.lowercase() }
        val sortedDirs = dirs.sortedWith(naturalOrder)
        val folderItems = sortedDirs.map { path ->
            val prefix = "$path/"
            FolderItem(
                path = path,
                docCount = newItems.count { it.folder == path },
                totalDocCount = newItems.count { it.relativePath.startsWith(prefix) },
                subfolderCount = sortedDirs.count { d -> d.startsWith(prefix) && '/' !in d.substring(prefix.length) },
            )
        }
        publish(newItems.sortedByDescending { it.modified }, folderItems)
        if (changed) saveMeta()
    }

    /** Apaga o que o Prumo guarda de um PDF que saiu da biblioteca (versão anterior, texto). */
    private fun forgetFiles(id: String) {
        File(versionsDir, "$id.pdf").delete()
        File(textDir, "$id.txt").delete()
        texts.remove(id)
    }

    private fun publish(items: List<PdfItem>, folders: List<FolderItem> = this.folders, analyzing: Boolean? = null) {
        val old = _state.value
        _state.value = LibrarySnapshot(items, folders, analyzing ?: old.analyzing, old.revision + 1)
    }

    private fun refreshItems() {
        publish(items.map { it.copy(meta = metas[it.id] ?: it.meta) })
    }

    // endregion

    // region Consultas

    fun item(id: String): PdfItem? = items.firstOrNull { it.id == id }
    fun itemAt(path: String): PdfItem? = items.firstOrNull { it.relativePath == path }
    fun folder(path: String): FolderItem? = folders.firstOrNull { it.path == path }

    fun itemsIn(folder: String): List<PdfItem> =
        items.filter { it.folder == folder }.sortedWith(compareBy(naturalOrder) { it.name })

    fun subfolders(of: String): List<FolderItem> = folders.filter { it.parent == of }

    val topLevelFolders: List<FolderItem> get() = subfolders("")

    fun needsOCRAttention(item: PdfItem): Boolean {
        val m = item.meta
        if (m.locked == true || m.isOCRResult) return false
        if (m.ocrCopyId != null && metas.containsKey(m.ocrCopyId)) return false
        return m.pendingOCR || !m.ocrPages.isNullOrEmpty()
    }

    fun items(list: DocList): List<PdfItem> = when (list) {
        DocList.ALL -> items
        DocList.RECENT -> {
            val limit = clock() - 7L * 24 * 3600 * 1000
            items.filter { it.lastActivity >= limit }.sortedByDescending { it.lastActivity }
        }
        DocList.PENDING_OCR -> items.filter { needsOCRAttention(it) }
        DocList.OCR -> items.filter { it.meta.isOCRResult }
        DocList.FAVORITES -> items.filter { it.meta.favorite }
    }

    fun count(list: DocList): Int = items(list).size

    /** Busca pelo nome e pelo texto dos PDFs (primeiro os que têm a busca no nome). */
    fun search(query: String, pool: List<PdfItem>? = null): List<PdfItem> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        val source = pool ?: items
        val byName = source.filter { fold(it.name).contains(q) }
        val ids = byName.map { it.id }.toSet()
        return byName + source.filter { it.id !in ids && text(it.id).contains(q) }
    }

    /** Texto do PDF já sem acentos e em minúsculas (guardado pela análise). */
    private fun text(id: String): String = synchronized(texts) {
        texts.getOrPut(id) { runCatching { File(textDir, "$id.txt").readText() }.getOrDefault("") }
    }

    // endregion

    // region Análise (número de páginas, senha)

    fun pendingAnalysis(): List<PdfItem> = items.filter { it.meta.signature != signature(it.size, it.modified) }

    fun applyAnalysis(id: String, sig: String, analysis: Analysis?) = synchronized(lock) {
        val m = metas[id] ?: return@synchronized
        metas[id] = m.copy(
            signature = sig, pageCount = analysis?.pageCount, locked = if (analysis?.locked == true) true else null,
            ocrPages = analysis?.ocrPages?.takeIf { it.isNotEmpty() },
        )
        if (analysis != null && !analysis.locked) {
            val folded = fold(analysis.text)
            runCatching { writeAtomic(File(textDir, "$id.txt"), folded) }
            synchronized(texts) { texts[id] = folded }
        }
        refreshItems()
    }

    fun setAnalyzing(on: Boolean) = synchronized(lock) {
        publish(items, analyzing = on)
        if (!on) saveMeta()
    }

    // endregion

    // region Mudanças

    private fun update(id: String, change: (DocMeta) -> DocMeta) {
        val m = metas[id] ?: return
        metas[id] = change(m)
    }

    fun setFavorite(ids: Collection<String>, value: Boolean) = synchronized(lock) {
        for (id in ids) update(id) { it.copy(favorite = value) }
        saveMeta()
        refreshItems()
    }

    fun toggleFavorite(id: String) {
        val m = metas[id] ?: return
        setFavorite(listOf(id), !m.favorite)
    }

    fun markOpened(id: String) = synchronized(lock) {
        update(id) { it.copy(lastOpened = clock()) }
        saveMeta()
        refreshItems()
    }

    fun setPendingOCR(ids: Collection<String>, value: Boolean) = synchronized(lock) {
        for (id in ids) update(id) { it.copy(pendingOCR = value) }
        saveMeta()
        refreshItems()
    }

    fun folderFile(folder: String): File = if (folder.isEmpty()) root else File(root, folder)

    fun relativePath(file: File): String {
        val base = root.canonicalPath
        val path = file.canonicalPath
        return if (path == base) "" else path.removePrefix("$base${File.separator}").replace(File.separatorChar, '/')
    }

    /** Arquivo livre na pasta: "Nome.pdf", "Nome (2).pdf", "Nome (3).pdf"… */
    fun uniqueFile(folder: String, baseName: String, except: File? = null): File {
        val dir = folderFile(folder)
        val clean = sanitize(baseName)
        var candidate = File(dir, "$clean.pdf")
        var n = 2
        while (candidate.exists() && candidate.canonicalPath != except?.canonicalPath) {
            candidate = File(dir, "$clean ($n).pdf")
            n++
        }
        return candidate
    }

    /** Copia um PDF recebido (seletor do Android, "Abrir com", "Compartilhar") para a biblioteca. */
    fun import(name: String, input: InputStream, folder: String = ""): PdfItem? = synchronized(lock) {
        folderFile(folder).mkdirs()
        val dest = uniqueFile(folder, name.substringAfterLast('/'))
        val tmp = File(dest.parentFile, ".${dest.name}.part")
        try {
            input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            if (!looksLikePdf(tmp)) {
                tmp.delete()
                return@synchronized null
            }
            if (!tmp.renameTo(dest)) throw UserError(L("Não foi possível salvar o arquivo."))
        } finally {
            tmp.delete()
        }
        val rel = relativePath(dest)
        reload()
        itemAt(rel)
    }

    fun rename(id: String, newName: String): PdfItem = synchronized(lock) {
        val item = item(id) ?: throw UserError(L("O arquivo não existe mais."))
        val clean = sanitize(newName)
        val dest = File(item.file.parentFile, "$clean.pdf")
        if (dest.canonicalPath == item.file.canonicalPath) return@synchronized item
        val sameIgnoringCase = dest.canonicalPath.equals(item.file.canonicalPath, ignoreCase = true)
        if (dest.exists() && !sameIgnoringCase) throw UserError(L("Já existe um PDF com esse nome nesta pasta."))
        moveFile(item.file, dest)
        update(id) { it.copy(path = relativePath(dest)) }
        saveMeta()
        reload()
        item(id)!!
    }

    fun move(ids: Collection<String>, folder: String): Int = synchronized(lock) {
        var n = 0
        for (id in ids) {
            val item = item(id) ?: continue
            if (item.folder == folder) continue
            folderFile(folder).mkdirs()
            val dest = uniqueFile(folder, item.name)
            moveFile(item.file, dest)
            update(id) { it.copy(path = relativePath(dest)) }
            n++
        }
        saveMeta()
        reload()
        n
    }

    fun copy(ids: Collection<String>, folder: String): List<PdfItem> = synchronized(lock) {
        val paths = mutableListOf<String>()
        for (id in ids) {
            val item = item(id) ?: continue
            folderFile(folder).mkdirs()
            val dest = uniqueFile(folder, item.name)
            item.file.copyTo(dest)
            paths += relativePath(dest)
        }
        reload()
        paths.mapNotNull { itemAt(it) }
    }

    fun duplicate(id: String): PdfItem? = synchronized(lock) {
        val item = item(id) ?: return@synchronized null
        val dest = uniqueFile(item.folder, LF("{} (cópia)", item.name))
        item.file.copyTo(dest)
        val rel = relativePath(dest)
        reload()
        itemAt(rel)
    }

    /** Tira os PDFs da biblioteca, guardando-os para o "Desfazer". */
    fun delete(ids: Collection<String>): TrashBatch = synchronized(lock) {
        val batch = UUID.randomUUID().toString()
        val dir = File(trashDir, batch)
        val entries = mutableListOf<Pair<String, DocMeta?>>()
        for (id in ids) {
            val item = item(id) ?: continue
            val dest = File(dir, item.relativePath)
            dest.parentFile?.mkdirs()
            moveFile(item.file, dest)
            entries += item.relativePath to metas[id]
        }
        reload()
        TrashBatch(batch, entries, emptyList())
    }

    fun createFolder(parent: String, name: String): String = synchronized(lock) {
        val clean = sanitizeFolder(name)
        val path = if (parent.isEmpty()) clean else "$parent/$clean"
        val dir = File(root, path)
        if (dir.exists()) throw UserError(L("Já existe uma pasta com esse nome."))
        if (!dir.mkdirs()) throw UserError(L("Não foi possível criar a pasta."))
        reload()
        path
    }

    fun renameFolder(path: String, newName: String): String = synchronized(lock) {
        val clean = sanitizeFolder(newName)
        val parent = if ('/' in path) path.substringBeforeLast('/') else ""
        val newPath = if (parent.isEmpty()) clean else "$parent/$clean"
        if (newPath == path) return@synchronized path
        val dest = File(root, newPath)
        if (dest.exists() && !dest.canonicalPath.equals(File(root, path).canonicalPath, ignoreCase = true)) {
            throw UserError(L("Já existe uma pasta com esse nome."))
        }
        moveFile(File(root, path), dest)
        val prefix = "$path/"
        for (m in metas.values.toList()) {
            if (m.path.startsWith(prefix)) metas[m.id] = m.copy(path = newPath + "/" + m.path.removePrefix(prefix))
        }
        saveMeta()
        reload()
        newPath
    }

    /** Tira a pasta (com tudo o que tem dentro) da biblioteca, guardando para o "Desfazer". */
    fun deleteFolder(path: String): TrashBatch = synchronized(lock) {
        val batch = UUID.randomUUID().toString()
        val src = File(root, path)
        val prefix = "$path/"
        val entries = items.filter { it.relativePath.startsWith(prefix) }.map { it.relativePath to metas[it.id] }
        val folders = listOf(path) + this.folders.filter { it.path.startsWith(prefix) }.map { it.path }
        val dest = File(File(trashDir, batch), path)
        dest.parentFile?.mkdirs()
        moveFile(src, dest)
        reload()
        TrashBatch(batch, entries, folders)
    }

    /** Põe de volta o que [delete] ou [deleteFolder] tirou, com os mesmos favoritos e ligações. */
    fun undoDelete(batch: TrashBatch): Int = synchronized(lock) {
        val dir = File(trashDir, batch.id)
        for (f in batch.folders) File(root, f).mkdirs()
        var n = 0
        for ((rel, meta) in batch.entries) {
            val src = File(dir, rel)
            if (!src.exists()) continue
            val folder = if ('/' in rel) rel.substringBeforeLast('/') else ""
            folderFile(folder).mkdirs()
            val dest = uniqueFile(folder, rel.substringAfterLast('/').removeSuffix(".pdf").removeSuffix(".PDF"))
            moveFile(src, dest)
            if (meta != null && !metas.containsKey(meta.id)) metas[meta.id] = meta.copy(path = relativePath(dest), signature = null, hasUndo = false)
            n++
        }
        dir.deleteRecursively()
        saveMeta()
        reload()
        n
    }

    // region Mudar o próprio PDF (com "Desfazer") e criar PDFs novos

    /**
     * Muda o próprio arquivo: [change] grava o resultado no arquivo temporário que recebe. O resultado é
     * conferido por [check] (número de páginas) antes de substituir o original, e a versão anterior fica
     * guardada para o "Desfazer".
     */
    fun modify(id: String, check: (File) -> Int?, change: (src: File, out: File) -> Unit): PdfItem = synchronized(lock) {
        val item = item(id) ?: throw UserError(L("O arquivo não existe mais."))
        val tmp = File(item.file.parentFile, ".${item.id}.tmp.pdf")
        try {
            change(item.file, tmp)
            val pages = check(tmp)
            if (pages == null || pages <= 0) throw UserError(L("O PDF salvo não pôde ser conferido; nada foi alterado."))
            val backup = File(versionsDir, "$id.pdf")
            item.file.copyTo(backup, overwrite = true)
            replaceFile(tmp, item.file)
        } finally {
            tmp.delete()
        }
        update(id) { it.copy(hasUndo = true, signature = null) }
        saveMeta()
        reload()
        item(id)!!
    }

    fun canUndo(item: PdfItem): Boolean = item.meta.hasUndo && File(versionsDir, "${item.id}.pdf").exists()

    /** Volta o arquivo para como estava antes da última mudança feita pelo Prumo. */
    fun undo(id: String) = synchronized(lock) {
        val item = item(id) ?: throw UserError(L("O arquivo não existe mais."))
        val backup = File(versionsDir, "$id.pdf")
        if (!backup.exists()) throw UserError(L("Não há alteração para desfazer."))
        replaceFile(backup, item.file)
        update(id) { it.copy(hasUndo = false, signature = null) }
        saveMeta()
        reload()
    }

    /**
     * Cria um PDF novo na pasta: [write] grava no arquivo temporário que recebe; o nome final é livre
     * ("Nome.pdf", "Nome (2).pdf"…).
     */
    fun saveNew(folder: String, baseName: String, check: (File) -> Int?, write: (File) -> Unit): PdfItem = synchronized(lock) {
        folderFile(folder).mkdirs()
        val dest = uniqueFile(folder, baseName)
        val tmp = File(dest.parentFile, ".${dest.name}.part")
        try {
            write(tmp)
            val pages = check(tmp)
            if (pages == null || pages <= 0) throw UserError(L("Não foi possível salvar o PDF."))
            replaceFile(tmp, dest)
        } finally {
            tmp.delete()
        }
        val rel = relativePath(dest)
        reload()
        itemAt(rel)!!
    }

    // endregion

    /** Apaga de vez o que foi excluído há mais de [days] dias. */
    fun purgeTrash(days: Int = 30) {
        val limit = clock() - days * 24L * 3600 * 1000
        trashDir.listFiles()?.forEach { if (it.lastModified() < limit) it.deleteRecursively() }
    }

    // endregion

    companion object {
        internal val json = Json { prettyPrint = true }

        val naturalOrder: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

        fun signature(size: Long, modified: Long) = "$size-${modified / 1000}"

        /** Minúsculas, sem acentos e com um só espaço entre as palavras (busca). */
        fun fold(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                .split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

        fun sanitize(name: String): String {
            var s = name.replace("/", "-").replace("\\", "-").replace(":", "-").replace("\n", " ")
                .replace(Regex("[\\u0000-\\u001f*?\"<>|]"), "").trim()
            while (s.startsWith(".")) s = s.substring(1)
            if (s.lowercase().endsWith(".pdf")) s = s.dropLast(4).trim()
            if (s.isEmpty()) s = L("Documento")
            return s.take(150)
        }

        fun sanitizeFolder(name: String): String {
            var s = name.replace("/", "-").replace("\\", "-").replace(":", "-").replace("\n", " ")
                .replace(Regex("[\\u0000-\\u001f*?\"<>|]"), "").trim()
            while (s.startsWith(".")) s = s.substring(1)
            if (s.isEmpty()) throw UserError(L("Digite um nome."))
            return s.take(120)
        }

        fun looksLikePdf(file: File): Boolean = runCatching {
            file.inputStream().use { inp ->
                val head = ByteArray(1024)
                val n = inp.read(head)
                n > 4 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
            }
        }.getOrDefault(false)

        /** Troca o conteúdo de [dest] pelo de [src] (rename quando dá; senão cópia). */
        internal fun replaceFile(src: File, dest: File) {
            if (src.renameTo(dest)) return
            src.copyTo(dest, overwrite = true)
            src.delete()
        }

        private fun moveFile(src: File, dest: File) {
            dest.parentFile?.mkdirs()
            if (src.renameTo(dest)) return
            // nomes que só mudam maiúsculas e minúsculas, em sistemas que não diferenciam
            if (src.canonicalPath.equals(dest.canonicalPath, ignoreCase = true)) {
                val tmp = File(src.parentFile, ".${UUID.randomUUID()}.tmp")
                if (src.renameTo(tmp) && tmp.renameTo(dest)) return
            }
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = false)
                src.deleteRecursively()
            } else {
                src.copyTo(dest, overwrite = false)
                src.delete()
            }
        }

        internal fun writeAtomic(file: File, text: String) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }

        /** Ordem natural: "Parte 2" antes de "Parte 10"; sem diferenciar maiúsculas e acentos. */
        fun compareNatural(a: String, b: String): Int {
            val x = fold(a)
            val y = fold(b)
            var i = 0
            var j = 0
            while (i < x.length && j < y.length) {
                val c = x[i]
                val d = y[j]
                if (c.isDigit() && d.isDigit()) {
                    var i2 = i
                    while (i2 < x.length && x[i2].isDigit()) i2++
                    var j2 = j
                    while (j2 < y.length && y[j2].isDigit()) j2++
                    val n1 = x.substring(i, i2).trimStart('0')
                    val n2 = y.substring(j, j2).trimStart('0')
                    if (n1.length != n2.length) return n1.length - n2.length
                    val cmp = n1.compareTo(n2)
                    if (cmp != 0) return cmp
                    i = i2
                    j = j2
                } else {
                    if (c != d) return c.compareTo(d)
                    i++
                    j++
                }
            }
            val rest = (x.length - i) - (y.length - j)
            return if (rest != 0) rest else a.compareTo(b)
        }

        internal fun metaToJson(m: DocMeta): JsonObject = buildJsonObject {
            put("id", m.id)
            put("path", m.path)
            put("favorite", m.favorite)
            put("pendingOCR", m.pendingOCR)
            put("isOCRResult", m.isOCRResult)
            m.ocrCopyId?.let { put("ocrCopyID", it) }
            m.sourceId?.let { put("sourceID", it) }
            m.pageCount?.let { put("pageCount", it) }
            m.locked?.let { put("locked", it) }
            m.signature?.let { put("signature", it) }
            m.lastOpened?.let { put("lastOpened", it) }
            m.added?.let { put("added", it) }
            m.ocrPages?.let { pages -> put("ocrPages", JsonArray(pages.map { JsonPrimitive(it) })) }
            if (m.hasUndo) put("hasUndo", true)
        }

        // Leitura tolerante: campos novos em versões futuras não quebram os dados antigos.
        internal fun metaFromJson(o: JsonObject): DocMeta? {
            fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
            fun bool(k: String) = (o[k] as? JsonPrimitive)?.booleanOrNull
            val id = str("id")?.takeIf { it.isNotEmpty() } ?: return null
            val path = str("path")?.takeIf { it.isNotEmpty() } ?: return null
            return DocMeta(
                id = id, path = path,
                favorite = bool("favorite") ?: false,
                pendingOCR = bool("pendingOCR") ?: false,
                isOCRResult = bool("isOCRResult") ?: false,
                ocrCopyId = str("ocrCopyID"), sourceId = str("sourceID"),
                pageCount = (o["pageCount"] as? JsonPrimitive)?.intOrNull,
                locked = bool("locked"),
                signature = str("signature"),
                lastOpened = (o["lastOpened"] as? JsonPrimitive)?.longOrNull,
                added = (o["added"] as? JsonPrimitive)?.longOrNull,
                ocrPages = (o["ocrPages"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull },
                hasUndo = bool("hasUndo") ?: false,
            )
        }
    }

    internal fun allMetas(): Map<String, DocMeta> = metas
    internal fun replaceMetas(m: MutableMap<String, DocMeta>) {
        metas = m
    }

    @Suppress("unused")
    private fun JsonObject.child(k: String) = this[k]?.jsonObject
}
