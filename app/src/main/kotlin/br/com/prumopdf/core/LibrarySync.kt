package br.com.prumopdf.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.UUID

/** O que acompanha cada PDF entre aparelhos (iPhone, Windows e Android). */
internal data class SharedMeta(
    val favorite: Boolean = false,
    val pendingOCR: Boolean = false,
    val isOCRResult: Boolean = false,
    val ocrCopy: String? = null,   // caminho em minúsculas, ou "id:<id>" se o arquivo ainda não chegou
    val source: String? = null,
)

/**
 * Sincronização com outros aparelhos pelo arquivo `.prumo/library.json` dentro da pasta da biblioteca. É o
 * mesmo arquivo e os mesmos nomes de campos do Prumo para iPhone (LibrarySync.swift) e Windows (library.py):
 * uma lista de objetos com path, id, favorite, pending_ocr, is_ocr_result, ocr_copy_id e source_id.
 * Cada aparelho relê o arquivo antes de gravar e mantém o que o outro mudou.
 */
internal class SharedSync(private val lib: Library) {
    private var diskStamp: String? = null
    private var diskEntries: Map<String, JsonObject> = emptyMap()
    private var snapshot: Map<String, SharedMeta> = emptyMap()
    val knownPaths = HashSet<String>()

    val sharedFile: File get() = File(File(lib.root, FOLDER), "library.json")

    private fun stampNow(): String? {
        val f = sharedFile
        return if (f.exists()) "${f.lastModified()}-${f.length()}" else null
    }

    private fun readDisk(): List<JsonObject> {
        val text = runCatching { sharedFile.readText() }.getOrNull() ?: return emptyList()
        val arr = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }.filter { !str(it, "path").isNullOrEmpty() && !str(it, "id").isNullOrEmpty() }
    }

    private fun setDisk(entries: List<JsonObject>) {
        val out = LinkedHashMap<String, JsonObject>()
        for (e in entries) {
            val k = key(str(e, "path")!!)
            if (k !in out) out[k] = e
        }
        diskEntries = out
    }

    fun sharedValues(): Map<String, SharedMeta> {
        val metas = lib.allMetas()
        val idPath = metas.values.associate { it.id to it.path.lowercase() }
        fun ref(id: String?) = id?.let { idPath[it] ?: "id:$it" }
        return metas.values.associate {
            it.path.lowercase() to SharedMeta(it.favorite, it.pendingOCR, it.isOCRResult, ref(it.ocrCopyId), ref(it.sourceId))
        }
    }

    private fun diskValues(e: JsonObject, idPath: Map<String, String>): SharedMeta {
        fun ref(k: String): String? {
            val id = str(e, k)?.takeIf { it.isNotEmpty() } ?: return null
            return idPath[id] ?: "id:$id"
        }
        return SharedMeta(
            favorite = bool(e, "favorite"), pendingOCR = bool(e, "pending_ocr"), isOCRResult = bool(e, "is_ocr_result"),
            ocrCopy = ref("ocr_copy_id"), source = ref("source_id"),
        )
    }

    private fun resolve(value: String?, byPath: Map<String, DocMeta>): String? {
        val v = value ?: return null
        if (v.startsWith("id:")) return v.substring(3)
        byPath[v]?.let { return it.id }
        return diskEntries[v]?.let { str(it, "id") }
    }

    /** Traz do library.json o que outro aparelho mudou. Os campos mudados aqui desde a última
     *  sincronização ficam como estão aqui. Devolve true se algo mudou. */
    fun syncFromDisk(): Boolean {
        val stamp = stampNow()
        if (stamp == diskStamp) return false
        diskStamp = stamp
        val entries = readDisk()
        setDisk(entries)
        if (entries.isEmpty()) return false
        val idPath = entries.associate { str(it, "id")!! to key(str(it, "path")!!) }
        val local = sharedValues()
        val metas = LinkedHashMap(lib.allMetas())
        val byPath = metas.values.associateBy { it.path.lowercase() }
        var changed = false
        for ((p, entry) in diskEntries) {
            val m0 = byPath[p] ?: continue
            val mine = local[p] ?: continue
            val disk = diskValues(entry, idPath)
            val snap = snapshot[p]
            fun <T> take(get: (SharedMeta) -> T): Boolean {
                if (snap != null && get(mine) != get(snap)) return false   // mudou aqui
                return get(disk) != get(mine)
            }
            var m = m0
            if (take { it.favorite }) m = m.copy(favorite = disk.favorite)
            if (take { it.pendingOCR }) m = m.copy(pendingOCR = disk.pendingOCR)
            if (take { it.isOCRResult }) m = m.copy(isOCRResult = disk.isOCRResult)
            if (take { it.ocrCopy }) m = m.copy(ocrCopyId = resolve(disk.ocrCopy, byPath))
            if (take { it.source }) m = m.copy(sourceId = resolve(disk.source, byPath))
            if (m != metas[m.id]) {
                metas[m.id] = m
                changed = true
            }
        }
        if (changed) lib.replaceMetas(metas)
        snapshot = sharedValues()
        return changed
    }

    /** Dados de um PDF novo na pasta: se ele veio de outro aparelho, usa o id e o favorito de lá. */
    fun newSharedMeta(path: String, usedIds: MutableSet<String>, now: Long): DocMeta {
        val e = diskEntries[path.lowercase()]
            ?: return DocMeta(id = UUID.randomUUID().toString().uppercase(), path = path, added = now)
        val wanted = str(e, "id")
        val id = if (wanted != null && wanted !in usedIds) wanted else UUID.randomUUID().toString().uppercase()
        usedIds += id
        return DocMeta(
            id = id, path = path, added = now,
            favorite = bool(e, "favorite"), pendingOCR = bool(e, "pending_ocr"), isOCRResult = bool(e, "is_ocr_result"),
            ocrCopyId = str(e, "ocr_copy_id"), sourceId = str(e, "source_id"),
        )
    }

    /** Grava o library.json compartilhado: os PDFs daqui e os que outro aparelho registrou e ainda
     *  não chegaram pela nuvem. */
    fun writeShared() {
        val metas = lib.allMetas()
        val local = metas.values.map { it.path.lowercase() }.toSet()
        val rows = metas.values.sortedBy { it.path }.map { m ->
            JsonObject(
                sortedMapOf<String, JsonElement>(
                    "favorite" to JsonPrimitive(m.favorite),
                    "id" to JsonPrimitive(m.id),
                    "is_ocr_result" to JsonPrimitive(m.isOCRResult),
                    "ocr_copy_id" to (m.ocrCopyId?.let { JsonPrimitive(it) } ?: JsonNull),
                    "path" to JsonPrimitive(m.path),
                    "pending_ocr" to JsonPrimitive(m.pendingOCR),
                    "source_id" to (m.sourceId?.let { JsonPrimitive(it) } ?: JsonNull),
                ),
            )
        }.toMutableList()
        for ((p, e) in diskEntries) if (p !in local && p !in knownPaths) rows += e
        val text = Library.json.encodeToString(JsonArray.serializer(), JsonArray(rows))
        runCatching { Library.writeAtomic(sharedFile, text) }.onFailure { return }
        diskStamp = stampNow()
        setDisk(rows)
        knownPaths += local
        snapshot = sharedValues()
    }

    companion object {
        const val FOLDER = ".prumo"
        fun key(path: String) = path.replace('\\', '/').lowercase()
        fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        fun bool(o: JsonObject, k: String): Boolean = (o[k] as? JsonPrimitive)?.booleanOrNull ?: false
    }
}
