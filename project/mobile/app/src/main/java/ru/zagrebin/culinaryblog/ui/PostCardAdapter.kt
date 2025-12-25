package ru.zagrebin.culinaryblog.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.model.PostCard

class PostCardAdapter(
    var items: List<PostCard>,
    private val onClick: (PostCard) -> Unit
) : RecyclerView.Adapter<PostCardAdapter.PostCardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_card, parent, false)
        return PostCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostCardViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class PostCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(post: PostCard, onClick: (PostCard) -> Unit) {
            val title = itemView.findViewById<TextView>(R.id.postTitle)
            val excerpt = itemView.findViewById<TextView>(R.id.postExcerpt)
            val cover = itemView.findViewById<ImageView>(R.id.postCover)
            val author = itemView.findViewById<TextView>(R.id.authorName)
            title.text = post.title
            excerpt.text = post.excerpt
            author.text = post.authorName ?: ""
            cover.load(post.coverUrl)
            itemView.setOnClickListener { onClick(post) }
        }
    }
}
