#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "Waltz at the Edge of Dark, Out of Step (Boss008)", a boss theme in THREE beats: a dramatic, classical waltz of death.

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

Written from the analysis that was sent with the request (t64): elegance and despair together - a heavy, tight waltz, a melody that leaps and then falls, a half-step bass cliche, and the dominant that throws the energy back
onto the first beat of the minor chord.
  - Meter: 3/4. A bar is 12 ticks (a quarter note is 4 ticks, 150 BPM = 10 ticks a second, so a song tick is exactly 2 server ticks; the 160-180 of the analysis is not a tempo the grid has - 150 is the fastest one it
    has and is inside the 130-160 of the style guide). Four bars are 48 ticks = three bars of the engine, so every 4 bars of the waltz end on a bar line of the game and the two phases can be changed over there.
  - The weight stays on the first beat: the bass (and the kick, and in the loud places the didgeridoo) strikes ONCE a bar, on the 1. Beats 2 and 3 are the sharp steps: the snare, the guitar and the iron
    xylophone (staccato chords). The bass does not wander; the melody above it does.
  - The melody: an arpeggio up (A C E), the octave LEAP (E4 -> E5) and a held note that is struck again softly, and the fall - stairs down in eighths and sixteenths, the sigh at the end of every phrase. It is only
    harp (the "piano"); the iron xylophone answers it a beat later (the strings of the analysis chasing the piano) in the second half and in the climax. Nothing is higher than F5 (so that a half step up still fits
    the harp), except a quiet flute an octave above in the climax.
  - Harmony: A minor (G# the leading tone). The half-step bass cliche C B Bb A (Am/C, E7/B, Bb, Am with E7 on the third beat) is the A section; the B section is the lament, the circle of fifths down (Am Dm G C F
    Bdim E7 Am); in the stumble (interlude) the bass falls A G# G F# under the same Am. Every phrase ends with E7 -> Am: the dominant goes into the first beat of the next minor chord, also at the loop point.
  - The stumble (interlude, 8 bars): the feet lose the 1-2-3. The melody starts off the beat and the kick, the bass and the snare fall in groups of TWO beats over the bars (a hemiola: 1-2 / 3-4 / 5-6 over two bars), and
    only at the end of the interlude the downbeat is found again.
  - Silence: the last half beat of the phrase-ending bars is a break for every instrument (a short, sudden stop); the bridge is a SOLO (the harp alone over a quiet bass, long rests) that ends on a stop of a whole
    beat before the final climb.
Two phases that are ONE piece (the same tempo, bars of the same length, the same chords, the same loop point): the game goes over at the next bar line from the same place (Bgm.playSceneSynced). Phase 2 is a HALF
STEP HIGHER (A minor -> Bb minor), with the 8-bit doubling the melody, a kick on every beat and a hat in eighths: the steps get frantic.
A minor, 3/4, 150 BPM, intro (a pickup of one beat and five bars, once; the loop starts at tick 64, a bar line of the game) and 96 bars of the waltz (2:01 in all). Nothing here is random: the same file comes
out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM: a quarter note is 4 ticks, a song tick is 2 server ticks
LOOP_START_BAR = 4  # tick 64: the pickup (4 ticks) and five bars of 3 beats are played once
TITLES = {1: "Waltz at the Edge of Dark (Boss008)", 2: "Waltz at the Edge of Dark, Out of Step (Boss008)"}
FILES = {1: "waltz_phase1.nbs", 2: "waltz_phase2.nbs"}
POLYPHONY = 8
SHIFT = {1: 0, 2: 1}   # phase 2 is a half step higher
BAR3 = 12
A_MINOR = scale("A3 B3 C4 D4 E4 F4 G4 A4 B4 C5 D5 E5 F5")

# chord -> (bass root, six notes of the chord for the stabs, low to high); all by what SOUNDS in phase 1 (the bass F#1 to F#3, the harp F#3 to F#5: nothing above F5 so that a half step up still fits)
CHORDS = {
    "Am": (33, [57, 60, 64, 69, 72, 76]),
    "Am/C": (36, [57, 60, 64, 69, 72, 76]),
    "Am/G#": (32, [57, 60, 64, 69, 72, 76]),     # the cliche of the interlude: the same chord, the bass falls
    "Am/G": (31, [57, 60, 64, 69, 72, 76]),
    "Am/F#": (30, [57, 60, 64, 69, 72, 76]),
    "E7/B": (35, [56, 59, 62, 64, 68, 71]),
    "E7": (40, [56, 59, 62, 64, 68, 71]),
    "Bb": (34, [58, 62, 65, 70, 74, 77]),
    "Dm": (38, [57, 62, 65, 69, 74, 77]),
    "Dm/C": (36, [57, 62, 65, 69, 74, 77]),
    "G": (43, [55, 59, 62, 67, 71, 74]),
    "C": (36, [55, 60, 64, 67, 72, 76]),
    "F": (41, [57, 60, 65, 69, 72, 77]),
    "Bdim": (47, [57, 59, 62, 65, 69, 71]),
}


