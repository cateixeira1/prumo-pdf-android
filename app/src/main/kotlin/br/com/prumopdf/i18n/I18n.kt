package br.com.prumopdf.i18n

import java.util.Locale

// Tradução da interface. A chave é o próprio texto em português; as traduções ficam em
// assets/locales/<idioma>.json, os mesmos catálogos do Prumo para iPhone e Windows (mais os textos que só
// o Android tem). Regras, conferidas no GitHub por tools/i18n_check.py:
// - todo texto da interface passa por L("..."); com parte variável, LF("Página {} de {}", n, total);
//   com singular e plural, LN(n, "1 página", "{} páginas");
// - nome de arquivo, de pasta e outros textos do usuário não passam pela tradução.

object I18n {
    /** Idiomas da interface: código do catálogo e nome no próprio idioma. */
    val languages: List<Pair<String, String>> = listOf(
        "pt" to "Português", "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
        "ja" to "日本語", "zh-CN" to "中文 (简体)", "zh-TW" to "中文 (繁體, 台灣)", "nb" to "Norsk bokmål",
        "sv" to "Svenska", "da" to "Dansk", "fi" to "Suomi",
    )
    private val codes = languages.map { it.first }.toSet()

    /** Lê o catálogo de um idioma (o texto do arquivo JSON), ou null se não existir. */
    private var loader: (String) -> String? = { null }
    private val catalogs = HashMap<String, Map<String, String>>()

    @Volatile
    var current: String = "pt"
        private set

    @Volatile
    private var catalog: Map<String, String> = emptyMap()

    @Volatile
    private var english: Map<String, String> = emptyMap()

    fun init(loader: (String) -> String?, language: String) {
        this.loader = loader
        synchronized(catalogs) { catalogs.clear() }
        english = catalogFor("en")
        setLanguage(language)
    }

    fun setLanguage(language: String) {
        current = if (language in codes) language else "en"
        catalog = if (current == "pt") emptyMap() else catalogFor(current)
    }

    /** Código do catálogo para um idioma do sistema (inglês quando o Prumo não tem o idioma). */
    fun codeFor(locale: Locale): String {
        val lang = locale.language.lowercase()
        return when {
            lang == "zh" -> {
                val script = locale.script.lowercase()
                val region = locale.country.uppercase()
                if (script == "hant" || region in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            }
            lang == "nb" || lang == "no" || lang == "nn" -> "nb"
            lang in codes -> lang
            else -> "en"
        }
    }

    fun catalogFor(code: String): Map<String, String> {
        if (code == "pt") return emptyMap()
        synchronized(catalogs) {
            catalogs[code]?.let { return it }
            val text = loader(code)
            val map = if (text == null) emptyMap() else parseCatalog(text)
            catalogs[code] = map
            return map
        }
    }

    fun parseCatalog(text: String): Map<String, String> {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        return obj.mapValues { (_, v) -> (v as kotlinx.serialization.json.JsonPrimitive).content }
    }

    fun translate(key: String): String {
        if (current == "pt") return key
        return catalog[key] ?: english[key] ?: key
    }

    /** Tradução para um idioma qualquer (não necessariamente o da interface). */
    fun translate(key: String, to: String): String {
        if (to == "pt") return key
        return catalogFor(to)[key] ?: key
    }

    /** O texto em português e em todos os idiomas, começando pelo atual (reconhecer nomes vindos de outro idioma). */
    fun allTranslations(key: String): List<String> {
        val out = mutableListOf(translate(key))
        for ((code, _) in languages) {
            val t = translate(key, code)
            if (t !in out) out += t
        }
        return out
    }

    /** Locale para datas e números no idioma do app. */
    fun locale(): Locale = when (current) {
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        "pt" -> Locale.forLanguageTag("pt-BR")
        else -> Locale.forLanguageTag(current)
    }

    val isCJK: Boolean get() = current == "ja" || current == "zh-CN" || current == "zh-TW"
}

/** Traduz um texto escrito em português para o idioma do app. */
fun L(key: String): String = I18n.translate(key)

/** Traduz o modelo e preenche cada "{}" com os valores, na ordem. */
fun LF(template: String, vararg args: Any?): String = fillTemplate(L(template), args.toList())

/** Singular ou plural: LN(3, "1 página", "{} páginas") → "3 páginas". O número entra no primeiro "{}". */
fun LN(n: Int, one: String, other: String, vararg args: Any?): String =
    if (n == 1) fillTemplate(L(one), args.toList()) else fillTemplate(L(other), listOf<Any?>(n) + args.toList())

fun fillTemplate(template: String, args: List<Any?>): String {
    if (args.isEmpty() || !template.contains("{}")) return template
    val out = StringBuilder()
    var rest = template
    var i = 0
    while (true) {
        val at = rest.indexOf("{}")
        if (at < 0) break
        out.append(rest, 0, at)
        if (i < args.size) out.append(args[i]?.toString() ?: "")
        i++
        rest = rest.substring(at + 2)
    }
    return out.append(rest).toString()
}

/** Junta frases com a pontuação do idioma (em japonês e chinês, "。" e sem espaço). */
fun sentences(vararg parts: String): String {
    val cjk = I18n.isCJK
    val ends = setOf('.', '!', '?', '…', '。', '！', '？', ':', '：')
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
        .joinToString(if (cjk) "" else " ") { p -> if (p.last() in ends) p else p + if (cjk) "。" else "." }
}
