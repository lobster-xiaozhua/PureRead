package com.pureread.update

/**
 * 应用更新信息数据类。
 *
 * @param versionCodeInt 服务端版本号（大于当前 versionCode 才需要更新）
 * @param versionName 服务端版本名称
 * @param apkUrl APK 下载地址（必须为 HTTPS）
 * @param releaseNote 更新说明
 */
public data class UpdateInfo(
    public val versionCodeInt: Int,
    public val versionName: String,
    public val apkUrl: String,
    public val releaseNote: String,
)
