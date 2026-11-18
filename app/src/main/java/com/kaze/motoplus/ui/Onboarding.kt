package com.kaze.motoplus.ui

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.BikeDraft
import com.kaze.motoplus.R
import com.kaze.motoplus.Screen
import com.kaze.motoplus.WELCOME_STEPS
import com.kaze.motoplus.domain.*
import java.time.LocalDate

private val STEP_TITLES = listOf(R.string.welcome_bike, R.string.welcome_bike, R.string.setup_watch, R.string.routines_title, R.string.interface_title, R.string.summary)

/** First-run intro: six short steps with Saltar always at hand; going back is possible on every step. */
@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val shop = LocalShop.current
    val w = vm.welcome ?: return
    val last = WELCOME_STEPS - 1
    BackHandler(w.step > 0) { vm.welcomeBack() }
    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (w.step > 0) IconBtn("back", stringResource(R.string.atras), vm::welcomeBack, shop.ink)
                else if (w.replay) IconBtn("back", stringResource(R.string.back), { vm.welcome = null; vm.screen = Screen.customize }, shop.ink)
            }
            if (w.step < last) TextButton({ vm.finishWelcome(skipped = true) }) { Text(stringResource(R.string.welcome_skip), maxLines = 1, softWrap = false) }
        }
        val shift = with(LocalDensity.current) { 32.dp.roundToPx() }
        AnimatedContent(w.step, Modifier.weight(1f), transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (fadeIn(motion()) + slideInHorizontally(motion()) { dir * shift }) togetherWith fadeOut(motion())
        }, label = "welcome") { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    Cap(stringResource(R.string.step_of, step + 1, WELCOME_STEPS))
                    Text(stringResource(STEP_TITLES[step]).uppercase(), style = Display.copy(fontSize = 34.sp))
                }
                when (step) {
                    0 -> WelcomeHello(vm)
                    1 -> WelcomeBike(vm)
                    2 -> vm.bike?.let { WelcomeWatch(vm, it) }
                    3 -> RoutineOptions(vm)
                    4 -> InterfaceOptions(vm)
                    else -> vm.bike?.let { WelcomeSummary(vm, it) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val label = stringResource(R.string.step_of, w.step + 1, WELCOME_STEPS)
            Row(Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
                repeat(WELCOME_STEPS) { i ->
                    val width by animateDpAsState(if (i == w.step) 22.dp else 8.dp, motion(), label = "dot")
                    val color by animateColorAsState(when { i == w.step -> shop.accent; i < w.step -> shop.ink2; else -> shop.line }, motion(), label = "dotColor")
                    Box(Modifier.size(width, 8.dp).background(color, RoundedCornerShape(4.dp)))
                }
            }
            val ready = w.step != 1 || (w.km.isNotEmpty() && (vm.bike != null || (vm.welcomeDraft.brand.isNotBlank() && vm.welcomeDraft.model.isNotBlank())))
            if (w.step == last) Btn(stringResource(R.string.welcome_start), { vm.finishWelcome(skipped = false) }, Modifier.fillMaxWidth(), icon = "check")
            else Btn(stringResource(R.string.welcome_next), vm::welcomeNext, Modifier.fillMaxWidth(), enabled = ready)
        }
    }
}

@Composable
private fun WelcomeHello(vm: AppViewModel) {
    val shop = LocalShop.current
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon("bike", shop.accent, 56.dp)
        Text(stringResource(R.string.app_name).uppercase(), style = Display.copy(fontSize = 52.sp, lineHeight = 52.sp))
    }
    Text(stringResource(R.string.welcome_line), fontSize = 23.sp, lineHeight = 30.sp)
    Sheet {
        LanguageChoice()
        SettingChoice(stringResource(R.string.currency_label), vm.garage.currency, listOf("COP", "USD", "EUR", "MXN").map { it to it }, vm::setCurrency)
    }
    Hint(stringResource(R.string.welcome_later))
}

