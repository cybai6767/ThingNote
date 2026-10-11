package com.daoqiji.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ItemDraftTest {
    private val today = LocalDate.of(2026, 10, 12)

    @Test fun nameOnlyIsStoredWithoutInventingWarrantyOrReminder() {
        val item = ItemDraft(title = "收藏品").toItem(today = today)
        assertEquals("", item.expireDate)
        assertNull(item.daysRemaining(today))
        assertNull(item.reminderDate(today))
        assertFalse(item.shouldNotifyOn(today))
        assertEquals(ExpiryStatus.NoWarranty, item.status(today))
        assertEquals(listOf(item), filterExpiryItems(listOf(item), "", ExpiryFilter.All, today))
        assertTrue(filterExpiryItems(listOf(item), "", ExpiryFilter.InWarranty, today).isEmpty())
        assertTrue(groupExpiryItemsByMonth(listOf(item), today).isEmpty())
        val overview = expiryOverview(listOf(item.copy(price = 99.0)), today)
        assertEquals(99.0, overview.totalPrice, 0.0)
        assertEquals(0, overview.active)
        assertEquals(0, overview.expired)
        assertEquals(1, overview.unprocessedCount)
    }

    @Test fun monthCalculationHandlesLeapDayAndExplicitDateWins() {
        val draft = ItemDraft(title = "手机", purchaseDate = "2024-02-29", warrantyMonths = "12")
        assertEquals("2025-02-28", draft.toItem().expireDate)
        assertEquals("2026-02-28", draft.copy(expireDate = "2026-02-28").toItem().expireDate)
        assertEquals("2024-03-31", draft.copy(purchaseDate = "2024-01-31", warrantyMonths = "2").toItem().expireDate)
    }

    @Test fun disablingRemindersAlsoDisablesExpiryDayNotification() {
        val item = ItemDraft(title = "手机", expireDate = today.toString(), reminderDays = "").toItem()
        assertFalse(item.shouldNotifyOn(today))
        assertTrue(item.allReminderDays().isEmpty())
        val enabled = ItemDraft(title = "手机", expireDate = today.toString(), reminderDays = "7,30,7,0").toItem()
        assertEquals(listOf(30, 7, 0), enabled.allReminderDays())
        assertTrue(enabled.shouldNotifyOn(today))
    }

    @Test fun retirementCapsTimeAndChangesStatusOnItsDate() {
        val item = ItemDraft(title = "手机", price = "100", purchaseDate = "2026-10-01",
            expireDate = "2027-10-01", retirementDate = "2026-10-11").toItem(today = today)
        assertEquals(10.0, item.dailyCost(today)!!, 0.0)
        assertEquals(10L, item.heldDays(today))
        assertFalse(item.isCompleted(LocalDate.of(2026, 10, 10)))
        assertTrue(item.isCompleted(LocalDate.of(2026, 10, 11)))
        assertEquals(1, expiryOverview(listOf(item), today).completed)
        assertEquals(0.0, expiryOverview(listOf(item), today).totalPrice, 0.0)
        assertFalse(item.shouldNotifyOn(LocalDate.of(2027, 10, 1)))
    }

    @Test fun costMethodsKeepAssetButNeverMixUnits() {
        val timed = ItemDraft(title = "手机", price = "100", purchaseDate = "2026-10-02").toItem(today = today)
        val counted = timed.copy(id = "count", costMethod = CostMethod.Count, useCount = 4)
        val excluded = timed.copy(id = "none", costMethod = CostMethod.None)
        assertNull(counted.dailyCost(today))
        assertEquals(25.0, counted.perUseCost()!!, 0.0)
        assertNull(counted.copy(useCount = 0).perUseCost())
        assertNull(excluded.dailyCost(today))
        val overview = expiryOverview(listOf(timed, counted, excluded), today)
        assertEquals(300.0, overview.totalPrice, 0.0)
        assertEquals(10.0, overview.dailyCost!!, 0.0)
        assertEquals(1, overview.dailyCostItemCount)
    }

    @Test fun editAndBackupPreserveAllFieldsIncludingZeroAndPhoto() {
        val item = ItemDraft(title = "自填的24时前可维修", category = "我的收藏", price = "0", purchaseDate = "2026-10-01",
            costMethod = CostMethod.Count, useCount = "2", note = "发票备注", retirementDate = "2028-01-01",
            imageBase64 = "dGVzdA==").toItem(today = today)
        val original = item.copy(renewalHistory = listOf("2025-01-01"))
        assertEquals(original, ItemDraft.from(original).toItem(original, today))
        assertEquals(listOf(original), ExpiryBackup.decode(ExpiryBackup.encode(listOf(original))))
        assertEquals(original.id, ItemDraft.from(original).copy(note = "新备注").toItem(original).id)
    }

    @Test fun invalidInputIsRejectedBeforeSave() {
        val valid = ItemDraft(title = "手机")
        listOf(valid.copy(title = " "), valid.copy(price = "1.234"), valid.copy(price = "NaN"),
            valid.copy(price = "-1"), valid.copy(expireDate = "2026-02-30"),
            valid.copy(warrantyMonths = "12"), valid.copy(purchaseDate = "2026-10-12", retirementDate = "2026-10-01"),
            valid.copy(purchaseDate = "2026-10-12", expireDate = "2026-10-01"),
            valid.copy(costMethod = CostMethod.Count, useCount = "2147483648"),
            valid.copy(expireDate = "2027-01-01", reminderDays = "7,坏数据")
        ).forEach { assertTrue("应拒绝 $it", runCatching { it.toItem() }.isFailure) }
    }

    @Test fun legacyBackupRetainsDefaultTimeModeAndDates() {
        val raw = """{"schema_version":2,"items":[{"id":"legacy","title":"旧手机","expire_date":"2027-03-01","price":103.55}]}"""
        val item = ExpiryBackup.decode(raw).single()
        assertEquals("2027-03-01", item.expireDate)
        assertEquals(CostMethod.Time, item.costMethod)
        assertEquals(103.55, item.price!!, 0.0)
    }
}
