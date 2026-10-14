package com.ranzxm.camskanuy.scanner

import kotlin.math.abs
import kotlin.math.hypot

data class ScanPoint(val x: Float, val y: Float)

/** Clockwise corners in the EXIF-corrected original: TL, TR, BR, BL; never rotated. */
data class ScanCrop(val points: List<ScanPoint>) {
    fun encode(): String = points.joinToString(";") { "${it.x},${it.y}" }

    fun isValid(): Boolean {
        if (points.size != 4 || points.any { !it.x.isFinite() || !it.y.isFinite() ||
                it.x !in 0f..1f || it.y !in 0f..1f }) return false
        // Strictly convex, clockwise in screen coordinates; excludes crossing and collapsed edges.
        if (points.indices.any { i ->
                val a = points[i]
                val b = points[(i + 1) % 4]
                val c = points[(i + 2) % 4]
                (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) < 0.0001f ||
                    hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) < 0.015
            }) return false
        return area() >= 0.005f
    }

    fun area(): Float = abs(points.indices.sumOf { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        (a.x * b.y - b.x * a.y).toDouble()
    }.toFloat()) / 2f

    fun move(index: Int, x: Float, y: Float): ScanCrop {
        if (index !in 0..3) return this
        val next = ScanCrop(points.mapIndexed { i, point ->
            if (i == index) ScanPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)) else point
        })
        return if (next.isValid()) next else this
    }

    companion object {
        val FULL = ScanCrop(listOf(ScanPoint(0f, 0f), ScanPoint(1f, 0f),
            ScanPoint(1f, 1f), ScanPoint(0f, 1f)))

        fun decode(value: String?): ScanCrop? {
            if (value == null) return null
            val points = value.split(';').map { pair ->
                val xy = pair.split(',')
                if (xy.size != 2) return null
                ScanPoint(xy[0].toFloatOrNull() ?: return null, xy[1].toFloatOrNull() ?: return null)
            }
            return ScanCrop(points).takeIf { it.isValid() }
        }
    }
}

enum class ScanFilter { ORIGINAL, COLOR, GRAYSCALE, BLACK_WHITE }

/** Rotation is applied after perspective correction, so stored corners stay source-relative. */
enum class ScanRotation(val degrees: Int) {
    R0(0), R90(90), R180(180), R270(270);
    fun next(): ScanRotation = entries[(ordinal + 1) % entries.size]
    companion object {
        fun fromDegrees(degrees: Int): ScanRotation = entries.firstOrNull {
            it.degrees == ((degrees % 360) + 360) % 360
        } ?: R0
    }
}

