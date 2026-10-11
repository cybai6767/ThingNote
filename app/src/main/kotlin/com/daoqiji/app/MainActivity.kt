package com.daoqiji.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs
import java.time.LocalDate
import java.time.YearMonth

private enum class HomeView { List, Calendar }
private enum class AppTab(val label: String) {
    Overview("概览"), Add("添加"), Mine("我的"), Settings("设置")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.scheduleDailyCheck(this)
        NotificationHelper.ensureChannel(this)
        setContent {
            DaoQiJiApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaoQiJiApp() {
    val context = LocalContext.current
    val repository = remember { ExpiryRepository(context.applicationContext) }
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(repository.getAll()) }
    var addDraft by remember { mutableStateOf(ItemDraft()) }
    var editingItem by remember { mutableStateOf<ExpiryItem?>(null) }
    var selectedTab by remember { mutableStateOf(AppTab.Overview) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var customCategories by remember { mutableStateOf(repository.getCustomCategories()) }
    var categoryOrder by remember { mutableStateOf(repository.getCategoryBarOrder()) }
    var hiddenCategories by remember { mutableStateOf(repository.getHiddenCategories()) }
    val overviewListState = rememberLazyListState()
    val mineListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()
    var showPrivacyConsent by remember { mutableStateOf(!settings.privacyAccepted) }
    var searchQuery by remember { mutableStateOf("") }
    var expiryFilter by remember { mutableStateOf(ExpiryFilter.All) }
    var homeView by remember { mutableStateOf(HomeView.List) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000)
            today = LocalDate.now()
        }
    }
    var notificationAllowed by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    val dark = when (settings.theme) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val activity = context as ComponentActivity
    BackHandler(enabled = selectedTab != AppTab.Overview && editingItem == null) {
        selectedTab = AppTab.Overview
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                today = LocalDate.now()
                notificationAllowed = NotificationHelper.canNotify(context)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = (if (dark) Color(0xFF121214) else Color.White).toArgb()
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = (if (dark) Color(0xFF121214) else Color.White).toArgb()
    }

    fun refreshItems() {
        items = repository.getAll()
        customCategories = repository.getCustomCategories()
        categoryOrder = repository.getCategoryBarOrder()
        hiddenCategories = repository.getHiddenCategories()
    }

