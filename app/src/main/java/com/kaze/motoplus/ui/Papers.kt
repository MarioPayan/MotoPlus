package com.kaze.motoplus.ui


import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.stringResource
import com.kaze.motoplus.R
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kaze.motoplus.*
import com.kaze.motoplus.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------- 4A: the papers tab ----------

@Composable
fun PapersScreen(vm: AppViewModel, bike: Bike) {
    val shop = LocalShop.current
    var adding by remember { mutableStateOf(false) }
    var limits by remember { mutableStateOf(false) }
    var selected by remember(bike.id) { mutableStateOf<String?>(null) }
    val docs = vm.papers
    val d = docs.firstOrNull { it.id == selected } ?: docs.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DocumentTabs(docs, d?.id) { selected = it.id }
        d?.let { doc ->
            Sheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionTitle(docLabel(doc)); Hint(expiryText(docState(doc, vm.today, vm.garage.paperReminderDays[doc.type] ?: 30), doc)) }
                    DaysRing(docState(doc, vm.today, vm.garage.paperReminderDays[doc.type] ?: 30))
                }
                if (doc.photos.isEmpty() && officialUri(doc) != null) OfficialCard(doc) { vm.editDoc(doc) }
                else Box(Modifier.fillMaxWidth().aspectRatio(1.58f).background(shop.sheet).clickable { vm.editDoc(doc) }, contentAlignment = Alignment.Center) {
                    doc.photos.firstOrNull()?.let { Photo(it, Modifier.fillMaxSize()) } ?: Icon("camera", shop.ink2, 30.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (doc.photos.isNotEmpty()) Btn(stringResource(R.string.p3_control), { vm.showingDoc = doc.id; vm.screen = Screen.control }, Modifier.weight(1f), icon = "doc")
                    Btn(stringResource(R.string.p3_details), { vm.editDoc(doc) }, Modifier.weight(1f), ghost = true, icon = "edit")
                }
                if (doc.photos.isNotEmpty()) SourceRow(doc)
            }
        }
        Box {
            TextButton({ adding = true }) { Text(stringResource(R.string.p3_add_paper)) }
            DropdownMenu(adding, { adding = false }, containerColor = shop.sheet) {
                DocType.entries.forEach { t -> MenuItem(t.icon, docTypeLabel(t)) { adding = false; vm.newDoc(t) } }
            }
        }
        if (docs.isEmpty()) Hint(stringResource(R.string.p3_no_photo))
        TextButton({ limits = !limits }) { Text(stringResource(R.string.p3_paper_limits)) }
        if (limits) PaperLimits(vm)
        Hint(stringResource(R.string.p3_document_info))
        Spacer(Modifier.height(12.dp))
    }
}

/** Only explicit web URLs can open outside the app. */
private fun officialUri(d: Document): Uri? = runCatching { Uri.parse(d.officialUrl.trim()) }.getOrNull()
    ?.takeIf { it.scheme in listOf("https", "http") && !it.host.isNullOrBlank() && it.userInfo == null }

@Composable
private fun SourceRow(d: Document) {
    val ctx = LocalContext.current
    officialUri(d)?.let { uri ->
        Btn(stringResource(R.string.p3_open_site), { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }, Modifier.fillMaxWidth(), ghost = true, icon = "external")
    }
}

@Composable
private fun OfficialCard(d: Document, addPhoto: () -> Unit) {
    val ctx = LocalContext.current
    val uri = officialUri(d) ?: return
    Row(Modifier.fillMaxWidth().border(1.dp, LocalShop.current.line, RoundedCornerShape(3.dp)).clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon("link", LocalShop.current.ink2, 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.officialSite.ifBlank { uri.host.orEmpty() })
            Hint(stringResource(R.string.p3_open_site))
        }
        Icon("external", LocalShop.current.ink2, 18.dp)
    }
    TextButton(addPhoto) { Text(stringResource(R.string.p3_add_photo)) }
}

