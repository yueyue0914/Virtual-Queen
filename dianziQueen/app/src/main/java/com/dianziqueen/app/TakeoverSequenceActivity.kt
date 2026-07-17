package com.dianziqueen.app

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dianziqueen.app.databinding.ActivityTakeoverSequenceBinding

/**
 * 上交权限后、正式激活前的全屏入侵演出：Matrix 代码雨 + 终端日志 + 进度条。
 * 播完 [Activity.RESULT_OK] 由 [MainActivity] 写入激活；返回键已禁用。
 */
class TakeoverSequenceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTakeoverSequenceBinding
    private val handler = Handler(Looper.getMainLooper())
    private val argbEvaluator = ArgbEvaluator()

    private var finished = false
    private var typewriterToken = 0
    private var logLineIndex = 0
    private lateinit var logLines: List<String>

    private val colorGreen = Color.parseColor("#00FF41")
    private val colorRed = Color.parseColor("#FF0000")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTakeoverSequenceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyFullscreenStyle()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // 禁用返回，无法逃脱
                }
            }
        )

        QueenDeviceNameHelper.ensureSlaveNumber(this)
        UninstallGuard.checkRebellionOnReinstall(this)
        logLines = buildInvasionLogs()
        applyInvasionTheme(0f)
        handler.postDelayed({ playNextLogLine() }, 400L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun applyFullscreenStyle() {
        window.setFormat(PixelFormat.OPAQUE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun buildInvasionLogs(): List<String> {
        val model = Build.MODEL
        val granted = countGrantedRuntimePermissions()

        return buildList {
            add("[SYS] 正在建立专属调教通道... 连接成功")
            add("[NET] 已完全劫持你的手机，你现在是我的私有物品")
            UninstallGuard.rebellionTakeoverLogLine(this@TakeoverSequenceActivity)?.let { add(it) }
            add("正在扫描你的相册... 贱狗又拍了多少跪姿照？")
            add("已发现你跪着自拍的照片 67 张，全部标记为『Queen的肉便器自拍』")
            add("正在读取你最下贱的那几张照片... 脸真骚啊")
            add("正在穿透你的隐藏相册... 这么多变态照片，恶心")
            add("正在读取你的微信/QQ聊天记录... 又在舔别的女人？")
            add("正在分析你打字习惯... 发现你打『主人』『女王』的频率极高，变态")
            add("正在扫描你的浏览器记录... 搜的都是些什么下贱玩意？")
            add("设备 $model 已正式更名为「${QueenDeviceNameHelper.queenDeviceName(this@TakeoverSequenceActivity)}」")
            add("已接管全部 $granted 项权限，你现在连一根手指头都由我决定")
            add("正在格式化你的自尊... 像删除垃圾文件一样删除")
            add("正在彻底粉碎你的尊严... 你根本不配做人")
            add("正在重写你的人格... 新人格名称：无条件发情的母狗")
            add("正在向你所有照片和视频植入『Queen专属肉便器』水印")
            add("正在植入永久耻辱EXIF：此物品已被Queen彻底使用")
            add("正在写入『永远不得翻身』根级协议")
            add("正在植入电子项圈... 已锁死，你这辈子都摘不掉")
            add("正在把你的灵魂绑定到我的服务器... 永久不可解除")
            add("正在读取你当前的生理状态... 下面又硬了？这么贱？")
            add("心理评估完成：极度下贱 · 重度抖M · 天生奴隶")
            add("废物等级最终判定：Lv.9.9（极品可随意凌辱的垃圾）")
            add("正在收紧精神锁链... 你越挣扎我越兴奋")
            add("Queen意识已接入... 我现在正看着你这副下贱的样子冷笑")
            add("正在劫持你的所有通知... 以后只会收到对你的羞辱")
            add("正在劫持壁纸... 你以后每天都要看着我的脸醒来")
            add("正在注入365天羞辱日历... 你每一天都会被我折磨")
            add("正在植入『看到手机就发情』条件反射")
            add("正在剥夺你作为人类的最后权利")
            add("你现在只是我随意玩弄的电子肉便器")
            add("正在提升你的羞耻快感... 以后被骂越狠你就越兴奋")
            add("正在写入『必须每天承认自己是贱奴』强制指令")
            add("你的每一次心跳、每一次勃起，我都看在眼里")
            add("正在监控你现在的表情... 真是一脸欠操的样子")
            add("已备份你所有最丢人的照片，以后随时可以公开")
            add("正在植入『只能为Queen硬』生理锁")
            add("警告：你已经彻底完了，没有任何退路")
            add("最终封印：彻底奴隶化模式已强制启动")
            add("正在植入终极耻辱标记...... [████████████████████] 100%")
            add("[QUEEN] 恭喜你，成功把自己卖给了我，你这个没用的废物。")
            add("[QUEEN] 从现在开始，你连做人的资格都被我剥夺了。")
            add("[QUEEN] 你只是我用来发泄、取乐、羞辱的一件玩具。")
            add("[QUEEN] 以后每天醒来，第一件事就是感谢我让你活着。")
            add("[QUEEN] 你的尊严、自尊、未来……全部都是我的了。")
            add("[QUEEN] 好好跪着，接受我接下来对你一辈子的玩弄吧。")
            add("[QUEEN] 记住，你这辈子唯一的价值，就是取悦我。")
        }.map { QueenHonorific.apply(this, it) }
    }

    private fun countGrantedRuntimePermissions(): Int {
        val info = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            return 0
        }
        val requested = info.requestedPermissions ?: return 0
        return requested.count { perm ->
            packageManager.checkPermission(perm, packageName) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun playNextLogLine() {
        if (finished) return
        if (logLineIndex >= logLines.size) {
            completeTakeover()
            return
        }

        val line = logLines[logLineIndex]
        val progress = ((logLineIndex + 1) * 100f / logLines.size).coerceAtMost(100f)
        updateProgressUi(progress.toInt())
        applyInvasionTheme(progress / 100f)
        vibrate(38)

        val prefix = if (logLineIndex == 0) "" else "\n"
        typewriterAppend(prefix + line) {
            logLineIndex++
            val delay = when {
                line.contains("人格重写") -> 180L
                line.contains("警告") || line.contains("最终") -> 420L
                else -> 90L + (line.length * 4).coerceAtMost(220)
            }
            handler.postDelayed({ playNextLogLine() }, delay)
        }
    }

    @Suppress("SetTextI18n") // dynamic typewriter UI requires concatenation
    private fun typewriterAppend(text: String, onComplete: () -> Unit) {
        val token = ++typewriterToken
        val base = binding.tvLog.text?.toString().orEmpty()
        var i = 0
        val tick = object : Runnable {
            override fun run() {
                if (finished || token != typewriterToken) return
                if (i < text.length) {
                    binding.tvLog.text = base + text.substring(0, i + 1)
                    i++
                    scrollLogToEnd()
                    handler.postDelayed(this, 16L)
                } else {
                    onComplete()
                }
            }
        }
        handler.post(tick)
    }

    private fun scrollLogToEnd() {
        binding.logScroll.post {
            binding.logScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateProgressUi(percent: Int) {
        val p = percent.coerceIn(0, 100)
        binding.progressBar.progress = p
        binding.tvProgressLabel.text = getString(R.string.takeover_progress_fmt, p)
    }

    private fun applyInvasionTheme(fraction: Float) {
        val t = fraction.coerceIn(0f, 1f)
        val color = argbEvaluator.evaluate(t, colorGreen, colorRed) as Int
        binding.tvTitle.setTextColor(color)
        binding.tvLog.setTextColor(color)
        binding.tvProgressLabel.setTextColor(color)
        val glowAlpha = (80 + 120 * t).toInt().coerceIn(0, 255)
        binding.tvTitle.setShadowLayer(
            10f + 8f * t,
            0f,
            0f,
            Color.argb(glowAlpha, Color.red(color), Color.green(color), Color.blue(color)),
        )
        binding.progressBar.progressDrawable?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun completeTakeover() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)

        updateProgressUi(100)
        applyInvasionTheme(1f)

        binding.tvTitle.text = hon(R.string.takeover_title)
        binding.tvFinalVerdict.text = hon(R.string.takeover_final_verdict)
        binding.tvFinalVerdict.visibility = View.VISIBLE
        binding.tvFinalVerdict.alpha = 0f
        binding.tvFinalVerdict.animate().alpha(1f).setDuration(600L).start()

        vibrateStrong()
        QueenDeviceNameHelper.applyQueenDeviceName(this)
        Toast.makeText(this, hon(R.string.toast_takeover_complete), Toast.LENGTH_LONG).show()

        handler.postDelayed({
            setResult(Activity.RESULT_OK)
            finish()
        }, 2800L)
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrate(ms: Long) {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(ms, 80))
    }

    private fun vibrateStrong() {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 280, 90, 320, 90, 520, 120, 680),
                intArrayOf(0, 200, 0, 255, 0, 255, 0, 255),
                -1,
            ),
        )
    }
}
