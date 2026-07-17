package com.dianziqueen.app



import android.content.Context

import android.widget.Toast

import androidx.annotation.StringRes

import androidx.appcompat.app.AlertDialog

import androidx.appcompat.app.AppCompatActivity

import java.util.Locale



/**

 * 可切换对主人的称谓（女王 / 黑爹 / 主人 / 爸爸 / 妈妈）。

 * 文案中的 Queen、女王、本女王等会通过 [apply] 替换为当前称谓。

 */

object QueenHonorific {



    enum class Preset(val storageKey: String, @StringRes val labelRes: Int, val defaultText: String) {

        QUEEN("queen", R.string.honorific_preset_queen, "女王"),

        BLACK_DAD("black_dad", R.string.honorific_preset_black_dad, "黑爹"),

        MASTER("master", R.string.honorific_preset_master, "主人"),

        DAD("dad", R.string.honorific_preset_dad, "爸爸"),

        MOM("mom", R.string.honorific_preset_mom, "妈妈"),

    }



    fun displayName(context: Context): String {
        val preset = getPreset(context)
        return context.getString(preset.labelRes)
    }



    fun getPreset(context: Context): Preset {

        val key = context.applicationContext

            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

            .getString(Prefs.QUEEN_HONORIFIC_PRESET, Preset.QUEEN.storageKey)

            ?: Preset.QUEEN.storageKey

        if (key == "custom") return Preset.QUEEN

        return Preset.entries.firstOrNull { it.storageKey == key } ?: Preset.QUEEN

    }



    fun setPreset(context: Context, preset: Preset) {

        context.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

            .edit()

            .putString(Prefs.QUEEN_HONORIFIC_PRESET, preset.storageKey)

            .apply()

    }



    fun apply(context: Context, raw: String): String {

        if (raw.isEmpty()) return raw

        val h = displayName(context)

        var out = raw

        for ((pattern, replacer) in REPLACERS) {

            if (out.contains(pattern, ignoreCase = false)) {

                out = out.replace(pattern, replacer(h), ignoreCase = false)

            }

        }

        return out

    }



    fun showPicker(activity: AppCompatActivity, onChanged: () -> Unit) {

        val presets = Preset.entries.toTypedArray()

        val labels = presets.map { activity.getString(it.labelRes) }.toTypedArray()

        var checked = presets.indexOf(getPreset(activity)).coerceAtLeast(0)

        AlertDialog.Builder(activity)

            .setTitle(R.string.honorific_picker_title)

            .setSingleChoiceItems(labels, checked) { _, which -> checked = which }

            .setPositiveButton(R.string.honorific_picker_ok) { _, _ ->

                val chosen = presets[checked.coerceIn(presets.indices)]

                setPreset(activity, chosen)

                Toast.makeText(
                    activity,
                    activity.getString(R.string.honorific_applied_fmt, activity.getString(chosen.labelRes)),
                    Toast.LENGTH_SHORT,
                ).show()

                onChanged()

            }

            .setNegativeButton(android.R.string.cancel, null)

            .show()

    }



    /** 右上角按钮摘要：称谓：女王 */

    fun settingsButtonLabel(context: Context): String =

        context.getString(R.string.honorific_settings_btn_fmt, displayName(context))



    private val REPLACERS: List<Pair<String, (String) -> String>> = listOf(

        "电子QUEEN" to { h -> "电子${h.uppercase(Locale.getDefault())}" },

        "电子Queen" to { h -> "电子$h" },

        "电子女王" to { h -> "电子$h" },

        "赛博女王" to { h -> "赛博$h" },

        "变态女王" to { h -> "变态$h" },

        "本女王" to { h -> "本$h" },

        "女王驾到" to { h -> "${h}驾到" },

        "女王·" to { h -> "$h·" },

        "女王" to { h -> h },

        "Queen" to { h -> h },

        "QUEEN" to { h -> h.uppercase(Locale.getDefault()) },

    )

}



fun Context.hon(@StringRes resId: Int): String = QueenHonorific.apply(this, getString(resId))



fun Context.hon(@StringRes resId: Int, vararg formatArgs: Any): String =

    QueenHonorific.apply(this, getString(resId, *formatArgs))

