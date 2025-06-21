---
description: Systematic approach for continuously improving rules for Android (Kotlin) development based on emerging patterns and best practices
globs: ""
alwaysApply: false
---

# Continuous Improvement Guide for Android Development Rules

This guide provides a systematic approach for continuously improving Android development rules based on emerging patterns, best practices, and lessons learned during development.

---

## Rule Improvement Triggers

### When to Create or Update Rules

**Create New Rules When:**
* A new Jetpack library (e.g., Compose, Room), Kotlin feature (e.g., Flows), or architectural pattern is used in 3+ files or modules.
* Common bugs like memory leaks, lifecycle issues, or Application Not Responding (ANR) errors could be prevented by a rule.
* Code reviews repeatedly mention the same feedback, such as improper `ViewModel` scoping or misuse of `Context`.
* New security or performance patterns emerge for Android.
* A complex task, like implementing an offline-first feature, requires a consistent approach.

**Update Existing Rules When:**
* Better examples exist in the codebase or are published by Google.
* Additional edge cases related to different Android versions or device form factors are discovered.
* Implementation details, such as a dependency's API, have changed.
* User feedback indicates a rule is confusing or hard to follow.

---

## Analysis Process

### 1. Pattern Recognition

Monitor your codebase for repeated patterns specific to Android and Kotlin.

```kotlin
// Example: If you see this state declaration pattern repeatedly:
private val _uiState = MutableStateFlow(MyScreenState())
val uiState: StateFlow<MyScreenState> = _uiState.asStateFlow()

// Consider documenting:
// - Standard way to expose state from a ViewModel.
// - Naming conventions for private mutable state and public immutable state.
// - When to use StateFlow vs. SharedFlow.
```

### 2. Error Pattern Analysis

Track common Android mistakes and their solutions.

```yaml
Common Error: "Application Not Responding (ANR)"
Root Cause: Performing long-running database or network operations on the main thread.
Solution: Use coroutines with a background dispatcher, e.g., withContext(Dispatchers.IO).
Rule Update: Add guidelines to concurrency rules to always move blocking I/O off the main thread.
```

### 3. Best Practice Evolution

Document emerging best practices in the Android ecosystem.

```markdown
## Before (Old Pattern)
- XML Layouts with `findViewById`
- Callbacks for asynchronous operations
- Manual management of background threads

## After (New Pattern)
- Jetpack Compose for UI
- Kotlin Coroutines with async/await for asynchronous operations
- Structured concurrency with `viewModelScope` or `lifecycleScope`
```

---

## Rule Quality Framework

### Structure Guidelines

Each rule should follow this structure:

