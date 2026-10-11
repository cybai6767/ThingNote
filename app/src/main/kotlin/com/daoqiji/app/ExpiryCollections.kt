package com.daoqiji.app

import java.time.LocalDate
import java.time.YearMonth

enum class ExpiryFilter(val label: String) {
    All("全部"), InWarranty("在保中"), Within30Days("30天内过保"), Expired("已过保"), Completed("已处理")
}

internal fun orderedCategories(available: List<String>, savedOrder: List<String>): List<String> {
    val unique = available.distinct()
    return savedOrder.filter { it in unique }.distinct() + unique.filterNot { it in savedOrder }
}

internal fun moveCategory(order: List<String>, source: String, target: String): List<String> {
    val targetIndex = order.indexOf(target)
    if (source !in order || targetIndex < 0 || source == target) return order
    return order.toMutableList().apply { remove(source); add(targetIndex, source) }
}

internal const val CATEGORY_ALL = "control:all"
internal const val CATEGORY_ADD = "control:add"
internal const val CATEGORY_DELETE = "control:delete"
internal fun categoryBarId(label: String): String = "category:$label"
internal fun categoryBarLabel(id: String): String = id.removePrefix("category:")

internal fun overviewCategoryLabels(items: List<ExpiryItem>, hidden: List<String>, barOrder: List<String>): List<String> =
    orderedCategories(items.map { it.categoryLabel }.distinct().filterNot { it in hidden },
        barOrder.filter { it.startsWith("category:") }.map(::categoryBarLabel))

internal fun reorderVisibleCategoryBar(barOrder: List<String>, visibleOrder: List<String>): List<String> {
    val ids = visibleOrder.distinct().map(::categoryBarId)
    val remaining = ids.iterator()
    return barOrder.map { if (it in ids) remaining.next() else it }
}

internal fun orderedCategoryBar(categories: List<String>, savedOrder: List<String>): List<String> {
    val labels = categories.distinct().map(::categoryBarId)
    val defaults = listOf(CATEGORY_ALL) + labels + listOf(CATEGORY_ADD, CATEGORY_DELETE)
    if (savedOrder.isEmpty()) return defaults
    val order = savedOrder.filter { it in defaults }.distinct().toMutableList()
    val missingLabels = labels.filterNot { it in order }
    val insertion = order.indexOf(CATEGORY_ADD).takeIf { it >= 0 } ?: order.size
    order.addAll(insertion, missingLabels)
    listOf(CATEGORY_ALL, CATEGORY_ADD, CATEGORY_DELETE).filterNot { it in order }.forEach { order.add(it) }
    return order
}

data class ExpiryOverview(
    val active: Int,
    val within30Days: Int,
    val expired: Int,
    val completed: Int,
    val totalPrice: Double = 0.0,
    val dailyCost: Double? = null,
    val dailyCostItemCount: Int = 0,
    val unprocessedCount: Int = 0
)

fun filterExpiryItems(
    items: List<ExpiryItem>,
    query: String,
    filter: ExpiryFilter,
    today: LocalDate = LocalDate.now(),
    category: ItemCategory? = null,
    categoryLabel: String? = null
): List<ExpiryItem> = items.filter { item ->
    val matchesQuery = query.isBlank() || item.title.contains(query.trim(), ignoreCase = true)
    val days = item.daysRemaining(today)
    val matchesFilter = when (filter) {
        ExpiryFilter.All -> !item.isCompleted(today)
        ExpiryFilter.InWarranty -> !item.isCompleted(today) && days != null && days >= 0
        ExpiryFilter.Within30Days -> !item.isCompleted(today) && days != null && days in 0..30
        ExpiryFilter.Expired -> !item.isCompleted(today) && item.repeatRule == RepeatRule.None && days != null && days < 0
        ExpiryFilter.Completed -> item.isCompleted(today)
    }
    matchesQuery && matchesFilter && (category == null || item.category == category) &&
        (categoryLabel == null || item.categoryLabel == categoryLabel)
}

fun groupExpiryItemsByMonth(
    items: List<ExpiryItem>,
    today: LocalDate = LocalDate.now()
): Map<YearMonth, List<ExpiryItem>> = items
    .filter { it.nextOccurrence(today) != null }
    .sortedBy { it.nextOccurrence(today) }
    .groupBy { YearMonth.from(it.nextOccurrence(today)) }
    .toSortedMap()

fun expiryOverview(
    items: List<ExpiryItem>,
    today: LocalDate = LocalDate.now()
): ExpiryOverview = ExpiryOverview(
    active = items.count { !it.isCompleted(today) && (it.daysRemaining(today)?.let { days -> days >= 0 } == true) },
    within30Days = items.count {
        !it.isCompleted(today) && (it.daysRemaining(today)?.let { days -> days in 0..30 } == true)
    },
    expired = items.count {
        !it.isCompleted(today) && it.repeatRule == RepeatRule.None && (it.daysRemaining(today)?.let { days -> days < 0 } == true)
    },
    completed = items.count { it.isCompleted(today) },
    totalPrice = items
        .filter { !it.isCompleted(today) }
        .sumOf { it.price ?: 0.0 },
    dailyCost = items.filter { !it.isCompleted(today) }
        .mapNotNull { it.dailyCost(today) }.let { costs ->
            if (costs.isEmpty() && items.any { !it.isCompleted(today) }) null else costs.sum()
        },
    dailyCostItemCount = items.count { !it.isCompleted(today) && it.dailyCost(today) != null },
    unprocessedCount = items.count { !it.isCompleted(today) }
)
