package name.anton3.layout

import java.util.*
import kotlin.collections.HashMap

fun main() {
    val counts = HashMap<Char, Int>()

    while (true) {
        val line = readLine()!!
        if (line == "<STOP>") break

        for (c in line) {
            val lower = c.toLowerCase()
            if (lower in 'a'..'z') counts[lower] = (counts[lower] ?: 0) + 1
        }
    }

    val sum = counts.values.sum().toDouble()
    counts.toList()
        .map { it.first to it.second.toDouble() / sum * 100.0 }
        .sortedByDescending { it.second }
        .forEach { println("%c  %.2f%%".format(Locale.ENGLISH, it.first, it.second)) }
}