@Composable
private fun DocumentTabs(docs: List<Document>, selected: String?, onPick: (Document) -> Unit) {
    val shop = LocalShop.current
    Row(Modifier.fillMaxWidth().selectableGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        docs.forEach { doc ->
            val on = doc.id == selected
            val name = docLabel(doc)
            val color = if (on) shop.accent else shop.ink2
            Column(Modifier.widthIn(min = 76.dp).clip(RoundedCornerShape(3.dp))
                .background(if (on) shop.accent.copy(alpha = .1f) else shop.sheet)
                .drawBehind { drawLine(if (on) shop.accent else shop.line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
                .selectable(selected = on, role = Role.Tab, onClick = { onPick(doc) }).semantics { contentDescription = name }
                .padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(doc.type.icon, color)
                Text(docTabLabel(doc.type).uppercase(), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = color)
            }
        }
    }
}

@Composable
fun DocCard(d: Document, today: LocalDate, onClick: () -> Unit) {
    val shop = LocalShop.current
    val st = docState(d, today)
    val c = shop.status(st.status)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(3.dp)).background(shop.sheet)
            .border(1.dp, shop.line, RoundedCornerShape(3.dp)).clickable(onClickLabel = stringResource(R.string.p3_view_edit), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c))
        Spacer(Modifier.width(10.dp))
        DocThumb(d, Modifier.size(58.dp, 40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(docLabel(d), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (d.number.isNotBlank()) Text(d.number, style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
            Text(expiryText(st, d), style = Mono.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = c)
        }
        DaysRing(st)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun expiryText(st: DocState, d: Document): String = documentExpiry(d, st, LocalContext.current)

@Composable
private fun docLabel(d: Document) = documentLabel(d, LocalContext.current)

@Composable
private fun docTypeLabel(type: DocType) = docLabel(Document("", type))

/** Days left inside a ring that empties over the last year; ∞ for papers that never expire. */
@Composable
fun DaysRing(st: DocState, size: Dp = 52.dp) {
    val shop = LocalShop.current
    val c = shop.status(st.status)
    val left = st.daysLeft
    val frac = if (left == null) 1f else (left.coerceAtLeast(0) / 365f).coerceIn(.03f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.width * .07f
            val inset = s / 2 + this.size.width * .06f
            val arc = Size(this.size.width - 2 * inset, this.size.width - 2 * inset)
            drawCircle(shop.line, arc.width / 2, style = Stroke(s))
            drawArc(c, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        val (big, small) = when {
            left == null -> "∞" to ""
            left < 0 -> "${-left}" to stringResource(R.string.p3_expired_caps)
            left > 365 -> "${left / 365}" to if (left / 365 == 1L) stringResource(R.string.p3_year_caps) else stringResource(R.string.p3_years_caps)
            else -> "$left" to if (left == 1L) stringResource(R.string.p3_day_caps) else stringResource(R.string.p3_days_caps)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = Mono.copy(fontSize = if (big.length > 3) 11.sp else 14.sp, fontWeight = FontWeight.SemiBold), color = c)
            if (small.isNotEmpty()) Text(small, style = Caption.copy(fontSize = 7.sp, letterSpacing = .6.sp), color = shop.ink2)
        }
    }
}

@Composable
private fun DocThumb(d: Document, modifier: Modifier) {
    val shop = LocalShop.current
    val photo = d.photos.firstOrNull()
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(shop.sheet2).border(1.dp, shop.line, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
        if (photo != null) Photo(photo, Modifier.fillMaxSize(), ContentScale.Crop, 300) else Icon(d.type.icon, shop.ink2, 18.dp)
    }
}

@Composable
fun Photo(path: String, modifier: Modifier, scale: ContentScale = ContentScale.Fit, maxSide: Int = 1600) {
    val bmp = remember(path, maxSide) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            val sample = Integer.highestOneBit(maxOf(1, maxOf(o.outWidth, o.outHeight) / maxSide))
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
    bmp?.let { Image(it, stringResource(R.string.p3_document_photo), modifier, contentScale = scale) }
}

// ---------- Add or edit one paper ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocScreen(vm: AppViewModel, draft: Document) {
    val shop = LocalShop.current
    val ctx = LocalContext.current
    var d by remember(draft.id) { mutableStateOf(draft) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var read by remember { mutableStateOf<LocalDate?>(null) }
    var slot by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<java.io.File?>(null) }
    val isNew = vm.garage.documents.none { it.id == d.id }

    fun setPhoto(path: String) {
        val list = d.photos.toMutableList()
        if (slot < list.size) list[slot] = path else list += path
        d = d.copy(photos = list)
        // Offer the latest date printed on the paper as its expiry.
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(ctx, android.net.Uri.fromFile(java.io.File(path))))
            .addOnSuccessListener { t -> pickExpiry(t.text)?.takeIf { it.toString() != d.expires }?.let { read = it } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> pending?.let { if (ok) setPhoto(it.path) else it.delete() } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.importDocPhoto(it)?.let(::setPhoto) } }
    fun shoot(i: Int) {
        slot = i
        val f = vm.newDocPhoto().also { pending = it }
        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }

    Column(Modifier.fillMaxSize().background(shop.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header(if (isNew) stringResource(R.string.p3_new_paper) else docLabel(d), { vm.docDraft = null; vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(stringResource(R.string.p3_front), stringResource(R.string.p3_reverse)).forEachIndexed { i, label ->
                    PhotoSlot(label, d.photos.getOrNull(i), Modifier.weight(1f), onCamera = { shoot(i) },
                        onGallery = { slot = i; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                }
            }
            read?.let { date ->
                Row(Modifier.fillMaxWidth().background(shop.accent.copy(alpha = .1f), RoundedCornerShape(6.dp)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.p3_ocr_date, localizedDate(date.toString())), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton({ d = d.copy(expires = date.toString()); read = null }) { Text(stringResource(R.string.p3_use_expiry)) }
                }
            }
            if (isNew) {
                Cap(stringResource(R.string.p3_type))
                ChoiceRow(DocType.entries.map { it.name to docTypeLabel(it) }, d.type.name) { d = d.copy(type = DocType.valueOf(it)) }
            }
            if (d.type == DocType.other) AppInput(d.title, { d = d.copy(title = it.take(60)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.p3_name)) }, singleLine = true)
            AppInput(d.number, { d = d.copy(number = it.take(60)) }, Modifier.fillMaxWidth(), label = { Text(docNumberLabel(d.type)) }, singleLine = true)
            Cap(stringResource(R.string.p3_expiry))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(d.expires?.let { localizedDate(it) } ?: stringResource(R.string.p3_choose_date), { picking = true }, Modifier.weight(1f), ghost = true, icon = "calendar")
                if (d.expires != null) TextButton({ d = d.copy(expires = null) }) { Text(stringResource(R.string.no_expiry)) }
            }
            if (d.expires != null) Hint(stringResource(R.string.p3_reminder_hint, vm.garage.paperReminderDays[d.type] ?: 30))
            AppInput(d.notes, { d = d.copy(notes = it.take(500)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.p3_notes)) }, maxLines = 3)
            SourceRow(d)
            FormField(d.officialUrl, stringResource(R.string.p3_digital_link), { d = d.copy(officialUrl = it.take(2000)) })
            if (d.officialUrl.isNotBlank()) {
                FormField(d.officialSite, stringResource(R.string.p3_site_name), { d = d.copy(officialSite = it.take(100)) })
                if (officialUri(d) == null) Hint(stringResource(R.string.p3_invalid_url), shop.bad)
            }
            if (!isNew) TextButton({ confirmDelete = true }) { Text(stringResource(R.string.delete), color = shop.bad) }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isNew && d.photos.isNotEmpty()) Btn(stringResource(R.string.p3_show), { vm.showingDoc = d.id; vm.screen = Screen.control }, ghost = true)
            Btn(stringResource(R.string.save), { vm.saveDoc(d) }, Modifier.weight(1f), enabled = (d.type != DocType.other || d.title.isNotBlank()) && (d.officialUrl.isBlank() || officialUri(d) != null))
        }
    }
    if (confirmDelete) ConfirmDelete(docLabel(d), { confirmDelete = false }) { vm.deleteDoc(d.id); confirmDelete = false }
    if (picking) {
        val start = d.expires?.let(LocalDate::parse) ?: vm.today.plusYears(1)
        val ps = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    ps.selectedDateMillis?.let { d = d.copy(expires = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text(stringResource(R.string.p3_accept)) }
            },
            dismissButton = { TextButton({ picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(ps) }
    }
}

@Composable
private fun PhotoSlot(label: String, path: String?, modifier: Modifier, onCamera: () -> Unit, onGallery: () -> Unit) {
    val shop = LocalShop.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.58f).clip(RoundedCornerShape(3.dp)).background(shop.sheet2)
                .border(1.dp, shop.line, RoundedCornerShape(3.dp)).clickable(onClickLabel = stringResource(R.string.p3_photo_for, label)) { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (path != null) Photo(path, Modifier.fillMaxSize(), ContentScale.Crop, 600)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon("camera", shop.ink2, 26.dp); Spacer(Modifier.height(4.dp)); Cap(label) }
        }
        DropdownMenu(menu, { menu = false }, containerColor = shop.sheet2) {
            MenuItem("camera", stringResource(R.string.p3_take_photo)) { menu = false; onCamera() }
            MenuItem("plus", stringResource(R.string.p3_gallery)) { menu = false; onGallery() }
        }
    }
}

