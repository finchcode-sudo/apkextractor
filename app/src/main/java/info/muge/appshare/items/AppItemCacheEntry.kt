package info.muge.appshare.items

import org.json.JSONArray
import org.json.JSONObject

/**
 * AppItem 的轻量磁盘缓存条目。
 * 只保存"计算耗时"的那部分字段（标题、大小、安装来源、启动类），
 * 用来在冷启动时跳过重复计算，实现秒开。
 *
 * 通过 packageName + versionCode + lastUpdateTime 判断某个应用自上次缓存后是否发生变化：
 * 三者都一致时才复用缓存，否则视为该应用需要重新计算。
 */
data class AppItemCacheEntry(
    val packageName: String,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val title: String,
    val size: Long,
    val installSource: String,
    val launchingClass: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("versionCode", versionCode)
        put("lastUpdateTime", lastUpdateTime)
        put("title", title)
        put("size", size)
        put("installSource", installSource)
        put("launchingClass", launchingClass)
    }

    companion object {
        fun fromJson(json: JSONObject): AppItemCacheEntry? {
            return try {
                AppItemCacheEntry(
                    packageName = json.getString("packageName"),
                    versionCode = json.optLong("versionCode", 0L),
                    lastUpdateTime = json.optLong("lastUpdateTime", 0L),
                    title = json.optString("title", ""),
                    size = json.optLong("size", 0L),
                    installSource = json.optString("installSource", ""),
                    launchingClass = json.optString("launchingClass", "")
                )
            } catch (e: Exception) {
                null
            }
        }

        fun listToJson(list: List<AppItemCacheEntry>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(text: String): List<AppItemCacheEntry> {
            return try {
                val array = JSONArray(text)
                val result = ArrayList<AppItemCacheEntry>(array.length())
                for (i in 0 until array.length()) {
                    fromJson(array.getJSONObject(i))?.let { result.add(it) }
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
