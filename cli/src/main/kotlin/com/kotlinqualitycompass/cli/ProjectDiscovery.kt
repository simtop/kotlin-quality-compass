package com.kotlinqualitycompass.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/** Discovers production Kotlin source sets without pulling tests or generated/build code in. */
object ProjectDiscovery {
    private val excludedSegments = setOf(
        ".git",
        ".gradle",
        ".claude",
        ".idea",
        ".agents",
        "build",
        "build-logic",
        "rod",
        "gradle-user-home",
    )

    private val generatedSegments = setOf("generated", "generated-src", "generated-sources")
    private val supportSegments = setOf(
        "fakes",
        "fixtures",
        "test-utils",
        "test_utils",
        "testing-utils",
        "testing_utils",
        "testing-utils-android",
        "testing_utils_android",
        "snapshot-testing",
        "snapshot_testing",
        "catalog-processor",
        "snapshot-processor",
        "processor",
        "processors",
    )
    private val supportFilePrefixes = setOf("Fake", "Fakes", "Fixture", "Fixtures", "Mock", "Mocks")

    fun productionKotlinFiles(project: Path, scope: QualityScope = QualityScope()): List<Path> =
        discoverWithReport(project, scope).files

    fun modulePath(project: Path, file: Path): String =
        project.relativize(moduleRoot(project, file)).joinToString("/")

    fun explicitSourceExclusion(project: Path, file: Path, scope: QualityScope): String? {
        require(isProductionSource(project, file)) {
            "Source must be a production Kotlin file under src/main or a *Main source set"
        }
        return exclusion(project, file, scope)
    }

    fun discoverWithReport(project: Path, scope: QualityScope = QualityScope()): DiscoveryResult {
        val files = Files.walk(project).use { paths ->
            paths
                .filter { it.isRegularFile() }
                .filter { it.toString().endsWith(".kt") }
                .filter { isProductionSource(project, it) }
                .sorted()
                .toList()
        }
        val exclusions = files.mapNotNull { file ->
            exclusion(project, file, scope)?.let { ExcludedSource(file, it) }
        }
        return DiscoveryResult(
            files = files.filter { exclusion(project, it, scope) == null },
            excludedSources = exclusions.sortedBy { project.relativize(it.path).toString() },
        )
    }

    private fun isProductionSource(project: Path, file: Path): Boolean {
        val parts = project.relativize(file).map(Path::toString)
        if (parts.any { it in excludedSegments }) return false
        if (file.fileName.toString().endsWith("Test.kt")) return false
        val sourceIndex = parts.indexOfLast { it == "src" }
        if (sourceIndex < 0 || sourceIndex + 2 >= parts.size) return false
        val sourceSet = parts[sourceIndex + 1]
        val language = parts[sourceIndex + 2]
        return language in setOf("kotlin", "java") &&
            (sourceSet == "main" || sourceSet.endsWith("Main"))
    }

    private fun exclusion(project: Path, file: Path, scope: QualityScope): String? {
        val parts = project.relativize(file).map(Path::toString)
        if (parts.any { it in generatedSegments }) return "generated source"

        val modulePath = project.relativize(moduleRoot(project, file)).joinToString("/")
        val gradleModulePath = ":" + modulePath.replace('/', ':').trim(':')
        if (scope.excludeModules.any { globMatches(it, modulePath) || globMatches(it, gradleModulePath) }) {
            return "module exclusion"
        }

        val relativePath = project.relativize(file).joinToString("/")
        if (scope.excludePaths.any { globMatches(it, relativePath) }) return "path exclusion"

        if (!scope.includeSupport && parts.any { it in supportSegments }) return "support source"
        if (!scope.includeSupport && supportFilePrefixes.any(file.fileName.toString()::startsWith)) return "support source"
        return null
    }

    private fun moduleRoot(project: Path, file: Path): Path {
        var current = file.parent
        while (current != null && current.startsWith(project)) {
            if (Files.exists(current.resolve("build.gradle.kts")) || Files.exists(current.resolve("build.gradle"))) {
                return current
            }
            current = current.parent
        }
        return project
    }

    private fun globMatches(pattern: String, value: String): Boolean = runCatching {
        java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern").matches(Path.of(value))
    }.getOrDefault(false)
}
