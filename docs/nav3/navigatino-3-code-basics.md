import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.navEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.runtime.samples.Dashboard
import androidx.navigation3.runtime.samples.DialogBase
import androidx.navigation3.runtime.samples.DialogContent
import androidx.navigation3.runtime.samples.NavigateBackButton
import androidx.navigation3.runtime.samples.Profile
import androidx.navigation3.runtime.samples.ProfileViewModel
import androidx.navigation3.runtime.samples.Scrollable
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import kotlinx.serialization.Serializable

@Sampled
@Composable
fun SceneNav() {
    val backStack = rememberNavBackStack(Profile)
    val showDialog = remember { mutableStateOf(false) }
    NavDisplay(
        backStack = backStack,
        entryDecorators =
            listOf(
                rememberSceneSetupNavEntryDecorator(),
                rememberSavedStateNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        onBack = { backStack.removeAt(backStack.lastIndex) },
        entryProvider =
            entryProvider({ NavEntry(it) { Text(text = "Invalid Key") } }) {
                entry<Profile> {
                    val viewModel = viewModel<ProfileViewModel>()
                    Profile(viewModel, { backStack.add(it) }) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                }
                entry<Scrollable> {
                    Scrollable({ backStack.add(it) }) { backStack.removeAt(backStack.lastIndex) }
                }
                entry<DialogBase> {
                    DialogBase(onClick = { showDialog.value = true }) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                }
                entry<Dashboard>(
                    metadata =
                        NavDisplay.predictivePopTransitionSpec {
                            slideInHorizontally(tween(700)) { it / 2 } togetherWith
                                slideOutHorizontally(tween(700)) { -it / 2 }
                        }
                ) { dashboardArgs ->
                    val userId = dashboardArgs.userId
                    Dashboard(userId, onBack = { backStack.removeAt(backStack.lastIndex) })
                }
            },
    )
    if (showDialog.value) {
        DialogContent(onDismissRequest = { showDialog.value = false })
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Sampled
@Composable
fun SceneNavSharedEntrySample() {

    /** The [SharedTransitionScope] of the [NavDisplay]. */
    val localNavSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope> =
        compositionLocalOf {
            throw IllegalStateException(
                "Unexpected access to LocalNavSharedTransitionScope. You must provide a " +
                    "SharedTransitionScope from a call to SharedTransitionLayout() or " +
                    "SharedTransitionScope()"
            )
        }

    /**
     * A [NavEntryDecorator] that wraps each entry in a shared element that is controlled by the
     * [Scene].
     */
    val sharedEntryInSceneNavEntryDecorator =
        navEntryDecorator<Any> { entry ->
            with(localNavSharedTransitionScope.current) {
                Box(
                    Modifier.sharedElement(
                        rememberSharedContentState(entry.contentKey),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    )
                ) {
                    entry.Content()
                }
            }
        }

    val backStack = rememberNavBackStack(CatList)
    SharedTransitionLayout {
        CompositionLocalProvider(localNavSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeAt(backStack.lastIndex) },
                entryDecorators =
                    listOf(
                        sharedEntryInSceneNavEntryDecorator,
                        rememberSceneSetupNavEntryDecorator(),
                        rememberSavedStateNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider {
                        entry<CatList> {
                            CatList(this@SharedTransitionLayout) { cat ->
                                backStack.add(CatDetail(cat))
                            }
                        }
                        entry<CatDetail> { args ->
                            CatDetail(args.cat, this@SharedTransitionLayout) {
                                backStack.removeAt(backStack.lastIndex)
                            }
                        }
                    },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Sampled
@Composable
fun SceneNavSharedElementSample() {
    val backStack = rememberNavBackStack(CatList)
    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeAt(backStack.lastIndex) },
            entryProvider =
                entryProvider {
                    entry<CatList> {
                        CatList(this@SharedTransitionLayout) { cat ->
                            backStack.add(CatDetail(cat))
                        }
                    }
                    entry<CatDetail> { args ->
                        CatDetail(args.cat, this@SharedTransitionLayout) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    }
                },
        )
    }
}

@Serializable object CatList : NavKey

@Serializable data class CatDetail(val cat: Cat) : NavKey

@Serializable
data class Cat(@DrawableRes val imageId: Int, val name: String, val description: String)

private val catList: List<Cat> =
    listOf(
        Cat(R.drawable.cat_1, "happy", "cat lying down"),
        Cat(R.drawable.cat_2, "lucky", "cat playing"),
        Cat(R.drawable.cat_3, "chocolate cake", "cat upside down"),
    )

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CatList(sharedScope: SharedTransitionScope, onClick: (cat: Cat) -> Unit) {
    Column {
        catList.forEach { cat: Cat ->
            Row(Modifier.clickable { onClick(cat) }) {
                with(sharedScope) {
                    val imageModifier =
                        Modifier.size(100.dp)
                            .sharedElement(
                                sharedScope.rememberSharedContentState(key = cat.imageId),
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                            )
                    Image(painterResource(cat.imageId), cat.description, imageModifier)
                    Text(cat.name)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CatDetail(cat: Cat, sharedScope: SharedTransitionScope, onBack: () -> Unit) {
    Column {
        Box {
            with(sharedScope) {
                val imageModifier =
                    Modifier.size(300.dp)
                        .sharedElement(
                            sharedScope.rememberSharedContentState(key = cat.imageId),
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        )
                Image(painterResource(cat.imageId), cat.description, imageModifier)
            }
        }
        Text(cat.name)
        Text(cat.description)
        NavigateBackButton(onBack)
    }
}

// [START android_compose_navigation3_basic_1]
// Define keys that will identify content
data object ProductList
data class ProductDetail(val id: String)

@Composable
fun MyApp() {

    // Create a back stack, specifying the key the app should start with
    val backStack = remember { mutableStateListOf<Any>(ProductList) }

    // Supply your back stack to a NavDisplay so it can reflect changes in the UI
    // ...more on this below...

    // Push a key onto the back stack (navigate forward), the navigation library will reflect the change in state
    backStack.add(ProductDetail(id = "ABC"))

    // Pop a key off the back stack (navigate back), the navigation library will reflect the change in state
    backStack.removeLastOrNull()
}
// [END android_compose_navigation3_basic_1]

@Composable
fun EntryProvider() {
    val backStack = remember { mutableStateListOf<Any>(ProductList) }
    NavDisplay(
        backStack = backStack,
        // [START android_compose_navigation3_basic_2]
        entryProvider = { key ->
            when (key) {
                is ProductList -> NavEntry(key) { Text("Product List") }
                is ProductDetail -> NavEntry(
                    key,
                    metadata = mapOf("extraDataKey" to "extraDataValue")
                ) { Text("Product ${key.id} ") }

                else -> {
                    NavEntry(Unit) { Text(text = "Invalid Key: $it") }
                }
            }
        }
        // [END android_compose_navigation3_basic_2]
    )
}

@Composable
fun EntryProviderDsl() {
    val backStack = remember { mutableStateListOf<Any>(ProductList) }
    NavDisplay(
        backStack = backStack,
        // [START android_compose_navigation3_basic_3]
        entryProvider = entryProvider {
            entry<ProductList> { Text("Product List") }
            entry<ProductDetail>(
                metadata = mapOf("extraDataKey" to "extraDataValue")
            ) { key -> Text("Product ${key.id} ") }
        }
        // [END android_compose_navigation3_basic_3]
    )
}

// [START android_compose_navigation3_basic_4]
data object Home
data class Product(val id: String)

@Composable
fun NavExample() {

    val backStack = remember { mutableStateListOf<Any>(Home) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                is Home -> NavEntry(key) {
                    ContentGreen("Welcome to Nav3") {
                        Button(onClick = {
                            backStack.add(Product("123"))
                        }) {
                            Text("Click to navigate")
                        }
                    }
                }

                is Product -> NavEntry(key) {
                    ContentBlue("Product ${key.id} ")
                }

                else -> NavEntry(Unit) { Text("Unknown route") }
            }
        }
    )
}
// [END android_compose_navigation3_basic_4]

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.compose.snippets.navigation3.ContentGreen
import com.example.compose.snippets.navigation3.ContentMauve
import com.example.compose.snippets.navigation3.ContentOrange
import kotlinx.serialization.Serializable

// [START android_compose_navigation3_animations_1]
@Serializable
data object ScreenA : NavKey

@Serializable
data object ScreenB : NavKey

@Serializable
data object ScreenC : NavKey

class AnimatedNavDisplayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            Scaffold { paddingValues ->

                val backStack = rememberNavBackStack(ScreenA)

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<ScreenA> {
                            ContentOrange("This is Screen A") {
                                Button(onClick = { backStack.add(ScreenB) }) {
                                    Text("Go to Screen B")
                                }
                            }
                        }
                        entry<ScreenB> {
                            ContentMauve("This is Screen B") {
                                Button(onClick = { backStack.add(ScreenC) }) {
                                    Text("Go to Screen C")
                                }
                            }
                        }
                        entry<ScreenC>(
                            metadata = NavDisplay.transitionSpec {
                                // Slide new content up, keeping the old content in place underneath
                                slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = tween(1000)
                                ) togetherWith ExitTransition.KeepUntilTransitionsFinished
                            } + NavDisplay.popTransitionSpec {
                                // Slide old content down, revealing the new content in place underneath
                                EnterTransition.None togetherWith
                                    slideOutVertically(
                                        targetOffsetY = { it },
                                        animationSpec = tween(1000)
                                    )
                            } + NavDisplay.predictivePopTransitionSpec {
                                // Slide old content down, revealing the new content in place underneath
                                EnterTransition.None togetherWith
                                    slideOutVertically(
                                        targetOffsetY = { it },
                                        animationSpec = tween(1000)
                                    )
                            }
                        ) {
                            ContentGreen("This is Screen C")
                        }
                    },
                    transitionSpec = {
                        // Slide in from right when navigating forward
                        slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                    },
                    popTransitionSpec = {
                        // Slide in from left when navigating back
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    predictivePopTransitionSpec = {
                        // Slide in from left when navigating back
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
// [END android_compose_navigation3_animations_1]