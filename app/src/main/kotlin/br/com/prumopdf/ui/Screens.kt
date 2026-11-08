package br.com.prumopdf.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.prumopdf.AppLanguage
import br.com.prumopdf.AppViewModel
import br.com.prumopdf.AppViewModel.Companion.title
import br.com.prumopdf.BuildConfig
import br.com.prumopdf.Route
import br.com.prumopdf.Tool
import br.com.prumopdf.core.DocList
import br.com.prumopdf.core.LibrarySnapshot
import br.com.prumopdf.core.PdfItem
import br.com.prumopdf.i18n.I18n
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LN
import kotlinx.coroutines.delay

// region Estrutura das telas

@Composable
fun ScreenHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    large: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, L("Voltar"), tint = Palette.blue)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            if (!large) {
                Text(
                    title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Prumo.colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            actions()
        }
        if (large) {
            Text(
                title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Prumo.colors.text, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 36.sp,
                modifier = Modifier.padding(horizontal = Dimens.margin).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
fun ScrollScreen(header: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        header()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.margin)
                .windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp),
        ) { content() }
    }
}

@Composable
private fun Footer() {
    Text(
        "Color Software", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Prumo.colors.secondaryText,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
    )
}

// endregion

// region Tela inicial

private data class HomeTile(
    val title: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val count: Int? = null,
    val shape: IconShape = IconShape.CIRCLE,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(vm: AppViewModel, snapshot: LibrarySnapshot, compact: Boolean = false) {
    val actions = LocalActions.current
    val lib = vm.library
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<PdfItem>()) }
    LaunchedEffect(query, snapshot.revision) {
        delay(150)
        results = lib.search(query)
    }
    ScrollScreen(header = {
        ScreenHeader("Prumo PDF", showBack = false, onBack = {}) {
            IconButton(onClick = { actions.open(Route.Settings) }, modifier = Modifier.testTag("settings")) {
                Icon(Icons.Filled.Settings, L("Ajustes"), tint = Palette.blue)
            }
        }
    }) {
        SearchField(query, { query = it }, L("Buscar nome ou texto"))
        Spacer(Modifier.height(Dimens.spacing))
        if (query.isBlank()) {
            val tiles = listOf(
                HomeTile(L("Escanear"), Icons.Filled.PhotoCamera, Palette.yellow, shape = IconShape.ROUNDED, enabled = false) {
                    actions.dialog = DialogState.NotReady(L("Escanear"))
                },
                HomeTile(L("Importar"), Icons.Filled.FileDownload, Palette.blue, shape = IconShape.ROUNDED) { actions.importFromDevice("") },
                HomeTile(L("Todos os PDFs"), Icons.Filled.FileCopy, Palette.red, snapshot.items.size) { actions.open(Route.List(DocList.ALL)) },
                HomeTile(L("Recentes"), Icons.Filled.Schedule, Palette.gray, lib.count(DocList.RECENT)) { actions.open(Route.List(DocList.RECENT)) },
                HomeTile(L("Sem OCR"), Icons.Filled.DocumentScanner, Palette.orange, lib.count(DocList.PENDING_OCR), IconShape.ROUNDED) {
                    actions.open(Route.List(DocList.PENDING_OCR))
                },
                HomeTile(L("Com OCR"), Icons.Filled.Verified, Palette.green, lib.count(DocList.OCR)) { actions.open(Route.List(DocList.OCR)) },
                HomeTile(L("Favoritos"), Icons.Filled.Star, Palette.yellow, lib.count(DocList.FAVORITES), IconShape.ROUNDED) {
                    actions.open(Route.List(DocList.FAVORITES))
                },
                HomeTile(L("Pastas"), Icons.Filled.Folder, Palette.indigo, lib.topLevelFolders.size) { actions.open(Route.Folders) },
            )
            HomeGrid(tiles, completeRows = true)
            SectionTitle(L("Ferramentas"))
            fun tool(t: Tool, icon: ImageVector, color: androidx.compose.ui.graphics.Color, shape: IconShape = IconShape.CIRCLE) =
                HomeTile(UiActions.toolTitle(t), icon, color, shape = shape, enabled = t.ready) { actions.runTool(t) }
            val tools = listOf(
                tool(Tool.READ, Icons.AutoMirrored.Filled.MenuBook, Palette.blue),
                tool(Tool.OCR, Icons.Filled.DocumentScanner, Palette.orange, IconShape.ROUNDED),
                tool(Tool.ORGANIZE, Icons.Filled.GridView, Palette.purple, IconShape.ROUNDED),
                tool(Tool.INSERT, Icons.Filled.LibraryAdd, Palette.teal, IconShape.ROUNDED),
                tool(Tool.REMOVE, Icons.Filled.RemoveCircleOutline, Palette.red, IconShape.ROUNDED),
                tool(Tool.EXTRACT, Icons.AutoMirrored.Filled.NoteAdd, Palette.indigo),
                tool(Tool.SPLIT, Icons.Filled.ContentCut, Palette.pink),
                tool(Tool.MERGE, Icons.Filled.Layers, Palette.purple, IconShape.ROUNDED),
                tool(Tool.CROP, Icons.Filled.CropRotate, Palette.indigo, IconShape.ROUNDED),
                tool(Tool.NUMBER, Icons.Filled.FormatListNumbered, Palette.indigo),
                tool(Tool.SIGN, Icons.Filled.Draw, Palette.blue, IconShape.ROUNDED),
                tool(Tool.COMPRESS, Icons.Filled.Compress, Palette.teal, IconShape.ROUNDED),
            )
            HomeGrid(tools, completeRows = true)
            Spacer(Modifier.height(Dimens.spacing))
            Tile(
                UiActions.toolTitle(Tool.CONVERT), Icons.Filled.Transform, Palette.teal, Modifier.fillMaxWidth(),
                subtitle = L("Fotos, imagens, Word, Excel, PowerPoint, texto"), enabled = false,
            ) { actions.runTool(Tool.CONVERT) }
            Footer()
        } else {
            if (results.isEmpty()) EmptyState(L("Nenhum PDF encontrado."))
            else DocGridStatic(vm, results, showFolder = true)
        }
    }
}

