#!/usr/bin/env python3
"""Makes atlas_dungeon_2.nbs: "Below the Sleeping Giant (Dungeon002)", the theme inside the Atlas cave dungeon, in a lower register than Dungeon001.

    python3 tools/bgm/make_atlas_dungeon2_bgm.py            writes src/main/resources/bgm/atlas_dungeon_2.nbs
    python3 tools/bgm/make_atlas_dungeon2_bgm.py out.nbs    writes somewhere else

The same piece as "Hollow Beneath the Giant (Dungeon001)" (the same form, the same tune, 100 BPM, 56 bars, 2:14), written again by what SOUNDS:
  - Dungeon001 gave the tune to the flute written in the notes of the file, and the flute sounds two octaves above the harp: it was at E6-F#7. Here there is no flute.
  - The tune is on the piano (the harp), a fifth lower than before (B minor instead of E minor: B3 to D5), and the bass doubles it two octaves down (B1 to C#3).
  - The accompaniment is the guitar (G2 to F#4) and the bass pulse (B1 to E3); the water drops are on the iron xylophone (B4 to F#5) instead of the chime (B7 and up).
  - The piano plays a repeated note under a long one (a note of the piano dies away at once): the rulebook's way to a sustained sound.

The motif of Atlas is the phrase, not the notes: the tonic triad falling in three steps and landing on the tonic (E5 B4 G4 E4 in Dungeon001: a fourth, a major third and a minor third
down). Here the bass plays it at the pitch it has in the piece (B2 F#2 D2 B1, the same steps) in the low voice and the piano takes up the shape in the tune.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers
import make_atlas_dungeon_v1_bgm as base

TPS = base.TPS
LOOP_START_BAR = base.LOOP_START_BAR
TITLE = "Below the Sleeping Giant (Dungeon002)"
SHIFT = -5  # E minor of Dungeon001 -> B minor: the written notes of the tune are moved down a fifth

CHORD_NAMES = {"Em": "Bm", "F": "C", "Dm": "Am", "C": "G", "G": "D", "Am": "Em", "B7": "F#7"}
# the guitar: five notes, low to high, inside what the guitar sounds (F#2 to F#4); the piano's soft arpeggio is the same an octave up (F#3 to F#5 is its range)
GUITAR_TONES = {
    "Bm": [47, 54, 59, 62, 66],
    "C": [48, 52, 55, 60, 64],
    "Am": [45, 52, 57, 60, 64],
    "G": [43, 50, 55, 59, 62],
    "D": [50, 54, 57, 62, 66],
    "Em": [47, 52, 55, 59, 64],
    "F#7": [54, 58, 61, 64, 66],
}
ROOTS = {"Bm": 47, "C": 48, "Am": 45, "G": 43, "D": 50, "Em": 52, "F#7": 42}  # the bass (it sounds F#1 to F#3)
ARPEGGIO = base.ARPEGGIO
ECHO_DELAY = base.ECHO_DELAY


def drop_ticks(bar_number, count):
    return base.drop_ticks(bar_number, count)


def tune_sound(written):
    """The sound of a note of the tune of Dungeon001 (written in the notes of the file) in this piece."""
    return written + SHIFT


def build():
    melody = Layer("Piano (the tune)", 100, 100)
    hold = Layer("Piano (the tune held)", 60, 100)
    low = Layer("Bass (doubles the tune)", 60, 100)
    harmony = Layer("Piano (a third below)", 50, 80)
    echo = Layer("Piano (echo of the cave)", 40, 150)
    arp_guitar = Layer("Guitar (arpeggio)", 70, 70)
    arp_piano = Layer("Piano (soft arpeggio)", 55, 115)
    pulse_bass = Layer("Bass (the pulse)", 85, 100)
    pulse_guitar = Layer("Guitar (muted pulse)", 55, 60)
    drops = Layer("Iron xylophone (water drops)", 45, 125)
    heart = Layer("Heartbeat (soft drum)", 60, 100)
    drone = Layer("Didgeridoo (the earth)", 40, 100)
    layers = [melody, hold, low, harmony, echo, arp_guitar, arp_piano, pulse_bass, pulse_guitar, drops, heart, drone]

    total = sum(len(section[1]) for section in base.SECTIONS) * BAR
    bar_number = 0
    for name, chords, tune, tune_instrument, voices, accompaniment, lead in base.SECTIONS:
        for index, original in enumerate(chords):
            start = bar_number * BAR
            chord = CHORD_NAMES[original]
            tones = GUITAR_TONES[chord]
            root = ROOTS[chord]
            soft = "harp-soft" in voices
            if "harp" in voices or soft:
                for step, tone_index in enumerate(ARPEGGIO):
                    vel = velocity((40 if step == 0 else 32) * (0.8 if soft else 1.0), accompaniment)
                    if soft:
                        arp_piano.put_sound(start + step * 2, HARP, tones[tone_index] + 12, vel)  # (quiet parts: the piano plays it an octave up, where it carries)
                    else:
                        arp_guitar.put_sound(start + step * 2, GUITAR, tones[tone_index], velocity(48 if step == 0 else 40, accompaniment))
            if "bass" in voices:  # a steady pulse on every beat
                for tick in (0, 4, 8, 12):
                    pulse_bass.put_sound(start + tick, BASS, root, velocity(66 if tick == 0 else 50, accompaniment))
            if "bass-soft" in voices:
                for tick in (0, 8):
                    pulse_bass.put_sound(start + tick, BASS, root, 48 if tick == 0 else 36)
            if "pulse" in voices:  # the muted guitar on the off beats
                for tick in (2, 6, 10, 14):
                    pulse_guitar.put_sound(start + tick, GUITAR, tones[0] + 12, 34)
            if "heart" in voices:
                for tick, vel in ((0, 46), (3, 30)) + (((8, 38), (11, 24)) if name == "C" else ()):
                    heart.put(start + tick, BASEDRUM, 62, vel)
            if "drone" in voices and index % 2 == 0:
                drone.put_sound(start, DIDGERIDOO, root - 12, 36)
            if "drops" in voices or "drops-more" in voices:
                count = 2 if "drops-more" in voices else 1 if bar_number % 2 == 0 else 0
                for offset, tick in enumerate(drop_ticks(bar_number, count)):
                    note = (76, 71, 74, 78)[(bar_number + offset) % 4]
                    drops.put_sound(start + tick, IRON_XYLOPHONE, note, 26 + 5 * offset)
            if "harp-intro" in voices:
                tick = 0
                for note, length in parse_bar(base.INTRO_HARP[index]):
                    if note is not None:
                        melody.put_sound(start + tick, HARP, tune_sound(note), 60)
                    tick += length
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        sound = tune_sound(note)
                        at = start + tick
                        strong = tick == 0
                        melody.put_sound(at, HARP, sound, velocity(74 if strong else 66, lead) if tune_instrument == "flute" else velocity(70 if strong else 62, lead))
                        # a long note: the piano dies away, so it is struck again under the note, softer
                        for again in range(4, length, 4):
                            hold.put_sound(at + again, HARP, sound, velocity(38, lead))
                        if tune_instrument == "flute":
                            if "echo" in voices and at + ECHO_DELAY < total:
                                echo.put_sound(at + ECHO_DELAY, HARP, sound, 22)
                            if "harmony" in voices:
                                harmony.put_sound(at, HARP, third_below(note, base.E_MINOR) + SHIFT, 32)
                            low.put_sound(at, BASS, sound - 24, 46)  # (two octaves down: the weight of the low voice)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/atlas_dungeon_2.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Theme inside the Atlas cave dungeon, lower: piano and bass instead of the flute. Dark and tense but not frightening, 56 bars, loops from bar 5.",
              LOOP_START_BAR)
    report(target, layers, length, TPS)


if __name__ == "__main__":
    main()
