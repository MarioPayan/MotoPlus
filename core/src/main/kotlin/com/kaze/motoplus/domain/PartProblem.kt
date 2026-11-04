package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ProblemKind { broken, notResponding }

/** Something the rider noticed on a part. Until logged maintenance or the rider clears it, the part is overdue whatever its km say. */
@Serializable
data class PartProblem(val kind: ProblemKind, val date: String, val note: String = "")

/** A reported part must show: it goes back on watch and off snooze. The rider just said it, so no reminder repeats it. */
fun Bike.reportProblem(partId: String, problem: PartProblem): Bike {
    if (parts.none { it.id == partId }) return this
    return copy(parts = parts.map { if (it.id == partId) it.copy(problem = problem, watched = true, snoozedUntil = null) else it }, notified = notified + (partId to Status.bad))
}

fun Bike.clearProblem(partId: String) = copy(parts = parts.map { if (it.id == partId) it.copy(problem = null) else it })
