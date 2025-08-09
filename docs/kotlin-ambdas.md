# https://kotlinlang.org/docs/lambdas.html

1. Concepts

2. Functions

3. Lambdas

# Higher-order functions and lambdas

Edit page 27 November 2024

Kotlin functions are first-class, which means they can be stored in variables and data structures, and can be passed as arguments to and returned from other higher-order functions. You can perform any operations on functions that are possible for other non-function values.

To facilitate this, Kotlin, as a statically typed programming language, uses a family of function types to represent functions, and provides a set of specialized language constructs, such as lambda expressions.

## Higher-order functions

A higher-order function is a function that takes functions as parameters, or returns a function.

A good example of a higher-order function is the functional programming idiom `fold` for collections. It takes an initial accumulator value and a combining function and builds its return value by consecutively combining the current accumulator value with each collection element, replacing the accumulator value each time:

initial: R,

): R {
var accumulator: R = initial
for (element: T in this) {
accumulator = combine(accumulator, element)
}
return accumulator
}

To call `fold`, you need to pass an instance of the function type to it as an argument, and lambda expressions ( described in more detail below) are widely used for this purpose at higher-order function call sites:

fun main() {
//sampleStart
val items = listOf(1, 2, 3, 4, 5)

// Lambdas are code blocks enclosed in curly braces.
items.fold(0, {

val result = acc + i
println("result = $result")
// The last expression in a lambda is considered the return value:
result
})

// Parameter types in a lambda are optional if they can be inferred:

// Function references can also be used for higher-order function calls:
val product = items.fold(1, Int::times)
//sampleEnd
println("joinedToString = $joinedToString")
println("product = $product")
}

xxxxxxxxxx
val items = listOf(1, 2, 3, 4, 5)
​
// Lambdas are code blocks enclosed in curly braces.
items.fold(0, {

print("acc = $acc, i = $i, ")
val result = acc + i
println("result = $result")
// The last expression in a lambda is considered the return value:
result
})
​
// Parameter types in a lambda are optional if they can be inferred:

​
// Function references can also be used for higher-order function calls:
val product = items.fold(1, Int::times)
​

Open in Playground →

Target: JVMRunning on v.2.2.0

## Function types

These types have a special notation that corresponds to the signatures of the functions - their parameters and return values:

>
> The arrow notation is right-associative, `(Int) -> (Int) -> Unit` is equivalent to the previous example, but not to `((Int) -> (Int)) -> Unit`.

You can also give a function type an alternative name by using a type alias:

### Instantiating a function type

There are several ways to obtain an instance of a function type:

- Use a code block within a function literal, in one of the following forms:

- an anonymous function: `fun(s: String): Int { return s.toIntOrNull() ?: 0 }`

Function literals with receiver can be used as values of function types with receiver.

- Use a callable reference to an existing declaration:

- a top-level, local, member, or extension function: `::isOdd`, `String::toInt`,

- a constructor: `::Regex`

These include bound callable references that point to a member of a particular instance: `foo::toString`.

- Use instances of a custom class that implements a function type as an interface:

override operator fun invoke(x: Int): Int = TODO()
}

The compiler can infer the function types for variables if there is enough information:

fun main() {
//sampleStart

return f("hello", 3)
}
val result = runTransformation(repeatFun) // OK
//sampleEnd
println("result = $result")
}

xxxxxxxxxx

​

return f("hello", 3)
}
val result = runTransformation(repeatFun) // OK
​

>
> A function type with no receiver is inferred by default, even if a variable is initialized with a reference to an extension function. To alter that, specify the variable type explicitly.

### Invoking a function type instance

A value of a function type can be invoked by using its `invoke(...)` operator: `f.invoke(x)` or just `f(x)`.

If the value has a receiver type, the receiver object should be passed as the first argument. Another way to invoke a value of a function type with receiver is to prepend it with the receiver object, as if the value were an extension function: `1.foo(2)`.

Example:

println(stringPlus("Hello, ", "world!"))

println(intPlus.invoke(1, 1))
println(intPlus(1, 2))
println(2.intPlus(3)) // extension-like call
//sampleEnd
}

println(stringPlus("Hello, ", "world!"))
​
println(intPlus.invoke(1, 1))
println(intPlus(1, 2))
println(2.intPlus(3)) // extension-like call
​

