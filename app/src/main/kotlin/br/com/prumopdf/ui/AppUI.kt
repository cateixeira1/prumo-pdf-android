package br.com.prumopdf.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.prumopdf.AppViewModel
import br.com.prumopdf.Route
import br.com.prumopdf.Tool
import br.com.prumopdf.core.FolderItem
import br.com.prumopdf.core.PdfItem
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LF
import br.com.prumopdf.i18n.LN

/** Dobradiça de um aparelho dobrável, quando ela separa a tela em duas partes (em pixels da janela). */
data class Hinge(val left: Int, val right: Int, val vertical: Boolean, val separating: Boolean)

/** Janelas de diálogo abertas pela interface. */
sealed interface DialogState {
    data class Rename(val item: PdfItem) : DialogState
    data class Move(val items: List<PdfItem>) : DialogState
    data class NewFolder(val parent: String, val offerAdd: Boolean, val then: ((String) -> Unit)? = null) : DialogState
    data class RenameFolder(val folder: FolderItem) : DialogState
    data class DeleteFolder(val folder: FolderItem) : DialogState
    data class AddPdfs(val folder: String, val justCreated: Boolean = false) : DialogState
    data class MoveOrCopy(val items: List<PdfItem>, val folder: String) : DialogState
    data class NotReady(val title: String) : DialogState
}

/** Pedido de escolher PDFs da biblioteca (ferramentas da tela inicial, "Adicionar PDFs"). */
data class ChooserRequest(
    val title: String,
    val multiple: Boolean,
    val excludeFolder: String? = null,
    val onDone: (List<PdfItem>) -> Unit,
)

class UiActions(val vm: AppViewModel) {
    var dialog by mutableStateOf<DialogState?>(null)
    var chooser by mutableStateOf<ChooserRequest?>(null)
    var wide by mutableStateOf(false)
    internal var launchImport: (String) -> Unit = {}
    internal var share: (List<PdfItem>) -> Unit = {}

    fun open(route: Route) = vm.open(route, wide)

    fun importFromDevice(folder: String) {
        vm.importFolder = folder
        launchImport(folder)
    }

    fun runTool(tool: Tool) {
        if (!tool.ready) {
            dialog = DialogState.NotReady(toolTitle(tool))
            return
        }
        chooser = ChooserRequest(L(tool.question), multiple = false) { picked ->
            val item = picked.firstOrNull() ?: return@ChooserRequest
            when (tool) {
                Tool.READ -> open(Route.Reader(item.id))
                else -> open(Route.Document(item.id))
            }
        }
    }

    fun addFromPrumo(folder: String) {
        chooser = ChooserRequest(L("Adicionar PDFs"), multiple = true, excludeFolder = folder) { picked ->
            if (picked.isNotEmpty()) dialog = DialogState.MoveOrCopy(picked, folder)
        }
    }

    fun menuFor(item: PdfItem): List<MenuAction> = listOf(
        MenuAction(L("Ler"), Icons.AutoMirrored.Filled.MenuBook) { open(Route.Reader(item.id)) },
        MenuAction(L("Compartilhar"), Icons.Filled.Share) { share(listOf(item)) },
        MenuAction(
            if (item.meta.favorite) L("Remover dos favoritos") else L("Favoritar"),
            if (item.meta.favorite) Icons.Filled.StarBorder else Icons.Filled.Star,
        ) { vm.toggleFavorite(item) },
        MenuAction(L("Renomear"), Icons.Filled.Edit) { dialog = DialogState.Rename(item) },
        MenuAction(L("Mover para pasta"), Icons.AutoMirrored.Filled.DriveFileMove) { dialog = DialogState.Move(listOf(item)) },
        MenuAction(L("Duplicar"), Icons.Filled.ContentCopy) { vm.duplicate(item) },
        MenuAction(L("Excluir"), Icons.Filled.Delete, destructive = true) { vm.delete(listOf(item)) },
    )

