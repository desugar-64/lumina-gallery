
### Animations in Compose

Animations are essential in a modern mobile app in order to realize a smooth and understandable user experience.

Quick guide

![](https://developer.android.com/develop/ui/compose/animation/composables-modifiers#animatedvisibility)

### Animate appearance and disappearance

The `AnimatedVisibility` composable allows you to hide or show content easily.

Learn more

![](https://developer.android.com/develop/ui/compose/animation/composables-modifiers#animateContentSize)

### Animate content size changes

Use `animateContentSize()` to achieve automatic size change animations.

![](https://developer.android.com/develop/ui/compose/animation/composables-modifiers#animatedcontent)

### Animate between different composables

Use `AnimatedContent` to animate between composables that have different content.

![](https://developer.android.com/develop/ui/compose/animation/value-based#animate-as-state)

### Animate a single value

Use `animate*AsState` functions to animate an individual property, such as opacity.

![](https://developer.android.com/develop/ui/compose/animation/quick-guide#concurrent-animations)

### Animate multiple values together

Use `Transition` to animate multiple values at once.

![](https://developer.android.com/develop/ui/compose/animation/value-based#rememberinfinitetransition)

### Animate properties indefinitely

Use `InfiniteTransition` to animate properties continuously.

### Customize your animations

Learn how to customize your animations duration, easing curve and spring configuration.

Customize

![](https://developer.android.com/develop/ui/compose/animation/testing)

### Test animations

Learn how to write tests for your animations.

Testing

![](https://developer.android.com/develop/ui/compose/animation/tooling)

### Android Studio Tooling


# Quick guide to Animations in Compose bookmark\_border Stay organized with collections Save and categorize content based on your preferences.

Practical magic with animations in Jetpack Compose - YouTube

Compose has many built-in animation mechanisms and it can be overwhelming to
know which one to choose. Below is a list of common animation use cases. For
more detailed information about the full set of different API options available
to you, read the full Compose Animation documentation.

## Animate common composable properties

Compose provides convenient APIs that allow you to solve for many common
animation use cases. This section demonstrates how you can animate common
properties of a composable.

### Animate appearing / disappearing

Use `AnimatedVisibility` to hide or show a Composable. Children inside
`AnimatedVisibility` can use `Modifier.animateEnterExit()` for their own enter
or exit transition.

var visible by remember {
mutableStateOf(true)
}
// Animated visibility will eventually remove the item from the composition once the animation has finished.
AnimatedVisibility(visible) {
// your composable here
// ...
}
AnimationQuickGuide.kt

The enter and exit parameters of `AnimatedVisibility` allow you to configure how
a composable behaves when it appears and disappears. Read the full\\
documentation for more information.

Another option for animating the visibility of a composable is to animate the
alpha over time using `animateFloatAsState`:

var visible by remember {
mutableStateOf(true)
}
val animatedAlpha by animateFloatAsState(
targetValue = if (visible) 1.0f else 0f,
label = "alpha"
)
Box(
modifier = Modifier
.size(200.dp)
.graphicsLayer {
alpha = animatedAlpha
}
.clip(RoundedCornerShape(8.dp))
.background(colorGreen)
.align(Alignment.TopCenter)
) {
}
AnimationQuickGuide.kt

However, changing the alpha comes with the caveat that the composable **remains**
**in the composition** and continues to occupy the space it's laid out in. This
could cause screen readers and other accessibility mechanisms to still consider
the item on screen. On the other hand, `AnimatedVisibility` eventually removes
the item from the composition.

### Animate background color

val animatedColor by animateColorAsState(
if (animateBackgroundColor) colorGreen else colorBlue,
label = "color"
)
Column(
modifier = Modifier.drawBehind {
drawRect(animatedColor)
}
) {
// your composable here
}
AnimationQuickGuide.kt

This option is more performant than using `Modifier.background()`.
`Modifier.background()` is acceptable for a one-shot color setting, but when
animating a color over time, this could cause more recompositions than
necessary.

For infinitely animating the background color, see repeating an animation\\
section.

### Animate the size of a composable

Compose lets you animate the size of composables in a few different ways. Use
`animateContentSize()` for animations between composable size changes.

For example, if you have a box that contains text which can expand from one to
multiple lines you can use `Modifier.animateContentSize()` to achieve a smoother
transition:

var expanded by remember { mutableStateOf(false) }
Box(
modifier = Modifier
.background(colorBlue)
.animateContentSize()
.height(if (expanded) 400.dp else 200.dp)
.fillMaxWidth()
.clickable(
interactionSource = remember { MutableInteractionSource() },
indication = null
) {
expanded = !expanded
}

) {
}
AnimationQuickGuide.kt

You can also use `AnimatedContent`, with a `SizeTransform` to describe
how size changes should take place.

### Animate position of composable

To animate the position of a composable, use `Modifier.offset{ }` combined with
`animateIntOffsetAsState()`.

var moved by remember { mutableStateOf(false) }
val pxToMove = with(LocalDensity.current) {
100.dp.toPx().roundToInt()
}
val offset by animateIntOffsetAsState(
targetValue = if (moved) {
IntOffset(pxToMove, pxToMove)
} else {
IntOffset.Zero
},
label = "offset"
)

Box(
modifier = Modifier
.offset {
offset
}
.background(colorBlue)
.size(100.dp)
.clickable(
interactionSource = remember { MutableInteractionSource() },
indication = null
) {
moved = !moved
}
)
AnimationQuickGuide.kt

If you want to ensure that composables are not drawn over or under other
composables when animating position or size, use `Modifier.layout{ }`. This
modifier propagates size and position changes to the parent, which then affects
other children.

For example, if you are moving a `Box` within a `Column` and the other children
need to move when the `Box` moves, include the offset information with
`Modifier.layout{ }` as follows:

var toggled by remember {
mutableStateOf(false)
}
val interactionSource = remember {
MutableInteractionSource()
}
Column(
modifier = Modifier
.padding(16.dp)
.fillMaxSize()
.clickable(indication = null, interactionSource = interactionSource) {
toggled = !toggled
}
) {
val offsetTarget = if (toggled) {
IntOffset(150, 150)
} else {
IntOffset.Zero
}
val offset = animateIntOffsetAsState(
targetValue = offsetTarget, label = "offset"
)
Box(
modifier = Modifier
.size(100.dp)
.background(colorBlue)
)
Box(
modifier = Modifier

val placeable = measurable.measure(constraints)
layout(placeable.width + offsetValue.x, placeable.height + offsetValue.y) {
placeable.placeRelative(offsetValue)
}
}
.size(100.dp)
.background(colorGreen)
)
Box(
modifier = Modifier
.size(100.dp)
.background(colorBlue)
)
}
AnimationQuickGuide.kt

### Animate padding of a composable

To animate the padding of a composable, use `animateDpAsState` combined with
`Modifier.padding()`:

var toggled by remember {
mutableStateOf(false)
}
val animatedPadding by animateDpAsState(
if (toggled) {
0.dp
} else {
20.dp
},
label = "padding"
)
Box(
modifier = Modifier
.aspectRatio(1f)
.fillMaxSize()
.padding(animatedPadding)
.background(Color(0xff53D9A1))
.clickable(
interactionSource = remember { MutableInteractionSource() },
indication = null
) {
toggled = !toggled
}
)
AnimationQuickGuide.kt

### Animate elevation of a composable

**Figure 8.** Composable's elevation animating on click

To animate the elevation of a composable, use `animateDpAsState` combined with
`Modifier.graphicsLayer{ }`. For once-off elevation changes, use
`Modifier.shadow()`. If you are animating the shadow, using
`Modifier.graphicsLayer{ }` modifier is the more performant option.

val mutableInteractionSource = remember {
MutableInteractionSource()
}
val pressed = mutableInteractionSource.collectIsPressedAsState()
val elevation = animateDpAsState(
targetValue = if (pressed.value) {
32.dp
} else {
8.dp
},
label = "elevation"
)
Box(
modifier = Modifier
.size(100.dp)
.align(Alignment.Center)
.graphicsLayer {
this.shadowElevation = elevation.value.toPx()
}
.clickable(interactionSource = mutableInteractionSource, indication = null) {
}
.background(colorGreen)
) {
}
AnimationQuickGuide.kt

Alternatively, use the `Card` composable, and set the elevation property to
different values per state.

### Animate text scale, translation or rotation

When animating scale, translation, or rotation of text, set the `textMotion`
parameter on `TextStyle` to `TextMotion.Animated`. This ensures smoother
transitions between text animations. Use `Modifier.graphicsLayer{ }` to
translate, rotate or scale the text.

val infiniteTransition = rememberInfiniteTransition(label = "infinite transition")
val scale by infiniteTransition.animateFloat(
initialValue = 1f,
targetValue = 8f,
animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
label = "scale"
)
Box(modifier = Modifier.fillMaxSize()) {
Text(
text = "Hello",
modifier = Modifier
.graphicsLayer {
scaleX = scale
scaleY = scale
transformOrigin = TransformOrigin.Center
}
.align(Alignment.Center),
// Text composable does not take TextMotion as a parameter.
// Provide it via style argument but make sure that we are copying from current theme
style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated)
)
}

AnimationQuickGuide.kt

### Animate text color

To animate text color, use the `color` lambda on the `BasicText` composable:

val infiniteTransition = rememberInfiniteTransition(label = "infinite transition")
val animatedColor by infiniteTransition.animateColor(
initialValue = Color(0xFF60DDAD),
targetValue = Color(0xFF4285F4),
animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
label = "color"
)

BasicText(
text = "Hello Compose",
color = {
animatedColor
},
// ...
)
AnimationQuickGuide.kt

## Switch between different types of content

Use `AnimatedContent` to animate between different composables, if you
just want a standard fade between composables, use `Crossfade`.

var state by remember {
mutableStateOf(UiState.Loading)
}
AnimatedContent(
state,
transitionSpec = {
fadeIn(
animationSpec = tween(3000)
) togetherWith fadeOut(animationSpec = tween(3000))
},
modifier = Modifier.clickable(
interactionSource = remember { MutableInteractionSource() },
indication = null
) {
state = when (state) {

}
},
label = "Animated Content"

LoadingScreen()
}

LoadedScreen()
}

ErrorScreen()
}
}
}
AnimationQuickGuide.kt

`AnimatedContent` can be customized to show many different kinds of enter and
exit transitions. For more information, read the documentation on
`AnimatedContent` or read this blog post on `AnimatedContent`.

## Animate whilst navigating to different destinations

To animate transitions between composables when using the
navigation-compose artifact, specify the `enterTransition` and
`exitTransition` on a composable. You can also set the default animation to be
used for all destinations at the top level `NavHost`:

val navController = rememberNavController()
NavHost(
navController = navController, startDestination = "landing",
enterTransition = { EnterTransition.None },
exitTransition = { ExitTransition.None }
) {
composable("landing") {
ScreenLanding(
// ...
)
}
composable(
"detail/{photoUrl}",
arguments = listOf(navArgument("photoUrl") { type = NavType.StringType }),
enterTransition = {
fadeIn(
animationSpec = tween(
300, easing = LinearEasing
)
) + slideIntoContainer(
animationSpec = tween(300, easing = EaseIn),
towards = AnimatedContentTransitionScope.SlideDirection.Start
)
},
exitTransition = {
fadeOut(
animationSpec = tween(
300, easing = LinearEasing
)
) + slideOutOfContainer(
animationSpec = tween(300, easing = EaseOut),
towards = AnimatedContentTransitionScope.SlideDirection.End
)
}

// ...
)
}
}
AnimationQuickGuide.kt

There are many different kinds of enter and exit transitions that apply
different effects to the incoming and outgoing content, see the
documentation for more.

## Repeat an animation

Use `rememberInfiniteTransition` with an `infiniteRepeatable` `animationSpec` to continuously repeat your animation. Change `RepeatModes` to
specify how it should go back and forth.

Use `finiteRepeatable` to repeat a set number of times.

val infiniteTransition = rememberInfiniteTransition(label = "infinite")
val color by infiniteTransition.animateColor(
initialValue = Color.Green,
targetValue = Color.Blue,
animationSpec = infiniteRepeatable(
animation = tween(1000, easing = LinearEasing),
repeatMode = RepeatMode.Reverse
),
label = "color"
)
Column(
modifier = Modifier.drawBehind {
drawRect(color)
}
) {
// your composable here
}
AnimationQuickGuide.kt

## Start an animation on launch of a composable

`LaunchedEffect` runs when a composable enters the composition. It starts
an animation on launch of a composable, you can use this to drive the animation
state change. Using `Animatable` with the `animateTo` method to start the
animation on launch:

val alphaAnimation = remember {
Animatable(0f)
}
LaunchedEffect(Unit) {
alphaAnimation.animateTo(1f)
}
Box(
modifier = Modifier.graphicsLayer {
alpha = alphaAnimation.value
}
)
AnimationQuickGuide.kt

## Create sequential animations

Use the `Animatable` coroutine APIs to perform sequential or concurrent
animations. Calling `animateTo` on the `Animatable` one after the other causes
each animation to wait for the previous animations to finish before proceeding .
This is because it is a suspend function.

val alphaAnimation = remember { Animatable(0f) }
val yAnimation = remember { Animatable(0f) }

LaunchedEffect("animationKey") {
alphaAnimation.animateTo(1f)
yAnimation.animateTo(100f)
yAnimation.animateTo(500f, animationSpec = tween(100))
}
AnimationQuickGuide.kt

## Create concurrent animations

Use the coroutine APIs ( `Animatable#animateTo()` or `animate`())), or
the `Transition` API to achieve concurrent animations. If you use multiple
launch functions in a coroutine context, it launches the animations at the same
time:

