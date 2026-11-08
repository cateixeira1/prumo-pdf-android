package br.com.prumopdf

import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.prumopdf.core.DocList
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.ui.UiActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppTest {
    /** Um teste preso falha em 3 minutos, com a pilha de chamadas no relatório (em vez de travar tudo). */
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.builder().withTimeout(180, TimeUnit.SECONDS).withLookingForStuckThread(true).build()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<PrumoApplication>()
    private val library get() = app.library
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        DemoData.reset(library)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After
    fun tearDown() = scenario.close()

    private fun vm(): AppViewModel {
        var vm: AppViewModel? = null
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
        return vm!!
    }

    @Test
    fun homeShowsListsAndAllTools() {
        val titles = listOf("Todos os PDFs", "Recentes", "Sem OCR", "Com OCR", "Favoritos", "Pastas", "Importar", "Ferramentas")
            .map { L(it) } + Tool.entries.map { UiActions.toolTitle(it) }
        for (t in titles) compose.onAllNodesWithText(t).onFirst().assertExists()
        compose.onNodeWithText("Color Software").assertExists()
    }

    @Test
    fun countsMatchTheLibrary() {
        assertEquals(DemoData.docs.size, library.items.size)
        assertEquals(2, library.count(DocList.FAVORITES))
        assertEquals(listOf("Clientes", "Contratos"), library.topLevelFolders.map { it.name })
    }

    @Test
    fun openDocumentAndRead() {
        compose.onNodeWithText(L("Todos os PDFs")).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("docgrid"), 5000)
        compose.onNodeWithTag("docgrid").performScrollToNode(hasText("Recibo de aluguel"))
        compose.onNodeWithText("Recibo de aluguel").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("read"), 5000)
        compose.onNodeWithTag("read").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("pageIndicator"), 5000)
        compose.onNodeWithText(LF("Página {} de {}", 1, 1)).assertExists()
        // a leitura conta como "Recentes"
        compose.waitUntil(3000) { library.items(DocList.RECENT).any { it.name == "Recibo de aluguel" } }
    }

    @Test
    fun analysisCountsPages() {
        compose.waitUntil(10_000) { library.items.all { it.pageCount != null } }
        assertEquals(18, library.itemAt("Clientes/Bruno Lima/Petição inicial.pdf")!!.pageCount)
        assertEquals(1, library.itemAt("Recibo de aluguel.pdf")!!.pageCount)
    }

    @Test
    fun favoritesListShowsStarredDocuments() {
        compose.onNodeWithText(L("Favoritos")).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Petição inicial"), 5000)
        compose.onNodeWithText("Contrato de locação").assertExists()
        compose.onAllNodesWithText("Recibo de aluguel").assertCountEquals(0)
    }

    @Test
    fun foldersOpen() {
        compose.onNodeWithText(L("Pastas")).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Clientes"), 5000)
        compose.onNodeWithText("Clientes").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Ana Souza"), 5000)
        compose.onNodeWithText("Bruno Lima").assertExists()
    }

    @Test
    fun importFileAndDeleteWithUndo() {
        val f = File(app.cacheDir, "Recebido.pdf")
        DemoData.write(f, "Recebido", 2)
        vm().importUris(listOf(Uri.fromFile(f)), folder = "")
        compose.waitUntil(5000) { library.itemAt("Recebido.pdf") != null }
        val item = library.itemAt("Recebido.pdf")!!
        vm().delete(listOf(item))
        compose.waitUntil(5000) { library.item(item.id) == null }
        compose.waitUntilAtLeastOneExists(hasText(L("Desfazer")), 5000)
        compose.onNodeWithText(L("Desfazer")).performClick()
        compose.waitUntil(5000) { library.item(item.id) != null }
        assertNotNull(library.itemAt("Recebido.pdf"))
    }

    @Test
    fun thumbnailsLoadAtTwoSizesAtOnce() {
        // em tablet e dobrável, a lista e a tela do documento pedem a miniatura do mesmo PDF juntas
        val item = library.itemAt("Contrato de locação.pdf")!!
        val thumbs = app.thumbnails
        val results = arrayOfNulls<android.graphics.Bitmap>(4)
        val threads = (0 until 4).map { i ->
            Thread { results[i] = thumbs.load(item.id, item.version, item.file, if (i % 2 == 0) 136 + i else 512 + i) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(results.all { it != null && it.width > 100 })
    }

    @Test
    fun sharedLibraryFileIsWritten() {
        val shared = File(library.root, ".prumo/library.json")
        assertTrue(shared.exists())
        val text = shared.readText()
        assertTrue(text.contains("\"pending_ocr\""))
        assertTrue(text.contains("Petição inicial.pdf"))
    }
}