```markdown
# Rule Name

## Purpose
Brief description of what this rule achieves.

## When to Apply
- Specific scenarios (e.g., "When creating a new screen").
- Trigger conditions (e.g., "Any ViewModel holding UI state").

## Implementation
### Basic Pattern
```kotlin
// Minimal working example for a ViewModel state holder.
class MyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MyState())
    val uiState: StateFlow<MyState> = _uiState.asStateFlow()
}
```

### Advanced Pattern
```kotlin
// A more complex scenario with dependency injection, SavedStateHandle, and combining flows.
class MyViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository
) : ViewModel() {
    val uiState: StateFlow<MyState> = combine(
        userRepository.getProfile(),
        savedStateHandle.getStateFlow("query", "")
    ) { profile, query ->
        MyState(userProfile = profile, searchQuery = query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MyState()
    )
}
```

## Common Pitfalls
- Known issues (e.g., "Risk of leaking subscriptions if not using `stateIn`").
- How to avoid them.

## References
- Related rules: [five.mdc, modern-android.mdc]
```

### Quality Checklist

Before publishing a rule, ensure it is:

- [ ] **Actionable**: Provides clear, implementable guidance.
- [ ] **Specific**: Avoids vague recommendations.
- [ ] **Tested**: Examples come from working code.
- [ ] **Complete**: Covers common Android edge cases.
- [ ] **Current**: References up-to-date Android and Jetpack libraries.
- [ ] **Linked**: Cross-references related rules and documentation.

---

## Continuous Improvement Workflow

### 1. Collection Phase

**Daily Development**
* Note repeated code patterns in ViewModels, Composables, or Repositories.
* Document solved problems and their solutions.
* Track usage of specific libraries and tools.

**Weekly Review**
* Analyze git commits for patterns.
* Review debugging sessions, especially those using the Profiler or Layout Inspector.
* Check Google Play Console for error logs and ANRs.

### 2. Analysis Phase

**Pattern Extraction**
```python
# Pseudo-code for pattern analysis
patterns = analyze_codebase()
for pattern in patterns:
    if pattern.frequency >= 3 and not documented(pattern):
        create_rule_draft(pattern)
```

**Impact Assessment**
* How many files or modules would benefit?
* What errors (crashes, ANRs) would be prevented?
* How much development or debugging time would be saved?

### 3. Documentation Phase

**Rule Creation Process**
1.  Draft the initial rule with Kotlin examples.
2.  Test the rule's guidance on existing code.
3.  Get feedback from the team.
4.  Refine and publish.
5.  Monitor the rule's effectiveness and adoption.

### 4. Maintenance Phase

**Regular Updates**
* **Monthly**: Review rule usage and feedback.
* **Quarterly**: Major updates for new Android releases or Jetpack libraries.
* **Annually**: Review and deprecate outdated rules.

---

## Meta-Rules for Rule Management

### Rule Versioning

```yaml
rule_version: 1.2.0
last_updated: 2025-06-16
breaking_changes:
  - v1.0.0: Initial release
  - v1.1.0: Added error handling for coroutines
  - v1.2.0: Updated examples for Jetpack Compose 1.7.0
```

### Deprecation Process

```markdown
## DEPRECATED: Old AsyncTask Pattern
**Status**: Deprecated as of v2.0.0
**Migration**: See [coroutines-and-flows.md]
**Removal Date**: 2025-12-31

[Original content preserved for reference]
```

### Rule Metrics

Track rule effectiveness to justify the time spent on maintaining them.

```yaml
metrics:
  usage_count: 45
  error_prevention: 12 bugs avoided (based on Play Console data)
  time_saved: ~3 hours/week
  user_feedback: 4.2/5
```

---

## Integration with Development Workflow

### Git Hooks
```bash
#!/bin/bash
# pre-commit hook to check for Kotlin linting issues
# Assumes ktlint is installed system-wide

echo "Running ktlint..."
ktlint --reporter=plain?color=true "src/**/*.kt"
```

### Static Analysis
```bash
# To run Android's built-in lint checker on the debug build variant:
./gradlew lintDebug
```

### IDE Integration
* **Code Style:** Use an `.editorconfig` file in the project root to ensure consistent formatting across Android Studio instances.
* **Live Templates:** Create shared Android Studio live templates for common patterns (e.g., a new Composable function, a ViewModel boilerplate) to speed up development.
* **Inspections:** Configure and share a project-level Inspection Profile (`.idea/inspectionProfiles/`) to highlight rule violations directly in the editor.

---

## New Section: Geometric Interaction Patterns

### Coordinate Transformation Utility

When working with transformed coordinate systems in Jetpack Compose:

```kotlin
object CoordinateTransformUtils {
    fun transformScreenToContent(
        screenPos: Offset,
        zoom: Float,
        offset: Offset
    ): Offset {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        return if (clampedZoom != 1f || offset != Offset.Zero) {
            Offset(
                (screenPos.x - offset.x) / clampedZoom,
                (screenPos.y - offset.y) / clampedZoom
            )
        } else {
            screenPos
        }
    }

    fun transformContentToScreen(
        contentPos: Offset,
        zoom: Float,
        offset: Offset
    ): Offset {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        return Offset(
            contentPos.x * clampedZoom + offset.x,
            contentPos.y * clampedZoom + offset.y
        )
    }
}
```

### Geometry Reader with Debug Mode

For complex hit testing scenarios:

```kotlin
class GeometryReader {
    private val elementBounds = mutableMapOf<Element, Rect>()
    var debugMode = false

    fun getBoundsFor(element: Element): Rect? = elementBounds[element]

    fun findElementAt(position: Offset): Element? {
        return elementBounds.entries.firstOrNull { (element, bounds) ->
            bounds.contains(position) && preciseContains(element, position)
        }?.key
    }

    fun clear() { elementBounds.clear() }

    fun debugDrawBounds(drawScope: DrawScope) {
        if (!debugMode) return
        elementBounds.forEach { (element, bounds) ->
            drawScope.drawRect(
                color = Color.Magenta.copy(alpha = 0.3f),
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(width = 2f)
            )
        }
    }
}
```

### Visual Feedback Patterns

For providing clear visual feedback:

```kotlin
// For rectangular elements:
drawRect(highlightColor, bounds, style = Stroke(width = 4f))

// For complex shapes:
drawPath(highlightColor, elementShape, style = Fill)
drawPath(borderColor, elementShape, style = Stroke(width = 2f))
```

---

## Summary

Write Android code that looks and feels like modern Android. The framework and tools have matured significantly—**trust Jetpack Compose, ViewModels, and Coroutines**. Focus on solving user problems, not on implementing complex architectural patterns from other eras.