LaunchedEffect("animationKey") {
launch {
alphaAnimation.animateTo(1f)
}
launch {
yAnimation.animateTo(100f)
}
}
AnimationQuickGuide.kt

You could use the `updateTransition` API to use the same state to drive
many different property animations at the same time. The example below animates
two properties controlled by a state change, `rect` and `borderWidth`:

var currentState by remember { mutableStateOf(BoxState.Collapsed) }
val transition = updateTransition(currentState, label = "transition")

}
}

}
}
AnimationQuickGuide.kt

## Optimize animation performance

Animations in Compose can cause performance problems. This is due to the nature
of what an animation is: moving or changing pixels on screen quickly,
frame-by-frame to create the illusion of movement.

Consider the different phases of Compose: composition, layout and draw. If
your animation changes the layout phase, it requires all affected composables to
relayout and redraw. If your animation occurs in the draw phase, it is by
default be more performant than if you were to run the animation in the layout
phase, as it would have less work to do overall.

To ensure your app does as little as possible while animating, choose the lambda
version of a `Modifier` where possible. This skips recomposition and performs
the animation outside of the composition phase, otherwise use
`Modifier.graphicsLayer{ }`, as this modifier always runs in the draw
phase. For more information on this, see the deferring reads section in
the performance documentation.

## Change animation timing