### Inline functions

Sometimes it is beneficial to use inline functions, which provide flexible control flow, for higher-order functions.

## Lambda expressions and anonymous functions

Lambda expressions and anonymous functions are function literals. Function literals are functions that are not declared but are passed immediately as an expression. Consider the following example:

The function `max` is a higher-order function, as it takes a function value as its second argument. This second argument is an expression that is itself a function, called a function literal, which is equivalent to the following named function:

fun compare(a: String, b: String): Boolean = a.length < b.length

### Lambda expression syntax

The full syntactic form of lambda expressions is as follows:

- A lambda expression is always surrounded by curly braces.

- Parameter declarations in the full syntactic form go inside curly braces and have optional type annotations.

- If the inferred return type of the lambda is not `Unit`, the last (or possibly single) expression inside the lambda body is treated as the return value.

If you leave all the optional annotations out, what's left looks like this:

### Passing trailing lambdas

According to Kotlin convention, if the last parameter of a function is a function, then a lambda expression passed as the corresponding argument can be placed outside the parentheses:

Such syntax is also known as trailing lambda.

If the lambda is the only argument in that call, the parentheses can be omitted entirely:

run { println("...") }

### it: implicit name of a single parameter

It's very common for a lambda expression to have only one parameter.

### Returning a value from a lambda expression

You can explicitly return a value from the lambda using the qualified return syntax. Otherwise, the value of the last expression is implicitly returned.

Therefore, the two following snippets are equivalent:

ints.filter {

shouldFilter
}

return@filter shouldFilter
}

This convention, along with passing a lambda expression outside of parentheses, allows for LINQ-style code:

strings.filter { it.length == 5 }.sortedBy { it }.map { it.uppercase() }

### Underscore for unused variables

If the lambda parameter is unused, you can place an underscore instead of its name:

### Destructuring in lambdas

Destructuring in lambdas is described as a part of destructuring declarations.

### Anonymous functions

The lambda expression syntax above is missing one thing – the ability to specify the function's return type. In most cases, this is unnecessary because the return type can be inferred automatically. However, if you do need to specify it explicitly, you can use an alternative syntax: an anonymous function.

fun(x: Int, y: Int): Int = x + y

An anonymous function looks very much like a regular function declaration, except its name is omitted. Its body can be either an expression (as shown above) or a block:

fun(x: Int, y: Int): Int {
return x + y
}

The parameters and the return type are specified in the same way as for regular functions, except the parameter types can be omitted if they can be inferred from the context:

>
> When passing anonymous functions as parameters, place them inside the parentheses. The shorthand syntax that allows you to leave the function outside the parentheses works only for lambda expressions.

Another difference between lambda expressions and anonymous functions is the behavior of non-local returns. A `return` statement without a label always returns from the function declared with the `fun` keyword. This means that a `return` inside a lambda expression will return from the enclosing function, whereas a `return` inside an anonymous function will return from the anonymous function itself.

### Closures

A lambda expression or anonymous function (as well as a local function and an object expression) can access its closure, which includes the variables declared in the outer scope. The variables captured in the closure can be modified in the lambda:

var sum = 0

sum += it
}
print(sum)

### Function literals with receiver

As mentioned above, Kotlin provides the ability to call an instance of a function type with receiver while providing the receiver object.

Inside the body of the function literal, the receiver object passed to a call becomes an implicit `this`, so that you can access the members of that receiver object without any additional qualifiers, or access the receiver object using a `this` expression.

This behavior is similar to that of extension functions, which also allow you to access the members of the receiver object inside the function body.

Here is an example of a function literal with receiver along with its type, where `plus` is called on the receiver object:

The anonymous function syntax allows you to specify the receiver type of a function literal directly. This can be useful if you need to declare a variable of a function type with receiver, and then to use it later.

val sum = fun Int.(other: Int): Int = this + other

Lambda expressions can be used as function literals with receiver when the receiver type can be inferred from the context. One of the most important examples of their usage is type-safe builders:

class HTML {
fun body() { ... }
}

val html = HTML() // create the receiver object
html.init() // pass the receiver object to the lambda
return html
}

html { // lambda with receiver begins here
body() // calling a method on the receiver object
}

Thanks for your feedback!

Was this page helpful?

YesNo

---

