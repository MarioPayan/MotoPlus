package com.kaze.motoplus.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Instrumento toggle: a wrapping text column, fixed control and one accessible touch target.
 * [enabled] false dims the control and ignores taps; [tag] adds a small neutral label after the title ("Próximamente").
 */
@Composable
internal fun Toggle(title: String, hint: String, on: Boolean, enabled: Boolean = true, tag: String? = null, onChange: (Boolean) -> Unit) {
    val shop = LocalShop.current
    val thumbOffset by animateDpAsState(if (on) 22.dp else 6.dp, label = "toggle")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, modifier = Modifier.weight(1f, fill = false))
                if (tag != null) Text(
                    tag.uppercase(), style = Mono.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp), color = shop.ink,
                    modifier = Modifier.background(shop.line, RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (hint.isNotBlank()) Hint(hint)
        }
        Box(Modifier.requiredSize(44.dp, 27.dp).alpha(if (enabled) 1f else 0.4f)
            .background(if (on) shop.accent else shop.sheet2, CircleShape)
            .border(2.dp, if (on) shop.accent else shop.ink2, CircleShape)) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = thumbOffset).size(15.dp)
                .background(if (on) shop.onAccent else shop.ink2, CircleShape))
        }
    }
}
