package info.muge.appshare.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 基于 Shizuku 的静默卸载工具。
 *
 * 用户需要提前安装并激活 Shizuku（ADB 配对或 Root 均可），
 * 且需要在本 App 内授予 Shizuku 权限，否则无法使用静默卸载，
 * 此时应回退到系统自带的卸载确认弹窗（[Intent.ACTION_DELETE]）。
 */
object ShizukuUninstaller {

    private const val SHIZUKU_REQUEST_CODE = 0x5A11 // "SALL" 谐音，随意取的常量

    /** Shizuku 服务是否已安装、正在运行并可用（不代表已授权）。 */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && !Shizuku.isPreV11()
        } catch (_: Throwable) {
            false
        }
    }

    /** 是否已经拿到 Shizuku 授权，可直接静默卸载。 */
    fun hasPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限。必须在主线程调用。
     * @param onResult true 表示已授权，可继续静默卸载
     */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!isShizukuAvailable()) {
            onResult(false)
            return
        }
        if (hasPermission()) {
            onResult(true)
            return
        }
        try {
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode == SHIZUKU_REQUEST_CODE) {
                        Shizuku.removeRequestPermissionResultListener(this)
                        onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (_: Throwable) {
            onResult(false)
        }
    }

    /**
     * 通过 Shizuku 静默卸载指定包名的应用，不会弹出系统确认框。
     * 需要先确认 [hasPermission] 为 true。
     *
     * @return true 表示卸载成功
     */
    suspend fun silentUninstall(context: Context, packageName: String): Boolean {
        if (!hasPermission()) return false

        return try {
            val appContext = context.applicationContext
            val packageInstaller = getPackageInstaller(appContext)

            val isSystemApp = try {
                val flags = appContext.packageManager
                    .getApplicationInfo(packageName, 0).flags
                (flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) {
                false
            }

            // PackageManager.DELETE_SYSTEM_APP = 0x00000004
            // PackageManager.DELETE_ALL_USERS  = 0x00000002
            val flags = if (isSystemApp) 0x00000004 else 0x00000002

            suspendCoroutine { continuation ->
                val action = "${appContext.packageName}.SHIZUKU_UNINSTALL_RESULT"
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        appContext.unregisterReceiver(this)
                        val status = intent?.getIntExtra(
                            PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE
                        ) ?: PackageInstaller.STATUS_FAILURE
                        continuation.resume(status == PackageInstaller.STATUS_SUCCESS)
                    }
                }
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(action),
                    ContextCompat.RECEIVER_EXPORTED
                )

                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    0,
                    Intent(action).setPackage(appContext.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                try {
                    HiddenApiBypass.invoke(
                        PackageInstaller::class.java,
                        packageInstaller,
                        "uninstall",
                        packageName,
                        flags,
                        pendingIntent.intentSender
                    )
                } catch (e: Exception) {
                    appContext.unregisterReceiver(receiver)
                    continuation.resume(false)
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    private val packageManagerService: IPackageManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm")
        }
        IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        )
    }

    private fun getPrivilegedPackageInstaller(): IPackageInstaller {
        val installer: IPackageInstaller = packageManagerService.packageInstaller
        return IPackageInstaller.Stub.asInterface(ShizukuBinderWrapper(installer.asBinder()))
    }

    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        InstantiationException::class,
    )
    private fun getPackageInstaller(context: Context): PackageInstaller {
        val iPackageInstaller = getPrivilegedPackageInstaller()
        val root = Shizuku.getUid() == 0
        val userId = if (root) android.os.Process.myUserHandle().hashCode() else 0
        // 使用 "com.android.shell" 作为安装者包名：
        // getMySessions 会校验安装者包名的所有者，adb shell 场景下需要与其保持一致。
        val installerPackageName = "com.android.shell"

        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(iPackageInstaller, installerPackageName, null, userId)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PackageInstaller::class.java.getConstructor(
                IPackageInstaller::class.java, String::class.java, Int::class.java
            ).newInstance(iPackageInstaller, installerPackageName, userId)
        } else {
            PackageInstaller::class.java.getConstructor(
                Context::class.java,
                PackageManager::class.java,
                IPackageInstaller::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(
                context,
                context.packageManager,
                iPackageInstaller,
                installerPackageName,
                userId
            )
        }
    }
}
