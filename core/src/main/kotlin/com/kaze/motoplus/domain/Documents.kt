package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
enum class DocType { transit_license, soat, inspection, insurance, tax, driving_license, other }

/** Whether a document belongs to a bike or to the rider (shared by every bike). */
val DocType.forRider get() = this == DocType.driving_license

/**
 * A paper the rider keeps a copy of. [bikeId] is null for the rider's own papers.
 * [photos] are file paths, front first. [alerted] is the last reminder stage sent (see [REMINDER_DAYS]).
 */
@Serializable
data class Document(
    val id: String,
    val type: DocType,
    val bikeId: String? = null,
    val title: String = "",
    val number: String = "",
    val expires: String? = null,
    val photos: List<String> = emptyList(),
    val notes: String = "",
    val alerted: Int? = null,
)

/** Reminders go out this many days before expiry, then on the day it expires (0). */
val REMINDER_DAYS = listOf(30, 7, 1, 0)

data class DocState(val status: Status, val daysLeft: Long?)

fun docState(d: Document, today: LocalDate): DocState {
    val exp = d.expires?.let(LocalDate::parse) ?: return DocState(Status.ok, null)
    val left = ChronoUnit.DAYS.between(today, exp)
    return DocState(if (left < 0) Status.bad else if (left <= 30) Status.warn else Status.ok, left)
}

/** The reminder stage a document has reached today, or null when it is not due yet or never expires. */
fun reminderStage(d: Document, today: LocalDate): Int? {
    val left = docState(d, today).daysLeft ?: return null
    return REMINDER_DAYS.lastOrNull { left <= it }
}

/** Documents with a reminder stage not sent yet, with that stage. */
fun docAlerts(docs: List<Document>, today: LocalDate): List<Pair<Document, Int>> = docs.mapNotNull { d ->
    val stage = reminderStage(d, today) ?: return@mapNotNull null
    if (d.alerted != null && d.alerted <= stage) null else d to stage
}

/** Expiry date read from a photo: the latest date in the text, as printed dd/mm/yyyy, dd-mm-yyyy or yyyy-mm-dd. */
fun pickExpiry(ocr: String): LocalDate? {
    val dmy = Regex("""(?<!\d)(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})(?!\d)""").findAll(ocr).mapNotNull { m ->
        val (d, mo, y) = m.destructured
        runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
    val ymd = Regex("""(?<!\d)(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})(?!\d)""").findAll(ocr).mapNotNull { m ->
        val (y, mo, d) = m.destructured
        runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
    return (dmy + ymd).maxOrNull()
}
