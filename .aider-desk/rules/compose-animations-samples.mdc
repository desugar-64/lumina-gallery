@Composable
fun HorizontalTransitionSample() {
    var visible by remember { mutableStateOf(true) }
    AnimatedVisibility(
        visible = visible,
        // Set the start width to 20 (pixels), 0 by default
        enter = expandHorizontally { 20 },
        exit =
            shrinkHorizontally(
                // Overwrites the default animation with tween for this shrink animation.
                animationSpec = tween(),
                // Shrink towards the end (i.e. right edge for LTR, left edge for RTL). The default
                // direction for the shrink is towards [Alignment.Start]
                shrinkTowards = Alignment.End,
            ) { fullWidth ->
                // Set the end width for the shrink animation to a quarter of the full width.
                fullWidth / 4
            },
    ) {
        // Content that needs to appear/disappear goes here:
        Box(Modifier.fillMaxWidth().requiredHeight(200.dp))
    }
}


@Composable
fun SlideTransition() {
    var visible by remember { mutableStateOf(true) }
    AnimatedVisibility(
        visible = visible,
        enter =
            slideInHorizontally(animationSpec = tween(durationMillis = 200)) { fullWidth ->
                // Offsets the content by 1/3 of its width to the left, and slide towards right
                // Overwrites the default animation with tween for this slide animation.
                -fullWidth / 3
            } +
                fadeIn(
                    // Overwrites the default animation with tween
                    animationSpec = tween(durationMillis = 200)
                ),
        exit =
            slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessHigh)) {
                // Overwrites the ending position of the slide-out to 200 (pixels) to the right
                200
            } + fadeOut(),
    ) {
        // Content that needs to appear/disappear goes here:
        Box(Modifier.fillMaxWidth().requiredHeight(200.dp)) {}
    }
}


@Composable
fun FadeTransition() {
    var visible by remember { mutableStateOf(true) }
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(
                // Overwrites the initial value of alpha to 0.4f for fade in, 0 by default
                initialAlpha = 0.4f
            ),
        exit =
            fadeOut(
                // Overwrites the default animation with tween
                animationSpec = tween(durationMillis = 250)
            ),
    ) {
        // Content that needs to appear/disappear goes here:
        Text("Content to appear/disappear", Modifier.fillMaxWidth().requiredHeight(200.dp))
    }
}

@OptIn(ExperimentalAnimationApi::class)

@Composable
fun FullyLoadedTransition() {
    var visible by remember { mutableStateOf(true) }
    AnimatedVisibility(
        visible = visible,
        enter =
            slideInVertically(
                // Start the slide from 40 (pixels) above where the content is supposed to go, to
                // produce a parallax effect
                initialOffsetY = { -40 }
            ) +
                expandVertically(expandFrom = Alignment.Top) +
                scaleIn(
                    // Animate scale from 0f to 1f using the top center as the pivot point.
                    transformOrigin = TransformOrigin(0.5f, 0f)
                ) +
                fadeIn(initialAlpha = 0.3f),
        exit = slideOutVertically() + shrinkVertically() + fadeOut() + scaleOut(targetScale = 1.2f),
    ) {
        // Content that needs to appear/disappear goes here:
        Text("Content to appear/disappear", Modifier.fillMaxWidth().requiredHeight(200.dp))
    }
}

@OptIn(ExperimentalAnimationApi::class)

@Composable
fun AnimatedVisibilityWithBooleanVisibleParamNoReceiver() {
    var visible by remember { mutableStateOf(true) }
    Box(modifier = Modifier.clickable { visible = !visible }) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(),
        ) { // Content that needs to appear/disappear goes here:
            // Here we can optionally define a custom enter/exit animation by creating an animation
            // using the Transition<EnterExitState> object from AnimatedVisibilityScope:

            // As a part of the enter transition, the corner radius will be animated from 0.dp to
            // 50.dp.
            val cornerRadius by
                transition.animateDp {
                    when (it) {
                        EnterExitState.PreEnter -> 0.dp
                        EnterExitState.Visible -> 50.dp
                        // No corner radius change when exiting.
                        EnterExitState.PostExit -> 50.dp
                    }
                }
            Box(
                Modifier.background(Color.Red, shape = RoundedCornerShape(cornerRadius))
                    .height(100.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)

@Composable
fun ColumnScope.AnimatedFloatingActionButton() {
    var expanded by remember { mutableStateOf(true) }
    FloatingActionButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp)) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Favorite",
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            AnimatedVisibility(expanded, modifier = Modifier.align(Alignment.CenterVertically)) {
                Text(modifier = Modifier.padding(start = 12.dp), text = "Favorite")
            }
        }
    }
    Spacer(Modifier.requiredHeight(20.dp))
}


@Composable
fun SlideInOutSample() {
    var visible by remember { mutableStateOf(true) }
    AnimatedVisibility(
        visible,
        enter =
            slideIn(tween(100, easing = LinearOutSlowInEasing)) { fullSize ->
                // Specifies the starting offset of the slide-in to be 1/4 of the width to the
                // right,
                // 100 (pixels) below the content position, which results in a simultaneous slide up
                // and slide left.
                IntOffset(fullSize.width / 4, 100)
            },
        exit =
            slideOut(tween(100, easing = FastOutSlowInEasing)) {
                // The offset can be entirely independent of the size of the content. This specifies
                // a target offset 180 pixels to the left of the content, and 50 pixels below. This
                // will
                // produce a slide-left combined with a slide-down.
                IntOffset(-180, 50)
            },
    ) {
        // Content that needs to appear/disappear goes here:
        Text("Content to appear/disappear", Modifier.fillMaxWidth().requiredHeight(200.dp))
    }
}


@Composable
fun ExpandShrinkVerticallySample() {
    var visible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible,
        // Sets the initial height of the content to 20, revealing only the top of the content at
        // the beginning of the expanding animation.
        enter = expandVertically(expandFrom = Alignment.Top) { 20 },
        // Shrinks the content to half of its full height via an animation.
        exit = shrinkVertically(animationSpec = tween()) { fullHeight -> fullHeight / 2 },
    ) {
        // Content that needs to appear/disappear goes here:
        Text("Content to appear/disappear", Modifier.fillMaxWidth().requiredHeight(200.dp))
    }
}


@Composable
fun ExpandInShrinkOutSample() {
    var visible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible,
        enter =
            expandIn(
                // Overwrites the default spring animation with tween
                animationSpec = tween(100, easing = LinearOutSlowInEasing),
                // Overwrites the corner of the content that is first revealed
                expandFrom = Alignment.BottomStart,
            ) {
                // Overwrites the initial size to 50 pixels by 50 pixels
                IntSize(50, 50)
            },
        exit =
            shrinkOut(
                tween(100, easing = FastOutSlowInEasing),
                // Overwrites the area of the content that the shrink animation will end on. The
                // following parameters will shrink the content's clip bounds from the full size of
                // the
                // content to 1/10 of the width and 1/5 of the height. The shrinking clip bounds
                // will
                // always be aligned to the CenterStart of the full-content bounds.
                shrinkTowards = Alignment.CenterStart,
            ) { fullSize ->
                // Overwrites the target size of the shrinking animation.
                IntSize(fullSize.width / 10, fullSize.height / 5)
            },
    ) {
        // Content that needs to appear/disappear goes here:
        Text("Content to appear/disappear", Modifier.fillMaxWidth().requiredHeight(200.dp))
    }
}


@Composable
fun ColumnAnimatedVisibilitySample() {
    var itemIndex by remember { mutableStateOf(0) }
    val colors = listOf(Color.Red, Color.Green, Color.Blue)
    Column(Modifier.fillMaxWidth().clickable { itemIndex = (itemIndex + 1) % colors.size }) {
        colors.forEachIndexed { i, color ->
            // By default ColumnScope.AnimatedVisibility expands and shrinks new content while
            // fading.
            AnimatedVisibility(i <= itemIndex) {
                Box(Modifier.requiredHeight(40.dp).fillMaxWidth().background(color))
            }
        }
    }
}


