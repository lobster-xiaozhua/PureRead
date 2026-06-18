package com.pureread.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pureread.R
import com.pureread.databinding.ActivityMainBinding
import com.pureread.ui.browser.BrowserFragment
import com.pureread.ui.common.EdgeToEdgeHelper
import com.pureread.ui.library.LibraryFragment
import com.pureread.ui.settings.SettingsFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 应用主 Activity。
 *
 * 职责：
 * - 托管 [BottomNavigationView] 与三个一级页面（文库、浏览占位、设置）。
 * - 处理 Android 16 边到边 insets。
 *
 * 线程安全：所有 UI 操作运行在主线程。
 */
public class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModel()

    protected override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdge()
        setupBottomNavigation()
        observeNavigationState()
    }

    private fun applyEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        EdgeToEdgeHelper.applyInsets(
            binding.coordinator,
            isTop = true,
            isBottom = false,
            isLeft = true,
            isRight = true,
        )
        EdgeToEdgeHelper.applyBottomNavigationView(binding.bottomNavigation)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            viewModel.selectItem(menuItem.itemId)
            true
        }
    }

    private fun observeNavigationState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedItemIdFlow
                    .distinctUntilChanged()
                    .collect { itemIdInt ->
                        switchFragment(itemIdInt)
                        binding.bottomNavigation.selectedItemId = itemIdInt
                    }
            }
        }
    }

    private fun switchFragment(itemIdInt: Int) {
        val targetFragment = when (itemIdInt) {
            R.id.navigation_library -> LibraryFragment.newInstance()
            R.id.navigation_browser -> BrowserFragment.newInstance()
            R.id.navigation_settings -> SettingsFragment()
            else -> LibraryFragment.newInstance()
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, targetFragment)
            .commit()
    }
}
