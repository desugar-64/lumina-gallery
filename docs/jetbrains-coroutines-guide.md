# https://kotlinlang.org/docs/coroutines-guide.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Coroutines guide

# Coroutines guide

Edit page 16 February 2022

Kotlin provides only minimal low-level APIs in its standard library to enable other libraries to utilize coroutines. Unlike many other languages with similar capabilities, `async` and `await` are not keywords in Kotlin and are not even part of its standard library. Moreover, Kotlin's concept of suspending function provides a safer and less error-prone abstraction for asynchronous operations than futures and promises.

`kotlinx.coroutines` is a rich library for coroutines developed by JetBrains. It contains a number of high-level coroutine-enabled primitives that this guide covers, including `launch`, `async`, and others.

This is a guide about the core features of `kotlinx.coroutines` with a series of examples, divided up into different topics.

In order to use coroutines as well as follow the examples in this guide, you need to add a dependency on the `kotlinx-coroutines-core` module as explained in the project README.

## Table of contents

- Coroutines basics

- Tutorial: Intro to coroutines and channels

- Cancellation and timeouts

- Composing suspending functions

- Coroutine context and dispatchers

- Asynchronous Flow

- Channels

- Coroutine exceptions handling

- Shared mutable state and concurrency

- Select expression (experimental)

- Tutorial: Debug coroutines using IntelliJ IDEA

- Tutorial: Debug Kotlin Flow using IntelliJ IDEA

## Additional references

- Guide to UI programming with coroutines

- Coroutines design document (KEEP)

- Full kotlinx.coroutines API reference

- Best practices for coroutines in Android

- Additional Android resources for Kotlin coroutines and flow

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/coroutines-basics.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Coroutines basics

# Coroutines basics

Edit page 16 February 2022

This section covers basic coroutine concepts.

## Your first coroutine

A coroutine is an instance of a suspendable computation. It is conceptually similar to a thread, in the sense that it takes a block of code to run that works concurrently with the rest of the code. However, a coroutine is not bound to any particular thread. It may suspend its execution in one thread and resume in another one.

Coroutines can be thought of as light-weight threads, but there is a number of important differences that make their real-life usage very different from threads.

Run the following code to get to your first working coroutine:

import kotlinx.coroutines.*
//sampleStart
fun main() = runBlocking { // this: CoroutineScope
launch { // launch a new coroutine and continue
delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
println("World!") // print after delay
}
println("Hello") // main coroutine continues while a previous one is delayed
}
//sampleEnd

xxxxxxxxxx
fun main() = runBlocking { // this: CoroutineScope
launch { // launch a new coroutine and continue
delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
println("World!") // print after delay
}
println("Hello") // main coroutine continues while a previous one is delayed
}

Open in Playground →

>
> You can get the full code here.

You will see the following result:

Hello
World!

Let's dissect what this code does.

launch is a coroutine builder. It launches a new coroutine concurrently with the rest of the code, which continues to work independently. That's why `Hello` has been printed first.

delay is a special suspending function. It suspends the coroutine for a specific time. Suspending a coroutine does not block the underlying thread, but allows other coroutines to run and use the underlying thread for their code.

runBlocking is also a coroutine builder that bridges the non-coroutine world of a regular `fun main()` and the code with coroutines inside of `runBlocking { ... }` curly braces. This is highlighted in an IDE by `this: CoroutineScope` hint right after the `runBlocking` opening curly brace.

If you remove or forget `runBlocking` in this code, you'll get an error on the launch call, since `launch` is declared only on the CoroutineScope:

Unresolved reference: launch

The name of `runBlocking` means that the thread that runs it (in this case — the main thread) gets blocked for the duration of the call, until all the coroutines inside `runBlocking { ... }` complete their execution. You will often see `runBlocking` used like that at the very top-level of the application and quite rarely inside the real code, as threads are expensive resources and blocking them is inefficient and is often not desired.

### Structured concurrency

Coroutines follow a principle of structured concurrency which means that new coroutines can only be launched in a specific CoroutineScope which delimits the lifetime of the coroutine. The above example shows that runBlocking establishes the corresponding scope and that is why the previous example waits until `World!` is printed after a second's delay and only then exits.

In a real application, you will be launching a lot of coroutines. Structured concurrency ensures that they are not lost and do not leak. An outer scope cannot complete until all its children coroutines complete. Structured concurrency also ensures that any errors in the code are properly reported and are never lost.

## Extract function refactoring

Let's extract the block of code inside `launch { ... }` into a separate function. When you perform "Extract function" refactoring on this code, you get a new function with the `suspend` modifier. This is your first suspending function. Suspending functions can be used inside coroutines just like regular functions, but their additional feature is that they can, in turn, use other suspending functions (like `delay` in this example) to suspend execution of a coroutine.

import kotlinx.coroutines.*
//sampleStart
fun main() = runBlocking { // this: CoroutineScope
launch { doWorld() }
println("Hello")
}
// this is your first suspending function
suspend fun doWorld() {
delay(1000L)
println("World!")
}
//sampleEnd

xxxxxxxxxx
fun main() = runBlocking { // this: CoroutineScope
launch { doWorld() }
println("Hello")
}
​
// this is your first suspending function
suspend fun doWorld() {
delay(1000L)
println("World!")
}

## Scope builder

In addition to the coroutine scope provided by different builders, it is possible to declare your own scope using the coroutineScope builder. It creates a coroutine scope and does not complete until all launched children complete.

runBlocking and coroutineScope builders may look similar because they both wait for their body and all its children to complete. The main difference is that the runBlocking method blocks the current thread for waiting, while coroutineScope just suspends, releasing the underlying thread for other usages. Because of that difference, runBlocking is a regular function and coroutineScope is a suspending function.

You can use `coroutineScope` from any suspending function. For example, you can move the concurrent printing of `Hello` and `World` into a `suspend fun doWorld()` function:

import kotlinx.coroutines.*
//sampleStart
fun main() = runBlocking {
doWorld()
}
suspend fun doWorld() = coroutineScope { // this: CoroutineScope
launch {
delay(1000L)
println("World!")
}
println("Hello")
}
//sampleEnd

xxxxxxxxxx
fun main() = runBlocking {
doWorld()
}
​
suspend fun doWorld() = coroutineScope { // this: CoroutineScope
launch {
delay(1000L)
println("World!")
}
println("Hello")
}

This code also prints:

## Scope builder and concurrency

A coroutineScope builder can be used inside any suspending function to perform multiple concurrent operations. Let's launch two concurrent coroutines inside a `doWorld` suspending function:

import kotlinx.coroutines.*
//sampleStart
// Sequentially executes doWorld followed by "Done"
fun main() = runBlocking {
doWorld()
println("Done")
}
// Concurrently executes both sections
suspend fun doWorld() = coroutineScope { // this: CoroutineScope
launch {
delay(2000L)
println("World 2")
}
launch {
delay(1000L)
println("World 1")
}
println("Hello")
}
//sampleEnd

xxxxxxxxxx
// Sequentially executes doWorld followed by "Done"
fun main() = runBlocking {
doWorld()
println("Done")
}
​
// Concurrently executes both sections
suspend fun doWorld() = coroutineScope { // this: CoroutineScope
launch {
delay(2000L)
println("World 2")
}
launch {
delay(1000L)
println("World 1")
}
println("Hello")
}

Both pieces of code inside `launch { ... }` blocks execute concurrently, with `World 1` printed first, after a second from start, and `World 2` printed next, after two seconds from start. A coroutineScope in `doWorld` completes only after both are complete, so `doWorld` returns and allows `Done` string to be printed only after that:

Hello
World 1
World 2
Done

## An explicit job

A launch coroutine builder returns a Job object that is a handle to the launched coroutine and can be used to wait for its completion explicitly. For example, you can wait for the completion of the child coroutine and then print the "Done" string:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val job = launch { // launch a new coroutine and keep a reference to its Job
delay(1000L)
println("World!")
}
println("Hello")
job.join() // wait until child coroutine completes
println("Done")
//sampleEnd
}

xxxxxxxxxx
val job = launch { // launch a new coroutine and keep a reference to its Job
delay(1000L)
println("World!")
}
println("Hello")
job.join() // wait until child coroutine completes
println("Done")

This code produces:

Hello
World!
Done

## Coroutines are light-weight

Coroutines are less resource-intensive than JVM threads. Code that exhausts the JVM's available memory when using threads can be expressed using coroutines without hitting resource limits. For example, the following code launches 50,000 distinct coroutines that each waits 5 seconds and then prints a period ('.') while consuming very little memory:

import kotlinx.coroutines.*
fun main() = runBlocking {
repeat(50_000) { // launch a lot of coroutines
launch {
delay(5000L)
print(".")
}
}
}

xxxxxxxxxx
import kotlinx.coroutines.*
​
fun main() = runBlocking {
repeat(50_000) { // launch a lot of coroutines
launch {
delay(5000L)
print(".")
}
}
}

If you write the same program using threads (remove `runBlocking`, replace `launch` with `thread`, and replace `delay` with `Thread.sleep`), it will consume a lot of memory. Depending on your operating system, JDK version, and its settings, it will either throw an out-of-memory error or start threads slowly so that there are never too many concurrently running threads.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/coroutines-and-channels.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Coroutines and channels − tutorial

# Coroutines and channels − tutorial

Edit page 16 February 2022

>
> No prior knowledge of coroutines is required, but you're expected to be familiar with basic Kotlin syntax.

You'll learn:

- Why and how to use suspending functions to perform network requests.

- How to send requests concurrently using coroutines.

- How to share information between different coroutines using channels.

>
> You can find solutions for all of the tasks on the `solutions` branch of the project's repository.

## Before you start

1. Download and install the latest version of IntelliJ IDEA.

2. Clone the project template by choosing Get from VCS on the Welcome screen or selecting File \| New \| Project from Version Control.

You can also clone it from the command line:

git clone

### Generate a GitHub developer token

You'll be using the GitHub API in your project. To get access, provide your GitHub account name and either a password or a token. If you have two-factor authentication enabled, a token will be enough.

Generate a new GitHub token to use the GitHub API with your account:

1. Specify the name of your token, for example, `coroutines-tutorial`:

2. Do not select any scopes. Click Generate token at the bottom of the page.

3. Copy the generated token.

### Run the code

The program loads the contributors for all of the repositories under the given organization (named “kotlin” by default). Later you'll add logic to sort the users by the number of their contributions.

1. Open the `src/contributors/main.kt` file and run the `main()` function. You'll see the following window:

If the font is too small, adjust it by changing the value of `setDefaultFontSize(18f)` in the `main()` function.

2. Provide your GitHub username and token (or password) in the corresponding fields.

3. Make sure that the BLOCKING option is selected in the Variant dropdown menu.

4. Click Load contributors. The UI should freeze for some time and then show the list of contributors.

5. Open the program output to ensure the data has been loaded. The list of contributors is logged after each successful request.

There are different ways of implementing this logic: by using blocking requests or callbacks. You'll compare these solutions with one that uses coroutines and see how channels can be used to share information between different coroutines.

## Blocking requests

You will use the Retrofit library to perform HTTP requests to GitHub. It allows requesting the list of repositories under the given organization and the list of contributors for each repository:

interface GitHubService {
@GET("orgs/{org}/repos?per_page=100")
fun getOrgReposCall(
@Path("org") org: String
): Call<List<Repo>>

@GET("repos/{owner}/{repo}/contributors?per_page=100")
fun getRepoContributorsCall(
@Path("owner") owner: String,
@Path("repo") repo: String
): Call<List<User>>
}

This API is used by the `loadContributorsBlocking()` function to fetch the list of contributors for the given organization.

1. Open `src/tasks/Request1Blocking.kt` to see its implementation:

fun loadContributorsBlocking(
service: GitHubService,
req: RequestData

val repos = service
.getOrgReposCall(req.org) // #1
.execute() // #2
.also { logRepos(req, it) } // #3
.body() ?: emptyList() // #4

.getRepoContributorsCall(req.org, repo.name) // #1
.execute() // #2
.also { logUsers(repo, it) } // #3
.bodyList() // #4
}.aggregate()
}

- At first, you get a list of the repositories under the given organization and store it in the `repos` list. Then for each repository, the list of contributors is requested, and all of the lists are merged into one final list of contributors.

- `getOrgReposCall()` and `getRepoContributorsCall()` both return an instance of the `*Call` class ( `#1`). At this point, no request is sent.

- `*Call.execute()` is then invoked to perform the request ( `#2`). `execute()` is a synchronous call that blocks the underlying thread.

- When you get the response, the result is logged by calling the specific `logRepos()` and `logUsers()` functions ( `#3`). If the HTTP response contains an error, this error will be logged here.

- Finally, get the response's body, which contains the data you need. For this tutorial, you'll use an empty list as a result in case there is an error, and you'll log the corresponding error ( `#4`).
2. To avoid repeating `.body() ?: emptyList()`, an extension function `bodyList()` is declared:

fun <T> Response<List<T>>.bodyList(): List<T> {
return body() ?: emptyList()
}

3. Run the program again and take a look at the system output in IntelliJ IDEA. It should have something like this:

1770 [AWT-EventQueue-0] INFO Contributors - kotlin: loaded 40 repos
2025 [AWT-EventQueue-0] INFO Contributors - kotlin-examples: loaded 23 contributors
2229 [AWT-EventQueue-0] INFO Contributors - kotlin-koans: loaded 45 contributors
...

- The first item on each line is the number of milliseconds that have passed since the program started, then the thread name in square brackets. You can see from which thread the loading request is called.

- The final item on each line is the actual message: how many repositories or contributors were loaded.

This log output demonstrates that all of the results were logged from the main thread. When you run the code with a BLOCKING option, the window freezes and doesn't react to input until the loading is finished. All of the requests are executed from the same thread as the one called `loadContributorsBlocking()` is from, which is the main UI thread (in Swing, it's an AWT event dispatching thread). This main thread becomes blocked, and that's why the UI is frozen:

After the list of contributors has loaded, the result is updated.

4. In `src/contributors/Contributors.kt`, find the `loadContributors()` function responsible for choosing how the contributors are loaded and look at how `loadContributorsBlocking()` is called:

when (getSelectedVariant()) {

val users = loadContributorsBlocking(service, req)
updateResults(users, startTime)
}
}

- The `updateResults()` call goes right after the `loadContributorsBlocking()` call.

- `updateResults()` updates the UI, so it must always be called from the UI thread.

- Since `loadContributorsBlocking()` is also called from the UI thread, the UI thread becomes blocked and the UI is frozen.

### Task 1

The first task helps you familiarize yourself with the task domain. Currently, each contributor's name is repeated several times, once for every project they have taken part in. Implement the `aggregate()` function combining the users so that each contributor is added only once. The `User.contributions` property should contain the total number of contributions of the given user to all the projects. The resulting list should be sorted in descending order according to the number of contributions.

>
> You can jump between the source code and the test class automatically by using the IntelliJ IDEA shortcut `Ctrl+Shift+T`/ `⇧ ⌘ T`.

After implementing this task, the resulting list for the "kotlin" organization should be similar to the following:

#### Solution for task 1

1. To group users by login, use `groupBy()`, which returns a map from a login to all occurrences of the user with this login in different repositories.

2. For each map entry, count the total number of contributions for each user and create a new instance of the `User` class by the given name and total of contributions.

3. Sort the resulting list in descending order:

groupBy { it.login }

.sortedByDescending { it.contributions }

An alternative solution is to use the `groupingBy()` function instead of `groupBy()`.

## Callbacks

The previous solution works, but it blocks the thread and therefore freezes the UI. A traditional approach that avoids this is to use callbacks.

Instead of calling the code that should be invoked right after the operation is completed, you can extract it into a separate callback, often a lambda, and pass that lambda to the caller in order for it to be called later.

To make the UI responsive, you can either move the whole computation to a separate thread or switch to the Retrofit API which uses callbacks instead of blocking calls.

### Use a background thread

1. Open `src/tasks/Request2Background.kt` and see its implementation. First, the whole computation is moved to a different thread. The `thread()` function starts a new thread:

thread {
loadContributorsBlocking(service, req)
}

Now that all of the loading has been moved to a separate thread, the main thread is free and can be occupied by other tasks:

2. The signature of the `loadContributorsBackground()` function changes. It takes an `updateResults()` callback as the last argument to call it after all the loading completes:

fun loadContributorsBackground(
service: GitHubService, req: RequestData,

)

3. Now when the `loadContributorsBackground()` is called, the `updateResults()` call goes in the callback, not immediately afterward as it did before:

updateResults(users, startTime)
}
}

By calling `SwingUtilities.invokeLater`, you ensure that the `updateResults()` call, which updates the results, happens on the main UI thread (AWT event dispatching thread).

However, if you try to load the contributors via the `BACKGROUND` option, you can see that the list is updated but nothing changes.

### Task 2

Fix the `loadContributorsBackground()` function in `src/tasks/Request2Background.kt` so that the resulting list is shown in the UI.

#### Solution for task 2

If you try to load the contributors, you can see in the log that the contributors are loaded but the result isn't displayed. To fix this, call `updateResults()` on the resulting list of users:

thread {
updateResults(loadContributorsBlocking(service, req))
}

Make sure to call the logic passed in the callback explicitly. Otherwise, nothing will happen.

### Use the Retrofit callback API

In the previous solution, the whole loading logic is moved to the background thread, but that still isn't the best use of resources. All of the loading requests go sequentially and the thread is blocked while waiting for the loading result, while it could have been occupied by other tasks. Specifically, the thread could start loading another request to receive the entire result earlier.

Handling the data for each repository should then be divided into two parts: loading and processing the resulting response. The second processing part should be extracted into a callback.

The loading for each repository can then be started before the result for the previous repository is received (and the corresponding callback is called):

The Retrofit callback API can help achieve this. The `Call.enqueue()` function starts an HTTP request and takes a callback as an argument. In this callback, you need to specify what needs to be done after each request.

Open `src/tasks/Request3Callbacks.kt` and see the implementation of `loadContributorsCallbacks()` that uses this API:

fun loadContributorsCallbacks(
service: GitHubService, req: RequestData,

) {

logRepos(req, responseRepos)
val repos = responseRepos.bodyList()

for (repo in repos) {
service.getRepoContributorsCall(req.org, repo.name)

logUsers(repo, responseUsers)
val users = responseUsers.bodyList()
allUsers += users
}
}
}
// TODO: Why doesn't this code work? How to fix that?
updateResults(allUsers.aggregate())
}

- For convenience, this code fragment uses the `onResponse()` extension function declared in the same file. It takes a lambda as an argument rather than an object expression.

- The logic for handling the responses is extracted into callbacks: the corresponding lambdas start at lines `#1` and `#2`.

However, the provided solution doesn't work. If you run the program and load contributors by choosing the CALLBACKS option, you'll see that nothing is shown. However, the test from `Request3CallbacksKtTest` immediately returns the result that it successfully passed.

Think about why the given code doesn't work as expected and try to fix it, or see the solutions below.

### Task 3 (optional)

Rewrite the code in the `src/tasks/Request3Callbacks.kt` file so that the loaded list of contributors is shown.

#### The first attempted solution for task 3

In the current solution, many requests are started concurrently, which decreases the total loading time. However, the result isn't loaded. This is because the `updateResults()` callback is called right after all of the loading requests are started, before the `allUsers` list has been filled with the data.

You could try to fix this with a change like the following:

for ((index, repo) in repos.withIndex()) { // #1
service.getRepoContributorsCall(req.org, repo.name)

val users = responseUsers.bodyList()
allUsers += users
if (index == repos.lastIndex) { // #2
updateResults(allUsers.aggregate())
}
}
}

- First, you iterate over the list of repos with an index ( `#1`).

- Then, from each callback, you check whether it's the last iteration ( `#2`).

- And if that's the case, the result is updated.

However, this code also fails to achieve our objective. Try to find the answer yourself, or see the solution below.

#### The second attempted solution for task 3

Since the loading requests are started concurrently, there's no guarantee that the result for the last one comes last. The results can come in any order.

Thus, if you compare the current index with the `lastIndex` as a condition for completion, you risk losing the results for some repos.

If the request that processes the last repo returns faster than some prior requests (which is likely to happen), all of the results for requests that take more time will be lost.

One way to fix this is to introduce an index and check whether all of the repositories have already been processed:

val numberOfProcessed = AtomicInteger()
for (repo in repos) {
service.getRepoContributorsCall(req.org, repo.name)

val users = responseUsers.bodyList()
allUsers += users
if (numberOfProcessed.incrementAndGet() == repos.size) {
updateResults(allUsers.aggregate())
}
}
}

This code uses a synchronized version of the list and `AtomicInteger()` because, in general, there's no guarantee that different callbacks that process `getRepoContributors()` requests will always be called from the same thread.

#### The third attempted solution for task 3

An even better solution is to use the `CountDownLatch` class. It stores a counter initialized with the number of repositories. This counter is decremented after processing each repository. It then waits until the latch is counted down to zero before updating the results:

val countDownLatch = CountDownLatch(repos.size)
for (repo in repos) {
service.getRepoContributorsCall(req.org, repo.name)

countDownLatch.countDown()
}
}
countDownLatch.await()
updateResults(allUsers.aggregate())

The result is then updated from the main thread. This is more direct than delegating the logic to the child threads.

>
> As an additional exercise, you can implement the same logic using a reactive approach with the RxJava library. All of the necessary dependencies and solutions for using RxJava can be found in a separate `rx` branch. It is also possible to complete this tutorial and implement or check the proposed Rx versions for a proper comparison.

## Suspending functions

You can implement the same logic using suspending functions. Instead of returning `Call<List<Repo>>`, define the API call as a suspending function as follows:

