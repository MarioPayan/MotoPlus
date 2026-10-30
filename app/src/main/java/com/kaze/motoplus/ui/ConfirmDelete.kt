package com.kaze.motoplus.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kaze.motoplus.R

/** All persisted user-data deletions share this explicit confirmation. */
@Composable
internal fun ConfirmDelete(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_question, name)) },
        text = { Text(stringResource(R.string.delete_warning)) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.delete), color = LocalShop.current.bad) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}
