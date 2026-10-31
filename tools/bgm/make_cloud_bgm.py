#!/usr/bin/env python3
"""Makes cloud_light.nbs: the field theme "Light Between the Clouds (Field003)", a bright but fleeting song (Note Block Studio v5 file).

    python3 tools/bgm/make_cloud_bgm.py            writes src/main/resources/bgm/cloud_light.nbs
    python3 tools/bgm/make_cloud_bgm.py out.nbs    writes somewhere else

Asked for as "a fleeting yet bright song, like" a piece of music of another game (a link was sent). Only that mood was taken: nothing was heard or copied - the tune, the chords and the form here are our own,
written from the general words: BRIGHT (a major key, seventh chords with the ninth and the raised fourth, rising lines that reach for the high notes) and FLEETING (a slow, drifting 75 BPM, lots of air: the notes
are few and short, rests in the middle of the phrases, every melody note answered by a fading echo, and no drums at all, so nothing pushes it).
  - G major, 4/4, 75 BPM (one tick = a sixteenth; 5 ticks a second, so a song tick is exactly 4 server ticks; a bar is 3.2 seconds), 44 bars of loop (2:21) after a 4-bar intro.
  - The voices: the harp sings the tune (sounds G4 to F#5); a fingerpicked guitar plays the chords as a soft ripple of eighths (G3 to F#4); the bass is one low, soft note that is struck again once as it fades;
    the iron xylophone is the ECHO (the "delay" of the style guide: every melody note comes back a beat later, much softer); a single bell far above, very quiet, at the start of the big parts.
    No drums, no flute (it sounds too high), nothing held with a pad: a note block fades by itself, and the song lets it.
  - Chords (seventh chords, nothing heavy): A is Gmaj7 D/F# Em7 Bm7 Cmaj7 Am7 D D; B is Em7 Cmaj7 G/B D Em7 Bm7 Cmaj7 D (the F# of the tune over Cmaj7 is the "raised fourth" that makes it float).
  - Form: intro (the guitar alone, the bass comes in), A, B, the rain (the guitar ripples under single high drops of the harp, no tune), A again (the echo and the bell), B again, and an outro that fades on the
    D chord (the dominant) so that the jump back to the start (G) is smooth. It loops from bar 5 (tick 64).
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 5.0           # 75 BPM: a quarter note is 4 ticks, a song tick is 4 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Light Between the Clouds (Field003)"
G_MAJOR = scale("G3 A3 B3 C4 D4 E4 F#4 G4 A4 B4 C5 D5 E5 F#5")

# chord -> (the bass root, the notes of the guitar: all between 52 and 66)
CHORDS = {
    "Gmaj7": (43, [55, 59, 62, 66]),
    "D/F#": (42, [54, 57, 62, 66]),
    "Em7": (40, [52, 55, 59, 62]),
    "Bm7": (35, [57, 59, 62, 66]),
    "Cmaj7": (36, [55, 59, 60, 64]),
    "Am7": (45, [55, 57, 60, 64]),
    "D": (38, [54, 57, 62, 66]),
    "G/B": (35, [55, 59, 62, 67 - 12]),
}

INTRO_CHORDS = ["Gmaj7", "D/F#", "Em7", "D"]
A_CHORDS = ["Gmaj7", "D/F#", "Em7", "Bm7", "Cmaj7", "Am7", "D", "D"]
A_TUNE = [                      # the tune: it rises and sighs; notes few and short, rests inside the phrases
    "r:2 B4:2 D5:6 B4:2 A4:4", "A4:6 F#4:2 A4:4 D5:4", "E5:8 D5:4 B4:4", "D5:6 B4:2 A4:8",
    "G4:4 C5:4 E5:8", "E5:6 D5:2 C5:4 A4:4", "B4:4 D5:4 F#5:8", "E5:6 D5:2 A4:8",
]
B_CHORDS = ["Em7", "Cmaj7", "G/B", "D", "Em7", "Bm7", "Cmaj7", "D"]
B_TUNE = [
    "r:4 B4:4 E5:8", "D5:6 C5:2 B4:8", "B4:4 D5:4 B4:4 G4:4", "A4:6 F#4:2 D5:8",
    "E5:6 F#5:2 E5:8", "D5:4 F#5:4 D5:8", "E5:8 D5:4 B4:4", "A4:12 r:4",
]
RAIN_CHORDS = ["Em7", "Cmaj7", "G/B", "D", "Em7", "Bm7", "Cmaj7", "D"]
OUTRO_CHORDS = ["D", "D/F#", "D", "D"]
OUTRO_TUNE = ["A4:8 F#4:8", "B4:8 A4:8", "D5:12 r:4", "A4:16"]

# (name, chords, tune, the echo and the bell)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, False),
    ("A", A_CHORDS, A_TUNE, False),
    ("B", B_CHORDS, B_TUNE, False),
    ("rain", RAIN_CHORDS, None, False),
    ("A2", A_CHORDS, A_TUNE, True),
    ("B2", B_CHORDS, B_TUNE, True),
    ("outro", OUTRO_CHORDS, OUTRO_TUNE, True),
]
PATTERN = (0, 2, 1, 3, 2, 3, 1, 2)   # the ripple of the guitar, an eighth each
DROPS = {0: 3, 6: 2, 10: 3, 13: 1}   # the rain: (tick of the bar) -> the number of the chord note (from the top) of the harp's single drop


def build():
    lead = Layer("Harp (the tune)", 100, 100)
    hold = Layer("Harp (the tune held)", 50, 100)
    drops = Layer("Harp (the rain)", 70, 100)
    echo = Layer("Iron xylophone (the echo)", 55, 90)
    echo2 = Layer("Iron xylophone (the second echo)", 40, 110)
    guitar = Layer("Guitar (the ripple)", 70, 60)
    bass = Layer("Bass (soft)", 80, 100)
    bell = Layer("Bell (a glint)", 40, 100)
    layers = [lead, hold, drops, echo, echo2, guitar, bass, bell]

    def put(layer, tick, instrument, sounding, vel):
        if tick not in layer.notes:
            layer.put_sound(tick, instrument, sounding, max(1, min(100, int(round(vel)))))

    position = 0
    for name, chords, tune, echoing in SECTIONS:
        count = len(chords)
        for index, chord in enumerate(chords):
            start = position
            root, tones = CHORDS[chord]
            fade = 1.0
            if name == "intro":
                fade = 0.5 + 0.5 * index / 3
            elif name == "outro":
                fade = 1.0 - 0.22 * index

            # ---- the guitar: a ripple of eighths (and in the outro it thins out)
            for number, tick in enumerate(range(0, BAR, 2)):
                if name == "outro" and index >= 2 and number % 2:
                    continue
                put(guitar, start + tick, GUITAR, tones[PATTERN[number] % len(tones)], (44 if number % 4 == 0 else 32) * fade)

            # ---- the bass: one soft note, struck again as it fades (it comes in at the third bar of the intro)
            if not (name == "intro" and index < 2):
                put(bass, start, BASS, root, 66 * fade)
                put(bass, start + 8, BASS, root, 34 * fade)

            # ---- the rain: single high drops of the harp (no tune)
            if name == "rain":
                top = sorted(tones)
                for tick, from_top in DROPS.items():
                    drop = top[-1 - (from_top % len(top))] + 12 if top[-1 - (from_top % len(top))] + 12 <= 77 else top[-1 - (from_top % len(top))]
                    put(drops, start + tick, HARP, drop, 54 - tick)
                    put(echo, start + tick + 4, IRON_XYLOPHONE, drop, 24)

            # ---- the tune, and its echo
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        at = start + tick
                        put(lead, at, HARP, note, (92 if tick == 0 else 82) * fade)
                        for again in range(4, length, 4):    # a long note is struck again every beat, softer
                            put(hold, at + again, HARP, note, (44 - again * 2) * fade)
                        if echoing:
                            put(echo, at + 4, IRON_XYLOPHONE, note, 34 * fade)
                            put(echo2, at + 8, IRON_XYLOPHONE, note, 18 * fade)
                    tick += length
            position += BAR
        if echoing and name in ("A2", "B2"):    # a glint of the bell at the start of the big parts: G6, far above and very quiet, fading by itself
            swell(bell, BELL, 91, position - count * BAR, 12, 32, 6, 4)
    return layers, position


def main():
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/cloud_light.nbs"
    layers, length = build()
    assert length % BAR == 0, length
    removed = limit_polyphony(layers, 8)
    write_nbs(target, layers, length, TPS, TITLE,
              "Field theme, bright but fleeting: a harp tune over a fingerpicked guitar ripple and a soft bass, answered by a fading echo; no drums.", LOOP_START_BAR)
    report(target, layers, length, TPS)
    print(f"  ({removed} quiet notes taken away where more than 8 sounded at once)")


if __name__ == "__main__":
    main()
