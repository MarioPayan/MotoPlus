package com.kaze.motoplus.ui

import com.kaze.motoplus.domain.DocType

val DocType.icon get() = when (this) {
    DocType.transit_license, DocType.tax, DocType.other -> "doc"
    DocType.soat, DocType.insurance -> "shield"
    DocType.inspection -> "wrench"
    DocType.driving_license -> "helmet"
}
