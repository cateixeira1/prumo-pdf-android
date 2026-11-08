package br.com.prumopdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.i18n.LN
import br.com.prumopdf.pdf.Pdfium
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import java.io.File

/** Telas da etapa 2: organizar, escolher páginas deslizando o dedo, busca no leitor, senha, seleção de vários. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PagesUiTest {
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
        compose.waitUntil(60_000) { library.items.all { it.pageCount != null } }
    }

    @After
    fun tearDown() = scenario.close()

    private fun vm(): AppViewModel {
        var vm: AppViewModel? = null
        scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
        return vm!!
    }

    private fun openRoute(route: Route) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm().open(route) }
        compose.waitForIdle()
    }

    /** Número no rodapé de cada página ("2 / 3" → "2"). */
    private fun footers(file: File): List<String> = Pdfium.openOrNull(file)!!.use { doc ->
        (0 until doc.pageCount).map { Regex("(\\d+) / \\d+").find(doc.text(it))?.groupValues?.get(1) ?: "?" }
    }

    /** Centro de um item da grade, nas coordenadas da grade. */
    private fun centerOf(gridTag: String, index: Int): Offset {
        val grid = compose.onNodeWithTag(gridTag).fetchSemanticsNode().boundsInRoot
        val cell = compose.onNodeWithTag("cell-$index", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return cell.center - grid.topLeft
    }

    @Test
    fun organizeDragToReorderAndSave() {
        val item = library.itemAt("Recibo de aluguel.pdf")!!
        // 3 páginas com títulos diferentes para conferir a ordem
        DemoData.write(item.file, "Recibo", 3)
        library.reload()
        compose.waitUntil(10_000) { library.itemAt("Recibo de aluguel.pdf")!!.pageCount == 3 }
        val id = item.id
        openRoute(Route.Organize(id))
        compose.waitUntilAtLeastOneExists(hasTestTag("cell-2"), 10_000)
        val from = centerOf("organizeGrid", 0)
        val to = centerOf("organizeGrid", 2)
        compose.onNodeWithTag("organizeGrid").performTouchInput {
            down(from)
            advanceEventTime(1200)
            moveTo(from + Offset(10f, 10f))
            for (k in 1..20) {
                moveTo(from + (to - from) * (k / 20f))
                advanceEventTime(30)
            }
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("organizeSave").performClick()
        compose.waitUntil(30_000) { library.item(id)?.meta?.hasUndo == true }
        // a página 1 foi para o fim: a ordem agora é 2, 3, 1
        assertEquals(listOf("2", "3", "1"), footers(library.item(id)!!.file))
    }

    @Test
    fun organizeRotateAndDeleteThenUndo() {
        val item = library.itemAt("Contrato de locação.pdf")!!
        openRoute(Route.Organize(item.id))
        compose.waitUntilAtLeastOneExists(hasTestTag("cell-1"), 10_000)
        compose.onNodeWithTag("cell-0", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("rotateRight").performClick()
        compose.onNodeWithTag("cell-1", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("deletePages").performClick()
        compose.onNodeWithTag("organizeSave").performClick()
        compose.waitUntil(30_000) { library.item(item.id)?.pageCount == 4 }
        Pdfium.openOrNull(library.item(item.id)!!.file)!!.use { assertEquals(1, it.rotation(0)) }
        // Desfazer volta as 6 páginas
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm().undoChange(library.item(item.id)!!) }
        compose.waitUntil(30_000) { library.item(item.id)?.pageCount == 6 }
    }

    @Test
    fun swipeSelectsSeveralPagesToRemove() {
        val item = library.itemAt("Clientes/Ana Souza/Prontuário.pdf")!!
        openRoute(Route.PickPages(item.id, PageMode.REMOVE))
        compose.waitUntilAtLeastOneExists(hasTestTag("cell-2"), 10_000)
        val a = centerOf("pickGrid", 0)
        val b = centerOf("pickGrid", 2)
        compose.onNodeWithTag("pickGrid").performTouchInput {
            down(a)
            // primeiro na horizontal (seleção), depois até o terceiro item
            for (k in 1..10) {
                moveTo(a + Offset(6f * k, 0f))
                advanceEventTime(20)
            }
            for (k in 1..20) {
                moveTo(a + Offset(60f, 0f) + (b - a - Offset(60f, 0f)) * (k / 20f))
                advanceEventTime(20)
            }
            moveTo(b)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText(LN(3, "Remover 1 página", "Remover {} páginas")).assertExists()
        compose.onNodeWithTag("pickConfirm").performClick()
        compose.waitUntil(10_000) { library.item(item.id)?.pageCount == 9 }
    }

    @Test
    fun readerSearchesTheDocument() {
        val item = library.itemAt("Contrato de locação.pdf")!!
        openRoute(Route.Reader(item.id))
        compose.waitUntilAtLeastOneExists(hasTestTag("searchToggle"), 10_000)
        compose.onNodeWithTag("searchToggle").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("readerSearch"))).performTextInput("clausula decima")
        compose.waitUntilAtLeastOneExists(hasText(LF("{} de {}", 1, 1)), 10_000)
        compose.waitUntilAtLeastOneExists(hasText(LF("Página {} de {}", 3, 6)), 10_000)
    }

    @Test
    fun passwordProtectedPdfOpensWithThePassword() {
        val f = File(app.cacheDir, "Protegido.pdf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("protegido.pdf").use { i -> f.outputStream().use { i.copyTo(it) } }
        vm().importUris(listOf(android.net.Uri.fromFile(f)), folder = "")
        compose.waitUntil(10_000) { library.itemAt("Protegido.pdf")?.meta?.locked == true }
        val item = library.itemAt("Protegido.pdf")!!
        assertTrue(item.isLocked)
        openRoute(Route.Reader(item.id))
        compose.waitUntilAtLeastOneExists(hasTestTag("passwordField"), 10_000)
        compose.onNodeWithTag("passwordField").performTextInput("errada")
        compose.onNodeWithTag("passwordOk").performClick()
        compose.waitUntilAtLeastOneExists(hasText(L("Senha incorreta. Tente de novo (maiúsculas e minúsculas fazem diferença).")), 10_000)
        compose.onNodeWithTag("passwordField").performTextClearance()
        compose.onNodeWithTag("passwordField").performTextInput("prumo123")
        compose.onNodeWithTag("passwordOk").performClick()
        compose.waitUntilAtLeastOneExists(hasText(LF("Página {} de {}", 1, 2)), 10_000)
        // a miniatura do PDF com senha não fica gravada no disco
        val thumbs = File(app.cacheDir, "thumbs").listFiles { x -> x.name.startsWith("${item.id}_") }.orEmpty()
        assertTrue(thumbs.isEmpty())
    }

    @Test
    fun selectSeveralPdfsAndCombine() {
        compose.onNodeWithText(L("Todos os PDFs")).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("selectMode"), 5000)
        compose.onNodeWithTag("selectMode").performClick()
        compose.onNodeWithText("Recibo de aluguel").performClick()
        compose.onNodeWithText("Relatório anual 2026").performClick()
        compose.onNodeWithText(LN(2, "1 selecionado", "{} selecionados")).assertExists()
        compose.onNodeWithTag("selMerge").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("mergeOk"), 5000)
        compose.onNodeWithTag("mergeOk").performClick()
        compose.waitUntil(10_000) { library.items.any { it.name == L("PDF combinado") } }
        compose.waitUntil(10_000) { library.items.first { it.name == L("PDF combinado") }.pageCount == 10 }
    }
}
