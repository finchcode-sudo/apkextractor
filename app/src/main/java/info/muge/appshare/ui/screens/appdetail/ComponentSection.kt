package info.muge.appshare.ui.screens.appdetail

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.muge.appshare.R
import info.muge.appshare.items.AppItem
import info.muge.appshare.ui.ToastManager
import info.muge.appshare.ui.theme.AppDimens
import info.muge.appshare.utils.AppOpsRootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 组件列表内容
 */
@Composable
fun ComponentListContent(
    context: Context,
    appItem: AppItem,
    componentType: ComponentType
) {
    val components = remember { mutableStateListOf<ComponentItem>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(appItem, componentType) {
        isLoading = true
        components.clear()

        val items = withContext(Dispatchers.IO) {
            val packageInfo = appItem.getFullPackageInfo(context)
            val result = mutableListOf<ComponentItem>()

            when (componentType) {
                ComponentType.PERMISSION -> {
                    val requestedFlags = packageInfo.requestedPermissionsFlags
                    packageInfo.requestedPermissions?.forEachIndexed { index, permission ->
                        if (permission != null) {
                            val granted = requestedFlags?.getOrNull(index)
                                ?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 } ?: false

                            var description: String? = null
                            var flagsText: String? = null
                            var definingPackage: String? = null
                            var group: String? = null

                            try {
                                @Suppress("DEPRECATION")
                                val permInfo = context.packageManager.getPermissionInfo(
                                    permission, PackageManager.GET_META_DATA
                                )
                                description = permInfo.loadDescription(context.packageManager)?.toString()
                                definingPackage = permInfo.packageName
                                group = permInfo.group
                                flagsText = describeProtectionLevel(permInfo.protectionLevel)
                            } catch (e: PackageManager.NameNotFoundException) {
                                // 未在系统中注册的权限（如厂商私有权限），仅展示名称
                            }

                            // 优先尝试用 root 权限查询实时 App Ops 状态，跟系统设置界面完全同步；
                            // 拿不到 root 或该权限没有对应 op 时，退回标准 PackageManager 授权标记
                            val opName = AppOpsRootHelper.permissionToOp(permission)
                            val liveStatus = opName?.let { op ->
                                AppOpsRootHelper.getOpMode(appItem.getPackageName(), op)?.let { mode ->
                                    when (mode) {
                                        AppOpsRootHelper.OpMode.ALLOW -> "granted (root)"
                                        AppOpsRootHelper.OpMode.FOREGROUND -> "foreground (root)"
                                        AppOpsRootHelper.OpMode.IGNORE,
                                        AppOpsRootHelper.OpMode.DENY -> "revoked (root)"
                                        AppOpsRootHelper.OpMode.DEFAULT ->
                                            if (granted) "granted (root)" else "revoked (root)"
                                        AppOpsRootHelper.OpMode.UNKNOWN -> null
                                    }
                                }
                            }

                            val statusText = liveStatus ?: if (granted) "granted" else "revoked"
                            val fullFlags = if (flagsText.isNullOrEmpty()) statusText else "$flagsText  $statusText"

                            result.add(
                                ComponentItem(
                                    name = permission,
                                    packageName = null,
                                    canLaunch = false,
                                    exported = false,
                                    permission = null,
                                    permissionDescription = description,
                                    permissionFlags = fullFlags,
                                    permissionDefiningPackage = definingPackage,
                                    permissionGroup = group ?: "android.permission-group.UNDEFINED"
                                )
                            )
                        }
                    }
                }
                ComponentType.ACTIVITY -> {
                    packageInfo.activities?.forEach { activityInfo ->
                        result.add(ComponentItem(
                            activityInfo.name,
                            activityInfo.packageName,
                            true,
                            activityInfo.exported,
                            activityInfo.permission
                        ))
                    }
                }
                ComponentType.SERVICE -> {
                    packageInfo.services?.forEach { serviceInfo ->
                        result.add(ComponentItem(
                            serviceInfo.name,
                            serviceInfo.packageName,
                            true,
                            serviceInfo.exported,
                            serviceInfo.permission
                        ))
                    }
                }
                ComponentType.RECEIVER -> {
                    packageInfo.receivers?.forEach { receiverInfo ->
                        result.add(ComponentItem(
                            receiverInfo.name,
                            null,
                            false,
                            receiverInfo.exported,
                            receiverInfo.permission
                        ))
                    }
                }
                ComponentType.PROVIDER -> {
                    packageInfo.providers?.forEach { providerInfo ->
                        result.add(ComponentItem(
                            providerInfo.name,
                            null,
                            false,
                            providerInfo.exported,
                            providerInfo.readPermission
                        ))
                    }
                }
                ComponentType.STATIC_LOADER -> {
                    val bundle = appItem.getStaticReceiversBundle()
                    bundle.keySet().forEach { key ->
                        val filters = bundle.getStringArrayList(key)
                        val description = filters?.joinToString(", ") ?: ""
                        result.add(ComponentItem(key, description, false, false, null))
                    }
                }
            }
            result
        }

        components.addAll(items)
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (components.isEmpty()) {
            Text(
                text = "暂无内容",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(components) { item ->
                    if (componentType == ComponentType.PERMISSION) {
                        PermissionItemCard(
                            item = item,
                            onClick = { copyToClipboard(context, item.name) }
                        )
                    } else {
                        ComponentItemCard(
                            item = item,
                            componentType = componentType,
                            onClick = { copyToClipboard(context, item.name) },
                            onLongClick = {
                                handleComponentLongClick(context, item, componentType)
                            },
                            canLongClick = item.canLaunch
                        )
                    }
                }
            }
        }
    }
}

