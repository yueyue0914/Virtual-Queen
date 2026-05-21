package com.dianziqueen.app



import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.ImageView

import android.widget.ProgressBar

import android.widget.TextView

import androidx.recyclerview.widget.DiffUtil

import androidx.recyclerview.widget.ListAdapter

import androidx.recyclerview.widget.RecyclerView

import java.text.SimpleDateFormat

import java.util.Date

import java.util.Locale



class QueenMessageAdapter(

    private val onPhotoMessageClick: (QueenChatMessage) -> Unit,

) : ListAdapter<QueenChatMessage, RecyclerView.ViewHolder>(Diff) {



    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())



    override fun getItemViewType(position: Int): Int =

        if (getItem(position).hasPhoto) VIEW_TYPE_PHOTO else VIEW_TYPE_TEXT



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {

            VIEW_TYPE_PHOTO -> PhotoHolder(

                inflater.inflate(R.layout.item_message_queen_photo, parent, false),

                timeFormat,

                onPhotoMessageClick,

            )

            else -> TextHolder(

                inflater.inflate(R.layout.item_message_queen, parent, false),

                timeFormat,

            )

        }

    }



    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val message = getItem(position)

        when (holder) {

            is TextHolder -> holder.bind(message)

            is PhotoHolder -> holder.bind(message)

        }

    }



    class TextHolder(

        itemView: View,

        private val timeFormat: SimpleDateFormat,

    ) : RecyclerView.ViewHolder(itemView) {

        private val body: TextView = itemView.findViewById(R.id.messageBody)

        private val time: TextView = itemView.findViewById(R.id.messageTime)



        fun bind(message: QueenChatMessage) {
            body.text = QueenHonorific.apply(itemView.context, message.text)
            itemView.findViewById<TextView>(R.id.messageSender)?.text =
                itemView.context.hon(R.string.messages_sender_queen)
            time.text = timeFormat.format(Date(message.timestampMs))
        }

    }



    class PhotoHolder(

        itemView: View,

        private val timeFormat: SimpleDateFormat,

        private val onPhotoMessageClick: (QueenChatMessage) -> Unit,

    ) : RecyclerView.ViewHolder(itemView) {

        private val body: TextView = itemView.findViewById(R.id.messageBody)

        private val time: TextView = itemView.findViewById(R.id.messageTime)

        private val thumb: ImageView = itemView.findViewById(R.id.messagePhotoThumb)

        private val progress: ProgressBar = itemView.findViewById(R.id.messagePhotoThumbProgress)

        private val tapHint: TextView = itemView.findViewById(R.id.messagePhotoTapHint)

        private val frame: View = itemView.findViewById(R.id.messagePhotoFrame)



        private var boundPhotoId: String? = null



        fun bind(message: QueenChatMessage) {
            body.text = QueenHonorific.apply(itemView.context, message.text)
            itemView.findViewById<TextView>(R.id.messageSender)?.text =
                itemView.context.hon(R.string.messages_sender_queen)
            time.text = timeFormat.format(Date(message.timestampMs))

            val photoId = message.photoId ?: return

            boundPhotoId = photoId



            tapHint.text = itemView.context.getString(

                if (message.photoRevealed) {

                    R.string.message_photo_tap_view_clear

                } else {

                    R.string.message_photo_tap_fullscreen

                },

            )



            progress.visibility = View.VISIBLE

            thumb.setImageDrawable(null)



            val clickTarget = View.OnClickListener { onPhotoMessageClick(message) }

            frame.setOnClickListener(clickTarget)

            thumb.setOnClickListener(clickTarget)



            QueenMessagePhotoLoader.loadMosaicForList(itemView.context, photoId) { bmp ->

                if (boundPhotoId != photoId) return@loadMosaicForList

                progress.visibility = View.GONE

                if (bmp != null) {

                    thumb.setImageBitmap(bmp)

                } else {

                    thumb.setBackgroundColor(0xFF1A0D0D.toInt())

                    tapHint.text = itemView.context.getString(R.string.message_photo_missing)

                }

            }

        }

    }



    companion object {

        private const val VIEW_TYPE_TEXT = 0

        private const val VIEW_TYPE_PHOTO = 1



        private val Diff = object : DiffUtil.ItemCallback<QueenChatMessage>() {

            override fun areItemsTheSame(oldItem: QueenChatMessage, newItem: QueenChatMessage): Boolean =

                oldItem.id == newItem.id



            override fun areContentsTheSame(oldItem: QueenChatMessage, newItem: QueenChatMessage): Boolean =

                oldItem == newItem

        }

    }

}

