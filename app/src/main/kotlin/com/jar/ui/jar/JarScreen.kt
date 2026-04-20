package com.jar.ui.jar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jar.data.JarState
import com.jar.ui.theme.JarTheme
import com.jar.ui.theme.jarAccent

@Composable
fun JarScreen(viewModel: JarViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    JarScreenContent(state)
}

@Composable
private fun JarScreenContent(state: JarState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            JarCanvas(state = state, modifier = Modifier.fillMaxSize())
            BalanceLabel(state = state)
        }
        Text(
            text = "spent this month: ${formatRupees(state.spent)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun JarCanvas(state: JarState, modifier: Modifier) {
    val accent = jarAccent(state)
    val targetLevel = state.fractionRemaining.coerceIn(0f, 1f)
    val level by animateFloatAsState(
        targetValue = targetLevel,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f),
        label = "jar-level"
    )
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Canvas(modifier = modifier) {
        val cornerRadius = CornerRadius(size.width * 0.10f, size.width * 0.10f)
        val jarRect = RoundRect(
            left = 0f, top = 0f,
            right = size.width, bottom = size.height,
            cornerRadius = cornerRadius
        )
        val jarPath = Path().apply { addRoundRect(jarRect) }

        clipPath(jarPath) {
            val liquidTop = size.height * (1f - level)
            drawRect(
                color = accent,
                topLeft = Offset(0f, liquidTop),
                size = Size(size.width, size.height - liquidTop)
            )
        }
        drawPath(path = jarPath, color = outlineColor, style = Stroke(width = 6f))
    }
}

@Composable
private fun BalanceLabel(state: JarState) {
    val remaining = state.startingAmount - state.spent
    val percentLeft = (state.fractionRemaining * 100f).toInt().coerceAtLeast(0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatRupees(remaining),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$percentLeft% left",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun JarScreenPreviewFull() {
    JarTheme {
        Surface(color = Color.White) {
            JarScreenContent(
                state = JarState(
                    startingAmount = 30_00_000L,
                    spent = 5_00_000L,
                    monthlyLimit = 25_00_000L,
                    fractionRemaining = 25f / 30f,
                    isOverdrawn = false,
                    isOverLimit = false
                )
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun JarScreenPreviewLow() {
    JarTheme {
        Surface(color = Color.White) {
            JarScreenContent(
                state = JarState(
                    startingAmount = 30_00_000L,
                    spent = 27_00_000L,
                    monthlyLimit = 25_00_000L,
                    fractionRemaining = 3f / 30f,
                    isOverdrawn = false,
                    isOverLimit = true
                )
            )
        }
    }
}
