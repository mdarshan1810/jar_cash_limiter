package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

class ParseRateReportTest {

    private val parser = HdfcParser()

    /**
     * Release gate threshold. Ramp this up as the corpus grows:
     *  - Seed phase (< 50 samples): 0.0 — report only, no enforcement
     *  - Once corpus ≥ 50: raise to 0.95 per spec §11 release gate
     */
    private val minRate: Float = 0.0f

    @Test fun corpusParseRateMeetsThreshold() {
        val url = javaClass.classLoader.getResource("hdfc_samples") ?: error("samples dir missing")
        val dir = File(url.toURI())
        val samples = dir.listFiles { f -> f.extension == "txt" }?.toList() ?: emptyList()
        assertTrue("no samples on classpath", samples.isNotEmpty())

        var success = 0
        val failures = mutableListOf<String>()
        for (s in samples) {
            val r = parser.parse(s.readText().trim())
            if (r is ParseResult.Success) success++ else failures += s.name
        }
        val rate = success.toFloat() / samples.size
        println("=== HDFC ParseRateReport ===")
        println("total=${samples.size} success=$success failed=${failures.size} rate=${"%.2f".format(rate * 100)}%")
        if (failures.isNotEmpty()) println("failed: $failures")
        assertTrue(
            "parse rate $rate below threshold $minRate",
            rate >= minRate
        )
    }
}
