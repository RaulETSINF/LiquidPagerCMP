# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-09-23

First public release.

### Added

- `LiquidPager`: a pager whose pages are revealed with a liquid wave, in the style of Cuberto's
  liquid-swipe, for Android and iOS (`iosArm64`, `iosSimulatorArm64`).
- `LiquidPagerState` and `rememberLiquidPagerState`: settled and target page, per-frame progress,
  `animateToPage` and `scrollToPage`. The settled page survives configuration changes and process
  death.
- `LiquidWaveSpec` with the `Default` (Cuberto) and `Plain` presets.
- `LiquidPagerDefaults.NextButton`: chevron button on the resting bulge.
- `LiquidWaveShape`, `LiquidWaveGeometry` and `Path.addLiquidWave` to reuse the wave outside the
  pager.
- Accessibility semantics (page and scroll actions, only the current page exposed) and
  right-to-left support.

[Unreleased]: https://github.com/RaulETSINF/LiquidPagerCMP/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/RaulETSINF/LiquidPagerCMP/releases/tag/v0.1.0