def parse(text, size):
    """'C5:2 Eb5:2 r:8' -> [(midi or None, ticks), ...]; the ticks must fill the bar of ``size`` ticks."""
    result = []
    total = 0
    for part in text.split():
        name, length = part.split(":")
        result.append((None if name == "r" else midi(name), int(length)))
        total += int(length)
    assert total == size, f"bar '{text}' has {total} ticks, not {size}"
    return result


def bars(chords, tune, sizes=None):
    sizes = sizes or [BAR3] * len(chords)
    tune = tune or [None] * len(chords)
    return list(zip(chords, sizes, tune))


# "X+E7": the chord X for two beats and E7 on the third (the dominant that throws the energy onto the next first beat)
INTRO = [("Am", 4, None)] + bars(["Am", "Am", "Dm", "Bb", "E7"], [None, None, "A4:4 D5:4 F5:4", "D5:4 F5:4 Bb4:4", "G#4:4 B4:4 D5:4"])

A8 = ["Am/C", "E7/B", "Bb", "Am+E7", "Dm", "Bb", "E7", "Am+E7"]
A_CHORDS = A8 + A8
A_TUNE = [                      # the arpeggio up, the leap, the fall; the second 8 bars are the same with the stairs
    "A4:4 C5:4 E5:4", "E5:6 D5:2 B4:4", "F5:6 D5:2 Bb4:4", "C5:4 A4:4 G#4:4",
    "A4:4 D5:4 F5:4", "Bb4:4 D5:4 F5:4", "E5:4 D5:4 B4:4", "A4:8 G#4:4",
    "A4:4 C5:4 E5:2 D5:1 C5:1", "E5:4 D5:2 C5:1 B4:1 D5:4", "F5:4 D5:4 Bb4:4", "C5:2 B4:2 A4:4 G#4:4",
    "F5:6 E5:2 D5:4", "D5:2 F5:2 D5:4 Bb4:4", "E5:6 D5:2 B4:2 G#4:2", "A4:8 B4:2 G#4:2",
]
B8 = ["Am", "Dm", "G", "C", "F", "Bdim", "E7", "Am+E7"]
B_CHORDS = B8 + B8
B_TUNE = [                      # the lament: the circle of fifths down; the leap of an octave and the long fall
    "E4:4 E5:8", "D5:6 C5:2 A4:4", "B4:6 A4:2 G4:4", "G4:4 C5:4 E5:4",
    "F5:4 E5:2 D5:2 C5:4", "D5:4 B4:4 F4:4", "G#4:4 B4:4 D5:4", "C5:6 B4:2 G#4:4",
    "E4:4 A4:2 C5:2 E5:4", "F5:4 E5:2 D5:2 C5:4", "D5:4 B4:2 A4:2 G4:4", "E5:6 D5:2 C5:4",
    "F5:4 C5:4 A4:4", "D5:2 F5:2 D5:2 B4:2 F4:4", "G#4:2 B4:2 D5:2 E5:2 B4:4", "A4:8 G#4:4",
]
I_CHORDS = ["Am", "Am/G#", "Am/G", "Am/F#", "Dm", "Bb", "E7", "E7"]
I_TUNE = [                      # the stumble: everything starts off the beat
    "r:2 A4:4 C5:4 E5:2", "E5:2 D5:2 C5:4 B4:4", "r:2 C5:4 B4:4 A4:2", "A4:2 G4:2 F#4:4 E4:4",
    "r:2 D5:4 F5:4 E5:2", "D5:2 Bb4:4 D5:4 F5:2", "r:2 G#4:4 B4:4 D5:2", "D5:2 B4:2 G#4:4 B4:4",
]
BRIDGE_CHORDS = ["Dm", "Dm/C", "Bdim", "E7", "Am", "F", "Bdim", "E7"]
BRIDGE_TUNE = [                 # the solo: long notes, long rests
    "A4:8 r:4", "D5:4 C5:8", "D5:8 r:4", "G#4:4 B4:8",
    "C5:8 r:4", "A4:4 C5:8", "F5:6 D5:2 B4:4", "G#4:12",
]
C_CHORDS = A8
C_TUNE = [                      # the climb: the same cliche, in fast stairs, the whole band
    "A4:2 C5:2 E5:4 D5:1 C5:1 B4:1 A4:1", "E5:2 D5:2 B4:2 G#4:2 B4:2 D5:2", "F5:4 D5:2 Bb4:2 D5:2 F5:2", "E5:2 C5:2 A4:2 C5:2 B4:2 G#4:2",
    "F5:2 E5:2 D5:2 C5:2 A4:2 D5:2", "D5:2 F5:2 D5:2 Bb4:2 D5:2 F5:2", "E5:4 D5:2 B4:2 G#4:2 B4:2", "A4:4 C5:2 B4:2 G#4:4",
]
CODA_CHORDS = ["Dm", "Bb", "Dm", "E7", "Am/C", "E7/B", "Bb", "E7"]
CODA_TUNE = [
    "F5:6 E5:2 D5:4", "D5:4 F5:4 D5:4", "A4:4 D5:4 F5:4", "E5:4 D5:4 B4:4",
    "C5:6 B4:2 A4:4", "G#4:4 B4:4 D5:4", "Bb4:8 r:4", "G#4:8 r:4",
]

