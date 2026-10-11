package com.daoqiji.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemForm(
    draft: ItemDraft,
    onChange: (ItemDraft) -> Unit,
    categories: List<String>,
    onCreateCategory: (String) -> Unit,
    onSave: (ExpiryItem) -> Unit,
    modifier: Modifier = Modifier,
    original: ExpiryItem? = null,
    onDelete: (() -> Unit)? = null
) {
    var error by remember { mutableStateOf<String?>(null) }
    var dateField by remember { mutableStateOf<String?>(null) }
    var showCategory by remember { mutableStateOf(false) }
    var showCost by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var importingImage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDraft by rememberUpdatedState(draft)
    val change by rememberUpdatedState(onChange)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importingImage = true
            runCatching { withContext(Dispatchers.IO) { readItemPhoto(context, uri) } }
                .onSuccess { change(currentDraft.copy(imageBase64 = it)) }
                .onFailure { error = "图片读取失败，请选择其他图片" }
            importingImage = false
        }
    }
    Column(modifier.imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (original == null) "添加物品" else "物品信息", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Text("填写名称即可保存，其他信息可以稍后补充", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.fillMaxWidth().clickable(enabled = !importingImage) {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                val photo = rememberItemPhoto(draft.imageBase64)
                if (photo != null) Image(photo, "物品图片，点击更换", Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Fit)
                else Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (importingImage) "正在处理图片…" else "点击添加物品图片（可选）")
                }
            }
            if (draft.imageBase64 != null) Row {
                TextButton(enabled = !importingImage, onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("更换图片") }
                TextButton(enabled = !importingImage, onClick = { onChange(draft.copy(imageBase64 = null)) }) { Text("移除图片") }
            }
            FormText("物品名称 *", draft.title, { onChange(draft.copy(title = it)) })
            FormChoice("物品分类", draft.category) { showCategory = true }
            FormText("购买价格（可选）", draft.price, { onChange(draft.copy(price = it)) }, KeyboardType.Decimal, prefix = "¥ ")
            FormChoice("购买日期（可选）", draft.purchaseDate.ifBlank { "请选择" },
                onClear = if (draft.purchaseDate.isNotBlank()) ({ onChange(draft.copy(purchaseDate = "")) }) else null) { dateField = "购买日期" }
            HorizontalDivider()
            Text("保修与提醒", fontWeight = FontWeight.SemiBold)
            FormChoice("过保日期（可选）", draft.expireDate.ifBlank { "未设置" },
                onClear = if (draft.expireDate.isNotBlank()) ({ onChange(draft.copy(expireDate = "")) }) else null) { dateField = "过保日期" }
            FormText("保修月数（可选）", draft.warrantyMonths, { onChange(draft.copy(warrantyMonths = it)) }, KeyboardType.Number)
            Text(if (draft.expireDate.isBlank()) "选择购买日期并填写保修月数，可自动计算过保日期；都留空则不设过保日期。"
                else "已手动选择过保日期，以该日期为准。清除后可按购买日期和保修月数计算。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (draft.expireDate.isBlank()) {
                val calculated = runCatching {
                    val months = draft.warrantyMonths.toIntOrNull()?.takeIf { it in 1..1200 }
                    months?.let { LocalDate.parse(draft.purchaseDate).plusMonths(it.toLong()) }
                }.getOrNull()
                calculated?.let { Text("计算结果：$it", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall) }
            }
            val hasWarranty = draft.expireDate.isNotBlank() || (draft.purchaseDate.isNotBlank() && (draft.warrantyMonths.toIntOrNull() ?: 0) > 0)
            if (hasWarranty) {
                FormText("提前提醒天数（可选）", draft.reminderDays, { onChange(draft.copy(reminderDays = it)) }, KeyboardType.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 7, 1).forEach { day ->
                        val selected = draft.reminderDays.split(Regex("[,，、\\s]+")).mapNotNull(String::toIntOrNull)
                        FilterChip(selected = day in selected, onClick = {
                            onChange(draft.copy(reminderDays = (if (day in selected) selected - day else selected + day)
                                .distinct().sortedDescending().joinToString(",")))
                        }, label = { Text("${day}天") })
                    }
                }
                Text("多个用逗号分隔，0 表示当天；留空则不提醒。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.repeatYearly, { onChange(draft.copy(repeatYearly = it)) })
                    Text("每年重复")
                }
            } else Text("未设置过保日期，不发送保修提醒，仍计入未处理物品和资产。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("使用与成本", fontWeight = FontWeight.SemiBold)
            FormChoice("计算方式", draft.costMethod.label) { showCost = true }
            when (draft.costMethod) {
                CostMethod.Time -> Text("购买价格 ÷ 持有天数；购买当天按 1 天计算。退役后截止到退役日期。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CostMethod.Count -> {
                    FormText("使用次数", draft.useCount, { onChange(draft.copy(useCount = it)) }, KeyboardType.Number)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("购买价格 ÷ 使用次数", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onChange(draft.copy(useCount = ((draft.useCount.toIntOrNull() ?: 0).toLong() + 1)
                            .coerceAtMost(Int.MAX_VALUE.toLong()).toString())) }) { Text("使用 +1") }
                    }
                }
                CostMethod.None -> Text("计入资产，不参与日均成本或单次成本计算。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FormChoice("退役日期（可选）", draft.retirementDate.ifBlank { "未设置" },
                onClear = if (draft.retirementDate.isNotBlank()) ({ onChange(draft.copy(retirementDate = "")) }) else null) { dateField = "退役日期" }
            Text("到达退役日期后自动归入已处理；未来日期作为计划退役日期。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(draft.note, { onChange(draft.copy(note = it)) }, Modifier.fillMaxWidth(),
                label = { Text("备注（可选）") }, minLines = 3, maxLines = 8, shape = RoundedCornerShape(12.dp))
            if (original != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.completed, { onChange(draft.copy(completed = it)) })
                    Text("标记为已处理")
                }
                if (original.renewalHistory.isNotEmpty()) Text("续期记录：${original.renewalHistory.joinToString(" → ")}",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showDelete = true }) { Text("删除物品", color = MaterialTheme.colorScheme.error) }
            } else TextButton(onClick = { showClear = true }) { Text("清空填写") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider()
        Button(onClick = {
            runCatching { draft.toItem(original) }.onSuccess(onSave).onFailure { error = it.message ?: "请检查填写的信息" }
        }, enabled = !importingImage, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp)) { Text(if (original == null) "保存物品" else "保存修改") }
        // Keep a validation error visible even when its field is above the current scroll position.
        error?.let { Text(it, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
    }
    dateField?.let { field ->
        val value = when (field) { "购买日期" -> draft.purchaseDate; "过保日期" -> draft.expireDate; else -> draft.retirementDate }
        DateWheelSheet(field, value, onDismiss = { dateField = null }) { selected ->
            onChange(when (field) {
                "购买日期" -> draft.copy(purchaseDate = selected)
                "过保日期" -> draft.copy(expireDate = selected)
                else -> draft.copy(retirementDate = selected)
            })
            dateField = null
        }
    }
    if (showCategory) {
        val labels = (listOf(draft.category) + categories + "未分类").distinct()
        ChoiceWheelSheet("物品分类", labels, draft.category, onDismiss = { showCategory = false },
            extra = { TextButton(onClick = { showCategory = false; showNewCategory = true }) { Text("+ 新建分类") } }) {
            onChange(draft.copy(category = it)); showCategory = false
        }
    }
    if (showCost) ChoiceWheelSheet("计算方式", CostMethod.entries.map { it.label }, draft.costMethod.label,
        onDismiss = { showCost = false }) { label ->
        onChange(draft.copy(costMethod = CostMethod.entries.first { it.label == label })); showCost = false
    }
    if (showNewCategory) AlertDialog(onDismissRequest = { showNewCategory = false }, title = { Text("新建分类") },
        text = { OutlinedTextField(categoryName, { categoryName = it }, label = { Text("分类名称，1–20个字") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            runCatching { onCreateCategory(categoryName) }.onSuccess {
                onChange(draft.copy(category = normalizeCategoryName(categoryName))); categoryName = ""; showNewCategory = false; error = null
            }.onFailure { error = it.message; showNewCategory = false }
        }) { Text("创建") } }, dismissButton = { TextButton(onClick = { showNewCategory = false }) { Text("取消") } })
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("删除此物品？") },
        text = { Text("此操作会删除物品记录及图片。") },
        confirmButton = { TextButton(onClick = { onDelete?.invoke(); showDelete = false }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } })
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("清空当前填写内容？") },
        confirmButton = { TextButton(onClick = { onChange(ItemDraft()); error = null; showClear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } })
}

