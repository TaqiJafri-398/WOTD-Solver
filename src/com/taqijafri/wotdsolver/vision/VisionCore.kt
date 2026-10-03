package com.taqijafri.wotdsolver.vision

/**
 * Platform-agnostic Binance WOTD puzzle vision pipeline.
 *
 * Every function operates on raw ARGB [IntArray] pixels plus width/height, so the
 * exact same code runs on Android (via Bitmap.getPixels) and on the JVM test
 * harness (via ImageIO). Letter templates are rendered by the platform layer and
 * passed in as 48x48 normalized ink maps (see [extractInk48]).
 */
object VisionCore {

    // Tile states (Wordle semantics)
    const val GRAY = 0
    const val YELLOW = 1
    const val GREEN = 2

    /** Tile bounding box; x1/y1 are exclusive. */
    data class Tile(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w: Int get() = x1 - x0
        val h: Int get() = y1 - y0
        val cx: Int get() = (x0 + x1) / 2
        val cy: Int get() = (y0 + y1) / 2
    }

    /** Detected puzzle grid: rows of tiles, top row first, left to right. */
    data class Grid(val rows: List<List<Tile>>) {
        val wordLength: Int get() = rows.firstOrNull()?.size ?: 0
        val rowCount: Int get() = rows.size
    }

    data class LetterMatch(val ch: Char, val score: Double, val margin: Double)

    // ------------------------------------------------------------------
    // Grayscale
    // ------------------------------------------------------------------

    /** Rec.601 luminance in 0..1. */
    fun luminance(pixels: IntArray): FloatArray {
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
        return out
    }

    // ------------------------------------------------------------------
    // Grid detection
    // ------------------------------------------------------------------

