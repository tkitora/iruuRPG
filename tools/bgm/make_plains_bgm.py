#!/usr/bin/env python3
"""Makes plains_adventure.nbs: the field theme for the great plain outside the first town (Note Block Studio v5 file).

    python3 tools/bgm/make_plains_bgm.py            writes src/main/resources/bgm/plains_adventure.nbs
    python3 tools/bgm/make_plains_bgm.py out.nbs    writes somewhere else

The song: D major, 4/4, 105 BPM (one tick = a sixteenth; 7 ticks a second), 36 bars (about 82 s), in the style of an Irish / Celtic
field tune: a flute melody, a fingerpicked guitar, a soft bass, a harp that sparkles on the off beats, a second flute a third below
(standing in for a fiddle - Minecraft has no violin) and a quiet bodhran-like drum. It loops from bar 5 (tick 64), so the intro is played once.
Every note is between F#3 and F#5 (the range of a note block). Nothing here is random: the same file comes out every time.
"""
import struct
import sys
from pathlib import Path

TPS = 7.0           # ticks a second; a tick is a sixteenth note, a quarter is 4 ticks: 105 BPM
BAR = 16            # ticks in a bar
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once

HARP, BASS, BASEDRUM, SNARE, HAT, GUITAR, FLUTE, BELL, CHIME = 0, 1, 2, 3, 4, 5, 6, 7, 8

SEMITONES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(name):
    """'F#5' -> 78."""
    letter, rest = name[0], name[1:]
    sharp = 0
    while rest and rest[0] in "#b":
        sharp += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return 12 * (int(rest) + 1) + SEMITONES[letter] + sharp


def key(midi_note):
    """The key of the file: 33 (F#3) to 57 (F#5) is what a note block can play."""
    result = midi_note - 21
    assert 33 <= result <= 57, f"note {midi_note} is outside the note block range"
    return result


D_MAJOR = [midi(n) for n in ("D3", "E3", "F#3", "G3", "A3", "B3", "C#4", "D4", "E4", "F#4", "G4", "A4", "B4", "C#5", "D5", "E5", "F#5")]


def third_below(note):
    """The note two steps down the D major scale (the harmony voice)."""
    index = D_MAJOR.index(note)
    return D_MAJOR[index - 2]


class Layer:
    def __init__(self, name, volume, pan=100):
        self.name = name
        self.volume = volume
        self.pan = pan
        self.notes = {}  # tick -> (instrument, key, velocity)

    def put(self, tick, instrument, midi_note, velocity):
        assert tick not in self.notes, f"{self.name}: two notes at tick {tick}"
        self.notes[tick] = (instrument, key(midi_note), velocity)


def parse_bar(text):
    """'F#4:2 A4:2 D5:4' -> [(midi, ticks), ...]; the ticks must fill the bar."""
    result = []
    total = 0
    for part in text.split():
        name, length = part.split(":")
        result.append((None if name == "r" else midi(name), int(length)))
        total += int(length)
    assert total == BAR, f"bar '{text}' has {total} ticks"
    return result


# ---- the tune ---------------------------------------------------------------------------------------------------------------------

CHORDS = {  # the notes of the chord, low to high, for the guitar; the bass root and fifth
    "D": ([62, 66, 69, 74], 62, 69),
    "A": ([57, 64, 69, 73], 57, 64),
    "Bm": ([59, 66, 71, 74], 59, 66),
    "G": ([55, 62, 67, 71], 55, 62),
    "Em": ([55, 64, 67, 71], 64, 59),
}

A_CHORDS = ["D", "A", "Bm", "G", "D", "A", "G", "A"]
A_MELODY = [
    "F#4:2 A4:2 D5:4 D5:2 E5:2 F#5:4",
    "E5:6 C#5:2 A4:4 C#5:4",
    "D5:4 B4:2 D5:2 E5:4 D5:4",
    "B4:6 A4:2 G4:4 B4:4",
    "A4:4 D5:2 E5:2 F#5:6 E5:2",
    "E5:4 C#5:2 D5:2 C#5:4 A4:4",
    "B4:2 D5:2 D5:4 B4:4 A4:4",
    "E5:4 C#5:4 E5:8",
]
A2_CHORDS = ["D", "A", "Bm", "G", "D", "Bm", "Em", "A"]
A2_MELODY = A_MELODY[:4] + [
    "F#5:4 E5:4 D5:4 A4:4",
    "D5:4 F#5:2 E5:2 D5:4 B4:4",
    "B4:2 G4:2 B4:4 E5:4 D5:4",
    "E5:4 C#5:4 A4:8",
]
B_CHORDS = ["G", "D", "Em", "A", "G", "D", "Em", "A"]
B_MELODY = [
    "B4:8 D5:4 E5:4",
    "F#5:8 E5:4 D5:4",
    "E5:6 D5:2 B4:4 G4:4",
    "C#5:4 E5:4 C#5:8",
    "B4:4 D5:4 E5:4 D5:4",
    "F#5:6 E5:2 D5:4 A4:4",
    "B4:4 E5:4 D5:4 B4:4",
    "C#5:4 D5:2 E5:2 A4:8",
]
INTRO_CHORDS = ["D", "A", "Bm", "G"]
INTRO_HARP = ["r:16", "r:16", "B4:8 D5:8", "G4:4 B4:4 D5:8"]

