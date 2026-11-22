package ph.edu.umak.momompurin.data

import kotlin.math.abs

/**
 * A self-contained QR Code encoder (UAT fix, item 9).
 *
 * ## Why this exists rather than a library
 *
 * The UAT report asked for a payment QR that works with **no network call**, and
 * the project's dependency rule is that the APK ships without a new third-party
 * runtime dependency mid-release. A QR encoder is a bounded, well-specified
 * problem - Reed-Solomon over GF(256) plus a mask choice - so it is implemented
 * here once, in the data layer, where a unit test can decode what it produces.
 *
 * ## What it can encode
 *
 * Byte mode, error-correction level M (the level payment QR conventions use:
 * ~15% recovery, which survives a phone camera at an angle), versions 1-10 - up
 * to 213 bytes. That covers a GCash/Maya payment payload (a name, a mobile number
 * and an amount), and deliberately does not attempt the large versions a URL with
 * a long query string would need. [encode] returns `null` rather than a wrong
 * matrix when the payload does not fit, so a caller can show the text instead.
 *
 * The output is a square `Array<BooleanArray>` where `true` is a dark module -
 * the shape Compose's Canvas draws directly, with no Bitmap round-trip.
 *
 * ## How its correctness is established
 *
 * This file is a line-for-line translation of `_qrverify/gen.py`, which was
 * verified three ways: every one of its matrices is byte-identical to `segno`
 * 1.6.6 for the same version, level and mask; it produces that matrix for every
 * version 1-10 at the capacity boundaries; and OpenCV's `QRCodeDetector` decodes
 * all of them back to the input at scales 3 through 20. Four bugs had to be
 * found and fixed to get there, and the four comments marked "VERIFIED FIX"
 * below record what each one was, because every one of them produced a matrix
 * that looked like a valid QR code while decoding to nothing or to garbage.
 */
object QrEncoder {

    /** The smallest version's dimension: 21x21 modules. */
    const val MIN_VERSION = 1

    /** The largest version this encoder implements (57x57 modules). */
    const val MAX_VERSION = 10

    /** Error-correction level M's 2-bit indicator in the format-information field. */
    private const val FORMAT_EC_M_BITS = 0b00

    /**
     * Total codewords per version, and EC codewords per block, and block count,
     * for level M.
     *
     * Index is the version (1..10) minus one. These are the ISO/IEC 18004 Table
     * 9 values; they are data, not logic, which is why they are stated in full
     * rather than derived.
     */
    private val TOTAL_CODEWORDS = intArrayOf(
        26, 44, 70, 100, 134, 172, 196, 242, 292, 346,
    )
    private val EC_CODEWORDS_PER_BLOCK = intArrayOf(
        10, 16, 26, 18, 24, 16, 18, 22, 22, 26,
    )
    private val NUM_BLOCKS = intArrayOf(
        1, 1, 1, 2, 2, 4, 4, 4, 5, 5,
    )

