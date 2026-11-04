"""The boss songs written for DRIVE (t271): fast, restless and rising, built on bossframe.

The complaint about the first rebuilds (t270, t271) was that they were dark but not boss-like: the tempo was slow, the tune slow, almost every phrase ended going DOWN, and so nothing pushed the player
(no tension, no rising excitement, no impatience). So here:
  - 300 BPM: 20 ticks a second and a beat of 4 ticks (a bar of 16: the engine's own bar line, so the phase switch always meets a bar), the tune in eighth notes (2 ticks) and sixteenths (1 tick);
  - the tune is a short CELL of eight notes that is repeated higher and higher (a rising sequence, one step every bar or two) and every eighth bar ends in a run that climbs and is left hanging UP (never a falling cadence);
  - tension: a bass that pumps on one note (a pedal) under chords that move, trills on a half step that climb, a snare roll before each block, an ensemble hit every eighth bar;
  - the weight of t270 stays (bossframe.heavy_voices gains, low bass and didgeridoo, power chords, the guitar chug, loud drums) with the tune at its normal height, the guitar an octave under it and, in the
    peak, the flute an octave over it and the bass two octaves under it.
A song is made by ``drive_spec(...)`` from its key, its cell and its chords; the devices of each song (a silence, a mirror, a waltz ...) are tags.
"""
from bossframe import *  # noqa: F401,F403


def fold_bar(text, top=76):
    """A bar moved down by octaves as a whole until its highest note is at most ``top`` (a rising sequence that gets too high starts again below, instead of single notes jumping down)."""
    notes = [midi(part.split(":")[0]) for part in text.split() if not part.startswith("r:")]
    if not notes:
        return text
    drop = 0
    while max(notes) - drop > top:
        drop += 12
    if not drop:
        return text
    return " ".join(part if part.startswith("r:") else f"{note_name(midi(part.split(':')[0]) - drop)}:{part.split(':')[1]}" for part in text.split())


def block_names(count, names):
    return [names[i % len(names)] for i in range(count)]


def drive_spec(*, bar=16, tonic, scale, cell, answer, tail1, tail2, high1, high2, trill, prog_a, prog_b, prog_p, prog_c, blocks=(4, 4, 4, 4, 3), pedal_b=None, extra_voices=None,
               voices=None, breaks=None, axis=None, run=(57, 60, 62, 64), level=None, drum_set=None, bass_set=None):
    """Everything a boss song needs. ``cell``/``answer``/... are bars written in the ticks of ``bar``; the progressions are lists of eight chords (one a bar; 'X|Y' for two in a bar)."""
    K = key_pcs(tonic, scale)
    R = f"r:{bar}"

    def seq(first, second, plan):
        return seq_block(first, second, K, plan)

    m1 = seq(cell, answer, [0, 0, 1, 1, 2, 2, ("u", 2), tail1])
    m2 = seq(cell, answer, [0, ("u", 0), 1, ("u", 1), 2, ("u", 2), 3, tail2])
    m3 = seq(high1, answer, [0, 0, 1, 1, 2, 2, ("u", 1), tail1])
    t1 = [mv(trill, K, n) for n in (0, 0, 1, 1, 2, 2, 3, 4)]
    t2 = [mv(trill, K, n) for n in (0, 1, 2, 3, 4, 5, 6, 8)]
    h1 = seq(high1, high2, [0, 0, ("u", 0), ("u", 0), 1, 1, ("u", 1), tail2])
    h2 = seq(high2, high1, [0, 1, ("u", 0), ("u", 1), 1, 2, ("u", 2), tail1])
    k1 = [cell, R, mv(cell, K, 1), R, mv(cell, K, 2), R, tail1, R]
    k2 = [mv(cell, K, 2), R, mv(cell, K, 3), R, mv(cell, K, 4), R, tail2, tail1]
    k3 = [R, R, cell, R, mv(cell, K, 1), R, tail1, tail2]
    a_count, a2_count, b_count, p_count, c_count = blocks
    for block in (m1, m2, m3, t1, t2, h1, h2, k1, k2, k3):
        block[:] = [fold_bar(bar_text) for bar_text in block]
    melody = {
        "A": sum(([m1, m2][i % 2] for i in range(a_count)), []),
        "A2": sum(([m1, m3][i % 2] for i in range(a2_count)), []),
        "B": sum(([t1, t2][i % 2] for i in range(b_count)), []),
        "PEAK": sum(([h1, h2][i % 2] for i in range(p_count)), []),
        "CALM": sum(([k1, k2, k3][i % 3] for i in range(c_count)), []),
    }
    chords = {
        "A": sum((list(prog_a) for _ in range(a_count)), []),
        "A2": sum((list(prog_a if i % 2 == 0 else prog_p) for i in range(a2_count)), []),
        "B": sum((list(prog_b) for _ in range(b_count)), []),
        "PEAK": sum((list(prog_p) for _ in range(p_count)), []),
        "CALM": sum((list(prog_c) for _ in range(c_count)), []),
    }
    intro_bars = 64 // bar
    chords["intro"] = [prog_a[i % 8] for i in range(intro_bars)]
    melody["intro"] = None
    sections = [("A", 8 * a_count), ("A2", 8 * a2_count), ("B", 8 * b_count), ("PEAK", 8 * p_count), ("CALM", 8 * c_count)]
    spec = dict(
        bar=bar, tps=20.0, tonic=tonic, scale=scale, shift=[0, 1], sections=sections, chords=chords, melody=melody, heavy=True,
        level=level or {"intro": 0.85, "A": 0.95, "A2": 1.0, "B": 0.95, "PEAK": 1.1, "CALM": 0.8},
        arp_low=54, arp_high=72, stab_low=54, stab_high=68, run=list(run),
    )
    if pedal_b:
        spec["pedal"] = {"B": pedal_b}
    if breaks:
        spec["breaks"] = breaks
        spec["break_keep"] = ("lead", "echo")
    if axis is not None:
        spec["axis"] = axis
    spec["voices"] = voices if voices is not None else [drive_voices(0, intro_bars, b_count, extra=extra_voices, drum_set=drum_set, bass_set=bass_set),
                                                       drive_voices(1, intro_bars, b_count, extra=extra_voices, drum_set=drum_set, bass_set=bass_set)]
    return spec


