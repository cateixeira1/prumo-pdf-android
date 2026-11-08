package br.com.prumopdf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.prumopdf.core.FolderItem
import br.com.prumopdf.core.PdfItem
import br.com.prumopdf.i18n.L
import br.com.prumopdf.i18n.LN
import br.com.prumopdf.platform.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class IconShape { CIRCLE, ROUNDED }

@Composable
fun TileIcon(icon: ImageVector, color: Color, shape: IconShape = IconShape.CIRCLE, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(if (shape == IconShape.CIRCLE) CircleShape else RoundedCornerShape(size * 0.24f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Fundo do cartão: cinza; ao tocar, assume a cor do ícone e o texto fica branco (como no iPhone). */
@Composable
fun TileSurface(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (highlighted: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val on = pressed || selected
    Box(
        modifier.clip(RoundedCornerShape(Dimens.tileRadius))
            .background(if (on) color else Prumo.colors.tile)
            .combinedClickable(
                interactionSource = source, indication = null, role = Role.Button,
                onLongClick = onLongClick, onClick = onClick,
            ),
    ) { content(on) }
}

@Composable
fun Tile(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    count: String? = null,
    shape: IconShape = IconShape.CIRCLE,
    subtitle: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    TileSurface(color, onClick, modifier.heightIn(min = Dimens.tileHeight), onLongClick = onLongClick) { hl ->
        val text = if (hl) Color.White else Prumo.colors.text
        Column(Modifier.padding(12.dp).fillMaxWidth().heightIn(min = Dimens.tileHeight - 24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                TileIcon(icon, if (hl) Color.White.copy(alpha = 0.28f) else if (enabled) color else color.copy(alpha = 0.45f), shape)
                Spacer(Modifier.weight(1f))
                if (count != null) {
                    Text(count, color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
            Text(
                title, color = if (enabled || hl) text else Prumo.colors.secondaryText, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
            )
            if (subtitle != null) {
                Text(
                    subtitle, color = if (hl) Color.White.copy(alpha = 0.9f) else Prumo.colors.secondaryText,
                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Grade de cartões com colunas pelo espaço disponível: 2 no telefone, 3 a 6 em tablet e dobrável aberto.
 * Com [completeRows], usa só 2, 3, 4 ou 6 colunas para não sobrar buraco na última linha (como no Windows).
 */
@Composable
fun TileGrid(
    count: Int,
    modifier: Modifier = Modifier,
    minTileWidth: Dp = 156.dp,
    completeRows: Boolean = false,
    item: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = columnsFor(maxWidth, minTileWidth, count, completeRows)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacing)) {
            for (start in 0 until count step cols) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacing)) {
                    for (i in start until start + cols) {
                        if (i < count) item(i, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

fun columnsFor(width: Dp, minTileWidth: Dp, count: Int, completeRows: Boolean): Int {
    val fit = ((width + Dimens.spacing) / (minTileWidth + Dimens.spacing)).toInt().coerceIn(2, 6)
    if (!completeRows) return fit
    return listOf(6, 4, 3, 2).firstOrNull { it <= fit && count % it == 0 } ?: listOf(6, 4, 3, 2).first { it <= fit }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Prumo.colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Prumo.colors.search)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Prumo.colors.placeholder, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Prumo.colors.placeholder, fontSize = 17.sp, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Prumo.colors.text, fontSize = 17.sp),
                cursorBrush = SolidColor(Palette.blue),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, L("Limpar busca"), tint = Prumo.colors.placeholder,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
            )
        }
    }
}

@Composable
fun Badge(text: String, color: Color, inverted: Boolean = false) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (inverted) color else Color.White,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(if (inverted) Color.White else color)
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
    )
}

@Composable
fun PageThumbnail(item: PdfItem, thumbnails: Thumbnails, width: Dp, height: Dp) {
    val px = with(LocalDensity.current) { width.roundToPx() } * 2
    val bmp by produceState(thumbnails.cached(item.id, item.version, px), item.id, item.version) {
        if (value == null) value = withContext(Dispatchers.IO) { thumbnails.load(item.id, item.version, item.file, px) }
    }
    Box(
        Modifier.size(width, height).shadow(1.5.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))
            .background(Color.White).border(0.5.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        } else if (item.isLocked) {
            Icon(Icons.Filled.Lock, null, tint = Palette.purple, modifier = Modifier.size(16.dp))
        }
    }
}

/** Uma ação do menu ⋮ de um PDF. */
data class MenuAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun DocTile(
    item: PdfItem,
    thumbnails: Thumbnails,
    modifier: Modifier = Modifier,
    needsOCR: Boolean = false,
    showFolder: Boolean = false,
    selection: Boolean? = null,
    showActions: Boolean = true,
    onFavorite: () -> Unit = {},
    menu: () -> List<MenuAction> = { emptyList() },
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val actions = showActions && selection == null
    Box(modifier) {
        TileSurface(Palette.blue, onClick, selected = selection == true, onLongClick = if (actions) ({ menuOpen = true }) else null) { hl ->
            val text = if (hl) Color.White else Prumo.colors.text
            Column(Modifier.padding(12.dp).fillMaxWidth().heightIn(min = 118.dp - 24.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    PageThumbnail(item, thumbnails, 34.dp, 44.dp)
                    Spacer(Modifier.weight(1f))
                    when {
                        selection != null -> Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(if (selection) Palette.blue else Color.Transparent)
                                .border(1.5.dp, if (selection) Palette.blue else Prumo.colors.secondaryText, CircleShape),
                        )
                        item.isLocked -> Icon(Icons.Filled.Lock, L("Com senha"), tint = if (hl) Color.White else Palette.purple)
                        item.pageCount != null -> Column(horizontalAlignment = Alignment.End) {
                            Text("${item.pageCount}", color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                            Text(
                                if (item.pageCount == 1) L("página") else L("páginas"),
                                color = if (hl) Color.White.copy(alpha = 0.9f) else Prumo.colors.secondaryText,
                                fontSize = 10.5.sp, fontWeight = FontWeight.Medium, lineHeight = 12.sp,
                            )
                        }
                        else -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.heightIn(min = 8.dp).weight(1f))
                Text(item.name, color = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Row(
                    Modifier.padding(top = 4.dp, end = if (actions) 76.dp else 0.dp).heightIn(min = if (actions) 26.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isLocked) Badge(L("Com senha"), Palette.purple, hl)
                    if (item.meta.isOCRResult) Badge("OCR", Palette.green, hl)
                    if (needsOCR) Badge(L("Sem OCR"), Palette.orange, hl)
                    if (showFolder) {
                        Text(
                            if (item.folder.isEmpty()) L("Sem pasta") else item.folder.substringAfterLast('/'),
                            fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (hl) Color.White.copy(alpha = 0.9f) else Prumo.colors.secondaryText,
                        )
                    }
                }
            }
        }
        if (actions) {
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 2.dp)) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (item.meta.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (item.meta.favorite) L("Remover dos favoritos") else L("Favoritar"),
                        tint = if (item.meta.favorite) Palette.yellow else Prumo.colors.secondaryText,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.MoreVert, L("Mais opções"), tint = Prumo.colors.secondaryText)
                    }
                    ActionMenu(menuOpen, { menuOpen = false }, if (menuOpen) menu() else emptyList())
                }
            }
        }
    }
}

@Composable
fun ActionMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (a in actions) {
            val tint = if (a.destructive) Palette.red else Prumo.colors.text
            DropdownMenuItem(
                text = { Text(a.label, color = tint) },
                leadingIcon = { Icon(a.icon, null, tint = tint) },
                onClick = {
                    onDismiss()
                    a.onClick()
                },
            )
        }
    }
}

@Composable
fun FolderTile(folder: FolderItem, modifier: Modifier = Modifier, menu: () -> List<MenuAction> = { emptyList() }, onClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Tile(
            title = folder.name, icon = Icons.Filled.Folder, color = Palette.indigo, count = "${folder.totalDocCount}",
            shape = IconShape.ROUNDED,
            subtitle = if (folder.subfolderCount > 0) LN(folder.subfolderCount, "1 subpasta", "{} subpastas") else null,
            onLongClick = { menuOpen = true }, onClick = onClick,
        )
        Box(Modifier.align(Alignment.BottomEnd)) {
            ActionMenu(menuOpen, { menuOpen = false }, if (menuOpen) menu() else emptyList())
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Prumo.colors.secondaryText, fontSize = 15.sp)
    }
}
