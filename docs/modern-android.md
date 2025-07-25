## Modern Kotlin Android Development

### Core Philosophy

* **Jetpack Compose is the default UI paradigm for modern Android**; embrace its declarative nature.
* Avoid legacy XML and imperative UI patterns.
* Focus on **simplicity, clarity, and a unidirectional data flow**.
* Let Compose handle the complexity of UI updates; don't fight the framework.

***

### Architecture Guidelines

#### 1. Embrace Native State Management

Use Jetpack Compose's built-in state APIs and `ViewModel` for a clear architecture:
* `remember { mutableStateOf(...) }` for local, ephemeral state that is internal to a Composable.
* **State Hoisting** for sharing state and creating stateless, reusable Composables.
* **`ViewModel` and `StateFlow`** for providing and managing business logic and screen-level state.
* **`CompositionLocalProvider`** for implicitly passing data down the Composable tree.

#### 2. Unidirectional Data Flow (UDF) Principles

* **State flows down** from your `ViewModel` to your Composables.
* **Events flow up** from your Composables to your `ViewModel`.
* Keep state as close to where it's read as possible.
* State should be owned by a single source of truth, typically a `ViewModel`.

#### 3. Modern Async Patterns

* Use **Kotlin Coroutines** and `suspend` functions as the default for asynchronous operations.
* Leverage the **`LaunchedEffect`** composable for calling `suspend` functions in a lifecycle-aware manner.
* Use `viewModelScope` to launch coroutines that are tied to the lifecycle of a `ViewModel`.
* Handle errors gracefully within coroutines using `try/catch` blocks.

#### 4. View Composition

* Build UI with **small, focused, and reusable Composables**.
* Extract new Composables from existing ones as your functions grow.
* Use **Modifiers** to encapsulate styling and behavior.
* Prefer **composition over inheritance**.

#### 5. Code Organization

* **Organize by feature**, not by layer (avoid `views/`, `viewmodels/`, `models/` folders at the top level).
* Keep related code, like a screen and its `ViewModel`, together.
* Use **Kotlin extension functions** to organize utility and helper code.
* Follow official Kotlin naming conventions consistently.

***

### Implementation Patterns

#### Simple State Example
```kotlin
@Composable
fun CounterView() {
    var count by remember { mutableStateOf(0) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Count: $count")
        Button(onClick = { count++ }) {
            Text("Increment")
        }
    }
}
```

#### Shared State with ViewModel & StateFlow
```kotlin
// The source of truth for screen state
class UserSessionViewModel : ViewModel() {
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun signIn() {
        _isAuthenticated.value = true
    }
}

// The UI observes state from the ViewModel
@Composable
fun MyApp(viewModel: UserSessionViewModel = viewModel()) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()

    if (isAuthenticated) {
        HomeScreen()
    } else {
        LoginScreen(onSignIn = { viewModel.signIn() })
    }
}
```

#### Async Data Loading
```kotlin
// UI State representation
data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

// ViewModel handles logic and state exposure
class ProfileViewModel(private val profileRepository: ProfileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = profileRepository.fetch()
                _uiState.update { it.copy(isLoading = false, profile = profile) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load profile.") }
            }
        }
    }
}

// The Composable observes state and triggers the data load
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Box(contentAlignment = Alignment.Center) {
        when {
            uiState.isLoading -> ProgressIndicator()
            uiState.profile != null -> ProfileContent(profile = uiState.profile!!)
            uiState.error != null -> ErrorView(error = uiState.error!!)
        }
    }

    // Triggers the data load when the Composable enters the Composition
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }
}
```

***

### Best Practices

#### DO:
* Write self-contained, stateless, and reusable Composables.
* Use `ViewModel` to manage UI logic and hold screen-level state.
* Test business logic in `ViewModel`s separately from the UI.
* Handle loading and error states explicitly in your UI state.
* Keep Composables focused on **presentation logic** (displaying state and sending events).
* Use Kotlin's type-safety and null-safety features.

#### DON'T:
* Create a `ViewModel` for every tiny component.
* Pass `ViewModel` instances to deeply nested Composables.
* Introduce abstraction layers without a clear benefit.
* Use legacy async patterns like `AsyncTask`.
* Fight the recomposition mechanism.
* Put business logic directly inside your Composables.

***

### Testing Strategy

* **Unit test** `ViewModel`s, business logic, and data transformations.
* Use **UI tests with the Compose Test Rule** for visual and interaction testing.
* Test `StateFlow` updates in your `ViewModel` tests.
* Keep tests simple and focused on a single responsibility.
* Don't sacrifice code clarity purely for testability.

***

### Modern Kotlin Features

* Use **Kotlin Coroutines & Flow** for all asynchronous programming.
* Utilize **Data Classes** for immutable UI state.
* Leverage **Sealed Classes** to model exclusive states (e.g., loading, success, error).
* Use **extension functions** to add utility behaviors.
* Embrace **delegation** and **composition** over class inheritance.

***

### New Section: Geometric Interaction Patterns

#### Coordinate Transformation Utility

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

#### Geometry Reader with Debug Mode

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

#### Visual Feedback Patterns

For providing clear visual feedback:

```kotlin
// For rectangular elements:
drawRect(highlightColor, bounds, style = Stroke(width = 4f))

// For complex shapes:
drawPath(highlightColor, elementShape, style = Fill)
drawPath(borderColor, elementShape, style = Stroke(width = 2f))
```

***

### Summary

Write Android code that looks and feels like modern Android. The framework and tools have matured significantly—**trust Jetpack Compose, ViewModels, and Coroutines**. Focus on solving user problems, not on implementing complex architectural patterns from other eras.
