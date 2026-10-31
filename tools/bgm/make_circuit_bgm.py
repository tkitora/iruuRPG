#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)", a boss theme that is built by SUBTRACTION and with only five instruments.

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

Written from the summary that was sent with the request (t68; the summary of what the scores of XavierEXE show - a core melody and a few strong parts, breaks that reset the ear, instruments chosen by the world
of the piece, and the clear attack of the note block). Four rules from it, followed to the letter:
  1. FEW ROLES AT ONCE: bass, rhythm guitar, lead, drums - four roles, and no more than four sound together (the lead's held note struck again and, in the loud places, a lead a third below, are the lead's own).
     Each has its own range, by what SOUNDS: the bass F#1 to B2 (below 48), the guitar G3 to F4 (53 to 66), the lead G4 to F5 (67 to 77); nothing else is in these ranges, nothing fills the middle.
  2. ONLY FIVE INSTRUMENTS in the whole song, in both phases: the bass, the guitar, the pling, the kick and the snare. The two that are set against each other are the guitar (the plucked string: pizzicato, a
     classical sound) and the pling (the digital electric piano): "sacred but mad" - the lead sings a slow, clear hymn (long notes, steps and fifths) while the guitar plucks restless rolls (triplet eighths, ten notes a second) under it.
  3. THE BREAK at two thirds (bar 64 of the 100 of the loop): the bass and the drums stop for eight whole bars; the pling plays the hymn alone, slowly, with long rests; the guitar comes back for the last four bars
     and the whole band lands on the first bar of the last section. Before each of the other sections there is a bar of GAP: the drums alone (a roll), or one single note of the lead (and nothing else).
  4. THE ATTACK STAYS CLEAR: every note is struck once and short (the guitar a pluck, the bass a hit, the drums tight: a kick and a snare, no hat, no sustain pad); a long note of the lead is struck again every
     four ticks, softer, so that it does not blur.
D Dorian (D minor with the B natural: the G chord is major), 4/4, 200 BPM (20 ticks a second: a song tick is one server tick; a quarter note is 6 ticks, an eighth 3, a triplet eighth 2; a bar is 24 ticks; the t69 request asked for a faster version of the 150 BPM one:
the next tempo the grid has - 171 would put the eighths between two ticks), an intro of 64 ticks (a drum roll of 16 ticks and two bars, once) and 100 bars of loop (2:03). Chords: Dm G F C, Bb C Dm A, and
the climb Dm Bb Gm A. Phase 2 is the same piece a HALF STEP HIGHER (E-flat Dorian), the same five instruments, with the kick on every beat and the guitar rolling a section earlier: overclocked.
Bar lines: four bars (96 ticks) are six bars of the game (16 ticks), so the game goes over from one phase to the other at a bar line of its own that falls on a bar of the song only every third time;
the two phases are the same in tempo and position, so the rhythm goes on.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0          # 200 BPM: a quarter note is 6 ticks, an eighth 3, a triplet eighth 2; one tick is one server tick
LOOP_START_BAR = 4  # the intro (4 bars) is played once
TITLES = {1: "Holy Circuit (Boss010)", 2: "Holy Circuit, Overclocked (Boss010)"}
FILES = {1: "circuit_phase1.nbs", 2: "circuit_phase2.nbs"}
POLYPHONY = 8
SHIFT = {1: 0, 2: 1}   # phase 2 is a half step higher
BARLEN = 24            # a bar of 4/4: four quarter notes of 6 ticks (the tunes below are written in old ticks, 16 to the bar, and scaled by 1.5)

# chord -> (bass root, the notes of the guitar: all between 53 and 66)
CHORDS = {
    "Dm": (38, [53, 57, 62, 65]),
    "G": (43, [55, 59, 62]),
    "F": (41, [53, 57, 60, 65]),
    "C": (36, [55, 60, 64]),
    "Bb": (34, [58, 62, 65]),
    "Gm": (43, [55, 58, 62]),
    "A": (33, [57, 61, 64]),
}

