#!/usr/bin/env python3
"""Makes gate_gravity.nbs: the dungeon theme "The Gate Where Gravity Turns (Dungeon004)", a deep, heavy, slow piece (Note Block Studio v5 file).

    python3 tools/bgm/make_gate_bgm.py            writes src/main/resources/bgm/gate_gravity.nbs
    python3 tools/bgm/make_gate_bgm.py out.nbs    writes somewhere else

Made from the NUMBERS of an analysis of a reference recording (an mp3 the user supplied: LaTale Music, Gates of Lysithea - Reverse Gravity, 99 s), NOT from its notes: nothing of its melody, chords or phrases is used -
the tune and the chords here are our own. What the measurements said (tools/bgm/analyze_audio.py and analyze_audio_tempo.py), and what was taken from them:
  - Tempo about 96 BPM, the notes in a flow of about sixteenths when they move (the most common gap is 0.16 s), but FEW notes: 3 a second on average, and for long stretches almost none (0.1 a second
    between 48 and 72 s: a held, droning sound). Taken: 100 BPM, a sparse tune of long notes, a guitar that ripples only now and then, and long swells instead of busy parts.
  - DEEP: the centre of the sound is only 230 to 500 Hz (the other recording: 900), and 50 to 86 percent of the energy is below 250 Hz. Taken: the bass and the didgeridoo carry the piece (struck again as
    they fade, loud), the guitar and the harp stay low, almost no high notes (no bell, no flute, no chime).
  - Key: B-flat major with its relative G minor; a long section at 56 to 72 s sits on a D (the dominant of G minor) with the E and the C# around it, and the loudest part of the piece is there (the loudness
    peaks 5 dB higher than elsewhere). Taken: G minor, and a climax of eight bars over a D pedal in the lowest register (the chords change over it: Gm/D Eb/D Cm/D D: the half-step rub of Eb against D).
  - Shape: it opens quietly (-15 dB) and fills, a dip at one third (32 to 40 s: quiet, the bass gone, the middle register 52 percent), a rise, the loud D climax at 56 to 72 s, a return to the first key, and an
    ending that fades onto a held low note. Taken: intro 4 bars, A 10, the dip 4, the rise 8, the climax 8, the return 14, the outro 4 (52 bars = 2:05); it loops from bar 5 (tick 64) and the outro ends on D,
    the dominant, so that the jump back to G minor is smooth.
Instruments (natural only): the bass, the didgeridoo (the drone under the climax), the guitar (a low ripple), the harp (the tune), the iron xylophone (a quiet echo) and a soft kick (the heartbeat of the
climax). Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0 / 3.0    # 100 BPM: a tick is a sixteenth, 6.67 ticks a second, a song tick is 3 server ticks
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "The Gate Where Gravity Turns (Dungeon004)"
G_MINOR = scale("G3 A3 Bb3 C4 D4 Eb4 F4 G4 A4 Bb4 C5 D5 Eb5 F5")

# chord -> (the bass root, the notes of the guitar: all between 50 and 66)
CHORDS = {
    "Gm": (43, [55, 58, 62, 65]),
    "Eb": (39, [51, 55, 58, 63]),
    "Bb": (34, [53, 58, 62, 65]),
    "F": (41, [53, 57, 60, 65]),
    "Cm": (36, [55, 58, 60, 63]),
    "D": (38, [54, 57, 62, 66]),
    "Gm/D": (38, [55, 58, 62, 65]),
    "Eb/D": (38, [51, 55, 58, 63]),
    "Cm/D": (38, [55, 58, 60, 63]),
}

A_CHORDS = ["Gm", "Eb", "Bb", "F", "Gm", "Eb", "Bb", "F", "Cm", "D"]
A_TUNE = [                      # sparse, long notes, a sigh at the end of each phrase
    "r:4 D5:8 Bb4:4", "Bb4:6 G4:2 Eb5:8", "D5:8 F5:4 D5:4", "C5:8 A4:8",
    "Bb4:4 D5:4 G4:8", "G4:8 Bb4:4 Eb5:4", "F5:6 D5:2 Bb4:8", "A4:12 r:4",
    "C5:8 Eb5:4 D5:4", "A4:8 D5:8",
]
DIP_CHORDS = ["Cm", "Gm", "Eb", "D"]
RISE_CHORDS = ["Gm", "Eb", "Bb", "F", "Cm", "Gm", "Eb", "D"]
RISE_TUNE = [
    "G4:4 Bb4:4 D5:8", "Bb4:4 Eb5:4 G4:8", "D5:4 F5:4 Bb4:8", "C5:4 F5:4 A4:8",
    "Eb5:4 C5:4 G4:8", "D5:4 Bb4:4 G4:8", "G4:4 Bb4:4 Eb5:8", "A4:4 D5:4 A4:8",
]
CLIMAX_CHORDS = ["Gm/D", "Eb/D", "Cm/D", "D", "Gm/D", "Eb/D", "Cm/D", "D"]
CLIMAX_TUNE = [                 # almost nothing: long high notes over the pedal
    "r:8 D5:8", "Eb5:16", "C5:8 D5:8", "A4:16", "Bb4:8 D5:8", "G4:8 Eb5:8", "Eb5:8 C5:8", "D5:16",
]
RETURN_CHORDS = A_CHORDS[:8] + ["Cm", "Gm", "Eb", "Bb", "F", "D"]
RETURN_TUNE = A_TUNE[:8] + ["C5:8 Eb5:4 D5:4", "D5:4 Bb4:4 G4:8", "G4:8 Bb4:4 Eb5:4", "F5:6 D5:2 Bb4:8", "C5:8 A4:8", "A4:12 r:4"]
INTRO_CHORDS = ["Gm", "Gm", "Eb", "D"]
OUTRO_CHORDS = ["Gm", "Eb", "D", "D"]
OUTRO_TUNE = ["D5:8 Bb4:8", "Bb4:8 G4:8", "A4:16", None]

# (name, chords, tune, echo, the bass plays, loudness, the guitar: ripple / beats / low)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, False, True, 0.7, "low"),
    ("A", A_CHORDS, A_TUNE, False, True, 0.85, "beats"),
    ("dip", DIP_CHORDS, None, False, False, 0.5, "low"),
    ("rise", RISE_CHORDS, RISE_TUNE, True, True, 0.95, "ripple"),
    ("climax", CLIMAX_CHORDS, CLIMAX_TUNE, True, True, 1.15, "beats"),
    ("return", RETURN_CHORDS, RETURN_TUNE, True, True, 0.9, "beats"),
    ("outro", OUTRO_CHORDS, OUTRO_TUNE, True, True, 0.7, "low"),
]
RIPPLE = (0, 1, 2, 3, 2, 1, 3, 2, 0, 1, 2, 3, 1, 2, 3, 2)


def build():
    lead = Layer("Harp (the tune)", 100, 100)
    hold = Layer("Harp (the tune held)", 50, 100)
    low = Layer("Harp (the dip, low)", 60, 100)
    echo = Layer("Iron xylophone (the echo)", 50, 90)
    guitar = Layer("Guitar (low)", 75, 60)
    bass = Layer("Bass (deep)", 100, 100)
    drone = Layer("Didgeridoo (the drone)", 70, 100)
    heart = Layer("Kick (the heartbeat)", 80, 100)
    layers = [lead, hold, low, echo, guitar, bass, drone, heart]

    def put(layer, tick, instrument, sounding, vel):
        if tick not in layer.notes:
            layer.put_sound(tick, instrument, sounding, max(1, min(100, int(round(vel)))))

    position = 0
    for name, chords, tune, echoing, bass_on, loud, guitar_mode in SECTIONS:
        for index, chord in enumerate(chords):
            start = position
            root, tones = CHORDS[chord]
            fade = loud
            if name == "intro":
                fade = loud * (0.55 + 0.45 * index / 3)
            elif name == "outro":
                fade = loud * (1.0 - 0.28 * index)

            # ---- the bass: long deep notes, struck four times a bar as they fade; the didgeridoo under it in the drone parts
            if bass_on:
                swell(bass, BASS, root, start, BAR, 78 * fade, 40 * fade, 4)
                if name in ("A", "rise", "climax", "return", "intro", "outro"):
                    swell(drone, DIDGERIDOO, root, start, BAR, (62 if name == "climax" else 34) * fade, (80 if name == "climax" else 46) * fade, 4)

            # ---- the guitar: low, and only now and then
            if guitar_mode == "ripple":
                for number, tick in enumerate(range(BAR)):
                    put(guitar, start + tick, GUITAR, tones[RIPPLE[number] % len(tones)], (40 if number % 4 == 0 else 26) * fade)
            elif guitar_mode == "beats":
                for number, tick in enumerate(range(0, BAR, 4)):
                    put(guitar, start + tick, GUITAR, tones[(0, 2, 1, 2)[number] % len(tones)], 36 * fade)
            else:
                put(guitar, start, GUITAR, tones[0], 30 * fade)
                put(guitar, start + 8, GUITAR, tones[2], 24 * fade)

            # ---- the heartbeat of the climax
            if name == "climax":
                for tick, vel in ((0, 70), (6, 44)):
                    heart.put(start + tick, BASEDRUM, 62, vel)

            # ---- the dip: single low notes of the harp
            if name == "dip":
                put(low, start, HARP, tones[1] + 12 if tones[1] + 12 <= 77 else tones[1], 40)
                put(low, start + 8, HARP, tones[2], 32)
                put(echo, start + 4, IRON_XYLOPHONE, tones[1] + 12 if tones[1] + 12 <= 77 else tones[1], 16)

            # ---- the tune, and its echo
            if tune is not None and tune[index] is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        at = start + tick
                        put(lead, at, HARP, note, (90 if tick == 0 else 80) * fade)
                        for again in range(4, length, 4):    # a long note is struck again every beat, softer
                            put(hold, at + again, HARP, note, (46 - again * 2) * fade)
                        if echoing:
                            put(echo, at + 4, IRON_XYLOPHONE, note, 30 * fade)
                    tick += length
            position += BAR
    return layers, position


def main():
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/gate_gravity.nbs"
    layers, length = build()
    assert length % BAR == 0, length
    removed = limit_polyphony(layers, 8)
    write_nbs(target, layers, length, TPS, TITLE,
              "Dungeon theme, deep and heavy: long bass and didgeridoo swells, a sparse harp tune, a loud climax over a D pedal; no high notes.", LOOP_START_BAR)
    report(target, layers, length, TPS)
    print(f"  ({removed} quiet notes taken away where more than 8 sounded at once)")


if __name__ == "__main__":
    main()
