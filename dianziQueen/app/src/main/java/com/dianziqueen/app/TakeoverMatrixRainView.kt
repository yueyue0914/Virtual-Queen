package com.dianziqueen.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * 接管页背景：高速 Matrix 字符雨 + 故障闪烁。
 * 使用软件绘制层，避免模拟器/部分设备 EGL_SWAP_BEHAVIOR 告警。
 */
class TakeoverMatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val glyphPool = (
        "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉ" +
            "0123456789ABCDEF" +
            "QUEEN贱奴玩具肉便器"
        ).toList()

    private data class Column(
        val x: Float,
        var headY: Float,
        val speed: Float,
        val length: Int,
        val chars: CharArray,
    )

    private val columns = mutableListOf<Column>()
    private var columnCount = 0
    private var fontHeight = 32f
    private var glitchUntilFrame = 0
    private var frame = 0
    private var glitchActive = false

    private val frameDelayMs = 32L

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            tick()
            invalidate()
            handler.postDelayed(this, frameDelayMs)
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(updateRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(updateRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val fm = paint.fontMetrics
        fontHeight = (fm.descent - fm.ascent) * 1.05f
        paint.textSize = (w / 28f).coerceIn(22f, 30f)
        columnCount = (w / paint.textSize).toInt().coerceIn(12, 36)
        columns.clear()
        val colW = w.toFloat() / columnCount
        repeat(columnCount) { i ->
            columns.add(spawnColumn(colW * i + colW * 0.5f, h.toFloat()))
        }
    }

    private fun spawnColumn(x: Float, viewH: Float): Column {
        val len = Random.nextInt(8, 22)
        val chars = CharArray(len) { glyphPool.random() }
        return Column(
            x = x,
            headY = Random.nextFloat() * viewH,
            speed = 14f + Random.nextFloat() * 22f,
            length = len,
            chars = chars,
        )
    }

    private fun tick() {
        frame++
        val h = height.toFloat()
        if (h <= 0f) return
        glitchActive = frame <= glitchUntilFrame
        if (Random.nextFloat() < 0.04f) {
            glitchUntilFrame = frame + Random.nextInt(2, 6)
        }
        columns.forEach { col ->
            col.headY += col.speed
            if (col.headY - col.length * fontHeight > h) {
                col.headY = -Random.nextFloat() * h * 0.3f
                repeat(col.chars.size) { i ->
                    col.chars[i] = glyphPool.random()
                }
            } else if (Random.nextFloat() < 0.18f) {
                col.chars[Random.nextInt(col.chars.size)] = glyphPool.random()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val glitch = glitchActive
        columns.forEach { col ->
            var y = col.headY
            for (i in col.chars.indices) {
                val fade = 1f - i.toFloat() / col.length.coerceAtLeast(1)
                val alpha = (fade * 0.42f * 255).toInt().coerceIn(18, 110)
                paint.color = when {
                    glitch && i == 0 -> Color.argb(140, 255, 40, 40)
                    glitch -> Color.argb(90, 220, 220, 220)
                    else -> Color.argb(alpha, 0, 255, 65)
                }
                canvas.drawText(col.chars[i].toString(), col.x, y, paint)
                y -= fontHeight
            }
        }
    }
}
