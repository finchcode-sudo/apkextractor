package info.muge.appshare.utils

import android.content.Context
import info.muge.appshare.items.AppItemCacheEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 应用列表磁盘缓存工具。
 *
 * 目的：避免每次冷启动（进程被杀后重新打开App）都要完整扫描全部已安装应用
 * （遍历 PackageManager + 逐个计算大小/图标/安装来源等）。
 *
 * 做法：
 * 1. 每次完整刷新完成后，把结果的轻量快照写入磁盘（app 内部私有目录，无需权限）；
 * 2. 下次启动时，先读取磁盘缓存并结合当前 PackageManager 的包信息"秒开"渲染列表；
 * 3. 随后在后台做一次真正的全量刷新，仅对"版本号或最后更新时间发生变化"的应用重新计算，
 *    其余应用直接复用缓存字段，計算量大幅减少；刷新完成后再次写盘更新缓存。
 */
object AppListCacheUtil {

    private const val CACHE_FILE_NAME = "app_list_cache.json"

    private fun getCacheFile(context: Context): File {
        return File(context.applicationContext.filesDir, CACHE_FILE_NAME)
    }

    /**
     * 读取磁盘缓存（IO线程执行）。文件不存在或解析失败时返回空表。
     */
    suspend fun load(context: Context): List<AppItemCacheEntry> = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(context)
            if (!file.exists()) return@withContext emptyList()
            val text = file.readText()
            if (text.isBlank()) return@withContext emptyList()
            AppItemCacheEntry.listFromJson(text)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 将扫描结果写入磁盘缓存（IO线程执行）。
     */
    suspend fun save(context: Context, entries: List<AppItemCacheEntry>) = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile(context)
            val json = AppItemCacheEntry.listToJson(entries)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清除磁盘缓存（例如用户在设置里手动"清除缓存"时调用）。
     */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        try {
            getCacheFile(context).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
