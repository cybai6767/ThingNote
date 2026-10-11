package com.daoqiji.app

data class ParsedExpiry @JvmOverloads constructor(
    val title: String,
    val expireDate: String,
    val remindBeforeDays: Int,
    val repeatRule: RepeatRule = RepeatRule.None,
    val additionalRemindBeforeDays: List<Int> = emptyList(),
    val price: Double? = null
)

internal fun isValidPriceInput(input: String?): Boolean = input.isNullOrBlank() ||
    (Regex("""\d+(?:\.\d{0,2})?""").matches(input) &&
        input.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true)

internal fun ParsedExpiry.withPriceInput(input: String?): ParsedExpiry =
    if (input == null) this else copy(price = input.toDoubleOrNull())
