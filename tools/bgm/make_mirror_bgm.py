#!/usr/bin/env python3
"""Makes mirror_phase1.nbs and mirror_phase2.nbs: "The Cracked Mirror Laughs (Boss007)" and "..., Shattered (Boss007)" - class 4, the carnival of madness and chaos (a trickster boss).

    python3 tools/bgm/make_mirror_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_mirror_bgm.py outdir     writes them somewhere else

200 BPM: 20 ticks a second, a beat of 6 ticks, a bar of 24 (4/4). The tick grid gives the triplet (2 ticks) and the straight eighth (3 ticks) together, so the song can change its FEEL without changing
its tempo: swing (long-short 4+2 ticks), straight polka / march (oom-pah), double-time (a backbeat on every off eighth), half-time (one heavy snare on beat 3), stop-time (one hit, a gap, a laugh) and the
march roll. The feel, the key (a half step, a minor third, a tritone ... every 4 bars, abruptly) and the lead instrument (every 2 bars) change all the time: the player is toyed with.
The device is the MIRROR: the tune (three 4-bar phrases in F# minor, chords i - bVII7 - iv - V7 and so on) is answered by its own upside-down reflection (every interval turned about an axis; the reflection
of the minor tonic is a major dominant with a major 7th, and the reflection of the blue notes grinds against the chord) on a second instrument: at once, bar by bar, phrase by phrase or one beat late.
Menace: heavy walking bass doubled by the didgeridoo, shell stabs of a third and a seventh (a tritone), guitar power hits, a cruel flute laugh doubled a tritone below.
Phase 2 (Shattered) is the same 104 bars, chords and loop point, but the mirror is cracked: the swing falls apart into chiptune - the feel changes every bar, the key every 2 bars, the tune is cut into
shards (halves, beats, stutters) and detuned by a half step here and there, the reflection axis wobbles, the bass stutters in eighths on the bit chip, arpeggios flicker and the drums glitch.
Everything is written by what SOUNDS. Nothing is random (a hash of the bar and the tick). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat45 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS = 20
BEAT = 6
BAR_T = 24
START = 64                       # the loop point: a silent-free 16-tick laugh + two bars of intro = 64 ticks
UNITS = 26                       # of 4 bars
NBARS = UNITS * 4                # 104 bars = 124.8 s
LENGTH = START + NBARS * BAR_T
TONIC = 66                       # F#4

# chords as (root, intervals above it)
I = (0, (0, 3, 7, 10))
IV = (5, (0, 3, 7, 10))
BVI = (8, (0, 4, 7, 11))
V7 = (7, (0, 4, 7, 10))
BVII7 = (10, (0, 4, 7, 10))
DIM = (11, (0, 3, 6, 9))
PROGS = {
    "A": [[(I, 0)], [(I, 0), (BVII7, 12)], [(IV, 0)], [(V7, 0)]],
    "B": [[(BVI, 0)], [(DIM, 0)], [(I, 0)], [(V7, 0)]],
    "C": [[(I, 0)], [(BVII7, 0)], [(BVI, 0)], [(V7, 0)]],
    "D": [[(I, 0)], [(I, 0)], [(I, 0)], [(V7, 0)]],
}
# the tunes: semitones above the tonic, on the swing grid (0 4 | 6 10 | 12 16 | 18 22); every phrase ends hanging UP
MEL = {
    "A": [  # the jazz-circus theme: a minor arpeggio with a leading tone, climbing
        [(0, 12), (4, 15), (6, 19), (10, 15), (12, 12), (16, 11), (18, 12), (22, 14)],
        [(0, 15), (4, 14), (6, 12), (10, 10), (12, 14), (16, 17), (18, 14), (22, 12)],
        [(0, 12), (4, 15), (6, 17), (10, 15), (12, 20), (16, 17), (18, 15), (22, 16)],
        [(0, 17), (4, 14), (6, 11), (10, 14), (12, 17), (16, 19), (18, 20), (22, 23)],
    ],
    "B": [  # a sequence in three steps ending in a chromatic climb
        [(0, 20), (4, 19), (6, 15), (10, 19), (12, 20), (16, 24), (18, 20), (22, 19)],
        [(0, 23), (4, 20), (6, 17), (10, 20), (12, 23), (16, 26), (18, 23), (22, 20)],
        [(0, 24), (4, 22), (6, 19), (10, 22), (12, 24), (16, 27), (18, 29), (22, 31)],
        [(0, 26), (4, 29), (6, 26), (10, 23), (12, 26), (14, 27), (16, 28), (18, 29), (20, 30), (22, 31)],
    ],
    "C": [  # the laughing shout: three repeated notes (a triplet), then a leap
        [(0, 19), (2, 19), (4, 19), (6, 22), (10, 19), (12, 15), (16, 19), (18, 22), (22, 24)],
        [(0, 22), (2, 22), (4, 22), (6, 26), (10, 22), (12, 20), (16, 22), (18, 26), (22, 29)],
        [(0, 24), (2, 24), (4, 24), (6, 27), (10, 24), (12, 20), (16, 24), (18, 27), (22, 31)],
        [(0, 26), (2, 26), (4, 26), (6, 29), (10, 26), (12, 23), (16, 26), (18, 29), (22, 32)],
    ],
}
AXIS = {"A": 17, "B": 23, "C": 24}      # the mirror: s becomes 2 * axis - s
LAUGH = [3, 0, 3, 0, 2, -1, 2, -1, 1, -2]   # semitones from the anchor of the flute (84 to 95), then the shriek
SHRIEK = 6

I_ = IRON_XYLOPHONE
INST = {"X": I_, "P": PLING, "B": BIT, "F": FLUTE, "J": BANJO, "G": GUITAR, "H": HARP, "Y": XYLOPHONE}
TARGET = {HARP: 70, I_: 70, PLING: 70, BIT: 70, BANJO: 70, FLUTE: 88, GUITAR: 54, XYLOPHONE: 100}


def U(prog, mel, feels, key, leads, mirror, stab, dens, tags=()):
    return dict(prog=prog, mel=mel, feels=feels.split(), key=key, leads=leads, mirror=mirror, stab=stab, dens=dens, tags=set(tags))


PLAN = [
    # A: swing (16 bars)
    U("A", "A", "SW SW SW SW", 0, "XX", None, "J", 3, "hit"),
    U("B", "B", "SW SW SW SW", 0, "XP", ("B", "call"), "J", 3, "fill"),
    U("A", "A", "SW SW SW HT", 1, "FF", None, "P", 3, "hit fill"),
    U("C", "C", "SW SW SW SB", 1, "JP", ("B", "alt"), "G", 4, "accel"),
    # A': straight polka / double time (16)
    U("A", "A", "ST ST ST ST", 4, "FF", None, "J", 4, "hit"),
    U("B", "B", "ST ST ST DB", 4, "BB", ("G", "alt"), "P", 4, "fill"),
    U("A", "A", "DB DB DB DB", 10, "PX", None, "J", 4, "hit"),
    U("C", "C", "DB DB HT HT", 10, "XY", ("B", "echo"), "G", 4, "accel"),
    # B: takes away (16)
    U("C", "C", "HT HT HT HT", 3, "GG", None, "G", 1, "hit lead"),
    U("D", "A", "SB SB SB SB", 3, "XX", None, "G", 1, "laugh"),
    U("C", "C", "HT HT HT HT", 0, "GB", ("B", "sim"), "G", 2, "hit"),
    U("A", "A", "HT HT SW SW", 0, "XX", ("B", "echo"), "J", 3, "accel"),
    # A'': swing returns, the instruments swap every 2 bars (16)
    U("A", "A", "SW SW SW SW", 6, "XF", None, "J", 4, "hit"),
    U("B", "B", "SW SW SW SW", 6, "PY", ("B", "call"), "P", 4, "fill"),
    U("A", "A", "SW SW DB DB", 7, "FB", ("G", "alt"), "G", 4, "hit"),
    U("C", "C", "DB DB DB MR", 7, "XX", ("B", "sim"), "J", 4, "accel"),
    # PEAK: everything, the feel changes every 2 bars (24)
    U("A", "A", "SW SW DB DB", 7, "FF", ("B", "sim"), "P", 5, "hit"),
    U("B", "B", "ST ST HT HT", 1, "XP", ("G", "sim"), "J", 5, "fill"),
    U("A", "A", "DB DB SW SW", 4, "BF", ("B", "sim"), "G", 5, "hit"),
    U("C", "C", "MR MR DB DB", 10, "YF", ("X", "sim"), "P", 5, "fill"),
    U("A", "A", "SW HT SW HT", 0, "FB", ("G", "sim"), "J", 5, "hit"),
    U("B", "B", "DB DB DB MR", 6, "XF", ("B", "sim"), "P", 5, "accel"),
    # CALM (12) and the ride back into the loop (4)
    U("A", "A", "HT HT HT HT", 0, "PP", None, "G", 2, "hit quiet"),
    U("B", "B", "SW SW SW SW", 0, "HH", None, "J", 2, "quiet"),
    U("C", "C", "HT HT SB SB", 5, "JJ", None, "G", 2, "quiet laugh"),
    U("A", "A", "SW SW SW SW", 0, "XX", ("B", "echo"), "J", 3, "hit accel"),
]
assert len(PLAN) == UNITS
QUARTER_KEY = [1, 6, 3, 7, 2, 5]

ROLES = {"lead": (100, 80), "mirror": (88, 120), "dbl": (70, 90), "laugh": (92, 100), "arp": (56, 110), "stab": (90, 100), "power": (85, 100),
         "bass": (100, 100), "didg": (100, 100), "kick": (100, 100), "snare": (90, 105), "hat": (60, 115)}


def key_of(phase, bar):
    unit, ub = divmod(bar, 4)
    key = PLAN[unit]["key"]
    if phase == 1 and unit < UNITS - 1 and ub >= 2:
        key = (key + QUARTER_KEY[(unit * 5 + 1) % 6]) % 12     # the key changes every 2 bars: a half step, a tritone, a third ...
    return key


def chords_of(phase, bar):
    unit, ub = divmod(bar, 4)
    key = key_of(phase, bar)
    return [((TONIC + key + c[0]) % 12, c, offset) for c, offset in PROGS[PLAN[unit]["prog"]][ub]]


def feel_of(phase, bar):
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    if phase == 0:
        return plan["feels"][ub]
    choices = {1: ["HT", "HT", "SB", "HT"], 2: ["HT", "ST", "SB", "DB"], 3: ["DB", "ST", "HT", "SW"], 4: ["DB", "SW", "ST", "DB", "HT", "SW"], 5: ["DB", "SW", "ST", "DB", "MR", "SW", "DB", "HT"]}
    if plan["feels"][ub] == "SB":
        return "SB"
    pool = choices[phase_dens(phase, bar)]
    return pool[hash_(bar, 7) % len(pool)]


def phase_dens(phase, bar):
    plan = PLAN[bar // 4]
    return min(5, plan["dens"] + (1 if phase == 1 and plan["dens"] == 3 and "quiet" not in plan["tags"] else 0))


def straight(events):
    table = {0: 0, 2: 3, 4: 3, 6: 6, 8: 9, 10: 9, 12: 12, 14: 15, 16: 15, 18: 18, 20: 21, 22: 21}
    seen, result = set(), []
    for tick, s in events:
        t = table[tick]
        if t not in seen:
            seen.add(t)
            result.append((t, s))
    return result


def place(instrument, key, semis):
    """The octave n for a whole phrase so that every note fits the instrument, nearest to its comfortable height."""
    low, high = RANGE[instrument]
    best, best_cost = 0, None
    for n in range(-48, 49, 12):
        pitches = [TONIC + key + s + n for s in semis]
        over = sum(max(0, p - high) + max(0, low - p) for p in pitches)
        cost = over * 100 + abs(sum(pitches) / len(pitches) - TARGET[instrument])
        if best_cost is None or cost < best_cost:
            best, best_cost = n, cost
    return best


def melody_bar(phase, bar, feel):
    """[(tick, s)] of the tune in this bar, shaped by the feel (and in phase 2 cut into shards)."""
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    events = list(MEL[plan["mel"]][ub])
    if feel in ("ST", "DB", "MR"):
        events = straight(events)
    elif feel == "HT":
        events = [(t, s) for t, s in events if t % 6 == 0]
    if phase == 1:
        mode = hash_(bar, 3) % (6 if phase_dens(phase, bar) >= 4 else 3)
        if mode == 1:
            events = [(t, s) for t, s in events if t < 12]
        elif mode == 2:
            events = [(t, s) for t, s in events if t >= 12]
        elif mode == 3:
            events = [(t, s) for t, s in events if t % 6 < 3]
        elif mode == 4 and events:
            events = [(2 * i, events[0][1]) for i in range(4)] + [(t, s) for t, s in events if t >= 12]
        elif mode == 5:
            events = [(t, s) for t, s in events if t < 6 or 12 <= t < 18]
        events = [(t, s + 1 if hash_(bar, t) % 7 == 0 else s) for t, s in events]
    return events


def bass_note(pc, target):
    return near(pc, target, BASS)


def play(phase):
    b = Builder(LENGTH, ROLES)
    # ---- the intro: a laugh (16 ticks) and two bars that set the swing, the second climbing into tick 64
    anchor = near(TONIC % 12, 88, FLUTE)
    laugh(b, 0, phase, anchor, 8, 0)
    b.drum("kick", 0, BASEDRUM, 100)
    b.note("bass", 0, BASS, bass_note(6, 36), 100)
    b.note("didg", 0, DIDGERIDOO, near(6, 34, DIDGERIDOO), 100)
    for k in range(8):
        b.drum("snare", 8 + k, SNARE, 30 + 8 * k)
    for index, (name, chord) in enumerate((("i", I), ("v", V7))):
        t0 = 16 + index * BAR_T
        pc = (TONIC + chord[0]) % 12
        feel = "SW" if phase == 0 else ("DB" if index == 0 else "SW")
        bar_rhythm(b, t0, [(pc, chord, 0)], feel, 0, 3 if index == 0 else 4, phase, 900 + index, (TONIC + 7) % 12 if index == 0 else TONIC % 12, "J", False, False)
        if index == 0:
            hit(b, t0, pc, chord)
    for k, s in enumerate((6, 7, 8, 9, 10, 11)):                # the run into the loop
        b.note("lead", 64 - 12 + 2 * k, I_ if phase == 0 else BIT, TONIC + s, 60 + 4 * k)
    # ---- the 104 bars
    for bar in range(NBARS):
        t0 = START + bar * BAR_T
        unit, ub = divmod(bar, 4)
        plan = PLAN[unit]
        feel = feel_of(phase, bar)
        dens = phase_dens(phase, bar)
        quiet = 0.7 if "quiet" in plan["tags"] else 1.0
        chords = chords_of(phase, bar)
        key = key_of(phase, bar)
        next_pc = chords_of(phase, bar + 1)[0][0] if bar + 1 < NBARS else TONIC % 12
        last = ub == 3
        accel = "accel" in plan["tags"] and last
        fill = last and "accel" not in plan["tags"] and dens >= 2 and feel != "SB"
        bar_rhythm(b, t0, chords, feel, key, dens, phase, bar, next_pc, plan["stab"], fill, accel, quiet)
        if ("hit" in plan["tags"] and ub == 0) or feel == "SB":
            hit(b, t0, chords[0][0], chords[0][1], 0.7 if quiet < 1 else 1.0)
        if feel == "SB":
            laugh(b, t0 + 12, phase, near(chords[0][0], 88, FLUTE), 6, bar)
        if feel != "SB":
            lead_bar(b, t0, phase, bar, feel, dens, quiet, key)
        if phase == 1 and dens >= 4 and feel != "SB":
            arp_bar(b, t0, bar, chords, dens)
    return b


def laugh(b, t0, phase, anchor, count, bar):
    """The flute laugh (2 ticks apart) ending in a shriek that is left hanging; doubled a tritone below on the bit chip. In phase 2 it stutters."""
    offsets = LAUGH[:count - 1] + [SHRIEK]
    for index, s in enumerate(offsets):
        tick = t0 + 2 * index
        pitch = anchor + s
        vel = 70 + 3 * index + (14 if index == len(offsets) - 1 else 0)
        b.note("laugh", tick, FLUTE, pitch, vel)
        b.note("laugh", tick, BIT, pitch - 24 + 6, vel * 0.7)
        if phase == 1 and index % 3 == 2:                       # glitch: a stuttered copy one tick later
            b.note("laugh", tick + 1, BIT, pitch - 24, vel * 0.5)


def hit(b, t0, pc, chord, scale_=1.0):
    """The ensemble hit on a downbeat: bass, didgeridoo, low guitar, the shell (third + seventh) in pling, kick and snare."""
    b.note("bass", t0, BASS, bass_note(pc, 36), 100 * scale_, once=True)
    b.note("didg", t0, DIDGERIDOO, near(pc, 33, DIDGERIDOO), 100 * scale_, once=True)
    b.note("power", t0, GUITAR, near(pc, 47, GUITAR), 92 * scale_, once=True)
    b.note("power", t0, GUITAR, near(pc + 7, 52, GUITAR), 80 * scale_, once=True)
    b.note("stab", t0, PLING, near(pc + chord[1][1], 62, PLING), 88 * scale_, once=True)
    b.note("stab", t0, PLING, near(pc + chord[1][3], 58, PLING), 88 * scale_, once=True)
    b.drum("kick", t0, BASEDRUM, 100 * scale_, once=True)
    b.drum("snare", t0, SNARE, 92 * scale_, once=True)


def bar_rhythm(b, t0, chords, feel, key, dens, phase, bar, next_pc, stab_code, fill, accel, quiet=1.0):
    """Bass, didgeridoo, stabs and drums of one bar in the given feel."""
    first_pc, first_chord, _ = chords[0]
    second = chords[1] if len(chords) > 1 else None
    def chord_at(tick):
        return second if second and tick >= second[2] else chords[0]
    appr = (next_pc + (1 if bar % 2 == 0 else -1)) % 12
    # --- bass
    notes = []
    r1 = bass_note(first_pc, 38)
    if feel == "SW":
        n1 = bass_note((first_pc + first_chord[1][1]) % 12, r1 + 2)
        if second:
            f1 = bass_note((first_pc + 7) % 12, r1 + 3)
            r2 = bass_note(second[0], f1)
            notes = [(0, r1, 92), (6, f1, 66), (12, r2, 88), (18, bass_note(appr, r2), 72)]
        else:
            n2 = bass_note((first_pc + 7) % 12, n1 + 2)
            notes = [(0, r1, 92), (6, n1, 68), (12, n2, 74), (18, bass_note(appr, n2), 72)]
    elif feel in ("ST", "MR"):
        f1 = bass_note((first_pc + 7) % 12, r1 + 3)
        r2 = bass_note(second[0], f1) if second else r1
        notes = [(0, r1, 95), (6, f1, 70), (12, r2, 90), (18, bass_note(appr, r2) if fill else bass_note((chord_at(18)[0] + 7) % 12, r2 + 3), 74)]
    elif feel == "DB":
        f1 = bass_note((first_pc + 7) % 12, r1 + 3)
        top = min(54, r1 + 12)
        notes = [(0, r1, 92), (3, f1, 60), (6, r1, 78), (9, f1, 60), (12, r1, 84), (15, f1, 60), (18, top, 74), (21, bass_note(appr, top), 70)]
    elif feel == "HT":
        f1 = bass_note((first_pc + 7) % 12, r1 + 3)
        notes = [(0, r1, 100), (9, r1, 62), (15, f1, 76), (21, bass_note(appr, r1), 74)]
    elif feel == "SB":
        notes = [(0, r1, 100)]
    for tick, pitch, vel in notes:
        if phase == 1 and feel in ("SW", "ST", "MR") and hash_(bar, tick, 11) % 5 == 0:
            continue                                          # shards of the bass line
        b.note("bass", t0 + tick, BASS, pitch, vel * quiet)
    if phase == 1 and feel in ("SW", "ST", "MR"):             # the bass stutters in eighths between the shards of the line
        used = {tick for tick, _, _ in notes}
        for tick in range(0, 24, 3):
            if tick not in used and hash_(bar, tick, 5) % 3:
                b.note("bass", t0 + tick, BASS, min(54, r1 + (12 if tick % 6 == 3 else 0)), 56 * quiet, once=True)
    # --- didgeridoo: the weight
    d = near(first_pc, 33, DIDGERIDOO)
    if feel == "HT":
        for index, tick in enumerate(range(0, 24, 4)):
            b.note("didg", t0 + tick, DIDGERIDOO, d, (88 - 7 * index) * quiet)
    elif feel == "SB":
        b.note("didg", t0, DIDGERIDOO, d, 100)
    elif dens >= 2:
        for tick in (0, 12):
            b.note("didg", t0 + tick, DIDGERIDOO, near(chord_at(tick)[0], 33, DIDGERIDOO), 84 * quiet)
        if dens >= 5:
            for tick in (6, 18):
                b.note("didg", t0 + tick, DIDGERIDOO, d, 62)
    # --- stabs (the comping): a third and a seventh, a tritone
    stab_inst = INST[stab_code]
    comps = {"SW": [(0, 66), (10, 62), (22, 56)], "ST": [(6, 68), (18, 68)], "DB": [(3, 62), (9, 62), (15, 62), (21, 62)], "HT": [(0, 88), (18, 70)], "MR": [(0, 70), (12, 70)], "SB": []}[feel]
    if dens >= 2:
        for tick, vel in comps:
            pc, chord, _ = chord_at(tick)
            third = near(pc + chord[1][1], 62, stab_inst)
            seventh = near(pc + chord[1][3], 57, stab_inst)
            if phase == 1 and hash_(bar, tick, 2) % 4 == 0:
                continue
            b.note("stab", t0 + tick, stab_inst, third, vel * quiet)
            b.note("stab", t0 + tick, stab_inst, seventh, vel * quiet)
            if dens >= 5 and tick in (0, 12):
                b.note("power", t0 + tick, GUITAR, near(pc, 47, GUITAR), 80)
                b.note("power", t0 + tick, GUITAR, near(pc + 7, 52, GUITAR), 70)
    # --- drums
    ev = {}
    def d_(drum, tick, vel):
        key_ = (drum, tick)
        ev[key_] = max(ev.get(key_, 0), vel * (1.0 if drum != HAT else 1.0))
    if feel == "SW":
        for tick, vel in ((0, 80), (12, 70)):
            d_(BASEDRUM, tick, vel)
        for tick, vel in ((6, 82), (18, 84)):
            d_(SNARE, tick, vel)
        if dens >= 3:
            d_(SNARE, 10, 26); d_(SNARE, 22, 26)
        if dens >= 4:
            d_(BASEDRUM, 22, 56)
        if dens >= 2:
            for tick, vel in ((0, 30), (6, 40), (10, 32), (12, 30), (18, 40), (22, 32)):
                d_(HAT, tick, vel)
    elif feel == "ST":
        for tick, vel in ((0, 96), (12, 92), (6, 46), (18, 46)):
            d_(BASEDRUM, tick, vel)
        for tick, vel in ((6, 84), (18, 84), (9, 44), (21, 44)):
            d_(SNARE, tick, vel)
        if dens >= 2:
            for tick in (3, 15):
                d_(HAT, tick, 36)
    elif feel == "DB":
        for tick in (0, 6, 12, 18):
            d_(BASEDRUM, tick, 88)
        for tick in (3, 9, 15, 21):
            d_(SNARE, tick, 74)
        if dens >= 3:
            for tick in (0, 6, 12, 18):
                d_(HAT, tick, 36)
    elif feel == "HT":
        d_(BASEDRUM, 0, 100); d_(BASEDRUM, 9, 62); d_(BASEDRUM, 21, 60)
        d_(SNARE, 12, 100)
        if dens >= 2:
            d_(HAT, 6, 40); d_(HAT, 18, 40)
        if dens >= 3:
            d_(SNARE, 15, 30)
    elif feel == "SB":
        d_(BASEDRUM, 0, 100); d_(SNARE, 0, 92)
    elif feel == "MR":
        for tick in range(0, 24, 2):
            d_(SNARE, tick, 30 + 2 * tick + (10 if tick % 6 == 0 else 0))
        d_(BASEDRUM, 0, 96); d_(BASEDRUM, 12, 92)
    if phase == 1 and feel != "SB":                           # the drums glitch: a burst of single-tick hits at the end of the bar
        burst = hash_(bar, 9) % 3
        if burst == 0:
            for tick in (20, 21, 22):
                d_(BASEDRUM, tick, 60)
        elif burst == 1:
            for tick in (18, 19, 20, 21):
                d_(SNARE, tick, 50 + 8 * (tick - 18))
        else:
            for tick in range(16, 24):
                d_(HAT, tick, 30 + 3 * (tick - 16))
    if fill:
        for key_ in [k for k in ev if k[1] >= 12 and k[0] in (SNARE, HAT)]:
            del ev[key_]
        step = 1 if dens >= 5 else 2
        for count, tick in enumerate(range(12, 24, step)):
            d_(SNARE, tick, 42 + 5 * count if step == 2 else 36 + 4 * count)
    if accel:
        for key_ in [k for k in ev if k[1] >= 6 and k[0] in (SNARE, HAT)]:
            del ev[key_]
        for index, tick in enumerate((6, 12, 16, 19, 21, 22, 23)):
            d_(SNARE, tick, 40 + 9 * index)
            if index % 2 == 0:
                d_(BASEDRUM, tick, 70 + 4 * index)
    for (drum, tick), vel in ev.items():
        role = {BASEDRUM: "kick", SNARE: "snare", HAT: "hat"}[drum]
        b.drum(role, t0 + tick, drum, vel * (quiet if quiet < 1 and drum != BASEDRUM else 1.0))


def lead_bar(b, t0, phase, bar, feel, dens, quiet, key):
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    if dens < 3 and "lead" not in plan["tags"]:
        return
    seg = ub // 2
    code = plan["leads"][seg]
    inst = INST[code]
    if phase == 1:
        inst = {FLUTE: BIT, I_: BIT, XYLOPHONE: PLING}.get(inst, inst) if hash_(bar, 1) % 3 else inst
    # the octave is chosen for the 2 bars together so that the line does not jump
    semis = [s for bb in (seg * 2, seg * 2 + 1) for _, s in MEL[plan["mel"]][bb]]
    n = place(inst, key, semis)
    events = melody_bar(phase, bar, feel)
    mirror = plan["mirror"]
    axis = AXIS[plan["mel"]]
    if phase == 1:
        axis += (hash_(bar, 4) % 3) - 1                          # the axis of the cracked mirror wobbles
    mode = mirror[1] if mirror else None
    play_lead = True
    play_mirror = False
    if mirror and dens >= 3:
        if mode == "sim":
            play_mirror = True
        elif mode == "alt":
            play_mirror = ub % 2 == 1
            play_lead = not play_mirror
        elif mode == "call":
            play_mirror = seg == 1
            play_lead = not play_mirror
        elif mode == "echo":
            play_mirror = True
    for tick, s in events:
        accent = 9 if tick % 12 == 0 else 0
        vel = (74 + accent) * quiet
        if play_lead:
            b.note("lead", t0 + tick, inst, TONIC + key + s + n, vel)
            if dens >= 4 and plan["leads"][seg] != "G":
                high = inst in (FLUTE, XYLOPHONE)
                b.note("dbl", t0 + tick, I_ if high else FLUTE, TONIC + key + s + n + (-12 if high else 12), vel * 0.55)
    if play_mirror:
        m_inst = INST[mirror[0]]
        m_semis = [2 * axis - s for _, s in MEL[plan["mel"]][ub]]
        all_m = [2 * axis - s for bb in range(4) for _, s in MEL[plan["mel"]][bb]]
        mn = place(m_inst, key, all_m)
        delay = BEAT if mode == "echo" else 0
        for tick, s in events:
            b.note("mirror", t0 + tick + delay, m_inst, TONIC + key + 2 * axis - s + mn, (68 + (8 if tick % 12 == 0 else 0)) * quiet * (0.8 if mode == "echo" else 1.0))


def arp_bar(b, t0, bar, chords, dens):
    """Phase 2: a chip arpeggio flickers over the chord (up and down, with holes); in the peak a note every tick."""
    step_ = 1 if dens >= 5 else 2
    for tick in range(0, 24, step_):
        pc, chord, off = chords[1] if len(chords) > 1 and tick >= chords[1][2] else chords[0]
        tones = [pc + i for i in chord[1]]
        order = [0, 1, 2, 3, 2, 1]
        step = (tick // step_) % 6
        if hash_(bar, tick, 6) % (2 if dens >= 5 else 3) == 0:
            continue
        b.note("arp", t0 + tick, BIT, near(tones[order[step]], 66 + 3 * order[step], BIT), 40 + (hash_(bar, tick) % 14))


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    titles = ("The Cracked Mirror Laughs (Boss007)", "The Cracked Mirror Laughs, Shattered (Boss007)")
    descriptions = ("Mirror boss, phase 1, class 4 (carnival of madness): 200 BPM swing / polka / double-time / half-time / stop-time, a key change every 4 bars, a tune answered by its own upside-down reflection. Loops from tick 64.",
                    "Mirror boss, phase 2: the same 104 bars and chords, the mirror cracked - the feel changes every bar, the key every 2 bars, the tune in shards, chiptune glitch, stuttering bass and drums.")
    edges = []
    cursor = 0
    for name, bars in (("intro", None), ("A", 16), ("Ap", 16), ("B", 16), ("App", 16), ("PEAK", 24), ("CALM", 12), ("RAMP", 4)):
        if bars is None:
            edges.append((name, 0, START))
            cursor = START
        else:
            edges.append((name, cursor, cursor + bars * BAR_T))
            cursor += bars * BAR_T
    for phase in range(2):
        b = play(phase)
        target = out / ("mirror_phase1.nbs" if phase == 0 else "mirror_phase2.nbs")
        layers = finish(target, b, TPS, titles[phase], descriptions[phase])
        print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))


if __name__ == "__main__":
    main()
