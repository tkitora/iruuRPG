#!/usr/bin/env python3
"""Makes cloud_remembered.nbs: the field theme "Clouds Remembered (Field004)", a second, brighter and more flowing answer to "a fleeting yet bright song" (Note Block Studio v5 file).

    python3 tools/bgm/make_cloud2_bgm.py            writes src/main/resources/bgm/cloud_remembered.nbs
    python3 tools/bgm/make_cloud2_bgm.py out.nbs    writes somewhere else

Made from the NUMBERS of an analysis of the reference recording (an mp3 the user supplied: LaTale Music, Cloud9), NOT from its notes: nothing of its melody, chords or phrases is used - the tune and the chords here are
our own, written from scratch. What the measurements said, and what was taken from them:
  - Tempo about 95 BPM, and the notes come in a steady flow of about SIXTEENTHS (the most common gap between two notes is 0.16 s, then 0.32 s): a rippling arpeggio that never stops, not a few separate notes.
    Taken: 100 BPM (the nearest tempo the note blocks have, 6.67 ticks a second) and a guitar ripple in sixteenths from the first bar to the last.
  - A major key (B-flat), with the relative minor (G minor) as a calm episode, and the whole piece LIFTED A HALF STEP (B-flat -> B) after two thirds of the time (at 64 s of 96 s): a warm lift without a dramatic
    change. Taken: the same shape - the calm episode of G minor at a third, the half-step lift at two thirds (the guide's rule for a lift), and then a quiet outro that fades; the loop then starts again in B-flat.
  - The low part is strong (40 to 55 percent of the energy is below 250 Hz) and the sound is soft (the centre of the sound near 900 Hz): a warm bed, nothing sharp. Taken: a bass that is struck four times a
    bar as it fades (a long soft note), the ripple on the guitar (warm), the tune on the harp, the iron xylophone only as a quiet echo.
  - The level rises in the first twelve seconds, stays high, dips for one calm part of about eight seconds in the low register (fewer notes, lower, 2 notes a second instead of 4) and ends with a fade. Taken:
    an intro that fades in, the calm part (8 bars: no tune, low and slow), and a fade at the end.
  - It is only 5 percent quiet: so the "fleeting" feeling does not come from rests, but from the soft sound and the harmony (a major key that keeps turning to the minor chord). Taken: the harmony (Bb Eb Gm F,
    the minor sixth-chord on every turn) and no drums at all.
Form (bars of 16 ticks, 2.4 seconds): intro 4 (the ripple fades in), A 8, A again 8 (with the echo), the calm part 8 (G minor, low), A once more 8 (the third below added), the LIFT 12 (everything half a step
higher, a new tune that climbs to the highest note, a glint of the bell), the outro 4 (fading); 52 bars = 2:05; it loops from bar 5 (tick 64).
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0 / 3.0    # 100 BPM: a tick is a sixteenth, 6.67 ticks a second, a song tick is 3 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Clouds Remembered (Field004)"
BB_MAJOR = scale("G3 A3 Bb3 C4 D4 Eb4 F4 G4 A4 Bb4 C5 D5 Eb5 F5")

# chord -> (the bass root, the notes of the guitar: all between 50 and 65, so that a half step up stays inside 66)
CHORDS = {
    "Bb": (34, [53, 58, 62, 65]),
    "Bb/D": (38, [50, 58, 62, 65]),
    "Eb": (39, [51, 55, 58, 63]),
    "Gm7": (43, [55, 58, 62, 65]),
    "Gm": (43, [55, 58, 62, 65]),
    "F": (41, [53, 57, 60, 65]),
    "Cm7": (36, [55, 58, 60, 63]),
}

INTRO_CHORDS = ["Bb", "Eb", "Gm7", "F"]
A_CHORDS = ["Bb", "Eb", "Gm7", "F", "Bb", "Eb", "Cm7", "F"]
A_TUNE = [                      # the tune: a rise and a sigh; the notes few and short, a rest inside the phrase
    "r:2 D5:2 F5:6 D5:2 Bb4:4", "G4:2 Bb4:2 Eb5:8 D5:2 Bb4:2", "D5:6 Bb4:2 G4:8", "A4:4 C5:4 F5:8",
    "F5:4 D5:4 Bb4:4 D5:4", "Eb5:6 D5:2 Bb4:8", "C5:4 Eb5:4 G4:8", "A4:12 r:4",
]
CALM_CHORDS = ["Gm", "Eb", "Cm7", "F", "Gm", "Eb", "F", "F"]
LIFT_CHORDS = ["Eb", "Bb", "Gm", "F", "Eb", "Bb/D", "Cm7", "F", "Gm", "Eb", "F", "F"]
LIFT_TUNE = [                   # the new tune: it climbs to the highest note
    "G4:4 Bb4:4 Eb5:8", "D5:4 F5:4 D5:8", "Bb4:4 D5:8 Bb4:4", "C5:4 F5:4 A4:8",
    "Eb5:8 D5:4 Bb4:4", "D5:4 F5:4 Bb4:8", "C5:4 Eb5:4 G4:4 Bb4:4", "A4:4 C5:4 F5:8",
    "D5:6 C5:2 Bb4:8", "G4:4 Bb4:4 Eb5:4 D5:4", "C5:8 A4:8", "F5:8 r:8",
]
OUTRO_CHORDS = ["Eb", "Gm", "F", "F"]
OUTRO_TUNE = ["Bb4:8 G4:8", "D5:8 Bb4:8", "A4:16", None]

# (name, chords, tune, the echo, the third below, up: half steps higher)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, False, False, 0),
    ("A", A_CHORDS, A_TUNE, False, False, 0),
    ("A2", A_CHORDS, A_TUNE, True, False, 0),
    ("calm", CALM_CHORDS, None, False, False, 0),
    ("A3", A_CHORDS, A_TUNE, True, True, 0),
    ("lift", LIFT_CHORDS, LIFT_TUNE, True, True, 1),
    ("outro", OUTRO_CHORDS, OUTRO_TUNE, True, False, 1),
]
RIPPLE = (0, 1, 2, 3, 2, 1, 3, 2, 0, 1, 2, 3, 1, 2, 3, 2)   # the guitar: a sixteenth each, over the four notes of the chord
LOW_RIPPLE = (0, 1, 0, 2, 0, 1, 0, 2)                         # the calm part: slower (an eighth each) and low


def build():
    lead = Layer("Harp (the tune)", 100, 100)
    hold = Layer("Harp (the tune held)", 50, 100)
    harmony = Layer("Harp (a third below)", 45, 80)
    low = Layer("Harp (the calm part, low)", 65, 100)
    echo = Layer("Iron xylophone (the echo)", 55, 90)
    echo2 = Layer("Iron xylophone (the second echo)", 40, 110)
    guitar = Layer("Guitar (the ripple)", 70, 60)
    bass = Layer("Bass (soft, long)", 85, 100)
    bell = Layer("Bell (a glint)", 40, 100)
    layers = [lead, hold, harmony, low, echo, echo2, guitar, bass, bell]

    def put(layer, tick, instrument, sounding, vel):
        if tick not in layer.notes:
            layer.put_sound(tick, instrument, sounding, max(1, min(100, int(round(vel)))))

    position = 0
    for name, chords, tune, echoing, third, up in SECTIONS:
        count = len(chords)
        start_of_section = position
        for index, chord in enumerate(chords):
            start = position
            root, tones = CHORDS[chord]
            root += up
            tones = [tone + up for tone in tones]
            fade = 1.0
            if name == "intro":
                fade = 0.45 + 0.55 * index / 3
            elif name == "outro":
                fade = 1.0 - 0.3 * index

            # ---- the guitar: a ripple of sixteenths that never stops (in the calm part: slower and low)
            if name == "calm":
                for number, tick in enumerate(range(0, BAR, 2)):
                    put(guitar, start + tick, GUITAR, tones[LOW_RIPPLE[number] % len(tones)], (36 if number % 4 == 0 else 26))
            else:
                for number, tick in enumerate(range(BAR)):
                    if name == "outro" and index == 3 and number % 2:
                        continue
                    put(guitar, start + tick, GUITAR, tones[RIPPLE[number] % len(tones)], (46 if number % 4 == 0 else 30) * fade)

            # ---- the bass: a long soft note, struck four times a bar as it fades (the warm low bed); it comes in at the second bar of the intro
            if not (name == "intro" and index < 1):
                swell(bass, BASS, root, start, BAR, (64 if name != "calm" else 48) * fade, (30 if name != "calm" else 22) * fade, 4)

            # ---- the calm part: single low notes of the harp, slow
            if name == "calm":
                put(low, start, HARP, tones[0] + 12, 46)
                put(low, start + 8, HARP, tones[2], 38)
                put(echo, start + 4, IRON_XYLOPHONE, tones[0] + 12, 18)

            # ---- the tune, and its echo
            if tune is not None and tune[index] is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        at = start + tick
                        note += up
                        put(lead, at, HARP, note, (92 if tick == 0 else 82) * fade)
                        for again in range(4, length, 4):    # a long note is struck again every beat, softer
                            put(hold, at + again, HARP, note, (44 - again * 2) * fade)
                        if third:
                            put(harmony, at, HARP, max(67, third_below(note - up, BB_MAJOR) + up), 40 * fade)
                        if echoing:
                            put(echo, at + 4, IRON_XYLOPHONE, note, 34 * fade)
                            put(echo2, at + 8, IRON_XYLOPHONE, note, 18 * fade)
                    tick += length
            position += BAR
        if name == "lift":    # a glint of the bell at the lift: G6, very quiet, fading by itself
            swell(bell, BELL, 91 + up, start_of_section, 12, 32, 6, 4)
    return layers, position


def main():
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/cloud_remembered.nbs"
    layers, length = build()
    assert length % BAR == 0, length
    removed = limit_polyphony(layers, 8)
    write_nbs(target, layers, length, TPS, TITLE,
              "Field theme, bright and flowing: a harp tune over a never-stopping guitar ripple and a warm soft bass, a calm minor part, and a lift of a half step; no drums.", LOOP_START_BAR)
    report(target, layers, length, TPS)
    print(f"  ({removed} quiet notes taken away where more than 8 sounded at once)")


if __name__ == "__main__":
    main()