@Composable
private fun WelcomeBike(vm: AppViewModel) {
    val w = vm.welcome ?: return
    val bike = vm.bike
    val d = vm.welcomeDraft
    Hint(stringResource(R.string.welcome_bike_hint))
    Sheet {
        if (bike != null) {
            ModelPicker(vm.catalog, bike.brand, bike.model, onPick = { h -> vm.setModel(h.brand, h.model.n, catalogYear(h), h.model.c) },
                onManual = { b, m -> if (b.isNotBlank() && m.isNotBlank()) vm.setModel(b, m, null, null) })
            IntegerField(bike.year?.toString().orEmpty(), { vm.setYear(it.toIntOrNull()) }, stringResource(R.string.year), Modifier.fillMaxWidth(), maxDigits = 4, grouping = false)
            Collapse(vm.setup.notice != null) { Hint(vm.setup.notice.orEmpty(), LocalShop.current.warn) }
        } else {
            ModelPicker(vm.catalog, d.brand, d.model, onPick = { h -> vm.welcomeDraft = BikeDraft(h.brand, h.model.n, catalogYear(h), h.model.c) },
                onManual = { b, m -> vm.welcomeDraft = d.copy(brand = b, model = m) })
            IntegerField(d.year?.toString().orEmpty(), { vm.welcomeDraft = vm.welcomeDraft.copy(year = it.toIntOrNull()) }, stringResource(R.string.year), Modifier.fillMaxWidth(), maxDigits = 4, grouping = false)
        }
    }
    IntegerField(w.km, { vm.welcome = w.copy(km = it, kmError = false) }, stringResource(R.string.current_km), Modifier.fillMaxWidth())
    if (w.kmError) Hint(stringResource(R.string.welcome_km_bad), LocalShop.current.bad)
}

@Composable
private fun WelcomeWatch(vm: AppViewModel, bike: Bike) {
    val w = vm.welcome ?: return
    val odo = estimate(bike, vm.today).km
    Hint(stringResource(R.string.setup_watch_hint))
    ChoiceRow(listOf(WatchPreset.essential.name to stringResource(R.string.preset_essential), WatchPreset.all.name to stringResource(R.string.preset_all),
        WatchPreset.none.name to stringResource(R.string.preset_none)), bike.preset?.name.orEmpty()) { vm.watchPreset(WatchPreset.valueOf(it)) }
    Column {
        bike.parts.byCategory().forEach { (c, parts) ->
            val open = c in w.open
            WatchCategory(c, parts, open, { vm.welcome = w.copy(open = if (open) w.open - c else w.open + c) }) { vm.watchCategory(c, it) }
            Collapse(open) { parts.forEach { p -> key(p.id) { WatchRow(p, partState(p, odo, vm.today, vm.prefs.soon)) { vm.watch(p.id, it) } } } }
        }
    }
    Text(stringResource(R.string.watch_summary, bike.watchedParts.size, bike.parts.size), style = Mono.copy(fontSize = 12.sp), color = LocalShop.current.ink2)
}

@Composable
private fun WelcomeSummary(vm: AppViewModel, bike: Bike) {
    val p = vm.prefs
    @Composable fun yn(on: Boolean) = stringResource(if (on) R.string.yes_label else R.string.no_label)
    Hint(stringResource(R.string.summary_hint))
    Sheet(padding = 4.dp) {
        val est = estimate(bike, vm.today)
        SummaryLine("bike", bike.title, stringResource(if (est.isProjected) R.string.approx_km_value else R.string.p2_km_value, distance(est.km.toInt())))
        SummaryLine("gauge", stringResource(R.string.setup_watch), stringResource(R.string.watch_summary, bike.watchedParts.size, bike.parts.size))
        FEATURE_ROWS.forEach { (f, title, _, icon) -> SummaryLine(icon, stringResource(title), yn(p.on(f)), p.on(f)) }
        SummaryLine("bulb", stringResource(R.string.ai_features), stringResource(R.string.coming_soon), false)
        SummaryLine("bike", stringResource(R.string.ui_drawing), yn(p.drawing), p.drawing)
        SummaryLine("list-checks", stringResource(R.string.ui_density), stringResource(if (p.density == Density.compact) R.string.density_compact else R.string.density_comfortable))
        SummaryLine("clock", stringResource(R.string.ui_reminders), yn(p.reminders), p.reminders)
        SummaryLine("settings", stringResource(R.string.theme), themeOptions().firstOrNull { it.first == vm.theme }?.second.orEmpty())
        SummaryLine("info", stringResource(R.string.language), languageOptions().firstOrNull { it.first == AppCompatDelegate.getApplicationLocales().toLanguageTags() }?.second.orEmpty())
    }
}

