package com.crinklecodone.mochibun.data.repository

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
    val categories: List<KaomojiCategory> = listOf(
        KaomojiCategory(
            id = "happy",
            title = "Happy / Cheerful",
            shortLabel = "Happy",
            icon = "˶ˆᗜˆ˵",
            items = listOf(
                "( ˶ˆᗜˆ˵ )",
                "(๑ > ᴗ < ๑)",
                "٩(ˊᗜˋ*)و",
                "(*ˊᗜˋ*)",
                "(✿◠‿◠)",
                "( ˙꒳​˙ )",
                "⸜(｡˃ ᵕ ˂ )⸝",
                "( ´ ▽ ` )ﾉ",
                "＼(＾▽＾)／",
                "(o^▽^o)",
                "(*˘︶˘*)",
                "( ´ ▿ ` )"
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
                "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)",
                "૮꒰ ˶• ༝ •˶꒱ა ♡",
                "( ˘ ³˘)♡",
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
                "(ง •̀_•́)ง",
                "( ꩜ ᯅ ꩜ )",
                "(๑・ω・๑)",
                "₍ᐢ. ̫.ᐢ₎",
                "(=^･ω･^=)",
                "(๑>ᴗ<๑)",
                "( ๑•̀ㅂ•́)و",
                "(=`ω´=)",
                "(｡•́‿•̀｡)"
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
                "(,,>﹏<,,)",
                "( ु⁎ᴗ_ᴗ⁎)ु.zZ",
                "(。-ω-)zzz",
                "(∪｡∪)｡｡zzZ",
                "( -ω-) zzZ",
                "(－_－) zzz",
                "(つд⊂)",
                "(´-ω-`)"
            )
        ),
        KaomojiCategory(
            id = "surprised",
            title = "Surprised / Confused",
            shortLabel = "Surprise",
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
                "(・o・)",
                "(O_O;)",
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
                "(⁄ ⁄•⁄ω⁄•⁄ ⁄)",
                "( ๑॔˃̶◡ ˂̶๑॓)",
                "(¬‿¬)",
                "ദ്ദി(˵ •̀ ᴗ - ˵ )",
                "ԅ(≖‿≖ԅ)",
                "( ੭˙꒳​˙)੭",
                "٩(｡•ω•｡)و",
                "( ˘꒳​˘ )",
                "( ﾟヮﾟ)",
                "(｡◝‿◜｡)",
                "ヽ(>∀<☆)ノ",
                "(ﾉ´ヮ`)ﾉ*: ･ﾟ"
            )
        )
    )
}