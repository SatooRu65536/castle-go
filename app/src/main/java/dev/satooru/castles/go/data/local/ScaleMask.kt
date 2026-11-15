package dev.satooru.castles.go.data.local

/**
 * 規模（1〜6）の集合を，ビットで表した整数．規模 n は `1 << (n - 1)` のビット．
 * Room の絞り込みの SQL（`((1 << (scale - 1)) & :scaleMask) != 0`）にそのまま渡せる．
 */
object ScaleMask {
    /** 規模 1〜6 のすべて */
    const val ALL = 0b111111

    fun of(scales: Iterable<Int>): Int = scales.fold(0) { mask, s -> if (s in 1..6) mask or bit(s) else mask }

    fun contains(mask: Int, scale: Int): Boolean = scale in 1..6 && mask and bit(scale) != 0

    fun scales(mask: Int): List<Int> = (1..6).filter { contains(mask, it) }

    /** [scale] の表示を切り替える．ただし，すべてが非表示になる切り替えは行わない． */
    fun toggle(mask: Int, scale: Int): Int {
        val next = mask xor bit(scale)
        return if (next and ALL == 0) mask else next and ALL
    }

    private fun bit(scale: Int): Int = 1 shl (scale - 1)
}