@Composable
private fun FormText(label: String, value: String, onValueChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text, prefix: String? = null) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(12.dp),
        prefix = prefix?.let { { Text(it) } })
}

@Composable
private fun FormChoice(label: String, value: String, onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp).heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f))
                if (onClear != null) TextButton(onClick = onClear) { Text("清除") }
                Icon(Icons.Default.KeyboardArrowDown, "选择$label", Modifier.padding(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateWheelSheet(title: String, value: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val initial = remember { runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now()) }
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }
    val lastDay = YearMonth.of(year, month).lengthOfMonth()
    val selectedDay = day.coerceAtMost(lastDay)
    LaunchedEffect(lastDay) { day = day.coerceAtMost(lastDay) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                NumberWheel(minOf(1900, initial.year)..maxOf(2100, initial.year), year, "年", Modifier.weight(1f)) { year = it }
                NumberWheel(1..12, month, "月", Modifier.weight(1f)) { month = it }
                NumberWheel(1..lastDay, selectedDay, "日", Modifier.weight(1f)) { day = it }
            }
            SheetActions(onDismiss) { onConfirm(LocalDate.of(year, month, selectedDay).toString()) }
        }
    }
}

@Composable
private fun NumberWheel(range: IntRange, value: Int, suffix: String, modifier: Modifier, onValue: (Int) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val currentOnValue by rememberUpdatedState(onValue)
    AndroidView(modifier = modifier.height(190.dp), factory = { context -> NumberPicker(context).apply {
        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        wrapSelectorWheel = false
        setOnValueChangedListener { _, _, newValue -> currentOnValue(newValue) }
    } }, update = { picker ->
        picker.displayedValues = null
        picker.minValue = range.first; picker.maxValue = range.last
        picker.displayedValues = range.map { "$it$suffix" }.toTypedArray()
        picker.value = value.coerceIn(range)
        if (android.os.Build.VERSION.SDK_INT >= 29) picker.textColor = color
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceWheelSheet(title: String, labels: List<String>, selected: String, onDismiss: () -> Unit,
    extra: @Composable () -> Unit = {}, onConfirm: (String) -> Unit) {
    var index by remember { mutableIntStateOf(labels.indexOf(selected).coerceAtLeast(0)) }
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); extra() }
            AndroidView(modifier = Modifier.fillMaxWidth().height(190.dp), factory = { context -> NumberPicker(context).apply {
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                minValue = 0; maxValue = labels.lastIndex; displayedValues = labels.toTypedArray()
                value = index; wrapSelectorWheel = false
                setOnValueChangedListener { _, _, value -> index = value }
            } }, update = { if (android.os.Build.VERSION.SDK_INT >= 29) it.textColor = color })
            SheetActions(onDismiss) { onConfirm(labels[index]) }
        }
    }
}

