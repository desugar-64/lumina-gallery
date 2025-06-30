To get started with Navigation 3, add the library to your project along with any
supporting libraries. Use the table below to decide which libraries to add.

## Artifacts

| **Name** | **What it does** | **Artifact** |
| --- | --- | --- |
| Navigation 3 runtime library | Core Navigation 3 API. Includes `NavEntry, EntryProvider` and the associated DSL. | androidx.navigation3:navigation3-runtime |
| Navigation 3 UI library | Provides classes to display content, including `NavDisplay` and `Scene`. | androidx.navigation3:navigation3-ui |
| ViewModel Lifecycle for Navigation 3 | Allows ViewModels to be scoped to entries in the back stack. | androidx.lifecycle:lifecycle-viewmodel-navigation3 |

## Project setup

To add the Navigation 3 library to your existing project, add the following to
your `libs.versions.toml`:

[versions]
nav3Core = "1.0.0-alpha01"
lifecycleViewmodelNav3 = "1.0.0-alpha01"
kotlinSerialization = "2.1.21"
kotlinxSerializationCore = "1.8.1"
material3AdaptiveNav3 = "1.0.0-SNAPSHOT"

[libraries]
# Core Navigation 3 libraries
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }

# Optional add-on libraries
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinxSerializationCore" }

# Note: The Material3 adaptive layouts library for Navigation 3 is currently
# only available in snapshot builds. Follow the instructions at androidx.dev to
# add the snapshot builds repository to your project.
androidx-material3-adaptive-navigation3 = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation3", version.ref = "material3AdaptiveNav3" }

[plugins]
# Optional plugins
jetbrains-kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlinSerialization"}

Also, update your compile SDK to 36 or above:

[versions]
compileSdk = "36"

Add the following to your **app** build file `app/build.gradle.kts`:

plugins {
...
// Optional, provides the @Serialize annotation for autogeneration of Serializers.
alias(libs.plugins.jetbrains.kotlin.serialization)
}

dependencies {
...
implementation(libs.androidx.navigation3.ui)
implementation(libs.androidx.navigation3.runtime)
implementation(libs.androidx.lifecycle.viewmodel.navigation3)
implementation(libs.androidx.material3.adaptive.navigation3)
implementation(libs.kotlinx.serialization.core)
}

---

