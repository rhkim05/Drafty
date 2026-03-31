package com.drafty.feature.canvas.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Page forward/back navigation controls with page indicator.
 *
 * Displays a compact bar: [‹] [3 / 12] [›]
 *
 * See: docs/research.md — Phase 2 §6 (Page Navigation)
 */
@Composable
fun PageNavigator(
    currentPage: Int,
    totalPages: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Previous page button
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(36.dp),
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleMedium,
                color = if (hasPrevious) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
            )
        }

        // Page indicator
        Text(
            text = "$currentPage / $totalPages",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // Next page button
        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(36.dp),
        ) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = if (hasNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
            )
        }
    }
}