@Composable
private fun SummaryLine(icon: String, label: String, value: String, on: Boolean = true) {
    val shop = LocalShop.current
    Column(Modifier.alpha(if (on) 1f else .5f)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, shop.ink2, 20.dp)
            Text(label, Modifier.weight(1f).padding(horizontal = 12.dp))
            Text(value, style = Mono.copy(fontSize = 12.sp), color = shop.ink2)
        }
        HorizontalDivider(color = shop.line)
    }
}

private data class FeatureRow(val feature: Feature, val title: Int, val hint: Int, val icon: String)

private val FEATURE_ROWS = listOf(
    FeatureRow(Feature.checks, R.string.before_ride, R.string.feat_checks_hint, "list-checks"),
    FeatureRow(Feature.fuel, R.string.feat_fuel, R.string.feat_fuel_hint, "fuel"),
    FeatureRow(Feature.papers, R.string.feat_papers, R.string.feat_papers_hint, "doc"),
    FeatureRow(Feature.workshop, R.string.feat_workshop, R.string.feat_workshop_hint, "wrench"),
    FeatureRow(Feature.kmAuto, R.string.feat_km_auto, R.string.feat_km_auto_hint, "route"),
)

private val CHECK_LABELS = mapOf("tires" to R.string.check_tires, "lights" to R.string.check_lights, "chain" to R.string.check_chain,
    "brakes" to R.string.check_brakes, "oil" to R.string.check_oil, "papers" to R.string.check_papers)