@Composable
private fun HomeGrid(tiles: List<HomeTile>, completeRows: Boolean) {
    TileGrid(tiles.size, completeRows = completeRows) { i, mod ->
        val t = tiles[i]
        Tile(t.title, t.icon, t.color, mod, count = t.count?.toString(), shape = t.shape, enabled = t.enabled, onClick = t.onClick)
    }
}

// endregion

// region Listas de PDFs

/** Grade de PDFs dentro de uma tela que já rola (tela inicial, pastas). */
@Composable
fun DocGridStatic(vm: AppViewModel, items: List<PdfItem>, showFolder: Boolean = false) {
    val actions = LocalActions.current
    TileGrid(items.size, minTileWidth = 160.dp) { i, mod ->
        val item = items[i]
        DocTile(
            item, vm.thumbnails, mod, needsOCR = vm.library.needsOCRAttention(item), showFolder = showFolder,
            onFavorite = { vm.toggleFavorite(item) }, menu = { actions.menuFor(item) },
        ) { actions.open(Route.Document(item.id)) }
    }
}

@Composable
fun DocListScreen(vm: AppViewModel, snapshot: LibrarySnapshot, list: DocList, showBack: Boolean) {
    val actions = LocalActions.current
    val items = remember(snapshot.revision, list) { vm.library.items(list) }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val grid = rememberLazyGridState()
    val ranges = remember(items) { RangeSelection({ items.map { it.id } }, selected) }
    LaunchedEffect(items) { selected.retainAll(items.map { it.id }.toSet()) }
    BackHandler(enabled = selecting) {
        selecting = false
        selected.clear()
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(list.title(), showBack, { vm.back() }) {
            if (items.isNotEmpty()) {
                TextButton(onClick = {
                    selecting = !selecting
                    selected.clear()
                }, modifier = Modifier.testTag("selectMode")) { Text(if (selecting) L("Cancelar") else L("Selecionar")) }
            }
        }
        if (items.isEmpty()) {
            EmptyState(L("Nenhum PDF"))
        } else {
            if (selecting) {
                Text(
                    L("Toque nos PDFs, ou deslize o dedo de lado sobre eles para marcar vários."),
                    fontSize = 13.sp, color = Prumo.colors.secondaryText, modifier = Modifier.padding(horizontal = Dimens.margin, vertical = 4.dp),
                )
            }
            val gridMod = Modifier.weight(1f).fillMaxWidth().testTag("docgrid")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                state = grid,
                contentPadding = PaddingValues(start = Dimens.margin, end = Dimens.margin, bottom = if (selecting) 16.dp else 96.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacing),
                modifier = if (selecting) gridMod.dragSelect(grid, ranges::start, ranges::range)
                else gridMod.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                items(items, key = { it.id }) { item ->
                    DocTile(
                        item, vm.thumbnails, needsOCR = vm.library.needsOCRAttention(item), showFolder = true,
                        selection = if (selecting) item.id in selected else null,
                        onFavorite = { vm.toggleFavorite(item) }, menu = { actions.menuFor(item) },
                    ) {
                        if (selecting) {
                            if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                        } else {
                            actions.open(Route.Document(item.id))
                        }
                    }
                }
            }
            if (selecting) {
                val chosen = items.filter { it.id in selected }
                SelectionBar(chosen.size) {
                    IconButton(onClick = { actions.merge(chosen) }, enabled = chosen.size >= 2, modifier = Modifier.testTag("selMerge")) {
                        Icon(Icons.Filled.Layers, L("Combinar PDFs"))
                    }
                    IconButton(onClick = { actions.dialog = DialogState.Move(chosen) }, enabled = chosen.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, L("Mover para pasta"))
                    }
                    IconButton(onClick = { actions.share(chosen) }, enabled = chosen.isNotEmpty()) {
                        Icon(Icons.Filled.Share, L("Compartilhar"))
                    }
                    IconButton(onClick = { vm.setFavorite(chosen, !chosen.all { it.meta.favorite }) }, enabled = chosen.isNotEmpty()) {
                        Icon(Icons.Filled.Star, L("Favoritar"), tint = if (chosen.isNotEmpty()) Palette.yellow else Prumo.colors.placeholder)
                    }
                    IconButton(onClick = {
                        vm.delete(chosen)
                        selected.clear()
                        selecting = false
                    }, enabled = chosen.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, L("Excluir"), tint = if (chosen.isNotEmpty()) Palette.red else Prumo.colors.placeholder)
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionBar(count: Int, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Prumo.colors.tile.copy(alpha = 0.7f)).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp).testTag("selectionBar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            LN(count, "1 selecionado", "{} selecionados"), fontSize = 14.sp, color = Prumo.colors.text,
            modifier = Modifier.weight(1f).padding(start = 8.dp).testTag("selectionCount"),
        )
        actions()
    }
}

