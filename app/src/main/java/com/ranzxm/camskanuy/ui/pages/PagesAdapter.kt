package com.ranzxm.camskanuy.ui.pages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.PageEntity
import com.ranzxm.camskanuy.databinding.ItemPageBinding
import com.ranzxm.camskanuy.ui.common.DocumentImages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class PagesAdapter(private val scope: CoroutineScope, private val edit: (PageEntity) -> Unit,
                   private val more: (PageEntity) -> Unit) :
    ListAdapter<PageEntity, PagesAdapter.Holder>(object : DiffUtil.ItemCallback<PageEntity>() {
        override fun areItemsTheSame(oldItem: PageEntity, newItem: PageEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PageEntity, newItem: PageEntity) = oldItem == newItem
    }) {
    class Holder(val binding: ItemPageBinding) : RecyclerView.ViewHolder(binding.root) { var imageJob: Job? = null }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val page = getItem(position)
        val binding = holder.binding
        binding.number.text = binding.root.context.getString(R.string.doc_page_number, position + 1)
        binding.more.contentDescription = binding.root.context.getString(R.string.doc_page_options, position + 1)
        binding.root.setOnClickListener { edit(page) }
        binding.more.setOnClickListener { more(page) }
        holder.imageJob?.cancel()
        holder.imageJob = DocumentImages.load(scope, page.thumbnailPath, binding.thumbnail)
    }
    override fun onViewRecycled(holder: Holder) {
        holder.imageJob?.cancel(); holder.binding.thumbnail.setImageDrawable(null)
        super.onViewRecycled(holder)
    }
}
