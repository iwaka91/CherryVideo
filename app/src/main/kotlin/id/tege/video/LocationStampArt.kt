package id.tege.video

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import id.tege.video.core.StampContent
import id.tege.video.core.StampTemplate

/**
 * Draws the location stamp (eight templates) into the upright text layer that goes into video and photos.
 * Sizes follow the shorter side of the layer, so every template looks the same in portrait and landscape.
 * [ceilY] and [floorY] keep the stamp clear of the title/date line and the lower third.
 */
internal object StampArt {
    private class Geo(val c: Canvas, val w: Float, val h: Float, val ceil: Float, val floor: Float) {
        val u = minOf(w, h)
        val m = u * .04f
    }

    fun draw(canvas: Canvas, width: Int, height: Int, template: StampTemplate, content: StampContent, ceilY: Float, floorY: Float) {
        if (content.isEmpty) return
        val g = Geo(canvas, width.toFloat(), height.toFloat(), ceilY, floorY)
        when (template) {
            StampTemplate.POLOS -> polos(g, content)
            StampTemplate.KARTU -> kartu(g, content)
            StampTemplate.BERITA -> berita(g, content)
            StampTemplate.CATATAN -> catatan(g, content)
            StampTemplate.LAPORAN -> laporan(g, content)
            StampTemplate.JUDUL -> judul(g, content)
            StampTemplate.POSTER -> poster(g, content)
            StampTemplate.STEMPEL -> stempel(g, content)
        }
    }

    private fun paint(size: Float, color: Int, face: Typeface, shadow: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = face
        if (shadow > 0f) setShadowLayer(shadow, 0f, shadow * .4f, Color.argb(190, 0, 0, 0))
    }
    private fun face(name: String, style: Int = Typeface.NORMAL): Typeface = Typeface.create(name, style)
    private fun fit(text: String, p: Paint, max: Float): String {
        if (max <= 0f || p.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && p.measureText(text.substring(0, end) + "…") > max) end--
        return text.substring(0, end) + "…"
    }
    private fun widest(lines: List<String>, p: Paint) = lines.maxOfOrNull { p.measureText(it) } ?: 0f
    private fun all(c: StampContent): List<String> = listOfNotNull(c.place, c.coords, c.dateTime, c.note)

    // 1. Polos: a few small lines at the bottom left, thin letters.
    private fun polos(g: Geo, c: StampContent) {
        val p = paint(g.u * .034f, Color.WHITE, face("sans-serif-light"), g.u * .006f)
        var y = g.floor - p.descent()
        for (line in all(c).asReversed()) { g.c.drawText(fit(line, p, g.w - 2 * g.m), g.m, y, p); y -= p.textSize * 1.35f }
    }