Compose by default uses **spring** animations for most animations. Springs, or
physics-based animations, feel more natural. They are also interruptible as
they take into account the object's current velocity, instead of a fixed time.
If you want to override the default, all the animation APIs demonstrated above
have the ability to set an `animationSpec` to customize how an animation runs,
whether you'd like it to execute over a certain duration or be more bouncy.

The following is a summary of the different `animationSpec` options:

- `spring`: Physics-based animation, the default for all animations. You
can change the stiffness or dampingRatio to achieve a different animation
look and feel.
- `tween` (short for **between**): Duration-based animation, animates
between two values with an `Easing` function.
- `keyframes`: Spec for specifying values at certain key points in an
animation.
- `repeatable`: Duration-based spec that runs a certain number of times,
specified by `RepeatMode`.
- `infiniteRepeatable`: Duration-based spec that runs forever.
- `snap`: Instantly snaps to the end value without any animation.

Read the full documentation for more information about animationSpecs.

- Core areas
- UI

- On this page
- Built-in animated composables
- Animate appearance and disappearance with AnimatedVisibility
- Animate based on target state with AnimatedContent
- Animate between two layouts with Crossfade
- Built-in animation modifiers
- Animate composable size changes with animateContentSize
- List item animations

Recommended for you

- Value-based animations
Updated Jun 10, 2025

- Animations in Compose
Updated Jul 1, 2024

- Animation tooling support
Updated Jun 10, 2025

- Android Developers
- Develop
- Core areas
- UI
- Docs

Was this helpful?

# Animation modifiers and composables bookmark\_borderbookmark Stay organized with collections Save and categorize content based on your preferences.

Compose comes with built-in composables and modifiers for handling common animation use cases.

## Built-in animated composables

### Animate appearance and disappearance with `AnimatedVisibility`

The
`AnimatedVisibility`
composable animates the appearance and disappearance of its content.

var visible by remember {
mutableStateOf(true)
}
// Animated visibility will eventually remove the item from the composition once the animation has finished.
AnimatedVisibility(visible) {
// your composable here
// ...
}
AnimationQuickGuide.kt

By default, the content appears by fading in and expanding, and it disappears by
fading out and shrinking. The transition can be customized by specifying
`EnterTransition`
and
`ExitTransition`.

var visible by remember { mutableStateOf(true) }
val density = LocalDensity.current
AnimatedVisibility(
visible = visible,
enter = slideInVertically {
// Slide in from 40 dp from the top.
with(density) { -40.dp.roundToPx() }
} + expandVertically(
// Expand from the top.
expandFrom = Alignment.Top
) + fadeIn(
// Fade in with the initial alpha of 0.3f.
initialAlpha = 0.3f
),
exit = slideOutVertically() + shrinkVertically() + fadeOut()
) {
Text(
"Hello",
Modifier
.fillMaxWidth()
.height(200.dp)
)
}
AnimationSnippets.kt

As you can see in the example above, you can combine multiple `EnterTransition`
or `ExitTransition` objects with a `+` operator, and each accepts optional
parameters to customize its behavior. See the references for more information.

#### `EnterTransition` and `ExitTransition` examples

| EnterTransition | ExitTransition |
| --- | --- |

`AnimatedVisibility` also offers a variant that takes a
`MutableTransitionState`. This allows you to trigger an animation as soon as the
`AnimatedVisibility` is added to the composition tree. It is also useful for
observing the animation state.

val state = remember {
MutableTransitionState(false).apply {
// Start the animation immediately.
targetState = true
}
}
Column {
AnimatedVisibility(visibleState = state) {
Text(text = "Hello, world!")
}

// Use the MutableTransitionState to know the current animation state
// of the AnimatedVisibility.
Text(
text = when {

}
)
}
AnimationSnippets.kt

#### Animate enter and exit for children

Content within `AnimatedVisibility` (direct or indirect children) can use the
`animateEnterExit`
modifier to specify different animation behavior for each of them. The visual
effect for each of these children is a combination of the animations specified
at the `AnimatedVisibility` composable and the child's own enter and
exit animations.

var visible by remember { mutableStateOf(true) }

AnimatedVisibility(
visible = visible,
enter = fadeIn(),
exit = fadeOut()
) {
// Fade in/out the background and the foreground.
Box(
Modifier
.fillMaxSize()
.background(Color.DarkGray)
) {
Box(
Modifier
.align(Alignment.Center)
.animateEnterExit(
// Slide in/out the inner box.
enter = slideInVertically(),
exit = slideOutVertically()
)
.sizeIn(minWidth = 256.dp, minHeight = 64.dp)
.background(Color.Red)
) {
// Content of the notification…
}
}
}
AnimationSnippets.kt

In some cases, you may want to have `AnimatedVisibility` apply no animations at
all so that children can each have their own distinct animations by
`animateEnterExit`. To achieve this, specify `EnterTransition.None` and
`ExitTransition.None` at the `AnimatedVisibility` composable.

#### Add custom animation

If you want to add custom animation effects beyond the built-in enter and exit
animations, access the underlying `Transition` instance via the `transition`
property inside the content lambda for `AnimatedVisibility`. Any animation
states added to the Transition instance will run simultaneously with the enter
and exit animations of `AnimatedVisibility`. `AnimatedVisibility` waits until
all animations in the `Transition` have finished before removing its content.
For exit animations created independent of `Transition` (such as using
`animate*AsState`), `AnimatedVisibility` would not be able to account for them,
and therefore may remove the content composable before they finish.

AnimatedVisibility(
visible = visible,
enter = fadeIn(),
exit = fadeOut()
) { // this: AnimatedVisibilityScope
// Use AnimatedVisibilityScope#transition to add a custom animation
// to the AnimatedVisibility.

}
Box(
modifier = Modifier
.size(128.dp)
.background(background)
)
}
AnimationSnippets.kt

See updateTransition for the details about `Transition`.

