#!/usr/bin/env python3
"""Makes mirror_phase1.nbs and mirror_phase2.nbs: "The Cracked Mirror Laughs (Boss007)" and "The Cracked Mirror Laughs, Shattered (Boss007)", the boss theme of a boss that does not exist yet (t54): a mad, mocking, ugly one.

    python3 tools/bgm/make_mirror_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_mirror_bgm.py outdir     writes them somewhere else

The boss is not in the game yet, so the name and the story come from the mood of the request: something is WRONG - a mirror that cracks, a mind that comes apart, a laugh that climbs and falls.
  - Scales: F# PHRYGIAN DOMINANT (the harmonic minor from its fifth degree: F# G A# B C# D E) and the half-whole DIMINISHED (F# G A A# C C# D# E): half steps everywhere, no clean minor.
  - The motif is a CRACK: a trill of half steps (C#5 D5 C#5 D5 C#5 C5) that falls a TRITONE (to F#4) and rests. Every phrase of A, B and the coda is this figure moved to the note of its chord,
    and the tritone is built in: the tail is always six half steps below the last trill note. The long notes of C fall by a half step (a note and the one under it) over chords that do not fit.
  - Chords: nothing resolves. F# with the G above it (a half step: the mud), G, C (a tritone from F#), and the diminished chords D#dim and A#dim. The bass is in the lowest register there is (F#1 and up): a heavy
    riff in groups of three ticks (a triplet-like 3 + 3 + 3 + 3 + 3 + 1) whose notes are the root, the root, a half step up, the root, a TRITONE up, a half step up.
  - Sounds, cold and metallic: the iron xylophone (the vibraphone-like one, in the same range as the piano) carries the melody instead of the warm harp; the electric piano and the iron xylophone keep a small
    dissonant cluster (a half step) going under everything, to cloud the air; the bell (gold) and the chime (ice) TRILL very high and quiet like glass about to break (a few bars only, and never above D#7: the high notes of these
    instruments are painful); the flute LAUGHS, a chromatic staircase that climbs and falls (B and the coda).
  - Rhythm: 150 BPM, a 16-note drive in A; B is in SEVEN beats (bars of 28 ticks); the bridge is in FIVE (bars of 20); sudden breaks - the drums and the bass stop, and only the high uncanny melody rings.
    The odd bars add up to whole 16-tick bars (four of 28 = seven, four of 20 and one of 16 = six), so the phase change always lands on a beat.
Two phases that are ONE piece (the same tempo, the same bars of the same lengths, the same chords, the same loop point): the game goes over at the next bar line from the same place (Bgm.playSceneSynced).
Phase 2 is a HALF STEP HIGHER (F# -> G), with the 8-bit on the melody, a louder cluster, a faster hat and the laugh in every bar of B.
Form: intro (4 bars, once: a bass that climbs by half steps and trills that grow), A (12), B (4 bars of seven beats: the laughter), C (12: the despair - slow, heavy, falling half steps), the bridge (5 bars of five
beats: no drums, the glass), A, B, C again, a coda that ends on F# so that the jump back to A is smooth. Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM: a song tick is 2 server ticks
LOOP_START_BAR = 4  # the intro (4 bars of 16 ticks) is played once
TITLES = {1: "The Cracked Mirror Laughs (Boss007)", 2: "The Cracked Mirror Laughs, Shattered (Boss007)"}
FILES = {1: "mirror_phase1.nbs", 2: "mirror_phase2.nbs"}
POLYPHONY = 8
SHIFT = {1: 0, 2: 1}   # phase 2 is a half step higher

# chord -> (bass root, six notes of the chord for the cluster and the arpeggios, low to high, with a half-step clash tone in them); all by what SOUNDS in phase 1
CHORDS = {
    "F#": (30, [54, 58, 61, 66, 67, 70]),       # F# A# C# F# and the G a half step over the F#
    "G": (31, [55, 59, 62, 66, 67, 71]),       # G B D with the F# under the G
    "C": (36, [60, 64, 67, 66, 72, 76]),       # C E G with the F# (the tritone) in it
    "D#dim": (39, [57, 60, 63, 66, 69, 72]),   # D# F# A C: the diminished seventh
    "E": (40, [56, 59, 64, 68, 71, 75]),       # E with the D# (a major seventh: the half step under the root)
    "A#dim": (34, [58, 61, 64, 67, 70, 73]),   # A# C# E G
}
ANCHOR = {"F#": 73, "G": 74, "C": 72, "D#dim": 75, "E": 76, "A#dim": 70}   # the top note of the trill on each chord

RIFF = [0, 0, 1, 0, 6, 1]    # half steps over the root: the root, the root, up a half step, the root, up a TRITONE, up a half step


def figure(anchor, size):
    """The crack: a trill of half steps (anchor, anchor+1, ..., anchor-1), a rest, and a fall of a tritone from the last note (six half steps), then rest."""
    a = anchor
    parts = [a, a + 1, a, a + 1, a, a - 1]
    text = " ".join(f"{note_name(note)}:1" for note in parts) + f" r:2 {note_name(a - 1 - 6)}:4"
    return text + (f" r:{size - 12}" if size > 12 else "")


def despair(anchor, size):
    """A note held and the one a half step under it held: the fall that does not fit its chord."""
    half = size // 2
    return f"{note_name(anchor)}:{half} {note_name(anchor - 1)}:{size - half}"


A_CHORDS = ["F#", "F#", "G", "F#", "C", "C", "D#dim", "E", "F#", "G", "A#dim", "C"]
B_CHORDS = ["F#", "C", "D#dim", "A#dim"]
B_SIZES = [28] * 4            # seven beats
C_CHORDS = ["F#", "G", "F#", "C", "D#dim", "E", "A#dim", "C", "F#", "G", "C", "F#"]
C_ANCHORS = [76, 74, 72, 70, 75, 73, 71, 69, 74, 72, 70, 68]
BRIDGE_CHORDS = ["F#", "F#", "D#dim", "D#dim", "C"]
BRIDGE_SIZES = [20, 20, 20, 20, 16]    # five beats, then four
CODA_CHORDS = ["G", "C", "D#dim", "F#"]

# the sudden breaks: the drums, the bass and the guitar-like stabs stop and only the high notes ring (section, bar of the section, from tick)
SOLO_BREAKS = [("A", 3, 8), ("A", 7, 8), ("A", 11, 4), ("C", 5, 8), ("C", 11, 4)]
# the whole band stops (section, bar of the section, from tick)
STOPS = [("coda", 3, 12)]


def bars(chords, sizes=None, tune=None):
    sizes = sizes or [16] * len(chords)
    return list(zip(chords, sizes, tune or [None] * len(chords)))


SECTIONS = [
    ("intro", bars(["F#", "F#", "F#", "G"])),
    ("A", bars(A_CHORDS, None, [figure(ANCHOR[c], 16) for c in A_CHORDS])),
    ("B", bars(B_CHORDS, B_SIZES, [figure(ANCHOR[c], 28) for c in B_CHORDS])),
    ("C", bars(C_CHORDS, None, [despair(a, 16) for a in C_ANCHORS])),
    ("bridge", bars(BRIDGE_CHORDS, BRIDGE_SIZES)),
    ("A2", bars(A_CHORDS, None, [figure(ANCHOR[c], 16) for c in A_CHORDS])),
    ("B2", bars(B_CHORDS, B_SIZES, [figure(ANCHOR[c], 28) for c in B_CHORDS])),
    ("C2", bars(C_CHORDS, None, [despair(a, 16) for a in C_ANCHORS])),
    ("coda", bars(CODA_CHORDS, None, [figure(ANCHOR[c], 16) for c in CODA_CHORDS])),
]


def parse(text, size):
    result = []
    total = 0
    for part in text.split():
        name, length = part.split(":")
        result.append((None if name == "r" else midi(name), int(length)))
        total += int(length)
    assert total == size, f"bar '{text}' has {total} ticks, not {size}"
    return result


def trill(layer, instrument, low, start, length, vel, step=2):
    """Two notes a half step apart, struck one after the other every ``step`` ticks (the notes are the sounding MIDI numbers of the instrument)."""
    for number, tick in enumerate(range(0, length, step)):
        if start + tick not in layer.notes:
            layer.put_sound(start + tick, instrument, low + (number % 2), vel)


def laugh(layer, start, length, vel, rising_first=True):
    """The laugh: a chromatic staircase of the flute that climbs and then falls (one half step a tick), in the flute's lowest octave (F#5 and up)."""
    half = length // 2
    for tick in range(length):
        step = tick if tick < half else length - 1 - tick
        if start + tick not in layer.notes:
            layer.put_sound(start + tick, FLUTE, 78 + step, vel)


