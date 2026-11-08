package br.com.prumopdf

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import br.com.prumopdf.core.Library
import br.com.prumopdf.i18n.I18n
import br.com.prumopdf.platform.Thumbnails
import java.io.File
import java.util.Locale

class PrumoApplication : Application() {
    lateinit var library: Library
        private set
    lateinit var thumbnails: Thumbnails
        private set

    override fun onCreate() {
        super.onCreate()
        I18n.init({ code -> readCatalog(this, code) }, AppLanguage.resolve(this))
        library = Library(root = libraryRoot(this), supportDir = File(filesDir, "Prumo"))
        thumbnails = Thumbnails(File(cacheDir, "thumbs"))
    }

    companion object {
        /** Pasta da biblioteca (só do Prumo, dentro do aparelho). A etapa 5 permite escolher outra pasta. */
        fun libraryRoot(context: Context) = File(context.filesDir, "Prumo PDF")

        fun readCatalog(context: Context, code: String): String? =
            runCatching { context.assets.open("locales/$code.json").bufferedReader().use { it.readText() } }.getOrNull()
    }
}

/**
 * Idioma do app. No Android 13 ou mais novo, o idioma escolhido fica com o sistema (também aparece em
 * Configurações › Apps › Prumo PDF › Idioma). Nos mais antigos, fica guardado no próprio app.
 */
object AppLanguage {
    private const val PREFS = "prumo"
    private const val KEY = "language"

    fun resolve(context: Context): String {
        if (Build.VERSION.SDK_INT < 33) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let { return it }
        }
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return I18n.codeFor(locale)
    }

    /** Código escolhido pelo usuário, ou null para seguir o idioma do aparelho. */
    fun chosen(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 33) {
            val list = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (list.isEmpty) null else I18n.codeFor(list[0])
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    }

    fun set(context: Context, code: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            val tags = when (code) {
                null -> LocaleList.getEmptyLocaleList()
                "zh-CN" -> LocaleList.forLanguageTags("zh-CN")
                "zh-TW" -> LocaleList.forLanguageTags("zh-TW")
                "pt" -> LocaleList.forLanguageTags("pt-BR")
                else -> LocaleList.forLanguageTags(code)
            }
            context.getSystemService(LocaleManager::class.java).applicationLocales = tags
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (code == null) remove(KEY) else putString(KEY, code)
            }.apply()
        }
        I18n.setLanguage(code ?: I18n.codeFor(Locale.getDefault()))
    }
}
