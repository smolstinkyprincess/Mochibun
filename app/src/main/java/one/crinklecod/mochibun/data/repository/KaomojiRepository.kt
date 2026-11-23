package one.crinklecod.mochibun.data.repository

/**
 * Data model representing a categorized collection of Japanese kaomoji by mood.
 */
data class KaomojiCategory(
    val id: String,
    val title: String,
    val shortLabel: String,
    val icon: String,
    val items: List<String>
)

/**
 * In-memory repository providing organized Japanese kaomoji for the physical keypad radial wheel.
 * Curated specifically for the AT-M120 (Android 12) font fallback stack to prevent 'tofu' (□) boxes.
 */
object KaomojiRepository {
    const val MAX_ITEMS_PER_CATEGORY = 10

    // Ordered clockwise from 12:00 North for the 8-wedge compass wheel
    val categories: List<KaomojiCategory> = listOf(
        KaomojiCategory(
            id = "happy",
            title = "Happy / Cheerful",
            shortLabel = "Happy",
            icon = "˶ˆᗜˆ˵",
            items = listOf(
                "( ˶ˆᗜˆ˵ )",
                "(๑ > ᴗ < ๑)",
                "(*ˊᗜˋ*)",
                "(✿◠‿◠)",
                "( ˙꒳​˙ )",
                "⸜(｡˃ ᵕ ˂ )⸝",
                "( ´ ▽ ` )ﾉ",
                "(o^▽^o)",
                "(*˘︶˘*)",
                "( ´ ▿ ` )"
            )
        ),
        KaomojiCategory(
            id = "hype",
            title = "Hype / Sparkle",
            shortLabel = "Hype",
            icon = "٩(ˊᗜˋ*)و",
            items = listOf(
                "٩(ˊᗜˋ*)و",
                "＼(＾▽＾)／",
                "(ง •̀_•́)ง",
                "( ๑•̀ㅂ•́)و",
                "٩(｡•ω•｡)و",
                "ヽ(>∀<☆)ノ",
                "(ﾉ´ヮ`)ﾉ*: ･ﾟ",
                "✧٩(ˊωˋ*)و✧",
                "ヽ(・∀・)ﾉ",
                "(★^O^★)"
            )
        ),
        KaomojiCategory(
            id = "love",
            title = "Love / Affection",
            shortLabel = "Love",
            icon = "˘ ³˘♥",
            items = listOf(
                "( ˘ ³˘)♥",
                "(づ๑•ᴗ•๑)づ",
                "(っ´∀｀)っ",
                "(♡´͈ ᵕ `͈ )",
                "૮꒰ ˶• ༝ •˶꒱ა ♡",
                "(人´3`)⌒♡",
                "(´｡• ᵕ •｡`) ♡",
                "(♡ˊ͈ ꒳ ˋ͈)",
                "(*♡∀♡)",
                "╰(⸝⸝⸝´꒳`⸝⸝⸝)╯"
            )
        ),
        KaomojiCategory(
            id = "cute",
            title = "Cute / Kawaii",
            shortLabel = "Cute",
            icon = "₍ ˶•⤙•˶ ₎",
            items = listOf(
                "(｡♥‿♥｡)",
                "( ˵ •̀ ᴗ - ˵ ) ✧",
                "૮₍ ˶•⤙•˶ ₎ა",
                "( ꩜ ᯅ ꩜ )",
                "(๑・ω・๑)",
                "₍ᐢ. ̫.ᐢ₎",
                "(=^･ω･^=)",
                "(=`ω´=)",
                "(｡•́‿•̀｡)",
                "(｡･ω･｡)ﾉ♡"
            )
        ),
        KaomojiCategory(
            id = "sleepy",
            title = "Sleepy / Eepy",
            shortLabel = "Sleepy",
            icon = "ᴗ͈ˬᴗ͈",
            items = listOf(
                "( ᴗ͈ˬᴗ͈ )zzZ",
                "( -_・)ヾ",
                "(つω`*)",
                "( ´･ω･)",
                "( ु⁎ᴗ_ᴗ⁎)ु.zZ",
                "(。-ω-)zzz",
                "(∪｡∪)｡｡zzZ",
                "( -ω-) zzZ",
                "(－_－) zzz",
                "(´-ω-`)"
            )
        ),
        KaomojiCategory(
            id = "shy",
            title = "Shy / Blush",
            shortLabel = "Shy",
            icon = "⁄ ⁄•⁄ω⁄•⁄ ⁄",
            items = listOf(
                "(⁄ ⁄•⁄ω⁄•⁄ ⁄)",
                "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)",
                "(,,>﹏<,,)",
                "(つд⊂)",
                "(*/ω＼)",
                "(⁄ ⁄•⁄-⁄•⁄ ⁄)",
                "( ◡‿◡ *)",
                "(〃▽〃)",
                "(´∩｡• ᵕ •｡∩`)",
                "(//▽//)"
            )
        ),
        KaomojiCategory(
            id = "surprised",
            title = "Surprised / Confused",
            shortLabel = "Shock",
            icon = "•ิ_•ิ?",
            items = listOf(
                "( °o° )",
                "(⊙_⊙)",
                "(•ิ_•ิ)?",
                "( ⚆ _ ⚆ )",
                "(°ロ°) !",
                "( ; ﾟДﾟ)",
                "(・・?)",
                "(゜Д゜;)",
                "Σ(ﾟДﾟ)",
                "(・∀・;)?"
            )
        ),
        KaomojiCategory(
            id = "silly",
            title = "Silly / Playful",
            shortLabel = "Silly",
            icon = "¬‿¬",
            items = listOf(
                "( ๑॔˃̶◡ ˂̶๑॓)",
                "(¬‿¬)",
                "ദ്ദി(˵ •̀ ᴗ - ˵ )",
                "ԅ(≖‿≖ԅ)",
                "( ੭˙꒳​˙)੭",
                "( ˘꒳​˘ )",
                "( ﾟヮﾟ)",
                "(｡◝‿◜｡)",
                "¯\\_(ツ)_/¯",
                "(・ω<)☆"
            )
        )
    )
}