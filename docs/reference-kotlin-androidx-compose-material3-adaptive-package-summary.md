## Classes

|                      |                                                                    |     |
|----------------------|--------------------------------------------------------------------|-----|
| `HingeInfo`          | A class that contains the info of a hinge relevant to a `Posture`. | Cmn |
| `Posture`            | Posture info that can help make layout adaptation decisions.       | Cmn |
| `WindowAdaptiveInfo` | This class collects window info that affects adaptation decisions. | Cmn |

## Annotations

|                                                     | |     |
|-----------------------------------------------------|-|-----|
| `ExperimentalMaterial3AdaptiveApi`                  | | Cmn |
| `ExperimentalMaterial3AdaptiveComponentOverrideApi` | | Cmn |

## Top-level functions summary

| | | |
|-|-|-|

| `State<List<FoldingFeature>>` | `@Composable<br>collectFoldingFeaturesAsState()`<br>Collects the current window folding features from `WindowInfoTracker` in to a `State`. | android |

## Extension properties summary

## Top-level functions

### calculatePosture

android

Artifact: androidx.compose.material3.adaptive:adaptive

View Source

Calculates the `Posture` for a given list of `FoldingFeature` s. This methods converts framework folding info into the Material-opinionated posture info.

### collectFoldingFeaturesAsState

@Composable
fun collectFoldingFeaturesAsState(): State<List<FoldingFeature>>

Collects the current window folding features from `WindowInfoTracker` in to a `State`.

| Returns                       |
|-------------------------------|
| `State<List<FoldingFeature>>` | a `State` of a `FoldingFeature` list. |

### currentWindowAdaptiveInfo

Cmn

Added in 1.2.0-alpha10

@Composable
fun currentWindowAdaptiveInfo(supportLargeAndXLargeWidth: Boolean = false): WindowAdaptiveInfo

Calculates and returns `WindowAdaptiveInfo` of the provided context. It's a convenient function that uses the default `WindowSizeClass` constructor and the default `Posture` calculation functions to retrieve `WindowSizeClass` and `Posture`.

| Parameters                                    |
|-----------------------------------------------|
| `supportLargeAndXLargeWidth: Boolean = false` | `true` to support the large and extra-large window width size classes, which makes the returned `WindowSizeClass` be calculated based on the breakpoints that include large and extra-large widths. |

| Returns              |
|----------------------|
| `WindowAdaptiveInfo` | `WindowAdaptiveInfo` of the provided context |

### currentWindowDpSize

@Composable
fun currentWindowDpSize(): DpSize

Returns and automatically update the current window size in `DpSize`.

| Returns  |
|----------|
| `DpSize` | an `DpSize` that represents the current window size. |

### currentWindowSize

@Composable
fun currentWindowSize(): IntSize

Returns and automatically update the current window size. It's a convenient function of getting `androidx.compose.ui.platform.WindowInfo.containerSize` from `LocalWindowInfo`.

| Returns   |
|-----------|
| `IntSize` | an `IntSize` that represents the current window size. |

## Extension properties

### allHorizontalHingeBounds

Added in 1.0.0

Returns the list of all horizontal hinge bounds.

### allVerticalHingeBounds

Returns the list of all vertical hinge bounds.

### occludingHorizontalHingeBounds

Returns the list of horizontal hinge bounds that are occluding.

### occludingVerticalHingeBounds

Returns the list of vertical hinge bounds that are occluding.

### separatingHorizontalHingeBounds

Returns the list of horizontal hinge bounds that are separating.

### separatingVerticalHingeBounds

Returns the list of vertical hinge bounds that are separating.

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-07-30 UTC.

---

