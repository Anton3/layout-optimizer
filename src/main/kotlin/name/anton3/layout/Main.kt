@file:Suppress("unused")

package name.anton3.layout

import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

private class Dummy

fun readResource(name: String): String {
    return Dummy::class.java.classLoader.getResource(name)!!.readText()
}

fun makeLayoutConversions(lines: List<String>): Pair<Map<Char, Byte>, Map<Byte, Char>> {
    val layout = lines.joinToString("").toCharArray()
    val layoutToIndex: Map<Char, Byte> = layout.withIndex().associateBy({ it.value }, { it.index.toByte() })
    val layoutFromIndex: Map<Byte, Char> = layout.withIndex().associateBy({ it.index.toByte() }, { it.value })
    return layoutToIndex to layoutFromIndex
}

@Suppress("NOTHING_TO_INLINE")
inline fun bigramIndex(letter1: Byte, letter2: Byte, nLetters: Int): Int {
    return letter1.toInt() * nLetters + letter2
}

@Suppress("NOTHING_TO_INLINE")
inline fun indexToBigram(index: Int, nLetters: Int): Pair<Byte, Byte> {
    return (index / nLetters).toByte() to (index % nLetters).toByte()
}

fun makeSameFingerBigramIndices(fingers: ByteArray): IntArray {
    val result = ArrayList<Int>()

    for (finger in 0..9) {
        val fingerIndices = fingers.withIndex().filter { it.value == finger.toByte() }.map { it.index.toByte() }

        for (idx1 in fingerIndices) {
            for (idx2 in fingerIndices) {
                if (idx1 != idx2) result.add(bigramIndex(idx1, idx2, fingers.size))
            }
        }
    }

    return result.toIntArray()
}

fun makeSameHandBigramIndices(fingers: ByteArray): IntArray {
    val result = ArrayList<Int>()

    for (hand in 0..1) {
        val handIndices = fingers.withIndex().filter { it.value / 5 == hand }.map { it.index.toByte() }

        for (idx1 in handIndices) {
            for (idx2 in handIndices) {
                if (idx1 != idx2) result.add(bigramIndex(idx1, idx2, fingers.size))
            }
        }
    }

    return result.toIntArray()
}

fun makeRollsBigramIndices(fingers: ByteArray): Triple<IntArray, IntArray, IntArray> {
    val inward = ArrayList<Int>()
    val outward = ArrayList<Int>()
    val rowJumps = ArrayList<Int>()

    for ((key1, finger1) in fingers.withIndex()) {
        for ((key2, finger2) in fingers.withIndex()) {

            val leftyComparison = (if (finger1 / 5 == 0) 1 else -1) * finger1.compareTo(finger2)

            val rollType = when {
                finger1 / 5 != finger2 / 5 -> null
                abs(key1 / 10 - key2 / 10) >= 2 -> rowJumps
                abs(finger1 - finger2) != 1 -> null
                leftyComparison < 0 -> inward
                leftyComparison > 0 -> outward
                else -> null
            }

            rollType?.add(bigramIndex(key1.toByte(), key2.toByte(), fingers.size))
        }
    }

    return Triple(inward.toIntArray(), outward.toIntArray(), rowJumps.toIntArray())
}

private const val NULL_INDEX: Byte = -1

fun letterAndBigramWeights(
    corpus: String,
    lowerToIndex: Map<Char, Byte>,
    upperToIndex: Map<Char, Byte>
): Pair<DoubleArray, DoubleArray> {
    val nLetters = lowerToIndex.size

    val letterCounts = IntArray(nLetters)
    val bigramCounts = IntArray(nLetters * nLetters)
    var previousLetter: Byte = NULL_INDEX

    for (c in corpus) {
        if (!c.isLetter()) continue

        val idx = lowerToIndex[c] ?: upperToIndex[c]
        if (idx == null) {
            previousLetter = NULL_INDEX
            continue
        }

        letterCounts[idx.toInt()] += 1
        if (previousLetter != NULL_INDEX) {
            bigramCounts[bigramIndex(previousLetter, idx, nLetters)] += 1
        }

        previousLetter = idx
    }

    val letterTotal = letterCounts.sumByDouble { it.toDouble() }
    val bigramTotal = bigramCounts.sumByDouble { it.toDouble() }

    val letterWeights = DoubleArray(letterCounts.size) { letterCounts[it].toDouble() / letterTotal }
    val bigramWeights = DoubleArray(bigramCounts.size) { bigramCounts[it].toDouble() / bigramTotal }

    return letterWeights to bigramWeights
}

