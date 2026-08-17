package info.muge.appshare.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import info.muge.appshare.Global
import info.muge.appshare.data.AppChangeRecord
import info.muge.appshare.data.AppChangeRepository
import info.muge.appshare.data.ChangeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 清单里静态注册的广播接收器，专门负责"记录"应用安装/卸载/更新事件。
 *
 * 背景：AppListViewModel 里那个动态注册的接收器，只有在 AppShare 进程存活、
 * 且应用列表页处于组合树中时才生效——用户切到桌面装/卸应用，尤其是国产 ROM
 * （比如 ColorOS）杀后台特别狠，动态接收器分分钟就跟着进程一起没了，实时记录
 * 完全靠不住。
 *
 * PACKAGE_ADDED / PACKAGE_REMOVED / PACKAGE_REPLACED（配合 package scheme）
 * 属于 Android 8.0 隐式广播限制的豁免名单：系统即使已经把我们的进程完全杀掉，
 * 收到这几个广播时也会把进程唤醒来投递，处理完再让它睡回去——这是唯一能做到
 * "不管进程死活都能可靠记录"的办法，不需要任何额外权限或后台服务。
 */
class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val changedPkg = intent.data?.schemeSpecificPart ?: return
        if (changedPkg == context.packageName) return

        val action = intent.action ?: return
        val changeType = when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    ChangeType.UPDATED
                } else {
                    ChangeType.INSTALLED
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    return // 更新前的移除，忽略
                }
                ChangeType.UNINSTALLED
            }
            else -> return
        }

        // goAsync 延长这次广播处理的生命周期，允许在协程里做磁盘IO；
        // 系统会等 pendingResult.finish() 之后才可能真正回收进程。
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val pm = appContext.packageManager

                val appName = try {
                    val appInfo = pm.getApplicationInfo(changedPkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    // 已卸载查不到了：先试内存缓存（进程恰好还活着的话），
                    // 再试磁盘缓存（进程是刚被系统唤醒的，Global.app_list 还是空的）
                    val fromMemory = synchronized(Global.app_list) {
                        Global.getAppItemByPackageNameFromList(Global.app_list, changedPkg)?.getAppName()
                    }
                    fromMemory
                        ?: AppListCacheUtil.load(appContext).firstOrNull { it.packageName == changedPkg }?.title
                        ?: changedPkg
                }

                val versionName = try {
                    pm.getPackageInfo(changedPkg, 0).versionName
                } catch (_: Exception) {
                    null
                }

                val installer = try {
                    val installerPkg = pm.getInstallerPackageName(changedPkg)
                    if (installerPkg.isNullOrBlank()) {
                        null
                    } else {
                        try {
                            val installerInfo = pm.getApplicationInfo(installerPkg, 0)
                            pm.getApplicationLabel(installerInfo).toString()
                        } catch (_: Exception) {
                            installerPkg
                        }
                    }
                } catch (_: Exception) {
                    null
                }

                AppChangeRepository.addRecord(
                    appContext,
                    AppChangeRecord(
                        packageName = changedPkg,
                        appName = appName,
                        changeType = changeType,
                        versionName = versionName,
                        installer = installer
                    )
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