    /**
     * Locate the WOTD tile grid in a screenshot.
     *
     * Strategy: tile bodies are brighter than the near-black background, so a
     * global threshold plus connected-component labeling finds tile-like blobs.
     * Blobs are filtered by relative area and squareness, clustered into rows,
     * and consecutive equal-width rows are merged into blocks. The block with
     * the largest tiles in the upper part of the image wins (this rejects the
     * on-screen keyboard, whose keys are smaller and sit at the bottom).
     *
     * Works for 3-8 columns; the column count is discovered, never assumed.
     */
    fun detectGrid(pixels: IntArray, w: Int, h: Int, binThreshold: Float = 0.075f): Grid? {
        val total = w * h
        if (total == 0) return null
        val lum = luminance(pixels)

        // 1. Binarize: tile interiors vs the near-black background. Filled tiles
        //    are ~0.10-0.30; empty (unfilled) tiles are much fainter (~0.03),
        //    so a lower threshold also picks those up.
        val bin = BooleanArray(total)
        for (i in 0 until total) bin[i] = lum[i] > binThreshold

        // 2. Connected components via union-find (4-connectivity), iterative.
        val parent = IntArray(total)
        for (i in 0 until total) parent[i] = if (bin[i]) i else -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                if (!bin[i]) continue
                if (x > 0 && bin[i - 1]) union(parent, i, i - 1)
                if (y > 0 && bin[i - w]) union(parent, i, i - w)
            }
        }

        // 3. Aggregate bounding boxes.
        val minX = IntArray(total) { Int.MAX_VALUE }
        val maxX = IntArray(total) { Int.MIN_VALUE }
        val minY = IntArray(total) { Int.MAX_VALUE }
        val maxY = IntArray(total) { Int.MIN_VALUE }
        val area = IntArray(total)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                if (!bin[i]) continue
                val r = find(parent, i)
                if (x < minX[r]) minX[r] = x
                if (x > maxX[r]) maxX[r] = x
                if (y < minY[r]) minY[r] = y
                if (y > maxY[r]) maxY[r] = y
                area[r]++
            }
        }

        data class Blob(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val area: Int)
        val blobs = ArrayList<Blob>()
        val minArea = (total * 0.0012).toInt()
        val maxArea = (total * 0.05).toInt()
        for (r in 0 until total) {
            val a = area[r]
            if (a < minArea || a > maxArea) continue
            val bw = maxX[r] - minX[r] + 1
            val bh = maxY[r] - minY[r] + 1
            if (bw <= 0 || bh <= 0) continue
            val aspect = bw.toFloat() / bh.toFloat()
            if (aspect < 0.8f || aspect > 1.25f) continue // tiles are square
            blobs.add(Blob(minX[r], minY[r], maxX[r] + 1, maxY[r] + 1, a))
        }
        if (blobs.isEmpty()) return null

        // 4. Cluster blobs into rows by vertical center.
        val sorted = blobs.sortedBy { (it.y0 + it.y1) / 2 }
        val medH = sorted[sorted.size / 2].let { it.y1 - it.y0 }.toFloat()
        val rows = ArrayList<MutableList<Blob>>()
        var rowCy = -1f
        for (b in sorted) {
            val cy = (b.y0 + b.y1) / 2f
            if (rows.isEmpty() || cy - rowCy > medH * 0.55f) {
                rows.add(ArrayList())
                rowCy = cy
            } else {
                // running mean of row center
                val row = rows.last()
                rowCy = (rowCy * row.size + cy) / (row.size + 1)
            }
            rows.last().add(b)
        }

        // 5. Keep plausible puzzle rows (3-8 tiles), sorted left to right.
        val tileRows = rows.mapNotNull { row ->
            if (row.size in 3..8) row.sortedBy { (it.x0 + it.x1) / 2 } else null
        }
        if (tileRows.isEmpty()) return null

        // 6. Merge consecutive rows with equal width and small gaps into blocks.
        data class Block(val rows: List<List<Blob>>, val top: Int)
        val blocks = ArrayList<Block>()
        var cur = ArrayList<List<Blob>>()
        var prevCy = -1f
        var prevCount = -1
        for (row in tileRows) {
            val cy = row.map { (it.y0 + it.y1) / 2f }.average().toFloat()
            if (cur.isEmpty() || (row.size == prevCount && cy - prevCy < medH * 2.4f)) {
                cur.add(row)
            } else {
                blocks.add(Block(cur, cur.first().minOf { it.y0 }))
                cur = ArrayList()
                cur.add(row)
            }
            prevCy = cy
            prevCount = row.size
        }
        if (cur.isNotEmpty()) blocks.add(Block(cur, cur.first().minOf { it.y0 }))

        // 7. Pick the block with the largest tiles among blocks in the upper
        //    88% of the image (excludes the keyboard); fall back to global max.
        fun medianTileArea(b: Block): Double {
            val areas = b.rows.flatten().map { it.area }.sorted()
            return areas[areas.size / 2].toDouble()
        }
        val upper = blocks.filter { it.top < h * 0.88 }
        val pool = if (upper.isNotEmpty()) upper else blocks
        val best = pool.maxByOrNull { medianTileArea(it) } ?: return null

        // 8. Normalize: keep rows with the modal column count.
        val modalCount = best.rows.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key
            ?: return null
        val gridRows = best.rows.filter { it.size == modalCount }.map { row ->
            row.map { Tile(it.x0, it.y0, it.x1, it.y1) }
        }
        if (gridRows.isEmpty()) return null
        return Grid(gridRows)
    }

    private fun find(parent: IntArray, a: Int): Int {
        var x = a
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }

    private fun union(parent: IntArray, a: Int, b: Int) {
        val ra = find(parent, a)
        val rb = find(parent, b)
        if (ra != rb) parent[ra] = rb
    }

    // ------------------------------------------------------------------
    // Tile color detection
    // ------------------------------------------------------------------

    /**
     * Classify a tile as GRAY / YELLOW / GREEN.
     *
     * Samples the inner region (avoids borders and rounded corners) and looks
     * at hue among saturated pixels only — this automatically ignores the
     * letter glyph (dark on colored tiles, light-gray on gray tiles) and is
     * tolerant to brightness/compression variations.
     */
    fun tileState(pixels: IntArray, w: Int, t: Tile): Int {
        val ix0 = t.x0 + (t.w * 24) / 100
        val ix1 = t.x1 - (t.w * 24) / 100
        val iy0 = t.y0 + (t.h * 24) / 100
        val iy1 = t.y1 - (t.h * 24) / 100
        if (ix1 <= ix0 || iy1 <= iy0) return GRAY

        var sat = 0
        var n = 0
        val hues = FloatArray(4096)
        var hn = 0
        var y = iy0
        while (y < iy1) {
            var x = ix0
            val rowOff = y * w
            while (x < ix1) {
                val p = pixels[rowOff + x]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                val s = if (mx > 0f) (mx - mn) / mx else 0f
                if (s > 0.22f && mx > 0.10f) {
                    sat++
                    val d = mx - mn
                    var hue = when {
                        mx == r -> (g - b) / (d + 1e-6f)
                        mx == g -> 2f + (b - r) / (d + 1e-6f)
                        else -> 4f + (r - g) / (d + 1e-6f)
                    } * 60f
                    if (hue < 0f) hue += 360f
                    if (hn < hues.size) hues[hn++] = hue
                }
                n++
                x += 2
            }
            y += 2
        }
        if (n == 0 || sat < n * 0.10) return GRAY
        hues.sort(0, hn)
        val med = hues[hn / 2]
        return when {
            med >= 70f && med <= 175f -> GREEN
            med >= 15f && med <= 75f -> YELLOW
            else -> GRAY
        }
    }

    // ------------------------------------------------------------------
    // Letter extraction + template matching
    // ------------------------------------------------------------------

    /**
     * Binarize [lum] with Otsu's method, treat the minority color as ink,
     * crop to the ink bounding box (+padding) and resize to 48x48 with ink=1.
     * Returns null when there is too little ink (empty tile / noise).
     */
    fun extractInk48(lum: FloatArray, w: Int, h: Int, minInkFrac: Float): FloatArray? {
        if (w <= 4 || h <= 4) return null
        val hist = IntArray(256)
        for (v in lum) hist[(v * 255f).toInt().coerceIn(0, 255)]++
        val thr = otsu(hist) / 255f

        val inkMask = FloatArray(w * h)
        var inkCount = 0
        for (i in lum.indices) {
            val isWhite = lum[i] > thr
            inkMask[i] = if (isWhite) 1f else 0f
            if (isWhite) inkCount++
        }
        // Ink is the minority color: light letters on dark tiles, or dark
        // letters on bright (green/yellow) tiles.
        var ink = inkCount
        if (inkCount > w * h / 2) {
            ink = w * h - inkCount
            for (i in inkMask.indices) inkMask[i] = 1f - inkMask[i]
        }
        if (ink < w * h * minInkFrac) return null

        var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
        for (yy in 0 until h) {
            for (xx in 0 until w) {
                if (inkMask[yy * w + xx] > 0.5f) {
                    if (xx < x0) x0 = xx
                    if (xx > x1) x1 = xx
                    if (yy < y0) y0 = yy
                    if (yy > y1) y1 = yy
                }
            }
        }
        if (x1 < x0) return null
        val pad = maxOf(x1 - x0 + 1, y1 - y0 + 1) / 8 + 2
        val rx0 = maxOf(0, x0 - pad)
        val ry0 = maxOf(0, y0 - pad)
        val rx1 = minOf(w - 1, x1 + pad)
        val ry1 = minOf(h - 1, y1 + pad)
        return resizeBilinear(inkMask, w, h, rx0, ry0, rx1, ry1, 48, 48)
    }

    /**
     * Extract the letter ink map from a tile (48x48, ink=1), or null when the
     * tile is empty. Handles both polarities (light letter on dark tile and
     * dark letter on colored tile) via the minority-ink rule.
     */
    fun tileLetterImage(pixels: IntArray, imgW: Int, t: Tile): FloatArray? {
        val ix0 = t.x0 + t.w / 10
        val ix1 = t.x1 - t.w / 10
        val iy0 = t.y0 + t.h / 10
        val iy1 = t.y1 - t.h / 10
        val cw = ix1 - ix0
        val ch = iy1 - iy0
        if (cw <= 8 || ch <= 8) return null
        val lum = FloatArray(cw * ch)
        for (yy in 0 until ch) {
            val rowOff = (iy0 + yy) * imgW + ix0
            for (xx in 0 until cw) {
                val p = pixels[rowOff + xx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                lum[yy * cw + xx] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            }
        }
        return extractInk48(lum, cw, ch, 0.03f)
    }

    /**
     * Match a 48x48 ink map against letter templates using normalized
     * cross-correlation. Each template char may have several variants
     * (e.g. font weights); the best variant wins. Returns null when the best
     * score is too weak to trust.
     */
    fun matchLetter(img: FloatArray, templates: Map<Char, List<FloatArray>>): LetterMatch? {
        var bestCh = ' '
        var best = -2.0
        var second = -2.0
        for ((ch, variants) in templates) {
            var s = -2.0
            for (v in variants) {
                val c = ncc(img, v)
                if (c > s) s = c
            }
            if (s > best) {
                second = best
                best = s
                bestCh = ch
            } else if (s > second) {
                second = s
            }
        }
        if (best < 0.42) return null
        return LetterMatch(bestCh, best, best - second)
    }

    fun ncc(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        return if (na < 1e-9 || nb < 1e-9) 0.0 else dot / kotlin.math.sqrt(na * nb)
    }

    fun otsu(hist: IntArray): Int {
        val total = hist.sum()
        if (total == 0) return 128
        var sum = 0.0
        for (i in hist.indices) sum += i.toDouble() * hist[i]
        var sumB = 0.0
        var wB = 0
        var max = -1.0
        var thr = 128
        for (i in hist.indices) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i.toDouble() * hist[i]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > max) {
                max = between
                thr = i
            }
        }
        return thr
    }

    /** Bilinear resize of a source region to outW x outH. Region bounds are inclusive. */
    fun resizeBilinear(
        src: FloatArray, srcW: Int, srcH: Int,
        rx0: Int, ry0: Int, rx1: Int, ry1: Int,
        outW: Int, outH: Int
    ): FloatArray {
        val out = FloatArray(outW * outH)
        val rw = (rx1 - rx0 + 1).toFloat()
        val rh = (ry1 - ry0 + 1).toFloat()
        for (oy in 0 until outH) {
            val sy = ry0 + (oy + 0.5f) * rh / outH - 0.5f
            val y0 = sy.toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceIn(0, srcH - 1)
            val fy = (sy - y0).coerceIn(0f, 1f)
            for (ox in 0 until outW) {
                val sx = rx0 + (ox + 0.5f) * rw / outW - 0.5f
                val x0 = sx.toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceIn(0, srcW - 1)
                val fx = (sx - x0).coerceIn(0f, 1f)
                val v00 = src[y0 * srcW + x0]
                val v10 = src[y0 * srcW + x1]
                val v01 = src[y1 * srcW + x0]
                val v11 = src[y1 * srcW + x1]
                out[oy * outW + ox] = (v00 * (1 - fx) + v10 * fx) * (1 - fy) +
                        (v01 * (1 - fx) + v11 * fx) * fy
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Debug helpers (used by the JVM test harness)
    // ------------------------------------------------------------------

    data class TileDebug(
        val hue: Float, val satRatio: Float, val state: Int,
        val letter: Char?, val score: Double, val margin: Double
    )

    /** Same sampling as [tileState] but returns the raw measurements. */
    fun debugTileColor(pixels: IntArray, w: Int, t: Tile): Triple<Float, Float, Int> {
        val ix0 = t.x0 + (t.w * 24) / 100
        val ix1 = t.x1 - (t.w * 24) / 100
        val iy0 = t.y0 + (t.h * 24) / 100
        val iy1 = t.y1 - (t.h * 24) / 100
        var sat = 0
        var n = 0
        val hues = FloatArray(4096)
        var hn = 0
        var y = iy0
        while (y < iy1) {
            var x = ix0
            val rowOff = y * w
            while (x < ix1) {
                val p = pixels[rowOff + x]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                val s = if (mx > 0f) (mx - mn) / mx else 0f
                if (s > 0.22f && mx > 0.10f) {
                    sat++
                    val d = mx - mn
                    var hue = when {
                        mx == r -> (g - b) / (d + 1e-6f)
                        mx == g -> 2f + (b - r) / (d + 1e-6f)
                        else -> 4f + (r - g) / (d + 1e-6f)
                    } * 60f
                    if (hue < 0f) hue += 360f
                    if (hn < hues.size) hues[hn++] = hue
                }
                n++
                x += 2
            }
            y += 2
        }
        val ratio = if (n == 0) 0f else sat.toFloat() / n
        hues.sort(0, hn)
        val med = if (hn == 0) -1f else hues[hn / 2]
        return Triple(med, ratio, tileState(pixels, w, t))
    }
}