    // 2. Kartu: translucent rounded card at the bottom left, firm letters.
    private fun kartu(g: Geo, c: StampContent) {
        val lines = all(c)
        val title = paint(g.u * .042f, Color.WHITE, face("sans-serif-medium", Typeface.BOLD))
        val body = paint(g.u * .032f, Color.WHITE, face("sans-serif"))
        val max = g.w * .82f
        val shown = lines.mapIndexed { i, s -> fit(s, if (i == 0) title else body, max) }
        val pad = g.u * .028f
        val boxW = (shown.mapIndexed { i, s -> (if (i == 0) title else body).measureText(s) }.maxOrNull() ?: 0f) + 2 * pad
        val boxH = 2 * pad + title.textSize * 1.15f + (shown.size - 1) * body.textSize * 1.4f
        val left = g.m; val bottom = g.floor; val top = bottom - boxH
        g.c.drawRoundRect(RectF(left, top, left + boxW, bottom), g.u * .022f, g.u * .022f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) })
        var y = top + pad + title.textSize
        shown.forEachIndexed { i, s ->
            g.c.drawText(s, left + pad, y, if (i == 0) title else body)
            y += if (i == 0) body.textSize * 1.5f else body.textSize * 1.4f
        }
    }

    // 3. Berita: full width news band at the bottom, condensed bold letters.
    private fun berita(g: Geo, c: StampContent) {
        val left = c.place?.uppercase() ?: c.coords ?: c.date ?: c.time ?: return
        val right = if (c.place == null && c.coords == null) c.time.takeIf { c.date != null } else c.time
        val strip = listOfNotNull(c.date.takeIf { c.date != left }, c.coords.takeIf { c.coords != left }, c.note).joinToString(" · ")
        val condensed = face("sans-serif-condensed", Typeface.BOLD)
        val big = paint(g.u * .05f, Color.WHITE, condensed)
        val small = paint(g.u * .03f, Color.WHITE, face("sans-serif-condensed"))
        small.letterSpacing = .04f
        val stripH = if (strip.isEmpty()) 0f else g.u * .055f
        val bandH = g.u * .095f
        val bottom = g.floor + g.m * .5f
        val bandBottom = bottom - stripH
        g.c.drawRect(0f, bandBottom - bandH, g.w, bandBottom, Paint().apply { color = Color.rgb(200, 16, 46) })
        val rightText = right?.let { fit(it, big, g.w * .3f) }
        val rightW = rightText?.let { big.measureText(it) } ?: 0f
        g.c.drawText(fit(left, big, g.w - 3 * g.m - rightW), g.m, bandBottom - bandH / 2 + big.textSize * .35f, big)
        if (rightText != null) { big.textAlign = Paint.Align.RIGHT; g.c.drawText(rightText, g.w - g.m, bandBottom - bandH / 2 + big.textSize * .35f, big) }
        if (stripH > 0f) {
            g.c.drawRect(0f, bandBottom, g.w, bottom, Paint().apply { color = Color.rgb(17, 17, 17) })
            g.c.drawText(fit(strip, small, g.w - 2 * g.m), g.m, bottom - (stripH - small.textSize) / 2f - small.descent(), small)
        }
    }

    // 4. Catatan: tilted sticky note at the top right, handwriting-style letters.
    private fun catatan(g: Geo, c: StampContent) {
        val lines = all(c)
        val big = paint(g.u * .056f, Color.rgb(58, 47, 0), face("cursive", Typeface.BOLD))
        val small = paint(g.u * .044f, Color.rgb(58, 47, 0), face("cursive", Typeface.BOLD))
        val max = g.w * .5f
        val shown = lines.mapIndexed { i, s -> fit(s, if (i == 0) big else small, max) }
        val pad = g.u * .026f
        val boxW = (shown.mapIndexed { i, s -> (if (i == 0) big else small).measureText(s) }.maxOrNull() ?: 0f) + 2 * pad
        val boxH = 2 * pad + big.textSize * 1.1f + (shown.size - 1) * small.textSize * 1.15f
        val right = g.w - g.m; val top = g.ceil
        val rect = RectF(right - boxW, top, right, top + boxH)
        g.c.save()
        g.c.rotate(-3f, rect.centerX(), rect.centerY())
        g.c.drawRect(RectF(rect.left + g.u * .005f, rect.top + g.u * .006f, rect.right + g.u * .005f, rect.bottom + g.u * .006f), Paint().apply { color = Color.argb(70, 0, 0, 0) })
        g.c.drawRect(rect, Paint().apply { color = Color.rgb(255, 243, 176) })
        var y = rect.top + pad + big.textSize * .9f
        shown.forEachIndexed { i, s ->
            g.c.drawText(s, rect.left + pad, y, if (i == 0) big else small)
            y += if (i == 0) small.textSize * 1.2f else small.textSize * 1.15f
        }
        g.c.restore()
    }

    // 5. Laporan: neat block with labels, monospace letters like an official form.
    private fun laporan(g: Geo, c: StampContent) {
        val rows = listOfNotNull(
            c.place?.let { "LOKASI" to (it + (c.note?.let { n -> " ($n)" } ?: "")) },
            c.coords?.let { "KOORD." to it },
            c.dateTime?.let { "WAKTU" to it },
        ).ifEmpty { listOfNotNull(c.note?.let { "CATATAN" to it }) }
        if (rows.isEmpty()) return
        val label = paint(g.u * .03f, Color.rgb(26, 26, 26), face("monospace", Typeface.BOLD))
        val value = paint(g.u * .03f, Color.rgb(26, 26, 26), face("monospace"))
        val pad = g.u * .022f
        val labelW = widest(rows.map { it.first }, label) + g.u * .02f
        val max = g.w - 2 * g.m - 2 * pad - labelW
        val shown = rows.map { it.first to fit(it.second, value, max) }
        val boxW = labelW + widest(shown.map { it.second }, value) + 2 * pad
        val lineH = value.textSize * 1.55f
        val boxH = 2 * pad + shown.size * lineH - (lineH - value.textSize)
        val left = g.m; val bottom = g.floor; val top = bottom - boxH
        g.c.drawRoundRect(RectF(left, top, left + boxW, bottom), g.u * .008f, g.u * .008f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 255, 255, 255) })
        var y = top + pad + value.textSize * .85f
        shown.forEach { (k, v) ->
            g.c.drawText(k, left + pad, y, label)
            g.c.drawText(v, left + pad + labelW, y, value)
            y += lineH
        }
    }

    // 6. Judul Tempat: big place name with a pin at the top left, serif letters.
    private fun judul(g: Geo, c: StampContent) {
        val head = c.headline ?: c.coords ?: c.dateTime ?: return
        val subs = listOfNotNull(c.subline, c.coords.takeIf { it != head }, c.dateTime.takeIf { it != head }, c.note)
        val big = paint(g.u * .07f, Color.WHITE, face("serif", Typeface.BOLD), g.u * .007f)
        val small = paint(g.u * .03f, Color.WHITE, face("sans-serif"), g.u * .005f)
        val r = g.u * .017f
        val textLeft = g.m + 2 * r + g.u * .015f
        val max = g.w - textLeft - g.m
        val top = g.ceil
        // Pin: round head, pointed tail, dark dot.
        val cx = g.m + r; val cy = top + big.textSize * .55f
        val pin = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * .8f, cy + r * .6f); lineTo(cx, cy + r * 2.3f); lineTo(cx + r * .8f, cy + r * .6f); close()
        }
        g.c.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(g.u * .005f, 0f, g.u * .002f, Color.argb(190, 0, 0, 0)) })
        g.c.drawCircle(cx, cy, r * .4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 19, 21) })
        var y = top + big.textSize
        g.c.drawText(fit(head, big, max), textLeft, y, big)
        for (s in subs) { y += small.textSize * 1.4f; g.c.drawText(fit(s, small, max), textLeft, y, small) }
    }

    // 7. Poster: big centered words at the bottom, condensed bold letters.
    private fun poster(g: Geo, c: StampContent) {
        val raw = c.headline ?: c.coords ?: c.dateTime ?: return
        val head = raw.uppercase()
        val sub = listOfNotNull(c.subline?.uppercase(), c.dateTime.takeIf { it != raw }, c.note).joinToString("  —  ")
        val big = paint(g.u * .11f, Color.WHITE, face("sans-serif-condensed", Typeface.BOLD), g.u * .008f).apply { letterSpacing = .06f; textAlign = Paint.Align.CENTER }
        while (big.measureText(head) > g.w * .92f && big.textSize > g.u * .03f) big.textSize *= .93f
        val small = paint(g.u * .034f, Color.WHITE, face("sans-serif-condensed"), g.u * .006f).apply { letterSpacing = .1f; textAlign = Paint.Align.CENTER }
        var y = g.floor - small.descent()
        if (sub.isNotEmpty()) { g.c.drawText(fit(sub, small, g.w * .92f), g.w / 2f, y, small); y -= small.textSize * 1.35f }
        g.c.drawText(head, g.w / 2f, y - big.descent(), big)
    }

    // 8. Stempel: tilted stamp at the bottom right, slab-style letters in amber.
    private fun stempel(g: Geo, c: StampContent) {
        val amber = Color.rgb(245, 184, 61)
        val raw = c.headline ?: c.coords ?: c.dateTime ?: return
        val head = raw.uppercase()
        val rest = listOfNotNull(c.subline?.uppercase(), c.coords.takeIf { it != raw }, c.dateTime.takeIf { it != raw }, c.note?.uppercase())
        val big = paint(g.u * .04f, amber, face("serif", Typeface.BOLD))
        val small = paint(g.u * .027f, amber, face("serif", Typeface.BOLD)).apply { letterSpacing = .05f }
        val max = g.w * .6f
        val shownHead = fit(head, big, max)
        val shown = rest.map { fit(it, small, max) }
        val pad = g.u * .022f
        val boxW = maxOf(big.measureText(shownHead), widest(shown, small)) + 2 * pad
        val boxH = 2 * pad + big.textSize + shown.size * small.textSize * 1.4f
        val right = g.w - g.m - g.u * .01f; val bottom = g.floor - g.u * .015f
        val rect = RectF(right - boxW, bottom - boxH, right, bottom)
        g.c.save()
        g.c.rotate(-4f, rect.centerX(), rect.centerY())
        g.c.drawRoundRect(rect, g.u * .014f, g.u * .014f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) })
        g.c.drawRoundRect(rect, g.u * .014f, g.u * .014f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = g.u * .006f; color = amber })
        var y = rect.top + pad + big.textSize * .9f
        g.c.drawText(shownHead, rect.left + pad, y, big)
        for (s in shown) { y += small.textSize * 1.4f; g.c.drawText(s, rect.left + pad, y, small) }
        g.c.restore()
    }
}
