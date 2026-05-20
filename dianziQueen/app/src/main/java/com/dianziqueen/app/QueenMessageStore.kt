package com.dianziqueen.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object QueenMessageStore {

    private const val MAX_MESSAGES = 300
    private const val KEY_MESSAGES_JSON = "queen_chat_messages_json"

    /** 相册有图时，本条消息改为「羞辱话术 + 图」的概率（不宜过高）。 */
    private const val PHOTO_TAUNT_PROBABILITY = 0.15f

    /** 两条带图消息之间至少间隔（毫秒）。 */
    private const val PHOTO_TAUNT_MIN_INTERVAL_MS = 18 * 60 * 1000L

    /** 选图时尽量避开最近若干条带图消息已用过的 photoId。 */
    private const val RECENT_PHOTO_ID_LOOKBACK = 12

    /** 消息内点开无码大图消耗（与相册查看一致）。 */
    const val PHOTO_VIEW_COST = 5

    fun load(context: Context): List<QueenChatMessage> {
        val raw = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getString(KEY_MESSAGES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QueenChatMessage(
                            id = o.getString("id"),
                            text = o.getString("text"),
                            timestampMs = o.getLong("ts"),
                            photoId = o.optString("photoId", "").ifBlank { null },
                            photoRevealed = o.optBoolean("photoRevealed", false),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun appendQueenMessage(
        context: Context,
        text: String,
        photoId: String? = null,
    ): QueenChatMessage {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty())
        val message = QueenChatMessage(
            id = "${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
            text = trimmed,
            timestampMs = System.currentTimeMillis(),
            photoId = photoId?.takeIf { it.isNotBlank() },
            photoRevealed = false,
        )
        val list = load(context).toMutableList()
        list.add(message)
        while (list.size > MAX_MESSAGES) {
            list.removeAt(0)
        }
        save(context, list)
        QueenMessageHub.dispatch(message)
        return message
    }

    fun appendRandomQueenMessage(context: Context): QueenChatMessage? {
        if (!isActivated(context)) return null
        val photoIds = QueenAlbumVault.listPhotoIds(context)
        if (
            photoIds.isNotEmpty() &&
            canSendPhotoTauntNow(context) &&
            Random.nextFloat() < PHOTO_TAUNT_PROBABILITY
        ) {
            return appendRandomPhotoTauntMessage(context, photoIds)
        }
        val line = randomInsultLine(context) ?: return null
        return appendQueenMessage(context, line)
    }

    private fun canSendPhotoTauntNow(context: Context): Boolean {
        val lastPhoto = load(context).lastOrNull { it.hasPhoto } ?: return true
        return System.currentTimeMillis() - lastPhoto.timestampMs >= PHOTO_TAUNT_MIN_INTERVAL_MS
    }

    private fun appendRandomPhotoTauntMessage(
        context: Context,
        photoIds: List<String>,
    ): QueenChatMessage {
        val photoId = pickPhotoIdForMessage(context, photoIds)
        val messages = load(context)
        val recentTexts = messages.takeLast(20).map { it.text }
        val text = QueenAlbumTauntLibrary.getRandom(recentTexts)
            ?: "啧啧啧，看看这是哪个贱狗的照片啊？"
        return appendQueenMessage(context, text, photoId)
    }

    /**
     * 从加密相册中随机选一张，优先避开最近带图消息已用过的 id；
     * 若相册仅一张则只能重复该张。
     */
    private fun pickPhotoIdForMessage(context: Context, photoIds: List<String>): String {
        if (photoIds.size == 1) return photoIds.first()
        val allMessages = load(context)
        val recentlyUsed = allMessages
            .asReversed()
            .filter { it.hasPhoto }
            .take(RECENT_PHOTO_ID_LOOKBACK)
            .mapNotNull { it.photoId }
            .toSet()
        val fresh = photoIds.filter { it !in recentlyUsed }
        if (fresh.isNotEmpty()) return fresh.random()
        // 近期每张都用过了：选「上一次出现最早」的那张
        val lastIndexById = photoIds.associateWith { id ->
            allMessages.indexOfLast { it.photoId == id }
        }
        return photoIds.minByOrNull { lastIndexById[it] ?: Int.MIN_VALUE } ?: photoIds.random()
    }

    fun markMessagePhotoRevealed(context: Context, messageId: String): Boolean {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == messageId }
        if (idx < 0) return false
        if (list[idx].photoRevealed) return true
        list[idx] = list[idx].copy(photoRevealed = true)
        save(context, list)
        QueenMessageHub.dispatch(list[idx])
        return true
    }

    fun getUnreadCount(context: Context): Int {
        val lastRead = lastReadTimestampMs(context)
        return load(context).count { it.timestampMs > lastRead }
    }

    /** 进入消息分页或正在该分页浏览时调用，清零未读。 */
    fun markAllRead(context: Context) {
        val messages = load(context)
        val markTs = messages.maxOfOrNull { it.timestampMs } ?: System.currentTimeMillis()
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(Prefs.QUEEN_MESSAGES_LAST_READ_TS, markTs)
            .apply()
    }

    private fun lastReadTimestampMs(context: Context): Long =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getLong(Prefs.QUEEN_MESSAGES_LAST_READ_TS, 0L)

    fun ensureSessionOpened(context: Context) {
        if (!isActivated(context)) return
        if (load(context).isNotEmpty()) return
        appendQueenMessage(
            context,
            "消息通道已接通。从现在起，本女王会不定时来骂你、笑你。别妄想回复，你只配跪着读。",
        )
    }

    private fun randomInsultLine(context: Context): String? {
        if (!isActivated(context)) return null
        val recent = load(context).takeLast(10).map { it.text }
        return QueenInsultLibrary.getRandom(recent)
    }

    private fun isActivated(context: Context): Boolean =
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(Prefs.ACTIVATED, false)

    private fun save(context: Context, messages: List<QueenChatMessage>) {
        val arr = JSONArray()
        for (m in messages) {
            val o = JSONObject()
                .put("id", m.id)
                .put("text", m.text)
                .put("ts", m.timestampMs)
            if (m.photoId != null) {
                o.put("photoId", m.photoId)
                o.put("photoRevealed", m.photoRevealed)
            }
            arr.put(o)
        }
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MESSAGES_JSON, arr.toString())
            .apply()
    }
}
