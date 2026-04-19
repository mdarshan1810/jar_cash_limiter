package com.jar.parser

import kotlinx.serialization.Serializable

@Serializable
data class ExpectedSample(
    val matchedPattern: String? = null,
    val amount: Long? = null,
    val merchant: String? = null,
    val balance: Long? = null,
    val accountLast4: String? = null,
    val minConfidence: Float? = null,
    val maxConfidence: Float? = null
)