@Composable
fun FoldersScreen(vm: AppViewModel, snapshot: LibrarySnapshot, showBack: Boolean) {
    val actions = LocalActions.current
    val folders = remember(snapshot.revision) { vm.library.topLevelFolders }
    ScrollScreen(header = {
        ScreenHeader(L("Pastas"), showBack, { vm.back() }) {
            IconButton(onClick = { actions.dialog = DialogState.NewFolder("", offerAdd = true) }) {
                Icon(Icons.Filled.CreateNewFolder, L("Nova pasta"), tint = Palette.blue)
            }
        }
    }) {
        if (folders.isEmpty()) {
            EmptyState(L("Nenhuma pasta"))
            TextButton(onClick = { actions.dialog = DialogState.NewFolder("", offerAdd = true) }, modifier = Modifier.fillMaxWidth()) {
                Text(L("Nova pasta"))
            }
        } else {
            TileGrid(folders.size) { i, mod ->
                val f = folders[i]
                FolderTile(f, mod, menu = { actions.folderMenu(f) }) { actions.open(Route.Folder(f.path)) }
            }
        }
    }
}

@Composable
fun FolderScreen(vm: AppViewModel, snapshot: LibrarySnapshot, path: String, showBack: Boolean) {
    val actions = LocalActions.current
    val folder = remember(snapshot.revision, path) { vm.library.folder(path) }
    val subfolders = remember(snapshot.revision, path) { vm.library.subfolders(path) }
    val items = remember(snapshot.revision, path) { vm.library.itemsIn(path) }
    var menuOpen by remember { mutableStateOf(false) }
    ScrollScreen(header = {
        ScreenHeader(path.substringAfterLast('/'), showBack, { vm.back() }) {
            IconButton(onClick = { actions.dialog = DialogState.AddPdfs(path) }) {
                Icon(Icons.Filled.Add, L("Adicionar PDFs"), tint = Palette.blue)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, L("Mais opções"), tint = Palette.blue) }
                val menu = buildList {
                    add(MenuAction(L("Nova subpasta"), Icons.Filled.CreateNewFolder) {
                        actions.dialog = DialogState.NewFolder(path, offerAdd = true)
                    })
                    if (folder != null) addAll(actions.folderMenu(folder))
                }
                ActionMenu(menuOpen, { menuOpen = false }, menu)
            }
        }
    }) {
        if (subfolders.isNotEmpty()) {
            TileGrid(subfolders.size) { i, mod ->
                val f = subfolders[i]
                FolderTile(f, mod, menu = { actions.folderMenu(f) }) { actions.open(Route.Folder(f.path)) }
            }
            Spacer(Modifier.height(Dimens.spacing))
        }
        if (items.isEmpty() && subfolders.isEmpty()) {
            EmptyState(L("Pasta vazia"))
            TextButton(onClick = { actions.dialog = DialogState.AddPdfs(path) }, modifier = Modifier.fillMaxWidth()) {
                Text(L("Adicionar PDFs"))
            }
        } else {
            DocGridStatic(vm, items)
        }
    }
}

