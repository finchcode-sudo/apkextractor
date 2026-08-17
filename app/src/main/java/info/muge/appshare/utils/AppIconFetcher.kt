package info.muge.appshare.utils

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.decode.DataSource
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil 加载模型：只携带包名，真正的图标数据在 [AppIconFetcher] 里按需（仅可见的列表项）取。
 * 相比"AppItem 构造时就把 Drawable 取出来"，这样可以让长列表只为屏幕上正在显示的几行付出
 * PackageManager 查图标的开销，而不是一次性为全部已安装应用都取一遍。
 */
data class AppIconModel(val packageName: String)

/**
 * 根据包名从 PackageManager 取图标的 Fetcher。结果由 Coil 的内存缓存自动缓存，
 * 同一个包名只会真正查询一次（除非内存缓存被回收）。
 */
class AppIconFetcher(
    private val context: Context,
    private val packageName: String
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val drawable = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val icon = packageManager.getApplicationIcon(appInfo)
            RecentIconCache.put(context, packageName, icon)
            icon
        } catch (e: Exception) {
            packageManager.defaultActivityIcon
        }
        ImageFetchResult(
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconModel> {
        override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(context.applicationContext, data.packageName)
        }
    }
}

/**
 * 自定义数据类型需要配套 Keyer，结果才能被 Coil 的内存缓存正确识别/复用。
 */
class AppIconKeyer : Keyer<AppIconModel> {
    override fun key(data: AppIconModel, options: Options): String = "app_icon:${data.packageName}"
}
