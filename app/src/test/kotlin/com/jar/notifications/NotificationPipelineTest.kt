package com.jar.notifications

import com.jar.data.AppDatabase
import com.jar.data.InMemoryDataStore
import com.jar.data.JarRepository
import com.jar.data.buildInMemoryDb
import com.jar.parser.BankParser
import com.jar.parser.ParseResult
import com.jar.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeBankParser(
    override val bankId: String = "HDFC",
    var nextResult: ParseResult = ParseResult.Failure("unconfigured")
) : BankParser {
    override fun parse(text: String): ParseResult = nextResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class NotificationPipelineTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: JarRepository
    private lateinit var parser: FakeBankParser
    private lateinit var pipeline: NotificationPipeline

    @Before fun setUp() {
        db = buildInMemoryDb()
        settings = SettingsStore(InMemoryDataStore())
        repo = JarRepository(
            txDao = db.transactionDao(),
            unparsedDao = db.unparsedNotificationDao(),
            settingsStore = settings
        )
        parser = FakeBankParser()
        pipeline = NotificationPipeline(
            parsers = mapOf("HDFC" to parser),
            whitelists = mapOf("HDFC" to HdfcWhitelist()),
            repository = repo,
            settingsStore = settings
        )
    }

    @After fun tearDown() {
        db.close()
    }

    private fun raw(
        text: String = "Sent Rs.420 from HDFC Bank A/C XXXX1234 to Zomato",
        sender: String? = "VM-HDFCBK",
        pkg: String = "com.google.android.apps.messaging",
        postMs: Long = 1_700_000_000_000L
    ) = RawNotification(text, sender, pkg, postMs)

    private fun success(
        amount: Long = 42_000L,
        last4: String? = "1234",
        confidence: Float = 1.0f
    ) = ParseResult.Success(
        amount = amount,
        merchant = "Zomato",
        balance = null,
        accountLast4 = last4,
        confidence = confidence,
        matchedPattern = "upi_sent"
    )

    @Test fun nonWhitelistedPackageIsSilentlyDropped() = runTest {
        parser.nextResult = success()
        val result = pipeline.handle(raw(sender = null, pkg = "com.whatsapp"))
        assertEquals(PipelineResult.NotWhitelisted, result)
        // Silent drop means: not in transactions AND not in unparsed
        assertEquals(0, repo.observeRecent(10).first().size)
        assertEquals(0, repo.observeUnparsed().first().size)
    }

    @Test fun noActiveBankWhenSettingsTrackedBankHasNoParser() = runTest {
        settings.setTrackedBank("SBI")
        val result = pipeline.handle(raw())
        assertEquals(PipelineResult.NoActiveBank, result)
        assertEquals(0, repo.observeRecent(10).first().size)
    }

    @Test fun whitelistedSuccessWithNullTrackedInsertsTransaction() = runTest {
        // Pre-onboarding: trackedAccountLast4 is null → accept any parsed account
        parser.nextResult = success(last4 = "9876")
        val result = pipeline.handle(raw())
        assertTrue(result is PipelineResult.Inserted)
        assertEquals(1, repo.observeRecent(10).first().size)
    }

    @Test fun whitelistedSuccessWithMatchingTrackedInsertsTransaction() = runTest {
        settings.setTrackedAccountLast4("1234")
        parser.nextResult = success(last4 = "1234")
        val result = pipeline.handle(raw())
        assertTrue(result is PipelineResult.Inserted)
    }

    @Test fun whitelistedSuccessWithDifferentAccountIsSilentDrop() = runTest {
        // Spec §7.2: parse last4 ≠ tracked → drop silently, don't pollute unparsed
        settings.setTrackedAccountLast4("1234")
        parser.nextResult = success(last4 = "9999")
        val result = pipeline.handle(raw())
        assertEquals(PipelineResult.DroppedDifferentAccount, result)
        assertEquals(0, repo.observeRecent(10).first().size)
        assertEquals(0, repo.observeUnparsed().first().size)
    }

    @Test fun whitelistedSuccessWithNullAccountWhenTrackedSetRoutesToUnparsed() = runTest {
        // Spec §7.2: parser couldn't extract last4 but we have a tracked account.
        // Safer to lose this than to mis-attribute.
        settings.setTrackedAccountLast4("1234")
        parser.nextResult = success(last4 = null)
        val result = pipeline.handle(raw())
        assertEquals(PipelineResult.RoutedToUnparsed, result)
        assertEquals(1, repo.observeUnparsed().first().size)
        assertEquals(0, repo.observeRecent(10).first().size)
    }

    @Test fun lowConfidenceRoutesToUnparsed() = runTest {
        parser.nextResult = success(confidence = 0.3f)
        val result = pipeline.handle(raw())
        assertEquals(PipelineResult.RoutedToUnparsed, result)
        assertEquals(1, repo.observeUnparsed().first().size)
    }

    @Test fun exactThresholdConfidenceIsAccepted() = runTest {
        parser.nextResult = success(confidence = 0.4f)
        val result = pipeline.handle(raw())
        assertTrue(result is PipelineResult.Inserted)
    }

    @Test fun parserFailureRoutesToUnparsed() = runTest {
        parser.nextResult = ParseResult.Failure("no pattern matched")
        val result = pipeline.handle(raw())
        assertEquals(PipelineResult.RoutedToUnparsed, result)
        val unparsed = repo.observeUnparsed().first()
        assertEquals(1, unparsed.size)
        assertEquals("VM-HDFCBK", unparsed.first().sender)
    }

    @Test fun duplicateTransactionCaughtByRepositoryDedupe() = runTest {
        parser.nextResult = success()
        val first = pipeline.handle(raw(postMs = 1_700_000_000_000L))
        val second = pipeline.handle(raw(postMs = 1_700_000_030_000L))  // 30s later
        assertTrue(first is PipelineResult.Inserted)
        assertEquals(PipelineResult.DuplicateTransaction, second)
        // Only one row persisted
        assertEquals(1, repo.observeRecent(10).first().size)
    }

    @Test fun sameTextReplayedGeneratesSameHash() = runTest {
        // Same raw.text → same source_sms_hash → unique index rejects even outside the
        // ±2min window. This proves the hash is deterministic and stable.
        parser.nextResult = success()
        val baseText = "Sent Rs.500 from HDFC Bank A/C XXXX1234 to Amazon"
        val first = pipeline.handle(raw(text = baseText, postMs = 1_700_000_000_000L))
        val replay = pipeline.handle(raw(text = baseText, postMs = 1_700_000_000_000L + 10 * 60_000))
        assertTrue(first is PipelineResult.Inserted)
        assertEquals(PipelineResult.DuplicateTransaction, replay)
    }
}
