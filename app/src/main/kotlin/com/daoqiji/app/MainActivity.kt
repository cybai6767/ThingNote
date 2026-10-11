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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
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
    var input by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(repository.getAll()) }
    var editingItem by remember { mutableStateOf<ExpiryItem?>(null) }
    var selectedTab by remember { mutableStateOf(AppTab.Overview) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var customCategories by remember { mutableStateOf(repository.getCustomCategories()) }
    var categoryOrder by remember { mutableStateOf(repository.getCategoryBarOrder()) }
    var hiddenCategories by remember { mutableStateOf(repository.getHiddenCategories()) }
    var addCategory by remember { mutableStateOf<String?>(null) }
    val overviewListState = rememberLazyListState()
    val addListState = rememberLazyListState()
    val mineListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()
    var showPrivacyConsent by remember { mutableStateOf(!settings.privacyAccepted) }
    var searchQuery by remember { mutableStateOf("") }
    var expiryFilter by remember { mutableStateOf(ExpiryFilter.All) }
    var homeView by remember { mutableStateOf(HomeView.List) }
    var notificationAllowed by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    val parsedInput = remember(input, priceInput, settings.defaultRemindBeforeDays) {
        ExpiryParser.parse(input, settings.defaultRemindBeforeDays)?.withPriceInput(priceInput)
    }
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
            } else LazyColumn(
                state = when (selectedTab) {
                    AppTab.Overview -> overviewListState
                    AppTab.Add -> addListState
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
                            overview = expiryOverview(items),
                            onFilter = {
                                expiryFilter = it
                                categoryFilter = null
                                searchQuery = ""
                                selectedTab = AppTab.Mine
                            }
                        )
                    }
                    item {
                        CategoryOverview(items = items, customCategories = customCategories,
                            hiddenCategories = hiddenCategories, onCategory = {
                            categoryFilter = it
                            expiryFilter = ExpiryFilter.All
                            searchQuery = ""
                            selectedTab = AppTab.Mine
                        })
                    }
                }
                if (selectedTab == AppTab.Add) {
                    item {
                        QuickTemplates(onSelect = {
                            input = templateInput(it)
                            priceInput = null
                            addCategory = null
                        })
                    }

                    item {
                        InputWorkspace(
                            input = input,
                            parsed = parsedInput,
                            priceInput = priceInput ?: parsedInput?.price?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(),
                            priceError = !isValidPriceInput(priceInput),
                            category = addCategory,
                            customCategories = customCategories,
                            onCategoryChange = { addCategory = it },
                            onCategoryCreate = { createCategory(it) },
                            onPriceChange = { priceInput = it },
                            onInputChange = { input = it },
                            onClear = { input = ""; priceInput = null; addCategory = null },
                            onAddClick = {
                                val parsed = parsedInput
                                if (!isValidPriceInput(priceInput)) {
                                    scope.launch { snackbarHostState.showSnackbar("金额请输入非负数字，最多两位小数") }
                                } else if (parsed == null) {
                                    scope.launch { snackbarHostState.showSnackbar("没识别到有效日期") }
                                } else {
                                    repository.add(parsed, addCategory)
                                    ReminderScheduler.scheduleDailyCheck(context)
                                    refreshItems()
                                    input = ""
                                    priceInput = null
                                    addCategory = null
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar("已添加：${parsed.title}", actionLabel = "查看")
                                        if (result == SnackbarResult.ActionPerformed) {
                                            searchQuery = ""
                                            categoryFilter = null
                                            expiryFilter = ExpiryFilter.All
                                            selectedTab = AppTab.Mine
                                        }
                                    }
                                }
                            }
                        )
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

                    val visibleItems = filterExpiryItems(items, searchQuery, expiryFilter, categoryLabel = categoryFilter)
                    if (visibleItems.isEmpty()) {
                        item { EmptyState(hasItems = items.isNotEmpty()) }
                    } else if (homeView == HomeView.List) {
                        items(visibleItems, key = { it.id }) { item ->
                            ExpiryItemRow(item = item, onClick = { editingItem = item })
                        }
                    } else {
                        groupExpiryItemsByMonth(visibleItems).forEach { (month, monthItems) ->
                            item(key = "month-$month") { CalendarMonthHeader(month) }
                            items(monthItems, key = { "calendar-${it.id}" }) { item ->
                                ExpiryItemRow(item = item, onClick = { editingItem = item })
                            }
                        }
                    }
                }
            }
        }
        editingItem?.let { item ->
            EditItemDialog(
                item = item,
                customCategories = customCategories,
                onCategoryCreate = { createCategory(it) },
                onDismiss = { editingItem = null },
                onSave = {
                    repository.update(it)
                    refreshItems()
                    editingItem = null
                },
                onComplete = {
                    repository.update(item.copy(lifecycle = ItemLifecycle.Completed))
                    refreshItems()
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
private fun InputWorkspace(
    input: String,
    parsed: ParsedExpiry?,
    priceInput: String,
    priceError: Boolean,
    category: String?,
    customCategories: List<String>,
    onCategoryChange: (String?) -> Unit,
    onCategoryCreate: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onClear: () -> Unit,
    onAddClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "添加物品",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "一句话输入物品、金额和过保时间，金额也可单独填写",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 3,
            placeholder = { Text("例如：手机3999元，2027年3月1日过保，提前30天和7天提醒") },
            shape = RoundedCornerShape(8.dp)
        )

        OutlinedTextField(
            value = priceInput,
            onValueChange = onPriceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("金额（可选）") },
            placeholder = { Text("例如：103.55") },
            prefix = { Text("¥") },
            supportingText = {
                Text(if (priceError) "请输入非负数字，最多两位小数"
                    else "自动回填识别金额，可手动修改；清空则不记录金额")
            },
            isError = priceError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(8.dp)
        )

        CategorySelectionField(selected = category, customCategories = customCategories,
            onSelect = onCategoryChange, onCreate = onCategoryCreate, allowAutomatic = true)

        if (parsed != null) {
            ParsedConfirmation(parsed = parsed)
        }

        if (input.isNotBlank() || priceInput.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClear) {
                    Text("清空输入")
                }
            }
        }

        Button(
            onClick = onAddClick,
            enabled = input.isNotBlank() && !priceError,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("确认添加", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ParsedConfirmation(parsed: ParsedExpiry) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val green = if (dark) Color(0xFF62D694) else Color(0xFF1F8A4C)
    Card(
        colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF18261F) else Color(0xFFF5FAF7)),
        border = BorderStroke(1.dp, if (dark) Color(0xFF304B3C) else Color(0xFFD8EEE0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = green
                )
                Spacer(Modifier.width(8.dp))
                Text("已识别", color = green, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            ParsedRow(label = "事项", value = parsed.title)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ParsedRow(label = "到期时间", value = parsed.expireDate)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ParsedRow(label = "金额", value = parsed.price?.let { formatPrice(it) } ?: "未填写（可选）")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ParsedRow(
                label = "提前提醒",
                value = (listOf(parsed.remindBeforeDays) + parsed.additionalRemindBeforeDays)
                    .distinct().joinToString(" / ") { "${it}天" }
            )
            if (parsed.repeatRule == RepeatRule.Yearly) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ParsedRow(label = "重复", value = parsed.repeatRule.label)
            }
        }
    }
}

