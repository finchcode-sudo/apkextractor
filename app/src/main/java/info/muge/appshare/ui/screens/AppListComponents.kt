package info.muge.appshare.ui.screens

import android.app.Activity
import android.text.format.Formatter

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import info.muge.appshare.R
import info.muge.appshare.items.AppItem
import info.muge.appshare.ui.theme.AppDimens
import info.muge.appshare.utils.PermissionExts
import info.muge.appshare.utils.AppIconModel
import info.muge.appshare.utils.apiToColor
import info.muge.appshare.utils.apiToVersion
import java.util.Locale

/**
 * 加载中内容 - MD3 风格
 */
@Composable
internal fun LoadingContent(
    current: Int,
    total: Int
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 进度环
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            CircularProgressIndicator(
                progress = { if (total > 0) current.toFloat() / total else 0f },
                modifier = Modifier.size(100.dp),
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (total > 0) "${(current * 100 / total)}%" else "...",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$current/$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.dialog_loading_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(AppDimens.Space.sm))

        Text(
            text = stringResource(R.string.loading_scanning_apps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 空内容 - MD3 风格
 */
@Composable
internal fun EmptyContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.Space.lg))

        Text(
            text = stringResource(R.string.word_content_blank),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(AppDimens.Space.sm))

        Text(
            text = stringResource(R.string.empty_pull_to_refresh),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 搜索无结果 - MD3 风格
 */
@Composable
internal fun SearchEmptyContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.Space.lg))

        Text(
            text = stringResource(R.string.word_content_blank),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(AppDimens.Space.sm))

        Text(
            text = stringResource(R.string.search_no_result),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun PermissionBottomBar(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = AppDimens.Elevation.none,
        tonalElevation = AppDimens.Elevation.none
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space.lg, vertical = AppDimens.Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(AppDimens.Radius.md),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(AppDimens.Space.md))
            Text(
                text = stringResource(R.string.permission_read_app_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = {
                    val activity = context as? Activity ?: return@FilledTonalButton
                    PermissionExts.requestreadInstallApps(activity) {
                        onPermissionGranted()
                    }
                }
            ) {
                Text(stringResource(R.string.permission_grant_short))
            }
        }
    }
}

