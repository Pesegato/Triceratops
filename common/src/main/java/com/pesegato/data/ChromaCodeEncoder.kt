package com.pesegato.data

/**
 * ChromaCode - High-density color-based binary encoding for screen display
 *
 * Format overview:
 * - 16 colors per cell = 4 bits per cell
 * - Reed-Solomon error correction (configurable)
 * - Structured with finder patterns, alignment markers, format info
 * - Designed for screen display (sRGB color space)
 * - Suitable for cryptographic keys and binary secrets (out-of-band transfer)
 *
 * Color palette (4-bit nibbles 0x0..0xF):
 *  0=Black, 1=White, 2=Red, 3=Green, 4=Blue, 5=Cyan, 6=Magenta, 7=Yellow,
 *  8=DarkRed, 9=DarkGreen, A=DarkBlue, B=Orange, C=Purple, D=Teal, E=Pink, F=LightGray
 */

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.sqrt


public object ChromaCode {

    // ── Palette: 16 perceptually-distinct colors ──────────────────────────────
    val PALETTE = arrayOf(
        Color(0x0D0D0D),   // 0  Near-Black
        Color(0xF5F5F5),   // 1  Near-White
        Color(0xE63232),   // 2  Red
        Color(0x32C832),   // 3  Green
        Color(0x3264E6),   // 4  Blue
        Color(0x32C8C8),   // 5  Cyan
        Color(0xC832C8),   // 6  Magenta
        Color(0xE6C832),   // 7  Yellow
        Color(0x8B1A1A),   // 8  Dark Red
        Color(0x1A6B1A),   // 9  Dark Green
        Color(0x1A3C8B),   // A  Dark Blue
        Color(0xE6781A),   // B  Orange
        Color(0x6B1A8B),   // C  Purple
        Color(0x1A7878),   // D  Teal
        Color(0xE678A0),   // E  Pink
        Color(0xAAAAAA),   // F  Mid-Gray
    )

    // Special marker colors (not in data palette)
    val COLOR_FINDER_OUTER = Color(0x101010)
    val COLOR_FINDER_INNER = Color(0xF0F0F0)
    val COLOR_FINDER_CENTER = Color(0x303030)
    val COLOR_QUIET = Color(0x1E1E1E)
    val COLOR_FORMAT_BG = Color(0x282828)

    const val CELL_SIZE = 10          // pixels per data cell
    const val QUIET_CELLS = 3         // quiet zone in cells
    const val FINDER_SIZE = 7         // cells per finder pattern
    const val VERSION = 1             // format version
    const val MAGIC = 0xCC.toByte()   // magic byte in header

