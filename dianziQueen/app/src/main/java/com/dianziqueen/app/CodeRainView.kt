package com.dianziqueen.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * 主界面背景：整句文案纵向排列（首字在底、向上读），整列自上而下飘落。
 * 激活前 / 激活后两套句子由 [CodeRainPhrases] 提供，[setActivatedMode] 切换。
 */
class CodeRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val RAIN_COLOR_ACTIVATED = 0xCCFF1744.toInt()
        private val RAIN_COLOR_GATE = 0xCC00E676.toInt()
    }

    private val rainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        color = RAIN_COLOR_GATE
        textAlign = Paint.Align.CENTER
    }

    private val handler = Handler(Looper.getMainLooper())
    private var activatedMode = false
    private var phrases: List<String> = CodeRainPhrases.lines(false)
    private val falling = mutableListOf<FallingBlock>()
    private val frameDelay = 48L
    private val maxStreams = 10
    private val spawnChance = 0.065f
    private var lineStep = 32f
    private var colHalfWidth = 18f

    private data class FallingBlock(
        /** 句首（第一个字）的 baseline Y，位于整列最下方，向上堆叠后续字 */
        var bottomBaselineY: Float,
        val x: Float,
        val glyphs: List<String>,
        val speed: Float
    )

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            tick()
            invalidate()
            handler.postDelayed(this, frameDelay)
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setActivatedMode(activated: Boolean) {
        val modeChanged = activatedMode != activated
        activatedMode = activated
        phrases = CodeRainPhrases.lines(activated)
        rainPaint.color = if (activated) RAIN_COLOR_ACTIVATED else RAIN_COLOR_GATE
        if (modeChanged) falling.clear()
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
        val fm = rainPaint.fontMetrics
        lineStep = (fm.descent - fm.ascent) * 1.12f
        colHalfWidth = (rainPaint.textSize * 0.62f).coerceAtLeast(12f)
    }

    private fun tick() {
        val h = height.toFloat()
        val w = width.toFloat()
        if (h <= 0f || w <= 0f) return
        if (phrases.isNotEmpty() && falling.size < maxStreams && Random.nextFloat() < spawnChance) {
            val text = phrases.random()
            val glyphs = codePointStrings(text)
            if (glyphs.isEmpty()) return
            val pad = 16f
            val span = (w - pad * 2f - colHalfWidth * 2f).coerceAtLeast(1f)
            val x = pad + colHalfWidth + Random.nextFloat() * span
            val speed = 2.2f + Random.nextFloat() * 3.2f
            val fm = rainPaint.fontMetrics
            val rise = (glyphs.size - 1).coerceAtLeast(0) * lineStep
            falling.add(
                FallingBlock(
                    bottomBaselineY = -rise - fm.descent - Random.nextFloat() * 220f,
                    x = x,
                    glyphs = glyphs,
                    speed = speed
                )
            )
        }
        val it = falling.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.bottomBaselineY += b.speed
            if (b.bottomBaselineY > h + 120f) it.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        for (b in falling) {
            var baseline = b.bottomBaselineY
            for (j in b.glyphs.indices) {
                val g = b.glyphs[j]
                canvas.drawText(g, 0, g.length, b.x, baseline, rainPaint)
                baseline -= lineStep
            }
        }
    }

    /** 按 Unicode 码点切分，避免增补汉字被拆坏 */
    private fun codePointStrings(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<String>(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            out.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return out
    }
}
