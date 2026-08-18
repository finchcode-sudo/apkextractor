package info.muge.appshare.data

import android.content.Context
import android.content.SharedPreferences
import info.muge.appshare.utils.DiskIconCache
import org.json.JSONArray
import org.json.JSONObject

/**
 * 应用变更类型
 */
enum class ChangeType {
    INSTALLED, UPDATED, UNINSTALLED
}

/**
 * 应用变更记录
 */
data class AppChangeRecord(
    val packageName: String,
    val appName: String,
    val changeType: ChangeType,
    val versionName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val installer: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("packageName", packageName)
            put("appName", appName)
            put("changeType", changeType.name)
            put("versionName", versionName ?: "")
            put("timestamp", timestamp)
            put("installer", installer ?: "")
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AppChangeRecord {
            return AppChangeRecord(
                packageName = json.optString("packageName", ""),
                appName = json.optString("appName", ""),
                changeType = try {
                    ChangeType.valueOf(json.optString("changeType", "INSTALLED"))
                } catch (_: Exception) {
                    ChangeType.INSTALLED
                },
                versionName = json.optString("versionName", "").ifEmpty { null },
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                installer = json.optString("installer", "").ifEmpty { null }
            )
        }
    }
}

/**
 * 应用变更记录仓库
 * 使用 SharedPreferences + JSON 序列化存储
 */
