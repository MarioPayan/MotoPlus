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
import com.kaze.motoplus.domain.Feature
import com.kaze.motoplus.domain.watchedParts

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val shop = LocalShop.current
    var about by remember { mutableStateOf(false) }
    var paperLimits by remember { mutableStateOf(false) }
    if (about) { AboutScreen(vm) { about = false }; return }
    if (paperLimits) {
        WorkshopPage(stringResource(R.string.p3_paper_limits), { paperLimits = false }) {
            if (!vm.prefs.reminders) Hint(stringResource(R.string.reminders_off), LocalShop.current.warn)
            PaperLimits(vm)
        }
        return
    }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding()) {
        Header(stringResource(R.string.settings), { vm.screen = Screen.main })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            vm.bike?.let { b ->
                Sheet {
                    SettingsEntry("bike", stringResource(R.string.my_bike), b.title + " · " + stringResource(R.string.watch_summary, b.watchedParts.size, b.parts.size)) { vm.openSetup(Screen.settings) }
                    SettingsEntry("sliders", stringResource(R.string.customize), hiddenText(vm.prefs)) { vm.screen = Screen.customize }
                }
            }
            Sheet {
                SettingChoice(stringResource(R.string.theme), vm.theme, themeOptions(), vm::chooseTheme)
                LanguageChoice()
                SettingChoice(stringResource(R.string.currency_label), vm.garage.currency, listOf("COP", "USD", "EUR", "MXN").map { it to it }, vm::setCurrency)
            }
            Sheet {
                Toggle(stringResource(R.string.ai_features), stringResource(R.string.ai_features_hint), vm.aiEnabled,
                    enabled = vm.aiAvailable, tag = if (vm.aiAvailable) null else stringResource(R.string.coming_soon), onChange = vm::setAi)
            }
            Sheet {
                SettingsEntry("cloud", stringResource(R.string.data_menu)) { vm.screen = Screen.data }
                if (vm.prefs.on(Feature.kmAuto)) SettingsEntry("route", stringResource(R.string.sources)) { vm.screen = Screen.kmSources }
                SettingsEntry("history", stringResource(R.string.history_title)) { vm.screen = Screen.history }
                if (vm.prefs.on(Feature.papers)) SettingsEntry("doc", stringResource(R.string.p3_paper_limits), if (vm.prefs.reminders) null else stringResource(R.string.reminders_off_short)) { paperLimits = !paperLimits }
                SettingsEntry("info", stringResource(R.string.p3_about)) { about = true }
            }
        }
    }
}

@Composable
fun themeOptions() = listOf("system" to stringResource(R.string.system), "light" to stringResource(R.string.light), "dark" to stringResource(R.string.dark))

@Composable
fun languageOptions() = listOf(
    "" to stringResource(R.string.system), "es" to stringResource(R.string.locales_es),
    "es-CO" to stringResource(R.string.locales_es_co), "en" to stringResource(R.string.locales_en),
)

@Composable
fun LanguageChoice() = SettingChoice(stringResource(R.string.language), AppCompatDelegate.getApplicationLocales().toLanguageTags(), languageOptions()) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
}

@Composable
internal fun SettingChoice(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
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
fun SettingsEntry(icon: String, label: String, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = rowPad), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, LocalShop.current.ink2)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label)
            sub?.let { Hint(it) }
        }
        Icon("chev", LocalShop.current.ink2, 18.dp)
    }
}


@Composable
private fun AboutScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var license by remember { mutableStateOf<String?>(null) }
    WorkshopPage(stringResource(R.string.p3_about), onBack) {
        SectionTitle(stringResource(R.string.p3_catalog_sources))
        vm.catalog?.sources?.values?.forEach { source -> Sheet { Text(source, style = MaterialTheme.typography.bodyLarge) } }
        SectionTitle(stringResource(R.string.p3_licenses))
        listOf(
            "OFL-barlow-condensed.txt" to R.string.p3_license_barlow,
            "OFL-plex.txt" to R.string.p3_license_plex,
            "Phosphor-MIT.txt" to R.string.p3_license_phosphor,
        ).forEach { (file, label) ->
            SettingsEntry("doc", stringResource(label)) { license = file }
        }
    }
    license?.let { file ->
        val text = remember(file) { ctx.assets.open("licenses/$file").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { license = null }, title = { Text(stringResource(R.string.p3_licenses)) },
            text = { Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton({ license = null }) { Text(stringResource(R.string.close)) } })
    }
}
