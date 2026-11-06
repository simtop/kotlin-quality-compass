package com.kotlinqualitycompass.cli

import com.kotlinqualitycompass.core.CrapScore

data class Arguments(
    val project: String,
    val source: String?,
    val all: Boolean,
    val coverageReport: String?,
    val testTask: String?,
    val coverageTask: String?,
    val threshold: Double,
    val format: OutputFormat,
    val outputFile: String?,
    val scanOnly: Boolean,
    val reuseCoverage: Boolean,
    val configFile: String?,
    val includeSupport: Boolean,
    val includePreviews: Boolean,
    val excludePaths: List<String>,
    val excludeModules: List<String>,
    val coverageUnavailableModules: List<String>,
    val explainScope: Boolean,
    val timeoutSeconds: Long,
)

enum class OutputFormat {
    TEXT,
    JSON,
}

fun parseArguments(args: Array<String>): Arguments {
    if (args.contains("--help")) {
        printUsage()
        kotlin.system.exitProcess(0)
    }
    require(args.isNotEmpty()) { "Missing arguments. Use --help for usage." }

    val singleton = mutableSetOf<String>()
    val values = mutableMapOf<String, String>()
    val repeated = mutableMapOf<String, MutableList<String>>()
    val flags = setOf(
        "--all",
        "--scan",
        "--reuse-coverage",
        "--include-support",
        "--include-previews",
        "--explain-scope",
    )
    val valueOptions = setOf(
        "--project",
        "--source",
        "--coverage-report",
        "--test-task",
        "--coverage-task",
        "--threshold",
        "--format",
        "--output",
        "--config",
        "--exclude-path",
        "--exclude-module",
        "--coverage-unavailable-module",
        "--timeout-seconds",
    )
    val repeatable = setOf("--exclude-path", "--exclude-module", "--coverage-unavailable-module")

    fun requireValue(option: String, index: Int): String {
        require(index + 1 < args.size && !args[index + 1].startsWith("--")) {
            "$option requires a value"
        }
        return args[index + 1]
    }

    var index = 0
    while (index < args.size) {
        val option = args[index]
        when {
            option in flags -> {
                require(singleton.add(option)) { "$option may be specified only once" }
            }
            option in valueOptions -> {
                val value = requireValue(option, index)
                if (option in repeatable) {
                    repeated.getOrPut(option) { mutableListOf() }.add(value)
                } else {
                    require(singleton.add(option)) { "$option may be specified only once" }
                    values[option] = value
                }
                index++
            }
            else -> throw IllegalArgumentException("Unknown option or positional argument: $option")
        }
        index++
    }

    val all = "--all" in singleton
    val source = values["--source"]
    require(all || source != null) { "Provide --source FILE.kt or --all" }
    require(!all || source == null) { "--all cannot be combined with --source" }

    val project = values["--project"] ?: throw IllegalArgumentException("Missing --project")
    val threshold = values["--threshold"]?.let {
        it.toDoubleOrNull() ?: throw IllegalArgumentException("--threshold must be a number")
    } ?: CrapScore.DEFAULT_THRESHOLD
    require(threshold.isFinite() && threshold >= 0.0) { "--threshold must be a finite number >= 0" }
    val timeoutSeconds = values["--timeout-seconds"]?.let {
        it.toLongOrNull() ?: throw IllegalArgumentException("--timeout-seconds must be an integer")
    } ?: 900L
    require(timeoutSeconds > 0) { "--timeout-seconds must be greater than zero" }
    val format = when (values["--format"]?.lowercase()) {
        null, "text" -> OutputFormat.TEXT
        "json" -> OutputFormat.JSON
        else -> throw IllegalArgumentException("--format must be text or json")
    }
    val scanOnly = "--scan" in singleton
    val reuseCoverage = "--reuse-coverage" in singleton
    require(!reuseCoverage || all || scanOnly || values["--coverage-report"] != null) {
        "--reuse-coverage with --source requires --coverage-report"
    }

    return Arguments(
        project = project,
        source = source,
        all = all,
        coverageReport = values["--coverage-report"],
        testTask = values["--test-task"],
        coverageTask = values["--coverage-task"],
        threshold = threshold,
        format = format,
        outputFile = values["--output"],
        scanOnly = scanOnly,
        reuseCoverage = reuseCoverage,
        configFile = values["--config"],
        includeSupport = "--include-support" in singleton,
        includePreviews = "--include-previews" in singleton,
        excludePaths = repeated["--exclude-path"].orEmpty(),
        excludeModules = repeated["--exclude-module"].orEmpty(),
        coverageUnavailableModules = repeated["--coverage-unavailable-module"].orEmpty(),
        explainScope = "--explain-scope" in singleton,
        timeoutSeconds = timeoutSeconds,
    )
}

private fun printUsage() {
    println(
        """
        Kotlin Quality Compass — Kotlin/KMP complexity and coverage analyzer

        Required:
          --project DIR             project root
          --source FILE.kt          one production Kotlin source file
          --all                     all production Kotlin source sets in the project

        Optional:
          --coverage-report FILE    JaCoCo XML to read
          --coverage-task TASK      Gradle task that produces the XML
          --test-task TASK          Gradle test task used as a baseline
          --threshold N             fail when the highest score is greater than N (default: 8.0)
          --format text|json        report format (default: text)
          --output FILE             write the report to FILE instead of stdout
          --config FILE             scope properties (default: .quality-compass.properties)
          --include-support         include fakes, fixtures, processors, and test utilities
          --include-previews        include Compose preview functions and preview providers
          --exclude-path GLOB       exclude a source path; repeatable
          --exclude-module GLOB     exclude a module path; repeatable
          --coverage-unavailable-module GLOB
                                    keep module functions but do not score JVM coverage; repeatable
          --explain-scope            print every excluded source and its reason
          --scan                    parse and report functions without running Gradle
          --reuse-coverage          use an existing JaCoCo report instead of running coverage
          --timeout-seconds N       Gradle coverage/test timeout (default: 900)
        """.trimIndent(),
    )
}
