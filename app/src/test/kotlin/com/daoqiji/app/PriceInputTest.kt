package com.daoqiji.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceInputTest {
    @Test
    fun parsesSupportedPriceExpressionsAndRemovesThemFromTitle() {
        val expressions = mapOf(
            "3999元" to 3999.0,
            "103.55块钱" to 103.55,
            "¥1,699.15" to 1699.15,
            "￥3999元" to 3999.0,
            "价格3999" to 3999.0,
            "金额：103.55" to 103.55,
            "售价 ¥3999" to 3999.0,
            "花了3999元" to 3999.0,
            "花费1.2万元" to 12000.0
        )
        expressions.forEach { (expression, expected) ->
            val result = ExpiryParser.parse("手机，$expression，2027年3月1日到期，提前30天和7天提醒")!!
            assertEquals(expression, expected, result.price!!, 0.001)
            assertEquals(expression, "手机", result.title)
            assertEquals(listOf(7), result.additionalRemindBeforeDays)
        }
    }

    @Test
    fun keepsModelNumbersAndDatesOutOfPrice() {
        listOf("技嘉5070", "航嘉750W", "redmi buds8", "手机价格2027年3月1日").forEach { title ->
            assertNull(ExpiryParser.parse("$title，2028年3月1日到期，提前30天提醒")!!.price)
        }
    }

    @Test
    fun handlesPriceAfterReminderWithoutChangingReminderDays() {
        val result = ExpiryParser.parse("耳机2027年5月21日到期，提前30天提醒，花了103.55元")!!
        assertEquals("耳机", result.title)
        assertEquals(30, result.remindBeforeDays)
        assertEquals(103.55, result.price!!, 0.001)
    }

    @Test
    fun manualPriceOverridesRecognitionAndBlankClearsPrice() {
        val parsed = ExpiryParser.parse("耳机199元，2027年5月21日到期")!!
        assertEquals(199.0, parsed.withPriceInput(null).price!!, 0.001)
        assertEquals(103.55, parsed.withPriceInput("103.55").price!!, 0.001)
        assertNull(parsed.withPriceInput("").price)
        assertEquals(parsed.title, parsed.withPriceInput("103.55").title)
    }

    @Test
    fun allowsOptionalPriceAndZeroButRejectsInvalidAmounts() {
        listOf(null, "", "0", "0.00", "103.55", "3000").forEach { assertTrue(isValidPriceInput(it)) }
        listOf("-1", "103.555", ".", "1.2.3", "NaN", "Infinity", "abc", "9".repeat(400))
            .forEach { assertFalse(isValidPriceInput(it)) }
    }

    @Test
    fun manualPriceWorksWithoutAmountInSentence() {
        val parsed = ExpiryParser.parse("耳机2027年5月21日到期")!!
        assertNull(parsed.price)
        assertEquals(103.55, parsed.withPriceInput("103.55").price!!, 0.001)
    }
}
