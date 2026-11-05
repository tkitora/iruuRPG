#!/usr/bin/env python3
"""Changes the tempo of a .nbs song to any BPM (not only the ones a whole number of server ticks gives).

    python3 tools/bgm/retime.py in.nbs out.nbs --bpm 130 --beat-ticks 4
    python3 tools/bgm/retime.py in.nbs out.nbs --scale 1.15          # every time 1.15 times faster, no BPM needed

A server tick is 50 ms, so a note can only sound on a tick of 20 a second. The new song is written at 20 ticks a second and every note is put on the nearest server tick of
its true time: the tempo is exact on average, and a single note is at most 25 ms off its place (a beat of 9.23 ticks is played as 9 and 10 ticks). Loop point and length move with the notes.

--beat-ticks is how many song ticks make one beat in the source (4 in the 300 BPM songs, 6 in the 200 BPM ones...): the source BPM is ticks a second * 60 / beat-ticks.
Where two notes of one layer land on the same tick, the later one goes to the next free tick (a note is never dropped unless the song is full there).
"""
import argparse
import struct
import sys


def read_all(path):
    data = open(path, "rb").read()
    pos = 0

    def take(fmt):
        nonlocal pos
        value = struct.unpack_from(fmt, data, pos)
        pos += struct.calcsize(fmt)
        return value if len(value) > 1 else value[0]

    def string():
        nonlocal pos
        n = take("<i")
        raw = data[pos:pos + n]
        pos += n
        return raw

    song = {}
    assert take("<h") == 0, "only the new format (version 5) is read"
    song["version"], song["instruments"] = take("<bb")
    song["length"] = take("<h") + 1
    song["layer_count"] = take("<h")
    song["title"], song["author"], song["original"], song["description"] = string(), string(), string(), string()
    song["tps"] = take("<h") / 100.0
    song["head"] = take("<bbb")
    song["stats"] = take("<iiiii")
    song["file"] = string()
    song["loop"], song["loops"], song["loop_start"] = take("<bbh")
    notes = []   # tick, layer, instrument, key, velocity, pan, pitch
    tick = -1
    while True:
        jump = take("<h")
        if jump == 0:
            break
        tick += jump
        layer = -1
        while True:
            jump = take("<h")
            if jump == 0:
                break
            layer += jump
            instrument, key, velocity, pan, pitch = take("<bbbBh")
            notes.append([tick, layer, instrument, key, velocity, pan, pitch])
    song["notes"] = notes
    layers = []
    for _ in range(song["layer_count"]):
        name = string()
        locked, volume, pan = take("<bbB")
        layers.append((name, locked, volume, pan))
    song["layers"] = layers
    song["tail"] = data[pos:]
    return song


def write_all(path, song):
    def string(raw):
        return struct.pack("<i", len(raw)) + raw

    out = bytearray()
    out += struct.pack("<h", 0)
    out += struct.pack("<bb", song["version"], song["instruments"])
    out += struct.pack("<h", song["length"] - 1)
    out += struct.pack("<h", song["layer_count"])
    for key in ("title", "author", "original", "description"):
        out += string(song[key])
    out += struct.pack("<h", int(round(song["tps"] * 100)))
    out += struct.pack("<bbb", *song["head"])
    out += struct.pack("<iiiii", *song["stats"])
    out += string(song["file"])
    out += struct.pack("<bbh", song["loop"], song["loops"], song["loop_start"])
    last = -1
    for tick in sorted({n[0] for n in song["notes"]}):
        out += struct.pack("<h", tick - last)
        last = tick
        previous = -1
        for n in sorted((n for n in song["notes"] if n[0] == tick), key=lambda n: n[1]):
            out += struct.pack("<h", n[1] - previous)
            previous = n[1]
            out += struct.pack("<bbbBh", n[2], n[3], n[4], n[5], n[6])
        out += struct.pack("<h", 0)
    out += struct.pack("<h", 0)
    for name, locked, volume, pan in song["layers"]:
        out += string(name)
        out += struct.pack("<bbB", locked, volume, pan)
    out += song["tail"]
    open(path, "wb").write(bytes(out))


def retime(song, scale):
    """``scale``: new ticks per old tick of the source's own time (server ticks a second = 20)."""
    factor = 20.0 / song["tps"] * scale          # old song tick -> new tick (at 20 a second), as a real number
    taken = set()
    moved = dropped = 0
    new_notes = []
    # (a stable order: earlier notes keep their place, later ones in the same layer move on)
    for n in sorted(song["notes"], key=lambda n: (n[0], n[1])):
        tick = int(round(n[0] * factor))
        while (tick, n[1]) in taken:
            tick += 1
            moved += 1
        new_length = int(round(song["length"] * factor))
        if tick >= new_length:
            dropped += 1
            continue
        taken.add((tick, n[1]))
        new_notes.append([tick] + n[1:])
    song["notes"] = new_notes
    song["length"] = int(round(song["length"] * factor))
    song["loop_start"] = int(round(song["loop_start"] * factor))
    song["tps"] = 20.0
    return moved, dropped


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("target")
    parser.add_argument("--bpm", type=float, help="the BPM to get (needs --beat-ticks)")
    parser.add_argument("--beat-ticks", type=float, help="song ticks in one beat of the source")
    parser.add_argument("--scale", type=float, help="how many times faster (instead of --bpm)")
    args = parser.parse_args()
    song = read_all(args.source)
    if args.scale:
        speed = args.scale
        source_bpm = args.beat_ticks and song["tps"] * 60 / args.beat_ticks
    elif args.bpm and args.beat_ticks:
        source_bpm = song["tps"] * 60 / args.beat_ticks
        speed = args.bpm / source_bpm
    else:
        parser.error("give --bpm with --beat-ticks, or --scale")
    old_seconds = song["length"] / song["tps"]
    moved, dropped = retime(song, 1.0 / speed)
    write_all(args.target, song)
    print(f"{args.source} -> {args.target}: {speed:.4f} times faster" + (f" ({source_bpm:.1f} -> {source_bpm * speed:.1f} BPM)" if source_bpm else "")
          + f", {old_seconds:.1f} s -> {song['length'] / 20.0:.1f} s, {song['length']} ticks, loop from {song['loop_start']}, {len(song['notes'])} notes ({moved} moved on, {dropped} dropped)")


if __name__ == "__main__":
    sys.exit(main())
