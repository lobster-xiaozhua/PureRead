package com.pureread.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.pureread.R
import com.pureread.core.log.PureLog
import com.pureread.databinding.FragmentSettingsBinding

/**
 * 设置页面 Fragment。
 *
 * 职责：
 * - 使用 [PreferenceFragmentCompat] 提供主题、字号等设置项。
 * - 支持一键清除 WebView 与磁盘缓存。
 *
 * 线程安全：所有方法均运行在主线程。
 */
public class SettingsFragment : PreferenceFragmentCompat() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding: FragmentSettingsBinding
        get() = checkNotNull(_binding) { "Binding 仅在 onCreateView 之后可用" }

    public override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val preferenceView = super.onCreateView(inflater, binding.settingsContainer, savedInstanceState)
        if (preferenceView != null && preferenceView.parent == null) {
            binding.settingsContainer.addView(preferenceView)
        }
        binding.toolbarSettings.setTitle(R.string.bottom_nav_settings)
        return binding.root
    }

    public override fun onDestroyView(): Unit {
        super.onDestroyView()
        _binding = null
    }

    public override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?): Unit {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    public override fun onPreferenceTreeClick(preference: androidx.preference.Preference): Boolean {
        return when (preference.key) {
            KEY_CLEAR_CACHE -> {
                clearCache()
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun clearCache() {
        val context = requireContext()
        try {
            WebView(context).clearCache(true)
            context.cacheDir.deleteRecursively()
            PureLog.i(TAG, "clearCache", "缓存清除 | 完成")
            showSnackbar(getString(R.string.settings_cache_cleared))
        } catch (e: Exception) {
            PureLog.e(TAG, "clearCache", e, "缓存清除失败")
            showSnackbar(getString(R.string.settings_cache_clear_failed))
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private companion object {
        private const val TAG = "SettingsFragment"
        private const val KEY_CLEAR_CACHE = "clear_cache"
    }
}
