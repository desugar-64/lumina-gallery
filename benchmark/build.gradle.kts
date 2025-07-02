plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.serhiiyaremych.lumina.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
//        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "TraceSectionMetric,MemoryUsageMetric"
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It"s signed with a debug key
        // for easy local/CI testing.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(project(path = ":common"))
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}

// Optimization Tracking Tasks
tasks.register("trackOptimization") {
    group = "benchmarking"
    description = "Track optimization progress with iterative benchmark collection"
    dependsOn("connectedBenchmarkAndroidTest")
    
    doLast {
        val benchmarkConfig = findLatestBenchmarkResult()
        val optimizationName = project.findProperty("optimization.name")?.toString()
            ?: throw GradleException("❌ Please specify optimization name: -Poptimization.name='bitmap_pooling'")
        
        println("📈 Tracking optimization progress: $optimizationName")
        
        executeOptimizationTracker("collect", optimizationName, benchmarkConfig.benchmarkFile)
        executeOptimizationTracker("compare", optimizationName)
        
        println("✅ Optimization tracking complete!")
    }
}

tasks.register("compareOptimization") {
    group = "benchmarking"
    description = "Compare all runs within a specific optimization"
    
    doLast {
        val optimizationName = project.findProperty("optimization.name")?.toString()
            ?: throw GradleException("❌ Please specify optimization name: -Poptimization.name='bitmap_pooling'")
        val showAllMetrics = project.hasProperty("all.metrics")
        
        println("📊 Comparing optimization runs: $optimizationName")
        println("=" + "=".repeat(59))
        
        executeOptimizationTracker("compare", optimizationName, showAllMetrics = showAllMetrics)
    }
}

tasks.register("listOptimizationRuns") {
    group = "benchmarking" 
    description = "List all runs for a specific optimization"
    
    doLast {
        val optimizationName = project.findProperty("optimization.name")?.toString()
            ?: throw GradleException("❌ Please specify optimization name: -Poptimization.name='bitmap_pooling'")
        
        executeOptimizationTracker("list", optimizationName)
    }
}

// Helper functions for atlas benchmarking
fun findLatestBenchmarkResult(): BenchmarkConfig {
    val outputDir = File(buildDir, "outputs/connected_android_test_additional_output/benchmark/connected")
    
    if (!outputDir.exists()) {
        throw GradleException("❌ Benchmark output directory not found: ${outputDir.absolutePath}")
    }
    
    val benchmarkFiles = outputDir.walkTopDown()
        .filter { 
            it.name.contains("benchmarkData") && 
            it.extension == "json"
        }
        .sortedByDescending { it.lastModified() }
    
    if (!benchmarkFiles.iterator().hasNext()) {
        throw GradleException("❌ No atlas benchmark results found in ${outputDir.absolutePath}")
    }
    
    val latestResult = benchmarkFiles.first()
    println("📊 Found latest benchmark result: ${latestResult.name}")
    
    return BenchmarkConfig(latestResult.absolutePath, latestResult.name)
}

// Optimization tracking functions
fun executeOptimizationTracker(command: String, optimizationName: String, benchmarkFile: String? = null, 
                              showAllMetrics: Boolean = false) {
    val scriptsDir = File(project.rootProject.projectDir, "scripts")
    val trackerScript = File(scriptsDir, "optimization_tracker.py")
    
    if (!trackerScript.exists()) {
        throw GradleException("❌ Optimization tracker script not found: ${trackerScript.absolutePath}")
    }
    
    val cmdArgs = mutableListOf("python3", trackerScript.absolutePath, command, optimizationName)
    
    // Add command-specific arguments
    when (command) {
        "collect" -> {
            if (benchmarkFile != null) {
                cmdArgs.add(benchmarkFile)
            }
        }
        "compare" -> {
            if (showAllMetrics) {
                cmdArgs.add("--all-metrics")
            }
        }
    }
    
    println("🔧 Executing: ${cmdArgs.joinToString(" ")}")
    
    exec {
        workingDir = project.rootProject.projectDir
        commandLine = cmdArgs
    }
}

fun printReportLocation() {
    val resultsDir = File(project.rootProject.projectDir, "benchmark_results")
    val reportFile = File(resultsDir, "atlas_performance_report.html")
    
    if (reportFile.exists()) {
        println("\n📊 Atlas Performance Report Generated!")
        println("🌐 View report: file://${reportFile.absolutePath}")
        println("📁 Timeline data: ${File(resultsDir, "atlas_timeline.json").absolutePath}")
    } else {
        println("\n📭 No report generated - timeline data saved to ${File(resultsDir, "atlas_timeline.json").absolutePath}")
    }
}

data class BenchmarkConfig(
    val benchmarkFile: String,
    val fileName: String
)