    fun createCategory(name: String) {
        repository.addCustomCategory(name)
        customCategories = repository.getCustomCategories()
        hiddenCategories = repository.getHiddenCategories()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationAllowed = NotificationHelper.canNotify(context)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(repository.exportBackup())
                } ?: error("无法写入文件")
            }
            scope.launch {
                snackbarHostState.showSnackbar(if (result.isSuccess) "备份已导出" else "导出失败，请重试")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                repository.importBackup(raw)
            }
            if (result.isSuccess) {
                refreshItems()
                ReminderScheduler.scheduleDailyCheck(context)
            }
            scope.launch {
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "已恢复 $it 条到期事项" },
                        onFailure = { "备份文件无效或已损坏" }
                    )
                )
            }
        }
    }

    LaunchedEffect(settings.privacyAccepted) {
        if (canRequestSensitivePermission(settings.privacyAccepted) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Color(0xFF5AA7FF),
            onPrimary = Color(0xFF081A30),
            background = Color(0xFF121214),
            surface = Color(0xFF121214),
            onSurface = Color(0xFFF2F2F7),
            onSurfaceVariant = Color(0xFFAEAEB8),
            surfaceContainerHigh = Color(0xFF242426),
            outlineVariant = Color(0xFF38383C)
        ) else lightColorScheme(
            primary = Color(0xFF007AFF),
            onPrimary = Color.White,
            background = Color(0xFFF5F5F7),
            surface = Color.White,
            onSurface = Color(0xFF1D1D1F),
            onSurfaceVariant = Color(0xFF6E6E73),
            surfaceContainerHigh = Color(0xFFF5F5F7),
            outlineVariant = Color(0xFFE5E5EA)
        )
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(if (selectedTab == AppTab.Overview) "记物本" else selectedTab.label,
                        fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(when (tab) {
                                    AppTab.Overview -> Icons.Default.Dashboard
                                    AppTab.Add -> Icons.Default.AddCircleOutline
                                    AppTab.Mine -> Icons.Default.Inventory2
                                    AppTab.Settings -> Icons.Default.Settings
                                }, contentDescription = null)
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ),
                            alwaysShowLabel = true
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            if (selectedTab == AppTab.Settings) {
                SettingsContent(
                    settings = settings,
                    notificationAllowed = notificationAllowed,
                    onChange = { updated ->
                        val timeChanged = settings.reminderTime != updated.reminderTime
                        settingsRepository.save(updated)
                        settings = updated
                        if (timeChanged) ReminderScheduler.scheduleDailyCheck(context)
                    },
                    onNotificationSettings = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                    onTestNotification = {
                        val submitted = NotificationHelper.showTestNotification(context)
                        notificationAllowed = NotificationHelper.canNotify(context)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (submitted) "测试通知已提交，请查看通知栏"
                                else "通知未能发送，请检查系统通知权限"
                            )
                        }
                    },
                    onExportBackup = {
                        exportBackupLauncher.launch("记物本备份-${LocalDate.now()}.json")
                    },
                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    modifier = Modifier.fillMaxSize().padding(padding),
                    scrollState = settingsScrollState
                )
            } else if (selectedTab == AppTab.Add) {
                ItemForm(draft = addDraft, onChange = { addDraft = it },
                    categories = (items.map { it.categoryLabel } + customCategories).distinct().filterNot { it in hiddenCategories },
                    onCreateCategory = { createCategory(it) },
                    onSave = {
                        repository.add(it)
                        refreshItems()
                        ReminderScheduler.scheduleDailyCheck(context)
                        addDraft = ItemDraft()
                        selectedTab = AppTab.Mine
                        expiryFilter = if (it.isCompleted(today)) ExpiryFilter.Completed else ExpiryFilter.All
                        categoryFilter = null
                        searchQuery = ""
                        scope.launch { snackbarHostState.showSnackbar("物品已保存") }
                    }, modifier = Modifier.fillMaxSize().padding(padding))
            } else LazyColumn(
                state = when (selectedTab) {
                    AppTab.Overview -> overviewListState
                    else -> mineListState
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == AppTab.Overview) {
                    item {
                        Spacer(Modifier.height(2.dp))
                        ExpiryOverviewPanel(
                            overview = expiryOverview(items, today),
                            onFilter = {
                                expiryFilter = it
                                categoryFilter = null
                                searchQuery = ""
                                selectedTab = AppTab.Mine
                            }
                        )
                    }
                    item {
                        CategoryOverview(items = items, savedOrder = categoryOrder, today = today,
                            hiddenCategories = hiddenCategories,
                            onReorder = { visibleOrder ->
                                val barOrder = orderedCategoryBar(
                                    (items.map { it.categoryLabel } + customCategories).distinct()
                                        .filterNot { it in hiddenCategories }, categoryOrder)
                                categoryOrder = reorderVisibleCategoryBar(barOrder, visibleOrder)
                                repository.saveCategoryBarOrder(categoryOrder)
                            },
                            onDelete = {
                                repository.hideCategory(it)
                                hiddenCategories = repository.getHiddenCategories()
                                categoryOrder = repository.getCategoryBarOrder()
                                if (categoryFilter == it) categoryFilter = null
                            }, onCategory = {
                            categoryFilter = it
                            expiryFilter = ExpiryFilter.All
                            searchQuery = ""
                            selectedTab = AppTab.Mine
                        })
                    }
                }
                if (selectedTab == AppTab.Mine) {
                    item {
                        Text("我的物品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    item {
                        CategoryFilters(selected = categoryFilter,
                            categories = (items.map { it.categoryLabel } + customCategories).distinct().filterNot { it in hiddenCategories },
                            savedOrder = categoryOrder,
                            hiddenCategories = hiddenCategories,
                            onSelect = { categoryFilter = it },
                            onReorder = {
                                categoryOrder = it
                                repository.saveCategoryBarOrder(it)
                            },
                            onDelete = {
                                repository.hideCategory(it)
                                hiddenCategories = repository.getHiddenCategories()
                                categoryOrder = repository.getCategoryBarOrder()
                                if (categoryFilter == it) categoryFilter = null
                            },
                            onCreate = { createCategory(it) })
                    }
                    item {
                        BrowseControls(
                            query = searchQuery,
                            filter = expiryFilter,
                            view = homeView,
                            onQueryChange = { searchQuery = it },
                            onFilterChange = { expiryFilter = it },
                            onViewChange = { homeView = it }
                        )
                    }

                    val visibleItems = filterExpiryItems(items, searchQuery, expiryFilter, today = today, categoryLabel = categoryFilter)
                    if (visibleItems.isEmpty()) {
                        item { EmptyState(hasItems = items.isNotEmpty()) }
                    } else if (homeView == HomeView.List) {
                        items(visibleItems, key = { it.id }) { item ->
                            ExpiryItemRow(item = item, today = today, onClick = { editingItem = item })
                        }
                    } else {
                        groupExpiryItemsByMonth(visibleItems, today).forEach { (month, monthItems) ->
                            item(key = "month-$month") { CalendarMonthHeader(month) }
                            items(monthItems, key = { "calendar-${it.id}" }) { item ->
                                ExpiryItemRow(item = item, today = today, onClick = { editingItem = item })
                            }
                        }
                        val undated = visibleItems.filter { it.expireDate.isBlank() }
                        if (undated.isNotEmpty()) {
                            item(key = "no-expiry") { Text("未设置过保日期", fontWeight = FontWeight.SemiBold) }
                            items(undated, key = { "undated-${it.id}" }) { item ->
                                ExpiryItemRow(item = item, today = today, onClick = { editingItem = item })
                            }
                        }
                    }
                }
            }
        }
        editingItem?.let { item ->
            EditItemForm(
                item = item,
                categories = (items.map { it.categoryLabel } + customCategories).distinct().filterNot { it in hiddenCategories },
                onCreateCategory = { createCategory(it) },
                onDismiss = { editingItem = null },
                onSave = {
                    repository.update(it)
                    refreshItems()
                    ReminderScheduler.scheduleDailyCheck(context)
                    editingItem = null
                },
                onDelete = {
                    repository.delete(item.id)
                    refreshItems()
                    editingItem = null
                }
            )
        }
        if (showPrivacyConsent) {
            PrivacyConsentDialog(
                onAccept = {
                    val updated = settings.copy(privacyAccepted = true)
                    settingsRepository.save(updated)
                    settings = updated
                    showPrivacyConsent = false
                },
                onDecline = {
                    showPrivacyConsent = false
                },
            )
        }
    }
}

