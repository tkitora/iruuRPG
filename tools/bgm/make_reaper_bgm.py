#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "Silence of the Scythe, Unleashed (Boss005)", a second boss theme for the same boss as Boss004 (the reaper on the snow mountain).

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

A different answer to the same boss (t49: "make it as Boss005, with the new analysis"), so it is a NEW pair of songs next to Boss004, not a replacement. Boss004 (Reaper's Arc) is an arc that is played full from the
start. This one is built on the analysis that was sent: SUBTRACTION, and the same melody in two disguises.
  - The motif is five notes, a PENDULUM, the swing of a scythe: the root, the step below, the fifth below, the step below, the root again (4 + 2 + 4 + 2 + 4 ticks) - B A F# A B on the tonic. It is the only
    melody: on each chord it is played from that chord's note, and a bar of it is answered by a held note and a rest. It is only a fourth wide.
  - Phase 1, the stalking: ONLY the melody (the piano), a heavy low refrain on the bass, and a click (the hat). Nothing else for the first 16 bars: "melody and bass only". Then the drums and guitar come in.
  - Phase 2, the same melody in the disguise of danger: the bass and the didgeridoo get heavier, the guitar chops it in short strokes (the pizzicato / staccato of the analysis), the electric piano stabs on the off-beats,
    and the whole piece is a HALF STEP HIGHER (B minor -> C minor): "the key goes up a half step when the boss is hurt".
  - The same notes, the same bars, the same chords in both phases (only moved a half step), so the game goes over at the next bar line (Bgm.playSceneSynced).
  - Silence: the last beat of every eight bars is a break for every instrument; the bridge is a SOLO (the piano, then the 8-bit) over the diminished chord (C# E G), with long rests and the echo of a struck note left
    to die away (the solo has the long gaps of the analysis).
  - The mid range is kept clear: the melody is the piano at F#4 to F#5, the bass is low, the guitar stabs are few.
B minor (the F# chord has the harmonic A#), 4/4, 150 BPM (one tick = a sixteenth; 10 ticks a second, so a song tick is exactly 2 server ticks), 80 bars (2:08).
Form: intro (4 bars, once), A (16), B (16, the chorus: the pendulum a third higher), the bridge (8: the solo), A' (16), B' (16), a coda (4) that ends on F# so that the jump back to A (B minor) is smooth.
Chords: i - VI - VII (Bm G A), iv (Em), V (F#), and the diminished ii (C#dim) once in the bridge. Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM: a song tick is 2 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLES = {1: "Silence of the Scythe (Boss005)", 2: "Silence of the Scythe, Unleashed (Boss005)"}
FILES = {1: "reaper_phase1.nbs", 2: "reaper_phase2.nbs"}
POLYPHONY = 8
SHIFT = {1: 0, 2: 1}   # phase 2 is a half step higher
S = scale("B3 C#4 D4 E4 F#4 G4 A4 B4 C#5 D5 E5 F#5")   # B minor, from B3: the notes of the melody are picked by their place in it

# chord -> (bass root, bass fifth, six notes of the chord for the piano, low to high); all by what SOUNDS in phase 1 (the bass F#1 to F#3, the piano F#3 to F#5; nothing above F5 so that a half step up still fits)
CHORDS = {
    "Bm": (47, 54, [54, 59, 62, 66, 71, 74]),
    "G": (43, 50, [55, 59, 62, 67, 71, 74]),
    "A": (45, 52, [57, 61, 64, 69, 73, 76]),
    "Em": (40, 47, [55, 59, 64, 67, 71, 76]),
    "F#": (42, 49, [54, 58, 61, 66, 70, 73]),
    "C#dim": (37, 43, [55, 61, 64, 67, 73, 76]),
}


def bar_text(kind, place):
    """One bar of the melody from its kind and the place of its anchor note in S: P = the pendulum, H = the anchor held (then a rest), U = three steps up, R = rest."""
    if kind == "R":
        return "r:16"
    if kind == "H":
        return f"{note_name(S[place])}:12 r:4"
    if kind == "U":
        return f"{note_name(S[place])}:4 {note_name(S[place + 1])}:4 {note_name(S[place + 2])}:8"
    return " ".join(f"{note_name(S[place + offset])}:{length}" for offset, length in ((0, 4), (-1, 2), (-3, 4), (-1, 2), (0, 4)))


def tune(bars):
    return [bar_text(kind, place) for kind, place in bars]


INTRO_CHORDS = ["Bm", "Bm", "Bm", "F#"]
INTRO_TUNE = tune([("R", 0), ("R", 0), ("P", 7), ("H", 4)])
A_CHORDS = ["Bm", "Bm", "G", "G", "Bm", "Bm", "A", "F#", "Bm", "Bm", "G", "A", "Em", "G", "A", "F#"]
A_TUNE = tune([("P", 7), ("H", 7), ("P", 5), ("U", 5), ("P", 7), ("H", 7), ("P", 6), ("P", 4),
               ("P", 7), ("H", 7), ("P", 5), ("P", 6), ("P", 3), ("P", 5), ("P", 6), ("H", 4)])
B_CHORDS = ["G", "A", "Bm", "Bm", "G", "A", "F#", "F#", "Em", "G", "A", "Bm", "Em", "F#", "Bm", "Bm"]
B_TUNE = tune([("P", 7), ("P", 8), ("P", 9), ("P", 9), ("P", 7), ("P", 8), ("P", 8), ("H", 8),
               ("P", 7), ("P", 7), ("P", 8), ("P", 9), ("P", 7), ("P", 8), ("P", 9), ("H", 9)])
BRIDGE_CHORDS = ["Bm", "Bm", "C#dim", "C#dim", "G", "G", "F#", "F#"]
SOLO = [                       # slow, with long rests (the sounding notes: the piano in phase 1, the 8-bit in phase 2)
    "B4:8 r:8",
    "D5:4 C#5:4 A4:8",
    "C#5:8 r:8",
    "E5:8 C#5:8",
    "B4:12 r:4",
    "D5:4 B4:4 G4:8",
    "A#4:8 C#5:8",
    "F#4:8 r:8",
]
CODA_CHORDS = ["G", "A", "F#", "F#"]
CODA_TUNE = tune([("H", 7), ("H", 8), ("P", 4), ("H", 4)])

# two notes held and struck again a little: (section, bar of the section, the lower note, the higher note), by what sounds in phase 1: a tritone in the bridge (C# and G over the diminished chord's
# neighbour) and a half step in the last chorus (B4 and C5 over Bm)
CLASHES = [("bridge", 3, 61, 67), ("B2", 15, 71, 72)]

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


def guitar_note(note):
    """The guitar sounds F#2 to F#4: a note above that goes down an octave."""
    return note if note <= 66 else note - 12


def build(phase):
    p2 = phase == 2
    up = SHIFT[phase]
    lead = Layer("Piano (the pendulum)", 100, 100)
    hold = Layer("Piano (the melody held)", 60, 100)
    harmony = Layer("Piano (a third below)", 50, 80)
    chip = Layer("8-bit (doubles the melody)", 40, 120)
    stab = Layer("Electric piano (stabs)", 40, 100)
    flake = Layer("Piano (a snowflake)", 45, 125)
    solo = Layer("Piano (solo)" if not p2 else "8-bit (solo)", 80 if not p2 else 60, 100)
    echo = Layer("Piano (echo of the solo)", 40, 150)
    clash_a = Layer("Rub (lower)", 50, 90)
    clash_b = Layer("Rub (higher)", 50, 110)
    bass = Layer("Bass (the refrain)", 90, 100)
    growl = Layer("Didgeridoo (growl)", 50, 100)
    chop_root = Layer("Guitar (chops, root)", 70, 60)
    chop_fifth = Layer("Guitar (chops, fifth)", 65, 70)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat (the click)", 55, 115)
    layers = [lead, hold, harmony, chip, stab, flake, solo, echo, clash_a, clash_b, bass, growl, chop_root, chop_fifth, kick, snare, hat]

    breaks = []
    bar_number = 0
    for name, chords, melody in SECTIONS:
        count = len(chords)
        second = name in ("A2", "B2")
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
            alone = name == "A" or (name in ("intro", "bridge"))   # "melody and bass only"

            # ---- the bass: a heavy refrain (the same pattern every bar), low
            if name == "bridge":
                for tick in (0, 8):
                    bass.put_sound(start + tick, BASS, bass_root, 54)
            elif name == "intro" and index < 2:
                bass.put_sound(start, BASS, bass_root, 62)
                bass.put_sound(start + 8, BASS, bass_root, 46)
            elif name == "coda" and last:  # the lowest note of the song, held and growing
                swell(bass, BASS, 35 + up, start, BAR, 60, 92, 4)   # low B
                swell(growl, DIDGERIDOO, 35 + up, start, BAR, 30, 62, 4)
            elif p2:  # eighths on the root, an octave jump every other one
                for step in range(8):
                    bass.put_sound(start + step * 2, BASS, octave(bass_root) if step % 2 else bass_root, (78 if step == 0 else 62) + (4 if big else 0))
                if big and index % 4 == 0:   # the earth growls under the chorus, now and then
                    swell(growl, DIDGERIDOO, bass_root, start, 8, 30, 22, 4)
            else:  # the refrain: root, root, root, up, fifth
                for tick, note, vel in ((0, bass_root, 80), (6, bass_root, 56), (8, bass_root, 64), (11, octave(bass_root), 46), (14, bass_fifth, 50)):
                    bass.put_sound(start + tick, BASS, note, vel)

            # ---- the guitar: in phase 1 a few stabs on the chorus; in phase 2 short chops (the pizzicato)
            if name in ("B", "A2", "B2", "coda") and not (name == "coda" and last):
                if p2:
                    chops = [(0, 54), (3, 40), (6, 48), (8, 52), (11, 40), (14, 46)] if name != "A2" else [(tick, 34) for tick in range(0, 16, 2)]
                else:
                    chops = [(0, 50), (8, 42)]
                for tick, vel in chops:
                    chop_root.put_sound(start + tick, GUITAR, guitar_note(bass_root + 12), vel)
                    if vel >= 44:
                        chop_fifth.put_sound(start + tick, GUITAR, guitar_note(bass_fifth + 12), vel - 8)
            if p2 and name in ("B", "B2"):   # the stabs on the off-beats
                for tick in (2, 6, 10, 14):
                    stab.put_sound(start + tick, PLING, tones[3 if tick % 8 == 2 else 4], 30)

            # ---- the drums: a click first, the weight later
            if name == "bridge":
                kick.put(start, BASEDRUM, 62, 70)
                for tick in range(0, 16, 4):
                    hat.put(start + tick, HAT, 66, 18)
            elif alone or name == "intro":
                for tick in (0, 4, 8, 12):
                    hat.put(start + tick, HAT, 66, 18)
                if name != "intro" or index >= 2:
                    kick.put(start, BASEDRUM, 62, 72)
            else:
                if p2 or second:
                    for tick in (0, 4, 8, 12):
                        kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                    for tick in (4, 12):
                        snare.put(start + tick, SNARE, 66, 76)
                else:
                    for tick, vel in ((0, 84), (6, 58), (10, 64)):
                        kick.put(start + tick, BASEDRUM, 62, vel)
                    snare.put(start + 8, SNARE, 66, 72)
                for tick in range(0, 16, 2):
                    hat.put(start + tick, HAT, 66, 34 if tick % 4 == 0 else 20)

            # ---- a snowflake: one high soft note every two bars of the quiet parts
            if name in ("A", "bridge") and index % 2 == 1:
                flake.put_sound(start + 10, HARP if not p2 else IRON_XYLOPHONE, tones[5], 22)

            # ---- the solo: with long rests, and the echo of each struck note
            if name == "bridge":
                tick = 0
                for note, length in parse_bar(SOLO[index]):
                    if note is not None:
                        if not p2:
                            solo.put_sound(start + tick, HARP, note, 86 if tick == 0 else 78)
                            for again in range(4, length, 4):
                                solo.put_sound(start + tick + again, HARP, note, 42)
                            if start + tick + 6 < (bar_number + 1) * BAR + BAR:
                                echo.put_sound(start + tick + 6, HARP, note, 22)
                        else:
                            solo.put_sound(start + tick, BIT, note + up, 72 if tick == 0 else 64)
                            for again in range(2, length, 2):
                                solo.put_sound(start + tick + again, BIT, note + up, 32)
                    tick += length

            # ---- the rubs
            for (section, bar, lower, higher) in CLASHES:
                if section == name and bar == index:
                    instrument = BIT if p2 else HARP
                    for again in range(0, 8, 4):
                        clash_a.put_sound(start + again, instrument, lower + up, 46 - again * 2)
                        clash_b.put_sound(start + again, instrument, higher + up, 46 - again * 2)

            # ---- the melody
            if melody is not None:
                tick = 0
                for note, length in parse_bar(melody[index]):
                    if note is not None:
                        at = start + tick
                        note += up
                        lead.put_sound(at, HARP, note, 92 if tick == 0 else 84)
                        for again in range(4, length, 4):  # a long note is struck again, softer
                            hold.put_sound(at + again, HARP, note, 52 - again * 2)
                        if second or name == "coda":
                            harmony.put_sound(at, HARP, max(54, third_below(note - up, S) + up), 44)
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
                  f"Second boss theme of the reaper, phase {phase} of 2: the same tempo, the same 80 bars and the same chords as the other phase (a half step higher in phase 2); the game goes over at a bar line. "
                  + ("Melody and bass only at first, a piano solo." if phase == 1 else "The same melody in short chops and a heavy bass, an 8-bit solo."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