    fun folderMenu(folder: FolderItem): List<MenuAction> = listOf(
        MenuAction(L("Renomear"), Icons.Filled.Edit) { dialog = DialogState.RenameFolder(folder) },
        MenuAction(L("Excluir pasta"), Icons.Filled.Delete, destructive = true) { dialog = DialogState.DeleteFolder(folder) },
    )

    companion object {
        fun toolTitle(tool: Tool): String = when (tool) {
            Tool.READ -> L("Ler")
            Tool.OCR -> L("Fazer OCR")
            Tool.ORGANIZE -> L("Organizar páginas")
            Tool.INSERT -> L("Incluir páginas")
            Tool.REMOVE -> L("Remover páginas")
            Tool.EXTRACT -> L("Extrair páginas")
            Tool.SPLIT -> L("Dividir em partes")
            Tool.MERGE -> L("Combinar PDFs")
            Tool.CROP -> L("Cortar e girar")
            Tool.NUMBER -> L("Numerar páginas")
            Tool.SIGN -> L("Assinar digitalmente")
            Tool.COMPRESS -> L("Comprimir PDF")
            Tool.CONVERT -> L("Converter em PDF")
        }
    }
}

val LocalActions = staticCompositionLocalOf<UiActions> { error("UiActions") }

@Composable
fun PrumoAppUI(vm: AppViewModel, hinge: Hinge?) {
    val actions = remember(vm) { UiActions(vm) }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris, vm.importFolder)
    }
    actions.launchImport = { importer.launch(arrayOf("application/pdf")) }
    actions.share = { items ->
        try {
            context.startActivity(vm.shareIntent(items))
        } catch (_: ActivityNotFoundException) {
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val notice = vm.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            val r = snackbar.showSnackbar(
                notice.text, actionLabel = if (notice.undo != null) L("Desfazer") else null,
                duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) notice.undo?.invoke()
            vm.clearNotice(notice)
        }
    }

    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize().background(Prumo.colors.background)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val split: Pair<Dp, Dp>? = with(density) {
                    when {
                        // Dobrável aberto como um livro: uma tela de cada lado da dobradiça.
                        hinge != null && hinge.vertical && hinge.separating -> hinge.left.toDp() to hinge.right.toDp()
                        maxWidth >= 840.dp -> {
                            val left = (maxWidth * 0.42f).coerceIn(340.dp, 460.dp)
                            left to left
                        }
                        else -> null
                    }
                }
                actions.wide = split != null
                if (split == null) {
                    SinglePane(vm)
                } else {
                    TwoPanes(vm, split.first, split.second, maxWidth)
                }
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 8.dp),
            )
        }
        Dialogs(actions)
        actions.chooser?.let { req ->
            DocumentChooser(req, onDismiss = { actions.chooser = null }) { picked ->
                actions.chooser = null
                req.onDone(picked)
            }
        }
    }
}

@Composable
private fun SinglePane(vm: AppViewModel) {
    AnimatedContent(vm.current, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tela") { route ->
        RouteScreen(vm, route, showBack = vm.stack.size > 1)
    }
}

@Composable
private fun TwoPanes(vm: AppViewModel, leftEnd: Dp, rightStart: Dp, total: Dp) {
    val listRoute = vm.stack.lastOrNull { !it.isDetail } ?: Route.Home
    val detail = vm.stack.lastOrNull { it.isDetail }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(leftEnd).fillMaxHeight()) {
            RouteScreen(vm, listRoute, showBack = vm.stack.count { !it.isDetail } > 1, pane = Pane.LEFT)
        }
        if (rightStart > leftEnd) Spacer(Modifier.width(rightStart - leftEnd))
        else Box(Modifier.width(1.dp).fillMaxHeight().background(Prumo.colors.tile))
        Box(Modifier.width(total - maxOf(rightStart, leftEnd + 1.dp)).fillMaxHeight()) {
            if (detail == null) {
                EmptyState(L("Escolha um PDF para ver aqui."), Modifier.align(Alignment.Center))
            } else {
                RouteScreen(vm, detail, showBack = true, pane = Pane.RIGHT)
            }
        }
    }
}

