#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Hymn for the Unburied (Boss004)" and "Hymn for the Unburied, Risen (Boss004)", the boss theme of the necromancer in two phases.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

F# minor with a Phrygian colour (G over F#), 4/4, 100 BPM (one tick = a sixteenth; 6.67 ticks a second = 20 / 3, so a song tick is exactly 3 server ticks), 52 bars (2:05).
A story boss (the guide's "D"): heavy, tragic, a dignified march rather than a rush. Two phases that are ONE piece (the same tempo, the same 52 bars, the same chord in every bar, the same
loop point): the game goes over from phase 1 to phase 2 at the next bar line, from the same bar (Bgm.playSceneSynced), so the piece changes its clothes without starting again.
  - Phase 1, "the ritual": the natural instruments (piano, bass, guitar, bass drum, didgeridoo, the hat as a clock), half time.
  - Phase 2, "risen": the electronic ones (8-bit, electric piano, iron xylophone = vibraphone) over the same piano, double time, a 16th-note drive.
Form (from a point to a surface): intro (4 bars, once: the low earth and a clock, nothing else), A (the tune, thin), B, A' (fuller), the interlude at about two thirds
(8 bars: drums and bass stop, one lone voice, a tritone), B' (everything), the coda (8 bars: the release, ending on F# MAJOR for one bar - the Picardy third - and then the jump back to A, F# minor).
Progression: i - VI - III - VII (F#m D A E) in A; VI - VII - i - i, VI - VII - bII (G) - V7 (C#7) in B; the coda goes VI - VII - V7 and lands on F#.

The motif of the necromancer: A4 G#4 G4 F#4 - a chromatic fall (the lament, "passus duriusculus") onto the tonic, dotted. It is not the falling triad of Atlas: this is another place.
The requests for the boss songs are used where they fit and only there: a solo (the interlude), a minor second and a tritone (twice), the lowest note (the intro), sustain by striking again, and
natural against electronic. Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0 / 3.0    # 100 BPM: a song tick is 3 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLES = {1: "Hymn for the Unburied (Boss004)", 2: "Hymn for the Unburied, Risen (Boss004)"}
FILES = {1: "necromancer_phase1.nbs", 2: "necromancer_phase2.nbs"}
POLYPHONY = 8
F_SHARP_MINOR = scale("F#3 G#3 A3 B3 C#4 D4 E4 F#4 G#4 A4 B4 C#5 D5 E5 F#5")

# chord -> (bass root, bass fifth, six notes of the chord for the piano, low to high); all by what SOUNDS (the bass F#1 to F#3, the piano F#3 to F#5)
CHORDS = {
    "F#m": (42, 49, [54, 57, 61, 66, 69, 73]),
    "D": (38, 45, [57, 62, 66, 69, 74, 78]),
    "A": (45, 52, [57, 61, 64, 69, 73, 76]),
    "E": (40, 47, [56, 59, 64, 68, 71, 76]),
    "G": (43, 50, [55, 59, 62, 67, 71, 74]),
    "C#7": (37, 44, [56, 61, 65, 68, 71, 73]),
    "F#": (42, 49, [54, 58, 61, 66, 70, 73]),
}

INTRO_CHORDS = ["F#m", "F#m", "F#m", "C#7"]
INTRO_TUNE = ["r:16", "r:16", "A4:6 G#4:2 G4:4 F#4:4", "G#4:8 F4:8"]
A_CHORDS = ["F#m", "D", "A", "E", "F#m", "D", "A", "E"]
A_TUNE = [
    "A4:6 G#4:2 G4:4 F#4:4",       # the motif of the necromancer, dotted
    "F#4:4 A4:4 D5:8",
    "C#5:6 B4:2 A4:4 E4:4",
    "G#4:4 B4:4 E5:4 B4:4",
    "A4:6 G#4:2 G4:4 F#4:4",
    "A4:4 F#4:4 D4:4 F#4:4",
    "C#5:4 E5:4 C#5:4 A4:4",
    "B4:4 G#4:4 B4:8",
]
B_CHORDS = ["D", "E", "F#m", "F#m", "D", "E", "G", "C#7"]
B_TUNE = [
    "D5:6 A4:2 F#4:8",
    "E5:6 B4:2 G#4:8",
    "C#5:4 A4:4 F#4:4 A4:4",
    "A4:6 G#4:2 G4:4 F#4:4",
    "F#5:6 D5:2 A4:8",
    "E5:4 G#4:4 B4:4 E5:4",
    "D5:4 B4:4 G4:4 B4:4",
    "B4:4 G#4:4 F4:4 G#4:4",
]
INTERLUDE_CHORDS = ["F#m", "F#m", "D", "D", "G", "G", "C#7", "C#7"]
SOLO = [                         # the lone voice
    "C#5:8 A4:4 F#4:4",
    "A4:6 G#4:2 G4:4 F#4:4",
    "D5:8 F#5:8",
    "E5:6 D5:2 A4:8",
    "G4:4 B4:4 D5:8",
    "D5:6 C5:2 B4:8",
    "G#4:4 B4:4 C#5:4 F5:4",
    "G#4:8 r:8",
]
CODA_CHORDS = ["D", "E", "C#7", "C#7", "D", "E", "C#7", "F#"]
CODA_TUNE = [
    "F#5:8 D5:8",
    "E5:8 B4:8",
    "G#4:4 B4:4 C#5:4 F5:4",
    "B4:8 G#4:8",
    "A4:4 D5:4 F#5:8",
    "G#4:4 B4:4 E5:8",
    "C#5:4 B4:4 G#4:4 F4:4",
    "A#4:16",                    # the Picardy third: F# MAJOR for one bar, then back to F# minor
]

# two notes held and struck again a little: (section, bar of the section, the lower note, the higher note): a tritone in the interlude (G and C#), a half step in the last chorus (F#4 and F4)
CLASHES = [("interlude", 5, 55, 61), ("B2", 7, 65, 66)]

# (name, chords, tune)
SECTIONS = [
    ("intro", INTRO_CHORDS, INTRO_TUNE),
    ("A", A_CHORDS, A_TUNE),
    ("B", B_CHORDS, B_TUNE),
    ("A2", A_CHORDS, A_TUNE),
    ("interlude", INTERLUDE_CHORDS, None),
    ("B2", B_CHORDS, B_TUNE),
    ("coda", CODA_CHORDS, CODA_TUNE),
]


def put_over(layer, tick, instrument, sounding, vel):
    """A drum or a fill puts its note over what is there."""
    layer.notes.pop(tick, None)
    layer.put(tick, instrument, sounding, vel)


def build(phase):
    p2 = phase == 2
    lead = Layer("Piano (the tune)", 100, 100)
    hold = Layer("Piano (the tune held)", 60, 100)
    echo = Layer("Piano (echo)", 40, 150)
    harmony = Layer("Piano (a third below)", 50, 80)
    chip = Layer("8-bit (doubles the tune)", 40, 120)
    pad = Layer("Piano (arpeggio)" if not p2 else "Electric piano (arpeggio)", 45, 90)
    sparkle = Layer("Iron xylophone (sparkle)", 45, 125)
    solo = Layer("Piano (solo)" if not p2 else "8-bit (solo)", 80 if not p2 else 60, 100)
    clash_a = Layer("Rub (lower)", 50, 90)
    clash_b = Layer("Rub (higher)", 50, 110)
    bass = Layer("Bass", 90, 100)
    earth = Layer("Didgeridoo (the earth)", 55, 100)
    guitar_root = Layer("Guitar (root)", 70, 60)
    guitar_fifth = Layer("Guitar (fifth)", 65, 70)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat (the clock)", 55, 115)
    layers = [lead, hold, echo, harmony, chip, pad, sparkle, solo, clash_a, clash_b, bass, earth, guitar_root, guitar_fifth, kick, snare, hat]

    bar_number = 0
    for name, chords, tune in SECTIONS:
        count = len(chords)
        fuller = name in ("A2", "B2")
        big = name == "B2"
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            bass_root, bass_fifth, tones = CHORDS[chord][0], CHORDS[chord][1], CHORDS[chord][2]
            last = index == count - 1
            release = name == "coda" and index >= 4

            # ---- the low notes
            if name == "intro":  # the lowest bass there is, held and growing, and the earth
                swell(bass, BASS, 30, start, BAR, 38 + index * 10, 48 + index * 12, 4)
                if index >= 2:
                    swell(earth, DIDGERIDOO, 30, start, BAR, 20 + (index - 2) * 8, 30 + (index - 2) * 10, 4)
            elif name == "interlude":
                if index == 7:  # the earth comes back under the last bar
                    swell(earth, DIDGERIDOO, bass_root, start, BAR, 18, 52, 4)
            elif release:
                for tick, vel in ((0, 56), (8, 40)) if not p2 else ((0, 56), (4, 36), (8, 44), (12, 34)):
                    bass.put_sound(start + tick, BASS, bass_root, vel)
            elif p2:
                for tick in range(0, 16, 2):  # eighths, the fifth on the last of each half
                    bass.put_sound(start + tick, BASS, bass_fifth if tick in (6, 14) else bass_root, (74 if tick % 8 == 0 else 58 if tick % 4 == 0 else 46) + (6 if big else 0))
            else:
                for tick, note, vel in ((0, bass_root, 78), (6, bass_root, 54), (8, bass_root, 62), (14, bass_fifth, 46)) if name != "A" else ((0, bass_root, 72), (8, bass_root, 50)):
                    bass.put_sound(start + tick, BASS, note, vel)
            if name in ("A2", "B", "B2") and not p2 and index % 4 == 0:
                swell(earth, DIDGERIDOO, bass_root, start, 8, 34, 26, 4)
            if p2 and big:
                swell(earth, DIDGERIDOO, bass_root, start, BAR, 30, 22, 4)

            # ---- the guitar
            if name in ("B", "A2", "B2") or (name == "coda" and index < 4):
                if p2 and fuller:
                    stabs = [(tick, 30) for tick in range(0, 16, 2)] if name == "A2" else [(0, 54), (6, 46), (8, 52), (14, 44)]
                elif name == "B" and not p2:
                    stabs = [(0, 46), (8, 40)]
                else:
                    stabs = [(0, 52), (8, 44)] if not p2 else [(0, 54), (6, 46), (8, 52), (14, 44)]
                for tick, vel in stabs:
                    guitar_root.put_sound(start + tick, GUITAR, bass_root + 12, vel)
                    if vel >= 40:
                        guitar_fifth.put_sound(start + tick, GUITAR, bass_fifth + 12, vel - 8)

            # ---- the drums
            if name == "intro":  # the clock: a tick on every beat, nothing else (and a heartbeat in the last two bars of phase 2)
                for tick in (0, 4, 8, 12):
                    hat.put(start + tick, HAT, 66, 18)
                if p2 and index >= 2:
                    kick.put(start, BASEDRUM, 62, 70)
                    kick.put(start + 3, BASEDRUM, 62, 46)
            elif name == "interlude":
                pass  # everything stops
            elif release:
                kick.put(start, BASEDRUM, 62, 64)
                for tick in (0, 8) if index < 6 else (0,):
                    hat.put(start + tick, HAT, 66, 18)
            elif name == "A":
                kick.put(start, BASEDRUM, 62, 74)
                if p2:
                    for tick in (4, 8, 12):
                        kick.put(start + tick, BASEDRUM, 62, 62)
                    snare.put(start + 8, SNARE, 66, 56)
                    for tick in range(0, 16, 2):
                        hat.put(start + tick, HAT, 66, 22 if tick % 4 == 0 else 14)
                else:
                    for tick in (0, 4, 8, 12):  # the clock goes on
                        hat.put(start + tick, HAT, 66, 18)
            else:
                if p2:
                    for tick in (0, 4, 8, 12) + ((14,) if big else ()):
                        kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                    for tick in (4, 12):
                        snare.put(start + tick, SNARE, 66, 74)
                    if big:  # the chain
                        for tick in (2, 6, 10, 14, 15):
                            snare.put(start + tick, SNARE, 66, 22 if tick != 15 else 30)
                    for tick in range(0, 16, 2):
                        hat.put(start + tick, HAT, 66, 36 if tick % 4 == 0 else 22)
                else:
                    kick.put(start, BASEDRUM, 62, 84)
                    kick.put(start + 6, BASEDRUM, 62, 58)
                    if fuller:
                        kick.put(start + 10, BASEDRUM, 62, 54)
                    snare.put(start + 8, SNARE, 66, 76)
                    for tick in range(0, 16, 2 if fuller else 4):
                        hat.put(start + tick, HAT, 66, 26 if tick % 4 == 0 else 18)
                if last and name in ("B", "A2"):  # a fill into the next section
                    fill = [(8, 50), (10, 56), (12, 62), (13, 68), (14, 76), (15, 86)] if p2 else [(12, 56), (14, 70), (15, 84)]
                    for tick, vel in fill:
                        put_over(snare, start + tick, SNARE, 66, vel)

            # ---- the pad: a soft arpeggio in eighths (A'), in sixteenths up and down at the end (B')
            if name == "A2" or (name == "B" and p2):
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 3, 2, 1]):
                    pad.put_sound(start + step * 2 + 1, PLING if p2 else HARP, tones[tone_index], 24)
            if big or (name == "coda" and index < 4 and p2):
                for step in range(16):
                    pad.put_sound(start + step, PLING if p2 else HARP, tones[(0, 1, 2, 3, 4, 5, 4, 3)[step % 8]], 30 if step % 4 else 38)

            # ---- the solo
            if name == "interlude":
                tick = 0
                for note, length in parse_bar(SOLO[index]):
                    if note is not None:
                        solo.put_sound(start + tick, HARP if not p2 else BIT, note, 80 if tick == 0 else 70)
                        step = 4 if not p2 else 2
                        for again in range(step, length, step):
                            solo.put_sound(start + tick + again, HARP if not p2 else BIT, note, 44 if not p2 else 34)
                        if not p2 and start + tick + 3 < (bar_number + 1) * BAR + BAR:
                            echo.put_sound(start + tick + 3, HARP, note, 22)
                    tick += length

            # ---- the rubs
            for (section, bar, lower, higher) in CLASHES:
                if section == name and bar == index:
                    instrument = BIT if p2 else HARP
                    for again in range(0, 8, 4):
                        clash_a.put_sound(start + again, instrument, lower, 46 - again * 2)
                        clash_b.put_sound(start + again, instrument, higher, 46 - again * 2)

            # ---- the tune
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        at = start + tick
                        strong = tick == 0
                        lead.put_sound(at, HARP, note, 90 if strong else 82)
                        for again in range(4, length, 4):  # a long note is struck again, softer
                            hold.put_sound(at + again, HARP, note, 52 - again * 2)
                        if not p2 and name in ("A", "intro"):  # the echo: the ritual hall
                            echo.put_sound(at + 3, HARP, note, 24)
                        if fuller or name == "coda":
                            harmony.put_sound(at, HARP, max(54, third_below(note, F_SHARP_MINOR)), 40)
                        if p2 and (name in ("B", "B2") or fuller):
                            chip.put_sound(at, BIT, note, 34)
                        if p2 and big and strong and index % 4 == 0:
                            sparkle.put_sound(at + 2, IRON_XYLOPHONE, note, 38)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
    out.mkdir(parents=True, exist_ok=True)
    for phase in (1, 2):
        layers, length = build(phase)
        removed = limit_polyphony(layers, POLYPHONY)
        target = out / FILES[phase]
        write_nbs(target, layers, length, TPS, TITLES[phase],
                  f"Boss theme of the necromancer, phase {phase} of 2: the same tempo, the same 52 bars and the same chords as the other phase (the game goes over at a bar line). "
                  + ("Natural instruments, half time." if phase == 1 else "Electronic instruments over the piano, double time."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
