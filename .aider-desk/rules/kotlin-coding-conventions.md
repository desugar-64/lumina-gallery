### **Source Code Organization**

* **Directory Structure**: On the JVM, Kotlin source files (`.kt`) should reside in the same source root as Java files and follow the same package-based directory structure.
* **File Naming**:
    * A file containing a single class/interface should be named `ClassName.kt`.
    * A file with multiple classes or only top-level declarations should have a descriptive name in `UpperCamelCase`, like `ProcessDeclarations.kt`.
    * Avoid meaningless names like `Util.kt`.
* **File Organization**:
    * Placing multiple, closely related declarations (classes, functions) in a single file is encouraged, provided the file size remains reasonable (not exceeding a few hundred lines).
    * Define extension functions relevant to all clients of a class in the same file as the class itself.
    * Define client-specific extension functions next to the client's code.
* **Class Layout**:
    * The contents of a class should be ordered as follows:
        1.  Property declarations and initializer blocks
        2.  Secondary constructors
        3.  Method declarations
        4.  Companion object
    * Do not sort methods by alphabet or visibility. Group related logic together.
    * Place nested classes near the code that uses them.
* **Interface Implementation**: When implementing an interface, keep the implementing members in the same order as the interface members.
* **Overloads**: Always place overloaded functions next to each other.

***

### **Naming Rules**

* **Packages**: Use all lowercase, no underscores (e.g., `org.example.project`). If multiple words are necessary, concatenate or use camelCase (e.g., `org.example.myProject`).
* **Classes and Objects**: Use `UpperCamelCase`.
* **Functions, Properties, Variables**: Use `lowerCamelCase` with no underscores.
* **Test Methods**: You can use method names with spaces enclosed in backticks (e.g., ``fun `ensure everything works`() { /*...*/ }``). Underscores are also permitted in test method names.
* **Constant Properties**: For `const val` properties, or top-level/object `val` properties holding deeply immutable data, use `SCREAMING_SNAKE_CASE`.
* **Backing Properties**: For a private property that is an implementation detail for a public one, prefix its name with an underscore (`_`).
* **Acronyms**: For a two-letter acronym, use uppercase for both letters (`IOStream`). For longer acronyms, capitalize only the first letter (`XmlFormatter`).

***

### **Formatting**

* **Indentation**: Use 4 spaces. Do not use tabs.
* **Braces**: The opening brace goes at the end of the line where the construct begins. The closing brace is on a separate line, aligned horizontally with the opening construct.
* **Horizontal Whitespace**:
    * Put spaces around binary operators (`a + b`), but not the "range to" operator (`0..i`).
    * Do not put spaces around unary operators (`a++`).
    * Put a space between a control flow keyword (`if`, `when`, `for`, `while`) and its opening parenthesis.
    * Do not put a space before an opening parenthesis in a primary constructor, method declaration, or method call.
* **Colon**:
    * Put a space *before* a colon when it separates a type and a supertype or a class and a constructor delegation.
    * Do not put a space *before* a colon when it separates a declaration and its type.
    * Always put a space *after* a colon.
* **Class Headers**: For classes with long headers, format each primary constructor parameter on a separate line with indentation. The closing parenthesis should be on a new line.
* **Modifier Order**: If a declaration has multiple modifiers, place them in this exact order: `public / protected / private / internal`, `expect / actual`, `final / open / abstract / sealed / const`, `external`, `override`, `lateinit`, `tailrec`, `vararg`, `suspend`, `inner`, `enum / annotation / fun`, `companion`, `inline / value`, `infix`, `operator`, `data`.
* **Functions**:
    * Prefer using an expression body for functions with a single expression. (`fun foo() = 1` is good; `fun foo(): Int { return 1 }` is bad).
    * If a function signature doesn't fit on one line, put each parameter on a separate indented line.
* **Control Flow Statements**:
    * For multiline `if` or `when` conditions, always use curly braces for the body.
    * Place `else`, `catch`, and `finally` on the same line as the preceding curly brace.
    * In `when` statements, short branches can go on the same line as the condition without braces.
* **Chained Calls**: When wrapping chained calls, put the `.` or `?.` on the next line with a single indent.
* **Lambdas**:
    * Use spaces around curly braces and the arrow (`->`).
    * If a call takes a single lambda, pass it outside of parentheses.
    * Do not put a space between a lambda label and the opening brace (`lit@{ ... }`).
* **Trailing Commas**: Using trailing commas at the declaration site is encouraged as it makes version-control diffs cleaner and simplifies adding or reordering elements.

***

### **Documentation and Idiomatic Usage**

* **Documentation (KDoc)**:
    * Avoid using `@param` and `@return`. Instead, integrate parameter and return value descriptions into the main documentation text, linking to parameters with square brackets (e.g., `the given [number]`).
* **Redundant Constructs**:
    * Omit the `: Unit` return type for functions.
    * Omit semicolons whenever possible.
    * Do not use curly braces in string templates for simple variables (`"$name"`). Use them only for longer expressions (`"${children.size}"`).
* **Immutability**: Prefer `val` over `var`. Always use immutable collection interfaces (`Collection`, `List`, `Set`, `Map`) for collections that are not mutated.
* **Parameters**:
    * Prefer functions with default parameter values to declaring overloaded functions.
    * Use named arguments when calling a function with multiple parameters of the same primitive type, or for `Boolean` parameters.
* **Lambdas**:
    * For short, non-nested lambdas, it's recommended to use the `it` convention.
    * In nested lambdas with parameters, always declare parameters explicitly.
    * Avoid multiple labeled returns in a lambda; aim for a single exit point.
* **Control Flow**:
    * Prefer using the expression form of `try`, `if`, and `when`.
    * Use `if` for binary conditions and `when` for three or more options.
    * For nullable `Boolean` values, use explicit checks like `if (value == true)`.
* **Loops vs. Higher-Order Functions**: Prefer using higher-order functions (`filter`, `map`, etc.) over loops. An exception is `forEach`, where a regular `for` loop is often preferred.
* **Ranges**: Use the `..<` operator to loop over open-ended ranges: `for (i in 0..<n)`.
* **Scope Functions (`let`, `run`, `with`, `apply`, `also`)**:
    * Use `apply` for object configuration where the object itself is returned. The context object is `this`.
    * Use `also` for additional actions on an object, such as logging. The context object is `it` and the object is returned.
    * Use `let` to execute a lambda on non-nullable objects. The context object is `it` and the lambda result is returned.
    * Use `run` for both configuration and computing a result. The context object is `this` and the lambda result is returned.
    * Use `with` to group calls on an object when you don't need the returned result. The context object is `this`.
* **Factory Functions**: If a factory function is declared for a class, prefer a distinct name that clarifies its behavior rather than using the same name as the class.
* **Library Conventions**: For public APIs, always explicitly specify member visibility and function/property types. Provide KDoc for all public members.