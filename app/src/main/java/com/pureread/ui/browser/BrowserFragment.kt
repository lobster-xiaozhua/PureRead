package com.pureread.ui.browser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pureread.databinding.FragmentBrowserBinding

/**
 * 浏览页占位 Fragment。
 *
 * 职责：
 * - 在主页底部导航中提供“浏览”入口占位。
 * - 点击按钮跳转至完整的 [BrowserActivity]。
 *
 * 线程安全：所有方法均运行在主线程。
 */
public class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding: FragmentBrowserBinding
        get() = checkNotNull(_binding) { "Binding 仅在 onCreateView 之后可用" }

    public override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    public override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonOpenBrowser.setOnClickListener {
            startActivity(Intent(requireContext(), BrowserActivity::class.java))
        }
    }

    public override fun onDestroyView(): Unit {
        super.onDestroyView()
        _binding = null
    }

    public companion object {

        /**
         * 创建 [BrowserFragment] 实例。
         *
         * @return 新的 Fragment 实例
         */
        @JvmStatic
        public fun newInstance(): BrowserFragment = BrowserFragment()
    }
}
