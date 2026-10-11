package com.daoqiji.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpiryFeaturesTest {
    @Test
    fun overviewCategoriesOnlyUseExistingItemLabelsAndRespectHiddenOrder() {
        val digital = ExpiryItem("a", "手机", "2028-01-01", 30, "2026-01-01", category = ItemCategory.Digital)
        val other = digital.copy(id = "b", category = ItemCategory.Other, lifecycle = ItemLifecycle.Completed)
        val custom = digital.copy(id = "c", customCategory = "收藏")
        assertEquals(listOf("其他", "数码家电"), overviewCategoryLabels(
            listOf(digital, other, custom), listOf("收藏"),
            listOf(CATEGORY_ADD, categoryBarId("其他"), categoryBarId("数码家电"))))
        assertEquals(emptyList<String>(), overviewCategoryLabels(emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun overviewReorderPreservesButtonsAndUnusedCategoryPositions() {
        val original = listOf(CATEGORY_DELETE, categoryBarId("数码家电"), CATEGORY_ALL,
            categoryBarId("空分类"), categoryBarId("其他"), CATEGORY_ADD)
        assertEquals(listOf(CATEGORY_DELETE, categoryBarId("其他"), CATEGORY_ALL,
            categoryBarId("空分类"), categoryBarId("数码家电"), CATEGORY_ADD),
            reorderVisibleCategoryBar(original, listOf("其他", "数码家电")))
    }

    private val items = listOf(
        ExpiryItem("1", "护照", "2026-10-20", 30, "2026-09-01"),
        ExpiryItem("2", "牛奶", "2026-09-15", 3, "2026-09-01"),
        ExpiryItem("3", "会员", "2026-08-01", 7, "2026-09-01")
    )

    @Test
    fun combinesCategoryStatusAndSearchFilters() {
        val today = LocalDate.of(2026, 10, 11)
        val digital = ExpiryItem("nav1", "手机", "2026-10-20", 7, "2026-09-01", category = ItemCategory.Digital)
        val furniture = digital.copy(id = "nav2", title = "沙发", category = ItemCategory.Furniture)
        val expired = digital.copy(id = "nav3", title = "旧手机", expireDate = "2026-10-01")
        val completed = digital.copy(id = "nav4", lifecycle = ItemLifecycle.Completed)
        val sample = listOf(digital, furniture, expired, completed)
        assertEquals(listOf(digital), filterExpiryItems(sample, "手机", ExpiryFilter.InWarranty, today, ItemCategory.Digital))
        assertEquals(listOf(expired), filterExpiryItems(sample, "", ExpiryFilter.Expired, today, ItemCategory.Digital))
        assertEquals(listOf(completed), filterExpiryItems(sample, "", ExpiryFilter.Completed, today, ItemCategory.Digital))
        assertEquals(2, expiryOverview(sample, today).active)
        assertEquals(1, expiryOverview(sample, today).expired)
        assertEquals(1, expiryOverview(sample, today).completed)
    }

    @Test
    fun customCategoriesSurviveBackupAlongsideExistingLabels() {
        val original = listOf(
            items[0].copy(category = ItemCategory.Digital),
            items[1].copy(category = ItemCategory.Other),
            items[2].copy(category = ItemCategory.Other, customCategory = "办公设备")
        )
        val backup = ExpiryBackup.encode(original, listOf("办公设备", "旅行用品"))
        assertEquals(original, ExpiryBackup.decode(backup))
        assertEquals(listOf("数码家电", "其他", "办公设备"), ExpiryBackup.decode(backup).map { it.categoryLabel })
        assertEquals(listOf("办公设备", "旅行用品"), ExpiryBackup.decodeCustomCategories(backup))
    }

    @Test
    fun readsOldBackupsWithoutCustomCategoryFields() {
        val backup = org.json.JSONObject(ExpiryBackup.encode(items))
        backup.remove("custom_categories")
        val entries = backup.getJSONArray("items")
        for (index in 0 until entries.length()) entries.getJSONObject(index).remove("custom_category")
        assertEquals(items, ExpiryBackup.decode(backup.toString()))
        assertEquals(emptyList<String>(), ExpiryBackup.decodeCustomCategories(backup.toString()))
    }

    @Test
    fun customCategoryFilterDoesNotMixWithOtherLabel() {
        val today = LocalDate.of(2026, 9, 10)
        val custom = items[0].copy(customCategory = "办公设备")
        val other = items[1].copy(category = ItemCategory.Other)
        assertEquals(listOf(custom), filterExpiryItems(listOf(custom, other), "", ExpiryFilter.All,
            today, categoryLabel = "办公设备"))
        assertEquals(listOf(other), filterExpiryItems(listOf(custom, other), "", ExpiryFilter.All,
            today, categoryLabel = "其他"))
        assertEquals("办公设备", custom.renew("2028-03-01").categoryLabel)
    }

    @Test
    fun validatesCustomCategoryNames() {
        assertEquals("办公 设备", normalizeCategoryName("  办公   设备  "))
        assertFalse(isValidCategoryName("  "))
        assertFalse(isValidCategoryName("物".repeat(21)))
        assertTrue(isValidCategoryName("办公设备"))
    }

    @Test
    fun categoryOrderMovesInBothDirectionsAndAddsNewLabelsLast() {
        val original = listOf("数码家电", "其他", "办公设备")
        assertEquals(listOf("其他", "数码家电", "办公设备"), moveCategory(original, "数码家电", "其他"))
        assertEquals(listOf("办公设备", "数码家电", "其他"), moveCategory(original, "办公设备", "数码家电"))
        assertEquals(listOf("其他", "数码家电", "办公设备"), orderedCategories(original, listOf("其他", "已删除", "其他")))
        assertEquals(original, moveCategory(original, "不存在", "其他"))
    }

    @Test
    fun categoryOrderSurvivesBackupAndOldBackupsHaveNoOrder() {
        val order = listOf("其他", "办公设备", "数码家电")
        val backup = ExpiryBackup.encode(items, listOf("办公设备"), order)
        assertEquals(order, ExpiryBackup.decodeCategoryOrder(backup))
        val oldBackup = org.json.JSONObject(backup).apply { remove("category_order") }
        assertEquals(emptyList<String>(), ExpiryBackup.decodeCategoryOrder(oldBackup.toString()))
    }

    @Test
    fun emptyCategoryBarHasThreeMovableControls() {
        val defaults = listOf(CATEGORY_ALL, CATEGORY_ADD, CATEGORY_DELETE)
        assertEquals(defaults, orderedCategoryBar(emptyList(), emptyList()))
        val reordered = moveCategory(defaults, CATEGORY_ALL, CATEGORY_DELETE)
        assertEquals(listOf(CATEGORY_ADD, CATEGORY_DELETE, CATEGORY_ALL), reordered)
        assertEquals(reordered, orderedCategoryBar(emptyList(), reordered))
        assertEquals(listOf(CATEGORY_DELETE, CATEGORY_ALL, CATEGORY_ADD),
            moveCategory(defaults, CATEGORY_DELETE, CATEGORY_ALL))
    }

    @Test
    fun preservesControlPositionsAndInsertsNewCategoriesBeforeAdd() {
        val saved = listOf(CATEGORY_DELETE, categoryBarId("其他"), CATEGORY_ALL, CATEGORY_ADD)
        val expected = listOf(CATEGORY_DELETE, categoryBarId("其他"), CATEGORY_ALL, categoryBarId("办公设备"), CATEGORY_ADD)
        assertEquals(expected, orderedCategoryBar(listOf("其他", "办公设备"), saved))
        assertEquals(listOf(CATEGORY_DELETE, CATEGORY_ALL, CATEGORY_ADD), orderedCategoryBar(emptyList(), saved))
        assertTrue(categoryBarId(CATEGORY_ADD) != CATEGORY_ADD)
    }

    @Test
    fun backupPreservesHiddenLabelsAndAllControlPositionsWithoutChangingItems() {
        val order = listOf(CATEGORY_ADD, CATEGORY_ALL, CATEGORY_DELETE, categoryBarId("其他"))
        val hidden = listOf("数码家电")
        val backup = ExpiryBackup.encode(items, emptyList(), emptyList(), order, hidden)
        assertEquals(order, ExpiryBackup.decodeCategoryBarOrder(backup))
        assertEquals(hidden, ExpiryBackup.decodeHiddenCategories(backup))
        assertEquals(items, ExpiryBackup.decode(backup))
        val old = org.json.JSONObject(ExpiryBackup.encode(items, emptyList(), listOf("其他")))
        old.remove("category_bar_order")
        old.remove("hidden_categories")
        assertEquals(listOf(CATEGORY_ALL, categoryBarId("其他"), CATEGORY_ADD, CATEGORY_DELETE),
            ExpiryBackup.decodeCategoryBarOrder(old.toString()))
        assertEquals(emptyList<String>(), ExpiryBackup.decodeHiddenCategories(old.toString()))
    }

    @Test
    fun sensitivePermissionRequiresPrivacyConsent() {
        assertFalse(canRequestSensitivePermission(false))
        assertTrue(canRequestSensitivePermission(true))
    }

    @Test
    fun filtersByKeywordAndStatus() {
        val today = LocalDate.of(2026, 9, 10)

        assertEquals(listOf("护照"), filterExpiryItems(items, "护", ExpiryFilter.All, today).map { it.title })
        assertEquals(listOf("牛奶"), filterExpiryItems(items, "", ExpiryFilter.Within30Days, today).map { it.title })
        assertEquals(listOf("会员"), filterExpiryItems(items, "", ExpiryFilter.Expired, today).map { it.title })
    }

    @Test
    fun groupsItemsIntoCalendarMonths() {
        val groups = groupExpiryItemsByMonth(items)

        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), groups.keys.toList())
        assertEquals("牛奶", groups[YearMonth.of(2026, 9)]!!.single().title)
    }

    @Test
    fun calendarGroupsYearlyItemsByTheirNextOccurrence() {
        val birthday = ExpiryItem("4", "妈妈生日", "2020-05-20", 7, "2020-01-01", repeatRule = RepeatRule.Yearly)

        val groups = groupExpiryItemsByMonth(listOf(birthday), LocalDate.of(2026, 6, 1))

        assertEquals(listOf(YearMonth.of(2027, 5)), groups.keys.toList())
    }

    @Test
    fun yearlyReminderUsesTheNextOccurrence() {
        val birthday = ExpiryItem("4", "妈妈生日", "2020-05-20", 7, "2020-01-01", repeatRule = RepeatRule.Yearly)

        assertEquals(LocalDate.of(2027, 5, 20), birthday.nextOccurrence(LocalDate.of(2026, 6, 1)))
        assertEquals(LocalDate.of(2026, 5, 20), birthday.nextOccurrence(LocalDate.of(2026, 5, 20)))
        assertEquals(LocalDate.of(2026, 5, 13), birthday.reminderDate(LocalDate.of(2026, 5, 13)))
    }

    @Test
    fun infersUsefulCategoriesFromCommonTitles() {
        assertEquals(ItemCategory.Digital, inferCategory("空调"))
        assertEquals(ItemCategory.Furniture, inferCategory("懒人沙发"))
        assertEquals(ItemCategory.Clothing, inferCategory("儿童羽绒服"))
        assertEquals(ItemCategory.Beauty, inferCategory("修护面霜"))
        assertEquals(ItemCategory.Sports, inferCategory("羽毛球拍"))
    }

    @Test
    fun warrantyFieldsComputeHeldDaysAndExpiry() {
        val item = ExpiryItem(
            "5", "空调", "2028-03-01", 30, "2026-09-01",
            purchaseDate = "2026-03-01",
            warrantyMonths = 24
        )

        assertEquals(LocalDate.of(2028, 3, 1), item.computedExpireDate())
        assertEquals(184L, item.heldDays(LocalDate.of(2026, 9, 1)))
        assertEquals(0L, item.heldDays(LocalDate.of(2026, 2, 20)))
    }

    @Test
    fun parsesPriceFromSentenceInput() {
        val parsed = ExpiryParser.parse("空调3000元2027年3月1日过保，提前30天提醒")!!

        assertEquals(3000.0, parsed.price!!, 1e-9)
        assertEquals(false, parsed.title.contains("3000"))
    }

    @Test
    fun parsesPriceWithSymbolAndWanUnit() {
        val symbol = ExpiryParser.parse("耳机¥1299 2027年5月1日过保")!!
        val wan = ExpiryParser.parse("笔记本1.5万元2027年1月1日过保")!!

        assertEquals(1299.0, symbol.price!!, 1e-9)
        assertEquals(15000.0, wan.price!!, 1e-9)
    }

    @Test
    fun overviewTotalsPricesOfActiveItemsOnly() {
        val active = ExpiryItem("7", "空调", "2028-03-01", 30, "2026-09-01", price = 3000.0)
        val completed = ExpiryItem("8", "手机", "2028-06-01", 30, "2026-09-01", price = 5000.0)
            .copy(lifecycle = ItemLifecycle.Completed)

        val overview = expiryOverview(listOf(active, completed), LocalDate.of(2026, 9, 1))

        assertEquals(3000.0, overview.totalPrice, 1e-9)
    }

    @Test
    fun costsIncludeExpiredItemsButExcludeCompletedItems() {
        val today = LocalDate.of(2026, 9, 1)
        val active = ExpiryItem("a", "手机", "2028-01-01", 30, "2026-08-01",
            purchaseDate = "2026-08-22", price = 100.0)
        val expired = active.copy(id = "b", expireDate = "2026-08-30", price = 200.0)
        val completed = active.copy(id = "c", lifecycle = ItemLifecycle.Completed, price = 500.0)
        val missingDate = active.copy(id = "d", purchaseDate = null, price = 50.0)
        val overview = expiryOverview(listOf(active, expired, completed, missingDate), today)

        assertEquals(350.0, overview.totalPrice, 1e-9)
        assertEquals(30.0, overview.dailyCost!!, 1e-9)
        assertEquals(2, overview.dailyCostItemCount)
        assertEquals(3, overview.unprocessedCount)
        assertEquals(2, overview.active)
        assertEquals(1, overview.expired)
    }

    @Test
    fun dailyCostHandlesPurchaseDayAndUnavailableData() {
        val today = LocalDate.of(2026, 9, 1)
        val item = ExpiryItem("a", "耳机", "2028-01-01", 30, "2026-01-01",
            purchaseDate = today.toString(), price = 100.0)
        assertEquals(100.0, item.dailyCost(today)!!, 1e-9)
        assertEquals(0.0, item.copy(price = 0.0).dailyCost(today)!!, 1e-9)
        assertEquals(null, item.copy(price = null).dailyCost(today))
        assertEquals(null, item.copy(purchaseDate = null).dailyCost(today))
        assertEquals(null, item.copy(purchaseDate = "invalid").dailyCost(today))
        assertEquals(null, item.copy(purchaseDate = "2026-09-02").dailyCost(today))
        assertEquals(null, expiryOverview(listOf(item.copy(purchaseDate = null)), today).dailyCost)
        assertEquals(0.0, expiryOverview(emptyList(), today).dailyCost!!, 1e-9)
    }

    @Test
    fun itemsWithoutPurchaseDateHaveNoHeldDays() {
        val item = ExpiryItem("6", " Unknown ".trim(), "2028-03-01", 30, "2026-09-01")

        assertEquals(null, item.purchaseDate)
        assertEquals(null, item.heldDays())
        assertEquals(null, item.computedExpireDate())
    }

    @Test
    fun supportsMultipleReminderDatesWithoutDuplicates() {
        val item = ExpiryItem(
            "4", "护照", "2026-12-31", 30, "2026-09-01",
            additionalRemindBeforeDays = listOf(7, 1, 30)
        )

        assertEquals(listOf(30, 7, 1), item.allReminderDays())
        assertTrue(item.shouldNotifyOn(LocalDate.of(2026, 12, 1)))
        assertTrue(item.shouldNotifyOn(LocalDate.of(2026, 12, 24)))
        assertFalse(item.shouldNotifyOn(LocalDate.of(2026, 12, 20)))
        assertEquals(7, item.leadDaysOn(LocalDate.of(2026, 12, 24)))
        assertEquals(null, item.leadDaysOn(LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun completedItemsHaveTheirOwnFilterAndOverviewCount() {
        val completed = items[0].copy(lifecycle = ItemLifecycle.Completed)
        val source = listOf(completed, items[1], items[2])
        val today = LocalDate.of(2026, 9, 10)

        assertEquals(listOf("护照"), filterExpiryItems(source, "", ExpiryFilter.Completed, today).map { it.title })
        assertEquals(listOf("牛奶"), filterExpiryItems(source, "", ExpiryFilter.Within30Days, today).map { it.title })
        assertEquals(1, expiryOverview(source, today).completed)
        assertEquals(1, expiryOverview(source, today).expired)
    }

    @Test
    fun renewingAnItemKeepsHistoryAndReactivatesIt() {
        val completed = items[0].copy(lifecycle = ItemLifecycle.Completed)

        val renewed = completed.renew("2030-10-20")

        assertEquals("2030-10-20", renewed.expireDate)
        assertEquals(ItemLifecycle.Active, renewed.lifecycle)
        assertEquals(listOf("2026-10-20"), renewed.renewalHistory)
    }

    @Test
    fun backupRoundTripPreservesExpiryManagementFields() {
        val original = ExpiryItem(
            id = "backup-1",
            title = "护照",
            expireDate = "2030-10-20",
            remindBeforeDays = 30,
            createdAt = "2026-09-13",
            category = ItemCategory.Digital,
            purchaseDate = "2026-09-13",
            warrantyMonths = 12,
            price = 3000.5,
            additionalRemindBeforeDays = listOf(7, 1),
            lifecycle = ItemLifecycle.Completed,
            renewalHistory = listOf("2026-10-20")
        )

        val restored = ExpiryBackup.decode(ExpiryBackup.encode(listOf(original)))

        assertEquals(listOf(original), restored)
    }

    @Test
    fun legacyBackupWithoutWarrantyFieldsStillImports() {
        val raw = """{"schema_version":1,"items":[{"id":"v1","title":"空调","expire_date":"2028-03-01","remind_before_days":30}]}"""

        val restored = ExpiryBackup.decode(raw)

        assertEquals("空调", restored.single().title)
        assertEquals(null, restored.single().purchaseDate)
        assertEquals(0, restored.single().warrantyMonths)
    }
}
