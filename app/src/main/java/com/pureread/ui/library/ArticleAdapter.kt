package com.pureread.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pureread.databinding.ItemArticleBinding

/**
 * 书架文章列表适配器，支持长按进入多选模式。
 *
 * 职责：
 * - 渲染 [ArticleUiModel] 列表。
 * - 维护多选状态并在点击事件中切换。
 *
 * 线程安全：所有回调均在主线程。
 */
public class ArticleAdapter public constructor(
    private val listener: ArticleClickListener,
) : ListAdapter<ArticleUiModel, ArticleAdapter.ArticleViewHolder>(DiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()

    /**
     * 当前是否处于多选模式。
     */
    public fun isSelectionMode(): Boolean = selectedIds.isNotEmpty()

    /**
     * 获取当前已选中的文章 ID 集合（只读副本）。
     */
    public fun getSelectedIds(): Set<Long> = selectedIds.toSet()

    /**
     * 重置并设置选中的文章 ID。
     *
     * @param ids 新的选中集合
     */
    public fun setSelectedIds(ids: Set<Long>): Unit {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    /**
     * 清空所有选中状态。
     */
    public fun clearSelection(): Unit {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    public override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemArticleBinding.inflate(inflater, parent, false)
        return ArticleViewHolder(binding)
    }

    public override fun onBindViewHolder(holder: ArticleViewHolder, position: Int): Unit {
        val article = getItem(position)
        holder.bind(article, selectedIds.contains(article.idLong))
    }

    private fun toggleSelection(articleIdLong: Long) {
        if (selectedIds.contains(articleIdLong)) {
            selectedIds.remove(articleIdLong)
        } else {
            selectedIds.add(articleIdLong)
        }
        notifyDataSetChanged()
    }

    /**
     * 列表项点击回调接口。
     */
    public interface ArticleClickListener {

        /**
         * 普通点击（非选择模式下打开阅读器）。
         */
        public fun onItemClick(article: ArticleUiModel): Unit

        /**
         * 长按点击（进入或继续选择模式）。
         */
        public fun onItemLongClick(article: ArticleUiModel): Unit
    }

    private inner class ArticleViewHolder(
        private val binding: ItemArticleBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private fun bind(article: ArticleUiModel, isSelected: Boolean) {
            binding.textTitle.text = article.title
            binding.textAuthor.text = article.author ?: ""
            binding.textAuthor.isVisible = !article.author.isNullOrBlank()
            binding.textMeta.text = buildMetaText(article)
            binding.progressRead.progress = article.readProgressPercentInt
            binding.checkSelected.isVisible = isSelectionMode()
            binding.checkSelected.isChecked = isSelected

            val context = binding.root.context
            if (isSelected) {
                binding.cardArticle.setCardBackgroundColor(
                    context.getColor(com.pureread.R.color.md_theme_primaryContainer),
                )
            } else {
                binding.cardArticle.setCardBackgroundColor(
                    context.getColor(com.pureread.R.color.md_theme_surface),
                )
            }

            binding.root.setOnClickListener {
                if (isSelectionMode()) {
                    toggleSelection(article.idLong)
                    listener.onItemClick(article)
                } else {
                    listener.onItemClick(article)
                }
            }

            binding.root.setOnLongClickListener {
                toggleSelection(article.idLong)
                listener.onItemLongClick(article)
                true
            }
        }

        private fun buildMetaText(article: ArticleUiModel): String {
            val wordCountText = "${article.wordCountInt} 字"
            val progressText = "已读 ${article.readProgressPercentInt}%"
            return "$wordCountText · $progressText"
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ArticleUiModel>() {
        public override fun areItemsTheSame(oldItem: ArticleUiModel, newItem: ArticleUiModel): Boolean {
            return oldItem.idLong == newItem.idLong
        }

        public override fun areContentsTheSame(oldItem: ArticleUiModel, newItem: ArticleUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
