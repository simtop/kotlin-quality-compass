package com.kotlinqualitycompass.cli

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectDiscoveryTest {
    @Test
    fun finds_production_main_and_kmp_sources_but_not_tests_or_build_logic() {
        val project = Files.createTempDirectory("quality-compass-project")
        project.resolve("feature/src/main/kotlin/sample/Main.kt").apply {
            parent.createDirectories()
            writeText("fun mainFunction() = 1")
        }
        project.resolve("feature/src/main/java/sample/JavaDirectory.kt").apply {
            parent.createDirectories()
            writeText("fun javaDirectoryFunction() = 1")
        }
        project.resolve("feature/src/commonMain/kotlin/sample/Common.kt").apply {
            parent.createDirectories()
            writeText("fun commonFunction() = 1")
        }
        project.resolve("feature/src/test/kotlin/sample/Test.kt").apply {
            parent.createDirectories()
            writeText("fun testFunction() = 1")
        }
        project.resolve("build-logic/src/main/kotlin/sample/Plugin.kt").apply {
            parent.createDirectories()
            writeText("fun pluginFunction() = 1")
        }

        val files = ProjectDiscovery.productionKotlinFiles(project)

        assertEquals(
            listOf(
                "feature/src/commonMain/kotlin/sample/Common.kt",
                "feature/src/main/java/sample/JavaDirectory.kt",
                "feature/src/main/kotlin/sample/Main.kt",
            ),
            files.map { project.relativize(it).toString() },
        )
    }

    @Test
    fun excludes_nested_worktrees_and_discovers_custom_kmp_source_sets() {
        val project = Files.createTempDirectory("quality-compass-worktrees")
        project.resolve("feature/src/androidMain/kotlin/sample/Android.kt").apply {
            parent.createDirectories()
            writeText("fun androidFunction() = 1")
        }
        project.resolve("feature/src/iosMain/kotlin/sample/Ios.kt").apply {
            parent.createDirectories()
            writeText("fun iosFunction() = 1")
        }
        project.resolve(".claude/worktrees/wt/feature/src/main/kotlin/sample/Worktree.kt").apply {
            parent.createDirectories()
            writeText("fun worktreeFunction() = 1")
        }

        val files = ProjectDiscovery.productionKotlinFiles(project)

        assertEquals(
            listOf(
                "feature/src/androidMain/kotlin/sample/Android.kt",
                "feature/src/iosMain/kotlin/sample/Ios.kt",
            ),
            files.map { project.relativize(it).toString() },
        )
    }

    @Test
    fun excludes_support_and_generated_sources_by_default_and_can_explain_scope() {
        val project = Files.createTempDirectory("quality-compass-scope")
        project.resolve("feature/build.gradle.kts").apply {
            parent.createDirectories()
            writeText("plugins { }")
        }
        project.resolve("feature/src/main/kotlin/sample/Production.kt").apply {
            parent.createDirectories()
            writeText("fun production() = 1")
        }
        project.resolve("feature/src/main/kotlin/sample/FakeBeer.kt").apply {
            writeText("fun fake() = 1")
        }
        project.resolve("feature/src/main/kotlin/generated/Generated.kt").apply {
            parent.createDirectories()
            writeText("fun generated() = 1")
        }

        val result = ProjectDiscovery.discoverWithReport(project)

        assertEquals(listOf("feature/src/main/kotlin/sample/Production.kt"), result.files.map { project.relativize(it).toString() })
        assertEquals(setOf("support source", "generated source"), result.excludedSources.map { it.reason }.toSet())
        assertTrue(result.excludedSources.any { it.path.fileName.toString() == "FakeBeer.kt" })
    }
}
