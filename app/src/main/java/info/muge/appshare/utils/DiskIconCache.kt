package info.muge.appshare.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream

/**
 * 磁盘态应用图标缓存，存在 filesDir/icon_cache/<包名>.png。
 *
 * 为什么 RecentIconCache（纯内存）不够用：
 * 清单静态注册的 PackageChangeReceiver 是靠系统"唤醒被杀掉的进程"来投递卸载广播的——
 * 这种情况下是一个全新的进程，从来没打开过应用列表、没机会往内存缓存里存过任何图标，
 * 内存缓存永远是空的。只有落到磁盘上的缓存才能跨进程重启存活。
 *
 * 用一个包名对应一个小尺寸PNG文件，量级很小，不会占用明显空间；写入用后台线程做，
 * 调用方不需要关心在哪个线程调用。
 */
object DiskIconCache {
    private const val CACHE_DIR_NAME = "icon_cache"
    private const val ICON_SIZE_PX = 128 // 只用于灰显展示，不需要原始分辨率，缩小节省空间

    private fun getCacheDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun fileFor(context: Context, packageName: String): File {
        // 包名本身是安全的文件名字符（只含字母数字和点），直接用，不需要额外编码
        return File(getCacheDir(context), "$packageName.png")
    }

    /**
     * 异步保存图标到磁盘（自动切到后台线程执行，调用方在任何线程调用都安全）。
     * 用于单个应用的即时缓存场景（比如列表滚动到某一个应用图标时）。
     */
    fun saveAsync(context: Context, packageName: String, drawable: Drawable) {
        val appContext = context.applicationContext
        Thread {
            save(appContext, packageName, drawable)
        }.start()
    }

    /**
     * 同步保存（调用方需要自己保证在后台线程调用，比如已经在 Dispatchers.IO 协程里）。
     * 用于批量场景（比如一次性给几百个已安装应用都存一份图标），
     * 避免像 saveAsync 那样每个应用都单独开一条线程，几百个应用会瞬间甩出几百个线程。
     * 已存在就跳过，避免重复写入同一个文件。
     */
    fun save(context: Context, packageName: String, drawable: Drawable) {
        try {
            val file = fileFor(context, packageName)
            if (file.exists()) return

            val bitmap = drawableToBitmap(drawable, ICON_SIZE_PX)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (_: Exception) {
            // 缓存失败不影响主流程，忽略即可
        }
    }

    /**
     * 判断某个包名是否已经缓存过图标（批量场景用来跳过已缓存的，避免重复取图标浪费时间）
     */
    fun has(context: Context, packageName: String): Boolean {
        return fileFor(context.applicationContext, packageName).exists()
    }

    /**
     * 同步读取磁盘缓存的图标（供 Compose remember 块里直接调用，文件很小，读取很快）。
     */
    fun load(context: Context, packageName: String): Drawable? {
        return try {
            val file = fileFor(context.applicationContext, packageName)
            if (!file.exists()) return null
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
            BitmapDrawable(context.resources, bitmap)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, targetSize: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bitmap
    }
}