@Composable
fun AVScopeAnimateEnterExit() {
    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun AnimatedVisibilityScope.Item(modifier: Modifier, backgroundColor: Color) {
        // Creates a custom enter/exit animation for scale property.
        val scale by
            transition.animateFloat { enterExitState ->
                // Enter transition will be animating the scale from 0.9f to 1.0f
                // (i.e. PreEnter -> Visible). Exit transition will be from 1.0f to
                // 0.5f (i.e. Visible -> PostExit)
                when (enterExitState) {
                    EnterExitState.PreEnter -> 0.9f
                    EnterExitState.Visible -> 1.0f
                    EnterExitState.PostExit -> 0.5f
                }
            }

        // Since we defined `Item` as an extension function on AnimatedVisibilityScope, we can use
        // the `animateEnterExit` modifier to produce an enter/exit animation for it. This will
        // run simultaneously with the `AnimatedVisibility`'s enter/exit.
        Box(
            modifier
                .fillMaxWidth()
                .padding(5.dp)
                .animateEnterExit(
                    // Slide in from below,
                    enter = slideInVertically(initialOffsetY = { it }),
                    // No slide on the way out. So the exit animation will be scale (from the custom
                    // scale animation defined above) and fade (from AnimatedVisibility)
                    exit = ExitTransition.None,
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .fillMaxSize()
        ) {
            // Content of the item goes here...
        }
    }

    @Composable
    fun AnimateMainContent(mainContentVisible: MutableTransitionState<Boolean>) {
        Box {
            // Use the `MutableTransitionState<Boolean>` to specify whether AnimatedVisibility
            // should be visible. This will also allow AnimatedVisibility animation states to be
            // observed externally.
            AnimatedVisibility(
                visibleState = mainContentVisible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box {
                    Column(Modifier.fillMaxSize()) {
                        // We have created `Item`s below as extension functions on
                        // AnimatedVisibilityScope in this example. So they can define their own
                        // enter/exit to run alongside the enter/exit defined in AnimatedVisibility.
                        Item(Modifier.weight(1f), backgroundColor = Color(0xffff6f69))
                        Item(Modifier.weight(1f), backgroundColor = Color(0xffffcc5c))
                    }
                    // This FAB will be simply fading in/out as specified by the AnimatedVisibility
                    FloatingActionButton(
                        onClick = {},
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                        backgroundColor = MaterialTheme.colors.primary,
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                    }
                }
            }

            // Here we can get a signal for when the Enter/Exit animation of the content above
            // has finished by inspecting the MutableTransitionState passed to the
            // AnimatedVisibility. This allows sequential animation after the enter/exit.
            AnimatedVisibility(
                // Once the main content is visible (i.e. targetState == true), and no pending
                // animations. We will start another enter animation sequentially.
                visible = mainContentVisible.targetState && mainContentVisible.isIdle,
                modifier = Modifier.align(Alignment.Center),
                enter = expandVertically(),
                exit = fadeOut(animationSpec = tween(50)),
            ) {
                Text("Transition Finished")
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable

fun AddAnimatedVisibilityToGenericTransitionSample() {
    @Composable
    fun ItemMainContent() {
        Row(Modifier.height(100.dp).fillMaxWidth(), Arrangement.SpaceEvenly) {
            Box(
                Modifier.size(60.dp)
                    .align(Alignment.CenterVertically)
                    .background(Color(0xffcdb7f6), CircleShape)
            )
            Column(Modifier.align(Alignment.CenterVertically)) {
                Box(Modifier.height(30.dp).width(300.dp).padding(5.dp).background(Color.LightGray))
                Box(Modifier.height(30.dp).width(300.dp).padding(5.dp).background(Color.LightGray))
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun SelectableItem() {
        // This sample animates a number of properties, including AnimatedVisibility, as a part of
        // the Transition going between selected and unselected.
        Box(Modifier.padding(15.dp)) {
            var selected by remember { mutableStateOf(false) }
            // Creates a transition to animate visual changes when `selected` is changed.
            val selectionTransition = updateTransition(selected)
            // Animates the border color as a part of the transition
            val borderColor by
                selectionTransition.animateColor { isSelected ->
                    if (isSelected) Color(0xff03a9f4) else Color.White
                }
            // Animates the background color when selected state changes
            val contentBackground by
                selectionTransition.animateColor { isSelected ->
                    if (isSelected) Color(0xffdbf0fe) else Color.White
                }
            // Animates elevation as a part of the transition
            val elevation by
                selectionTransition.animateDp { isSelected -> if (isSelected) 10.dp else 2.dp }
            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(2.dp, borderColor),
                modifier = Modifier.clickable { selected = !selected },
                color = contentBackground,
                elevation = elevation,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    ItemMainContent()
                    // Creates an AnimatedVisibility as a part of the transition, so that when
                    // selected it's visible. This will hoist all the animations that are internal
                    // to AnimatedVisibility (i.e. fade, slide, etc) to the transition. As a result,
                    // `selectionTransition` will not finish until all the animations in
                    // AnimatedVisibility as well as animations added directly to it have finished.
                    selectionTransition.AnimatedVisibility(
                        visible = { it },
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed" +
                                    " eiusmod tempor incididunt labore et dolore magna aliqua. " +
                                    "Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
                                    "laboris nisi ut aliquip ex ea commodo consequat. Duis aute " +
                                    "irure dolor."
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun AnimatedVisibilityLazyColumnSample() {
    val turquoiseColors =
        listOf(
            Color(0xff07688C),
            Color(0xff1986AF),
            Color(0xff50B6CD),
            Color(0xffBCF8FF),
            Color(0xff8AEAE9),
            Color(0xff46CECA),
        )

    // MyModel class handles the data change of the items that are displayed in LazyColumn.
    class MyModel {
        private val _items: MutableList<ColoredItem> = mutableStateListOf()
        private var lastItemId = 0
        val items: List<ColoredItem> = _items

        // Each item has a MutableTransitionState field to track as well as to mutate the item's
        // visibility. When the MutableTransitionState's targetState changes, corresponding
        // transition will be fired. MutableTransitionState allows animation lifecycle to be
        // observed through it's [currentState] and [isIdle]. See below for details.
        inner class ColoredItem(val visible: MutableTransitionState<Boolean>, val itemId: Int) {
            val color: Color
                get() = turquoiseColors.let { it[itemId % it.size] }
        }

        fun addNewItem() {
            lastItemId++
            _items.add(
                ColoredItem(
                    // Here the initial state of the MutableTransitionState is set to false, and
                    // target state is set to true. This will result in an enter transition for
                    // the newly added item.
                    MutableTransitionState(false).apply { targetState = true },
                    lastItemId,
                )
            )
        }

        fun removeItem(item: ColoredItem) {
            // By setting the targetState to false, this will effectively trigger an exit
            // animation in AnimatedVisibility.
            item.visible.targetState = false
        }

        fun pruneItems() {
            // Inspect the animation status through MutableTransitionState. If isIdle == true,
            // all animations have finished for the transition.
            _items.removeAll(
                items.filter {
                    // This checks that the animations have finished && the animations are exit
                    // transitions. In other words, the item has finished animating out.
                    it.visible.isIdle && !it.visible.targetState
                }
            )
        }

        fun removeAll() {
            _items.forEach { it.visible.targetState = false }
        }
    }

    @Composable
    fun AnimatedVisibilityInLazyColumn() {
        Column {
            val model = remember { MyModel() }
            Row(Modifier.fillMaxWidth()) {
                Button({ model.addNewItem() }, modifier = Modifier.padding(15.dp).weight(1f)) {
                    Text("Add")
                }
            }

            // This sets up a flow to check whether any item has finished animating out. If yes,
            // notify the model to prune the list.
            LaunchedEffect(model) {
                snapshotFlow {
                        model.items.firstOrNull { it.visible.isIdle && !it.visible.targetState }
                    }
                    .collect {
                        if (it != null) {
                            model.pruneItems()
                        }
                    }
            }
            LazyColumn {
                items(model.items, key = { it.itemId }) { item ->
                    AnimatedVisibility(
                        item.visible,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Box(Modifier.fillMaxWidth().requiredHeight(90.dp).background(item.color)) {
                            Button(
                                { model.removeItem(item) },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(15.dp),
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            Button({ model.removeAll() }, modifier = Modifier.align(Alignment.End).padding(15.dp)) {
                Text("Clear All")
            }
        }
    }
}


@Composable
fun AVColumnScopeWithMutableTransitionState() {
    var visible by remember { mutableStateOf(true) }
    val colors = remember { listOf(Color(0xff2a9d8f), Color(0xffe9c46a), Color(0xfff4a261)) }
    Column {
        repeat(3) {
            AnimatedVisibility(
                visibleState =
                    remember {
                            // This sets up the initial state of the AnimatedVisibility to false to
                            // guarantee an initial enter transition. In contrast, initializing this
                            // as
                            // `MutableTransitionState(visible)` would result in no initial enter
                            // transition.
                            MutableTransitionState(initialState = false)
                        }
                        .apply {
                            // This changes the target state of the visible state. If it's different
                            // than
                            // the initial state, an enter/exit transition will be triggered.
                            targetState = visible
                        }
            ) { // Content that needs to appear/disappear goes here:
                Box(Modifier.fillMaxWidth().height(100.dp).background(colors[it]))
            }
        }
    }
}


@Composable
fun AnimateEnterExitPartialContent() {
    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun FullScreenNotification(visible: Boolean) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            // Fade in/out the background and foreground
            Box(Modifier.fillMaxSize().background(Color(0x88000000))) {
                Box(
                    Modifier.align(Alignment.TopStart)
                        .animateEnterExit(
                            // Slide in/out the rounded rect
                            enter = slideInVertically(),
                            exit = slideOutVertically(),
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .requiredHeight(100.dp)
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    // Content of the notification goes here
                }
            }
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScaledEnterExit() {
    Column {
        var showRed by remember { mutableStateOf(true) }
        var showGreen by remember { mutableStateOf(true) }

        AnimatedVisibility(
            visible = showGreen,
            // By Default, `scaleIn` uses the center as its pivot point. When used with a vertical
            // expansion from the vertical center, the content will be growing from the center of
            // the vertically expanding layout.
            enter = scaleIn() + expandVertically(expandFrom = Alignment.CenterVertically),
            // By Default, `scaleOut` uses the center as its pivot point. When used with an
            // ExitTransition that shrinks towards the center, the content will be shrinking both
            // in terms of scale and layout size towards the center.
            exit = scaleOut() + shrinkVertically(shrinkTowards = Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(100.dp)
                    .background(color = Color.Green, shape = RoundedCornerShape(20.dp))
            )
        }

        AnimatedVisibility(
            visible = showRed,
            // Scale up from the TopLeft by setting TransformOrigin to (0f, 0f), while expanding the
            // layout size from Top start and fading. This will create a coherent look as if the
            // scale is impacting the size.
            enter =
                scaleIn(transformOrigin = TransformOrigin(0f, 0f)) +
                    fadeIn() +
                    expandIn(expandFrom = Alignment.TopStart),
            // Scale down from the TopLeft by setting TransformOrigin to (0f, 0f), while shrinking
            // the layout towards Top start and fading. This will create a coherent look as if the
            // scale is impacting the layout size.
            exit =
                scaleOut(transformOrigin = TransformOrigin(0f, 0f)) +
                    fadeOut() +
                    shrinkOut(shrinkTowards = Alignment.TopStart),
        ) {
            Box(
                Modifier.size(100.dp)
                    .background(color = Color.Red, shape = RoundedCornerShape(20.dp))
            )
        }
    }
}



@Composable
fun AnimateContent() {
    val shortText = "Hi"
    val longText = "Very long text\nthat spans across\nmultiple lines"
    var short by remember { mutableStateOf(true) }
    Box(
        modifier =
            Modifier.background(Color.Blue, RoundedCornerShape(15.dp))
                .clickable { short = !short }
                .padding(20.dp)
                .wrapContentSize()
                .animateContentSize()
    ) {
        Text(
            if (short) {
                shortText
            } else {
                longText
            },
            style = LocalTextStyle.current.copy(color = Color.White),
        )
    }
}


@Composable
fun CrossfadeSample() {
    Crossfade(targetState = "A") { screen ->
        when (screen) {
            "A" -> Text("Page A")
            "B" -> Text("Page B")
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)

@Composable
fun NestedSharedBoundsSample() {
    // Nested shared bounds sample.
    val selectionColor = Color(0xff3367ba)
    var expanded by remember { mutableStateOf(true) }
    SharedTransitionLayout(
        Modifier.fillMaxSize().clickable { expanded = !expanded }.background(Color(0x88000000))
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(20.dp)
                        .sharedBounds(
                            rememberSharedContentState(key = "container"),
                            this@AnimatedVisibility,
                        )
                        .requiredHeightIn(max = 60.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        Modifier.padding(10.dp)
                            // By using Modifier.skipToLookaheadSize(), we are telling the layout
                            // system to layout the children of this node as if the animations had
                            // all finished. This avoid re-laying out the Row with animated width,
                            // which is _sometimes_ desirable. Try removing this modifier and
                            // observe the effect.
                            .skipToLookaheadSize()
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            modifier =
                                Modifier.padding(
                                    top = 10.dp,
                                    bottom = 10.dp,
                                    start = 10.dp,
                                    end = 20.dp,
                                ),
                        )
                        Icon(
                            Icons.Outlined.Favorite,
                            contentDescription = "Favorite",
                            modifier =
                                Modifier.padding(
                                    top = 10.dp,
                                    bottom = 10.dp,
                                    start = 10.dp,
                                    end = 20.dp,
                                ),
                        )
                        Icon(
                            Icons.Outlined.Create,
                            contentDescription = "Create",
                            tint = Color.White,
                            modifier =
                                Modifier.sharedBounds(
                                        rememberSharedContentState(key = "icon_background"),
                                        this@AnimatedVisibility,
                                    )
                                    .background(selectionColor, RoundedCornerShape(50))
                                    .padding(
                                        top = 10.dp,
                                        bottom = 10.dp,
                                        start = 20.dp,
                                        end = 20.dp,
                                    )
                                    .sharedElement(
                                        rememberSharedContentState(key = "icon"),
                                        this@AnimatedVisibility,
                                    ),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !expanded,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(30.dp)
                        .sharedBounds(
                            rememberSharedContentState(key = "container"),
                            this@AnimatedVisibility,
                            enter = EnterTransition.None,
                        )
                        .sharedBounds(
                            rememberSharedContentState(key = "icon_background"),
                            this@AnimatedVisibility,
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                        ),
                    shape = RoundedCornerShape(30.dp),
                    color = selectionColor,
                ) {
                    Icon(
                        Icons.Outlined.Create,
                        contentDescription = "Create",
                        tint = Color.White,
                        modifier =
                            Modifier.padding(30.dp)
                                .size(40.dp)
                                .sharedElement(
                                    rememberSharedContentState(key = "icon"),
                                    this@AnimatedVisibility,
                                ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)

@Composable
fun SharedElementWithMovableContentSample() {
    var showThumbnail by remember { mutableStateOf(true) }
    val movableContent = remember {
        movableContentOf {
            val cornerRadius = animateDpAsState(targetValue = if (!showThumbnail) 20.dp else 5.dp)
            Image(
                painterResource(id = R.drawable.yt_profile),
                contentDescription = "cute cat",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.clip(shape = RoundedCornerShape(cornerRadius.value)),
            )
        }
    }
    SharedTransitionLayout(
        Modifier.clickable { showThumbnail = !showThumbnail }.fillMaxSize().padding(10.dp)
    ) {
        Column {
            Box(
                // When using Modifier.sharedElementWithCallerManagedVisibility(), even when
                // visible == false, the layout will continue to occupy space in its parent layout.
                // The content will continue to be composed, unless the content is [MovableContent]
                // like in this example below.
                Modifier.sharedElementWithCallerManagedVisibility(
                        rememberSharedContentState(key = "YT"),
                        showThumbnail,
                    )
                    .size(100.dp)
            ) {
                if (showThumbnail) {
                    movableContent()
                }
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xffffcc5c), RoundedCornerShape(5.dp))
            )
            Box(
                Modifier.fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xff2a9d84), RoundedCornerShape(5.dp))
            )
        }
        Box(
            Modifier.fillMaxSize()
                .aspectRatio(1f)
                .sharedElementWithCallerManagedVisibility(
                    rememberSharedContentState(key = "YT"),
                    !showThumbnail,
                )
        ) {
            if (!showThumbnail) {
                movableContent()
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)

@Composable
fun SharedElementWithFABInOverlaySample() {
    // Create an Image that will be shared between the two shared elements.
    @Composable
    fun Cat(modifier: Modifier = Modifier) {
        Image(
            painterResource(id = R.drawable.yt_profile),
            contentDescription = "cute cat",
            contentScale = ContentScale.FillHeight,
            modifier = modifier.clip(shape = RoundedCornerShape(10)),
        )
    }

    var showThumbnail by remember { mutableStateOf(true) }
    SharedTransitionLayout(
        Modifier.clickable { showThumbnail = !showThumbnail }.fillMaxSize().padding(10.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            // Create an AnimatedVisibility for the shared element, so that the layout siblings
            // (i.e. the two boxes below) will move in to fill the space during the exit transition.
            AnimatedVisibility(visible = showThumbnail) {
                Cat(
                    Modifier.size(100.dp)
                        // Create a shared element, using string as the key
                        .sharedElement(
                            rememberSharedContentState(key = "YT"),
                            this@AnimatedVisibility,
                        )
                )
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xffffcc5c), RoundedCornerShape(5.dp))
            )
            Box(
                Modifier.fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xff2a9d84), RoundedCornerShape(5.dp))
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(!showThumbnail) {
                Cat(
                    Modifier.fillMaxSize()
                        // Create another shared element, and make sure the string key matches
                        // the other shared element.
                        .sharedElement(
                            rememberSharedContentState(key = "YT"),
                            this@AnimatedVisibility,
                        )
                )
            }
            FloatingActionButton(
                modifier =
                    Modifier.padding(20.dp)
                        .align(Alignment.BottomEnd)
                        // During shared element transition, shared elements will be rendered in
                        // overlay to escape any clipping or layer transform from parents. It also
                        // means they will render over on top of UI elements such as Floating Action
                        // Button. Once the transition is finished, they will be dropped from the
                        // overlay to their own DrawScopes. To help support keeping specific UI
                        // elements always on top, Modifier.renderInSharedTransitionScopeOverlay
                        // will temporarily elevate them into the overlay as well. By default,
                        // this modifier keeps content in overlay during the time when the
                        // shared transition is active (i.e.
                        // SharedTransitionScope#isTransitionActive).
                        // The duration can be customize via `renderInOverlay` parameter.
                        .renderInSharedTransitionScopeOverlay(
                            // zIndexInOverlay by default is 0f for this modifier and for shared
                            // elements. By overwriting zIndexInOverlay to 1f, we can ensure this
                            // FAB is rendered on top of the shared elements.
                            zIndexInOverlay = 1f
                        ),
                onClick = {},
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "favorite")
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable

fun SharedElementInAnimatedContentSample() {
    // This is the Image that we will add shared element modifier on. It's important to make sure
    // modifiers that are not shared between the two shared elements (such as size modifiers if
    // the size changes) are the parents (i.e. on the left side) of Modifier.sharedElement.
    // Meanwhile, the modifiers that are shared between the shared elements (e.g. Modifier.clip
    // in this case) are on the right side of the Modifier.sharedElement.
    @Composable
    fun Cat(modifier: Modifier = Modifier) {
        Image(
            painterResource(id = R.drawable.yt_profile),
            contentDescription = "cute cat",
            contentScale = ContentScale.FillHeight,
            modifier = modifier.clip(shape = RoundedCornerShape(10)),
        )
    }

    // Shared element key is of type `Any`, which means it can be id, string, etc. The only
    // requirement for the key is that it should be the same for shared elements that you intend
    // to match. Here we use the image resource id as the key.
    val sharedElementKey = R.drawable.yt_profile
    var showLargeImage by remember { mutableStateOf(true) }

    // First, we need to create a SharedTransitionLayout, this Layout will provide the coordinator
    // space for shared element position animation, as well as an overlay for shared elements to
    // render in. Children content in this Layout will be able to create shared element transition
    // using the receiver scope: SharedTransitionScope
    SharedTransitionLayout(
        Modifier.clickable { showLargeImage = !showLargeImage }.fillMaxSize().padding(10.dp)
    ) {
        // In the SharedTransitionLayout, we will be able to access the receiver scope (i.e.
        // SharedTransitionScope) in order to create shared element transition.
        AnimatedContent(targetState = showLargeImage) { showLargeImageMode ->
            if (showLargeImageMode) {
                Cat(
                    Modifier.fillMaxSize()
                        .aspectRatio(1f)
                        // Creating a shared element. Note that this modifier is *after*
                        // the size modifier and aspectRatio modifier, because those size specs
                        // are not shared between the two shared elements.
                        .sharedElement(
                            rememberSharedContentState(sharedElementKey),
                            // Using the AnimatedVisibilityScope from the AnimatedContent
                            // defined above.
                            this@AnimatedContent,
                        )
                )
                Text(
                    "Cute Cat YT",
                    fontSize = 40.sp,
                    color = Color.Blue,
                    // Prefer Modifier.sharedBounds for text, unless the texts in both initial
                    // content and target content are exactly the same (i.e. same
                    // size/font/color)
                    modifier =
                        Modifier.fillMaxWidth()
                            // IMPORTANT: Prefer using wrapContentWidth/wrapContentSize over
                            // textAlign
                            // for shared text transition. This allows the layout system sees actual
                            // position and size of the text to facilitate bounds animation.
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .sharedBounds(
                                rememberSharedContentState(key = "text"),
                                this@AnimatedContent,
                            ),
                )
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cat(
                            Modifier.size(100.dp)
                                // Creating another shared element with the same key.
                                // Note that this modifier is *after* the size modifier,
                                // The size changes between these two shared elements, i.e. the size
                                // is not shared between the two shared elements.
                                .sharedElement(
                                    rememberSharedContentState(sharedElementKey),
                                    this@AnimatedContent,
                                )
                        )
                        Text(
                            "Cute Cat YT",
                            // Change text color & size
                            fontSize = 20.sp,
                            color = Color.DarkGray,
                            // Prefer Modifier.sharedBounds for text, unless the texts in both
                            // initial content and target content are exactly the same (i.e. same
                            // size/font/color)
                            modifier =
                                Modifier
                                    // The modifier that is not a part of the shared content, but
                                    // rather
                                    // for positioning and sizes should be on the *left* side of
                                    // sharedBounds/sharedElement.
                                    .padding(start = 20.dp)
                                    .sharedBounds(
                                        // Here we use a string-based key, in contrast to the key
                                        // above.
                                        rememberSharedContentState(key = "text"),
                                        this@AnimatedContent,
                                    ),
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xffffcc5c), RoundedCornerShape(5.dp))
                    )
                    Box(
                        Modifier.fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xff2a9d84), RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}

private enum class ComponentState {
    Pressed,
    Released,
}


@Composable
fun GestureAnimationSample() {
    // enum class ComponentState { Pressed, Released }
    var useRed by remember { mutableStateOf(false) }
    var toState by remember { mutableStateOf(ComponentState.Released) }
    val modifier =
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    toState = ComponentState.Pressed
                    tryAwaitRelease()
                    toState = ComponentState.Released
                }
            )
        }

    // Defines a transition of `ComponentState`, and updates the transition when the provided
    // [targetState] changes. The tran
    // sition will run all of the child animations towards the new
    // [targetState] in response to the [targetState] change.
    val transition: Transition<ComponentState> = updateTransition(targetState = toState)
    // Defines a float animation as a child animation the transition. The current animation value
    // can be read from the returned State<Float>.
    val scale: Float by
        transition.animateFloat(
            // Defines a transition spec that uses the same low-stiffness spring for *all*
            // transitions of this float, no matter what the target is.
            transitionSpec = { spring(stiffness = 50f) }
        ) { state ->
            // This code block declares a mapping from state to value.
            if (state == ComponentState.Pressed) 3f else 1f
        }

    // Defines a color animation as a child animation of the transition.
    val color: Color by
        transition.animateColor(
            transitionSpec = {
                when {
                    ComponentState.Pressed isTransitioningTo ComponentState.Released ->
                        // Uses spring for the transition going from pressed to released
                        spring(stiffness = 50f)
                    else ->
                        // Uses tween for all the other transitions. (In this case there is
                        // only one other transition. i.e. released -> pressed.)
                        tween(durationMillis = 500)
                }
            }
        ) { state ->
            when (state) {
                // Similar to the float animation, we need to declare the target values
                // for each state. In this code block we can access theme colors.
                ComponentState.Pressed -> MaterialTheme.colors.primary
                // We can also have the target value depend on other mutableStates,
                // such as `useRed` here. Whenever the target value changes, transition
                // will automatically animate to the new value even if it has already
                // arrived at its target state.
                ComponentState.Released -> if (useRed) Color.Red else MaterialTheme.colors.secondary
            }
        }
    Column {
        Button(
            modifier = Modifier.padding(10.dp).align(Alignment.CenterHorizontally),
            onClick = { useRed = !useRed },
        ) {
            Text("Change Color")
        }
        Box(
            modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .size((100 * scale).dp)
                .background(color)
        )
    }
}


@Composable
fun InfiniteTransitionSample() {
    @Composable
    fun InfinitelyPulsingHeart() {
        // Creates an [InfiniteTransition] instance for managing child animations.
        val infiniteTransition = rememberInfiniteTransition()

        // Creates a child animation of float type as a part of the [InfiniteTransition].
        val scale by
            infiniteTransition.animateFloat(
                initialValue = 3f,
                targetValue = 6f,
                animationSpec =
                    infiniteRepeatable(
                        // Infinitely repeating a 1000ms tween animation using default easing curve.
                        animation = tween(1000),
                        // After each iteration of the animation (i.e. every 1000ms), the animation
                        // will
                        // start again from the [initialValue] defined above.
                        // This is the default [RepeatMode]. See [RepeatMode.Reverse] below for an
                        // alternative.
                        repeatMode = RepeatMode.Restart,
                    ),
            )

        // Creates a Color animation as a part of the [InfiniteTransition].
        val color by
            infiniteTransition.animateColor(
                initialValue = Color.Red,
                targetValue = Color(0xff800000), // Dark Red
                animationSpec =
                    infiniteRepeatable(
                        // Linearly interpolate between initialValue and targetValue every 1000ms.
                        animation = tween(1000, easing = LinearEasing),
                        // Once [TargetValue] is reached, starts the next iteration in reverse (i.e.
                        // from
                        // TargetValue to InitialValue). Then again from InitialValue to
                        // TargetValue. This
                        // [RepeatMode] ensures that the animation value is *always continuous*.
                        repeatMode = RepeatMode.Reverse,
                    ),
            )

        Box(Modifier.fillMaxSize()) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.Center).graphicsLayer(scaleX = scale, scaleY = scale),
                tint = color,
            )
        }
    }
}



@Composable
fun AnimatableAnimateToGenericsType() {
    // Creates an `Animatable` to animate Offset and `remember` it.
    val animatedOffset = remember { Animatable(Offset(0f, 0f), Offset.VectorConverter) }

    Box(
        Modifier.fillMaxSize().background(Color(0xffb99aff)).pointerInput(Unit) {
            coroutineScope {
                while (true) {
                    val offset = awaitPointerEventScope { awaitFirstDown().position }
                    // Launch a new coroutine for animation so the touch detection thread is not
                    // blocked.
                    launch {
                        // Animates to the pressed position, with the given animation spec.
                        animatedOffset.animateTo(
                            offset,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                        )
                    }
                }
            }
        }
    ) {
        Text("Tap anywhere", Modifier.align(Alignment.Center))
        Box(
            Modifier.offset {
                    // Use the animated offset as the offset of the Box.
                    IntOffset(
                        animatedOffset.value.x.roundToInt(),
                        animatedOffset.value.y.roundToInt(),
                    )
                }
                .size(40.dp)
                .background(Color(0xff3c1361), CircleShape)
        )
    }
}


fun AnimatableDecayAndAnimateToSample() {
    /**
     * In this example, we create a swipe-to-dismiss modifier that dismisses the child via a
     * vertical swipe-up.
     */
    fun Modifier.swipeToDismiss(): Modifier = composed {
        // Creates a Float type `Animatable` and `remember`s it
        val animatedOffsetY = remember { Animatable(0f) }
        this.pointerInput(Unit) {
                coroutineScope {
                    while (true) {
                        val pointerId = awaitPointerEventScope { awaitFirstDown().id }
                        val velocityTracker = VelocityTracker()
                        awaitPointerEventScope {
                            verticalDrag(pointerId) {
                                // Snaps the value by the amount of finger movement
                                launch {
                                    animatedOffsetY.snapTo(
                                        animatedOffsetY.value + it.positionChange().y
                                    )
                                }
                                velocityTracker.addPosition(it.uptimeMillis, it.position)
                            }
                        }
                        // At this point, drag has finished. Now we obtain the velocity at the end
                        // of
                        // the drag, and animate the offset with it as the starting velocity.
                        val velocity = velocityTracker.calculateVelocity().y

                        // The goal for the animation below is to animate the dismissal if the fling
                        // velocity is high enough. Otherwise, spring back.
                        launch {
                            // Checks where the animation will end using decay
                            val decay = splineBasedDecay<Float>(this@pointerInput)

                            // If the animation can naturally end outside of visual bounds, we will
                            // animate with decay.
                            if (
                                decay.calculateTargetValue(animatedOffsetY.value, velocity) <
                                    -size.height
                            ) {
                                // (Optionally) updates lower bounds. This stops the animation as
                                // soon
                                // as bounds are reached.
                                animatedOffsetY.updateBounds(lowerBound = -size.height.toFloat())
                                // Animate with the decay animation spec using the fling velocity
                                animatedOffsetY.animateDecay(velocity, decay)
                            } else {
                                // Not enough velocity to be dismissed, spring back to 0f
                                animatedOffsetY.animateTo(0f, initialVelocity = velocity)
                            }
                        }
                    }
                }
            }
            .offset { IntOffset(0, animatedOffsetY.value.roundToInt()) }
    }
}


fun AnimatableAnimationResultSample() {
    suspend fun CoroutineScope.animateBouncingOffBounds(
        animatable: Animatable<Offset, *>,
        flingVelocity: Offset,
        parentSize: Size,
    ) {
        launch {
            var startVelocity = flingVelocity
            // Set bounds for the animation, so that when it reaches bounds it will stop
            // immediately. We can then inspect the returned `AnimationResult` and decide whether
            // we should start another animation.
            animatable.updateBounds(Offset(0f, 0f), Offset(parentSize.width, parentSize.height))
            do {
                val result = animatable.animateDecay(startVelocity, exponentialDecay())
                // Copy out the end velocity of the previous animation.
                startVelocity = result.endState.velocity

                // Negate the velocity for the dimension that hits the bounds, to create a
                // bouncing off the bounds effect.
                with(animatable) {
                    if (value.x == upperBound?.x || value.x == lowerBound?.x) {
                        // x dimension hits bounds
                        startVelocity = startVelocity.copy(x = -startVelocity.x)
                    }
                    if (value.y == upperBound?.y || value.y == lowerBound?.y) {
                        // y dimension hits bounds
                        startVelocity = startVelocity.copy(y = -startVelocity.y)
                    }
                }
                // Repeat the animation until the animation ends for reasons other than hitting
                // bounds, e.g. if `stop()` is called, or preempted by another animation.
            } while (result.endReason == AnimationEndReason.BoundReached)
        }
    }
}


fun AnimatableFadeIn() {
    fun Modifier.fadeIn(): Modifier = composed {
        // Creates an `Animatable` and remembers it.
        val alphaAnimation = remember { Animatable(0f) }
        // Launches a coroutine for the animation when entering the composition.
        // Uses `alphaAnimation` as the subject so the job in `LaunchedEffect` will run only when
        // `alphaAnimation` is created, which happens one time when the modifier enters
        // composition.
        LaunchedEffect(alphaAnimation) {
            // Animates to 1f from 0f for the fade-in, and uses a 500ms tween animation.
            alphaAnimation.animateTo(
                targetValue = 1f,
                // Default animationSpec uses [spring] animation, here we overwrite the default.
                animationSpec = tween(500),
            )
        }
        this.graphicsLayer(alpha = alphaAnimation.value)
    }
}

@OptIn(ExperimentalAnimatableApi::class)

@Composable
fun DeferredTargetAnimationSample() {
    // Creates a custom modifier that animates the constraints and measures child with the
    // animated constraints. This modifier is built on top of `Modifier.approachLayout` to approach
    // th destination size determined by the lookahead pass. A resize animation will be kicked off
    // whenever the lookahead size changes, to animate children from current size to destination
    // size. Fixed constraints created based on the animation value will be used to measure
    // child, so the child layout gradually changes its animated constraints until the approach
    // completes.
    fun Modifier.animateConstraints(
        sizeAnimation: DeferredTargetAnimation<IntSize, AnimationVector2D>,
        coroutineScope: CoroutineScope,
    ) =
        this.approachLayout(
            isMeasurementApproachInProgress = { lookaheadSize ->
                // Update the target of the size animation.
                sizeAnimation.updateTarget(lookaheadSize, coroutineScope)
                // Return true if the size animation has pending target change or is currently
                // running.
                !sizeAnimation.isIdle
            }
        ) { measurable, _ ->
            // In the measurement approach, the goal is to gradually reach the destination size
            // (i.e. lookahead size). To achieve that, we use an animation to track the current
            // size, and animate to the destination size whenever it changes. Once the animation
            // finishes, the approach is complete.

            // First, update the target of the animation, and read the current animated size.
            val (width, height) = sizeAnimation.updateTarget(lookaheadSize, coroutineScope)
            // Then create fixed size constraints using the animated size
            val animatedConstraints = Constraints.fixed(width, height)
            // Measure child with animated constraints.
            val placeable = measurable.measure(animatedConstraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

    var fullWidth by remember { mutableStateOf(false) }

    // Creates a size animation with a target unknown at the time of instantiation.
    val sizeAnimation = remember { DeferredTargetAnimation(IntSize.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()
    Row(
        (if (fullWidth) Modifier.fillMaxWidth() else Modifier.width(100.dp))
            .height(200.dp)
            // Use the custom modifier created above to animate the constraints passed
            // to the child, and therefore resize children in an animation.
            .animateConstraints(sizeAnimation, coroutineScope)
            .clickable { fullWidth = !fullWidth }
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xffff6f69)))
        Box(Modifier.weight(2f).fillMaxHeight().background(Color(0xffffcc5c)))
    }
}



@Composable
fun AlphaAnimationSample() {
    @Composable
    fun alphaAnimation(visible: Boolean) {
        // Animates to 1f or 0f based on [visible].
        // This [animateState] returns a State<Float> object. The value of the State object is
        // being updated by animation. (This method is overloaded for different parameter types.)
        // Here we use the returned [State] object as a property delegate.
        val alpha: Float by animateFloatAsState(if (visible) 1f else 0f)

        // Updates the alpha of a graphics layer with the float animation value. It is more
        // performant to modify alpha in a graphics layer than using `Modifier.alpha`. The former
        // limits the invalidation scope of alpha change to graphicsLayer's draw stage (i.e. no
        // recomposition would be needed). The latter triggers recomposition on each animation
        // frame.
        Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }.background(Color.Red))
    }
}

data class MySize(val width: Dp, val height: Dp)


@Composable
fun ArbitraryValueTypeTransitionSample() {
    @Composable
    fun ArbitraryValueTypeAnimation(enabled: Boolean) {
        // Sets up the different animation target values based on the [enabled] flag.
        val mySize =
            remember(enabled) {
                if (enabled) {
                    MySize(500.dp, 500.dp)
                } else {
                    MySize(100.dp, 100.dp)
                }
            }

        // Animates a custom type value to the given target value, using a [TwoWayConverter]. The
        // converter tells the animation system how to convert the custom type from and to
        // [AnimationVector], so that it can be animated.
        val animSize: MySize by
            animateValueAsState(
                mySize,
                TwoWayConverter<MySize, AnimationVector2D>(
                    convertToVector = { AnimationVector2D(it.width.value, it.height.value) },
                    convertFromVector = { MySize(it.v1.dp, it.v2.dp) },
                ),
            )
        Box(Modifier.size(animSize.width, animSize.height).background(color = Color.Red))
    }
}


@Composable
fun DpAnimationSample() {
    @Composable
    fun HeightAnimation(collapsed: Boolean) {
        // Animates a height of [Dp] type to different target values based on the [collapsed] flag.
        val height: Dp by animateDpAsState(if (collapsed) 10.dp else 20.dp)
        Box(Modifier.fillMaxWidth().requiredHeight(height).background(color = Color.Red))
    }
}


@Composable
@Suppress("UNUSED_VARIABLE")
fun AnimateOffsetSample() {
    @Composable
    fun OffsetAnimation(selected: Boolean) {
        // Animates the offset depending on the selected flag.
        // [animateOffsetAsState] returns a State<Offset> object. The value of the State object is
        // updated by the animation. Here we use that State<Offset> as a property delegate.
        val offset: Offset by
            animateOffsetAsState(if (selected) Offset(0f, 0f) else Offset(20f, 20f))

        // In this example, animateIntOffsetAsState returns a State<IntOffset>. The value of the
        // returned
        // State object is updated by the animation.
        val intOffset: IntOffset by
            animateIntOffsetAsState(if (selected) IntOffset(0, 0) else IntOffset(50, 50))
    }
}



@Composable
fun InfiniteProgressIndicator() {
    // This is an infinite progress indicator with 3 pulsing dots that grow and shrink.
    @Composable
    fun Dot(scale: State<Float>) {
        Box(
            Modifier.padding(5.dp)
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(Color.Gray, shape = CircleShape)
        )
    }

    val infiniteTransition = rememberInfiniteTransition()
    val scale1 =
        infiniteTransition.animateFloat(
            0.2f,
            1f,
            // No offset for the 1st animation
            infiniteRepeatable(tween(600), RepeatMode.Reverse),
        )
    val scale2 =
        infiniteTransition.animateFloat(
            0.2f,
            1f,
            infiniteRepeatable(
                tween(600),
                RepeatMode.Reverse,
                // Offsets the 2nd animation by starting from 150ms of the animation
                // This offset will not be repeated.
                initialStartOffset = StartOffset(offsetMillis = 150, StartOffsetType.FastForward),
            ),
        )
    val scale3 =
        infiniteTransition.animateFloat(
            0.2f,
            1f,
            infiniteRepeatable(
                tween(600),
                RepeatMode.Reverse,
                // Offsets the 3rd animation by starting from 300ms of the animation. This
                // offset will be not repeated.
                initialStartOffset = StartOffset(offsetMillis = 300, StartOffsetType.FastForward),
            ),
        )
    Row {
        Dot(scale1)
        Dot(scale2)
        Dot(scale3)
    }
}



fun OffsetArcAnimationSpec() {
    // Will interpolate the Offset in arcs such that the curve of the quarter of an Ellipse is above
    // the center.
    ArcAnimationSpec<Offset>(mode = ArcAbove)
}


fun OffsetKeyframesWithArcsBuilder() {
    keyframes<Offset> {
        // Animate for 1.2 seconds
        durationMillis = 1200

        // Animate to Offset(100f, 100f) at 50% of the animation using LinearEasing then, animate
        // using ArcAbove for the rest of the animation
        Offset(100f, 100f) atFraction 0.5f using LinearEasing using ArcAbove
    }
}


@Composable
fun InfiniteTransitionSample() {
    @Composable
    fun InfinitelyPulsingHeart() {
        // Creates an [InfiniteTransition] instance for managing child animations.
        val infiniteTransition = rememberInfiniteTransition()

        // Creates a child animation of float type as a part of the [InfiniteTransition].
        val scale by
            infiniteTransition.animateFloat(
                initialValue = 3f,
                targetValue = 6f,
                animationSpec =
                    infiniteRepeatable(
                        // Infinitely repeating a 1000ms tween animation using default easing curve.
                        animation = tween(1000),
                        // After each iteration of the animation (i.e. every 1000ms), the animation
                        // will
                        // start again from the [initialValue] defined above.
                        // This is the default [RepeatMode]. See [RepeatMode.Reverse] below for an
                        // alternative.
                        repeatMode = RepeatMode.Restart,
                    ),
            )

        // Creates a Color animation as a part of the [InfiniteTransition].
        val color by
            infiniteTransition.animateColor(
                initialValue = Color.Red,
                targetValue = Color(0xff800000), // Dark Red
                animationSpec =
                    infiniteRepeatable(
                        // Linearly interpolate between initialValue and targetValue every 1000ms.
                        animation = tween(1000, easing = LinearEasing),
                        // Once [TargetValue] is reached, starts the next iteration in reverse (i.e.
                        // from
                        // TargetValue to InitialValue). Then again from InitialValue to
                        // TargetValue. This
                        // [RepeatMode] ensures that the animation value is *always continuous*.
                        repeatMode = RepeatMode.Reverse,
                    ),
            )

        Box(Modifier.fillMaxSize()) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.Center).graphicsLayer(scaleX = scale, scaleY = scale),
                tint = color,
            )
        }
    }
}


@Composable
fun InfiniteTransitionAnimateValueSample() {
    // Creates an [InfiniteTransition] instance to run child animations.
    val infiniteTransition = rememberInfiniteTransition()
    // Infinitely animate a Dp offset from 0.dp to 100.dp
    val offsetX by
        infiniteTransition.animateValue(
            initialValue = 0.dp,
            targetValue = 100.dp,
            typeConverter = Dp.VectorConverter,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = 500
                            0.dp at 200 // ms
                            80.dp at 300 using FastOutLinearInEasing
                        }
                    // Use the default RepeatMode.Restart to start from 0.dp after each iteration
                ),
        )

    Box(Modifier.offset(x = offsetX)) {
        // Content goes here
    }
}

fun FloatKeyframesBuilder() {
    KeyframesSpec(
        KeyframesSpec.KeyframesSpecConfig<Float>().apply {
            0f at 0 // ms  // Optional
            0.4f at 75 // ms
            0.4f at 225 // ms
            0f at 375 // ms  // Optional
            durationMillis = 375
        }
    )
}


fun FloatKeyframesBuilderInline() {
    keyframes {
        0f at 0 // ms  // Optional
        0.4f at 75 // ms
        0.4f at 225 // ms
        0f at 375 // ms  // Optional
        durationMillis = 375
    }
}


fun KeyframesBuilderWithEasing() {
    // Use FastOutSlowInEasing for the interval from 0 to 50 ms, and LinearOutSlowInEasing for the
    // time between 50 and 100ms
    keyframes<Float> {
        durationMillis = 100
        0f at 0 using FastOutSlowInEasing
        1.5f at 50 using LinearOutSlowInEasing
        1f at 100
    }
}


fun KeyframesBuilderForPosition() {
    // Use FastOutSlowInEasing for the interval from 0 to 50 ms, and LinearOutSlowInEasing for the
    // time between 50 and 100ms
    keyframes<DpOffset> {
        durationMillis = 200
        DpOffset(0.dp, 0.dp) at 0 using LinearEasing
        DpOffset(500.dp, 100.dp) at 100 using LinearOutSlowInEasing
        DpOffset(400.dp, 50.dp) at 150
    }
}


fun KeyframesSpecBaseConfig<Float, KeyframesSpec.KeyframeEntity<Float>>.floatAtSample() {
    0.8f at 150 // ms
}


fun KeyframesSpecBaseConfig<Float, KeyframesSpec.KeyframeEntity<Float>>.floatAtFractionSample() {
    // Make sure to set the duration before calling `atFraction` otherwise the keyframe will be set
    // based on the default duration
    durationMillis = 300
    0.8f atFraction 0.50f // half of the overall duration set
}

@OptIn(ExperimentalAnimationSpecApi::class)

fun KeyframesBuilderForOffsetWithSplines() {
    keyframesWithSpline {
        durationMillis = 200
        Offset(0f, 0f) at 0
        Offset(500f, 100f) at 100
        Offset(400f, 50f) at 150
    }
}

@OptIn(ExperimentalAnimationSpecApi::class)

fun KeyframesBuilderForIntOffsetWithSplines() {
    keyframesWithSpline {
        durationMillis = 200
        IntOffset(0, 0) at 0
        IntOffset(500, 100) at 100
        IntOffset(400, 50) at 150
    }
}

@OptIn(ExperimentalAnimationSpecApi::class)

fun KeyframesBuilderForDpOffsetWithSplines() {
    keyframesWithSpline {
        durationMillis = 200
        DpOffset(0.dp, 0.dp) at 0
        DpOffset(500.dp, 100.dp) at 100
        DpOffset(400.dp, 50.dp) at 150
    }
}

@OptIn(ExperimentalAnimationSpecApi::class)
@Composable

fun PeriodicKeyframesWithSplines() {
    var alpha by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    // With a periodicBias of 0.5f it creates a similar animation to a sinusoidal
                    // curve
                    // so the transition as the animation repeats is completely seamless
                    animation =
                        keyframesWithSpline(periodicBias = 0.5f) {
                            durationMillis = 2000

                            1f at 1000 using LinearEasing
                        },
                    repeatMode = RepeatMode.Restart,
                ),
        ) { value, _ ->
            alpha = value
        }
    }
    Image(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        modifier = Modifier.size(150.dp).graphicsLayer { this.alpha = alpha },
        colorFilter = ColorFilter.tint(Color.Red),
    )
}



@Composable
fun PathEasingSample() {
    // Creates a custom PathEasing curve and applies it to an animation
    var toggled by remember { mutableStateOf(true) }
    val pathForAnimation = remember {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        }
    }
    val offset by
        animateIntOffsetAsState(
            targetValue = if (toggled) IntOffset.Zero else IntOffset(300, 300),
            label = "offset",
            animationSpec = tween(durationMillis = 1000, easing = PathEasing(pathForAnimation)),
        )
    Box(modifier = Modifier.fillMaxSize().clickable { toggled = !toggled }) {
        Box(modifier = Modifier.offset { offset }.size(100.dp).background(Color.Blue))
    }
}


fun animateToOnAnimationState() {
    @Composable
    fun simpleAnimate(target: Float): Float {
        // Create an AnimationState to be updated by the animation.
        val animationState = remember { AnimationState(target) }

        // Launch the suspend animation into the composition's CoroutineContext, and pass
        // `target` to LaunchedEffect so that when`target` changes the old animation job is
        // canceled, and a new animation is created with a new target.
        LaunchedEffect(target) {
            // This starts an animation that updates the animationState on each frame
            animationState.animateTo(
                targetValue = target,
                // Use a low stiffness spring. This can be replaced with any type of `AnimationSpec`
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                // If the previous animation was interrupted (i.e. not finished), configure the
                // animation as a sequential animation to continue from the time the animation was
                // interrupted.
                sequentialAnimation = !animationState.isFinished,
            )
            // When the function above returns, the animation has finished.
        }
        // Return the value updated by the animation.
        return animationState.value
    }
}


fun suspendAnimateFloatVariant() {
    @Composable
    fun InfiniteAnimationDemo() {
        // Create a mutable state for alpha, and update it in the animation.
        val alpha = remember { mutableStateOf(1f) }
        LaunchedEffect(Unit) {
            // Animate from 1f to 0f using an infinitely repeating animation
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec =
                    infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
            ) { value, /* velocity */ _ ->
                // Update alpha mutable state with the current animation value
                alpha.value = value
            }
        }
        Box(Modifier.fillMaxSize()) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.Center)
                        .graphicsLayer(scaleX = 3.0f, scaleY = 3.0f, alpha = alpha.value),
                tint = Color.Red,
            )
        }
    }
}


@Composable
fun GestureAnimationSample() {
    // enum class ComponentState { Pressed, Released }
    var useRed by remember { mutableStateOf(false) }
    var toState by remember { mutableStateOf(ComponentState.Released) }
    val modifier =
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    toState = ComponentState.Pressed
                    tryAwaitRelease()
                    toState = ComponentState.Released
                }
            )
        }

    // Defines a transition of `ComponentState`, and updates the transition when the provided
    // [targetState] changes. The transition will run all of the child animations towards the new
    // [targetState] in response to the [targetState] change.
    val transition: Transition<ComponentState> = updateTransition(targetState = toState)
    // Defines a float animation as a child animation the transition. The current animation value
    // can be read from the returned State<Float>.
    val scale: Float by
        transition.animateFloat(
            // Defines a transition spec that uses the same low-stiffness spring for *all*
            // transitions of this float, no matter what the target is.
            transitionSpec = { spring(stiffness = 50f) }
        ) { state ->
            // This code block declares a mapping from state to value.
            if (state == ComponentState.Pressed) 3f else 1f
        }

    // Defines a color animation as a child animation of the transition.
    val color: Color by
        transition.animateColor(
            transitionSpec = {
                when {
                    ComponentState.Pressed isTransitioningTo ComponentState.Released ->
                        // Uses spring for the transition going from pressed to released
                        spring(stiffness = 50f)
                    else ->
                        // Uses tween for all the other transitions. (In this case there is
                        // only one other transition. i.e. released -> pressed.)
                        tween(durationMillis = 500)
                }
            }
        ) { state ->
            when (state) {
                // Similar to the float animation, we need to declare the target values
                // for each state. In this code block we can access theme colors.
                ComponentState.Pressed -> MaterialTheme.colors.primary
                // We can also have the target value depend on other mutableStates,
                // such as `useRed` here. Whenever the target value changes, transition
                // will automatically animate to the new value even if it has already
                // arrived at its target state.
                ComponentState.Released -> if (useRed) Color.Red else MaterialTheme.colors.secondary
            }
        }
    Column {
        Button(
            modifier = Modifier.padding(10.dp).align(Alignment.CenterHorizontally),
            onClick = { useRed = !useRed },
        ) {
            Text("Change Color")
        }
        Box(
            modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .size((100 * scale).dp)
                .background(color)
        )
    }
}

private enum class SquareSize {
    Small,
    Large,
}

private enum class ComponentState {
    Pressed,
    Released,
}

private enum class ButtonStatus {
    Initial,
    Pressed,
    Released,
}


@Composable
fun AnimateFloatSample() {
    // enum class ButtonStatus {Initial, Pressed, Released}
    @Composable
    fun AnimateAlphaAndScale(modifier: Modifier, transition: Transition<ButtonStatus>) {
        // Defines a float animation as a child animation of transition. This allows the
        // transition to manage the states of this animation. The returned State<Float> from the
        // [animateFloat] function is used here as a property delegate.
        // This float animation will use the default [spring] for all transition destinations, as
        // specified by the default `transitionSpec`.
        val scale: Float by
            transition.animateFloat { state -> if (state == ButtonStatus.Pressed) 1.2f else 1f }

        // Alternatively, we can specify different animation specs based on the initial state and
        // target state of the a transition run using `transitionSpec`.
        val alpha: Float by
            transition.animateFloat(
                transitionSpec = {
                    when {
                        ButtonStatus.Initial isTransitioningTo ButtonStatus.Pressed -> {
                            keyframes {
                                durationMillis = 225
                                0f at 0 // optional
                                0.3f at 75
                                0.2f at 225 // optional
                            }
                        }
                        ButtonStatus.Pressed isTransitioningTo ButtonStatus.Released -> {
                            tween(durationMillis = 220)
                        }
                        else -> {
                            snap()
                        }
                    }
                }
            ) { state ->
                // Same target value for Initial and Released states
                if (state == ButtonStatus.Pressed) 0.2f else 0f
            }

        Box(modifier.graphicsLayer(alpha = alpha, scaleX = scale)) {
            // content goes here
        }
    }
}

@OptIn(ExperimentalTransitionApi::class)

fun InitialStateSample() {
    // This composable enters the composition with a custom enter transition. This is achieved by
    // defining a different initialState than the first target state using `MutableTransitionState`
    @Composable
    fun PoppingInCard() {
        // Creates a transition state with an initial state where visible = false
        val visibleState = remember { MutableTransitionState(false) }
        // Sets the target state of the transition state to true. As it's different than the initial
        // state, a transition from not visible to visible will be triggered.
        visibleState.targetState = true

        // Creates a transition with the transition state created above.
        val transition = rememberTransition(visibleState)
        // Adds a scale animation to the transition to scale the card up when transitioning in.
        val scale by
            transition.animateFloat(
                // Uses a custom spring for the transition.
                transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy) }
            ) { visible ->
                if (visible) 1f else 0.8f
            }
        // Adds an elevation animation that animates the dp value of the animation.
        val elevation by
            transition.animateDp(
                // Uses a tween animation
                transitionSpec = {
                    // Uses different animations for when animating from visible to not visible, and
                    // the other way around
                    if (false isTransitioningTo true) {
                        tween(1000)
                    } else {
                        spring()
                    }
                }
            ) { visible ->
                if (visible) 10.dp else 0.dp
            }

        Card(
            Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                .size(200.dp, 100.dp)
                .fillMaxWidth(),
            elevation = elevation,
        ) {}
    }
}

