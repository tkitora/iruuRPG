#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the boss theme of the necromancer in two phases. The second version.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

The first version was a slow hymn (100 BPM, a ritual); the request (t45) was: faster, a new motif (forget the old one), and no ritual feeling because the necromancer fights with a SCYTHE.
So this is a chase and a swing, not a ceremony: dry, staccato, cutting, no echo hall, no clock, no hymn.
G minor (harmonic: F# over D7), 4/4, 150 BPM (one tick = a sixteenth; 10 ticks a second, so a song tick is exactly 2 server ticks), 80 bars (2:08). Two phases that are ONE piece (the same
tempo, the same 80 bars, the same chord in every bar, the same loop point): the game goes over from phase 1 to phase 2 at the next bar line, from the same bar (Bgm.playSceneSynced).
  - Phase 1: the natural instruments (piano, bass, guitar, bass drum, snare, hat), swung. The guitar plays the bridge as a solo.
  - Phase 2: the same piano with the electronic ones over it (8-bit on the tune, electric piano arpeggios, iron xylophone = vibraphone for the gleam of the blade), a four-on-the-floor kick,
    and an 8-bit solo in the bridge.
The swing of the scythe: the motif is three + three + two sixteenths (a fall, a fall, a flick), and the hat plays every THIRD tick (0 3 6 9 12 15) over the four beats of the kick and the snare:
three against four, a sweep across the bar. A sweeping run (six sixteenths falling through the chord) marks the end of every eight bars.
Form: intro (4 bars, once), A (16), B (16, the chorus), the bridge (8: the solo, half time), A' (16: the tonic pedal in the bass, the piano a third below), B' (16: everything), a coda (4) that ends on
D7 so that the jump back to A (G minor) is smooth. Progression: i - bVI - VII (Gm Eb F), a bII (Ab) once in B and in the bridge for the cold colour, and the V7 with the leading tone (D7, F#).

The motif of the necromancer (new): D5 Bb4 G4 | F#4 A4 D5 - a fall of a third and a third (3 + 3 + 2 ticks) and then a flick up through the leading tone to the top.
Rulebook items used where they fit: a solo (the bridge), a minor second and a tritone (once each), the lowest note (the end of the coda), sustain by striking again, grace notes, a tonic pedal point,
natural against electronic. Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM: a song tick is 2 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLES = {1: "Reaper's Arc (Boss004)", 2: "Reaper's Arc, Sundered (Boss004)"}
FILES = {1: "necromancer_phase1.nbs", 2: "necromancer_phase2.nbs"}
POLYPHONY = 8
G_MINOR = scale("G3 A3 Bb3 C4 D4 Eb4 F4 G4 A4 Bb4 C5 D5 Eb5 F5 G5")
TONIC = 31          # G1: the pedal point (and G2 an octave up)

# chord -> (bass root, bass fifth, six notes of the chord for the piano, low to high); all by what SOUNDS (the bass F#1 to F#3, the piano F#3 to F#5)
CHORDS = {
    "Gm": (43, 50, [55, 58, 62, 67, 70, 74]),
    "Eb": (39, 46, [55, 58, 63, 67, 70, 75]),
    "F": (41, 48, [57, 60, 65, 69, 72, 77]),
    "D7": (38, 45, [57, 60, 66, 69, 72, 74]),
    "Cm": (36, 43, [55, 60, 63, 67, 72, 75]),
    "Bb": (46, 53, [58, 62, 65, 70, 74, 77]),
    "Ab": (44, 51, [56, 60, 63, 68, 72, 75]),
}
SWING = [0, 3, 6, 9, 12, 15]       # every third tick: three against four

INTRO_CHORDS = ["Gm", "Gm", "Gm", "D7"]
INTRO_TUNE = ["r:16", "r:16", "D5:3 Bb4:3 G4:2 F#4:2 A4:2 D5:4", "A4:3 C5:3 F#4:2 A4:2 C5:2 D5:4"]
A_CHORDS = ["Gm", "Gm", "Eb", "F", "Gm", "Gm", "Eb", "D7", "Cm", "Cm", "Gm", "Gm", "Eb", "F", "D7", "D7"]
A_TUNE = [
    "D5:3 Bb4:3 G4:2 F#4:2 A4:2 D5:4",     # the motif
    "Bb4:3 G4:3 D4:2 G4:2 Bb4:2 D5:4",
    "Eb5:3 Bb4:3 G4:2 Bb4:2 Eb5:2 G4:4",
    "C5:3 A4:3 F4:2 A4:2 C5:2 F5:4",
    "D5:3 Bb4:3 G4:2 F#4:2 A4:2 D5:4",
    "D5:3 Bb4:3 G4:2 D5:2 Bb4:2 G4:4",
    "Bb4:3 G4:3 Eb4:2 G4:2 Bb4:2 Eb5:4",
    "F#4:3 A4:3 C5:2 A4:2 F#4:2 D4:4",
    "Eb5:3 C5:3 G4:2 C5:2 Eb5:2 G4:4",
    "C5:3 Eb5:3 G4:2 Eb5:2 C5:2 G4:4",
    "D5:3 Bb4:3 G4:2 F#4:2 A4:2 D5:4",
    "D5:3 G4:3 Bb4:2 D5:6 r:2",
    "Eb5:4 Bb4:4 G4:4 Bb4:4",
    "F5:4 C5:4 A4:4 C5:4",
    "A4:3 C5:3 F#4:2 A4:2 C5:2 D5:4",
    "D5:4 A4:4 F#4:4 A4:4",
]
B_CHORDS = ["Eb", "F", "Gm", "Gm", "Eb", "F", "Ab", "D7", "Cm", "Bb", "Eb", "F", "Cm", "D7", "Gm", "Gm"]
B_TUNE = [
    "G4:2 Bb4:2 Eb5:4 Bb4:4 G4:4",
    "A4:2 C5:2 F5:4 C5:4 A4:4",
    "D5:6 Bb4:2 G4:8",
    "D5:3 Bb4:3 G4:2 F#4:2 A4:2 D5:4",
    "Bb4:2 Eb5:2 G4:4 Bb4:4 Eb5:4",
    "C5:2 F5:2 A4:4 C5:4 F5:4",
    "Ab4:4 C5:4 Eb5:4 C5:4",
    "F#4:2 A4:2 C5:4 A4:2 D5:2 F#5:4",
    "C5:6 G4:2 Eb4:8",
    "D5:6 Bb4:2 F4:8",
    "Eb5:4 G4:4 Bb4:4 Eb5:4",
    "F5:4 C5:4 A4:8",
    "G4:2 C5:2 Eb5:4 C5:2 G4:2 Eb4:4",
    "A4:2 C5:2 F#5:4 D5:2 C5:2 A4:4",
    "D5:3 Bb4:3 G4:2 F#4:2 A4:2 D5:4",
    "G4:12 r:4",
]
BRIDGE_CHORDS = ["Gm", "Gm", "Ab", "Ab", "Cm", "Cm", "D7", "D7"]
SOLO = [                      # the guitar sounds F#2 to F#4; the 8-bit plays the same an octave up
    "G3:4 Bb3:4 D4:4 Bb3:4",
    "F#4:3 D4:3 Bb3:2 D4:2 F#4:2 D4:4",
    "Ab3:4 C4:4 Eb4:4 C4:4",
    "Eb4:6 C4:2 Ab3:8",
    "C4:4 Eb4:4 G3:4 C4:4",
    "Eb4:3 C4:3 G3:2 C4:2 Eb4:2 G3:4",
    "A3:2 C4:2 F#4:4 D4:2 C4:2 A3:4",
    "F#4:8 r:8",
]
CODA_CHORDS = ["Eb", "F", "Ab", "D7"]
CODA_TUNE = ["Bb4:3 G4:3 Eb4:2 G4:2 Bb4:2 Eb5:4", "A4:3 F4:3 C5:2 A4:2 F4:2 C5:4", "C5:4 Eb5:4 Ab4:8", "A4:2 C5:2 F#4:4 A4:4 D5:4"]

# two notes held and struck again a little: (section, bar of the section, the lower note, the higher note): a tritone in the bridge (C and F#), a half step in the last chorus (G4 and Ab4)
CLASHES = [("bridge", 5, 60, 66), ("B2", 15, 67, 68)]

# (name, chords, tune)
SECTIONS = [
    ("intro", INTRO_CHORDS, INTRO_TUNE),
    ("A", A_CHORDS, A_TUNE),
    ("B", B_CHORDS, B_TUNE),
    ("bridge", BRIDGE_CHORDS, None),
    ("A2", A_CHORDS, A_TUNE),
    ("B2", B_CHORDS, B_TUNE),
    ("coda", CODA_CHORDS, CODA_TUNE),
]


def octave(note):
    """The same note an octave up, or an octave down where the bass cannot go up (it sounds F#1 to F#3)."""
    return note + 12 if note + 12 <= 54 else note - 12


def put_over(layer, tick, instrument, sounding, vel):
    """A drum or a fill puts its note over what is there."""
    layer.notes.pop(tick, None)
    layer.put(tick, instrument, sounding, vel)


def build(phase):
    p2 = phase == 2
    lead = Layer("Piano (the tune)", 100, 100)
    hold = Layer("Piano (the tune held)", 60, 100)
    grace = Layer("Piano (grace note)", 50, 100)
    harmony = Layer("Piano (a third below)", 50, 80)
    chip = Layer("8-bit (doubles the tune)", 40, 120)
    pad = Layer("Piano (arpeggio)" if not p2 else "Electric piano (arpeggio)", 45, 90)
    sparkle = Layer("Iron xylophone (the gleam)", 45, 125)
    solo = Layer("Guitar (solo)" if not p2 else "8-bit (solo)", 80 if not p2 else 60, 100)
    sweep = Layer("Piano (the sweep)", 55, 110)
    clash_a = Layer("Rub (lower)", 50, 90)
    clash_b = Layer("Rub (higher)", 50, 110)
    bass = Layer("Bass", 90, 100)
    growl = Layer("Didgeridoo (growl)", 50, 100)
    guitar_root = Layer("Guitar (root)", 70, 60)
    guitar_fifth = Layer("Guitar (fifth)", 65, 70)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat (the sweep of the scythe)", 55, 115)
    layers = [lead, hold, grace, harmony, chip, pad, sparkle, solo, sweep, clash_a, clash_b, bass, growl, guitar_root, guitar_fifth, kick, snare, hat]

    bar_number = 0
    for name, chords, tune in SECTIONS:
        count = len(chords)
        fuller = name in ("A2", "B2")
        big = name == "B2"
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            bass_root, bass_fifth, tones = CHORDS[chord]
            last = index == count - 1

            # ---- the bass
            if name == "bridge":
                for tick in (0, 8):
                    bass.put_sound(start + tick, BASS, octave(bass_root), 56)
            elif name == "intro" and index < 2:
                bass.put_sound(start, BASS, bass_root, 60)
                bass.put_sound(start + 6, BASS, bass_root, 46)
            elif name == "coda" and last:  # the lowest note of the song, held and growing
                swell(bass, BASS, 38, start, BAR, 60, 92, 4)
                swell(growl, DIDGERIDOO, 38, start, BAR, 30, 62, 4)
            elif name == "A2":  # the tonic pedal point: sixteenths on G with an octave jump, whatever the chord
                for tick in range(16):
                    bass.put_sound(start + tick, BASS, TONIC + 12 if tick % 4 == 2 else TONIC, 72 if tick % 8 == 0 else 54 if tick % 4 == 0 else 44)
            elif p2:  # eighths, the root jumping an octave
                for step in range(8):
                    bass.put_sound(start + step * 2, BASS, octave(bass_root) if step % 2 else bass_root, (76 if step == 0 else 62) + (4 if big else 0))
            else:  # the swing: the root, the root, the fifth, the root an octave up
                for tick, note, vel in ((0, bass_root, 78), (3, bass_root, 54), (6, bass_fifth, 60), (10, octave(bass_root), 50), (12, bass_root, 56)):
                    bass.put_sound(start + tick, BASS, note, vel)

            # ---- the guitar: dry stabs
            if name in ("A2", "B", "B2", "coda"):
                if p2 and name == "A2":
                    stabs = [(tick, 30) for tick in range(0, 16, 2)]
                elif p2 or big:
                    stabs = [(0, 54), (3, 40), (6, 48), (10, 44), (12, 50)]
                else:
                    stabs = [(0, 52), (6, 44), (10, 40)]
                for tick, vel in stabs:
                    guitar_root.put_sound(start + tick, GUITAR, bass_root + 12, vel)
                    if vel >= 44:
                        guitar_fifth.put_sound(start + tick, GUITAR, bass_fifth + 12, vel - 8)

            # ---- the drums
            if name == "bridge":  # half time under the solo
                kick.put(start, BASEDRUM, 62, 74)
                snare.put(start + 8, SNARE, 66, 66)
                for tick in range(0, 16, 4):
                    hat.put(start + tick, HAT, 66, 22)
            elif name == "intro" and index < 2:
                for tick in SWING:
                    hat.put(start + tick, HAT, 66, 24 if tick % 6 == 0 else 16)
                kick.put(start, BASEDRUM, 62, 76)
            else:
                if p2:
                    for tick in (0, 4, 8, 12) + ((14,) if big else ()):
                        kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                    for tick in (4, 12):
                        snare.put(start + tick, SNARE, 66, 76)
                    if big:  # the chain
                        for tick in (2, 6, 10, 14, 15):
                            snare.put(start + tick, SNARE, 66, 22 if tick != 15 else 30)
                else:
                    for tick, vel in ((0, 86), (3, 52), (10, 66)) if name != "A" else ((0, 82), (10, 62)):
                        kick.put(start + tick, BASEDRUM, 62, vel)
                    for tick in (4, 12):
                        snare.put(start + tick, SNARE, 66, 76)
                for tick in SWING:  # the hat: every third tick, three against four
                    hat.put(start + tick, HAT, 66, (36 if tick % 6 == 0 else 24) if name != "A" else 20)
                if last and name in ("B", "A2", "B2", "coda"):  # a fill into the next section
                    fill = [(8, 50), (10, 56), (12, 62), (13, 68), (14, 76), (15, 86)] if p2 or name in ("B2", "coda") else [(12, 56), (14, 70), (15, 84)]
                    for tick, vel in fill:
                        put_over(snare, start + tick, SNARE, 66, vel)

            # ---- the pad: a soft arpeggio in eighths (A'), in eighths up and down (B')
            if name == "A2" or (name == "B" and p2):
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 3, 2, 1]):
                    pad.put_sound(start + step * 2 + 1, PLING if p2 else HARP, tones[tone_index], 24)
            if big and p2:  # a faster, brighter arpeggio: eighths on the off-ticks, up the whole chord and back
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 5, 4, 3]):
                    pad.put_sound(start + step * 2 + 1, PLING, tones[tone_index], 34)

            # ---- the sweep: a run of six sixteenths falling through the chord, at the end of every eight bars of A, B and their repeats
            if name in ("A", "B", "A2", "B2") and index % 8 == 7:
                for step, tone_index in enumerate([5, 4, 3, 2, 1, 0]):
                    sweep.put_sound(start + 10 + step, HARP, tones[tone_index], 46 - step * 4)

            # ---- the solo
            if name == "bridge":
                tick = 0
                for note, length in parse_bar(SOLO[index]):
                    if note is not None:
                        if not p2:
                            solo.put_sound(start + tick, GUITAR, note, 88 if tick == 0 else 78)
                            for again in range(4, length, 4):
                                solo.put_sound(start + tick + again, GUITAR, note, 42)
                        else:
                            solo.put_sound(start + tick, BIT, note + 12, 74 if tick == 0 else 66)
                            for again in range(1, length):  # a trill on the end of a long note, the rest held
                                if length >= 6 and again >= length - 4:
                                    solo.put_sound(start + tick + again, BIT, min(78, note + 12 + (2 if (again - length) % 2 == 0 else 0)), 58)
                                elif again % 2 == 0:
                                    solo.put_sound(start + tick + again, BIT, note + 12, 34)
                    tick += length
                lead.put_sound(start, HARP, tones[3], 40)
                lead.put_sound(start + 8, HARP, tones[2], 32)

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
                        lead.put_sound(at, HARP, note, 92 if strong else 84)
                        for again in range(4, length, 4):  # a long note is struck again, softer
                            hold.put_sound(at + again, HARP, note, 52 - again * 2)
                        if strong and index % 4 == 0 and at > 0 and note + 1 <= 78 and name in ("A", "B", "A2", "B2"):
                            grace.put_sound(at - 1, HARP, note + 1, 52)  # a grace note: a half step above, one tick before
                        if fuller or name == "coda":
                            harmony.put_sound(at, HARP, max(54, third_below(note, G_MINOR)), 44)
                        if p2 and name in ("B", "B2", "A2", "coda"):
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
                  f"Boss theme of the necromancer (a scythe), phase {phase} of 2: the same tempo, the same 80 bars and the same chords as the other phase (the game goes over at a bar line). "
                  + ("Natural instruments, swung, a guitar solo." if phase == 1 else "Electronic instruments over the piano, a 16th drive, an 8-bit solo."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