/** Tus rutinas, shared by the intro and Personalizar. Antes de salir expands into the steps to check; AI is shown but Próximamente. */
@Composable
fun RoutineOptions(vm: AppViewModel) {
    val p = vm.prefs
    val bike = vm.bike
    Hint(stringResource(R.string.routines_hint))
    Sheet {
        FEATURE_ROWS.forEachIndexed { i, (f, title, hint) ->
            if (i > 0) HorizontalDivider(color = LocalShop.current.line)
            Toggle(stringResource(title), stringResource(hint), p.on(f)) { on -> vm.updatePrefs { it.copy(features = if (on) it.features + f else it.features - f) } }
            if (f == Feature.checks) Collapse(p.on(Feature.checks)) {
                Column(Modifier.padding(start = 12.dp, top = 6.dp)) {
                    Cap(stringResource(R.string.checks_pick))
                    CHECK_STEPS.forEach { id ->
                        val shown = (id != "papers" || p.on(Feature.papers)) && (id != "chain" || bike?.drive in setOf(Drive.chain, Drive.belt))
                        Collapse(shown) {
                            val label = if (id == "chain" && bike?.drive == Drive.belt) driveLabel(Drive.belt) else stringResource(CHECK_LABELS.getValue(id))
                            Row(Modifier.fillMaxWidth().toggleable(id in p.checks, role = Role.Checkbox) { on -> vm.updatePrefs { it.copy(checks = if (on) it.checks + id else it.checks - id) } },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(id in p.checks, null)
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = LocalShop.current.line)
        Toggle(stringResource(R.string.ai_features), stringResource(R.string.feat_ai_hint), false, enabled = false, tag = stringResource(R.string.coming_soon)) {}
    }
}

/** Interfaz, shared by the intro and Personalizar. */
@Composable
fun InterfaceOptions(vm: AppViewModel) {
    val p = vm.prefs
    Sheet {
        SettingChoice(stringResource(R.string.theme), vm.theme, themeOptions(), vm::chooseTheme)
        HorizontalDivider(color = LocalShop.current.line)
        Toggle(stringResource(R.string.ui_drawing), stringResource(R.string.ui_drawing_hint), p.drawing) { on -> vm.updatePrefs { it.copy(drawing = on) } }
        HorizontalDivider(color = LocalShop.current.line)
        Cap(stringResource(R.string.ui_density))
        ChoiceRow(listOf(Density.comfortable.name to stringResource(R.string.density_comfortable), Density.compact.name to stringResource(R.string.density_compact)), p.density.name) { d ->
            vm.updatePrefs { it.copy(density = Density.valueOf(d)) }
        }
        vm.bike?.let { DensityPreview(it, vm.today, vm.prefs.soon) }
        HorizontalDivider(color = LocalShop.current.line)
        Toggle(stringResource(R.string.ui_reminders), stringResource(R.string.ui_reminders_hint), p.reminders) { on -> vm.updatePrefs { it.copy(reminders = on) } }
    }
}

/** Nothing else on the intro's Interfaz step is a part list, so a few real rows show the choice where it is made. */
@Composable
private fun DensityPreview(bike: Bike, today: LocalDate, soon: SoonThreshold) {
    val shop = LocalShop.current
    val odo = estimate(bike, today).km
    Column {
        Cap(stringResource(R.string.density_preview), modifier = Modifier.padding(bottom = 6.dp))
        bike.watchedParts.ifEmpty { bike.parts }.take(3).forEach { p ->
            val s = partState(p, odo, today, soon)
            HorizontalDivider(color = shop.line)
            Row(Modifier.fillMaxWidth().padding(vertical = rowPad), verticalAlignment = Alignment.CenterVertically) {
                Icon(partIcon(p), shop.status(s.status), 24.dp)
                Column(Modifier.padding(start = 20.dp)) {
                    Text(partLabel(p), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(remaining(s), style = Mono.copy(fontSize = 11.sp), color = shop.status(s.status))
                }
            }
        }
    }
}

@Composable
fun hiddenText(p: Preferences): String = if (p.hidden == 0) stringResource(R.string.all_visible) else pluralStringResource(R.plurals.hidden_features, p.hidden, p.hidden)

/** The quiet line at the bottom of the Moto tab: what is hidden and the way back. AI does not count. */
@Composable
fun HiddenLine(vm: AppViewModel) {
    if (vm.prefs.hidden == 0) return
    val shop = LocalShop.current
    Row(Modifier.fillMaxWidth().clickable { vm.screen = Screen.customize }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon("eye-slash", shop.ink2, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.hidden_line, hiddenText(vm.prefs), stringResource(R.string.customize_short)), style = Mono.copy(fontSize = 12.sp), color = shop.ink2)
    }
}

/** Personalizar MotoPlus: the intro's choices as a settings page, plus Volver a mostrar todo, Modo mínimo and the intro again. */
@Composable
fun CustomizeScreen(vm: AppViewModel, bike: Bike) {
    WorkshopPage(stringResource(R.string.customize), { vm.screen = Screen.settings }) {
        Hint(stringResource(R.string.customize_hint))
        Sheet { SettingsEntry("gauge", stringResource(R.string.setup_watch), bike.title + " · " + stringResource(R.string.watch_summary, bike.watchedParts.size, bike.parts.size)) { vm.openSetup(Screen.customize) } }
        SectionTitle(stringResource(R.string.routines_title))
        RoutineOptions(vm)
        SectionTitle(stringResource(R.string.interface_title))
        InterfaceOptions(vm)
        Sheet {
            Btn(stringResource(R.string.show_all), vm::showAll, Modifier.fillMaxWidth())
            Btn(stringResource(R.string.minimal_mode), vm::minimalMode, Modifier.fillMaxWidth(), ghost = true)
            Hint(stringResource(R.string.minimal_hint))
            TextButton(vm::replayIntro, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.replay_intro)) }
        }
    }
}
