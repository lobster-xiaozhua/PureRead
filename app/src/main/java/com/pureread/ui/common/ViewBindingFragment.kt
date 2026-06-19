package com.pureread.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * 基于 [ViewBinding] 的 Fragment 抽象基类。
 *
 * 职责：
 * - 统一托管 Binding 的生命周期，避免内存泄漏。
 * - 子类仅需实现 [createBinding] 提供具体 Binding 实例。
 *
 * 线程安全：所有方法均运行在主线程。
 */
public abstract class ViewBindingFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    /**
     * 仅在 [onCreateView] 之后、[onDestroyView] 之前可安全访问。
     */
    protected val binding: VB
        get() = checkNotNull(_binding) { "Binding 仅在 onCreateView 之后可用" }

    /**
     * 创建具体 Binding 实例。
     *
     * @param inflater 布局充气器
     * @param container 父容器，可能为 null
     * @return 已充气但未附加的 Binding 实例
     */
    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    public override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = createBinding(inflater, container)
        return binding.root
    }

    public override fun onDestroyView(): Unit {
        super.onDestroyView()
        _binding = null
    }
}
