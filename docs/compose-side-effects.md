Side-effects in Compose

- On this page
- State and effect use cases
- LaunchedEffect: run suspend functions in the scope of a composable
- rememberCoroutineScope: obtain a composition-aware scope to launch a coroutine outside a composable
- rememberUpdatedState: reference a value in an effect that shouldn't restart if the value changes
- DisposableEffect: effects that require cleanup
- SideEffect: publish Compose state to non-Compose code
- produceState: convert non-Compose state into Compose state
- derivedStateOf: convert one or multiple state objects into another state
- snapshotFlow: convert Compose's State into Flows
- Restarting effects
- Constants as keys

A **side-effect** is a change to the state of the app that happens outside the
scope of a composable function.
Due to composables' lifecycle and properties such as unpredictable
recompositions, executing recompositions of composables in different orders, or
recompositions that can be discarded, composables should ideally be side-effect\\
free.

However, sometimes side-effects are necessary, for example, to trigger a one-off
event such as showing a snackbar or navigate to another screen given a certain
state condition. These actions should be called from a controlled
environment that is aware of the lifecycle of the composable. In this page,
you'll learn about the different side-effect APIs Jetpack Compose offers.

## State and effect use cases

As covered in the Thinking in Compose
documentation, composables should be side-effect free. When you need to make
changes to the state of the app (as described in the Managing\\
state documentation doc), **you should use the Effect**
**APIs so that those side effects are executed in a predictable manner**.

Due to the different possibilities effects open up in Compose, they can be
easily overused. Make sure that the work you do in them is UI related and
doesn't break **unidirectional data flow** as explained in the Managing state\\
documentation.

### `LaunchedEffect`: run suspend functions in the scope of a composable

To perform work over the life of a composable and have the ability to call
suspend functions, use the
`LaunchedEffect`
composable. When `LaunchedEffect` enters the Composition, it launches a
coroutine with the block of code passed as a parameter. The coroutine will be
cancelled if `LaunchedEffect` leaves the composition. If `LaunchedEffect` is
recomposed with different keys (see the Restarting\\
Effects section below), the existing coroutine will be
cancelled and the new suspend function will be launched in a new coroutine.

For example, here is an animation that pulses the alpha value with a
configurable delay:

// Allow the pulse rate to be configured, so it can be sped up if the user is running
// out of time
var pulseRateMs by remember { mutableStateOf(3000L) }
val alpha = remember { Animatable(1f) }
LaunchedEffect(pulseRateMs) { // Restart the effect when the pulse rate changes
while (isActive) {
delay(pulseRateMs) // Pulse the alpha every pulseRateMs to alert the user
alpha.animateTo(0f)
alpha.animateTo(1f)
}
}
SideEffectsSnippets.kt

In the code above, the animation uses the suspending function
`delay`
to wait the set amount of time. Then, it sequentially animates the alpha
to zero and back again using
`animateTo`.
This will repeat for the life of the composable.

### `rememberCoroutineScope`: obtain a composition-aware scope to launch a coroutine outside a composable

As `LaunchedEffect` is a composable function, it can only be used inside other
composable functions. In order to launch a coroutine outside of a composable,
but scoped so that it will be automatically canceled once it leaves the
composition, use
`rememberCoroutineScope`.
Also use `rememberCoroutineScope` whenever you need to control the lifecycle of
one or more coroutines manually, for example, cancelling an animation when a
user event happens.

`rememberCoroutineScope` is a composable function that returns a
`CoroutineScope` bound to the point of the Composition where it's called. The
scope will be cancelled when the call leaves the Composition.

Following the previous example, you could use this code to show a `Snackbar`
when the user taps on a `Button`:

@Composable
fun MoviesScreen(snackbarHostState: SnackbarHostState) {

// Creates a CoroutineScope bound to the MoviesScreen's lifecycle
val scope = rememberCoroutineScope()

Scaffold(
snackbarHost = {
SnackbarHost(hostState = snackbarHostState)
}

Button(
onClick = {
// Create a new coroutine in the event handler to show a snackbar
scope.launch {
snackbarHostState.showSnackbar("Something happened!")
}
}
) {
Text("Press me")
}
}
}
}
SideEffectsSnippets.kt

### `rememberUpdatedState`: reference a value in an effect that shouldn't restart if the value changes

`LaunchedEffect` restarts when one of the key parameters changes. However, in
some situations you might want to capture a value in your effect that, if it
changes, you do not want the effect to restart. In order to do this, it is
required to use `rememberUpdatedState` to create a reference to this value which
can be captured and updated. This approach is helpful for effects that contain
long-lived operations that may be expensive or prohibitive to recreate and
restart.

For example, suppose your app has a `LandingScreen` that disappears after some
time. Even if `LandingScreen` is recomposed, the effect that waits for some time
and notifies that the time passed shouldn't be restarted:

@Composable

// This will always refer to the latest onTimeout function that
// LandingScreen was recomposed with
val currentOnTimeout by rememberUpdatedState(onTimeout)

