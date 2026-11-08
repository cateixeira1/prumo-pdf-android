package br.com.prumopdf

import br.com.prumopdf.core.InsertPosition
import br.com.prumopdf.core.PageSelection
import br.com.prumopdf.core.SplitPlan
import br.com.prumopdf.core.TextFold
import br.com.prumopdf.core.UserError
import br.com.prumopdf.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PagesTest {
    @Before
    fun pt() = I18n.init({ null }, "pt")

    private fun fails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("deveria recusar")
        } catch (_: UserError) {
        }
    }

    @Test
    fun parsesPageLists() {
        assertEquals(listOf(1, 3, 4, 5, 10, 11, 12), PageSelection.parse("1, 3-5, 10-fim", 12))
        assertEquals(listOf(2, 3), PageSelection.parse(" 3 ; 2 ", 5))
        assertEquals(listOf(4, 5), PageSelection.parse("4-", 5))
        assertEquals(listOf(5), PageSelection.parse("end", 5))
        assertEquals(listOf(1, 2), PageSelection.parse("1–2", 5))   // travessão
        fails { PageSelection.parse("", 5) }
        fails { PageSelection.parse("6", 5) }
        fails { PageSelection.parse("4-2", 5) }
        fails { PageSelection.parse("abc", 5) }
    }

    @Test
    fun formatsPageLists() {
        assertEquals("1-3, 5, 7-8", PageSelection.format(listOf(8, 1, 2, 3, 5, 7)))
        assertEquals("4", PageSelection.format(listOf(4)))
        assertEquals("", PageSelection.format(emptyList()))
    }

    @Test
    fun splitsAsEvenlyAsPossible() {
        assertEquals(listOf(67, 67, 66), SplitPlan.sizes(200, 3))
        assertEquals(listOf(1..3, 4..6), SplitPlan.ranges(6, 2))
        assertEquals("4 partes de 50 páginas", SplitPlan.summary(200, 4))
        assertEquals("3 partes: 67, 67 e 66 páginas", SplitPlan.summary(200, 3))
        fails { SplitPlan.sizes(3, 4) }
        fails { SplitPlan.sizes(10, 1) }
    }

    @Test
    fun insertPositions() {
        assertEquals(0, InsertPosition.Start.index(10))
        assertEquals(10, InsertPosition.End.index(10))
        assertEquals(3, InsertPosition.After(3).index(10))
        assertEquals(10, InsertPosition.After(99).index(10))
    }

    @Test
    fun searchIgnoresAccentsCaseAndRepeatedSpaces() {
        val text = "Cláusula  DÉCIMA: o contrato vale\npor trinta meses. clausula decima"
        val hits = TextFold.find(text, "clausula decima")
        assertEquals(2, hits.size)
        assertEquals("Cláusula  DÉCIMA", text.substring(hits[0].first, hits[0].last + 1))
        // a busca atravessa a quebra de linha como espaço
        assertEquals(1, TextFold.find(text, "vale por").size)
        assertTrue(TextFold.find(text, "   ").isEmpty())
        // mesmo comprimento: as posições servem para o texto original
        assertEquals(text.length, TextFold.fold(text).length)
    }
}
