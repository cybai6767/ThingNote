package com.daoqiji.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun CategoryOverview(
    items: List<ExpiryItem>, savedOrder: List<String>, hiddenCategories: List<String>, today: LocalDate,
    onCategory: (String) -> Unit, onReorder: (List<String>) -> Unit, onDelete: (String) -> Unit
) {
    val categories = overviewCategoryLabels(items, hiddenCategories, savedOrder)
    val currentCategories by rememberUpdatedState(categories)
    val reorder by rememberUpdatedState(onReorder)
    val select by rememberUpdatedState(onCategory)
    val positions = remember { mutableStateMapOf<String, Rect>() }
    val scope = rememberCoroutineScope()
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragCenter by remember { mutableStateOf(Offset.Zero) }
    var lastTarget by remember { mutableStateOf<String?>(null) }
    var deleteCategory by remember { mutableStateOf<String?>(null) }
    val deleting by rememberUpdatedState(deleteCategory)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("物品分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (deleteCategory != null) TextButton(onClick = { deleteCategory = null }) { Text("完成") }
        }
        Text("长按3秒拖动排序 · 长按5秒显示删除按钮", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (categories.isEmpty()) {
            Text("暂无物品分类", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Keep every card under the same parent so reordering does not cancel its active gesture.
        Layout(modifier = Modifier.fillMaxWidth(), content = {
            categories.forEach { category ->
                key(category) {
                    val categoryItems = items.filter { it.categoryLabel == category && !it.isCompleted(today) }
                    Box(Modifier.zIndex(if (dragged == category) 1f else 0f).onGloballyPositioned {
                        positions[category] = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                    }) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                if (dragged == category) {
                                    val center = positions[category]?.center ?: dragCenter
                                    translationX = dragCenter.x - center.x
                                    translationY = dragCenter.y - center.y
                                    shadowElevation = 4.dp.toPx()
                                }
                            }.pointerInput(category) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val endedEarly = withTimeoutOrNull(3_000L) {
                                        var finished = false
                                        while (!finished) {
                                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                            finished = change == null || !change.pressed || change.isConsumed ||
                                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                        }
                                        true
                                    }
                                    if (endedEarly != null) return@awaitEachGesture
                                    val bounds = positions[category] ?: return@awaitEachGesture
                                    dragged = category
                                    dragCenter = bounds.center
                                    lastTarget = null
                                    var travel = 0f
                                    val revealDelete = scope.launch {
                                        delay(2_000L)
                                        deleteCategory = category
                                    }
                                    try {
                                        while (true) {
                                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                            val delta = change.positionChange()
                                            change.consume()
                                            if (!change.pressed) break
                                            travel += delta.getDistance()
                                            if (travel > viewConfiguration.touchSlop) {
                                                revealDelete.cancel()
                                                deleteCategory = null
                                            }
                                            dragCenter += delta
                                            if (travel <= viewConfiguration.touchSlop) continue
                                            val target = currentCategories.filter { positions[it] != null }
                                                .minByOrNull { (positions.getValue(it).center - dragCenter).getDistanceSquared() }
                                            if (target == category) lastTarget = null
                                            else if (target != null && target != lastTarget) {
                                                lastTarget = target
                                                reorder(moveCategory(currentCategories, category, target))
                                            }
                                        }
                                    } finally {
                                        revealDelete.cancel()
                                        dragged = null
                                        lastTarget = null
                                    }
                                }
                            }.clickable {
                                if (deleting != null) deleteCategory = null else select(category)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(category, modifier = Modifier.padding(end = 22.dp),
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(categoryItems.size.toString(), style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("件", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(formatPrice(categoryItems.sumOf { it.price ?: 0.0 }),
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (deleteCategory == category) {
                                    WobblingDeleteButton(Modifier.align(Alignment.TopEnd), category) {
                                        onDelete(category)
                                        deleteCategory = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }) { measurables, constraints ->
            val gap = 12.dp.roundToPx()
            val width = ((constraints.maxWidth - gap) / 2).coerceAtLeast(0)
            val cards = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            val rows = cards.chunked(2)
            val heights = rows.map { row -> row.maxOf { it.height } }
            val height = heights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
            layout(constraints.maxWidth, height) {
                var y = 0
                rows.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { column, card -> card.placeRelative(column * (width + gap), y) }
                    y += heights[rowIndex] + gap
                }
            }
        }
    }
}

@Composable
private fun WobblingDeleteButton(modifier: Modifier, category: String, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "分类删除按钮")
    val angle by transition.animateFloat(-5f, 5f,
        infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "轻微摇晃")
    IconButton(onClick = onClick, modifier = modifier.size(44.dp).graphicsLayer { rotationZ = angle }) {
        Icon(Icons.Default.DeleteOutline, contentDescription = "删除分类$category（保留物品）",
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
    }
}
