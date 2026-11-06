package com.kotlinqualitycompass.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/** Controls which production sources are treated as application behavior. */
data class QualityScope(
    val includeSupport: Boolean = false,
    val includePreviews: Boolean = false,
    val excludePaths: List<String> = emptyList(),
    val excludeModules: List<String> = emptyList(),
    val coverageUnavailableModules: List<String> = emptyList(),
) {
    fun isCoverageUnavailable(project: Path, source: Path): Boolean {
        val modulePath = ProjectDiscovery.modulePath(project, source)
        val gradleModulePath = ":" + modulePath.replace('/', ':').trim(':')
        return coverageUnavailableModules.any { pattern ->
            globMatches(pattern, modulePath) || globMatches(pattern, gradleModulePath)
        }
    }

    companion object {
        fun load(project: Path, arguments: Arguments): QualityScope {
            val configuredFile = arguments.configFile?.let(Paths::get)?.let { path ->
                if (path.isAbsolute) path else project.resolve(path)
            } ?: project.resolve(".quality-compass.properties")
            val properties = Properties()
            if (Files.exists(configuredFile)) {
                configuredFile.toFile().inputStream().use(properties::load)
            }
            return QualityScope(
                includeSupport = arguments.includeSupport || properties.boolean("includeSupport"),
                includePreviews = arguments.includePreviews || properties.boolean("includePreviews"),
                excludePaths = properties.csv("excludePath") + arguments.excludePaths,
                excludeModules = properties.csv("excludeModule") + arguments.excludeModules,
                coverageUnavailableModules =
                    properties.csv("coverageUnavailableModule") + arguments.coverageUnavailableModules,
            )
        }

        private fun Properties.boolean(name: String): Boolean =
            getProperty(name)?.trim()?.toBooleanStrictOrNull() ?: false

        private fun Properties.csv(name: String): List<String> =
            getProperty(name)?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

        private fun globMatches(pattern: String, value: String): Boolean = runCatching {
            java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern").matches(Path.of(value))
        }.getOrDefault(false)
    }
}

data class ExcludedSource(
    val path: Path,
    val reason: String,
)

data class DiscoveryResult(
    val files: List<Path>,
    val excludedSources: List<ExcludedSource>,
)