# two notes held and struck again a little: (section, bar of the section, the lower note, the higher note), by what sounds in phase 1: the half step of the cliche (G# and A over the Am of the interlude)
CLASHES = [("interlude", 1, 68, 69), ("bridge", 3, 62, 68)]

# the breaks: the whole band stops from this tick of the bar to its end (section, bar of the section, from tick)
STOPS = [("A", 7, 10), ("A", 15, 10), ("B", 7, 10), ("B", 15, 10), ("A2", 7, 10), ("A2", 15, 10), ("B2", 7, 10), ("B2", 15, 10), ("interlude", 7, 10),
         ("bridge", 7, 8), ("climb", 7, 10), ("coda", 7, 4)]

# (name, bars)
SECTIONS = [
    ("intro", INTRO),
    ("A", bars(A_CHORDS, A_TUNE)),
    ("B", bars(B_CHORDS, B_TUNE)),
    ("interlude", bars(I_CHORDS, I_TUNE)),
    ("A2", bars(A_CHORDS, A_TUNE)),
    ("B2", bars(B_CHORDS, B_TUNE)),
    ("bridge", bars(BRIDGE_CHORDS, BRIDGE_TUNE)),
    ("climb", bars(C_CHORDS, C_TUNE)),
    ("coda", bars(CODA_CHORDS, CODA_TUNE)),
]

CHASE = 4   # ticks: the iron xylophone answers the harp a beat later


def guitar_note(note):
    """The guitar sounds F#2 to F#4: a note above that goes down an octave."""
    while note > 66:
        note -= 12
    return note


