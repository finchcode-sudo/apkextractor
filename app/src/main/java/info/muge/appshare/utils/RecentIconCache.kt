package info.muge.appshare.utils

import android.graphics.drawable.Drawable

/**
 * 最近见过的应用图标缓存（内存态，简单 LRU）。
 *
 * 用途：应用卸载后，PackageManager 就再也查不到它的图标了。但如果这个应用之前在
 * 应用列表/变更记录里展示过，图标其实早就被真实抓取过一次——这里把抓到的图标顺手
 * 缓存一份，供"应用变更记录"页面在应用已卸载、查不到实时图标时兜底使用，
 * 这样能展示"卸载前最后见过的真实图标"（配合灰度滤镜），而不是一个跟应用毫无关系的
 * 通用占位图标。
 *
 * 注意：纯内存缓存，进程重启后会清空——这是预期行为，不追求持久化，只是让同一次
 * 使用会话里的体验更好。
 */
object RecentIconCache {
    private const val MAX_ENTRIES = 500

    // LinkedHashMap 的 accessOrder=true 模式天然支持 LRU 淘汰
    private val cache = object : LinkedHashMap<String, Drawable>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun put(packageName: String, drawable: Drawable) {
        cache[packageName] = drawable
    }

    @Synchronized
    fun get(packageName: String): Drawable? {
        return cache[packageName]
    }
}
