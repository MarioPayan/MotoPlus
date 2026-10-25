package com.kaze.motoplus.ui

import android.app.Activity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val mine = vm.garage.documents.filter { it.bikeId == bike.id }.sortedBy { docState(it, vm.today).daysLeft ?: Long.MAX_VALUE }
    val rider = vm.garage.documents.filter { it.bikeId == null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cap("De la moto · ${bike.model}", modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (vm.papers.isNotEmpty()) TextButton({ vm.showingDoc = null; vm.screen = Screen.control }) {
                Text("MODO CONTROL", style = Mono.copy(fontSize = 12.sp), color = shop.accent)
            }
        }
        mine.forEach { DocCard(it, vm.today) { vm.editDoc(it) } }
        missing(mine, listOf(DocType.transit_license, DocType.soat, DocType.inspection)).forEach { t -> EmptyDoc(t) { vm.newDoc(t) } }
        Cap("Tuyos", modifier = Modifier.padding(start = 6.dp, top = 10.dp))
        rider.forEach { DocCard(it, vm.today) { vm.editDoc(it) } }
        if (rider.none { it.type == DocType.driving_license }) EmptyDoc(DocType.driving_license) { vm.newDoc(DocType.driving_license) }
        Box {
            Btn("Añadir otro papel", { adding = true }, Modifier.fillMaxWidth().padding(top = 6.dp), ghost = true, icon = "plus")
            DropdownMenu(adding, { adding = false }, containerColor = shop.sheet2) {
                DocType.entries.forEach { t -> MenuItem(t.icon, t.label) { adding = false; vm.newDoc(t) } }
            }
        }
        Hint("Copias de referencia guardadas solo en este móvil. No sustituyen al documento original ante la autoridad.", modifier = Modifier.padding(6.dp))
        Spacer(Modifier.height(8.dp))
    }
}

private fun missing(have: List<Document>, wanted: List<DocType>) = wanted.filter { t -> have.none { it.type == t } }

@Composable
fun DocCard(d: Document, today: LocalDate, onClick: () -> Unit) {
    val shop = LocalShop.current
    val st = docState(d, today)
    val c = shop.status(st.status)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp)).background(shop.sheet)
            .border(1.dp, shop.line, RoundedCornerShape(10.dp)).clickable(onClickLabel = "Ver o editar", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c))
        Spacer(Modifier.width(10.dp))
        DocThumb(d, Modifier.size(58.dp, 40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(d.name(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (d.number.isNotBlank()) Text(d.number, style = Mono.copy(fontSize = 11.sp), color = shop.ink2)
            Text(expiryText(st, d), style = Mono.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = c)
        }
        DaysRing(st)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun EmptyDoc(t: DocType, onClick: () -> Unit) {
    val shop = LocalShop.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, shop.line, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Añadir", onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(t.icon, shop.ink2, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(t.label, fontSize = 14.sp, color = shop.ink2, modifier = Modifier.weight(1f))
        Icon("plus", shop.accent, 20.dp)
    }
}

fun expiryText(st: DocState, d: Document): String {
    val e = d.expires ?: return "Sin vencimiento"
    return if (st.status == Status.bad) "Venció ${prettyDate(e)}" else "Vence ${prettyDate(e)}"
}

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
            left < 0 -> "${-left}" to "VENCIDO"
            left > 365 -> "${left / 365}" to if (left / 365 == 1L) "AÑO" else "AÑOS"
            else -> "$left" to if (left == 1L) "DÍA" else "DÍAS"
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
    bmp?.let { Image(it, "Foto del papel", modifier, contentScale = scale) }
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
        Header(if (isNew) "Nuevo papel" else d.name(), { vm.docDraft = null; vm.screen = Screen.main })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Frente", "Reverso").forEachIndexed { i, label ->
                    PhotoSlot(label, d.photos.getOrNull(i), Modifier.weight(1f), onCamera = { shoot(i) },
                        onGallery = { slot = i; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                }
            }
            read?.let { date ->
                Row(Modifier.fillMaxWidth().background(shop.accent.copy(alpha = .1f), RoundedCornerShape(6.dp)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Leí en la foto: ${prettyDate(date.toString())}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton({ d = d.copy(expires = date.toString()); read = null }) { Text("USAR COMO VENCIMIENTO") }
                }
            }
            if (isNew) {
                Cap("Tipo")
                ChoiceRow(DocType.entries.map { it.name to it.label }, d.type.name) { d = d.copy(type = DocType.valueOf(it)) }
            }
            if (d.type == DocType.other) OutlinedTextField(d.title, { d = d.copy(title = it.take(60)) }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(d.number, { d = d.copy(number = it.take(60)) }, Modifier.fillMaxWidth(), label = { Text(d.type.numberLabel) }, singleLine = true)
            Cap("Vencimiento")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(d.expires?.let { prettyDate(it) } ?: "Elegir fecha", { picking = true }, Modifier.weight(1f), ghost = true, icon = "calendar")
                if (d.expires != null) TextButton({ d = d.copy(expires = null) }) { Text("SIN VENCIMIENTO") }
            }
            if (d.expires != null) Hint("Te aviso 30, 7 y 1 día antes, y el día que vence.")
            OutlinedTextField(d.notes, { d = d.copy(notes = it.take(500)) }, Modifier.fillMaxWidth(), label = { Text("Notas") }, maxLines = 3)
            if (!isNew) {
                if (!confirmDelete) TextButton({ confirmDelete = true }, contentPadding = PaddingValues(0.dp)) { Text("Borrar este papel", color = shop.bad) }
                else Btn("Sí, borrar ${d.name()}", { vm.deleteDoc(d.id) }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isNew && d.photos.isNotEmpty()) Btn("Mostrar", { vm.showingDoc = d.id; vm.screen = Screen.control }, ghost = true)
            Btn("Guardar", { vm.saveDoc(d) }, Modifier.weight(1f), enabled = d.type != DocType.other || d.title.isNotBlank())
        }
    }
    if (picking) {
        val start = d.expires?.let(LocalDate::parse) ?: vm.today.plusYears(1)
        val ps = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    ps.selectedDateMillis?.let { d = d.copy(expires = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("ACEPTAR") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("CANCELAR") } },
        ) { DatePicker(ps) }
    }
}