data class ComponentItem(
    val name: String,
    val packageName: String?,
    val canLaunch: Boolean,
    val exported: Boolean,
    val permission: String?,
    // 权限专属附加信息（仅 ComponentType.PERMISSION 使用）
    val permissionDescription: String? = null,
    val permissionFlags: String? = null,
    val permissionDefiningPackage: String? = null,
    val permissionGroup: String? = null
)

/**
 * 将 PermissionInfo.protectionLevel 解析为可读字符串，风格参考系统 PermissionInfo.protectionToString()
 * 自行实现以兼容 minSdk 24（该系统方法 API 28 才提供）
 */
private fun describeProtectionLevel(level: Int): String {
    val base = level and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
    val baseText = when (base) {
        android.content.pm.PermissionInfo.PROTECTION_NORMAL -> "normal"
        android.content.pm.PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
        android.content.pm.PermissionInfo.PROTECTION_SIGNATURE -> "signature"
        4 -> "internal" // PROTECTION_INTERNAL (API 28+)
        else -> "unknown"
    }

    val flags = mutableListOf<String>()
    fun hasFlag(flag: Int) = level and flag != 0
    if (hasFlag(0x10)) flags.add("privileged") // PROTECTION_FLAG_PRIVILEGED / SYSTEM
    if (hasFlag(0x20)) flags.add("development")
    if (hasFlag(0x40)) flags.add("appop")
    if (hasFlag(0x80)) flags.add("pre23")
    if (hasFlag(0x100)) flags.add("installer")
    if (hasFlag(0x200)) flags.add("verifier")
    if (hasFlag(0x400)) flags.add("preinstalled")
    if (hasFlag(0x800)) flags.add("setup")
    if (hasFlag(0x1000)) flags.add("instant")
    if (hasFlag(0x2000)) flags.add("runtime")
    if (hasFlag(0x4000)) flags.add("oem")
    if (hasFlag(0x8000)) flags.add("vendorPrivileged")

    return (listOf(baseText) + flags).joinToString("  ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComponentItemCard(
    item: ComponentItem,
    componentType: ComponentType,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    canLongClick: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .then(
                if (canLongClick) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        shape = RoundedCornerShape(AppDimens.Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (componentType != ComponentType.PERMISSION && componentType != ComponentType.STATIC_LOADER) {
                    if (item.exported) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = "Exported",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (!item.permission.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.permission,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    item: ComponentItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space.lg)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.permissionDescription.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.permissionDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!item.permissionFlags.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.permissionFlags,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!item.permissionDefiningPackage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "应用包名: ${item.permissionDefiningPackage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!item.permissionGroup.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "群组: ${item.permissionGroup}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun handleComponentLongClick(
    context: Context,
    item: ComponentItem,
    componentType: ComponentType
) {
    if (!item.canLaunch || item.packageName.isNullOrEmpty()) return

    when (componentType) {
        ComponentType.ACTIVITY -> {
            try {
                val intent = Intent()
                intent.setClassName(item.packageName, item.name)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                ToastManager.showToast(context, "无法启动该Activity", Toast.LENGTH_SHORT)
            }
        }
        ComponentType.SERVICE -> {
            try {
                val intent = Intent()
                intent.setClassName(item.packageName, item.name)
                context.startService(intent)
                ToastManager.showToast(context, "已尝试启动Service", Toast.LENGTH_SHORT)
            } catch (e: Exception) {
                ToastManager.showToast(context, "无法启动该Service", Toast.LENGTH_SHORT)
            }
        }
        else -> {}
    }
}
