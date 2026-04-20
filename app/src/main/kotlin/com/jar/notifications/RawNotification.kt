package com.jar.notifications

data class RawNotification(
    val text: String,
    val sender: String?,
    val packageName: String,
    val postTimeMillis: Long
)
