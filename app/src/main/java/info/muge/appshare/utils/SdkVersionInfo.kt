package info.muge.appshare.utils

/**
 * targetSdkVersion 对应的展示颜色，参考 SDK Monitor 配色方案：
 * 越老的 targetSdk 越红（意味着适配滞后），越新越绿。
 */
fun Int.apiToColor(): Long =
    when (this) {
        in 0..32 -> 0xFFD31B33 // red
        33 -> 0xFFE54B4B // red-orange
        34 -> 0xFFE37A46 // orange
        35 -> 0xFF178E96 // blue-green
        else -> 0xFF14B572 // green
    }

/**
 * API Level 对应的 Android 版本代号/名称，用于展示在SDK标签副标题上
 */
fun Int.apiToVersion(): String =
    when (this) {
        3 -> "Cupcake"
        4 -> "Donut"
        5, 6, 7 -> "Eclair"
        8 -> "Froyo"
        9, 10 -> "Gingerbread"
        11, 12, 13 -> "Honeycomb"
        14, 15 -> "Ice Cream Sandwich"
        16, 17, 18 -> "Jelly Bean"
        19, 20 -> "KitKat"
        21, 22 -> "Lollipop"
        23 -> "Marshmallow"
        24, 25 -> "Nougat"
        26, 27 -> "Oreo"
        28 -> "Pie"
        29, 30, 31 -> "Android ${this - 19}"
        32 -> "Android 12L"
        else -> "Android ${this - 20}"
    }