fun wantedBelow(value: Double, threshold: Double) = (value - threshold).coerceAtLeast(0.0) / threshold * 1000

fun wantedAbove(value: Double, threshold: Double) = (threshold - value).coerceAtLeast(0.0) / threshold * 1000

class Energy(
    val key: Double,
    val sameFinger: Double,
    val sameHand: Double,
    val inwardRolls: Double,
    val outwardRolls: Double,
    val rowJumps: Double,
    val rowFrequencies: DoubleArray
) : Comparable<Energy> {

    @Suppress("MemberVisibilityCanBePrivate")
    fun total(): Double = listOf(
        key * 20.0,
        sameHand * 2.3,
        sameFinger * 2.0,
        inwardRolls * -3.0,
        outwardRolls * -0.5,
        rowJumps * 0.0,
        wantedBelow(key, 0.1350) * 0,
        wantedBelow(sameFinger, 0.0330) * 1,
        wantedBelow(sameHand, 0.3300) * 1,
        wantedBelow(rowJumps, 0.0080) * 0,
        wantedAbove(rowFrequencies[1], 0.65) * 0,
        wantedBelow(rowFrequencies[2], 0.030) * 1
    ).sum()

    override fun compareTo(other: Energy): Int = this.total().compareTo(other.total())

    override fun toString(): String =
        String.format(
            Locale.ENGLISH,
            "(key: %.4f, same-finger: %.4f, same-hand: %.4f, inward: %.4f, outward: %.4f, stretched: %.4f)",
            key,
            sameFinger,
            sameHand,
            inwardRolls,
            outwardRolls,
            rowJumps
        )
}

val targetFingerDistribution = listOf(0.04, 0.11, 0.16, 0.19, 0.0, 0.0, 0.19, 0.16, 0.11, 0.04)

class OptimizationWeights(
    val keyWeights: DoubleArray,
    val bigramWeights: DoubleArray,
    val fingerPenalties: DoubleArray,
    val sameFingerBigramIndices: IntArray,
    val sameHandBigramIndices: IntArray,
    val inwardRollsBigramIndices: IntArray,
    val outwardRollsBigramIndices: IntArray,
    val stretchedRollsBigramIndices: IntArray,
    val fingers: ByteArray
)

@Suppress("NOTHING_TO_INLINE")
private inline fun sumBigramFreq(mapping: ByteArray, bigramIndices: IntArray, bigramWeights: DoubleArray): Double {
    val nLetters = mapping.size
    return bigramIndices.sumByDouble { fromBigramIndex ->
        val (from1, from2) = indexToBigram(fromBigramIndex, nLetters)
        val toBigramIndex = bigramIndex(mapping[from1.toInt()], mapping[from2.toInt()], nLetters)
        bigramWeights[toBigramIndex]
    }
}

fun evaluateEnergy(mapping: ByteArray, weights: OptimizationWeights): Energy {
    val keyEnergy = mapping.indices.sumByDouble { from ->
        weights.fingerPenalties[from] * weights.keyWeights[mapping[from].toInt()]
    }
    val sameFingerFreq = sumBigramFreq(mapping, weights.sameFingerBigramIndices, weights.bigramWeights)
    val sameHandFreq = sumBigramFreq(mapping, weights.sameHandBigramIndices, weights.bigramWeights)
    val inwardRollFreq = sumBigramFreq(mapping, weights.inwardRollsBigramIndices, weights.bigramWeights)
    val outwardRollFreq = sumBigramFreq(mapping, weights.outwardRollsBigramIndices, weights.bigramWeights)
    val rowJumpFreq = sumBigramFreq(mapping, weights.stretchedRollsBigramIndices, weights.bigramWeights)
    val rowFreq = mapping.asList()
        .chunked(10) { chars -> chars.sumByDouble { weights.keyWeights[it.toInt()] } }
        .toDoubleArray()

    return Energy(
        key = keyEnergy,
        sameFinger = sameFingerFreq,
        sameHand = sameHandFreq,
        inwardRolls = inwardRollFreq,
        outwardRolls = outwardRollFreq,
        rowJumps = rowJumpFreq,
        rowFrequencies = rowFreq
    )
}

