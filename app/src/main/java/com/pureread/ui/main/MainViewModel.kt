package com.pureread.ui.main

import androidx.lifecycle.ViewModel
import com.pureread.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主页底部导航状态 ViewModel。
 *
 * 职责：
 * - 维护当前选中的底部导航项 ID。
 * - 在配置变更后恢复导航状态。
 *
 * 线程安全：[selectedItemIdFlow] 仅在主线程消费。
 */
public class MainViewModel : ViewModel() {

    private val _selectedItemIdFlow = MutableStateFlow(R.id.navigation_library)

    /**
     * 当前选中的底部导航项 ID。
     */
    public val selectedItemIdFlow: StateFlow<Int> = _selectedItemIdFlow.asStateFlow()

    /**
     * 切换当前选中的底部导航项。
     *
     * @param itemIdInt 导航项 ID
     */
    public fun selectItem(itemIdInt: Int): Unit {
        _selectedItemIdFlow.value = itemIdInt
    }
}
