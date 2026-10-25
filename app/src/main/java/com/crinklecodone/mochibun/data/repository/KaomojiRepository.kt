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
                "(✿◠‿◠)"
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
                "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)"
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
                "( ꩜ ᯅ ꩜ )"
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
                "(,,>﹏<,,)"
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
                "(°ロ°) !"
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
                "ദ്ദി(˵ •̀ ᴗ - ˵ )"
            )
        )
    )
}
