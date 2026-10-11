package com.daoqiji.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class ExpiryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("expiry_items", Context.MODE_PRIVATE)

    fun getAll(): List<ExpiryItem> {
        val raw = prefs.getString(KEY_ITEMS, "[]").orEmpty()
        val items = JSONArray(raw)
        return buildList {
            for (index in 0 until items.length()) {
                val json = items.optJSONObject(index) ?: continue
                val title = json.optString("title")
                add(
                    ExpiryItem(
                        id = json.optString("id"),
                        title = title,
                        expireDate = json.optString("expire_date"),
                        remindBeforeDays = json.optInt("remind_before_days", 30),
                        createdAt = json.optString("created_at"),
                        purchaseDate = json.optString("purchase_date").takeIf(String::isNotBlank),
                        warrantyMonths = json.optInt("warranty_months", 0),
                        price = json.optDouble("price", 0.0).takeIf { it > 0.0 },
                        repeatRule = RepeatRule.entries.firstOrNull {
                            it.name == json.optString("repeat_rule")
                        } ?: RepeatRule.None,
                        category = ItemCategory.entries.firstOrNull {
                            it.name == json.optString("category")
                        } ?: inferCategory(title),
                        additionalRemindBeforeDays = json.optJSONArray("additional_remind_before_days")
                            ?.toIntList().orEmpty(),
                        lifecycle = ItemLifecycle.entries.firstOrNull {
                            it.name == json.optString("lifecycle")
                        } ?: ItemLifecycle.Active,
                        renewalHistory = json.optJSONArray("renewal_history")?.toStringList().orEmpty(),
                        customCategory = json.optString("custom_category").takeIf(String::isNotBlank)
                    )
                )
            }
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() && it.expireDate.isNotBlank() }
            .sortedBy { it.expireDate }
    }

        fun add(parsed: ParsedExpiry, categoryLabel: String? = null): ExpiryItem {
            val builtIn = ItemCategory.entries.firstOrNull { it.label == categoryLabel }
            val item = ExpiryItem(
                id = UUID.randomUUID().toString(),
                title = parsed.title,
                expireDate = parsed.expireDate,
                remindBeforeDays = parsed.remindBeforeDays,
                createdAt = LocalDate.now().toString(),
                repeatRule = parsed.repeatRule,
                category = builtIn ?: if (categoryLabel == null) inferCategory(parsed.title) else ItemCategory.Other,
                customCategory = categoryLabel?.takeIf { builtIn == null },
                additionalRemindBeforeDays = parsed.additionalRemindBeforeDays,
                price = parsed.price
            )
            saveAll(getAll() + item)
            return item
        }

    fun update(item: ExpiryItem) {
        saveAll(getAll().map { if (it.id == item.id) item else it })
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    fun getCustomCategories(): List<String> {
        val stored = JSONArray(prefs.getString("custom_categories", "[]").orEmpty()).toStringList()
        return (stored + getAll().mapNotNull { it.customCategory }).distinct()
    }

    fun addCustomCategory(name: String) {
        val normalized = normalizeCategoryName(name)
        require(isValidCategoryName(normalized)) { "分类名称需为1–20个字" }
        if (normalized in getHiddenCategories()) {
            prefs.edit().putString("hidden_categories", JSONArray(getHiddenCategories() - normalized).toString())
                .putString("custom_categories", JSONArray((getCustomCategories() + normalized).distinct()).toString()).apply()
            return
        }
        require(normalized !in ItemCategory.entries.map { it.label } + getCustomCategories()) { "分类已存在" }
        prefs.edit().putString("custom_categories", JSONArray(getCustomCategories() + normalized).toString()).apply()
    }

    fun getCategoryOrder(): List<String> =
        JSONArray(prefs.getString("category_order", "[]").orEmpty()).toStringList()

    fun saveCategoryOrder(order: List<String>) {
        prefs.edit().putString("category_order", JSONArray(order.distinct()).toString()).apply()
    }

    fun getCategoryBarOrder(): List<String> {
        if (prefs.contains("category_bar_order")) {
            return JSONArray(prefs.getString("category_bar_order", "[]").orEmpty()).toStringList()
        }
        val previous = getCategoryOrder()
        return if (previous.isEmpty()) emptyList() else
            listOf(CATEGORY_ALL) + previous.map(::categoryBarId) + listOf(CATEGORY_ADD, CATEGORY_DELETE)
    }

    fun saveCategoryBarOrder(order: List<String>) {
        prefs.edit().putString("category_bar_order", JSONArray(order.distinct()).toString()).apply()
        saveCategoryOrder(order.filter { it.startsWith("category:") }.map(::categoryBarLabel))
    }

    fun getHiddenCategories(): List<String> =
        JSONArray(prefs.getString("hidden_categories", "[]").orEmpty()).toStringList()

    fun hideCategory(label: String) {
        prefs.edit().putString("hidden_categories", JSONArray((getHiddenCategories() + label).distinct()).toString()).apply()
        saveCategoryBarOrder(getCategoryBarOrder().filterNot { it == categoryBarId(label) })
    }

    fun exportBackup(): String = ExpiryBackup.encode(getAll(), getCustomCategories(), getCategoryOrder(),
        getCategoryBarOrder(), getHiddenCategories())

    fun importBackup(raw: String): Int {
        val restored = ExpiryBackup.decode(raw)
        val categories = ExpiryBackup.decodeCustomCategories(raw)
        val categoryOrder = ExpiryBackup.decodeCategoryOrder(raw)
        val barOrder = ExpiryBackup.decodeCategoryBarOrder(raw)
        val hidden = ExpiryBackup.decodeHiddenCategories(raw)
        saveAll(restored)
        prefs.edit().putString("custom_categories", JSONArray(categories).toString()).apply()
        saveCategoryOrder(categoryOrder)
        prefs.edit().putString("category_bar_order", JSONArray(barOrder).toString())
            .putString("hidden_categories", JSONArray(hidden).toString()).apply()
        return restored.size
    }

    private fun saveAll(items: List<ExpiryItem>) {
        val array = JSONArray()
        items.sortedBy { it.expireDate }.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("expire_date", item.expireDate)
                    .put("remind_before_days", item.remindBeforeDays)
                    .put("created_at", item.createdAt)
                    .put("purchase_date", item.purchaseDate.orEmpty())
                    .put("warranty_months", item.warrantyMonths)
                    .put("price", item.price ?: 0)
                    .put("repeat_rule", item.repeatRule.name)
                    .put("category", item.category.name)
                    .put("custom_category", item.customCategory.orEmpty())
                    .put("additional_remind_before_days", JSONArray(item.additionalRemindBeforeDays))
                    .put("lifecycle", item.lifecycle.name)
                    .put("renewal_history", JSONArray(item.renewalHistory))
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).mapNotNull { index -> optInt(index).takeIf { it >= 0 } }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
