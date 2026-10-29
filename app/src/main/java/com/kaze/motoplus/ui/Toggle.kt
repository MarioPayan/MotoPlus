package com.kaze.motoplus.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Instrumento toggle: a wrapping text column, fixed control and one accessible touch target. */
@Composable
internal fun Toggle(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val shop = LocalShop.current
    val thumbOffset by animateDpAsState(if (on) 22.dp else 6.dp, label = "toggle")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 18.sp)
            if (hint.isNotBlank()) Hint(hint)
        }
        Box(Modifier.requiredSize(44.dp, 27.dp)
            .background(if (on) shop.accent else shop.sheet2, CircleShape)
            .border(2.dp, if (on) shop.accent else shop.ink2, CircleShape)) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = thumbOffset).size(15.dp)
                .background(if (on) shop.onAccent else shop.ink2, CircleShape))
        }
    }
}
