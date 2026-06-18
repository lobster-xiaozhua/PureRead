package com.pureread.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pureread.core.log.PureLog

/**
 * 安装未知应用权限辅助类。
 *
 * 职责：
 * - 检测当前是否拥有 REQUEST_INSTALL_PACKAGES 权限
 * - 无权限时跳转系统设置页
 *
 * 线程安全：仅依赖 [Context] 读取系统状态，无共享可变状态。
 */
public object InstallPermissionHelper {

    /**
     * 判断当前应用是否可以请求安装未知应用。
     *
     * @param context 应用上下文
     * @return true 表示已授权或系统版本低于 O
     */
    @JvmStatic
    public fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * 打开系统“安装未知应用”设置页。
     *
     * 副作用：启动新 Activity（需外部启动调用方处理异常）。
     *
     * @param context 应用上下文
     * @return true 表示 Intent 成功启动
     */
    @JvmStatic
    public fun openInstallSettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ContextCompat.startActivity(context, intent, null)
            PureLog.i("InstallPermissionHelper", "openInstallSettings", "已跳转设置页")
            true
        } catch (e: Exception) {
            PureLog.e("InstallPermissionHelper", "openInstallSettings", e, "跳转失败")
            false
        }
    }
}
