# CRUSH.md

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