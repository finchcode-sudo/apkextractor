package info.muge.appshare.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import info.muge.appshare.Constants

/**
 * SharedPreferences工具类
 */
object SPUtil {

    fun getDisplayingExportPath(context: Context): String {
        return "内置存储/Download/AppKit/"
    }

    /**
     * 获取当前应用导出的内置主路径
     * @return 应用导出内置路径，最后没有文件分隔符，例如 /storage/emulated/0
     */
    fun getInternalSavePath(): String {
        return Constants.PREFERENCE_SAVE_PATH_DEFAULT
    }

    /**
     * 获取全局配置
     */
    fun getGlobalSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 判断是否存储到了外置设备上
     * @return true-存储到了外置存储上
     */
    fun getIsSaved2ExternalStorage(context: Context): Boolean {
        return false
    }

    /**
     * 获取外置存储的uri值
     */
    fun getExternalStorageUri(context: Context): String {
        return Uri.parse(Constants.PREFERENCE_SAVE_PATH_DEFAULT).toString()
    }

    /**
     * 发送/接收 端口号，默认6565
     */
    fun getPortNumber(context: Context): Int {
        return getGlobalSharedPreferences(context).getInt(
            Constants.PREFERENCE_NET_PORT,
            Constants.PREFERENCE_NET_PORT_DEFAULT
        )
    }

    /**
     * 获取导出压缩包的扩展名
     */
    fun getCompressingExtensionName(context: Context): String {
        return getGlobalSharedPreferences(context).getString(
            Constants.PREFERENCE_COMPRESSING_EXTENSION,
            Constants.PREFERENCE_COMPRESSING_EXTENSION_DEFAULT
        ) ?: Constants.PREFERENCE_COMPRESSING_EXTENSION_DEFAULT
    }

    /**
     * 获取设备名称
     */
    fun getDeviceName(context: Context): String {
        return try {
            getGlobalSharedPreferences(context)
                .getString(Constants.PREFERENCE_DEVICE_NAME, Build.BRAND)
                ?: Constants.PREFERENCE_DEVICE_NAME_DEFAULT
        } catch (e: Exception) {
            e.printStackTrace()
            Constants.PREFERENCE_DEVICE_NAME_DEFAULT
        }
    }

    /**
     * 全局开关：导出完成后是否自动卸载该应用（备份并卸载）。
     * 默认关闭——单纯导出，不再顺手把应用卸载掉。
     */
    fun getAutoUninstallAfterExport(context: Context): Boolean {
        return getGlobalSharedPreferences(context).getBoolean(
            Constants.PREFERENCE_AUTO_UNINSTALL_AFTER_EXPORT,
            Constants.PREFERENCE_AUTO_UNINSTALL_AFTER_EXPORT_DEFAULT
        )
    }

    fun setAutoUninstallAfterExport(context: Context, enabled: Boolean) {
        getGlobalSharedPreferences(context).edit()
            .putBoolean(Constants.PREFERENCE_AUTO_UNINSTALL_AFTER_EXPORT, enabled)
            .apply()
    }

    /**
     * 全局开关：卸载时是否优先走 Shizuku 静默卸载（不弹系统确认框）。
     * 关闭时走系统自带的卸载确认框（[Intent.ACTION_DELETE]）。
     */
    fun getUseShizukuUninstall(context: Context): Boolean {
        return getGlobalSharedPreferences(context).getBoolean(
            Constants.PREFERENCE_USE_SHIZUKU_UNINSTALL,
            Constants.PREFERENCE_USE_SHIZUKU_UNINSTALL_DEFAULT
        )
    }

    fun setUseShizukuUninstall(context: Context, enabled: Boolean) {
        getGlobalSharedPreferences(context).edit()
            .putBoolean(Constants.PREFERENCE_USE_SHIZUKU_UNINSTALL, enabled)
            .apply()
    }
}
