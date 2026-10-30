#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "Shoulders of Stone, Unbound (Boss003)", the two phases of one boss fight.

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

D minor, 4/4, 150 BPM (10 ticks a second: a song tick is 2 server ticks; a tick is a sixteenth), 80 bars (2:08). Both songs are THE SAME PIECE: the same tempo, the same 80 bars, the same chord
in every bar, the same loop point (bar 5). The game goes from phase 1 to phase 2 by playing phase 2 from the same bar on, at the next bar line, with a crossfade (BgmChannel.playSceneSynced):
the fight does not restart a song, it changes the clothes of the one that plays. So the two songs may differ in everything but the harmony and the pulse.

Phase 1 is natural and heavy, in half time (the snare on the third beat only): piano (the harp) on the tune, bass guitar, guitar power chords, bass drum and snare, the didgeridoo for the earth.
Phase 2 is electronic and drives, in double time (the snare on 2 and 4, eighth notes on the hat): the same tune struck again on every eighth, the 8-bit square wave doubling it, the electric
piano and the 8-bit chip running arpeggios, the iron xylophone (a vibraphone) sparkling, the didgeridoo as a saw bass under the bass guitar.

The tools that make the boss louder than the other songs, used only where one thing must stand out:
  - a solo: the bridge (8 bars) is a bass solo in phase 1 (the motif, two octaves down: D3 A2 F2 D2) and an 8-bit solo in phase 2 (the same line two octaves up with a trill, then arpeggios and a chromatic run);
  - dissonance: two notes a half step (or a tritone) apart struck together and held, so that a chord rubs for a moment (the Eb chords, the bridge);
  - low notes: the bass solo, a swelling didgeridoo under the end of the bridge, a long A1 (the lowest the bass plays) at the end of the run-up;
  - sustained notes: a note block dies away at once, so a held note is struck again under it; where it must grow (the bridge) the loudness rises.

The motif of Atlas: D5 A4 F4 D4 - the falling tonic triad (a fourth, a major third, a minor third), as in the other songs of the cave.
Nothing here is random: the same files come out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM: a song tick is 2 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLES = {1: "Shoulders of Stone (Boss003)", 2: "Shoulders of Stone, Unbound (Boss003)"}
FILES = {1: "colossus_phase1.nbs", 2: "colossus_phase2.nbs"}
POLYPHONY = 8

# chord -> (bass root, bass fifth, guitar root, guitar fifth, six notes of the chord for the keys, low to high); all by what SOUNDS
CHORDS = {
    "Dm": (38, 45, 50, 57, [57, 62, 65, 69, 74, 77]),
    "Bb": (46, 53, 58, 65, [58, 62, 65, 70, 74, 77]),
    "Gm": (43, 50, 55, 62, [55, 58, 62, 67, 70, 74]),
    "A": (45, 52, 57, 64, [57, 61, 64, 69, 73, 76]),
    "F": (41, 48, 53, 60, [57, 60, 65, 69, 72, 77]),
    "C": (36, 43, 48, 55, [55, 60, 64, 67, 72, 76]),
    "Eb": (39, 46, 51, 58, [55, 58, 63, 67, 70, 75]),
}