interface GitHubService {
@GET("orgs/{org}/repos?per_page=100")
suspend fun getOrgRepos(
@Path("org") org: String

- `getOrgRepos()` is defined as a `suspend` function. When you use a suspending function to perform a request, the underlying thread isn't blocked. More details about how this works will come in later sections.

- `getOrgRepos()` returns the result directly instead of returning a `Call`. If the result is unsuccessful, an exception is thrown.

Alternatively, Retrofit allows returning the result wrapped in `Response`. In this case, the result body is provided, and it is possible to check for errors manually. This tutorial uses the versions that return `Response`.

In `src/contributors/GitHubService.kt`, add the following declarations to the `GitHubService` interface:

interface GitHubService {
// getOrgReposCall & getRepoContributorsCall declarations

@GET("orgs/{org}/repos?per_page=100")
suspend fun getOrgRepos(
@Path("org") org: String
): Response<List<Repo>>

@GET("repos/{owner}/{repo}/contributors?per_page=100")
suspend fun getRepoContributors(
@Path("owner") owner: String,
@Path("repo") repo: String
): Response<List<User>>
}

### Task 4

>
> Suspending functions can't be called everywhere. Calling a suspending function from `loadContributorsBlocking()` will result in an error with the message "Suspend function 'getOrgRepos' should be called only from a coroutine or another suspend function".

1. Copy the implementation of `loadContributorsBlocking()` that is defined in `src/tasks/Request1Blocking.kt` into the `loadContributorsSuspend()` that is defined in `src/tasks/Request4Suspend.kt`.

2. Modify the code so that the new suspending functions are used instead of the ones that return `Call` s.

3. Run the program by choosing the SUSPEND option and ensure that the UI is still responsive while the GitHub requests are performed.

#### Solution for task 4

Replace `.getOrgReposCall(req.org).execute()` with `.getOrgRepos(req.org)` and repeat the same replacement for the second "contributors" request:

val repos = service
.getOrgRepos(req.org)
.also { logRepos(req, it) }
.bodyList()

.also { logUsers(repo, it) }
.bodyList()
}.aggregate()
}

- `loadContributorsSuspend()` should be defined as a `suspend` function.

- You no longer need to call `execute`, which returned the `Response` before, because now the API functions return the `Response` directly. Note that this detail is specific to the Retrofit library. With other libraries, the API will be different, but the concept is the same.

## Coroutines

The code with suspending functions looks similar to the "blocking" version. The major difference from the blocking version is that instead of blocking the thread, the coroutine is suspended:

>
> Coroutines are often called lightweight threads because you can run code on coroutines, similar to how you run code on threads. The operations that were blocking before (and had to be avoided) can now suspend the coroutine instead.

### Starting a new coroutine

If you look at how `loadContributorsSuspend()` is used in `src/contributors/Contributors.kt`, you can see that it's called inside `launch`. `launch` is a library function that takes a lambda as an argument:

launch {
val users = loadContributorsSuspend(req)
updateResults(users, startTime)
}

Here `launch` starts a new computation that is responsible for loading the data and showing the results. The computation is suspendable – when performing network requests, it is suspended and releases the underlying thread. When the network request returns the result, the computation is resumed.

Such a suspendable computation is called a coroutine. So, in this case, `launch` starts a new coroutine responsible for loading data and showing the results.

Coroutines run on top of threads and can be suspended. When a coroutine is suspended, the corresponding computation is paused, removed from the thread, and stored in memory. Meanwhile, the thread is free to be occupied by other tasks:

Gif

When the computation is ready to be continued, it is returned to a thread (not necessarily the same one).

In the `loadContributorsSuspend()` example, each "contributors" request now waits for the result using the suspension mechanism. First, the new request is sent. Then, while waiting for the response, the whole "load contributors" coroutine that was started by the `launch` function is suspended.

The coroutine resumes only after the corresponding response is received:

While the response is waiting to be received, the thread is free to be occupied by other tasks. The UI stays responsive, despite all the requests taking place on the main UI thread:

1. Run the program using the SUSPEND option. The log confirms that all of the requests are sent to the main UI thread:

