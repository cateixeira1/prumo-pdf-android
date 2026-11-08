package br.com.prumopdf.core

import br.com.prumopdf.i18n.I18n
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.i18n.LN

/** Uma página na organização: de qual página do original veio (a partir de 0) e quanto girar a mais. */
data class PageEntry(val source: Int, val extraQuarters: Int = 0, val key: Long = nextKey()) {
    companion object {
        private var counter = 0L
        @Synchronized fun nextKey() = ++counter
    }
}

/** Onde incluir páginas novas. */
sealed interface InsertPosition {
    data object Start : InsertPosition
    data object End : InsertPosition
    data class After(val page: Int) : InsertPosition   // depois da página N (começando em 1)

    fun index(pageCount: Int): Int = when (this) {
        Start -> 0
        End -> pageCount
        is After -> page.coerceIn(0, pageCount)
    }
}

/** Converte textos como "1, 3-5, 10-fim" em números de página (começando em 1) e vice-versa. Igual ao iPhone. */
object PageSelection {
    /** Palavras aceitas para "última página": fim, end, última e as palavras de todos os idiomas do app. */
    val endWords: Set<String>
        get() = (setOf("fim", "end", "última", "ultima") + I18n.allTranslations("fim").map { it.lowercase() }).toSet()

    fun parse(text: String, pageCount: Int): List<Int> {
        val parts = text.replace(";", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw UserError(L("Informe ao menos uma página."))
        val pages = sortedSetOf<Int>()
        val ends = endWords
        fun pagesText() = LN(pageCount, "1 página", "{} páginas")
        fun notUnderstood(part: String) = UserError(LF("Não entendi “{}”. Use algo como 1, 3-5, 10-fim.", part))
        for (part in parts) {
            val lower = part.lowercase()
            if (lower in ends) {
                pages += pageCount
                continue
            }
            val dash = part.indexOfFirst { it == '-' || it == '–' || it == '—' }
            if (dash >= 0) {
                val a = part.substring(0, dash).trim()
                val b = part.substring(dash + 1).trim().lowercase()
                val start = a.toIntOrNull() ?: throw notUnderstood(part)
                val end = when {
                    b.isEmpty() || b in ends -> pageCount
                    else -> b.toIntOrNull() ?: throw notUnderstood(part)
                }
                if (start > end) throw UserError(LF("Intervalo invertido: {}", part))
                if (start < 1 || end > pageCount) throw UserError(LF("O documento tem {}; “{}” está fora do intervalo.", pagesText(), part))
                pages.addAll(start..end)
            } else {
                val n = part.toIntOrNull() ?: throw notUnderstood(part)
                if (n < 1 || n > pageCount) throw UserError(LF("O documento tem {}; a página {} não existe.", pagesText(), n))
                pages += n
            }
        }
        return pages.toList()
    }

    /** [1,2,3,5,7,8] -> "1-3, 5, 7-8" */
    fun format(pages: Collection<Int>): String {
        val sorted = pages.toSortedSet().toList()
        if (sorted.isEmpty()) return ""
        val out = mutableListOf<String>()
        var start = sorted[0]
        var prev = start
        for (p in sorted.drop(1) + Int.MIN_VALUE) {
            if (p == prev + 1) {
                prev = p
                continue
            }
            out += if (start == prev) "$start" else "$start-$prev"
            start = p
            prev = p
        }
        return out.joinToString(", ")
    }
}

/** Divide um total de páginas em partes o mais iguais possível (200 em 3 = 67, 67, 66). Igual ao iPhone. */
object SplitPlan {
    fun sizes(pageCount: Int, parts: Int): List<Int> {
        if (parts < 2) throw UserError(L("Escolha pelo menos 2 partes."))
        if (pageCount < parts) {
            throw UserError(LF("O documento tem {}; não dá para dividir em {} partes.", LN(pageCount, "1 página", "{} páginas"), parts))
        }
        val base = pageCount / parts
        val extra = pageCount % parts
        return (0 until parts).map { if (it < extra) base + 1 else base }
    }

    /** Faixas de páginas (começando em 1) de cada parte. */
    fun ranges(pageCount: Int, parts: Int): List<IntRange> {
        var start = 1
        return sizes(pageCount, parts).map { size -> (start until start + size).also { start += size } }
    }

    /** "4 partes de 50 páginas" ou "3 partes: 67, 67 e 66 páginas". */
    fun summary(pageCount: Int, parts: Int): String? {
        val s = runCatching { sizes(pageCount, parts) }.getOrNull() ?: return null
        if (s.toSet().size == 1) return LN(parts, "1 parte de {}", "{} partes de {}", LN(s[0], "1 página", "{} páginas"))
        val list = s.map { it.toString() }
        return LF("{} partes: {} páginas", parts, LF("{} e {}", list.dropLast(1).joinToString(", "), list.last()))
    }
}

/** Busca sem diferenciar maiúsculas e acentos, mantendo a posição de cada letra (para destacar no PDF). */
object TextFold {
    fun char(c: Char): Char {
        if (c.code < 128) return c.lowercaseChar()
        val d = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
        return (d.firstOrNull() ?: c).lowercaseChar()
    }

    /** Mesmo comprimento do original: cada letra vira a letra-base minúscula; quebras e tabulações viram espaço. */
    fun fold(s: String): String {
        val out = CharArray(s.length)
        for (i in s.indices) {
            val c = s[i]
            out[i] = if (c == '\r' || c == '\n' || c == '\t' || c == ' ') ' ' else char(c)
        }
        return String(out)
    }

    /** Posições onde [query] aparece em [text] (sem acentos, maiúsculas e com espaços repetidos tratados como um). */
    fun find(text: String, query: String): List<IntRange> {
        val q = fold(query.trim()).replace(Regex(" +"), " ")
        if (q.isEmpty()) return emptyList()
        val t = fold(text)
        val out = mutableListOf<IntRange>()
        var i = 0
        while (i < t.length) {
            val end = matchAt(t, i, q)
            if (end > 0) {
                out += i until end
                i = end
            } else {
                i++
            }
        }
        return out
    }

    private fun matchAt(t: String, start: Int, q: String): Int {
        var i = start
        var j = 0
        while (j < q.length) {
            if (i >= t.length) return -1
            if (q[j] == ' ') {
                if (t[i] != ' ') return -1
                while (i < t.length && t[i] == ' ') i++
                j++
            } else {
                if (t[i] != q[j]) return -1
                i++
                j++
            }
        }
        return i
    }
}
