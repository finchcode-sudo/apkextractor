package info.muge.appshare.utils

import android.app.AppOpsManager

/**
 * 通过 root 权限调用系统自带的 appops shell 命令，读取跟系统设置界面完全同步的实时授权状态。
 *
 * 原理：Android 系统自带 `appops` / `cmd appops` 这个 shell 工具，任何拥有 root（或 shell UID）权限的
 * 进程都可以直接调用它查询任意应用、任意 op 的当前模式（allow/ignore/deny/default），
 * 这与系统设置里"外部来源应用""通知权限"等开关背后读写的是同一份数据。
 * 不需要 Shizuku，不需要隐藏 API 反射，也不需要额外启动守护进程。
 *
 * op 用 AppOpsManager.permissionToOp() 返回的字符串名字（如 "android:request_install_packages"），
 * 这是 Android SDK 里的公开 API，不是隐藏 API。
 */
object AppOpsRootHelper {

    private const val TAG = "AppOpsRootHelper"

    /**
     * App Ops 模式，对应 appops 命令输出里的 allow/ignore/deny/default
     */
    enum class OpMode(val display: String) {
        ALLOW("allow"),
        IGNORE("ignore"),
        DENY("deny"),
        DEFAULT("default"),
        FOREGROUND("foreground"),
        UNKNOWN("unknown")
    }

    /**
     * 将标准权限名（如 android.permission.REQUEST_INSTALL_PACKAGES）转换为对应的 AppOps op 名称。
     * 使用 AppOpsManager 的公开 API，不涉及隐藏 API。
     * 并非每个权限都有对应的 op，没有则返回 null。
     */
    fun permissionToOp(permission: String): String? {
        return try {
            AppOpsManager.permissionToOp(permission)
        } catch (e: Exception) {
            LogUtil.e("Error resolving op for permission: $permission", e, TAG)
            null
        }
    }

    /**
     * 查询指定应用、指定 op 的实时模式。
     * 需要先确认 [RootUtils.isRootAvailable] 为 true 再调用，否则直接返回 null。
     */
    fun getOpMode(packageName: String, op: String): OpMode? {
        if (!RootUtils.isRootAvailable()) return null

        // 优先尝试独立的 appops 命令，失败再退回 cmd appops（不同 Android 版本命令入口略有差异）
        val output = RootUtils.execRootCommand("appops get $packageName $op")
            ?: return null

        if (output.isBlank() || output.contains("Unknown") || output.contains("No such")) {
            val fallback = RootUtils.execRootCommand("cmd appops get $packageName $op") ?: return null
            return parseOpModeOutput(fallback, op)
        }

        return parseOpModeOutput(output, op)
    }

    /**
     * 解析 appops get 命令的输出，典型格式：
     * "REQUEST_INSTALL_PACKAGES: allow; time=..."
     * 或多行/带其它 op 混杂时，找到目标 op 所在那一行再解析
     */
    private fun parseOpModeOutput(output: String, op: String): OpMode? {
        val line = output.lineSequence().firstOrNull { it.contains(op, ignoreCase = true) }
            ?: output.lineSequence().firstOrNull { it.isNotBlank() }
            ?: return null

        return when {
            line.contains("allow", ignoreCase = true) -> OpMode.ALLOW
            line.contains("ignore", ignoreCase = true) -> OpMode.IGNORE
            line.contains("deny", ignoreCase = true) -> OpMode.DENY
            line.contains("foreground", ignoreCase = true) -> OpMode.FOREGROUND
            line.contains("default", ignoreCase = true) -> OpMode.DEFAULT
            else -> OpMode.UNKNOWN
        }
    }

    /**
     * 设置指定应用、指定 op 的模式（需要 root）。
     * 返回是否设置成功（通过设置后再读一次进行校验）。
     */
    fun setOpMode(packageName: String, op: String, mode: OpMode): Boolean {
        if (!RootUtils.isRootAvailable()) return false
        val modeArg = when (mode) {
            OpMode.ALLOW -> "allow"
            OpMode.IGNORE -> "ignore"
            OpMode.DENY -> "deny"
            OpMode.DEFAULT -> "default"
            OpMode.FOREGROUND -> "foreground"
            OpMode.UNKNOWN -> return false
        }

        RootUtils.execRootCommand("appops set $packageName $op $modeArg")
        RootUtils.execRootCommand("cmd appops set $packageName $op $modeArg")

        // appops set 命令成功时通常没有输出，用读回的方式校验是否真的生效
        val verify = getOpMode(packageName, op)
        return verify == mode || (mode == OpMode.ALLOW && verify == OpMode.FOREGROUND)
    }
}