def drive_voices(phase, intro_bars, b_blocks, extra=None, drum_set=None, bass_set=None):
    """The voices: a pumping bass, a never-silent didgeridoo, a chugging guitar, power chords, drums that drive, and the tune at its height with the guitar under it."""
    drums = drum_set or {"intro": "four", "A": "drive", "A2": "double", "B": "half", "PEAK": "double", "CALM": "heart"}
    bass = bass_set or {"A": "pulse", "A2": "pulse", "B": "pulse", "PEAK": "pulse", "CALM": "half"}
    rolls = ["roll@%d-%d" % (8 * k + 7, 8 * k + 8) for k in range(b_blocks)]
    if phase == 0:
        sections = {
            "intro": ["bass:pulse", "drone:loud", "riff:GUITAR:4@1", "drums:%s@1" % drums["intro"], "roll@%d-%d" % (intro_bars - 1, intro_bars)],
            "A": ["hit:8", "lead:HARP", "bass:%s" % bass["A"], "drone:loud", "riff:GUITAR:4", "drums:%s" % drums["A"], "fill"] + ["roll@%d-%d" % (8 * k + 7, 8 * k + 8) for k in range(1, 4)],
            "A2": ["hit:8", "lead:HARP", "dbl:GUITAR:-12:0.9@8", "dbl:FLUTE:12:0.4@16", "pow:GUITAR:0,6,10@8", "bass:%s" % bass["A2"], "drone:loud", "riff:GUITAR:4@0-8", "riff:GUITAR:2@8", "drums:%s" % drums["A2"], "fill"],
            "B": ["lead:HARP", "bass:%s" % bass["B"], "drone:loud", "riff:GUITAR:4", "drums:%s" % drums["B"], "pow:GUITAR:0@8"] + rolls,
            "PEAK": ["hit:4", "lead:HARP", "dbl:GUITAR:-12:0.9", "dbl:FLUTE:12:0.6", "dbl:BASS:-24:0.8", "pow:GUITAR:0,6,10", "bass:%s" % bass["PEAK"], "drone:loud", "riff:GUITAR:2", "drums:%s" % drums["PEAK"], "fill"],
            "CALM": ["lead:HARP", "bass:%s" % bass["CALM"], "drone:loud", "drums:%s" % drums["CALM"], "riff:GUITAR:8"],
        }
    else:
        sections = {
            "intro": ["bass:pulse", "drone:loud", "riff:GUITAR:2@1", "drums:drive@1", "roll@%d-%d" % (intro_bars - 1, intro_bars)],
            "A": ["hit:8", "lead:PLING", "dbl:BIT:0:0.5@8", "bass:pulse", "drone:loud", "riff:GUITAR:4@0-8", "riff:GUITAR:2@8", "drums:drive", "fill"] + ["roll@%d-%d" % (8 * k + 7, 8 * k + 8) for k in range(1, 4)],
            "A2": ["hit:8", "lead:PLING", "dbl:BIT:0:0.6", "dbl:GUITAR:-12:0.9@8", "dbl:FLUTE:12:0.4@16", "pow:BIT:0,6,10@8", "bass:pulse", "drone:loud", "riff:GUITAR:2", "drums:double", "fill"],
            "B": ["lead:BIT", "bass:pulse", "drone:loud", "riff:GUITAR:4", "drums:%s" % ("drive" if drums["B"] == "half" else drums["B"]), "pow:BIT:0@8"] + rolls,
            "PEAK": ["hit:4", "lead:PLING", "dbl:BIT:0:0.7", "dbl:GUITAR:-12:0.9", "dbl:FLUTE:12:0.6", "dbl:BASS:-24:0.8", "pow:BIT:0,6,10", "bass:sixteenth", "drone:loud", "riff:GUITAR:2", "drums:double", "fill"],
            "CALM": ["lead:PLING", "bass:half", "drone:loud", "drums:half", "riff:GUITAR:4"],
        }
    for key, tags in (extra or {}).items():
        sections[key] = sections[key] + tags
    return sections
