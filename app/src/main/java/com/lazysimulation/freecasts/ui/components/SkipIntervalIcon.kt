package com.lazysimulation.freecasts.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lazysimulation.freecasts.R
import com.lazysimulation.freecasts.data.preferences.DEFAULT_SKIP_INTERVAL_SECONDS

@Composable
fun SkipBackwardIcon(
    intervalSeconds: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    SkipIntervalIcon(
        intervalSeconds = intervalSeconds,
        isForward = false,
        modifier = modifier,
        iconSize = iconSize,
    )
}

@Composable
fun SkipForwardIcon(
    intervalSeconds: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    SkipIntervalIcon(
        intervalSeconds = intervalSeconds,
        isForward = true,
        modifier = modifier,
        iconSize = iconSize,
    )
}

@Composable
private fun SkipIntervalIcon(
    intervalSeconds: Int,
    isForward: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    val imageVector = when {
        intervalSeconds == DEFAULT_SKIP_INTERVAL_SECONDS && isForward ->
            ImageVector.vectorResource(R.drawable.ic_forward_30)
        intervalSeconds == DEFAULT_SKIP_INTERVAL_SECONDS ->
            ImageVector.vectorResource(R.drawable.ic_replay_30)
        isForward -> Icons.Default.FastForward
        else -> Icons.Default.Replay
    }

    Box(
        modifier = modifier.size(iconSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = skipIntervalContentDescription(isForward, intervalSeconds),
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

fun skipIntervalContentDescription(isForward: Boolean, intervalSeconds: Int): String {
    return if (isForward) {
        "Skip forward $intervalSeconds seconds"
    } else {
        "Skip back $intervalSeconds seconds"
    }
}
