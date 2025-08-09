# AGENTS.md

## OpenCode Specialized Subagents

This project uses **5 specialized OpenCode subagents** with local LLM models for domain-expert code review. Each agent is designed for specific expertise areas using optimally matched models from your local LMStudio setup.

### Agent Overview & Model Assignments

| Agent | Model | Expertise | Primary Use Cases |
|-------|-------|-----------|-------------------|
| `@compose-guardian` | Qwen3 Code Q4 | Jetpack Compose API patterns | @Composable review, side effects, state management |
| `@coroutines-auditor` | QWQ 32b Q4 | Concurrency & threading | Race conditions, scope management, Flow patterns |
| `@graphics-performance` | Qwen3 Code Q4 | Graphics optimization | Atlas performance, bitmap memory, Canvas optimization |
| `@architecture-workflow` | GPT OSS 20b | Clean Architecture & workflows | Build processes, ktlint, dependency injection |
| `@functional-purist` | QWQ 32b Q4 | Functional programming | Pure functions, OOP elimination, stateless design |

### Detailed Agent Capabilities

#### 1. **Compose Standards Guardian** (`@compose-guardian`)
**When to Use:**
- Reviewing any @Composable function for API compliance
- Checking LaunchedEffect/DisposableEffect key patterns
- Validating state hoisting and recomposition optimization
- Enforcing official Jetpack Compose naming conventions
- Side effect isolation and lifecycle management

**Examples:**
```bash
@compose-guardian "Review MediaHexVisualization for proper Compose patterns"
@compose-guardian "Check LaunchedEffect keys in this ViewModel integration"
@compose-guardian "Validate state hoisting in this UI component"
```

#### 2. **Coroutines Concurrency Auditor** (`@coroutines-auditor`)
**When to Use:**
- Any code using coroutines, Flow, or threading
- Reviewing ViewModels for scope management
- Checking data repositories for race conditions
- Validating exception handling in coroutines
- Ensuring proper dispatcher injection

**Examples:**
```bash
@coroutines-auditor "Review StreamingAtlasManager for thread safety"
@coroutines-auditor "Check this Repository for proper coroutine scope"
@coroutines-auditor "Validate Flow collection patterns in ViewModel"
```

#### 3. **Android Graphics Performance Engineer** (`@graphics-performance`)
**When to Use:**
- Any atlas system or graphics performance work
- Bitmap memory management and recycling
- Canvas operations and hardware acceleration
- Performance optimization targeting 300ms goals
- Graphics-related benchmarking and profiling

**Examples:**
```bash
@graphics-performance "Optimize PhotoLODProcessor for 300ms target"
@graphics-performance "Review Canvas operations in MediaRenderer"
@graphics-performance "Analyze bitmap memory usage in TexturePacker"
```

#### 4. **Architecture & Workflow Quality Guardian** (`@architecture-workflow`)
**When to Use:**
- Before committing any changes (workflow enforcement)
- Reviewing new features for Clean Architecture compliance
- Checking ktlint violations (especially parameter comments)
- Validating Hilt dependency injection patterns
- Ensuring proper build→format→lint workflow

**Examples:**
```bash
@architecture-workflow "Check build workflow compliance before commit"
@architecture-workflow "Verify Clean Architecture in new feature"
@architecture-workflow "Review Hilt modules for proper injection"
```

#### 5. **Functional Programming Purist** (`@functional-purist`)
**When to Use:**
- Reviewing domain logic for functional purity
- Eliminating unnecessary classes in favor of functions
- Checking @Composables for internal state management
- Converting imperative loops to functional operations
- Simplifying OOP hierarchies

**Examples:**
```bash
@functional-purist "Review this class - can it be simplified to functions?"
@functional-purist "Check domain logic for pure function compliance"
@functional-purist "Eliminate stateful patterns in this Composable"
```

### Common Usage Workflows

#### **New Feature Development**
```bash
# 1. Design phase - ensure functional approach
@functional-purist "Review domain logic design for purity"

# 2. Implementation phase - check architecture
@architecture-workflow "Verify Clean Architecture compliance"

# 3. UI implementation - check Compose patterns  
@compose-guardian "Review Composables for API compliance"

# 4. Concurrent operations - check thread safety
@coroutines-auditor "Review async operations for safety"
```

#### **Performance Optimization**
```bash
# 1. Identify bottlenecks
@graphics-performance "Analyze performance bottlenecks in atlas system"

# 2. Check concurrent access patterns
@coroutines-auditor "Review threading in performance-critical code"

# 3. Verify functional approach
@functional-purist "Ensure pure functions in performance code"
```

#### **Pre-Commit Review**
```bash
# 1. Mandatory workflow check
@architecture-workflow "Run complete build workflow verification"

# 2. Final Compose review
@compose-guardian "Final review of Compose patterns"

# 3. Functional compliance check
@functional-purist "Final purity and simplicity review"
```

