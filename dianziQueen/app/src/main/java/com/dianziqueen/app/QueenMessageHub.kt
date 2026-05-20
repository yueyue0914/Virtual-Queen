package com.dianziqueen.app

import java.util.concurrent.CopyOnWriteArrayList

/** 女王新消息到达时通知 UI（如消息分页）。 */
object QueenMessageHub {

    fun interface Listener {
        fun onQueenMessageAppended(message: QueenChatMessage)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    internal fun dispatch(message: QueenChatMessage) {
        for (listener in listeners) {
            listener.onQueenMessageAppended(message)
        }
    }
}
