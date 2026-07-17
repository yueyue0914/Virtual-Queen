package com.dianziqueen.app

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AlbumPhotoAdapter(
    private val onPhotoClick: (String) -> Unit,
    private val onPhotoLongClick: (String) -> Unit,
) : ListAdapter<String, AlbumPhotoAdapter.Holder>(Diff) {

    private var cachedBitmapProvider: ((String) -> android.graphics.Bitmap?)? = null
    private var requestLoad: ((String) -> Unit)? = null

    /** 缓存命中立刻显示；未命中先占位，再通过 [requestLoad] 异步解码。 */
    fun setAsyncThumbnails(
        getCached: (String) -> android.graphics.Bitmap?,
        requestLoad: (String) -> Unit,
    ) {
        cachedBitmapProvider = getCached
        this.requestLoad = requestLoad
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album_photo, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val id = getItem(position)
        val cached = cachedBitmapProvider?.invoke(id)
        holder.bind(id, cached)
        if (cached == null) {
            requestLoad?.invoke(id)
        }
        holder.itemView.setOnClickListener { onPhotoClick(id) }
        holder.itemView.setOnLongClickListener {
            onPhotoLongClick(id)
            true
        }
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumb: ImageView = itemView.findViewById(R.id.albumPhotoThumb)
        private val overlay: View = itemView.findViewById(R.id.albumPhotoOverlay)

        fun bind(photoId: String, bitmap: android.graphics.Bitmap?) {
            if (bitmap != null) {
                thumb.setImageBitmap(bitmap)
                thumb.setBackgroundColor(0)
            } else {
                thumb.setImageDrawable(null)
                thumb.setBackgroundColor(0xFF1A0D0D.toInt())
            }
            val redeemed = QueenAlbumVault.isRedeemed(itemView.context, photoId)
            overlay.visibility = if (redeemed) View.GONE else View.VISIBLE
            thumb.contentDescription = photoId
        }
    }

    companion object {
        fun decodeThumbnail(bytes: ByteArray, maxSide: Int = 512): android.graphics.Bitmap? {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) {
                sample *= 2
            }
            return BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }

        private val Diff = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean =
                oldItem == newItem
        }
    }
}
