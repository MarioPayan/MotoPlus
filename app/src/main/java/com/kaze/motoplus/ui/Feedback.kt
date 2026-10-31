package com.kaze.motoplus.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.R
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ponytail: tester notes for Mario only, a plain text file read with
// `adb pull /sdcard/Android/data/com.kaze.motoplus/files/notas.txt`. Remove before a public release.
internal fun feedbackFile(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "notas.txt")

/** Floating note button on every screen: saves what the rider writes with the time and the screen it was written on. */
@Composable
internal fun FeedbackButton(where: String, modifier: Modifier = Modifier) {
    val shop = LocalShop.current
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Box(modifier.background(shop.sheet2.copy(alpha = .85f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
        .border(1.dp, shop.line, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))) {
        IconBtn("note-pencil", stringResource(R.string.feedback_title), { open = true }, color = shop.accent)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, containerColor = shop.sheet,
        title = { Text(stringResource(R.string.feedback_title)) },
        text = { AppInput(text, { text = it }, minLines = 4, placeholder = { Text(stringResource(R.string.feedback_hint)) }) },
        confirmButton = {
            TextButton({
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                feedbackFile(context).appendText("[$stamp · $where]\n${text.trim()}\n\n")
                text = ""; open = false
            }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton({ open = false }) { Text(stringResource(R.string.cancel)) } })
}
