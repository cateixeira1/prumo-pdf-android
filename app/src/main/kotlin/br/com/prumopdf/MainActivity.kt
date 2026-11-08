package br.com.prumopdf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import br.com.prumopdf.i18n.I18n
import br.com.prumopdf.ui.Hinge
import br.com.prumopdf.ui.PrumoAppUI
import br.com.prumopdf.ui.PrumoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var hinge by mutableStateOf<Hinge?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // O idioma escolhido em Configurações › Apps (Android 13+) chega pela configuração da Activity.
        I18n.setLanguage(AppLanguage.resolve(this))
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PrumoTheme {
                PrumoAppUI(vm, hinge)
            }
        }
        // Dobradiça dos dobráveis: em pé e aberto como um livro, uma tela de cada lado.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    hinge = fold?.let {
                        val vertical = it.orientation == FoldingFeature.Orientation.VERTICAL
                        Hinge(
                            left = if (vertical) it.bounds.left else it.bounds.top,
                            right = if (vertical) it.bounds.right else it.bounds.bottom,
                            vertical = vertical,
                            separating = it.isSeparating,
                        )
                    }
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    /** "Abrir com o Prumo" e "Compartilhar com o Prumo": copia os PDFs para a biblioteca. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamListExtra()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) vm.importUris(uris, folder = "", openSingle = true)
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streamListExtra(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
}
