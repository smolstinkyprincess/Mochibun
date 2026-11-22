#!/usr/bin/env python3
"""
Build-time generator for Mochibun's offline English T9 dictionary (app/src/main/assets/t9/en.tsv).

Inputs (tools/input/ is gitignored, not shipped):
  tools/input/en_50k.txt       FrequencyWords en_50k (`word count` per line)
  tools/input/scowl.txt        SCOWL american-english-large (one word per line)
  tools/t9_extra_words.txt     Committed extra texting/everyday words (one word per line)
  tools/input/extra_words.txt  Optional local extra words (one word per line)
"""

from __future__ import annotations

import os
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INPUT_DIR = ROOT / "tools" / "input"
EN_50K_PATH = INPUT_DIR / "en_50k.txt"
SCOWL_PATH = INPUT_DIR / "scowl.txt"
COMMITTED_EXTRA_WORDS_PATH = ROOT / "tools" / "t9_extra_words.txt"
LOCAL_EXTRA_WORDS_PATH = INPUT_DIR / "extra_words.txt"
OUTPUT_PATH = ROOT / "app" / "src" / "main" / "assets" / "t9" / "en.tsv"

TOP_WORD_LIMIT = 15_000
MAX_WORD_LEN = 32
TOKEN_RE = re.compile(r"^[a-z']+$")

CONTRACTIONS: tuple[str, ...] = (
    "don't", "doesn't", "didn't", "can't", "won't", "isn't", "aren't", "wasn't",
    "weren't", "hasn't", "haven't", "hadn't", "wouldn't", "couldn't", "shouldn't", "ain't",
    "i'm", "i'll", "i've", "i'd",
    "you're", "you'll", "you've", "you'd",
    "he's", "she's", "it's", "that's", "what's", "there's", "here's", "let's", "who's", "where's",
    "we're", "we'll", "we've",
    "they're", "they'll", "they've",
)
CONTRACTION_SET = frozenset(CONTRACTIONS)

# Split-prefix tokens in en_50k that uniquely correspond to their n't contraction
# (excludes "can" and "won", which are also high-frequency standalone words).
DIRECT_NT_SPLITS: dict[str, str] = {
    "don't": "don",
    "doesn't": "doesn",
    "didn't": "didn",
    "isn't": "isn",
    "aren't": "aren",
    "wasn't": "wasn",
    "weren't": "weren",
    "hasn't": "hasn",
    "haven't": "haven",
    "hadn't": "hadn",
    "wouldn't": "wouldn",
    "couldn't": "couldn",
    "shouldn't": "shouldn",
    "ain't": "ain",
}

MANUAL_MAP = {
    "ß": "ss",
    "æ": "ae",
    "œ": "oe",
    "ø": "o",
    "ł": "l",
    "đ": "d",
}

CHAR_TO_DIGIT = {
    "a": "2", "b": "2", "c": "2",
    "d": "3", "e": "3", "f": "3",
    "g": "4", "h": "4", "i": "4",
    "j": "5", "k": "5", "l": "5",
    "m": "6", "n": "6", "o": "6",
    "p": "7", "q": "7", "r": "7", "s": "7",
    "t": "8", "u": "8", "v": "8",
    "w": "9", "x": "9", "y": "9", "z": "9",
}


def t9_signature(word: str) -> str | None:
    """Matches one.crinklecod.mochibun.data.ime.T9Keymap.signature."""
    if not word:
        return None
    normalized = unicodedata.normalize("NFD", word.lower())
    out: list[str] = []
    for ch in normalized:
        if unicodedata.category(ch) in ("Mn", "Mc", "Me"):
            continue
        if ch in MANUAL_MAP:
            for mapped in MANUAL_MAP[ch]:
                out.append(CHAR_TO_DIGIT[mapped])
            continue
        if ch in ("'", "-"):
            continue
        digit = CHAR_TO_DIGIT.get(ch)
        if digit is None:
            return None
        out.append(digit)
    return "".join(out) if out else None


def load_scowl(path: Path) -> set[str]:
    words: set[str] = set()
    with path.open("r", encoding="utf-8", errors="replace") as f:
        for raw in f:
            w = raw.strip().lower()
            if not w:
                continue
            # Ignore possessive "'s" entries, but always keep explicit "'s" contractions.
            if w.endswith("'s") and w not in CONTRACTION_SET:
                continue
            words.add(w)
    return words