fun layoutAsString(
    mapping: ByteArray,
    lowerFromIndex: Map<Byte, Char>,
    weights: OptimizationWeights
): String {
    val eOriginalIndex = lowerFromIndex.entries.first { it.value == 'e' }.key
    val eRemappedIndex = mapping.indexOf(eOriginalIndex).toByte()
    val shouldFlip = eRemappedIndex % 10 / 5 == 0

    val fingerFreq = mapping.withIndex()
        .groupingBy { weights.fingers[it.index] }
        .fold(0.0) { accumulator: Double, element: IndexedValue<Byte> ->
            accumulator + weights.keyWeights[element.value.toInt()]
        }
        .toList()
        .sortedBy { it.first }
        .map { it.second }
        .let { if (shouldFlip) it.asReversed() else it }
        .joinToString(" ") { String.format(Locale.ENGLISH, "%.1f%%", it * 100) }

    val rowFreq = mapping.toList()
        .chunked(10) { chars -> chars.sumByDouble { weights.keyWeights[it.toInt()] } }
        .joinToString(" ") { String.format(Locale.ENGLISH, "%.1f%%", it * 100) }

    return mapping.asList().chunked(10) { letters ->
        val flipped = if (shouldFlip) letters.reversed() else letters
        flipped.joinToString(" ") { letter ->
            "${lowerFromIndex[letter]}"
        }
    }.plus("$fingerFreq | $rowFreq").joinToString("\n")
}

data class StartingLayout(
    val lower: List<String>,
    val upper: List<String>,
    val movable: List<String>
) {
    init {
        require(lower.map { it.length } == upper.map { it.length })
        require(lower.map { it.length } == movable.map { it.length })
        require(movable.joinToString("").all { it in "01" })
    }
}

@Suppress("SpellCheckingInspection")
val colemakNoDiagLayout = StartingLayout(
    lower = listOf(
        "qwfp,.luyg",
        "arstdhneio",
        "zxcv/;mbkj"
    ),
    upper = listOf(
        "QWFP<>LUYG",
        "ARSTDHNEIO",
        "ZXCV?:MBKJ"
    ),
    movable = listOf(
        "1111001111",
        "1111111111",
        "1111001111"
    )
)

@Suppress("SpellCheckingInspection")
val colemakNoDiagCornersLayout = StartingLayout(
    lower = listOf(
        "jwfp,.luyz",
        "arstdhneio",
        "xgcv/;mbkq"
    ),
    upper = listOf(
        "JWFP<>LUYZ",
        "ARSTDHNEIO",
        "XGCV?:MBKQ"
    ),
    movable = listOf(
        "0111001110",
        "1111111111",
        "0111001110"
    )
)

@Suppress("SpellCheckingInspection")
val colemakNoLowerDiagLayout = StartingLayout(
    lower = listOf(
        "qwfp,.luyg",
        "arstdhneio",
        "zxcv/;mbkj"
    ),
    upper = listOf(
        "QWFP<>LUYG",
        "ARSTDHNEIO",
        "ZXCV?:MBKJ"
    ),
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111001111"
    )
)

//val customLayoutTemplate = """
//v p c f , . y u j q
//r n s h l g t e a o
//w b d m / ; k i x z
//""".trimIndent().split('\n').map { it.replace(" ", "") }

val customLayoutTemplate = """
q w c h v x g u p j
d r s n l o a e t i
, b f m / ; z y k .
""".trimIndent().split('\n').map { it.replace(" ", "") }

@Suppress("SpellCheckingInspection")
val customLayout = StartingLayout(
    lower = customLayoutTemplate,
    upper = customLayoutTemplate.map { it.toUpperCase() },
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111001111"
    )
)

@Suppress("SpellCheckingInspection")
val dvorakLayout = StartingLayout(
    lower = listOf(
        "/,.pyfgcrl",
        "aoeuidhtns",
        ";qjkxbmwvz"
    ),
    upper = listOf(
        "?<>PYFGCRL",
        "AOEUIDHTNS",
        ":QJKXBMWVZ"
    ),
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111111111"
    )
)

@Suppress("SpellCheckingInspection")
val colemakNoBottomRightLayout: StartingLayout = StartingLayout(
    lower = listOf(
        "qwfpgjluyb",
        "arstdhneio",
        "zxcv;/mk,."
    ),
    upper = listOf(
        "QWFPGJLUYB",
        "ARSTDHNEIO",
        "ZXCV:?MK<>"
    ),
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111001100"
    )
)

@Suppress("SpellCheckingInspection")
val colemakLayout: StartingLayout = StartingLayout(
    lower = listOf(
        "qwfpgjluy;",
        "arstdhneio",
        "zxcvbkm,./"
    ),
    upper = listOf(
        "QWFPGJLUY:",
        "ARSTDHNEIO",
        "ZXCVBKM<>?"
    ),
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111111111"
    )
)

