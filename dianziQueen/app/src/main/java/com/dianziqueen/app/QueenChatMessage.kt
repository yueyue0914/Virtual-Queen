package com.dianziqueen.app

data class QueenChatMessage(
    val id: String,
    val text: String,
    val timestampMs: Long,
    /** 关联加密相册照片 id；非空时消息带马赛克图。 */
    val photoId: String? = null,
    /** 本条消息内已付积分看过无码。 */
    val photoRevealed: Boolean = false,
) {
    val hasPhoto: Boolean get() = !photoId.isNullOrBlank()
}