// ---------- Control mode: the paper full screen at full brightness ----------

@Composable
fun ControlScreen(vm: AppViewModel, bike: Bike) {
    val ctx = LocalContext.current
    val docs = vm.papers
    val start = docs.indexOfFirst { it.id == vm.showingDoc }.coerceAtLeast(0)
    var current by remember { mutableIntStateOf(start) }
    val close = { vm.screen = if (vm.docDraft != null) Screen.doc else Screen.main }
    DisposableEffect(Unit) {
        val w = (ctx as Activity).window
        val before = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            w.attributes = w.attributes.apply { screenBrightness = before }
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    androidx.activity.compose.BackHandler(onBack = close)
    val d = docs.getOrNull(current) ?: run { close(); return }
    val control = shopFor(true)
    CompositionLocalProvider(LocalShop provides control) {
        Column(Modifier.fillMaxSize().background(control.paper).statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cap(stringResource(R.string.p3_control_title), control.ink2, Modifier.weight(1f))
                IconBtn("close", stringResource(R.string.close), close, control.ink)
            }
            val pager = androidx.compose.runtime.key(d.id) { rememberPagerState { d.photos.size.coerceAtLeast(1) } }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    d.photos.getOrNull(i)?.let { Photo(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))) }
                        ?: if (officialUri(d) != null) Column { OfficialCard(d) { vm.editDoc(d) } } else Text(stringResource(R.string.p3_no_photo), color = control.ink2)
                }
            }
            if (d.photos.size > 1) Cap(if (pager.currentPage == 0) stringResource(R.string.p3_front_swipe) else stringResource(R.string.p3_reverse), control.ink2)
            Spacer(Modifier.height(12.dp))
            Text(docLabel(d), color = control.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            if (d.number.isNotBlank()) Text(d.number, style = Mono.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = control.ink)
            Text(if (d.bikeId != null) bike.title else stringResource(R.string.p3_rider), color = control.plate, fontSize = 13.sp)
            d.expires?.let { Text(expiryText(docState(d, vm.today, vm.garage.paperReminderDays[d.type] ?: 30), d), style = Mono.copy(fontSize = 12.sp), color = control.plate) }
            Spacer(Modifier.height(14.dp))
            DocumentTabs(docs, d.id) { current = docs.indexOf(it) }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.p3_document_info), color = control.ink2, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun docNumberLabel(type: DocType) = stringResource(when (type) {
    DocType.transit_license -> R.string.p3_plate
    DocType.soat, DocType.insurance -> R.string.p3_policy
    DocType.driving_license -> R.string.p3_license_number
    DocType.inspection -> R.string.p3_certificate
    DocType.tax, DocType.other -> R.string.p3_reference
})