### Animate based on target state with `AnimatedContent`

The `AnimatedContent`
composable animates its content as it changes based on a
target state.

Row {
var count by remember { mutableIntStateOf(0) }
Button(onClick = { count++ }) {
Text("Add")
}
AnimatedContent(
targetState = count,
label = "animated content"

Text(text = "Count: $targetCount")
}
}
AnimationSnippets.kt

Note that you should always use the lambda parameter and reflect it to the
content. The API uses this value as the key to identify the content that's
currently shown.

By default, the initial content fades out and then the target content fades in
(this behavior is called fade through). You
can customize this animation behavior by specifying a `ContentTransform` object to the
`transitionSpec` parameter. You can create `ContentTransform` by combining an
`EnterTransition`
with an `ExitTransition`
using the `with` infix function. You can apply `SizeTransform`
to the `ContentTransform` by attaching it with the
`using` infix function.

AnimatedContent(
targetState = count,
transitionSpec = {
// Compare the incoming number with the previous number.

// If the target number is larger, it slides up and fades in
// while the initial (smaller) number slides up and fades out.

} else {
// If the target number is smaller, it slides down and fades in
// while the initial number slides down and fades out.

}.using(
// Disable clipping since the faded slide-in/out should
// be displayed out of bounds.
SizeTransform(clip = false)
)
}, label = "animated content"

}
AnimationSnippets.kt

`EnterTransition` defines how the target content should appear, and
`ExitTransition` defines how the initial content should disappear. In addition
to all of the `EnterTransition` and `ExitTransition` functions available for
`AnimatedVisibility`, `AnimatedContent` offers `slideIntoContainer`
and `slideOutOfContainer`.
These are convenient alternatives to `slideInHorizontally/Vertically` and
`slideOutHorizontally/Vertically` that calculate the slide distance based on
the sizes of the initial content and the target content of the
`AnimatedContent` content.

`SizeTransform` defines how the
size should animate between the initial and the target contents. You have
access to both the initial size and the target size when you are creating the
animation. `SizeTransform` also controls whether the content should be clipped
to the component size during animations.

var expanded by remember { mutableStateOf(false) }
Surface(
color = MaterialTheme.colorScheme.primary,
onClick = { expanded = !expanded }
) {
AnimatedContent(
targetState = expanded,
transitionSpec = {
fadeIn(animationSpec = tween(150, 150)) togetherWith
fadeOut(animationSpec = tween(150)) using

keyframes {
// Expand horizontally first.
IntSize(targetSize.width, initialSize.height) at 150
durationMillis = 300
}
} else {
keyframes {
// Shrink vertically first.
IntSize(initialSize.width, targetSize.height) at 150
durationMillis = 300
}
}
}
}, label = "size transform"

Expanded()
} else {
ContentIcon()
}
}
}
AnimationSnippets.kt

#### Animate child enter and exit transitions

Just like `AnimatedVisibility`, the `animateEnterExit`
modifier is available inside the content lambda of `AnimatedContent`. Use this
to apply `EnterAnimation` and `ExitAnimation` to each of the direct or indirect
children separately.

#### Add custom animation

Just like `AnimatedVisibility`, the `transition` field is available inside the
content lambda of `AnimatedContent`. Use this to create a custom animation
effect that runs simultaneously with the `AnimatedContent` transition. See
updateTransition for the details.

### Animate between two layouts with `Crossfade`

`Crossfade` animates between two layouts with a crossfade animation. By toggling
the value passed to the `current` parameter, the content is switched with a
crossfade animation.

var currentPage by remember { mutableStateOf("A") }

}
}
AnimationSnippets.kt

## Built-in animation modifiers

### Animate composable size changes with `animateContentSize`

The `animateContentSize` modifier animates a size change.

var expanded by remember { mutableStateOf(false) }
Box(
modifier = Modifier
.background(colorBlue)
.animateContentSize()
.height(if (expanded) 400.dp else 200.dp)
.fillMaxWidth()
.clickable(
interactionSource = remember { MutableInteractionSource() },
indication = null
) {
expanded = !expanded
}

) {
}
AnimationQuickGuide.kt

## List item animations

If you are looking to animate item reorderings inside a Lazy list or grid, take a look at the
Lazy layout item animation documentation.

## Recommended for you

### Value-based animations

Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android. Quickly bring your app to life with less code, powerful tools, and intuitive Kotlin APIs.

Updated Jun 10, 2025

### Animations in Compose

Updated Jul 1, 2024

### Animation tooling support

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-10 UTC.

iframe

---

# https://developer.android.com/develop/ui/compose/animation/value-based

- Core areas
- UI

- On this page
- Animate a single value with animate\*AsState
- Animate multiple properties simultaneously with a transition
- Use transition with AnimatedVisibility and AnimatedContent
- Encapsulate a transition and make it reusable
- Create an infinitely repeating animation with rememberInfiniteTransition
- Low-level animation APIs
- Animatable: Coroutine-based single value animation
- Animation: Manually controlled animation


## Animate a single value with `animate*AsState`

The `animate*AsState` functions are the simplest animation APIs in Compose for
animating a single value. You only provide the target value (or end value), and
the API starts animation from the current value to the specified value.

Below is an example of animating alpha using this API. By simply wrapping the
target value in `animateFloatAsState`, the alpha value is now an animation value
between the provided values ( `1f` or `0.5f` in this case).

var enabled by remember { mutableStateOf(true) }

val animatedAlpha: Float by animateFloatAsState(if (enabled) 1f else 0.5f, label = "alpha")
Box(
Modifier
.fillMaxSize()
.graphicsLayer { alpha = animatedAlpha }
.background(Color.Red)
)
AnimationSnippets.kt

Note that you don't need to create an instance of any animation class, or handle
interruption. Under the hood, an animation object (namely, an `Animatable`
instance) will be created and remembered at the call site, with the first target
value as its initial value. From there on, any time you supply this composable a
different target value, an animation is automatically started towards that
value. If there's already an animation in flight, the animation starts from its
current value (and velocity) and animates toward the target value. During the
animation, this composable gets recomposed and returns an updated animation
value every frame.

Out of the box, Compose provides `animate*AsState` functions for `Float`,
`Color`, `Dp`, `Size`, `Offset`, `Rect`, `Int`, `IntOffset`, and
`IntSize`. You can easily add support for other data types by providing a
`TwoWayConverter` to `animateValueAsState` that takes a generic type.

You can customize the animation specifications by providing an `AnimationSpec`.
See AnimationSpec for more information.

## Animate multiple properties simultaneously with a transition

`Transition` manages one or more animations as its children and runs them
simultaneously between multiple states.

The states can be of any data type. In many cases, you can use a custom `enum`
type to ensure type safety, as in this example:

enum class BoxState {
Collapsed,
Expanded
}
AnimationSnippets.kt

`updateTransition` creates and remembers an instance of `Transition` and updates
its state.

var currentState by remember { mutableStateOf(BoxState.Collapsed) }
val transition = updateTransition(currentState, label = "box state")
AnimationSnippets.kt