def build(phase):
    p2 = phase == 2
    up = SHIFT[phase]
    lead = Layer("Piano (the waltz melody)", 100, 100)
    hold = Layer("Piano (the melody held)", 60, 100)
    harmony = Layer("Piano (a third below)", 50, 80)
    chase = Layer("Iron xylophone (the answer)", 55, 110)
    chip = Layer("8-bit (doubles the melody)", 40, 120)
    flute = Layer("Flute (the octave above)", 35, 130)
    clash_a = Layer("Rub (lower)", 50, 90)
    clash_b = Layer("Rub (higher)", 50, 110)
    bass = Layer("Bass (the first beat)", 95, 100)
    earth = Layer("Didgeridoo (the weight)", 55, 100)
    step_guitar = Layer("Guitar (the steps, root)", 70, 60)
    step_fifth = Layer("Guitar (the steps, fifth)", 65, 70)
    step_bar = Layer("Iron xylophone (the steps)", 45, 120)
    kick = Layer("Kick (the landing)", 90, 100)
    snare = Layer("Snare (the steps)", 75, 105)
    hat = Layer("Hat", 55, 115)
    layers = [lead, hold, harmony, chase, chip, flute, clash_a, clash_b, bass, earth, step_guitar, step_fifth, step_bar, kick, snare, hat]

    stops = []
    position = 0
    for name, section in SECTIONS:
        second = name in ("A2", "B2")
        loud = name in ("B2", "climb", "coda") or (second and p2)
        for index, (chord_text, size, text) in enumerate(section):
            start = position
            first, _, tail = chord_text.partition("+")
            bass_root, tones = CHORDS[first]
            bass_root += up
            tones = [tone + up for tone in tones]
            fifth = bass_root + 7
            tail_tones = [tone + up for tone in CHORDS[tail][1]] if tail else None
            for (section_name, bar, from_tick) in STOPS:
                if section_name == name and bar == index:
                    stops.append((start + from_tick, start + size))

            # ---- the weight: ONE strike on the first beat (bass, kick, in the loud places the didgeridoo); in the interlude the strikes fall in groups of two beats (the hemiola)
            if name == "intro" and size == 4:      # the pickup: one low toll
                bass.put_sound(start, BASS, bass_root, 80)
                kick.put(start, BASEDRUM, 62, 70)
                position += size
                continue
            if name == "interlude":
                offset = (index % 2) * BAR3
                weight = [tick for tick in range(size) if (offset + tick) % 8 == 0]
                step = [tick for tick in range(size) if (offset + tick) % 8 == 4]
            else:
                weight = [0]
                step = [4, 8]
            lonely = name == "bridge"
            for tick in weight:
                strong = tick == 0 or name == "interlude"
                bass.put_sound(start + tick, BASS, bass_root, (50 if lonely else 92) if strong else 70)
                if not lonely:
                    kick.put(start + tick, BASEDRUM, 62, 88 if tick == 0 else 66)
            if tail:
                bass.put_sound(start + 8, BASS, CHORDS[tail][0] + up, 46)
            if loud or name in ("A", "B", "climb"):
                if not lonely and name != "intro":
                    earth.put_sound(start, DIDGERIDOO, bass_root, 44 if name == "climb" or p2 else 34)

            # ---- the steps: beats 2 and 3 (or the off beats of the hemiola) are sharp and short: the snare, and chord stabs on the guitar and the iron xylophone
            if name == "intro" and index <= 3:
                if index == 3:
                    for tick in (4, 8):
                        snare.put(start + tick, SNARE, 66, 30)
            elif lonely:
                for tick in (4, 8):
                    hat.put(start + tick, HAT, 66, 16)
            else:
                for tick in step:
                    snare.put(start + tick, SNARE, 66, 62 if tick == step[0] else 54)
                    if name != "intro":
                        chord_now = tail_tones if tail_tones is not None and tick >= 8 else tones
                        step_guitar.put_sound(start + tick, GUITAR, guitar_note(chord_now[0] + 12), 46)
                        step_fifth.put_sound(start + tick, GUITAR, guitar_note(chord_now[2] + 12), 38)
                        if loud or name == "B":
                            step_bar.put_sound(start + tick, IRON_XYLOPHONE, chord_now[3], 32)
                if loud:      # the run of sixteenths into the next bar's landing in the loud places
                    for tick in (10, 11):
                        snare.put(start + tick, SNARE, 66, 22)
                if p2:        # the kick lands on every beat and the hat runs in eighths: the steps get frantic
                    for tick in (4, 8):
                        if start + tick not in kick.notes:
                            kick.put(start + tick, BASEDRUM, 62, 42)
                    for tick in range(0, size, 2):
                        hat.put(start + tick, HAT, 66, 26 if tick % 4 == 0 else 16)

            # ---- the rubs
            for (section_name, bar, lower, higher) in CLASHES:
                if section_name == name and bar == index:
                    instrument = BIT if p2 else HARP
                    for again in range(0, 8, 4):
                        clash_a.put_sound(start + again, instrument, lower + up, 42 - again * 2)
                        clash_b.put_sound(start + again, instrument, higher + up, 42 - again * 2)

            # ---- the melody (and the voices that follow it)
            if text is not None:
                tick = 0
                for note, length in parse(text, size):
                    if note is not None:
                        at = start + tick
                        note += up
                        lead.put_sound(at, HARP, note, 94 if tick == 0 else 84)
                        for again in range(4, length, 4):  # a long note is struck again, softer
                            hold.put_sound(at + again, HARP, note, 52 - again * 2)
                        if name in ("B2", "climb", "coda"):
                            harmony.put_sound(at, HARP, max(54, third_below(note - up, A_MINOR) + up), 40)
                        if name in ("B2", "climb", "interlude") or (p2 and name == "B"):   # the iron xylophone chases the harp a beat later
                            chase.put_sound(at + CHASE, IRON_XYLOPHONE, note, 46)
                        if name == "climb" and note - up >= 66 and length >= 2:            # the quiet flute an octave above (it sounds F#5 and up)
                            flute.put_sound(at, FLUTE, note + 12, 30)
                        if p2 and name not in ("intro", "bridge"):
                            chip.put_sound(at, BIT, note, 32)
                    tick += length
            position += size

    # the breaks: the whole band stops from the given tick to the end of the bar
    for first, end in stops:
        for layer in layers:
            for tick in range(first, end):
                layer.notes.pop(tick, None)
    for layer in layers:   # the chase of the last bar must not run past the end of the song
        for tick in [tick for tick in layer.notes if tick >= position]:
            del layer.notes[tick]
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
                  f"Boss theme in three beats, phase {phase} of 2: the same tempo, the same bars and the same chords as the other phase (a half step higher in phase 2); the game goes over at a bar line. "
                  + ("Natural instruments: the harp, the bass and the guitar, the snare for the steps." if phase == 1 else "The 8-bit doubles the melody, the kick lands on every beat."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
