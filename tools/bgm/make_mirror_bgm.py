#!/usr/bin/env python3
"""Makes mirror_phase1.nbs and mirror_phase2.nbs: "The Cracked Mirror Laughs (Boss007)" and "..., Shattered (Boss007)" - class 4, the carnival of madness and chaos (a trickster boss).

    python3 tools/bgm/make_mirror_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_mirror_bgm.py outdir     writes them somewhere else

t277: STYLE 3 of the style doc ("symphonic open", base 180 BPM) on the 16th-note machine-gun ladder of t276.
180 BPM, the limit speed: 12 ticks a second (180 / 15), a tick is a sixteenth, a beat 4 ticks, a bar 16 (4/4) = the engine's bar line. 4 intro bars + 108 bars = 1792 ticks = 149.3 s.
SWING without a triplet grid: the off eighth is put on the third tick of the beat (long-short = 3 + 1); a TRIPLET FIGURE is three notes over two beats (ticks 0, 3, 5; 3 + 3 + 2); a triplet inside one
beat is ticks 0, 1, 3. The FEEL changes bar by bar (SW swing, ST straight polka, TR triplet figures, HT half-time, SB stop-time, MR march roll); the key (a half step, a minor third, a tritone ... every 4
bars, abruptly) and the lead instrument (every 2 bars) change all the time: the player is toyed with.
Time table (style doc -> this song; the bass level is the t276 ladder):
  0:00  OPENING (the 4 intro bars): the last phrase of the chorus (IV - V7, F# MAJOR) at full power: sixteenth bass, tune in full, toms - and it hangs on the dominant.
  0:05  A (32 bars, to 0:48): sparse - only bass (swung eighths, then a walking line) and snare/kick, hardly any stabs or hat; the tune is moved onto the OFF beats and chatters (F# minor).
  0:48  B (24 bars, to 1:20): the tune is HALF-ED (every note twice as long, 4 bars of tune over 8) while the drums keep their speed: bass eighths with sixteenth double hits and triplet figures, the line climbing and falling.
        A stop-time laugh inside; the last bars: a rising roll, then ONE BAR OF BREAK - all drums, bass and everything muted, a single high bell note (phase 2: the same).
  1:20  CHORUS (36 bars, to 2:08): the SAME-TONIC MAJOR flip (F# minor -> F# major: A, D#, E# replace A, D, E; chords I - IV - V7 - vi): the catchiest form of the tune in the top of the harp range with a flute
        descant in long notes from bar 16; the bass is a full SIXTEENTH machine gun, hat and drums in sixteenths. A half-step jolt in the middle.
  2:08  OUTRO (16 bars): back to minor; tom-roll fill-ins (the bass drum on falling pitches) in every bar, the last bar a rising roll that leads into tick 64.
The device is the MIRROR: the tune (three 4-bar phrases) is answered by its own upside-down reflection on a second instrument (at once, bar by bar, phrase by phrase or one beat late); in the chorus the
reflection of the MAJOR tune grinds against the chord. Menace: heavy walking bass doubled by the didgeridoo, shell stabs of a third and a seventh (a tritone), guitar power hits, a cruel flute laugh.
Phase 2 (Shattered) is the same 108 bars, chords, events and loop point, but the mirror is cracked: the machine gun starts earlier (A is already eighths that walk, B is sixteenths), the feel changes every bar,
the key every 2 bars (not in the chorus), the tune is cut into shards, detuned by a half step here and there, the reflection axis wobbles, a chip arpeggio flickers and the drums glitch.
Everything is written by what SOUNDS. Nothing is random (a hash of the bar and the tick). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat45 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS = 180 / 15                   # 12 ticks a second
BAR_T = 16
START = 64                       # the loop point: 4 bars of opening = 64 ticks
UNITS = 27                       # of 4 bars
NBARS = UNITS * 4                # 108 bars = 144 s + the 5.3 s intro
LENGTH = START + NBARS * BAR_T
TONIC = 66                       # F#4

# chords as (root, intervals above it); a pair is (chord, tick of the bar where it starts)
I = (0, (0, 3, 7, 10))
IV = (5, (0, 3, 7, 10))
BVI = (8, (0, 4, 7, 11))
V7 = (7, (0, 4, 7, 10))
BVII7 = (10, (0, 4, 7, 10))
DIM = (11, (0, 3, 6, 9))
IM7 = (0, (0, 4, 7, 11))
IVM = (5, (0, 4, 7, 11))
VIM = (9, (0, 3, 7, 10))
PROGS_M = {                      # the same-tonic major of the chorus
    "A": [[(IM7, 0)], [(IM7, 0), (IVM, 8)], [(IVM, 0)], [(V7, 0)]],
    "B": [[(VIM, 0)], [(IVM, 0)], [(IM7, 0)], [(V7, 0)]],
    "C": [[(IM7, 0)], [(BVII7, 0)], [(IVM, 0)], [(V7, 0)]],
    "D": [[(IM7, 0)], [(IM7, 0)], [(IM7, 0)], [(V7, 0)]],
}
PROGS = {
    "A": [[(I, 0)], [(I, 0), (BVII7, 8)], [(IV, 0)], [(V7, 0)]],
    "B": [[(BVI, 0)], [(DIM, 0)], [(I, 0)], [(V7, 0)]],
    "C": [[(I, 0)], [(BVII7, 0)], [(BVI, 0)], [(V7, 0)]],
    "D": [[(I, 0)], [(I, 0)], [(I, 0)], [(V7, 0)]],
}


def conv(old_events):
    """The old tunes were written in bars of 24 ticks (6 a beat); on the 16-tick bar a beat is 4 ticks: 0, 1, 1, 2, 3, 3 (the off eighth is the third tick, a triplet is 0, 1, 3)."""
    return [((t // 6) * 4 + (0, 1, 1, 2, 3, 3)[t % 6], s) for t, s in old_events]


# the tunes: semitones above the tonic; every phrase ends hanging UP
MEL = {
    "A": [  # the jazz-circus theme: a minor arpeggio with a leading tone, climbing
        conv([(0, 12), (4, 15), (6, 19), (10, 15), (12, 12), (16, 11), (18, 12), (22, 14)]),
        conv([(0, 15), (4, 14), (6, 12), (10, 10), (12, 14), (16, 17), (18, 14), (22, 12)]),
        conv([(0, 12), (4, 15), (6, 17), (10, 15), (12, 20), (16, 17), (18, 15), (22, 16)]),
        conv([(0, 17), (4, 14), (6, 11), (10, 14), (12, 17), (16, 19), (18, 20), (22, 23)]),
    ],
    "B": [  # a sequence in three steps ending in a chromatic climb
        conv([(0, 20), (4, 19), (6, 15), (10, 19), (12, 20), (16, 24), (18, 20), (22, 19)]),
        conv([(0, 23), (4, 20), (6, 17), (10, 20), (12, 23), (16, 26), (18, 23), (22, 20)]),
        conv([(0, 24), (4, 22), (6, 19), (10, 22), (12, 24), (16, 27), (18, 29), (22, 31)]),
        conv([(0, 26), (4, 29), (6, 26), (10, 23), (12, 26), (14, 27), (16, 28), (18, 29), (20, 30), (22, 31)]),
    ],
    "C": [  # the laughing shout: three repeated notes (a triplet in one beat: ticks 0, 1, 3), then a leap
        conv([(0, 19), (2, 19), (4, 19), (6, 22), (10, 19), (12, 15), (16, 19), (18, 22), (22, 24)]),
        conv([(0, 22), (2, 22), (4, 22), (6, 26), (10, 22), (12, 20), (16, 22), (18, 26), (22, 29)]),
        conv([(0, 24), (2, 24), (4, 24), (6, 27), (10, 24), (12, 20), (16, 24), (18, 27), (22, 31)]),
        conv([(0, 26), (2, 26), (4, 26), (6, 29), (10, 26), (12, 23), (16, 26), (18, 29), (22, 32)]),
    ],
}
for tune in MEL.values():
    for bar_events in tune:
        assert len({t for t, _ in bar_events}) == len(bar_events) and all(0 <= t < 16 for t, _ in bar_events), bar_events
AXIS = {"A": 17, "B": 23, "C": 24}      # the mirror: s becomes 2 * axis - s
LAUGH = [3, 0, 3, 0, 2, -1, 2, -1, 1, -2]   # semitones from the anchor of the flute (84 to 95), then the shriek
SHRIEK = 6

I_ = IRON_XYLOPHONE
INST = {"X": I_, "P": PLING, "B": BIT, "F": FLUTE, "J": BANJO, "G": GUITAR, "H": HARP, "Y": XYLOPHONE}
TARGET = {HARP: 70, I_: 70, PLING: 70, BIT: 70, BANJO: 70, FLUTE: 88, GUITAR: 54, XYLOPHONE: 100}


def U(prog, mel, feels, key, leads, mirror, stab, levels, tags=(), mode="m"):
    return dict(prog=prog, mel=mel, feels=feels.split(), key=key, leads=leads, mirror=mirror, stab=stab, lv=levels, tags=set(tags), mode=mode)


# levels (bass): 0 quarters, 1 eighths, 2 eighths that walk, 3 eighths + sixteenth doubles + triplet figures, 4 sixteenths; (phase 1, phase 2). mode: m = F# minor, M = F# major (the chorus)
PLAN = [
    # A (32 bars): sparse, swing and polka, the tune on the off beats
    U("A", "A", "SW SW SW SW", 0, "XX", None, "J", (1, 2), "hit"),
    U("B", "B", "SW SW SW SW", 0, "XP", ("B", "call"), "J", (1, 2), "fill"),
    U("A", "A", "SW SW SW TR", 1, "FF", None, "P", (1, 2), "hit fill"),
    U("C", "C", "SW SW SW SB", 1, "JP", ("B", "alt"), "G", (1, 2), ""),
    U("A", "A", "ST ST ST ST", 4, "FF", None, "J", (2, 3), "hit"),
    U("B", "B", "ST ST ST TR", 4, "BB", ("G", "alt"), "P", (2, 3), "fill"),
    U("A", "A", "ST ST HT HT", 10, "PX", None, "J", (2, 3), "hit"),
    U("C", "C", "SW SW TR TR", 0, "XY", ("B", "echo"), "G", (2, 3), "accel"),
    # B (24 bars): the tune half-ed (two units per tune), the drums keep their speed; the last bar is the break
    U("A", "A", "SW TR SW TR", 3, "XX", None, "G", (3, 4), "hit"),
    U("A", "A", "SW TR SW SB", 3, "FB", None, "J", (3, 4), "laugh"),
    U("B", "B", "TR SW TR SW", 6, "PX", ("B", "echo"), "P", (3, 4), "hit"),
    U("B", "B", "SW TR SW TR", 6, "XF", ("G", "sim"), "G", (3, 4), ""),
    U("C", "C", "TR TR SW SW", 0, "YF", ("B", "sim"), "J", (3, 4), "hit"),
    U("C", "C", "TR SW TR SW", 0, "XX", ("X", "sim"), "G", (3, 4), ""),
    # CHORUS (36 bars): F# major
    U("A", "A", "ST ST SW SW", 0, "FF", ("B", "sim"), "P", (4, 4), "hit", "M"),
    U("B", "B", "ST ST ST SW", 0, "XP", ("G", "sim"), "J", (4, 4), "fill", "M"),
    U("A", "A", "SW SW ST ST", 0, "BF", ("B", "sim"), "G", (4, 4), "hit", "M"),
    U("C", "C", "ST ST SW SW", 0, "YF", ("X", "sim"), "P", (4, 4), "fill", "M"),
    U("A", "A", "ST ST SW SW", 1, "FB", ("G", "sim"), "J", (4, 4), "hit", "M"),
    U("B", "B", "SW SW ST ST", 1, "XF", ("B", "sim"), "P", (4, 4), "fill", "M"),
    U("A", "A", "ST SW ST SW", 0, "YF", ("B", "sim"), "G", (4, 4), "hit", "M"),
    U("C", "C", "ST ST MR MR", 0, "FX", ("X", "sim"), "P", (4, 4), "fill", "M"),
    U("A", "A", "ST ST ST ST", 0, "FF", ("B", "sim"), "P", (4, 4), "hit", "M"),
    # OUTRO (16 bars): back to minor, tom-roll fill-ins
    U("D", "A", "SW SW SW SW", 0, "XX", None, "J", (3, 4), "hit"),
    U("C", "C", "SW TR SW TR", 0, "FB", ("B", "echo"), "G", (3, 4), ""),
    U("A", "A", "TR SW TR SW", 0, "XF", ("B", "echo"), "P", (3, 4), ""),
    U("D", "A", "SW SW SW SW", 0, "XX", None, "J", (3, 4), "hit"),
]
assert len(PLAN) == UNITS
SECTIONS = [("A", 8), ("B", 6), ("CHORUS", 9), ("OUTRO", 4)]
assert sum(n for _, n in SECTIONS) == UNITS
SEC = [name for name, n in SECTIONS for _ in range(n)]
BREAK_BAR = 4 * 14 - 1                 # the last bar of the B section
QUARTER_KEY = [1, 6, 3, 7, 2, 5]

ROLES = {"lead": (100, 80), "mirror": (88, 120), "dbl": (70, 90), "laugh": (92, 100), "arp": (56, 110), "stab": (90, 100), "power": (85, 100),
         "bass": (100, 100), "didg": (100, 100), "chip": (70, 100), "kick": (100, 100), "tom": (100, 100), "snare": (90, 105), "hat": (60, 115), "bell": (70, 100)}


def key_of(phase, bar):
    unit, ub = divmod(bar, 4)
    key = PLAN[unit]["key"]
    if phase == 1 and SEC[unit] != "CHORUS" and unit < UNITS - 1 and ub >= 2:
        key = (key + QUARTER_KEY[(unit * 5 + 1) % 6]) % 12     # the key changes every 2 bars: a half step, a tritone, a third ...
    return key


def chords_of(phase, bar):
    if bar >= NBARS:
        return [(TONIC % 12, I, 0)]
    unit, ub = divmod(bar, 4)
    key = key_of(phase, bar)
    progs = PROGS_M if PLAN[unit]["mode"] == "M" else PROGS
    return [((TONIC + key + c[0]) % 12, c, offset) for c, offset in progs[PLAN[unit]["prog"]][ub]]


def major(s):
    """The same-tonic major: the minor third, the flat sixth and the flat seventh move up a half step."""
    return s + 1 if s % 12 in (3, 8, 10) else s


def offbeat(events, bar, phase):
    """Section A: the tune is moved onto the off beats (the beat's first tick moves to the third) and chatters (a sixteenth repeat)."""
    placed = {}
    for t, s in events:
        t2 = t + 2 if t % 4 == 0 else t
        placed.setdefault(t2, s)
    result = dict(placed)
    for t, s in placed.items():
        if t % 4 >= 2 and t + 1 < 16 and t + 1 not in result and (phase == 1 or hash_(bar, t, 17) % 2):
            result[t + 1] = s
    return sorted(result.items())


def halved(events, half):
    """The tune half-ed: every note twice as long (the tick doubles). Returns [(tick in this bar, s, length)] for the first (half 0) or second (half 1) bar of the two."""
    stretched = [(2 * t, s) for t, s in events]
    result = []
    for index, (t, s) in enumerate(stretched):
        end = stretched[index + 1][0] if index + 1 < len(stretched) else 32
        if 16 * half <= t < 16 * half + 16:
            result.append((t - 16 * half, s, end - t))
    return result


def level_of(phase, bar):
    return PLAN[bar // 4]["lv"][phase]


def feel_of(phase, bar):
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    if phase == 0:
        return plan["feels"][ub]
    if plan["feels"][ub] == "SB":
        return "SB"
    level = level_of(phase, bar)
    pools = {0: ["HT", "HT", "SB", "HT"], 1: ["HT", "SW", "ST", "TR"], 2: ["SW", "ST", "TR", "HT"], 3: ["SW", "TR", "ST", "HT", "SW", "TR"], 4: ["HT", "SW", "TR", "ST", "MR", "SW", "HT", "TR"]}
    pool = pools[level]
    return pool[hash_(bar, 7) % len(pool)]


def straight(events):
    """Straight eighths instead of swung ones: the third tick of a beat moves to the second."""
    seen, result = set(), []
    for tick, s in events:
        t = tick - 1 if tick % 4 == 3 else tick
        if t not in seen:
            seen.add(t)
            result.append((t, s))
    return result


TARGET_CHORUS = {HARP: 75, I_: 75, PLING: 75, BIT: 75, BANJO: 72, FLUTE: 90, GUITAR: 60, XYLOPHONE: 100}


def place(instrument, key, semis, target=None):
    """The octave n for a whole phrase so that every note fits the instrument, nearest to its comfortable height."""
    target = target or TARGET
    low, high = RANGE[instrument]
    best, best_cost = 0, None
    for n in range(-48, 49, 12):
        pitches = [TONIC + key + s + n for s in semis]
        over = sum(max(0, p - high) + max(0, low - p) for p in pitches)
        cost = over * 100 + abs(sum(pitches) / len(pitches) - target[instrument])
        if best_cost is None or cost < best_cost:
            best, best_cost = n, cost
    return best


def melody_bar(phase, bar, feel):
    """[(tick, s)] of the tune in this bar, shaped by the feel (and in phase 2 cut into shards)."""
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    events = list(MEL[plan["mel"]][ub])
    if feel in ("ST", "MR"):
        events = straight(events)
    elif feel == "HT":
        events = [(t, s) for t, s in events if t % 4 == 0]
    if phase == 1:
        mode = hash_(bar, 3) % (6 if level_of(phase, bar) >= 3 else 3)
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
        events = [(t, s + 1 if hash_(bar, t) % 7 == 0 else s) for t, s in events]
    return events


def long_melody(bar, events):
    """The half-time melody of the peak: the strong beats of the tune, held. Bars of the phrase: whole note, halves, halves, quarters (rising towards the next phrase).
    Returns [(tick, s, length)]; a note is the first event at or after its tick inside the beat, else the last note before it (the tune is not lost)."""
    ub = bar % 4
    starts = {0: (0,), 1: (0, 8), 2: (0, 8), 3: (0, 4, 8, 12)}[ub]
    result = []
    last = events[0][1]
    for index, tick in enumerate(starts):
        end = starts[index + 1] if index + 1 < len(starts) else 16
        inside = [s for t, s in events if tick <= t < min(tick + 4, end)]
        pick = inside[0] if inside else last
        result.append((tick, pick, end - tick))
        last = pick
    return result


def bass_note(pc, target):
    return near(pc, target, BASS)


def reach(root, interval):
    pitch = root + interval
    while pitch > 54:
        pitch -= 12
    return pitch


def bass_pitch(level, tick, root, chord, approach, bar):
    """The pitch of one bass hit. Level 1: the root repeats (an approach note at the very end of every other bar); level 2: root, root, fifth (oom-pah) and the approach;
    level 3: the line climbs and falls through the chord (root, third, fifth, seventh, fifth, third, octave, approach); level 4: the sixteenths sway root - fifth - root - a chord tone."""
    iv = chord[1]
    slot = tick // 2
    if level <= 1:
        pick = "A" if (slot == 7 and bar % 2) else 0
    elif level == 2:
        pick = (0, 0, 2, 0, 0, 0, 2, "A")[slot]
    elif level == 3:
        pick = (0, 1, 2, 3, 2, 1, "O", "A")[slot]
    else:
        beat = tick // 4
        pick = (0, 2, 0, (1, 2, 3, 2)[bar % 4])[beat]
    if pick == "A":
        return approach
    if pick == "O":
        return reach(root, 12) if root + 12 <= 54 else root
    pitch = reach(root, iv[pick])
    if level == 4 and tick % 4 == 3 and (tick // 4 + bar) % 2 == 0 and pitch + 12 <= 54:
        pitch += 12
    return pitch


def tom_roll(b, t0, start, end, ascending=False, vel_from=40, vel_to=100):
    """A tom roll: the bass drum struck on falling (or rising) pitches, a snare accent on the beats."""
    count = end - start
    for index, tick in enumerate(range(start, end)):
        frac = index / max(1, count - 1)
        k = int(round(78 - 24 * (frac if not ascending else 1 - frac)))        # bass drum key 54..78 = pitch
        b.drum("tom", t0 + tick, BASEDRUM, int(vel_from + (vel_to - vel_from) * frac), key=k, once=True)
        if tick % 4 == 0:
            b.drum("snare", t0 + tick, SNARE, int(vel_from + (vel_to - vel_from) * frac) - 10, once=True)


def break_bar(b, t0):
    """The break before the chorus: every drum muted - and the bass, the stabs, everything - a single high bell note."""
    b.note("bell", t0, BELL, 96, 88)


def opening(b, phase):
    """The 4 intro bars: the last two bars of the chorus (IV - V7, F# major) twice, at full power, hanging on the dominant."""
    pseudo = [4 * 22 + 2, 4 * 22 + 3, 4 * 22 + 2, 4 * 22 + 3]
    for i, pb in enumerate(pseudo):
        t0 = 16 * i
        feel = ("ST", "SW", "SW", "ST")[i]
        chords = chords_of(phase, pb)
        key = key_of(phase, pb)
        next_pc = chords_of(phase, pseudo[i + 1])[0][0] if i < 3 else TONIC % 12
        bar_rhythm(b, t0, chords, feel, 4, phase, 900 + i, next_pc, "P", False, False, 1.0, toms=6 if i == 3 else None)
        lead_bar(b, t0, phase, pb, feel, 4, 1.0, key, "CHORUS")
        if i in (0, 2):
            hit(b, t0, chords[0][0], chords[0][1])


def play(phase):
    b = Builder(LENGTH, ROLES)
    opening(b, phase)
    # ---- the 108 bars
    for bar in range(NBARS):
        t0 = START + bar * BAR_T
        unit, ub = divmod(bar, 4)
        plan = PLAN[unit]
        sec = SEC[unit]
        if bar == BREAK_BAR:
            break_bar(b, t0)
            continue
        feel = feel_of(phase, bar)
        level = level_of(phase, bar)
        quiet = 0.7 if "quiet" in plan["tags"] else (0.85 if sec == "A" else 1.0)
        chords = chords_of(phase, bar)
        key = key_of(phase, bar)
        next_pc = chords_of(phase, bar + 1)[0][0]
        last = ub == 3
        accel = "accel" in plan["tags"] and last and feel != "SB"
        fill = last and "accel" not in plan["tags"] and level >= 1 and feel != "SB" and sec != "OUTRO"
        toms = None
        if bar == BREAK_BAR - 1:
            accel = True
        if sec == "OUTRO":
            toms = 10 if phase == 0 else 8
            if bar == NBARS - 1:
                toms = 0
        bar_rhythm(b, t0, chords, feel, level, phase, bar, next_pc, plan["stab"], fill, accel, quiet, sparse=sec == "A", toms=toms,
                   toms_up=bar == NBARS - 1)
        if ("hit" in plan["tags"] and ub == 0) or feel == "SB":
            hit(b, t0, chords[0][0], chords[0][1], 0.7 if quiet < 1 else 1.0)
        if feel == "SB":
            laugh(b, t0 + 6, phase, near(chords[0][0], 88, FLUTE), 5, bar)
        if feel != "SB" and level >= 1:
            lead_bar(b, t0, phase, bar, feel, level, quiet, key, sec)
        if phase == 1 and level >= 3 and feel != "SB":
            arp_bar(b, t0, bar, chords, level)
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


def bar_rhythm(b, t0, chords, feel, level, phase, bar, next_pc, stab_code, fill, accel, quiet=1.0, sparse=False, toms=None, toms_up=False):
    """Bass, didgeridoo, stabs and drums of one bar in the given feel and at the given bass level."""
    second = chords[1] if len(chords) > 1 else None

    def chord_at(tick):
        return second if second and tick >= second[2] else chords[0]

    appr = (next_pc + (1 if bar % 2 == 0 else -1)) % 12
    # --- bass and didgeridoo: the subdivision grows with the level
    ticks = [0] if feel == "SB" else hit_ticks(level, feel, bar)
    shown = 0
    for tick in ticks:
        pc, chord, _ = chord_at(tick)
        root = bass_note(pc, 38)
        approach = bass_note(appr, root)
        pitch = bass_pitch(0 if feel == "SB" else level, tick, root, chord, approach, bar)
        if phase == 1 and level <= 3 and feel in ("SW", "ST", "TR") and tick not in (0, 8) and hash_(bar, tick, 11) % 6 == 0:
            continue                                          # shards of the bass line
        vel = bass_velocity(tick, level)
        if level in (1, 2, 3) and tick % 4 in (2, 3) and tick % 2 == 1:
            vel = 66                                          # the swung off eighth
        if feel == "SB":
            vel = 100
        elif level == 0:
            vel = 56
        b.note("bass", t0 + tick, BASS, pitch, vel * quiet)
        heavy = (tick % 4 == 0) if level >= 3 else (tick % 8 == 0)
        if feel == "HT":
            heavy = tick % 4 == 0
        if heavy and (level >= 1 or feel == "SB"):
            b.note("didg", t0 + tick, DIDGERIDOO, near(pitch % 12, pitch, DIDGERIDOO), (96 if tick % 8 == 0 else 80) * quiet)
        if phase == 1 and level == 4 and tick % 4 == 0 and pitch + 24 <= 78:
            b.note("chip", t0 + tick, BIT, pitch + 24, 46)      # the chip doubles the heavy hits two octaves up
        shown += 1
    # --- stabs (the comping): a third and a seventh, a tritone; they leave room at the machine-gun levels
    stab_inst = INST[stab_code]
    comps = {"SW": [(3, 62), (7, 62), (11, 56), (15, 50)], "ST": [(2, 66), (6, 66), (10, 66), (14, 66)], "TR": [(0, 70), (3, 64), (5, 60), (8, 70), (11, 64), (13, 60)],
             "HT": [(0, 88), (12, 70)], "MR": [(0, 70), (8, 70)], "SB": []}[feel]
    if level == 4:
        comps = [(t, v) for t, v in comps if t in (0, 3, 8, 11)] if feel in ("SW", "TR") else [(t, v) for t, v in comps if t % 8 in (2, 0)]
    if sparse:                                                    # section A: hardly any comping (one shell on the first off beat of every other bar)
        comps = comps[:1] if bar % 2 == 0 else []
    if level >= 1:
        for tick, vel in comps:
            if level == 1 and tick % 8 not in (3, 2, 0, 5):
                continue
            pc, chord, _ = chord_at(tick)
            third = near(pc + chord[1][1], 62, stab_inst)
            seventh = near(pc + chord[1][3], 57, stab_inst)
            if phase == 1 and hash_(bar, tick, 2) % 4 == 0:
                continue
            b.note("stab", t0 + tick, stab_inst, third, vel * quiet)
            b.note("stab", t0 + tick, stab_inst, seventh, vel * quiet)
            if level >= 4 and tick in (0, 8):
                b.note("power", t0 + tick, GUITAR, near(pc, 47, GUITAR), 80)
                b.note("power", t0 + tick, GUITAR, near(pc + 7, 52, GUITAR), 70)
    # --- drums
    ev = {}

    def d_(drum, tick, vel):
        ev[(drum, tick)] = max(ev.get((drum, tick), 0), vel)

    sixteenth = level >= 4
    if feel == "SW":
        for tick, vel in ((0, 84), (8, 76)):
            d_(BASEDRUM, tick, vel)
        for tick, vel in ((4, 84), (12, 86)):
            d_(SNARE, tick, vel)
        if level >= 3:
            d_(SNARE, 7, 30); d_(SNARE, 15, 30)
            d_(BASEDRUM, 11, 62)
        if sixteenth:
            d_(BASEDRUM, 3, 70); d_(BASEDRUM, 6, 60)
        if level >= 1 and not sparse:
            for tick in (3, 7, 11, 15):                               # the ride on the swung off eighths
                d_(HAT, tick, 38)
            for tick in (0, 4, 8, 12):
                d_(HAT, tick, 30)
    elif feel == "ST":
        for tick, vel in ((0, 96), (4, 70), (8, 92), (12, 70)):
            d_(BASEDRUM, tick, vel)
        for tick in (2, 6, 10, 14):
            d_(SNARE, tick, 66 if tick % 8 == 6 else 56)
        if level >= 2:
            for tick in (4, 12):
                d_(SNARE, tick, 84)
        if level >= 3 and not sparse:
            for tick in range(0, 16, 2):
                d_(HAT, tick, 34)
    elif feel == "TR":
        for tick in (0, 5, 8, 13):
            d_(BASEDRUM, tick, 90 if tick % 8 == 0 else 70)
        for tick in (3, 11):
            d_(SNARE, tick, 84)
        if level >= 2:
            d_(SNARE, 15, 60); d_(SNARE, 7, 56)
        if level >= 1:
            for tick in (0, 3, 5, 8, 11, 13):
                d_(HAT, tick, 34)
    elif feel == "HT":
        d_(BASEDRUM, 0, 100); d_(BASEDRUM, 6, 62); d_(BASEDRUM, 11, 60)
        d_(SNARE, 8, 100)
        if level >= 1:
            d_(HAT, 4, 40); d_(HAT, 12, 40)
        if level >= 3:
            d_(SNARE, 15, 30)
    elif feel == "SB":
        d_(BASEDRUM, 0, 100); d_(SNARE, 0, 92)
    elif feel == "MR":
        for tick in range(0, 16, 2):
            d_(SNARE, tick, 30 + 3 * tick + (10 if tick % 4 == 0 else 0))
        d_(BASEDRUM, 0, 96); d_(BASEDRUM, 8, 92)
    if sixteenth and feel not in ("SB", "MR"):                        # the machine gun of the drums: the hat in every sixteenth
        for tick in range(16):
            d_(HAT, tick, 38 if tick % 4 == 0 else (30 if tick % 2 == 0 else 20))
    if phase == 1 and feel != "SB":                                   # the drums glitch: a burst of single-tick hits at the end of the bar
        burst = hash_(bar, 9) % 3
        if burst == 0:
            for tick in (13, 14, 15):
                d_(BASEDRUM, tick, 60)
        elif burst == 1:
            for tick in (12, 13, 14, 15):
                d_(SNARE, tick, 50 + 8 * (tick - 12))
        else:
            for tick in range(8, 16):
                d_(HAT, tick, 30 + 3 * (tick - 8))
    if fill:
        for key_ in [k for k in ev if k[1] >= 8 and k[0] in (SNARE, HAT)]:
            del ev[key_]
        for count, tick in enumerate(range(8, 16)):
            d_(SNARE, tick, 38 + 7 * count)
    if accel:
        for key_ in [k for k in ev if k[1] >= 4 and k[0] in (SNARE, HAT)]:
            del ev[key_]
        for index, tick in enumerate((4, 8, 10, 12, 13, 14, 15)):
            d_(SNARE, tick, 40 + 9 * index)
            if index % 2 == 0:
                d_(BASEDRUM, tick, 70 + 4 * index)
    if toms is not None:                                              # tom-roll fill-in: the drums of the last ticks are replaced by the rolling toms
        for key_ in [k for k in ev if k[1] >= toms]:
            del ev[key_]
    for (drum, tick), vel in ev.items():
        role = {BASEDRUM: "kick", SNARE: "snare", HAT: "hat"}[drum]
        b.drum(role, t0 + tick, drum, vel * (quiet if quiet < 1 and drum != BASEDRUM else 1.0))
    if toms is not None:
        tom_roll(b, t0, toms, 16, ascending=toms_up)


def lead_bar(b, t0, phase, bar, feel, level, quiet, key, sec):
    """The tune of one bar. A: on the off beats, chattering; B: half-ed (every note twice as long, 4 bars of tune over 8); CHORUS: the catchy form in the top of the range (major),
    from the second half with a flute descant in long notes; OUTRO: the plain tune. The mirror voice answers with the upside-down reflection."""
    unit, ub = divmod(bar, 4)
    plan = PLAN[unit]
    mode = plan["mode"]
    chorus = sec == "CHORUS"
    fix = major if mode == "M" else (lambda s: s)
    seg = ub // 2
    code = plan["leads"][seg]
    inst = INST[code]
    if phase == 1:
        inst = {FLUTE: BIT, I_: BIT, XYLOPHONE: PLING}.get(inst, inst) if hash_(bar, 1) % 3 else inst
    if chorus and (plan["mel"] != "A" or code in "FX"):
        inst = FLUTE                                               # the top of the range belongs to the flute (the tunes B and C lie above the harp's F#5)
    mel = MEL[plan["mel"]]
    if sec == "B":
        pos = (unit % 2) * 4 + ub
        tune_bar, half = divmod(pos, 2)
        semis = [fix(s) for _, s in mel[tune_bar]]
        notes = halved(mel[tune_bar], half)
    else:
        events = melody_bar(phase, bar, feel)
        events = [(t, fix(s)) for t, s in events]
        if sec == "A":
            events = offbeat(events, bar, phase)
        semis = [fix(s) for bb in (seg * 2, seg * 2 + 1) for _, s in mel[bb]]
        notes = [(t, s, 0) for t, s in events]
    n = place(inst, key, semis, TARGET_CHORUS if chorus else None)
    mirror = plan["mirror"]
    axis = AXIS[plan["mel"]]
    if phase == 1:
        axis += (hash_(bar, 4) % 3) - 1                          # the axis of the cracked mirror wobbles
    mode_m = mirror[1] if mirror else None
    play_lead = True
    play_mirror = False
    if mirror and level >= 3:
        if mode_m == "sim":
            play_mirror = True
        elif mode_m == "alt":
            play_mirror = ub % 2 == 1
            play_lead = not play_mirror
        elif mode_m == "call":
            play_mirror = seg == 1
            play_lead = not play_mirror
        elif mode_m == "echo":
            play_mirror = True

    def voice(role, instrument, pitch, tick, length, vel):
        if length >= 5:
            sustain(b, role, t0 + tick, instrument, pitch, length, vel, vel + 10, every=4)
        else:
            b.note(role, t0 + tick, instrument, pitch, vel)

    for tick, s, length in notes:
        accent = 9 if tick % 8 == 0 else 0
        vel = (74 + accent) * quiet
        if play_lead:
            voice("lead", inst, TONIC + key + s + n, tick, length, vel)
            if level >= 3 and plan["leads"][seg] != "G" and not (sec == "A"):
                high = inst in (FLUTE, XYLOPHONE)
                if length < 5 or sec != "B":
                    b.note("dbl", t0 + tick, I_ if high else FLUTE, TONIC + key + s + n + (-12 if high else 12), vel * 0.55)
    if play_mirror:
        m_inst = INST[mirror[0]]
        all_m = [2 * axis - fix(s) for bb in range(4) for _, s in mel[bb]]
        mn = place(m_inst, key, all_m, TARGET_CHORUS if chorus else None)
        delay = BEAT if mode_m == "echo" else 0
        for tick, s, length in notes:
            vel = (68 + (8 if tick % 8 == 0 else 0)) * quiet * (0.8 if mode_m == "echo" else 1.0)
            t_ = tick + delay
            if t_ < 16:
                voice("mirror", m_inst, TONIC + key + 2 * axis - s + mn, t_, length, vel)
    if chorus and unit >= 18:                                  # the harp descant: the strong beats of the tune held, below the flute
        base = [(t, fix(s)) for t, s in mel[ub]]
        for tick, s, length in long_melody(bar, base):
            v0 = 70 if tick % 8 == 0 else 64
            pitch = near((TONIC + key + s) % 12, 72, HARP)
            sustain(b, "dbl", t0 + tick, HARP, pitch, length, v0 + 10, v0 + 22, every=4)


BEAT = 4


def arp_bar(b, t0, bar, chords, level):
    """Phase 2: a chip arpeggio flickers over the chord (up and down, with holes); in the peak it only sparkles on the off beats."""
    for tick in range(0, 16, 1 if level >= 4 else 2):
        pc, chord, off = chords[1] if len(chords) > 1 and tick >= chords[1][2] else chords[0]
        tones = [pc + i for i in chord[1]]
        order = [0, 1, 2, 3, 2, 1]
        step = tick % 6
        if hash_(bar, tick, 6) % (3 if level >= 4 else 2) == 0:
            continue
        if level >= 4 and tick % 2 == 0:
            continue
        b.note("arp", t0 + tick, BIT, near(tones[order[step]], 66 + 3 * order[step], BIT), 40 + (hash_(bar, tick) % 14))


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    titles = ("The Cracked Mirror Laughs (Boss007)", "The Cracked Mirror Laughs, Shattered (Boss007)")
    descriptions = ("Mirror boss, phase 1, class 4 (carnival of madness): style 3 (symphonic open), 180 BPM: opening on the chorus's last phrase, sparse off-beat A, half-ed tune in B, a one-bar bell break, the chorus in the same-tonic major on a sixteenth bass, tom-roll outro; a tune answered by its upside-down reflection. Loops from tick 64.",
                    "Mirror boss, phase 2: the same 108 bars, events and chords, the mirror cracked - the machine gun starts earlier, the feel changes every bar, the key every 2 bars, the tune in shards, chiptune glitch and drums.")
    edges = [("opening", 0, START)]
    cursor = START
    for name, units in SECTIONS:
        edges.append((name, cursor, cursor + units * 4 * BAR_T))
        cursor += units * 4 * BAR_T
    for phase in range(2):
        b = play(phase)
        target = out / ("mirror_phase1.nbs" if phase == 0 else "mirror_phase2.nbs")
        layers = finish(target, b, TPS, titles[phase], descriptions[phase])
        print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
        bass = [layer for layer in layers if layer.name.startswith("bass")]
        print("   bass notes per bar by section: " + ", ".join(
            f"{n} {sum(1 for layer in bass for t in layer.notes if s <= t < e) / ((e - s) / 16):.1f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
