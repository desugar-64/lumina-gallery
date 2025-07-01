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

// Atlas Benchmarking Tasks - Comprehensive Timeline Management
tasks.register("initAtlasBaseline") {
    group = "atlas-benchmarking"
    description = "Initialize atlas benchmarking with new baseline"
    dependsOn("connectedBenchmarkAndroidTest")
    
    doLast {
        val baselineConfig = findLatestBenchmarkResult()
        val baselineName = project.findProperty("baseline.name")?.toString() ?: "baseline"
        val allowDirty = project.hasProperty("allow.dirty")
        
        println("🎯 Initializing fresh atlas baseline: $baselineName")
        
        executePythonCollector("init", baselineConfig.benchmarkFile, baselineName, allowDirty)
        generateAtlasReport()
        
        println("✅ Atlas baseline initialization complete!")
        printReportLocation()
    }
}

tasks.register("updateAtlasBaseline") {
    group = "atlas-benchmarking"
    description = "Update baseline while preserving optimization timeline"
    dependsOn("connectedBenchmarkAndroidTest")
    
    doLast {
        val baselineConfig = findLatestBenchmarkResult()
        val baselineName = project.findProperty("baseline.name")?.toString() ?: "baseline_updated"
        val allowDirty = project.hasProperty("allow.dirty")
        
        println("🔄 Updating atlas baseline: $baselineName")
        
        executePythonCollector("collect", baselineConfig.benchmarkFile, baselineName, allowDirty, "update_baseline")
        generateAtlasReport()
        
        println("✅ Atlas baseline update complete!")
        printReportLocation()
    }
}

tasks.register("benchmarkAtlasOptimization") {
    group = "atlas-benchmarking"
    description = "Benchmark specific optimization and add to timeline"
    dependsOn("connectedBenchmarkAndroidTest")
    
    doLast {
        val optimizationConfig = findLatestBenchmarkResult()
        val optimizationName = project.findProperty("optimization.name")?.toString()
            ?: throw GradleException("❌ Please specify optimization name: -Poptimization.name='your_optimization'")
        val allowDirty = project.hasProperty("allow.dirty")
        
        println("📈 Tracking atlas optimization: $optimizationName")
        
        executePythonCollector("collect", optimizationConfig.benchmarkFile, optimizationName, allowDirty, "optimization")
        generateAtlasReport()
        
        println("✅ Atlas optimization tracking complete!")
        printReportLocation()
    }
}

tasks.register("cleanAtlasTimeline") {
    group = "atlas-benchmarking"
    description = "Remove all benchmark results and start fresh"
    
    doLast {
        val force = project.hasProperty("force")
        val noBackup = project.hasProperty("no.backup")
        
        println("🗑️  Cleaning all atlas timeline data...")
        
        executePythonCollector("clean-all", force = force, noBackup = noBackup)
        
        println("✅ Atlas timeline cleaned!")
        println("🎯 Ready for fresh baseline initialization with 'initAtlasBaseline'")
    }
}

tasks.register("listAtlasTimeline") {
    group = "atlas-benchmarking"
    description = "List all timeline entries with performance summary"
    
    doLast {
        println("📊 Atlas Timeline Entries:")
        println("=" * 60)
        
        executePythonCollector("list")
    }
}

tasks.register("cleanAtlasExperimental") {
    group = "atlas-benchmarking"
    description = "Remove experimental (-dirty) entries from timeline"
    
    doLast {
        val force = project.hasProperty("force")
        val noBackup = project.hasProperty("no.backup")
        
        println("🧪 Cleaning experimental atlas entries...")
        
        executePythonCollector("clean", force = force, noBackup = noBackup)
        
        println("✅ Experimental entries cleaned!")
    }
}

tasks.register("generateAtlasReport") {
    group = "atlas-benchmarking"
    description = "Generate HTML performance report from timeline data"
    
    doLast {
        generateAtlasReport()
        printReportLocation()
    }
}

// Helper functions for atlas benchmarking
fun findLatestBenchmarkResult(): BenchmarkConfig {
    val outputDir = File(buildDir, "outputs/connected_android_test_additional_output/benchmarkAndroidTest/connected")
    
    if (!outputDir.exists()) {
        throw GradleException("❌ Benchmark output directory not found: ${outputDir.absolutePath}")
    }
    
    val benchmarkFiles = outputDir.walkTopDown()
        .filter { 
            it.name.contains("AtlasPerformanceBenchmark") && 
            it.extension == "json" &&
            it.name.contains("atlasGenerationThroughZoomInteractions")
        }
        .sortedByDescending { it.lastModified() }
    
    if (benchmarkFiles.isEmpty()) {
        throw GradleException("❌ No atlas benchmark results found in ${outputDir.absolutePath}")
    }
    
    val latestResult = benchmarkFiles.first()
    println("📊 Found latest benchmark result: ${latestResult.name}")
    
    return BenchmarkConfig(latestResult.absolutePath, latestResult.name)
}

fun executePythonCollector(command: String, benchmarkFile: String? = null, name: String? = null, 
                          allowDirty: Boolean = false, mode: String? = null, force: Boolean = false, 
                          noBackup: Boolean = false) {
    val scriptsDir = File(project.rootProject.projectDir, "scripts")
    val collectorScript = File(scriptsDir, "atlas_benchmark_collector.py")
    
    if (!collectorScript.exists()) {
        throw GradleException("❌ Collector script not found: ${collectorScript.absolutePath}")
    }
    
    val cmdArgs = mutableListOf("python3", collectorScript.absolutePath, command)
    
    // Add command-specific arguments
    when (command) {
        "collect" -> {
            if (benchmarkFile != null && name != null) {
                cmdArgs.addAll(listOf(benchmarkFile, name))
                if (allowDirty) cmdArgs.add("--allow-dirty")
                if (mode != null) cmdArgs.addAll(listOf("--mode", mode))
            }
        }
        "init" -> {
            if (benchmarkFile != null) {
                cmdArgs.add(benchmarkFile)
                if (name != null) cmdArgs.addAll(listOf("--baseline-name", name))
                if (allowDirty) cmdArgs.add("--allow-dirty")
            }
        }
        "clean-all", "clean" -> {
            if (noBackup) cmdArgs.add("--no-backup")
            if (force) cmdArgs.add("--force")
        }
    }
    
    println("🔧 Executing: ${cmdArgs.joinToString(" ")}")
    
    exec {
        workingDir = project.rootProject.projectDir
        commandLine = cmdArgs
    }
}

fun generateAtlasReport() {
    val scriptsDir = File(project.rootProject.projectDir, "scripts")
    val chartScript = File(scriptsDir, "atlas_timeline_chart.py")
    
    if (chartScript.exists()) {
        println("📈 Generating atlas performance report...")
        exec {
            workingDir = project.rootProject.projectDir
            commandLine("python3", chartScript.absolutePath)
        }
    } else {
        println("⚠️  Chart generator not found: ${chartScript.absolutePath}")
        println("   Report generation skipped - only timeline data collected")
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