You can then use one of `animate*` extension functions to define a child
animation in this transition. Specify the target values for each of the states.
These `animate*` functions return an animation value that is updated every frame
during the animation when the transition state is updated with
`updateTransition`.

}
}

}
}
AnimationSnippets.kt

Optionally, you can pass a `transitionSpec` parameter to specify a different
`AnimationSpec` for each of the combinations of transition state changes. See
AnimationSpec for more information.

val color by transition.animateColor(
transitionSpec = {
when {

}
}, label = "color"

Once a transition has arrived at the target state, `Transition.currentState`
will be the same as `Transition.targetState`. This can be used as a signal for
whether the transition has finished.

We sometimes want to have an initial state different from the first target
state. We can use `updateTransition` with `MutableTransitionState` to achieve
this. For example, it allows us to start animation as soon as the code enters
composition.

// Start in collapsed state and immediately animate to expanded
var currentState = remember { MutableTransitionState(BoxState.Collapsed) }
currentState.targetState = BoxState.Expanded
val transition = rememberTransition(currentState, label = "box state")
// ……
AnimationSnippets.kt

For a more complex transition involving multiple composable functions, you can
use `createChildTransition`
to create a child transition. This technique is useful for separating concerns
among multiple subcomponents in a complex composable. The parent transition will
be aware of all the animation values in the child transitions.

enum class DialerState { DialerMinimized, NumberPad }

@Composable

// `isVisibleTransition` spares the need for the content to know
// about other DialerStates. Instead, the content can focus on
// animating the state change between visible and not visible.
}

@Composable
fun Dialer(dialerState: DialerState) {
val transition = updateTransition(dialerState, label = "dialer state")
Box {
// Creates separate child transitions of Boolean type for NumberPad
// and DialerButton for any content animation between visible and
// not visible
NumberPad(
transition.createChildTransition {
it == DialerState.NumberPad
}
)
DialerButton(
transition.createChildTransition {
it == DialerState.DialerMinimized
}
)
}
}
AnimationSnippets.kt

### Use transition with `AnimatedVisibility` and `AnimatedContent`

`AnimatedVisibility`
and `AnimatedContent`
are available as extension functions of `Transition`. The `targetState` for
`Transition.AnimatedVisibility` and `Transition.AnimatedContent` is derived
from the `Transition`, and triggering enter/exit transitions as needed when the
`Transition`'s `targetState` has changed. These extension functions allow all
the enter/exit/sizeTransform animations that would otherwise be internal to
`AnimatedVisibility`/ `AnimatedContent` to be hoisted into the `Transition`.
With these extension functions, `AnimatedVisibility`/ `AnimatedContent`'s state
change can be observed from outside. Instead of a boolean `visible` parameter,
this version of `AnimatedVisibility` takes a lambda that converts the parent
transition's target state into a boolean.

See AnimatedVisibility and AnimatedContent for the details.

var selected by remember { mutableStateOf(false) }
// Animates changes when `selected` is changed.
val transition = updateTransition(selected, label = "selected state")

}

}
Surface(
onClick = { selected = !selected },
shape = RoundedCornerShape(8.dp),
border = BorderStroke(2.dp, borderColor),
shadowElevation = elevation
) {
Column(
modifier = Modifier
.fillMaxWidth()
.padding(16.dp)
) {
Text(text = "Hello, world!")
// AnimatedVisibility as a part of the transition.
transition.AnimatedVisibility(

enter = expandVertically(),
exit = shrinkVertically()
) {
Text(text = "It is fine today.")
}
// AnimatedContent as a part of the transition.

Text(text = "Selected")
} else {
Icon(imageVector = Icons.Default.Phone, contentDescription = "Phone")
}
}
}
}
AnimationSnippets.kt

### Encapsulate a transition and make it reusable

For simple use cases, defining transition animations in the same composable as
your UI is a perfectly valid option. When you are working on a complex component
with a number of animated values, however, you might want to separate the
animation implementation from the composable UI.

You can do so by creating a class that holds all the animation values and an
‘update’ function that returns an instance of that class. The transition
implementation can be extracted into the new separate function. This pattern is
useful when there is a need to centralize the animation logic, or make complex
animations reusable.

enum class BoxState { Collapsed, Expanded }

@Composable
fun AnimatingBox(boxState: BoxState) {
val transitionData = updateTransitionData(boxState)
// UI tree
Box(
modifier = Modifier
.background(transitionData.color)
.size(transitionData.size)
)
}

// Holds the animation values.
private class TransitionData(

val color by color
val size by size
}

// Create a Transition and return its animation values.
@Composable
private fun updateTransitionData(boxState: BoxState): TransitionData {
val transition = updateTransition(boxState, label = "box state")

}
}

}
}
return remember(transition) { TransitionData(color, size) }
}
AnimationSnippets.kt

## Create an infinitely repeating animation with `rememberInfiniteTransition`

`InfiniteTransition` holds one or more child animations like `Transition`, but
the animations start running as soon as they enter the composition and do not
stop unless they are removed. You can create an instance of `InfiniteTransition`
with `rememberInfiniteTransition`. Child animations can be added with
`animateColor`, `animatedFloat`, or `animatedValue`. You also need to specify an
infiniteRepeatable to specify the animation
specifications.

val infiniteTransition = rememberInfiniteTransition(label = "infinite")
val color by infiniteTransition.animateColor(
initialValue = Color.Red,
targetValue = Color.Green,
animationSpec = infiniteRepeatable(
animation = tween(1000, easing = LinearEasing),
repeatMode = RepeatMode.Reverse
),
label = "color"
)

Box(
Modifier
.fillMaxSize()
.background(color)
)
AnimationSnippets.kt

## Low-level animation APIs

All the high-level animation APIs mentioned in the previous section are built on
top of the foundation of the low-level animation APIs.

The `animate*AsState` functions are the simplest APIs, that render an instant
value change as an animation value. It is backed by `Animatable`, which is a
coroutine-based API for animating a single value. `updateTransition` creates a
transition object that can manage multiple animating values and run them based
on a state change. `rememberInfiniteTransition` is similar, but it creates an
infinite transition that can manage multiple animations that keep on running
indefinitely. All of these APIs are composables except for `Animatable`, which
means these animations can be created outside of composition.

All of these APIs are based on the more fundamental `Animation` API. Though most
apps will not interact directly with `Animation`, some of the customization
capabilities for `Animation` are available through higher-level APIs. See
Customize animations for more information on
`AnimationVector` and `AnimationSpec`.

### `Animatable`: Coroutine-based single value animation

`Animatable` is a value holder that can animate the value as it is changed via
`animateTo`. This is the API backing up the implementation of `animate*AsState`.
It ensures consistent continuation and mutual exclusiveness, meaning that the
value change is always continuous and any ongoing animation will be canceled.

Many features of `Animatable`, including `animateTo`, are provided as suspend
functions. This means that they need to be wrapped in an appropriate coroutine
scope. For example, you can use the `LaunchedEffect` composable to create a
scope just for the duration of the specified key value.

