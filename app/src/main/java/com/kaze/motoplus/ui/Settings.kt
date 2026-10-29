package com.kaze.motoplus.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.R
import com.kaze.motoplus.Screen

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val shop = LocalShop.current
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(stringResource(R.string.settings), { vm.screen = Screen.main })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Sheet {
                SettingChoice(stringResource(R.string.theme), vm.theme, listOf(
                    "system" to stringResource(R.string.system), "light" to stringResource(R.string.light), "dark" to stringResource(R.string.dark),
                ), vm::chooseTheme)
                SettingChoice(stringResource(R.string.language), AppCompatDelegate.getApplicationLocales().toLanguageTags(), listOf(
                    "" to stringResource(R.string.system), "es" to stringResource(R.string.locales_es),
                    "es-CO" to stringResource(R.string.locales_es_co), "en" to stringResource(R.string.locales_en),
                )) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) }
                SettingChoice(stringResource(R.string.currency_label), vm.garage.currency, listOf("COP", "USD", "EUR", "MXN").map { it to it }, vm::setCurrency)
            }
            Sheet {
                SettingsEntry("cloud", stringResource(R.string.data_menu)) { vm.screen = Screen.data }
                SettingsEntry("route", stringResource(R.string.sources)) { vm.screen = Screen.kmSources }
                SettingsEntry("history", stringResource(R.string.history_title)) { vm.screen = Screen.history }
                SettingsEntry("wrench", stringResource(R.string.part_limits)) { vm.screen = Screen.parts }
            }
        }
    }
}

@Composable
private fun SettingChoice(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Cap(label)
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(options.firstOrNull { it.first == value }?.second ?: value, modifier = Modifier.weight(1f))
                Icon("chev", LocalShop.current.ink2, 18.dp)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(id) }) }
            }
        }
    }
}

@Composable
fun SettingsEntry(icon: String, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, LocalShop.current.ink2)
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp))
        Icon("chev", LocalShop.current.ink2, 18.dp)
    }
}
