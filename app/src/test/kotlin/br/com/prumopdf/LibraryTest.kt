package br.com.prumopdf

import br.com.prumopdf.core.DocList
import br.com.prumopdf.core.Library
import br.com.prumopdf.core.UserError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var support: File
    private var now = 1_800_000_000_000L

    private fun pdf(rel: String, text: String = rel): File {
        val f = File(root, rel)
        f.parentFile.mkdirs()
        f.writeText("%PDF-1.4\n% $text\n%%EOF\n")
        return f
    }

    private fun lib() = Library(root, support) { now }

    private fun shared(): List<JsonObject> =
        (Json.parseToJsonElement(File(root, ".prumo/library.json").readText()) as JsonArray).map { it as JsonObject }

    @Before
    fun setUp() {
        root = tmp.newFolder("Prumo PDF")
        support = tmp.newFolder("support")
    }

    @Test
    fun findsPdfsAndFoldersAndSkipsHiddenOnes() {
        pdf("Contrato.pdf")
        pdf("Clientes/Ana/Laudo.PDF")
        pdf(".prumo/oculto.pdf")
        File(root, "Clientes/nota.txt").writeText("x")
        val lib = lib()
        assertEquals(setOf("Contrato.pdf", "Clientes/Ana/Laudo.PDF"), lib.items.map { it.relativePath }.toSet())
        assertEquals(listOf("Clientes", "Clientes/Ana"), lib.folders.map { it.path })
        val clientes = lib.folder("Clientes")!!
        assertEquals(0, clientes.docCount)
        assertEquals(1, clientes.totalDocCount)
        assertEquals(1, clientes.subfolderCount)
        assertEquals("Laudo", lib.itemAt("Clientes/Ana/Laudo.PDF")!!.name)
    }

    @Test
    fun sharedFileUsesTheSameFieldsAsIphoneAndWindows() {
        pdf("A.pdf")
        val lib = lib()
        lib.toggleFavorite(lib.items[0].id)
        val row = shared().single()
        assertEquals(
            setOf("path", "id", "favorite", "pending_ocr", "is_ocr_result", "ocr_copy_id", "source_id"),
            row.keys,
        )
        assertEquals("A.pdf", row["path"]!!.jsonPrimitive.content)
        assertTrue(row["favorite"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun pdfFromAnotherDeviceKeepsItsIdAndFavorite() {
        // library.json gravado pelo Windows ou pelo iPhone, com um PDF que acabou de chegar pela nuvem
        File(root, ".prumo").mkdirs()
        File(root, ".prumo/library.json").writeText(
            """[{"path": "Pasta\\Recibo.pdf", "id": "abc-123", "favorite": true, "pending_ocr": false,
               "is_ocr_result": false, "ocr_copy_id": null, "source_id": null},
               {"path": "Ainda vindo.pdf", "id": "zzz", "favorite": true, "pending_ocr": false,
               "is_ocr_result": false, "ocr_copy_id": null, "source_id": null}]""",
        )
        pdf("Pasta/Recibo.pdf")
        val lib = lib()
        val item = lib.itemAt("Pasta/Recibo.pdf")!!
        assertEquals("abc-123", item.id)
        assertTrue(item.meta.favorite)
        // o que ainda não chegou continua no arquivo para o outro aparelho
        lib.toggleFavorite(item.id)
        assertTrue(shared().any { it["id"]!!.jsonPrimitive.content == "zzz" })
    }

    @Test
    fun changesFromAnotherDeviceArriveAndLocalChangesWin() {
        pdf("A.pdf")
        pdf("B.pdf")
        val lib = lib()
        val a = lib.itemAt("A.pdf")!!.id
        val b = lib.itemAt("B.pdf")!!.id
        lib.toggleFavorite(a)          // grava o arquivo compartilhado
        // outro aparelho favorita o B e tira o favorito do A
        val rows = shared().map { r ->
            val p = r["path"]!!.jsonPrimitive.content
            JsonObject(r + ("favorite" to kotlinx.serialization.json.JsonPrimitive(p == "B.pdf")))
        }
        val f = File(root, ".prumo/library.json")
        f.writeText(Json.encodeToString(JsonArray.serializer(), JsonArray(rows)))
        f.setLastModified(f.lastModified() + 5000)
        lib.reload()
        assertFalse(lib.item(a)!!.meta.favorite)
        assertTrue(lib.item(b)!!.meta.favorite)
    }

    @Test
    fun renameMoveAndDuplicateKeepTheRightIds() {
        pdf("Petição.pdf")
        val lib = lib()
        val id = lib.items[0].id
        lib.toggleFavorite(id)
        val renamed = lib.rename(id, "Petição inicial")
        assertEquals(id, renamed.id)
        assertEquals("Petição inicial.pdf", renamed.relativePath)
        assertTrue(renamed.meta.favorite)
        lib.createFolder("", "Processos")
        assertEquals(1, lib.move(listOf(id), "Processos"))
        assertEquals("Processos/Petição inicial.pdf", lib.item(id)!!.relativePath)
        val copy = lib.duplicate(id)!!
        assertEquals("Petição inicial (cópia)", copy.name)
        assertFalse(copy.id == id)
        assertFalse(copy.meta.favorite)
    }

    @Test
    fun renameRefusesAnExistingName() {
        pdf("A.pdf")
        pdf("B.pdf")
        val lib = lib()
        try {
            lib.rename(lib.itemAt("A.pdf")!!.id, "B")
            throw AssertionError("deveria recusar")
        } catch (_: UserError) {
        }
    }

    @Test
    fun deleteAndUndoRestoreTheSameDocument() {
        pdf("Pasta/A.pdf")
        val lib = lib()
        val id = lib.items[0].id
        lib.toggleFavorite(id)
        val batch = lib.delete(listOf(id))
        assertNull(lib.item(id))
        assertFalse(File(root, "Pasta/A.pdf").exists())
        assertEquals(1, lib.undoDelete(batch))
        val back = lib.item(id)
        assertNotNull(back)
        assertTrue(back!!.meta.favorite)
        assertEquals("Pasta/A.pdf", back.relativePath)
    }

    @Test
    fun deleteFolderAndUndo() {
        pdf("Casos/Ana/1.pdf")
        pdf("Casos/2.pdf")
        val lib = lib()
        val batch = lib.deleteFolder("Casos")
        assertTrue(lib.items.isEmpty())
        assertTrue(lib.folders.isEmpty())
        assertEquals(2, lib.undoDelete(batch))
        assertEquals(setOf("Casos/Ana/1.pdf", "Casos/2.pdf"), lib.items.map { it.relativePath }.toSet())
    }

    @Test
    fun renameFolderMovesTheMetadata() {
        pdf("Velha/A.pdf")
        val lib = lib()
        val id = lib.items[0].id
        lib.toggleFavorite(id)
        assertEquals("Nova", lib.renameFolder("Velha", "Nova"))
        assertEquals("Nova/A.pdf", lib.item(id)!!.relativePath)
        assertTrue(lib.item(id)!!.meta.favorite)
    }

    @Test
    fun importRefusesNonPdfAndNumbersRepeatedNames() {
        val lib = lib()
        assertNull(lib.import("nota.pdf", "não é PDF".byteInputStream()))
        val one = lib.import("Laudo.pdf", "%PDF-1.7\n".byteInputStream())!!
        val two = lib.import("Laudo.pdf", "%PDF-1.7\n".byteInputStream())!!
        assertEquals("Laudo.pdf", one.relativePath)
        assertEquals("Laudo (2).pdf", two.relativePath)
        assertTrue(root.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun listsFavoritesRecentAndSearch() {
        pdf("Contrato de Locação.pdf")
        pdf("Outro.pdf")
        val lib = lib()
        val id = lib.itemAt("Contrato de Locação.pdf")!!.id
        lib.toggleFavorite(id)
        assertEquals(listOf(id), lib.items(DocList.FAVORITES).map { it.id })
        assertEquals(listOf(id), lib.search("locacao").map { it.id })
        assertEquals(listOf(id), lib.search("  CONTRATO   de ").map { it.id })
        // arquivos de 1970 só ficam em "Recentes" depois de abertos
        File(root, "Outro.pdf").setLastModified(0)
        File(root, "Contrato de Locação.pdf").setLastModified(0)
        lib.reload()
        assertEquals(0, lib.count(DocList.RECENT))
        lib.markOpened(id)
        assertEquals(listOf(id), lib.items(DocList.RECENT).map { it.id })
    }

    @Test
    fun metadataSurvivesRestart() {
        pdf("A.pdf")
        val first = lib()
        val id = first.items[0].id
        first.setPendingOCR(listOf(id), true)
        val second = lib()
        assertEquals(id, second.items[0].id)
        assertTrue(second.items[0].meta.pendingOCR)
        assertEquals(1, second.count(DocList.PENDING_OCR))
    }

    @Test
    fun modifyKeepsThePreviousVersionForUndo() {
        pdf("A.pdf", "versão 1")
        val lib = lib()
        val id = lib.items[0].id
        lib.modify(id, { 2 }) { _, out -> out.writeText("%PDF-1.4\n% versão 2\n") }
        assertTrue(File(root, "A.pdf").readText().contains("versão 2"))
        assertTrue(lib.canUndo(lib.item(id)!!))
        lib.undo(id)
        assertTrue(File(root, "A.pdf").readText().contains("versão 1"))
        assertFalse(lib.item(id)!!.meta.hasUndo)
        assertTrue(root.listFiles()!!.none { it.name.startsWith(".") && it.name.endsWith(".pdf") })
    }

    @Test
    fun modifyKeepsTheOriginalWhenTheResultCannotBeChecked() {
        pdf("A.pdf", "original")
        val lib = lib()
        val id = lib.items[0].id
        try {
            lib.modify(id, { null }) { _, out -> out.writeText("lixo") }
            throw AssertionError("deveria recusar")
        } catch (_: UserError) {
        }
        assertTrue(File(root, "A.pdf").readText().contains("original"))
        assertFalse(lib.canUndo(lib.item(id)!!))
    }

    @Test
    fun saveNewUsesAFreeName() {
        pdf("Pasta/Laudo - Parte 1.pdf")
        val lib = lib()
        val created = lib.saveNew("Pasta", "Laudo - Parte 1", { 1 }) { it.writeText("%PDF-1.4\n") }
        assertEquals("Pasta/Laudo - Parte 1 (2).pdf", created.relativePath)
    }

    @Test
    fun analysisFeedsSearchAndTheOcrList() {
        pdf("Escaneado.pdf")
        pdf("Digital.pdf")
        val lib = lib()
        val scan = lib.itemAt("Escaneado.pdf")!!
        val digital = lib.itemAt("Digital.pdf")!!
        lib.applyAnalysis(scan.id, "x", br.com.prumopdf.core.Analysis(3, false, ocrPages = listOf(1, 2, 3)))
        lib.applyAnalysis(digital.id, "y", br.com.prumopdf.core.Analysis(2, false, text = "Cláusula DÉCIMA do contrato"))
        assertEquals(listOf(scan.id), lib.items(DocList.PENDING_OCR).map { it.id })
        assertEquals(listOf(digital.id), lib.search("clausula decima").map { it.id })
        // o texto fica guardado para a próxima vez
        assertEquals(listOf(digital.id), lib().search("clausula").map { it.id })
    }

    @Test
    fun namesAndOrder() {
        assertEquals("a-b-c", Library.sanitize("a/b:c.pdf"))
        assertEquals("Documento", Library.sanitize("..."))
        assertEquals(
            listOf("Ábaco", "Parte 1", "parte 2", "Parte 10"),
            listOf("Parte 10", "parte 2", "Ábaco", "Parte 1").sortedWith(Library.naturalOrder),
        )
        assertTrue(Library.compareNatural("Parte 2", "Parte 10") < 0)
        assertTrue(Library.compareNatural("ábaco", "Abacaxi") > 0)
    }
}