@Composable
private fun BrowseControls(
    query: String,
    filter: ExpiryFilter,
    view: HomeView,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ExpiryFilter) -> Unit,
    onViewChange: (HomeView) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("我的物品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索物品") },
            shape = RoundedCornerShape(8.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpiryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) },
                    colors = browseChipColors()
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = view == HomeView.List,
                onClick = { onViewChange(HomeView.List) },
                label = { Text("列表") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null, Modifier.size(18.dp)) },
                colors = browseChipColors()
            )
            FilterChip(
                selected = view == HomeView.Calendar,
                onClick = { onViewChange(HomeView.Calendar) },
                label = { Text("日历") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(18.dp)) },
                colors = browseChipColors()
            )
        }
    }
}

@Composable
private fun browseChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun CalendarMonthHeader(month: YearMonth) {
    Text(
        text = "${month.year}年${month.monthValue}月",
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun EmptyState(hasItems: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (hasItems) "没有匹配的物品" else "还没有物品", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasItems) "试试其他关键词或筛选条件" else "从上方添加第一件物品",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpiryItemRow(
    item: ExpiryItem,
    today: LocalDate,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape),
                color = item.status(today).statusColor(),
                content = {}
            )
            Spacer(Modifier.width(12.dp))
            rememberItemPhoto(item.imageBase64)?.let { photo ->
                androidx.compose.foundation.Image(photo, "${item.title}图片", Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        item.categoryLabel,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (item.isCompleted(today)) "已处理" else compactRemainingText(item, today),
                        color = if (item.isCompleted(today)) Color(0xFF8E8E93) else item.status(today).statusColor(),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.expireDate.ifBlank { "未设置过保日期" })
                        item.heldDays(today)?.let { append(" · 已持有${it}天") }
                        item.price?.let { append(" · ${formatPrice(it)}") }
                        if (item.expireDate.isNotBlank() && item.allReminderDays().isNotEmpty()) {
                            append(" · 提前")
                            append(item.allReminderDays().joinToString("/") { "${it}天" })
                        }
                        if (item.repeatRule == RepeatRule.Yearly) append(" · 每年")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (item.costMethod) {
                        CostMethod.Time -> item.dailyCost(today)?.let { "${formatPrice(it)}/天" } ?: "日均成本：暂无法计算（需有效价格和购买日期）"
                        CostMethod.Count -> item.perUseCost()?.let { "${formatPrice(it)}/次 · 已使用${item.useCount}次" } ?: "单次成本：待补充价格或使用次数"
                        CostMethod.None -> "不计算均价"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "编辑${item.title}",
                tint = Color(0xFFAEAEB2)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ExpiryOverviewPanel(
    overview: ExpiryOverview,
    onFilter: (ExpiryFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("物品概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OverviewMetric("在保中", overview.active, Modifier.weight(1f)) { onFilter(ExpiryFilter.InWarranty) }
            OverviewMetric("30天内过保", overview.within30Days, Modifier.weight(1f)) { onFilter(ExpiryFilter.Within30Days) }
            OverviewMetric("已过保", overview.expired, Modifier.weight(1f)) { onFilter(ExpiryFilter.Expired) }
            OverviewMetric("已处理", overview.completed, Modifier.weight(1f)) { onFilter(ExpiryFilter.Completed) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("总资产", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPrice(overview.totalPrice), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            VerticalDivider(Modifier.height(48.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("日均成本", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(overview.dailyCost?.let { "${formatPrice(it)}/天" } ?: "暂无法计算",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(if (overview.dailyCostItemCount < overview.unprocessedCount)
            "所有未处理物品 · 日均成本已计入${overview.dailyCostItemCount}/${overview.unprocessedCount}件（需按时间计算、价格和购买日）"
            else "所有未处理物品（包含已过保）",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CategoryFilters(selected: String?, categories: List<String>, savedOrder: List<String>,
    hiddenCategories: List<String>, onSelect: (String?) -> Unit, onCreate: (String) -> Unit,
    onDelete: (String) -> Unit, onReorder: (List<String>) -> Unit) {
    var showCustom by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val barItems = orderedCategoryBar(categories, savedOrder)
    val scrollState = rememberScrollState()
    val positions = remember { mutableStateMapOf<String, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var draggedCategory by remember { mutableStateOf<String?>(null) }
    var dragCenterX by remember { mutableStateOf(0f) }
    var lastMoveTarget by remember { mutableStateOf<String?>(null) }
    val currentCategories by rememberUpdatedState(barItems)
    val reorder by rememberUpdatedState(onReorder)
    val edgeWidth = with(LocalDensity.current) { 48.dp.toPx() }
    val scrollStep = with(LocalDensity.current) { 8.dp.toPx() }
    fun reorderAtPointer() {
        val source = draggedCategory ?: return
        val target = currentCategories.filter { positions[it] != null }
            .minByOrNull { abs(positions.getValue(it).center.x - dragCenterX) } ?: return
        if (source == target) {
            lastMoveTarget = null
        } else if (lastMoveTarget != target) {
            lastMoveTarget = target
            reorder(moveCategory(currentCategories, source, target))
        }
    }
    LaunchedEffect(draggedCategory) {
        if (draggedCategory == null) return@LaunchedEffect
        while (draggedCategory != null) {
            delay(16)
            val distance = when {
                dragCenterX < viewport.left + edgeWidth -> -scrollStep
                dragCenterX > viewport.right - edgeWidth -> scrollStep
                else -> 0f
            }
            if (distance != 0f) scrollState.scrollBy(distance)
            reorderAtPointer()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned {
            viewport = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scrollState, enabled = draggedCategory == null),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            barItems.forEach { category ->
                key(category) {
                    Box(Modifier.zIndex(if (draggedCategory == category) 1f else 0f)
                        .onGloballyPositioned {
                            positions[category] = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                        }) {
                        FilterChip(selected = when (category) {
                                CATEGORY_ALL -> selected == null
                                CATEGORY_ADD, CATEGORY_DELETE -> false
                                else -> selected == categoryBarLabel(category)
                            },
                            onClick = {
                                if (draggedCategory == null) when (category) {
                                    CATEGORY_ALL -> onSelect(null)
                                    CATEGORY_ADD -> showCustom = true
                                    CATEGORY_DELETE -> showDelete = true
                                    else -> onSelect(categoryBarLabel(category))
                                }
                            },
                            modifier = Modifier.graphicsLayer {
                                if (draggedCategory == category) {
                                    translationX = dragCenterX - (positions[category]?.center?.x ?: dragCenterX)
                                    alpha = 0.8f
                                    shadowElevation = 4.dp.toPx()
                                }
                            }.pointerInput(category) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragCenterX = positions[category]?.center?.x ?: 0f
                                        lastMoveTarget = null
                                        draggedCategory = category
                                    },
                                    onDragEnd = { draggedCategory = null },
                                    onDragCancel = { draggedCategory = null },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragCenterX += amount.x
                                        reorderAtPointer()
                                    }
                                )
                            },
                            label = {
                                when (category) {
                                    CATEGORY_ALL -> Text("全部分类")
                                    CATEGORY_ADD -> Icon(Icons.Default.Add, contentDescription = "新建分类", modifier = Modifier.size(18.dp))
                                    CATEGORY_DELETE -> Icon(Icons.Default.DeleteOutline, contentDescription = "删除分类标签", modifier = Modifier.size(18.dp))
                                    else -> Text(categoryBarLabel(category), maxLines = 1)
                                }
                            }, colors = browseChipColors())
                    }
                }
            }
        }
        }
        Text("长按标签或按钮可拖动排序", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        selected?.let { Text("当前分类：$it", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (showCustom) {
        CategoryDialog(categories = categories, selected = selected, showExisting = false,
            restorableCategories = hiddenCategories,
            onSelect = { onSelect(it); showCustom = false },
            onCreate = onCreate, onDismiss = { showCustom = false })
    }
    if (showDelete) {
        AlertDialog(onDismissRequest = { showDelete = false },
            title = { Text("删除分类标签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择要从分类栏移除的标签。物品和原分类信息保留，可用＋输入同名恢复。",
                        style = MaterialTheme.typography.bodySmall)
                    if (categories.isEmpty()) Text("暂无可删除的分类标签")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(categories) { label ->
                            Row(Modifier.fillMaxWidth().clickable {
                                onDelete(label)
                                showDelete = false
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除$label", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDelete = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun CategoryDialog(categories: List<String>, selected: String?,
    onSelect: (String?) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit,
    allowAutomatic: Boolean = false, showExisting: Boolean = true,
    restorableCategories: List<String> = emptyList()) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("自定义分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("输入名称创建新分类。", style = MaterialTheme.typography.bodySmall)
                if (showExisting) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    if (allowAutomatic) item {
                        TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("自动分类") }
                    }
                    items(categories) { category ->
                        Row(Modifier.fillMaxWidth().clickable { onSelect(category) }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (category == selected) Icon(Icons.Default.CheckCircle, contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it; error = null },
                    label = { Text("新分类名称") }, placeholder = { Text("例如：办公设备") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = error != null,
                    supportingText = { Text(error ?: "1–20个字，创建后可用于新增和编辑物品") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = normalizeCategoryName(name)
                error = when {
                    !isValidCategoryName(normalized) -> "请输入1–20个字的分类名称"
                    normalized !in restorableCategories &&
                        (normalized in categories || normalized in ItemCategory.entries.map { it.label }) -> "此分类已存在，请使用其他名称"
                    else -> null
                }
                if (error == null) {
                    runCatching { onCreate(normalized) }.onSuccess { onSelect(normalized) }
                        .onFailure { error = it.message ?: "创建失败，请重试" }
                }
            }) { Text("创建并选择") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun OverviewMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("隐私保护说明") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("请阅读《记物本隐私政策》：", fontWeight = FontWeight.SemiBold)
                Text(PRIVACY_POLICY_TEXT)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("同意并继续") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("不同意") } }
    )
}

internal fun formatPrice(price: Double): String {
    val text = if (price == price.toLong().toDouble()) {
        "%,d".format(price.toLong())
    } else {
        "%,.2f".format(price)
    }
    return "¥$text"
}

private fun compactRemainingText(item: ExpiryItem, today: LocalDate): String {
    val days = item.daysRemaining(today) ?: return "未设过保日"
    return when {
        days < 0 -> "已过保${-days}天"
        days == 0L -> "今天过保"
        days < 365 -> "剩${days}天"
        else -> "剩${days / 365}年"
    }
}

private fun ExpiryStatus.statusColor(): Color {
    return when (this) {
        ExpiryStatus.Safe -> Color(0xFF34C759)
        ExpiryStatus.Soon -> Color(0xFFFFCC00)
        ExpiryStatus.Urgent -> Color(0xFFFF3B30)
        ExpiryStatus.Expired, ExpiryStatus.NoWarranty -> Color(0xFF8E8E93)
    }
}
