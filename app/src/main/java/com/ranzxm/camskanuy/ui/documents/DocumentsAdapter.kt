package com.ranzxm.camskanuy.ui.documents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ranzxm.camskanuy.R
import com.ranzxm.camskanuy.data.DocumentWithPages
import com.ranzxm.camskanuy.databinding.ItemDocumentBinding
import com.ranzxm.camskanuy.ui.common.DocumentImages
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class DocumentsAdapter(
    private val scope: CoroutineScope,
    private val open: (DocumentWithPages) -> Unit,
    private val more: (DocumentWithPages) -> Unit,
) : ListAdapter<DocumentWithPages, DocumentsAdapter.Holder>(object : DiffUtil.ItemCallback<DocumentWithPages>() {
    override fun areItemsTheSame(oldItem: DocumentWithPages, newItem: DocumentWithPages) = oldItem.document.id == newItem.document.id
    override fun areContentsTheSame(oldItem: DocumentWithPages, newItem: DocumentWithPages) = oldItem == newItem
}) {
    class Holder(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root) { var imageJob: Job? = null }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val doc = item.document
        val binding = holder.binding
        val context = binding.root.context
        binding.name.text = if (doc.isDraft) context.getString(R.string.doc_draft_name, doc.name) else doc.name
        binding.metadata.text = context.getString(R.string.doc_metadata, item.pages.size,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(doc.updatedAt)))
        binding.more.contentDescription = context.getString(R.string.doc_document_options, doc.name)
        binding.root.setOnClickListener { open(item) }
        binding.more.setOnClickListener { more(item) }
        holder.imageJob?.cancel()
        holder.imageJob = DocumentImages.load(scope, item.sortedPages.firstOrNull()?.thumbnailPath, binding.thumbnail)
    }

    override fun onViewRecycled(holder: Holder) {
        holder.imageJob?.cancel()
        holder.binding.thumbnail.setImageDrawable(null)
        super.onViewRecycled(holder)
    }
}
