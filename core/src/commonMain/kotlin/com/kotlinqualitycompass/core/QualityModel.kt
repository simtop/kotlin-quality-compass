package com.kotlinqualitycompass.core

/** A concrete Kotlin function discovered by the source analyzer. */
data class FunctionDescriptor(
    val sourcePath: String,
    val packageName: String,
    val ownerName: String?,
    val name: String,
    val isInline: Boolean = false,
    val startLine: Int,
    val endLine: Int,
    val cyclomaticComplexity: Int,
)

/** Whether coverage was mapped exactly enough to calculate a score. */
enum class CoverageStatus {
    EXACT_LINE_SPAN,
    APPROXIMATE_LINE_SPAN,
    UNAVAILABLE,
}

data class CoverageMeasurement(
    val percentage: Double?,
    val status: CoverageStatus,
    val note: String? = null,
)

data class FunctionMetric(
    val function: FunctionDescriptor,
    val coverage: CoverageMeasurement,
    val score: Double?,
)

object CrapScore {
    const val DEFAULT_THRESHOLD = 8.0

    /** Calculates the CRAP-style score from a complexity integer and 0..100 coverage percent. */
    fun calculate(complexity: Int, coveragePercentage: Double?): Double? {
        if (coveragePercentage == null) return null
        require(complexity >= 1) { "Cyclomatic complexity must be at least 1" }
        require(coveragePercentage in 0.0..100.0) { "Coverage must be between 0 and 100" }
        val uncovered = 1.0 - coveragePercentage / 100.0
        return complexity * complexity * uncovered * uncovered * uncovered + complexity
    }
}
