#!/usr/bin/env python3
"""Makes mirror_phase1.nbs and mirror_phase2.nbs: "The Cracked Mirror Laughs (Boss007)" and "..., Shattered (Boss007)" - class 4, the carnival of madness (a trickster). Built again from zero (t290).

    python3 tools/bgm/make_mirror_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_mirror_bgm.py outdir     writes them somewhere else

STYLE 3 of the style doc (symphonic open, 180 BPM) built so that tools/bgm/guidecheck.py finds the guide in the notes. 12 ticks a second (a tick is a sixteenth), 16-tick bars, 4 opening bars + 108 =
1792 ticks = 149.3 s. Key G.
THE MIRROR LAUGHS: the motif is four notes that go wrong, G Bb D Db (the fifth is struck and then bent a half step down: a laugh that cracks), played at the root of the chord and again a step higher in
the second half of the bar; the fourth bar of every phrase climbs and hangs. A second voice plays the same tune turned upside down (every interval reflected about D). Swing = the off eighth on the third
tick of the beat; a triplet figure = ticks 0 3 5; the feel changes bar by bar (SW swing, ST polka, TR triplet figure, HT half time, MR march roll), the key jolts, the lead instrument swaps every 2 bars.
Time table (a bar is 1.33 s; the guide's style 3 table):
  0:00  OPENING (4 bars): the last phrase of the chorus (C - D7, G MAJOR) at full strength - the tune in long notes, ensemble hits, a tom fill - over QUARTER-note bass (the ladder starts at quarters).
  0:05  A (34 bars, to 0:51): small - only the bass (swung EIGHTHS), kick and snare; the tune on the off beats and busy; two bars in which the melody is taken away (stop-time hits, the bass stays).
  0:51  B (20 bars, to 1:17): the tune HALF-ED (every note twice as long) while the drums stay and grow; the bass mixes sixteenth double hits and triplet figures into the eighths; the last bar is the BREAK:
        every drum and the tune stop, ONE bell, the bass keeps a pedal.
  1:17  CHORUS (38 bars, to 2:08): the SAME-TONIC MAJOR (G minor -> G major: B, E, F# for Bb, Eb, F): the tune in LONG notes (halves and wholes struck again every beat, one voice only) over a full SIXTEENTH
        bass and drums; power chords on the beats; a bar without melody in the middle (melody taken away, the bass stays).
  2:08  OUTRO (16 bars): back to minor; tom rolls (the bass drum on falling pitches) fill every bar's end, the last bar a rising roll into tick 64.
Phase 2 (Shattered): the same bars, chords, break and loop, a half step higher; the lead goes electronic (8-bit, pling), the key jolts every 2 bars in A and B, the drums glitch, the bass gets more double hits.
Nothing is random (a hash of the bar and the tick). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from boss281 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 180 / 15
BAR_T = 16
START = 64
TONIC = 67                      # G4 (the pitch class G = 7)

Gm = (0, (0, 3, 7, 10))
Cm = (5, (0, 3, 7, 10))
D7 = (7, (0, 4, 7, 10))
Bb = (3, (0, 4, 7, 11))
Ab = (1, (0, 4, 7, 11))
Eb = (8, (0, 4, 7, 11))
Fm = (10, (0, 4, 7, 10))
GM = (0, (0, 4, 7, 11))
CM = (5, (0, 4, 7, 11))
Em = (9, (0, 3, 7, 10))
DM = (7, (0, 4, 7, 10))
PROG_M = {"R1": [Gm, Cm, D7, D7], "R2": [Gm, Bb, Cm, D7], "R3": [Gm, Ab, Bb, D7], "R4": [Gm, Fm, Eb, D7]}
PROG_MAJ = {"R1": [GM, CM, DM, DM], "R2": [GM, Em, CM, DM], "R3": [GM, Fm, CM, DM], "R4": [GM, Fm, Em, DM]}
SHIFTS = {"R1": [0, 5, 7], "R2": [0, 3, 5], "R3": [0, 1, 3], "R4": [0, 10, 8]}
CELL_A = [(0, 0), (3, 3), (4, 7), (7, 6), (8, 2), (11, 5), (12, 9), (15, 8)]
CELL_B = [(0, 0), (1, 0), (3, 0), (4, 3), (7, 7), (8, 6), (12, 2), (15, 5)]
CLIMB = [(0, 7), (3, 10), (4, 14), (7, 12), (8, 14), (12, 19)]
AXIS = 7
I_ = IRON_XYLOPHONE
INST = {"X": I_, "P": PLING, "B": BIT, "F": FLUTE, "J": BANJO, "G": GUITAR, "H": HARP, "Y": XYLOPHONE}
TARGET = {HARP: 64, I_: 64, PLING: 64, BIT: 64, BANJO: 62, FLUTE: 86, GUITAR: 54, XYLOPHONE: 100}
KEYJOLT = [1, 6, 3, 7, 2, 5]


def U(prog, cell, feels, key, leads, tags="", mode="m", bars=4):
    return dict(prog=prog, cell=cell, feels=feels.split(), key=key, leads=leads, tags=set(tags.split()), mode=mode, bars=bars)


A_UNITS = [U("R1", "A", "SW SW SW SW", 0, "XX", "hit"), U("R2", "B", "SW SW SW SW", 0, "XP", "hit"), U("R4", "A", "SW TR SW SW", 1, "FF", "hit rest2"),
           U("R1", "B", "SW SW SW HT", 1, "JP", "hit"), U("R2", "A", "ST ST ST ST", 4, "FF", "hit"), U("R3", "B", "ST ST ST TR", 4, "BB", "hit"),
           U("R4", "A", "ST ST HT HT", 10, "PX", "hit rest1"), U("R3", "A", "SW SW TR TR", 0, "XY", "hit"), U("R1", "A", "SW SW", 0, "XX", "hit", bars=2)]
B_UNITS = [U("R1", "A", "SW TR SW TR", 3, "XX", "hit"), U("R2", "A", "TR SW TR SW", 3, "FP", "hit"), U("R3", "B", "SW TR SW TR", 6, "PX", "hit"),
           U("R4", "A", "TR TR SW SW", 6, "XB", "hit"), U("R1", "A", "TR SW TR SW", 0, "XX", "hit break")]
C_UNITS = [U("R1", "A", "ST ST SW SW", 0, "FF", "hit", "M"), U("R2", "B", "ST ST ST SW", 0, "FF", "hit", "M"), U("R4", "A", "SW SW ST ST", 0, "FF", "hit", "M"),
           U("R3", "B", "ST ST SW SW", 0, "FF", "hit", "M"), U("R1", "A", "ST ST SW SW", 1, "FF", "hit", "M"), U("R2", "B", "SW SW ST ST", 1, "FF", "hit rest2", "M"),
           U("R4", "A", "ST SW ST SW", 0, "FF", "hit", "M"), U("R3", "B", "ST ST SW SW", 0, "FF", "hit", "M"), U("R1", "A", "ST ST ST ST", 0, "FF", "hit", "M"),
           U("R2", "A", "ST SW", 0, "FF", "hit", "M", bars=2)]
O_UNITS = [U("R1", "A", "SW SW SW SW", 0, "XX", "hit"), U("R2", "B", "SW TR SW TR", 0, "FB", "hit"), U("R3", "A", "TR SW TR SW", 0, "XF", "hit rest2"), U("R1", "B", "SW SW SW SW", 0, "XX", "hit")]
SECTIONS = [("A", A_UNITS), ("B", B_UNITS), ("CHORUS", C_UNITS), ("OUTRO", O_UNITS)]
BARS = []
for name, units in SECTIONS:
    for ui, unit in enumerate(units):
        for ub in range(unit["bars"]):
            BARS.append(dict(sec=name, unit=unit, ub=ub, ui=ui))
NBARS = len(BARS)
LENGTH = START + NBARS * BAR_T
BREAK_BAR = next(i for i, b in enumerate(BARS) if b["sec"] == "B" and "break" in b["unit"]["tags"] and b["ub"] == 3)
OPEN_UNIT = C_UNITS[-1]                       # the opening quotes the last two bars of the chorus
ROLES = {"lead": (100, 80), "mirror": (88, 120), "dbl": (70, 90), "power": (90, 100), "power2": (80, 100), "bass": (100, 100), "didg": (100, 100), "kick": (100, 100), "tom": (100, 100),
         "snare": (95, 105), "hat": (55, 115), "bell": (70, 100)}


def major(s):
    return s + 1 if s % 12 in (3, 8, 10) else s


def bass_note(pc, target=34):
    return near(pc, target, BASS)


def reach(root, interval):
    p = root + interval
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
    feel = unit["feels"][ub]
    return dict(bar=bar, unit=unit, ub=ub, ui=info["ui"], sec=sec, key=key, chords=chords, feel=feel, level=level, mode=unit["mode"], phase=phase)


def tune_events(sp):
    unit, ub = sp["unit"], sp["ub"]
    if ub == 3:
        events = list(CLIMB)
    else:
        base = SHIFTS[unit["prog"]][ub]
        events = [(t, s + base) for t, s in (CELL_A if unit["cell"] == "A" else CELL_B)]
    if sp["mode"] == "M":
        events = [(t, major(s)) for t, s in events]
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
        events = [(t, s) for t, s in events if t % 4 == 0 or t % 4 == 3]
    return events


def offbeat(events):
    placed = {}
    for t, s in events:
        placed.setdefault(t + 2 if t % 4 == 0 else t, s)
    return sorted(placed.items())


def halved(events, half):
    stretched = [(2 * t, s) for t, s in events]
    out = []
    for i, (t, s) in enumerate(stretched):
        end = stretched[i + 1][0] if i + 1 < len(stretched) else 32
        if 16 * half <= t < 16 * half + 16:
            out.append((t - 16 * half, s, end - t))
    return out


def long_notes(sp):
    """The tune in long notes: the strong beats of the cell held (bar: whole, halves, halves, quarters)."""
    ub = sp["ub"]
    starts = {0: (0,), 1: (0, 8), 2: (0, 8), 3: (0, 4, 8, 12)}[ub]
    events = tune_events(dict(sp, feel="SW"))
    out, last = [], events[0][1]
    for i, tk in enumerate(starts):
        end = starts[i + 1] if i + 1 < len(starts) else 16
        inside = [s for t, s in events if tk <= t < min(tk + 4, end)]
        pick = inside[0] if inside else last
        last = pick
        out.append((tk, pick, end - tk))
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


def lead_voice(b, t0, sp):
    """Plays the tune; returns the set of ticks at which a tune voice strikes (the guitar hits go only there)."""
    ub, sec, phase, unit = sp["ub"], sp["sec"], sp["phase"], sp["unit"]
    seg = ub // 2
    code = unit["leads"][min(seg, len(unit["leads"]) - 1)]
    inst = INST[code]
    if phase == 1:
        inst = {FLUTE: BIT, I_: BIT, XYLOPHONE: PLING}.get(inst, inst) if hash_(sp["bar"], 1) % 3 else inst
    long_mode = sec == "CHORUS" or sp.get("opening")
    if long_mode:
        inst = FLUTE
    events = tune_events(sp)
    ticks = set()
    if sec == "B":
        pos = (sp["ui"] % 2) * 4 + ub
        tb, half = divmod(pos, 2)
        notes = halved(tune_events(dict(sp, ub=min(tb, 3), feel="SW")), half)
    elif long_mode:
        notes = long_notes(sp)
    else:
        if sec == "A":
            events = offbeat(events)
        notes = [(t, s, 0) for t, s in events]
    semis = [s for _, s, *_ in notes]
    n = place(inst, sp["key"], [s for _, s in tune_events(dict(sp, feel="SW"))], TARGET)
    for tick, s, length in notes:
        vel = min(100, 93 + (7 if tick % 8 == 0 else 0))
        pitch = TONIC + sp["key"] + s + n
        hold(b, "lead", inst, t0 + tick, pitch, length if length else 1, vel, rise=6)
        for t in range(tick, tick + max(1, length), 4):
            ticks.add(t)
        ticks.add(tick)
    if sec in ("OUTRO",) or sp.get("opening"):                      # the mirror: the same tune turned upside down on a second instrument
        m_inst = PLING if not long_mode else HARP
        refl = [2 * AXIS - s for _, s in tune_events(dict(sp, feel="SW"))]
        mn = place(m_inst, sp["key"], refl, {k: v - 6 for k, v in TARGET.items()})
        for tick, s, length in notes:
            if sec == "OUTRO" and tick + 4 > 15:
                continue
            hold(b, "mirror", m_inst, t0 + tick + (4 if sec == "OUTRO" else 0), TONIC + sp["key"] + 2 * AXIS - s + mn, length if length else 1, 86, rise=6)
    if long_mode:                                                      # the harp an octave and more below, on the same ticks (hidden under the flute)
        for tick, s, length in notes:
            hold(b, "dbl", HARP, t0 + tick, near((TONIC + sp["key"] + s) % 12, 58, HARP), length if length else 1, 90, rise=6)
    return ticks


def laugh_rest(b, t0, sp):
    root = bass_note(sp["chords"][0][0])
    b.drum("kick", t0, BASEDRUM, 100, once=True)
    b.drum("snare", t0, SNARE, 94, once=True)
    for tick in (0, 4, 8, 12):
        b.note("bass", t0 + tick, BASS, root, 100 if tick == 0 else 74)
        b.note("didg", t0 + tick, DIDGERIDOO, near(root % 12, root, DIDGERIDOO), 86 if tick == 0 else 70)
    for tick in (6, 10, 14):
        b.drum("snare", t0 + tick, SNARE, 52 + tick)


def bass_pitch(level, tick, root, iv, approach, bar):
    slot = tick // 2
    if level <= 0:
        pick = 0
    elif level == 1:
        pick = "A" if (slot == 7 and bar % 2) else (2 if slot in (2, 6) and bar % 4 == 3 else 0)
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


def rhythm(b, t0, sp, next_pc, ltick, fill=False, accel=False, toms=None, toms_up=False, open_level=None):
    feel, phase, bar, sec = sp["feel"], sp["phase"], sp["bar"], sp["sec"]
    level = sp["level"] if open_level is None else open_level
    pc, chord, _ = sp["chords"][0]
    root = bass_note(pc)
    appr = bass_note((next_pc + (1 if bar % 2 == 0 else -1)) % 12, root)
    if level == 0:
        ticks = [0, 4, 8, 12]
    else:
        ticks = hit_ticks(level, feel, bar)
        if phase == 1 and level == 3:
            ticks = sorted(set(ticks) | {t + 1 for t in ticks if t + 1 < 16 and (t + bar) % 5 == 0})
        if level == 3:
            ticks = ticks[:11]
    for tick in ticks:
        p = bass_pitch(level, tick, root, chord[1], appr, bar)
        vel = 100 if tick % 8 == 0 else (90 if tick % 4 == 0 else (78 if tick % 2 == 0 else 62))
        if level == 4:
            vel = bass_velocity(tick, 4)
        b.note("bass", t0 + tick, BASS, p, vel)
        if tick % 4 == 0 and (level >= 3 or tick % 8 == 0) or level == 0:
            b.note("didg", t0 + tick, DIDGERIDOO, near(p % 12, p, DIDGERIDOO), 92 if tick % 8 == 0 else 80)
    if sec == "CHORUS" or sp.get("opening"):
        for tick in (0, 4, 8, 12):
            if tick in ltick:
                b.note("power", t0 + tick, GUITAR, near(pc, 47, GUITAR), 98, once=True)
    ev = {}

    def d_(drum, tick, vel):
        ev[(drum, tick)] = max(ev.get((drum, tick), 0), vel)

    sparse = sec == "A"
    sixteenth = level == 4
    if feel == "SW":
        d_(BASEDRUM, 0, 90); d_(BASEDRUM, 8, 82); d_(SNARE, 4, 92); d_(SNARE, 12, 94)
        if level >= 3 and not sixteenth:
            d_(BASEDRUM, 11, 66); d_(SNARE, 7, 40); d_(SNARE, 15, 40)
        if 3 <= level < 4:
            for tick in (3, 7, 11, 15):
                d_(HAT, tick, 38)
    elif feel == "ST":
        for tick, vel in ((0, 98), (4, 74), (8, 94), (12, 74)):
            d_(BASEDRUM, tick, vel)
        if not sixteenth:
            for tick in (2, 6, 10, 14):
                d_(SNARE, tick, 62)
        if level >= 3 or sparse:
            d_(SNARE, 4, 88); d_(SNARE, 12, 88)
    elif feel == "TR":
        for tick in (0, 5, 8, 13):
            d_(BASEDRUM, tick, 92 if tick % 8 == 0 else 72)
        d_(SNARE, 3, 88); d_(SNARE, 11, 88)
        if level >= 3:
            d_(SNARE, 7, 58); d_(SNARE, 15, 62)
    elif feel == "HT":
        d_(BASEDRUM, 0, 100); d_(BASEDRUM, 6, 66); d_(BASEDRUM, 11, 62); d_(SNARE, 8, 100)
        d_(HAT, 4, 40); d_(HAT, 12, 40)
    elif feel == "MR":
        for tick in range(0, 16, 2):
            d_(SNARE, tick, 36 + 3 * tick + (10 if tick % 4 == 0 else 0))
        d_(BASEDRUM, 0, 98); d_(BASEDRUM, 8, 94)
    if sixteenth and feel != "MR":
        for tick in range(0, 16, 4):
            d_(HAT, tick, 38)
    if phase == 1 and hash_(bar, 9) % 3 == 0 and not sixteenth:
        for tick in (13, 14, 15):
            d_(SNARE if tick != 13 else BASEDRUM, tick, 54 + 6 * (tick - 13))
    if fill:
        for k in [k for k in ev if k[1] >= 10]:
            del ev[k]
        for count, tick in enumerate(range(10, 16)):
            d_(SNARE, tick, 44 + 9 * count)
    if accel:
        for k in [k for k in ev if k[1] >= 4 and k[0] in (SNARE, HAT)]:
            del ev[k]
        for index, tick in enumerate((4, 8, 10, 12, 13, 14, 15)):
            d_(SNARE, tick, 42 + 8 * index)
            if index % 2 == 0:
                d_(BASEDRUM, tick, 70 + 4 * index)
    if toms is not None:
        for k in [k for k in ev if k[1] >= toms]:
            del ev[k]
    for (drum, tick), vel in ev.items():
        b.drum({BASEDRUM: "kick", SNARE: "snare", HAT: "hat"}[drum], t0 + tick, drum, vel)
    if toms is not None:
        tom_roll(b, t0, toms, 16, ascending=toms_up)


def ensemble(b, t0, sp, with_tune):
    pc, chord, _ = sp["chords"][0]
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
    rest = ("rest1" in unit["tags"] and ub == 1) or ("rest2" in unit["tags"] and ub == 2) or (sec == "CHORUS" and sp["ui"] == 5 and ub == 2)
    if rest:                                                          # melody taken away: stop-time hits, the bass stays
        laugh_rest(b, t0, sp)
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
    ltick = lead_voice(b, t0, sp) if not (sp["feel"] == "SB") else set()
    rhythm(b, t0, sp, next_pc, ltick, fill, accel, toms, special == "final", open_level=0 if sp.get("opening") else None)
    if ub in (0, 2) or sp.get("opening"):
        if sp["sec"] in ("CHORUS", "OUTRO") or sp.get("opening"):
            ensemble(b, t0, sp, 0 in ltick)
        elif "hit" in unit["tags"]:
            ensemble(b, t0, sp, True)


def play(phase):
    b = Builder(LENGTH, ROLES)
    # ---- the opening: the last two bars of the chorus (C - D7, G major) twice, at full strength, over quarter-note bass
    base_bar = len(BARS) - 1
    for i in range(4):
        info = dict(sec="CHORUS", unit=OPEN_UNIT, ub=i % 2, ui=9)
        sp = dict(spec(phase, 0, info), opening=True, bar=900 + i, feel=("ST", "SW", "SW", "ST")[i], mode="M")
        nxt = bass_note(sp["chords"][0][0]) % 12
        render(b, 16 * i, sp, (TONIC + phase + 7) % 12, special="open_last" if i == 3 else None)
    for bar in range(NBARS):
        t0 = START + bar * BAR_T
        sp = spec(phase, bar)
        if bar == BREAK_BAR:                                          # the break: drums and tune stop, ONE bell - the bass stays
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
    titles = ("The Cracked Mirror Laughs (Boss007)", "The Cracked Mirror Laughs, Shattered (Boss007)")
    descriptions = ("Mirror boss, phase 1, class 4, style 3 (symphonic open), 180 BPM: a four-note motif that cracks, opening on the chorus's last phrase over quarter-note bass, small off-beat A, half-ed tune in B, a one-bell break, the chorus in G major in long notes over a sixteenth bass, tom-roll outro. Loops from tick 64.",
                    "Mirror boss, phase 2: the same bars, chords, break and loop a half step higher - electronic leads, key jolts, glitch drums, more double hits.")
    edges = [("opening", 0, START)]
    cursor = START
    for name, units in SECTIONS:
        n = sum(u["bars"] for u in units)
        edges.append((name, cursor, cursor + n * BAR_T))
        cursor += n * BAR_T
    for phase in range(2):
        b = play(phase)
        target = out / ("mirror_phase1.nbs" if phase == 0 else "mirror_phase2.nbs")
        layers = finish281(target, b, TPS, titles[phase], descriptions[phase])
        print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
        print("   section starts (s): " + ", ".join(f"{n} {s / TPS:.0f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