/**
 * 多选卡片 - MD3 风格
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MultiSelectCard(
    selectedCount: Int,
    selectedSize: Long,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeselectAll: () -> Unit,
    onCopyPackageNames: () -> Unit,
    onUninstallSelected: () -> Unit = {},
    onExportSelected: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = AppDimens.Radius.xl, topEnd = AppDimens.Radius.xl),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = AppDimens.Elevation.none,
        tonalElevation = AppDimens.Elevation.none
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space.xl, vertical = AppDimens.Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space.sm)
            ) {
                Surface(
                    shape = RoundedCornerShape(AppDimens.Radius.sm),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$selectedCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = AppDimens.Space.md, vertical = AppDimens.Space.xs)
                    )
                }

                Text(
                    text = stringResource(R.string.unit_item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                )

                Text(
                    text = Formatter.formatFileSize(LocalContext.current, selectedSize),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.Space.md))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space.sm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space.xs)
            ) {
                val compactPadding = PaddingValues(
                    horizontal = AppDimens.Space.md,
                    vertical = AppDimens.Space.sm
                )

                FilledTonalButton(
                    onClick = onSelectAll,
                    contentPadding = compactPadding
                ) {
                    Text(
                        text = stringResource(R.string.select_all_change),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onInvertSelection,
                    contentPadding = compactPadding
                ) {
                    Text(
                        text = stringResource(R.string.invert_selection),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onDeselectAll,
                    contentPadding = compactPadding
                ) {
                    Text(
                        text = stringResource(R.string.deselect_all),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onCopyPackageNames,
                    contentPadding = compactPadding
                ) {
                    Text(
                        text = stringResource(R.string.copy_package_names),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onExportSelected,
                    contentPadding = compactPadding
                ) {
                    Text(
                        text = stringResource(R.string.action_batch_export),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onUninstallSelected,
                    contentPadding = compactPadding,
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.action_batch_uninstall),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * 列表项 - 线性模式，MD3 风格
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LinearAppItem(
    app: AppItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    highlightKeyword: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space.md, vertical = AppDimens.Space.xs)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(AppDimens.Radius.lg),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.Elevation.none)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(AppIconModel(app.getPackageName()))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(AppDimens.Radius.md)),
                    contentScale = ContentScale.Crop
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected && isMultiSelectMode,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)),
                    exit = scaleOut(spring(stiffness = Spring.StiffnessMedium))
                ) {
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(AppDimens.Radius.md)),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.Space.md)
            ) {
                Text(
                    text = highlightText(app.getAppName(), highlightKeyword),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.isRedMarked()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(AppDimens.Space.xs))

                Text(
                    text = highlightText(app.getPackageName(), highlightKeyword),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (isMultiSelectMode) {
                Surface(
                    shape = RoundedCornerShape(AppDimens.Radius.sm),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(AppDimens.Space.xs),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0f)
                    )
                }
            } else {
                val targetSdk = app.getPackageInfo().applicationInfo?.targetSdkVersion ?: 0
                val apiColor = Color(targetSdk.apiToColor())
                val apiDescription = targetSdk.apiToVersion()

                // 用实际文字测量而不是拍脑袋定死一个 dp 值：
                // 以当前最常见、最宽的文案（"Android 16"）为基准，测出它真实渲染需要多宽，
                // 让所有徽章的宽度都跟这个基准对齐——36 的徽章本来就是被这段文字撑开的，
                // 所以它自己的宽度不会有任何变化，其它较短文案的徽章会被撑到跟它一样宽。
                val textMeasurer = rememberTextMeasurer()
                val labelSmallStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                val referenceTextWidth = remember(labelSmallStyle) {
                    textMeasurer.measure("Android 16", style = labelSmallStyle).size.width
                }
                val referenceWidthDp = with(LocalDensity.current) { referenceTextWidth.toDp() }
                val badgeMinWidth = referenceWidthDp + AppDimens.Space.sm * 2

                Column(
                    modifier = Modifier
                        .widthIn(min = badgeMinWidth)
                        .clip(RoundedCornerShape(AppDimens.Radius.sm))
                        .background(apiColor.copy(alpha = 0.07f))
                        .border(
                            width = 1.dp,
                            color = apiColor,
                            shape = RoundedCornerShape(AppDimens.Radius.sm)
                        )
                        .padding(horizontal = AppDimens.Space.sm, vertical = AppDimens.Space.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = targetSdk.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = apiColor
                    )
                    Text(
                        text = apiDescription,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = apiColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 列表项 - 网格模式，MD3 风格
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridAppItem(
    app: AppItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(AppDimens.Space.xs)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(AppDimens.Radius.xl),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.Elevation.none)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Space.sm, vertical = AppDimens.Space.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(AppIconModel(app.getPackageName()))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(AppDimens.Radius.md)),
                        contentScale = ContentScale.Crop
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)),
                        exit = scaleOut(spring(stiffness = Spring.StiffnessMedium))
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(AppDimens.Radius.md)),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.Space.sm))

                Text(
                    text = app.getAppName(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.isRedMarked()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 高亮文本
 */
@Composable
internal fun highlightText(text: String, keyword: String?): AnnotatedString {
    if (keyword.isNullOrEmpty()) {
        return AnnotatedString(text)
    }

    val lowerText = text.lowercase(Locale.getDefault())
    val lowerKeyword = keyword.lowercase(Locale.getDefault())

    return buildAnnotatedString {
        var startIndex = 0
        var index = lowerText.indexOf(lowerKeyword)

        while (index != -1) {
            append(text.substring(startIndex, index))
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                append(text.substring(index, index + keyword.length))
            }
            startIndex = index + keyword.length
            index = lowerText.indexOf(lowerKeyword, startIndex)
        }
        append(text.substring(startIndex))
    }
}
