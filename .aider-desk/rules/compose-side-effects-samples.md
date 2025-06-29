import androidx.annotation.Sampled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Suppress("UNREACHABLE_CODE", "CanBeVal", "UNUSED_VARIABLE")

fun snapshotFlowSample() {
// Define Snapshot state objects
var greeting by mutableStateOf("Hello")
var person by mutableStateOf("Adam")

    // ...

    // Create a flow that will emit whenever our person-specific greeting changes
    val greetPersonFlow = snapshotFlow { "$greeting, $person" }

    // ...

    val collectionScope: CoroutineScope = TODO("Use your scope here")

    // Collect the flow and offer greetings!
    collectionScope.launch { greetPersonFlow.collect { println(greeting) } }

    // ...

    // Change snapshot state; greetPersonFlow will emit a new greeting
    Snapshot.withMutableSnapshot {
        greeting = "Ahoy"
        person = "Sean"
    }

}

@Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER")
private suspend fun doSomethingSuspending(param: Any?): Any? = null

@Suppress("ClassName")
private object someObject {
var stateA by mutableStateOf(0)
var stateB by mutableStateOf(0)
}

@Suppress("unused")

fun snapshotAsContextElementSample() {
runBlocking {
val snapshot = Snapshot.takeSnapshot()
try {
withContext(snapshot.asContextElement()) {
// Data observed by separately reading stateA and stateB are consistent with
// the snapshot context element across suspensions
doSomethingSuspending(someObject.stateA)
doSomethingSuspending(someObject.stateB)
}
} finally {
// Snapshot must be disposed after it will not be used again
snapshot.dispose()
}
}
}

import androidx.annotation.Sampled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private interface Disposable {
fun dispose()
}

private interface UserDataRequest

private interface UserDataResponse

private interface UserRepository {
fun fetchUserData(
request: UserDataRequest,
onError: (Throwable) -> Unit,
onSuccess: (UserDataResponse) -> Unit,
): Disposable
}

private sealed class UserDataState {
object Loading : UserDataState()

    class UserData(val name: String, val avatar: String) : UserDataState() {
        constructor(response: UserDataResponse) : this("", "")
    }

    class Error(val message: String?) : UserDataState()

}


fun disposableEffectSample() {
@Composable
fun UserProfile(userRepository: UserRepository, userRequest: UserDataRequest) {
var userDataState by remember { mutableStateOf<UserDataState>(UserDataState.Loading) }

        // If either the repository or request change, we must cancel our old data fetch
        // and begin a new data fetch. We will cancel the current data fetch if UserProfile
        // leaves the composition.
        DisposableEffect(userRepository, userRequest) {
            val requestDisposable =
                userRepository.fetchUserData(
                    userRequest,
                    onSuccess = { response -> userDataState = UserDataState.UserData(response) },
                    onError = { throwable ->
                        userDataState = UserDataState.Error(throwable.message)
                    },
                )

            onDispose { requestDisposable.dispose() }
        }

        // ...
    }

}

private interface Dispatcher {
fun addListener(listener: () -> Unit): Disposable
}

@Suppress("UnnecessaryLambdaCreation")

fun rememberUpdatedStateSampleWithDisposableEffect() {
@Composable
fun EventHandler(dispatcher: Dispatcher, onEvent: () -> Unit) {
val currentOnEvent by rememberUpdatedState(onEvent)

        // Event handlers are ordered and a new onEvent should not cause us to re-register,
        // losing our position in the dispatcher.
        DisposableEffect(dispatcher) {
            val disposable =
                dispatcher.addListener {
                    // currentOnEvent will always refer to the latest onEvent function that
                    // the EventHandler was recomposed with
                    currentOnEvent()
                }
            onDispose { disposable.dispose() }
        }
    }

}

private interface NotificationState {
val currentNotification: Notification?
}

private interface Notification {
fun dismiss()
}

private const val NotificationTimeout = 5_000L

fun rememberUpdatedStateSampleWithLaunchedEffect() {
@Composable
fun NotificationHost(state: NotificationState, onTimeout: (Notification) -> Unit) {
val currentOnTimeout by rememberUpdatedState(onTimeout)

        state.currentNotification?.let { currentNotification ->
            LaunchedEffect(currentNotification) {
                // We should not restart this delay if onTimeout changes, but we want to call
                // the onTimeout we were last recomposed with when it completes.
                delay(NotificationTimeout)
                currentOnTimeout(currentNotification)
            }
        }

        // ...
    }

}


import androidx.annotation.Sampled
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

@Suppress("UNUSED_PARAMETER") @Composable private fun Text(text: String): Unit = TODO()

class ProduceStateSampleViewModel {
val people: Flow<List<Person>> = TODO()

    interface Disposable {
        fun dispose()
    }

    @Suppress("UNUSED_PARAMETER")
    fun registerPersonObserver(observer: (Person) -> Unit): Disposable = TODO()
}

data class Person(val name: String)

private sealed class UiState<out T> {
object Loading : UiState<Nothing>()

    class Data<T>(val data: T) : UiState<T>()
}

@Composable
fun ProduceState(viewModel: ProduceStateSampleViewModel) {
val uiState by
produceState<UiState<List<Person>>>(UiState.Loading, viewModel) {
viewModel.people.map { UiState.Data(it) }.collect { value = it }
}

    when (val state = uiState) {
        is UiState.Loading -> Text("Loading...")
        is UiState.Data ->
            Column {
                for (person in state.data) {
                    Text("Hello, ${person.name}")
                }
            }
    }
}

@Suppress("UNUSED_VARIABLE")

@Composable
fun ProduceStateAwaitDispose(viewModel: ProduceStateSampleViewModel) {
val currentPerson by
produceState<Person?>(null, viewModel) {
val disposable = viewModel.registerPersonObserver { person -> value = person }

            awaitDispose { disposable.dispose() }
        }
}
