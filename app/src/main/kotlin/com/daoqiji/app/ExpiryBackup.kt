package com.daoqiji.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object ExpiryBackup {
    private const val SCHEMA_VERSION = 3

    fun encode(items: List<ExpiryItem>, customCategories: List<String> = emptyList(),
        categoryOrder: List<String> = emptyList(), categoryBarOrder: List<String> = emptyList(),
        hiddenCategories: List<String> = emptyList()): String = JSONObject()
        .put("schema_version", SCHEMA_VERSION)
        .put("exported_at", LocalDate.now().toString())
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        .put("custom_categories", JSONArray((customCategories + items.mapNotNull { it.customCategory }).distinct()))
        .put("category_order", JSONArray(categoryOrder.distinct()))
        .put("category_bar_order", JSONArray(categoryBarOrder.distinct()))
        .put("hidden_categories", JSONArray(hiddenCategories.distinct()))
        .toString(2)

    fun decode(raw: String): List<ExpiryItem> {
        val root = JSONObject(raw)
        require(root.optInt("schema_version") in 1..SCHEMA_VERSION) { "不支持的备份版本" }
        val array = root.getJSONArray("items")
        return (0 until array.length()).map { index -> array.getJSONObject(index).toExpiryItem() }
    }

    fun decodeCustomCategories(raw: String): List<String> =
        (JSONObject(raw).optJSONArray("custom_categories").toStringList() +
            decode(raw).mapNotNull { it.customCategory }).filter(String::isNotBlank).distinct()

    fun decodeCategoryOrder(raw: String): List<String> =
        JSONObject(raw).optJSONArray("category_order").toStringList().filter(String::isNotBlank).distinct()

    fun decodeCategoryBarOrder(raw: String): List<String> {
        val root = JSONObject(raw)
        if (root.has("category_bar_order")) return root.optJSONArray("category_bar_order").toStringList().distinct()
        val oldOrder = decodeCategoryOrder(raw)
        return if (oldOrder.isEmpty()) emptyList() else
            listOf(CATEGORY_ALL) + oldOrder.map(::categoryBarId) + listOf(CATEGORY_ADD, CATEGORY_DELETE)
    }

    fun decodeHiddenCategories(raw: String): List<String> =
        JSONObject(raw).optJSONArray("hidden_categories").toStringList().filter(String::isNotBlank).distinct()

    internal fun ExpiryItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("expire_date", expireDate)
        .put("remind_before_days", remindBeforeDays)
        .put("created_at", createdAt)
        .put("purchase_date", purchaseDate.orEmpty())
        .put("warranty_months", warrantyMonths)
        .put("price", price ?: JSONObject.NULL)
        .put("repeat_rule", repeatRule.name)
        .put("category", category.name)
        .put("custom_category", customCategory.orEmpty())
        .put("additional_remind_before_days", JSONArray(additionalRemindBeforeDays))
        .put("lifecycle", lifecycle.name)
        .put("renewal_history", JSONArray(renewalHistory))
        .put("cost_method", costMethod.name)
        .put("use_count", useCount)
        .put("note", note)
        .put("retirement_date", retirementDate.orEmpty())
        .put("image_base64", imageBase64.orEmpty())

    internal fun JSONObject.toExpiryItem(): ExpiryItem {
        val title = getString("title")
        val date = getString("expire_date")
        if (date.isNotBlank()) LocalDate.parse(date)
        require(title.isNotBlank() && getString("id").isNotBlank()) { "物品名称或 ID 为空" }
        return ExpiryItem(
            id = getString("id"),
            title = title,
            expireDate = date,
            remindBeforeDays = optInt("remind_before_days", 30),
            createdAt = optString("created_at", LocalDate.now().toString()),
            purchaseDate = optString("purchase_date").takeIf(String::isNotBlank),
            warrantyMonths = optInt("warranty_months", 0),
            price = if (isNull("price")) null else optDouble("price", Double.NaN)
                .takeIf { it.isFinite() && if (has("cost_method")) it >= 0.0 else it > 0.0 },
            repeatRule = RepeatRule.entries.firstOrNull { it.name == optString("repeat_rule") }
                ?: RepeatRule.None,
            category = ItemCategory.entries.firstOrNull { it.name == optString("category") }
                ?: inferCategory(title),
            customCategory = optString("custom_category").takeIf(String::isNotBlank),
            additionalRemindBeforeDays = optJSONArray("additional_remind_before_days").toIntList(),
            lifecycle = ItemLifecycle.entries.firstOrNull { it.name == optString("lifecycle") }
                ?: ItemLifecycle.Active,
            renewalHistory = optJSONArray("renewal_history").toStringList(),
            costMethod = CostMethod.entries.firstOrNull { it.name == optString("cost_method") } ?: CostMethod.Time,
            useCount = optInt("use_count", 0).coerceAtLeast(0),
            note = optString("note"),
            retirementDate = optString("retirement_date").takeIf(String::isNotBlank)?.also { LocalDate.parse(it) },
            imageBase64 = optString("image_base64").takeIf(String::isNotBlank)
        )
    }

    private fun JSONArray?.toIntList(): List<Int> = this?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optInt(index).takeIf { it >= 0 } }
    }.orEmpty()

    private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    }.orEmpty()
}
