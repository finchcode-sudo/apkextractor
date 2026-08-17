package info.muge.appshare.tasks

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import info.muge.appshare.Constants
import info.muge.appshare.Global
import info.muge.appshare.items.AppItem
import info.muge.appshare.items.AppItemCacheEntry
import info.muge.appshare.utils.AppListCacheUtil
import info.muge.appshare.utils.SPUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * 刷新已安装的应用列表（协程版）
 */
class RefreshInstalledListTask(
    private val context: Context
) {
    /**
     * 冷启动"秒开"：仅依据磁盘缓存 + 当前已安装包名单快速构建列表，不做任何耗时计算。
     * 缓存中已卸载的应用会被自动过滤；新安装、还没被缓存过的应用会被跳过（等下一次 [execute] 补齐）。
     * 结果不会写入 Global.app_list（避免覆盖真实数据），仅用于界面"占位式"快速展示。
     */
    suspend fun quickLoadFromCache(): List<AppItem> = withContext(Dispatchers.IO) {
        val cacheEntries = AppListCacheUtil.load(context)
        if (cacheEntries.isEmpty()) return@withContext emptyList()

        val manager = context.applicationContext.packageManager
        val cacheMap = cacheEntries.associateBy { it.packageName }
        val settings = SPUtil.getGlobalSharedPreferences(context)
        val flagSystem = settings.getBoolean(
            Constants.PREFERENCE_SHOW_SYSTEM_APP,
            Constants.PREFERENCE_SHOW_SYSTEM_APP_DEFAULT
        )

        val installedPackages = try {
            manager.getInstalledPackages(0)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val result = ArrayList<AppItem>()
        for (info in installedPackages) {
            ensureActive()
            val appInfo = info.applicationInfo ?: continue
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) > 0
            if (!flagSystem && isSystemApp) continue

            val cache = cacheMap[info.packageName] ?: continue
            try {
                result.add(AppItem(context, info, cache))
            } catch (e: Exception) {
                // 单个应用构建失败不影响整体秒开展示
            }
        }

        AppItem.sort_config = settings.getInt(Constants.PREFERENCE_SORT_CONFIG, 0)
        Collections.sort(result)
        result
    }

    /**
     * 执行刷新（挂起函数，在 IO 线程执行）
     * @param onProgressStarted 进度开始回调（主线程，非阻塞）
     * @param onProgressUpdated 进度更新回调（主线程，非阻塞）
     * @param useCacheAcceleration 是否使用磁盘缓存加速：对于版本号和最后更新时间都未变化的应用，
     *        跳过重新计算大小/安装来源/启动类等耗时字段，直接复用缓存值。
     * @return 排序后的应用列表
     */
    suspend fun execute(
        onProgressStarted: ((total: Int) -> Unit)? = null,
        onProgressUpdated: ((current: Int, total: Int) -> Unit)? = null,
        useCacheAcceleration: Boolean = true
    ): List<AppItem> = withContext(Dispatchers.IO) {
        val mainScope = CoroutineScope(Dispatchers.Main)
        val settings = SPUtil.getGlobalSharedPreferences(context)
        val flagSystem = settings.getBoolean(
            Constants.PREFERENCE_SHOW_SYSTEM_APP,
            Constants.PREFERENCE_SHOW_SYSTEM_APP_DEFAULT
        )

        val manager = context.applicationContext.packageManager

        var flag = 0
        if (settings.getBoolean(Constants.PREFERENCE_LOAD_PERMISSIONS, Constants.PREFERENCE_LOAD_PERMISSIONS_DEFAULT)) {
            flag = flag or PackageManager.GET_PERMISSIONS
        }
        if (settings.getBoolean(Constants.PREFERENCE_LOAD_ACTIVITIES, Constants.PREFERENCE_LOAD_ACTIVITIES_DEFAULT)) {
            flag = flag or PackageManager.GET_ACTIVITIES
        }
        if (settings.getBoolean(Constants.PREFERENCE_LOAD_RECEIVERS, Constants.PREFERENCE_LOAD_RECEIVERS_DEFAULT)) {
            flag = flag or PackageManager.GET_RECEIVERS
        }
        if (settings.getBoolean(Constants.PREFERENCE_LOAD_APK_SIGNATURE, Constants.PREFERENCE_LOAD_APK_SIGNATURE_DEFAULT)) {
            flag = flag or PackageManager.GET_SIGNATURES
        }
        if (settings.getBoolean(Constants.PREFERENCE_LOAD_SERVICES, Constants.PREFERENCE_LOAD_SERVICES_DEFAULT)) {
            flag = flag or PackageManager.GET_SERVICES
        }
        if (settings.getBoolean(Constants.PREFERENCE_LOAD_PROVIDERS, Constants.PREFERENCE_LOAD_PROVIDERS_DEFAULT)) {
            flag = flag or PackageManager.GET_PROVIDERS
        }

        val list = ArrayList<android.content.pm.PackageInfo>()
        synchronized(RefreshInstalledListTask::class.java) {
            list.clear()
            list.addAll(manager.getInstalledPackages(flag))
        }

        // 非阻塞 fire-and-forget，匹配原 Global.handler.post 语义
        mainScope.launch {
            onProgressStarted?.invoke(list.size)
        }

        // 加载磁盘缓存用于增量加速（版本号+最后更新时间都一致才复用，否则视为已变化）
        val cacheMap: Map<String, AppItemCacheEntry> = if (useCacheAcceleration) {
            AppListCacheUtil.load(context).associateBy { it.packageName }
        } else {
            emptyMap()
        }

        val listSum = ArrayList<AppItem>()
        for (i in list.indices) {
            ensureActive()

            val info = list[i]
            val isSystemApp = (info.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) > 0
            val current = i + 1

            // 非阻塞 fire-and-forget，不挂起 IO 协程
            mainScope.launch {
                onProgressUpdated?.invoke(current, list.size)
            }

            if (!flagSystem && isSystemApp) continue

            val cached = cacheMap[info.packageName]
            val isUnchanged = cached != null &&
                cached.versionCode == info.longVersionCode &&
                cached.lastUpdateTime == info.lastUpdateTime

            val item = if (isUnchanged) {
                try {
                    AppItem(context, info, cached!!)
                } catch (e: Exception) {
                    AppItem(context, info)
                }
            } else {
                AppItem(context, info)
            }
            listSum.add(item)
        }

        ensureActive()

        AppItem.sort_config = settings.getInt(Constants.PREFERENCE_SORT_CONFIG, 0)
        Collections.sort(listSum)

        synchronized(Global.app_list) {
            Global.app_list.clear()
            Global.app_list.addAll(listSum)
        }

        // 写入磁盘缓存，供下次冷启动秒开 & 增量加速使用
        AppListCacheUtil.save(context, listSum.map { it.toCacheEntry() })

        listSum
    }

    /**
     * 刷新已安装列表任务回调（保留向后兼容）
     */
    interface RefreshInstalledListTaskCallback {
        fun onRefreshProgressStarted(total: Int)
        fun onRefreshProgressUpdated(current: Int, total: Int)
        fun onRefreshCompleted(appList: List<AppItem>)
    }
}
