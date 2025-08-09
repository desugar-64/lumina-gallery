# ktlint Configuration Template

This template provides a comprehensive ktlint setup for Android Kotlin projects with Jetpack Compose.

## Files Created/Modified

### 1. `.editorconfig` (Project Root)
```editorconfig
# EditorConfig is awesome: https://EditorConfig.org

# Top-most EditorConfig file
root = true

[*.{kt,kts}]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

# Ktlint Standard Rules
# https://pinterest.github.io/ktlint/latest/rules/standard/
ktlint_standard_chain-wrapping = enabled
ktlint_standard_comment-spacing = enabled
ktlint_standard_filename = enabled
ktlint_standard_final-newline = enabled
ktlint_standard_function-signature = enabled
ktlint_standard_if-else-wrapping = enabled
ktlint_standard_import-ordering = disabled
ktlint_standard_indent = enabled
ktlint_standard_max-line-length = 180
ktlint_standard_modifier-order = enabled
ktlint_standard_no-blank-line-before-rbrace = enabled
ktlint_standard_no-consecutive-blank-lines = enabled
ktlint_standard_no-empty-class-bodies = enabled
ktlint_standard_no-line-break-after-else = enabled
ktlint_standard_no-line-break-before-assignment = enabled
ktlint_standard_no-multi-spaces = enabled
ktlint_standard_no-semi = enabled
ktlint_standard_no-trailing-spaces = enabled
ktlint_standard_no-unit-return = enabled
ktlint_standard_no-used-imports = enabled
ktlint_standard_no-wildcard-imports = disabled # Often desired for cleaner imports
ktlint_standard_package-name = enabled
ktlint_standard_parameter-list-wrapping = enabled
ktlint_standard_string-template = enabled
ktlint_standard_trailing-comma-on-call-site = enabled # Recommended for cleaner diffs
ktlint_standard_trailing-comma-on-declaration-site = enabled # Recommended for cleaner diffs
ktlint_standard_wrapping = enabled

# Ktlint Experimental Rules
# https://pinterest.github.io/ktlint/latest/rules/experimental/
ktlint_experimental = true
ktlint_experimental_argument-list-wrapping = disabled # Often too strict
ktlint_experimental_blank-line-before-declaration = enabled
ktlint_experimental_discouraged-comment-location = enabled
ktlint_experimental_enum-entry-name-case = enabled
ktlint_experimental_function-naming = enabled
# Allows PascalCase for @Composable functions
ktlint_function_naming_ignore_when_annotated_with = Composable
ktlint_experimental_multiline-if-else = enabled
ktlint_experimental_no-empty-first-line-in-method-block = enabled
ktlint_experimental_package-name = enabled
ktlint_experimental_spacing-around-angle-brackets = enabled
ktlint_experimental_spacing-around-double-colon = enabled
ktlint_experimental_spacing-between-declarations-with-annotations = enabled
ktlint_experimental_spacing-between-declarations-with-comments = enabled
ktlint_experimental_type-argument-list-spacing = enabled
ktlint_experimental_unary-op-spacing = enabled

# Ktlint Code Style
ktlint_code_style = android_studio

# Compose-specific adjustments
ktlint_standard_property-naming = disabled # Allows object Colors, etc.

# Basic editor config
max_line_length = 180
indent_size = 4
continuation_indent_size = 4
```

### 2. `gradle/libs.versions.toml` (Add ktlint version)
```toml
[versions]
# ... existing versions ...
ktlintGradle = "12.1.1"

[libraries]
# ... existing libraries ...
ktlint-gradle = { group = "org.jlleitschuh.gradle", name = "ktlint-gradle", version.ref = "ktlintGradle" }

[plugins]
# ... existing plugins ...
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlintGradle" }
```

### 3. Root `build.gradle.kts` (Apply to all subprojects)
```kotlin
plugins {
    // ... existing plugins ...
    alias(libs.plugins.ktlint) apply false
}

// Apply ktlint to all subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.7.1")
        debug.set(false)
        verbose.set(false)
        android.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        
        // Use .editorconfig for configuration
        baseline.set(file("ktlint-baseline.xml"))
        
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }
}
```

## Usage Commands

### Basic ktlint Commands
```bash
# Check all Kotlin files
ktlint

# Check specific files/directories  
ktlint "src/**/*.kt"
ktlint app/src/main/java/

# Auto-fix formatting issues
ktlint -F "**/*.kt"

# Generate baseline (ignore existing issues)
ktlint --baseline=ktlint-baseline.xml

# Check with Android rules
ktlint --android
```

### Gradle Integration Commands
```bash
# Check code style
./gradlew ktlintCheck

# Auto-fix formatting 
./gradlew ktlintFormat

# Check specific module
./gradlew :app:ktlintCheck
./gradlew :app:ktlintFormat

# Generate baseline file
./gradlew ktlintCheck --continue > /dev/null || true
./gradlew ktlintGenerateBaseline
```

## IDE Integration

### Android Studio/IntelliJ
1. Install ktlint plugin from marketplace
2. Configure ktlint path in settings
3. Enable "Run ktlint on save"

### VS Code
1. Install "ktlint" extension
2. Add to settings.json:
```json
{
    "ktlint.enableOnSave": true,
    "ktlint.lintType": "file"
}
```

## CI/CD Integration

### GitHub Actions
```yaml
- name: Run ktlint
  run: ./gradlew ktlintCheck
```

### GitLab CI
```yaml
ktlint:
  script:
    - ./gradlew ktlintCheck
```

## Key Features for Android/Compose Projects

1. **Compose Support**: Allows PascalCase for `@Composable` functions
2. **Android Studio Style**: Uses `android_studio` code style by default
3. **Trailing Commas**: Enabled for cleaner git diffs
4. **Wildcard Imports**: Disabled by default (can be enabled if preferred)
5. **180 Character Line Length**: Generous for modern ultrawide screens and complex Compose code
6. **Experimental Rules**: Enabled for latest Kotlin features

## Customization Tips

1. **Disable specific rules**: Add `ktlint_standard_rule-name = disabled`
2. **Project-specific baseline**: Use `ktlint-baseline.xml` to ignore existing issues
3. **Module-specific config**: Override settings in module `build.gradle.kts`
4. **Team preferences**: Adjust rules based on team coding standards

## Troubleshooting

1. **Import order conflicts**: Check `ktlint_standard_import-ordering` setting
2. **Compose function naming**: Ensure `ktlint_function_naming_ignore_when_annotated_with = Composable`
3. **Line length issues**: Adjust `max_line_length` in `.editorconfig` (currently 180 chars)
4. **Build failures**: Use `./gradlew ktlintFormat` to auto-fix most issues