@Composable
internal fun PaperLimits(vm: AppViewModel) {
    val shop = LocalShop.current
    var editing by remember { mutableStateOf<DocType?>(null) }
    var days by remember { mutableStateOf("") }
    DocType.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { type ->
                Column(Modifier.weight(1f).border(1.dp, shop.line, RoundedCornerShape(3.dp)).clickable {
                    editing = type; days = (vm.garage.paperReminderDays[type] ?: 30).toString()
                }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(type.icon, shop.ink2, 24.dp)
                    Text(docTypeLabel(type))
                    Hint(stringResource(R.string.p3_reminder_days, vm.garage.paperReminderDays[type] ?: 30))
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    editing?.let { type -> AlertDialog(onDismissRequest = { editing = null }, title = { Text(docTypeLabel(type)) },
        text = { FormField(days, stringResource(R.string.p3_anticipation), { days = it.filter(Char::isDigit).take(4) }, numeric = true) },
        confirmButton = { TextButton({
            vm.setPaperReminder(type, days.toInt()); editing = null
        }, enabled = days.toIntOrNull() in 0..1095) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel)) } }) }
}


@Composable
private fun docTabLabel(type: DocType) = stringResource(when (type) {
    DocType.transit_license -> R.string.p3_tab_transit_license
    DocType.soat -> R.string.p3_tab_soat
    DocType.inspection -> R.string.p3_tab_inspection
    DocType.insurance -> R.string.p3_tab_insurance
    DocType.tax -> R.string.p3_tab_tax
    DocType.driving_license -> R.string.p3_tab_driving_license
    DocType.other -> R.string.p3_tab_other
})
