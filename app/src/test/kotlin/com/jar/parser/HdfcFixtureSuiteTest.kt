package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import java.io.File

class HdfcFixtureSuiteTest {

    private val parser = HdfcParser()
    private val json = Json { ignoreUnknownKeys = true }

    private fun samplesDir(): File {
        val url = javaClass.classLoader?.getResource("hdfc_samples")
            ?: error("hdfc_samples/ not on classpath — check src/test/resources")
        return File(url.toURI())
    }

    @Test fun everyFixtureMatchesItsExpected() {
        val dir = samplesDir()
        val pairs = dir.listFiles { f -> f.extension == "txt" }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertNotNull("no fixtures found", pairs)
        assert(pairs.isNotEmpty()) { "no fixtures found in $dir" }

        val failures = mutableListOf<String>()
        for (txt in pairs) {
            val expFile = File(txt.parentFile, txt.nameWithoutExtension + ".expected.json")
            if (!expFile.exists()) {
                failures += "${txt.name}: missing ${expFile.name}"
                continue
            }
            val expected = json.decodeFromString(ExpectedSample.serializer(), expFile.readText())
            val input = txt.readText().trim()
            val actual = parser.parse(input)

            val diff = compare(txt.name, expected, actual)
            if (diff != null) failures += diff
        }
        if (failures.isNotEmpty()) {
            fail("fixture mismatches:\n" + failures.joinToString("\n"))
        }
    }

    private fun compare(name: String, expected: ExpectedSample, actual: ParseResult): String? {
        if (expected.matchedPattern == null) {
            return if (actual is ParseResult.Failure) null
                   else "$name: expected Failure, got $actual"
        }
        if (actual !is ParseResult.Success) return "$name: expected Success, got $actual"

        val errs = buildList {
            if (actual.matchedPattern != expected.matchedPattern)
                add("matchedPattern ${actual.matchedPattern} != ${expected.matchedPattern}")
            if (expected.amount != null && actual.amount != expected.amount)
                add("amount ${actual.amount} != ${expected.amount}")
            if (expected.merchant != null && actual.merchant != expected.merchant)
                add("merchant ${actual.merchant} != ${expected.merchant}")
            if (expected.balance != null && actual.balance != expected.balance)
                add("balance ${actual.balance} != ${expected.balance}")
            if (expected.accountLast4 != null && actual.accountLast4 != expected.accountLast4)
                add("accountLast4 ${actual.accountLast4} != ${expected.accountLast4}")
            if (expected.minConfidence != null && actual.confidence < expected.minConfidence)
                add("confidence ${actual.confidence} < min ${expected.minConfidence}")
            if (expected.maxConfidence != null && actual.confidence > expected.maxConfidence)
                add("confidence ${actual.confidence} > max ${expected.maxConfidence}")
        }
        return if (errs.isEmpty()) null else "$name: ${errs.joinToString("; ")}"
    }
}
