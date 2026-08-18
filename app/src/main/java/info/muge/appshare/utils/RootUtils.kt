package info.muge.appshare.utils

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root 权限工具类
 * 通过 su 执行 shell 命令，不依赖 Shizuku、不依赖任何原生守护进程或隐藏 API 反射，
 * 仅用系统自带的 shell 工具（如 appops）来获取跟系统设置完全同步的实时状态。
 */
object RootUtils {

    private const val TAG = "RootUtils"

    // 是否有 root 权限的检测结果缓存（进程存活期间只探测一次，避免每次都弹 su 授权提示）
    @Volatile
    private var rootAvailable: Boolean? = null

    /**
     * 检测设备是否已 root 且本应用是否已被授予 su 权限。
     * 结果会被缓存，首次调用可能触发一次 su 授权弹窗（如 Magisk/SuperSU 的授权对话框）。
     */
    fun isRootAvailable(): Boolean {
        rootAvailable?.let { return it }
        val result = try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            LogUtil.e("Root not available", e, TAG)
            false
        }
        rootAvailable = result
        return result
    }

    /**
     * 通过 su 执行一条 shell 命令，返回标准输出内容（失败返回 null）。
     */
    fun execRootCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }
            process.waitFor()
            output
        } catch (e: Exception) {
            LogUtil.e("Error executing root command: $command", e, TAG)
            null
        }
    }
}