def load_en_50k(path: Path) -> list[tuple[str, int]]:
    counts: dict[str, int] = {}
    with path.open("r", encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = raw.rstrip("\r\n")
            parts = line.split(" ")
            if len(parts) != 2 or not parts[0] or not parts[1].isdigit():
                continue
            word = parts[0].lower()
            freq = int(parts[1])
            prev = counts.get(word)
            if prev is None or freq > prev:
                counts[word] = freq
    return sorted(counts.items(), key=lambda item: (-item[1], item[0]))


def synthesize_contraction_freq(
    word: str,
    freq_lookup: dict[str, int],
    fallback_freq: int,
) -> tuple[int, str]:
    """
    FrequencyWeights (en_50k) tokenizes contractions on apostrophes (e.g. "don't" -> "don" + "'t",
    "i'm" -> "i" + "'m"), so whole contractions are absent from en_50k.txt.

    Synthesis formula:
      1. If the whole contraction exists in en_50k, use it directly.
      2. "i'm": use en_50k["'m"] directly ("'m" only comes from "I'm").
      3. Direct n't split stems (DIRECT_NT_SPLITS, e.g. "didn't" -> "didn", "don't" -> "don"):
         use the split left-token count directly.
      4. Remaining contractions ("can't", "won't", and "'s"/"'re"/"'ll"/"'ve"/"'d" forms):
         take 10% of the stem word's en_50k count (using "will" as the stem for "won't"),
         capped by the split suffix token's en_50k count (e.g. "'t", "'s", "'re", "'ll", "'ve",
         "'d") so a single contraction never exceeds its suffix's corpus total.
    """
    direct = freq_lookup.get(word)
    if direct is not None:
        return direct, "en_50k direct"

    if word == "i'm":
        m_count = freq_lookup.get("'m", fallback_freq)
        return m_count, "split token \"'m\""

    split_stem = DIRECT_NT_SPLITS.get(word)
    if split_stem is not None and split_stem in freq_lookup:
        return freq_lookup[split_stem], f"split token \"{split_stem}\""

    stem, suffix = word.split("'", 1)
    stem_key = "will" if word == "won't" else stem
    stem_freq = freq_lookup.get(stem_key, fallback_freq)
    suffix_token = "'" + suffix
    suffix_cap = freq_lookup.get(suffix_token, stem_freq)
    synth = max(fallback_freq, min(max(1, stem_freq // 10), suffix_cap))
    return synth, f"0.10*\"{stem_key}\" (cap \"{suffix_token}\")"


def load_extra_words(path: Path) -> list[str]:
    if not path.is_file():
        return []
    words: list[str] = []
    with path.open("r", encoding="utf-8", errors="replace") as f:
        for raw in f:
            w = raw.strip().lower()
            if not w or w.startswith("#"):
                continue
            words.append(w)
    return words


def main() -> None:
    if not EN_50K_PATH.is_file():
        raise SystemExit(f"Missing input: {EN_50K_PATH}")
    if not SCOWL_PATH.is_file():
        raise SystemExit(f"Missing input: {SCOWL_PATH}")

    scowl = load_scowl(SCOWL_PATH)
    freq_entries = load_en_50k(EN_50K_PATH)
    freq_lookup = dict(freq_entries)

    kept: dict[str, int] = {}
    dropped_not_in_scowl: list[str] = []

    for word, freq in freq_entries:
        if len(word) > MAX_WORD_LEN:
            continue
        if not TOKEN_RE.fullmatch(word):
            continue
        if len(word) == 1 and word not in ("a", "i"):
            continue
        if t9_signature(word) is None:
            continue
        if word not in scowl and word not in CONTRACTION_SET:
            dropped_not_in_scowl.append(word)
            continue
        if len(kept) < TOP_WORD_LIMIT:
            kept[word] = freq

    lowest_kept_freq = min(kept.values()) if kept else 1

    contraction_report: list[tuple[str, int, str]] = []
    for c_word in CONTRACTIONS:
        c_freq, reason = synthesize_contraction_freq(c_word, freq_lookup, lowest_kept_freq)
        if c_word not in kept or c_freq > kept[c_word]:
            kept[c_word] = c_freq
        contraction_report.append((c_word, kept[c_word], reason))

    for extra_path in (COMMITTED_EXTRA_WORDS_PATH, LOCAL_EXTRA_WORDS_PATH):
        for w in load_extra_words(extra_path):
            if len(w) > MAX_WORD_LEN:
                continue
            if t9_signature(w) is None:
                continue
            if w not in kept:
                kept[w] = freq_lookup.get(w, lowest_kept_freq)

    rows: list[tuple[str, str, int]] = []
    for word, freq in kept.items():
        sig = t9_signature(word)
        if sig is None:
            continue
        rows.append((sig, word, freq))

    rows.sort(key=lambda r: (r[0], -r[2], r[1]))

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8", newline="\n") as f:
        for sig, word, freq in rows:
            f.write(f"{sig}\t{word}\t{freq}\n")

    size_bytes = os.path.getsize(OUTPUT_PATH)
    print(f"Lines written: {len(rows)} -> {OUTPUT_PATH} ({size_bytes} bytes)")
    print(f"Lowest kept frequency before extras: {lowest_kept_freq}")

    dropped_no_apos = [w for w in dropped_not_in_scowl if "'" not in w]
    print(f"Words dropped for not being in SCOWL (total={len(dropped_not_in_scowl)}, no-apostrophe={len(dropped_no_apos)}):")
    print("Next 100 dropped (no apostrophe):", ", ".join(dropped_no_apos[:100]))

    print("\nSynthesized contractions:")
    for c_word, c_freq, reason in contraction_report:
        print(f"  {c_word:12s} {c_freq:>10d}  ({reason})")

    by_sig: dict[str, list[tuple[str, int]]] = {}
    for sig, word, freq in rows:
        by_sig.setdefault(sig, []).append((word, freq))

    print("\nProbe sequences (first 20):")
    for probe in ("46", "66", "3668", "843", "8447", "4663"):
        matches = by_sig.get(probe, [])[:20]
        formatted = ", ".join(f"{w}({fr})" for w, fr in matches)
        print(f"  {probe}: {formatted}")


if __name__ == "__main__":
    main()