2538 [AWT-EventQueue-0 @coroutine#1] INFO Contributors - kotlin: loaded 30 repos
2729 [AWT-EventQueue-0 @coroutine#1] INFO Contributors - ts2kt: loaded 11 contributors
3029 [AWT-EventQueue-0 @coroutine#1] INFO Contributors - kotlin-koans: loaded 45 contributors
...
11252 [AWT-EventQueue-0 @coroutine#1] INFO Contributors - kotlin-coroutines-workshop: loaded 1 contributors

2. The log can show you which coroutine the corresponding code is running on. To enable it, open Run \| Edit configurations and add the `-Dkotlinx.coroutines.debug` VM option:

The coroutine name will be attached to the thread name while `main()` is run with this option. You can also modify the template for running all of the Kotlin files and enable this option by default.

Now all of the code runs on one coroutine, the "load contributors" coroutine mentioned above, denoted as `@coroutine#1`. While waiting for the result, you shouldn't reuse the thread for sending other requests because the code is written sequentially. The new request is sent only when the previous result is received.

Suspending functions treat the thread fairly and don't block it for "waiting". However, this doesn't yet bring any concurrency into the picture.

## Concurrency

Kotlin coroutines are much less resource-intensive than threads. Each time you want to start a new computation asynchronously, you can create a new coroutine instead.

To start a new coroutine, use one of the main coroutine builders: `launch`, `async`, or `runBlocking`. Different libraries can define additional coroutine builders.

`async` starts a new coroutine and returns a `Deferred` object. `Deferred` represents a concept known by other names such as `Future` or `Promise`. It stores a computation, but it defers the moment you get the final result; it promises the result sometime in the future.

The main difference between `async` and `launch` is that `launch` is used to start a computation that isn't expected to return a specific result. `launch` returns a `Job` that represents the coroutine. It is possible to wait until it completes by calling `Job.join()`.

To get the result of a coroutine, you can call `await()` on the `Deferred` instance. While waiting for the result, the coroutine that this `await()` is called from is suspended:

import kotlinx.coroutines.*

fun main() = runBlocking {

loadData()
}
println("waiting...")
println(deferred.await())
}

suspend fun loadData(): Int {
println("loading...")
delay(1000L)
println("loaded!")
return 42
}

>
> Watch this video for a better understanding of coroutines.

If there is a list of deferred objects, you can call `awaitAll()` to await the results of all of them:

fun main() = runBlocking {
val deferreds: List<Deferred<Int>> = (1..3).map {
async {
delay(1000L * it)
println("Loading $it")
it
}
}
val sum = deferreds.awaitAll().sum()
println("$sum")
}

When each "contributors" request is started in a new coroutine, all of the requests are started asynchronously. A new request can be sent before the result for the previous one is received:

The total loading time is approximately the same as in the CALLBACKS version, but it doesn't need any callbacks. What's more, `async` explicitly emphasizes which parts run concurrently in the code.

### Task 5

In the `Request5Concurrent.kt` file, implement a `loadContributorsConcurrent()` function by using the previous `loadContributorsSuspend()` function.

#### Tip for task 5

You can only start a new coroutine inside a coroutine scope. Copy the content from `loadContributorsSuspend()` to the `coroutineScope` call so that you can call `async` functions there:

suspend fun loadContributorsConcurrent(
service: GitHubService,
req: RequestData

// ...
}

Base your solution on the following scheme:

val deferreds: List<Deferred<List<User>>> = repos.map { repo ->
async {
// load contributors for each repo
}
}
deferreds.awaitAll() // List<List<User>>

#### Solution for task 5

Wrap each "contributors" request with `async` to create as many coroutines as there are repositories. `async` returns `Deferred<List<User>>`. This is not an issue because creating new coroutines is not very resource-intensive, so you can create as many as you need.

1. You can no longer use `flatMap` because the `map` result is now a list of `Deferred` objects, not a list of lists. `awaitAll()` returns `List<List<User>>`, so call `flatten().aggregate()` to get the result:

val deferreds: List<Deferred<List<User>>> = repos.map { repo ->
async {
service.getRepoContributors(req.org, repo.name)
.also { logUsers(repo, it) }
.bodyList()
}
}
deferreds.awaitAll().flatten().aggregate()
}

2. Run the code and check the log. All of the coroutines still run on the main UI thread because multithreading hasn't been employed yet, but you can already see the benefits of running coroutines concurrently.

3. To change this code to run "contributors" coroutines on different threads from the common thread pool, specify `Dispatchers.Default` as the context argument for the `async` function:

async(Dispatchers.Default) { }

- `CoroutineDispatcher` determines what thread or threads the corresponding coroutine should be run on. If you don't specify one as an argument, `async` will use the dispatcher from the outer scope.

- `Dispatchers.Default` represents a shared pool of threads on the JVM. This pool provides a means for parallel execution. It consists of as many threads as there are CPU cores available, but it will still have two threads if there's only one core.
4. Modify the code in the `loadContributorsConcurrent()` function to start new coroutines on different threads from the common thread pool. Also, add additional logging before sending the request:

async(Dispatchers.Default) {
log("starting loading for ${repo.name}")
service.getRepoContributors(req.org, repo.name)
.also { logUsers(repo, it) }
.bodyList()
}

5. Run the program once again. In the log, you can see that each coroutine can be started on one thread from the thread pool and resumed on another:

1946 [DefaultDispatcher-worker-2 @coroutine#4] INFO Contributors - starting loading for kotlin-koans
1946 [DefaultDispatcher-worker-3 @coroutine#5] INFO Contributors - starting loading for dokka
1946 [DefaultDispatcher-worker-1 @coroutine#3] INFO Contributors - starting loading for ts2kt
...
2178 [DefaultDispatcher-worker-1 @coroutine#4] INFO Contributors - kotlin-koans: loaded 45 contributors
2569 [DefaultDispatcher-worker-1 @coroutine#5] INFO Contributors - dokka: loaded 36 contributors
2821 [DefaultDispatcher-worker-2 @coroutine#3] INFO Contributors - ts2kt: loaded 11 contributors

For instance, in this log excerpt, `coroutine#4` is started on the `worker-2` thread and continued on the `worker-1` thread.

In `src/contributors/Contributors.kt`, check the implementation of the CONCURRENT option:

1. To run the coroutine only on the main UI thread, specify `Dispatchers.Main` as an argument:

launch(Dispatchers.Main) {
updateResults()
}

- If the main thread is busy when you start a new coroutine on it, the coroutine becomes suspended and scheduled for execution on this thread. The coroutine will only resume when the thread becomes free.

- It's considered good practice to use the dispatcher from the outer scope rather than explicitly specifying it on each end-point. If you define `loadContributorsConcurrent()` without passing `Dispatchers.Default` as an argument, you can call this function in any context: with a `Default` dispatcher, with the main UI thread, or with a custom dispatcher.

- As you'll see later, when calling `loadContributorsConcurrent()` from tests, you can call it in the context with `TestDispatcher`, which simplifies testing. That makes this solution much more flexible.
2. To specify the dispatcher on the caller side, apply the following change to the project while letting `loadContributorsConcurrent` start coroutines in the inherited context:

launch(Dispatchers.Default) {
val users = loadContributorsConcurrent(service, req)
withContext(Dispatchers.Main) {
updateResults(users, startTime)
}
}

- `updateResults()` should be called on the main UI thread, so you call it with the context of `Dispatchers.Main`.

- `withContext()` calls the given code with the specified coroutine context, is suspended until it completes, and returns the result. An alternative but more verbose way to express this would be to start a new coroutine and explicitly wait (by suspending) until it completes: `launch(context) { ... }.join()`.
3. Run the code and ensure that the coroutines are executed on the threads from the thread pool.

## Structured concurrency

- The coroutine scope is responsible for the structure and parent-child relationships between different coroutines. New coroutines usually need to be started inside a scope.

- The coroutine context stores additional technical information used to run a given coroutine, like the coroutine custom name, or the dispatcher specifying the threads the coroutine should be scheduled on.

When `launch`, `async`, or `runBlocking` are used to start a new coroutine, they automatically create the corresponding scope. All of these functions take a lambda with a receiver as an argument, and `CoroutineScope` is the implicit receiver type:

launch { /* this: CoroutineScope */ }

- New coroutines can only be started inside a scope.

- `launch` and `async` are declared as extensions to `CoroutineScope`, so an implicit or explicit receiver must always be passed when you call them.

- The coroutine started by `runBlocking` is the only exception because `runBlocking` is defined as a top-level function. But because it blocks the current thread, it's intended primarily to be used in `main()` functions and tests as a bridge function.

A new coroutine inside `runBlocking`, `launch`, or `async` is started automatically inside the scope:

fun main() = runBlocking { /* this: CoroutineScope */
launch { /* ... */ }
// the same as:
this.launch { /* ... */ }
}

When you call `launch` inside `runBlocking`, it's called as an extension to the implicit receiver of the `CoroutineScope` type. Alternatively, you could explicitly write `this.launch`.

The nested coroutine (started by `launch` in this example) can be considered as a child of the outer coroutine (started by `runBlocking`). This "parent-child" relationship works through scopes; the child coroutine is started from the scope corresponding to the parent coroutine.

It's possible to create a new scope without starting a new coroutine, by using the `coroutineScope` function. To start new coroutines in a structured way inside a `suspend` function without access to the outer scope, you can create a new coroutine scope that automatically becomes a child of the outer scope that this `suspend` function is called from. `loadContributorsConcurrent()` is a good example.

You can also start a new coroutine from the global scope using `GlobalScope.async` or `GlobalScope.launch`. This will create a top-level "independent" coroutine.

The mechanism behind the structure of the coroutines is called structured concurrency. It provides the following benefits over global scopes:

- The scope is generally responsible for child coroutines, whose lifetime is attached to the lifetime of the scope.

- The scope can automatically cancel child coroutines if something goes wrong or a user changes their mind and decides to revoke the operation.

- The scope automatically waits for the completion of all child coroutines. Therefore, if the scope corresponds to a coroutine, the parent coroutine does not complete until all the coroutines launched in its scope have completed.

When using `GlobalScope.async`, there is no structure that binds several coroutines to a smaller scope. Coroutines started from the global scope are all independent – their lifetime is limited only by the lifetime of the whole application. It's possible to store a reference to the coroutine started from the global scope and wait for its completion or cancel it explicitly, but that won't happen automatically as it would with structured concurrency.

### Canceling the loading of contributors

Create two versions of the function that loads the list of contributors. Compare how both versions behave when you try to cancel the parent coroutine. The first version will use `coroutineScope` to start all of the child coroutines, whereas the second will use `GlobalScope`.

1. In `Request5Concurrent.kt`, add a 3-second delay to the `loadContributorsConcurrent()` function:

// ...
async {
log("starting loading for ${repo.name}")
delay(3000)
// load repo contributors
}
// ...
}

The delay affects all of the coroutines that send requests, so that there's enough time to cancel the loading after the coroutines are started but before the requests are sent.

2. Create the second version of the loading function: copy the implementation of `loadContributorsConcurrent()` to `loadContributorsNotCancellable()` in `Request5NotCancellable.kt` and then remove the creation of a new `coroutineScope`.

3. The `async` calls now fail to resolve, so start them by using `GlobalScope.async`:

suspend fun loadContributorsNotCancellable(
service: GitHubService,
req: RequestData

// ...
GlobalScope.async { // #2
log("starting loading for ${repo.name}")
// load repo contributors
}
// ...
return deferreds.awaitAll().flatten().aggregate() // #3
}

- The function now returns the result directly, not as the last expression inside the lambda (lines `#1` and `#3`).

- All of the "contributors" coroutines are started inside the `GlobalScope`, not as children of the coroutine scope (line `#2`).
4. Run the program and choose the CONCURRENT option to load the contributors.

5. Wait until all of the "contributors" coroutines are started, and then click Cancel. The log shows no new results, which means that all of the requests were indeed canceled:

2896 [AWT-EventQueue-0 @coroutine#1] INFO Contributors - kotlin: loaded 40 repos
2901 [DefaultDispatcher-worker-2 @coroutine#4] INFO Contributors - starting loading for kotlin-koans
...
2909 [DefaultDispatcher-worker-5 @coroutine#36] INFO Contributors - starting loading for mpp-example
/* click on 'cancel' */
/* no requests are sent */

6. Repeat step 5, but this time choose the `NOT_CANCELLABLE` option:

2570 [AWT-EventQueue-0 @coroutine#1] INFO Contributors - kotlin: loaded 30 repos
2579 [DefaultDispatcher-worker-1 @coroutine#4] INFO Contributors - starting loading for kotlin-koans
...
2586 [DefaultDispatcher-worker-6 @coroutine#36] INFO Contributors - starting loading for mpp-example
/* click on 'cancel' */
/* but all the requests are still sent: */
6402 [DefaultDispatcher-worker-5 @coroutine#4] INFO Contributors - kotlin-koans: loaded 45 contributors
...
9555 [DefaultDispatcher-worker-8 @coroutine#36] INFO Contributors - mpp-example: loaded 8 contributors

In this case, no coroutines are canceled, and all the requests are still sent.

7. Check how the cancellation is triggered in the "contributors" program. When the Cancel button is clicked, the main "loading" coroutine is explicitly canceled and the child coroutines are canceled automatically:

interface Contributors {

fun loadContributors() {
// ...
when (getSelectedVariant()) {

launch {
val users = loadContributorsConcurrent(service, req)
updateResults(users, startTime)
}.setUpCancellation() // #1
}
}
}

private fun Job.setUpCancellation() {
val loadingJob = this // #2

// cancel the loading job if the 'cancel' button was clicked:
val listener = ActionListener {
loadingJob.cancel() // #3
updateLoadingStatus(CANCELED)
}
// add a listener to the 'cancel' button:
addCancelListener(listener)

// update the status and remove the listener
// after the loading job is completed
}
}

The `launch` function returns an instance of `Job`. `Job` stores a reference to the "loading coroutine", which loads all of the data and updates the results. You can call the `setUpCancellation()` extension function on it (line `#1`), passing an instance of `Job` as a receiver.

Another way you could express this would be to explicitly write:

val job = launch { }
job.setUpCancellation()

- For readability, you could refer to the `setUpCancellation()` function receiver inside the function with the new `loadingJob` variable (line `#2`).

- Then you could add a listener to the Cancel button so that when it's clicked, the `loadingJob` is canceled (line `#3`).

With structured concurrency, you only need to cancel the parent coroutine and this automatically propagates cancellation to all of the child coroutines.

### Using the outer scope's context

When you start new coroutines inside the given scope, it's much easier to ensure that all of them run with the same context. It is also much easier to replace the context if needed.

Now it's time to learn how using the dispatcher from the outer scope works. The new scope created by the `coroutineScope` or by the coroutine builders always inherits the context from the outer scope. In this case, the outer scope is the scope the `suspend loadContributorsConcurrent()` function was called from:

launch(Dispatchers.Default) { // outer scope
val users = loadContributorsConcurrent(service, req)
// ...
}

All of the nested coroutines are automatically started with the inherited context. The dispatcher is a part of this context. That's why all of the coroutines started by `async` are started with the context of the default dispatcher:

suspend fun loadContributorsConcurrent(
service: GitHubService, req: RequestData

// this scope inherits the context from the outer scope
// ...
async { // nested coroutine started with the inherited context
// ...
}
// ...
}

>
> When you write code with coroutines for UI applications, for example Android ones, it's a common practice to use `CoroutineDispatchers.Main` by default for the top coroutine and then to explicitly put a different dispatcher when you need to run the code on a different thread.

## Showing progress

Despite the information for some repositories being loaded rather quickly, the user only sees the resulting list after all of the data has been loaded. Until then, the loader icon runs showing the progress, but there's no information about the current state or what contributors are already loaded.

You can show the intermediate results earlier and display all of the contributors after loading the data for each of the repositories:

To implement this functionality, in the `src/tasks/Request6Progress.kt`, you'll need to pass the logic updating the UI as a callback, so that it's called on each intermediate state:

suspend fun loadContributorsProgress(
service: GitHubService,
req: RequestData,

) {
// loading the data
// calling `updateResults()` on intermediate states
}

On the call site in `Contributors.kt`, the callback is passed to update the results from the `Main` thread for the PROGRESS option:

launch(Dispatchers.Default) {

updateResults(users, startTime, completed)
}
}
}

- The `updateResults()` parameter is declared as `suspend` in `loadContributorsProgress()`. It's necessary to call `withContext`, which is a `suspend` function inside the corresponding lambda argument.

- `updateResults()` callback takes an additional Boolean parameter as an argument specifying whether the loading has completed and the results are final.

### Task 6

In the `Request6Progress.kt` file, implement the `loadContributorsProgress()` function that shows the intermediate progress. Base it on the `loadContributorsSuspend()` function from `Request4Suspend.kt`.

- Use a simple version without concurrency; you'll add it later in the next section.

- The intermediate list of contributors should be shown in an "aggregated" state, not just the list of users loaded for each repository.

- The total number of contributions for each user should be increased when the data for each new repository is loaded.

#### Solution for task 6

To store the intermediate list of loaded contributors in the "aggregated" state, define an `allUsers` variable which stores the list of users, and then update it after contributors for each new repository are loaded:

) {
val repos = service
.getOrgRepos(req.org)
.also { logRepos(req, it) }
.bodyList()

for ((index, repo) in repos.withIndex()) {
val users = service.getRepoContributors(req.org, repo.name)
.also { logUsers(repo, it) }
.bodyList()

allUsers = (allUsers + users).aggregate()
updateResults(allUsers, index == repos.lastIndex)
}
}

#### Consecutive vs concurrent

An `updateResults()` callback is called after each request is completed:

This code doesn't include concurrency. It's sequential, so you don't need synchronization.

The best option would be to send requests concurrently and update the intermediate results after getting the response for each repository:

To add concurrency, use channels.

## Channels

Writing code with a shared mutable state is quite difficult and error-prone (like in the solution using callbacks). A simpler way is to share information by communication rather than by using a common mutable state. Coroutines can communicate with each other through channels.

Channels are communication primitives that allow data to be passed between coroutines. One coroutine can send some information to a channel, while another can receive that information from it:

A coroutine that sends (produces) information is often called a producer, and a coroutine that receives (consumes) information is called a consumer. One or multiple coroutines can send information to the same channel, and one or multiple coroutines can receive data from it:

When many coroutines receive information from the same channel, each element is handled only once by one of the consumers. Once an element is handled, it is immediately removed from the channel.

You can think of a channel as similar to a collection of elements, or more precisely, a queue, in which elements are added to one end and received from the other. However, there's an important difference: unlike collections, even in their synchronized versions, a channel can suspend `send()` and `receive()` operations. This happens when the channel is empty or full. The channel can be full if the channel size has an upper bound.

`Channel` is represented by three different interfaces: `SendChannel`, `ReceiveChannel`, and `Channel`, with the latter extending the first two. You usually create a channel and give it to producers as a `SendChannel` instance so that only they can send information to the channel. You give a channel to consumers as a `ReceiveChannel` instance so that only they can receive from it. Both `send` and `receive` methods are declared as `suspend`:

suspend fun send(element: E)
fun close(): Boolean
}

suspend fun receive(): E
}

The producer can close a channel to indicate that no more elements are coming.

Several types of channels are defined in the library. They differ in how many elements they can internally store and whether the `send()` call can be suspended or not. For all of the channel types, the `receive()` call behaves similarly: it receives an element if the channel is not empty; otherwise, it is suspended.

Unlimited channel

An unlimited channel is the closest analog to a queue: producers can send elements to this channel and it will keep growing indefinitely. The `send()` call will never be suspended. If the program runs out of memory, you'll get an `OutOfMemoryException`. The difference between an unlimited channel and a queue is that when a consumer tries to receive from an empty channel, it becomes suspended until some new elements are sent.

Buffered channel

The size of a buffered channel is constrained by the specified number. Producers can send elements to this channel until the size limit is reached. All of the elements are internally stored. When the channel is full, the next \`send\` call on it is suspended until more free space becomes available.

Rendezvous channel

The "Rendezvous" channel is a channel without a buffer, the same as a buffered channel with zero size. One of the functions ( `send()` or `receive()`) is always suspended until the other is called.

If the `send()` function is called and there's no suspended `receive()` call ready to process the element, then `send()` is suspended. Similarly, if the `receive()` function is called and the channel is empty or, in other words, there's no suspended `send()` call ready to send the element, the `receive()` call is suspended.

The "rendezvous" name ("a meeting at an agreed time and place") refers to the fact that `send()` and `receive()` should "meet on time".

Conflated channel

A new element sent to the conflated channel will overwrite the previously sent element, so the receiver will always get only the latest element. The `send()` call is never suspended.

When you create a channel, specify its type or the buffer size (if you need a buffered one):

By default, a "Rendezvous" channel is created.

In the following task, you'll create a "Rendezvous" channel, two producer coroutines, and a consumer coroutine:

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.*

launch {
channel.send("A1")
channel.send("A2")
log("A done")
}
launch {
channel.send("B1")
log("B done")
}
launch {
repeat(3) {
val x = channel.receive()
log(x)
}
}
}

fun log(message: Any?) {
println("[${Thread.currentThread().name}] $message")
}

>
> Watch this video for a better understanding of channels.

### Task 7

In `src/tasks/Request7Channels.kt`, implement the function `loadContributorsChannels()` that requests all of the GitHub contributors concurrently and shows intermediate progress at the same time.

Use the previous functions, `loadContributorsConcurrent()` from `Request5Concurrent.kt` and `loadContributorsProgress()` from `Request6Progress.kt`.

#### Tip for task 7

Different coroutines that concurrently receive contributor lists for different repositories can send all of the received results to the same channel:

val channel = Channel<List<User>>()
for (repo in repos) {
launch {
val users = TODO()
// ...
channel.send(users)
}
}

Then the elements from this channel can be received one by one and processed:

repeat(repos.size) {
val users = channel.receive()
// ...
}

Since the `receive()` calls are sequential, no additional synchronization is needed.

#### Solution for task 7

As with the `loadContributorsProgress()` function, you can create an `allUsers` variable to store the intermediate states of the "all contributors" list. Each new list received from the channel is added to the list of all users. You aggregate the result and update the state using the `updateResults` callback:

suspend fun loadContributorsChannels(
service: GitHubService,
req: RequestData,

) = coroutineScope {

val channel = Channel<List<User>>()
for (repo in repos) {
launch {
val users = service.getRepoContributors(req.org, repo.name)
.also { logUsers(repo, it) }
.bodyList()
channel.send(users)
}
}

repeat(repos.size) {
val users = channel.receive()
allUsers = (allUsers + users).aggregate()
updateResults(allUsers, it == repos.lastIndex)
}
}

- Results for different repositories are added to the channel as soon as they are ready. At first, when all of the requests are sent, and no data is received, the `receive()` call is suspended. In this case, the whole "load contributors" coroutine is suspended.

- Then, when the list of users is sent to the channel, the "load contributors" coroutine resumes, the `receive()` call returns this list, and the results are immediately updated.

You can now run the program and choose the CHANNELS option to load the contributors and see the result.

Although neither coroutines nor channels completely remove the complexity that comes with concurrency, they make life easier when you need to understand what's going on.

## Testing coroutines

Let's now test all solutions to check that the solution with concurrent coroutines is faster than the solution with the `suspend` functions, and check that the solution with channels is faster than the simple "progress" one.

In the following task, you'll compare the total running time of the solutions. You'll mock a GitHub service and make this service return results after the given timeouts:

repos request - returns an answer within 1000 ms delay
repo-1 - 1000 ms delay
repo-2 - 1200 ms delay
repo-3 - 800 ms delay

The sequential solution with the `suspend` functions should take around 4000 ms (4000 = 1000 + (1000 + 1200 + 800)). The concurrent solution should take around 2200 ms (2200 = 1000 + max(1000, 1200, 800)).

For the solutions that show progress, you can also check the intermediate results with timestamps.

The corresponding test data is defined in `test/contributors/testData.kt`, and the files `Request4SuspendKtTest`, `Request7ChannelsKtTest`, and so on contain the straightforward tests that use mock service calls.

However, there are two problems here:

- These tests take too long to run. Each test takes around 2 to 4 seconds, and you need to wait for the results each time. It's not very efficient.

- You can't rely on the exact time the solution runs because it still takes additional time to prepare and run the code. You could add a constant, but then the time would differ from machine to machine. The mock service delays should be higher than this constant so you can see a difference. If the constant is 0.5 sec, making the delays 0.1 sec won't be enough.

A better way would be to use special frameworks to test the timing while running the same code several times (which increases the total time even more), but that is complicated to learn and set up.

To solve these problems and make sure that solutions with provided test delays behave as expected, one faster than the other, use virtual time with a special test dispatcher. This dispatcher keeps track of the virtual time passed from the start and runs everything immediately in real time. When you run coroutines on this dispatcher, the `delay` will return immediately and advance the virtual time.

Tests that use this mechanism run fast, but you can still check what happens at different moments in virtual time. The total running time drastically decreases:

To use virtual time, replace the `runBlocking` invocation with a `runTest`. `runTest` takes an extension lambda to `TestScope` as an argument. When you call `delay` in a `suspend` function inside this special scope, `delay` will increase the virtual time instead of delaying in real time:

@Test
fun testDelayInSuspend() = runTest {
val realStartTime = System.currentTimeMillis()
val virtualStartTime = currentTime

foo()
println("${System.currentTimeMillis() - realStartTime} ms") // ~ 6 ms
println("${currentTime - virtualStartTime} ms") // 1000 ms
}

suspend fun foo() {
delay(1000) // auto-advances without delay
println("foo") // executes eagerly when foo() is called
}

You can check the current virtual time using the `currentTime` property of `TestScope`.

The actual running time in this example is several milliseconds, whereas virtual time equals the delay argument, which is 1000 milliseconds.

To get the full effect of "virtual" `delay` in child coroutines, start all of the child coroutines with `TestDispatcher`. Otherwise, it won't work. This dispatcher is automatically inherited from the other `TestScope`, unless you provide a different dispatcher:

@Test
fun testDelayInLaunch() = runTest {
val realStartTime = System.currentTimeMillis()
val virtualStartTime = currentTime

bar()

println("${System.currentTimeMillis() - realStartTime} ms") // ~ 11 ms
println("${currentTime - virtualStartTime} ms") // 1000 ms
}

suspend fun bar() = coroutineScope {
launch {
delay(1000) // auto-advances without delay
println("bar") // executes eagerly when bar() is called
}
}

If `launch` is called with the context of `Dispatchers.Default` in the example above, the test will fail. You'll get an exception saying that the job has not been completed yet.

You can test the `loadContributorsConcurrent()` function this way only if it starts the child coroutines with the inherited context, without modifying it using the `Dispatchers.Default` dispatcher.

>
> The testing API that supports virtual time is Experimental and may change in the future.

By default, the compiler shows warnings if you use the experimental testing API. To suppress these warnings, annotate the test function or the whole class containing the tests with `@OptIn(ExperimentalCoroutinesApi::class)`. Add the compiler argument instructing the compiler that you're using the experimental API:

compileTestKotlin {
kotlinOptions {
freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
}
}

In the project corresponding to this tutorial, the compiler argument has already been added to the Gradle script.

### Task 8

Refactor the following tests in `tests/tasks/` to use virtual time instead of real time:

- Request4SuspendKtTest.kt

- Request5ConcurrentKtTest.kt

- Request6ProgressKtTest.kt

- Request7ChannelsKtTest.kt

Compare the total running times before and after applying your refactoring.

#### Tip for task 8

1. Replace the `runBlocking` invocation with `runTest`, and replace `System.currentTimeMillis()` with `currentTime`:

@Test
fun test() = runTest {
val startTime = currentTime
// action
val totalTime = currentTime - startTime
// testing result
}

2. Uncomment the assertions that check the exact virtual time.

3. Don't forget to add `@UseExperimental(ExperimentalCoroutinesApi::class)`.

#### Solution for task 8

Here are the solutions for the concurrent and channels cases:

fun testConcurrent() = runTest {
val startTime = currentTime
val result = loadContributorsConcurrent(MockGithubService, testRequestData)
Assert.assertEquals("Wrong result for 'loadContributorsConcurrent'", expectedConcurrentResults.users, result)
val totalTime = currentTime - startTime

Assert.assertEquals(
"The calls run concurrently, so the total virtual time should be 2200 ms: " +
"1000 for repos request plus max(1000, 1200, 800) = 1200 for concurrent contributors requests)",
expectedConcurrentResults.timeFromStart, totalTime
)
}

First, check that the results are available exactly at the expected virtual time, and then check the results themselves:

fun testChannels() = runTest {
val startTime = currentTime
var index = 0

val time = currentTime - startTime
Assert.assertEquals(
"Expected intermediate results after ${expected.timeFromStart} ms:",
expected.timeFromStart, time
)
Assert.assertEquals("Wrong intermediate results after $time:", expected.users, users)
}
}

>
> The tests for the remaining "suspend" and "progress" tasks are very similar – you can find them in the project's `solutions` branch.

## What's next

- Check out the Asynchronous Programming with Kotlin workshop at KotlinConf.

- Find out more about using virtual time and the experimental testing package.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/cancellation-and-timeouts.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Cancellation and timeouts

# Cancellation and timeouts

Edit page 16 February 2022

This section covers coroutine cancellation and timeouts.

## Cancelling coroutine execution

In a long-running application, you might need fine-grained control on your background coroutines. For example, a user might have closed the page that launched a coroutine, and now its result is no longer needed and its operation can be cancelled. The launch function returns a Job that can be used to cancel the running coroutine:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val job = launch {

delay(500L)
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancel() // cancels the job
job.join() // waits for job's completion
println("main: Now I can quit.")
//sampleEnd
}

xxxxxxxxxx
val job = launch {

println("job: I'm sleeping $i ...")
delay(500L)
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancel() // cancels the job
job.join() // waits for job's completion
println("main: Now I can quit.")

Open in Playground →

>
> You can get the full code here.

It produces the following output:

job: I'm sleeping 0 ...
job: I'm sleeping 1 ...
job: I'm sleeping 2 ...
main: I'm tired of waiting!
main: Now I can quit.

As soon as main invokes `job.cancel`, we don't see any output from the other coroutine because it was cancelled. There is also a Job extension function cancelAndJoin that combines cancel and join invocations.

## Cancellation is cooperative

Coroutine cancellation is cooperative. A coroutine code has to cooperate to be cancellable. All the suspending functions in `kotlinx.coroutines` are cancellable. They check for cancellation of coroutine and throw CancellationException when cancelled. However, if a coroutine is working in a computation and does not check for cancellation, then it cannot be cancelled, like the following example shows:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val startTime = System.currentTimeMillis()
val job = launch(Dispatchers.Default) {
var nextPrintTime = startTime
var i = 0
while (i < 5) { // computation loop, just wastes CPU
// print a message twice a second

println("job: I'm sleeping ${i++} ...")
nextPrintTime += 500L
}
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")
//sampleEnd
}

xxxxxxxxxx
val startTime = System.currentTimeMillis()
val job = launch(Dispatchers.Default) {
var nextPrintTime = startTime
var i = 0
while (i < 5) { // computation loop, just wastes CPU
// print a message twice a second

println("job: I'm sleeping ${i++} ...")
nextPrintTime += 500L
}
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")

Run it to see that it continues to print "I'm sleeping" even after cancellation until the job completes by itself after five iterations.

The same problem can be observed by catching a CancellationException and not rethrowing it:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val job = launch(Dispatchers.Default) {

// print a message twice a second
println("job: I'm sleeping $i ...")
delay(500)
} catch (e: Exception) {
// log the exception
println(e)
}
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")
//sampleEnd
}

xxxxxxxxxx
val job = launch(Dispatchers.Default) {

try {
// print a message twice a second
println("job: I'm sleeping $i ...")
delay(500)
} catch (e: Exception) {
// log the exception
println(e)
}
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")

While catching `Exception` is an anti-pattern, this issue may surface in more subtle ways, like when using the `runCatching` function, which does not rethrow CancellationException.

## Making computation code cancellable

There are two approaches to making computation code cancellable. The first one is periodically invoking a suspending function that checks for cancellation. There are the yield and ensureActive functions, which are great choices for that purpose. The other one is explicitly checking the cancellation status using isActive. Let us try the latter approach.

Replace `while (i < 5)` in the previous example with `while (isActive)` and rerun it.

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val startTime = System.currentTimeMillis()
val job = launch(Dispatchers.Default) {
var nextPrintTime = startTime
var i = 0
while (isActive) { // cancellable computation loop
// prints a message twice a second

xxxxxxxxxx
val startTime = System.currentTimeMillis()
val job = launch(Dispatchers.Default) {
var nextPrintTime = startTime
var i = 0
while (isActive) { // cancellable computation loop
// prints a message twice a second

As you can see, now this loop is cancelled. isActive is an extension property available inside the coroutine via the CoroutineScope object.

## Closing resources with finally

Cancellable suspending functions throw CancellationException on cancellation, which can be handled in the usual way. For example, the `try {...} finally {...}` expression and Kotlin's use function execute their finalization actions normally when a coroutine is cancelled:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val job = launch {
try {

delay(500L)
}
} finally {
println("job: I'm running finally")
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")
//sampleEnd
}

xxxxxxxxxx
val job = launch {
try {

println("job: I'm sleeping $i ...")
delay(500L)
}
} finally {
println("job: I'm running finally")
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")

Both join and cancelAndJoin wait for all finalization actions to complete, so the example above produces the following output:

job: I'm sleeping 0 ...
job: I'm sleeping 1 ...
job: I'm sleeping 2 ...
main: I'm tired of waiting!
job: I'm running finally
main: Now I can quit.

## Run non-cancellable block

Any attempt to use a suspending function in the `finally` block of the previous example causes CancellationException, because the coroutine running this code is cancelled. Usually, this is not a problem, since all well-behaved closing operations (closing a file, cancelling a job, or closing any kind of communication channel) are usually non-blocking and do not involve any suspending functions. However, in the rare case when you need to suspend in a cancelled coroutine you can wrap the corresponding code in `withContext(NonCancellable) {...}` using withContext function and NonCancellable context as the following example shows:

delay(500L)
}
} finally {
withContext(NonCancellable) {
println("job: I'm running finally")
delay(1000L)
println("job: And I've just delayed for 1 sec because I'm non-cancellable")
}
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")
//sampleEnd
}

println("job: I'm sleeping $i ...")
delay(500L)
}
} finally {
withContext(NonCancellable) {
println("job: I'm running finally")
delay(1000L)
println("job: And I've just delayed for 1 sec because I'm non-cancellable")
}
}
}
delay(1300L) // delay a bit
println("main: I'm tired of waiting!")
job.cancelAndJoin() // cancels the job and waits for its completion
println("main: Now I can quit.")

## Timeout

The most obvious practical reason to cancel execution of a coroutine is because its execution time has exceeded some timeout. While you can manually track the reference to the corresponding Job and launch a separate coroutine to cancel the tracked one after delay, there is a ready to use withTimeout function that does it. Look at the following example:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
withTimeout(1300L) {

delay(500L)
}
}
//sampleEnd
}

xxxxxxxxxx
withTimeout(1300L) {

println("I'm sleeping $i ...")
delay(500L)
}
}

I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
Exception in thread "main" kotlinx.coroutines.TimeoutCancellationException: Timed out waiting for 1300 ms

The TimeoutCancellationException that is thrown by withTimeout is a subclass of CancellationException. We have not seen its stack trace printed on the console before. That is because inside a cancelled coroutine `CancellationException` is considered to be a normal reason for coroutine completion. However, in this example we have used `withTimeout` right inside the `main` function.

Since cancellation is just an exception, all resources are closed in the usual way. You can wrap the code with timeout in a `try {...} catch (e: TimeoutCancellationException) {...}` block if you need to do some additional action specifically on any kind of timeout or use the withTimeoutOrNull function that is similar to withTimeout but returns `null` on timeout instead of throwing an exception:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val result = withTimeoutOrNull(1300L) {

delay(500L)
}
"Done" // will get cancelled before it produces this result
}
println("Result is $result")
//sampleEnd
}

xxxxxxxxxx
val result = withTimeoutOrNull(1300L) {

println("I'm sleeping $i ...")
delay(500L)
}
"Done" // will get cancelled before it produces this result
}
println("Result is $result")

There is no longer an exception when running this code:

I'm sleeping 0 ...
I'm sleeping 1 ...
I'm sleeping 2 ...
Result is null

## Asynchronous timeout and resources

The timeout event in withTimeout is asynchronous with respect to the code running in its block and may happen at any time, even right before the return from inside of the timeout block. Keep this in mind if you open or acquire some resource inside the block that needs closing or release outside of the block.

For example, here we imitate a closeable resource with the `Resource` class that simply keeps track of how many times it was created by incrementing the `acquired` counter and decrementing the counter in its `close` function. Now let us create a lot of coroutines, each of which creates a `Resource` at the end of the `withTimeout` block and releases the resource outside the block. We add a small delay so that it is more likely that the timeout occurs right when the `withTimeout` block is already finished, which will cause a resource leak.

import kotlinx.coroutines.*
//sampleStart
var acquired = 0
class Resource {
init { acquired++ } // Acquire the resource
fun close() { acquired-- } // Release the resource
}
fun main() {
runBlocking {
repeat(10_000) { // Launch 10K coroutines
launch {
val resource = withTimeout(60) { // Timeout of 60 ms
delay(50) // Delay for 50 ms
Resource() // Acquire a resource and return it from withTimeout block
}
resource.close() // Release the resource
}
}
}
// Outside of runBlocking all coroutines have completed
println(acquired) // Print the number of resources still acquired
}
//sampleEnd

xxxxxxxxxx
var acquired = 0
​
class Resource {
init { acquired++ } // Acquire the resource
fun close() { acquired-- } // Release the resource
}
​
fun main() {
runBlocking {
repeat(10_000) { // Launch 10K coroutines
launch {
val resource = withTimeout(60) { // Timeout of 60 ms
delay(50) // Delay for 50 ms
Resource() // Acquire a resource and return it from withTimeout block
}
resource.close() // Release the resource
}
}
}
// Outside of runBlocking all coroutines have completed
println(acquired) // Print the number of resources still acquired
}

>
> Note that incrementing and decrementing `acquired` counter here from 10K coroutines is completely thread-safe, since it always happens from the same thread, the one used by `runBlocking`. More on that will be explained in the chapter on coroutine context.

To work around this problem you can store a reference to the resource in a variable instead of returning it from the `withTimeout` block.

import kotlinx.coroutines.*
var acquired = 0
class Resource {
init { acquired++ } // Acquire the resource
fun close() { acquired-- } // Release the resource
}
fun main() {
//sampleStart
runBlocking {
repeat(10_000) { // Launch 10K coroutines
launch {
var resource: Resource? = null // Not acquired yet
try {
withTimeout(60) { // Timeout of 60 ms
delay(50) // Delay for 50 ms
resource = Resource() // Store a resource to the variable if acquired
}
// We can do something else with the resource here
} finally {
resource?.close() // Release the resource if it was acquired
}
}
}
}
// Outside of runBlocking all coroutines have completed
println(acquired) // Print the number of resources still acquired
//sampleEnd
}

xxxxxxxxxx
runBlocking {
repeat(10_000) { // Launch 10K coroutines
launch {
var resource: Resource? = null // Not acquired yet
try {
withTimeout(60) { // Timeout of 60 ms
delay(50) // Delay for 50 ms
resource = Resource() // Store a resource to the variable if acquired
}
// We can do something else with the resource here
} finally {
resource?.close() // Release the resource if it was acquired
}
}
}
}
// Outside of runBlocking all coroutines have completed
println(acquired) // Print the number of resources still acquired

This example always prints zero. Resources do not leak.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/composing-suspending-functions.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Composing suspending functions

# Composing suspending functions

Edit page 16 February 2022

This section covers various approaches to composition of suspending functions.

## Sequential by default

Assume that we have two suspending functions defined elsewhere that do something useful like some kind of remote service call or computation. We just pretend they are useful, but actually each one just delays for a second for the purpose of this example:

suspend fun doSomethingUsefulOne(): Int {
delay(1000L) // pretend we are doing something useful here
return 13
}

suspend fun doSomethingUsefulTwo(): Int {
delay(1000L) // pretend we are doing something useful here, too
return 29
}

What do we do if we need them to be invoked sequentially — first `doSomethingUsefulOne` and then `doSomethingUsefulTwo`, and compute the sum of their results? In practice, we do this if we use the result of the first function to make a decision on whether we need to invoke the second one or to decide on how to invoke it.

We use a normal sequential invocation, because the code in the coroutine, just like in the regular code, is sequential by default. The following example demonstrates it by measuring the total time it takes to execute both suspending functions:

import kotlinx.coroutines.*
import kotlin.system.*

//sampleStart
val time = measureTimeMillis {
val one = doSomethingUsefulOne()
val two = doSomethingUsefulTwo()
println("The answer is ${one + two}")
}
println("Completed in $time ms")
//sampleEnd
}
suspend fun doSomethingUsefulOne(): Int {
delay(1000L) // pretend we are doing something useful here
return 13
}
suspend fun doSomethingUsefulTwo(): Int {
delay(1000L) // pretend we are doing something useful here, too
return 29
}

xxxxxxxxxx
val time = measureTimeMillis {
val one = doSomethingUsefulOne()
val two = doSomethingUsefulTwo()
println("The answer is ${one + two}")
}
println("Completed in $time ms")

Open in Playground →

>
> You can get the full code here.

It produces something like this:

The answer is 42
Completed in 2017 ms

## Concurrent using async

What if there are no dependencies between invocations of `doSomethingUsefulOne` and `doSomethingUsefulTwo` and we want to get the answer faster, by doing both concurrently? This is where async comes to help.

Conceptually, async is just like launch. It starts a separate coroutine which is a light-weight thread that works concurrently with all the other coroutines. The difference is that `launch` returns a Job and does not carry any resulting value, while `async` returns a Deferred — a light-weight non-blocking future that represents a promise to provide a result later. You can use `.await()` on a deferred value to get its eventual result, but `Deferred` is also a `Job`, so you can cancel it if needed.

//sampleStart
val time = measureTimeMillis {
val one = async { doSomethingUsefulOne() }
val two = async { doSomethingUsefulTwo() }
println("The answer is ${one.await() + two.await()}")
}
println("Completed in $time ms")
//sampleEnd
}
suspend fun doSomethingUsefulOne(): Int {
delay(1000L) // pretend we are doing something useful here
return 13
}
suspend fun doSomethingUsefulTwo(): Int {
delay(1000L) // pretend we are doing something useful here, too
return 29
}

xxxxxxxxxx
val time = measureTimeMillis {
val one = async { doSomethingUsefulOne() }
val two = async { doSomethingUsefulTwo() }
println("The answer is ${one.await() + two.await()}")
}
println("Completed in $time ms")

The answer is 42
Completed in 1017 ms

This is twice as fast, because the two coroutines execute concurrently. Note that concurrency with coroutines is always explicit.

## Lazily started async

Optionally, async can be made lazy by setting its `start` parameter to CoroutineStart.LAZY. In this mode it only starts the coroutine when its result is required by await, or if its `Job`'s start function is invoked. Run the following example:

//sampleStart
val time = measureTimeMillis {
val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
// some computation
one.start() // start the first one
two.start() // start the second one
println("The answer is ${one.await() + two.await()}")
}
println("Completed in $time ms")
//sampleEnd
}
suspend fun doSomethingUsefulOne(): Int {
delay(1000L) // pretend we are doing something useful here
return 13
}
suspend fun doSomethingUsefulTwo(): Int {
delay(1000L) // pretend we are doing something useful here, too
return 29
}

xxxxxxxxxx
val time = measureTimeMillis {
val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
// some computation
one.start() // start the first one
two.start() // start the second one
println("The answer is ${one.await() + two.await()}")
}
println("Completed in $time ms")

So, here the two coroutines are defined but not executed as in the previous example, but the control is given to the programmer on when exactly to start the execution by calling start. We first start `one`, then start `two`, and then await for the individual coroutines to finish.

Note that if we just call await in `println` without first calling start on individual coroutines, this will lead to sequential behavior, since await starts the coroutine execution and waits for its finish, which is not the intended use-case for laziness. The use-case for `async(start = CoroutineStart.LAZY)` is a replacement for the standard lazy function in cases when computation of the value involves suspending functions.

>
> This programming style with async functions is provided here only for illustration, because it is a popular style in other programming languages. Using this style with Kotlin coroutines is strongly discouraged for the reasons explained below.

>
> GlobalScope is a delicate API that can backfire in non-trivial ways, one of which will be explained below, so you must explicitly opt-in into using `GlobalScope` with `@OptIn(DelicateCoroutinesApi::class)`.

fun somethingUsefulOneAsync() = GlobalScope.async {
doSomethingUsefulOne()
}

fun somethingUsefulTwoAsync() = GlobalScope.async {
doSomethingUsefulTwo()
}

Note that these `xxxAsync` functions are notsuspending functions. They can be used from anywhere. However, their use always implies asynchronous (here meaning concurrent) execution of their action with the invoking code.

The following example shows their use outside of coroutine:

import kotlinx.coroutines.*
import kotlin.system.*
//sampleStart
// note that we don't have `runBlocking` to the right of `main` in this example
fun main() {
val time = measureTimeMillis {
// we can initiate async actions outside of a coroutine
val one = somethingUsefulOneAsync()
val two = somethingUsefulTwoAsync()
// but waiting for a result must involve either suspending or blocking.
// here we use `runBlocking { ... }` to block the main thread while waiting for the result
runBlocking {
println("The answer is ${one.await() + two.await()}")
}
}
println("Completed in $time ms")
}
//sampleEnd
@OptIn(DelicateCoroutinesApi::class)
fun somethingUsefulOneAsync() = GlobalScope.async {
doSomethingUsefulOne()
}
@OptIn(DelicateCoroutinesApi::class)
fun somethingUsefulTwoAsync() = GlobalScope.async {
doSomethingUsefulTwo()
}
suspend fun doSomethingUsefulOne(): Int {
delay(1000L) // pretend we are doing something useful here
return 13
}
suspend fun doSomethingUsefulTwo(): Int {
delay(1000L) // pretend we are doing something useful here, too
return 29
}

xxxxxxxxxx
// note that we don't have `runBlocking` to the right of `main` in this example
fun main() {
val time = measureTimeMillis {
// we can initiate async actions outside of a coroutine
val one = somethingUsefulOneAsync()
val two = somethingUsefulTwoAsync()
// but waiting for a result must involve either suspending or blocking.
// here we use `runBlocking { ... }` to block the main thread while waiting for the result
runBlocking {
println("The answer is ${one.await() + two.await()}")
}
}
println("Completed in $time ms")
}

Consider what happens if between the `val one = somethingUsefulOneAsync()` line and `one.await()` expression there is some logic error in the code, and the program throws an exception, and the operation that was being performed by the program aborts. Normally, a global error-handler could catch this exception, log and report the error for developers, but the program could otherwise continue doing other operations. However, here we have `somethingUsefulOneAsync` still running in the background, even though the operation that initiated it was aborted. This problem does not happen with structured concurrency, as shown in the section below.

## Structured concurrency with async

Let's refactor the Concurrent using async example into a function that runs `doSomethingUsefulOne` and `doSomethingUsefulTwo` concurrently and returns their combined results. Since async is a CoroutineScope extension, we'll use the coroutineScope function to provide the necessary scope:

suspend fun concurrentSum(): Int = coroutineScope {
val one = async { doSomethingUsefulOne() }
val two = async { doSomethingUsefulTwo() }
one.await() + two.await()
}

This way, if something goes wrong inside the code of the `concurrentSum` function, and it throws an exception, all the coroutines that were launched in its scope will be cancelled.

//sampleStart
val time = measureTimeMillis {
println("The answer is ${concurrentSum()}")
}
println("Completed in $time ms")
//sampleEnd
}
suspend fun concurrentSum(): Int = coroutineScope {
val one = async { doSomethingUsefulOne() }
val two = async { doSomethingUsefulTwo() }
one.await() + two.await()
}
suspend fun doSomethingUsefulOne(): Int {
delay(1000L) // pretend we are doing something useful here
return 13
}
suspend fun doSomethingUsefulTwo(): Int {
delay(1000L) // pretend we are doing something useful here, too
return 29
}

xxxxxxxxxx
val time = measureTimeMillis {
println("The answer is ${concurrentSum()}")
}
println("Completed in $time ms")

We still have concurrent execution of both operations, as evident from the output of the above `main` function:

Cancellation is always propagated through coroutines hierarchy:

import kotlinx.coroutines.*

try {
failedConcurrentSum()
} catch(e: ArithmeticException) {
println("Computation failed with ArithmeticException")
}
}
suspend fun failedConcurrentSum(): Int = coroutineScope {

try {
delay(Long.MAX_VALUE) // Emulates very long computation
42
} finally {
println("First child was cancelled")
}
}

println("Second child throws an exception")
throw ArithmeticException()
}
one.await() + two.await()
}

xxxxxxxxxx
import kotlinx.coroutines.*
​

try {
failedConcurrentSum()
} catch(e: ArithmeticException) {
println("Computation failed with ArithmeticException")
}
}
​
suspend fun failedConcurrentSum(): Int = coroutineScope {

Note how both the first `async` and the awaiting parent are cancelled on failure of one of the children (namely, `two`):

Second child throws an exception
First child was cancelled
Computation failed with ArithmeticException

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Coroutine context and dispatchers

# Coroutine context and dispatchers

Edit page 16 February 2022

Coroutines always execute in some context represented by a value of the CoroutineContext type, defined in the Kotlin standard library.

The coroutine context is a set of various elements. The main elements are the Job of the coroutine, which we've seen before, and its dispatcher, which is covered in this section.

## Dispatchers and threads

The coroutine context includes a coroutine dispatcher (see CoroutineDispatcher) that determines what thread or threads the corresponding coroutine uses for its execution. The coroutine dispatcher can confine coroutine execution to a specific thread, dispatch it to a thread pool, or let it run unconfined.

All coroutine builders like launch and async accept an optional CoroutineContext parameter that can be used to explicitly specify the dispatcher for the new coroutine and other context elements.

Try the following example:

import kotlinx.coroutines.*

//sampleStart
launch { // context of the parent, main runBlocking coroutine
println("main runBlocking : I'm working in thread ${Thread.currentThread().name}")
}
launch(Dispatchers.Unconfined) { // not confined -- will work with main thread
println("Unconfined : I'm working in thread ${Thread.currentThread().name}")
}
launch(Dispatchers.Default) { // will get dispatched to DefaultDispatcher
println("Default : I'm working in thread ${Thread.currentThread().name}")
}
launch(newSingleThreadContext("MyOwnThread")) { // will get its own new thread
println("newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
}
//sampleEnd
}

xxxxxxxxxx
launch { // context of the parent, main runBlocking coroutine
println("main runBlocking : I'm working in thread ${Thread.currentThread().name}")
}
launch(Dispatchers.Unconfined) { // not confined -- will work with main thread
println("Unconfined : I'm working in thread ${Thread.currentThread().name}")
}
launch(Dispatchers.Default) { // will get dispatched to DefaultDispatcher
println("Default : I'm working in thread ${Thread.currentThread().name}")
}
launch(newSingleThreadContext("MyOwnThread")) { // will get its own new thread
println("newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
}

Open in Playground →

>
> You can get the full code here.

It produces the following output (maybe in different order):

Unconfined : I'm working in thread main
Default : I'm working in thread DefaultDispatcher-worker-1
newSingleThreadContext: I'm working in thread MyOwnThread
main runBlocking : I'm working in thread main

When `launch { ... }` is used without parameters, it inherits the context (and thus dispatcher) from the CoroutineScope it is being launched from. In this case, it inherits the context of the main `runBlocking` coroutine which runs in the `main` thread.

Dispatchers.Unconfined is a special dispatcher that also appears to run in the `main` thread, but it is, in fact, a different mechanism that is explained later.

The default dispatcher is used when no other dispatcher is explicitly specified in the scope. It is represented by Dispatchers.Default and uses a shared background pool of threads.

newSingleThreadContext creates a thread for the coroutine to run. A dedicated thread is a very expensive resource. In a real application it must be either released, when no longer needed, using the close function, or stored in a top-level variable and reused throughout the application.

## Unconfined vs confined dispatcher

The Dispatchers.Unconfined coroutine dispatcher starts a coroutine in the caller thread, but only until the first suspension point. After suspension it resumes the coroutine in the thread that is fully determined by the suspending function that was invoked. The unconfined dispatcher is appropriate for coroutines which neither consume CPU time nor update any shared data (like UI) confined to a specific thread.

On the other side, the dispatcher is inherited from the outer CoroutineScope by default. The default dispatcher for the runBlocking coroutine, in particular, is confined to the invoker thread, so inheriting it has the effect of confining execution to this thread with predictable FIFO scheduling.

//sampleStart
launch(Dispatchers.Unconfined) { // not confined -- will work with main thread
println("Unconfined : I'm working in thread ${Thread.currentThread().name}")
delay(500)
println("Unconfined : After delay in thread ${Thread.currentThread().name}")
}
launch { // context of the parent, main runBlocking coroutine
println("main runBlocking: I'm working in thread ${Thread.currentThread().name}")
delay(1000)
println("main runBlocking: After delay in thread ${Thread.currentThread().name}")
}
//sampleEnd
}

xxxxxxxxxx
launch(Dispatchers.Unconfined) { // not confined -- will work with main thread
println("Unconfined : I'm working in thread ${Thread.currentThread().name}")
delay(500)
println("Unconfined : After delay in thread ${Thread.currentThread().name}")
}
launch { // context of the parent, main runBlocking coroutine
println("main runBlocking: I'm working in thread ${Thread.currentThread().name}")
delay(1000)
println("main runBlocking: After delay in thread ${Thread.currentThread().name}")
}

Produces the output:

Unconfined : I'm working in thread main
main runBlocking: I'm working in thread main
Unconfined : After delay in thread kotlinx.coroutines.DefaultExecutor
main runBlocking: After delay in thread main

>
> The unconfined dispatcher is an advanced mechanism that can be helpful in certain corner cases where dispatching of a coroutine for its execution later is not needed or produces undesirable side-effects, because some operation in a coroutine must be performed right away. The unconfined dispatcher should not be used in general code.

## Debugging coroutines and threads

Coroutines can suspend on one thread and resume on another thread. Even with a single-threaded dispatcher it might be hard to figure out what the coroutine was doing, where, and when if you don't have special tooling.

### Debugging with IDEA

>
> Debugging works for versions 1.3.8 or later of `kotlinx-coroutines-core`.

The Debug tool window contains the Coroutines tab. In this tab, you can find information about both currently running and suspended coroutines. The coroutines are grouped by the dispatcher they are running on.

With the coroutine debugger, you can:

- Check the state of each coroutine.

- See the values of local and captured variables for both running and suspended coroutines.

- See a full coroutine creation stack, as well as a call stack inside the coroutine. The stack includes all frames with variable values, even those that would be lost during standard debugging.

- Get a full report that contains the state of each coroutine and its stack. To obtain it, right-click inside the Coroutines tab, and then click Get Coroutines Dump.

To start coroutine debugging, you just need to set breakpoints and run the application in debug mode.

Learn more about coroutines debugging in the tutorial.

### Debugging using logging

Another approach to debugging applications with threads without Coroutine Debugger is to print the thread name in the log file on each log statement. This feature is universally supported by logging frameworks. When using coroutines, the thread name alone does not give much of a context, so `kotlinx.coroutines` includes debugging facilities to make it easier.

Run the following code with `-Dkotlinx.coroutines.debug` JVM option:

import kotlinx.coroutines.*
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

//sampleStart
val a = async {
log("I'm computing a piece of the answer")
6
}
val b = async {
log("I'm computing another piece of the answer")
7
}
log("The answer is ${a.await() * b.await()}")
//sampleEnd
}

xxxxxxxxxx
val a = async {
log("I'm computing a piece of the answer")
6
}
val b = async {
log("I'm computing another piece of the answer")
7
}
log("The answer is ${a.await() * b.await()}")

There are three coroutines. The main coroutine (#1) inside `runBlocking` and two coroutines computing the deferred values `a` (#2) and `b` (#3). They are all executing in the context of `runBlocking` and are confined to the main thread. The output of this code is:

[main @coroutine#2] I'm computing a piece of the answer
[main @coroutine#3] I'm computing another piece of the answer
[main @coroutine#1] The answer is 42

>
> Debugging mode is also turned on when JVM is run with `-ea` option. You can read more about debugging facilities in the documentation of the DEBUG\_PROPERTY\_NAME property.

## Jumping between threads

Run the following code with the `-Dkotlinx.coroutines.debug` JVM option (see debug):

fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

fun main() {

log("Started in ctx1")
withContext(ctx2) {
log("Working in ctx2")
}
log(".

The example above demonstrates new techniques in coroutine usage.

The first technique shows how to use runBlocking with a specified context.

The second technique involves calling withContext, which may suspend the current coroutine and switch to a new context—provided the new context differs from the existing one. Specifically, if you specify a different CoroutineDispatcher, extra dispatches are required: the block is scheduled on the new dispatcher, and once it finishes, execution returns to the original dispatcher.

As a result, the output of the above code is:

[Ctx1 @coroutine#1] Started in ctx1
[Ctx2 @coroutine#1] Working in ctx2
[Ctx1 @coroutine#1] when they're no longer needed.

## Job in the context

The coroutine's Job is part of its context, and can be retrieved from it using the `coroutineContext[Job]` expression:

//sampleStart
println("My job is ${coroutineContext[Job]}")
//sampleEnd
}

xxxxxxxxxx
println("My job is ${coroutineContext[Job]}")

In debug mode, it outputs something like this:

My job is "coroutine#1":BlockingCoroutine{Active}@6d311334

Note that isActive in CoroutineScope is just a convenient shortcut for `coroutineContext[Job]?.isActive == true`.

## Children of a coroutine

When a coroutine is launched in the CoroutineScope of another coroutine, it inherits its context via CoroutineScope.coroutineContext and the Job of the new coroutine becomes a child of the parent coroutine's job. When the parent coroutine is cancelled, all its children are recursively cancelled, too.

However, this parent-child relation can be explicitly overridden in one of two ways:

1. When a different scope is explicitly specified when launching a coroutine (for example, `GlobalScope.launch`), it does not inherit a `Job` from the parent scope.

2. When a different `Job` object is passed as the context for the new coroutine (as shown in the example below), it overrides the `Job` of the parent scope.

In both cases, the launched coroutine is not tied to the scope it was launched from and operates independently.

//sampleStart
// launch a coroutine to process some kind of incoming request
val request = launch {
// it spawns two other jobs
launch(Job()) {
println("job1: I run in my own Job and execute independently!")
delay(1000)
println("job1: I am not affected by cancellation of the request")
}
// and the other inherits the parent context
launch {
delay(100)
println("job2: I am a child of the request coroutine")
delay(1000)
println("job2: I will not execute this line if my parent request is cancelled")
}
}
delay(500)
request.cancel() // cancel processing of the request
println("main: Who has survived request cancellation?")
delay(1000) // delay the main thread for a second to see what happens
//sampleEnd
}

xxxxxxxxxx
// launch a coroutine to process some kind of incoming request
val request = launch {
// it spawns two other jobs
launch(Job()) {
println("job1: I run in my own Job and execute independently!")
delay(1000)
println("job1: I am not affected by cancellation of the request")
}
// and the other inherits the parent context
launch {
delay(100)
println("job2: I am a child of the request coroutine")
delay(1000)
println("job2: I will not execute this line if my parent request is cancelled")
}
}
delay(500)
request.cancel() // cancel processing of the request
println("main: Who has survived request cancellation?")
delay(1000) // delay the main thread for a second to see what happens

The output of this code is:

job1: I run in my own Job and execute independently!
job2: I am a child of the request coroutine
main: Who has survived request cancellation?
job1: I am not affected by cancellation of the request

## Parental responsibilities

A parent coroutine always waits for the completion of all its children. A parent does not have to explicitly track all the children it launches, and it does not have to use Job.join to wait for them at the end:

//sampleStart
// launch a coroutine to process some kind of incoming request
val request = launch {

launch {
delay((i + 1) * 200L) // variable delay 200ms, 400ms, 600ms
println("Coroutine $i is done")
}
}
println("request: I'm done and I don't explicitly join my children that are still active")
}
request.join() // wait for completion of the request, including all its children
println("Now processing of the request is complete")
//sampleEnd
}

xxxxxxxxxx
// launch a coroutine to process some kind of incoming request
val request = launch {

launch {
delay((i + 1) * 200L) // variable delay 200ms, 400ms, 600ms
println("Coroutine $i is done")
}
}
println("request: I'm done and I don't explicitly join my children that are still active")
}
request.join() // wait for completion of the request, including all its children
println("Now processing of the request is complete")

The result is going to be:

request: I'm done and I don't explicitly join my children that are still active
Coroutine 0 is done
Coroutine 1 is done
Coroutine 2 is done
Now processing of the request is complete

## Naming coroutines for debugging

Automatically assigned ids are good when coroutines log often and you just need to correlate log records coming from the same coroutine. However, when a coroutine is tied to the processing of a specific request or doing some specific background task, it is better to name it explicitly for debugging purposes. The CoroutineName context element serves the same purpose as the thread name. It is included in the thread name that is executing this coroutine when the debugging mode is turned on.

The following example demonstrates this concept:

import kotlinx.coroutines.*
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")
fun main() = runBlocking(CoroutineName("main")) {
//sampleStart
log("Started main coroutine")
// run two background value computations
val v1 = async(CoroutineName("v1coroutine")) {
delay(500)
log("Computing v1")
6
}
val v2 = async(CoroutineName("v2coroutine")) {
delay(1000)
log("Computing v2")
7
}
log("The answer for v1 * v2 = ${v1.await() * v2.await()}")
//sampleEnd
}

xxxxxxxxxx
log("Started main coroutine")
// run two background value computations
val v1 = async(CoroutineName("v1coroutine")) {
delay(500)
log("Computing v1")
6
}
val v2 = async(CoroutineName("v2coroutine")) {
delay(1000)
log("Computing v2")
7
}
log("The answer for v1 * v2 = ${v1.await() * v2.await()}")

The output it produces with `-Dkotlinx.coroutines.debug` JVM option is similar to:

[main @main#1] Started main coroutine
[main @v1coroutine#2] Computing v1
[main @v2coroutine#3] Computing v2
[main @main#1] The answer for v1 * v2 = 42

## Combining context elements

Sometimes we need to define multiple elements for a coroutine context. We can use the `+` operator for that. For example, we can launch a coroutine with an explicitly specified dispatcher and an explicitly specified name at the same time:

//sampleStart
launch(Dispatchers.Default + CoroutineName("test")) {
println("I'm working in thread ${Thread.currentThread().name}")
}
//sampleEnd
}

xxxxxxxxxx
launch(Dispatchers.Default + CoroutineName("test")) {
println("I'm working in thread ${Thread.currentThread().name}")
}

The output of this code with the `-Dkotlinx.coroutines.debug` JVM option is:

I'm working in thread DefaultDispatcher-worker-1 @test#2

## Coroutine scope

Let us put our knowledge about contexts, children, and jobs together. Assume that our application has an object with a lifecycle, but that object is not a coroutine. For example, we are writing an Android application, and launching various coroutines in the context of an Android activity to perform asynchronous operations to fetch and update data, do animations, etc. These coroutines must be cancelled when the activity is destroyed to avoid memory leaks. We, of course, can manipulate contexts and jobs manually to tie the lifecycles of the activity and its coroutines, but `kotlinx.coroutines` provides an abstraction encapsulating that: CoroutineScope. You should be already familiar with the coroutine scope as all coroutine builders are declared as extensions on it.

We manage the lifecycles of our coroutines by creating an instance of CoroutineScope tied to the lifecycle of our activity. A `CoroutineScope` instance can be created by the CoroutineScope() or MainScope() factory functions. The former creates a general-purpose scope, while the latter creates a scope for UI applications and uses Dispatchers.Main as the default dispatcher:

class Activity {
private val mainScope = MainScope()

fun destroy() {
mainScope.cancel()
}
// to be continued ...

Now, we can launch coroutines in the scope of this `Activity` using the defined `mainScope`. For the demo, we launch ten coroutines that delay for a different time:

// class Activity continues
fun doSomething() {
// launch ten coroutines for a demo, each working for a different time

delay((i + 1) * 200L) // variable delay 200ms, 400ms, ... etc
println("Coroutine $i is done")
}
}
}
} // class Activity ends

In our main function we create the activity, call our test `doSomething` function, and destroy the activity after 500ms. This cancels all the coroutines that were launched from `doSomething`. We can see that because after the destruction of the activity, no more messages are printed, even if we wait a little longer.

import kotlinx.coroutines.*
class Activity {
private val mainScope = CoroutineScope(Dispatchers.Default) // use Default for test purposes

fun destroy() {
mainScope.cancel()
}
fun doSomething() {
// launch ten coroutines for a demo, each working for a different time

//sampleStart
val activity = Activity()
activity.doSomething() // run test function
println("Launched coroutines")
delay(500L) // delay for half a second
println("Destroying activity!")
activity.destroy() // cancels all coroutines
delay(1000) // visually confirm that they don't work
//sampleEnd
}

xxxxxxxxxx
val activity = Activity()
activity.doSomething() // run test function
println("Launched coroutines")
delay(500L) // delay for half a second
println("Destroying activity!")
activity.destroy() // cancels all coroutines
delay(1000) // visually confirm that they don't work

The output of this example is:

Launched coroutines
Coroutine 0 is done
Coroutine 1 is done
Destroying activity!

>
> Note that Android has first-party support for coroutine scope in all entities with the lifecycle. See the corresponding documentation.

### Thread-local data

Sometimes it is convenient to be able to pass some thread-local data to or between coroutines. However, since they are not bound to any particular thread, this will likely lead to boilerplate if done manually.

For `ThreadLocal`, the asContextElement extension function is here for the rescue. It creates an additional context element which keeps the value of the given `ThreadLocal` and restores it every time the coroutine switches its context.

It is easy to demonstrate it in action:

//sampleStart
threadLocal.set("main")
println("Pre-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
val job = launch(Dispatchers.Default + threadLocal.asContextElement(value = "launch")) {
println("Launch start, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
yield()
println("After yield, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
}
job.join()
println("Post-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
//sampleEnd
}

xxxxxxxxxx
threadLocal.set("main")
println("Pre-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
val job = launch(Dispatchers.Default + threadLocal.asContextElement(value = "launch")) {
println("Launch start, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
yield()
println("After yield, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
}
job.join()
println("Post-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")

In this example, we launch a new coroutine in a background thread pool using Dispatchers.Default, so it works on different threads from the thread pool, but it still has the value of the thread local variable that we specified using `threadLocal.asContextElement(value = "launch")`, no matter which thread the coroutine is executed on. Thus, the output (with debug) is:

Pre-main, current thread: Thread[main @coroutine#1,5,main], thread local value: 'main'
Launch start, current thread: Thread[DefaultDispatcher-worker-1 @coroutine#2,5,main], thread local value: 'launch'
After yield, current thread: Thread[DefaultDispatcher-worker-2 @coroutine#2,5,main], thread local value: 'launch'
Post-main, current thread: Thread[main @coroutine#1,5,main], thread local value: 'main'

It's easy to forget to set the corresponding context element. The thread-local variable accessed from the coroutine may then have an unexpected value if the thread running the coroutine is different. To avoid such situations, it is recommended to use the ensurePresent method and fail-fast on improper usages.

`ThreadLocal` has first-class support and can be used with any primitive `kotlinx.coroutines` provides. It has one key limitation, though: when a thread-local is mutated, a new value is not propagated to the coroutine caller (because a context element cannot track all `ThreadLocal` object accesses), and the updated value is lost on the next suspension. Use withContext to update the value of the thread-local in a coroutine, see asContextElement for more details.

Alternatively, a value can be stored in a mutable box like `class Counter(var i: Int)`, which is, in turn, stored in a thread-local variable. However, in this case, you are fully responsible to synchronize potentially concurrent modifications to the variable in this mutable box.

For advanced usage, for example, for integration with logging MDC, transactional contexts or any other libraries that internally use thread-locals for passing data, see the documentation of the ThreadContextElement interface that should be implemented.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/flow.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Asynchronous Flow

# Asynchronous Flow

Edit page 16 February 2022

A suspending function asynchronously returns a single value, but how can we return multiple asynchronously computed values? This is where Kotlin Flows come in.

## Representing multiple values

Multiple values can be represented in Kotlin using collections. For example, we can have a `simple` function that returns a List of three numbers and then print them all using forEach:

fun main() {

}

xxxxxxxxxx

Open in Playground →

>
> You can get the full code here.

This code outputs:

1
2
3

### Sequences

If we are computing the numbers with some CPU-consuming blocking code (each computation taking 100ms), then we can represent the numbers using a Sequence:

for (i in 1..3) {
Thread.sleep(100) // pretend we are computing it
yield(i) // yield next value
}
}
fun main() {

for (i in 1..3) {
Thread.sleep(100) // pretend we are computing it
yield(i) // yield next value
}
}
​
fun main() {

This code outputs the same numbers, but it waits 100ms before printing each one.

### Suspending functions

However, this computation blocks the main thread that is running the code. When these values are computed by asynchronous code we can mark the `simple` function with a `suspend` modifier, so that it can perform its work without blocking and return the result as a list:

import kotlinx.coroutines.*

//sampleStart

delay(1000) // pretend we are doing something asynchronous here
return listOf(1, 2, 3)
}

}
//sampleEnd

delay(1000) // pretend we are doing something asynchronous here
return listOf(1, 2, 3)
}
​

This code prints the numbers after waiting for a second.

### Flows

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
//sampleStart

for (i in 1..3) {
delay(100) // pretend we are doing something useful here
emit(i) // emit next value
}
}

// Launch a concurrent coroutine to check if the main thread is blocked
launch {
for (k in 1..3) {
println("I'm not blocked $k")
delay(100)
}
}
// Collect the flow

for (i in 1..3) {
delay(100) // pretend we are doing something useful here
emit(i) // emit next value
}
}
​

This code waits 100ms before printing each number without blocking the main thread. This is verified by printing "I'm not blocked" every 100ms from a separate coroutine that is running in the main thread:

I'm not blocked 1
1
I'm not blocked 2
2
I'm not blocked 3
3

Notice the following differences in the code with the Flow from the earlier examples:

- A builder function of Flow type is called flow.

- Code inside a `flow { ... }` builder block can suspend.

- The `simple` function is no longer marked with a `suspend` modifier.

- Values are emitted from the flow using an emit function.

>
> We can replace delay with `Thread.sleep` in the body of `simple`'s `flow { ... }` and see that the main thread is blocked in this case.

## Flows are cold

Flows are cold streams similar to sequences — the code inside a flow builder does not run until the flow is collected. This becomes clear in the following example:

println("Flow started")
for (i in 1..3) {
delay(100)
emit(i)
}
}

println("Calling simple function...")
val flow = simple()
println("Calling collect...")

println("Calling collect again...")

println("Flow started")
for (i in 1..3) {
delay(100)
emit(i)
}
}
​

Which prints:

Calling simple function...
Calling collect...
Flow started
1
2
3
Calling collect again...
Flow started
1
2
3

This is a key reason the `simple` function (which returns a flow) is not marked with `suspend` modifier. The `simple()` call itself returns quickly and does not wait for anything. The flow starts afresh every time it is collected and that is why we see "Flow started" every time we call `collect` again.

## Flow cancellation basics

Flows adhere to the general cooperative cancellation of coroutines. As usual, flow collection can be cancelled when the flow is suspended in a cancellable suspending function (like delay). The following example shows how the flow gets cancelled on a timeout when running in a withTimeoutOrNull block and stops executing its code:

for (i in 1..3) {
delay(100)
println("Emitting $i")
emit(i)
}
}

withTimeoutOrNull(250) { // Timeout after 250ms

}
println("Done")
}
//sampleEnd

for (i in 1..3) {
delay(100)
println("Emitting $i")
emit(i)
}
}
​

}
println("Done")
}

Notice how only two numbers get emitted by the flow in the `simple` function, producing the following output:

Emitting 1
1
Emitting 2
2
Done

See Flow cancellation checks section for more details.

## Flow builders

The `flow { ... }` builder from the previous examples is the most basic one. There are other builders that allow flows to be declared:

- The flowOf builder defines a flow that emits a fixed set of values.

- Various collections and sequences can be converted to flows using the `.asFlow()` extension function.

For example, the snippet that prints the numbers 1 to 3 from a flow can be rewritten as follows:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

//sampleStart
// Convert an integer range to a flow

//sampleEnd
}

xxxxxxxxxx
// Convert an integer range to a flow

## Intermediate flow operators

Flows can be transformed using operators, in the same way as you would transform collections and sequences. Intermediate operators are applied to an upstream flow and return a downstream flow. These operators are cold, just like flows are. A call to such an operator is not a suspending function itself. It works quickly, returning the definition of a new transformed flow.

The basic operators have familiar names like map and filter. An important difference of these operators from sequences is that blocks of code inside these operators can call suspending functions.

For example, a flow of incoming requests can be mapped to its results with a map operator, even when performing a request is a long-running operation that is implemented by a suspending function:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
//sampleStart
suspend fun performRequest(request: Int): String {
delay(1000) // imitate long-running asynchronous work
return "response $request"
}

(1..3).asFlow() // a flow of requests

suspend fun performRequest(request: Int): String {
delay(1000) // imitate long-running asynchronous work
return "response $request"
}
​

It produces the following three lines, each appearing one second after the previous:

response 1
response 2
response 3

### Transform operator

Among the flow transformation operators, the most general one is called transform. It can be used to imitate simple transformations like map and filter, as well as implement more complex transformations. Using the `transform` operator, we can emit arbitrary values an arbitrary number of times.

For example, using `transform` we can emit a string before performing a long-running asynchronous request and follow it with a response:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
suspend fun performRequest(request: Int): String {
delay(1000) // imitate long-running asynchronous work
return "response $request"
}

//sampleStart
(1..3).asFlow() // a flow of requests

emit(performRequest(request))
}

xxxxxxxxxx
(1..3).asFlow() // a flow of requests

emit("Making request $request")
emit(performRequest(request))
}

The output of this code is:

Making request 1
response 1
Making request 2
response 2
Making request 3
response 3

### Size-limiting operators

Size-limiting intermediate operators like take cancel the execution of the flow when the corresponding limit is reached. Cancellation in coroutines is always performed by throwing an exception, so that all the resource-management functions (like `try { ... } finally { ... }` blocks) operate normally in case of cancellation:

try {
emit(1)
emit(2)
println("This line will not execute")
emit(3)
} finally {
println("Finally in numbers")
}
}

numbers()
.take(2) // take only the first two

try {
emit(1)
emit(2)
println("This line will not execute")
emit(3)
} finally {
println("Finally in numbers")
}
}
​

The output of this code clearly shows that the execution of the `flow { ... }` body in the `numbers()` function stopped after emitting the second number:

1
2
Finally in numbers

## Terminal flow operators

Terminal operators on flows are suspending functions that start a collection of the flow. The collect operator is the most basic one, but there are other terminal operators, which can make it easier:

- Conversion to various collections like toList and toSet.

- Operators to get the first value and to ensure that a flow emits a single value.

- Reducing a flow to a value with reduce and fold.

For example:

//sampleStart
val sum = (1..5).asFlow()
.map { it * it } // squares of numbers from 1 to 5

println(sum)
//sampleEnd
}

xxxxxxxxxx
​
val sum = (1..5).asFlow()
.map { it * it } // squares of numbers from 1 to 5

println(sum)

Prints a single number:

55

## Flows are sequential

Each individual collection of a flow is performed sequentially unless special operators that operate on multiple flows are used. The collection works directly in the coroutine that calls a terminal operator. No new coroutines are launched by default. Each emitted value is processed by all the intermediate operators from upstream to downstream and is then delivered to the terminal operator after.

See the following example that filters the even integers and maps them to strings:

//sampleStart
(1..5).asFlow()
.filter {
println("Filter $it")
it % 2 == 0
}
.map {
println("Map $it")
"string $it"
}.collect {
println("Collect $it")
}
//sampleEnd
}

xxxxxxxxxx
​
(1..5).asFlow()
.filter {
println("Filter $it")
it % 2 == 0
}
.map {
println("Map $it")
"string $it"
}.collect {
println("Collect $it")
}

Producing:

Filter 1
Filter 2
Map 2
Collect string 2
Filter 3
Filter 4
Map 4
Collect string 4
Filter 5

## Flow context

Collection of a flow always happens in the context of the calling coroutine. For example, if there is a `simple` flow, then the following code runs in the context specified by the author of this code, regardless of the implementation details of the `simple` flow:

withContext(context) {

}
}

This property of a flow is called context preservation.

So, by default, code in the `flow { ... }` builder runs in the context that is provided by a collector of the corresponding flow. For example, consider the implementation of a `simple` function that prints the thread it is called on and emits three numbers:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

log("Started simple flow")
for (i in 1..3) {
emit(i)
}
}

log("Started simple flow")
for (i in 1..3) {
emit(i)
}
}
​

Running this code produces:

[main @coroutine#1] Started simple flow
[main @coroutine#1] Collected 1
[main @coroutine#1] Collected 2
[main @coroutine#1] Collected 3

Since `simple().collect` is called from the main thread, the body of `simple`'s flow is also called in the main thread. This is the perfect default for fast-running or asynchronous code that does not care about the execution context and does not block the caller.

### A common pitfall when using withContext

However, the long-running CPU-consuming code might need to be executed in the context of Dispatchers.Default and UI-updating code might need to be executed in the context of Dispatchers.Main. Usually, withContext is used to change the context in the code using Kotlin coroutines, but code in the `flow { ... }` builder has to honor the context preservation property and is not allowed to emit from a different context.

Try running the following code:

// The WRONG way to change context for CPU-consuming code in flow builder
kotlinx.coroutines.withContext(Dispatchers.Default) {
for (i in 1..3) {
Thread.sleep(100) // pretend we are computing it in CPU-consuming way
emit(i) // emit next value
}
}
}

// The WRONG way to change context for CPU-consuming code in flow builder
kotlinx.coroutines.withContext(Dispatchers.Default) {
for (i in 1..3) {
Thread.sleep(100) // pretend we are computing it in CPU-consuming way
emit(i) // emit next value
}
}
}
​

This code produces the following exception:

Exception in thread "main" java.lang.IllegalStateException: Flow invariant is violated:
Flow was collected in [CoroutineId(1), "coroutine#1":BlockingCoroutine{Active}@5511c7f8, BlockingEventLoop@2eac3323],
but emission happened in [CoroutineId(1), "coroutine#1":DispatchedCoroutine{Active}@2dae0000, Dispatchers.Default].
Please refer to 'flow' documentation or use 'flowOn' instead
at ...

### flowOn operator

The exception refers to the flowOn function that shall be used to change the context of the flow emission. The correct way to change the context of a flow is shown in the example below, which also prints the names of the corresponding threads to show how it all works:

for (i in 1..3) {
Thread.sleep(100) // pretend we are computing it in CPU-consuming way
log("Emitting $i")
emit(i) // emit next value
}
}.flowOn(Dispatchers.Default) // RIGHT way to change context for CPU-consuming code in flow builder

}
}
//sampleEnd

for (i in 1..3) {
Thread.sleep(100) // pretend we are computing it in CPU-consuming way
log("Emitting $i")
emit(i) // emit next value
}
}.flowOn(Dispatchers.Default) // RIGHT way to change context for CPU-consuming code in flow builder
​

log("Collected $value")
}
}

Notice how `flow { ... }` works in the background thread, while collection happens in the main thread:

[DefaultDispatcher-worker-1 @coroutine#2] Emitting 1
[main @coroutine#1] Collected 1
[DefaultDispatcher-worker-1 @coroutine#2] Emitting 2
[main @coroutine#1] Collected 2
[DefaultDispatcher-worker-1 @coroutine#2] Emitting 3
[main @coroutine#1] Collected 3

Another thing to observe here is that the flowOn operator has changed the default sequential nature of the flow. Now collection happens in one coroutine ("coroutine#1") and emission happens in another coroutine ("coroutine#2") that is running in another thread concurrently with the collecting coroutine. The flowOn operator creates another coroutine for an upstream flow when it has to change the CoroutineDispatcher in its context.

## Buffering

Running different parts of a flow in different coroutines can be helpful from the standpoint of the overall time it takes to collect the flow, especially when long-running asynchronous operations are involved. For example, consider a case when the emission by a `simple` flow is slow, taking 100 ms to produce an element; and collector is also slow, taking 300 ms to process an element. Let's see how long it takes to collect such a flow with three numbers:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.system.*
//sampleStart

for (i in 1..3) {
delay(100) // pretend we are asynchronously waiting 100 ms
emit(i) // emit next value
}
}

val time = measureTimeMillis {

println(value)
}
}
println("Collected in $time ms")
}
//sampleEnd

for (i in 1..3) {
delay(100) // pretend we are asynchronously waiting 100 ms
emit(i) // emit next value
}
}
​

delay(300) // pretend we are processing it for 300 ms
println(value)
}
}
println("Collected in $time ms")
}

It produces something like this, with the whole collection taking around 1200 ms (three numbers, 400 ms for each):

1
2
3
Collected in 1220 ms

We can use a buffer operator on a flow to run emitting code of the `simple` flow concurrently with collecting code, as opposed to running them sequentially:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.system.*

//sampleStart
val time = measureTimeMillis {
simple()
.buffer() // buffer emissions, don't wait

println(value)
}
}
println("Collected in $time ms")
//sampleEnd
}

xxxxxxxxxx
val time = measureTimeMillis {
simple()
.buffer() // buffer emissions, don't wait

delay(300) // pretend we are processing it for 300 ms
println(value)
}
}
println("Collected in $time ms")

It produces the same numbers just faster, as we have effectively created a processing pipeline, having to only wait 100 ms for the first number and then spending only 300 ms to process each number. This way it takes around 1000 ms to run:

1
2
3
Collected in 1071 ms

>
> Note that the flowOn operator uses the same buffering mechanism when it has to change a CoroutineDispatcher, but here we explicitly request buffering without changing the execution context.

### Conflation

When a flow represents partial results of the operation or operation status updates, it may not be necessary to process each value, but instead, only most recent ones. In this case, the conflate operator can be used to skip intermediate values when a collector is too slow to process them. Building on the previous example:

//sampleStart
val time = measureTimeMillis {
simple()
.conflate() // conflate emissions, don't process each one

xxxxxxxxxx
val time = measureTimeMillis {
simple()
.conflate() // conflate emissions, don't process each one

We see that while the first number was still being processed the second, and third were already produced, so the second one was conflated and only the most recent (the third one) was delivered to the collector:

1
3
Collected in 758 ms

### Processing the latest value

Conflation is one way to speed up processing when both the emitter and collector are slow. It does it by dropping emitted values. The other way is to cancel a slow collector and restart it every time a new value is emitted. There is a family of `xxxLatest` operators that perform the same essential logic of a `xxx` operator, but cancel the code in their block on a new value. Let's try changing conflate to collectLatest in the previous example:

//sampleStart
val time = measureTimeMillis {
simple()

println("Collecting $value")
delay(300) // pretend we are processing it for 300 ms
println("Done $value")
}
}
println("Collected in $time ms")
//sampleEnd
}

xxxxxxxxxx
val time = measureTimeMillis {
simple()

println("Collecting $value")
delay(300) // pretend we are processing it for 300 ms
println("Done $value")
}
}
println("Collected in $time ms")

Since the body of collectLatest takes 300 ms, but new values are emitted every 100 ms, we see that the block is run on every value, but completes only for the last value:

Collecting 1
Collecting 2
Collecting 3
Done 3
Collected in 741 ms

## Composing multiple flows

There are lots of ways to compose multiple flows.

### Zip

Just like the Sequence.zip extension function in the Kotlin standard library, flows have a zip operator that combines the corresponding values of two flows:

//sampleStart
val nums = (1..3).asFlow() // numbers 1..3
val strs = flowOf("one", "two", "three") // strings

.collect { println(it) } // collect and print
//sampleEnd
}

xxxxxxxxxx
​
val nums = (1..3).asFlow() // numbers 1..3
val strs = flowOf("one", "two", "three") // strings

.collect { println(it) } // collect and print

This example prints:

### Combine

When flow represents the most recent value of a variable or operation (see also the related section on conflation), it might be needed to perform a computation that depends on the most recent values of the corresponding flows and to recompute it whenever any of the upstream flows emit a value. The corresponding family of operators is called combine.

>
> We use a onEach intermediate operator in this example to delay each element and make the code that emits sample flows more declarative and shorter.

//sampleStart
val nums = (1..3).asFlow().onEach { delay(300) } // numbers 1..3 every 300 ms
val strs = flowOf("one", "two", "three").onEach { delay(400) } // strings every 400 ms
val startTime = System.currentTimeMillis() // remember the start time

println("$value at ${System.currentTimeMillis() - startTime} ms from start")
}
//sampleEnd
}

xxxxxxxxxx
​
val nums = (1..3).asFlow().onEach { delay(300) } // numbers 1..3 every 300 ms
val strs = flowOf("one", "two", "three").onEach { delay(400) } // strings every 400 ms
val startTime = System.currentTimeMillis() // remember the start time

println("$value at ${System.currentTimeMillis() - startTime} ms from start")
}

However, when using a combine operator here instead of a zip:

We get quite a different output, where a line is printed at each emission from either `nums` or `strs` flows:

## Flattening flows

Flows represent asynchronously received sequences of values, and so it is quite easy to get into a situation where each value triggers a request for another sequence of values. For example, we can have the following function that returns a flow of two strings 500 ms apart:

emit("$i: First")
delay(500) // wait 500 ms
emit("$i: Second")
}

Now if we have a flow of three integers and call `requestFlow` on each of them like this:

(1..3).asFlow().map { requestFlow(it) }

Then we will end up with a flow of flows ( `Flow<Flow<String>>`) that needs to be flattened into a single flow for further processing. Collections and sequences have flatten and flatMap operators for this. However, due to the asynchronous nature of flows they call for different modes of flattening, and hence, a family of flattening operators on flows exists.

### flatMapConcat

Concatenation of flows of flows is provided by the flatMapConcat and flattenConcat operators. They are the most direct analogues of the corresponding sequence operators. They wait for the inner flow to complete before starting to collect the next one as the following example shows:

//sampleStart
val startTime = System.currentTimeMillis() // remember the start time
(1..3).asFlow().onEach { delay(100) } // emit a number every 100 ms
.flatMapConcat { requestFlow(it) }

xxxxxxxxxx
val startTime = System.currentTimeMillis() // remember the start time
(1..3).asFlow().onEach { delay(100) } // emit a number every 100 ms
.flatMapConcat { requestFlow(it) }

The sequential nature of flatMapConcat is clearly seen in the output:

1: First at 121 ms from start
1: Second at 622 ms from start
2: First at 727 ms from start
2: Second at 1227 ms from start
3: First at 1328 ms from start
3: Second at 1829 ms from start

### flatMapMerge

Another flattening operation is to concurrently collect all the incoming flows and merge their values into a single flow so that values are emitted as soon as possible. It is implemented by flatMapMerge and flattenMerge operators. They both accept an optional `concurrency` parameter that limits the number of concurrent flows that are collected at the same time (it is equal to DEFAULT\_CONCURRENCY by default).

//sampleStart
val startTime = System.currentTimeMillis() // remember the start time
(1..3).asFlow().onEach { delay(100) } // a number every 100 ms
.flatMapMerge { requestFlow(it) }

xxxxxxxxxx
val startTime = System.currentTimeMillis() // remember the start time
(1..3).asFlow().onEach { delay(100) } // a number every 100 ms
.flatMapMerge { requestFlow(it) }

The concurrent nature of flatMapMerge is obvious:

1: First at 136 ms from start
2: First at 231 ms from start
3: First at 333 ms from start
1: Second at 639 ms from start
2: Second at 732 ms from start
3: Second at 833 ms from start

>
> Note that the flatMapMerge calls its block of code ( `{ requestFlow(it) }` in this example) sequentially, but collects the resulting flows concurrently, it is the equivalent of performing a sequential `map { requestFlow(it) }` first and then calling flattenMerge on the result.

### flatMapLatest

In a similar way to the collectLatest operator, that was described in the section "Processing the latest value", there is the corresponding "Latest" flattening mode where the collection of the previous flow is cancelled as soon as new flow is emitted. It is implemented by the flatMapLatest operator.

//sampleStart
val startTime = System.currentTimeMillis() // remember the start time
(1..3).asFlow().onEach { delay(100) } // a number every 100 ms
.flatMapLatest { requestFlow(it) }

xxxxxxxxxx
val startTime = System.currentTimeMillis() // remember the start time
(1..3).asFlow().onEach { delay(100) } // a number every 100 ms
.flatMapLatest { requestFlow(it) }

The output here in this example is a good demonstration of how flatMapLatest works:

1: First at 142 ms from start
2: First at 322 ms from start
3: First at 425 ms from start
3: Second at 931 ms from start

>
> Note that flatMapLatest cancels all the code in its block ( `{ requestFlow(it) }` in this example) when a new value is received. It makes no difference in this particular example, because the call to `requestFlow` itself is fast, not-suspending, and cannot be cancelled. However, a differnce in output would be visible if we were to use suspending functions like `delay` in `requestFlow`.

## Flow exceptions

Flow collection can complete with an exception when an emitter or code inside the operators throw an exception. There are several ways to handle these exceptions.

### Collector try and catch

A collector can use Kotlin's `try/catch` block to handle exceptions:

for (i in 1..3) {
println("Emitting $i")
emit(i) // emit next value
}
}

try {

check(value <= 1) { "Collected $value" }
}
} catch (e: Throwable) {
println("Caught $e")
}
}
//sampleEnd

for (i in 1..3) {
println("Emitting $i")
emit(i) // emit next value
}
}
​

println(value)
check(value <= 1) { "Collected $value" }
}
} catch (e: Throwable) {
println("Caught $e")
}
}

This code successfully catches an exception in collect terminal operator and, as we see, no more values are emitted after that:

Emitting 1
1
Emitting 2
2
Caught java.lang.IllegalStateException: Collected 2

### Everything is caught

The previous example actually catches any exception happening in the emitter or in any intermediate or terminal operators. For example, let's change the code so that emitted values are mapped to strings, but the corresponding code produces an exception:

flow {
for (i in 1..3) {
println("Emitting $i")
emit(i) // emit next value
}
}

"string $value"
}

} catch (e: Throwable) {
println("Caught $e")
}
}
//sampleEnd

check(value <= 1) { "Crashed on $value" }
"string $value"
}
​

} catch (e: Throwable) {
println("Caught $e")
}
}

This exception is still caught and collection is stopped:

Emitting 1
string 1
Emitting 2
Caught java.lang.IllegalStateException: Crashed on 2

## Exception transparency

But how can code of the emitter encapsulate its exception handling behavior?

Flows must be transparent to exceptions and it is a violation of the exception transparency to emit values in the `flow { ... }` builder from inside of a `try/catch` block. This guarantees that a collector throwing an exception can always catch it using `try/catch` as in the previous example.

The emitter can use a catch operator that preserves this exception transparency and allows encapsulation of its exception handling. The body of the `catch` operator can analyze an exception and react to it in different ways depending on which exception was caught:

- Exceptions can be rethrown using `throw`.

- Exceptions can be turned into emission of values using emit from the body of catch.

- Exceptions can be ignored, logged, or processed by some other code.

For example, let us emit the text on catching an exception:

//sampleStart
simple()

xxxxxxxxxx
simple()

The output of the example is the same, even though we do not have `try/catch` around the code anymore.

### Transparent catch

The catch intermediate operator, honoring exception transparency, catches only upstream exceptions (that is an exception from all the operators above `catch`, but not below it). If the block in `collect { ... }` (placed below `catch`) throws an exception then it escapes:

for (i in 1..3) {
println("Emitting $i")
emit(i)
}
}

simple()

println(value)
}
}
//sampleEnd

for (i in 1..3) {
println("Emitting $i")
emit(i)
}
}
​

check(value <= 1) { "Collected $value" }
println(value)
}
}

A "Caught ..." message is not printed despite there being a `catch` operator:

Emitting 1
1
Emitting 2
Exception in thread "main" java.lang.IllegalStateException: Collected 2
at ...

### Catching declaratively

We can combine the declarative nature of the catch operator with a desire to handle all the exceptions, by moving the body of the collect operator into onEach and putting it before the `catch` operator. Collection of this flow must be triggered by a call to `collect()` without parameters:

println(value)
}

.collect()
//sampleEnd
}

check(value <= 1) { "Collected $value" }
println(value)
}

.collect()

Now we can see that a "Caught ..." message is printed and so we can catch all the exceptions without explicitly using a `try/catch` block:

Emitting 1
1
Emitting 2
Caught java.lang.IllegalStateException: Collected 2

## Flow completion

When flow collection completes (normally or exceptionally) it may need to execute an action. As you may have already noticed, it can be done in two ways: imperative or declarative.

### Imperative finally block

In addition to `try`/ `catch`, a collector can also use a `finally` block to execute an action upon `collect` completion.

} finally {
println("Done")
}
}
//sampleEnd

​

} finally {
println("Done")
}
}

This code prints three numbers produced by the `simple` flow followed by a "Done" string:

1
2
3
Done

### Declarative handling

For the declarative approach, flow has onCompletion intermediate operator that is invoked when the flow has completely collected.

The previous example can be rewritten using an onCompletion operator and produces the same output:

//sampleStart
simple()
.onCompletion { println("Done") }

xxxxxxxxxx
simple()
.onCompletion { println("Done") }

The key advantage of onCompletion is a nullable `Throwable` parameter of the lambda that can be used to determine whether the flow collection was completed normally or exceptionally. In the following example the `simple` flow throws an exception after emitting the number 1:

emit(1)
throw RuntimeException()
}

emit(1)
throw RuntimeException()
}
​

As you may expect, it prints:

1
Flow completed exceptionally
Caught exception

The onCompletion operator, unlike catch, does not handle the exception. As we can see from the above example code, the exception still flows downstream. It will be delivered to further `onCompletion` operators and can be handled with a `catch` operator.

### Successful completion

Another difference with catch operator is that onCompletion sees all exceptions and receives a `null` exception only on successful completion of the upstream flow (without cancellation or failure).

We can see the completion cause is not null, because the flow was aborted due to downstream exception:

1
Flow completed with java.lang.IllegalStateException: Collected 2
Exception in thread "main" java.lang.IllegalStateException: Collected 2

## Imperative versus declarative

Now we know how to collect flow, and handle its completion and exceptions in both imperative and declarative ways. The natural question here is, which approach is preferred and why? As a library, we do not advocate for any particular approach and believe that both options are valid and should be selected according to your own preferences and code style.

## Launching flow

It is easy to use flows to represent asynchronous events that are coming from some source. In this case, we need an analogue of the `addEventListener` function that registers a piece of code with a reaction for incoming events and continues further work. The onEach operator can serve this role. However, `onEach` is an intermediate operator. We also need a terminal operator to collect the flow. Otherwise, just calling `onEach` has no effect.

If we use the collect terminal operator after `onEach`, then the code after it will wait until the flow is collected:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
//sampleStart
// Imitate a flow of events

events()

.collect() // <--- Collecting the flow waits
println("Done")
}
//sampleEnd

xxxxxxxxxx
// Imitate a flow of events

.collect() // <--- Collecting the flow waits
println("Done")
}

As you can see, it prints:

Event: 1
Event: 2
Event: 3
Done

The launchIn terminal operator comes in handy here. By replacing `collect` with `launchIn` we can launch a collection of the flow in a separate coroutine, so that execution of further code immediately continues:

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
// Imitate a flow of events

.launchIn(this) // <--- Launching the flow in a separate coroutine
println("Done")
}
//sampleEnd

.launchIn(this) // <--- Launching the flow in a separate coroutine
println("Done")
}

It prints:

Done
Event: 1
Event: 2
Event: 3

The required parameter to `launchIn` must specify a CoroutineScope in which the coroutine to collect the flow is launched. In the above example this scope comes from the runBlocking coroutine builder, so while the flow is running, this runBlocking scope waits for completion of its child coroutine and keeps the main function from returning and terminating this example.

In actual applications a scope will come from an entity with a limited lifetime. As soon as the lifetime of this entity is terminated the corresponding scope is cancelled, cancelling the collection of the corresponding flow. This way the pair of `onEach { ... }.launchIn(scope)` works like the `addEventListener`. However, there is no need for the corresponding `removeEventListener` function, as cancellation and structured concurrency serve this purpose.

Note that launchIn also returns a Job, which can be used to cancel the corresponding flow collection coroutine only without cancelling the whole scope or to join it.

### Flow cancellation checks

For convenience, the flow builder performs additional ensureActive checks for cancellation on each emitted value. It means that a busy loop emitting from a `flow { ... }` is cancellable:

for (i in 1..5) {
println("Emitting $i")
emit(i)
}
}

for (i in 1..5) {
println("Emitting $i")
emit(i)
}
}
​

if (value == 3) cancel()
println(value)
}
}

We get only numbers up to 3 and a CancellationException after trying to emit number 4:

Emitting 1
1
Emitting 2
2
Emitting 3
3
Emitting 4
Exception in thread "main" kotlinx.coroutines.JobCancellationException: BlockingCoroutine was cancelled; job="coroutine#1":BlockingCoroutine{Cancelled}@6d7b4f4c

However, most other flow operators do not do additional cancellation checks on their own for performance reasons. For example, if you use IntRange.asFlow extension to write the same busy loop and don't suspend anywhere, then there are no checks for cancellation:

All numbers from 1 to 5 are collected and cancellation gets detected only before return from `runBlocking`:

1
2
3
4
5
Exception in thread "main" kotlinx.coroutines.JobCancellationException: BlockingCoroutine was cancelled; job="coroutine#1":BlockingCoroutine{Cancelled}@3327bd23

#### Making busy flow cancellable

In the case where you have a busy loop with coroutines you must explicitly check for cancellation. You can add `.onEach { currentCoroutineContext().ensureActive() }`, but there is a ready-to-use cancellable operator provided to do that:

With the `cancellable` operator only the numbers from 1 to 3 are collected:

1
2
3
Exception in thread "main" kotlinx.coroutines.JobCancellationException: BlockingCoroutine was cancelled; job="coroutine#1":BlockingCoroutine{Cancelled}@5ec0a365

## Flow and Reactive Streams

For those who are familiar with Reactive Streams or reactive frameworks such as RxJava and project Reactor, design of the Flow may look very familiar.

Indeed, its design was inspired by Reactive Streams and its various implementations. But Flow main goal is to have as simple design as possible, be Kotlin and suspension friendly and respect structured concurrency. Achieving this goal would be impossible without reactive pioneers and their tremendous work. You can read the complete story in Reactive Streams and Kotlin Flows article.

While being different, conceptually, Flow is a reactive stream and it is possible to convert it to the reactive (spec and TCK compliant) Publisher and vice versa. Such converters are provided by `kotlinx.coroutines` out-of-the-box and can be found in corresponding reactive modules ( `kotlinx-coroutines-reactive` for Reactive Streams, `kotlinx-coroutines-reactor` for Project Reactor and `kotlinx-coroutines-rx2`/ `kotlinx-coroutines-rx3` for RxJava2/RxJava3). Integration modules include conversions from and to `Flow`, integration with Reactor's `Context` and suspension-friendly ways to work with various reactive entities.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/channels.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Channels

# Channels

Edit page 16 February 2022

Deferred values provide a convenient way to transfer a single value between coroutines. Channels provide a way to transfer a stream of values.

## Channel basics

A Channel is conceptually very similar to `BlockingQueue`. One key difference is that instead of a blocking `put` operation it has a suspending send, and instead of a blocking `take` operation it has a suspending receive.

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
fun main() = runBlocking {
//sampleStart

launch {
// this might be heavy CPU-consuming computation or async logic,
// we'll just send five squares
for (x in 1..5) channel.send(x * x)
}
// here we print five received integers:
repeat(5) { println(channel.receive()) }
println("Done!")
//sampleEnd
}

xxxxxxxxxx

launch {
// this might be heavy CPU-consuming computation or async logic,
// we'll just send five squares
for (x in 1..5) channel.send(x * x)
}
// here we print five received integers:
repeat(5) { println(channel.receive()) }
println("Done!")

Open in Playground →

>
> You can get the full code here.

The output of this code is:

1
4
9
16
25
Done!

## Closing and iteration over channels

Unlike a queue, a channel can be closed to indicate that no more elements are coming. On the receiver side it is convenient to use a regular `for` loop to receive elements from the channel.

Conceptually, a close is like sending a special close token to the channel. The iteration stops as soon as this close token is received, so there is a guarantee that all previously sent elements before the close are received:

launch {
for (x in 1..5) channel.send(x * x)
channel.close() // we're done sending
}
// here we print received values using `for` loop (until the channel is closed)
for (y in channel) println(y)
println("Done!")
//sampleEnd
}

launch {
for (x in 1..5) channel.send(x * x)
channel.close() // we're done sending
}
// here we print received values using `for` loop (until the channel is closed)
for (y in channel) println(y)
println("Done!")

## Building channel producers

The pattern where a coroutine is producing a sequence of elements is quite common. This is a part of producer-consumer pattern that is often found in concurrent code. You could abstract such a producer into a function that takes channel as its parameter, but this goes contrary to common sense that results must be returned from functions.

There is a convenient coroutine builder named produce that makes it easy to do it right on producer side, and an extension function consumeEach, that replaces a `for` loop on the consumer side:

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
//sampleStart

for (x in 1..5) send(x * x)
}
fun main() = runBlocking {
val squares = produceSquares()
squares.consumeEach { println(it) }
println("Done!")
//sampleEnd
}

for (x in 1..5) send(x * x)
}
​
fun main() = runBlocking {
val squares = produceSquares()
squares.consumeEach { println(it) }
println("Done!")

## Pipelines

A pipeline is a pattern where one coroutine is producing, possibly infinite, stream of values:

var x = 1
while (true) send(x++) // infinite stream of integers starting from 1
}

And another coroutine or coroutines are consuming that stream, doing some processing, and producing some other results. In the example below, the numbers are just squared:

for (x in numbers) send(x * x)
}

The main code starts and connects the whole pipeline:

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
fun main() = runBlocking {
//sampleStart
val numbers = produceNumbers() // produces integers from 1 and on
val squares = square(numbers) // squares integers
repeat(5) {
println(squares.receive()) // print first five
}
println("Done!") // we are done
coroutineContext.cancelChildren() // cancel children coroutines
//sampleEnd
}

xxxxxxxxxx
val numbers = produceNumbers() // produces integers from 1 and on
val squares = square(numbers) // squares integers
repeat(5) {
println(squares.receive()) // print first five
}
println("Done!") // we are done
coroutineContext.cancelChildren() // cancel children coroutines

> ### note
>
> All functions that create coroutines are defined as extensions on CoroutineScope, so that we can rely on structured concurrency to make sure that we don't have lingering global coroutines in our application.

## Prime numbers with pipeline

Let's take pipelines to the extreme with an example that generates prime numbers using a pipeline of coroutines. We start with an infinite sequence of numbers.

var x = start
while (true) send(x++) // infinite stream of integers from start
}

The following pipeline stage filters an incoming stream of numbers, removing all the numbers that are divisible by the given prime number:

for (x in numbers) if (x % prime != 0) send(x)
}

Now we build our pipeline by starting a stream of numbers from 2, taking a prime number from the current channel, and launching new pipeline stage for each prime number found:

The following example prints the first ten prime numbers, running the whole pipeline in the context of the main thread. Since all the coroutines are launched in the scope of the main runBlocking coroutine we don't have to keep an explicit list of all the coroutines we have started. We use cancelChildren extension function to cancel all the children coroutines after we have printed the first ten prime numbers.

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
fun main() = runBlocking {
//sampleStart
var cur = numbersFrom(2)
repeat(10) {
val prime = cur.receive()
println(prime)
cur = filter(cur, prime)
}
coroutineContext.cancelChildren() // cancel all children to let main finish
//sampleEnd
}

xxxxxxxxxx
var cur = numbersFrom(2)
repeat(10) {
val prime = cur.receive()
println(prime)
cur = filter(cur, prime)
}
coroutineContext.cancelChildren() // cancel all children to let main finish

2
3
5
7
11
13
17
19
23
29

Note that you can build the same pipeline using `iterator` coroutine builder from the standard library. Replace `produce` with `iterator`, `send` with `yield`, `receive` with `next`, `ReceiveChannel` with `Iterator`, and get rid of the coroutine scope. You will not need `runBlocking` either. However, the benefit of a pipeline that uses channels as shown above is that it can actually use multiple CPU cores if you run it in Dispatchers.Default context.

Anyway, this is an extremely impractical way to find prime numbers. In practice, pipelines do involve some other suspending invocations (like asynchronous calls to remote services) and these pipelines cannot be built using `sequence`/ `iterator`, because they do not allow arbitrary suspension, unlike `produce`, which is fully asynchronous.

## Fan-out

Multiple coroutines may receive from the same channel, distributing work between themselves. Let us start with a producer coroutine that is periodically producing integers (ten numbers per second):

var x = 1 // start from 1
while (true) {
send(x++) // produce next
delay(100) // wait 0.1s
}
}

Then we can have several processor coroutines. In this example, they just print their id and received number:

for (msg in channel) {
println("Processor #$id received $msg")
}
}

Now let us launch five processors and let them work for almost a second. See what happens:

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

//sampleStart
val producer = produceNumbers()
repeat(5) { launchProcessor(it, producer) }
delay(950)
producer.cancel() // cancel producer coroutine and thus kill them all
//sampleEnd
}

xxxxxxxxxx
val producer = produceNumbers()
repeat(5) { launchProcessor(it, producer) }
delay(950)
producer.cancel() // cancel producer coroutine and thus kill them all

The output will be similar to the following one, albeit the processor ids that receive each specific integer may be different:

Processor #2 received 1
Processor #4 received 2
Processor #0 received 3
Processor #1 received 4
Processor #3 received 5
Processor #2 received 6
Processor #4 received 7
Processor #0 received 8
Processor #1 received 9
Processor #3 received 10

Note that cancelling a producer coroutine closes its channel, thus eventually terminating iteration over the channel that processor coroutines are doing.

Also, pay attention to how we explicitly iterate over channel with `for` loop to perform fan-out in `launchProcessor` code. Unlike `consumeEach`, this `for` loop pattern is perfectly safe to use from multiple coroutines. If one of the processor coroutines fails, then others would still be processing the channel, while a processor that is written via `consumeEach` always consumes (cancels) the underlying channel on its normal or abnormal completion.

## Fan-in

Multiple coroutines may send to the same channel. For example, let us have a channel of strings, and a suspending function that repeatedly sends a specified string to this channel with a specified delay:

while (true) {
delay(time)
channel.send(s)
}
}

Now, let us see what happens if we launch a couple of coroutines sending strings (in this example we launch them in the context of the main thread as main coroutine's children):

launch { sendString(channel, "foo", 200L) }
launch { sendString(channel, "BAR!", 500L) }
repeat(6) { // receive first six
println(channel.receive())
}
coroutineContext.cancelChildren() // cancel all children to let main finish
//sampleEnd
}

launch { sendString(channel, "foo", 200L) }
launch { sendString(channel, "BAR!", 500L) }
repeat(6) { // receive first six
println(channel.receive())
}
coroutineContext.cancelChildren() // cancel all children to let main finish

The output is:

foo
foo
BAR!
foo
foo
BAR!

## Buffered channels

The channels shown so far had no buffer. Unbuffered channels transfer elements when sender and receiver meet each other (aka rendezvous). If send is invoked first, then it is suspended until receive is invoked, if receive is invoked first, it is suspended until send is invoked.

Both Channel() factory function and produce builder take an optional `capacity` parameter to specify buffer size. Buffer allows senders to send multiple elements before suspending, similar to the `BlockingQueue` with a specified capacity, which blocks when buffer is full.

Take a look at the behavior of the following code:

//sampleStart

val sender = launch { // launch sender coroutine
repeat(10) {
println("Sending $it") // print before sending each element
channel.send(it) // will suspend when buffer is full
}
}
// don't receive anything... just wait....
delay(1000)
sender.cancel() // cancel sender coroutine
//sampleEnd
}

val sender = launch { // launch sender coroutine
repeat(10) {
println("Sending $it") // print before sending each element
channel.send(it) // will suspend when buffer is full
}
}
// don't receive anything... just wait....
delay(1000)
sender.cancel() // cancel sender coroutine

It prints "sending" five times using a buffered channel with capacity of four:

Sending 0
Sending 1
Sending 2
Sending 3
Sending 4

The first four elements are added to the buffer and the sender suspends when trying to send the fifth one.

## Channels are fair

Send and receive operations to channels are fair with respect to the order of their invocation from multiple coroutines. They are served in first-in first-out order, e.g. the first coroutine to invoke `receive` gets the element. In the following example two coroutines "ping" and "pong" are receiving the "ball" object from the shared "table" channel.

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
//sampleStart
data class Ball(var hits: Int)
fun main() = runBlocking {

launch { player("ping", table) }
launch { player("pong", table) }
table.send(Ball(0)) // serve the ball
delay(1000) // delay 1 second
coroutineContext.cancelChildren() // game over, cancel them
}

for (ball in table) { // receive the ball in a loop
ball.hits++
println("$name $ball")
delay(300) // wait a bit
table.send(ball) // send the ball back
}
}
//sampleEnd

xxxxxxxxxx
data class Ball(var hits: Int)
​
fun main() = runBlocking {

launch { player("ping", table) }
launch { player("pong", table) }
table.send(Ball(0)) // serve the ball
delay(1000) // delay 1 second
coroutineContext.cancelChildren() // game over, cancel them
}
​

for (ball in table) { // receive the ball in a loop
ball.hits++
println("$name $ball")
delay(300) // wait a bit
table.send(ball) // send the ball back
}
}

The "ping" coroutine is started first, so it is the first one to receive the ball. Even though "ping" coroutine immediately starts receiving the ball again after sending it for details.

## Ticker channels

Ticker channel is a special rendezvous channel that produces `Unit` every time given delay passes since last consumption from this channel. Though it may seem to be useless standalone, it is a useful building block to create complex time-based produce pipelines and operators that do windowing and other time-dependent processing. Ticker channel can be used in select to perform "on tick" action.

To create such channel use a factory method ticker. To indicate that no further elements are needed use ReceiveChannel.cancel method on it.

Now let's see how it works in practice:

val tickerChannel = ticker(delayMillis = 200, initialDelayMillis = 0) // create a ticker channel
var nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
println("Initial element is available immediately: $nextElement") // no initial delay
nextElement = withTimeoutOrNull(100) { tickerChannel.receive() } // all subsequent elements have 200ms delay
println("Next element is not ready in 100 ms: $nextElement")
nextElement = withTimeoutOrNull(120) { tickerChannel.receive() }
println("Next element is ready in 200 ms: $nextElement")
// Emulate large consumption delays
println("Consumer pauses for 300ms")
delay(300)
// Next element is available immediately
nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
println("Next element is available immediately after large consumer delay: $nextElement")
// Note that the pause between `receive` calls is taken into account and next element arrives faster
nextElement = withTimeoutOrNull(120) { tickerChannel.receive() }
println("Next element is ready in 100ms after consumer pause in 300ms: $nextElement")
tickerChannel.cancel() // indicate that no more elements are needed
}
//sampleEnd

val tickerChannel = ticker(delayMillis = 200, initialDelayMillis = 0) // create a ticker channel
var nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
println("Initial element is available immediately: $nextElement") // no initial delay
​
nextElement = withTimeoutOrNull(100) { tickerChannel.receive() } // all subsequent elements have 200ms delay
println("Next element is not ready in 100 ms: $nextElement")
​
nextElement = withTimeoutOrNull(120) { tickerChannel.receive() }
println("Next element is ready in 200 ms: $nextElement")
​
// Emulate large consumption delays
println("Consumer pauses for 300ms")
delay(300)
// Next element is available immediately
nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
println("Next element is available immediately after large consumer delay: $nextElement")
// Note that the pause between `receive` calls is taken into account and next element arrives faster
nextElement = withTimeoutOrNull(120) { tickerChannel.receive() }
println("Next element is ready in 100ms after consumer pause in 300ms: $nextElement")
​
tickerChannel.cancel() // indicate that no more elements are needed
}

It prints following lines:

Initial element is available immediately: kotlin.Unit
Next element is not ready in 100 ms: null
Next element is ready in 200 ms: kotlin.Unit
Consumer pauses for 300ms
Next element is available immediately after large consumer delay: kotlin.Unit
Next element is ready in 100ms after consumer pause in 300ms: kotlin.Unit

Note that ticker is aware of possible consumer pauses and, by default, adjusts next produced element delay if a pause occurs, trying to maintain a fixed rate of produced elements.

Optionally, a `mode` parameter equal to TickerMode.FIXED\_DELAY can be specified to maintain a fixed delay between elements.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/exception-handling.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Coroutine exceptions handling

# Coroutine exceptions handling

Edit page 16 February 2022

This section covers exception handling and cancellation on exceptions. We already know that a cancelled coroutine throws CancellationException in suspension points and that it is ignored by the coroutines' machinery. Here we look at what happens if an exception is thrown during cancellation or multiple children of the same coroutine throw an exception.

## Exception propagation

Coroutine builders come in two flavors: propagating exceptions automatically ( launch) or exposing them to users ( async and produce). When these builders are used to create a root coroutine, that is not a child of another coroutine, the former builders treat exceptions as uncaught exceptions, similar to Java's `Thread.uncaughtExceptionHandler`, while the latter are relying on the user to consume the final exception, for example via await or receive ( produce and receive are covered in Channels section).

>
> GlobalScope is a delicate API that can backfire in non-trivial ways. Creating a root coroutine for the whole application is one of the rare legitimate uses for `GlobalScope`, so you must explicitly opt-in into using `GlobalScope` with `@OptIn(DelicateCoroutinesApi::class)`.

import kotlinx.coroutines.*
//sampleStart
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
val job = GlobalScope.launch { // root coroutine with launch
println("Throwing exception from launch")
throw IndexOutOfBoundsException() // Will be printed to the console by Thread.defaultUncaughtExceptionHandler
}
job.join()
println("Joined failed job")
val deferred = GlobalScope.async { // root coroutine with async
println("Throwing exception from async")
throw ArithmeticException() // Nothing is printed, relying on user to call await
}
try {
deferred.await()
println("Unreached")
} catch (e: ArithmeticException) {
println("Caught ArithmeticException")
}
}
//sampleEnd

xxxxxxxxxx
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
val job = GlobalScope.launch { // root coroutine with launch
println("Throwing exception from launch")
throw IndexOutOfBoundsException() // Will be printed to the console by Thread.defaultUncaughtExceptionHandler
}
job.join()
println("Joined failed job")
val deferred = GlobalScope.async { // root coroutine with async
println("Throwing exception from async")
throw ArithmeticException() // Nothing is printed, relying on user to call await
}
try {
deferred.await()
println("Unreached")
} catch (e: ArithmeticException) {
println("Caught ArithmeticException")
}
}

Open in Playground →

>
> You can get the full code here.

The output of this code is (with debug):

Throwing exception from launch
Exception in thread "DefaultDispatcher-worker-1 @coroutine#2" java.lang.IndexOutOfBoundsException
Joined failed job
Throwing exception from async
Caught ArithmeticException

## CoroutineExceptionHandler

It is possible to customize the default behavior of printing uncaught exceptions to the console. CoroutineExceptionHandler context element on a root coroutine can be used as a generic `catch` block for this root coroutine and all its children where custom exception handling may take place. It is similar to `Thread.uncaughtExceptionHandler`. You cannot recover from the exception in the `CoroutineExceptionHandler`. The coroutine had already completed with the corresponding exception when the handler is called. Normally, the handler is used to log the exception, show some kind of error message, terminate, and/or restart the application.

>
> Coroutines running in supervision scope do not propagate exceptions to their parent and are excluded from this rule. A further Supervision section of this document gives more details.

import kotlinx.coroutines.*
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
//sampleStart

}
val job = GlobalScope.launch(handler) { // root coroutine, running in GlobalScope
throw AssertionError()
}
val deferred = GlobalScope.async(handler) { // also root, but async instead of launch
throw ArithmeticException() // Nothing will be printed, relying on user to call deferred.await()
}
joinAll(job, deferred)
//sampleEnd
}

xxxxxxxxxx

println("CoroutineExceptionHandler got $exception")
}
val job = GlobalScope.launch(handler) { // root coroutine, running in GlobalScope
throw AssertionError()
}
val deferred = GlobalScope.async(handler) { // also root, but async instead of launch
throw ArithmeticException() // Nothing will be printed, relying on user to call deferred.await()
}
joinAll(job, deferred)

The output of this code is:

CoroutineExceptionHandler got java.lang.AssertionError

## Cancellation and exceptions

Cancellation is closely related to exceptions. Coroutines internally use `CancellationException` for cancellation, these exceptions are ignored by all handlers, so they should be used only as the source of additional debug information, which can be obtained by `catch` block. When a coroutine is cancelled using Job.cancel, it terminates, but it does not cancel its parent.

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val job = launch {
val child = launch {
try {
delay(Long.MAX_VALUE)
} finally {
println("Child is cancelled")
}
}
yield()
println("Cancelling child")
child.cancel()
child.join()
yield()
println("Parent is not cancelled")
}
job.join()
//sampleEnd
}

xxxxxxxxxx
val job = launch {
val child = launch {
try {
delay(Long.MAX_VALUE)
} finally {
println("Child is cancelled")
}
}
yield()
println("Cancelling child")
child.cancel()
child.join()
yield()
println("Parent is not cancelled")
}
job.join()

Cancelling child
Child is cancelled
Parent is not cancelled

>
> In these examples, CoroutineExceptionHandler is always installed to a coroutine that is created in GlobalScope. It does not make sense to install an exception handler to a coroutine that is launched in the scope of the main runBlocking, since the main coroutine is going to be always cancelled when its child completes with exception despite the installed handler.

The original exception is handled by the parent only when all its children terminate, which is demonstrated by the following example.

}
val job = GlobalScope.launch(handler) {
launch { // the first child
try {
delay(Long.MAX_VALUE)
} finally {
withContext(NonCancellable) {
println("Children are cancelled, but exception is not handled until all children terminate")
delay(100)
println("The first child finished its non cancellable block")
}
}
}
launch { // the second child
delay(10)
println("Second child throws an exception")
throw ArithmeticException()
}
}
job.join()
//sampleEnd
}

println("CoroutineExceptionHandler got $exception")
}
val job = GlobalScope.launch(handler) {
launch { // the first child
try {
delay(Long.MAX_VALUE)
} finally {
withContext(NonCancellable) {
println("Children are cancelled, but exception is not handled until all children terminate")
delay(100)
println("The first child finished its non cancellable block")
}
}
}
launch { // the second child
delay(10)
println("Second child throws an exception")
throw ArithmeticException()
}
}
job.join()

Second child throws an exception
Children are cancelled, but exception is not handled until all children terminate
The first child finished its non cancellable block
CoroutineExceptionHandler got java.lang.ArithmeticException

## Exceptions aggregation

When multiple children of a coroutine fail with an exception, the general rule is "the first exception wins", so the first exception gets handled. All additional exceptions that happen after the first one are attached to the first exception as suppressed ones.

import kotlinx.coroutines.*
import java.io.*
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {

}
val job = GlobalScope.launch(handler) {
launch {
try {
delay(Long.MAX_VALUE) // it gets cancelled when another sibling fails with IOException
} finally {
throw ArithmeticException() // the second exception
}
}
launch {
delay(100)
throw IOException() // the first exception
}
delay(Long.MAX_VALUE)
}
job.join()
}

xxxxxxxxxx
import kotlinx.coroutines.*
import java.io.*
​
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {

println("CoroutineExceptionHandler got $exception with suppressed ${exception.suppressed.contentToString()}")
}
val job = GlobalScope.launch(handler) {
launch {
try {
delay(Long.MAX_VALUE) // it gets cancelled when another sibling fails with IOException
} finally {
throw ArithmeticException() // the second exception
}
}
launch {
delay(100)
throw IOException() // the first exception
}
delay(Long.MAX_VALUE)
}
job.join()
}

CoroutineExceptionHandler got java.io.IOException with suppressed [java.lang.ArithmeticException]

>
> Note that this mechanism currently only works on Java version 1.7+. The JS and Native restrictions are temporary and will be lifted in the future.

Cancellation exceptions are transparent and are unwrapped by default:

import kotlinx.coroutines.*
import java.io.*
@OptIn(DelicateCoroutinesApi::class)
fun main() = runBlocking {
//sampleStart

}
val job = GlobalScope.launch(handler) {
val innerJob = launch { // all this stack of coroutines will get cancelled
launch {
launch {
throw IOException() // the original exception
}
}
}
try {
innerJob.join()
} catch (e: CancellationException) {
println("Rethrowing CancellationException with original cause")
throw e // cancellation exception is rethrown, yet the original IOException gets to the handler
}
}
job.join()
//sampleEnd
}

println("CoroutineExceptionHandler got $exception")
}
val job = GlobalScope.launch(handler) {
val innerJob = launch { // all this stack of coroutines will get cancelled
launch {
launch {
throw IOException() // the original exception
}
}
}
try {
innerJob.join()
} catch (e: CancellationException) {
println("Rethrowing CancellationException with original cause")
throw e // cancellation exception is rethrown, yet the original IOException gets to the handler
}
}
job.join()

Rethrowing CancellationException with original cause
CoroutineExceptionHandler got java.io.IOException

## Supervision

As we have studied before, cancellation is a bidirectional relationship propagating through the whole hierarchy of coroutines. Let us take a look at the case when unidirectional cancellation is required.

A good example of such a requirement is a UI component with the job defined in its scope. If any of the UI's child tasks have failed, it is not always necessary to cancel (effectively kill) the whole UI component, but if the UI component is destroyed (and its job is cancelled), then it is necessary to cancel all child jobs as their results are no longer needed.

Another example is a server process that spawns multiple child jobs and needs to supervise their execution, tracking their failures and only restarting the failed ones.

### Supervision job

The SupervisorJob can be used for these purposes. It is similar to a regular Job with the only exception that cancellation is propagated only downwards. This can easily be demonstrated using the following example:

import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
val supervisor = SupervisorJob()
with(CoroutineScope(coroutineContext + supervisor)) {
// launch the first child -- its exception is ignored for this example (don't do this in practice!)

println("The first child is failing")
throw AssertionError("The first child is cancelled")
}
// launch the second child
val secondChild = launch {
firstChild.join()
// Cancellation of the first child is not propagated to the second child
println("The first child is cancelled: ${firstChild.isCancelled}, but the second one is still active")
try {
delay(Long.MAX_VALUE)
} finally {
// But cancellation of the supervisor is propagated
println("The second child is cancelled because the supervisor was cancelled")
}
}
// wait until the first child fails & completes
firstChild.join()
println("Cancelling the supervisor")
supervisor.cancel()
secondChild.join()
}
//sampleEnd
}

xxxxxxxxxx
val supervisor = SupervisorJob()
with(CoroutineScope(coroutineContext + supervisor)) {
// launch the first child -- its exception is ignored for this example (don't do this in practice!)

println("The first child is failing")
throw AssertionError("The first child is cancelled")
}
// launch the second child
val secondChild = launch {
firstChild.join()
// Cancellation of the first child is not propagated to the second child
println("The first child is cancelled: ${firstChild.isCancelled}, but the second one is still active")
try {
delay(Long.MAX_VALUE)
} finally {
// But cancellation of the supervisor is propagated
println("The second child is cancelled because the supervisor was cancelled")
}
}
// wait until the first child fails & completes
firstChild.join()
println("Cancelling the supervisor")
supervisor.cancel()
secondChild.join()
}

The first child is failing
The first child is cancelled: true, but the second one is still active
Cancelling the supervisor
The second child is cancelled because the supervisor was cancelled

### Supervision scope

Instead of coroutineScope, we can use supervisorScope for scoped concurrency. It propagates the cancellation in one direction only and cancels all its children only if it failed itself. It also waits for all children before completion just like coroutineScope does.

import kotlin.coroutines.*
import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart
try {
supervisorScope {
val child = launch {
try {
println("The child is sleeping")
delay(Long.MAX_VALUE)
} finally {
println("The child is cancelled")
}
}
// Give our child a chance to execute and print using yield
yield()
println("Throwing an exception from the scope")
throw AssertionError()
}
} catch(e: AssertionError) {
println("Caught an assertion error")
}
//sampleEnd
}

xxxxxxxxxx
try {
supervisorScope {
val child = launch {
try {
println("The child is sleeping")
delay(Long.MAX_VALUE)
} finally {
println("The child is cancelled")
}
}
// Give our child a chance to execute and print using yield
yield()
println("Throwing an exception from the scope")
throw AssertionError()
}
} catch(e: AssertionError) {
println("Caught an assertion error")
}

The child is sleeping
Throwing an exception from the scope
The child is cancelled
Caught an assertion error

#### Exceptions in supervised coroutines

Another crucial difference between regular and supervisor jobs is exception handling. Every child should handle its exceptions by itself via the exception handling mechanism. This difference comes from the fact that child's failure does not propagate to the parent. It means that coroutines launched directly inside the supervisorScope do use the CoroutineExceptionHandler that is installed in their scope in the same way as root coroutines do (see the CoroutineExceptionHandler section for details).

import kotlin.coroutines.*
import kotlinx.coroutines.*
fun main() = runBlocking {
//sampleStart

}
supervisorScope {
val child = launch(handler) {
println("The child throws an exception")
throw AssertionError()
}
println("The scope is completing")
}
println("The scope is completed")
//sampleEnd
}

println("CoroutineExceptionHandler got $exception")
}
supervisorScope {
val child = launch(handler) {
println("The child throws an exception")
throw AssertionError()
}
println("The scope is completing")
}
println("The scope is completed")

The scope is completing
The child throws an exception
CoroutineExceptionHandler got java.lang.AssertionError
The scope is completed

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Shared mutable state and concurrency

# Shared mutable state and concurrency

Edit page 16 February 2022

Coroutines can be executed parallelly using a multi-threaded dispatcher like the Dispatchers.Default. It presents all the usual parallelism problems. The main problem being synchronization of access to shared mutable state. Some solutions to this problem in the land of coroutines are similar to the solutions in the multi-threaded world, but others are unique.

## The problem

Let us launch a hundred coroutines all doing the same action a thousand times. We'll also measure their completion time for further comparisons:

val n = 100 // number of coroutines to launch
val k = 1000 // times an action is repeated by each coroutine
val time = measureTimeMillis {
coroutineScope { // scope for coroutines
repeat(n) {
launch {
repeat(k) { action() }
}
}
}
}
println("Completed ${n * k} actions in $time ms")
}

We start with a very simple action that increments a shared mutable variable using multi-threaded Dispatchers.Default.

import kotlinx.coroutines.*
import kotlin.system.*

val n = 100 // number of coroutines to launch
val k = 1000 // times an action is repeated by each coroutine
val time = measureTimeMillis {
coroutineScope { // scope for coroutines
repeat(n) {
launch {
repeat(k) { action() }
}
}
}
}
println("Completed ${n * k} actions in $time ms")
}
//sampleStart
var counter = 0
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
counter++
}
}
println("Counter = $counter")
}
//sampleEnd

xxxxxxxxxx
var counter = 0
​
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
counter++
}
}
println("Counter = $counter")
}

Open in Playground →

>
> You can get the full code here.

What does it print at the end? It is highly unlikely to ever print "Counter = 100000", because a hundred coroutines increment the `counter` concurrently from multiple threads without any synchronization.

## Volatiles are of no help

There is a common misconception that making a variable `volatile` solves concurrency problem. Let us try it:

val n = 100 // number of coroutines to launch
val k = 1000 // times an action is repeated by each coroutine
val time = measureTimeMillis {
coroutineScope { // scope for coroutines
repeat(n) {
launch {
repeat(k) { action() }
}
}
}
}
println("Completed ${n * k} actions in $time ms")
}
//sampleStart
@Volatile // in Kotlin `volatile` is an annotation
var counter = 0
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
counter++
}
}
println("Counter = $counter")
}
//sampleEnd

xxxxxxxxxx
@Volatile // in Kotlin `volatile` is an annotation
var counter = 0
​
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
counter++
}
}
println("Counter = $counter")
}

This code works slower, but we still don't always get "Counter = 100000" at the end, because volatile variables guarantee linearizable (this is a technical term for "atomic") reads and writes to the corresponding variable, but do not provide atomicity of larger actions (increment in our case).

## Thread-safe data structures

The general solution that works both for threads and for coroutines is to use a thread-safe (aka synchronized, linearizable, or atomic) data structure that provides all the necessary synchronization for the corresponding operations that needs to be performed on a shared state. In the case of a simple counter we can use `AtomicInteger` class which has atomic `incrementAndGet` operations:

import kotlinx.coroutines.*
import java.util.concurrent.atomic.*
import kotlin.system.*

val n = 100 // number of coroutines to launch
val k = 1000 // times an action is repeated by each coroutine
val time = measureTimeMillis {
coroutineScope { // scope for coroutines
repeat(n) {
launch {
repeat(k) { action() }
}
}
}
}
println("Completed ${n * k} actions in $time ms")
}
//sampleStart
val counter = AtomicInteger()
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
counter.incrementAndGet()
}
}
println("Counter = $counter")
}
//sampleEnd

xxxxxxxxxx
val counter = AtomicInteger()
​
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
counter.incrementAndGet()
}
}
println("Counter = $counter")
}

This is the fastest solution for this particular problem. It works for plain counters, collections, queues and other standard data structures and basic operations on them. However, it does not easily scale to complex state or to complex operations that do not have ready-to-use thread-safe implementations.

## Thread confinement fine-grained

Thread confinement is an approach to the problem of shared mutable state where all access to the particular shared state is confined to a single thread. It is typically used in UI applications, where all UI state is confined to the single event-dispatch/application thread. It is easy to apply with coroutines by using a single-threaded context.

val n = 100 // number of coroutines to launch
val k = 1000 // times an action is repeated by each coroutine
val time = measureTimeMillis {
coroutineScope { // scope for coroutines
repeat(n) {
launch {
repeat(k) { action() }
}
}
}
}
println("Completed ${n * k} actions in $time ms")
}
//sampleStart
val counterContext = newSingleThreadContext("CounterContext")
var counter = 0
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
// confine each increment to a single-threaded context
withContext(counterContext) {
counter++
}
}
}
println("Counter = $counter")
}
//sampleEnd

xxxxxxxxxx
val counterContext = newSingleThreadContext("CounterContext")
var counter = 0
​
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
// confine each increment to a single-threaded context
withContext(counterContext) {
counter++
}
}
}
println("Counter = $counter")
}

This code works very slowly, because it does fine-grained thread-confinement. Each individual increment switches from multi-threaded Dispatchers.Default context to the single-threaded context using withContext(counterContext) block.

## Thread confinement coarse-grained

In practice, thread confinement is performed in large chunks, e.g. big pieces of state-updating business logic are confined to the single thread. The following example does it like that, running each coroutine in the single-threaded context to start with.

val n = 100 // number of coroutines to launch
val k = 1000 // times an action is repeated by each coroutine
val time = measureTimeMillis {
coroutineScope { // scope for coroutines
repeat(n) {
launch {
repeat(k) { action() }
}
}
}
}
println("Completed ${n * k} actions in $time ms")
}
//sampleStart
val counterContext = newSingleThreadContext("CounterContext")
var counter = 0
fun main() = runBlocking {
// confine everything to a single-threaded context
withContext(counterContext) {
massiveRun {
counter++
}
}
println("Counter = $counter")
}
//sampleEnd

xxxxxxxxxx
val counterContext = newSingleThreadContext("CounterContext")
var counter = 0
​
fun main() = runBlocking {
// confine everything to a single-threaded context
withContext(counterContext) {
massiveRun {
counter++
}
}
println("Counter = $counter")
}

This now works much faster and produces correct result.

## Mutual exclusion

Mutual exclusion solution to the problem is to protect all modifications of the shared state with a critical section that is never executed concurrently. In a blocking world you'd typically use `synchronized` or `ReentrantLock` for that. Coroutine's alternative is called Mutex. It has lock and unlock functions to delimit a critical section. The key difference is that `Mutex.lock()` is a suspending function. It does not block a thread.

There is also withLock extension function that conveniently represents `mutex.lock(); try { ... } finally { mutex.unlock() }` pattern:

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlin.system.*

val n = 100 // number of coroutines to launch
val k = 1000 // times an action is repeated by each coroutine
val time = measureTimeMillis {
coroutineScope { // scope for coroutines
repeat(n) {
launch {
repeat(k) { action() }
}
}
}
}
println("Completed ${n * k} actions in $time ms")
}
//sampleStart
val mutex = Mutex()
var counter = 0
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
// protect each increment with lock
mutex.withLock {
counter++
}
}
}
println("Counter = $counter")
}
//sampleEnd

xxxxxxxxxx
val mutex = Mutex()
var counter = 0
​
fun main() = runBlocking {
withContext(Dispatchers.Default) {
massiveRun {
// protect each increment with lock
mutex.withLock {
counter++
}
}
}
println("Counter = $counter")
}

The locking in this example is fine-grained, so it pays the price. However, it is a good choice for some situations where you absolutely must modify some shared state periodically, but there is no natural thread that this state is confined to.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/select-expression.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Select expression (experimental)

# Select expression (experimental)

Edit page 16 February 2022

>
> Select expressions are an experimental feature of `kotlinx.coroutines`. Their API is expected to evolve in the upcoming updates of the `kotlinx.coroutines` library with potentially breaking changes.

## Selecting from channels

Let us have two producers of strings: `fizz` and `buzz`. The `fizz` produces "Fizz" string every 500 ms:

while (true) { // sends "Fizz" every 500 ms
delay(500)
send("Fizz")
}
}

And the `buzz` produces "Buzz!" string every 1000 ms:

while (true) { // sends "Buzz!" every 1000 ms
delay(1000)
send("Buzz!")
}
}

Using receive suspending function we can receive either from one channel or the other. But select expression allows us to receive from both simultaneously using its onReceive clauses:

}

}
}
}

Let us run it all seven times:

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*

//sampleStart
val fizz = fizz()
val buzz = buzz()
repeat(7) {
selectFizzBuzz(fizz, buzz)
}
coroutineContext.cancelChildren() // cancel fizz & buzz coroutines
//sampleEnd
}

xxxxxxxxxx
val fizz = fizz()
val buzz = buzz()
repeat(7) {
selectFizzBuzz(fizz, buzz)
}
coroutineContext.cancelChildren() // cancel fizz & buzz coroutines

Open in Playground →

>
> You can get the full code here.

The result of this code is:

## Selecting on close

The onReceive clause in `select` fails when the channel is closed causing the corresponding `select` to throw an exception. We can use onReceiveCatching clause to perform a specific action when the channel is closed. The following example also shows that `select` is an expression that returns the result of its selected clause:

if (value != null) {

} else {
"Channel 'a' is closed"
}
}

} else {
"Channel 'b' is closed"
}
}
}

Let's use it with channel `a` that produces "Hello" string four times and channel `b` that produces "World" four times:

//sampleStart

repeat(4) { send("Hello $it") }
}

repeat(4) { send("World $it") }
}
repeat(8) { // print first eight results
println(selectAorB(a, b))
}
coroutineContext.cancelChildren()
//sampleEnd
}

xxxxxxxxxx

repeat(4) { send("Hello $it") }
}

repeat(4) { send("World $it") }
}
repeat(8) { // print first eight results
println(selectAorB(a, b))
}
coroutineContext.cancelChildren()

The result of this code is quite interesting, so we'll analyze it in more detail:

Channel 'a' is closed
Channel 'a' is closed

There are a couple of observations to make out of it.

First of all, `select` is biased to the first clause. When several clauses are selectable at the same time, the first one among them gets selected. Here, both channels are constantly producing strings, so `a` channel, being the first clause in select, wins. However, because we are using unbuffered channel, the `a` gets suspended from time to time on its send invocation and gives a chance for `b` to send, too.

The second observation, is that onReceiveCatching gets immediately selected when the channel is already closed.

## Selecting to send

Select expression has onSend clause that can be used for a great good in combination with a biased nature of selection.

Let us write an example of a producer of integers that sends its values to a `side` channel when the consumers on its primary channel cannot keep up with it:

for (num in 1..10) { // produce 10 numbers from 1 to 10
delay(100) // every 100 ms

onSend(num) {} // Send to the primary channel
side.onSend(num) {} // or to the side channel
}
}
}

Consumer is going to be quite slow, taking 250 ms to process each number:

launch { // this is a very fast consumer for the side channel
side.consumeEach { println("Side channel has $it") }
}
produceNumbers(side).consumeEach {
println("Consuming $it")
delay(250) // let us digest the consumed number properly, do not hurry
}
println("Done consuming")
coroutineContext.cancelChildren()
//sampleEnd
}

launch { // this is a very fast consumer for the side channel
side.consumeEach { println("Side channel has $it") }
}
produceNumbers(side).consumeEach {
println("Consuming $it")
delay(250) // let us digest the consumed number properly, do not hurry
}
println("Done consuming")
coroutineContext.cancelChildren()

So let us see what happens:

Consuming 1
Side channel has 2
Side channel has 3
Consuming 4
Side channel has 5
Side channel has 6
Consuming 7
Side channel has 8
Side channel has 9
Consuming 10
Done consuming

## Selecting deferred values

Deferred values can be selected using onAwait clause. Let us start with an async function that returns a deferred string value after a random delay:

fun CoroutineScope.asyncString(time: Int) = async {
delay(time.toLong())
"Waited for $time ms"
}

Let us start a dozen of them with a random delay.

fun CoroutineScope.asyncStringsList(): List<Deferred<String>> {
val random = Random(3)
return List(12) { asyncString(random.nextInt(1000)) }
}

Now the main function awaits for the first of them to complete and counts the number of deferred values that are still active. Note that we've used here the fact that `select` expression is a Kotlin DSL, so we can provide clauses for it using an arbitrary code. In this case we iterate over a list of deferred values to provide `onAwait` clause for each deferred value.

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.*
import java.util.*

fun CoroutineScope.asyncString(time: Int) = async {
delay(time.toLong())
"Waited for $time ms"
}
fun CoroutineScope.asyncStringsList(): List<Deferred<String>> {
val random = Random(3)
return List(12) { asyncString(random.nextInt(1000)) }
}

//sampleStart
val list = asyncStringsList()

}
}
}
println(result)
val countActive = list.count { it.isActive }
println("$countActive coroutines are still active")
//sampleEnd
}

xxxxxxxxxx
val list = asyncStringsList()

"Deferred $index produced answer '$answer'"
}
}
}
println(result)
val countActive = list.count { it.isActive }
println("$countActive coroutines are still active")

The output is:

Deferred 4 produced answer 'Waited for 128 ms'
11 coroutines are still active

## Switch over a channel of deferred values

Let us write a channel producer function that consumes a channel of deferred string values, waits for each received deferred value, but only until the next deferred value comes over or the channel is closed. This example puts together onReceiveCatching and onAwait clauses in the same `select`:

fun CoroutineScope.switchMapDeferreds(input: ReceiveChannel<Deferred<String>>) = produce<String> {
var current = input.receive() // start with first received deferred value
while (isActive) { // loop while not cancelled/closed

input.receiveCatching().getOrNull() // and use the next deferred from the input channel
}
}
if (next == null) {
println("Channel was closed")
break // out of loop
} else {
current = next
}
}
}

To test it, we'll use a simple async function that resolves to a specified string after a specified time:

fun CoroutineScope.asyncString(str: String, time: Long) = async {
delay(time)
str
}

The main function just launches a coroutine to print results of `switchMapDeferreds` and sends some test data to it:

input.receiveCatching().getOrNull() // and use the next deferred from the input channel
}
}
if (next == null) {
println("Channel was closed")
break // out of loop
} else {
current = next
}
}
}
fun CoroutineScope.asyncString(str: String, time: Long) = async {
delay(time)
str
}

//sampleStart
val chan = Channel<Deferred<String>>() // the channel for test
launch { // launch printing coroutine
for (s in switchMapDeferreds(chan))
println(s) // print each received string
}
chan.send(asyncString("BEGIN", 100))
delay(200) // enough time for "BEGIN" to be produced
chan.send(asyncString("Slow", 500))
delay(100) // not enough time to produce slow
chan.send(asyncString("Replace", 100))
delay(500) // give it time before the last one
chan.send(asyncString("END", 500))
delay(1000) // give it time to process
chan.close() // close the channel ...
delay(500) // and wait some time to let it finish
//sampleEnd
}

xxxxxxxxxx
val chan = Channel<Deferred<String>>() // the channel for test
launch { // launch printing coroutine
for (s in switchMapDeferreds(chan))
println(s) // print each received string
}
chan.send(asyncString("BEGIN", 100))
delay(200) // enough time for "BEGIN" to be produced
chan.send(asyncString("Slow", 500))
delay(100) // not enough time to produce slow
chan.send(asyncString("Replace", 100))
delay(500) // give it time before the last one
chan.send(asyncString("END", 500))
delay(1000) // give it time to process
chan.close() // close the channel ...
delay(500) // and wait some time to let it finish

The result of this code:

BEGIN
Replace
END
Channel was closed

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/debug-coroutines-with-idea.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Debug coroutines using IntelliJ IDEA – tutorial

# Debug coroutines using IntelliJ IDEA – tutorial

Edit page 16 February 2022

This tutorial demonstrates how to create Kotlin coroutines and debug them using IntelliJ IDEA.

The tutorial assumes you have prior knowledge of the coroutines concept.

## Create coroutines

1. Open a Kotlin project in IntelliJ IDEA. If you don't have a project, create one.

2. To use the `kotlinx.coroutines` library in a Gradle project, add the following dependency to `build.gradle(.kts)`:

Kotlin

Groovy

dependencies {
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
dependencies {
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2'
}

For other build systems, see instructions in the `kotlinx.coroutines` README.

3. Open the `Main.kt` file in `src/main/kotlin`.

The `src` directory contains Kotlin source files and resources. The `Main.kt` file contains sample code that will print `Hello World!`.

4. Change code in the `main()` function:

- Use the `runBlocking()` block to wrap a coroutine.

- Use the `async()` function to create coroutines that compute deferred values `a` and `b`.

- Use the `await()` function to await the computation result.

- Use the `println()` function to print computing status and the result of multiplication to the output.

import kotlinx.coroutines.*

val a = async {
println("I'm computing part of the answer")
6
}
val b = async {
println("I'm computing another part of the answer")
7
}
println("The answer is ${a.await() * b.await()}")
}

5. Build the code by clicking Build Project.

## Debug coroutines

1. Set breakpoints at the lines with the `println()` function call:

2. Run the code in debug mode by clicking Debug next to the run configuration at the top of the screen.

The Debug tool window appears:

- The Frames tab contains the call stack.

- The Variables tab contains variables in the current context.

- The Coroutines tab contains information on running or suspended coroutines. It shows that there are three coroutines. The first one has the RUNNING status, and the other two have the CREATED status.

3. Resume the debugger session by clicking Resume Program in the Debug tool window:

Now the Coroutines tab shows the following:

- The first coroutine has the SUSPENDED status – it is waiting for the values so it can multiply them.

- The second coroutine is calculating the `a` value – it has the RUNNING status.

- The third coroutine has the CREATED status and isn’t calculating the value of `b`.
4. Resume the debugger session by clicking Resume Program in the Debug tool window:

- The second coroutine has computed its value and disappeared.

- The third coroutine is calculating the value of `b` – it has the RUNNING status.

Using IntelliJ IDEA debugger, you can dig deeper into each coroutine to debug your code.

### Optimized-out variables

If you use `suspend` functions, in the debugger, you might see the "was optimized out" text next to a variable's name:

>
> Never use this flag in production: `-Xdebug` can cause memory leaks.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/debug-flow-with-idea.html

1. Official libraries

2. Coroutines (kotlinx.coroutines)

3. Debug Kotlin Flow using IntelliJ IDEA – tutorial

# Debug Kotlin Flow using IntelliJ IDEA – tutorial

Edit page 16 February 2022

This tutorial demonstrates how to create Kotlin Flow and debug it using IntelliJ IDEA.

The tutorial assumes you have prior knowledge of the coroutines and Kotlin Flow concepts.

## Create a Kotlin flow

Create a Kotlin flow with a slow emitter and a slow collector:

1. Open a Kotlin project in IntelliJ IDEA. If you don't have a project, create one.

2. To use the `kotlinx.coroutines` library in a Gradle project, add the following dependency to `build.gradle(.kts)`:

Kotlin

Groovy

dependencies {
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
dependencies {
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2'
}

For other build systems, see instructions in the `kotlinx.coroutines` README.

3. Open the `Main.kt` file in `src/main/kotlin`.

The `src` directory contains Kotlin source files and resources. The `Main.kt` file contains sample code that will print `Hello World!`.

4. Create the `simple()` function that returns a flow of three numbers:

- Use the `delay()` function to imitate CPU-consuming blocking code. It suspends the coroutine for 100 ms without blocking the thread.

- Produce the values in the `for` loop using the `emit()` function.

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.system.*

for (i in 1..3) {
delay(100)
emit(i)
}
}

5. Change the code in the `main()` function:

- Use the `runBlocking()` block to wrap a coroutine.

- Collect the emitted values using the `collect()` function.

- Use the `delay()` function to imitate CPU-consuming code. It suspends the coroutine for 300 ms without blocking the thread.

- Print the collected value from the flow using the `println()` function.

fun main() = runBlocking {
simple()

println(value)
}
}

6. Build the code by clicking Build Project.

## Debug the coroutine

1. Set a breakpoint at the line where the `emit()` function is called:

2. Run the code in debug mode by clicking Debug next to the run configuration at the top of the screen.

The Debug tool window appears:

- The Frames tab contains the call stack.

- The Variables tab contains variables in the current context. It tells us that the flow is emitting the first value.

- The Coroutines tab contains information on running or suspended coroutines.

3. Resume the debugger session by clicking Resume Program in the Debug tool window. The program stops at the same breakpoint.

Now the flow emits the second value.

### Optimized-out variables

If you use `suspend` functions, in the debugger, you might see the "was optimized out" text next to a variable's name:

>
> Never use this flag in production: `-Xdebug` can cause memory leaks.

## Add a concurrently running coroutine

1. Open the `Main.kt` file in `src/main/kotlin`.

2. Enhance the code to run the emitter and collector concurrently:

- Add a call to the `buffer()` function to run the emitter and collector concurrently. `buffer()` stores emitted values and runs the flow collector in a separate coroutine.

simple()
.buffer()

3. Build the code by clicking Build Project.

## Debug a Kotlin flow with two coroutines

1. Set a new breakpoint at `println(value)`.

The Debug tool window appears.

In the Coroutines tab, you can see that there are two coroutines running concurrently. The flow collector and emitter run in separate coroutines because of the `buffer()` function. The `buffer()` function buffers emitted values from the flow. The emitter coroutine has the RUNNING status, and the collector coroutine has the SUSPENDED status.

3. Resume the debugger session by clicking Resume Program in the Debug tool window.

Now the collector coroutine has the RUNNING status, while the emitter coroutine has the SUSPENDED status.

You can dig deeper into each coroutine to debug your code.

Thanks for your feedback!

Was this page helpful?

YesNo

---

# https://kotlinlang.org/docs/coroutines-basics.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/coroutines-and-channels.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/cancellation-and-timeouts.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/composing-suspending-functions.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/flow.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/channels.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/exception-handling.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/select-expression.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/debug-coroutines-with-idea.html)

# Page not found

Please use search or try

starting from home.

---

# https://kotlinlang.org/docs/debug-flow-with-idea.html)

# Page not found

Please use search or try

starting from home.

---