A_CHORDS = ["Dm", "Dm", "G", "G", "F", "F", "C", "C", "Dm", "Dm", "G", "G", "Bb", "Bb", "A", "A"]
A_TUNE = [                      # the hymn: long, clear notes, steps and fifths; the last bar is the gap (the drums alone)
    "A4:8 D5:8", "E5:8 D5:4 A4:4", "B4:8 D5:8", "C5:8 B4:4 G4:4", "A4:8 C5:8", "D5:8 C5:4 A4:4", "G4:8 C5:8", "E5:12 r:4",
    "A4:8 D5:8", "F5:8 E5:4 D5:4", "D5:8 B4:8", "G4:6 B4:2 D5:8", "F5:8 D5:8", "D5:8 Bb4:4 D5:4", "E5:8 C#5:8", "r:16",
]
B_CHORDS = ["Bb", "C", "Dm", "Dm", "Bb", "C", "A", "A", "Gm", "Gm", "C", "C", "F", "A", "Dm", "Dm"]
B_TUNE = [                      # the same hymn in eighths: the stairs; the last bar is the gap of ONE note
    "F5:4 D5:2 F5:2 D5:4 Bb4:4", "E5:4 C5:2 E5:2 C5:4 G4:4", "D5:4 F5:4 A4:8", "D5:2 E5:2 F5:4 E5:4 D5:4",
    "F5:4 D5:2 F5:2 Bb4:8", "E5:4 G4:2 C5:2 E5:8", "E5:4 C#5:2 E5:2 A4:8", "C#5:4 E5:4 A4:4 C#5:4",
    "D5:4 Bb4:2 D5:2 G4:8", "Bb4:4 D5:4 F5:8", "E5:4 C5:2 E5:2 G4:4 E5:4", "C5:8 B4:4 C5:4",
    "A4:4 C5:4 F5:8", "E5:4 C#5:4 A4:4 E5:4", "D5:8 A4:8", "D5:4 r:12",
]
C_CHORDS = ["Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "A", "A", "Dm", "F", "Gm", "A", "Bb", "C", "A", "A"]
C_TUNE = [                      # the climb; the last bar is the gap (the drums alone)
    "A4:2 D5:2 F5:4 D5:2 A4:2 D5:4", "E5:2 F5:2 A4:4 D5:8", "D5:2 F5:2 D5:2 Bb4:2 D5:4 F5:4", "F5:4 D5:4 Bb4:8",
    "D5:2 G4:2 Bb4:4 D5:4 F5:4", "F5:4 D5:4 Bb4:4 G4:4", "E5:2 C#5:2 A4:4 C#5:4 E5:4", "C#5:2 D5:2 E5:4 C#5:4 A4:4",
    "D5:4 F5:4 A4:4 D5:4", "C5:4 F5:4 A4:4 C5:4", "Bb4:4 D5:4 G4:4 Bb4:4", "C#5:4 E5:4 A4:4 C#5:4",
    "F5:4 D5:4 Bb4:4 D5:4", "G4:4 C5:4 E5:4 C5:4", "E5:4 C#5:4 A4:4 E5:4", "r:16",
]
BREAK_CHORDS = ["Dm", "Dm", "G", "G", "F", "F", "A", "A"]
BREAK_TUNE = [                  # the solo: the hymn alone, slow, with long rests
    "A4:6 r:2 D5:8", "F5:12 r:4", "B4:8 r:8", "D5:6 C5:2 B4:8", "A4:8 r:8", "C5:6 A4:2 r:8", "C#5:8 E5:8", "E5:12 r:4",
]
OUTRO_CHORDS = ["Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "Dm", "F", "Gm", "A", "A", "A"]
OUTRO_TUNE = ["D5:8 A4:8", "F5:8 D5:8", "D5:8 Bb4:8", "F5:8 D5:8", "Bb4:8 G4:8", "D5:8 Bb4:8", "A4:8 D5:8", "C5:8 A4:8", "Bb4:8 G4:8", "E5:8 C#5:8", "E5:12 r:4", "r:16"]

# name: (chords, tune, bass, guitar mode, drums mode, lead mode, gap of the last bar)
# guitar: none, eighths, rolls, slow; drums: none, a, b, c, sparse; lead: single, third (with a lead a third below in the loud places)
SECTIONS = [
    ("A", A_CHORDS, A_TUNE, True, "eighths", "a", "single", "drums"),
    ("B", B_CHORDS, B_TUNE, True, "rolls", "b", "single", "single"),
    ("C", C_CHORDS, C_TUNE, True, "rolls", "c", "single", "drums"),
    ("A2", A_CHORDS, A_TUNE, True, "rolls", "b", "third", "drums"),
    ("break", BREAK_CHORDS, BREAK_TUNE, False, "slow", "none", "single", None),
    ("final", A_CHORDS, A_TUNE, True, "rolls", "c", "third", "drums"),
    ("outro", OUTRO_CHORDS, OUTRO_TUNE, True, "slow", "sparse", "single", "drums"),
]
A_MINOR = scale("G4 A4 B4 C5 D5 E5 F5")


def parse(text):
    """A bar of the tunes below ('A4:8 D5:8': 16 old ticks) -> [(midi or None, ticks), ...] scaled to the 24 ticks of a bar."""
    return [(note, length * 3 // 2) for note, length in parse_bar(text)]


def scaled(ticks):
    return tuple(tick * 3 // 2 for tick in ticks)


def build(phase):
    p2 = phase == 2
    up = SHIFT[phase]
    lead = Layer("Pling (the hymn)", 100, 100)
    hold = Layer("Pling (the hymn held)", 55, 100)
    harmony = Layer("Pling (a third below)", 45, 90)
    bass = Layer("Bass (hits)", 95, 100)
    guitar = Layer("Guitar (pizzicato)", 75, 60)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    layers = [lead, hold, harmony, bass, guitar, kick, snare]

    def fifth(root):
        """The fifth of a bass root: above it, or below where that would leave the bass range (below 48)."""
        return root + 7 if root + 7 <= 47 + up else root - 5

    def put(layer, tick, instrument, sounding, vel):
        if tick not in layer.notes:
            layer.put_sound(tick, instrument, sounding, max(1, min(100, int(round(vel)))))

    def drum(layer, tick, instrument, key_, vel):
        if tick not in layer.notes:
            layer.put(tick, instrument, key_, max(1, min(100, int(round(vel)))))

    def bar_content(start, chord, drums, bass_on, guitar_mode, gap, bar_in_section):
        """Everything but the lead of one bar of 24 ticks."""
        root, tones = CHORDS[chord]
        root += up
        tones = [tone + up for tone in tones]
        if gap == "drums":                                      # the drums alone: a roll that grows
            drum(kick, start, BASEDRUM, 62, 84)
            for tick in range(4, BARLEN, 2):
                drum(snare, start + tick, SNARE, 66, 24 + tick * 2)
            return
        if gap == "single":                                     # nothing but the one note of the lead
            return
        if bass_on:                                             # three hits: the root, the root, the fifth
            for tick, note, vel in ((0, root, 90), (9, root, 62), (15, fifth(root), 68)):
                put(bass, start + tick, BASS, note, vel)
        if guitar_mode == "eighths":
            for number, tick in enumerate(range(0, BARLEN, 3)):
                put(guitar, start + tick, GUITAR, tones[(0, 2, 1, 2)[number % 4] % len(tones)], 58 if tick % 6 == 0 else 42)
        elif guitar_mode == "rolls":                            # triplet eighths (2 ticks): a rolling gallop, ten notes a second
            for number, tick in enumerate(range(0, BARLEN, 2)):
                put(guitar, start + tick, GUITAR, tones[(0, 1, 2)[number % 3] % len(tones)], 60 if number % 3 == 0 else 36)
        elif guitar_mode == "slow":                             # one pluck a beat
            for number, tick in enumerate(range(0, BARLEN, 6)):
                put(guitar, start + tick, GUITAR, tones[number % len(tones)], 44)
        # drums: tight, a kick and a snare only
        if drums == "a":
            kicks, snares = scaled((0, 10)), scaled((4, 12))
        elif drums == "b":
            kicks, snares = scaled((0, 6, 10)), scaled((4, 12))
        elif drums == "c":
            kicks, snares = scaled((0, 6, 8, 10)), scaled((4, 12))
        elif drums == "sparse":
            kicks, snares = (0,), (12,)
        else:
            kicks, snares = (), ()
        if p2 and drums in ("b", "c"):
            kicks = tuple(sorted(set(kicks) | {0, 6, 12, 18}))
        for tick in kicks:
            drum(kick, start + tick, BASEDRUM, 62, 86 if tick == 0 else 66)
        for tick in snares:
            drum(snare, start + tick, SNARE, 66, 76)
        if drums == "c" and bar_in_section % 4 == 3:          # a short roll at the end of the fourth bar
            for tick in (20, 22):
                drum(snare, start + tick, SNARE, 66, 40)

    # ---- the intro (64 ticks): 16 ticks of a drum roll that grows, a bar of the bass and the drums, a bar with the guitar and one note of the lead
    drum(kick, 0, BASEDRUM, 62, 78)
    for tick in range(4, 16, 2):
        drum(snare, tick, SNARE, 66, 30 + tick * 3)
    for bar in range(2):
        start = 16 + bar * BARLEN
        root, tones = CHORDS["Dm"]
        root += up
        tones = [tone + up for tone in tones]
        for tick, vel in zip(scaled((0, 10)), (84, 64)):
            drum(kick, start + tick, BASEDRUM, 62, vel)
        for tick in scaled((4, 12)):
            drum(snare, start + tick, SNARE, 66, 70)
        for tick, note, vel in ((0, root, 88), (9, root, 60), (15, fifth(root), 66)):
            put(bass, start + tick, BASS, note, vel)
        if bar == 1:
            for number, tick in enumerate(range(0, BARLEN, 6)):
                put(guitar, start + tick, GUITAR, tones[number % len(tones)], 44)
            put(lead, start + 12, PLING, 76 + up, 70)   # E5

    position = 64
    for name, chords, tune, bass_on, guitar_mode, drums, lead_mode, last_gap in SECTIONS:
        count = len(chords)
        for index, chord in enumerate(chords):
            start = position
            gap = last_gap if index == count - 1 else None
            if name == "break":
                bass_here, drums_here = False, "none"
                mode = "slow" if index >= 4 else "none"
            else:
                bass_here, drums_here, mode = bass_on, drums, guitar_mode
            if p2 and name == "A":
                mode = "rolls"
            bar_content(start, chord, drums_here, bass_here, mode, gap, index)

            # ---- the lead
            if gap == "drums":
                position += BARLEN
                continue
            tick = 0
            for note, length in parse(tune[index]):
                if note is not None:
                    at = start + tick
                    note += up
                    put(lead, at, PLING, note, 90 if tick == 0 else 80)
                    for again in range(6, length, 6):    # a long note is struck again every beat (6 ticks), softer
                        put(hold, at + again, PLING, note, 52 - again)
                    if lead_mode == "third" and not gap:
                        put(harmony, at, PLING, max(67 + up, third_below(note - up, A_MINOR) + up), 42)
                tick += length
                if gap == "single":                      # only the first note of the bar
                    break
            position += BARLEN
    return layers, position


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
    out.mkdir(parents=True, exist_ok=True)
    for phase in (1, 2):
        layers, length = build(phase)
        assert length % BAR == 0, length
        removed = limit_polyphony(layers, POLYPHONY)
        target = out / FILES[phase]
        write_nbs(target, layers, length, TPS, TITLES[phase],
                  f"Boss theme with five instruments, phase {phase} of 2: the same tempo, the same bars and the same chords as the other phase (a half step higher in phase 2); the game goes over at a bar line. "
                  + ("The pling sings a hymn over plucked guitar, the bass and a tight kick and snare." if phase == 1 else "The same five instruments, the kick on every beat, the guitar rolling."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
