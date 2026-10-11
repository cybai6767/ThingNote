package com.daoqiji.app

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

enum class RepeatRule(val label: String) {
    None("不重复"), Yearly("每年重复")
}

enum class ItemCategory(val label: String) {
    Digital("数码家电"), Furniture("家具家居"), Clothing("服饰鞋包"),
    Beauty("美妆个护"), Sports("运动户外"), Other("其他")
}

enum class ItemLifecycle { Active, Completed }
enum class CostMethod(val label: String) { Time("按时间"), Count("按次数"), None("不计算") }

data class ExpiryItem(
    val id: String,
    val title: String,
    val expireDate: String,
    val remindBeforeDays: Int,
    val createdAt: String,
    val purchaseDate: String? = null,
    val warrantyMonths: Int = 0,
    val price: Double? = null,
    val repeatRule: RepeatRule = RepeatRule.None,
    val category: ItemCategory = ItemCategory.Other,
    val additionalRemindBeforeDays: List<Int> = emptyList(),
    val lifecycle: ItemLifecycle = ItemLifecycle.Active,
    val renewalHistory: List<String> = emptyList(),
    val customCategory: String? = null,
    val costMethod: CostMethod = CostMethod.Time,
    val useCount: Int = 0,
    val note: String = "",
    val retirementDate: String? = null,
    val imageBase64: String? = null
) {
    val categoryLabel: String get() = customCategory?.takeIf(String::isNotBlank) ?: category.label
    fun expireLocalDate(): LocalDate? = expireDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)

    fun nextOccurrence(today: LocalDate = LocalDate.now()): LocalDate? {
        val date = expireLocalDate() ?: return null
        if (repeatRule == RepeatRule.None || !date.isBefore(today)) return date
        val monthDay = MonthDay.from(date)
        fun occurrence(year: Int): LocalDate = runCatching { monthDay.atYear(year) }
            .getOrElse { LocalDate.of(year, 2, 28) }
        val thisYear = occurrence(today.year)
        return if (thisYear.isBefore(today)) occurrence(today.year + 1) else thisYear
    }

    fun reminderDate(today: LocalDate = LocalDate.now()): LocalDate? =
        nextOccurrence(today)?.minusDays(remindBeforeDays.toLong())

    fun allReminderDays(): List<Int> =
        (listOf(remindBeforeDays) + additionalRemindBeforeDays)
            .filter { it >= 0 }
            .distinct()
            .sortedDescending()

    fun shouldNotifyOn(today: LocalDate = LocalDate.now()): Boolean {
        return leadDaysOn(today) != null
    }

    fun leadDaysOn(today: LocalDate = LocalDate.now()): Int? {
        if (isCompleted(today) || allReminderDays().isEmpty()) return null
        val date = nextOccurrence(today) ?: return null
        val days = ChronoUnit.DAYS.between(today, date).toInt()
        return days.takeIf { it == 0 || it in allReminderDays() }
    }

    fun purchaseLocalDate(): LocalDate? = purchaseDate
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun retirementLocalDate(): LocalDate? = retirementDate?.takeIf(String::isNotBlank)?.let(LocalDate::parse)

    fun isCompleted(today: LocalDate = LocalDate.now()): Boolean = lifecycle == ItemLifecycle.Completed ||
        retirementLocalDate()?.let { !it.isAfter(today) } == true

    private fun costEndDate(today: LocalDate): LocalDate = retirementLocalDate()?.let { minOf(it, today) } ?: today

    fun heldDays(today: LocalDate = LocalDate.now()): Long? =
        purchaseLocalDate()?.let { ChronoUnit.DAYS.between(it, costEndDate(today)).coerceAtLeast(0) }

    fun dailyCost(today: LocalDate = LocalDate.now()): Double? {
        if (costMethod != CostMethod.Time) return null
        val amount = price?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val purchased = purchaseLocalDate()?.takeUnless { it.isAfter(today) } ?: return null
        return amount / ChronoUnit.DAYS.between(purchased, costEndDate(today)).coerceAtLeast(1)
    }

    fun perUseCost(): Double? = price?.takeIf { it.isFinite() && it >= 0 && useCount > 0 && costMethod == CostMethod.Count }
        ?.div(useCount)

    fun computedExpireDate(): LocalDate? =
        purchaseLocalDate()?.takeIf { warrantyMonths > 0 }?.plusMonths(warrantyMonths.toLong())

    fun daysRemaining(today: LocalDate = LocalDate.now()): Long? =
        nextOccurrence(today)?.let { ChronoUnit.DAYS.between(today, it) }
}

internal fun normalizeCategoryName(name: String): String = name.trim().replace(Regex("""\s+"""), " ")

internal fun isValidCategoryName(name: String): Boolean = normalizeCategoryName(name).length in 1..20

fun ExpiryItem.renew(newExpireDate: String): ExpiryItem {
    LocalDate.parse(newExpireDate)
    return copy(
        expireDate = newExpireDate,
        lifecycle = ItemLifecycle.Active,
        renewalHistory = renewalHistory + expireDate
    )
}

fun inferCategory(title: String): ItemCategory = when {
    listOf("手机", "电脑", "笔记本", "平板", "耳机", "相机", "电视", "冰箱", "洗衣机", "空调", "电饭煲", "数码").any(title::contains) -> ItemCategory.Digital
    listOf("沙发", "床垫", "床", "桌子", "柜", "家具", "台灯").any(title::contains) -> ItemCategory.Furniture
    listOf("衣服", "外套", "羽绒服", "毛衣", "鞋", "包").any(title::contains) -> ItemCategory.Clothing
    listOf("化妆品", "护肤", "香水", "面霜", "口红", "美妆").any(title::contains) -> ItemCategory.Beauty
    listOf("健身", "运动", "自行车", "跑鞋", "球拍", "瑜伽").any(title::contains) -> ItemCategory.Sports
    else -> ItemCategory.Other
}

enum class ExpiryStatus {
    Safe,
    Soon,
    Urgent,
    Expired,
    NoWarranty
}

fun ExpiryItem.status(today: LocalDate = LocalDate.now()): ExpiryStatus {
    val days = daysRemaining(today) ?: return ExpiryStatus.NoWarranty
    return when {
        days < 0 -> ExpiryStatus.Expired
        days < 30 -> ExpiryStatus.Urgent
        days <= 90 -> ExpiryStatus.Soon
        else -> ExpiryStatus.Safe
    }
}