enum class Pane { FULL, LEFT, RIGHT }

@Composable
fun RouteScreen(vm: AppViewModel, route: Route, showBack: Boolean, pane: Pane = Pane.FULL) {
    val snapshot by vm.state.collectAsState()
    when (route) {
        Route.Home -> HomeScreen(vm, snapshot, compact = pane == Pane.LEFT)
        is Route.List -> DocListScreen(vm, snapshot, route.list, showBack)
        Route.Folders -> FoldersScreen(vm, snapshot, showBack)
        is Route.Folder -> FolderScreen(vm, snapshot, route.path, showBack)
        Route.Settings -> SettingsScreen(vm, showBack)
        is Route.Document -> DocumentScreen(vm, snapshot, route.id, showBack)
        is Route.Reader -> ReaderScreen(vm, snapshot, route.id, showBack)
    }
}

@Composable
private fun Dialogs(actions: UiActions) {
    val vm = actions.vm
    val snapshot by vm.state.collectAsState()
    val close = { actions.dialog = null }
    when (val d = actions.dialog) {
        null -> {}
        is DialogState.Rename -> TextInputDialog(L("Renomear"), d.item.name, L("Salvar"), close) {
            close()
            vm.rename(d.item, it)
        }
        is DialogState.Move -> FolderPickerDialog(
            L("Mover para pasta"), snapshot.folders,
            current = d.items.map { it.folder }.distinct().singleOrNull(), onDismiss = close,
            onNewFolder = {
                actions.dialog = DialogState.NewFolder("", offerAdd = false) { path -> vm.move(d.items, path) }
            },
        ) {
            close()
            vm.move(d.items, it)
        }
        is DialogState.NewFolder -> TextInputDialog(L("Nova pasta"), "", L("Criar"), close) { name ->
            close()
            vm.createFolder(d.parent, name) { path ->
                d.then?.invoke(path)
                if (d.offerAdd) actions.dialog = DialogState.AddPdfs(path, justCreated = true)
            }
        }
        is DialogState.RenameFolder -> TextInputDialog(L("Renomear"), d.folder.name, L("Salvar"), close) {
            close()
            vm.renameFolder(d.folder.path, it)
        }
        is DialogState.DeleteFolder -> ConfirmDialog(
            LF("Excluir a pasta “{}”?", d.folder.name),
            if (d.folder.totalDocCount > 0) LN(d.folder.totalDocCount, "O PDF dentro dela também será excluído.", "Os {} PDFs dentro dela também serão excluídos.") else null,
            L("Excluir"), destructive = true, onDismiss = close,
        ) {
            close()
            vm.deleteFolder(d.folder.path)
        }
        is DialogState.AddPdfs -> ChoiceDialog(
            if (d.justCreated) LF("Pasta “{}” criada", d.folder.substringAfterLast('/')) else L("Adicionar PDFs"),
            if (d.justCreated) L("Adicionar PDFs a esta pasta agora?") else null,
            listOf(
                L("Do aparelho…") to {
                    close()
                    actions.importFromDevice(d.folder)
                },
                L("PDFs do Prumo…") to {
                    close()
                    actions.addFromPrumo(d.folder)
                },
            ),
            onDismiss = close,
        )
        is DialogState.MoveOrCopy -> ChoiceDialog(
            LN(d.items.size, "1 PDF escolhido", "{} PDFs escolhidos"),
            L("Mover tira o PDF do lugar onde está; copiar deixa o original onde estava."),
            listOf(
                L("Mover") to {
                    close()
                    vm.move(d.items, d.folder)
                },
                L("Copiar") to {
                    close()
                    vm.copy(d.items, d.folder)
                },
            ),
            onDismiss = close,
        )
        is DialogState.NotReady -> InfoDialog(
            d.title, L("Esta função ainda não chegou ao Prumo para Android. Ela vem nas próximas versões."), close,
        )
    }
}
