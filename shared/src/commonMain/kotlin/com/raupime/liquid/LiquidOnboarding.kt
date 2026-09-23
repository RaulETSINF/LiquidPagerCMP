package com.raupime.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raupime.liquidpage.LiquidPager
import com.raupime.liquidpage.LiquidPagerState
import com.raupime.liquidpage.LiquidWaveSpec
import com.raupime.liquidpage.rememberLiquidPagerState
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Demo onboarding shared by the Android and iOS apps. */
private data class OnboardingPage(
    val title: String,
    val body: String,
    val emoji: String,
    val background: Color,
    val foreground: Color = Color.White,
)

private val pages = listOf(
    OnboardingPage(
        title = "Hello fellow developer",
        body = "Swipe left to reveal the next page with a liquid wave, or tap the arrow.",
        emoji = "🏔️",
        background = Color(0xFF5D11F7),
    ),
    OnboardingPage(
        title = "Follow the finger",
        body = "The wave center follows your finger vertically while you drag.",
        emoji = "🗺️",
        background = Color(0xFFFF5A3B),
    ),
    OnboardingPage(
        title = "Swipe back too",
        body = "Drag to the right and the previous page pours in from the left edge.",
        emoji = "🧳",
        background = Color(0xFF1F036C),
    ),
    OnboardingPage(
        title = "Pure Compose",
        body = "No Android views, no Java. Pages never move: they are clipped in place.",
        emoji = "📷",
        background = Color(0xFF579F2B),
    ),
    OnboardingPage(
        title = "Cheers ^_^",
        body = "Ready to be published and taken multiplatform.",
        emoji = "🚐",
        background = Color(0xFFF6D336),
        foreground = Color(0xFF1C1B1F),
    ),
)

@Composable
fun LiquidOnboarding(modifier: Modifier = Modifier) {
    val state = rememberLiquidPagerState { pages.size }
    var plainStyle by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        LiquidPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            waveSpec = if (plainStyle) LiquidWaveSpec.Plain else LiquidWaveSpec.Default,
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        // Overlay: style toggle, skip button and page indicator.
        val visualPage = state.currentPage + state.currentPageOffsetFraction
        val overlayColor = overlayColorFor(visualPage)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(
                    text = if (plainStyle) "Style: plain" else "Style: Cuberto",
                    color = overlayColor,
                    onClick = { plainStyle = !plainStyle },
                )
                if (state.currentPage < pages.lastIndex) {
                    Pill(
                        text = "Skip",
                        color = overlayColor,
                        onClick = { scope.launch { state.animateToPage(pages.lastIndex) } },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PageIndicator(
                state = state,
                pageCount = pages.size,
                color = overlayColor,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(page.background)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = page.emoji, fontSize = 96.sp)
        Spacer(Modifier.height(32.dp))
        Text(
            text = page.title,
            color = page.foreground,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = page.body,
            color = page.foreground.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun PageIndicator(
    state: LiquidPagerState,
    pageCount: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val position = state.currentPage + state.currentPageOffsetFraction
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { page ->
            val distance = abs(position - page).coerceIn(0f, 1f)
            val width = 8.dp + 16.dp * (1f - distance)
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.4f + 0.6f * (1f - distance))),
            )
        }
    }
}

/** Overlay color that follows the page underneath during transitions. */
@Composable
private fun overlayColorFor(visualPage: Float): Color {
    val index = visualPage.toInt().coerceIn(0, pages.lastIndex)
    val next = (index + 1).coerceAtMost(pages.lastIndex)
    val fraction = (visualPage - index).coerceIn(0f, 1f)
    val target = if (fraction < 0.5f) pages[index].foreground else pages[next].foreground
    val animated by animateColorAsState(target, label = "overlayColor")
    return animated
}
