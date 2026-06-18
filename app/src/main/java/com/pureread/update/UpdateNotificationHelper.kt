package com.pureread.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pureread.R

/**
 * 更新下载通知辅助类。
 *
 * 职责：
 * - 创建通知渠道（Android O+）
 * - 构建下载进度/完成/失败通知
 *
 * 线程安全：通知构建器为局部变量，无共享可变状态。
 */
public class UpdateNotificationHelper(
    private val context: Context,
) {

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    /**
     * 构建带下载进度的前台通知。
     *
     * @param progressInt 当前进度百分比（0-100）；负数表示不确定进度
     * @param title 通知标题
     * @return 可提交给 [androidx.work.ListenableWorker.setForeground] 的通知
     */
    public fun buildProgressNotification(
        progressInt: Int,
        title: String,
    ): android.app.Notification {
        val isIndeterminate = progressInt < 0 || progressInt >= MAX_PROGRESS_INT
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                MAX_PROGRESS_INT,
                if (isIndeterminate) 0 else progressInt,
                isIndeterminate
            )
        return builder.build()
    }

    /**
     * 显示下载完成通知。
     *
     * @param title 通知标题
     * @param message 通知内容
     */
    public fun showCompleteNotification(title: String, message: String): Unit {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 显示下载失败通知。
     *
     * @param title 通知标题
     * @param message 失败原因
     */
    public fun showErrorNotification(title: String, message: String): Unit {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * 取消通知。
     */
    public fun cancel(): Unit {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private companion object {
        private const val CHANNEL_ID = "pureread_update_channel"
        private const val CHANNEL_NAME = "应用更新"
        private const val CHANNEL_DESCRIPTION = "PureRead 应用更新下载进度"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_PROGRESS_INT = 100
    }
}
