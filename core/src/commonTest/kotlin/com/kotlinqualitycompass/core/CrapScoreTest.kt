package com.kotlinqualitycompass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrapScoreTest {
    @Test
    fun untested_complex_method_has_expected_score() {
        assertEquals(12.0, CrapScore.calculate(complexity = 3, coveragePercentage = 0.0))
    }

    @Test
    fun fully_covered_method_has_complexity_as_score() {
        assertEquals(3.0, CrapScore.calculate(complexity = 3, coveragePercentage = 100.0))
    }

    @Test
    fun missing_coverage_has_no_score() {
        assertNull(CrapScore.calculate(complexity = 3, coveragePercentage = null))
    }
}