### Agent Selection Guidelines

**Choose by Code Type:**
- **@Composable functions** → `@compose-guardian`
- **Coroutines/Flow/async** → `@coroutines-auditor` 
- **Graphics/Canvas/bitmap** → `@graphics-performance`
- **Classes/architecture** → `@architecture-workflow` or `@functional-purist`
- **Domain logic** → `@functional-purist`

**Choose by Task:**
- **Code review** → Specific domain expert
- **Performance** → `@graphics-performance` + `@coroutines-auditor`
- **Architecture** → `@architecture-workflow`
- **Simplification** → `@functional-purist`
- **Pre-commit** → `@architecture-workflow`

---

## Legacy Documentation Notice

This file contains the project's build, lint, test commands, and code style guidelines.
These instructions are now located in CLAUDE.md which serves as the authoritative source for all project documentation.

See CLAUDE.md for complete information on:
- Build Commands
- Lint Commands  
- Test Commands
- Running Single Tests
- Code Style Guidelines

## Quick Development Workflow

**MANDATORY**: After implementing any feature or making code changes, always:
1. **Build the project first** to verify it compiles: `./gradlew -q assembleDebug`
2. **If build fails**: **STOP and ASK THE USER** if they want you to fix the build errors before proceeding
3. **If build succeeds**: Format code to ensure consistent style: `./gradlew ktlintFormat`
4. **Check code style** for any remaining issues: `./gradlew ktlintCheck`
5. **Check for lint issues** using: `./gradlew -q lint`
6. **If ktlint or lint issues are found**: **ASK THE USER** if they want you to fix the issues before proceeding
7. **Ask the user** if they want to launch the application to test the changes: `./gradlew -q installDebug && adb shell am start -n dev.serhiiyaremych.lumina/.MainActivity`

**CRITICAL**: Never proceed if build fails. Build success is mandatory before any formatting or style checks.

## Code Formatting with ktlint

**Quick Commands for Development:**
```bash
# Daily development (fast CLI)
ktlint "**/*.kt"              # Check formatting
ktlint -F "**/*.kt"           # Auto-fix formatting

# Team/CI consistency (Gradle)
./gradlew ktlintCheck          # Check all modules
./gradlew ktlintFormat         # Auto-fix all modules

# Specific files while coding
ktlint "app/src/main/java/MyFile.kt"
ktlint -F "app/src/main/java/MyFile.kt"
```

**Configuration:**
- **180 character line length** for modern wide screens
- **PascalCase for @Composable functions** (via `ktlint_function_naming_ignore_when_annotated_with = Composable`)
- **Wildcard imports allowed** for cleaner code organization
- **Import ordering disabled** for flexible import organization
- **Android Studio code style** with experimental rules enabled

**⚠️ CRITICAL ktlint Rule - Parameter Comments:**
```kotlin
// ❌ WRONG - Inline comments after parameters
fun example(
    param1: String = "value", // This causes ktlint error
    param2: Int = 42 // This also causes error
)

// ✅ CORRECT - Comments above parameters
fun example(
    param1: String = "value", // This is OK (not last param)
    // This is the correct way
    param2: Int = 42
)
```
**This is the most common ktlint violation - always check parameter comments!**

**Recommended Workflow:**
- **While coding**: Use fast CLI `ktlint -F "**/*.kt"`
- **Before committing**: Use consistent Gradle `./gradlew ktlintFormat && ./gradlew ktlintCheck`
- **In CI/CD**: Use `./gradlew ktlintCheck` to enforce standards

## Issue Detection Commands (In Order)

**After every code modification, run these commands in this exact sequence:**
```bash
# 1. FIRST: Verify project builds (syntax check)
./gradlew -q assembleDebug

# 2. ONLY if build succeeds: Format and check code style
./gradlew ktlintFormat
./gradlew ktlintCheck

# 3. Check for lint issues
./gradlew -q lint
```

**If any command fails or reports issues:**
1. **STOP immediately**
2. **Report the specific error/warning to the user**
3. **ASK THE USER** whether they want you to fix the issue
4. **DO NOT proceed** until the user gives explicit permission to fix or ignore the issue

**Build errors must be fixed before any formatting - syntax correctness comes first.**

## Repository Structure Exploration

The repository structure can be explored using the aider tool with the following command:

```bash
aider --map-tokens 1024 --show-repo-map
```

This command provides a detailed map of the repository layout, showing:
- High-level directory structure
- File organization patterns
- Project architecture overview
- Key components and their relationships

The N parameter (1024 in this example) sets how many tokens are included in the map. Higher values provide more detail but use more context:
- Lower values (e.g., 512): Quick overview of main directories and file types
- Medium values (e.g., 1024–2048): Detailed view with more specific directory structure
- Higher values (e.g., 2048–4096): Comprehensive detail including nested directories, file extensions, and project organization

Adjust based on how much structural information you want to see for your current task.