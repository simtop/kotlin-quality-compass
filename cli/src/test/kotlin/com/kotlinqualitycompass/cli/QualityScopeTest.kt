package com.kotlinqualitycompass.cli

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityScopeTest {
    @Test
    fun configured_module_is_coverage_unavailable_by_glob() {
        val project = Files.createTempDirectory("quality-compass-scope")
        val source = project.resolve("app/src/main/kotlin/sample/App.kt").apply {
            parent.createDirectories()
            writeText("fun appFunction() = 1")
        }
        project.resolve("app/build.gradle.kts").writeText("plugins { }")

        val scope = QualityScope(coverageUnavailableModules = listOf("app"))

        assertTrue(scope.isCoverageUnavailable(project, source))
        assertFalse(scope.isCoverageUnavailable(project, project.resolve("core/src/main/kotlin/sample/Core.kt")))
    }

    @Test
    fun properties_and_cli_values_are_combined() {
        val project = Files.createTempDirectory("quality-compass-config")
        project.resolve(".quality-compass.properties").writeText("coverageUnavailableModule=app,feature/*")
        val arguments = parseArguments(
            arrayOf(
                "--project", project.toString(), "--all",
                "--coverage-unavailable-module", "android",
            ),
        )

        val scope = QualityScope.load(project, arguments)

        assertTrue(scope.coverageUnavailableModules.containsAll(listOf("app", "feature/*", "android")))
    }
}