// endregion

// region Tela do documento

@Composable
fun DocumentScreen(vm: AppViewModel, snapshot: LibrarySnapshot, id: String, showBack: Boolean) {
    val actions = LocalActions.current
    val item = remember(snapshot.revision, id) { vm.library.item(id) } ?: return
    val context = LocalContext.current
    ScrollScreen(header = {
        ScreenHeader(item.name, showBack, { vm.back() }, large = false) {
            IconButton(onClick = { vm.toggleFavorite(item) }) {
                Icon(
                    if (item.meta.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (item.meta.favorite) L("Remover dos favoritos") else L("Favoritar"),
                    tint = if (item.meta.favorite) Palette.yellow else Prumo.colors.placeholder,
                )
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, L("Mais opções"), tint = Palette.blue) }
                ActionMenu(menuOpen, { menuOpen = false }, actions.menuFor(item).drop(1))
            }
        }
    }) {
        Row(verticalAlignment = Alignment.Top) {
            PageThumbnail(item, vm.thumbnails, 96.dp, 128.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Prumo.colors.text)
                Spacer(Modifier.height(6.dp))
                val info = buildList {
                    item.pageCount?.let { add(LN(it, "1 página", "{} páginas")) }
                    add(Formatter.formatShortFileSize(context, item.size))
                    add(if (item.folder.isEmpty()) L("Sem pasta") else item.folder)
                }
                Text(info.joinToString(" · "), fontSize = 14.sp, color = Prumo.colors.secondaryText)
                if (item.isLocked) {
                    Spacer(Modifier.height(6.dp))
                    Badge(L("Com senha"), Palette.purple)
                }
            }
        }
        Spacer(Modifier.height(Dimens.spacing * 2))
        Tile(L("Ler"), Icons.AutoMirrored.Filled.MenuBook, Palette.blue, Modifier.fillMaxWidth().testTag("read")) {
            actions.open(Route.Reader(item.id))
        }
        if (item.isLocked) {
            Spacer(Modifier.height(Dimens.spacing))
            Tile(
                L("Remover senha"), Icons.Filled.LockOpen, Palette.purple, Modifier.fillMaxWidth().testTag("removePassword"),
                subtitle = L("Cria uma cópia sem senha"),
            ) { actions.dialog = DialogState.RemovePassword(item) }
        }
        if (vm.library.canUndo(item)) {
            Spacer(Modifier.height(Dimens.spacing))
            Tile(
                L("Desfazer última alteração"), Icons.AutoMirrored.Filled.Undo, Palette.gray, Modifier.fillMaxWidth().testTag("undoChange"),
            ) { vm.undoChange(item) }
        }
        SectionTitle(L("Ferramentas"))
        val tools = listOf(
            Tool.ORGANIZE, Tool.INSERT, Tool.REMOVE, Tool.EXTRACT, Tool.SPLIT, Tool.MERGE,
            Tool.OCR, Tool.CROP, Tool.NUMBER, Tool.SIGN, Tool.COMPRESS,
        )
        TileGrid(tools.size) { i, mod ->
            val t = tools[i]
            Tile(
                UiActions.toolTitle(t), toolIcon(t), toolColor(t), mod.testTag("tool-${t.name}"), enabled = t.ready && !item.isLocked,
            ) { actions.toolOn(t, item) }
        }
    }
}

fun toolColor(t: Tool): androidx.compose.ui.graphics.Color = when (t) {
    Tool.READ, Tool.SIGN -> Palette.blue
    Tool.OCR -> Palette.orange
    Tool.ORGANIZE, Tool.MERGE -> Palette.purple
    Tool.INSERT, Tool.COMPRESS, Tool.CONVERT -> Palette.teal
    Tool.REMOVE -> Palette.red
    Tool.SPLIT -> Palette.pink
    Tool.EXTRACT, Tool.CROP, Tool.NUMBER -> Palette.indigo
}

