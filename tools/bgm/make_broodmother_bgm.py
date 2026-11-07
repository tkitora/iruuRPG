#!/usr/bin/env python3
"""Makes broodmother_phase1.nbs and broodmother_phase2.nbs: "Mother of a Thousand Legs (Boss006)" and "..., Swarming (Boss006)", the spider queen in two phases. Built again from zero (t290).

    python3 tools/bgm/make_broodmother_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_broodmother_bgm.py outdir     writes them somewhere else

STYLE 3 of the style doc (symphonic open, 180 BPM) built so that tools/bgm/guidecheck.py finds the guide in the notes. 12 ticks a second (a tick is a sixteenth), 16-tick bars, 4 opening bars + 102 bars
= 1696 ticks = 141.3 s. Key D.
THE LEGS: the whole piece walks in the gait 3 + 3 + 3 + 3 + 2 + 2 (ticks 0 3 6 9 12 14): six legs that never keep step. The motif is a crawl of six notes, one on every step of the gait, that wobbles a
half step around the root and then leaps to the fifth, D Eb D C# D A, raised to the next root of the chord every bar; the fourth bar climbs through D phrygian dominant (D Eb F# G A Bb C: a flat second
over a major third, the venom) and hangs on the flat ninth. The bass skitters on the gait and the snare marches on its odd steps (3 and 9).
Time table (a bar is 1.33 s; the guide's style 3 table):
  0:00  OPENING (4 bars): the last phrase of the chorus (G - A7, D MAJOR) at full strength - the crawl in long notes, ensemble hits, a tom fill - over QUARTER-note bass.
  0:05  A (34 bars, to 0:51): small - the bass on the gait in EIGHTHS (8 hits a bar), kick and the marching snare; the crawl a tick late and busy; two bars in which the melody is taken away.
  0:51  B (20 bars, to 1:17): the crawl HALF-ED (every note twice as long) while the drums stay and grow; the bass mixes sixteenth double hits into the gait; the last bar is the BREAK: drums and tune stop,
        ONE bell, the bass keeps a pedal.
  1:17  CHORUS (32 bars, to 2:00): the SAME-TONIC MAJOR flash (D phrygian dominant -> D major: E, B, C# for Eb, Bb, C): the crawl in long notes (one voice, struck again every beat) over a full SIXTEENTH
        bass and drums, power chords on the beats; one bar without melody in the middle.
  2:00  OUTRO (16 bars): back to the venom; tom rolls fill every bar's end, the last bar rises into tick 64.
Phase 2 (Swarming): the same bars, chords, break and loop, a half step higher (Eb); electric piano and 8-bit legs, key jolts, glitch drums, more double hits.
Nothing is random (a hash of the bar and the tick). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from boss281 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 180 / 15
BAR_T = 16
START = 64
TONIC = 62                      # D4 (the pitch class D = 2)
GAIT = (0, 3, 6, 9, 12, 14)
A8 = (0, 2, 3, 6, 8, 9, 12, 14)                 # the gait in eighths: 8 hits a bar

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
PROG_M = {"UA": [D5, D5, Eb, A7], "UB": [D5, Bb, Cc, A7], "UC": [Eb, D5, Bb, A7], "UD": [D5, Gm, Bb, A7]}
PROG_MAJ = {"UA": [DM, Em, GM, A7], "UB": [DM, GM, A7, A7], "UC": [Em, DM, GM, A7], "UD": [DM, Bm, GM, A7]}
SHIFTS = {"UA": [0, 1, 5], "UB": [0, 8, 10], "UC": [1, 0, 8], "UD": [0, 5, 8]}
CELL_A = [0, 1, 0, -1, 0, 7]
CELL_B = [0, 4, 1, 5, 4, 7]
CLIMB = [4, 5, 7, 8, 10, 13]
KEYJOLT = [1, 6, 3, 7, 2, 5]


def U(prog, cell, key, tags="", mode="m", bars=4):
    return dict(prog=prog, cell=cell, key=key, tags=set(tags.split()), mode=mode, bars=bars)


A_UNITS = [U("UA", "A", 0), U("UB", "A", 0), U("UA", "B", 0, "rest2"), U("UC", "A", 0), U("UB", "B", 0), U("UD", "A", 0, "rest1"), U("UC", "B", 0), U("UD", "A", 0), U("UA", "A", 0, bars=2)]
B_UNITS = [U("UA", "A", 0), U("UB", "A", 0), U("UC", "B", 0), U("UD", "A", 0), U("UA", "A", 0, "break")]
C_UNITS = [U("UA", "A", 0, mode="M"), U("UB", "B", 0, mode="M"), U("UC", "A", 0, mode="M"), U("UD", "B", 0, "rest2", mode="M"), U("UA", "A", 0, mode="M"), U("UB", "B", 0, mode="M"),
           U("UC", "A", 0, mode="M"), U("UD", "B", 0, mode="M")]
O_UNITS = [U("UA", "A", 0), U("UB", "B", 0, "rest2"), U("UC", "A", 0), U("UD", "A", 0)]
SECTIONS = [("A", A_UNITS), ("B", B_UNITS), ("CHORUS", C_UNITS), ("OUTRO", O_UNITS)]
BARS = []
for name, units in SECTIONS:
    for ui, unit in enumerate(units):
        for ub in range(unit["bars"]):
            BARS.append(dict(sec=name, unit=unit, ub=ub, ui=ui))
NBARS = len(BARS)
LENGTH = START + NBARS * BAR_T
BREAK_BAR = next(i for i, b in enumerate(BARS) if b["sec"] == "B" and "break" in b["unit"]["tags"] and b["ub"] == 3)
OPEN_UNIT = U("UB", "A", 0, mode="M")                 # the opening: the last phrase of the chorus (G - A7)
ROLES = {"lead": (100, 80), "mirror": (88, 120), "dbl": (70, 90), "power": (90, 100), "power2": (80, 100), "bass": (100, 100), "didg": (100, 100), "kick": (100, 100), "tom": (100, 100),
         "snare": (95, 105), "hat": (55, 115), "bell": (70, 100)}


def maj(s):
    return s + 1 if s % 12 in (1, 8, 10) else s


def bass_note(pc, target=35):
    return near(pc, target, BASS)


def reach(root, iv):
    p = root + iv
    while p > 50:
        p -= 12
    return p


def spec(phase, bar, override=None):
    info = BARS[bar] if override is None else override
    unit, ub, sec = info["unit"], info["ub"], info["sec"]
    key = unit["key"] + phase
    if phase == 1 and sec in ("A", "B") and ub >= 2 and unit["bars"] == 4:
        key = (key + KEYJOLT[(info["ui"] * 5 + 1) % 6]) % 12
    table = PROG_MAJ if unit["mode"] == "M" else PROG_M
    chord = table[unit["prog"]][ub]
    chords = [((TONIC + key + chord[0]) % 12, chord, 0)]
    level = {"A": 1, "B": 3, "CHORUS": 4, "OUTRO": 3}[sec]
    return dict(bar=bar, unit=unit, ub=ub, ui=info["ui"], sec=sec, key=key, chords=chords, level=level, mode=unit["mode"], phase=phase)


def tune_events(sp):
    """[(tick, semitones above the tonic)]: the cell on the six steps of the gait (bar 3 the climb)."""
    unit, ub = sp["unit"], sp["ub"]
    if ub == 3:
        offs = CLIMB
    else:
        offs = [s + SHIFTS[unit["prog"]][ub] for s in (CELL_A if unit["cell"] == "A" else CELL_B)]
    events = list(zip(GAIT, offs))
    if sp["mode"] == "M":
        events = [(t, maj(s)) for t, s in events]
    return events


def halved(events, half):
    stretched = [(2 * t, s) for t, s in events]
    out = []
    for i, (t, s) in enumerate(stretched):
        end = stretched[i + 1][0] if i + 1 < len(stretched) else 32
        if 16 * half <= t < 16 * half + 16:
            out.append((t - 16 * half, s, end - t))
    return out


def long_notes(sp):
    ub = sp["ub"]
    starts = {0: (0,), 1: (0, 8), 2: (0, 8), 3: (0, 4, 8, 12)}[ub]
    events = tune_events(sp)
    out, last = [], events[0][1]
    for i, tk in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else 16
        inside = [s for t, s in events if tk <= t < min(tk + 4, end)]
        pick = inside[0] if inside else last
        last = pick
        out.append((tk, pick, end - tk))
    return out


def lead_voice(b, t0, sp):
    """Plays the crawl and returns the ticks at which a tune voice strikes (the guitar goes only there)."""
    ub, sec, phase = sp["ub"], sp["sec"], sp["phase"]
    long_mode = sec == "CHORUS" or sp.get("opening")
    inst = FLUTE if long_mode else (HARP if phase == 0 else PLING)
    events = tune_events(sp)
    if sec == "B":
        pos = (sp["ui"] % 2) * 4 + ub
        tb, half = divmod(pos, 2)
        notes = halved(tune_events(dict(sp, ub=min(tb, 3))), half)
    elif long_mode:
        notes = long_notes(sp)
    elif sec == "A":                                                   # a tick late (the off beat); the stuttering legs repeat a note
        notes = [(min(15, t + 1), s, 0) for t, s in events]
        notes += [(t + 2, s + 1, 0) for t, s in events if t + 2 < 15]                 # the legs scuttle: a half step above, a tick after
        notes.sort()
    else:
        notes = [(t, s, 0) for t, s in events]
    low, high = RANGE[inst]
    target = 86 if inst == FLUTE else 63
    semis = [s for _, s in events]
    best = None
    for n in range(-36, 37, 12):
        pitches = [TONIC + sp["key"] + s + n for s in semis]
        cost = sum(max(0, p - high) + max(0, low - p) for p in pitches) * 100 + abs(sum(pitches) / len(pitches) - target)
        if best is None or cost < best[0]:
            best = (cost, n)
    n = best[1]
    ticks = set()
    for tick, s, length in notes:
        vel = 100 if tick in GAIT or length else 93
        pitch = TONIC + sp["key"] + s + n
        hold(b, "lead", inst, t0 + tick, pitch, length if length else 1, vel, rise=6)
        for t in range(tick, tick + max(1, length), 4):
            ticks.add(t)
        ticks.add(tick)
    if sec == "OUTRO" or sp.get("opening"):                            # the second voice: the crawl turned upside down (reflected about the fifth)
        m_inst = HARP if long_mode else (BANJO if phase == 0 else BIT)
        for tick, s, length in notes:
            if sec == "OUTRO" and tick + 4 > 15:
                continue
            p = TONIC + sp["key"] + 14 - s + n - 12
            hold(b, "mirror", m_inst, t0 + tick + (4 if sec == "OUTRO" else 0), p, length if length else 1, 86, rise=6)
    if long_mode:
        for tick, s, length in notes:
            hold(b, "dbl", HARP, t0 + tick, near((TONIC + sp["key"] + s) % 12, 58, HARP), length if length else 1, 90, rise=6)
    return ticks


def rest_bar(b, t0, sp):
    """Melody taken away: the hits and the marching snare, the bass keeps skittering on the gait."""
    root = bass_note(sp["chords"][0][0])
    b.drum("kick", t0, BASEDRUM, 100, once=True)
    b.drum("snare", t0, SNARE, 94, once=True)
    for tick in GAIT:
        b.note("bass", t0 + tick, BASS, root, 100 if tick == 0 else 76)
        if tick in (0, 6, 12):
            b.note("didg", t0 + tick, DIDGERIDOO, near(root % 12, root, DIDGERIDOO), 90 if tick == 0 else 76)
    for tick in (3, 9):
        b.drum("snare", t0 + tick, SNARE, 80)


def bass_hits(level, bar):
    if level <= 0:
        return [0, 4, 8, 12]
    if level == 1:
        return list(A8)
    if level == 3:
        extra = [t + 1 for t in GAIT if t + 1 not in GAIT and hash_(bar, t, 3) % 2]
        return sorted(set(GAIT) | set(extra) | ({4, 10, 15} if hash_(bar, 1) % 2 else {1, 7, 13}))[:11]
    return list(range(16))


def rhythm(b, t0, sp, next_pc, ltick, fill=False, accel=False, toms=None, toms_up=False, open_level=None):
    phase, bar, sec = sp["phase"], sp["bar"], sp["sec"]
    level = sp["level"] if open_level is None else open_level
    pc, chord, _ = sp["chords"][0]
    root = bass_note(pc)
    appr = bass_note((next_pc + (1 if bar % 2 else -1)) % 12, root)
    ticks = bass_hits(level, bar)
    if phase == 1 and level == 3:
        ticks = sorted(set(ticks) | {t + 1 for t in GAIT if t + 1 < 16 and (t + bar) % 5 == 0})[:12]
    for index, tick in enumerate(ticks):
        if level >= 4:
            pick = (0, 2, 0, 1, 2, 3)[GAIT.index(tick)] if tick in GAIT else 0
            p = reach(root, chord[1][pick])
            if tick not in GAIT and tick % 4 == 3 and p + 12 <= 46 and (tick + bar) % 2 == 0:
                p += 12
        elif level == 3:
            p = reach(root, chord[1][(0, 2, 0, 1, 2, 3, 2, 0)[index % 8]])
        elif level == 1:
            p = reach(root, chord[1][2 if tick in (6, 12) and bar % 4 == 3 else 0])
        else:
            p = root
        if level <= 3 and level > 0 and tick == ticks[-1] and bar % 2:
            p = appr
        vel = (100 if tick in GAIT else 60) if level >= 3 else (100 if tick in (0, 6, 12) else 84)
        if level == 0:
            vel = 90 if tick % 8 == 0 else 78
        b.note("bass", t0 + tick, BASS, p, vel)
        if tick in (0, 6, 12) or level == 0 and tick % 8 == 0:
            b.note("didg", t0 + tick, DIDGERIDOO, near(p % 12, p, DIDGERIDOO), 96 if tick == 0 else 82)
    if sec in ("CHORUS",) or sp.get("opening"):
        for tick in (0, 4, 8, 12):
            if tick in ltick:
                b.note("power", t0 + tick, GUITAR, near(pc, 47, GUITAR), 98, once=True)
    ev = {}

    def d_(drum, tick, vel):
        ev[(drum, tick)] = max(ev.get((drum, tick), 0), vel)

    for tick in (0, 6, 12):
        d_(BASEDRUM, tick, 100 if tick == 0 else 84)
    for tick in (3, 9):
        d_(SNARE, tick, 92)
    if level >= 3 and level < 4:
        d_(BASEDRUM, 8, 66); d_(SNARE, 14, 72); d_(SNARE, 15, 44)
        for tick in (3, 9, 14):
            d_(HAT, tick, 38)
    if level == 4:
        d_(SNARE, 14, 70)
        for tick in (3, 9, 14):
            d_(HAT, tick, 36)
    if level == 0:
        ev.clear()
        for tick in (0, 4, 8, 12):
            d_(BASEDRUM, tick, 100 if tick % 8 == 0 else 80)
        d_(SNARE, 8, 96); d_(SNARE, 14, 70); d_(SNARE, 15, 80)
    if phase == 1 and level in (1, 3) and hash_(bar, 9) % 3 == 0:
        for tick in (13, 14, 15):
            d_(SNARE, tick, 54 + 6 * (tick - 13))
    if fill:
        for k in [k for k in ev if k[1] >= 10]:
            del ev[k]
        for count, tick in enumerate(range(10, 16)):
            d_(SNARE, tick, 44 + 9 * count)
    if accel:
        for k in [k for k in ev if k[1] >= 8]:
            del ev[k]
        for index, tick in enumerate((8, 10, 12, 13, 14, 15)):
            d_(SNARE, tick, 44 + 9 * index)
    if toms is not None:
        for k in [k for k in ev if k[1] >= toms]:
            del ev[k]
    for (drum, tick), vel in ev.items():
        b.drum({BASEDRUM: "kick", SNARE: "snare", HAT: "hat"}[drum], t0 + tick, drum, vel)
    if toms is not None:
        tom_roll(b, t0, toms, 16, ascending=toms_up)


def ensemble(b, t0, sp, with_tune):
    pc = sp["chords"][0][0]
    low = bass_note(pc)
    b.note("bass", t0, BASS, low, 100, once=True)
    b.note("didg", t0, DIDGERIDOO, near(pc, 33, DIDGERIDOO), 100, once=True)
    b.drum("kick", t0, BASEDRUM, 100, once=True)
    b.drum("snare", t0, SNARE, 94, once=True)
    if with_tune:
        b.note("power", t0, GUITAR, near(pc, 47, GUITAR), 100, once=True)
        b.note("power2", t0, GUITAR, near(pc + 7, 52, GUITAR), 90, once=True)


def render(b, t0, sp, next_pc, special=None):
    unit, ub, sec = sp["unit"], sp["ub"], sp["sec"]
    last = ub == unit["bars"] - 1
    if ("rest1" in unit["tags"] and ub == 1) or ("rest2" in unit["tags"] and ub == 2):
        rest_bar(b, t0, sp)
        return
    fill = last and sec in ("A", "B", "CHORUS") and special is None and unit["bars"] == 4
    accel = special == "build"
    toms = None
    if sec == "OUTRO":
        toms = 10 if sp["phase"] == 0 else 8
    if special == "open_last":
        toms = 6
    if special == "final":
        toms = 0
    ltick = lead_voice(b, t0, sp)
    rhythm(b, t0, sp, next_pc, ltick, fill, accel, toms, special == "final", open_level=0 if sp.get("opening") else None)
    if sec == "A":
        ensemble(b, t0, sp, True)                                       # the small A keeps a hit on every bar line (no long-note rule here)
        if ub % 2:
            ensemble(b, t0 + 8, sp, True)
    elif sec == "OUTRO" or sp.get("opening"):
        for tick in (0, 8):
            ensemble(b, t0 + tick, sp, True)
    else:
        for tick in (0, 8):
            if tick in ltick:
                ensemble(b, t0 + tick, sp, True)


def play(phase):
    b = Builder(LENGTH, ROLES)
    for i in range(4):                                                  # the opening: G - A7 (bars 1 and 2 of the chorus phrase UB), twice
        info = dict(sec="CHORUS", unit=OPEN_UNIT, ub=1 + i % 2, ui=0)
        sp = dict(spec(phase, 0, info), opening=True, bar=900 + i, mode="M")
        render(b, 16 * i, sp, (TONIC + phase + 7) % 12, special="open_last" if i == 3 else None)
    for bar in range(NBARS):
        t0 = START + bar * BAR_T
        sp = spec(phase, bar)
        if bar == BREAK_BAR:
            b.note("bell", t0, BELL, 96, 90)
            root = bass_note(sp["chords"][0][0])
            for tick in (0, 4, 8, 12):
                b.note("bass", t0 + tick, BASS, root, 78)
                b.note("didg", t0 + tick, DIDGERIDOO, near(root % 12, root, DIDGERIDOO), 74)
            continue
        nxt = spec(phase, bar + 1)["chords"][0][0] if bar + 1 < NBARS else (TONIC + phase) % 12
        special = "build" if bar == BREAK_BAR - 1 else ("final" if bar == NBARS - 1 else None)
        render(b, t0, sp, nxt, special)
    return b


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    titles = ("Mother of a Thousand Legs (Boss006)", "Mother of a Thousand Legs, Swarming (Boss006)")
    descs = ("Spider queen, phase 1 (natural): style 3, 180 BPM, the gait 3+3+3+3+2+2, a six-note crawl in D phrygian dominant raised to each chord root, small off-beat A, half-ed B with a one-bell break, the chorus a flash of D major in long notes over a sixteenth bass, tom-roll outro. Loops from tick 64.",
             "Spider queen, phase 2 (a half step up, electronic): the same bars, chords, break and loop - electric piano and 8-bit legs, key jolts, glitch drums, more double hits.")
    edges = [("opening", 0, START)]
    cursor = START
    for name, units in SECTIONS:
        n = sum(u["bars"] for u in units)
        edges.append((name, cursor, cursor + n * BAR_T))
        cursor += n * BAR_T
    for phase in range(2):
        b = play(phase)
        target = out / ("broodmother_phase1.nbs" if phase == 0 else "broodmother_phase2.nbs")
        layers = finish281(target, b, TPS, titles[phase], descs[phase])
        print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
        print("   section starts (s): " + ", ".join(f"{n} {s / TPS:.0f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
