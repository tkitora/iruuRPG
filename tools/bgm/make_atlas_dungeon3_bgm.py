#!/usr/bin/env python3
"""Makes cave_depths.nbs: "Where the Crystals Hum (Dungeon003)", the theme inside a deeper cave of crystals (a dungeon after the first one).

    python3 tools/bgm/make_atlas_dungeon3_bgm.py            writes src/main/resources/bgm/cave_depths.nbs
    python3 tools/bgm/make_atlas_dungeon3_bgm.py out.nbs    writes somewhere else

C# Dorian (the minor with the bright sixth: open and glassy instead of dark), 4/4, 60 BPM (4 ticks a second: a song tick is 5 server ticks; a tick is a sixteenth), 44 bars (2:56).
Natural and electronic sounds are kept apart on purpose: the rock, the water and the air are the natural instruments (harp, bass, guitar, bass drum, didgeridoo);
the crystals are the electronic ones (the electric piano, the iron xylophone = vibraphone, the 8-bit square wave). The crystals only speak where the cave "hums":
shimmering over the chorus, alone in the quiet interlude and in the swell.
Sustained notes: a note block dies away at once, so a held note is struck again, softly, under it (a swell where it must grow: the interlude).
Dissonance: a minor second (a half step above a chord tone) held for a moment and then falling into the chord tone, four times only (interlude and swell).
Form: intro (4 bars, once), A, B, a quiet interlude (the crystals alone), A' (a guitar and a third below), C (the swell), which ends on G#7 so that the jump back to A (C# minor) is smooth.

The motif of Atlas: C#5 G#4 E4 C#4 - the falling tonic triad (a fourth, a major third, a minor third), as in the other songs of the cave; here dotted, slow and low.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 4.0           # 60 BPM: a song tick is 5 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Where the Crystals Hum (Dungeon003)"

# chord -> (the bass root, the bass fifth, six notes of the chord for the harp, low to high); all by what SOUNDS
CHORDS = {
    "C#m": (37, 44, [56, 61, 64, 68, 73, 76]),
    "F#": (42, 49, [58, 61, 66, 70, 73, 78]),
    "B": (47, 54, [59, 63, 66, 71, 75, 78]),
    "G#m": (44, 51, [56, 59, 63, 68, 71, 75]),
    "G#7": (44, 51, [56, 60, 63, 66, 68, 72]),
    "E": (40, 47, [56, 59, 64, 68, 71, 76]),
    "D#m": (39, 46, [58, 63, 66, 70, 75, 78]),
    "A#dim": (46, 52, [58, 61, 64, 70, 73, 76]),
}

INTRO_CHORDS = ["C#m", "C#m", "C#m", "G#7"]
INTRO_HARP = ["r:16", "r:16", "C#5:8 G#4:8", "G#4:4 C5:4 D#5:8"]
A_CHORDS = ["C#m", "C#m", "F#", "F#", "C#m", "B", "G#m", "G#m"]
A_MELODY = [
    "C#5:6 G#4:2 E4:4 C#4:4",    # the motif of Atlas, dotted
    "E4:4 G#4:4 B4:4 G#4:4",
    "A#4:6 F#4:2 C#4:4 F#4:4",
    "C#5:4 A#4:4 F#4:8",
    "C#5:6 G#4:2 E4:4 C#4:4",
    "B4:4 F#4:4 D#4:4 F#4:4",
    "G#4:6 B4:2 D#5:4 B4:4",
    "G#4:8 r:8",
]
B_CHORDS = ["E", "E", "F#", "F#", "D#m", "D#m", "G#m", "B"]
B_MELODY = [
    "E4:2 G#4:2 B4:4 E5:4 B4:4",
    "E5:4 B4:4 G#4:8",
    "F#4:2 A#4:2 C#5:4 F#5:4 C#5:4",
    "A#4:4 C#5:4 A#4:8",
    "D#5:6 A#4:2 F#4:4 A#4:4",
    "F#4:4 A#4:4 D#5:8",
    "B4:4 D#5:4 G#4:4 B4:4",
    "F#4:4 D#4:4 B3:8",
]
QUIET_CHORDS = ["C#m", "C#m", "A#dim", "A#dim", "F#", "F#", "G#m", "G#m"]
C_CHORDS = ["C#m", "F#", "C#m", "B", "E", "F#", "G#7", "G#7"]
C_MELODY = [                      # the swell: the motif higher, then answered
    "E5:4 C#5:4 G#4:4 E4:4",
    "F#5:4 C#5:4 A#4:4 C#5:4",
    "E5:4 G#4:4 C#5:4 E5:4",
    "F#5:6 D#5:2 B4:4 F#4:4",
    "E5:4 B4:4 G#4:4 B4:4",
    "C#5:4 A#4:4 F#4:4 A#4:4",
    "D#5:6 C5:2 G#4:8",
    "G#4:4 D#4:4 r:8",
]

# the minor seconds: (bar of the section, the chord tone); the half step above it falls into it
CLASHES = {"quiet": [(3, 70), (7, 68)], "C": [(3, 71), (5, 73)]}

# (name, chords, melody, the voices, the loudness of the accompaniment)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, {"bass-soft", "drops", "harp-intro", "drone"}, 1.0),
    ("A", A_CHORDS, A_MELODY, {"harp", "bass", "drops", "echo"}, 1.0),
    ("B", B_CHORDS, B_MELODY, {"harp", "bass", "drops", "echo", "heart", "pling"}, 1.0),
    ("quiet", QUIET_CHORDS, None, {"harp-soft", "bass-soft", "drops-more", "hum"}, 0.8),
    ("A2", A_CHORDS, A_MELODY, {"harp", "bass", "drops", "echo", "harmony", "guitar", "heart"}, 1.0),
    ("C", C_CHORDS, C_MELODY, {"harp", "bass", "heart", "harmony", "pling", "hum-high", "drone-swell", "glint"}, 1.0),
]

ARPEGGIO = [0, 2, 3, 2, 4, 2, 3, 1]   # eight eighths a bar through the six notes of the chord


def drop_ticks(bar_number, count):
    """Where the water drops fall in a bar: the same every time (a small fixed generator)."""
    state = (bar_number * 2654435761 + 54321) & 0xFFFFFFFF
    ticks = []
    for _ in range(count):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        ticks.append(3 + (state >> 8) % 12)
    return sorted(set(ticks))


def build():
    lead = Layer("Harp (the tune)", 100, 100)
    echo = Layer("Harp (echo of the cave)", 40, 150)
    harmony = Layer("Harp (a third below)", 55, 80)
    arp = Layer("Harp (arpeggio)", 65, 85)
    bass = Layer("Bass", 85, 100)
    guitar = Layer("Guitar (root and fifth)", 60, 70)
    drops = Layer("Harp (water drops)", 50, 160)
    heart = Layer("Heartbeat (soft drum)", 60, 100)
    drone = Layer("Didgeridoo (the earth)", 45, 100)
    pling = Layer("Electric piano (the crystals shimmer)", 40, 115)
    hum_low = Layer("Iron xylophone (crystal hum)", 45, 90)
    hum_high = Layer("Iron xylophone (crystal hum, high)", 45, 110)
    clash = Layer("8-bit (the half step)", 35, 100)
    clash_low = Layer("Electric piano (the chord tone under the half step)", 35, 100)
    glint = Layer("8-bit (glints)", 30, 130)
    layers = [lead, echo, harmony, arp, bass, guitar, drops, heart, drone, pling, hum_low, hum_high, clash, clash_low, glint]

    total = sum(len(section[1]) for section in SECTIONS) * BAR
    bar_number = 0
    for name, chords, tune, voices, accompaniment in SECTIONS:
        count = len(chords)
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            root, fifth, tones = CHORDS[chord]
            soft = "harp-soft" in voices
            if "harp" in voices or soft:
                for step, tone_index in enumerate(ARPEGGIO):
                    if soft and step % 2:
                        continue
                    base = 36 if step == 0 else 28
                    arp.put_sound(start + step * 2, HARP, tones[tone_index], velocity(base * (0.8 if soft else 1.0), accompaniment))
            if "bass" in voices:  # the root, then the fifth; struck again softly under them
                for tick, note, vel in ((0, root, 70), (4, root, 30), (8, fifth, 54), (12, fifth, 26)):
                    bass.put_sound(start + tick, BASS, note, velocity(vel, accompaniment))
            if "bass-soft" in voices:  # one long root, struck again
                swell(bass, BASS, root, start, BAR, 50, 30, 8)
            if "guitar" in voices:
                for tick, vel in ((0, 38), (8, 30)):
                    guitar.put_sound(start + tick, GUITAR, root + 12, vel)
                    guitar.put_sound(start + tick + 1, GUITAR, fifth + 12, vel - 8)
            if "heart" in voices:
                for tick, vel in ((0, 44), (3, 30)):
                    heart.put(start + tick, BASEDRUM, 62, vel)
            if "drone" in voices and index % 2 == 0:
                drone.put_sound(start, DIDGERIDOO, root, 36)
            if "drone-swell" in voices:  # the earth rises under the swell
                level = bar_number - (total // BAR - count)
                swell(drone, DIDGERIDOO, root, start, BAR, 30 + level * 3, 36 + level * 3, 8)
            if "drops" in voices or "drops-more" in voices:
                number = 2 if "drops-more" in voices else 1 if bar_number % 2 == 0 else 0
                for offset, tick in enumerate(drop_ticks(bar_number, number)):
                    note = (76, 71, 74, 78)[(bar_number + offset) % 4]
                    drops.put_sound(start + tick, HARP, note, 24 + 6 * offset)
            if "harp-intro" in voices:
                tick = 0
                for note, length in parse_bar(INTRO_HARP[index]):
                    if note is not None:
                        lead.put_sound(start + tick, HARP, note, 52)
                    tick += length
            if "hum" in voices:  # the crystals alone: two notes of the chord held, growing over the bar
                swell(hum_low, IRON_XYLOPHONE, tones[3], start, BAR, 14, 36, 4)
            if "hum" in voices or "hum-high" in voices:
                swell(hum_high, IRON_XYLOPHONE, tones[5], start, BAR, 12, 30 if "hum" in voices else 24, 4)
            if "glint" in voices and index % 2 == 1:
                glint.put_sound(start + 10, BIT, tones[5], 20)
            for (bar, low) in CLASHES.get(name, []):
                if bar == index:  # a half step above a chord tone, held and falling into it
                    clash.put_sound(start + 4, BIT, low + 1, 30)
                    clash.put_sound(start + 6, BIT, low + 1, 30)
                    clash.put_sound(start + 8, BIT, low, 24)
                    clash_low.put_sound(start + 4, PLING, low, 26)
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        lead.put_sound(start + tick, HARP, note, velocity(66 if tick == 0 else 58, 1.0))
                        if "echo" in voices and start + tick + 3 < total:
                            echo.put_sound(start + tick + 3, HARP, note, 24)
                        if "harmony" in voices:
                            harmony.put_sound(start + tick, HARP, max(54, third_below(note, scale("C#3 D#3 E3 F#3 G#3 A#3 B3 C#4 D#4 E4 F#4 G#4 A#4 B4 C#5 D#5 E5 F#5"))), 34)
                        if "pling" in voices:
                            pling.put_sound(start + tick, PLING, note, 22)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/cave_depths.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Theme inside a cave of crystals. Natural instruments for the rock and water, electronic ones for the crystals. 44 bars at 60 BPM, loops from bar 5.", LOOP_START_BAR)
    report(target, layers, length, TPS)
    for layer in layers:
        span = layer.sounding_range()
        print(f"  {layer.name}: {len(layer.notes)} notes" + (f", sounds {span[0]}-{span[1]}" if span else ""))


if __name__ == "__main__":
    main()