// Create an effect that matches the lifecycle of LandingScreen.
// If LandingScreen recomposes, the delay shouldn't start again.
LaunchedEffect(true) {
delay(SplashWaitTimeMillis)
currentOnTimeout()
}

/* Landing screen content */
}
SideEffectsSnippets.kt

To create an effect that matches the lifecycle of the call site, a
never-changing constant like `Unit` or `true` is passed as a parameter. In the
code above, `LaunchedEffect(true)` is used. To make sure that the `onTimeout`
lambda _always_ contains the latest value that `LandingScreen` was recomposed
with, `onTimeout` needs to be wrapped with the `rememberUpdatedState` function.
The returned `State`, `currentOnTimeout` in the code, should be used in the
effect.

### `DisposableEffect`: effects that require cleanup

For side effects that need to be _cleaned up_ after the keys change or if the
composable leaves the Composition, use
`DisposableEffect`.
If the `DisposableEffect` keys change, the composable needs to _dispose_ (do
the cleanup for) its current effect, and reset by calling the effect again.

As an example, you might want to send analytics events based on
`Lifecycle` events
by using a
`LifecycleObserver`.
To listen for those events in Compose, use a `DisposableEffect` to register and
unregister the observer when needed.

@Composable
fun HomeScreen(
lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,

) {
// Safely update the current lambdas when a new one is provided
val currentOnStart by rememberUpdatedState(onStart)
val currentOnStop by rememberUpdatedState(onStop)

// If `lifecycleOwner` changes, dispose and reset the effect
DisposableEffect(lifecycleOwner) {
// Create an observer that triggers our remembered callbacks
// for sending analytics events

currentOnStart()
} else if (event == Lifecycle.Event.ON_STOP) {
currentOnStop()
}
}

// Add the observer to the lifecycle
lifecycleOwner.lifecycle.addObserver(observer)

// When the effect leaves the Composition, remove the observer
onDispose {
lifecycleOwner.lifecycle.removeObserver(observer)
}
}

/* Home screen content */
}
SideEffectsSnippets.kt

In the code above, the effect will add the `observer` to the
`lifecycleOwner`. If `lifecycleOwner` changes, the effect is disposed and
restarted with the new `lifecycleOwner`.

A `DisposableEffect` must include an `onDispose` clause as the final statement
in its block of code. Otherwise, the IDE displays a build-time error.

### `SideEffect`: publish Compose state to non-Compose code

To share Compose state with objects not managed by compose, use the
`SideEffect`
composable. Using a `SideEffect` guarantees that the effect executes after every
successful recomposition. On the other hand, it is incorrect to
perform an effect before a successful recomposition is guaranteed, which is the
case when writing the effect directly in a composable.

For example, your analytics library might allow you to segment your user
population by attaching custom metadata ("user properties" in this example)
to all subsequent analytics events. To communicate the user type of the
current user to your analytics library, use `SideEffect` to update its value.

@Composable
fun rememberFirebaseAnalytics(user: User): FirebaseAnalytics {
val analytics: FirebaseAnalytics = remember {
FirebaseAnalytics()
}

// On every successful composition, update FirebaseAnalytics with
// the userType from the current User, ensuring that future analytics
// events have this metadata attached
SideEffect {
analytics.setUserProperty("userType", user.userType)
}
return analytics
}
SideEffectsSnippets.kt

### `produceState`: convert non-Compose state into Compose state

`produceState`
launches a coroutine scoped to the Composition that can push values into a
returned `State`. Use it to
convert non-Compose state into Compose state, for example bringing external
subscription-driven state such as `Flow`, `LiveData`, or `RxJava` into the
Composition.

The producer is launched when `produceState` enters the Composition, and will be
cancelled when it leaves the Composition. The returned `State` conflates;
setting the same value won't trigger a recomposition.

Even though `produceState` creates a coroutine, it can also be used to observe
non-suspending sources of data. To remove the subscription to that source, use
the
`awaitDispose`
function.

The following example shows how to use `produceState` to load an image from the
network. The `loadNetworkImage` composable function returns a `State` that can
be used in other composables.

@Composable
fun loadNetworkImage(
url: String,
imageRepository: ImageRepository = ImageRepository()
): State<Result<Image>> {

// If either `url` or `imageRepository` changes, the running producer
// will cancel and will be re-launched with the new inputs.
return produceState<Result<Image>>(initialValue = Result.Loading, url, imageRepository) {
// In a coroutine, can make suspend calls
val image = imageRepository.load(url)

// Update State with either an Error or Success result.
// This will trigger a recomposition where this State is read
value = if (image == null) {
Result.Error
} else {
Result.Success(image)
}
}
}
SideEffectsSnippets.kt

### `derivedStateOf`: convert one or multiple state objects into another state

In Compose, recomposition occurs
each time an observed state object or composable input changes. A state object
or input may be changing more often than the UI actually needs to update,
leading to unnecessary recomposition.