enum class LikedStates {
    Initial,
    Liked,
    Disappeared,
}


@Composable
fun DoubleTapToLikeSample() {
    // enum class LikedStates { Initial, Liked, Disappeared }
    @Composable
    fun doubleTapToLike() {
        // Creates a transition state that starts in [Disappeared] State
        var transitionState by remember {
            mutableStateOf(MutableTransitionState(LikedStates.Disappeared))
        }

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // This creates a new `MutableTransitionState` object. When a new
                        // `MutableTransitionState` object gets passed to `updateTransition`, a
                        // new transition will be created. All existing values, velocities will
                        // be lost as a result. Hence, in most cases, this is not recommended.
                        // The exception is when it's more important to respond immediately to
                        // user interaction than preserving continuity.
                        transitionState = MutableTransitionState(LikedStates.Initial)
                    }
                )
            }
        ) {
            // This ensures sequential states: Initial -> Liked -> Disappeared
            if (transitionState.currentState == LikedStates.Initial) {
                transitionState.targetState = LikedStates.Liked
            } else if (transitionState.currentState == LikedStates.Liked) {
                // currentState will be updated to targetState when the transition is finished, so
                // it can be used as a signal to start the next transition.
                transitionState.targetState = LikedStates.Disappeared
            }

            // Creates a transition using the TransitionState object that gets recreated at each
            // double tap.
            val transition = rememberTransition(transitionState)
            // Creates an alpha animation, as a part of the transition.
            val alpha by
                transition.animateFloat(
                    transitionSpec = {
                        when {
                            // Uses different animation specs for transitioning from/to different
                            // states
                            LikedStates.Initial isTransitioningTo LikedStates.Liked ->
                                keyframes {
                                    durationMillis = 500
                                    0f at 0 // optional
                                    0.5f at 100
                                    1f at 225 // optional
                                }
                            LikedStates.Liked isTransitioningTo LikedStates.Disappeared ->
                                tween(durationMillis = 200)
                            else -> snap()
                        }
                    }
                ) {
                    if (it == LikedStates.Liked) 1f else 0f
                }

            // Creates a scale animation, as a part of the transition
            val scale by
                transition.animateFloat(
                    transitionSpec = {
                        when {
                            // Uses different animation specs for transitioning from/to different
                            // states
                            LikedStates.Initial isTransitioningTo LikedStates.Liked ->
                                spring(dampingRatio = Spring.DampingRatioHighBouncy)
                            LikedStates.Liked isTransitioningTo LikedStates.Disappeared ->
                                tween(200)
                            else -> snap()
                        }
                    }
                ) {
                    when (it) {
                        LikedStates.Initial -> 0f
                        LikedStates.Liked -> 4f
                        LikedStates.Disappeared -> 2f
                    }
                }

            Icon(
                Icons.Filled.Favorite,
                "Like",
                Modifier.align(Alignment.Center)
                    .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale),
                tint = Color.Red,
            )
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")