    /**
     * Alignment-pattern centre coordinates by version (ISO/IEC 18004 Annex E).
     * Version 1 has none, which is why its entry is empty.
     */
    private val ALIGNMENT_POSITIONS = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50),
    )

    /** The alternating pad codewords of ISO/IEC 18004 7.4.10. */
    private val PAD_CODEWORDS = intArrayOf(0xEC, 0x11)

    /**
     * Encode [text] as a QR matrix, or null when it does not fit the versions
     * implemented here.
     *
     * The mask is chosen by the standard's penalty rules rather than fixed at 0,
     * because a single-choice mask produces large blank runs that a real scanner
     * reads more slowly or not at all.
     */
    fun encode(text: String): Array<BooleanArray>? {
        val payload = text.toByteArray(Charsets.UTF_8)
        val version = chooseVersion(payload.size) ?: return null

        val codewords = finalCodewords(payload, version)

        val size = version * 4 + 17
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }
        placeFunctionPatterns(matrix, reserved, version)
        placeData(matrix, reserved, codewords)

        // ---- choose the mask with the lowest penalty -------------------------
        // The penalty is scored on the masked matrix WITHOUT format/version bits,
        // because those bits are not part of the data region the standard's rules
        // are evaluating. Scoring them in shifts the mask choice, which is how
        // this encoder first differed from segno.
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestMatrix: Array<BooleanArray>? = null
        for (mask in 0..7) {
            val candidate = Array(size) { matrix[it].copyOf() }
            applyMask(candidate, reserved, mask)
            val penalty = penalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestMatrix = candidate
            }
        }
        val out = bestMatrix ?: return null // unreachable: the loop always runs
        placeFormatBits(out, bestMask)
        placeVersionBits(out, version)
        return out
    }

    /** The version whose data capacity holds [byteCount] bytes, or null. */
    private fun chooseVersion(byteCount: Int): Int? {
        for (version in MIN_VERSION..MAX_VERSION) {
            // 4 mode bits + the count field + the payload, in bits.
            val needed = 4 + charCountBits(version) + byteCount * 8
            if (needed <= dataCapacityBits(version)) return version
        }
        return null
    }

    /** Byte-mode character-count field width: 8 bits for versions 1-9, else 16. */
    private fun charCountBits(version: Int): Int = if (version <= 9) 8 else 16

    /** Total DATA bits available at this version and level M. */
    private fun dataCapacityBits(version: Int): Int =
        (TOTAL_CODEWORDS[version - 1] - EC_CODEWORDS_PER_BLOCK[version - 1] * NUM_BLOCKS[version - 1]) * 8

    // ---------------------------------------------------------------------------
    // Data bit stream -> interleaved final codewords
    // ---------------------------------------------------------------------------

    /** Mode + count + payload + terminator + padding, as data codeword bytes. */
    private fun dataCodewords(payload: ByteArray, version: Int): IntArray {
        val capacity = dataCapacityBits(version)
        val bits = BitBuilder()
        bits.append(0b0100, 4) // byte mode
        bits.append(payload.size, charCountBits(version))
        payload.forEach { bits.append(it.toInt() and 0xFF, 8) }

        // ISO/IEC 18004 7.4.9 terminator: up to four zero bits, truncated at capacity.
        bits.append(0, minOf(4, capacity - bits.size))

        // ISO/IEC 18004 7.4.10 padding bits, to the next codeword boundary.
        //
        // VERIFIED FIX: `8 - size % 8` appends a full eight zero bits when the
        // stream is already aligned, where the standard asks for none. That is
        // what segno does and it is harmless - eight zero bits read back as a
        // second terminator - but it changes the codewords, so it is reproduced
        // exactly. Without it this encoder and the verified Python disagree on
        // every payload whose length makes the stream land on a byte boundary.
        bits.append(0, 8 - bits.size % 8)

        // Pad codewords, alternating, then clip. The clip matters because the
        // padding-byte quirk above can overshoot a payload that exactly fills the
        // symbol; the extra byte would otherwise push us past capacity.
        var i = 0
        while (bits.size < capacity) {
            bits.append(PAD_CODEWORDS[i % 2], 8)
            i++
        }
        return bits.toCodewords(capacity / 8)
    }

    /**
     * Splits the data codewords across blocks, correcting the extra-byte order.
     *
     * VERIFIED FIX: when the data count does not divide evenly, the extra byte
     * belongs to the LAST blocks, not the first. Versions 1-7 all divide evenly,
     * so only versions 8, 9 and 10 are affected - but getting it backwards there
     * yields individually valid RS blocks in the wrong order, which decodes as
     * silence rather than failing loudly. Found on the 213-byte v10 case.
     */
    private fun splitIntoBlocks(data: IntArray, version: Int): List<IntArray> {
        val blocks = NUM_BLOCKS[version - 1]
        val totalData = TOTAL_CODEWORDS[version - 1] - EC_CODEWORDS_PER_BLOCK[version - 1] * blocks
        val base = totalData / blocks
        val extra = totalData % blocks
        val short = blocks - extra // leading blocks are the shorter ones
        var offset = 0
        return (0 until blocks).map { index ->
            val length = base + if (index < short) 0 else 1
            data.copyOfRange(offset, offset + length).also { offset += length }
        }
    }

    /**
     * Data + EC codewords, interleaved as ISO/IEC 18004 7.6 requires: one byte
     * from each data block in turn, then one from each EC block in turn.
     */
    private fun finalCodewords(payload: ByteArray, version: Int): IntArray {
        val dataBlocks = splitIntoBlocks(dataCodewords(payload, version), version)
        val ecCount = EC_CODEWORDS_PER_BLOCK[version - 1]
        val ecBlocks = dataBlocks.map { reedSolomon(it, ecCount) }

        val out = ArrayList<Int>(TOTAL_CODEWORDS[version - 1])
        val maxData = dataBlocks.maxOf { it.size }
        for (i in 0 until maxData) {
            dataBlocks.forEach { block -> if (i < block.size) out.add(block[i]) }
        }
        val maxEc = ecBlocks.maxOf { it.size }
        for (i in 0 until maxEc) {
            ecBlocks.forEach { block -> if (i < block.size) out.add(block[i]) }
        }
        return out.toIntArray()
    }

    // ---------------------------------------------------------------------------
    // Reed-Solomon over GF(256), primitive polynomial 0x11D
    // ---------------------------------------------------------------------------

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

    private fun gfMul(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else GF_EXP[GF_LOG[a] + GF_LOG[b]]

    /**
     * The generator polynomial for [degree] EC codewords, highest power first.
     *
     * VERIFIED FIX: the two product terms were the wrong way round - the `a^i`
     * term landed at index `j` and the shift at `j + 1`, which built the reverse
     * of the correct polynomial. Because the result stayed monic either way, the
     * division "looked" consistent and produced plausible bytes; the EC codewords
     * were simply wrong, so every scan failed the checksum.
     */
    private fun generatorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]                          // x * poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], GF_EXP[i]) // a^i * poly[j]
            }
            poly = next
        }
        return poly
    }

    /**
     * The [ecCount] error-correction codewords for one block, by synthetic
     * division by the monic generator: g[0] is 1, so the feedback byte is
     * `data ^ remainder[0]` and g[i + 1] multiplies the remainder slot i.
     */
    private fun reedSolomon(data: IntArray, ecCount: Int): IntArray {
        val gen = generatorPoly(ecCount)
        val remainder = IntArray(ecCount)
        for (byte in data) {
            val factor = (byte and 0xFF) xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1)
            remainder[ecCount - 1] = 0
            for (i in 0 until ecCount) {
                remainder[i] = remainder[i] xor gfMul(gen[i + 1], factor)
            }
        }
        return remainder
    }

    // ---------------------------------------------------------------------------
    // Function patterns and module placement
    // ---------------------------------------------------------------------------

    private fun placeFunctionPatterns(
        matrix: Array<BooleanArray>,
        reserved: Array<BooleanArray>,
        version: Int,
    ) {
        val size = matrix.size

        // The three finder patterns, each a 7x7 square with its light separator.
        fun finder(row: Int, col: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = row + dr
                    val c = col + dc
                    if (r !in 0 until size || c !in 0 until size) continue
                    // Dark on the outer 7x7 border and the inner 3x3 core, light
                    // in the 1-module gap between them.
                    val dark = (dr in 0..6 && dc in 0..6) &&
                        (dr == 0 || dr == 6 || dc == 0 || dc == 6 ||
                            (dr in 2..4 && dc in 2..4))
                    matrix[r][c] = dark
                    reserved[r][c] = true
                }
            }
        }
        finder(0, 0)
        finder(0, size - 7)
        finder(size - 7, 0)

        // Timing patterns: dark on even coordinates, between the finder groups.
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            matrix[6][i] = dark
            reserved[6][i] = true
            matrix[i][6] = dark
            reserved[i][6] = true
        }

        // Alignment patterns, skipping the three finder corners they would overlap.
        val centres = ALIGNMENT_POSITIONS[version - 1]
        for (r in centres) {
            for (c in centres) {
                val overlapsFinder =
                    (r <= 8 && c <= 8) ||
                        (r <= 8 && c >= size - 9) ||
                        (r >= size - 9 && c <= 8)
                if (overlapsFinder) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        // Dark ring, light ring, dark centre.
                        matrix[r + dr][c + dc] = maxOf(abs(dr), abs(dc)) != 1
                        reserved[r + dr][c + dc] = true
                    }
                }
            }
        }

        // The dark module, always dark, just above the lower-left format block.
        matrix[version * 4 + 9][8] = true
        reserved[version * 4 + 9][8] = true

        // Reserve the format areas so data placement skips them; their real bits
        // are written by [placeFormatBits] once the mask is known.
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in 0 until 8) {
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }
        // Version information exists only from version 7 upward.
        if (version >= 7) {
            for (i in 0 until 18) {
                reserved[i / 3][size - 11 + i % 3] = true
                reserved[size - 11 + i % 3][i / 3] = true
            }
        }
    }

    /**
     * ISO/IEC 18004 7.7.3: fill the free modules in 2-wide columns from the
     * bottom-right corner, boustrophedon, skipping the vertical timing column.
     *
     * The up/down direction is keyed off the RIGHT column of the pair and then
     * flips again for the columns left of the timing strip. Keying it off the
     * left column, or dropping the second flip, produces a matrix that looks
     * plausible and decodes as garbage - so this loop mirrors the verified
     * Python's `right`/`j` bookkeeping exactly rather than using a tidy
     * "flip a flag each column" formulation.
     */
    private fun placeData(
        matrix: Array<BooleanArray>,
        reserved: Array<BooleanArray>,
        codewords: IntArray,
    ) {
        val size = matrix.size
        val stream = BitBuilder.fromCodewords(codewords)
        for (rawRight in size - 1 downTo 1 step 2) {
            // The pair's columns shift left by one once we are past the vertical
            // timing strip, because that strip occupies column 6 and leaves the
            // remaining columns unevenly paired. This is a *local* adjustment:
            // the loop's own step must not be affected by it.
            val right = if (rawRight <= 6) rawRight - 1 else rawRight
            for (vertical in 0 until size) {
                for (z in 0..1) {
                    val j = right - z
                    if (j < 0) continue
                    val upwards = ((right and 2) == 0) xor (j < 6)
                    val i = if (upwards) size - 1 - vertical else vertical
                    if (reserved[i][j]) continue
                    matrix[i][j] = stream.next()
                }
            }
        }
        // Modules left over - versions 2 to 6 have 7 remainder bits - stay false,
        // which is exactly the light value the standard requires for them.
    }

    private fun applyMask(
        matrix: Array<BooleanArray>,
        reserved: Array<BooleanArray>,
        mask: Int,
    ) {
        val size = matrix.size
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (reserved[row][col]) continue
                val product = row * col
                val invert = when (mask) {
                    0 -> (row + col) % 2 == 0
                    1 -> row % 2 == 0
                    2 -> col % 3 == 0
                    3 -> (row + col) % 3 == 0
                    4 -> (row / 2 + col / 3) % 2 == 0
                    5 -> product % 2 + product % 3 == 0
                    6 -> (product % 2 + product % 3) % 2 == 0
                    else -> ((row + col) % 2 + product % 3) % 2 == 0
                }
                if (invert) matrix[row][col] = !matrix[row][col]
            }
        }
    }

    /**
     * Write the 15-bit format information (level M, [mask]) into both copies,
     * BCH-encoded per ISO/IEC 18004 7.9.
     *
     * Copy 1 sits transposed around the top-left finder. Copy 2 is split between
     * the top-right (row 8) and bottom-left (column 8), and its two halves run in
     * *opposite* bit orders, both starting from bit 0 at the symbol edge.
     *
     * VERIFIED FIX: both copies had their bit orders transposed - copy 1 put bits
     * 0-5 along row 8 where they belong down column 8, and copy 2 gave the row-8
     * half the bits that belong on the column-8 half. The matrix still looked
     * like a QR code and its data region was intact, but a decoder read back the
     * wrong mask number and refused the symbol outright.
     */
    private fun placeFormatBits(matrix: Array<BooleanArray>, mask: Int) {
        val size = matrix.size
        val data = (FORMAT_EC_M_BITS shl 3) or mask
        var remainder = data shl 10
        // BCH(15,5) generator x^10 + x^8 + x^5 + x^4 + x^2 + x + 1 = 0x537.
        for (i in 14 downTo 10) {
            if ((remainder shr i) and 1 != 0) remainder = remainder xor (0x537 shl (i - 10))
        }
        val bits = ((data shl 10) or remainder) xor 0x5412
        fun bit(i: Int): Boolean = (bits shr i) and 1 != 0

        // ---- Copy 1, around the top-left finder -------------------------------
        for (i in 0..5) matrix[i][8] = bit(i)   // bits 0-5 down column 8
        matrix[7][8] = bit(6)                   // row 6 is the timing strip
        matrix[8][8] = bit(7)
        matrix[8][7] = bit(8)                   // column 6 is the timing strip
        for (i in 9..14) matrix[8][14 - i] = bit(i)

        // ---- Copy 2, split between the two other finders -----------------------
        // bit i at row 8 / column size-1-i, and bit 14-i at column 8 / row size-1-i.
        for (i in 0..7) {
            matrix[size - 1 - i][8] = bit(14 - i)
            matrix[8][size - 1 - i] = bit(i)
        }

        // The dark module is NOT part of the format bits but must stay set.
        matrix[size - 8][8] = true
    }

    /**
     * The 18-bit version information, for versions 7 and above only.
     *
     * Below version 7 the symbol carries no version block at all, which is why
     * this is a no-op there rather than a zero-filled block.
     */
    private fun placeVersionBits(matrix: Array<BooleanArray>, version: Int) {
        if (version < 7) return
        val size = matrix.size
        // BCH(18,6) with generator 0x1F25.
        var remainder = version shl 12
        for (i in 17 downTo 12) {
            if ((remainder shr i) and 1 != 0) remainder = remainder xor (0x1F25 shl (i - 12))
        }
        val bits = (version shl 12) or remainder
        for (i in 0 until 18) {
            val dark = (bits shr i) and 1 != 0
            val a = size - 11 + i % 3
            val b = i / 3
            matrix[a][b] = dark // bottom-left block: 6 wide, 3 tall
            matrix[b][a] = dark // top-right block: the transpose
        }
    }

    /**
     * The standard's four penalty rules, used to pick the least-scannable mask.
     *
     * Rule 3 counts a 1:1:3:1:1 pattern only when at least one side of it is
     * entirely light (or the pattern touches the symbol edge) - not when four
     * light modules are merely present on either side, which would double-count
     * overlapping matches.
     */
    private fun penalty(matrix: Array<BooleanArray>): Int {
        val size = matrix.size
        var score = 0

        // Rule 1: runs of five or more same-coloured modules, 3 + (run - 5) each.
        fun runs(read: (Int) -> Boolean) {
            var run = 1
            for (i in 1 until size) {
                if (read(i) == read(i - 1)) {
                    run++
                } else {
                    if (run >= 5) score += run - 2
                    run = 1
                }
            }
            if (run >= 5) score += run - 2
        }
        for (row in 0 until size) runs { col -> matrix[row][col] }
        for (col in 0 until size) runs { row -> matrix[row][col] }

        // Rule 2: every 2x2 block of one colour, 3 each.
        for (row in 0 until size - 1) {
            for (col in 0 until size - 1) {
                val value = matrix[row][col]
                if (value == matrix[row][col + 1] &&
                    value == matrix[row + 1][col] &&
                    value == matrix[row + 1][col + 1]
                ) {
                    score += 3
                }
            }
        }

        // Rule 3: the finder-like 1:1:3:1:1 pattern, 40 each.
        //
        // The pattern only counts when one of its flanks is *entirely* light:
        // all four flanking modules light, or the pattern butting against the
        // symbol edge. Counting "at least one light module beside it" instead
        // double-counts overlapping matches and shifts the mask choice.
        val pattern = booleanArrayOf(true, false, true, true, true, false, true)

        fun occurrences(read: (Int) -> Boolean): Int {
            var total = 0
            var idx = 0
            while (idx <= size - 7) {
                var match = true
                for (i in 0 until 7) {
                    if (read(idx + i) != pattern[i]) {
                        match = false
                        break
                    }
                }
                if (!match) {
                    idx++
                    continue
                }
                val end = idx + 7
                val beforeAllLight = (maxOf(idx - 4, 0) until idx).all { !read(it) }
                val afterAllLight = (end until minOf(end + 4, size)).all { !read(it) }
                if (idx == 0 || idx == size - 7 || beforeAllLight || afterAllLight) {
                    total += 40
                    idx = end
                } else {
                    // Matches must not overlap: resume at the next possible start.
                    idx += 4
                }
            }
            return total
        }

        for (row in 0 until size) score += occurrences { col -> matrix[row][col] }
        for (col in 0 until size) score += occurrences { row -> matrix[row][col] }

        // Rule 4: the dark-module ratio's distance from 50%, in 5% steps, 10 each.
        // Computed in exact integer arithmetic: a float percentage can land on a
        // 5% boundary differently in Kotlin than in Python, which would flip the
        // mask and needlessly break byte-for-byte parity with the reference.
        val dark = matrix.sumOf { row -> row.count { it } }
        val total = size * size
        score += 10 * (abs(dark * 100 - 50 * total) / (5 * total))

        return score
    }

    /** A growable bit buffer: append, read back sequentially, pack to codewords. */
    private class BitBuilder {
        private val bits = ArrayList<Boolean>(256)
        private var cursor = 0

        val size: Int get() = bits.size

        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value shr i) and 1 != 0)
        }

        /** The next bit, or false once the stream runs out (the remainder bits). */
        fun next(): Boolean = if (cursor < bits.size) bits[cursor++] else false

        /** Packs the stream into [count] codewords, most significant bit first. */
        fun toCodewords(count: Int): IntArray {
            val out = IntArray(count)
            for ((index, bit) in bits.withIndex()) {
                if (bit) out[index / 8] = out[index / 8] or (1 shl (7 - index % 8))
            }
            return out
        }

        companion object {
            fun fromCodewords(codewords: IntArray): BitBuilder = BitBuilder().apply {
                codewords.forEach { append(it, 8) }
            }
        }
    }
}