// Start out gray and animate to green/red based on `ok`
val color = remember { Animatable(Color.Gray) }
LaunchedEffect(ok) {
color.animateTo(if (ok) Color.Green else Color.Red)
}
Box(
Modifier
.fillMaxSize()
.background(color.value)
)
AnimationSnippets.kt

In the example above, we create and remember an instance of `Animatable` with
the initial value of `Color.Gray`. Depending on the value of the boolean flag
`ok`, the color animates to either `Color.Green` or `Color.Red`. Any subsequent
change to the boolean value starts animation to the other color. If there's an
ongoing animation when the value is changed, the animation is canceled, and the
new animation starts from the current snapshot value with the current velocity.

This is the animation implementation that backs up the `animate*AsState` API
mentioned in the previous section. Compared to `animate*AsState`, using
`Animatable` directly gives us finer-grained control on several respects. First,
`Animatable` can have an initial value different from its first target value.
For example, the code example above shows a gray box at first, which immediately
starts animating to either green or red. Second, `Animatable` provides more
operations on the content value, namely `snapTo` and `animateDecay`. `snapTo`
sets the current value to the target value immediately. This is useful when the
animation itself is not the only source of truth and has to be synced with other
states, such as touch events. `animateDecay` starts an animation that slows down
from the given velocity. This is useful for implementing fling behavior. See
Gesture and animation for more information.

Out of the box, `Animatable` supports `Float` and `Color`, but any data type can
be used by providing a `TwoWayConverter`. See
AnimationVector for more information.

### `Animation`: Manually controlled animation

`Animation` is the lowest-level Animation API available. Many of the animations
we've seen so far build ontop of Animation. There are two `Animation` subtypes:
`TargetBasedAnimation` and `DecayAnimation`.

`Animation` should only be used to manually control the time of the animation.
`Animation` is stateless, and it does not have any concept of lifecycle. It
serves as an animation calculation engine that the higher-level APIs use.

#### `TargetBasedAnimation`

Other APIs cover most use cases, but using `TargetBasedAnimation` directly
allows you to control the animation play time yourself. In the example below,
the play time of the `TargetAnimation` is manually controlled based on the frame
time provided by `withFrameNanos`.

val anim = remember {
TargetBasedAnimation(
animationSpec = tween(200),
typeConverter = Float.VectorConverter,
initialValue = 200f,
targetValue = 1000f
)
}
var playTime by remember { mutableLongStateOf(0L) }

LaunchedEffect(anim) {
val startTime = withFrameNanos { it }

do {
playTime = withFrameNanos { it } - startTime
val animationValue = anim.getValueFromNanos(playTime)
} while (someCustomCondition())
}
AnimationSnippets.kt

#### `DecayAnimation`

Unlike `TargetBasedAnimation`,
`DecayAnimation`
does not require a `targetValue` to be provided. Instead, it calculates its
`targetValue` based on the starting conditions, set by `initialVelocity` and
`initialValue` and the supplied `DecayAnimationSpec`.

Decay animations are often used after a fling gesture to slow elements down to a
stop. The animation velocity starts at the value set by `initialVelocityVector`
and slows down over time.

## Recommended for you

### Customize animations

Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android. Quickly bring your app to life with less code, powerful tools, and intuitive Kotlin APIs.

Updated Jun 10, 2025

### Animations in Compose

Updated Jul 1, 2024

### Animation modifiers and composables

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-10 UTC.

iframe

---

# https://developer.android.com/develop/ui/compose/animation/customize

- Core areas
- UI

- On this page
- Customize animations with the AnimationSpec parameter
- Create physics-based animation with spring
- Animate between start and end values with easing curve with tween
- Animate to specific values at certain timings with keyframes
- Animate between keyframes smoothly with keyframesWithSplines
- Repeat an animation with repeatable
- Repeat an animation infinitely with infiniteRepeatable
- Immediately snap to end value with snap
- Set a custom easing function
- Animate custom data types by converting to and from AnimationVector


# Customize animations bookmark\_borderbookmark Stay organized with collections Save and categorize content based on your preferences.

Many of the Animation APIs commonly accept parameters for customizing their
behavior.

## Customize animations with the `AnimationSpec` parameter

Most animation APIs allow developers to customize animation specifications by an
optional `AnimationSpec` parameter.

val alpha: Float by animateFloatAsState(
targetValue = if (enabled) 1f else 0.5f,
// Configure the animation duration and easing.
animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
label = "alpha"
)
AnimationSnippets.kt

There are different kinds of `AnimationSpec` for creating different types of
animation.

### Create physics-based animation with `spring`

`spring` creates a physics-based animation between start and end values. It
takes 2 parameters: `dampingRatio` and `stiffness`.

`dampingRatio` defines how bouncy the spring should be. The default value is
`Spring.DampingRatioNoBouncy`.

**Figure 1**. Setting different spring damping ratios.

`stiffness` defines how fast the spring should move toward the end value. The
default value is `Spring.StiffnessMedium`.

**Figure 2**. Setting different spring stiffness.

val value by animateFloatAsState(
targetValue = 1f,
animationSpec = spring(
dampingRatio = Spring.DampingRatioHighBouncy,
stiffness = Spring.StiffnessMedium
),
label = "spring spec"
)
AnimationSnippets.kt

`spring` can handle interruptions more smoothly than duration-based
`AnimationSpec` types because it guarantees the continuity of velocity when
target value changes amid animations. `spring` is used as the default
AnimationSpec by many animation APIs, such as `animate*AsState` and
`updateTransition`.

For example, if we apply a `spring` config to the following animation that is
driven by user touch, when interrupting the animation as its progressing, you
can see that using `tween` doesn't respond as smoothly as using `spring`.

**Figure 3**. Setting `tween` versus `spring` specs for animation, and interrupting it.

### Animate between start and end values with easing curve with `tween`

`tween` animates between start and end values over the specified
`durationMillis` using an easing curve. `tween` is short for the word between -
as it goes _between_ two values.

You can also specify `delayMillis` to postpone the start of the animation.

val value by animateFloatAsState(
targetValue = 1f,
animationSpec = tween(
durationMillis = 300,
delayMillis = 50,
easing = LinearOutSlowInEasing
),
label = "tween delay"
)
AnimationSnippets.kt

See Easing for more information.

### Animate to specific values at certain timings with `keyframes`

`keyframes` animates based on the snapshot values specified at different
timestamps in the duration of the animation. At any given time, the animation
value will be interpolated between two keyframe values. For each of these
keyframes, Easing can be specified to determine the interpolation curve.

It is optional to specify the values at 0 ms and at the duration time. If you do
not specify these values, they default to the start and end values of the
animation, respectively.

val value by animateFloatAsState(
targetValue = 1f,
animationSpec = keyframes {
durationMillis = 375
0.0f at 0 using LinearOutSlowInEasing // for 0-15 ms
0.2f at 15 using FastOutLinearInEasing // for 15-75 ms
0.4f at 75 // ms
0.4f at 225 // ms
},
label = "keyframe"
)
AnimationSnippets.kt

