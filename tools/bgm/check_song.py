#!/usr/bin/env python3
"""Reads .nbs files written by nbslib.write_nbs and prints what a bundled boss song must satisfy (the rules of BgmTest, without running Gradle).

    python3 tools/bgm/check_song.py src/main/resources/bgm/colossus_phase1.nbs [more.nbs ...]

Prints: title, length (ticks / seconds), ticks a second, loop start, notes, the most notes at one tick, notes a second by 8-bar windows (of the 16-tick engine bar), the pitch range of every instrument
(by what SOUNDS) and every rule that is broken (exit code 1). With two files (phase 1 and phase 2) it also checks that they are one piece (same length, tempo and loop point).
"""
import struct
import sys

BASE = {0: 54, 1: 30, 5: 42, 6: 78, 7: 90, 8: 90, 9: 90, 10: 54, 11: 66, 12: 30, 13: 54, 14: 54, 15: 54}
NAMES = ["harp", "bass", "bass drum", "snare", "hat", "guitar", "flute", "bell", "chime", "xylophone", "iron xylophone", "cow bell", "didgeridoo", "bit", "banjo", "pling"]


def read(path):
    data = open(path, "rb").read()
    pos = 0

    def take(fmt):
        nonlocal pos
        size = struct.calcsize(fmt)
        value = struct.unpack_from(fmt, data, pos)
        pos += size
        return value if len(value) > 1 else value[0]

    def string():
        nonlocal pos
        n = take("<i")
        text = data[pos:pos + n].decode("cp1252")
        pos += n
        return text

    assert take("<h") == 0
    version, instruments = take("<bb")
    length = take("<h") + 1
    layers = take("<h")
    title = string()
    string(); string(); string()
    tps = take("<h") / 100.0
    take("<bbb"); take("<iiiii"); string()
    loop_on, loops, loop_start = take("<bbh")
    notes = []   # (tick, layer, instrument, key, velocity)
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
            instrument, key, velocity, pan, pitch = take("<bbBBh") if False else struct.unpack_from("<bbbBh", data, pos)
            pos += struct.calcsize("<bbbBh")
            notes.append((tick, layer, instrument, key, velocity))
    return dict(title=title, length=length, tps=tps, loop=bool(loop_on), loop_start=loop_start, notes=notes, layers=layers)


def sounding(instrument, key):
    return BASE[instrument] + key - 33 if instrument in BASE else None


def check(path, quiet=False):
    song = read(path)
    problems = []
    notes = song["notes"]
    seconds = song["length"] / song["tps"]
    per_tick = {}
    for tick, layer, instrument, key, velocity in notes:
        per_tick[tick] = per_tick.get(tick, 0) + 1
    peak = max(per_tick.values())
    if seconds < 120:
        problems.append(f"shorter than 120 s ({seconds:.1f})")
    if not song["loop"] or song["loop_start"] != 64:
        problems.append(f"must loop from tick 64 (loop={song['loop']}, start={song['loop_start']})")
    if abs(20.0 / song["tps"] - round(20.0 / song["tps"])) > 0.01:
        problems.append(f"ticks a second {song['tps']} does not divide 20")
    if peak > 8:
        problems.append(f"{peak} notes at one tick (8 at most)")
    if not song["title"].endswith(")") or "(" not in song["title"]:
        problems.append("title must look like 'Name (Boss007)'")
    ranges = {}
    for tick, layer, instrument, key, velocity in notes:
        s = sounding(instrument, key)
        if s is not None:
            low, high = ranges.get(instrument, (999, 0))
            ranges[instrument] = (min(low, s), max(high, s))
            if instrument in (7, 8) and not (90 <= s <= 99):
                problems.append(f"{NAMES[instrument]} at {s}: the bell and chime stay between 90 (F#6) and 99 (D#7)")
                break
    if not quiet:
        print(f"{path}\n  {song['title']}: {song['length']} ticks = {seconds:.1f} s at {song['tps']} ticks/s, loop from {song['loop_start']}, {len(notes)} notes, {peak} at most at once, {song['layers']} layers")
        window = 128 * 1  # eight engine bars of 16 ticks
        row = []
        for start in range(0, song["length"], window):
            count = sum(1 for tick, *_ in notes if start <= tick < start + window)
            row.append(f"{count / (min(window, song['length'] - start) / song['tps']):.0f}")
        print("  notes a second by 128 ticks: " + " ".join(row))
        print("  " + ", ".join(f"{NAMES[i]} {lo}-{hi}" for i, (lo, hi) in sorted(ranges.items())))
        drums = {2: 0, 3: 0, 4: 0}
        for tick, layer, instrument, key, velocity in notes:
            if instrument in drums:
                drums[instrument] += 1
        print(f"  bass drum {drums[2]}, snare {drums[3]}, hat {drums[4]}")
    return song, problems


def main():
    paths = sys.argv[1:]
    songs = []
    bad = False
    for path in paths:
        song, problems = check(path)
        songs.append(song)
        for problem in problems:
            print("  PROBLEM:", problem)
            bad = True
    if len(songs) == 2:
        a, b = songs
        for field in ("length", "tps", "loop_start"):
            if a[field] != b[field]:
                print(f"  PROBLEM: the phases differ in {field}: {a[field]} / {b[field]}")
                bad = True
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
