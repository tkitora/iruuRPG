"""What the song scripts of tools/bgm share: note names, layers and the writer of Note Block Studio (.nbs, version 5) files.

A song is a list of layers; a layer holds at most one note a tick (so two notes together need two layers). Ticks are counted from 0; a bar is 16 ticks (a tick is a sixteenth).
"""
import struct
from pathlib import Path

BAR = 16

# the instruments of Note Block Studio (the 16 of the game)
HARP, BASS, BASEDRUM, SNARE, HAT, GUITAR, FLUTE, BELL, CHIME, XYLOPHONE, IRON_XYLOPHONE, COW_BELL, DIDGERIDOO, BIT, BANJO, PLING = range(16)

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


class Layer:
    def __init__(self, name, volume, pan=100):
        self.name = name
        self.volume = volume
        self.pan = pan
        self.notes = {}  # tick -> (instrument, key, velocity)

    def put(self, tick, instrument, midi_note, velocity):
        assert tick not in self.notes, f"{self.name}: two notes at tick {tick}"
        assert 1 <= velocity <= 100, f"{self.name}: velocity {velocity}"
        self.notes[tick] = (instrument, key(midi_note), velocity)


def parse_bar(text):
    """'F#4:2 A4:2 D5:4' -> [(midi, ticks), ...] ('r' is a rest); the ticks must fill the bar."""
    result = []
    total = 0
    for part in text.split():
        name, length = part.split(":")
        result.append((None if name == "r" else midi(name), int(length)))
        total += int(length)
    assert total == BAR, f"bar '{text}' has {total} ticks"
    return result


def scale(names):
    return [midi(n) for n in names.split()]


def third_below(note, notes):
    """The note two steps down the scale ``notes`` (ascending MIDI numbers); a note that is not in it goes down a major third."""
    if note in notes:
        index = notes.index(note)
        if index >= 2:
            return notes[index - 2]
    return note - 4


def velocity(base, scale_factor):
    return max(1, min(100, int(round(base * scale_factor))))


def string(text):
    data = text.encode("cp1252", errors="replace")
    return struct.pack("<i", len(data)) + data


def write_nbs(path, layers, length, ticks_per_second, title, description, loop_start_bar):
    """Writes the song. ``ticks_per_second`` should divide 20 (20, 10, 6.67, 5, 4 ...): a song tick is then a whole number of server ticks."""
    out = bytearray()
    out += struct.pack("<h", 0)                                  # a new-format file
    out += struct.pack("<bb", 5, 16)                              # version 5, 16 built-in instruments
    out += struct.pack("<h", length - 1)                          # the last tick
    out += struct.pack("<h", len(layers))
    out += string(title)
    out += string("iruuRPG")
    out += string("")
    out += string(description)
    out += struct.pack("<h", int(round(ticks_per_second * 100)))
    out += struct.pack("<bbb", 0, 10, 4)                          # auto-save off, 10 min, 4/4
    out += struct.pack("<iiiii", 0, 0, 0, 0, 0)                   # minutes spent, clicks, blocks added / removed
    out += string("")
    out += struct.pack("<bbh", 1, 0, loop_start_bar * BAR)        # loop on, forever, from the end of the intro

    ticks = sorted({tick for layer in layers for tick in layer.notes})
    last = -1
    for tick in ticks:
        out += struct.pack("<h", tick - last)
        last = tick
        previous = -1
        for number, layer in enumerate(layers):
            if tick not in layer.notes:
                continue
            instrument, note_key, vel = layer.notes[tick]
            out += struct.pack("<h", number - previous)
            previous = number
            out += struct.pack("<bbbBh", instrument, note_key, vel, layer.pan, 0)
        out += struct.pack("<h", 0)
    out += struct.pack("<h", 0)

    for layer in layers:
        out += string(layer.name)
        out += struct.pack("<bbB", 0, layer.volume, layer.pan)
    out += struct.pack("<b", 0)                                   # no custom instruments
    Path(path).write_bytes(bytes(out))


def statistics(layers, length, ticks_per_second):
    """(most notes at once, most notes in one second, notes a second on average): the rulebook asks to keep them low."""
    per_tick = {}
    for layer in layers:
        for tick in layer.notes:
            per_tick[tick] = per_tick.get(tick, 0) + 1
    per_second = {}
    for tick, count in per_tick.items():
        second = int(tick / ticks_per_second)
        per_second[second] = per_second.get(second, 0) + count
    return max(per_tick.values()), max(per_second.values()), sum(per_second.values()) / (length / ticks_per_second)


def report(target, layers, length, ticks_per_second):
    total = sum(len(layer.notes) for layer in layers)
    peak, busiest, average = statistics(layers, length, ticks_per_second)
    print(f"{target}: {length} ticks ({length / ticks_per_second:.1f} s), {len(layers)} layers, {total} notes")
    print(f"at most {peak} notes at once, {busiest} in the busiest second, {average:.1f} a second on average")
