package one.crinklecod.mochibun.data.model

/**
 * Procedural card border styles supported across Mochibun cards, headers, and modal dialogs.
 *
 * When a custom 9-slice `card_border.png` frame is active in `CustomCardBorderManager`, the 9-slice
 * bitmap takes visual precedence; otherwise the active [CardBorderStyle] procedural renderer is used.
 */
enum class CardBorderStyle(val id: String, val label: String, val subtitle: String) {
    SOLID("solid", "Solid", "Clean 1.5dp pastel outline"),
    DOUBLE("double", "Double", "Twin inner & outer keitai frame"),
    LACE("lace", "Lace", "Delicate scalloped lace & pearl dots"),
    STITCH("stitch", "Stitch", "Hand-sewn dashed craft stitching"),
    PIXEL("pixel", "Pixel", "Retro 2-step chamfered pixel corners");

    fun next(): CardBorderStyle = entries[(ordinal + 1) % entries.size]

    fun previous(): CardBorderStyle = entries[(ordinal - 1 + entries.size) % entries.size]

    companion object {
        const val DEFAULT_ID: String = "solid"
        const val DEFAULT_SLICE_PX: Int = 16
        const val MIN_SLICE_PX: Int = 4
        const val MAX_SLICE_PX: Int = 64
        const val MIN_IMAGE_DIM_PX: Int = 16
        const val MAX_IMAGE_DIM_PX: Int = 256

        /**
         * Resolves a [CardBorderStyle] from a raw manifest/YAML/preference string (case-insensitive),
         * safely defaulting to [SOLID] for null, blank, or unrecognized values.
         */
        fun fromId(raw: String?): CardBorderStyle {
            if (raw.isNullOrBlank()) return SOLID
            val trimmed = raw.trim()
            return entries.firstOrNull {
                it.id.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true)
            } ?: SOLID
        }

        /** Canonical lowercase identifier (`"solid"`, `"double"`, `"lace"`, `"stitch"`, `"pixel"`). */
        fun normalizeId(raw: String?): String = fromId(raw).id

        /** Clamps a 9-slice corner inset to the valid `[4, 64]` pixel range. */
        fun clampSlicePx(slicePx: Int): Int = slicePx.coerceIn(MIN_SLICE_PX, MAX_SLICE_PX)
    }
}
