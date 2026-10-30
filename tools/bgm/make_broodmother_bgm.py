#!/usr/bin/env python3
"""Makes broodmother_phase1.nbs and broodmother_phase2.nbs: "Mother of a Thousand Legs (Boss006)" and "Mother of a Thousand Legs, Swarming (Boss006)", the boss theme of the broodmother (a huge spider queen).

    python3 tools/bgm/make_broodmother_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_broodmother_bgm.py outdir     writes them somewhere else

Written from the analysis that was sent with the request (t51): a J-RPG boss fight in the dark fantasy way - fast, melodic, uneasy, with a story told by the form.
  - Sound (the three layers): (1) a bass with a hard attack, in short bouncing staccato notes, with the didgeridoo under its first beat for the size; (2) the cinematic layer: the snare as an army march in
    sixteenths, and the iron xylophone (the vibraphone-like one that sounds in the same range as the piano; the bone-block xylophone of the analysis sounds three octaves too high here) as the skittering
    of the legs; (3) the melody in the clear high register, sorrowful, on the piano (the harp) and doubled by the iron xylophone in the chorus.
  - Rhythm: 150 BPM, the fastest grid there is (10 ticks a second, so a song tick is 2 server ticks). SYNCOPATION: the skittering fragments of A start off the beat. ODD METER: the chorus ends with a bar of
    3 beats and then a bar of 5 (a beat taken away, a beat added), and the interlude (the chaos) is eight bars of 3 beats and then two of 4. KIME: the whole band stops at the end of the phrases (a
    silence from the middle or the last beat of the bar to its end) and comes back together on the downbeat.
  - Melody and chords: C minor, the natural minor with the harmonic B over G (the leading tone), the loop i - VI - VII - v (Cm Ab Bb Gm), a half-step bass cliche (C B Bb A) in the intro, a flash of C MAJOR
    in the chorus (one bar of light - the drums fall away, a bright arpeggio - and straight back into the minor), and F minor (iv) and the rising bass of the B section.
  - Form (the story): intro (the warning: a low note that falls by half steps, and a growing snare roll), A (the fight begins: the bass and drums carry, the melody is skittering fragments that undercut the
    beat), B (the rise: the chords climb to G), the chorus (the most singing melody, long notes, the major flash), the interlude (the chaos: two voices in canon on 3-beat bars, the third voice in phase 2),
    A and B again (fuller), the chorus again with everything, a coda that ends on G so that the jump back to A is smooth.
  - The motif is a lullaby turned wrong (the mother of the brood): G C Eb D C - up the triad, down the step - sung in long notes in the chorus and chopped into sixteenths in A.
Two phases that are ONE piece (the same tempo, the same bars of the same lengths, the same chords, the same loop point): the game goes over at the next bar line from the same place
(Bgm.playSceneSynced). Phase 2 is a HALF STEP HIGHER (C minor -> C# minor), with the 8-bit on the melody, an electric piano skittering in sixteenths and a four-on-the-floor kick.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM: a song tick is 2 server ticks
LOOP_START_BAR = 4  # the intro (4 bars of 16 ticks) is played once
TITLES = {1: "Mother of a Thousand Legs (Boss006)", 2: "Mother of a Thousand Legs, Swarming (Boss006)"}
FILES = {1: "broodmother_phase1.nbs", 2: "broodmother_phase2.nbs"}
POLYPHONY = 8
SHIFT = {1: 0, 2: 1}   # phase 2 is a half step higher
C_MINOR = scale("G3 Ab3 Bb3 C4 D4 Eb4 F4 G4 Ab4 Bb4 C5 D5 Eb5 F5")

# chord -> (bass root, six notes of the chord for the piano, low to high); all by what SOUNDS in phase 1 (the bass F#1 to F#3, the piano F#3 to F#5; nothing above F5 so that a half step up still fits)
CHORDS = {
    "Cm": (36, [55, 60, 63, 67, 72, 75]),
    "Cm/B": (35, [55, 60, 63, 67, 72, 75]),     # the half-step cliche: the same chord, the bass falls
    "Cm/Bb": (34, [55, 60, 63, 67, 72, 75]),
    "Cm/A": (33, [55, 60, 63, 67, 72, 75]),
    "Ab": (44, [56, 60, 63, 68, 72, 75]),
    "Bb": (46, [58, 62, 65, 70, 74, 77]),
    "Gm": (43, [55, 58, 62, 67, 70, 74]),
    "G": (43, [55, 59, 62, 67, 71, 74]),         # major: the B natural is the leading tone
    "Eb": (39, [55, 58, 63, 67, 70, 75]),
    "Fm": (41, [56, 60, 65, 68, 72, 77]),
    "C": (36, [55, 60, 64, 67, 72, 76]),         # C major: one bar of light
}


def parse(text, size):
    """'C5:2 Eb5:2 r:12' -> [(midi or None, ticks), ...]; the ticks must fill the bar of ``size`` ticks."""
    result = []
    total = 0
    for part in text.split():
        name, length = part.split(":")
        result.append((None if name == "r" else midi(name), int(length)))
        total += int(length)
    assert total == size, f"bar '{text}' has {total} ticks, not {size}"
    return result


# each section: a list of bars (chord, ticks in the bar, the melody of the bar or None)
def bars(chords, tune, sizes=None):
    sizes = sizes or [16] * len(chords)
    tune = tune or [None] * len(chords)
    return list(zip(chords, sizes, tune))


INTRO = bars(["Cm", "Cm/B", "Cm/Bb", "Cm/A"], [None, None, None, "C5:4 B4:4 G4:8"])
A_CHORDS = ["Cm", "Cm", "Ab", "Bb", "Cm", "Cm", "Ab", "G"]
A_TUNE = [                      # the lullaby in pieces: sixteenths that start off the beat
    "r:2 C5:1 Eb5:1 D5:1 C5:1 r:2 C5:1 Eb5:1 D5:1 C5:1 r:4",
    "r:2 G4:1 C5:1 Bb4:1 G4:1 r:2 G4:1 C5:1 B4:1 G4:1 r:4",
    "r:2 Ab4:1 C5:1 Bb4:1 Ab4:1 r:2 Eb5:1 C5:1 Bb4:1 Ab4:1 r:4",
    "r:2 Bb4:1 D5:1 C5:1 Bb4:1 r:2 F5:1 D5:1 C5:1 Bb4:1 r:4",
    "r:2 C5:1 Eb5:1 D5:1 C5:1 r:2 C5:1 Eb5:1 D5:1 C5:1 r:4",
    "r:2 G4:1 C5:1 Bb4:1 G4:1 r:2 G4:1 C5:1 B4:1 G4:1 r:4",
    "r:2 Ab4:1 C5:1 Bb4:1 Ab4:1 r:2 Eb5:1 C5:1 Bb4:1 Ab4:1 r:4",
    "r:2 G4:1 B4:1 A4:1 G4:1 r:2 D5:1 B4:1 A4:1 G4:1 r:4",
]
B_CHORDS = ["Ab", "Bb", "Cm", "Eb", "Fm", "G", "Ab", "G"]
B_TUNE = [                      # the rise: the chords climb to G
    "Ab4:4 C5:4 Eb5:8",
    "Bb4:4 D5:4 F5:8",
    "G4:4 C5:4 Eb5:8",
    "G4:4 Bb4:4 Eb5:8",
    "Ab4:4 C5:4 F5:8",
    "D5:4 B4:4 G4:8",
    "Ab4:4 C5:4 Eb5:4 C5:4",
    "B4:12 r:4",
]
CHORUS_CHORDS = ["Cm", "Ab", "Bb", "Gm", "Cm", "Ab", "Bb", "G", "Fm", "Cm", "Ab", "Bb", "C", "Cm", "Bb", "G"]
CHORUS_SIZES = [16] * 14 + [12, 20]      # a bar of three beats, then a bar of five
CHORUS_TUNE = [                 # the lullaby, sung in long notes
    "G4:4 C5:4 Eb5:8",
    "Eb5:6 C5:2 Ab4:8",
    "D5:6 Bb4:2 D5:8",
    "D5:4 C5:4 Bb4:8",
    "G4:4 C5:4 Eb5:8",
    "Eb5:6 C5:2 Ab4:8",
    "F5:6 D5:2 Bb4:8",
    "B4:4 D5:4 G4:4 B4:4",
    "F5:6 C5:2 Ab4:8",
    "G4:4 Eb5:4 C5:8",
    "Ab4:4 C5:4 Eb5:8",
    "D5:8 F5:8",
    "E5:8 G4:4 C5:4",            # the major flash
    "Eb5:8 D5:4 C5:4",
    "Bb4:4 D5:4 F5:4",           # three beats
    "B4:4 D5:4 B4:4 G4:8",       # five beats
]
INTERLUDE_CHORDS = ["Cm", "Ab", "Cm", "Ab", "Bb", "G", "Bb", "G", "Cm", "G"]
INTERLUDE_SIZES = [12] * 8 + [16, 16]    # eight bars of three beats, then two of four
INTERLUDE_TUNE = [
    "C5:2 Eb5:2 D5:2 C5:2 G4:4",
    "Ab4:2 C5:2 Bb4:2 Ab4:2 Eb5:4",
    "C5:2 Eb5:2 D5:2 C5:2 G4:4",
    "Ab4:2 C5:2 Bb4:2 Ab4:2 C5:4",
    "Bb4:2 D5:2 C5:2 Bb4:2 F5:4",
    "G4:2 B4:2 A4:2 G4:2 D5:4",
    "Bb4:2 D5:2 C5:2 Bb4:2 F5:4",
    "G4:2 B4:2 A4:2 G4:2 B4:4",
    "C5:4 Eb5:4 D5:4 C5:4",
    "B4:4 D5:4 B4:4 G4:4",
]
CODA_CHORDS = ["Ab", "Bb", "G", "G"]
CODA_TUNE = ["Eb5:8 C5:8", "F5:8 D5:8", "B4:8 D5:8", "G4:12 r:4"]

CANON_DELAY = 6   # ticks: the second voice of the chaos

# two notes held and struck again a little: (section, bar of the section, the lower note, the higher note), by what sounds in phase 1: a half step in the interlude (G and Ab over G) and a tritone in the
# last chorus (F# and C over G)
CLASHES = [("interlude", 9, 67, 68), ("chorus2", 7, 66, 72)]

# the kime: the whole band stops from this tick of the bar to its end (section, bar of the section, from tick)
STOPS = [("A", 7, 12), ("A2", 7, 12), ("B", 7, 8), ("B2", 7, 8), ("chorus", 7, 12), ("chorus", 13, 12), ("chorus2", 7, 12), ("chorus2", 13, 12), ("interlude", 9, 8), ("coda", 3, 12)]

# (name, bars)
SECTIONS = [
    ("intro", INTRO),
    ("A", bars(A_CHORDS, A_TUNE)),
    ("B", bars(B_CHORDS, B_TUNE)),
    ("chorus", bars(CHORUS_CHORDS, CHORUS_TUNE, CHORUS_SIZES)),
    ("interlude", bars(INTERLUDE_CHORDS, INTERLUDE_TUNE, INTERLUDE_SIZES)),
    ("A2", bars(A_CHORDS, A_TUNE)),
    ("B2", bars(B_CHORDS, B_TUNE)),
    ("chorus2", bars(CHORUS_CHORDS, CHORUS_TUNE, CHORUS_SIZES)),
    ("coda", bars(CODA_CHORDS, CODA_TUNE)),
]


def octave(note):
    """The same note an octave up, or an octave down where the bass cannot go up (it sounds F#1 to F#3)."""
    return note + 12 if note + 12 <= 54 else note - 12


def guitar_note(note):
    """The guitar sounds F#2 to F#4: a note above that goes down an octave."""
    return note if note <= 66 else note - 12


BASS_HITS = {12: [0, 3, 6, 9], 16: [0, 3, 6, 8, 11, 14], 20: [0, 3, 6, 8, 11, 14, 17]}   # the hard staccato bass, syncopated


def build(phase):
    p2 = phase == 2
    up = SHIFT[phase]
    lead = Layer("Piano (the lullaby)", 100, 100)
    hold = Layer("Piano (the lullaby held)", 60, 100)
    harmony = Layer("Piano (a third below)", 50, 80)
    high = Layer("Iron xylophone (the melody, high)", 45, 125)
    canon = Layer("Iron xylophone (second voice)", 55, 110)
    chip = Layer("8-bit (doubles the melody)", 40, 120)
    chip_low = Layer("8-bit (third voice)", 40, 120)
    legs = Layer("Electric piano (the legs)", 40, 100)
    light = Layer("Electric piano (the light)", 45, 110)
    clash_a = Layer("Rub (lower)", 50, 90)
    clash_b = Layer("Rub (higher)", 50, 110)
    bass = Layer("Bass (hard staccato)", 90, 100)
    earth = Layer("Didgeridoo (the weight)", 55, 100)
    guitar_root = Layer("Guitar (root)", 70, 60)
    guitar_fifth = Layer("Guitar (fifth)", 65, 70)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare (the march)", 70, 105)
    hat = Layer("Hat", 55, 115)
    layers = [lead, hold, harmony, high, canon, chip, chip_low, legs, light, clash_a, clash_b, bass, earth, guitar_root, guitar_fifth, kick, snare, hat]

    stops = []
    position = 0
    for name, section in SECTIONS:
        second = name in ("A2", "B2", "chorus2")
        sings = name in ("chorus", "chorus2")
        for index, (chord, size, text) in enumerate(section):
            start = position
            bass_root, tones = CHORDS[chord]
            bass_root += up
            tones = [tone + up for tone in tones]
            fifth = bass_root + 7
            hits = BASS_HITS[size]
            for (section_name, bar, from_tick) in STOPS:
                if section_name == name and bar == index:
                    stops.append((start + from_tick, start + size))
            sun = chord == "C"

            # ---- the bass: hard bouncing staccato (single short strikes), the first beat of a bar heavy with the didgeridoo under it
            if name == "intro":   # the warning: a low note that falls by half steps, long, struck again
                swell(bass, BASS, bass_root, start, size, 52 + index * 8, 70 + index * 8, 4)
                if index >= 2:
                    swell(earth, DIDGERIDOO, bass_root, start, size, 24, 44, 4)
            elif sun:   # the light: the bass is soft
                bass.put_sound(start, BASS, bass_root, 46)
                bass.put_sound(start + 8, BASS, fifth, 36)
            elif name == "coda" and index == 3:  # the lowest note of the song, held and growing
                swell(bass, BASS, 31 + up, start, size, 60, 92, 4)    # low G
                swell(earth, DIDGERIDOO, 31 + up, start, size, 30, 62, 4)
            else:
                for number, tick in enumerate(hits):
                    note = bass_root if number % 2 == 0 or tick == 8 else octave(bass_root)
                    if number == 4 and size >= 16:
                        note = fifth
                    bass.put_sound(start + tick, BASS, note, 82 if tick == 0 else 58 if tick % 8 else 66)
                if name in ("A", "A2") and size >= 16:
                    earth.put_sound(start, DIDGERIDOO, bass_root, 40)

            # ---- the guitar: hard stabs between the bass
            if name in ("A2", "B", "B2", "chorus", "chorus2", "interlude") and not sun:
                stab = [(3, 46), (11, 42)] if size == 16 else [(3, 46), (9, 40)] if size == 12 else [(3, 46), (11, 42), (17, 40)]
                if p2 or second:
                    stab = [(0, 52)] + stab
                for tick, vel in stab:
                    guitar_root.put_sound(start + tick, GUITAR, guitar_note(bass_root + 12), vel)
                    if vel >= 44 and not sings:
                        guitar_fifth.put_sound(start + tick, GUITAR, guitar_note(fifth + 12), vel - 8)

            # ---- the drums: the march
            if name == "intro":
                if index >= 2:   # the roll that grows
                    for tick in range(0, size, 2 if index == 2 else 1):
                        snare.put(start + tick, SNARE, 66, 22 + int(54 * ((index - 2) * 16 + tick) / 32.0))
                if index == 0:
                    kick.put(start, BASEDRUM, 62, 80)
            elif sun:
                for tick in (0, 8):
                    hat.put(start + tick, HAT, 66, 20)
            elif sings or name in ("A2", "B2"):   # the march in sixteenths
                for tick in range(size):
                    accent = tick % 4 == 0
                    snare.put(start + tick, SNARE, 66, (66 if tick in (4, 12) else 40 if accent else 22))
                if size == 12:
                    kick_ticks = [0, 4, 8]
                elif p2 or sings:
                    kick_ticks = [0, 4, 8, 12] + ([16] if size == 20 else [])
                else:
                    kick_ticks = [0, 6, 10]
                for tick in kick_ticks:
                    kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
            elif name == "interlude":   # three beats: kick, hat, snare
                kick.put(start, BASEDRUM, 62, 84)
                if size == 12:
                    hat.put(start + 4, HAT, 66, 30)
                    snare.put(start + 8, SNARE, 66, 70)
                else:
                    snare.put(start + 4, SNARE, 66, 70)
                    snare.put(start + 12, SNARE, 66, 70)
                    kick.put(start + 8, BASEDRUM, 62, 70)
            else:   # A, B
                for tick, vel in ((0, 84), (6, 58), (10, 64)):
                    kick.put(start + tick, BASEDRUM, 62, vel)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 74)
                for tick in (14, 15):
                    snare.put(start + tick, SNARE, 66, 26)
                for tick in range(0, size, 2):
                    hat.put(start + tick, HAT, 66, 34 if tick % 4 == 0 else 20)

            # ---- the light: a bright arpeggio rising through the C major chord
            if sun:
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 5, 4, 5]):
                    light.put_sound(start + step * 2, PLING, tones[tone_index], 40 + step * 3)

            # ---- the legs: phase 2 skitters in sixteenths under B and the choruses
            if p2 and name == "B2":
                for tick in range(size):
                    legs.put_sound(start + tick, PLING, tones[(0, 2, 1, 3, 2, 4, 3, 1)[tick % 8]], 26 if tick % 4 else 32)

            # ---- the rubs
            for (section_name, bar, lower, higher) in CLASHES:
                if section_name == name and bar == index:
                    instrument = BIT if p2 else HARP
                    for again in range(0, 8, 4):
                        clash_a.put_sound(start + again, instrument, lower + up, 46 - again * 2)
                        clash_b.put_sound(start + again, instrument, higher + up, 46 - again * 2)

            # ---- the melody (and the voices that follow it)
            if text is not None:
                tick = 0
                for note, length in parse(text, size):
                    if note is not None:
                        at = start + tick
                        note += up
                        lead.put_sound(at, HARP, note, 92 if tick == 0 else 84)
                        for again in range(4, length, 4):  # a long note is struck again, softer
                            hold.put_sound(at + again, HARP, note, 52 - again * 2)
                        if name in ("A2", "B2", "coda"):
                            harmony.put_sound(at, HARP, max(54, third_below(note - up, C_MINOR) + up), 44)
                        if sings and length >= 4:   # the clear high voice on the long notes
                            high.put_sound(at, IRON_XYLOPHONE, note, 40)
                        if name == "interlude":      # the chaos: a canon on the iron xylophone, and in phase 2 the 8-bit as a third voice a third up
                            if at + CANON_DELAY < start + size + CANON_DELAY:
                                canon.put_sound(at + CANON_DELAY, IRON_XYLOPHONE, note, 52)
                            if p2:
                                chip_low.put_sound(at, BIT, min(78, note + 4), 36)
                        if p2 and (sings or name in ("B2", "A2")):
                            chip.put_sound(at, BIT, note, 34)
                    tick += length
            position += size

    # the kime: the whole band stops from the given tick to the end of the bar
    for first, end in stops:
        for layer in layers:
            for tick in range(first, end):
                layer.notes.pop(tick, None)
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
                  f"Boss theme of the broodmother, phase {phase} of 2: the same tempo, the same bars and the same chords as the other phase (a half step higher in phase 2); the game goes over at a bar line. "
                  + ("Natural instruments, the iron xylophone high." if phase == 1 else "The 8-bit and the electric piano over the piano, a four-on-the-floor kick."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
