#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "Waltz at the Edge of Dark, Out of Step (Boss008)", a tragic waltz in two phases.

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

Boss class 2 "sorrow and fated battle" as an elegant, doomed duel, built on STYLE 1 of the style doc ("royal high-speed battle") adapted to 3/4 (t277) and on the subdivision guide of t276: base tempo 150 BPM,
a song tick is a sixteenth (10 ticks a second), a beat is 4 ticks, a bar of 3/4 is 12 ticks (1.2 s; eighth 2, sixteenth 1). A bar of 12 ticks meets the engine's 16-tick line every 4 bars, so every section
is a multiple of 4 bars. The loop starts at tick 64 = a 4-tick silence and five bars of intro; the body is 108 bars, 1360 ticks, 136 s. Time table (the style's, scaled 1:1; bars counted from the song start):
    0:00 -  0:15  intro (bars 0-12)  a single-line solo-string refrain, NO drums; from the loop point (bar 5) the whole tune once more with the snare getting denser, the last four bars most
    0:15 -  0:45  A     (13-36)      full drums, the bass nails the root (eighth repeats), the tune in E minor. Gimmick: pseudo HALF TIME (the drums strike once or twice a bar, the tune goes on)
    0:45 -  1:14  B     (37-60)      BASS RELEASE: the bass runs up and down (16th doubles, dotted eighths = 4 against 3, walks), the lament an OCTAVE UP (flute). Gimmick: pseudo DOUBLE TIME (the snare on every beat)
    1:14 -  1:20  pre-break and BREAK (61-64)  stop-time, then ONE bar with every drum and the bass muted and a single high bell (B6)
    1:20 -  1:49  CHORUS (65-88)     the RELATIVE MAJOR, G, with a 3rd-above harmony; the bass is the full 16th machine gun, the tune ENLARGED to half and dotted-half notes (three statements)
    1:49 -  2:16  FINALE (89-112)    the whole structure a WHOLE STEP UP (A major): the tune with its own rhythm over the machine gun, then the long notes twice; the last bar is B7, back into the loop
The tune (8 bars, E minor): a fifth up into the tonic (B4 E5), a leap to the leading tone's F#5 held over B7, a sigh down (E5 B4), a fall to A4, then the same rise and a turn (F#5 E5 D#5 F#5), a descent over C
(E5 D5 C5) and an arpeggio of B7 that hangs on F#5. The major sections have D natural for D#, so the same tune is re-harmonised in G (G D7 G C | G D7 C D7). The lead is a solo violin made of pling /
bit / flute / harp whose long notes are struck again every eighth with the loudness breathing.
Phase 2 'Out of Step' has the same events with the footing lost: the bass and drums fall into hemiola (two bars of 3/4 felt as three of 3/2: accents at 0, 8, 16 of 24 ticks), a statement of the tune comes a
beat late against the beat (and is cut off to catch up), the bass stings a half step above (F against E), and the bass climbs the ladder earlier (sixteenths from A's end).
Bass ladder: intro quarters -> A eighths -> B eighths with 16th doubles, dotted eighths and walks -> bursts (a beat of eighths, two of sixteenths) -> chorus and finale 16th machine gun.
Sections (bars of the body): intro 8, A 24, B 24, pre-break 3, break 1, chorus 24, finale 24. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Waltz at the Edge of Dark (Boss008)", "Waltz at the Edge of Dark, Out of Step (Boss008)")
FILES = ("waltz_phase1.nbs", "waltz_phase2.nbs")
DESCRIPTIONS = ("Waltz boss, phase 1 (sorrow and fated battle): a tragic 3/4 waltz at 150 BPM in E minor, a bass release, a bell break, the relative major with a third above, and a whole-step finale.",
                "Waltz boss, phase 2: the same waltz out of step - hemiola in the bass and drums, the tune a beat late, a half-step sting in the bass, an earlier machine gun.")
TPS, BAR, INTRO, BODY = 10, 12, 5, 108
# t279: the piece is written 7 semitones lower than in t277 (A minor / C major / D major instead of E minor / G major / A major) so that the tune lives at 60-72 and the bass roots stay low.
# Only the lament (B) and the pre-break stay in E minor (the flute needs the notes there); then it falls back for the break.
KEY, KEY_B = -7, 0
_kit_sing = sing


def sing(score, bar, text, voices, transpose=0, key=None, **kw):
    return _kit_sing(score, bar, text, voices, transpose=transpose + (KEY if key is None else key), **kw)


def K(name):
    return shift_chord(name, KEY)


G_MAJOR = tuple((p + KEY) % 12 for p in (7, 9, 11, 0, 2, 4, 6))
A_MAJOR = tuple((p + KEY) % 12 for p in (9, 11, 1, 2, 4, 6, 8))


def maj(text):
    return text.replace("D#", "D")


# --- the tune and its sisters (12 ticks a bar: a beat is 4, an eighth 2, a sixteenth 1) ---------------------------------------
T = ["B4:8 E5:4", "D#5:4 F#5:8", "E5:8 B4:4", "C5:8 A4:4", "B4:8 E5:4", "F#5:4 E5:2 D#5:2 F#5:4", "E5:6 D5:2 C5:4", "A4:4 D#5:4 F#5:4"]
TV = ["B4:8 E5:4", "D#5:2 E5:2 F#5:8", "E5:6 D5:2 B4:4", "C5:6 B4:2 A4:4", "B4:2 B4:2 B4:4 E5:4", "F#5:6 E5:2 D#5:4", "E5:6 D5:2 C5:2 D5:2", "A4:4 D#5:4 F#5:4"]
LAMENT = ["A4:4 C5:4 E5:4", "F#5:8 D5:4", "D5:8 B4:4", "E5:8 C5:4", "A4:4 C5:4 E5:4", "F#5:8 D#5:4", "G4:4 B4:4 E5:4", "D#5:4 F#5:8"]
LAMENT2 = list(LAMENT)
LAMENT2[0] = "A4:4 C5:2 E5:6"
LAMENT2[2] = "D5:6 E5:2 B4:4"
SEQ = ["B4:8 E5:4", "D5:8 G5:4", "E5:8 A5:4", "F#5:8 B5:4", "G5:4 B5:4 E6:4", "E6:8 D6:2 C6:2", "C6:4 B5:4 A5:4", "D#6:4 F#6:8"]
# the tune ENLARGED for the chorus: long notes held by re-striking (three statements; the third climbs to A5 at its end)
PK = ["B4:4 E5:8", "D#5:4 F#5:8", "E5:12", "C5:8 A4:4", "B4:4 E5:8", "F#5:8 D#5:4", "E5:12", "D#5:4 F#5:8"]
PK2 = ["B4:4 E5:8", "D#5:4 F#5:8", "E5:8 F#5:4", "C5:8 A4:4", "B4:4 E5:8", "F#5:12", "E5:4 C5:8", "D#5:4 F#5:8"]
PK3 = ["B4:4 E5:8", "D#5:4 F#5:8", "E5:8 F#5:4", "C5:4 E5:8", "B4:4 E5:8", "F#5:4 E5:4 D#5:4", "E5:8 C5:4", "A4:2 D#5:2 F#5:4 A5:4"]
CH_MIN = {"T": [K(c) for c in ["Em", "B7", "Em", "Am", "Em", "B7", "C", "B7"]], "W": ["Am", "D", "G", "C", "Am", "B7", "Em", "B7"], "C": ["Em", "G", "Am", "B7", "Em", "C", "Am|B7", "B7"]}
CH_MAJ = [K(c) for c in ["G", "D7", "G", "C", "G", "D7", "C", "D7"]]
CH_UP = [K(c) for c in ["A", "E7", "A", "D", "A", "E7", "D", "E7"]]

# --- patterns of a bar of 12 ticks ------------------------------------------------------------------------------------------
HAT16 = [(t, 46 if t % 4 == 0 else (36 if t % 2 == 0 else 26)) for t in range(12)]
DRUMS = {
    "waltz": {"kick": [(0, 100), (6, 50)], "snare": [(4, 80), (8, 80)], "hat": [(t, 40 if t % 4 == 0 else 28) for t in range(0, 12, 2)]},
    "drive": {"kick": [(0, 100), (6, 60), (10, 50)], "snare": [(4, 92), (8, 96), (11, 44)], "hat": [(t, 46 if t % 4 == 0 else 30) for t in range(0, 12, 2)]},
    "drive16": {"kick": [(0, 100), (6, 60), (10, 50)], "snare": [(4, 92), (8, 96), (11, 44)], "hat": HAT16},
    # pseudo half time: the drums strike half as often (one kick, one snare on the third beat)
    "half": {"kick": [(0, 96)], "snare": [(8, 90)], "hat": [(t, 28 if t % 4 == 0 else 20) for t in range(0, 12, 2)]},
    # pseudo double time: the snare on EVERY beat, the kick on the off-beats, sixteenth hats
    "dbl": {"kick": [(2, 86), (6, 80), (10, 86)], "snare": [(0, 90), (4, 96), (8, 96)], "hat": HAT16},
    "peak": {"kick": [(0, 100), (2, 50), (6, 70), (10, 60)], "snare": [(4, 100), (8, 100), (11, 56)], "hat": HAT16, "tom": [(3, 50, 57), (7, 50, 54)]},
    "quiet": {"kick": [(0, 92)], "snare": [(8, 64)], "hat": [(4, 28), (8, 28)]},
    "stop": {"kick": [(0, 100), (8, 90)], "snare": [(0, 90), (8, 90)]},
    # hemiola: two bars of 3/4 felt as three of 3/2 (the strong beats at 0, 8, 16 of the 24 ticks: bar A holds 0 and 8, bar B holds 4)
    "hemiA": {"kick": [(0, 100), (8, 96)], "snare": [(4, 84), (11, 44)], "hat": [(t, 44 if t % 4 == 0 else 26) for t in range(0, 12, 2)]},
    "hemiB": {"kick": [(4, 96), (11, 44)], "snare": [(0, 84), (8, 84)], "hat": [(t, 44 if t % 4 == 0 else 26) for t in range(0, 12, 2)], "tom": [(6, 46, 54)]},
    "hemiPA": {"kick": [(0, 100), (2, 56), (8, 96), (10, 56)], "snare": [(4, 90), (6, 60), (11, 50)], "hat": HAT16},
    "hemiPB": {"kick": [(4, 98), (6, 56), (11, 50)], "snare": [(0, 90), (2, 56), (8, 90), (10, 56)], "hat": HAT16, "tom": [(7, 50, 54)]},
}


def eighths(accents, base=46):
    return [(t, "r", accents.get(t, base)) for t in range(0, 12, 2)]


def sixteenths(accents, octave=(3, 7, 11), fifth=(), base_even=50, base_odd=42):
    return [(t, "o" if t in octave else ("f" if t in fifth else "r"), accents.get(t, base_even if t % 2 == 0 else base_odd)) for t in range(12)]


BASS_PAT = {  # (tick, kind, velocity); an integer kind is a MIDI note
    "Q": [(0, "r", 64), (4, "r", 36), (8, "r", 36)],
    "E": eighths({0: 100, 4: 66, 8: 66}),
    "E_soft": [(t, "r", 52 if t == 0 else 36) for t in range(0, 12, 2)],
    # the root moves a little: octave jumps and a fifth (the repeats never run for 20 seconds)
    "E_mv": [(0, "r", 100), (2, "r", 48), (4, "o", 66), (6, "r", 50), (8, "f", 66), (10, "o", 50)],
    "halfb": [(0, "r", 98), (6, "r", 50), (8, "r", 72)],
    # eighths with sudden 16th doubles (a note at 5 and 11 right after the eighth before it)
    "ED_a": [(0, "r", 100), (2, "r", 48), (4, "r", 66), (5, "r", 40), (6, "r", 50), (8, "r", 66), (10, "r", 50), (11, "o", 42)],
    # the same, the line moving up and down (root, fifth, third, back)
    "ED_b": [(0, "r", 100), (2, "r", 50), (4, "f", 66), (5, "f", 40), (6, "t", 52), (8, "f", 66), (10, "t", 50), (11, "r", 42)],
    # dotted eighths, 3+3+3+3: four against three
    "TRI": [(0, "r", 100), (3, "r", 60), (6, "f", 72), (9, "t", 62)],
    "WALK": [(0, "r", 100), (2, "r", 50), (4, "t", 66), (6, "f", 52), (8, "o", 66), (10, "f", 50), (11, "t", 42)],
    # one beat of eighths, then two beats of sixteenths
    "BURST": [(0, "r", 100), (2, "r", 50)] + [(t, "o" if t in (7, 11) else "r", 70 if t == 4 else 56 if t % 2 == 0 else 44) for t in range(4, 12)],
    "S16": sixteenths({0: 100, 4: 72, 8: 72}),
    "S16b": sixteenths({0: 100, 4: 72, 8: 72}, fifth=(5, 9)),
    "S16sting": [(0, "r", 100), (1, "r", 42), (2, "r", 50), (3, "o", 42), (4, "r", 72), (5, "r", 42), (6, "r", 50), (7, "o", 42)] + [(t, 41, 96 if t == 8 else 60 if t % 2 == 0 else 46) for t in range(8, 12)],
    "hemiA": eighths({0: 100, 8: 94}),
    "hemiB": [(t, "o" if t == 10 else "r", 96 if t == 4 else 46) for t in range(0, 12, 2)],
    "hemiPA": sixteenths({0: 100, 8: 96}),
    "hemiPB": sixteenths({4: 98, 0: 50}),
    "STOP": [(0, "r", 100), (8, "r", 90)],
    "END": [(0, "r", 96), (4, "r", 70), (8, "r", 50)],
}
GUITAR_PAT = {
    "arp": [(t, i, v) for t, i, v in zip(range(0, 12, 2), (0, 2, 3, 2, 3, 2), (52, 36, 40, 36, 40, 36))],
    "chug": [(t, 0, 66 if t == 0 else 46) for t in range(0, 12, 2)],
}
HARP_ARP = [(t, i, 30) for t, i in zip(range(0, 12, 2), (0, 1, 2, 3, 2, 1))]
HARP_Q = [(t, i, 30) for t, i in zip((2, 6, 10), (1, 2, 3))]
HARP_SLOW = [(0, 0, 34), (4, 1, 30), (8, 2, 30)]
DRONE = [(0, "r", 54), (6, "r", 34)]


def pick(plan, i):
    """plan: [(first bar, name), ...] ascending; the entry of bar i."""
    name = plan[0][1]
    for first, n in plan:
        if i >= first:
            name = n
    return name


def build(P):
    s = Score(TPS, BAR, INTRO, BODY)

    pling = Voice("lead", PLING, 0, 1.0, "cry", 2, octave_fit=True)
    bit = lambda g=0.55, off=0: Voice("dbl", BIT, 0, g, "cry", 4 if off else 2, octave_fit=True, offbeat=off)
    harp_hit = lambda g=0.55: Voice("dbl", HARP, 0, g, "none", octave_fit=True)
    flute12 = lambda g=0.6, off=0: Voice("oct", FLUTE, 12, g, "cry", 4 if off else 2, octave_fit=True, offbeat=off)
    gtr_lo = lambda g=0.7, off=0: Voice("low", GUITAR, -12, g, "cry", 4 if off else 2, octave_fit=True, offbeat=off)
    hand = [Voice("lead", PLING, 0, 1.0, "cry", 2, high=77), Voice("lead", FLUTE, 0, 1.0, "cry", 2, low=78)]     # the pling takes the tune as high as it sounds, the flute carries on

    def harmony(scale, g=0.55):
        return [Voice("harm", FLUTE, 12, g, "cry", 2, scale=scale)]      # the third above rings an octave up, on the flute

    def fill(j, length=6, hot=False):
        t0 = s.tick(s.b(j)) + 12 - length
        roll(s, t0, t0 + length, 1 if hot else 2, 40, 98)

    def hemi(name, i, on):
        """phase 2: in the bars of ``on`` the bass and the drums fall into hemiola (an even bar holds the pair's first half)."""
        if P == 1 and i in on:
            return ("hemiA" if i % 2 == 0 else "hemiB")
        return name

    def acc(j, i, chord, bass_name, drum_name=None, guitar=None, harp=None, drone=False, pad=False, level=1.0, drum_level=1.0):
        bar = s.b(j + i)
        if bass_name:
            bass(s, bar, chord, BASS_PAT[bass_name], level, high=54)
        if drone:
            bass(s, bar, chord, DRONE, level, role="drone", instrument=DIDGERIDOO)
        if drum_name:
            drums(s, bar, DRUMS[drum_name], drum_level)
        if guitar == "pah":
            for t in (4, 8):
                stab(s, bar, t, chord, "gtr", GUITAR, 47, 64, (54 if t == 4 else 46) * level, count=3)
        elif guitar:
            notes_from(chord, GUITAR_PAT[guitar], "gtr", GUITAR, 45, 66, s, bar, level)
        if harp:
            notes_from(chord, harp, "arp", HARP, 54, 72, s, bar, level)
        if pad:
            stab(s, bar, 0, chord, "pad", PLING, 50, 68, 26 * level, count=2, length=12, every=4, style="flat")

    TC, WC = CH_MIN["T"], CH_MIN["W"]

    # --- intro (bars 0-4, played once, after four silent ticks): the first bars of the tune as a single line, like a solo string; no drums; phase 2 a beat late ---
    late = 4 if P else 0
    iv = [pling, harp_hit(0.6)] + ([gtr_lo(0.6)] if P else [])
    for k, ti in enumerate((0, 1, 2, 3, 7)):
        sing(s, k, T[ti], iv, vel=86 if P == 0 else 92, start=late if k < 2 else 0, cut=(k == 1))
        if k >= 2 or P:
            bass(s, k, TC[ti], BASS_PAT["Q"], 1.0, high=54)
        notes_from(TC[ti], HARP_Q, "arp", HARP, 54, 72, s, k, 0.8)
    # --- intro (body 0-7, from the loop point): the whole tune once more; the snare gets denser bar by bar (most in the last four), the bass climbs quarters -> eighths ---
    ramp = [[], [], [], [(8, 40)], [(8, 46)], [(4, 50), (8, 56)], [(4, 56), (6, 60), (8, 64), (10, 66)], [(t, 52 + 4 * t // 2) for t in range(0, 8, 2)]]
    for k in range(8):
        sing(s, s.b(k), T[k], iv, vel=72 if P == 0 else 84, start=2 if (P and k in (1, 2)) else 0, cut=(P == 1 and k == 2))
        bass(s, s.b(k), TC[k], BASS_PAT["Q" if k < 4 else "E_soft"], 0.8 if P == 0 else 1.0, high=54)
        notes_from(TC[k], HARP_Q, "arp", HARP, 54, 72, s, s.b(k), 0.9)
        for t, v in ramp[k]:
            s.drum("snare", s.tick(s.b(k), t), SNARE, v * (1.0 if P == 0 else 1.15))
    s.drum("kick", s.tick(s.b(6)), BASEDRUM, 60)
    s.drum("kick", s.tick(s.b(7)), BASEDRUM, 84)
    roll(s, s.tick(s.b(7), 8), s.tick(s.b(8)), 1, 60, 100)

    # --- A (24 bars, body 8-31): full drums, the bass nails the root; the tune in E minor; bars 12-15 of A: pseudo half time ---------------------------------------
    A_BASS = [[(0, "E"), (10, "E_mv"), (12, "halfb"), (16, "ED_a"), (20, "ED_b")], [(0, "E"), (6, "ED_a"), (10, "E_mv"), (12, "halfb"), (16, "BURST"), (20, "ED_b")]]
    A_DRUM = [[(0, "waltz"), (4, "drive"), (12, "half"), (16, "drive")], [(0, "waltz"), (4, "drive"), (12, "half"), (16, "peak")]]
    for i in range(24):
        r, k = divmod(i, 8)
        if r == 0:
            text, v = T[k], [pling, harp_hit(0.5)] + ([flute12(0.45, 2)] if k >= 4 else [])
        elif r == 1:
            text, v = TV[k], [pling, bit(0.6, 2), flute12(0.6, 2)] + ([gtr_lo(0.7, 2)] if k >= 4 else [])
        else:
            text, v = T[k], [pling, bit(0.6, 2), flute12(0.7, 2), gtr_lo(0.7, 2)]
        displaced = P == 1 and r == 2 and k >= 4
        sing(s, s.b(8 + i), text, v, vel=(86 if P == 0 else 90) + 2 * r, start=4 if displaced else 0, cut=displaced and k == 7)
        bn, dn = hemi(pick(A_BASS[P], i), i, {6, 7, 22, 23}), hemi(pick(A_DRUM[P], i), i, {6, 7, 22, 23})
        half = 12 <= i < 16
        acc(8, i, TC[k], bn, dn, "pah" if not half else None, HARP_ARP if (r >= 1 and not half) else None, drone=(r >= 1 and not half), level=0.9 if r == 0 else 0.95)
        if k in (3, 7):
            fill(8 + i, 4 if k == 3 else 6, hot=(r >= 1))

    # --- B (24 bars, body 32-55): BASS RELEASE - the bass runs up and down; the lament an octave up (flute) with the pling under it; bars 10-19: pseudo double time ---
    B_BASS = [[(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "ED_b"), (4, "ED_a"), (5, "TRI"), (6, "WALK"), (7, "ED_b"), (8, "BURST"), (9, "ED_b"), (10, "BURST"), (11, "ED_b"), (12, "BURST"),
               (13, "ED_b"), (14, "BURST"), (15, "ED_b"), (16, "BURST"), (17, "ED_b"), (18, "BURST"), (19, "ED_b"), (20, "BURST"), (22, "S16")],
              [(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "BURST"), (4, "ED_b"), (5, "TRI"), (6, "WALK"), (7, "BURST"), (8, "S16"), (9, "BURST"), (10, "S16"), (11, "BURST"), (12, "S16"),
               (13, "BURST"), (14, "S16"), (15, "BURST"), (16, "S16"), (17, "BURST"), (18, "S16"), (19, "BURST"), (20, "S16"), (22, "S16b")]]
    for i in range(24):
        r, k = divmod(i, 8)
        if r == 0:
            text, c = LAMENT[k], WC[k]
            v = [Voice("lead", FLUTE, 12, 1.0, "cry", 2, octave_fit=True), Voice("dbl", PLING, -12, 0.95, "cry", 2, octave_fit=True), Voice("echo", HARP, -12, 0.5, "none", delay=4, octave_fit=True)]
        elif r == 1:
            text, c = LAMENT2[k], WC[k]
            v = [Voice("lead", FLUTE, 12, 1.0, "cry", 2, octave_fit=True), Voice("dbl", PLING, -12, 0.95, "cry", 2, octave_fit=True), Voice("low", GUITAR, -12, 0.85, "cry", 2, octave_fit=True)]
        else:
            text, c = SEQ[k], CH_MIN["C"][k]
            v = [Voice("lead", PLING, -12, 1.0, "cry", 2), Voice("lead", FLUTE, 0, 1.0, "cry", 2, low=78), Voice("low", GUITAR, -12, 0.85, "cry", 2, octave_fit=True)]
        sing(s, s.b(32 + i), text, v, key=KEY_B, vel=90 if P == 0 else 94)
        dbl = 10 <= i < 20
        dn = "dbl" if (dbl or i >= 20) else ("drive16" if (P == 0 or i < 2) else "peak")
        if P and not dbl and i >= 8 and i < 10:
            dn = "peak"
        bn, dn = hemi(pick(B_BASS[P], i), i, {4, 5}), hemi(dn, i, {4, 5})
        acc(32, i, c, bn, dn, "chug" if i < 12 else None, HARP_ARP if i >= 8 else None, drone=(i >= 8), level=1.0)
        if k in (3, 7):
            fill(32 + i, 4 if k == 3 else 6, hot=True)

    # --- pre-break (body 56-58): the dominant, a rising roll, then STOP-TIME (stabs on beats 1 and 3, nothing between); break (body 59): ALL silent but one bell ---
    sing(s, s.b(56), "A4:4 D#5:4 F#5:4", [pling, bit(0.6, 2), flute12(0.7, 2), gtr_lo(0.7, 2)], key=KEY_B, vel=96)
    acc(56, 0, "B7", "ED_b", "drive16", None, None, level=1.0)
    stab(s, s.b(56), 0, "B7", "stab", GUITAR, 45, 64, 80, count=3, length=4, every=2)
    sing(s, s.b(57), "F#5:12", [pling, flute12(0.8, 2)], key=KEY_B, vel=92)
    acc(57, 0, "B7", "BURST", "drive16", None, None, level=1.0)
    roll(s, s.tick(s.b(57), 4), s.tick(s.b(58)), 1, 50, 100)
    sing(s, s.b(58), "F#5:12", [pling, flute12(0.8, 2)], key=KEY_B, vel=92)
    bass(s, s.b(58), "B7", BASS_PAT["STOP"], 1.0, high=54)
    drums(s, s.b(58), DRUMS["stop"], 1.0)
    for t in (0, 8):
        stab(s, s.b(58), t, "B7", "stab", GUITAR, 45, 64, 90, count=3, length=4, every=2)
    bell(s, s.tick(s.b(59)), 95, 80)

    # --- CHORUS (24 bars, body 60-83): the relative MAJOR with a third above; the tune ENLARGED over the 16th machine gun, three statements ----------------------------
    for r in range(3):
        j0 = 60 + 8 * r
        for i in range(8):
            c = CH_MAJ[i]
            text = maj((PK, PK2, PK3)[r][i])
            v = [pling, flute12(0.95), gtr_lo(0.95)] + harmony(G_MAJOR, 0.9)
            if r >= 1:
                v.append(harp_hit(0.5))
            displaced = P == 1 and r == 2 and i < 7
            sing(s, s.b(j0 + i), text, v, vel=100, start=4 if displaced else 0, cut=displaced and i == 6)
            if P == 1 and r == 1:
                name = "hemiPA" if i % 2 == 0 else "hemiPB"
            elif P == 1 and i == 7:
                name = "S16sting"
            else:
                name = "S16b" if (r == 2 and i % 2) else "S16"
            bass(s, s.b(j0 + i), c, BASS_PAT[name], 1.0, high=54)
            drums(s, s.b(j0 + i), DRUMS[name if name.startswith("hemi") else "peak"], 1.0)
            if i % 2 == 0:
                bass(s, s.b(j0 + i), c, DRONE, 0.9, role="drone", instrument=DIDGERIDOO)
            if r == 2:
                notes_from(c, HARP_ARP, "arp", HARP, 54, 72, s, s.b(j0 + i), 0.8)
        stab(s, s.b(j0), 0, K("G"), "stab", PLING, 52, 72, 92, count=3, length=6, every=2)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        fill(j0 + 3, 4, hot=True)
        fill(j0 + 7, 6, hot=bool(P))

    # --- FINALE (24 bars, body 84-107): the whole structure a WHOLE STEP UP (A major): the tune with its own rhythm, then the long notes twice; the last bar is B7 ---
    for r in range(3):
        j0 = 84 + 8 * r
        for i in range(8):
            c = CH_UP[i]
            if r == 0:
                text = maj(TV[i])
                v = hand + [flute12(0.95), gtr_lo(0.95)]
            else:
                text = maj((None, PK2, PK3)[r][i])
                v = hand + [flute12(0.95), gtr_lo(0.95), harp_hit(0.5)] + harmony(A_MAJOR, 0.9)
            last = r == 2 and i == 7
            if last:
                text, c = "A4:4 D#5:4 F#5:4", K("B7")
            displaced = P == 1 and r == 1 and i < 7
            sing(s, s.b(j0 + i), text, v, transpose=0 if last else 2, vel=100, start=4 if displaced else 0, cut=displaced and i == 6)
            name = "S16b" if i % 2 else "S16"
            if P == 1 and r == 1:
                name = "hemiPA" if i % 2 == 0 else "hemiPB"
            elif P == 1 and i == 7 and not last:
                name = "S16sting"
            if last:
                bass(s, s.b(j0 + i), c, BASS_PAT["END"], 1.0, high=54)
                for t, vv in ((0, 90), (4, 70), (8, 56)):
                    s.drum("kick", s.tick(s.b(j0 + i), t), BASEDRUM, vv)
                roll(s, s.tick(s.b(j0 + i), 8), s.tick(s.b(j0 + i + 1)), 2, 70, 30)
                continue
            bass(s, s.b(j0 + i), c, BASS_PAT[name], 1.0, high=54)
            drums(s, s.b(j0 + i), DRUMS[name if name.startswith("hemi") else "peak"], 1.0)
        stab(s, s.b(j0), 0, K("A"), "stab", PLING, 52, 72, 96, count=3, length=6, every=2)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        fill(j0 + 3, 4, hot=True)
        if r < 2:
            fill(j0 + 7, 6, hot=True)
    return s


SECTIONS = [("intro", 0, 8), ("A", 8, 24), ("B", 32, 24), ("pre", 56, 3), ("break", 59, 1), ("chorus", 60, 24), ("finale", 84, 24)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in (0, 1):
        score = build(phase)
        finish(score, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], SECTIONS)


if __name__ == "__main__":
    main()
