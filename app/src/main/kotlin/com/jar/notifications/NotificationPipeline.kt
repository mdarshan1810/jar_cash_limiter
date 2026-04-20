package com.jar.notifications

import com.jar.data.InsertTxResult
import com.jar.data.JarRepository
import com.jar.data.TransactionEntity
import com.jar.data.UnparsedNotificationEntity
import com.jar.parser.BankParser
import com.jar.parser.ParseResult
import com.jar.settings.Settings
import com.jar.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

sealed class PipelineResult {
    data object NoActiveBank : PipelineResult()
    data object NotWhitelisted : PipelineResult()
    data class Inserted(val id: Long) : PipelineResult()
    data object DuplicateTransaction : PipelineResult()
    data object RoutedToUnparsed : PipelineResult()
    data object DroppedDifferentAccount : PipelineResult()
}

private const val MIN_CONFIDENCE = 0.4f

/**
 * Orchestrates the notification → database path per spec §7.2. Order of operations:
 * parser/whitelist lookup → whitelist filter → parse → (confidence + account-match gate) →
 * repository insert (which applies ±2min + unique-hash dedupe). Anything rejected before
 * parse is silently dropped; low-confidence or account-ambiguous hits are routed to
 * `unparsed_notifications` for later triage.
 */
class NotificationPipeline(
    private val parsers: Map<String, BankParser>,
    private val whitelists: Map<String, BankWhitelist>,
    private val repository: JarRepository,
    private val settingsStore: SettingsStore
) {

    suspend fun handle(raw: RawNotification): PipelineResult {
        val settings = settingsStore.flow.first()
        val parser = parsers[settings.trackedBank] ?: return PipelineResult.NoActiveBank
        val whitelist = whitelists[settings.trackedBank] ?: return PipelineResult.NoActiveBank

        if (!whitelist.matches(raw)) return PipelineResult.NotWhitelisted

        val result = parser.parse(raw.text)
        val hash = sha256(raw.text)

        return when (result) {
            is ParseResult.Failure -> routeToUnparsed(raw)
            is ParseResult.Success -> route(raw, result, hash, settings)
        }
    }

    private suspend fun route(
        raw: RawNotification,
        parsed: ParseResult.Success,
        hash: String,
        settings: Settings
    ): PipelineResult {
        if (parsed.confidence < MIN_CONFIDENCE) return routeToUnparsed(raw)

        val tracked = settings.trackedAccountLast4
        val acceptable = when {
            tracked == null -> true                        // pre-onboarding: accept any
            parsed.accountLast4 == tracked -> true         // explicit match
            parsed.accountLast4 == null -> null            // ambiguous — route to unparsed
            else -> false                                  // different account — silent drop
        }
        return when (acceptable) {
            true -> insertTransaction(raw, parsed, hash)
            null -> routeToUnparsed(raw)
            false -> PipelineResult.DroppedDifferentAccount
        }
    }

    private suspend fun insertTransaction(
        raw: RawNotification,
        parsed: ParseResult.Success,
        hash: String
    ): PipelineResult {
        val tx = TransactionEntity(
            amount = parsed.amount,
            merchantRaw = parsed.merchant,
            timestamp = raw.postTimeMillis,
            sourceSmsHash = hash,
            parseConfidence = parsed.confidence,
            accountLast4 = parsed.accountLast4
        )
        return when (val r = repository.insertTransaction(tx)) {
            is InsertTxResult.Inserted -> PipelineResult.Inserted(r.id)
            InsertTxResult.Duplicate -> PipelineResult.DuplicateTransaction
        }
    }

    private suspend fun routeToUnparsed(raw: RawNotification): PipelineResult {
        repository.insertUnparsed(
            UnparsedNotificationEntity(
                rawText = raw.text,
                sender = raw.sender,
                timestamp = raw.postTimeMillis,
                packageName = raw.packageName
            )
        )
        return PipelineResult.RoutedToUnparsed
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
