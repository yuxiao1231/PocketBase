package com.xiao.pocketbase

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class BookAdapter : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    private var bookList: List<Pair<File, BookMetadataEntity>> = emptyList()

    fun submitList(list: List<Pair<File, BookMetadataEntity>>) {
        bookList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val (file, meta) = bookList[position]
        val statusIcon = if (meta.isMatched) "✅" else "❓"
        val typeIcon = if (file.extension.lowercase() == "epub") "📗" else "📄"
        
        holder.tvIcon.text = typeIcon
        holder.tvTitle.text = "${meta.title} (${meta.author})"
        holder.tvStatus.text = statusIcon
    }

    override fun getItemCount(): Int = bookList.size

    class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIcon: TextView = itemView.findViewById(R.id.tv_item_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_item_title)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_item_status)
    }
}