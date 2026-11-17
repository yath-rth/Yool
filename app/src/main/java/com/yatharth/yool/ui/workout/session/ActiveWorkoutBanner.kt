package com.yatharth.yool.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yatharth.yool.appContainer
import com.yatharth.yool.domain.WorkoutMath
import androidx.compose.ui.text.style.TextOverflow
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxBadge
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.workout.session.RestTimer
import com.yatharth.yool.ui.workout.session.SessionMath

/** Banner above the bottom bar while a workout is active; tap to return to it. Renders nothing otherwise. */
@Composable
fun ActiveWorkoutBanner(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val repo = ctx.appContainer.workoutRepository
    val active by remember(repo) { repo.observeActive() }.collectAsState(initial = null)
    val w = active ?: return
    val rest by RestTimer.state(ctx).collectAsState()
    val now by rememberNow(500)
    val remaining = rest?.remainingSec(now) ?: 0

    ShadowCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), onClick = onOpen, contentPadding = 10.dp, shadow = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                w.workout.name.uppercase() + " · " + SessionMath.elapsedClock(now - w.workout.startedAt),
                style = MaterialTheme.typography.labelMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (remaining > 0) Text("REST ${WorkoutMath.formatClock(remaining)}", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
            TxBadge("Resume", color = Coral)
        }
    }
}
