package com.pureread.core.log

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [PureLog] 单元测试。
 *
 * 重点验证：在 JVM 单元测试环境（无 Android Runtime）下不会抛出异常。
 */
internal class PureLogTest {

    @Test
    fun `d does not throw in jvm environment`() {
        PureLog.d("PureLogTest", "test", "debug message")
    }

    @Test
    fun `i does not throw in jvm environment`() {
        PureLog.i("PureLogTest", "test", "info message")
    }

    @Test
    fun `w does not throw in jvm environment`() {
        PureLog.w("PureLogTest", "test", "warn message")
    }

    @Test
    fun `e does not throw in jvm environment`() {
        PureLog.e("PureLogTest", "test", RuntimeException("dummy"), "error message")
    }

    @Test
    fun `isAndroidRuntime returns false in jvm environment`() {
        // 通过反射访问私有属性以断言当前环境判定
        val field = PureLog.javaClass.getDeclaredField("isAndroidRuntime\$delegate")
        field.isAccessible = true
        val lazy = field.get(PureLog) as Lazy<*>
        assertFalse(lazy.value as Boolean)
    }
}
