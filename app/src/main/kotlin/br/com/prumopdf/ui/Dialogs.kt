package br.com.prumopdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.prumopdf.core.FolderItem
import br.com.prumopdf.i18n.L

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    message: String? = null,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val ok = value.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) Text(message, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    label = { Text(L("Nome")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onConfirm(value.text.trim()) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text.trim()) }, enabled = ok) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    confirm: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) Palette.red else Palette.blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}

@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("Fechar")) } },
    )
}

/** Escolher uma pasta da biblioteca ("Sem pasta" = raiz). */
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FolderItem>,
    current: String?,
    onDismiss: () -> Unit,
    onNewFolder: (() -> Unit)? = null,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { FolderRow(L("Sem pasta"), 0, enabled = current != "", icon = true) { onPick("") } }
                items(folders, key = { it.path }) { f ->
                    FolderRow(f.name, f.depth + 1, enabled = current != f.path) { onPick(f.path) }
                }
            }
        },
        confirmButton = {
            if (onNewFolder != null) TextButton(onClick = onNewFolder) { Text(L("Nova pasta")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}

@Composable
private fun FolderRow(name: String, depth: Int, enabled: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(start = (8 + depth * 16).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (icon) Icons.Filled.FolderOpen else Icons.Filled.Folder, null,
            tint = if (enabled) Palette.indigo else Prumo.colors.placeholder, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
            color = if (enabled) Prumo.colors.text else Prumo.colors.placeholder,
        )
    }
}

/** Uma escolha entre opções curtas (ex.: "Mover" ou "Copiar"). */
@Composable
fun ChoiceDialog(
    title: String,
    message: String?,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message != null) Text(message)
                for ((label, action) in options) {
                    Text(
                        label, color = Palette.blue, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Prumo.colors.tile.copy(alpha = 0.5f)).clickable { action() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancelar")) } },
    )
}
