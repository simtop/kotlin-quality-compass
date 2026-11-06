package com.kotlinqualitycompass.cli

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

data class GradleResult(val exitCode: Int, val output: String, val durationMillis: Long)

class GradleRunner {
    fun run(project: Path, task: String, timeoutSeconds: Long): GradleResult {
        val wrapper = project.resolve("gradlew")
        require(wrapper.exists()) { "Expected Gradle wrapper at $wrapper" }
        val command = listOf(wrapper.toString(), task, "--console=plain")
        val process = ProcessBuilder(command)
            .directory(project.toFile())
            .redirectErrorStream(true)
            .start()
        val output = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val startedAt = System.nanoTime()
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
            return GradleResult(
                exitCode = 124,
                output = "Gradle task timed out after $timeoutSeconds seconds\n${readOutput(output)}",
                durationMillis = elapsedMillis(startedAt),
            )
        }
        return GradleResult(
            exitCode = process.exitValue(),
            output = readOutput(output),
            durationMillis = elapsedMillis(startedAt),
        )
    }

    private fun readOutput(output: CompletableFuture<String>): String =
        runCatching { output.get(30, TimeUnit.SECONDS) }
            .getOrElse { "Unable to drain Gradle output: ${it.message}" }

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
