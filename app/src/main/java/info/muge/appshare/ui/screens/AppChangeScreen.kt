package info.muge.appshare.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import info.muge.appshare.data.AppChangeRecord
import info.muge.appshare.data.AppChangeRepository
import info.muge.appshare.data.ChangeType
import info.muge.appshare.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 应用变更记录页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChangeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var records by remember { mutableStateOf(AppChangeRepository.getRecords(context)) }
    var detailPackageName by remember { mutableStateOf<String?>(null) }

    // 单个应用的完整历史时间线（点击列表项时弹出）
    detailPackageName?.let { pkg ->
        val appRecords = records.filter { it.packageName == pkg }.sortedByDescending { it.timestamp }
        if (appRecords.isNotEmpty()) {
            AppChangeDetailSheet(
                records = appRecords,
                onDismiss = { detailPackageName = null }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "应用变更记录",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = {
                            AppChangeRepository.clearRecords(context)
                            records = emptyList()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清空记录",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无变更记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "应用安装、更新或卸载时将自动记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // 按日期分组
            val grouped = records.groupBy { record ->
                getDateGroup(record.timestamp)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = AppDimens.Space.lg),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (dateLabel, dayRecords) ->
                    item {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    items(dayRecords, key = { "${it.packageName}_${it.timestamp}" }) { record ->
                        ChangeRecordCard(
                            record = record,
                            onClick = { detailPackageName = record.packageName }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 变更类型对应的图标指示（颜色+图标），风格参考主流应用管理工具：
 * 蓝色向上箭头 = 更新，绿色向右箭头 = 安装，红色向下箭头 = 卸载
 */
@Composable
private fun changeTypeVisual(changeType: ChangeType): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> {
    return when (changeType) {
        ChangeType.UPDATED -> Icons.Default.North to MaterialTheme.colorScheme.primary
        ChangeType.INSTALLED -> Icons.Default.East to androidx.compose.ui.graphics.Color(0xFF2E7D32)
        ChangeType.UNINSTALLED -> Icons.Default.South to MaterialTheme.colorScheme.error
    }
}

private fun changeTypeLabel(changeType: ChangeType): String = when (changeType) {
    ChangeType.INSTALLED -> "安装时间"
    ChangeType.UPDATED -> "最近更新"
    ChangeType.UNINSTALLED -> "已卸载"
}

/**
 * 列表项：图标 + 应用名 + 版本号 + "更新/安装于 HH:mm" + 右侧变更类型指示图标
 */
@Composable
private fun ChangeRecordCard(record: AppChangeRecord, onClick: () -> Unit) {
    val context = LocalContext.current

    val icon = remember(record.packageName) {
        try {
            context.packageManager.getApplicationIcon(record.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    val (typeIcon, typeColor) = changeTypeVisual(record.changeType)
    val timeVerb = when (record.changeType) {
        ChangeType.INSTALLED -> "Installed"
        ChangeType.UPDATED -> "Updated"
        ChangeType.UNINSTALLED -> "Uninstalled"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.Radius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(icon)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(AppDimens.Radius.md)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(AppDimens.Radius.md))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = record.appName.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (!record.versionName.isNullOrEmpty()) {
                    Text(
                        text = "Version: ${record.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "$timeVerb ${formatTime(record.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧圆形图标指示：颜色+方向一眼看出是安装/更新/卸载
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = changeTypeLabel(record.changeType),
                    tint = typeColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 单个应用的完整变更时间线（底部弹出），仿主流应用管理工具的"最近更新/安装时间"时间轴样式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppChangeDetailSheet(
    records: List<AppChangeRecord>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val first = records.first()

    val icon = remember(first.packageName) {
        try {
            context.packageManager.getApplicationIcon(first.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space.lg)
                .padding(bottom = 24.dp)
        ) {
            // 应用名 + 图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(icon)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(AppDimens.Radius.md)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = first.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = first.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 时间轴：每一条记录一行，左侧图标+连接线，右侧详情
            records.forEachIndexed { index, record ->
                val (typeIcon, typeColor) = changeTypeVisual(record.changeType)
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 左侧：图标 + 连接线
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(typeColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = null,
                                tint = typeColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (index != records.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.padding(bottom = 20.dp)) {
                        Text(
                            text = changeTypeLabel(record.changeType),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (!record.versionName.isNullOrEmpty()) {
                            Text(
                                text = "版本 ${record.versionName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatFullDateTime(record.timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!record.installer.isNullOrEmpty()) {
                            Text(
                                text = record.installer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 将时间戳格式化为日期分组标签
 */
private fun getDateGroup(timestamp: Long): String {
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()

    cal.timeInMillis = timestamp

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "今天"
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "昨天"
        else -> SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(Date(timestamp))
    }
}

/**
 * 格式化时间显示（列表用，简短）
 */
private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}

/**
 * 格式化完整日期时间显示（详情时间轴用）
 */
private fun formatFullDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