/** Small, deterministic local detector; no model, network, native library, or Android dependency. */
object DocumentEdgeDetector {
    fun detect(gray: IntArray, width: Int, height: Int, checkpoint: () -> Unit = {}): ScanCrop? {
        require(width > 0 && height > 0 && gray.size == width * height)
        if (width < 16 || height < 16) return null
        val histogram = IntArray(256)
        gray.forEach { histogram[it.coerceIn(0, 255)]++ }
        val threshold = otsu(histogram, gray.size)
        val mean = gray.average()
        // Uniform/low-contrast inputs have no defensible page boundary.
        if (gray.sumOf { val d = it - mean; d * d } / gray.size < 36.0) return null
        val candidates = mutableListOf<ScanCrop>()
        for (bright in listOf(true, false)) {
            checkpoint()
            val mask = BooleanArray(gray.size) { if (bright) gray[it] > threshold else gray[it] <= threshold }
            // Close isolated printing holes and connect short breaks without a full-resolution buffer.
            val dilated = BooleanArray(gray.size)
            for (y in 1 until height - 1) for (x in 1 until width - 1) {
                val p = y * width + x
                dilated[p] = mask[p] || mask[p - 1] || mask[p + 1] || mask[p - width] || mask[p + width]
            }
            candidates += components(dilated, width, height, checkpoint)
        }
        // Gradient components handle borders whose paper/background brightness overlaps.
        val edges = BooleanArray(gray.size)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val p = y * width + x
            val gx = abs(gray[p + 1] - gray[p - 1])
            val gy = abs(gray[p + width] - gray[p - width])
            edges[p] = gx + gy > 70
        }
        val joined = BooleanArray(gray.size)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val p = y * width + x
            joined[p] = (-1..1).any { dy -> (-1..1).any { dx -> edges[p + dy * width + dx] } }
        }
        candidates += components(joined, width, height, checkpoint)
        return candidates.filter { crop ->
            crop.area() in 0.12f..0.96f && boundaryContrast(crop, gray, width, height) >= 10f
        }.maxByOrNull { it.area() }
    }

    private fun components(mask: BooleanArray, w: Int, h: Int, checkpoint: () -> Unit): List<ScanCrop> {
        val queue = IntArray(mask.size)
        val result = mutableListOf<ScanCrop>()
        for (start in mask.indices) {
            if (start % w == 0) checkpoint()
            if (!mask[start]) continue
            var head = 0
            var tail = 1
            queue[0] = start
            mask[start] = false
            val extreme = IntArray(4) { start }
            val scores = doubleArrayOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
            while (head < tail) {
                val p = queue[head++]
                val x = p % w
                val y = p / w
                val sum = x.toDouble() / w + y.toDouble() / h
                val diff = x.toDouble() / w - y.toDouble() / h
                if (sum < scores[0]) { scores[0] = sum; extreme[0] = p }
                if (diff > scores[1]) { scores[1] = diff; extreme[1] = p }
                if (sum > scores[2]) { scores[2] = sum; extreme[2] = p }
                if (diff < scores[3]) { scores[3] = diff; extreme[3] = p }
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val next = ny * w + nx
                    if (mask[next]) { mask[next] = false; queue[tail++] = next }
                }
            }
            if (tail < (w + h) / 3) continue
            val crop = ScanCrop(extreme.map { ScanPoint((it % w).toFloat() / (w - 1), (it / w).toFloat() / (h - 1)) })
            if (crop.isValid()) result += crop
        }
        return result
    }

    private fun boundaryContrast(crop: ScanCrop, gray: IntArray, w: Int, h: Int): Float {
        var sum = 0f
        var count = 0
        for (i in 0..3) {
            val a = crop.points[i]
            val b = crop.points[(i + 1) % 4]
            val dx = (b.x - a.x) * w
            val dy = (b.y - a.y) * h
            val length = hypot(dx, dy).coerceAtLeast(1f)
            val nx = -dy / length * 3f
            val ny = dx / length * 3f
            for (step in 2..8) {
                val t = step / 10f
                val x = (a.x + (b.x - a.x) * t) * (w - 1)
                val y = (a.y + (b.y - a.y) * t) * (h - 1)
                val ix = (x + nx).toInt(); val iy = (y + ny).toInt()
                val ox = (x - nx).toInt(); val oy = (y - ny).toInt()
                if (ix in 0 until w && ox in 0 until w && iy in 0 until h && oy in 0 until h) {
                    sum += abs(gray[iy * w + ix] - gray[oy * w + ox]); count++
                }
            }
        }
        return if (count >= 12) sum / count else 0f
    }

    private fun otsu(histogram: IntArray, total: Int): Int {
        val sum = histogram.indices.sumOf { it.toLong() * histogram[it] }
        var left = 0L
        var leftSum = 0L
        var best = -1.0
        var threshold = 127
        for (t in 0..254) {
            left += histogram[t]
            leftSum += t.toLong() * histogram[t]
            val right = total - left
            if (left == 0L || right == 0L) continue
            val difference = leftSum.toDouble() / left - (sum - leftSum).toDouble() / right
            val variance = left.toDouble() * right * difference * difference
            if (variance > best) { best = variance; threshold = t }
        }
        return threshold
    }
}
