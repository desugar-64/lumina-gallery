
Navigation describes the way users move around your app. Users interact with UI
elements, usually by tapping or clicking on them, and the app responds by
displaying new content. If the user wants to go **Figure 1.** Diagram showing how the back stack changes with user navigation events.

## Create a back stack

In Navigation 3, the back stack does not actually contain content. Instead, it
contains _references to content_, known as **keys**. Keys can be any type but
are usually simple, serializable data classes. Using references rather than
content has the following benefits:

- It is simple to navigate by pushing keys onto the back stack.
- As long as the keys are serializable, the back stack can be saved to
persistent storage, allowing it to survive configuration changes and process
death. This is important because users expect to leave your app, come for more information.

A key concept in the Navigation 3 API is that you own the back stack. The
library:

`T` is the type of your back stack `keys`. You can use `Any` or you can
provide your own, more strongly-typed keys. When you see the terms "push" or
"pop", the underlying implementation is to add or remove items from the end of
a list.
- Observes your back stack and reflects its state in the UI using a
`NavDisplay`.

The following example shows how to create keys and a back stack, and modify the
back stack in response to user navigation events:

// Define keys that will identify content
data object ProductList
data class ProductDetail(val id: String)

@Composable
fun MyApp() {

// Create a back stack, specifying the key the app should start with

// Supply your back stack to a NavDisplay so it can reflect changes in the UI
// ...more on this below...

// Push a key onto the back stack (navigate forward), the navigation library will reflect the change in state
backStack.add(ProductDetail(id = "ABC"))

// Pop a key off the back stack (navigate back), the navigation library will reflect the change in state
backStack.removeLastOrNull()
}
BasicSnippets.kt

## Resolve keys to content

Content is modeled in Navigation 3 using `NavEntry`, which is a class
containing a composable function. It represents a _destination_ \- a single piece
of content that the user can navigate _forward to_ and _back from_.

A `NavEntry` can also contain metadata - information about the content. This
metadata can be read by container objects, like `NavDisplay`, to help them
decide how to display the `NavEntry`'s content. For example, metadata can be
used to override the default animations for a specific `NavEntry`. NavEntry
`metadata` is a map of `String` keys to `Any` values, providing versatile data
storage.

To convert a `key` to a `NavEntry`, create an Entry Provider. This is a
function that accepts a `key` and returns a `NavEntry` for that `key`. It is
usually defined as a lambda parameter when creating a `NavDisplay`.

There are two ways to create an Entry Provider, either by creating a lambda
function directly, or by using the `entryProvider` DSL.

### Create an Entry Provider function directly

You typically create an Entry Provider function using a `when` statement, with
a branch for each of your keys.

key,
metadata = mapOf("extraDataKey" to "extraDataValue")
) { Text("Product ${key.id} ") }

NavEntry(Unit) { Text(text = "Invalid Key: $it") }
}
}
}
BasicSnippets.kt

### Use the `entryProvider` DSL

The `entryProvider` DSL can simplify your lambda function by avoiding the need
to test against each of your key types, and construct a `NavEntry` for each one.
Use the `entryProvider` builder function for this. It also includes default
fallback behavior (throwing an error) if the key isn't found.

entryProvider = entryProvider {

metadata = mapOf("extraDataKey" to "extraDataValue")

}
BasicSnippets.kt

Note the following from the snippet:

- `entry` is used to define a `NavEntry` with the given type and composable
content
- `entry` accepts a `metadata` parameter to set `NavEntry.metadata`

## Display the back stack

The back stack represents your app's navigation state. Whenever the back stack
changes, the app UI should reflect the new back stack state. In Navigation 3, a
`NavDisplay` observes your back stack and updates its UI accordingly. Construct
it with following parameters:

the type of your back stack keys. It is an observable `List` so that it
triggers recomposition of `NavDisplay` when it changes.
- An `entryProvider` to convert the keys in your back stack to `NavEntry` s.
- Optionally, supply a lambda to the `onBack` parameter. This is called when the
user triggers a back event.

The following example shows how to create a `NavDisplay`.

data object Home
data class Product(val id: String)

@Composable
fun NavExample() {

NavDisplay(
backStack = backStack,
onBack = { backStack.removeLastOrNull() },

ContentGreen("Welcome to Nav3") {
Button(onClick = {
backStack.add(Product("123"))
}) {
Text("Click to navigate")
}
}
}

ContentBlue("Product ${key.id} ")
}

}
}
)
}
BasicSnippets.kt

By default, the `NavDisplay` shows the topmost `NavEntry` on the back stack in a
single pane layout. The following recording shows this app running:

destinations.

## Putting it all together

The following diagram shows how data flows between the various objects in
Navigation 3:

1. **Navigation events initiate changes**. Keys are added or removed from the
back stack in response to user interactions.

2. **Change in back stack state triggers content retrieval**. The `NavDisplay`
(a composable that renders a back stack) observes the back stack. In its
default configuration, it displays the topmost back stack entry in a single
pane layout. When the top key on the back stack changes, the `NavDisplay` uses
this key to request the corresponding content from the entry provider.

3. **Entry provider supplies content**. The entry provider is a function that
resolves a key to a `NavEntry`. Upon receiving a key from the `NavDisplay`,
the entry provider provides the associated `NavEntry`, which contains both the
key and the content.

4. **Content is displayed**. The `NavDisplay` receives the `NavEntry` and
displays the content.

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-17 UTC.

iframe

---

