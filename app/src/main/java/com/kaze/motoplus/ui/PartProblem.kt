package com.kaze.motoplus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaze.motoplus.AppViewModel
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.*

/** The way to report a problem from the Moto tab, where a healthy part has no row of its own. */
@Composable
fun ReportPartButton(vm: AppViewModel, bike: Bike, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    var part by remember { mutableStateOf<String?>(null) }
    Btn(stringResource(R.string.problem_report), { picking = true }, modifier, ghost = true, icon = "warning")
    if (picking) PartPicker(bike, bike.parts.filter { it.problem != null }.map { it.id }.toSet(), { id -> picking = false; part = id }, { picking = false },
        stringResource(R.string.problem_pick_part))
    part?.let { id -> bike.parts.firstOrNull { it.id == id }?.let { p -> ProblemDialog(vm, p) { part = null } } }
}

/** The report on a part's detail: what it is, its note and date, and how to change or clear it; otherwise the way to report one. */
@Composable
fun PartProblemSection(vm: AppViewModel, part: Part) {
    var editing by remember { mutableStateOf(false) }
    val problem = part.problem
    if (problem == null) Btn(stringResource(R.string.problem_report), { editing = true }, Modifier.fillMaxWidth(), ghost = true, icon = "warning")
    else {
        Text(stringResource(problemLabel(problem.kind)), fontWeight = FontWeight.SemiBold, color = LocalShop.current.status(Status.bad))
        ProblemDetails(problem)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(stringResource(R.string.problem_edit), { editing = true }, Modifier.weight(1f), ghost = true)
            Btn(stringResource(R.string.problem_clear), { vm.clearProblem(part.id) }, Modifier.weight(1f), ghost = true)
        }
    }
    if (editing) ProblemDialog(vm, part) { editing = false }
}

@Composable
fun ProblemDetails(problem: PartProblem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Hint(stringResource(R.string.problem_reported_on, localizedDate(problem.date)))
        if (problem.note.isNotBlank()) Hint(problem.note)
    }
}

/** Picks the kind and an optional note; a part already reported starts from its report and can be cleared here too. */
@Composable
fun ProblemDialog(vm: AppViewModel, part: Part, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(part.problem?.kind ?: ProblemKind.broken) }
    var note by remember { mutableStateOf(part.problem?.note.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = LocalShop.current.sheet, title = { Text(partLabel(part)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Hint(stringResource(R.string.problem_hint))
            ChoiceRow(ProblemKind.entries.map { it.name to stringResource(problemLabel(it)) }, kind.name) { kind = ProblemKind.valueOf(it) }
            FormField(note, stringResource(R.string.problem_note), { note = it.take(400) }, multiline = true)
        }
    }, confirmButton = { TextButton({ vm.reportProblem(part.id, kind, note); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (part.problem != null) TextButton({ vm.clearProblem(part.id); onDismiss() }) { Text(stringResource(R.string.problem_clear)) }
                TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        })
}