INTRO_CHORDS = ["Dm", "Dm", "Gm", "A"]
INTRO_TUNE = ["r:16", "r:16", "D5:4 Bb4:4 G4:8", "E5:4 C#5:4 A4:8"]
A_CHORDS = ["Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "A", "A", "Dm", "Dm", "Bb", "Bb", "Gm", "A", "Dm", "Dm"]
A_TUNE = [
    "D5:6 A4:2 F4:4 D4:4",       # the motif of Atlas
    "F4:4 A4:4 D5:8",
    "D5:6 Bb4:2 F4:8",
    "D5:4 C5:4 Bb4:8",
    "G4:6 Bb4:2 D5:8",
    "D5:4 C5:4 Bb4:4 G4:4",
    "A4:6 C#5:2 E5:8",
    "E5:4 D5:4 C#5:8",
    "D5:6 A4:2 F4:4 D4:4",
    "F4:2 G4:2 A4:4 D5:8",
    "D5:6 Bb4:2 F4:4 Bb4:4",
    "C5:4 D5:4 F5:8",
    "D5:4 Bb4:4 G4:8",
    "A4:4 C#5:4 E5:4 A4:4",
    "D5:6 A4:2 F4:4 D4:4",
    "D4:8 r:8",
]
B_CHORDS = ["Bb", "F", "C", "Dm", "Bb", "F", "A", "A", "Bb", "F", "C", "Dm", "Gm", "Eb", "A", "A"]
B_TUNE = [
    "F5:4 D5:4 Bb4:4 D5:4",
    "C5:4 F5:4 C5:4 A4:4",
    "E5:4 C5:4 G4:4 C5:4",
    "D5:6 A4:2 F4:4 A4:4",
    "F5:4 D5:4 Bb4:4 D5:4",
    "A4:4 C5:4 F5:8",
    "E5:4 C#5:4 A4:4 C#5:4",
    "E5:12 r:4",
    "F5:4 D5:4 Bb4:4 D5:4",
    "C5:4 A4:4 F4:8",
    "E5:4 C5:4 G4:8",
    "D5:6 A4:2 F4:4 D4:4",
    "D5:4 Bb4:4 G4:8",
    "Eb5:4 Bb4:4 G4:8",
    "C#5:4 E5:4 A4:8",
    "E5:12 r:4",
]
BRIDGE_CHORDS = ["Dm", "Dm", "Eb", "Eb", "Gm", "Gm", "A", "A"]
SOLO = [                         # (the bass in phase 1; the 8-bit two octaves up in phase 2)
    "D3:6 A2:2 F2:4 D2:4",
    "F2:4 A2:4 D3:8",
    "Eb3:6 Bb2:2 G2:8",
    "Bb2:4 G2:4 Eb2:8",
    "G2:6 Bb2:2 D3:8",
    "D3:4 Bb2:4 G2:4 D2:4",
    "A2:4 C#3:4 E3:4 A2:4",
    "E3:12 r:4",
]
RUNUP_CHORDS = ["Bb", "Gm", "A", "A"]
RUNUP_TUNE = ["F5:6 D5:2 Bb4:8", "D5:6 Bb4:2 G4:8", "E5:4 C#5:4 A4:4 C#5:4", "E5:12 r:4"]

# two notes struck together and held (struck again), a half step or a tritone apart: (section, bar of the section, the lower note, the higher note)
CLASHES = [("B", 13, 74, 75), ("B2", 13, 74, 75), ("bridge", 2, 62, 63), ("bridge", 3, 63, 69), ("bridge", 7, 69, 75)]

# (name, chords, tune)
SECTIONS = [
    ("intro", INTRO_CHORDS, INTRO_TUNE),
    ("A", A_CHORDS, A_TUNE),
    ("B", B_CHORDS, B_TUNE),
    ("bridge", BRIDGE_CHORDS, None),
    ("A2", A_CHORDS, A_TUNE),
    ("B2", B_CHORDS, B_TUNE),
    ("runup", RUNUP_CHORDS, RUNUP_TUNE),
]


def put_over(layer, tick, instrument, sounding, vel):
    """A drum or a fill puts its note over what is there."""
    layer.notes.pop(tick, None)
    layer.put(tick, instrument, sounding, vel)


def build(phase):
    p2 = phase == 2
    lead = Layer("Piano (the tune)", 100, 100)
    hold = Layer("Piano (the tune held)", 60, 100)
    harmony = Layer("Piano (a third below)", 50, 80)
    chip = Layer("8-bit (doubles the tune)", 45, 120)
    pling = Layer("Electric piano (arpeggio)", 40, 90)
    sparkle = Layer("Iron xylophone (sparkle)", 45, 125)
    solo = Layer("Bass (solo)" if not p2 else "8-bit (solo)", 85 if not p2 else 70, 100)
    clash_a = Layer("Dissonance (lower)", 55, 90)
    clash_b = Layer("Dissonance (higher)", 55, 110)
    bass = Layer("Bass", 90, 100)
    low = Layer("Didgeridoo (the earth)" if not p2 else "Didgeridoo (saw bass)", 55, 100)
    guitar_root = Layer("Guitar (root)", 75, 60)
    guitar_fifth = Layer("Guitar (fifth)", 70, 70)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat", 55, 115)
    layers = [lead, hold, harmony, chip, pling, sparkle, solo, clash_a, clash_b, bass, low, guitar_root, guitar_fifth, kick, snare, hat]

    bar_number = 0
    for name, chords, tune in SECTIONS:
        count = len(chords)
        big = name in ("B", "B2")
        second = name in ("A2", "B2")
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            bass_root, bass_fifth, gtr_root, gtr_fifth, tones = CHORDS[chord]
            last = index == count - 1

            # ---- the bass
            if name == "bridge":
                pass  # (the solo; in phase 1 it is the bass itself)
            elif (name == "intro" and index < 2) or (name == "runup" and index == 3):
                pass  # (the last bar of the run-up: one long low A)
            elif p2:
                for tick in range(0, 16, 2):
                    bass.put_sound(start + tick, BASS, bass_fifth if tick in (6, 14) else bass_root, 74 if tick % 8 == 0 else 58 if tick % 4 == 0 else 46)
            else:
                for tick, note, vel in ((0, bass_root, 78), (6, bass_root, 56), (8, bass_root, 66), (14, bass_fifth, 50)):
                    bass.put_sound(start + tick, BASS, note, vel)

            # ---- the guitar
            if name in ("A", "A2", "B", "B2") or (name == "runup" and index >= 2):
                if p2 and name in ("A", "A2"):
                    stabs = [(tick, 30) for tick in range(0, 16, 2)]  # muted eighths
                elif big or name == "runup":
                    stabs = [(0, 54), (6, 46), (8, 52), (14, 44)]
                else:
                    stabs = [(0, 50), (8, 44)]
                for tick, vel in stabs:
                    guitar_root.put_sound(start + tick, GUITAR, gtr_root, vel)
                    if vel >= 40:
                        guitar_fifth.put_sound(start + tick, GUITAR, gtr_fifth, vel - 8)

            # ---- the drums
            if name == "intro":
                if p2 and index >= 2:
                    for tick in (0, 4, 8, 12):
                        kick.put(start + tick, BASEDRUM, 62, 80 if tick == 0 else 66)
                else:
                    kick.put(start, BASEDRUM, 62, 80)
                    kick.put(start + 8, BASEDRUM, 62, 70)
            elif name == "bridge":
                kick.put(start, BASEDRUM, 62, 72)
                if p2:
                    kick.put(start + 8, BASEDRUM, 62, 64)
                    snare.put(start + 4, SNARE, 66, 52)
                    snare.put(start + 12, SNARE, 66, 58)
                else:
                    snare.put(start + 8, SNARE, 66, 60)
            elif name == "runup" and index >= 2:
                for tick in range(0, 16, 2 if index == 2 else 1):
                    progress = (tick / 16.0 + (0 if index == 2 else 1)) / 2.0
                    put_over(snare, start + tick, SNARE, 66, 30 + int(60 * progress))
                if index == 2:
                    kick.put(start, BASEDRUM, 62, 80)
            else:
                if p2:
                    for tick in (0, 4, 8, 12) + ((14,) if big and second else ()):
                        kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                    for tick in (4, 12):
                        snare.put(start + tick, SNARE, 66, 76)
                    for tick in range(0, 16, 2):
                        hat.put(start + tick, HAT, 66, 36 if tick % 4 == 0 else 22)
                else:
                    kick.put(start, BASEDRUM, 62, 86)
                    kick.put(start + 6, BASEDRUM, 62, 60)
                    if big:
                        kick.put(start + 10, BASEDRUM, 62, 54)
                    snare.put(start + 8, SNARE, 66, 78)
                    for tick in range(0, 16, 2 if big else 4):
                        hat.put(start + tick, HAT, 66, 26 if tick % 4 == 0 else 18)
                if last and name != "runup":  # a fill into the next section
                    fill = [(8, 50), (10, 56), (12, 62), (13, 68), (14, 76), (15, 86)] if p2 else [(12, 56), (14, 70), (15, 84)]
                    for tick, vel in fill:
                        put_over(snare, start + tick, SNARE, 66, vel)

            # ---- the low notes
            if name == "bridge" and index >= 4:  # the earth rises under the end of the bridge
                swell(low, DIDGERIDOO, bass_root, start, BAR, 18 + (index - 4) * 10, 30 + (index - 4) * 12, 4)
            elif p2 and big:  # the saw bass under the chorus
                swell(low, DIDGERIDOO, bass_root, start, BAR, 30, 22, 4)
            elif not p2 and second and name == "A2" and index % 4 == 0:
                swell(low, DIDGERIDOO, bass_root, start, 8, 36, 28, 4)
            if name == "runup" and index == 3:  # the lowest the bass plays, held
                swell(bass, BASS, 33, start, BAR, 60, 90, 4)

            # ---- the solo
            if name == "bridge":
                tick = 0
                for note, length in parse_bar(SOLO[index]):
                    if note is not None:
                        if not p2:
                            solo.put_sound(start + tick, BASS, note, 88 if tick == 0 else 76)
                            for again in range(4, length, 4):
                                solo.put_sound(start + tick + again, BASS, note, 40)
                        elif index < 4:
                            solo.put_sound(start + tick, BIT, note + 24, 72 if tick == 0 else 64)
                            for again in range(1, length):  # a trill on the end of a long note, the rest held
                                if length >= 8 and again >= length - 4:
                                    solo.put_sound(start + tick + again, BIT, note + 24 + (2 if (again - length) % 2 == 0 else 0), 58)
                                elif again % 4 == 0:
                                    solo.put_sound(start + tick + again, BIT, note + 24, 36)
                    tick += length
                if p2 and index >= 4:
                    if index < 7:  # arpeggios of the chord, up and back
                        for step, tone_index in enumerate([0, 1, 2, 3, 4, 5, 4, 3, 2, 3, 4, 5, 4, 3, 2, 1]):
                            solo.put_sound(start + step, BIT, tones[tone_index], 52 + step if step else 70)
                    else:  # a chromatic run up to the top (the half steps rub against the chord on purpose)
                        for step in range(16):
                            solo.put_sound(start + step, BIT, 63 + step, 44 + step * 3)
                if not p2:  # the harp only marks the bar
                    lead.put_sound(start, HARP, tones[3], 40)
                    lead.put_sound(start + 8, HARP, tones[2], 32)

            # ---- the dissonance
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
                        lead.put_sound(at, HARP, note, 96 if strong else 88)
                        if p2:  # struck again on every eighth under a long note: the drive
                            for again in range(2 if length >= 6 else 4, length, 2 if length >= 6 else 4):
                                hold.put_sound(at + again, HARP, note, 54 - again * 2 if again < 8 else 40)
                            if big or second:
                                chip.put_sound(at, BIT, note, 40)
                            if big and strong and name in ("B", "B2") and index % 4 == 0 and note + 12 <= 78:
                                sparkle.put_sound(at + 2, IRON_XYLOPHONE, note, 40)
                        else:  # struck again, softer, every beat under a long note
                            for again in range(4, length, 4):
                                hold.put_sound(at + again, HARP, note, 52 - again * 2)
                            if second:
                                harmony.put_sound(at, HARP, third_below(note, D_MINOR), 44)
                    tick += length

            # ---- the arpeggios of phase 2
            if p2 and name in ("A2", "B", "B2"):
                for step, tone_index in enumerate([1, 3, 4, 5, 4, 3, 2, 3]):
                    pling.put_sound(start + step * 2 + 1, PLING, tones[tone_index], 26 if name == "B" else 30)
            bar_number += 1
    return layers, bar_number * BAR


D_MINOR = scale("D3 E3 F3 G3 A3 Bb3 C4 D4 E4 F4 G4 A4 Bb4 C5 D5 E5 F5")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
    out.mkdir(parents=True, exist_ok=True)
    for phase in (1, 2):
        layers, length = build(phase)
        removed = limit_polyphony(layers, POLYPHONY)
        target = out / FILES[phase]
        write_nbs(target, layers, length, TPS, TITLES[phase],
                  f"Boss theme, phase {phase} of 2: the same tempo, the same 80 bars and the same chords as the other phase (the game goes over at a bar line). "
                  + ("Natural instruments, half time, a bass solo." if phase == 1 else "Electronic instruments, double time, an 8-bit solo."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")
        for layer in layers:
            span = layer.sounding_range()
            if span and layer.notes:
                print(f"  {layer.name}: {len(layer.notes)} notes, sounds {span[0]}-{span[1]}")


if __name__ == "__main__":
    main()
