package com.kaze.motoplus.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        Knob(on, enabled)
    }
}

/** The switch alone, for rows that carry their own text (Mi moto, Qué vigilar); [label] names it for TalkBack. */
@Composable
internal fun ToggleKnob(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Box(Modifier.size(56.dp, 48.dp).semantics { contentDescription = label }.toggleable(on, role = Role.Switch, onValueChange = onChange), contentAlignment = Alignment.Center) {
        Knob(on, true)
    }
}

/** Track and thumb; the thumb slides and both colours fade. */
@Composable
private fun Knob(on: Boolean, enabled: Boolean) {
    val shop = LocalShop.current
    val thumbOffset by animateDpAsState(if (on) 22.dp else 6.dp, motion(), label = "toggle")
    val track by animateColorAsState(if (on) shop.accent else shop.sheet2, motion(), label = "track")
    val edge by animateColorAsState(if (on) shop.accent else shop.ink2, motion(), label = "edge")
    val thumb by animateColorAsState(if (on) shop.onAccent else shop.ink2, motion(), label = "thumb")
    Box(Modifier.requiredSize(44.dp, 27.dp).alpha(if (enabled) 1f else 0.4f).background(track, CircleShape).border(2.dp, edge, CircleShape)) {
        Box(Modifier.align(Alignment.CenterStart).offset(x = thumbOffset).size(15.dp).background(thumb, CircleShape))
    }
}
