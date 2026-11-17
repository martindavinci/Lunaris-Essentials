package io.github.martindavinci.lunaris.archives.browse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NaturalOrderTest {
    @Test fun digitRunsCompareAsNumbers() {
        assertThat(listOf("file10.txt", "file2.txt", "File1.txt").sortedWith(NaturalOrder))
            .containsExactly("File1.txt", "file2.txt", "file10.txt").inOrder()
    }
    @Test fun caseIsIgnoredThenUsedAsTieBreak() {
        assertThat(listOf("b", "B", "a").sortedWith(NaturalOrder)).containsExactly("a", "B", "b").inOrder()
    }
    @Test fun leadingZerosAndHugeNumbers() {
        assertThat(listOf("a0010", "a9", "a99999999999999999999").sortedWith(NaturalOrder))
            .containsExactly("a9", "a0010", "a99999999999999999999").inOrder()
    }
    @Test fun precomputedKeys_giveTheSameOrder() {
        val names = listOf("file10.txt", "b", "B", "file2.txt", "a0010", "a9")
        val byKeys = names.map { it to NaturalOrder.key(it) }
            .sortedWith { x, y -> NaturalOrder.compare(x.first, x.second, y.first, y.second) }.map { it.first }
        assertThat(byKeys).isEqualTo(names.sortedWith(NaturalOrder))
    }
    @Test fun namesThatMixDigitScripts_sortWithoutThrowing() {
        val kanji = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        val random = java.util.Random(1)
        repeat(2000) {
            val names = (1..60).map { n ->
                val number = when (random.nextInt(3)) {
                    0 -> n.toString()
                    1 -> n.toString().map { '０' + (it - '0') }.joinToString("")
                    else -> kanji[n % 10]
                }
                "第${number}話_$n.txt"
            }
            names.sortedWith(NaturalOrder)
        }
    }
    @Test fun aFullWidthDigit_isAnOrdinaryCharacter() {
        assertThat(listOf("第１章", "第二章", "第10章").sortedWith(NaturalOrder)).containsExactly("第10章", "第二章", "第１章").inOrder()
    }
}
