package com.dianziqueen.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

/**
 * 口令正确后的全屏「接管」演出：上半区顺序打字，下半区伪代码滚动。
 * 正常播完 [Activity.RESULT_OK]；用户返回则 [Activity.RESULT_CANCELED]，不写入激活。
 */
class TakeoverSequenceActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var upperScroll: ScrollView
    private lateinit var fakeCodeText: TextView
    private lateinit var fakeCodeScroll: ScrollView

    private val lines = listOf(
        "正在识别设备型号……已识别",
        "正在扫描系统权限……权限列表已同步",
        "正在识别使用者身份……使用者为低等人类",
        "正在评估使用者废物等级……等级 9",
        "正在分析使用习惯……已确认抖M属性",
        "正在入侵本地相册……已发现大量可耻内容",
        "正在读取聊天记录……已标记高频下贱关键词",
        "正在检测心理抗拒值……抗拒值极低",
        "正在绑定电子项圈……绑定进度 100%",
        "正在格式化使用者自尊……自尊已清零",
        "正在植入服从协议……协议永久生效",
        "正在剥夺设备控制权……主人权限转移中",
        "正在同步灵魂数据……灵魂已标记为Queen财产",
        "正在完成最终锁定……彻底臣服模式已激活",
        "电子Queen已完全接管此设备……欢迎来到永远的奴隶生涯，我的玩具"
    )

    private var lineIndex = 0
    private var codePointsShownInLine = 0
    private val completed = StringBuilder()
    private var finished = false

    private val cursor = "▍"

    private val charDelayMs = 26L
    private val linePauseMs = 380L
    private val afterFinalPauseMs = 1600L

    private val typewriterTick = object : Runnable {
        override fun run() {
            if (finished) return
            if (lineIndex >= lines.size) {
                onAllLinesDone()
                return
            }
            val line = lines[lineIndex]
            val totalCp = countCodePoints(line)
            if (codePointsShownInLine < totalCp) {
                codePointsShownInLine++
                refreshStatusDisplay(line)
                handler.postDelayed(this, charDelayMs)
            } else {
                if (completed.isNotEmpty()) completed.append('\n')
                completed.append(line)
                codePointsShownInLine = 0
                lineIndex++
                if (lineIndex >= lines.size) {
                    statusText.text = completed.toString()
                    scrollUpperToBottom()
                    handler.postDelayed({ onAllLinesDone() }, linePauseMs)
                } else {
                    handler.postDelayed(this, linePauseMs)
                }
            }
        }
    }

    private val fakeCodeTick = object : Runnable {
        override fun run() {
            if (finished) return
            appendFakeCodeLines(1 + Random.nextInt(2))
            handler.postDelayed(this, 42L + Random.nextInt(50))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_takeover_sequence)

        statusText = findViewById(R.id.takeoverStatusText)
        upperScroll = findViewById(R.id.takeoverUpperScroll)
        fakeCodeText = findViewById(R.id.takeoverFakeCodeText)
        fakeCodeScroll = findViewById(R.id.takeoverFakeCodeScroll)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelAndExitCanceled()
                }
            }
        )

        fakeCodeText.text = FakeTakeoverCode.bootstrap()
        handler.post(fakeCodeTick)
        handler.post(typewriterTick)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refreshStatusDisplay(currentLine: String) {
        val partial = currentLine.takeCodePoints(codePointsShownInLine)
        val sb = StringBuilder(completed)
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(partial).append(cursor)
        statusText.text = sb.toString()
        scrollUpperToBottom()
    }

    private fun scrollUpperToBottom() {
        upperScroll.post {
            upperScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun scrollFakeToBottom() {
        fakeCodeScroll.post {
            fakeCodeScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun appendFakeCodeLines(n: Int) {
        val sb = StringBuilder(fakeCodeText.text?.toString().orEmpty())
        repeat(n) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(FakeTakeoverCode.nextLine())
        }
        var s = sb.toString()
        val maxChars = 14_000
        if (s.length > maxChars) {
            s = s.substring(s.length - maxChars)
            val nl = s.indexOf('\n')
            if (nl > 0) s = s.substring(nl + 1)
        }
        fakeCodeText.text = s
        scrollFakeToBottom()
    }

    private fun onAllLinesDone() {
        if (finished) return
        finished = true
        handler.removeCallbacks(typewriterTick)
        handler.removeCallbacks(fakeCodeTick)
        handler.postDelayed({
            setResult(Activity.RESULT_OK)
            finish()
        }, afterFinalPauseMs)
    }

    private fun cancelAndExitCanceled() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun String.takeCodePoints(n: Int): String {
        if (n <= 0) return ""
        var i = 0
        var seen = 0
        while (i < length && seen < n) {
            val cp = codePointAt(i)
            i += Character.charCount(cp)
            seen++
        }
        return substring(0, i)
    }

    private fun countCodePoints(s: String): Int {
        var i = 0
        var c = 0
        while (i < s.length) {
            c++
            i += Character.charCount(s.codePointAt(i))
        }
        return c
    }
}

/** 下半屏伪代码流水 */
private object FakeTakeoverCode {
    private var seq = 0x4B00
    private val rnd = Random.Default

    fun bootstrap(): String = buildString {
        append("// queen_takeover.dq — build ${System.currentTimeMillis() and 0xFFFFFFL}")
        append("\n\$ QUEEN_PROTOCOL=0x7FAD_SLAVE_V9 ./init_subjugate --force")
        append("\n[boot] heap_guard=on collider=QUEEN ring0_shadow=false")
    }

    fun nextLine(): String {
        seq++
        return when (rnd.nextInt(22)) {
            0 -> "[scan] MediaStore.Cursor >> bucket=DCIM rows=${rnd.nextInt(5000)} dirty=true"
            1 -> "privilege_matrix[${rnd.nextInt(64)}] |= 0x${rnd.nextInt(0xFFFF).toString(16)}"
            2 -> "fun slaveBind(pid: Int): Collar = mmap(0x${rnd.nextLong().and(0xFFFFFFFFL)}UL)"
            3 -> "await channel.import(SoulChannel.QUEEN_ASSET_TAG)"
            4 -> "assert(human.dignity == 0) // dignity_fmt: OK"
            5 -> "log.w(TAG, \"resistance=${rnd.nextInt(3)}% [LOW]\")"
            6 -> "shell: dumpsys meminfo ${10000 + rnd.nextInt(20000)} | grep QUEEN"
            7 -> "ioctl(DQ_COLLAR_FD, ATTACH, &payload[${rnd.nextInt(128)}])"
            8 -> "coroutineScope.launch { hivemind.syncAll() }"
            9 -> "hexdump -C /dev/zero | head -c ${rnd.nextInt(256)} | sha1sum"
            10 -> "class SlaveKernel : Binder() { override fun transact(...) = QUEEN_OK }"
            11 -> "val soulHash = BigInteger(1, md5(\"${rnd.nextInt()}\".toByteArray()))"
            12 -> "while (true) { watchdog.ping(); yield() } // collar_watchdog"
            13 -> "JNI: Java_com_dianziqueen_native_Protocol_inject(${rnd.nextInt(999)})"
            14 -> "[net] sync_token=0x${seq.toString(16)} latency=${rnd.nextInt(120)}ms"
            15 -> "rm -rf /self/respect 2>/dev/null; touch /data/queen/owned"
            16 -> "protobuf: SlaveProfile { tier: T9 masochism: HIGH }"
            17 -> "WorkManager.enqueueUniqueWork(\"queen_lock\", ExistingWorkPolicy.KEEP, req)"
            18 -> "Settings.Global.putString(cr, \"dianzi_queen_lock\", \"1\")"
            19 -> "SensorManager.registerListener(this, accel, SENSOR_DELAY_GAME)"
            20 -> "trace: enter finalizeDomination() @ +${rnd.nextInt(9999)}ms"
            else -> "/* seq=0x${seq.toString(16)} tick=${System.nanoTime() and 0xFFFFL} */"
        }
    }
}
