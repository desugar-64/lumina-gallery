**GRADLE BUILD RULE:** Always use `./gradlew`. Use `-q` for successful builds, `--stacktrace` for failures.

**CORE COMMANDS:**
- `./gradlew clean build` - Full clean rebuild
- `./gradlew -q build` - Complete build (quiet)
- `./gradlew -q assembleDebug` - Debug build only
- `./gradlew -q compileKotlin` - Compile only

**ON FAILURE:** Add `--stacktrace` to any command for error details
**DEPENDENCIES:** `./gradlew dependencies [--configuration name]`

**STRATEGY:** Success = quiet mode (-q), Failure = verbose (--stacktrace/--info)