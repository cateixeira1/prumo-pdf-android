package br.com.prumopdf

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.prumopdf.i18n.L
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Capturas de tela para conferir no GitHub (branch ci-output): tela inicial, ferramentas, lista, pastas,
 * documento e leitor, em cada idioma pedido (argumento "langs", ex.: "pt en de ja") e com o nome do aparelho
 * no arquivo (argumento "device", ex.: "phone", "tablet", "fold-open").
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<PrumoApplication>()
    private val args get() = InstrumentationRegistry.getArguments()
    private val outDir get() = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }

    private fun shot(lang: String, n: Int, name: String) {
        compose.waitForIdle()
        Thread.sleep(900)       // miniaturas carregadas e animações paradas
        compose.waitForIdle()
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val device = args.getString("device") ?: "device"
        File(outDir, "%s-%s-%02d-%s.png".format(device, lang, n, name)).outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun screens() {
        val langs = (args.getString("langs") ?: "pt").split(" ", ",").filter { it.isNotBlank() }
        val lib = app.library
        DemoData.reset(lib)
        lib.markOpened(lib.itemAt("Contrato de locação.pdf")!!.id)
        lib.markOpened(lib.itemAt("Clientes/Bruno Lima/Petição inicial.pdf")!!.id)
        lib.setPendingOCR(listOfNotNull(lib.itemAt("Clientes/Ana Souza/Prontuário.pdf")?.id), true)
        try {
            for (lang in langs) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    AppLanguage.set(app, if (lang == "system") null else lang)
                }
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.waitUntil(10_000) { lib.items.all { item -> item.pageCount != null } }
                    shot(lang, 1, "inicio")
                    compose.onNodeWithText(L("Comprimir PDF")).performScrollTo()
                    shot(lang, 2, "ferramentas")
                    compose.onNodeWithText(L("Todos os PDFs")).performScrollTo().performClick()
                    compose.waitUntilAtLeastOneExists(hasTestTag("docgrid"), 5000)
                    shot(lang, 3, "todos")
                    compose.onNodeWithTag("docgrid").performScrollToNode(hasText("Contrato de locação"))
                    compose.onNodeWithText("Contrato de locação").performClick()
                    compose.waitUntilAtLeastOneExists(hasTestTag("read"), 5000)
                    // a miniatura grande da tela do documento demora mais no emulador na primeira vez
                    val id = lib.itemAt("Contrato de locação.pdf")!!.id
                    val thumbs = File(app.cacheDir, "thumbs")
                    compose.waitUntil(8000) { (thumbs.listFiles { f -> f.name.startsWith("${id}_") }?.size ?: 0) >= 2 }
                    shot(lang, 4, "documento")
                    compose.onNodeWithTag("read").performScrollTo().performClick()
                    compose.waitUntilAtLeastOneExists(hasTestTag("pageIndicator"), 5000)
                    shot(lang, 5, "leitor")
                }
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.onNodeWithText(L("Pastas")).performScrollTo().performClick()
                    compose.waitUntilAtLeastOneExists(hasText("Clientes"), 5000)
                    compose.onNodeWithText("Clientes").performClick()
                    compose.waitUntilAtLeastOneExists(hasText("Ana Souza"), 5000)
                    shot(lang, 6, "pasta")
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { AppLanguage.set(app, null) }
        }
    }
}
