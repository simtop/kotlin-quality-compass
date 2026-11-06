package com.kotlinqualitycompass.cli

import com.kotlinqualitycompass.core.CoverageMeasurement
import com.kotlinqualitycompass.core.CoverageStatus
import com.kotlinqualitycompass.core.CrapScore
import com.kotlinqualitycompass.core.FunctionMetric
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/** Runs the analyzer and keeps the report stream separate from diagnostics. */
fun main(rawArgs: Array<String>) {
    try {
        val arguments = parseArguments(rawArgs)
        val project = Path.of(arguments.project).absolute().normalize()
        require(project.exists()) { "Project does not exist: $project" }
        val scope = QualityScope.load(project, arguments)
        val sources = if (arguments.all) {
            val discovery = ProjectDiscovery.discoverWithReport(project, scope)
            printExcludedSources(project, discovery.excludedSources, arguments.explainScope)
            discovery.files
        } else {
            val source = project.resolve(requireNotNull(arguments.source)).normalize()
            require(source.startsWith(project)) { "Source must be inside --project" }
            require(source.exists() && source.isRegularFile()) { "Source does not exist: $source" }
            require(source.fileName.toString().endsWith(".kt")) { "Source must be a Kotlin file: $source" }
            val exclusion = ProjectDiscovery.explicitSourceExclusion(project, source, scope)
            require(exclusion == null) { "Source is excluded from quality scope: $exclusion" }
            listOf(source)
        }
        require(sources.isNotEmpty()) { "No production Kotlin source files found" }
        if (arguments.all) {
            System.err.println("Discovered ${sources.size} production Kotlin source files")
        }

        if (!arguments.scanOnly && arguments.testTask != null) {
            val result = GradleRunner().run(project, arguments.testTask, arguments.timeoutSeconds)
            require(result.exitCode == 0) {
                "Baseline test task failed (exit=${result.exitCode})\n${result.output}"
            }
        }
        if (!arguments.scanOnly && !arguments.reuseCoverage) {
            val task = arguments.coverageTask ?: if (arguments.all) ":jacocoRootReport" else null
            if (task != null) {
                val result = GradleRunner().run(project, task, arguments.timeoutSeconds)
                require(result.exitCode == 0) {
                    "Coverage task failed (exit=${result.exitCode})\n${result.output}"
                }
            }
        }

        val coverageReport = arguments.coverageReport ?: if (arguments.all) {
            "build/reports/jacoco/jacocoRootReport/jacocoRootReport.xml"
        } else {
            null
        }
        val coverage = if (arguments.scanOnly) {
            null
        } else {
            coverageReport?.let {
                val report = resolveProjectPath(project, it)
                require(report.exists() && report.isRegularFile()) {
                    "JaCoCo report does not exist: $report"
                }
                JaCoCoCoverage.read(report)
            }
        }
        val functions = functionsFor(sources, scope)
        if (functions.isEmpty()) {
            writeMessage(arguments, project, "No concrete Kotlin functions found.\n")
            return
        }
        val metrics = functions.map { function ->
            val measurement = if (function.isInline) {
                CoverageMeasurement(
                    null,
                    CoverageStatus.UNAVAILABLE,
                    "Inline function coverage cannot be attributed reliably after Kotlin inlining",
                )
            } else if (scope.isCoverageUnavailable(project, Path.of(function.sourcePath))) {
                CoverageMeasurement(
                    null,
                    CoverageStatus.UNAVAILABLE,
                    "JVM coverage unavailable for module configured by coverageUnavailableModule",
                )
            } else {
                coverage?.measure(function)
            } ?: CoverageMeasurement(
                null,
                CoverageStatus.UNAVAILABLE,
                "No JaCoCo report supplied",
            )
            FunctionMetric(
                function = function.copy(sourcePath = project.relativize(Path.of(function.sourcePath)).toString()),
                coverage = measurement,
                score = CrapScore.calculate(
                    function.cyclomaticComplexity,
                    measurement.percentage,
                ),
            )
        }.sortedWith(
            compareByDescending<FunctionMetric> { it.score ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.function.sourcePath }
                .thenBy { it.function.startLine },
        )

        writeReport(arguments, project, metrics)
        val worst = metrics.mapNotNull { it.score }.maxOrNull()
        if (worst != null && worst > arguments.threshold) kotlin.system.exitProcess(2)
    } catch (error: IllegalArgumentException) {
        System.err.println("Usage error: ${error.message}")
        kotlin.system.exitProcess(1)
    } catch (error: Exception) {
        System.err.println("Analysis failed: ${error.message}")
        kotlin.system.exitProcess(1)
    }
}

private fun functionsFor(sources: List<Path>, scope: QualityScope) =
    sources.flatMap { source -> KotlinSourceAnalyzer().discover(source, scope.includePreviews) }

private fun writeMessage(arguments: Arguments, project: Path, message: String) {
    arguments.outputFile?.let { output ->
        val path = resolveProjectPath(project, output)
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path).use { it.write(message) }
    } ?: print(message)
}

private fun writeReport(
    arguments: Arguments,
    project: Path,
    metrics: List<FunctionMetric>,
) {
    arguments.outputFile?.let { output ->
        val path = resolveProjectPath(project, output)
        path.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(path).use { writer ->
            ReportFormatter.write(arguments.format, metrics, arguments.threshold, writer)
        }
    } ?: ReportFormatter.write(arguments.format, metrics, arguments.threshold, System.out.writer())
}

private fun resolveProjectPath(project: Path, path: String): Path {
    val candidate = Path.of(path)
    return (if (candidate.isAbsolute) candidate else project.resolve(candidate)).normalize()
}

private fun printExcludedSources(project: Path, excluded: List<ExcludedSource>, explain: Boolean) {
    if (excluded.isEmpty()) return
    val counts = excluded.groupingBy(ExcludedSource::reason).eachCount()
    System.err.println(
        "Excluded ${excluded.size} source files from quality scope: " +
            counts.entries.joinToString { "${it.value} ${it.key}" },
    )
    if (explain) {
        excluded.forEach { System.err.println("  ${project.relativize(it.path)} — ${it.reason}") }
    } else {
        System.err.println("Use --explain-scope to list excluded files")
    }
}