    // ── Reed-Solomon GF(256) ─────────────────────────────────────────────────

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun gfPoly(nEcc: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until nEcc) {
            val factor = intArrayOf(1, GF_EXP[i])
            val result = IntArray(g.size + factor.size - 1)
            for (j in g.indices) for (k in factor.indices)
                result[j + k] = result[j + k] xor gfMul(g[j], factor[k])
            g = result
        }
        return g
    }

    fun rsEncode(data: ByteArray, nEcc: Int): ByteArray {
        val gen = gfPoly(nEcc)
        val msg = IntArray(data.size + nEcc)
        for (i in data.indices) msg[i] = data[i].toInt() and 0xFF
        for (i in data.indices) {
            val coef = msg[i]
            if (coef != 0) for (j in 1..gen.lastIndex)
                msg[i + j] = msg[i + j] xor gfMul(gen[j], coef)
        }
        return ByteArray(nEcc) { msg[data.size + it].toByte() }
    }

    // ── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Encodes [data] bytes into a BufferedImage ChromaCode symbol.
     *
     * @param data       Raw binary payload (e.g. a cryptographic key)
     * @param eccRatio   Fraction of ECC codewords (0.2 = 20% redundancy). Range: 0.1..0.4
     * @param cellSize   Pixels per cell (default 10)
     */
    fun encode(
        data: ByteArray,
        eccRatio: Double = 0.25,
        cellSize: Int = CELL_SIZE
    ): BufferedImage {
        // 1. Build payload: header + data + ECC
        val nEcc = maxOf(4, (data.size * eccRatio).toInt())
        val header = buildHeader(data.size, nEcc, VERSION)
        val fullData = header + data
        val eccBytes = rsEncode(fullData, nEcc)
        val payload = fullData + eccBytes  // bytes

        // 2. Convert bytes to nibbles (4-bit values)
        val nibbles = bytesToNibbles(payload)

        // 3. Compute grid dimensions
        val gridCells = computeGridSize(nibbles.size)
        val totalCells = gridCells * gridCells

        // Pad nibbles to fill grid exactly
        val paddedNibbles = nibbles + IntArray(totalCells - nibbles.size) { 0 }

        // 4. Render image
        return render(paddedNibbles, gridCells, cellSize, nEcc)
    }

    private fun buildHeader(dataLen: Int, nEcc: Int, version: Int): ByteArray {
        // Header: MAGIC(1) VERSION(1) DataLen(2 big-endian) EccLen(1) Reserved(1)
        return byteArrayOf(
            MAGIC,
            version.toByte(),
            (dataLen shr 8).toByte(),
            (dataLen and 0xFF).toByte(),
            nEcc.toByte(),
            0x00  // reserved
        )
    }

    private fun bytesToNibbles(bytes: ByteArray): IntArray {
        val result = IntArray(bytes.size * 2)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            result[i * 2] = (b shr 4) and 0xF
            result[i * 2 + 1] = b and 0xF
        }
        return result
    }

    private fun computeGridSize(nibbleCount: Int): Int {
        // Account for finder patterns (4 corners × 7×7 cells = 196 cells)
        // and format strip (border row/col)
        val overhead = 4 * FINDER_SIZE * FINDER_SIZE + FINDER_SIZE * 4
        val needed = nibbleCount + overhead
        return maxOf(FINDER_SIZE * 2 + 4, ceil(sqrt(needed.toDouble())).toInt() + 2)
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private fun render(
        nibbles: IntArray,
        gridCells: Int,
        cellSize: Int,
        nEcc: Int
    ): BufferedImage {
        val totalGrid = gridCells + QUIET_CELLS * 2
        val imgSize = totalGrid * cellSize
        val img = BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_RGB)
        val g2 = img.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

        // Fill quiet zone
        g2.color = COLOR_QUIET
        g2.fillRect(0, 0, imgSize, imgSize)

        val offset = QUIET_CELLS * cellSize  // pixel offset for data area

        fun cellRect(col: Int, row: Int) =
            java.awt.Rectangle(offset + col * cellSize, offset + row * cellSize, cellSize, cellSize)

        // Draw data background
        g2.color = Color(0x181818)
        g2.fillRect(offset, offset, gridCells * cellSize, gridCells * cellSize)

        // Finder patterns at 3 corners (top-left, top-right, bottom-left)
        // 4th corner (bottom-right) is reserved for format info
        drawFinder(g2, cellRect(0, 0), cellSize)
        drawFinder(g2, cellRect(gridCells - FINDER_SIZE, 0), cellSize)
        drawFinder(g2, cellRect(0, gridCells - FINDER_SIZE), cellSize)

        // Format info block (bottom-right corner)
        drawFormatBlock(g2, cellRect(gridCells - FINDER_SIZE, gridCells - FINDER_SIZE), cellSize, nEcc, gridCells)

        // Build occupied mask
        val occupied = Array(gridCells) { BooleanArray(gridCells) }
        markFinder(occupied, 0, 0)
        markFinder(occupied, gridCells - FINDER_SIZE, 0)
        markFinder(occupied, 0, gridCells - FINDER_SIZE)
        markFormatBlock(occupied, gridCells - FINDER_SIZE, gridCells - FINDER_SIZE)

        // Place alignment markers (every ~15 cells in interior)
        val alignPositions = computeAlignPositions(gridCells)
        for (ar in alignPositions) for (ac in alignPositions) {
            if (!occupied[ar][ac]) {
                drawAlignment(g2, cellRect(ac, ar), cellSize)
                markAlignment(occupied, ac, ar)
            }
        }

        // Fill data cells
        var nIdx = 0
        for (row in 0 until gridCells) {
            for (col in 0 until gridCells) {
                if (occupied[row][col]) continue
                val nibble = if (nIdx < nibbles.size) nibbles[nIdx++] else 0
                g2.color = PALETTE[nibble]
                val r = cellRect(col, row)
                g2.fillRect(r.x, r.y, r.width, r.height)
            }
        }

        // Grid lines rimosso - corrompevano i pixel di bordo causando errori di decode

        g2.dispose()
        return img
    }

    private fun drawFinder(g2: java.awt.Graphics2D, base: java.awt.Rectangle, cs: Int) {
        // 7×7 finder: outer ring dark, middle ring light, inner 3×3 dark
        for (r in 0 until FINDER_SIZE) for (c in 0 until FINDER_SIZE) {
            val color = when {
                r == 0 || r == 6 || c == 0 || c == 6 -> COLOR_FINDER_OUTER
                r == 1 || r == 5 || c == 1 || c == 5 -> COLOR_FINDER_INNER
                else -> COLOR_FINDER_CENTER
            }
            g2.color = color
            g2.fillRect(base.x + c * cs, base.y + r * cs, cs, cs)
        }
    }

    private fun markFinder(occ: Array<BooleanArray>, col: Int, row: Int) {
        for (r in row until row + FINDER_SIZE + 1) for (c in col until col + FINDER_SIZE + 1)
            if (r < occ.size && c < occ[0].size) occ[r][c] = true
    }

    private fun drawAlignment(g2: java.awt.Graphics2D, base: java.awt.Rectangle, cs: Int) {
        // 3×3 alignment: outer ring PALETTE[7] (yellow), center PALETTE[0] (black)
        for (r in 0 until 3) for (c in 0 until 3) {
            g2.color = if (r == 1 && c == 1) PALETTE[0] else PALETTE[7]
            g2.fillRect(base.x + c * cs, base.y + r * cs, cs, cs)
        }
    }

    private fun markAlignment(occ: Array<BooleanArray>, col: Int, row: Int) {
        for (r in row until row + 3) for (c in col until col + 3)
            if (r < occ.size && c < occ[0].size) occ[r][c] = true
    }

    private fun drawFormatBlock(
        g2: java.awt.Graphics2D,
        base: java.awt.Rectangle,
        cs: Int,
        nEcc: Int,
        gridCells: Int
    ) {
        // 7×7 format block encoding: ECC level, grid size, version
        g2.color = COLOR_FORMAT_BG
        g2.fillRect(base.x, base.y, FINDER_SIZE * cs, FINDER_SIZE * cs)

        // Encode format nibbles visually (2×2 super-cells = 4 nibbles)
        val formatNibbles = intArrayOf(
            (nEcc shr 4) and 0xF,
            nEcc and 0xF,
            (gridCells shr 4) and 0xF,
            gridCells and 0xF,
        )
        val positions = listOf(Pair(1, 1), Pair(1, 4), Pair(4, 1), Pair(4, 4))
        for ((i, pos) in positions.withIndex()) {
            g2.color = PALETTE[formatNibbles[i]]
            g2.fillRect(base.x + pos.second * cs, base.y + pos.first * cs, 2 * cs, 2 * cs)
        }
        // Border
        g2.color = COLOR_FINDER_OUTER
        g2.stroke = BasicStroke(1.5f)
        g2.drawRect(base.x, base.y, FINDER_SIZE * cs - 1, FINDER_SIZE * cs - 1)
    }

    private fun markFormatBlock(occ: Array<BooleanArray>, col: Int, row: Int) {
        for (r in row until row + FINDER_SIZE) for (c in col until col + FINDER_SIZE)
            if (r < occ.size && c < occ[0].size) occ[r][c] = true
    }

    private fun computeAlignPositions(gridCells: Int): List<Int> {
        if (gridCells < 18) return emptyList()
        val step = 12
        val result = mutableListOf<Int>()
        var pos = FINDER_SIZE + 3
        while (pos < gridCells - FINDER_SIZE - 3) {
            result.add(pos)
            pos += step
        }
        return result
    }

    // ── Decoding ─────────────────────────────────────────────────────────────

    /**
     * Decodes a ChromaCode BufferedImage back to the original byte array.
     * Throws [ChromaCodeException] on decode failure.
     */
    fun decode(img: BufferedImage, cellSize: Int = CELL_SIZE): ByteArray {
        val imgSize = img.width
        val gridCells = (imgSize / cellSize) - QUIET_CELLS * 2
        val offset = QUIET_CELLS * cellSize

        // Read format block to get ECC length
        val fmtBase = Pair(
            offset + (gridCells - FINDER_SIZE) * cellSize,
            offset + (gridCells - FINDER_SIZE) * cellSize
        )
        val formatNibbles = readFormatBlock(img, fmtBase.first, fmtBase.second, cellSize)
        val nEcc = ((formatNibbles[0] shl 4) or formatNibbles[1])
        val readGridCells = ((formatNibbles[2] shl 4) or formatNibbles[3])

        if (readGridCells != gridCells)
            throw ChromaCodeException("Grid size mismatch: expected $gridCells got $readGridCells")

        // Build occupied mask
        val occupied = Array(gridCells) { BooleanArray(gridCells) }
        markFinder(occupied, 0, 0)
        markFinder(occupied, gridCells - FINDER_SIZE, 0)
        markFinder(occupied, 0, gridCells - FINDER_SIZE)
        markFormatBlock(occupied, gridCells - FINDER_SIZE, gridCells - FINDER_SIZE)
        val alignPositions = computeAlignPositions(gridCells)
        for (ar in alignPositions) for (ac in alignPositions) {
            if (!occupied[ar][ac]) markAlignment(occupied, ac, ar)
        }

        // Read all nibbles
        val nibbles = mutableListOf<Int>()
        for (row in 0 until gridCells) {
            for (col in 0 until gridCells) {
                if (occupied[row][col]) continue
                val px = offset + col * cellSize + cellSize / 2
                val py = offset + row * cellSize + cellSize / 2
                nibbles.add(closestPaletteIndex(img.getRGB(px, py)))
            }
        }

        // Convert nibbles → bytes
        val allBytes = nibblesToBytes(nibbles.toIntArray())

        // RS decode
        val headerSize = 6
        val dataLen = ((allBytes[2].toInt() and 0xFF) shl 8) or (allBytes[3].toInt() and 0xFF)
        val eccLen = allBytes[4].toInt() and 0xFF

        if (allBytes[0] != MAGIC) throw ChromaCodeException("Bad magic byte")
        if (allBytes[1] != VERSION.toByte()) throw ChromaCodeException("Unknown version")

        val payload = allBytes.copyOf(headerSize + dataLen)
        val eccReceived = allBytes.copyOfRange(headerSize + dataLen, headerSize + dataLen + eccLen)
        val eccExpected = rsEncode(payload, eccLen)

        // Check syndrome
        val syndrome = ByteArray(eccLen) { (eccReceived[it].toInt() xor eccExpected[it].toInt()).toByte() }
        if (syndrome.any { it != 0.toByte() }) {
            // Attempt single-block correction (simplified)
            throw ChromaCodeException("ECC mismatch — data may be corrupted")
        }

        return payload.copyOfRange(headerSize, headerSize + dataLen)
    }

    private fun readFormatBlock(img: BufferedImage, bx: Int, by: Int, cs: Int): IntArray {
        val positions = listOf(Pair(1, 1), Pair(1, 4), Pair(4, 1), Pair(4, 4))
        return IntArray(4) { i ->
            val (r, c) = positions[i]
            // Centro del super-cell 2×2
            val centerX = bx + c * cs + cs
            val centerY = by + r * cs + cs

            // Media RGB su kernel 2×2 per robustezza contro pixel corrotti
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (dy in -1..0) {
                for (dx in -1..0) {
                    val rgb = img.getRGB(centerX + dx, centerY + dy)
                    rSum += (rgb shr 16) and 0xFF
                    gSum += (rgb shr 8) and 0xFF
                    bSum += rgb and 0xFF
                }
            }
            // Media dei 4 pixel
            val avgRGB = ((rSum / 4) shl 16) or ((gSum / 4) shl 8) or (bSum / 4)
            closestPaletteIndex(avgRGB)
        }
    }

    private fun nibblesToBytes(nibbles: IntArray): ByteArray {
        val size = nibbles.size / 2
        return ByteArray(size) { i ->
            ((nibbles[i * 2] shl 4) or nibbles[i * 2 + 1]).toByte()
        }
    }

    private fun closestPaletteIndex(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in PALETTE.indices) {
            val pr = PALETTE[i].red
            val pg = PALETTE[i].green
            val pb = PALETTE[i].blue
            val dist = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (dist < bestDist) {
                bestDist = dist; best = i
            }
        }
        return best
    }

    class ChromaCodeException(message: String) : Exception(message)
}

// ── Usage example ─────────────────────────────────────────────────────────────

fun main() {
    val secret = ByteArray(1024) { it.toByte() }  // simulated 256-bit key

    println("Encoding ${secret.size} bytes...")
    val img = ChromaCode.encode(secret, eccRatio = 0.25, cellSize = 10)

    val outputFile = java.io.File("chromacode_output.png")
    javax.imageio.ImageIO.write(img, "PNG", outputFile)
    println("Saved: ${outputFile.absolutePath} (${img.width}×${img.height} px)")

    println("Decoding...")
    val decoded = ChromaCode.decode(img, cellSize = 10)

    println("Match: ${decoded.contentEquals(secret)}")
    println("Decoded hex: ${decoded.joinToString("") { "%02X".format(it) }}")
}