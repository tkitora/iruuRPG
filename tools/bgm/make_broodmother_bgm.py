#!/usr/bin/env python3
"""Makes broodmother_phase1.nbs and broodmother_phase2.nbs: "Mother of a Thousand Legs (Boss006)" and "..., Swarming (Boss006)", the spider queen in two phases. Written from zero (t281).

    python3 tools/bgm/make_broodmother_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_broodmother_bgm.py outdir     writes them somewhere else

STYLE 3 of the style doc (symphonic open, 180 BPM) on the ladder of the BPM guide: 12 ticks a second, a tick is a sixteenth, a bar is 16 ticks (4 intro + 96 bars = 1664 ticks = 138.7 s). Key D.
THE LEGS: the whole piece walks in the gait 3 + 3 + 3 + 3 + 2 + 2 (ticks 0 3 6 9 12 14): six legs that never keep step. The motif is a crawl of six notes, one on every step of the gait,
D Eb F# Eb D A in D PHRYGIAN DOMINANT (D Eb F# G A Bb C: a flat second over a major third, the spider's venom); every bar it is raised one step of that scale, and the fourth bar climbs and hangs.
The bass skitters on the gait; the snare marches on its odd steps (3 and 9). Phase 1 is natural (harp, flute, guitar); phase 2 is a half step up (Eb), electric piano / 8-bit legs, the machine gun earlier.
Time table (a bar is 1.33 s):
  0:00  OPENING (4 bars): the last phrase of the chorus (D MAJOR) at full strength, sixteenth bass, tom rolls, hanging.
  0:05  A (28 bars, to 0:43): sparse - skittering bass on the gait, the kick and the marching snare; the crawl is moved one tick late (the off beat) and chatters.
  0:43  B (20 bars, to 1:07): the crawl HALF-ED (every note twice as long) while the drums keep their speed; the bass gets sixteenth double hits and runs up and down; the last bar is the BREAK (all drums
        and the bass muted, one bell).
  1:07  CHORUS (32 bars, to 1:50): the SAME-TONIC MAJOR flash (D phrygian dominant -> D major: E, B, C# for Eb, Bb, C): the crawl in the flute at the top of the range over a full SIXTEENTH bass;
        power chords on every beat; the descant of the harp in long notes from bar 16.
  1:50  OUTRO (16 bars): back to the venom; tom rolls fill every bar's end, the last bar rises into tick 64.
Nothing is random (a hash of the bar and the tick). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from boss281 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 180 / 15
BAR_T = 16
START = 64
UNITS = 24
NBARS = UNITS * 4
LENGTH = START + NBARS * BAR_T
GAIT = (0, 3, 6, 9, 12, 14)
PD = [0, 1, 4, 5, 7, 8, 10]             # D phrygian dominant
TONIC_PC = 2
TONIC = 62                              # D4
CELL = [0, 1, 2, 1, 0, 4]               # scale degrees on the six steps of the gait
CLIMB = [2, 3, 4, 5, 6, 9]


def deg(i):
    q, r = divmod(i, 7)
    return PD[r] + 12 * q


D5 = (0, (0, 4, 7, 10))
Eb = (1, (0, 4, 7, 11))
Bb = (8, (0, 4, 7, 11))
Cc = (10, (0, 4, 7, 10))
Gm = (5, (0, 3, 7, 10))
A7 = (7, (0, 4, 7, 10))
DM = (0, (0, 4, 7, 11))
Em = (2, (0, 3, 7, 10))
GM = (5, (0, 4, 7, 11))
Bm = (9, (0, 3, 7, 10))
PROG_V = {"UA": [D5, D5, Eb, A7], "UB": [D5, Bb, Cc, A7], "UC": [Eb, D5, Bb, A7], "UD": [D5, Gm, Bb, A7]}
PROG_MAJ = {"UA": [DM, Em, GM, A7], "UB": [DM, GM, A7, A7], "UC": [Em, DM, GM, A7], "UD": [DM, Bm, GM, A7]}
SHIFTS = {"UA": [0, 1, 2], "UB": [0, 2, 3], "UC": [1, 2, 4], "UD": [0, 1, 3]}


def U(prog, levels, mode="m", tags=""):
    return dict(prog=prog, lv=levels, mode=mode, tags=set(tags.split()))


PLAN = ([U("UA", (1, 2)), U("UB", (1, 2)), U("UA", (1, 2)), U("UC", (1, 2)), U("UB", (2, 3)), U("UD", (2, 3)), U("UC", (2, 3), tags="accel")]
        + [U("UA", (3, 4)), U("UB", (3, 4)), U("UC", (3, 4)), U("UD", (3, 4)), U("UA", (3, 4), tags="break")]
        + [U(p, (4, 4), "M") for p in ("UA", "UB", "UC", "UD", "UA", "UB", "UC", "UD")]
        + [U("UA", (3, 4)), U("UB", (3, 4)), U("UC", (3, 4)), U("UD", (3, 4))])
assert len(PLAN) == UNITS
SECTIONS = [("A", 7), ("B", 5), ("CHORUS", 8), ("OUTRO", 4)]
SEC = [name for name, n in SECTIONS for _ in range(n)]
BREAK_BAR = 4 * 12 - 1
LAST_CHORUS = 19
ROLES = {"lead": (100, 80), "dbl": (80, 90), "stab": (85, 100), "power": (90, 100), "power2": (80, 100), "chug": (70, 100), "bass": (100, 100), "didg": (100, 100), "chip": (60, 100),
         "kick": (100, 100), "tom": (100, 100), "snare": (95, 105), "hat": (55, 115), "bell": (70, 100)}


def maj(s):
    return s + 1 if s % 12 in (1, 8, 10) else s


def bass_note(pc, target=35):
    return near(pc, target, BASS)


def reach(root, iv):
    p = root + iv
    while p > 50:
        p -= 12
    return p


def spec(phase, bar):
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    sec = SEC[unit]
    table = PROG_MAJ if plan["mode"] == "M" else PROG_V
    chord = table[plan["prog"]][ub]
    shift = phase                                    # phase 2: a half step up
    return dict(bar=bar, unit=unit, ub=ub, plan=plan, sec=sec, chord=chord, level=plan["lv"][phase], mode=plan["mode"], phase=phase, shift=shift)


def tune(sp):
    """[(tick, semitones above the tonic)] of the crawl in this bar (cell on the gait, bar 3 the climb)."""
    ub, plan = sp["ub"], sp["plan"]
    degs = CLIMB if ub == 3 else [d + SHIFTS[plan["prog"]][ub] for d in CELL]
    notes = [(t, deg(d)) for t, d in zip(GAIT, degs)]
    if sp["mode"] == "M":
        notes = [(t, maj(s)) for t, s in notes]
    if sp["phase"] == 1 and sp["level"] >= 3:                       # the swarm: now and then a leg stutters (the note again one tick later)
        notes = notes + [(t + 1, s) for t, s in notes if t + 1 < 16 and t + 1 not in GAIT and hash_(sp["bar"], t, 5) % 5 == 0]
        notes.sort()
    return notes


def bass_ticks(level, seed):
    if level <= 0:
        return [0, 4, 8, 12]
    if level == 1:
        return list(GAIT)
    if level == 2:
        return [0, 2, 3, 6, 8, 9, 12, 14]
    if level == 3:
        extra = [t + 1 for t in GAIT if t + 1 not in GAIT and hash_(seed, t, 3) % 3]
        return sorted(set(GAIT) | set(extra) | {1, 4, 7, 10, 13, 15} if hash_(seed, 1) % 2 else set(GAIT) | set(extra) | {4, 10, 15})
    return list(range(16))


def play_bar(b, t0, sp, next_pc, fill=False, accel=False, toms=None, toms_up=False):
    level, phase, bar = sp["level"], sp["phase"], sp["bar"]
    shift = sp["shift"]
    sec = sp["sec"]
    pc = (TONIC_PC + shift + sp["chord"][0]) % 12
    iv = sp["chord"][1]
    root = bass_note(pc)
    appr = bass_note((next_pc + (1 if bar % 2 else -1)) % 12, root)
    ticks = bass_ticks(level, bar)
    contour = (0, 2, 0, 1, 2, 3, 2, 0) if level == 3 else (0, 0, 2, 0, 0, 2, 0, 0)
    for index, tick in enumerate(ticks):
        if level >= 4:
            pick = 0 if tick not in GAIT else (0, 2, 0, 1, 2, 3)[GAIT.index(tick)]
            p = reach(root, iv[pick])
            if tick not in GAIT and tick % 4 == 3 and p + 12 <= 46 and (tick + bar) % 2 == 0:
                p += 12
        elif level == 3:
            p = reach(root, iv[contour[index % 8]])
        elif level == 2:
            p = reach(root, iv[2 if index % 4 == 2 else 0])
        else:
            p = root
        if level <= 3 and tick == ticks[-1] and bar % 2:
            p = appr
        vel = (100 if tick in GAIT else 58) if level >= 3 else (100 if tick in (0, 6, 12) else 84)
        if level == 0:
            vel = 70
        b.note("bass", t0 + tick, BASS, p, vel)
        if tick in (0, 6, 12) or (level >= 4 and tick in (3, 9)):
            b.note("didg", t0 + tick, DIDGERIDOO, near(p % 12, p, DIDGERIDOO), 96 if tick == 0 else 82)
        if level == 3 and tick in (3, 9, 14) and sec != "A":
            b.note("chug", t0 + tick, GUITAR, near(p % 12, 47, GUITAR), 70)
    # the comp: a third and a seventh on the odd legs
    if level in (1, 2, 3) and (sec != "A" or bar % 2 == 0):
        inst = PLING if phase == 1 else GUITAR
        for tick in ((3, 9) if level < 4 else (3,)):
            b.note("stab", t0 + tick, inst, near(pc + iv[1], 60 if inst == PLING else 55, inst), 66)
            b.note("stab", t0 + tick, inst, near(pc + iv[3], 56 if inst == PLING else 52, inst), 66)
    if sp["sec"] in ("CHORUS", "OUTRO") or (sp["sec"] == "B" and level >= 4):
        for tick in (0, 6, 12):
            b.note("power", t0 + tick, GUITAR, near(pc, 47, GUITAR), 98, once=True)
    # drums: the kick on the gait's even steps, the snare marching on its odd steps
    ev = {}

    def d_(drum, tick, vel):
        ev[(drum, tick)] = max(ev.get((drum, tick), 0), vel)

    for tick in (0, 6, 12):
        d_(BASEDRUM, tick, 100 if tick == 0 else 82)
    for tick in (3, 9):
        d_(SNARE, tick, 90)
    if level >= 2:
        d_(BASEDRUM, 8, 66); d_(SNARE, 14, 70)
    if level >= 3:
        d_(SNARE, 15, 44); d_(BASEDRUM, 4, 60)
    hat_ticks = {1: (), 2: GAIT, 3: GAIT, 4: (3, 9, 14)}.get(level, ())
    if sec == "A":
        hat_ticks = ()
    for tick in hat_ticks:
        d_(HAT, tick, 38)
    if phase == 1 and level < 4 and hash_(bar, 9) % 3 == 0:
        for tick in (13, 14, 15):
            d_(SNARE, tick, 54 + 6 * (tick - 13))
    if fill:
        for k in [k for k in ev if k[1] >= 10]:
            del ev[k]
        for count, tick in enumerate(range(10, 16)):
            d_(SNARE, tick, 44 + 9 * count)
    if accel:
        for k in [k for k in ev if k[1] >= 4 and k[0] in (SNARE, HAT)]:
            del ev[k]
        for index, tick in enumerate((8, 10, 12, 13, 14, 15)):
            d_(SNARE, tick, 44 + 9 * index)
    if toms is not None:
        for k in [k for k in ev if k[1] >= toms]:
            del ev[k]
    for (drum, tick), vel in ev.items():
        role = {BASEDRUM: "kick", SNARE: "snare", HAT: "hat"}[drum]
        b.drum(role, t0 + tick, drum, vel)
    if toms is not None:
        tom_roll(b, t0, toms, 16, ascending=toms_up)


def hit(b, t0, sp):
    pc = (TONIC_PC + sp["shift"] + sp["chord"][0]) % 12
    b.note("bass", t0, BASS, bass_note(pc), 100, once=True)
    b.note("didg", t0, DIDGERIDOO, near(pc, 33, DIDGERIDOO), 100, once=True)
    b.note("power", t0, GUITAR, near(pc, 47, GUITAR), 100, once=True)
    b.note("power2", t0, GUITAR, near(pc + 7, 52, GUITAR), 90, once=True)
    b.note("stab", t0, HARP, near(pc + sp["chord"][1][1], 62, HARP), 92, once=True)
    b.drum("kick", t0, BASEDRUM, 100, once=True)
    b.drum("snare", t0, SNARE, 94, once=True)


def lead(b, t0, sp):
    phase, sec, ub, unit = sp["phase"], sp["sec"], sp["ub"], sp["unit"]
    shift = sp["shift"]
    base = tune(sp)
    chorus = sec == "CHORUS"
    inst = FLUTE if chorus else (HARP if phase == 0 else PLING)
    if sec == "A":                                                   # the crawl a tick late (the off beat), chattering
        notes = [(min(15, t + 1), s, 0) for t, s in base]
        notes += [(t + 1, s, 0) for t, s, _ in list(notes) if t + 1 < 16 and t + 1 not in {x for x, _, _ in notes} and hash_(sp["bar"], t, 17) % 2]
    elif sec == "B":
        pos = (unit % 2) * 4 + ub
        tb, half = divmod(pos, 2)
        ref = tune(dict(sp, ub=min(tb, 3), phase=0))
        stretched = [(2 * t, s) for t, s in ref]
        notes = []
        for i, (t, s) in enumerate(stretched):
            end = stretched[i + 1][0] if i + 1 < len(stretched) else 32
            if 16 * half <= t < 16 * half + 16:
                notes.append((t - 16 * half, s, end - t))
    else:
        notes = [(t, s, 0) for t, s in base]
    semis = [s for _, s, *_ in notes]
    low, high = RANGE[inst]
    target = 88 if chorus else 64
    best = None
    for n in range(-36, 37, 12):
        pitches = [TONIC + shift + s + n for s in semis]
        cost = sum(max(0, p - high) + max(0, low - p) for p in pitches) * 100 + abs(sum(pitches) / len(pitches) - target)
        if best is None or cost < best[0]:
            best = (cost, n)
    n = best[1]
    for tick, s, length in notes:
        vel = 100 if tick in GAIT or length else 92
        pitch = TONIC + shift + s + n
        hold(b, "lead", inst, t0 + tick, pitch, length if length else 1, vel, rise=6)
        if sec in ("B", "CHORUS", "OUTRO") and tick in (0, 6, 12):
            dbl_inst = GUITAR if phase == 0 else BIT
            dp = pitch - 12 if not chorus else pitch - 24
            b.note("dbl", t0 + tick, dbl_inst, dp, 80, once=True)
    if chorus and unit >= LAST_CHORUS - 3:                          # the harp descant: the strong beats held
        starts = (0, 8) if ub != 3 else (0, 4, 8, 12)
        for i, tk in enumerate(starts):
            end = starts[i + 1] if i + 1 < len(starts) else 16
            inside = [s for t, s, *_ in notes if tk <= t < min(tk + 4, end)]
            s = inside[0] if inside else notes[0][1]
            hold(b, "dbl", HARP, t0 + tk, near((TONIC + shift + s) % 12, 62, HARP), end - tk, 92, rise=8)


def render(b, t0, sp, next_pc, special=None):
    plan = sp["plan"]
    ub, sec = sp["ub"], sp["sec"]
    last = ub == 3
    fill = last and "accel" not in plan["tags"] and sec in ("A", "B", "CHORUS") and not (sec == "B" and sp["unit"] == 16)
    accel = ("accel" in plan["tags"] and last) or special == "build"
    toms = None
    if sec == "OUTRO":
        toms = 10 if sp["phase"] == 0 else 8
    if special == "open_last":
        toms = 6
    if special == "final":
        toms = 0
    play_bar(b, t0, sp, next_pc, fill, accel, toms, special == "final")
    if (ub in (0, 2) and sec in ("B", "CHORUS", "OUTRO")) or (ub == 0 and sec == "A") or special == "open_first":
        hit(b, t0, sp)
    if sp["level"] >= 1:
        lead(b, t0, sp)
    if sp["phase"] == 1 and sec == "B":
        for tick in GAIT:
            if hash_(sp["bar"], tick, 6) % 2:
                pc = (TONIC_PC + 1 + sp["chord"][0]) % 12
                b.note("chip", t0 + tick, BIT, near(pc + sp["chord"][1][(tick // 3) % 4], 70, BIT), 50)


def play(phase):
    b = Builder(LENGTH, ROLES)
    for i in range(4):                                               # the opening: bars 2 and 3 of the last chorus unit, twice
        sp = dict(spec(phase, 4 * LAST_CHORUS + 2 + i % 2), level=4, bar=900 + i)
        nxt = (TONIC_PC + phase + spec(phase, 4 * LAST_CHORUS + 3)["chord"][0]) % 12 if i < 3 else (TONIC_PC + phase) % 12
        render(b, 16 * i, sp, nxt, "open_last" if i == 3 else ("open_first" if i in (0, 2) else None))
    for bar in range(NBARS):
        t0 = START + bar * BAR_T
        sp = spec(phase, bar)
        if bar == BREAK_BAR:                                  # the break: drums and tune stop, ONE bell - and the bass stays (a total silence sounds as if the server stopped)
            b.note("bell", t0, BELL, 96, 90)
            root = bass_note((TONIC_PC + phase + sp["chord"][0]) % 12)
            for tick in (0, 4, 8, 12):
                b.note("bass", t0 + tick, BASS, root, 78)
                b.note("didg", t0 + tick, DIDGERIDOO, near(root % 12, root, DIDGERIDOO), 74)
            continue
        nxt = (TONIC_PC + phase + spec(phase, bar + 1)["chord"][0]) % 12 if bar + 1 < NBARS else (TONIC_PC + phase) % 12
        special = "build" if bar == BREAK_BAR - 1 else ("final" if bar == NBARS - 1 else None)
        render(b, t0, sp, nxt, special)
    return b


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    titles = ("Mother of a Thousand Legs (Boss006)", "Mother of a Thousand Legs, Swarming (Boss006)")
    descs = ("Spider queen, phase 1 (natural): style 3, 180 BPM, the gait 3+3+3+3+2+2, a six-note crawl in D phrygian dominant raised a step each bar, sparse off-beat A, half-ed B with a bell break, the chorus a flash of D major over a sixteenth bass, tom-roll outro. Loops from tick 64.",
             "Spider queen, phase 2 (a half step up, electronic): the same bars and chords, electric piano and 8-bit legs, the stuttering swarm, the machine gun earlier.")
    edges = [("opening", 0, START)]
    cursor = START
    for name, units in SECTIONS:
        edges.append((name, cursor, cursor + units * 4 * BAR_T))
        cursor += units * 4 * BAR_T
    for phase in range(2):
        b = play(phase)
        target = out / ("broodmother_phase1.nbs" if phase == 0 else "broodmother_phase2.nbs")
        layers = finish281(target, b, TPS, titles[phase], descs[phase])
        print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
        bass = [layer for layer in layers if layer.name.startswith("bass")]
        print("   bass notes per bar by section: " + ", ".join(f"{n} {sum(1 for layer in bass for t in layer.notes if s <= t < e) / ((e - s) / 16):.1f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