@Composable
private fun ParsedRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, fontWeight = FontWeight.Medium)
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
                color = item.status().statusColor(),
                content = {}
            )
            Spacer(Modifier.width(12.dp))
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
                        text = if (item.lifecycle == ItemLifecycle.Completed) "已处理" else compactRemainingText(item),
                        color = if (item.lifecycle == ItemLifecycle.Completed) Color(0xFF8E8E93) else item.status().statusColor(),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.expireDate)
                        item.heldDays()?.let { append(" · 已持有${it}天") }
                        item.price?.let { append(" · ${formatPrice(it)}") }
                        append(" · ")
                        append(item.allReminderDays().joinToString("/") { "${it}天" })
                        if (item.repeatRule == RepeatRule.Yearly) append(" · 每年")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.dailyCost()?.let { "${formatPrice(it)}/天" } ?: "日均成本：暂无法计算",
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
private fun EditItemDialog(
    item: ExpiryItem,
    customCategories: List<String>,
    onCategoryCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ExpiryItem) -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var date by remember(item.id) { mutableStateOf(item.expireDate) }
    var purchaseDate by remember(item.id) { mutableStateOf(item.purchaseDate.orEmpty()) }
    var warrantyMonths by remember(item.id) {
        mutableStateOf(if (item.warrantyMonths > 0) item.warrantyMonths.toString() else "")
    }
    var price by remember(item.id) {
        mutableStateOf(
            item.price?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            }.orEmpty()
        )
    }
    var remindDays by remember(item.id) { mutableStateOf(item.allReminderDays().joinToString(",")) }
    var repeatYearly by remember(item.id) { mutableStateOf(item.repeatRule == RepeatRule.Yearly) }
    var category by remember(item.id) { mutableStateOf<String?>(item.categoryLabel) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑物品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("物品名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("过保日 YYYY-MM-DD") },
                    supportingText = { Text("留空则按购买日 + 保修月数自动计算") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = purchaseDate,
                    onValueChange = { purchaseDate = it },
                    label = { Text("购买日 YYYY-MM-DD（可选）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = warrantyMonths,
                    onValueChange = { warrantyMonths = it.filter(Char::isDigit) },
                    label = { Text("保修月数（可选）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { input -> price = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("金额（可选）") },
                    supportingText = { Text("例如：3000") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = remindDays,
                    onValueChange = { remindDays = it.filter { char -> char.isDigit() || char in ",，、 " } },
                    label = { Text("提醒天数，可填多个") },
                    supportingText = { Text("例如：30,7,1") },
                    singleLine = true
                )
                CategorySelectionField(selected = category, customCategories = customCategories,
                    onSelect = { category = it }, onCreate = onCategoryCreate)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { repeatYearly = !repeatYearly },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
                    Text("每年重复")
                }
                if (item.renewalHistory.isNotEmpty()) {
                    Text(
                        "续期记录：${item.renewalHistory.joinToString(" → ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val days = remindDays.split(Regex("""[,\s，、]+"""))
                        .mapNotNull(String::toIntOrNull).distinct()
                    val months = warrantyMonths.toIntOrNull() ?: 0
                    val priceValue = if (price.isBlank()) null else price.toDoubleOrNull()
                    val validPurchase = purchaseDate.isBlank() ||
                        runCatching { LocalDate.parse(purchaseDate) }.isSuccess
                    val effectiveDate = date.ifBlank {
                        purchaseDate.takeIf(String::isNotBlank)
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            ?.takeIf { months > 0 }
                            ?.plusMonths(months.toLong())?.toString().orEmpty()
                    }
                    val validDate = runCatching { LocalDate.parse(effectiveDate) }.isSuccess
                    error = when {
                        title.isBlank() -> "物品名称不能为空"
                        !validPurchase -> "购买日格式应为 YYYY-MM-DD"
                        months !in 0..1200 -> "保修月数应为 0–1200"
                        price.isNotBlank() && priceValue == null -> "金额格式不正确"
                        !validDate -> "日期格式应为 YYYY-MM-DD"
                        days.isEmpty() -> "提醒天数不能为空"
                        days.any { it !in 0..3650 } -> "提醒天数应为 0–3650"
                        item.lifecycle == ItemLifecycle.Completed && effectiveDate == item.expireDate -> "续期请填写新的到期日"
                        else -> null
                    }
                    if (error == null) {
                        val base = if (item.lifecycle == ItemLifecycle.Completed) item.renew(effectiveDate)
                        else item.copy(expireDate = effectiveDate)
                        onSave(base.copy(
                            title = title.trim(),
                            purchaseDate = purchaseDate.takeIf(String::isNotBlank),
                            warrantyMonths = months,
                            price = priceValue,
                            remindBeforeDays = days.first(),
                            additionalRemindBeforeDays = days.drop(1),
                            repeatRule = if (repeatYearly) RepeatRule.Yearly else RepeatRule.None,
                            category = ItemCategory.entries.firstOrNull { it.label == category } ?: ItemCategory.Other,
                            customCategory = category?.takeIf { name -> ItemCategory.entries.none { it.label == name } }
                        ))
                    }
                }
            ) {
                Text(if (item.lifecycle == ItemLifecycle.Completed) "续期" else "保存")
            }
        },
        dismissButton = {
            Row {
                if (item.lifecycle == ItemLifecycle.Active) {
                    TextButton(onClick = onComplete) { Text("已处理") }
                }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
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
            "所有未处理物品 · 日均成本已计入${overview.dailyCostItemCount}/${overview.unprocessedCount}件（需金额和购买日）"
            else "所有未处理物品（包含已过保）",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CategoryOverview(items: List<ExpiryItem>, customCategories: List<String>,
    hiddenCategories: List<String>, onCategory: (String) -> Unit) {
    val visibleCategories = (ItemCategory.entries.map { it.label } + customCategories)
        .distinct().filterNot { it in hiddenCategories }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("物品分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("未处理物品 · 点击分类查看", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (visibleCategories.isEmpty()) {
            Text("暂无分类，可在“我的”页面通过＋新增", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleCategories.chunked(2).forEach { categories ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                categories.forEach { category ->
                    val categoryItems = items.filter { it.categoryLabel == category && it.lifecycle == ItemLifecycle.Active }
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onCategory(category) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(category, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(categoryItems.size.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("件", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatPrice(categoryItems.sumOf { it.price ?: 0.0 }),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (categories.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
private fun CategorySelectionField(selected: String?, customCategories: List<String>,
    onSelect: (String?) -> Unit, onCreate: (String) -> Unit, allowAutomatic: Boolean = false) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)) {
        Text("分类：${selected ?: "自动分类"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (showPicker) {
        CategoryDialog(categories = (ItemCategory.entries.map { it.label } + customCategories).distinct(),
            selected = selected, allowAutomatic = allowAutomatic,
            onSelect = { onSelect(it); showPicker = false }, onCreate = onCreate,
            onDismiss = { showPicker = false })
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
private fun QuickTemplates(onSelect: (ItemCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("快捷添加", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemCategory.entries.filterNot { it == ItemCategory.Other }.forEach { category ->
                FilterChip(
                    selected = false,
                    onClick = { onSelect(category) },
                    label = { Text(category.label) }
                )
            }
        }
    }
}

private fun templateInput(category: ItemCategory): String = when (category) {
    ItemCategory.Digital -> "手机3999元，2027年3月1日过保，提前30天和7天提醒"
    ItemCategory.Furniture -> "沙发2499元，2027年6月30日过保，提前30天提醒"
    ItemCategory.Clothing -> "羽绒服599元，2026年12月31日过保，提前7天提醒"
    ItemCategory.Beauty -> "面霜199元，2026年10月15日过期，提前7天和1天提醒"
    ItemCategory.Sports -> "跑鞋399元，2026年11月30日过保，提前7天提醒"
    ItemCategory.Other -> "物品99元，2027年3月1日过保，提前7天提醒"
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

private fun formatPrice(price: Double): String {
    val text = if (price == price.toLong().toDouble()) {
        "%,d".format(price.toLong())
    } else {
        "%,.2f".format(price)
    }
    return "¥$text"
}

private fun compactRemainingText(item: ExpiryItem): String {
    val days = item.daysRemaining()
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
        ExpiryStatus.Expired -> Color(0xFF8E8E93)
    }
}
