package com.ffboostx.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ffboostx.ui.theme.BoostColors
import com.ffboostx.ui.theme.LocalMotion

/**
 * Shared shell for every popup in the app. It deliberately does not use
 * AlertDialog: the platform dialog brings its own light surface, title/body type
 * scale and button bar, none of which match the rest of the interface.
 *
 * The window is sized by its content with a max width, so it stays compact on a
 * small phone and does not stretch edge to edge on a large one.
 */
@Composable
fun PremiumDialog(
    onDismissRequest: () -> Unit,
    dismissOnOutsideTouch: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val motion = LocalMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(motion.durationMs(220)),
        label = "dialogEnter"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = dismissOnOutsideTouch,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        // A small rise-and-settle rather than a bounce.
                        scaleX = 0.94f + 0.06f * progress
                        scaleY = 0.94f + 0.06f * progress
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF161A26).copy(alpha = 0.98f),
                                Color(0xFF0C0F18).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, BoostColors.Ember.copy(alpha = 0.30f)),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(22.dp),
                content = content
            )
        }
    }
}

/**
 * Replaces the stock permission/error alert. Used for every blocking message so
 * the app never drops out of its own visual language.
 */
@Composable
fun PermissionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Not now",
    accent: Color = BoostColors.Warning
) {
    PremiumDialog(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .border(
                        BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text("⚡", fontSize = 16.sp)
            }
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = BoostColors.TextPrimary
            )
        }

        Spacer(Modifier.padding(vertical = 7.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = BoostColors.TextSecondary
        )

        Spacer(Modifier.padding(vertical = 9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DialogButton(
                label = dismissLabel,
                filled = false,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            DialogButton(
                label = confirmLabel,
                filled = true,
                onClick = onConfirm,
                modifier = Modifier.weight(1.3f)
            )
        }
    }
}

/** Compact button used inside popups. Meets the 48dp minimum touch target. */
@Composable
fun DialogButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (filled) {
                    Modifier.background(BoostColors.EmberGradient)
                } else {
                    Modifier
                        .background(BoostColors.Surface)
                        .border(BorderStroke(1.dp, BoostColors.Line), RoundedCornerShape(14.dp))
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) Color(0xFF200A00) else BoostColors.TextSecondary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