### Animate between keyframes smoothly with `keyframesWithSplines`

To create an animation that follows a smooth curve as it transitions between
values, you can use `keyframesWithSplines` instead of `keyframes` animation
specs.

val offset by animateOffsetAsState(
targetValue = Offset(300f, 300f),
animationSpec = keyframesWithSpline {
durationMillis = 6000
Offset(0f, 0f) at 0
Offset(150f, 200f) atFraction 0.5f
Offset(0f, 100f) atFraction 0.7f
}
)
AnimationSnippets.kt

Spline-based keyframes are particularly useful for 2D movement of items on
screen.

The following videos showcase the differences between `keyframes` and
`keyframesWithSpline` given the same set of x, y coordinates that a circle
should follow.

| `keyframes` | `keyframesWithSplines` |
| --- | --- |
| | |

As you can see, the spline-based keyframes offer smoother transitions between
points, as they use bezier curves to smoothly animate between items. This spec
is useful for a preset animation. However,if you're working with user-driven
points, it's preferable to use springs to achieve a similar smoothness between
points because those are interruptible.

### Repeat an animation with `repeatable`

`repeatable` runs a duration-based animation (such as `tween` or `keyframes`)
repeatedly until it reaches the specified iteration count. You can pass the
`repeatMode` parameter to specify whether the animation should repeat by
starting from the beginning ( `RepeatMode.Restart`) or from the end
( `RepeatMode.Reverse`).

val value by animateFloatAsState(
targetValue = 1f,
animationSpec = repeatable(
iterations = 3,
animation = tween(durationMillis = 300),
repeatMode = RepeatMode.Reverse
),
label = "repeatable spec"
)
AnimationSnippets.kt

### Repeat an animation infinitely with `infiniteRepeatable`

`infiniteRepeatable` is like `repeatable`, but it repeats for an infinite amount
of iterations.

val value by animateFloatAsState(
targetValue = 1f,
animationSpec = infiniteRepeatable(
animation = tween(durationMillis = 300),
repeatMode = RepeatMode.Reverse
),
label = "infinite repeatable"
)
AnimationSnippets.kt

In tests using
`ComposeTestRule`,
animations using `infiniteRepeatable` are not run. The component will be
rendered using the initial value of each animated value.

### Immediately snap to end value with `snap`

`snap` is a special `AnimationSpec` that immediately switches the value to the
end value. You can specify `delayMillis` in order to delay the start of the
animation.

val value by animateFloatAsState(
targetValue = 1f,
animationSpec = snap(delayMillis = 50),
label = "snap spec"
)
AnimationSnippets.kt

## Set a custom easing function

Duration-based `AnimationSpec` operations (such as `tween` or `keyframes`) use
`Easing` to adjust an animation's fraction. This allows the animating value to
speed up and slow down, rather than moving at a constant rate. Fraction is a
value between 0 (start) and 1.0 (end) indicating the current point in the
animation.

Easing is in fact a function that takes a fraction value between 0 and 1.0 and
returns a float. The returned value can be outside the boundary to represent
overshoot or undershoot. A custom Easing can be created like the code below.

@Composable
fun EasingUsage() {
val value by animateFloatAsState(
targetValue = 1f,
animationSpec = tween(
durationMillis = 300,
easing = CustomEasing
),
label = "custom easing"
)
// ……
}
AnimationSnippets.kt

Compose provides several built-in `Easing` functions that cover most use cases.
See Speed - Material Design for more
information about what Easing to use depending on your scenario.

- `FastOutSlowInEasing`
- `LinearOutSlowInEasing`
- `FastOutLinearEasing`
- `LinearEasing`
- `CubicBezierEasing`
- See more

## Animate custom data types by converting to and from `AnimationVector`

Most Compose animation APIs support `Float`, `Color`, `Dp`, and other basic data
types as animation values by default, but you sometimes need to animate
other data types including your custom ones. During animation, any animating
value is represented as an `AnimationVector`. The value is converted into an
`AnimationVector` and vice versa by a corresponding `TwoWayConverter` so that
the core animation system can handle them uniformly. For example, an `Int` is
represented as an `AnimationVector1D` that holds a single float value.
`TwoWayConverter` for `Int` looks like this:

TwoWayConverter({ AnimationVector1D(it.toFloat()) }, { it.value.toInt() })
AnimationSnippets.kt

`Color` is essentially a set of 4 values, red, green, blue, and alpha, so
`Color` is converted into an `AnimationVector4D` that holds 4 float values. In
this manner, every data type used in animations is converted to either
`AnimationVector1D`, `AnimationVector2D`, `AnimationVector3D`, or
`AnimationVector4D` depending on its dimensionality. This allows different
components of the object to be animated independently, each with their own
velocity tracking. Built-in converters for basic data types can be accessed
using converters such as `Color.VectorConverter` or `Dp.VectorConverter`.

When you want to add support for a new data type as an animating value, you can
create your own `TwoWayConverter` and provide it to the API. For example, you
can use `animateValueAsState` to animate your custom data type like this:

data class MySize(val width: Dp, val height: Dp)

@Composable
fun MyAnimation(targetSize: MySize) {
val animSize: MySize by animateValueAsState(
targetSize,
TwoWayConverter(

AnimationVector2D(size.width.value, size.height.value)
},

}
),
label = "size"
)
}
AnimationSnippets.kt

The following list includes some built-in `VectorConverter` s:

- `Color.VectorConverter`
- `Dp.VectorConverter`
- `Offset.VectorConverter`
- `Int.VectorConverter`
- `Float.VectorConverter`
- `IntSize.VectorConverter`

## Recommended for you

### Value-based animations

Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android. Quickly bring your app to life with less code, powerful tools, and intuitive Kotlin APIs.

Updated Jun 10, 2025

### Iterative code development

### Animations in Compose

Updated Jul 1, 2024

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-10 UTC.

iframe

---

- Core areas
- UI

Was this helpful?

# Test animations bookmark\_borderbookmark Stay organized with collections Save and categorize content based on your preferences.

Compose offers `ComposeTestRule` that allows you to write tests for animations
in a deterministic manner with full control over the test clock. This allows you
to verify intermediate animation values. In addition, a test can run quicker
than the actual duration of the animation.

`ComposeTestRule` exposes its test clock as `mainClock`. You can set the
`autoAdvance` property to false to control the clock in your test code. After
initiating the animation you want to test, the clock can be moved forward with
`advanceTimeBy`.

One thing to note here is that `advanceTimeBy` doesn't move the clock exactly by
the specified duration. Rather, it rounds it up to the nearest duration that is
a multiplier of the frame duration.

@get:Rule
val rule = createComposeRule()

@Test
fun testAnimationWithClock() {
// Pause animations
rule.mainClock.autoAdvance = false
var enabled by mutableStateOf(false)
rule.setContent {
val color by animateColorAsState(
targetValue = if (enabled) Color.Red else Color.Green,
animationSpec = tween(durationMillis = 250)
)
Box(Modifier.size(64.dp).background(color))
}

// Initiate the animation.
enabled = true

// Let the animation proceed.
rule.mainClock.advanceTimeBy(50L)

// Compare the result with the image showing the expected result.
// `assertAgainGolden` needs to be implemented in your code.
rule.onRoot().captureToImage().assertAgainstGolden()
}
AnimationTestingSnippets.kt

