package id.tege.video

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View

/** Face position guide (oval + eye line) inside the visible picture. Preview only; never recorded. */
internal class GuideOverlay(context: Context, private val picture: () -> IntArray?) : View(context) {
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * density; color = Color.argb(170, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(10 * density, 7 * density), 0f)
    }
    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val p = picture() ?: return
        val l = p[0].toFloat(); val t = p[1].toFloat(); val w = p[2].toFloat(); val h = p[3].toFloat()
        if (w <= 0f || h <= 0f) return
        // Eyes on the upper third line; the face oval sits around it, a bit above the centre.
        val eye = t + h / 3f
        canvas.drawLine(l + w * .08f, eye, l + w * .92f, eye, line)
        val faceH = minOf(h * .42f, w * .62f); val faceW = faceH * .74f
        val cx = l + w / 2f; val cy = eye + faceH * .08f
        canvas.drawOval(cx - faceW / 2, cy - faceH * .45f, cx + faceW / 2, cy + faceH * .55f, line)
    }
}

/** Teleprompter: script scrolls upward while recording. Tap to stop/continue. Preview only; never recorded. */
internal class PrompterView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(3 * density, 0f, 1f, Color.BLACK) }
    private val back = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val marker = Paint().apply { color = Color.rgb(230, 190, 127) }
    private var layout: StaticLayout? = null
    var script = ""; set(value) { field = value; layout = null; invalidate() }
    var sizeSp = 28; set(value) { field = value; layout = null; invalidate() }
    /** Scroll speed in dp per second. */
    var speedDp = 40
    var running = false; private set
    private var offset = 0f
    private var lastFrameNs = 0L
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun start() { running = true; lastFrameNs = 0L; postInvalidateOnAnimation() }
    fun stop() { running = false }
    fun rewind() { offset = 0f; invalidate() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layout = null }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) { if (running) stop() else start(); performClick() }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), back)
        val pad = 16 * density
        val textWidth = (width - 2 * pad).toInt()
        if (textWidth <= 0) return
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat(), resources.displayMetrics)
        val l = layout ?: StaticLayout.Builder.obtain(script, 0, script.length, paint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.15f).build().also { layout = it }
        if (running) {
            val now = System.nanoTime()
            if (lastFrameNs != 0L) offset += speedDp * density * (now - lastFrameNs) / 1e9f
            lastFrameNs = now
            // Stop once the last line has passed the reading line.
            if (offset > l.height + height * .2f) { offset = l.height + height * .2f; running = false }
            else postInvalidateOnAnimation()
        }
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(pad, height * .3f - offset)
        l.draw(canvas)
        canvas.restore()
        // Reading line.
        canvas.drawRect(0f, height * .3f - 2 * density, 6 * density, height * .3f + 2 * density, marker)
    }
}

/** Microphone level bar while recording; turns red when the sound clips. */
internal class LevelMeter(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    /** 0..1 */
    var level = 0f; set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var clipping = false; set(value) { field = value; invalidate() }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, bg)
        bar.color = when { clipping -> Color.rgb(235, 70, 60); level > .85f -> Color.rgb(240, 190, 60); else -> Color.rgb(47, 191, 113) }
        val inset = 1.5f * density
        canvas.drawRoundRect(inset, inset, inset + (width - 2 * inset) * level, height - inset, r, r, bar)
    }
}

/** Builds the upright text layer drawn into the video: title + date (top left) and the lower third (bottom left). */
internal object OverlayArt {
    fun draw(width: Int, height: Int, topLine: String?, lowerTitle: String?, lowerSub: String?): Bitmap? {
        if (topLine.isNullOrBlank() && lowerTitle.isNullOrBlank()) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val unit = minOf(width, height).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(unit * .006f, 0f, unit * .002f, Color.argb(200, 0, 0, 0)) }
        if (!topLine.isNullOrBlank()) {
            paint.textSize = unit * .045f; paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            c.drawText(fit(topLine, paint, width * .9f), width * .04f, height * .04f + paint.textSize, paint)
        }
        if (!lowerTitle.isNullOrBlank()) {
            val titleSize = unit * .06f; val subSize = unit * .04f
            val hasSub = !lowerSub.isNullOrBlank()
            val boxH = titleSize * 1.5f + if (hasSub) subSize * 1.4f else 0f
            val left = width * .04f; val bottom = height * .93f; val top = bottom - boxH
            paint.textSize = titleSize; paint.typeface = Typeface.DEFAULT_BOLD
            val title = fit(lowerTitle, paint, width * .8f)
            var boxW = paint.measureText(title)
            var sub = ""
            if (hasSub) { paint.textSize = subSize; sub = fit(lowerSub!!, paint, width * .8f); boxW = maxOf(boxW, paint.measureText(sub)) }
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 16, 19, 21) }
            c.drawRoundRect(left, top, left + boxW + unit * .07f, bottom, unit * .012f, unit * .012f, box)
            c.drawRect(left, top, left + unit * .012f, bottom, Paint().apply { color = Color.rgb(230, 190, 127) })
            paint.clearShadowLayer()
            paint.textSize = titleSize; paint.typeface = Typeface.DEFAULT_BOLD; paint.color = Color.WHITE
            c.drawText(title, left + unit * .035f, top + titleSize * 1.15f, paint)
            if (hasSub) {
                paint.textSize = subSize; paint.typeface = Typeface.DEFAULT; paint.color = Color.rgb(230, 190, 127)
                c.drawText(sub, left + unit * .035f, top + titleSize * 1.3f + subSize * 1.05f, paint)
            }
        }
        return bitmap
    }
    private fun fit(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > max) end--
        return text.substring(0, end) + "…"
    }
}
