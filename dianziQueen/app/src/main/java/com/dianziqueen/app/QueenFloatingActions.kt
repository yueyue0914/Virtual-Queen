package com.dianziqueen.app

import android.content.Context
import android.content.Intent
import android.widget.Toast

/** 悬浮女王菜单动作（供 [QueenFloatingOverlay] 与 [QueenMenuDialog] 共用）。 */
object QueenFloatingActions {

    fun onConfess(context: Context, showTaunt: (QueenFloatingMood, String) -> Unit) {
        val app = context.applicationContext
        val insult = QueenInsultLibrary.getRandom()?.let { QueenHonorific.apply(app, it) }.orEmpty()
        if (insult.isNotEmpty()) {
            QueenMessageStore.appendQueenMessage(app, "【认罪】$insult")
        }
        showTaunt(QueenFloatingMood.COLD_SMILE, QueenFloatingPhraseLibrary.confessionReply())
    }

    fun onDailyTask(context: Context, showTaunt: (QueenFloatingMood, String) -> Unit) {
        val app = context.applicationContext
        if (DailySelfieScheduler.shouldEnforce(app)) {
            DailySelfieEnforcement.launch(app)
            showTaunt(QueenFloatingMood.COMMAND, app.getString(R.string.queen_float_daily_enforced))
        } else {
            showTaunt(QueenFloatingMood.COLD_SMILE, app.getString(R.string.queen_float_daily_done))
        }
    }

    fun onPunishment(context: Context, showTaunt: (QueenFloatingMood, String) -> Unit) {
        val app = context.applicationContext
        QueenVibratorHelper.punish(app)
        showTaunt(QueenFloatingMood.ANGRY, QueenFloatingPhraseLibrary.punishmentTaunt())
    }

    fun onSelfieUpload(context: Context, showTaunt: (QueenFloatingMood, String) -> Unit) {
        val app = context.applicationContext
        if (DailySelfieScheduler.shouldEnforce(app)) {
            DailySelfieEnforcement.bringDemandToFront(app)
        } else {
            openMainTab(app, album = true)
            Toast.makeText(app, R.string.queen_float_selfie_to_album, Toast.LENGTH_SHORT).show()
        }
        showTaunt(QueenFloatingMood.EXCITED, app.getString(R.string.queen_float_selfie_go))
    }

    fun onMessages(context: Context) {
        openMainTab(context.applicationContext, messages = true)
    }

    private fun openMainTab(app: Context, messages: Boolean = false, album: Boolean = false) {
        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (messages) putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true)
            if (album) putExtra(MainActivity.EXTRA_OPEN_ALBUM, true)
        }
        app.startActivity(intent)
    }
}
