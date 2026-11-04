package com.kaze.motoplus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*

/** The form stores raw digits; the rider edits a grouped, locale-aware odometer. */
@Composable
internal fun OdometerField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    IntegerField(value, onChange, stringResource(R.string.odo_km), modifier)
}

@Composable
internal fun IntegerField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, maxDigits: Int = 7, grouping: Boolean = true) {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = formatIntegerInput(value, locale, maxDigits, grouping)
    var field by remember { mutableStateOf(TextFieldValue(formatted, TextRange(formatted.length))) }
    LaunchedEffect(formatted) {
        if (field.text != formatted) field = TextFieldValue(formatted, TextRange(formatted.length))
    }
    AppInput(field, { next ->
        val display = formatIntegerInput(next.text, locale, maxDigits, grouping)
        field = TextFieldValue(display, TextRange(groupedInputCursor(next.text, next.selection.end, display)))
        onChange(display.filter(Char::isDigit))
    }, modifier, label = { Text(label) }, singleLine = true,
        textStyle = Mono.copy(fontSize = 18.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
internal fun DecimalField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, maxFractionDigits: Int = 1) {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = formatDecimalInput(value, locale, maxFractionDigits)
    var field by remember { mutableStateOf(TextFieldValue(formatted, TextRange(formatted.length))) }
    LaunchedEffect(formatted) {
        if (field.text != formatted) field = TextFieldValue(formatted, TextRange(formatted.length))
    }
    AppInput(field, { next ->
        val canonical = decimalInput(next.text, maxFractionDigits)
        val display = formatDecimalInput(canonical, locale, maxFractionDigits)
        val significant = next.text.take(next.selection.end).count { it.isDigit() || it == ',' || it == '.' }
        field = TextFieldValue(display, TextRange(significant.coerceAtMost(display.length)))
        onChange(canonical)
    }, modifier, label = { Text(label) }, singleLine = true,
        textStyle = Mono.copy(fontSize = 18.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

/** One flat input with its label above the field, shared by every form. */
@Composable
fun AppInput(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null, singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: @Composable (() -> Unit)? = null, textStyle: TextStyle = inputStyle(keyboardOptions),
    minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true, readOnly: Boolean = false, trailingIcon: @Composable (() -> Unit)? = null,
) {
    InputFrame(modifier, label) {
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), singleLine = singleLine,
            keyboardOptions = keyboardOptions, placeholder = placeholder, textStyle = textStyle,
            minLines = minLines, maxLines = maxLines, enabled = enabled, readOnly = readOnly,
            trailingIcon = trailingIcon, shape = RoundedCornerShape(3.dp), colors = inputColors())
    }
}

@Composable
fun AppInput(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null, singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = inputStyle(keyboardOptions), enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
) {
    InputFrame(modifier, label) {
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), singleLine = singleLine,
            keyboardOptions = keyboardOptions, textStyle = textStyle, enabled = enabled, placeholder = placeholder,
            shape = RoundedCornerShape(3.dp), colors = inputColors())
    }
}

@Composable
private fun InputFrame(modifier: Modifier, label: @Composable (() -> Unit)?, field: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label != null) ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, color = LocalShop.current.ink2)) { label() }
        field()
    }
}

@Composable
private fun inputStyle(options: KeyboardOptions): TextStyle =
    if (options.keyboardType in listOf(KeyboardType.Number, KeyboardType.Decimal)) MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono.fontFamily) else MaterialTheme.typography.bodyLarge

@Composable
private fun inputColors() = LocalShop.current.let { s -> OutlinedTextFieldDefaults.colors(
    focusedContainerColor = s.sheet, unfocusedContainerColor = s.sheet, disabledContainerColor = s.sheet,
    focusedBorderColor = s.accent, unfocusedBorderColor = s.line,
    focusedTextColor = s.ink, unfocusedTextColor = s.ink, cursorColor = s.accent,
    focusedPlaceholderColor = s.ink2, unfocusedPlaceholderColor = s.ink2,
) }
