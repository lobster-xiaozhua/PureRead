package com.pureread.ui.library

import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.pureread.R
import com.pureread.databinding.FragmentLibraryBinding
import com.pureread.ui.common.ViewBindingFragment
import com.pureread.ui.reader.ReaderActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 书架页面 Fragment。
 *
 * 职责：
 * - 展示文章列表、空状态、搜索栏与下拉刷新。
 * - 支持长按进入多选并通过 ActionMode 批量删除。
 *
 * 线程安全：所有 UI 操作运行在主线程。
 */
public class LibraryFragment : ViewBindingFragment<FragmentLibraryBinding>() {

    private val viewModel: LibraryViewModel by viewModel()
    private lateinit var adapter: ArticleAdapter

    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        public override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.add(0, ACTION_DELETE_ID, 0, getString(R.string.action_delete))
                ?.setIcon(android.R.drawable.ic_menu_delete)
                ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            updateActionModeTitle(mode)
            return true
        }

        public override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        public override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            if (item?.itemId == ACTION_DELETE_ID) {
                viewModel.deleteSelected(adapter.getSelectedIds())
                finishActionMode()
                return true
            }
            return false
        }

        public override fun onDestroyActionMode(mode: ActionMode?): Unit {
            adapter.clearSelection()
            actionMode = null
        }
    }

    protected override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentLibraryBinding = FragmentLibraryBinding.inflate(inflater, container, false)

    public override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit {
        super.onViewCreated(view, savedInstanceState)
        adapter = ArticleAdapter(createArticleClickListener())
        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun createArticleClickListener(): ArticleAdapter.ArticleClickListener {
        return object : ArticleAdapter.ArticleClickListener {
            public override fun onItemClick(article: ArticleUiModel): Unit {
                if (adapter.isSelectionMode()) {
                    updateActionModeTitle(actionMode)
                    if (!adapter.isSelectionMode()) {
                        finishActionMode()
                    }
                } else {
                    ReaderActivity.start(requireContext(), article.idLong)
                }
            }

            public override fun onItemLongClick(article: ArticleUiModel): Unit {
                if (!adapter.isSelectionMode()) {
                    startActionMode()
                }
                updateActionModeTitle(actionMode)
                if (!adapter.isSelectionMode()) {
                    finishActionMode()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerArticles.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchInput.editText?.doAfterTextChanged { editable ->
            viewModel.search(editable?.toString().orEmpty())
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.articlesUiFlow.collect { articleList ->
                        adapter.submitList(articleList)
                        setEmptyStateVisible(articleList.isEmpty())
                    }
                }

                launch {
                    viewModel.isLoadingFlow.collect { isLoading ->
                        binding.swipeRefresh.isRefreshing = isLoading
                    }
                }
            }
        }
    }

    private fun setEmptyStateVisible(isEmpty: Boolean) {
        binding.emptyStateView.root.isVisible = isEmpty
        binding.recyclerArticles.isVisible = !isEmpty
    }

    private fun startActionMode() {
        val activity = requireActivity() as? AppCompatActivity ?: return
        actionMode = activity.startSupportActionMode(actionModeCallback)
    }

    private fun updateActionModeTitle(mode: ActionMode?) {
        val count = adapter.getSelectedIds().size
        mode?.title = getString(R.string.selected_count, count)
    }

    private fun finishActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    public companion object {

        /**
         * 创建 [LibraryFragment] 实例。
         *
         * @return 新的 Fragment 实例
         */
        @JvmStatic
        public fun newInstance(): LibraryFragment = LibraryFragment()

        private const val ACTION_DELETE_ID = 1
    }
}