fun CreateChildTransitionSample() {
    // enum class DialerState { DialerMinimized, NumberPad }
    @OptIn(ExperimentalTransitionApi::class)
    @Composable
    fun DialerButton(visibilityTransition: Transition<Boolean>, modifier: Modifier) {
        val scale by visibilityTransition.animateFloat { visible -> if (visible) 1f else 2f }
        Box(modifier.scale(scale).background(Color.Black)) {
            // Content goes here
        }
    }

    @Composable
    fun NumberPad(visibilityTransition: Transition<Boolean>) {
        // Create animations using the provided Transition for visibility change here...
    }

    @OptIn(ExperimentalTransitionApi::class)
    @Composable
    fun childTransitionSample() {
        var dialerState by remember { mutableStateOf(DialerState.NumberPad) }
        Box(Modifier.fillMaxSize()) {
            val parentTransition = updateTransition(dialerState)

            // Animate to different corner radius based on target state
            val cornerRadius by
                parentTransition.animateDp { if (it == DialerState.NumberPad) 0.dp else 20.dp }

            Box(
                Modifier.align(Alignment.BottomCenter)
                    .widthIn(50.dp)
                    .heightIn(50.dp)
                    .clip(RoundedCornerShape(cornerRadius))
            ) {
                NumberPad(
                    // Creates a child transition that derives its target state from the parent
                    // transition, and the mapping from parent state to child state.
                    // This will allow:
                    // 1) Parent transition to account for additional animations in the child
                    // Transitions before it considers itself finished. This is useful when you
                    // have a subsequent action after all animations triggered by a state change
                    // have finished.
                    // 2) Separation of concerns. This allows the child composable (i.e.
                    // NumberPad) to only care about its own visibility, rather than knowing about
                    // DialerState.
                    visibilityTransition =
                        parentTransition.createChildTransition {
                            // This is the lambda that defines how the parent target state maps to
                            // child target state.
                            it == DialerState.NumberPad
                        }
                    // Note: If it's not important for the animations within the child composable to
                    // be observable, it's perfectly valid to not hoist the animations through
                    // a Transition object and instead use animate*AsState.
                )
                DialerButton(
                    visibilityTransition =
                        parentTransition.createChildTransition {
                            it == DialerState.DialerMinimized
                        },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

enum class DialerState {
    DialerMinimized,
    NumberPad,
}


@OptIn(ExperimentalTransitionApi::class)
@Composable
fun TransitionStateIsIdleSample() {
    @Composable
    fun SelectableItem(selectedState: MutableTransitionState<Boolean>) {
        val transition = rememberTransition(selectedState)
        val cornerRadius by transition.animateDp { selected -> if (selected) 10.dp else 0.dp }
        val backgroundColor by
            transition.animateColor { selected -> if (selected) Color.Red else Color.White }
        Box(Modifier.background(backgroundColor, RoundedCornerShape(cornerRadius))) {
            // Item content goes here
        }
    }

    @OptIn(ExperimentalTransitionApi::class)
    @Composable
    fun ItemsSample(selectedId: Int) {
        Column {
            repeat(3) { id ->
                Box {
                    // Initialize the selected state as false to produce a transition going from
                    // false to true if `selected` parameter is true when entering composition.
                    val selectedState = remember { MutableTransitionState(false) }
                    // Mutate target state as needed.
                    selectedState.targetState = id == selectedId
                    // Now we pass the `MutableTransitionState` to the `Selectable` item and
                    // observe state change.
                    SelectableItem(selectedState)
                    if (selectedState.isIdle && selectedState.targetState) {
                        // If isIdle == true, it means the transition has arrived at its target
                        // state
                        // and there is no pending animation.
                        // Now we can do something after the selection transition is
                        // finished:
                        Text("Nice choice")
                    }
                }
            }
        }
    }
}

enum class BoxSize {
    Small,
    Medium,
    Large,
}


@Composable
fun SeekingAnimationSample() {
    Column {
        val seekingState = remember { SeekableTransitionState(BoxSize.Small) }
        val scope = rememberCoroutineScope()
        Column {
            Row {
                Button(
                    onClick = { scope.launch { seekingState.animateTo(BoxSize.Small) } },
                    Modifier.wrapContentWidth().weight(1f),
                ) {
                    Text("Animate Small")
                }
                Button(
                    onClick = { scope.launch { seekingState.seekTo(0f, BoxSize.Small) } },
                    Modifier.wrapContentWidth().weight(1f),
                ) {
                    Text("Seek Small")
                }
                Button(
                    onClick = { scope.launch { seekingState.seekTo(0f, BoxSize.Medium) } },
                    Modifier.wrapContentWidth().weight(1f),
                ) {
                    Text("Seek Medium")
                }
                Button(
                    onClick = { scope.launch { seekingState.seekTo(0f, BoxSize.Large) } },
                    Modifier.wrapContentWidth().weight(1f),
                ) {
                    Text("Seek Large")
                }
                Button(
                    onClick = { scope.launch { seekingState.animateTo(BoxSize.Large) } },
                    Modifier.wrapContentWidth().weight(1f),
                ) {
                    Text("Animate Large")
                }
            }
        }
        Slider(
            value = seekingState.fraction,
            modifier = Modifier.systemGestureExclusion().padding(10.dp),
            onValueChange = { value -> scope.launch { seekingState.seekTo(fraction = value) } },
        )
        val transition = rememberTransition(seekingState)

        val scale: Float by
            transition.animateFloat(
                transitionSpec = { tween(easing = LinearEasing) },
                label = "Scale",
            ) { state ->
                when (state) {
                    BoxSize.Small -> 1f
                    BoxSize.Medium -> 2f
                    BoxSize.Large -> 3f
                }
            }

        transition.AnimatedContent(
            transitionSpec = {
                fadeIn(tween(easing = LinearEasing)) togetherWith
                    fadeOut(tween(easing = LinearEasing))
            }
        ) { state ->
            if (state == BoxSize.Large) {
                Box(Modifier.size(50.dp).background(Color.Magenta))
            } else {
                Box(Modifier.size(50.dp))
            }
        }
        Box(
            Modifier.fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .size(100.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(Color.Blue)
        )
    }
}


@Composable
@Suppress("UNUSED_VARIABLE")
fun SeekToSample() {
    val seekingState = remember { SeekableTransitionState(BoxSize.Small) }
    LaunchedEffect(seekingState.targetState) { seekingState.seekTo(0f, BoxSize.Large) }
    val scope = rememberCoroutineScope()
    Slider(
        value = seekingState.fraction,
        modifier = Modifier.systemGestureExclusion().padding(10.dp),
        onValueChange = { value -> scope.launch { seekingState.seekTo(fraction = value) } },
    )
    val transition = rememberTransition(seekingState)
    // use the transition
}


@Composable
@Suppress("UNUSED_VARIABLE")
fun SnapToSample() {
    val seekingState = remember { SeekableTransitionState(BoxSize.Small) }
    val scope = rememberCoroutineScope()
    Button(onClick = { scope.launch { seekingState.snapTo(BoxSize.Large) } }) {
        Text("Snap to the Small state")
    }
    val transition = rememberTransition(seekingState)
    // use the transition
}
@Composable
fun AnimatedVectorSample() {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    @Composable
    fun AnimatedVector(@DrawableRes drawableId: Int) {
        val image = AnimatedImageVector.animatedVectorResource(drawableId)
        var atEnd by remember { mutableStateOf(false) }
        Image(
            painter = rememberAnimatedVectorPainter(image, atEnd),
            contentDescription = "Your content description",
            modifier = Modifier.size(64.dp).clickable { atEnd = !atEnd },
        )
    }
}

