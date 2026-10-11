package com.daoqiji.app

import java.time.LocalDate
import java.util.UUID

/** Text stays in the draft until validation succeeds; partial input never alters saved items. */
internal data class ItemDraft(
    val title: String = "",
    val category: String = "未分类",
    val price: String = "",
    val purchaseDate: String = "",
    val expireDate: String = "",
    val warrantyMonths: String = "",
    val reminderDays: String = "30,7,1",
    val repeatYearly: Boolean = false,
    val costMethod: CostMethod = CostMethod.Time,
    val useCount: String = "0",
    val retirementDate: String = "",
    val note: String = "",
    val imageBase64: String? = null,
    val completed: Boolean = false
) {
    fun toItem(original: ExpiryItem? = null, today: LocalDate = LocalDate.now()): ExpiryItem {
        require(title.isNotBlank()) { "请填写物品名称" }
        fun date(value: String, label: String): LocalDate? = if (value.isBlank()) null else
            runCatching { LocalDate.parse(value) }.getOrElse { error("${label}无效") }
        val purchased = date(purchaseDate, "购买日期")
        val retired = date(retirementDate, "退役日期")
        val months = if (warrantyMonths.isBlank()) 0 else warrantyMonths.toIntOrNull()
        require(months != null && months in 0..1200) { "保修月数应为 0–1200" }
        require(months == 0 || purchased != null || expireDate.isNotBlank()) { "请先选择购买日期，再按保修月数计算" }
        val expiry = date(expireDate, "过保日期") ?: purchased?.takeIf { months > 0 }?.plusMonths(months.toLong())
        require(purchased == null || retired == null || retired >= purchased) { "退役日期不能早于购买日期" }
        require(purchased == null || expiry == null || expiry >= purchased) { "过保日期不能早于购买日期" }
        val amount = if (price.isBlank()) null else price.toDoubleOrNull()
        require(price.isBlank() || (amount != null && amount.isFinite() && amount >= 0 && amount <= 999999999.99)) {
            "请输入有效价格（0–999999999.99）"
        }
        require(price.isBlank() || Regex("\\d+(\\.\\d{1,2})?").matches(price)) { "价格最多填写两位小数" }
        val count = useCount.toIntOrNull()
        require(costMethod != CostMethod.Count || (count != null && count >= 0)) { "使用次数应为非负整数" }
        val reminders = if (expiry == null || reminderDays.isBlank()) emptyList() else {
            val tokens = reminderDays.trim().split(Regex("[,，、\\s]+"))
            require(tokens.all { it.toIntOrNull()?.let { n -> n in 0..3650 } == true }) { "提醒天数应为 0–3650，多个用逗号分隔" }
            tokens.map(String::toInt).distinct().sortedDescending()
        }
        val label = normalizeCategoryName(category).ifBlank { "未分类" }
        require(isValidCategoryName(label)) { "分类名称需为1–20个字" }
        val builtIn = ItemCategory.entries.firstOrNull { it.label == label }
        val base = original ?: ExpiryItem(UUID.randomUUID().toString(), title.trim(), "", -1, today.toString())
        return base.copy(
            title = title.trim(), category = builtIn ?: ItemCategory.Other,
            customCategory = label.takeIf { builtIn == null }, price = amount,
            purchaseDate = purchased?.toString(), expireDate = expiry?.toString().orEmpty(), warrantyMonths = months,
            remindBeforeDays = reminders.firstOrNull() ?: -1, additionalRemindBeforeDays = reminders.drop(1),
            repeatRule = if (repeatYearly && expiry != null) RepeatRule.Yearly else RepeatRule.None,
            costMethod = costMethod, useCount = count ?: 0, retirementDate = retired?.toString(),
            note = note.trim(), imageBase64 = imageBase64,
            lifecycle = if (completed) ItemLifecycle.Completed else ItemLifecycle.Active,
            renewalHistory = if (original?.isCompleted(today) == true && !completed &&
                expiry != null && expiry.toString() != original.expireDate && original.expireDate.isNotBlank())
                original.renewalHistory + original.expireDate else base.renewalHistory
        )
    }

    companion object {
        fun from(item: ExpiryItem) = ItemDraft(
            title = item.title, category = item.categoryLabel,
            price = item.price?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(),
            purchaseDate = item.purchaseDate.orEmpty(), expireDate = item.expireDate,
            warrantyMonths = item.warrantyMonths.takeIf { it > 0 }?.toString().orEmpty(),
            reminderDays = item.allReminderDays().joinToString(","), repeatYearly = item.repeatRule == RepeatRule.Yearly,
            costMethod = item.costMethod, useCount = item.useCount.toString(), retirementDate = item.retirementDate.orEmpty(),
            note = item.note, imageBase64 = item.imageBase64, completed = item.lifecycle == ItemLifecycle.Completed
        )
    }
}
