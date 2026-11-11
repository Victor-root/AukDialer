package auk.dialer.vroot.view.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R

/** The header shown while rows are selected: how many, a way out, and [actions] on them. */
@Composable
fun AukSelectionActionBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    AukAccentHeader {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_clear_selection))
                }
                Text(
                    text = pluralStringResource(R.plurals.selection_count_selected, selectedCount, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                actions()
            }
        }
    }
}

/** A selection header whose one action deletes the selected rows, once confirmed. */
@Composable
fun SelectionDeleteActionBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    confirmTitle: String,
    confirmMessage: String,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    AukSelectionActionBar(selectedCount, onClearSelection) {
        IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(Icons.Default.Delete, stringResource(R.string.content_desc_delete_selected))
        }
    }
    if (showDeleteConfirm) {
        AukConfirmationDialog(
            onDismissRequest = { showDeleteConfirm = false },
            onConfirm = onDelete,
            title = confirmTitle,
            message = confirmMessage,
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            icon = Icons.Default.Delete,
            isDestructive = true
        )
    }
}
