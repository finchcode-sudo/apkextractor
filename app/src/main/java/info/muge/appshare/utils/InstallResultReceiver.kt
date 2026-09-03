package info.muge.appshare.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * 接收 [SplitApkInstaller] 提交安装会话后的回调：
 * - STATUS_PENDING_USER_ACTION：系统要求用户确认安装，需要把系统给的确认页 Intent 拉起来；
 * - STATUS_SUCCESS：安装成功；
 * - 其它：安装失败，给出提示。
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(it)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        "无法打开安装确认页：${e.message}".toast()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                "安装成功".toast()
            }
            else -> {
                "安装失败${if (!message.isNullOrEmpty()) "：$message" else ""}".toast()
            }
        }
    }
}
