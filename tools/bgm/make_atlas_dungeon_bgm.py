#!/usr/bin/env python3
"""Makes atlas_dungeon.nbs: "Hollow Beneath the Giant (Dungeon001)", the theme inside the Atlas cave dungeon (the first dungeon; a short one). The second version.

    python3 tools/bgm/make_atlas_dungeon_bgm.py            writes src/main/resources/bgm/atlas_dungeon.nbs
    python3 tools/bgm/make_atlas_dungeon_bgm.py out.nbs    writes somewhere else

E Phrygian / minor, 4/4, 100 BPM (one tick = a sixteenth; 6.67 ticks a second = 20 / 3, so a song tick is exactly 3 server ticks), 56 bars (2:14). Same form, tune and chords as the
first version; written again by what SOUNDS and with the techniques of the style guides (docs/bgm-style-guide.md, docs/bgm-style-guide-01.md):
  - No flute, bell or chime (they sounded two or three octaves above the rest). The tune is on the piano (the harp, E4 to F5), with an echo three ticks after it (a time-division delay).
  - Natural and electronic apart: the cave is natural (harp, bass, guitar, bass drum, didgeridoo); the electronic instruments are the ones that do not belong to the cave:
    a very quiet 8-bit rub (the half step F over E, a tritone B-flat over E) that keeps going in the background, the iron xylophone (vibraphone) hum of the interlude, and the electric
    piano that doubles the tune in the swell.
  - The beat is hidden: the bass does not strike on every beat any more but plays 3 + 3 + 2 (ticks 0, 6, 12), the first hit soft and the hits on the off-beats strong.
  - The interlude is a falling bass line (E D# D C#, a line cliché) under a held Em, and one bar of it is a ghost rest: everything stops, one high note of the piano and its echo are left.
  - A tritone in the swell (F over B7, held and struck again), once only.
  - Sustain by striking again, softly (the harp, the bass, the iron xylophone); a fast arpeggio of sixteenths in the swell makes the pad.
Form: intro (4 bars, once), A, B (the pulse builds), a quiet interlude, A' (a second voice, the rub), B' (the electric piano on the tune), C (the swell), coda. It loops from bar 5,
and the coda stops on the dominant (B7) so that the jump back to A (E minor) is smooth.

The motif of Atlas (the place): E5 B4 G4 E4 - a falling E minor triad, the plains theme turned upside down; here it opens the piece in dotted notes and returns at the top of the swell.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0 / 3.0    # ticks a second (a divisor of 20: a song tick is 3 server ticks); a tick is a sixteenth note, a quarter is 4 ticks: 100 BPM
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Hollow Beneath the Giant (Dungeon001)"
POLYPHONY = 8
E_MINOR = scale("E3 F#3 G3 A3 B3 C4 D4 E4 F#4 G4 A4 B4 C5 D5 E5 F#5")

# chord -> (the bass root, five notes of the chord for the harp, low to high); all by what SOUNDS (the bass sounds F#1 to F#3, the harp F#3 to F#5)
CHORDS = {
    "Em": (40, [59, 64, 67, 71, 76]),
    "Em/D#": (39, [59, 64, 67, 71, 76]),   # the line cliché: the bass falls under a held Em
    "Em/D": (38, [59, 64, 67, 71, 76]),
    "Em/C#": (37, [59, 64, 67, 71, 76]),
    "F": (41, [57, 60, 65, 69, 72]),
    "Dm": (38, [57, 62, 65, 69, 74]),
    "C": (36, [60, 64, 67, 72, 76]),
    "G": (43, [55, 59, 62, 67, 71]),
    "Am": (45, [57, 60, 64, 69, 72]),
    "B7": (47, [59, 63, 66, 69, 71]),
}

INTRO_CHORDS = ["Em", "Em", "F", "B7"]
INTRO_HARP = ["r:16", "r:16", "E5:8 B4:8", "C5:8 B4:8"]
A_CHORDS = ["Em", "F", "Em", "F", "Em", "Dm", "C", "B7"]
A_MELODY = [
    "E5:6 B4:2 G4:4 E4:4",     # the motif of Atlas, dotted
    "C5:4 A4:4 F4:8",
    "E5:4 B4:4 G4:4 B4:4",
    "A4:6 F4:2 E4:8",          # the half step: F falls to E
    "E5:6 B4:2 G4:4 E4:4",
    "D5:4 A4:4 F4:4 A4:4",
    "E5:4 C5:4 G4:8",
    "F#4:4 A4:4 B4:8",
]
B_CHORDS = ["Em", "Em", "F", "F", "Am", "B7", "Em", "Em"]
B_MELODY = [
    "E4:2 G4:2 B4:4 G4:4 E4:4",
    "B4:4 E5:4 B4:4 G4:4",
    "F4:2 A4:2 C5:4 A4:4 F4:4",
    "C5:4 F5:4 C5:4 A4:4",
    "A4:2 C5:2 E5:4 C5:4 A4:4",
    "D#5:4 F#5:4 D#5:4 B4:4",
    "E5:4 B4:4 G4:4 B4:4",
    "E4:8 r:8",
]
QUIET_CHORDS = ["Em", "Em/D#", "Em/D", "Em/C#", "F", "F", "Am", "B7"]
GHOST_BAR = 5          # of the interlude: everything stops
C_CHORDS = ["Em", "F", "G", "Am", "F", "B7", "Em", "B7"]
C_MELODY = [                  # the swell: the motif at the top, then double time
    "E5:4 B4:4 G4:4 E4:4",
    "F5:4 C5:4 A4:4 C5:4",
    "D5:4 G4:4 B4:4 D5:4",
    "E5:4 C5:4 A4:8",
    "F5:6 C5:2 A4:8",
    "D#5:2 F#5:2 B4:4 D#5:4 F#5:4",
    "E5:2 B4:2 G4:4 E5:2 B4:2 G4:4",
    "F#5:8 D#5:8",
]
CODA_CHORDS = ["Em", "F", "Em", "B7"]
CODA_HARP = ["E5:8 B4:8", "C5:8 A4:8", "G4:8 E4:8", "F#4:16"]

# a tritone held and struck again: (section, bar of the section, the lower note, the higher note): B (71) and F (77) over B7
CLASHES = [("C", 5, 71, 77)]

# (name, chords, melody, the voices, the loudness of the accompaniment, of the melody)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, {"harp-intro", "bass-soft", "drops", "drone"}, 1.0, 1.0),
    ("A", A_CHORDS, A_MELODY, {"harp", "bass", "drops", "echo"}, 1.0, 1.0),
    ("B", B_CHORDS, B_MELODY, {"harp", "bass", "drops", "echo", "heart", "guitar"}, 1.0, 1.0),
    ("quiet", QUIET_CHORDS, None, {"harp-soft", "bass-soft", "drops-more", "hum"}, 0.7, 1.0),
    ("A2", A_CHORDS, A_MELODY, {"harp", "bass", "drops", "echo", "harmony", "heart", "drone", "rub"}, 1.0, 1.0),
    ("B2", B_CHORDS, B_MELODY, {"harp", "bass", "drops", "echo", "harmony", "heart", "guitar", "pling", "rub"}, 1.0, 1.05),
    ("C", C_CHORDS, C_MELODY, {"harp16", "bass", "heart", "guitar", "harmony", "pling", "rub"}, 1.0, 1.15),
    ("coda", CODA_CHORDS, CODA_HARP, {"harp-soft", "bass-soft", "drops"}, 0.6, 0.8),
]

ARPEGGIO = [0, 2, 1, 3, 2, 4, 3, 1]   # eight eighths a bar through the five notes of the chord, low to high
ECHO_DELAY = 4                         # ticks (0.6 s)
BASS_HITS = ((0, 34), (6, 66), (12, 58))   # 3 + 3 + 2: the first hit soft, the off-beats strong


def drop_ticks(bar_number, count):
    """Where the water drops fall in a bar: the same every time (a small fixed generator)."""
    state = (bar_number * 2654435761 + 54321) & 0xFFFFFFFF
    ticks = []
    for _ in range(count):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        ticks.append(3 + (state >> 8) % 12)
    return sorted(set(ticks))


def build():
    lead = Layer("Piano (Atlas motif)", 100, 100)
    pling = Layer("Electric piano (doubles the tune)", 40, 125)
    harmony = Layer("Piano (a third below)", 55, 80)
    echo = Layer("Piano (echo of the cave)", 45, 150)
    arp = Layer("Piano (arpeggio)", 70, 90)
    bass = Layer("Bass (3 + 3 + 2)", 85, 100)
    guitar = Layer("Guitar (muted, off the beat)", 55, 65)
    drops = Layer("Piano (water drops, high)", 50, 125)
    heart = Layer("Heartbeat (soft drum)", 60, 100)
    drone = Layer("Didgeridoo (the earth)", 40, 100)
    hum = Layer("Iron xylophone (hum of the interlude)", 40, 110)
    rub = Layer("8-bit (the half step, very quiet)", 40, 100)
    clash_a = Layer("Tritone (lower)", 40, 90)
    clash_b = Layer("Tritone (higher)", 40, 110)
    layers = [lead, pling, harmony, echo, arp, bass, guitar, drops, heart, drone, hum, rub, clash_a, clash_b]

    total = sum(len(section[1]) for section in SECTIONS) * BAR
    bar_number = 0
    for name, chords, tune, voices, accompaniment, lead_level in SECTIONS:
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            root, tones = CHORDS[chord]
            soft = "harp-soft" in voices
            ghost = name == "quiet" and index == GHOST_BAR
            if ghost:  # a bar of rest: one high note of the piano and its echo are all that is left
                lead.put_sound(start, HARP, 78, 40)
                echo.put_sound(start + ECHO_DELAY, HARP, 78, 20)
                echo.put_sound(start + 2 * ECHO_DELAY, HARP, 78, 10)
                bar_number += 1
                continue
            if "harp" in voices or soft:
                for step, tone_index in enumerate(ARPEGGIO):
                    if soft and step % 2:
                        continue
                    base = 42 if step == 0 else 34
                    arp.put_sound(start + step * 2, HARP, tones[tone_index], velocity(base * (0.7 if soft else 1.0), accompaniment))
            if "harp16" in voices:  # a fast arpeggio, up and down: the pad of the swell
                for step in range(16):
                    arp.put_sound(start + step, HARP, tones[(0, 1, 2, 3, 4, 3, 2, 1)[step % 8]], velocity(30 if step % 4 else 38, accompaniment))
            if "bass" in voices:  # 3 + 3 + 2: the beat is hidden
                for tick, vel in BASS_HITS:
                    bass.put_sound(start + tick, BASS, root, velocity(vel, accompaniment))
                bass.put_sound(start + 14, BASS, root + 7, velocity(30, accompaniment))
            if "bass-soft" in voices:  # one long root, struck again
                swell(bass, BASS, root, start, BAR, 48, 30, 8)
            if "guitar" in voices:  # the muted guitar between the hits of the bass
                for tick, vel in ((3, 34), (9, 28)):
                    guitar.put_sound(start + tick, GUITAR, root + 12, vel)
            if "heart" in voices:
                for tick, vel in ((0, 46), (3, 30)) + (((8, 38), (11, 24)) if name == "C" else ()):
                    heart.put(start + tick, BASEDRUM, 62, vel)
            if "drone" in voices and index % 2 == 0:
                drone.put_sound(start, DIDGERIDOO, root, 36)
            if "drops" in voices or "drops-more" in voices:
                count = 2 if "drops-more" in voices else 1 if bar_number % 2 == 0 else 0
                for offset, tick in enumerate(drop_ticks(bar_number, count)):
                    note = (76, 71, 74, 78)[(bar_number + offset) % 4]
                    drops.put_sound(start + tick, HARP, note, 26 + 6 * offset)
            if "harp-intro" in voices:
                tick = 0
                for note, length in parse_bar(INTRO_HARP[index]):
                    if note is not None:
                        lead.put_sound(start + tick, HARP, note, 56)
                    tick += length
            if "hum" in voices:  # the vibraphone, alone and rising over the bar
                swell(hum, IRON_XYLOPHONE, tones[2], start, BAR, 14, 34, 4)
            if "rub" in voices and chord in ("Em", "B7"):  # F over E (and B-flat over E, a tritone): tiny and staccato, every bar
                rub.put_sound(start + 14, BIT, 77, 22)
                if chord == "Em":
                    rub.put_sound(start + 6, BIT, 70, 18)
            for (section, bar, low, high) in CLASHES:
                if section == name and bar == index:
                    for again in range(8, 16, 4):
                        clash_a.put_sound(start + again, PLING, low, 44 - (again - 8) * 2)
                        clash_b.put_sound(start + again, BIT, high, 40 - (again - 8) * 2)
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        lead.put_sound(start + tick, HARP, note, velocity(66 if tick == 0 else 58, lead_level))
                        if "echo" in voices and start + tick + ECHO_DELAY < total:
                            echo.put_sound(start + tick + ECHO_DELAY, HARP, note, 26)
                        if "harmony" in voices:
                            harmony.put_sound(start + tick, HARP, max(54, third_below(note, E_MINOR)), 34)
                        if "pling" in voices:
                            pling.put_sound(start + tick, PLING, note, 26)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    removed = limit_polyphony(layers, POLYPHONY)
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/atlas_dungeon.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Theme inside the Atlas cave dungeon, the first dungeon. Dark and tense but not frightening, 56 bars, loops from bar 5 (the intro is played once).",
              LOOP_START_BAR)
    report(target, layers, length, TPS)
    print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