@Composable
private fun SheetActions(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onDismiss, Modifier.weight(1f)) { Text("取消") }
        Button(onConfirm, Modifier.weight(1f)) { Text("确定") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditItemForm(item: ExpiryItem, categories: List<String>, onCreateCategory: (String) -> Unit,
    onDismiss: () -> Unit, onSave: (ExpiryItem) -> Unit, onDelete: () -> Unit) {
    var draft by remember(item.id) { mutableStateOf(ItemDraft.from(item)) }
    var discard by remember { mutableStateOf(false) }
    val dismiss = { if (draft != ItemDraft.from(item)) discard = true else onDismiss() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = { TopAppBar(title = { Text("编辑物品") }, navigationIcon = {
                IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            }) }) { padding ->
                ItemForm(draft, { draft = it }, categories, onCreateCategory, onSave,
                    Modifier.fillMaxSize().padding(padding), item, onDelete)
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    }
}

@Composable
internal fun rememberItemPhoto(base64: String?) = remember(base64) {
    base64?.let { runCatching {
        require(it.length <= 400000)
        val bytes = Base64.decode(it, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1024) inSampleSize *= 2
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrNull() }
}

/** Import a bounded copy so photos survive picker access expiry and JSON backup/restore. */
private fun readItemPhoto(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
    }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("无法读取图片")
    val orientation = runCatching { resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } }.getOrNull()
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(90f) }
        }
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    val scale = minOf(1f, 1024f / maxOf(rotated.width, rotated.height))
    val resized = Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt().coerceAtLeast(1),
        (rotated.height * scale).toInt().coerceAtLeast(1), true)
    if (resized !== rotated) rotated.recycle()
    try {
        var quality = 85
        val output = ByteArrayOutputStream()
        do {
            output.reset(); resized.compress(Bitmap.CompressFormat.JPEG, quality, output); quality -= 10
        } while (output.size() > 256 * 1024 && quality >= 25)
        require(output.size() <= 256 * 1024) { "图片过大" }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    } finally { resized.recycle() }
}
