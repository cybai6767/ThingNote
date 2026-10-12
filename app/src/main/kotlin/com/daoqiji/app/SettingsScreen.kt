package com.daoqiji.app

import android.app.TimePickerDialog
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.text.format.Formatter

@Composable
internal fun SettingsContent(
    settings: AppSettings,
    notificationAllowed: Boolean,
    onChange: (AppSettings) -> Unit,
    onNotificationSettings: () -> Unit,
    onTestNotification: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    var showDays by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var cacheResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().removeSuffix("-debug")
    }

    Column(modifier.verticalScroll(scrollState).padding(horizontal = 20.dp)) {
        SettingsHeading("外观")
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            ThemeChoice.entries.forEach { choice ->
                Row(
                    Modifier.selectable(
                        selected = settings.theme == choice,
                        role = Role.RadioButton,
                        onClick = { onChange(settings.copy(theme = choice)) }
                    ).heightIn(min = 48.dp).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.theme == choice, onClick = null)
                    Text(choice.label, modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SettingsHeading("提醒")
        SettingsRow("通知权限", if (notificationAllowed) "已开启" else "未开启", onNotificationSettings)
        SettingsRow("发送测试通知", "", onTestNotification)
        SettingsRow("每日提醒时间", settings.reminderTime.toString(), { showTime = true })
        SettingsRow("默认提前提醒", "${settings.defaultRemindBeforeDays} 天", { showDays = true })
        Text(
            "默认提前天数仅用于未注明提前时间的新事项。提醒可能因系统省电策略延迟。",
            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsHeading("数据")
        SettingsRow("导出本地备份", "", onExportBackup)
        SettingsRow("从备份恢复", "", onImportBackup)
        SettingsRow("清理缓存", if (clearingCache) "清理中…" else "", {
            if (!clearingCache) scope.launch {
                clearingCache = true
                cacheResult = runCatching { withContext(Dispatchers.IO) { clearAppCache(context.applicationContext) } }
                    .fold(onSuccess = {
                        val amount = Formatter.formatFileSize(context, it.clearedBytes)
                        when {
                            it.failedFiles > 0 -> "已释放 $amount，部分缓存正在使用，暂未清除。"
                            it.clearedBytes == 0L -> "暂无可清理的缓存。"
                            else -> "已释放 $amount 缓存空间。"
                        }
                    }, onFailure = { "缓存清理失败，请稍后重试。" })
                clearingCache = false
            }
        })
        Text(
            "备份文件由你自行保存，恢复时会替换当前事项。清理缓存仅删除临时文件，保留物品、图片、备注和设置。",
            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsHeading("关于")
        SettingsRow("意见反馈", "", { showFeedback = true })
        SettingsRow("隐私说明", "", { showPrivacy = true })
        SettingsRow("当前版本", version)
        Text("记物本", modifier = Modifier.padding(top = 28.dp, bottom = 32.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }

    if (showDays) {
        var days by remember { mutableStateOf(settings.defaultRemindBeforeDays.toString()) }
        val value = days.toIntOrNull()
        val valid = value != null && value in 0..3650
        AlertDialog(
            onDismissRequest = { showDays = false },
            title = { Text("默认提前提醒") },
            text = {
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("提前天数") },
                    supportingText = { Text("0–3650 天，0 表示到期当天") },
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    onChange(settings.copy(defaultRemindBeforeDays = value!!))
                    showDays = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showDays = false }) { Text("取消") } }
        )
    }

    cacheResult?.let { message ->
        AlertDialog(onDismissRequest = { cacheResult = null }, title = { Text("清理缓存") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { cacheResult = null }) { Text("确定") } })
    }

    if (showTime) {
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        DisposableEffect(Unit) {
            val picker = TimePickerDialog(
                context,
                if (dark) android.R.style.Theme_Material_Dialog_Alert
                else android.R.style.Theme_Material_Light_Dialog_Alert,
                { _, hour, minute ->
                    onChange(settings.copy(reminderTime = LocalTime.of(hour, minute)))
                    showTime = false
                }, settings.reminderTime.hour, settings.reminderTime.minute, true
            )
            picker.setTitle("每日提醒时间")
            picker.setButton(TimePickerDialog.BUTTON_POSITIVE, "保存", picker)
            picker.setButton(TimePickerDialog.BUTTON_NEGATIVE, "取消") { _, _ -> showTime = false }
            picker.setOnDismissListener { showTime = false }
            picker.show()
            onDispose { picker.dismiss() }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私说明") },
            text = {
                Text(
                    PRIVACY_POLICY_TEXT,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("关闭") } }
        )
    }

    if (showFeedback) {
        AlertDialog(
            onDismissRequest = { showFeedback = false },
            title = { Text("意见反馈") },
            text = { androidx.compose.foundation.text.selection.SelectionContainer {
                Text("xiaobai67\n2168804617@qq.com")
            } },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_SENDTO,
                            Uri.parse("mailto:2168804617@qq.com")))
                        showFeedback = false
                    } catch (_: ActivityNotFoundException) {
                        // The selectable address remains available without a mail app.
                    }
                }) { Text("写邮件") }
            },
            dismissButton = { TextButton(onClick = { showFeedback = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun SettingsHeading(title: String) {
    Text(title, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
