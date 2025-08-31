// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    id("dev.serhiiyaremych.kotlin.lsp.workspace") version "1.0.0-SNAPSHOT"
}

kotlinLspWorkspace {
    // Optional configuration - most settings auto-detected
    workspaceFile.set(file("workspace.json"))  // default
    includeTestDependencies.set(false)         // default
    copyJarsToTemp.set(true)                   // default
    autoRegenerate.set(true)                   // auto-regenerate on dependency changes

    // VS Code configuration
    generateVSCodeConfig.set(true)             // generate VS Code config files
    vsCodeDirectory.set(file(".vscode"))       // default
    generateLaunchJson.set(false)              // disabled - launch configs don't work in VS Code
    generateTasksJson.set(true)                // generate tasks.json
    mainActivityName.set("MainActivity")       // default activity name
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
