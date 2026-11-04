#!/usr/bin/env python3
"""Makes first_town.nbs: "Morning in the First Town" (Field005), the song of the first town, written by the recipe of "BGM制作ガイド" (Note Block Studio v5 file).

    python3 tools/bgm/make_town_bgm.py            writes src/main/resources/bgm/first_town.nbs
    python3 tools/bgm/make_town_bgm.py out.nbs    writes somewhere else

The song (one recipe row of the guide, "始まりの街"): D major (Ionian), 4/4, 100 BPM (6.67 ticks a second, a tick is a sixteenth), chords I - vi - IV - bVII, and the chord changes every
TWO bars (a peaceful song). The one device of the song: the world theme (the rising F# A D / D E F# of "Under an Endless Sky", Field001) in its clearest form. The theme comes six times
and no more than twice in the same shape (harp alone; the same with a new ending; dotted; hidden in the guitar of the B part; an octave up over everything at the peak; stretched in the bass of the calm part).
Form (64 bars, 2:34; it loops from bar 5): intro 4 / A 16 / A' 16 / B 12 / A'' 12 (the peak, at 75 %) / calm 4. B takes away: the melody goes to the iron xylophone, the chords borrow the minor
iv (Gm) and the last bar is empty (a rest before the peak). Layers are added in steps of 4 or 8 bars; the hat, the last to come, is only in the peak. The bass is in half notes on the root and the fifth.
The notes are written by what they SOUND as (nbslib.put_sound), so the flute (F#5-F#7) and the bell (F#6-F#8) are where the guide wants them in the engine's real octaves.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0 / 3.0    # a divisor of 20: a song tick is 3 server ticks; a tick is a sixteenth, a quarter is 4 ticks: 100 BPM
BAR = 16
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Morning in the First Town (Field005)"

# chord: (harp arpeggio tones low to high, bass root, bass fifth, bell tone, guitar tones)   - the bass never has a third; everything sounds in the real range of its instrument
CHORDS = {
    "D":  ([54, 57, 62, 66], 38, 45, 90, [50, 57, 62]),
    "Bm": ([59, 62, 66, 62], 35, 42, 90, [47, 54, 59]),
    "G":  ([55, 59, 62, 67], 43, 50, 95, [43, 50, 55]),
    "C":  ([55, 60, 64, 67], 36, 43, 91, [48, 55, 60]),
    "Gm": ([55, 58, 62, 67], 43, 50, 94, [43, 50, 55]),
    "Em": ([55, 59, 64, 59], 40, 47, 95, [40, 47, 52]),
    "A":  ([57, 61, 64, 69], 33, 40, 97, [45, 52, 57]),
}

# every chord lasts two bars, so each chord has a pair of melody bars
INTRO = ["D", "C"]
A_CHORDS = ["D", "Bm", "G", "C", "D", "Bm", "G", "C"]
A_MELODY = [
    ("F#4:2 A4:2 D5:4 D5:2 E5:2 F#5:4", "E5:4 D5:4 A4:8"),            # the world theme, as it is in the plains
    ("D5:6 B4:2 F#4:4 B4:4", "A4:12 r:4"),
    ("B4:4 D5:4 E5:4 D5:4", "B4:8 G4:8"),
    ("E5:6 D5:2 C5:4 E5:4", "D5:8 r:4 A4:4"),
    ("F#4:2 A4:2 D5:4 D5:2 E5:2 F#5:4", "E5:4 F#5:4 D5:8"),             # the theme again, a new ending
    ("D5:4 F#5:4 E5:4 D5:4", "B4:12 r:4"),
    ("B4:2 D5:2 E5:4 D5:4 B4:4", "G4:8 B4:8"),
    ("G4:4 C5:4 E5:8", "D5:4 C5:4 A4:8"),
]
A2_CHORDS = ["D", "Bm", "G", "C", "D", "C", "G", "D"]
A2_MELODY = [
    ("F#4:3 A4:1 D5:6 E5:2 F#5:4", "E5:6 D5:2 A4:8"),                   # the theme with a dotted rhythm
    ("D5:4 F#5:4 E5:4 D5:4", "B4:8 D5:4 B4:4"),
    ("B4:6 D5:2 E5:4 D5:4", "G4:4 B4:4 D5:8"),
    ("C5:4 E5:4 D5:4 C5:4", "E5:8 r:4 A4:4"),
    ("A4:2 D5:2 F#5:4 E5:4 D5:4", "A4:8 F#4:4 A4:4"),
    ("G4:4 C5:4 E5:4 C5:4", "D5:12 r:4"),
    ("D5:6 B4:2 G4:4 B4:4", "D5:4 E5:4 D5:8"),
    ("A4:4 D5:4 F#5:4 E5:4", "E5:8 D5:4 B4:4"),
]
B_CHORDS = ["G", "Gm", "D", "Bm", "Em", "A"]
B_MELODY = [  # on the iron xylophone
    ("D5:8 B4:4 G4:4", "B4:6 A4:2 G4:8"),
    ("Bb4:6 A4:2 G4:8", "D5:12 r:4"),
    ("A4:4 D5:4 F#5:8", "E5:4 D5:4 A4:8"),
    ("B4:4 D5:4 F#5:4 E5:4", "D5:8 B4:8"),
    ("G4:4 B4:4 E5:8", "D5:6 B4:2 G4:8"),
    ("A4:4 C#5:4 E5:4 A4:4", "E5:6 r:10"),                               # the last bar is empty: a rest before the peak
]
P_CHORDS = ["D", "Bm", "G", "C", "D", "G"]
P_MELODY = [
    ("F#4:2 A4:2 D5:4 D5:2 E5:2 F#5:4", "F#5:4 E5:4 D5:8"),             # the theme, with the flute an octave above and the bell
    ("D5:4 F#5:4 E5:4 D5:4", "D5:4 B4:4 D5:8"),
    ("B4:4 D5:4 F#5:8", "E5:8 D5:8"),
    ("E5:4 D5:4 C5:4 E5:4", "D5:8 E5:8"),
    ("F#5:6 E5:2 D5:4 A4:4", "D5:8 F#5:8"),
    ("E5:4 D5:4 B4:4 D5:4", "D5:8 B4:8"),
]
CALM_CHORDS = ["Bm", "C"]
CALM_MELODY = [("D5:8 F#5:8", "E5:8 D5:8"), ("E5:8 D5:8", "r:16")]


def melody_bars(pairs):
    return [bar for pair in pairs for bar in pair]


def bars_of(chords):
    return [chord for chord in chords for _ in range(2)]


def build():
    melody = Layer("Harp (melody)", 100, 100)
    arpeggio = Layer("Harp (broken chords)", 70, 135)
    xylophone = Layer("Iron xylophone (melody of B)", 90, 100)
    flute = Layer("Flute (an octave above the harp)", 55, 80)
    guitar = Layer("Guitar (afterbeats, the theme hidden in B)", 70, 65)
    bass = Layer("Bass (half notes)", 80, 100)
    bell = Layer("Bell (phrase heads, quiet)", 55, 120)
    hat = Layer("Hat (soft, only in the peak)", 40, 110)
    layers = [melody, arpeggio, xylophone, flute, guitar, bass, bell, hat]

    bar_number = 0
    # (name, chords by the bar, melody bars or None, melody layer, lead loudness, arpeggio step in ticks (4 = quarters, 2 = eighths), accompaniment loudness, voices)
    sections = [
        ("intro", bars_of(INTRO), None, None, 1.0, 4, 0.9, {"bass", "bell-first"}),
        ("A", bars_of(A_CHORDS), melody_bars(A_MELODY), melody, 0.85, 4, 1.0, {"bass", "bell-soft"}),
        ("A2", bars_of(A2_CHORDS), melody_bars(A2_MELODY), melody, 0.92, 2, 1.0, {"bass", "flute", "guitar", "bell"}),
        ("B", bars_of(B_CHORDS), melody_bars(B_MELODY), xylophone, 0.8, 4, 0.85, {"bass-long", "bell-soft", "theme-guitar"}),
        ("peak", bars_of(P_CHORDS), melody_bars(P_MELODY), melody, 1.0, 2, 1.1, {"bass", "flute", "guitar", "bell", "hat"}),
        ("calm", bars_of(CALM_CHORDS), melody_bars(CALM_MELODY), melody, 0.7, 4, 0.8, {"bass-motif", "bell-soft", "run"}),
    ]
    for name, chords, tune, tune_layer, lead, step, loudness, voices in sections:
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            last_bar = index == len(chords) - 1
            tones, root, fifth, bell_tone, guitar_tones = CHORDS[chord]
            first_of_chord = index % 2 == 0
            empty_end = name == "B" and last_bar          # the rest before the peak: only the bass root rings, and the harp stops after the first beat
            # broken chord on the harp
            order = [0, 1, 2, 1] if step == 4 else [0, 1, 2, 3, 2, 1, 2, 1]
            for place, tone_index in enumerate(order):
                tick = place * step
                if (empty_end and tick >= 4) or ("run" in voices and last_bar and tick >= 8):
                    break          # (the run into the loop takes the second half of the last bar)
                accent = 1.0 if place == 0 else 0.85
                arpeggio.put(start + tick, HARP, tones[tone_index], velocity(34 * accent, loudness))
            # the bass: half notes on the root and the fifth
            if "bass" in voices:
                bass.put_sound(start, BASS, root, velocity(70, loudness))
                if not empty_end:
                    bass.put_sound(start + 8, BASS, fifth, velocity(56, loudness))
            if "bass-long" in voices:
                if first_of_chord:
                    bass.put_sound(start, BASS, root, velocity(62, loudness))
                    bass.put_sound(start + 8, BASS, fifth, velocity(46, loudness))
                else:
                    bass.put_sound(start, BASS, root, velocity(50, loudness))
            if "bass-motif" in voices:
                if name == "calm" and index < 2:      # the theme stretched four times in the bass: F# A D over Bm
                    if index == 0:
                        for offset, tone in ((0, 42), (8, 45)):
                            bass.put_sound(start + offset, BASS, tone, 62)
                    else:
                        bass.put_sound(start, BASS, 50, 62)
                else:                                   # over the C chord: root and fifth, quiet
                    bass.put_sound(start, BASS, root, 54)
                    if index == 2:
                        bass.put_sound(start + 8, BASS, fifth, 44)
            # bell on the head of each phrase (every two bars), and at the very start of the song
            if first_of_chord and ("bell" in voices or ("bell-soft" in voices and index % 4 == 0)):
                bell.put_sound(start, BELL, bell_tone, 52 if name == "peak" else 44)
            if "bell-first" in voices and index == 0:
                bell.put_sound(start, BELL, bell_tone, 44)
            # afterbeats of the guitar (the peak and A2 only)
            if "guitar" in voices and not empty_end:
                for offset, tone_index in ((4, 1), (12, 2)):
                    guitar.put_sound(start + offset, GUITAR, guitar_tones[tone_index], velocity(34, loudness))
            if "hat" in voices:
                for offset in (4, 12):
                    hat.put(start + offset, HAT, 66, 22)
            # the theme hidden in the guitar of B: D F Bb (rising a third and a fourth), under the Gm chord
            if "theme-guitar" in voices and chord == "Gm" and first_of_chord:
                for offset, tone, length in ((0, 50, 2), (2, 53, 2), (4, 58, 4)):
                    guitar.put_sound(start + offset, GUITAR, tone, 52)
            # the run into the loop: A C D E, then the F# the song starts on
            if "run" in voices and last_bar:
                for place, tone in enumerate((57, 60, 62, 64)):
                    arpeggio.put(start + 8 + place * 2, HARP, tone, 38 + place * 4)
            # the melody
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        peak_note = note >= 74
                        vel = velocity(88 if tick == 0 else (80 if peak_note else 72), lead)
                        if tune_layer is melody:
                            melody.put(start + tick, HARP, note, vel)
                        else:
                            xylophone.put_sound(start + tick, IRON_XYLOPHONE, note, vel)
                        if "flute" in voices and (name == "peak" or index >= 4):
                            flute.put_sound(start + tick, FLUTE, note + 12, velocity(46, lead))
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    removed = limit_polyphony(layers, 8)
    assert removed == 0, f"{removed} notes were thinned out"
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/first_town.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE,
              "Song of the first town. Peaceful, I-vi-IV-bVII in D major with the world theme of the plains; the chords change every two bars; 64 bars, loops from bar 5.",
              LOOP_START_BAR)
    report(target, layers, length, TPS)


if __name__ == "__main__":
    main()
