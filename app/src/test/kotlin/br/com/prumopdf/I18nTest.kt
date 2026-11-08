package br.com.prumopdf

import br.com.prumopdf.i18n.I18n
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.i18n.LN
import br.com.prumopdf.i18n.fillTemplate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class I18nTest {
    private val dir = listOf(File("src/main/assets/locales"), File("app/src/main/assets/locales")).first { it.isDirectory }

    private fun load(language: String) = I18n.init({ code -> File(dir, "$code.json").takeIf { it.exists() }?.readText() }, language)

    @After
    fun back() = load("pt")

    @Test
    fun templatesAndPlurals() {
        load("pt")
        assertEquals("Página 2 de 10", LF("Página {} de {}", 2, 10))
        assertEquals("1 página", LN(1, "1 página", "{} páginas"))
        assertEquals("3 páginas", LN(3, "1 página", "{} páginas"))
        assertEquals("a  c", fillTemplate("a {} c", listOf(null)))
    }

    @Test
    fun translatesWithTheSharedCatalogs() {
        load("en")
        assertEquals("Favorites", L("Favoritos"))
        assertEquals("3 pages", LN(3, "1 página", "{} páginas"))
        load("de")
        assertEquals("Favoriten", L("Favoritos"))
        load("ja")
        assertTrue(L("Favoritos") != "Favoritos")
    }

    @Test
    fun systemLanguagesMapToTheCatalogs() {
        assertEquals("pt", I18n.codeFor(Locale.forLanguageTag("pt-PT")))
        assertEquals("zh-TW", I18n.codeFor(Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals("zh-TW", I18n.codeFor(Locale.forLanguageTag("zh-TW")))
        assertEquals("zh-CN", I18n.codeFor(Locale.forLanguageTag("zh-CN")))
        assertEquals("nb", I18n.codeFor(Locale.forLanguageTag("no")))
        assertEquals("nb", I18n.codeFor(Locale.forLanguageTag("nn-NO")))
        assertEquals("en", I18n.codeFor(Locale.forLanguageTag("ko-KR")))
    }

    @Test
    fun everyCatalogKeepsThePlaceholders() {
        val problems = mutableListOf<String>()
        for ((code, _) in I18n.languages) {
            if (code == "pt") continue
            val cat = I18n.parseCatalog(File(dir, "$code.json").readText())
            for ((k, v) in cat) {
                if (k.split("{}").size != v.split("{}").size) problems += "$code: $k → $v"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun copyNamesAreRecognisedInAnyLanguage() {
        load("en")
        val all = I18n.allTranslations("{} (cópia)")
        assertTrue(all.contains("{} (cópia)"))
        assertTrue(all.size > 3)
    }
}
