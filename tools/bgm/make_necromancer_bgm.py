#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the boss theme of the reaper on the snow mountain. The third version.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

Why a third version (t48): the second one jumped up and down too much and had no unity. So this one is built to be plain and tidy, as the other boss songs are (Boss001, Boss003):
  - ONE motif, an arc: A4 C5 E5 D5 C5 B4 A4 (up by thirds to the top, down by steps) - the swing of a scythe. It is only a fifth wide (A4 to E5). Every phrase is this arc, moved to the
    chord (F: F A C ... E; G: G B D ... G) or answered by a falling line. No leap is larger than a third except the one back to the root.
  - The whole tune lies in D4 to E5 (a ninth). The chorus holds long notes on the arc's top (C5 D5 E5), and each phrase ends where the next one begins (step to step), so the line does not saw up and down.
  - The chords are the plain ones: i - VI - VII - i (Am F G Am), with iv (Dm) and V (E, the G# of the harmonic minor) - nothing else. The same eight chords make A and its repeat, and the chorus.
  - The gaps are planned (the guide for the server: at a fast tempo the break is short and sudden): at the end of every eight bars EVERY instrument stops for the last beat, so that the next downbeat
    hits; and the answer-bars of the arc end on a rest.
  - Nothing else moves the pitch: no run, no grace note, no counter-melody. The piano plays the tune and the same tune a third below in the repeats.
The background (from the request): a mid-level boss on a snow mountain, a side dungeon off the story line, a "strong enemy" for its level, a reaper with a scythe and no face - cold, thin, dry
and quick, not a ceremony. Snowflakes: one high soft note every two bars (the piano in phase 1, the iron xylophone in phase 2).

A minor (the harmonic G# on the E chord), 4/4, 150 BPM (one tick = a sixteenth; 10 ticks a second, so a song tick is exactly 2 server ticks), 80 bars (2:08). Two phases that are ONE piece (the same
tempo, the same 80 bars, the same chord in every bar, the same loop point): the game goes over from phase 1 to phase 2 at the next bar line, from the same bar (Bgm.playSceneSynced). As the guide for
the server says, phase 2 is a HALF STEP HIGHER (A minor -> B-flat minor): the same notes moved up, for more energy.
  - Phase 1: the natural instruments (piano, bass, guitar, bass drum, snare, hat). The guitar plays the bridge as a solo.
  - Phase 2: the same piano with the electronic ones over it (8-bit on the tune, electric piano arpeggios, iron xylophone), a four-on-the-floor kick, and an 8-bit solo in the bridge.
Form: intro (4 bars, once), A (16), B (16, the chorus), the bridge (8: the solo, half time, the tritone), A' (16: the tonic pedal in the bass, the piano a third below), B' (16: everything),
a coda (4) that ends on E so that the jump back to A (A minor) is smooth.
Rulebook items used where they fit: a solo (the bridge), a minor second and a tritone (once each), the lowest note (the end of the coda), sustain by striking again, the tonic pedal point,
natural against electronic, a key change up a half step for the second phase. Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM: a song tick is 2 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLES = {1: "Reaper's Arc (Boss004)", 2: "Reaper's Arc, Sundered (Boss004)"}
FILES = {1: "necromancer_phase1.nbs", 2: "necromancer_phase2.nbs"}
POLYPHONY = 8
SHIFT = {1: 0, 2: 1}   # phase 2 is a half step higher
A_MINOR = scale("A3 B3 C4 D4 E4 F4 G4 A4 B4 C5 D5 E5")
TONIC = 33             # A1: the pedal point (and A2 an octave up)

# chord -> (bass root, bass fifth, six notes of the chord for the piano, low to high); all by what SOUNDS in phase 1 (the bass F#1 to F#3, the piano F#3 to F#5)
CHORDS = {
    "Am": (45, 52, [57, 60, 64, 69, 72, 76]),
    "F": (41, 48, [57, 60, 65, 69, 72, 77]),
    "G": (43, 50, [55, 59, 62, 67, 71, 74]),
    "E": (40, 47, [56, 59, 64, 68, 71, 76]),
    "Dm": (38, 45, [57, 62, 65, 69, 74, 77]),
}

ARC = "A4:2 C5:2 E5:4 D5:2 C5:2 B4:2 A4:2"      # the motif
INTRO_CHORDS = ["Am", "Am", "Am", "E"]
INTRO_TUNE = ["r:16", "r:16", ARC, "B4:2 C5:2 B4:4 G#4:4 r:4"]
A_CHORDS = ["Am", "Am", "F", "G", "Am", "Am", "F", "E", "Dm", "Dm", "Am", "Am", "F", "G", "E", "E"]
A_TUNE = [
    ARC,
    "B4:2 C5:2 B4:4 A4:4 r:4",              # the answer ends on a rest
    "F4:2 A4:2 C5:4 A4:2 G4:2 F4:2 E4:2",   # the arc on F
    "G4:2 B4:2 D5:4 C5:2 B4:2 A4:2 G4:2",   # the arc on G
    ARC,
    "B4:2 C5:2 D5:4 C5:2 B4:2 A4:4",
    "F4:2 A4:2 C5:4 A4:2 G4:2 F4:2 E4:2",
    "E4:2 G#4:2 B4:4 A4:2 E4:2 r:4",
    "D4:2 F4:2 A4:4 G4:2 F4:2 E4:2 D4:2",   # the arc on Dm
    "F4:2 A4:2 D5:4 C5:2 A4:2 F4:4",
    ARC,
    "B4:2 C5:2 B4:4 A4:4 r:4",
    "C5:4 A4:4 F4:4 A4:4",
    "B4:4 G4:4 D4:4 G4:4",
    "G#4:4 B4:4 E5:4 B4:4",
    "E5:4 D5:2 B4:2 G#4:4 r:4",
]
B_CHORDS = ["F", "G", "Am", "Am", "F", "G", "E", "E", "Dm", "F", "G", "Am", "Dm", "E", "Am", "Am"]
B_TUNE = [                                    # the chorus: the top of the arc held on each chord, the phrases joined step to step (no phrase ends far from where the next begins)
    "C5:6 A4:2 C5:8",
    "D5:6 B4:2 D5:8",
    "E5:6 C5:2 A4:8",
    ARC,
    "C5:6 A4:2 C5:8",
    "D5:6 B4:2 D5:8",
    "E5:6 B4:2 G#4:8",
    "B4:4 G#4:4 B4:4 r:4",
    "D5:6 A4:2 D5:8",
    "C5:6 A4:2 C5:8",
    "B4:6 D5:2 B4:8",
    "C5:4 B4:4 A4:8",
    "D5:4 C5:4 A4:8",
    "B4:4 G#4:4 E4:4 G#4:4",
    ARC,
    "A4:12 r:4",
]
BRIDGE_CHORDS = ["Am", "Am", "F", "F", "Dm", "Dm", "E", "E"]
SOLO = [                      # the arc an octave down, then plain lines (the guitar sounds F#2 to F#4; the 8-bit plays the same an octave up)
    "A3:2 C4:2 E4:4 D4:2 C4:2 B3:2 A3:2",
    "B3:2 C4:2 B3:4 A3:4 r:4",
    "F3:2 A3:2 C4:4 A3:2 G3:2 F3:2 E3:2",
    "A3:4 C4:4 A3:8",
    "D4:4 F4:4 E4:4 D4:4",
    "C4:4 A3:4 D4:8",
    "G#3:2 B3:2 E4:4 D4:2 B3:2 G#3:4",
    "B3:8 r:8",
]
CODA_CHORDS = ["F", "G", "E", "E"]
CODA_TUNE = ["C5:6 A4:2 C5:8", "D5:6 B4:2 D5:8", "E5:4 B4:4 G#4:4 B4:4", "G#4:8 r:8"]

# two notes held and struck again a little: (section, bar of the section, the lower note, the higher note), by what sounds in phase 1: a tritone in the bridge (B and F over E) and a
# half step in the last chorus (A4 and Bb4 over Am)
CLASHES = [("bridge", 7, 59, 65), ("B2", 15, 69, 70)]

# the bars whose last beat is a break: every instrument stops (section, bar of the section)
BREAKS = [("A", 7), ("A", 15), ("B", 7), ("A2", 7), ("A2", 15), ("B2", 7), ("bridge", 7), ("coda", 3)]

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


def build(phase):
    p2 = phase == 2
    up = SHIFT[phase]
    lead = Layer("Piano (the tune)", 100, 100)
    hold = Layer("Piano (the tune held)", 60, 100)
    harmony = Layer("Piano (a third below)", 50, 80)
    chip = Layer("8-bit (doubles the tune)", 40, 120)
    pad = Layer("Piano (arpeggio)" if not p2 else "Electric piano (arpeggio)", 45, 90)
    flake = Layer("Piano (a snowflake)" if not p2 else "Iron xylophone (a snowflake)", 45, 125)
    solo = Layer("Guitar (solo)" if not p2 else "8-bit (solo)", 80 if not p2 else 60, 100)
    clash_a = Layer("Rub (lower)", 50, 90)
    clash_b = Layer("Rub (higher)", 50, 110)
    bass = Layer("Bass", 90, 100)
    growl = Layer("Didgeridoo (growl)", 50, 100)
    guitar_root = Layer("Guitar (root)", 70, 60)
    guitar_fifth = Layer("Guitar (fifth)", 65, 70)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat", 55, 115)
    layers = [lead, hold, harmony, chip, pad, flake, solo, clash_a, clash_b, bass, growl, guitar_root, guitar_fifth, kick, snare, hat]

    breaks = []
    bar_number = 0
    for name, chords, tune in SECTIONS:
        count = len(chords)
        fuller = name in ("A2", "B2")
        big = name == "B2"
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            bass_root, bass_fifth, tones = CHORDS[chord]
            bass_root += up
            bass_fifth += up
            tones = [tone + up for tone in tones]
            last = index == count - 1
            if (name, index) in BREAKS:
                breaks.append(start)

            # ---- the bass
            if name == "bridge":
                for tick in (0, 8):
                    bass.put_sound(start + tick, BASS, octave(bass_root), 56)
            elif name == "intro" and index < 2:
                bass.put_sound(start, BASS, bass_root, 60)
                bass.put_sound(start + 8, BASS, bass_root, 46)
            elif name == "coda" and last:  # the lowest note of the song, held and growing
                swell(bass, BASS, TONIC + 5 + up, start, BAR, 60, 92, 4)   # low E
                swell(growl, DIDGERIDOO, TONIC + 5 + up, start, BAR, 30, 62, 4)
            elif name == "A2":  # the tonic pedal point: sixteenths on A with an octave jump, whatever the chord
                for tick in range(16):
                    bass.put_sound(start + tick, BASS, TONIC + up + 12 if tick % 4 == 2 else TONIC + up, 72 if tick % 8 == 0 else 54 if tick % 4 == 0 else 44)
            elif p2 or name in ("B", "B2"):  # eighths, the root jumping an octave
                for step in range(8):
                    bass.put_sound(start + step * 2, BASS, octave(bass_root) if step % 2 else bass_root, (76 if step == 0 else 62) + (4 if big else 0))
            else:
                for tick, note, vel in ((0, bass_root, 78), (6, bass_root, 54), (8, bass_fifth, 60), (14, bass_root, 46)):
                    bass.put_sound(start + tick, BASS, note, vel)

            # ---- the guitar: dry stabs
            if name in ("A2", "B", "B2", "coda"):
                if p2 and name == "A2":
                    stabs = [(tick, 30) for tick in range(0, 16, 2)]
                elif p2 or big:
                    stabs = [(0, 54), (6, 46), (8, 52), (14, 44)]
                else:
                    stabs = [(0, 52), (8, 44)]
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
            elif name == "intro":
                for tick in (0, 4, 8, 12):
                    hat.put(start + tick, HAT, 66, 22)
                if index >= 2:
                    kick.put(start, BASEDRUM, 62, 76)
            else:
                if p2 or fuller:
                    for tick in (0, 4, 8, 12):
                        kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                else:
                    for tick, vel in ((0, 84), (6, 58), (10, 64)) if name == "B" else ((0, 82), (8, 62)):
                        kick.put(start + tick, BASEDRUM, 62, vel)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 76)
                for tick in range(0, 16, 2 if name != "A" or p2 else 4):
                    hat.put(start + tick, HAT, 66, 36 if tick % 4 == 0 else 22)

            # ---- the pad: a soft arpeggio in eighths (A'), brighter in the last chorus of phase 2
            if name == "A2" or (big and p2):
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 3, 2, 1]):
                    pad.put_sound(start + step * 2 + 1, PLING if p2 else HARP, tones[tone_index], 26 if name == "A2" else 34)

            # ---- a snowflake: one high soft note every two bars (never over the tune: the piano in phase 1, the iron xylophone in phase 2)
            if name in ("A", "A2", "bridge") and index % 2 == 1:
                flake.put_sound(start + 10, IRON_XYLOPHONE if p2 else HARP, tones[5], 24)

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
                            solo.put_sound(start + tick, BIT, note + 12 + up, 74 if tick == 0 else 66)
                            for again in range(2, length, 2):
                                solo.put_sound(start + tick + again, BIT, note + 12 + up, 34)
                    tick += length
                lead.put_sound(start, HARP, tones[3], 40)
                lead.put_sound(start + 8, HARP, tones[2], 32)

            # ---- the rubs
            for (section, bar, lower, higher) in CLASHES:
                if section == name and bar == index:
                    instrument = BIT if p2 else HARP
                    for again in range(0, 8, 4):
                        clash_a.put_sound(start + again, instrument, lower + up, 46 - again * 2)
                        clash_b.put_sound(start + again, instrument, higher + up, 46 - again * 2)

            # ---- the tune
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        at = start + tick
                        note += up
                        lead.put_sound(at, HARP, note, 92 if tick == 0 else 84)
                        for again in range(4, length, 4):  # a long note is struck again, softer
                            hold.put_sound(at + again, HARP, note, 52 - again * 2)
                        if fuller or name == "coda":
                            harmony.put_sound(at, HARP, max(54, third_below(note - up, A_MINOR) + up), 44)
                        if p2 and name in ("B", "B2", "A2", "coda"):
                            chip.put_sound(at, BIT, note, 34)
                    tick += length
            bar_number += 1

    # the breaks: every instrument stops for the last beat of the bar
    for start in breaks:
        for layer in layers:
            for tick in range(start + 12, start + BAR):
                layer.notes.pop(tick, None)
    return layers, bar_number * BAR


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
    out.mkdir(parents=True, exist_ok=True)
    for phase in (1, 2):
        layers, length = build(phase)
        removed = limit_polyphony(layers, POLYPHONY)
        target = out / FILES[phase]
        write_nbs(target, layers, length, TPS, TITLES[phase],
                  f"Boss theme of the reaper on the snow mountain, phase {phase} of 2: the same tempo, the same 80 bars and the same chords as the other phase (a half step higher in phase 2); "
                  "the game goes over at a bar line. " + ("Natural instruments, a guitar solo." if phase == 1 else "Electronic instruments over the piano, an 8-bit solo."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
