package com.jar.parser

interface BankParser {
    val bankId: String
    fun parse(text: String): ParseResult
}
