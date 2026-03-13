package com.pesegato.data

/**
 * ChromaCode — Multi-profile color-based binary encoding
 *
 * Profiles:
 *   CC4 — 16 colors, 4 bit/cell.  Robust, any camera, JPEG q70+
 *   CC8 — 256 colors, 8 bit/cell. 2× density, JPEG q95+, calibrated display
 *
 * Use ChromaCodeProfile.recommend(data) to auto-select profile + parameters.
 */

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.sqrt

// ══════════════════════════════════════════════════════════════════════════════
// Profiles
// ══════════════════════════════════════════════════════════════════════════════

enum class ChromaProfile(
    /**
     * Nibble (0x1..0xF) written into the visual format block.
     * Kept small and distinct from any accidental byte value in the header.
     *   CC4 → 0x1  (palette CC4[1] = Near-White, high contrast on dark bg)
     *   CC8 → 0x2  (palette CC4[2] = Red, unambiguous)
     * These values are intentionally ≠ bitsPerCell to avoid confusion.
     */
    val nibble: Int,
    val bitsPerCell: Int,
    val paletteSize: Int,
    val label: String,
    val minJpegQuality: Int,
    val description: String
) {
    CC4(
        nibble = 0x1,
        bitsPerCell = 4,
        paletteSize = 16,
        label = "CC4",
        minJpegQuality = 70,
        description = "16 colors · 4 bit/cell · universal"
    ),
    CC8(
        nibble = 0x2,
        bitsPerCell = 8,
        paletteSize = 256,
        label = "CC8",
        minJpegQuality = 95,
        description = "256 colors · 8 bit/cell · high-density"
    );

    /** Cells needed to store [byteCount] bytes (raw, before ECC). */
    fun cellsForBytes(byteCount: Int): Int =
        ceil(byteCount * 8.0 / bitsPerCell).toInt()

    companion object {
        fun fromNibble(nibble: Int) = entries.firstOrNull { it.nibble == nibble }
            ?: throw ChromaCode.ChromaCodeException(
                "Unknown profile nibble: 0x${nibble.toString(16).uppercase()} " +
                        "(valid: CC4=0x1, CC8=0x2)"
            )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Recommendation engine
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Output of [ChromaCodeProfile.recommend].
 *
 * @param profile    Suggested ChromaProfile
 * @param cellSize   Suggested cell size in pixels
 * @param eccRatio   Suggested ECC ratio
 * @param estimatedImageSize  Expected image side in pixels
 * @param payloadBytes        Actual payload bytes that will be encoded
 * @param reason     Human-readable rationale
 */
data class ChromaRecommendation(
    val profile: ChromaProfile,
    val cellSize: Int,
    val eccRatio: Double,
    val estimatedImageSize: Int,
    val payloadBytes: Int,
    val reason: String
)

object ChromaCodeProfile {

    // Thresholds
    private const val CC4_MAX_PAYLOAD = 3_256   // @ 800px, cs=8, ecc=0.25
    private const val CC8_MAX_PAYLOAD = 6_518   // @ 800px, cs=8, ecc=0.25
    private const val TARGET_IMAGE_PX = 800

    /**
     * Recommends the optimal [ChromaProfile], [cellSize], and [eccRatio]
     * for encoding [data].
     *
     * Decision logic:
     * 1. If payload ≤ CC4 threshold → use CC4 (universally compatible)
     * 2. If payload ≤ CC8 threshold → use CC8 (higher density)
     * 3. If payload > CC8 threshold → CC8 with warning, or split required
     *
     * cellSize is chosen so the symbol fits in ~800px at the given ECC.
     * eccRatio defaults to 0.25 (robust for indoor use).
     */
    fun recommend(data: ByteArray, targetImagePx: Int = TARGET_IMAGE_PX): ChromaRecommendation {
        val payloadBytes = data.size

        // Add ChromaCode + DeviceRecord header overhead estimate
        val totalBytes = payloadBytes + 6  // ChromaCode header

        return when {
            totalBytes <= CC4_MAX_PAYLOAD -> {
                val (cs, ecc) = fitParameters(totalBytes, ChromaProfile.CC4, targetImagePx)
                ChromaRecommendation(
                    profile = ChromaProfile.CC4,
                    cellSize = cs,
                    eccRatio = ecc,
                    estimatedImageSize = estimateImageSize(totalBytes, ChromaProfile.CC4, cs, ecc),
                    payloadBytes = payloadBytes,
                    reason = "CC4 fits comfortably (${payloadBytes}B ≤ ${CC4_MAX_PAYLOAD}B). " +
                            "Universally compatible with any camera and JPEG quality."
                )
            }

            totalBytes <= CC8_MAX_PAYLOAD -> {
                val (cs, ecc) = fitParameters(totalBytes, ChromaProfile.CC8, targetImagePx)
                ChromaRecommendation(
                    profile = ChromaProfile.CC8,
                    cellSize = cs,
                    eccRatio = ecc,
                    estimatedImageSize = estimateImageSize(totalBytes, ChromaProfile.CC8, cs, ecc),
                    payloadBytes = payloadBytes,
                    reason = "CC4 would require oversized image. CC8 doubles density. " +
                            "Requires JPEG quality ≥ 95 in CameraX and calibrated display."
                )
            }

            else -> {
                // Best effort with CC8 max density
                ChromaRecommendation(
                    profile = ChromaProfile.CC8,
                    cellSize = 8,
                    eccRatio = 0.20,
                    estimatedImageSize = estimateImageSize(totalBytes, ChromaProfile.CC8, 8, 0.20),
                    payloadBytes = payloadBytes,
                    reason = "WARNING: payload ${payloadBytes}B exceeds single-symbol capacity " +
                            "(CC8 max ≈ ${CC8_MAX_PAYLOAD}B @ 800px). Consider splitting into " +
                            "multiple symbols or compressing the payload."
                )
            }
        }
    }

    /**
     * Finds the best (cellSize, eccRatio) pair so the symbol fits within
     * [targetImagePx] pixels.
     */
    private fun fitParameters(
        totalBytes: Int,
        profile: ChromaProfile,
        targetPx: Int
    ): Pair<Int, Double> {
        val eccRatio = 0.25  // default for indoor use

        // Try cell sizes from large to small, pick largest that fits
        for (cs in listOf(10, 8)) {
            val imgSize = estimateImageSize(totalBytes, profile, cs, eccRatio)
            if (imgSize <= targetPx) return Pair(cs, eccRatio)
        }
        // Fallback: smallest cell size
        return Pair(8, eccRatio)
    }

    private fun estimateImageSize(
        totalBytes: Int,
        profile: ChromaProfile,
        cellSize: Int,
        eccRatio: Double
    ): Int {
        val eccBytes = ceil(totalBytes * eccRatio).toInt()
        val allBytes = totalBytes + eccBytes
        val nibbles = allBytes * (8.0 / profile.bitsPerCell)
        val overhead = 3 * 8 * 8 + 7 * 7 + 25 * 9  // finders + format + alignments
        val gridCells = ceil(sqrt(nibbles + overhead)).toInt() + 2
        val quietCells = 3
        return (gridCells + quietCells * 2) * cellSize
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Palettes
// ══════════════════════════════════════════════════════════════════════════════

object ChromaPalettes {

    /** CC4: 16 perceptually-distinct colors, min ΔE ≈ 72, safe for any camera */
    val CC4 = arrayOf(
        Color(0x0D0D0D),  // 0  Near-Black
        Color(0xF5F5F5),  // 1  Near-White
        Color(0xE63232),  // 2  Red
        Color(0x32C832),  // 3  Green
        Color(0x3264E6),  // 4  Blue
        Color(0x32C8C8),  // 5  Cyan
        Color(0xC832C8),  // 6  Magenta
        Color(0xE6C832),  // 7  Yellow
        Color(0x8B1A1A),  // 8  Dark Red
        Color(0x1A6B1A),  // 9  Dark Green
        Color(0x1A3C8B),  // A  Dark Blue
        Color(0xE6781A),  // B  Orange
        Color(0x6B1A8B),  // C  Purple
        Color(0x1A7878),  // D  Teal
        Color(0xE678A0),  // E  Pink
        Color(0xAAAAAA),  // F  Mid-Gray
    )

    /**
     * CC8: 256 colors, Lab in-gamut farthest-point sampled.
     * Min RGB distance ≈ 25.5 (ΔE ≈ 11).
     * Requires JPEG quality ≥ 95 and a calibrated display.
     */
    val CC8 = arrayOf(
        Color(0x163719), Color(0xE2E2E2), Color(0x1986E3), Color(0xE96C2E),
        Color(0x61D056), Color(0x82269B), Color(0xC36BEA), Color(0x60E9DC),
        Color(0xDADA69), Color(0x891C1A), Color(0x247672), Color(0x9A8E91),
        Color(0x858915), Color(0x18D19B), Color(0xE9648A), Color(0x1C9820),
        Color(0x2F1F7B), Color(0x6E6AD5), Color(0x96E79A), Color(0x715856),
        Color(0x9DBBE6), Color(0xE3ABA7), Color(0xBF3854), Color(0xB69B4B),
        Color(0x54ABAF), Color(0x1B4DAB), Color(0x535616), Color(0xB153AB),
        Color(0x4C213F), Color(0x27CAE6), Color(0x569244), Color(0x646794),
        Color(0xBF3E15), Color(0xE3A3E7), Color(0x20B465), Color(0x82185F),
        Color(0xE7986D), Color(0xDBE9A8), Color(0x66A1E5), Color(0x9AD859),
        Color(0x4FDF96), Color(0xEB74C3), Color(0xA96370), Color(0xA76832),
        Color(0x306A3C), Color(0xB8BD8A), Color(0xBA8FC2), Color(0x83A967),
        Color(0x317CB2), Color(0x17324D), Color(0x4FA075), Color(0xB6CCBD),
        Color(0x86B3B4), Color(0x5D3676), Color(0x1FA198), Color(0x9382E9),
        Color(0x4F42AB), Color(0x878745), Color(0x9E3678), Color(0x84541A),
        Color(0x154975), Color(0x8DE4D3), Color(0xA152D6), Color(0x3F5562),
        Color(0xCE6552), Color(0x5B1915), Color(0x9577B8), Color(0xC7908D),
        Color(0x75CB89), Color(0x34DEBB), Color(0x89383C), Color(0x7BAC31),
        Color(0x687C6E), Color(0x16894A), Color(0x6FDAB2), Color(0x47B42F),
        Color(0x1EACC9), Color(0xC64981), Color(0x521691), Color(0xD6B650),
        Color(0x428621), Color(0x8C588C), Color(0x4B85D1), Color(0x729890),
        Color(0xA1173D), Color(0x3A508A), Color(0x3BA7E5), Color(0xC7812A),
        Color(0x6D6E2E), Color(0x8453B6), Color(0x3BC185), Color(0x7990C5),
        Color(0xB4DBE7), Color(0xE6C185), Color(0x633D30), Color(0xB8E27D),
        Color(0xABABAB), Color(0xAE9C71), Color(0x77C4D3), Color(0x3A3D2C),
        Color(0xD34536), Color(0xD9C0C7), Color(0x5064BE), Color(0x427F90),
        Color(0x8E836A), Color(0x9DB540), Color(0x1C5F1E), Color(0xE9834E),
        Color(0x15729C), Color(0xC875AC), Color(0x93B486), Color(0xCC7B6F),
        Color(0x711935), Color(0x52C9D3), Color(0xA39127), Color(0xB9BF63),
        Color(0xBE9EE2), Color(0x5A88AE), Color(0x4AB252), Color(0xDD94C7),
        Color(0x985953), Color(0xAB7950), Color(0x6C3357), Color(0xE96867),
        Color(0xB96C8D), Color(0x515442), Color(0x507351), Color(0x2E9568),
        Color(0x6B5777), Color(0x8DEB71), Color(0x1C5B5A), Color(0xA23E27),
        Color(0x42355A), Color(0x989ACA), Color(0x467272), Color(0xBE5CCA),
        Color(0x5EBF72), Color(0xD0589F), Color(0x2AA63A), Color(0x15DEC5),
        Color(0x7D377B), Color(0xCCC8A1), Color(0x83CA6C), Color(0xE28196),
        Color(0x36BDBC), Color(0x7A82A6), Color(0x699023), Color(0xB5E0A4),
        Color(0x673F93), Color(0x167B2B), Color(0xA03A58), Color(0xD0EAC5),
        Color(0x896338), Color(0x389EAA), Color(0x48156C), Color(0x6BBAA1),
        Color(0xBC5C1F), Color(0xAD3E95), Color(0x81CC4C), Color(0x9AD3B3),
        Color(0x224F3E), Color(0xE4E88B), Color(0x4D3815), Color(0x68AF53),
        Color(0xAF4B43), Color(0x368741), Color(0xCCC1E2), Color(0x733518),
        Color(0x212F90), Color(0x52DCBC), Color(0xAA7DD7), Color(0x66DE78),
        Color(0x847280), Color(0x6849B9), Color(0xB4B7D4), Color(0x567415),
        Color(0xC9A66B), Color(0xD77CDD), Color(0x3FC268), Color(0x2D68C7),
        Color(0xA3759E), Color(0x651669), Color(0x708C57), Color(0x3F5AA8),
        Color(0x3B6A20), Color(0x9D42B7), Color(0x99D683), Color(0x894B67),
        Color(0xB32728), Color(0x7FAEEA), Color(0x2A3969), Color(0x49388E),
        Color(0x167DBB), Color(0xC6A2AC), Color(0x9DB46A), Color(0x8AA39D),
        Color(0xE9D29C), Color(0xE55224), Color(0xA0581C), Color(0xE2534A),
        Color(0x4DAE94), Color(0x6E31AC), Color(0x9DC29C), Color(0x711F86),
        Color(0x94721A), Color(0xA1CFCF), Color(0xBF5468), Color(0x307759),
        Color(0x8541A0), Color(0xB1781C), Color(0xE29988), Color(0x3EDBD8),
        Color(0x8C61CD), Color(0x788CE0), Color(0x67A5CA), Color(0x6A9C75),
        Color(0x369BC5), Color(0xC4824A), Color(0x65C03E), Color(0xDE9F44),
        Color(0xE9D0B8), Color(0x8E2283), Color(0x424175), Color(0x507436),
        Color(0x8438BA), Color(0xA07A7C), Color(0xCA6936), Color(0xDB5575),
        Color(0x5B4E62), Color(0xE9807C), Color(0x2CC0A1), Color(0xCECA7B),
        Color(0x569160), Color(0x716FBB), Color(0x36A620), Color(0x9C9152),
        Color(0x4C93E9), Color(0x85E8B7), Color(0xAF6AB8), Color(0x627DE3),
        Color(0x86957F), Color(0x155392), Color(0x54D7EB), Color(0x56A221),
        Color(0x9C6AE4), Color(0x33A27E), Color(0x342C46), Color(0x8B7355),
        Color(0x588C94), Color(0x333B9E), Color(0x8AAFCF), Color(0x691B9E),
        Color(0x326F9C), Color(0x1EAFAE), Color(0x7CE5E6), Color(0xD18F60),
    )

    fun forProfile(profile: ChromaProfile) = when (profile) {
        ChromaProfile.CC4 -> CC4
        ChromaProfile.CC8 -> CC8
    }

    /** Returns the index of the closest palette entry using Euclidean RGB distance. */
    fun closestIndex(argbOrRgb: Int, profile: ChromaProfile): Int {
        val r = (argbOrRgb shr 16) and 0xFF
        val g = (argbOrRgb shr 8) and 0xFF
        val b = argbOrRgb and 0xFF
        return closestIndexRGB(r, g, b, profile)
    }

    fun closestIndexRGB(r: Int, g: Int, b: Int, profile: ChromaProfile): Int {
        val palette = forProfile(profile)
        var best = 0;
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = palette[i].red;
            val pg = palette[i].green;
            val pb = palette[i].blue
            val d = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (d < bestDist) {
                bestDist = d; best = i
            }
        }
        return best
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Reed-Solomon GF(256)
// ══════════════════════════════════════════════════════════════════════════════

internal object RS {
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x; LOG[x] = i
            x = x shl 1; if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) EXP[i] = EXP[i - 255]
    }

    private fun mul(a: Int, b: Int) = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    private fun poly(n: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until n) {
            val f = intArrayOf(1, EXP[i])
            val r = IntArray(g.size + f.size - 1)
            for (j in g.indices) for (k in f.indices) r[j + k] = r[j + k] xor mul(g[j], f[k])
            g = r
        }
        return g
    }

    fun encode(data: ByteArray, nEcc: Int): ByteArray {
        val gen = poly(nEcc)
        val msg = IntArray(data.size + nEcc)
        for (i in data.indices) msg[i] = data[i].toInt() and 0xFF
        for (i in data.indices) {
            val c = msg[i]
            if (c != 0) for (j in 1..gen.lastIndex) msg[i + j] = msg[i + j] xor mul(gen[j], c)
        }
        return ByteArray(nEcc) { msg[data.size + it].toByte() }
    }

    fun verify(payload: ByteArray, eccBytes: ByteArray): Boolean {
        val expected = encode(payload, eccBytes.size)
        return expected.contentEquals(eccBytes)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ChromaCode encoder
// ══════════════════════════════════════════════════════════════════════════════

object ChromaCode {

    val COLOR_QUIET = Color(0x1A1A1A)
    val COLOR_FINDER_OUTER = Color(0x101010)
    val COLOR_FINDER_INNER = Color(0xF0F0F0)
    val COLOR_FINDER_CENTER = Color(0x303030)
    val COLOR_FORMAT_BG = Color(0x282828)

    const val QUIET_CELLS = 3
    const val FINDER_SIZE = 7
    const val MAGIC: Byte = 0xCC.toByte()
    const val VERSION: Byte = 0x02  // v2 adds profile field

    /**
     * Encodes [data] into a ChromaCode [BufferedImage].
     *
     * @param data      Raw binary payload
     * @param profile   Color profile: CC4 (16 colors) or CC8 (256 colors)
     * @param eccRatio  ECC redundancy (0.10..0.40). Default 0.25
     * @param cellSize  Pixels per cell. Default 10
     */
    fun encode(
        data: ByteArray,
        profile: ChromaProfile = ChromaProfile.CC4,
        eccRatio: Double = 0.25,
        cellSize: Int = 10
    ): BufferedImage {
        val nEcc = maxOf(4, (data.size * eccRatio).toInt())
        val header = buildHeader(data.size, nEcc, profile)
        val fullData = header + data
        val eccBytes = RS.encode(fullData, nEcc)
        val payload = fullData + eccBytes

        val symbols = bytesToSymbols(payload, profile)
        val gridCells = computeGridSize(symbols.size, profile)
        val padded = symbols + IntArray(gridCells * gridCells - symbols.size) { 0 }

        return render(padded, gridCells, cellSize, nEcc, profile)
    }

    private fun buildHeader(dataLen: Int, nEcc: Int, profile: ChromaProfile): ByteArray =
        byteArrayOf(
            MAGIC, VERSION,
            profile.nibble.toByte(),                   // profile nibble (0x1=CC4, 0x2=CC8)
            (dataLen shr 8).toByte(), (dataLen and 0xFF).toByte(),
            nEcc.toByte()
        )   // 6 bytes total

    private fun bytesToSymbols(bytes: ByteArray, profile: ChromaProfile): IntArray {
        return when (profile) {
            ChromaProfile.CC4 -> {  // 2 nibbles per byte
                IntArray(bytes.size * 2) { i ->
                    val b = bytes[i / 2].toInt() and 0xFF
                    if (i % 2 == 0) (b shr 4) and 0xF else b and 0xF
                }
            }

            ChromaProfile.CC8 -> {  // 1 byte per symbol (index 0-255)
                IntArray(bytes.size) { i -> bytes[i].toInt() and 0xFF }
            }
        }
    }

    private fun computeGridSize(symbolCount: Int, profile: ChromaProfile): Int {
        val overhead = 4 * (FINDER_SIZE + 1) * (FINDER_SIZE + 1)
        return maxOf(FINDER_SIZE * 2 + 4, ceil(sqrt((symbolCount + overhead).toDouble())).toInt() + 2)
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun render(
        symbols: IntArray,
        gridCells: Int,
        cellSize: Int,
        nEcc: Int,
        profile: ChromaProfile
    ): BufferedImage {
        val totalGrid = gridCells + QUIET_CELLS * 2
        val imgSize = totalGrid * cellSize
        val img = BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_RGB)
        val g2 = img.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

        val off = QUIET_CELLS * cellSize
        g2.color = COLOR_QUIET; g2.fillRect(0, 0, imgSize, imgSize)
        g2.color = Color(0x181818); g2.fillRect(off, off, gridCells * cellSize, gridCells * cellSize)

        fun cellRect(col: Int, row: Int) =
            java.awt.Rectangle(off + col * cellSize, off + row * cellSize, cellSize, cellSize)

        drawFinder(g2, cellRect(0, 0), cellSize)
        drawFinder(g2, cellRect(gridCells - FINDER_SIZE, 0), cellSize)
        drawFinder(g2, cellRect(0, gridCells - FINDER_SIZE), cellSize)
        drawFormatBlock(
            g2,
            cellRect(gridCells - FINDER_SIZE, gridCells - FINDER_SIZE),
            cellSize,
            nEcc,
            gridCells,
            profile
        )

        val occupied = Array(gridCells) { BooleanArray(gridCells) }
        markFinder(occupied, 0, 0)
        markFinder(occupied, gridCells - FINDER_SIZE, 0)
        markFinder(occupied, 0, gridCells - FINDER_SIZE)
        markFormatBlock(occupied, gridCells - FINDER_SIZE, gridCells - FINDER_SIZE)

        for (ar in computeAlignPositions(gridCells)) for (ac in computeAlignPositions(gridCells)) {
            if (!occupied[ar][ac]) {
                drawAlignment(g2, cellRect(ac, ar), cellSize, profile)
                markAlignment(occupied, ac, ar)
            }
        }

        val palette = ChromaPalettes.forProfile(profile)
        var sIdx = 0
        for (row in 0 until gridCells) for (col in 0 until gridCells) {
            if (occupied[row][col]) continue
            val sym = if (sIdx < symbols.size) symbols[sIdx++] else 0
            g2.color = palette[sym]
            val r = cellRect(col, row)
            g2.fillRect(r.x, r.y, r.width, r.height)
        }

        // Subtle grid
        g2.color = Color(0, 0, 0, 30)
        g2.stroke = BasicStroke(0.5f)
        for (i in 0..gridCells) {
            g2.drawLine(off + i * cellSize, off, off + i * cellSize, off + gridCells * cellSize)
            g2.drawLine(off, off + i * cellSize, off + gridCells * cellSize, off + i * cellSize)
        }
        g2.dispose()
        return img
    }

    private fun drawFinder(g2: java.awt.Graphics2D, base: java.awt.Rectangle, cs: Int) {
        for (r in 0 until FINDER_SIZE) for (c in 0 until FINDER_SIZE) {
            g2.color = when {
                r == 0 || r == 6 || c == 0 || c == 6 -> COLOR_FINDER_OUTER
                r == 1 || r == 5 || c == 1 || c == 5 -> COLOR_FINDER_INNER
                else -> COLOR_FINDER_CENTER
            }
            g2.fillRect(base.x + c * cs, base.y + r * cs, cs, cs)
        }
    }

    private fun drawAlignment(
        g2: java.awt.Graphics2D, base: java.awt.Rectangle, cs: Int, profile: ChromaProfile
    ) {
        val outer = ChromaPalettes.forProfile(profile)[if (profile == ChromaProfile.CC4) 7 else 8]
        for (r in 0 until 3) for (c in 0 until 3) {
            g2.color = if (r == 1 && c == 1) Color(0x101010) else outer
            g2.fillRect(base.x + c * cs, base.y + r * cs, cs, cs)
        }
    }

    private fun drawFormatBlock(
        g2: java.awt.Graphics2D, base: java.awt.Rectangle, cs: Int,
        nEcc: Int, gridCells: Int, profile: ChromaProfile
    ) {
        g2.color = COLOR_FORMAT_BG
        g2.fillRect(base.x, base.y, FINDER_SIZE * cs, FINDER_SIZE * cs)
        // Encode: [profile_id, nEcc_hi, nEcc_lo, grid_hi, grid_lo] as 5 colored cells 2×2
        val palette = ChromaPalettes.CC4  // always CC4 for format block (readable by any decoder)
        val cells = intArrayOf(
            profile.nibble,                            // 0x1=CC4, 0x2=CC8 — unambiguous nibble
            (nEcc shr 4) and 0xF, nEcc and 0xF,
            (gridCells shr 4) and 0xF, gridCells and 0xF
        )
        val positions = listOf(Pair(0, 0), Pair(0, 3), Pair(2, 0), Pair(2, 3), Pair(4, 1))
        for ((i, pos) in positions.withIndex()) {
            g2.color = palette[cells[i]]
            g2.fillRect(base.x + pos.second * cs, base.y + pos.first * cs, 2 * cs, 2 * cs)
        }
        g2.color = COLOR_FINDER_OUTER
        g2.stroke = BasicStroke(1.5f)
        g2.drawRect(base.x, base.y, FINDER_SIZE * cs - 1, FINDER_SIZE * cs - 1)
    }

    // ── Occupied mask helpers ──────────────────────────────────────────────────

    internal fun markFinder(occ: Array<BooleanArray>, col: Int, row: Int) {
        for (r in row until row + FINDER_SIZE + 1)
            for (c in col until col + FINDER_SIZE + 1)
                if (r < occ.size && c < occ[0].size) occ[r][c] = true
    }

    internal fun markFormatBlock(occ: Array<BooleanArray>, col: Int, row: Int) {
        for (r in row until row + FINDER_SIZE)
            for (c in col until col + FINDER_SIZE)
                if (r < occ.size && c < occ[0].size) occ[r][c] = true
    }

    internal fun markAlignment(occ: Array<BooleanArray>, col: Int, row: Int) {
        for (r in row until row + 3) for (c in col until col + 3)
            if (r < occ.size && c < occ[0].size) occ[r][c] = true
    }

    internal fun computeAlignPositions(gridCells: Int): List<Int> {
        if (gridCells < 18) return emptyList()
        val result = mutableListOf<Int>();
        var pos = FINDER_SIZE + 3
        while (pos < gridCells - FINDER_SIZE - 3) {
            result.add(pos); pos += 12
        }
        return result
    }

    // ── Decoder ────────────────────────────────────────────────────────────────

    fun decode(img: BufferedImage, cellSize: Int = 10): ByteArray {
        val gridCells = (img.width / cellSize) - QUIET_CELLS * 2
        val off = QUIET_CELLS * cellSize

        val (profile, nEcc, readGrid) = readFormatBlock(img, gridCells, cellSize, off)
        if (readGrid != gridCells) throw ChromaCodeException("Grid mismatch: $readGrid vs $gridCells")

        val occupied = buildOccupiedMask(gridCells)
        val symbols = readSymbols(img, occupied, gridCells, cellSize, off, profile)
        val allBytes = symbolsToBytes(symbols, profile)
        return decodePayload(allBytes, nEcc, profile)
    }

    private fun readFormatBlock(
        img: BufferedImage, gridCells: Int, cs: Int, off: Int
    ): Triple<ChromaProfile, Int, Int> {
        val bx = off + (gridCells - FINDER_SIZE) * cs
        val by = off + (gridCells - FINDER_SIZE) * cs
        // Each colored block is 2×2 cells; sample at center of first cell (offset +cs/2)
        // Positions (row, col) of the top-left cell of each 2×2 block:
        val positions = listOf(Pair(0, 0), Pair(0, 3), Pair(2, 0), Pair(2, 3), Pair(4, 1))
        val cells = IntArray(5) { i ->
            val (r, c) = positions[i]
            val px = bx + c * cs + cs / 2   // center of top-left cell of the 2×2 block
            val py = by + r * cs + cs / 2
            ChromaPalettes.closestIndex(img.getRGB(px, py), ChromaProfile.CC4)
        }
        val profile = ChromaProfile.fromNibble(cells[0])
        val nEcc = (cells[1] shl 4) or cells[2]
        val grid = (cells[3] shl 4) or cells[4]
        return Triple(profile, nEcc, grid)
    }

    private fun buildOccupiedMask(gridCells: Int): Array<BooleanArray> {
        val occ = Array(gridCells) { BooleanArray(gridCells) }
        markFinder(occ, 0, 0); markFinder(occ, gridCells - FINDER_SIZE, 0)
        markFinder(occ, 0, gridCells - FINDER_SIZE)
        markFormatBlock(occ, gridCells - FINDER_SIZE, gridCells - FINDER_SIZE)
        val ap = computeAlignPositions(gridCells)
        for (ar in ap) for (ac in ap) if (!occ[ar][ac]) markAlignment(occ, ac, ar)
        return occ
    }

    private fun readSymbols(
        img: BufferedImage, occupied: Array<BooleanArray>,
        gridCells: Int, cs: Int, off: Int, profile: ChromaProfile
    ): IntArray {
        val result = mutableListOf<Int>()
        val half = cs / 3
        for (row in 0 until gridCells) for (col in 0 until gridCells) {
            if (occupied[row][col]) continue
            val cx = off + col * cs + cs / 2;
            val cy = off + row * cs + cs / 2
            var sumR = 0;
            var sumG = 0;
            var sumB = 0;
            var count = 0
            for (dy in -half..half step maxOf(1, half))
                for (dx in -half..half step maxOf(1, half)) {
                    val px = (cx + dx).coerceIn(0, img.width - 1)
                    val py = (cy + dy).coerceIn(0, img.height - 1)
                    val argb = img.getRGB(px, py)
                    sumR += (argb shr 16) and 0xFF
                    sumG += (argb shr 8) and 0xFF
                    sumB += argb and 0xFF
                    count++
                }
            result.add(ChromaPalettes.closestIndexRGB(sumR / count, sumG / count, sumB / count, profile))
        }
        return result.toIntArray()
    }

    private fun symbolsToBytes(symbols: IntArray, profile: ChromaProfile): ByteArray =
        when (profile) {
            ChromaProfile.CC4 -> ByteArray(symbols.size / 2) { i ->
                ((symbols[i * 2] shl 4) or symbols[i * 2 + 1]).toByte()
            }

            ChromaProfile.CC8 -> ByteArray(symbols.size) { i ->
                symbols[i].toByte()
            }
        }

    private fun decodePayload(allBytes: ByteArray, nEcc: Int, profile: ChromaProfile): ByteArray {
        if (allBytes.size < 6) throw ChromaCodeException("Payload too short")
        if (allBytes[0] != MAGIC) throw ChromaCodeException("Bad magic")
        if (allBytes[1] != VERSION) throw ChromaCodeException(
            "Unsupported version: ${allBytes[1].toInt() and 0xFF} (this decoder supports v${VERSION.toInt() and 0xFF})"
        )
        // Cross-check: profile nibble in header must match what format block told us
        val headerProfileNibble = allBytes[2].toInt() and 0xFF
        if (headerProfileNibble != profile.nibble) throw ChromaCodeException(
            "Profile mismatch: format block says ${profile.label} (nibble=${profile.nibble}) " +
                    "but header says nibble=$headerProfileNibble"
        )
        val dataLen = ((allBytes[3].toInt() and 0xFF) shl 8) or (allBytes[4].toInt() and 0xFF)
        val eccLen = allBytes[5].toInt() and 0xFF
        if (allBytes.size < 6 + dataLen + eccLen) throw ChromaCodeException(
            "Truncated payload: need ${6 + dataLen + eccLen}B, got ${allBytes.size}B"
        )
        val payload = allBytes.copyOf(6 + dataLen)
        val eccBytes = allBytes.copyOfRange(6 + dataLen, 6 + dataLen + eccLen)
        if (!RS.verify(payload, eccBytes)) throw ChromaCodeException("ECC mismatch — rescan")
        return payload.copyOfRange(6, 6 + dataLen)
    }

    class ChromaCodeException(message: String) : Exception(message)
}

// ══════════════════════════════════════════════════════════════════════════════
// Demo
// ══════════════════════════════════════════════════════════════════════════════

fun main() {
    val sizes = listOf(32, 256, 592, 1500, 4000)

    println("=== ChromaCode Profile Recommender ===\n")
    for (n in sizes) {
        val data = ByteArray(n) { it.toByte() }
        val rec = ChromaCodeProfile.recommend(data)
        println("Payload: ${n.toString().padStart(5)}B")
        println("  Profile:  ${rec.profile.label} (${rec.profile.description})")
        println("  cellSize: ${rec.cellSize}px  eccRatio: ${(rec.eccRatio * 100).toInt()}%")
        println("  Image:    ~${rec.estimatedImageSize}×${rec.estimatedImageSize}px")
        println("  Reason:   ${rec.reason}")
        println()
    }

    // Encode/decode round-trip
    println("=== Round-trip test ===\n")
    for (profile in ChromaProfile.entries) {
        val data = ByteArray(64) { (it * 3 + 7).toByte() }
        val img = ChromaCode.encode(data, profile = profile, eccRatio = 0.25, cellSize = 10)
        val decoded = ChromaCode.decode(img, cellSize = 10)
        println(
            "${profile.label}: encode+decode ${if (decoded.contentEquals(data)) "✓ OK" else "✗ FAIL"} " +
                    "(${img.width}×${img.height}px)"
        )
    }
}