@Suppress("SpellCheckingInspection")
val workmanLayout: StartingLayout = StartingLayout(
    lower = listOf(
        "qdrwbjfup;",
        "ashtgyneoi",
        "zxmcvkl,./"
    ),
    upper = listOf(
        "QDRWBJFUP;",
        "ASHTGYNEOI",
        "ZXMCVKL,./"
    ),
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111111111"
    )
)

@Suppress("SpellCheckingInspection")
val oneProduct: StartingLayout = StartingLayout(
    lower = listOf(
        "pldwgjxoyq",
        "nrstmuaeih",
        "zcfvb,.:/k"
    ),
    upper = listOf(
        "PLDWGJXOYQ",
        "NRSTMUAEIH",
        "ZCFVB,.:/K"
    ),
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111111111"
    )
)

@Suppress("SpellCheckingInspection")
val drsnLoaeti: StartingLayout = StartingLayout(
    lower = listOf(
        "qwchvxgupj",
        "drsnloaeti",
        ";bfm,.zyk/"
    ),
    upper = listOf(
        "QWCHVXGUPJ",
        "DRSNLOAETI",
        ":BFM<>ZYK?"
    ),
    movable = listOf(
        "1111111111",
        "1111111111",
        "1111111111"
    )
)

val ergoFingers: List<String> = listOf(
    "0123366789",
    "0123366789",
    "0123366789"
)

val fingerPenaltiesBase: DoubleArray = doubleArrayOf(
    2.7, 2.7, 2.3, 2.3, 2.8, 2.8, 2.3, 2.3, 2.7, 2.7,
    1.0, 0.5, 0.0, 0.0, 2.0, 2.0, 0.0, 0.0, 0.5, 1.0,
    3.5, 3.2, 2.5, 2.5, 3.6, 3.6, 2.5, 2.5, 3.2, 3.5
)

val fingerPenaltiesNoDiag: DoubleArray = doubleArrayOf(
    2.7, 2.7, 2.3, 2.3, 4.5, 4.5, 2.3, 2.3, 2.7, 2.7,
    1.0, 0.5, 0.0, 0.0, 2.5, 2.5, 0.0, 0.0, 0.5, 1.0,
    3.5, 3.2, 2.5, 2.5, 4.5, 4.5, 2.5, 2.5, 3.2, 3.5
)

val fingerPenaltiesNoDiagNoPinkiesOld: DoubleArray = doubleArrayOf(
    3.62, 2.82, 2.02, 2.02, 4.42, 4.41, 2.01, 2.01, 2.81, 3.61,
    1.6, 0.8, 0.0, 0.0, 2.4, 2.4, 0.0, 0.0, 0.8, 1.6,
    3.61, 2.81, 2.01, 2.01, 4.41, 4.40, 2.00, 2.00, 2.80, 3.60
)

val fingerPenaltiesNoDiagNoPinkies: DoubleArray = doubleArrayOf(
    3.61, 2.81, 2.01, 2.01, 4.41, 4.4, 2.0, 2.0, 2.8, 3.6,
    1.61, 0.81, 0.01, 0.01, 2.41, 2.4, 0.0, 0.0, 0.8, 1.6,
    4.01, 3.01, 2.21, 2.21, 8.41, 8.4, 2.2, 2.2, 3.0, 4.0
)

val fingerPenaltiesNoDiagNoPinkiesNew: DoubleArray = doubleArrayOf(
    5.0, 2.8, 2.2, 2.0, 4.4, 4.4, 2.0, 2.2, 2.8, 5.0,
    2.5, 0.8, 0.2, 0.0, 2.4, 2.4, 0.0, 0.2, 0.8, 2.5,
    5.8, 3.1, 2.2, 2.2, 8.4, 8.4, 2.2, 2.2, 3.1, 5.8
)

val fingerPenaltiesNoDiagNoPinkiesNew2: DoubleArray = doubleArrayOf(
    9.0, 2.8, 2.2, 2.0, 4.4, 4.4, 2.0, 2.2, 2.8, 9.0,
    2.5, 0.8, 0.2, 0.0, 2.4, 2.4, 0.0, 0.2, 0.8, 2.5,
    11.0, 3.1, 2.2, 2.2, 8.4, 8.4, 2.2, 2.2, 3.1, 11.0
)

