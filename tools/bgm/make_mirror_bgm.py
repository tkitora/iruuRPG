#!/usr/bin/env python3
"""Makes mirror_phase1.nbs and mirror_phase2.nbs: "The Cracked Mirror Laughs (Boss007)" and "..., Shattered (Boss007)" - class 4, the carnival of madness (a trickster). Written from zero (t281).

    python3 tools/bgm/make_mirror_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_mirror_bgm.py outdir     writes them somewhere else

STYLE 3 of the style doc (symphonic open, 180 BPM) on the ladder of the BPM guide: 12 ticks a second, a tick is a sixteenth, a bar is 16 ticks (4 intro + 108 bars = 1792 ticks = 149.3 s). Key A.
THE MIRROR: the motif is a palindrome, A Bb C Bb A (a cell that reads the same backwards), swung (ticks 0 3 4 7 8) and followed by a falling-rising tail. It is played ON the root of every chord, so
that it climbs with the chords (A - D - E ...: a cell raised a step each bar), and the fourth bar of every phrase climbs and hangs. The mirror voice plays the same tune turned upside down (every
interval reflected about E). Swing = the off eighth on the third tick of the beat; a triplet figure = ticks 0 3 5 (3 + 3 + 2) or 0 1 3 inside a beat. The FEEL changes bar by bar (SW swing, ST polka,
TR triplet figure, HT half time, SB stop time + laugh, MR march roll), the key jolts abruptly, and the lead instrument is swapped every two bars.
Time table (a bar is 1.33 s):
  0:00  OPENING (4 bars): the last phrase of the chorus (D - E7, A MAJOR) at full strength: sixteenth bass, tom rolls, hanging on the dominant.
  0:05  A (28 bars, to 0:42): sparse - only the bass (swung eighths, then oom-pah), the kick and the snare; the tune moved onto the off beats and chattering (A minor).
  0:42  B (24 bars, to 1:14): the tune HALF-ED (every note twice as long) while the drums keep their speed; eighths with sixteenth double hits and triplet figures, the line climbing and falling;
        the last bar is the BREAK: every drum and the bass muted, one bell.
  1:14  CHORUS (40 bars, to 2:07): the SAME-TONIC MAJOR (A minor -> A major: C#, F#, G# for C, F, G): the catchy form of the tune in the flute, over a full SIXTEENTH bass; power chords on every beat;
        the descant in long notes of the harp from bar 20; a half step jolt in the middle.
  2:07  OUTRO (16 bars): back to minor; tom rolls (the bass drum on falling pitches) fill every bar's end, the last bar a rising roll into tick 64.
Phase 2 (Shattered): the same bars, chords, break and loop; the machine gun starts earlier (A already walks, B is sixteenths); the feel changes every bar, the key every 2 bars (not in the chorus), the tune in
shards and detuned by a half step here and there, the axis of the mirror wobbles, a chip arpeggio flickers in B, the drums glitch.
Nothing is random (a hash of the bar and the tick). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from boss281 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 180 / 15
BAR_T = 16
START = 64
TONIC = 69                      # A4 (the pitch class A = 9)
UNITS = 27
NBARS = UNITS * 4
LENGTH = START + NBARS * BAR_T

# chords: (root in semitones above A, intervals)
Am = (0, (0, 3, 7, 10))
Dm = (5, (0, 3, 7, 10))
Em = (7, (0, 3, 7, 10))
E7 = (7, (0, 4, 7, 10))
C = (3, (0, 4, 7, 11))
Bb = (1, (0, 4, 7, 11))
G7 = (10, (0, 4, 7, 10))
F = (8, (0, 4, 7, 11))
D7 = (5, (0, 4, 7, 10))
A6 = (0, (0, 4, 7, 11))
DM = (5, (0, 4, 7, 11))
FSm = (9, (0, 3, 7, 10))
# progressions of a 4-bar unit: the roots rise (the cell is played on every root and climbs with them); minor, and the same in the major of the chorus
PROG_M = {
    "R1": [[(Am, 0)], [(Dm, 0)], [(Em, 0)], [(E7, 0)]],
    "R2": [[(Am, 0)], [(C, 0)], [(Dm, 0)], [(E7, 0)]],
    "R3": [[(Am, 0)], [(Bb, 0)], [(C, 0)], [(Dm, 0), (E7, 8)]],
    "R4": [[(Am, 0)], [(Am, 0), (G7, 8)], [(F, 0)], [(E7, 0)]],
}
PROG_MAJ = {
    "R1": [[(A6, 0)], [(DM, 0)], [(E7, 0)], [(E7, 0)]],
    "R2": [[(A6, 0)], [(FSm, 0)], [(DM, 0)], [(E7, 0)]],
    "R3": [[(A6, 0)], [(A6, 0), (G7, 8)], [(DM, 0)], [(DM, 0), (E7, 8)]],
    "R4": [[(A6, 0)], [(DM, 0)], [(FSm, 0)], [(E7, 0)]],
}
ROOTS = {"R1": [0, 5, 7], "R2": [0, 3, 5], "R3": [0, 1, 3], "R4": [0, 10, 8]}       # semitones where the cell is played in bars 0-2 (bar 3 climbs)
CELL_A = [(0, 0), (3, 1), (4, 3), (7, 1), (8, 0), (11, -2), (12, 0), (15, 1)]
CELL_B = [(0, 0), (1, 0), (3, 0), (4, 3), (7, 1), (8, 0), (12, 3), (15, 1)]          # the laugh: three repeated notes in a beat (0 1 3)
CLIMB = [(0, 7), (3, 10), (4, 14), (7, 12), (8, 14), (12, 19)]
AXIS = 7                         # the mirror: s -> 2 * 7 - s
I_ = IRON_XYLOPHONE
INST = {"X": I_, "P": PLING, "B": BIT, "F": FLUTE, "J": BANJO, "G": GUITAR, "H": HARP, "Y": XYLOPHONE}
TARGET = {HARP: 66, I_: 66, PLING: 66, BIT: 66, BANJO: 64, FLUTE: 88, GUITAR: 54, XYLOPHONE: 100}
TARGET_CH = {HARP: 66, I_: 66, PLING: 62, BIT: 62, BANJO: 62, FLUTE: 90, GUITAR: 54, XYLOPHONE: 100}


def U(prog, cell, feels, key, leads, mirror, stab, levels, tags=(), mode="m"):
    return dict(prog=prog, cell=cell, feels=feels.split(), key=key, leads=leads, mirror=mirror, stab=stab, lv=levels, tags=set(tags), mode=mode)


PLAN = [
    # A (28 bars)
    U("R1", "A", "SW SW SW SW", 0, "XX", None, "J", (1, 2), "hit"),
    U("R2", "B", "SW SW SW TR", 0, "XP", ("B", "call"), "J", (1, 2), "hit"),
    U("R4", "A", "SW SW SW SW", 1, "FF", None, "P", (1, 2), "hit"),
    U("R1", "B", "SW SW SW SB", 1, "JP", ("B", "alt"), "G", (1, 2), ""),
    U("R2", "A", "ST ST ST ST", 4, "FF", None, "J", (2, 3), "hit"),
    U("R3", "B", "ST ST ST TR", 4, "BB", ("G", "alt"), "P", (2, 3), "hit"),
    U("R4", "A", "ST ST HT HT", 10, "PX", ("B", "echo"), "J", (2, 3), "accel"),
    # B (24 bars): the tune half-ed; the last bar of the last unit is the break
    U("R1", "A", "SW TR SW TR", 3, "XX", None, "G", (3, 4), "hit"),
    U("R1", "A", "SW TR SW SB", 3, "FB", None, "J", (3, 4), "laugh"),
    U("R2", "B", "TR SW TR SW", 6, "PX", ("B", "echo"), "P", (3, 4), "hit"),
    U("R2", "B", "SW TR SW TR", 6, "XF", ("G", "sim"), "G", (3, 4), ""),
    U("R3", "A", "TR TR SW SW", 0, "YF", ("B", "sim"), "J", (3, 4), "hit"),
    U("R3", "A", "TR SW TR SW", 0, "XX", ("X", "sim"), "G", (3, 4), ""),
    # CHORUS (40 bars): A major
    U("R1", "A", "ST ST SW SW", 0, "FF", ("B", "sim"), "P", (4, 4), "hit", "M"),
    U("R2", "B", "ST ST ST SW", 0, "FF", ("G", "sim"), "J", (4, 4), "hit", "M"),
    U("R4", "A", "SW SW ST ST", 0, "FF", ("B", "sim"), "G", (4, 4), "hit", "M"),
    U("R3", "B", "ST ST SW SW", 0, "FF", ("X", "sim"), "P", (4, 4), "hit", "M"),
    U("R1", "A", "ST ST SW SW", 1, "FF", ("G", "sim"), "J", (4, 4), "hit", "M"),
    U("R2", "B", "SW SW ST ST", 1, "FF", ("B", "sim"), "P", (4, 4), "hit", "M"),
    U("R4", "A", "ST SW ST SW", 0, "FF", ("B", "sim"), "G", (4, 4), "hit", "M"),
    U("R3", "B", "ST ST MR MR", 0, "FF", ("X", "sim"), "P", (4, 4), "hit", "M"),
    U("R1", "A", "ST ST ST ST", 0, "FF", ("B", "sim"), "P", (4, 4), "hit", "M"),
    U("R2", "A", "ST SW ST ST", 0, "FF", ("B", "sim"), "P", (4, 4), "hit", "M"),
    # OUTRO (16 bars)
    U("R1", "A", "SW SW SW SW", 0, "XX", None, "J", (3, 4), "hit"),
    U("R2", "B", "SW TR SW TR", 0, "FB", ("B", "echo"), "G", (3, 4), ""),
    U("R3", "A", "TR SW TR SW", 0, "XF", ("B", "echo"), "P", (3, 4), ""),
    U("R1", "B", "SW SW SW SW", 0, "XX", None, "J", (3, 4), "hit"),
]
assert len(PLAN) == UNITS
SECTIONS = [("A", 7), ("B", 6), ("CHORUS", 10), ("OUTRO", 4)]
assert sum(n for _, n in SECTIONS) == UNITS
SEC = [name for name, n in SECTIONS for _ in range(n)]
BREAK_BAR = 4 * 13 - 1
KEYJOLT = [1, 6, 3, 7, 2, 5]
ROLES = {"lead": (100, 80), "mirror": (88, 120), "dbl": (70, 90), "laugh": (92, 100), "arp": (50, 110), "stab": (85, 100), "power": (90, 100), "power2": (80, 100), "chug": (70, 100),
         "bass": (100, 100), "didg": (100, 100), "chip": (60, 100), "kick": (100, 100), "tom": (100, 100), "snare": (95, 105), "hat": (55, 115), "bell": (70, 100)}


def major(s):
    return s + 1 if s % 12 in (3, 8, 10) else s


def bass_note(pc, target=34):
    return near(pc, target, BASS)


def reach(root, interval):
    p = root + interval
    while p > 50:
        p -= 12
    return p


def spec(phase, bar):
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    sec = SEC[unit]
    key = plan["key"]
    if phase == 1 and sec != "CHORUS" and unit < UNITS - 1 and ub >= 2:
        key = (key + KEYJOLT[(unit * 5 + 1) % 6]) % 12
    table = PROG_MAJ if plan["mode"] == "M" else PROG_M
    chords = [((TONIC + key + c[0]) % 12, c, off) for c, off in table[plan["prog"]][ub]]
    plan_feel = plan["feels"][ub]
    level = plan["lv"][phase]
    feel = plan_feel
    if phase == 1 and plan_feel != "SB":
        pool = {0: ["HT"], 1: ["HT", "SW", "ST", "TR"], 2: ["SW", "ST", "TR", "HT"], 3: ["SW", "TR", "ST", "HT", "SW", "TR"], 4: ["HT", "SW", "TR", "ST", "MR", "SW", "HT", "TR"]}[level]
        feel = pool[hash_(bar, 7) % len(pool)]
    return dict(bar=bar, unit=unit, ub=ub, plan=plan, sec=sec, key=key, chords=chords, feel=feel, level=level, mode=plan["mode"], phase=phase, quiet=0.8 if "quiet" in plan["tags"] else 1.0)


def tune_events(sp):
    """The tune of this bar as [(tick, semitone above the tonic)] (the cell on the chord root, bar 3 the climb), shaped by the feel."""
    plan, ub = sp["plan"], sp["ub"]
    if ub == 3:
        events = list(CLIMB)
    else:
        base = ROOTS[plan["prog"]][ub]
        events = [(t, s + base) for t, s in (CELL_A if plan["cell"] == "A" else CELL_B)]
    fix = major if sp["mode"] == "M" else (lambda s: s)
    events = [(t, fix(s)) for t, s in events]
    feel = sp["feel"]
    if feel in ("ST", "MR"):
        seen, out = set(), []
        for t, s in events:
            t2 = t - 1 if t % 4 == 3 else t
            if t2 not in seen:
                seen.add(t2)
                out.append((t2, s))
        events = out
    elif feel == "HT":
        events = [(t, s) for t, s in events if t % 4 == 0]
    if sp["phase"] == 1:
        mode = hash_(sp["bar"], 3) % (6 if sp["level"] >= 3 else 3)
        if mode == 1:
            events = [(t, s) for t, s in events if t < 8]
        elif mode == 2:
            events = [(t, s) for t, s in events if t >= 8]
        elif mode == 3:
            events = [(t, s) for t, s in events if t % 4 < 2]
        elif mode == 4 and events:
            events = [(i, events[0][1]) for i in range(0, 8, 2)] + [(t, s) for t, s in events if t >= 8]
        elif mode == 5:
            events = [(t, s) for t, s in events if t < 4 or 8 <= t < 12]
        events = [(t, s + 1 if hash_(sp["bar"], t) % 7 == 0 else s) for t, s in events]
    return events


def offbeat(events, sp):
    placed = {}
    for t, s in events:
        placed.setdefault(t + 2 if t % 4 == 0 else t, s)
    result = dict(placed)
    for t, s in placed.items():
        if t % 4 >= 2 and t + 1 < 16 and t + 1 not in result and (sp["phase"] == 1 or hash_(sp["bar"], t, 17) % 2):
            result[t + 1] = s
    return sorted(result.items())


def halved(events, half):
    stretched = [(2 * t, s) for t, s in events]
    out = []
    for i, (t, s) in enumerate(stretched):
        end = stretched[i + 1][0] if i + 1 < len(stretched) else 32
        if 16 * half <= t < 16 * half + 16:
            out.append((t - 16 * half, s, end - t))
    return out


def place(instrument, key, semis, target):
    low, high = RANGE[instrument]
    best, best_cost = 0, None
    for n in range(-48, 49, 12):
        pitches = [TONIC + key + s + n for s in semis]
        over = sum(max(0, p - high) + max(0, low - p) for p in pitches)
        cost = over * 100 + abs(sum(pitches) / len(pitches) - target[instrument])
        if best_cost is None or cost < best_cost:
            best, best_cost = n, cost
    return best


def bass_pitch(level, tick, root, iv, approach, bar):
    slot = tick // 2
    if level <= 1:
        pick = "A" if (slot == 7 and bar % 2) else 0
    elif level == 2:
        pick = (0, 0, 2, 0, 0, 0, 2, "A")[slot]
    elif level == 3:
        pick = (0, 1, 2, 3, 2, 1, "O", "A")[slot]
    else:
        pick = (0, 2, 0, (1, 2, 3, 2)[bar % 4])[tick // 4]
    if pick == "A":
        return approach
    if pick == "O":
        return reach(root, 12) if root + 12 <= 50 else root
    p = reach(root, iv[pick])
    if level == 4 and tick % 4 == 3 and (tick // 4 + bar) % 2 == 0 and p + 12 <= 46:
        p += 12
    return p


def laugh(b, t0, phase, anchor, count):
    offs = [3, 0, 3, 0, 2, -1, 2, -1, 1, -2][:count - 1] + [6]
    for i, s in enumerate(offs):
        v = 80 + 3 * i + (14 if i == len(offs) - 1 else 0)
        b.note("laugh", t0 + 2 * i, FLUTE, anchor + s, min(100, v))
        b.note("laugh", t0 + 2 * i, BIT, anchor + s - 18, v * 0.7)
        if phase == 1 and i % 3 == 2:
            b.note("laugh", t0 + 2 * i + 1, BIT, anchor + s - 24, v * 0.5)


def hit(b, t0, pc, chord, scale_=1.0):
    low = bass_note(pc)
    b.note("bass", t0, BASS, low, 100 * scale_, once=True)
    b.note("didg", t0, DIDGERIDOO, near(pc, 33, DIDGERIDOO), 100 * scale_, once=True)
    b.note("power", t0, GUITAR, near(pc, 47, GUITAR), 100 * scale_, once=True)
    b.note("power2", t0, GUITAR, near(pc + 7, 52, GUITAR), 90 * scale_, once=True)
    b.note("stab", t0, PLING, near(pc + chord[1][1], 62, PLING), 92 * scale_, once=True)
    b.drum("kick", t0, BASEDRUM, 100 * scale_, once=True)
    b.drum("snare", t0, SNARE, 94 * scale_, once=True)


def rhythm(b, t0, sp, next_pc, fill=False, accel=False, toms=None, toms_up=False, chip=True):
    feel, level, phase, bar, chords = sp["feel"], sp["level"], sp["phase"], sp["bar"], sp["chords"]
    quiet = sp["quiet"]
    sparse = sp["sec"] == "A"
    second = chords[1] if len(chords) > 1 else None

    def chord_at(tick):
        return second if second and tick >= second[2] else chords[0]

    appr = (next_pc + (1 if bar % 2 == 0 else -1)) % 12
    ticks = [0, 4, 8, 12] if feel == "SB" else hit_ticks(level, feel, bar)
    for tick in ticks:
        pc, chord, _ = chord_at(tick)
        root = bass_note(pc)
        pitch = bass_pitch(0 if feel == "SB" else level, tick, root, chord[1], bass_note(appr, root), bar)
        if phase == 1 and level <= 3 and feel in ("SW", "ST", "TR") and tick not in (0, 8) and hash_(bar, tick, 11) % 6 == 0:
            continue
        vel = bass_velocity(tick, level)
        if level in (1, 2, 3) and tick % 4 in (2, 3) and tick % 2:
            vel = 70
        vel = (100 if tick == 0 else 72) if feel == "SB" else (66 if level == 0 else vel)
        b.note("bass", t0 + tick, BASS, pitch, vel * quiet)
        heavy = (tick % 4 == 0) if level >= 3 or feel == "HT" else (tick % 8 == 0)
        if heavy:
            b.note("didg", t0 + tick, DIDGERIDOO, near(pitch % 12, pitch, DIDGERIDOO), (96 if tick % 8 == 0 else 82) * quiet)
        if chip and phase == 1 and level == 4 and tick % 8 == 0 and pitch + 24 <= 78:
            b.note("chip", t0 + tick, BIT, pitch + 24, 46)
    comps = {"SW": [(3, 62), (7, 62), (11, 56), (15, 50)], "ST": [(2, 66), (6, 66), (10, 66), (14, 66)], "TR": [(0, 70), (3, 64), (5, 60), (8, 70), (11, 64), (13, 60)],
             "HT": [(0, 90), (12, 72)], "MR": [(0, 72), (8, 72)], "SB": []}[feel]
    if level >= 3:
        comps = comps[::2]
    if level == 4:
        comps = []                                               # the machine gun leaves no room for the comp (and a second stab voice is not heard)
    if sparse:
        comps = comps[:1] if bar % 2 == 0 else []
    inst = INST[sp["plan"]["stab"]]
    if level >= 1:
        for tick, vel in comps:
            if phase == 1 and hash_(bar, tick, 2) % 4 == 0:
                continue
            pc, chord, _ = chord_at(tick)
            b.note("stab", t0 + tick, inst, near(pc + chord[1][1], 62, inst), vel * quiet)
            b.note("stab", t0 + tick, inst, near(pc + chord[1][3], 57, inst), vel * quiet)
    if sp["sec"] == "CHORUS" and feel != "SB":
        for tick in (0, 4, 8, 12):
            pc0 = chord_at(tick)[0]
            b.note("power", t0 + tick, GUITAR, near(pc0, 47, GUITAR), 98, once=True)
            b.note("didg", t0 + tick, DIDGERIDOO, near(pc0, 33, DIDGERIDOO), 92, once=True)
    ev = {}

    def d_(drum, tick, vel):
        ev[(drum, tick)] = max(ev.get((drum, tick), 0), vel)

    sixteenth = level >= 4
    if feel == "SW":
        d_(BASEDRUM, 0, 88); d_(BASEDRUM, 8, 80)
        d_(SNARE, 4, 90); d_(SNARE, 12, 92)
        if level >= 2 and not sixteenth:
            d_(BASEDRUM, 11, 64)
        if 3 <= level < 4:
            d_(SNARE, 7, 34); d_(SNARE, 15, 34)
        if 2 <= level < 4 and not sparse:
            for tick in (3, 7, 11, 15):
                d_(HAT, tick, 38)
    elif feel == "ST":
        for tick, vel in ((0, 98), (4, 74), (8, 94), (12, 74)):
            d_(BASEDRUM, tick, vel)
        if not sixteenth:
            for tick in (2, 6, 10, 14):
                d_(SNARE, tick, 62)
        if level >= 2:
            d_(SNARE, 4, 88); d_(SNARE, 12, 88)
        if level >= 3 and not sparse:
            for tick in range(0, 16, 2):
                d_(HAT, tick, 34)
    elif feel == "TR":
        for tick in (0, 5, 8, 13):
            d_(BASEDRUM, tick, 92 if tick % 8 == 0 else 72)
        d_(SNARE, 3, 88); d_(SNARE, 11, 88)
        if level >= 2:
            d_(SNARE, 7, 58); d_(SNARE, 15, 62)
        if level >= 2:
            for tick in (0, 3, 5, 8, 11, 13):
                d_(HAT, tick, 34)
    elif feel == "HT":
        d_(BASEDRUM, 0, 100); d_(BASEDRUM, 6, 66); d_(BASEDRUM, 11, 62)
        d_(SNARE, 8, 100)
        if level >= 1:
            d_(HAT, 4, 40); d_(HAT, 12, 40)
    elif feel == "SB":
        d_(BASEDRUM, 0, 100); d_(SNARE, 0, 94)
    elif feel == "MR":
        for tick in range(0, 16, 2):
            d_(SNARE, tick, 34 + 3 * tick + (10 if tick % 4 == 0 else 0))
        d_(BASEDRUM, 0, 98); d_(BASEDRUM, 8, 94)
    if sixteenth and feel not in ("SB", "MR"):
        for tick in range(0, 16, 4):                             # hats on the beats only (under a sixteenth bass and a kick they are not heard)
            d_(HAT, tick, 38)
    if phase == 1 and feel != "SB":
        burst = hash_(bar, 9) % 3
        if burst == 0:
            for tick in (13, 14, 15):
                d_(BASEDRUM, tick, 62)
        elif burst == 1:
            for tick in (12, 13, 14, 15):
                d_(SNARE, tick, 50 + 8 * (tick - 12))
    if fill:
        for k in [k for k in ev if k[1] >= 10]:
            del ev[k]
        for count, tick in enumerate(range(10, 16)):
            d_(SNARE, tick, 44 + 9 * count)
    if accel:
        for k in [k for k in ev if k[1] >= 4 and k[0] in (SNARE, HAT)]:
            del ev[k]
        for index, tick in enumerate((4, 8, 10, 12, 13, 14, 15)):
            d_(SNARE, tick, 40 + 9 * index)
            if index % 2 == 0:
                d_(BASEDRUM, tick, 70 + 4 * index)
    if toms is not None:
        for k in [k for k in ev if k[1] >= toms]:
            del ev[k]
    for (drum, tick), vel in ev.items():
        role = {BASEDRUM: "kick", SNARE: "snare", HAT: "hat"}[drum]
        b.drum(role, t0 + tick, drum, vel * (quiet if quiet < 1 and drum != BASEDRUM else 1.0))
    if toms is not None:
        tom_roll(b, t0, toms, 16, ascending=toms_up)


def lead(b, t0, sp):
    unit, ub, phase, sec = sp["unit"], sp["ub"], sp["phase"], sp["sec"]
    plan, key, level, quiet = sp["plan"], sp["key"], sp["level"], sp["quiet"]
    chorus = sec == "CHORUS"
    seg = ub // 2
    code = plan["leads"][seg]
    inst = INST[code]
    if phase == 1:
        inst = {FLUTE: BIT, I_: BIT, XYLOPHONE: PLING}.get(inst, inst) if hash_(sp["bar"], 1) % 3 else inst
    if chorus:
        inst = FLUTE
    fix = major if sp["mode"] == "M" else (lambda s: s)
    events = tune_events(sp)
    if sec == "B":
        pos = (unit % 2) * 4 + ub
        tb, half = divmod(pos, 2)
        base_sp = dict(sp, ub=min(tb, 3), phase=0, feel="SW")
        notes = halved(tune_events(base_sp), half)
    else:
        if sec == "A":
            events = offbeat(events, sp)
        notes = [(t, s, 0) for t, s in events]
    target = TARGET_CH if chorus else TARGET
    n = place(inst, key, [s for _, s in tune_events(dict(sp, phase=0, feel="SW"))] + [s for t, s, *_ in notes], target)
    axis = AXIS + ((hash_(sp["bar"], 4) % 3) - 1 if phase == 1 else 0)
    mirror = plan["mirror"]
    play_lead, play_mirror = True, False
    if mirror and level >= 3:
        mode_m = mirror[1]
        if mode_m == "sim":
            play_mirror = ub == 3 if chorus else True
        elif mode_m == "echo":
            play_mirror = True
        elif mode_m == "alt":
            play_mirror = ub % 2 == 1
            play_lead = not play_mirror
        elif mode_m == "call":
            play_mirror = seg == 1
            play_lead = not play_mirror
    else:
        mode_m = None

    def voice(role, instrument, pitch, tick, length, vel):
        hold(b, role, instrument, t0 + tick, pitch, length if length else 1, vel, rise=6)

    for tick, s, length in notes:
        vel = min(100, (92 + (8 if tick % 8 == 0 else 0)) * quiet)
        if play_lead:
            voice("lead", inst, TONIC + key + s + n, tick, length, vel)
            if level >= 3 and sec != "A" and plan["leads"][seg] != "G" and (length < 5 or sec != "B") and tick % 8 == 0:
                high = inst in (FLUTE, XYLOPHONE)
                b.note("dbl", t0 + tick, I_ if high else FLUTE, TONIC + key + s + n + (-24 if inst == FLUTE else (-12 if high else 12)), vel * 0.55)
    if play_mirror:
        m_inst = INST[mirror[0]]
        if chorus and m_inst in (GUITAR, I_, XYLOPHONE, HARP):
            m_inst = PLING
        refl = [2 * axis - fix(s) for _, s in tune_events(dict(sp, phase=0, feel="SW"))]
        mn = place(m_inst, key, refl, {k: v - (12 if chorus else 6) for k, v in target.items()})
        delay = 4 if mode_m == "echo" else 0
        for tick, s, length in notes:
            if tick + delay < 16:
                vel = (84 + (8 if tick % 8 == 0 else 0)) * quiet * (0.85 if mode_m == "echo" else 1.0)
                voice("mirror", m_inst, TONIC + key + 2 * axis - s + mn, tick + delay, length, vel)
    if chorus and unit >= 23:                                     # the harp descant: the strong beats of the tune held
        starts = {0: (0,), 1: (0, 8), 2: (0, 8), 3: (0, 4, 8, 12)}[ub]
        full = tune_events(dict(sp, phase=0, feel="SW"))
        last = full[0][1]
        for i, tk in enumerate(starts):
            end = starts[i + 1] if i + 1 < len(starts) else 16
            inside = [s for t, s in full if tk <= t < min(tk + 4, end)]
            pick = inside[0] if inside else last
            last = pick
            hold(b, "dbl", HARP, t0 + tk, near((TONIC + key + pick) % 12, 56, HARP), end - tk, 92, rise=8)


def render(b, phase, t0, sp, next_pc, special=None):
    unit, ub, sec = sp["unit"], sp["ub"], sp["sec"]
    plan = sp["plan"]
    last = ub == 3
    fill = last and "accel" not in plan["tags"] and sp["level"] >= 1 and sp["feel"] != "SB" and sec != "OUTRO"
    accel = ("accel" in plan["tags"] and last and sp["feel"] != "SB") or special == "build"
    toms = None
    if sec == "OUTRO":
        toms = 10 if phase == 0 else 8
    if special == "open_last":
        toms = 6
    if special == "final":
        toms = 0
    rhythm(b, t0, sp, next_pc, fill, accel, toms, special == "final", chip=sec == "B")
    if ("hit" in plan["tags"] and ub == 0) or sp["feel"] == "SB":
        hit(b, t0, sp["chords"][0][0], sp["chords"][0][1], 0.7 if sp["quiet"] < 1 else 1.0)
    if sp["feel"] == "SB":
        laugh(b, t0 + 6, phase, near(sp["chords"][0][0], 88, FLUTE), 5)
    if sp["feel"] != "SB" and sp["level"] >= 1:
        lead(b, t0, sp)
    if phase == 1 and sp["level"] >= 3 and sec == "B" and sp["feel"] != "SB":
        for tick in (3, 7, 11, 15):
            pc, chord, off = sp["chords"][1] if len(sp["chords"]) > 1 and tick >= sp["chords"][1][2] else sp["chords"][0]
            tones = [pc + i for i in chord[1]]
            if hash_(sp["bar"], tick, 6) % 2:
                b.note("arp", t0 + tick, BIT, near(tones[(tick // 2) % 4], 66, BIT), 44 + hash_(sp["bar"], tick) % 14)


def play(phase):
    b = Builder(LENGTH, ROLES)
    # ---- the opening: the last phrase of the chorus (unit 26-ish, bars 2 and 3) twice, at full strength
    last_chorus_unit = 13 + 9
    for i in range(4):
        pb = 4 * last_chorus_unit + (2 + i % 2)
        sp = spec(phase, pb)
        sp = dict(sp, feel=("ST", "SW", "SW", "ST")[i], level=4, bar=900 + i)
        nxt = spec(phase, 4 * last_chorus_unit + 3)["chords"][0][0] if i < 3 else TONIC % 12
        render(b, phase, 16 * i, sp, nxt, special="open_last" if i == 3 else None)
        if i in (0, 2):
            hit(b, 16 * i, sp["chords"][0][0], sp["chords"][0][1])
    # ---- the 108 bars
    for bar in range(NBARS):
        t0 = START + bar * BAR_T
        sp = spec(phase, bar)
        if bar == BREAK_BAR:                                  # the break: drums and tune stop, ONE bell - and the bass stays (a total silence sounds as if the server stopped)
            b.note("bell", t0, BELL, 96, 90)
            root = bass_note(sp["chords"][0][0])
            for tick in (0, 4, 8, 12):
                b.note("bass", t0 + tick, BASS, root, 78)
                b.note("didg", t0 + tick, DIDGERIDOO, near(root % 12, root, DIDGERIDOO), 74)
            continue
        nxt = spec(phase, bar + 1)["chords"][0][0] if bar + 1 < NBARS else TONIC % 12
        special = "build" if bar == BREAK_BAR - 1 else ("final" if bar == NBARS - 1 else None)
        render(b, phase, t0, sp, nxt, special)
    return b


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    titles = ("The Cracked Mirror Laughs (Boss007)", "The Cracked Mirror Laughs, Shattered (Boss007)")
    descriptions = ("Mirror boss, phase 1, class 4, style 3 (symphonic open), 180 BPM: a palindrome motif played on every chord root, opening on the chorus's last phrase, sparse off-beat A, half-ed tune in B, a one-bar bell break, the chorus in A major on a sixteenth bass, tom-roll outro. Loops from tick 64.",
                    "Mirror boss, phase 2: the same 108 bars, chords, break and loop - the mirror cracked: the machine gun starts earlier, the feel changes every bar, the key every 2 bars, the tune in shards, chip arpeggios and glitch drums.")
    edges = [("opening", 0, START)]
    cursor = START
    for name, units in SECTIONS:
        edges.append((name, cursor, cursor + units * 4 * BAR_T))
        cursor += units * 4 * BAR_T
    for phase in range(2):
        b = play(phase)
        target = out / ("mirror_phase1.nbs" if phase == 0 else "mirror_phase2.nbs")
        layers = finish281(target, b, TPS, titles[phase], descriptions[phase])
        print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
        bass = [layer for layer in layers if layer.name.startswith("bass")]
        print("   bass notes per bar by section: " + ", ".join(f"{n} {sum(1 for layer in bass for t in layer.notes if s <= t < e) / ((e - s) / 16):.1f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