# the sections, in order: (name, chords, melody or None, the voices that play)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, {"guitar", "bass", "harp-intro", "chime"}),
    ("A", A_CHORDS, A_MELODY, {"guitar", "bass", "melody"}),
    ("A2", A2_CHORDS, A2_MELODY, {"guitar", "bass", "melody", "harmony-late", "drums"}),
    ("B", B_CHORDS, B_MELODY, {"guitar", "bass", "melody", "harmony", "sparkle", "drums", "chime"}),
    ("A3", A_CHORDS, A_MELODY, {"guitar", "bass", "melody", "harmony", "sparkle", "drums"}),
]

PICK = [0, 2, 3, 2, 1, 2, 3, 2]  # the fingerpicking: root, fifth, octave, fifth, third, fifth, octave, fifth


def build():
    melody = Layer("Flute (melody)", 100, 100)
    harmony = Layer("Flute (fiddle part, a third below)", 60, 80)
    guitar = Layer("Guitar (fingerpicking)", 80, 65)
    bass = Layer("Bass", 80, 100)
    harp = Layer("Harp", 70, 135)
    chime = Layer("Chime", 55, 120)
    kick = Layer("Drum (soft)", 55, 100)
    hat = Layer("Hat (soft)", 45, 110)
    layers = [melody, harmony, guitar, bass, harp, chime, kick, hat]

    bar_number = 0
    for name, chords, tune, voices in SECTIONS:
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            tones, root, fifth = CHORDS[chord]
            if "guitar" in voices:
                for step, tone_index in enumerate(PICK):
                    velocity = 70 if step == 0 else 62 if step == 4 else 52
                    guitar.put(start + step * 2, GUITAR, tones[tone_index], velocity)
            if "bass" in voices:
                bass.put(start, BASS, root, 80)
                bass.put(start + 8, BASS, fifth, 66)
            if "drums" in voices:
                kick.put(start, BASEDRUM, 66, 42)
                kick.put(start + 8, BASEDRUM, 66, 34)
                hat.put(start + 4, HAT, 66, 24)
                hat.put(start + 12, HAT, 66, 24)
            if "sparkle" in voices:
                for tick in (3, 7, 11, 15):  # the harp on the off beats
                    harp.put(start + tick, HARP, tones[3], 30 if tick != 15 else 26)
            if "chime" in voices and name == "intro" and index == 0:
                chime.put(start, CHIME, 74, 45)
            if "chime" in voices and name == "B" and index % 2 == 0:
                chime.put(start, CHIME, 78 if index % 4 == 0 else 74, 38)
            if "harp-intro" in voices:
                tick = 0
                for note, length in parse_bar(INTRO_HARP[index]):
                    if note is not None:
                        harp.put(start + tick, HARP, note, 62)
                    tick += length
            if tune is not None:
                tick = 0
                late = "harmony-late" in voices and index >= 4
                for note, length in parse_bar(tune[index]):
                    melody.put(start + tick, FLUTE, note, 88 if tick == 0 else 80)
                    if "harmony" in voices or late:
                        harmony.put(start + tick, FLUTE, third_below(note), 50)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


# ---- Note Block Studio v5 --------------------------------------------------------------------------------------------------------


def string(text):
    data = text.encode("cp1252", errors="replace")
    return struct.pack("<i", len(data)) + data


def write_nbs(path, layers, length):
    out = bytearray()
    out += struct.pack("<h", 0)                                  # a new-format file
    out += struct.pack("<bb", 5, 16)                              # version 5, 16 built-in instruments
    out += struct.pack("<h", length - 1)                          # the last tick
    out += struct.pack("<h", len(layers))
    out += string("Plains Adventure")
    out += string("iruuRPG")
    out += string("")
    out += string("Field theme of the great plain outside the first town: Irish / Celtic, medium tempo, loops from bar 5.")
    out += struct.pack("<h", int(round(TPS * 100)))
    out += struct.pack("<bbb", 0, 10, 4)                          # auto-save off, 10 min, 4/4
    out += struct.pack("<iiiii", 0, 0, 0, 0, 0)                   # minutes spent, clicks, blocks added / removed
    out += string("")
    out += struct.pack("<bbh", 1, 0, LOOP_START_BAR * BAR)        # loop on, forever, from the end of the intro

    ticks = sorted({tick for layer in layers for tick in layer.notes})
    last = -1
    for tick in ticks:
        out += struct.pack("<h", tick - last)
        last = tick
        previous = -1
        for number, layer in enumerate(layers):
            if tick not in layer.notes:
                continue
            instrument, note_key, velocity = layer.notes[tick]
            out += struct.pack("<h", number - previous)
            previous = number
            out += struct.pack("<bbbBh", instrument, note_key, velocity, layer.pan, 0)
        out += struct.pack("<h", 0)
    out += struct.pack("<h", 0)

    for layer in layers:
        out += string(layer.name)
        out += struct.pack("<bbB", 0, layer.volume, layer.pan)
    out += struct.pack("<b", 0)                                   # no custom instruments
    Path(path).write_bytes(bytes(out))


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/plains_adventure.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length)
    total = sum(len(layer.notes) for layer in layers)
    print(f"{target}: {length} ticks ({length / TPS:.1f} s), {len(layers)} layers, {total} notes")


if __name__ == "__main__":
    main()