## Recommended for you

### Test your Compose layout

Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android. Quickly bring your app to life with less code, powerful tools, and intuitive Kotlin APIs.

Updated Jun 10, 2025

### Other considerations

### Customize animations


Customize animations

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-10 UTC.

iframe

---

# https://developer.android.com/develop/ui/compose/animation/tooling

Skip to main content


# Animation tooling support bookmark\_borderbookmark Stay organized with collections Save and categorize content based on your preferences.

Android Studio supports inspection of `animate*AsState`, `CrossFade`, `rememberInfiniteTransition`, `AnimatedContent`,
`updateTransition` and
`animatedVisibility` in
Animation Preview. You can do the
following:

- Preview a transition frame by frame
- Inspect values for all animations in the transition
- Preview a transition between any initial and target state
- Inspect and coordinate multiple animations at once

When you start Animation Preview, you see the "Animations" pane where you can
run any transition included in the preview. The transition as well as each of
its animation values is labeled with a default name. You can customize the label
by specifying the `label` parameter in the `updateTransition` and the
`AnimatedVisibility` functions. For more information, see
Animation Preview.

## Recommended for you

### Value-based animations

Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android. Quickly bring your app to life with less code, powerful tools, and intuitive Kotlin APIs.

Updated Jun 10, 2025

### Animations in Compose

Updated Jul 1, 2024

### Animation modifiers and composables

Previous\\
\\
arrow\_back\\
\\
Test animations

Next\\
\\
Additional resources\\
\\
arrow\_forward

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-10 UTC.

iframe

---

# Choose an animation API bookmark\_borderbookmark Stay organized with collections Save and categorize content based on your preferences.

The diagram below helps you decide what API to use to implement your animation.

Follow the decision tree questions below to choose which animation API is most appropriate for your use case:

- Is my animation more like art, consisting of many visual elements? i.e. SVGs or images
- Yes: Does it have simple SVGs? i.e. an icon with micro-animations
- Yes: `AnimatedVectorDrawable`
- No: Third-party animation framework, i.e. `Lottie`
- No: Does it need to repeat forever?
- Yes: `rememberInfiniteTransition`
- No: Is this a layout animation?
- Yes: Changing between multiple composables that have different content?
- Yes: With navigation-compose?
- Yes: `composable()` with `enterTransition` and `exitTransition` set
- No: `AnimatedContent`, `Crossfade` or `Pager`
- No: Animating appearance / disappearance?
- Yes: `AnimatedVisibility` or `animateFloatAsState` with `Modifier.alpha()`
- No: Animating size?
- Yes: `Modifier.animateContentSize`
- No: Other layout property? ie offset, padding etc
- Yes: See "Are the properties completely independent of each other?"
- No: List item animations?
- Yes: `animateItemPlacement()` (reorder and delete coming soon)
- No: Do you need to animate multiple properties?
- Yes: Are the properties completely independent of each other?
- Yes: `animate*AsState`, for Text, use `TextMotion.Animated`
- No: Start at the same time?
- Yes: Yes: `updateTransition` with `AnimatedVisibility`, `animateFloat`, `animateInt` etc
- No: `Animatable` with `animateTo` called with different timings (using suspend functions)
- No: Does the animation have a set of predefined target values?
- Yes: `animate*AsState`, for Text, use `TextMotion.Animated`
- No: Gesture driven animation? Your animation is the only source of truth?
- Yes: `Animatable` with `animateTo` / `snapTo`
- No: One shot animation without state management?
- Yes: `AnimationState` or `animate`
- No: Answer not here? File a feature request

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

# Advanced animation example: Gestures bookmark\_borderbookmark Stay organized with collections Save and categorize content based on your preferences.

There are several things we have to take into consideration when we are working
with touch events and animations, compared to when we are working with
animations alone. First of all, we might need to interrupt an ongoing animation
when touch events begin as user interaction should have the highest priority.

In the example below, we use an `Animatable` to represent the offset position of
a circle component. Touch events are processed with the
`pointerInput`
modifier. When we detect a new tap event, we call `animateTo` to animate the
offset value to the tap position. A tap event can happen during the animation
too, and in that case, `animateTo` interrupts the ongoing animation and starts
the animation to the new target position while maintaining the velocity of the
interrupted animation.

@Composable
fun Gesture() {
val offset = remember { Animatable(Offset(0f, 0f), Offset.VectorConverter) }
Box(
modifier = Modifier
.fillMaxSize()
.pointerInput(Unit) {
coroutineScope {
while (true) {
// Detect a tap event and obtain its position.
awaitPointerEventScope {
val position = awaitFirstDown().position

launch {
// Animate to the tap position.
offset.animateTo(position)
}
}
}
}
}
) {
Circle(modifier = Modifier.offset { offset.value.toIntOffset() })
}
}

private fun Offset.toIntOffset() = IntOffset(x.roundToInt(), y.roundToInt())
AdvancedAnimationSnippets.kt

Another frequent pattern is we need to synchronize animation values with values
coming from touch events, such as drag. In the example below, we see "swipe to
dismiss" implemented as a `Modifier` (rather than using the
`SwipeToDismiss`
composable). The horizontal offset of the element is represented as an
`Animatable`. This API has a characteristic useful in gesture animation. Its
value can be changed by touch events as well as the animation. When we receive a
touch down event, we stop the `Animatable` by the `stop` method so that any
ongoing animation is intercepted.

During a drag event, we use `snapTo` to update the `Animatable` value with the
value calculated from touch events. For fling, Compose provides
`VelocityTracker` to record drag events and calculate velocity. The velocity can
be fed directly to `animateDecay` for the fling animation. When we want to slide
the offset value

Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android. Quickly bring your app to life with less code, powerful tools, and intuitive Kotlin APIs.

# Animated vector images in Compose bookmark\_borderbookmark Stay organized with collections Save and categorize content based on your preferences.

Animating vectors in Compose is possible in a few different ways. You can use any of the following:

- `AnimatedVectorDrawable` file format
- `ImageVector` with Compose animation APIs, like in this Medium article
- A third-party solution like Lottie

## Animated vector drawables (experimental)

To use an `AnimatedVectorDrawable` resource, load up the drawable file using `animatedVectorResource` and pass in a `boolean` to switch between the start and end state of your drawable, performing the animation.

@Composable
fun AnimatedVectorDrawable() {
val image = AnimatedImageVector.animatedVectorResource(R.drawable.ic_hourglass_animated)
var atEnd by remember { mutableStateOf(false) }
Image(
painter = rememberAnimatedVectorPainter(image, atEnd),
contentDescription = "Timer",
modifier = Modifier.clickable {
atEnd = !atEnd
},
contentScale = ContentScale.Crop
)
}