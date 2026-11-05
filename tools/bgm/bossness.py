#!/usr/bin/env python3
"""Measures how much a boss song PUSHES (t279): loudness, register, weight and hits, against what the user liked (the 300 BPM drive songs of t271 and Boss006).

    python3 tools/bgm/bossness.py src/main/resources/bgm/necromancer_phase1.nbs [more.nbs ...]    # one line each, the failed limits marked with !
    python3 tools/bgm/bossness.py --table src/main/resources/bgm/necromancer_phase1.nbs          # and by 15 seconds (find the quiet or high places)

The limits (what the liked songs measured: lead 79-89, pitch 59-68, hits 88-250 a minute, kick 4-7 a second; the t277 rebuild: lead 55-73 in most songs, pitch up to 73, kick 2.6-2.8 in three):
  lead   loudness of the tune (the loudest 40 % of the harp/pling/bit/xylophone/flute/guitar notes), velocity x layer volume   >= 78
  bass   loudness of bass + didgeridoo                                                                                      >= 55
  drums  loudness of bass drum + snare                                                                                      >= 55
  pitch  mean SOUNDING pitch of the tune (harp F#3 = 54 ... F#5 = 78)                                                <= 68
  low    mean SOUNDING pitch of bass + didgeridoo                                                                           <= 42
  hits   ticks with 6 or more notes at once (ensemble hits, power chords, the downbeat of a block) a minute                  >= 80
  kick   drum (bass drum + snare) hits a second                                                                            >= 3.0
The exit code is 1 when a limit is broken. Measure by listening too: the numbers only say the song is not timid.
"""
import sys
from collections import defaultdict

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from check_song import read, sounding  # noqa: E402
import retime  # noqa: E402

LEAD = (0, 5, 6, 9, 10, 13, 14, 15)      # harp guitar flute xylophone iron xylophone bit banjo pling (bell/chime/cow bell are decoration)
PITCH_LEAD = (0, 10, 13, 14, 15)         # the tune itself (not the guitar chug, not the flute that sits an octave over it)
LOW = (1, 12)
DRUM = (2, 3)
LIMITS = {"lead": (78, ">="), "bass": (55, ">="), "drums": (55, ">="), "pitch": (68, "<="), "low": (42, "<="), "hits": (80, ">="), "kick": (3.0, ">=")}
WEAK = 62                                  # a 15 s window whose tune is quieter than this is timid (only a designed break / lost-melody part may be)


def mean(values):
    return sum(values) / len(values) if values else 0.0


def loudest(values):
    """The mean of the loudest 40 % (the tune itself; the quiet arpeggios and echoes of the same instruments do not count)."""
    return mean(values[:max(1, int(len(values) * 0.4))])


def measure(notes, seconds):
    per_tick = defaultdict(int)
    for n in notes:
        per_tick[n[0]] += 1
    return {
        "lead": loudest(sorted([n[4] for n in notes if n[2] in LEAD], reverse=True)),
        "bass": mean([n[4] for n in notes if n[2] in LOW]),
        "drums": mean([n[4] for n in notes if n[2] in DRUM]),
        "pitch": mean([sounding(n[2], n[3]) for n in notes if n[2] in PITCH_LEAD]),
        "low": mean([sounding(n[2], n[3]) for n in notes if n[2] in LOW]),
        "hits": sum(1 for c in per_tick.values() if c >= 6) / seconds * 60,
        "kick": sum(1 for n in notes if n[2] in DRUM) / seconds,
        "rate": len(notes) / seconds,
    }


def bad(key, value):
    limit, op = LIMITS[key]
    return value < limit if op == ">=" else value > limit


def line(m):
    return " ".join(f"{k}={m[k]:5.1f}{'!' if bad(k, m[k]) else ' '}" for k in ("lead", "bass", "drums", "pitch", "low", "hits", "kick")) + f" n/s={m['rate']:5.1f}"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    table = "--table" in sys.argv
    failed = False
    for path in args:
        song = read(path)
        layers = retime.read_all(path)["layers"]
        song["notes"] = [(t, l, i, k, v * layers[l][2] / 100.0) for t, l, i, k, v in song["notes"]]
        seconds = song["length"] / song["tps"]
        m = measure(song["notes"], seconds)
        failed |= any(bad(k, m[k]) for k in LIMITS)
        print(f"{path.rsplit('/', 1)[-1][:26]:26s} {song['tps']:5.2f}t/s {seconds:4.0f}s  {line(m)}")
        window = int(15 * song["tps"])
        parts = defaultdict(list)
        for n in song["notes"]:
            parts[n[0] // window].append(n)
        weak = [w * 15 for w in sorted(parts) if measure(parts[w], 15)["lead"] < WEAK]
        if weak:
            print(f"   timid 15 s windows (tune quieter than {WEAK}): " + ", ".join(f"{t}s" for t in weak) + "   (fine only for a designed break / lost-melody part)")
        if table:
            for w in sorted(parts):
                print(f"   {w * 15:3d}s " + line(measure(parts[w], 15)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