fun toolIcon(t: Tool): ImageVector = when (t) {
    Tool.READ -> Icons.AutoMirrored.Filled.MenuBook
    Tool.OCR -> Icons.Filled.DocumentScanner
    Tool.ORGANIZE -> Icons.Filled.GridView
    Tool.INSERT -> Icons.Filled.LibraryAdd
    Tool.REMOVE -> Icons.Filled.RemoveCircleOutline
    Tool.EXTRACT -> Icons.AutoMirrored.Filled.NoteAdd
    Tool.SPLIT -> Icons.Filled.ContentCut
    Tool.MERGE -> Icons.Filled.Layers
    Tool.CROP -> Icons.Filled.CropRotate
    Tool.NUMBER -> Icons.Filled.FormatListNumbered
    Tool.SIGN -> Icons.Filled.Draw
    Tool.COMPRESS -> Icons.Filled.Compress
    Tool.CONVERT -> Icons.Filled.Transform
}

// endregion

// region Ajustes

@Composable
fun SettingsScreen(vm: AppViewModel, showBack: Boolean) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(AppLanguage.chosen(context)) }
    ScrollScreen(header = { ScreenHeader(L("Ajustes"), showBack, { vm.back() }) }) {
        SectionTitle(L("Idioma do aplicativo"))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.tileRadius)).background(Prumo.colors.tile)) {
            val options = listOf<Pair<String?, String>>(null to L("Idioma do aparelho")) + I18n.languages
            for ((code, name) in options) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        chosen = code
                        AppLanguage.set(context, code)
                        (context as? android.app.Activity)?.recreate()
                    }.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == code, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(name, color = Prumo.colors.text, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
        SectionTitle(L("Biblioteca"))
        Text(
            L("Os PDFs ficam numa pasta do Prumo dentro deste aparelho. Nada é enviado para a internet."),
            color = Prumo.colors.secondaryText, fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))
        Text(L("Desenvolvido por Color Software"), color = Prumo.colors.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Prumo PDF ${BuildConfig.VERSION_NAME}", color = Prumo.colors.secondaryText, fontSize = 13.sp)
    }
}

// endregion

// region Escolher PDF da biblioteca

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentChooser(req: ChooserRequest, onDismiss: () -> Unit, onDone: (List<PdfItem>) -> Unit) {
    val actions = LocalActions.current
    val vm = actions.vm
    val snapshot by vm.state.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf<String>("all") }   // all, recent, favorites ou "folder:<caminho>"
    val selected = remember { mutableStateListOf<String>() }
    val lib = vm.library
    val pool = remember(snapshot.revision, filter) {
        when {
            filter == "recent" -> lib.items(DocList.RECENT)
            filter == "favorites" -> lib.items(DocList.FAVORITES)
            filter.startsWith("folder:") -> lib.itemsIn(filter.removePrefix("folder:"))
            else -> lib.items(DocList.ALL)
        }.filter { req.excludeFolder == null || it.folder != req.excludeFolder }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Prumo.colors.background) {
        Column(Modifier.fillMaxWidth().heightIn(min = 400.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.margin), verticalAlignment = Alignment.CenterVertically) {
                Text(req.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Prumo.colors.text, modifier = Modifier.weight(1f))
                if (req.multiple) {
                    TextButton(onClick = { onDone(pool.filter { it.id in selected }) }, enabled = selected.isNotEmpty()) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text(LN(selected.size, "Adicionar 1", "Adicionar {}"))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.margin, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filter == "all", { filter = "all" }, { Text(L("Todos os PDFs")) })
                FilterChip(filter == "recent", { filter = "recent" }, { Text(L("Recentes")) })
                FilterChip(filter == "favorites", { filter = "favorites" }, { Text(L("Favoritos")) })
                for (f in snapshot.folders) {
                    if (f.path == req.excludeFolder) continue
                    FilterChip(filter == "folder:${f.path}", { filter = "folder:${f.path}" }, { Text(f.path) })
                }
            }
            if (req.excludeFolder == null) {
                TextButton(onClick = {
                    onDismiss()
                    actions.importFromDevice("")
                }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.FileDownload, null)
                    Spacer(Modifier.width(6.dp))
                    Text(L("Do aparelho…"))
                }
            }
            if (pool.isEmpty()) {
                EmptyState(L("Nenhum PDF"))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(Dimens.margin),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacing),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacing),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp),
                ) {
                    items(pool, key = { it.id }) { item ->
                        DocTile(
                            item, vm.thumbnails, showFolder = true, showActions = false,
                            selection = if (req.multiple) item.id in selected else null,
                        ) {
                            if (req.multiple) {
                                if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                            } else {
                                onDone(listOf(item))
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// endregion
