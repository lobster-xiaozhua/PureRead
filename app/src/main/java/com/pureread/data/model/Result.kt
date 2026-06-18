package com.pureread.data.model

/**
 * PureRead 业务结果密封类。
 *
 * 职责：
 * - 统一表示成功/失败两种结果，强制调用方处理错误分支
 * - 与 Kotlin 标准库 Result 解耦，避免与协程异常机制混淆
 *
 * 线程安全：不可变数据类，线程安全。
 */
public sealed class Result<out T> {

    /**
     * 成功结果。
     *
     * @param data 载荷数据
     */
    public data class Success<T>(val data: T) : Result<T>()

    /**
     * 失败结果。
     *
     * @param error 业务错误
     */
    public data class Error(val error: PureError) : Result<Nothing>()
}