val fingerPenaltiesChords: DoubleArray = doubleArrayOf(
    8.0, 1.6, 1.0, 0.8, 7.0, 7.0, 0.8, 1.0, 1.6, 8.0,
    3.5, 0.8, 0.2, 0.0, 5.0, 5.0, 0.0, 0.2, 0.8, 3.5,
    15.0, 5.4, 4.8, 4.6, 10.0, 10.0, 4.6, 4.8, 5.4, 15.0
)

@Suppress("NOTHING_TO_INLINE")
inline fun ByteArray.swap(idx1: Int, idx2: Int) {
    val temp = this[idx1]
    this[idx1] = this[idx2]
    this[idx2] = temp
}

fun keySwaps(mapping: ByteArray, movable: BooleanArray): Sequence<Unit> = sequence {
    // Swap 1 pair of keys
    for (i in (0 until 30).shuffled()) {
        for (j in (0 until i).shuffled()) {
            if (!movable[i] || !movable[j]) continue
            mapping.swap(i, j)
            yield(Unit)
        }
    }

    // Swap a pair of columns
    for (i in (0 until 10).shuffled()) {
        for (j in (0 until i).shuffled()) {
            if (!movable[i + 0] || !movable[j + 0] ||
                !movable[i + 10] || !movable[j + 10] ||
                !movable[i + 20] || !movable[j + 20]
            ) continue

            mapping.swap(i + 0, j + 0)
            mapping.swap(i + 10, j + 10)
            mapping.swap(i + 20, j + 20)
            yield(Unit)
        }
    }

    // Swap 3 keys cyclically
    for (i in (0 until 30).shuffled()) {
        for (j in (0 until 30).shuffled()) {
            for (k in (0 until 30).shuffled()) {
                if (!movable[i] || !movable[j] || !movable[k]) continue
                mapping.swap(i, j)
                mapping.swap(j, k)
            }
        }
    }

    // Swap 3 columns cyclically
    for (i in (0 until 10).shuffled()) {
        for (j in (0 until 10).shuffled()) {
            for (k in (0 until 10).shuffled()) {
                if (!movable[i + 0] || !movable[j + 0] || !movable[k + 0] ||
                    !movable[i + 10] || !movable[j + 10] || !movable[k + 10] ||
                    !movable[i + 20] || !movable[j + 20] || !movable[k + 20]
                ) continue

                mapping.swap(i + 0, j + 0)
                mapping.swap(i + 10, j + 10)
                mapping.swap(i + 20, j + 20)
                mapping.swap(j + 0, k + 0)
                mapping.swap(j + 10, k + 10)
                mapping.swap(j + 20, k + 20)
            }
        }
    }

    // Swap 2 pairs of keys
    for (i1 in (0 until 30).shuffled()) {
        for (j1 in (0 until i1).shuffled()) {
            if (!movable[i1] || !movable[j1]) continue

            for (i2 in (0 until 30).shuffled()) {
                for (j2 in (0 until i2).shuffled()) {
                    if (!movable[i2] || !movable[j2]) continue
                    mapping.swap(i1, j1)
                    mapping.swap(i2, j2)
                    yield(Unit)
                }
            }
        }
    }

    // Swap 2 pairs of columns
    for (i1 in (0 until 10).shuffled()) {
        for (j1 in (0 until i1).shuffled()) {
            if (!movable[i1 + 0] || !movable[j1 + 0] ||
                !movable[i1 + 10] || !movable[j1 + 10] ||
                !movable[i1 + 20] || !movable[j1 + 20]
            ) continue

            for (i2 in (0 until 10).shuffled()) {
                for (j2 in (0 until i2).shuffled()) {
                    if (!movable[i2 + 0] || !movable[j2 + 0] ||
                        !movable[i2 + 10] || !movable[j2 + 10] ||
                        !movable[i2 + 20] || !movable[j2 + 20]
                    ) continue

                    mapping.swap(i1 + 0, j1 + 0)
                    mapping.swap(i1 + 10, j1 + 10)
                    mapping.swap(i1 + 20, j1 + 20)
                    mapping.swap(i2 + 0, j2 + 0)
                    mapping.swap(i2 + 10, j2 + 10)
                    mapping.swap(i2 + 20, j2 + 20)
                    yield(Unit)
                }
            }
        }
    }

    // Swap a pair of columns, then a pair of keys
    for (i1 in (0 until 10).shuffled()) {
        for (j1 in (0 until i1).shuffled()) {
            if (!movable[i1 + 0] || !movable[j1 + 0] ||
                !movable[i1 + 10] || !movable[j1 + 10] ||
                !movable[i1 + 20] || !movable[j1 + 20]
            ) continue

            for (i2 in (0 until 30).shuffled()) {
                for (j2 in (0 until i2).shuffled()) {
                    if (!movable[i2] || !movable[j2]) continue

                    mapping.swap(i1 + 0, j1 + 0)
                    mapping.swap(i1 + 10, j1 + 10)
                    mapping.swap(i1 + 20, j1 + 20)
                    mapping.swap(i2, j2)
                    yield(Unit)
                }
            }
        }
    }

    // Swap 3 pairs of keys
    @Suppress("ConstantConditionIf")
    if (false) {
        for (i1 in (0 until 30).shuffled()) {
            for (j1 in (0 until i1).shuffled()) {
                if (!movable[i1] || !movable[j1]) continue

                for (i2 in (0..i1).shuffled()) {
                    for (j2 in (0 until i2).shuffled()) {
                        if (!movable[i2] || !movable[j2]) continue

                        for (i3 in (0..i2).shuffled()) {
                            for (j3 in (0 until i3).shuffled()) {
                                if (!movable[i3] || !movable[j3]) continue
                                mapping.swap(i1, j1)
                                mapping.swap(i2, j2)
                                mapping.swap(i3, j3)
                                yield(Unit)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun main() {
    val layout = drsnLoaeti

    val (layoutLowerToIndex, layoutLowerFromIndex) = makeLayoutConversions(layout.lower)
    val (layoutUpperToIndex, _) = makeLayoutConversions(layout.upper)

    check(layoutLowerToIndex.size == layoutUpperToIndex.size)
    val nLetters = layoutLowerToIndex.size

    val movable: BooleanArray = layout.movable.joinToString("").map { it == '1' }.toBooleanArray()
    val movableIndices: ByteArray = movable.withIndex().filter { it.value }.map { it.index.toByte() }.toByteArray()

    val fingers: ByteArray = ergoFingers.joinToString("")
        .map { c -> Character.digit(c, 10).also { check(it != -1) }.toByte() }.toByteArray()

    val sameFingerBigramIndices = makeSameFingerBigramIndices(fingers)
    val sameHandBigramIndices = makeSameHandBigramIndices(fingers)
    val (inwardBigramIndices, outwardBigramIndices, rowJumpIndices) = makeRollsBigramIndices(fingers)

    val fingerPenaltiesRaw = fingerPenaltiesChords
    val fingerPenalties = fingerPenaltiesRaw
//    val fingerPenalties = fingerPenaltiesRaw.map {
//        (it - fingerPenaltiesRaw.min()!!) / (fingerPenaltiesRaw.max()!! - fingerPenaltiesRaw.min()!!)
//    }.toDoubleArray()

    val corpus = readResource("books.txt")

    val (letterWeights, bigramWeights) = letterAndBigramWeights(corpus, layoutLowerToIndex, layoutUpperToIndex)

    val weights = OptimizationWeights(
        letterWeights,
        bigramWeights,
        fingerPenalties,
        sameFingerBigramIndices,
        sameHandBigramIndices,
        inwardBigramIndices,
        outwardBigramIndices,
        rowJumpIndices,
        fingers
    )

    val mapping = ByteArray(nLetters) { it.toByte() }
    var energy = evaluateEnergy(mapping, weights)
    var maxEnergy = energy
    val testMapping = ByteArray(nLetters)

    println(layoutAsString(mapping, layoutLowerFromIndex, weights))
    println("Energy: $energy")
    println()

    var iter = 0

    noReset@ while (true) {
        mapping.copyInto(testMapping)

        for (ks in keySwaps(testMapping, movable)) {
            ++iter
            val testEnergy = evaluateEnergy(testMapping, weights)

            if (testEnergy < energy) {
                testMapping.copyInto(mapping)
                energy = testEnergy

                if (energy < maxEnergy) {
                    maxEnergy = energy
                    println(layoutAsString(mapping, layoutLowerFromIndex, weights))
                    println("Min energy $energy")
                    println()
                }
                continue@noReset
            }

            mapping.copyInto(testMapping)
        }

        println("Final energy $energy")

        val randomMapping = listOf(
            movable.withIndex().filter { !it.value }.map { it.index.toByte() to it.index.toByte() },
            movableIndices.withIndex().shuffled().map { movableIndices[it.index] to it.value }
        ).flatten().sortedBy { it.first }.map { it.second }.toByteArray()

        randomMapping.copyInto(mapping)
        energy = evaluateEnergy(mapping, weights)
        iter = 0
    }
}