def build(phase):
    p2 = phase == 2
    up = SHIFT[phase]
    lead = Layer("Iron xylophone (the crack)", 100, 100)
    hold = Layer("Iron xylophone (held)", 60, 100)
    chip = Layer("8-bit (doubles the crack)", 40, 120)
    haze_a = Layer("Electric piano (cluster, lower)", 35, 90)
    haze_b = Layer("Iron xylophone (cluster, higher)", 35, 110)
    glass_a = Layer("Bell (trill, gold)", 30, 90)
    glass_b = Layer("Chime (trill, ice)", 30, 110)
    giggle = Layer("Flute (the laugh)", 40, 100)
    bass = Layer("Bass (the riff)", 95, 100)
    earth = Layer("Didgeridoo (the ground)", 60, 100)
    stab = Layer("Guitar (the stab)", 60, 70)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat", 55, 115)
    layers = [lead, hold, chip, haze_a, haze_b, glass_a, glass_b, giggle, bass, earth, stab, kick, snare, hat]
    drums_and_bass = [bass, earth, stab, kick, snare, hat]

    stops = []         # (first tick, end tick, the layers that stop)
    position = 0
    for name, section in SECTIONS:
        sings = name in ("C", "C2")
        for index, (chord, size, text) in enumerate(section):
            start = position
            root, tones = CHORDS[chord]
            root += up
            tones = [tone + up for tone in tones]
            for (section_name, bar, from_tick) in SOLO_BREAKS:
                if name.rstrip("2") == section_name and bar == index:
                    stops.append((start + from_tick, start + size, drums_and_bass + [haze_a, haze_b]))
            for (section_name, bar, from_tick) in STOPS:
                if section_name == name and bar == index:
                    stops.append((start + from_tick, start + size, layers))

            # ---- the bass: a heavy riff in groups of three ticks (the lowest notes there are)
            if name == "intro":   # climbing by half steps, long, struck again
                swell(bass, BASS, 30 + up + index, start, size, 56, 78, 4)
                if index >= 2:
                    swell(earth, DIDGERIDOO, 30 + up + index, start, size, 24, 44, 4)
            elif name == "bridge":   # no drums: only the ground, under the glass
                swell(earth, DIDGERIDOO, root, start, size, 26, 40, 4)
            elif name == "coda" and index == 3:   # the lowest note, held and growing
                swell(bass, BASS, 30 + up, start, size, 60, 92, 4)
                swell(earth, DIDGERIDOO, 30 + up, start, size, 30, 62, 4)
            elif name in ("C", "C2"):   # slow and heavy: one long note, and the half step under it at the half
                swell(bass, BASS, root, start, size // 2, 70, 56, 4)
                swell(bass, BASS, root - 1 if root > 30 + up else root + 1, start + size // 2, size - size // 2, 64, 50, 4)
                earth.put_sound(start, DIDGERIDOO, root, 46)
            else:
                for number, tick in enumerate(range(0, size, 3)):
                    offset = RIFF[number % len(RIFF)]
                    bass.put_sound(start + tick, BASS, root + offset, 84 if tick == 0 else 72 if offset == 6 else 58)
                if size >= 16:
                    earth.put_sound(start, DIDGERIDOO, root, 42)

            # ---- the stabs: dry, on the off-beats, with the clash tone
            if name in ("A", "A2", "B", "B2") and index % 2 == 0:
                for tick in (5, 11) if size == 16 else (5, 11, 19, 25):
                    stab.put_sound(start + tick, GUITAR, tones[3] - 12, 40)

            # ---- the drums
            if name in ("A", "A2"):   # a sixteen-beat drive
                for tick in (0, 3, 10):
                    kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 66)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 76)
                for tick in range(0, 16):
                    if p2 or tick % 2 == 0:   # sixteenths in phase 2, eighths in phase 1
                        hat.put(start + tick, HAT, 66, 34 if tick % 4 == 0 else 22)
            elif name in ("B", "B2"):   # seven beats: 4 + 3
                for tick in (0, 8, 16, 22):
                    kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 68)
                for tick in (4, 12, 24):
                    snare.put(start + tick, SNARE, 66, 74)
                for tick in range(0, size, 2):
                    hat.put(start + tick, HAT, 66, 34 if tick % 8 == 0 else 22)
            elif sings:   # half time, heavy
                kick.put(start, BASEDRUM, 62, 90)
                snare.put(start + 8, SNARE, 66, 80)
                if p2:
                    kick.put(start + 10, BASEDRUM, 62, 70)
            elif name == "intro" and index == 3:   # a roll that grows
                for tick in range(size):
                    snare.put(start + tick, SNARE, 66, 20 + tick * 3)
            elif name == "coda" and index < 3:
                kick.put(start, BASEDRUM, 62, 80)
                snare.put(start + 8, SNARE, 66, 70)

            # ---- the cluster: a small dissonant half step kept going under the drive, all the time (it clouds the air)
            if name not in ("intro", "bridge") and not (name == "coda" and index == 3):
                for tick in range(0, size, 4):
                    haze_a.put_sound(start + tick, PLING, tones[3], 16 if not p2 else 22)
                    haze_b.put_sound(start + tick + 2, IRON_XYLOPHONE, tones[4], 16 if not p2 else 22)

            # ---- the glass: the bell and the chime trill, very high and quiet (a few bars only)
            if name == "intro" and index >= 2:
                trill(glass_a, BELL, 90 + index + up, start, size, 14 + 8 * (index - 2))
            elif name == "bridge":
                trill(glass_a, BELL, 90 + index + up, start, size, 26)
                trill(glass_b, CHIME, 93 + index + up, start, size, 22, 3)   # a tritone above, a little out of step
            elif name in ("A", "A2") and index in (3, 7, 11):
                trill(glass_a, BELL, 94 + up, start + 8, 8, 28)
            elif name == "coda" and index == 3:
                trill(glass_b, CHIME, 90 + up, start, 12, 20)

            # ---- the laugh: the flute climbs and falls by half steps
            if name in ("B", "B2") and (p2 or index % 2 == 0):
                laugh(giggle, start, size, 34)
            elif name in ("coda",) and index == 2:
                laugh(giggle, start, size, 30)

            # ---- the melody
            if text is not None:
                tick = 0
                for note, length in parse(text, size):
                    if note is not None:
                        at = start + tick
                        note += up
                        lead.put_sound(at, IRON_XYLOPHONE, note, 92 if tick == 0 or length > 1 else 84)
                        for again in range(4, length, 4):   # a long note is struck again, softer
                            hold.put_sound(at + again, IRON_XYLOPHONE, note, 52 - again * 2)
                        if p2:
                            chip.put_sound(at, BIT, note, 36 if length > 1 else 28)
                    tick += length
            position += size

    # the breaks
    for first, end, layer_set in stops:
        for layer in layer_set:
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
                  f"Boss theme of a mad mocking boss (not in the game yet), phase {phase} of 2: the same tempo, the same bars and the same chords as the other phase (a half step higher in phase 2); the game goes over at a bar line. "
                  + ("Cold metal: the iron xylophone, a dissonant cluster, bell and chime trills, a laughing flute." if phase == 1 else "The 8-bit joins, the cluster is louder, the laugh is in every bar of B."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
