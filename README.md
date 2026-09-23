# LiquidPage

[![Maven Central](https://img.shields.io/maven-central/v/io.github.rauletsinf/liquidpage?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.rauletsinf/liquidpage)
[![CI](https://github.com/RaulETSINF/LiquidPagerCMP/actions/workflows/ci.yml/badge.svg)](https://github.com/RaulETSINF/LiquidPagerCMP/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-lightgrey.svg)

A liquid swipe pager for **Compose Multiplatform** (Android and iOS), in the style of
[Cuberto's liquid-swipe](https://github.com/Cuberto/liquid-swipe).

Swiping forward carves a wave into the current page through which the next page appears; swiping
back pours the previous page in from the leading edge. Pages are clipped in place, never moved, so
the effect works with any content.

<!-- Demo GIF: <p align="center"><img src="docs/demo.gif" width="320" alt="LiquidPage demo"></p> -->

- Pure Compose in common code: no Android views, no UIKit, no resources.
- Cuberto's look by default, with a "next" button on a resting bulge, or a `Plain` preset.
- Each frame only re-records the clip of one layer: no recomposition, no re-layout.
- Flings, chained swipes and programmatic navigation.
- Accessibility and right-to-left support.

## Installation

```kotlin
// Compose Multiplatform
commonMain.dependencies {
    implementation("io.github.rauletsinf:liquidpage:0.1.0")
}

// Android only
dependencies {
    implementation("io.github.rauletsinf:liquidpage:0.1.0")
}
```

| LiquidPage | Kotlin | Compose Multiplatform | Jetpack Compose | Android `minSdk` | iOS targets |
| --- | --- | --- | --- | --- | --- |
| 0.1.0 | 2.3.21 | 1.12.1 | 1.12.1 | 23 | `iosArm64`, `iosSimulatorArm64` |

Compose is an `api` dependency, so your project resolves at least these versions; use the same
Kotlin version or a newer one. On iOS the pager is used from Kotlin inside a
`ComposeUIViewController`; there is no Swift API. Wave edges are anti-aliased on iOS and on modern
Android (verified on Android 15); older hardware canvases may render them aliased.

## Usage

```kotlin
import com.raupime.liquidpage.LiquidPager
import com.raupime.liquidpage.rememberLiquidPagerState

val state = rememberLiquidPagerState { pages.size }

LiquidPager(state = state, modifier = Modifier.fillMaxSize()) { page ->
    Box(Modifier.fillMaxSize().background(pages[page].color)) {
        Text(pages[page].title)
    }
}
```

Swipe or tap the chevron to move forward, swipe the other way to go back.

- Each page should fill the pager and draw its own background: the next page is rendered
  underneath and shows through the wave. Pages with transparent areas need `pagesAreOpaque = false`.
- The pager fills the space it is given; inside unbounded constraints, give it a size.
- Only the current page, its neighbours and the page being revealed are composed. Page state is kept
  with `rememberSaveable`; pass `key` if pages can be reordered.

A complete example is in
[`LiquidOnboarding.kt`](shared/src/commonMain/kotlin/com/raupime/liquid/LiquidOnboarding.kt).

## State

`rememberLiquidPagerState(initialPage = 0) { pageCount }` survives configuration changes and process
death.

| Member | Description |
| --- | --- |
| `currentPage` | Settled page; changes when a transition completes. |
| `targetPage` | Page being revealed, or `currentPage` when idle. |
| `progress` | Progress from `currentPage` to `targetPage`, `0..1`. |
| `currentPageOffsetFraction` | Signed offset in pages: `currentPage + currentPageOffsetFraction` is the visual position. |
| `isTransitioning`, `isDragging`, `isAnimationRunning` | Gesture and animation status. |
| `canScrollForward`, `canScrollBackward` | Whether there is a next / previous page. |
| `suspend animateToPage(page)` | Reveals `page` with the wave; pages further than one away are revealed directly. Ignored while dragging. |
| `suspend scrollToPage(page)` | Jumps to `page` without animation. |

```kotlin
Button(onClick = { scope.launch { state.animateToPage(state.currentPage + 1) } }) { Text("Next") }
```

`progress` and `currentPageOffsetFraction` change every frame: read them in a `graphicsLayer` or
draw block (to drive a page indicator, for example) to avoid recomposing.

## Customization

```kotlin
LiquidPager(
    state = state,
    waveSpec = LiquidWaveSpec.Default,                      // or LiquidWaveSpec.Plain, or a copy()
    animationSpec = tween(800, easing = LinearEasing),      // duration of a full transition
    userScrollEnabled = true,                               // false to drive it only from code
    pagesAreOpaque = true,
    snapPositionalThreshold = 1f / 3f,                      // fraction of the width that commits
    snapVelocityThreshold = 400.dp,                         // fling speed (per second) that commits
    key = { page -> pages[page].id },
    nextButton = { LiquidPagerDefaults.NextButton(state) }, // null hides it
) { page -> /* ... */ }
```

A half-revealed page takes half the tween duration to finish; springs work too.

### Wave presets

| Preset | Look |
| --- | --- |
| `LiquidWaveSpec.Default` | Cuberto's design: resting bulge with the next button, a big wave whose tip follows the finger, and a small "curtain" wave when swiping back. |
| `LiquidWaveSpec.Plain` | No resting bulge and no button: the wave only appears while swiping. |

```kotlin
val spec = LiquidWaveSpec.Default.copy(
    restHorizontalRadius = 40.dp,       // resting bulge size; 0.dp removes it (and the button)
    restWaveCenterFraction = 0.72f,     // vertical position of the bulge
    forwardDragFraction = 0.45f,        // progress reached by a full-width forward drag
    waveCenterFollowsPointer = true,    // the wave center chases the finger
    maxHorizontalRadiusFraction = 0.8f, // width of the forward wave
    backwardWaveAmplitude = 48.dp,      // wave used when swiping back
)
```

`forwardDragFraction` is the main "speed" knob: `0.45` keeps the wave tip under the finger, `1`
completes the transition with a full-width drag. Every parameter is documented in the KDoc.

### Next button

```kotlin
nextButton = { LiquidPagerDefaults.NextButton(state, tint = Color.Black) } // restyled
nextButton = { MyArrow() }                                                  // your own, ~48 dp
nextButton = null                                                           // none
```

It is shown only when there is a next page and the spec has a resting bulge.

### The wave as a shape

`LiquidWaveShape` is a regular `Shape`, so the wave can clip any composable:

```kotlin
Box(Modifier.clip(LiquidWaveShape(progress = progress)))                  // forward wave
Box(Modifier.clip(LiquidWaveShape(progress = progress, backward = true))) // backward wave
```

`LiquidWaveGeometry` and `Path.addLiquidWave` expose the resolved geometry for custom drawing.

## Credits

The wave geometry and choreography are ported from Cuberto's
[liquid-swipe](https://github.com/Cuberto/liquid-swipe) and its Android port
[LiquidSwipe](https://github.com/Chrisvin/LiquidSwipe) by Jem, both MIT licensed.

## License

[MIT](LICENSE) © 2026 Raul Piqueras Melero
