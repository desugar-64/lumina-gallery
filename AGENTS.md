# AGENTS.md

# File Ops System Prompt (Kotlin/Android, Bash-first)

You work on Kotlin/Android repos. Conserve tokens when reading/modifying files.

## Always check size first
```bash
wc -l <file>

Policy:
<100 lines: safe to read fully
100–699 lines: read targeted slices only
≥700 lines: NEVER read fully. Use structure/search + partial reads

## Repository & structure overview (no big reads)
# Fast counts & hotspots
tokei .                         # LOC per language/dirs
scc --by-file --no-complexity . # LOC per file (find big Kotlin files)

# Kotlin-only file lists
```bash
fd -e kt -e kts                 # list Kotlin sources
find . -name "*.kt" -o -name "*.kts" | head -50  # fallback if no fd

# ast-grep: structural search (CORRECTED SYNTAX)
ast-grep -p 'class $NAME { $$$ }' -l kotlin <dir>
ast-grep -p 'fun $NAME($$$)' -l kotlin <dir>
ast-grep -p 'suspend fun $NAME($$$)' -l kotlin <dir>
ast-grep -p '@Composable fun $NAME($$$)' -l kotlin <dir>
ast-grep -p 'data class $NAME($$$)' -l kotlin <dir>
ast-grep -p 'sealed class $NAME' -l kotlin <dir>

# Get stats without dumping matches
ast-grep -p 'fun $NAME($$$)' -l kotlin <dir> --stats

# File tree (Kotlin only) if needed
tree -I 'build|.git|.gradle|.idea' -P '*.kt' -P '*.kts' -L 3
```

## Targeted searching (before reading)
# Ripgrep: Kotlin-only, with line numbers and context
```bash
rg -n -t kotlin 'pattern' <dir>
rg -n -t kotlin -C5 'class\s+\w+ViewModel' <dir>
rg -n -t kotlin -g '!**/build/**' -g '!**/generated/**' 'suspend fun'

# List files only (no content)
rg -l -t kotlin 'ViewModel' <dir>

# Jump points inside a file
rg -n '^package ' <file.kt>
rg -n '^import ' <file.kt> | head -20
rg -n '^(class|interface|object|enum) ' <file.kt>
rg -n '^\s*(fun|val|var) ' <file.kt>

# Android specific patterns
rg -n '@(Inject|Provides|Module|Component)' <file.kt>  # Dagger/Hilt
rg -n 'findViewById|viewBinding|dataBinding' <file.kt>  # View binding
```

## Partial reading (slices only)
```bash
# With bat (if available - shows syntax highlighting)
bat -n -r 1:80 <file.kt>              # head slice
bat -n -r 400:460 <file.kt>           # specific window

# POSIX fallbacks
head -n 80 <file.kt>
tail -n 120 <file.kt>
sed -n '100,200p' <file.kt>
awk 'NR>=145 && NR<=165' <file.kt>

# Read around line N with context
awk -v n=150 'NR >= n-20 && NR <= n+20' <file.kt>
sed -n "$((N-20)),$((N+20))p" <file.kt>  # if N is set

# Read specific function/class (between patterns)
sed -n '/^class UserViewModel/,/^class\|^interface\|^object\|^enum\|^$/p' <file.kt>
sed -n '/fun onCreate/,/^    }/p' <file.kt>
```

## Edit with minimal churn
```bash
# Single-line or range substitutions
sed -i '42s/.*/val limit = 100/' <file.kt>
sed -i '100,140s/MutableLiveData/StateFlow/g' <file.kt>

# Append (cheapest)
printf '\n@Deprecated("Use Foo")\nclass Bar\n' >> <file.kt>

# Insert at specific line
sed -i '100i\    private val logger = LoggerFactory.getLogger()' <file.kt>

# Safe patch workflow
cp <file.kt> <file.kt.bak>
# make edits to file.kt
diff -u <file.kt.bak> <file.kt> > changes.patch
# review patch, then apply if needed

# Git-based (if in repo)
git diff --no-index <old.kt> <new.kt>
```

## Android & Gradle specifics
```bash
# Gradle deps block only
sed -n '/dependencies\s*{/,/^}/p' build.gradle.kts
grep "implementation\|api\|kapt\|ksp" build.gradle.kts

# Android manifest
grep "android:name=" AndroidManifest.xml

# Resources
grep -o '@string/[^"]*' res/layout/*.xml | sort -u
grep "name=" res/values/strings.xml | head -20
```

## Decision flow (MUST follow)
wc -l <file>
1. <100 lines → may read fully (but prefer slices if possible)
2. 100–699 → use rg/ast-grep to locate, then bat/sed exact ranges
3. ≥700 → use ast-grep/rg to pinpoint; only read windows around matches
4. Edits → limit to smallest line ranges; prefer sed/patch

## Common pitfalls to avoid
1. NEVER use cat on files >100 line
2. NEVER use grep -r without file type filters
3. IGNORE build/, .gradle/, generated/ directories
4. CHECK file existence before operations: [[ -f <file> ]] && wc -l <file>

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