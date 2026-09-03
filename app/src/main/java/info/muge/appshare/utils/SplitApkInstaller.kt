package info.muge.appshare.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.util.zip.ZipInputStream

/**
 * 分包安装包（.apks / .apkm / .xapk / .apkx）安装工具。
 *
 * 这类文件本质上是一个 zip 压缩包，里面装的是 base.apk + 若干按 abi/屏幕密度/语言拆分出来的 split apk。
 * 系统安装器不支持直接对着这种 zip 容器发 ACTION_VIEW 去安装，必须用 PackageInstaller 的多文件 Session，
 * 把里面每一个 .apk 都写进同一个安装会话再一起提交，系统才会把它们当成"同一个应用的多个 split"正确装上。
 */
object SplitApkInstaller {

    const val ACTION_INSTALL_RESULT = "info.muge.appshare.action.INSTALL_RESULT"

    /** 根据文件名判断是否是需要走"多 split 安装"流程的容器文件 */
    fun isSplitContainer(fileName: String): Boolean {
        val lower = fileName.trim().lowercase()
        return lower.endsWith(".apks") || lower.endsWith(".xapk") ||
            lower.endsWith(".apkm") || lower.endsWith(".apkx")
    }

    /** 读取 Uri 对应的显示文件名（用于判断扩展名），拿不到时退化为 lastPathSegment */
    fun queryDisplayName(context: Context, uri: Uri): String {
        var name = ""
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx) ?: ""
                    }
                }
        } catch (_: Exception) { }
        if (name.isEmpty()) name = uri.lastPathSegment ?: ""
        return name
    }

    /**
     * 解压 [uri] 指向的分包容器，把其中所有 .apk 条目写入一个 PackageInstaller Session 并提交安装。
     * 安装结果（需要用户确认 / 成功 / 失败）通过 [InstallResultReceiver] 异步处理。
     *
     * @param onError 发起安装阶段出现异常（文件损坏、没有找到任何 apk 条目、写入失败等）时回调，参数为提示文案。
     */
    fun installSplitContainer(context: Context, uri: Uri, onError: (String) -> Unit) {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val packageInstaller = appContext.packageManager.packageInstaller
        var sessionId = -1

        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            var apkCount = 0
            val input = resolver.openInputStream(uri)
            if (input == null) {
                session.abandon()
                onError("无法读取所选文件")
                return
            }

            input.use { rawStream ->
                ZipInputStream(rawStream).use { zip ->
                    var index = 0
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && name.lowercase().endsWith(".apk")) {
                            val safeName = "split_${index++}_" +
                                name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9_.]"), "_")
                            session.openWrite(safeName, 0, -1).use { out ->
                                zip.copyTo(out)
                                session.fsync(out)
                            }
                            apkCount++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (apkCount == 0) {
                session.abandon()
                onError("压缩包内未找到任何 apk 文件，无法安装")
                return
            }

            val resultIntent = Intent(appContext, InstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_RESULT
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(appContext, sessionId, resultIntent, flags)
            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            e.printStackTrace()
            if (sessionId != -1) {
                try { packageInstaller.abandonSession(sessionId) } catch (_: Exception) { }
            }
            onError("安装失败：${e.message}")
        }
    }
}
