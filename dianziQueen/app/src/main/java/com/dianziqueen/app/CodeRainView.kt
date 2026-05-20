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
 * 每句带景深（远近大小/透明度/速度不同）；车道分配避免句间重叠。
 */
class CodeRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val COLOR_ACTIVATED = 0xFFFF1744.toInt()
        private const val COLOR_GATE = 0xFF00E676.toInt()
        private const val DEPTH_MIN = 0.32f
        private const val DEPTH_MAX = 1f
    }

    private val rainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val metricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val handler = Handler(Looper.getMainLooper())
    private var activatedMode = false
    private var phrases: List<String> = CodeRainPhrases.lines(false)
    private val falling = mutableListOf<FallingBlock>()
    private val frameDelay = 48L
    private val spawnChance = 0.07f

    /** 基准字号（近处句子的尺寸） */
    private var baseTextSizePx = 30f
    private var laneCount = 4
    private var laneCenters = FloatArray(0)
    private var laneBusy = BooleanArray(0)

    private data class FallingBlock(
        var bottomBaselineY: Float,
        val x: Float,
        val glyphs: List<String>,
        /** 0=远 1=近 */
        val depth: Float,
        val lane: Int,
        val textSize: Float,
        val lineStep: Float,
        val speed: Float,
        val ascent: Float,
        val descent: Float,
        val colorArgb: Int,
    ) {
        /** 整列最上方像素 Y（越小越靠上） */
        fun topY(): Float = bottomBaselineY - (glyphs.size - 1).coerceAtLeast(0) * lineStep + ascent

        /** 整列最下方像素 Y */
        fun bottomY(): Float = bottomBaselineY + descent

        fun fullyBelowScreen(viewHeight: Float): Boolean = topY() > viewHeight + 4f
    }

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
        updateBaseTextSize()
    }

    fun setActivatedMode(activated: Boolean) {
        val modeChanged = activatedMode != activated
        activatedMode = activated
        phrases = CodeRainPhrases.lines(activated)
        if (modeChanged) {
            falling.clear()
            laneBusy.fill(false)
        }
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
        if (w <= 0) return
        updateBaseTextSize()
        layoutLanes(w.toFloat())
    }

    private fun updateBaseTextSize() {
        baseTextSizePx = (14f * resources.displayMetrics.scaledDensity).coerceAtLeast(28f)
    }

    private fun layoutLanes(viewWidth: Float) {
        val minColWidth = baseTextSizePx * DEPTH_MIN * 1.15f
        laneCount = (viewWidth / minColWidth).toInt().coerceIn(3, 7)
        val pad = 20f
        val usable = (viewWidth - pad * 2f).coerceAtLeast(1f)
        val step = usable / laneCount
        laneCenters = FloatArray(laneCount) { i -> pad + step * (i + 0.5f) }
        val busy = BooleanArray(laneCount)
        for (b in falling) {
            if (b.lane in busy.indices) busy[b.lane] = true
        }
        laneBusy = busy
    }

    private fun depthScale(depth: Float): Float = 0.56f + depth * 0.44f

    private fun depthAlpha(depth: Float): Int =
        ((0.28f + depth * 0.72f) * 255f).toInt().coerceIn(40, 230)

    private fun depthSpeed(depth: Float): Float = 1.6f + depth * 3.4f

    private fun baseRgb(): Int = if (activatedMode) COLOR_ACTIVATED else COLOR_GATE

    private fun colorForDepth(depth: Float): Int {
        val rgb = baseRgb()
        return Color.argb(depthAlpha(depth), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    private fun metricsForSize(textSize: Float): Paint.FontMetrics {
        metricsPaint.textSize = textSize
        return metricsPaint.fontMetrics
    }

    private fun trySpawn() {
        val h = height.toFloat()
        val w = width.toFloat()
        if (h <= 0f || w <= 0f || phrases.isEmpty()) return

        val freeLanes = laneBusy.indices.filter { !laneBusy[it] }
        if (freeLanes.isEmpty()) return
        if (Random.nextFloat() >= spawnChance) return

        val text = phrases.random()
        val glyphs = codePointStrings(text)
        if (glyphs.isEmpty()) return

        val depth = DEPTH_MIN + Random.nextFloat() * (DEPTH_MAX - DEPTH_MIN)
        val textSize = baseTextSizePx * depthScale(depth)
        val fm = metricsForSize(textSize)
        val lineStep = (fm.descent - fm.ascent) * 1.1f
        val lane = freeLanes.random()
        val x = laneCenters.getOrElse(lane) { w * 0.5f }

        val rise = (glyphs.size - 1).coerceAtLeast(0) * lineStep
        val columnHeight = rise + fm.descent - fm.ascent
        val spawnGap = Random.nextFloat() * h * 0.35f
        val bottomBaselineY = -columnHeight - spawnGap

        falling.add(
            FallingBlock(
                bottomBaselineY = bottomBaselineY,
                x = x,
                glyphs = glyphs,
                depth = depth,
                lane = lane,
                textSize = textSize,
                lineStep = lineStep,
                speed = depthSpeed(depth),
                ascent = fm.ascent,
                descent = fm.descent,
                colorArgb = colorForDepth(depth),
            ),
        )
        laneBusy[lane] = true
    }

    private fun tick() {
        val h = height.toFloat()
        if (h <= 0f) return
        trySpawn()
        val it = falling.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.bottomBaselineY += b.speed
            if (b.fullyBelowScreen(h)) {
                if (b.lane in laneBusy.indices) {
                    laneBusy[b.lane] = false
                }
                it.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val sorted = falling.sortedBy { it.depth }
        for (b in sorted) {
            rainPaint.textSize = b.textSize
            rainPaint.color = b.colorArgb
            var baseline = b.bottomBaselineY
            for (g in b.glyphs) {
                canvas.drawText(g, 0, g.length, b.x, baseline, rainPaint)
                baseline -= b.lineStep
            }
        }
    }

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