You should use the `derivedStateOf`
function when your inputs to a composable are changing more often than you need
to recompose. This often occurs when something is frequently changing, such as
a scroll position, but the composable only needs to react to it once it crosses
a certain threshold. `derivedStateOf` creates a new Compose state object you
can observe that only updates as much as you need. In this way, it acts
similarly to the Kotlin Flows
`distinctUntilChanged()`
operator.

#### Correct usage

The following snippet shows an appropriate use case for `derivedStateOf`:

@Composable
// When the messages parameter changes, the MessageList
// composable recomposes. derivedStateOf does not
// affect this recomposition.

Box {
val listState = rememberLazyListState()

LazyColumn(state = listState) {
// ...
}

// Show the button if the first visible item is past
// the first item. We use a remembered derived state to
// minimize unnecessary compositions
val showButton by remember {
derivedStateOf {

}
}

AnimatedVisibility(visible = showButton) {
ScrollToTopButton()
}
}
}
SideEffectsSnippets.kt

In this snippet, `firstVisibleItemIndex` changes any time the first visible item
changes. As you scroll, the value becomes `0`, `1`, `2`, `3`, `4`, `5`, etc.
However, recomposition only needs to occur if the value is greater than `0`.
This mismatch in update frequency means that this is a good use case for
`derivedStateOf`.

#### Incorrect usage

A common mistake is to assume that, when you combine two Compose state objects,
you should use `derivedStateOf` because you are "deriving state". However, this
is purely overhead and not required, as shown in the following snippet:

// DO NOT USE. Incorrect usage of derivedStateOf.
var firstName by remember { mutableStateOf("") }
var lastName by remember { mutableStateOf("") }

val fullNameBad by remember { derivedStateOf { "$firstName $lastName" } } // This is bad!!!
val fullNameCorrect = "$firstName $lastName" // This is correct
SideEffectsSnippets.kt

In this snippet, `fullName` needs to update just as often as `firstName` and
`lastName`. Therefore, no excess recomposition is occurring, and using
`derivedStateOf` is not necessary.

### `snapshotFlow`: convert Compose's State into Flows

Use `snapshotFlow`

objects into a cold Flow. `snapshotFlow` runs its block when collected and emits
the result of the `State` objects read in it. When one of the `State` objects
read inside the `snapshotFlow` block mutates, the Flow will emit the new value
to its collector if the new value is not equal to
the previous emitted value (this behavior is similar to that of
`Flow.distinctUntilChanged`).

The following example shows a side effect that records when the user scrolls
past the first item in a list to analytics:

val listState = rememberLazyListState()

LazyColumn(state = listState) {
// ...
}

LaunchedEffect(listState) {
snapshotFlow { listState.firstVisibleItemIndex }

.distinctUntilChanged()
.filter { it == true }
.collect {
MyAnalyticsService.sendScrolledPastFirstItemEvent()
}
}

In the code above, `listState.firstVisibleItemIndex` is converted to a Flow that
can benefit from the power of Flow's operators.

## Restarting effects

Some effects in Compose, like `LaunchedEffect`, `produceState`, or
`DisposableEffect`, take a variable number of arguments, keys, that are used to
cancel the running effect and start a new one with the new keys.

The typical form for these APIs is:

EffectName(restartIfThisKeyChanges, orThisKey, orThisKey, ...) { block }

Due to the subtleties of this behavior, problems can occur if the parameters
used to restart the effect are not the right ones:

- Restarting effects less than they should could cause bugs in your app.
- Restarting effects more than they should could be inefficient.

As a rule of thumb, mutable and immutable variables used in the effect block of
code should be added as parameters to the effect composable. Apart from those,
more parameters can be added to force restarting the effect. If the change of
a variable shouldn't cause the effect to restart, the variable should be wrapped
in `rememberUpdatedState`. If the variable never
changes because it's wrapped in a `remember` with no keys, you don't need to
pass the variable as a key to the effect.

In the `DisposableEffect` code shown above, the effect takes as a parameter the
`lifecycleOwner` used in its block, because any change to them should cause the
effect to restart.

) {
// These values never change in Composition
val currentOnStart by rememberUpdatedState(onStart)
val currentOnStop by rememberUpdatedState(onStop)

DisposableEffect(lifecycleOwner) {

}

lifecycleOwner.lifecycle.addObserver(observer)
onDispose {
lifecycleOwner.lifecycle.removeObserver(observer)
}
}
}

`currentOnStart` and `currentOnStop` are not needed as `DisposableEffect`
keys, because their value never change in Composition due to the usage of
`rememberUpdatedState`. If you don't pass `lifecycleOwner` as a parameter and
it changes, `HomeScreen` recomposes, but the `DisposableEffect` isn't disposed
of and restarted. That causes problems because the wrong `lifecycleOwner` is
used from that point onward.

### Constants as keys

You can use a constant like `true` as an effect key to
make it **follow the lifecycle of the call site**. There are valid use cases for
it, like the `LaunchedEffect` example shown above. However, before doing that,
think twice and make sure that's what you need.
---

