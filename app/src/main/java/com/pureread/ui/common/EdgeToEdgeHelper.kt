package com.pureread.ui.common

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 边到边（Edge-to-Edge）Insets 工具类。
 *
 * 职责：
 * - 为 Activity/Fragment 根视图分配系统栏 insets。
 * - 兼容 Android 16 默认开启的 edge-to-edge 行为。
 *
 * 线程安全：所有方法均运行在主线程。
 */
public object EdgeToEdgeHelper {

    /**
     * 开启 Activity 的边到边绘制，并将系统栏 insets 应用到根视图。
     *
     * @param activity 目标 Activity
     * @param root 需要接收 insets 的根视图
     */
    @JvmStatic
    public fun applyToActivity(activity: Activity, root: View): Unit {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        applyInsets(root, isTop = true, isBottom = true, isLeft = true, isRight = true)
    }

    /**
     * 将系统栏 insets 按指定方向应用到视图 padding。
     *
     * @param view 目标视图
     * @param isTop 是否应用顶部 inset
     * @param isBottom 是否应用底部 inset
     * @param isLeft 是否应用左侧 inset
     * @param isRight 是否应用右侧 inset
     */
    @JvmStatic
    public fun applyInsets(
        view: View,
        isTop: Boolean,
        isBottom: Boolean,
        isLeft: Boolean,
        isRight: Boolean,
    ): Unit {
        ViewCompat.setOnApplyWindowInsetsListener(view) { targetView, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            targetView.updatePadding(
                left = if (isLeft) systemBars.left else targetView.paddingLeft,
                top = if (isTop) systemBars.top else targetView.paddingTop,
                right = if (isRight) systemBars.right else targetView.paddingRight,
                bottom = if (isBottom) systemBars.bottom else targetView.paddingBottom,
            )
            insets
        }
    }

    /**
     * 专为底部导航栏分配底部导航栏 inset。
     *
     * @param bottomNav 底部导航视图
     */
    @JvmStatic
    public fun applyBottomNavigationView(bottomNav: View): Unit {
        applyInsets(bottomNav, isTop = false, isBottom = true, isLeft = false, isRight = false)
    }
}