object AppChangeRepository {
    private const val PREFS_NAME = "app_change_records"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 5000

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取安装来源的显示名称（复用与 AppItem 一致的逻辑：优先显示安装器应用名，取不到就显示包名）
     */
    private fun getInstallerName(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val installerPackage = pm.getInstallerPackageName(packageName) ?: return null
            if (installerPackage.isBlank()) return null
            try {
                val appInfo = pm.getApplicationInfo(installerPackage, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                installerPackage
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 添加一条变更记录
     * 加锁：PackageChangeReceiver（广播触发）和列表页离线校验（每次回到列表页触发）
     * 都可能并发调用写入，读-改-写没有同步的话会互相覆盖导致数据重复，
     * 进而让"应用变更记录"列表出现重复 key 直接崩溃。
     */
    @Synchronized
    fun addRecord(context: Context, record: AppChangeRecord) {
        val records = getRecords(context).toMutableList()
        records.add(0, record) // 最新的在前面
        // 限制最大数量
        while (records.size > MAX_RECORDS) {
            records.removeAt(records.size - 1)
        }
        saveRecords(context, records)
    }

    /**
     * 判断是否已存在完全相同的记录（同包名+同变更类型+同时间戳），用于回填时去重，
     * 避免重复调用导致同一条历史事件被记录多次。
     */
    fun hasRecord(context: Context, packageName: String, changeType: ChangeType, timestamp: Long): Boolean {
        return getRecords(context).any {
            it.packageName == packageName && it.changeType == changeType && it.timestamp == timestamp
        }
    }

    private fun recordKey(packageName: String, changeType: ChangeType, timestamp: Long) =
        "$packageName|${changeType.name}|$timestamp"

    /**
     * 用系统当前掌握的信息（PackageManager 的 firstInstallTime / lastUpdateTime）
     * 回填"当前已安装应用"的安装、更新历史。
     *
     * 这份数据由系统持久保存，不依赖本 App 是否一直运行、甚至不依赖本 App 自己的数据有没有被清空——
     * 只要应用还装在设备上，系统就记得它的安装/更新时间，所以即使 AppShare 是后装的，或者被清过数据，
     * 重新打开后依然能把现存应用的历史补出来。
     *
     * 局限：如果某个应用在本方法第一次运行之前就已经"装了又卸载"，系统不会保留任何痕迹，
     * 这部分历史无法恢复——这是系统层面的限制，任何非root应用都无法绕过。
     *
     * 本方法可重复安全调用（幂等，已存在的记录不会被重复添加）。
     * 注意：本方法涉及大量 PackageManager 查询与磁盘IO，调用方必须在后台线程（如 Dispatchers.IO）执行，
     * 绝不能在主线程调用，否则遍历几百个应用会导致明显卡顿甚至 ANR。
     */
    @Synchronized
    fun backfillFromInstalledPackages(context: Context) {
        val packages = try {
            context.packageManager.getInstalledPackages(0)
        } catch (_: Exception) {
            return
        }
        val pm = context.packageManager

        // 一次性读出已有记录，后续全部在内存里去重判断，避免每个应用都重复读写一遍磁盘
        val existingRecords = getRecords(context).toMutableList()
        val existingKeys = existingRecords.mapTo(HashSet()) {
            recordKey(it.packageName, it.changeType, it.timestamp)
        }
        val newRecords = ArrayList<AppChangeRecord>()

        for (info in packages) {
            val packageName = info.packageName ?: continue
            if (packageName == context.packageName) continue

            val appName = try {
                pm.getApplicationLabel(info.applicationInfo!!).toString()
            } catch (_: Exception) {
                packageName
            }

            // 顺手给这个应用缓存一份图标（供以后卸载时兜底展示用）。
            // 先查磁盘文件是不是已经存在，已经有了就跳过昂贵的 getApplicationIcon 调用——
            // 这样只有"第一次遇到这个应用"时才会真正付出取图标的开销，
            // 不会导致每次冷启动都要为几百个应用重复取一遍图标拖慢速度。
            if (!DiskIconCache.has(context, packageName)) {
                try {
                    val icon = pm.getApplicationIcon(info.applicationInfo!!)
                    DiskIconCache.save(context, packageName, icon)
                } catch (_: Exception) {
                }
            }

            val installTime = info.firstInstallTime
            val installKey = recordKey(packageName, ChangeType.INSTALLED, installTime)
            if (installTime > 0 && existingKeys.add(installKey)) {
                newRecords.add(
                    AppChangeRecord(
                        packageName = packageName,
                        appName = appName,
                        changeType = ChangeType.INSTALLED,
                        versionName = info.versionName,
                        timestamp = installTime,
                        installer = getInstallerName(context, packageName)
                    )
                )
            }

            val updateTime = info.lastUpdateTime
            val updateKey = recordKey(packageName, ChangeType.UPDATED, updateTime)
            if (updateTime > 0 && updateTime != installTime && existingKeys.add(updateKey)) {
                newRecords.add(
                    AppChangeRecord(
                        packageName = packageName,
                        appName = appName,
                        changeType = ChangeType.UPDATED,
                        versionName = info.versionName,
                        timestamp = updateTime,
                        installer = getInstallerName(context, packageName)
                    )
                )
            }
        }

        if (newRecords.isNotEmpty()) {
            addRecordsBatch(context, existingRecords, newRecords)
        }
    }

    /**
     * 对比"上一次扫描缓存"和"这一次最新的应用列表"，补上离线期间（AppShare 未运行时）
     * 发生的安装/卸载/更新——不依赖广播监听，弥补广播只在前台才生效的缺陷。
     *
     * @param oldPackages 上一次扫描缓存里的 包名 -> (versionCode, lastUpdateTime, appName)
     * @param newList 这一次最新扫描到的应用列表
     *
     * 注意：本方法涉及大量 PackageManager 查询与磁盘IO，调用方必须在后台线程（如 Dispatchers.IO）执行。
     */
    @Synchronized
    fun diffAndRecordOfflineChanges(
        context: Context,
        oldPackages: Map<String, Triple<Long, Long, String>>,
        newList: List<info.muge.appshare.items.AppItem>
    ) {
        if (oldPackages.isEmpty()) return // 首次扫描没有旧快照可比，交给 backfillFromInstalledPackages 处理

        val newPackageNames = newList.map { it.getPackageName() }.toSet()

        // 一次性读出已有记录，后续全部在内存里去重判断
        val existingRecords = getRecords(context).toMutableList()
        val existingKeys = existingRecords.mapTo(HashSet()) {
            recordKey(it.packageName, it.changeType, it.timestamp)
        }
        val newRecords = ArrayList<AppChangeRecord>()

        // 新出现的包名：离线期间新装的应用（用真实的 firstInstallTime，而不是"现在"）
        for (app in newList) {
            val pkg = app.getPackageName()
            if (pkg == context.packageName) continue
            if (!oldPackages.containsKey(pkg)) {
                val installTime = app.getPackageInfo().firstInstallTime
                val key = recordKey(pkg, ChangeType.INSTALLED, installTime)
                if (installTime > 0 && existingKeys.add(key)) {
                    newRecords.add(
                        AppChangeRecord(
                            packageName = pkg,
                            appName = app.getAppName(),
                            changeType = ChangeType.INSTALLED,
                            versionName = app.getVersionName(),
                            timestamp = installTime,
                            installer = getInstallerName(context, pkg)
                        )
                    )
                }
            } else {
                // 版本号或最后更新时间变化：离线期间更新过
                val (oldVersionCode, oldUpdateTime, _) = oldPackages.getValue(pkg)
                val newVersionCode = app.getPackageInfo().longVersionCode
                val newUpdateTime = app.getPackageInfo().lastUpdateTime
                val key = recordKey(pkg, ChangeType.UPDATED, newUpdateTime)
                if ((newVersionCode != oldVersionCode || newUpdateTime != oldUpdateTime) && existingKeys.add(key)) {
                    newRecords.add(
                        AppChangeRecord(
                            packageName = pkg,
                            appName = app.getAppName(),
                            changeType = ChangeType.UPDATED,
                            versionName = app.getVersionName(),
                            timestamp = newUpdateTime,
                            installer = getInstallerName(context, pkg)
                        )
                    )
                }
            }
        }

        // 旧快照里有、现在没有了：离线期间卸载的应用（卸载后系统查不到信息了，只能用发现时间当时间戳）
        val discoveredAt = System.currentTimeMillis()
        for ((pkg, info) in oldPackages) {
            if (pkg == context.packageName) continue
            if (!newPackageNames.contains(pkg)) {
                val (_, _, appName) = info
                val key = recordKey(pkg, ChangeType.UNINSTALLED, discoveredAt)
                if (existingKeys.add(key)) {
                    newRecords.add(
                        AppChangeRecord(
                            packageName = pkg,
                            appName = appName,
                            changeType = ChangeType.UNINSTALLED,
                            versionName = null,
                            timestamp = discoveredAt
                        )
                    )
                }
            }
        }

        if (newRecords.isNotEmpty()) {
            addRecordsBatch(context, existingRecords, newRecords)
        }
    }

    /**
     * 批量插入记录：只做一次读取（调用方已经读过了，这里直接复用）+ 一次排序 + 一次写入，
     * 避免像单条 addRecord 那样每条都读写一遍磁盘。
     */
    private fun addRecordsBatch(
        context: Context,
        existingRecords: MutableList<AppChangeRecord>,
        newRecords: List<AppChangeRecord>
    ) {
        existingRecords.addAll(0, newRecords)
        // 最新的在前面
        val sorted = existingRecords.sortedByDescending { it.timestamp }.toMutableList()
        while (sorted.size > MAX_RECORDS) {
            sorted.removeAt(sorted.size - 1)
        }
        saveRecords(context, sorted)
    }

    /**
     * 获取所有变更记录
     */
    fun getRecords(context: Context): List<AppChangeRecord> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            (0 until array.length()).map { i ->
                AppChangeRecord.fromJson(array.getJSONObject(i))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 清空所有记录
     */
    fun clearRecords(context: Context) {
        getPrefs(context).edit().remove(KEY_RECORDS).apply()
    }

    /**
     * 获取记录数量
     */
    fun getRecordCount(context: Context): Int {
        return getRecords(context).size
    }

    private fun saveRecords(context: Context, records: List<AppChangeRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        getPrefs(context).edit().putString(KEY_RECORDS, array.toString()).apply()
    }
}
