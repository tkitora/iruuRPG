#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the boss theme of the Atlas cave in a lower register than Boss001, and more uplifting than the dungeon theme.

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes src/main/resources/bgm/atlas_vindicator_2.nbs
    python3 tools/bgm/make_atlas_boss2_bgm.py out.nbs    writes somewhere else

The same piece as "The Axe at the Gate (Boss001)" (the same form and tune, E minor, 150 BPM, 80 bars, 2:08), written again by what SOUNDS:
  - Boss001 gave the tune to the flute (E6-F#7, with a bell at E7 and up). Here the tune is on the piano (the harp; B3 to F#5), no flute, no bell, no chime.
  - The chorus is brighter instead of higher: the electric piano (pling) doubles the tune in the first chorus, the piano's third below joins in the last one, the iron xylophone
    (B4 to F#5) sparkles on the bar, the bass doubles the tune two octaves down in the first chorus, and the guitar plays rising arpeggios in the last chorus.
  - Drums, bass and guitar stabs are the same: the 3-3-2 pattern (ticks 0, 3, 6, 8, 11, 14) with a thick kick, the snare on 2 and 4, the hat.
  - The piano plays a repeated note under a long one (its notes die away at once).

The motif of Atlas is the phrase: the tonic triad falling in three steps and landing on the tonic (a fourth, a major third and a minor third down), here in eighths and driving.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers
import make_atlas_boss_bgm as base

TPS = base.TPS
LOOP_START_BAR = base.LOOP_START_BAR
TITLE = "Raise the Axe (Boss002)"
TRESILLO = base.TRESILLO
E_MINOR = base.E_MINOR

# (name, chords, melody, drums, guitar, extras)
#   extras: "pling" (the electric piano doubles the tune) | "harmony" (the piano's third below) | "low" (the bass doubles the tune) | "sparkle" (iron xylophone on the bar)
#           | "tension" (rising sixteenths on the guitar in the last two bars) | "arp8" (eighths on the guitar) | "soft-lead" / "soft-bass" | "roll-intro"
SECTIONS = [
    ("intro", base.INTRO_CHORDS, base.INTRO_MELODY, "kick", None, {"roll-intro"}),
    ("A", base.A_CHORDS, base.A_MELODY, "full", "muted", set()),
    ("B", base.B_CHORDS, base.B_MELODY, "full", "tresillo", {"pling", "low", "sparkle"}),
    ("bridge", base.BRIDGE_CHORDS, base.BRIDGE_MELODY, "half", None, {"tension", "soft-bass", "soft-lead"}),
    ("A2", base.A_CHORDS, base.A_MELODY, "full", "tresillo", {"harmony"}),
    ("B2", base.B_CHORDS, base.B_MELODY, "climax", "tresillo", {"pling", "harmony", "sparkle", "arp8"}),
    ("outro", base.OUTRO_CHORDS, base.OUTRO_MELODY, "roll", "muted", set()),
]


def build():
    lead = Layer("Piano (the tune)", 100, 100)
    hold = Layer("Piano (the tune held)", 60, 100)
    pling = Layer("Electric piano (doubles the tune)", 45, 120)
    harmony = Layer("Piano (a third below)", 50, 80)
    low = Layer("Bass (doubles the tune)", 55, 100)
    arp = Layer("Guitar (arpeggio)", 55, 70)
    guitar_root = Layer("Guitar (root)", 75, 60)
    guitar_fifth = Layer("Guitar (fifth)", 70, 70)
    bass = Layer("Bass", 90, 100)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat", 55, 115)
    sparkle = Layer("Iron xylophone (sparkle)", 45, 125)
    layers = [lead, hold, pling, harmony, low, arp, guitar_root, guitar_fifth, bass, kick, snare, hat, sparkle]

    bar_number = 0
    for name, chords, tune, drums, guitar, extras in SECTIONS:
        last = len(chords) - 1
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            tones, root, fifth = base.CHORDS[chord]  # (written notes: the bass sounds them 24 down, the guitar 12 down)

            if name != "intro" or index >= 2:  # the bass: the 3-3-2 pattern on the root, the fifth on the third hit of each half
                hits = TRESILLO if "soft-bass" not in extras else [0, 8]
                for tick in hits:
                    note = fifth if tick in (6, 14) else root
                    vel = (78 if tick in (0, 8) else 64) if "soft-bass" not in extras else 56
                    bass.put_sound(start + tick, BASS, note - 24, vel)

            if guitar is not None:  # power chord stabs
                stabs = [0, 8] if guitar == "muted" else TRESILLO
                for tick in stabs:
                    guitar_root.put_sound(start + tick, GUITAR, root - 12, 56 if tick in (0, 8) else 48)
                    if tick in (0, 8):
                        guitar_fifth.put_sound(start + tick, GUITAR, fifth - 12, 44)

            if drums == "kick":
                kick.put(start, BASEDRUM, 62, 80)
                kick.put(start + 8, BASEDRUM, 62, 70)
            elif drums in ("full", "roll"):
                for tick in (0, 6, 8, 14):
                    kick.put(start + tick, BASEDRUM, 62, 84 if tick == 0 else 70)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 70)
                for tick in range(0, 16, 2):
                    hat.put(start + tick, HAT, 66, 34 if tick in (4, 12) else 24)
            elif drums == "half":
                kick.put(start, BASEDRUM, 62, 80)
                snare.put(start + 8, SNARE, 66, 66)
                for tick in range(0, 16, 4):
                    hat.put(start + tick, HAT, 66, 22)
            elif drums == "climax":
                for tick in TRESILLO:
                    kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 74)
                for tick in range(0, 16, 2):
                    hat.put(start + tick, HAT, 66, 38 if tick % 4 == 0 else 22)
            roll_bar = (drums == "roll" and index == last) or ("roll-intro" in extras and index >= last - 1)
            if roll_bar:  # snare rolls that run up to the next section
                for tick in list(snare.notes):
                    if start <= tick < start + BAR:
                        del snare.notes[tick]
                for tick in range(16):
                    if "roll-intro" in extras and index == last - 1 and tick % 2:
                        continue
                    progress = (tick + (16 if index == last else 0)) / 32.0
                    snare.put(start + tick, SNARE, 66, 30 + int(48 * progress))
                if drums == "roll":
                    for tick in list(hat.notes):
                        if start <= tick < start + BAR:
                            del hat.notes[tick]

            if "sparkle" in extras and index % 4 == 0:
                sparkle.put_sound(start + 2, IRON_XYLOPHONE, 78 if index % 8 == 0 else 74, 36)
            if "tension" in extras and index >= last - 1:  # rising sixteenths on the guitar (an octave and a bit lower than the written chord)
                for tick in range(16):
                    arp.put_sound(start + tick, GUITAR, tones[tick % 4 + (1 if index == last else 0)] - 12, 26 + (16 if index == last else 0) + tick)
            if "arp8" in extras:
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 3, 2, 1]):
                    arp.put_sound(start + step * 2 + 1, GUITAR, tones[tone_index] - 12, 34)

            # the tune: the piano, at the pitch it is written (B3 to F#5)
            tick = 0
            for note, length in parse_bar(tune[index]):
                if note is not None:
                    at = start + tick
                    strong = tick == 0
                    soft = 0.65 if "soft-lead" in extras else 1.0
                    lead.put_sound(at, HARP, note, velocity(96 if strong else 88, soft))
                    for again in range(4, length, 4):  # a long note: struck again under it, softer
                        hold.put_sound(at + again, HARP, note, velocity(48, soft))
                    if "pling" in extras:
                        pling.put_sound(at, PLING, note, 36)
                    if "harmony" in extras:
                        harmony.put_sound(at, HARP, third_below(note, E_MINOR), 44)
                    if "low" in extras:
                        low.put_sound(at, BASS, note - 24, 52)
                tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/atlas_vindicator_2.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Boss theme of the Atlas cave (the vindicator), lower: piano and bass instead of the flute. Fast, heavy drums, 80 bars, loops from bar 5.", LOOP_START_BAR)
    report(target, layers, length, TPS)


if __name__ == "__main__":
    main()
