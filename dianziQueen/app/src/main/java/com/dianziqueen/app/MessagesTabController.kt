package com.dianziqueen.app



import android.view.View

import androidx.activity.result.contract.ActivityResultContracts

import androidx.appcompat.app.AppCompatActivity

import androidx.recyclerview.widget.LinearLayoutManager

import androidx.recyclerview.widget.RecyclerView



/**

 * 激活后「消息」分页：模拟与女王的单向对话，不定时收到侮辱/取笑信息。

 */

class MessagesTabController(

    private val activity: AppCompatActivity,

    messagesRoot: View,

    private val onPointsChanged: () -> Unit = {},
    private val onUnreadChanged: () -> Unit = {},

) : QueenMessageHub.Listener {



    private val messagesList: RecyclerView = messagesRoot.findViewById(R.id.messagesList)

    private val adapter = QueenMessageAdapter { message -> openPhotoFullscreen(message) }

    private val layoutManager = LinearLayoutManager(activity).apply {

        stackFromEnd = true

    }



    private val photoViewerLauncher = activity.registerForActivityResult(

        ActivityResultContracts.StartActivityForResult(),

    ) { result ->

        if (result.resultCode == AppCompatActivity.RESULT_OK) {

            onPointsChanged()

            refreshList(scrollToEnd = false)

        }

    }



    init {

        messagesList.layoutManager = layoutManager

        messagesList.adapter = adapter

        QueenMessageHub.addListener(this)

    }



    fun onTabShown() {
        QueenMessageStore.ensureSessionOpened(activity)
        QueenMessageStore.markAllRead(activity)
        onUnreadChanged()
        refreshList(scrollToEnd = true)
    }



    fun shutdown() {

        QueenMessageHub.removeListener(this)

    }



    override fun onQueenMessageAppended(message: QueenChatMessage) {

        activity.runOnUiThread {

            val current = adapter.currentList.toMutableList()

            val existingIdx = current.indexOfFirst { it.id == message.id }

            if (existingIdx >= 0) {

                current[existingIdx] = message

            } else {

                current.add(message)

            }

            adapter.submitList(current) {

                scrollToEnd()

            }

        }

    }



    private fun openPhotoFullscreen(message: QueenChatMessage) {

        val photoId = message.photoId ?: return

        if (!QueenAlbumVault.listPhotoIds(activity).contains(photoId)) {

            android.widget.Toast.makeText(

                activity,

                R.string.message_photo_missing,

                android.widget.Toast.LENGTH_SHORT,

            ).show()

            return

        }

        photoViewerLauncher.launch(

            MessagePhotoFullscreenActivity.intent(

                activity,

                message.id,

                photoId,

                message.photoRevealed,

            ),

        )

    }



    private fun refreshList(scrollToEnd: Boolean) {

        val items = QueenMessageStore.load(activity)

        adapter.submitList(items) {

            if (scrollToEnd) scrollToEnd()

        }

    }



    private fun scrollToEnd() {

        val count = adapter.itemCount

        if (count > 0) {

            messagesList.smoothScrollToPosition(count - 1)

        }

    }

}

