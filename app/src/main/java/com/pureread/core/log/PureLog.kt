package com.pureread.core.log

import android.util.Log

/**
 * PureRead 全局日志单例。
 *
 * 职责：
 * - 统一所有模块的日志格式："[SimpleClassName] [op] | result | costTimeMs=..."
 * - 提供带/不带耗时的多态重载
 * - 所有异常日志必须经此入口输出，禁止空 catch
 * - 兼容 JVM 单元测试环境：当 Android Runtime 不可用时回退到标准输出
 *
 * 线程安全：无状态对象，可在任意线程调用。
 */
public object PureLog {

    private const val DEFAULT_LOG_TAG = "PureRead"

    private val isAndroidRuntime: Boolean by lazy {
        runCatching {
            Class.forName("android.util.Log")
            true
        }.getOrDefault(false)
    }

    /**
     * 输出 DEBUG 级别日志。
     *
     * @param tagString 类简单名或模块标识
     * @param opString  操作名
     * @param msgString 结果描述
     */
    @JvmStatic
    public fun d(tagString: String, opString: String, msgString: String): Unit {
        log(LogLevel.DEBUG, tagString, opString, msgString)
    }

    /**
     * 输出 DEBUG 级别日志（带耗时）。
     *
     * @param tagString    类简单名或模块标识
     * @param opString     操作名
     * @param msgString    结果描述
     * @param costTimeMs   操作耗时（毫秒）
     */
    @JvmStatic
    public fun d(
        tagString: String,
        opString: String,
        msgString: String,
        costTimeMs: Long
    ): Unit {
        log(LogLevel.DEBUG, tagString, opString, "$msgString | costTimeMs=$costTimeMs")
    }

    /**
     * 输出 INFO 级别日志。
     *
     * @param tagString 类简单名或模块标识
     * @param opString  操作名
     * @param msgString 结果描述
     */
    @JvmStatic
    public fun i(tagString: String, opString: String, msgString: String): Unit {
        log(LogLevel.INFO, tagString, opString, msgString)
    }

    /**
     * 输出 INFO 级别日志（带耗时）。
     *
     * @param tagString  类简单名或模块标识
     * @param opString   操作名
     * @param msgString  结果描述
     * @param costTimeMs 操作耗时（毫秒）
     */
    @JvmStatic
    public fun i(
        tagString: String,
        opString: String,
        msgString: String,
        costTimeMs: Long
    ): Unit {
        log(LogLevel.INFO, tagString, opString, "$msgString | costTimeMs=$costTimeMs")
    }

    /**
     * 输出 WARN 级别日志。
     *
     * @param tagString 类简单名或模块标识
     * @param opString  操作名
     * @param msgString 结果描述
     */
    @JvmStatic
    public fun w(tagString: String, opString: String, msgString: String): Unit {
        log(LogLevel.WARN, tagString, opString, msgString)
    }

    /**
     * 输出 WARN 级别日志（带耗时）。
     *
     * @param tagString  类简单名或模块标识
     * @param opString   操作名
     * @param msgString  结果描述
     * @param costTimeMs 操作耗时（毫秒）
     */
    @JvmStatic
    public fun w(
        tagString: String,
        opString: String,
        msgString: String,
        costTimeMs: Long
    ): Unit {
        log(LogLevel.WARN, tagString, opString, "$msgString | costTimeMs=$costTimeMs")
    }

    /**
     * 输出 ERROR 级别日志（无异常对象）。
     *
     * @param tagString 类简单名或模块标识
     * @param opString  操作名
     * @param msgString 结果描述
     */
    @JvmStatic
    public fun e(tagString: String, opString: String, msgString: String): Unit {
        log(LogLevel.ERROR, tagString, opString, msgString)
    }

    /**
     * 输出 ERROR 级别日志（异常对象可能为 null）。
     *
     * @param tagString       类简单名或模块标识
     * @param opString        操作名
     * @param throwable       异常对象，可能为 null
     * @param msgString       结果描述
     */
    @JvmStatic
    public fun e(
        tagString: String,
        opString: String,
        throwable: Throwable?,
        msgString: String
    ): Unit {
        if (throwable == null) {
            log(LogLevel.ERROR, tagString, opString, msgString)
        } else {
            log(LogLevel.ERROR, tagString, opString, "$msgString\n${throwable.stackTraceToString()}")
        }
    }

    /**
     * 输出 ERROR 级别日志（带异常对象与耗时）。
     *
     * @param tagString  类简单名或模块标识
     * @param opString   操作名
     * @param throwable  异常对象
     * @param msgString  结果描述
     * @param costTimeMs 操作耗时（毫秒）
     */
    @JvmStatic
    public fun e(
        tagString: String,
        opString: String,
        throwable: Throwable,
        msgString: String,
        costTimeMs: Long
    ): Unit {
        log(
            LogLevel.ERROR,
            tagString,
            opString,
            "$msgString | costTimeMs=$costTimeMs\n${throwable.stackTraceToString()}"
        )
    }

    private fun log(
        level: LogLevel,
        tagString: String,
        opString: String,
        msgString: String
    ) {
        val formattedMessage = "[$tagString] [$opString] | $msgString"
        if (isAndroidRuntime) {
            writeToAndroidLog(level, formattedMessage)
        } else {
            writeToJvmLog(level, formattedMessage)
        }
    }

    private fun writeToAndroidLog(level: LogLevel, message: String) {
        runCatching {
            when (level) {
                LogLevel.DEBUG -> Log.d(DEFAULT_LOG_TAG, message)
                LogLevel.INFO -> Log.i(DEFAULT_LOG_TAG, message)
                LogLevel.WARN -> Log.w(DEFAULT_LOG_TAG, message)
                LogLevel.ERROR -> Log.e(DEFAULT_LOG_TAG, message)
            }
        }.onFailure { throwable ->
            // Android Log 调用失败（通常发生在单元测试），回退到标准输出
            writeToJvmLog(level, "AndroidLogFallback | ${throwable.message}\n$message")
        }
    }

    private fun writeToJvmLog(level: LogLevel, message: String) {
        val prefix = "[$DEFAULT_LOG_TAG] ${level.name}: "
        when (level) {
            LogLevel.ERROR -> System.err.println("$prefix$message")
            else -> println("$prefix$message")
        }
    }

    private enum class LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }
}
