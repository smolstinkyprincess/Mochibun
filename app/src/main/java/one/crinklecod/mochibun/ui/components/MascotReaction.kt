package one.crinklecod.mochibun.ui.components

import androidx.compose.runtime.Immutable

/**
 * Mochibunny animation slots. [defaultRow] is the row in the built-in atlas (and in auto-inferred
 * custom atlases); a custom atlas that declares its slots uses its own rows.
 */
enum class MascotAnimSlot(val defaultRow: Int) {
    IDLE(0), TALK(1), HAPPY(2), HUNGRY(3), SULKY(4), SLEEPY(5), EAT(6), PET(7)
}

/** What she is reacting to, which bubble pool she speaks from, and which slot she plays. */
enum class MascotReactionKind(val slot: MascotAnimSlot) {
    /** Chat: a fresh mood-driven line. */
    CHAT(MascotAnimSlot.TALK),
    /** Feed succeeded. */
    FED(MascotAnimSlot.EAT),
    /** Feed with no treats: she explains how to earn some. */
    NO_TREATS(MascotAnimSlot.TALK),
    /** Pet. */
    PETTED(MascotAnimSlot.PET),
}

/**
 * A one-shot reaction request for the Home mascot. Each new request has a bigger [id];
 * [MochibunnyWithSpeechBubble] plays each id once (and never replays an old one on re-entry).
 */
@Immutable
data class MascotReaction(val id: Int = 0, val kind: MascotReactionKind = MascotReactionKind.CHAT)