@Composable
private fun PhotoSlot(label: String, path: String?, modifier: Modifier, onCamera: () -> Unit, onGallery: () -> Unit) {
    val shop = LocalShop.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.58f).clip(RoundedCornerShape(8.dp)).background(shop.sheet2)
                .border(1.dp, shop.line, RoundedCornerShape(8.dp)).clickable(onClickLabel = "Foto del $label") { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (path != null) Photo(path, Modifier.fillMaxSize(), ContentScale.Crop, 600)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon("camera", shop.ink2, 26.dp); Spacer(Modifier.height(4.dp)); Cap(label) }
        }
        DropdownMenu(menu, { menu = false }, containerColor = shop.sheet2) {
            MenuItem("camera", "Hacer foto") { menu = false; onCamera() }
            MenuItem("plus", "Elegir de la galería") { menu = false; onGallery() }
        }
    }
}

// ---------- Control mode: the paper full screen at full brightness ----------

@Composable
fun ControlScreen(vm: AppViewModel, bike: Bike) {
    val ctx = LocalContext.current
    val docs = vm.papers.filter { it.photos.isNotEmpty() }.ifEmpty { vm.papers }
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
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cap("Modo control · brillo al máximo", Color(0xFF9AA8B8), Modifier.weight(1f))
            IconBtn("close", "Cerrar", close, Color.White)
        }
        val pager = rememberPagerState { d.photos.size.coerceAtLeast(1) }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                d.photos.getOrNull(i)?.let { Photo(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))) }
                    ?: Text("Sin foto", color = Color(0xFF9AA8B8))
            }
        }
        if (d.photos.size > 1) Cap(if (pager.currentPage == 0) "Frente · desliza para el reverso" else "Reverso", Color(0xFF9AA8B8))
        Spacer(Modifier.height(12.dp))
        Text(d.name(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        if (d.number.isNotBlank()) Text(d.number, style = Mono.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
        Text(if (d.bikeId != null) bike.title else "Conductor", color = Color(0xFFCCD3DD), fontSize = 13.sp)
        d.expires?.let { Text(expiryText(docState(d, vm.today), d), style = Mono.copy(fontSize = 12.sp), color = Color(0xFFCCD3DD)) }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            docs.forEachIndexed { i, x ->
                val on = i == current
                Text(
                    x.type.label, style = Mono.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = if (on) Color.Black else Color(0xFFBBBBBB),
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) Color.White else Color.Transparent)
                        .border(1.dp, if (on) Color.White else Color(0xFF444444), RoundedCornerShape(999.dp))
                        .clickable { current = i }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Copia de referencia guardada en tu móvil. No sustituye el documento original.", color = Color(0xFF889099), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
