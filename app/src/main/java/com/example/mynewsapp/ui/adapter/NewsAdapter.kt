package com.example.mynewsapp.ui.adapter

import android.graphics.drawable.Drawable
import com.example.mynewsapp.model.Article
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.example.mynewsapp.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NewsAdapter : ListAdapter<Article, NewsAdapter.ArticleViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Article>() {
            override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
                return oldItem == newItem
            }

        }
    }

    class ArticleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView = view.findViewById<TextView>(R.id.tvTitle)
        val publishedAtView = view.findViewById<TextView>(R.id.tvPublishedAt)
        val articleImageView = view.findViewById<ImageView>(R.id.ivArticleImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news_article, parent, false)
        return ArticleViewHolder(view)
    }

    private var onNewsClickListener: ((Article) -> Unit)? = null

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        val currentArticle = getItem(position)

        holder.apply {
            titleView.text = currentArticle.title

            Glide
                .with(this.itemView)
                .load(currentArticle.urlToImage)
                .centerCrop()
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in Drawable>?
                    ) {
                        // load as imageView background
                        articleImageView.background = resource
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }
                })
            // format publishedAt time
            val inputPattern = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault())
            val outputPattern = DateTimeFormatter.ofPattern("yyyy/MM/dd kk:mm")
            publishedAtView.text = ZonedDateTime.parse(currentArticle.publishedAt, inputPattern).format(outputPattern)

            itemView.setOnClickListener {

                onNewsClickListener?.let { it1 -> it1(currentArticle) }
            }

        }
    }


    fun setClickListener(listener: (Article) -> Unit) {
        onNewsClickListener = listener
    }

}