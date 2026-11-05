#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the reaper of the snow mountain in two phases.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

Boss class 2 "sorrow and fated battle", built on STYLE 1 of the style doc ("royal high-speed battle", t277) and on the subdivision guide of t276: base tempo 150 BPM, a song tick is a sixteenth
(10 ticks a second), a 4/4 bar is 16 ticks (1.6 s), 80 bars of body and a 4-bar intro (1344 ticks, 134.4 s), loop from tick 64. Time table (the style's 0:00 ... 2:15 scaled 1:1; bars counted from the song start):
    0:00 -  0:13  intro (bars 0-7)   a single-line solo-string refrain (the first half of the tune), NO drums; from bar 4 (the loop point) the snare gets denser bar by bar
    0:13 -  0:45  A     (8-27)       full drums, the bass nails the root (eighth-note repeats) - heavy, the tune in minor, eerie. Gimmick: bars 20-23 pseudo HALF TIME (the drums halve, the tune goes on)
    0:45 -  1:17  B     (28-47)      BASS RELEASE: the bass runs up and down (16th doubles, 3-3-2 triplet figures, walks), the tune an OCTAVE UP (flute), a guitar chug. Gimmick: bars 38-45 pseudo DOUBLE TIME
                                      (the snare on every beat, the kick on the off-beats)
    1:17 -  1:20  pre-break + BREAK  (48-50) two bars of stop-time, then ONE bar with all drums (and the bass) muted and a single high bell (A6)
    1:20 -  1:46  CHORUS (51-66)     the PARALLEL (relative) MAJOR, F, with a 3rd-above harmony line; the bass is the full 16th machine gun, the tune ENLARGED to half and whole notes
    1:46 -  2:15  FINALE (67-83)     the whole structure a WHOLE STEP UP (G major): the tune with its own rhythm over the machine gun, then the long notes again, then a bar of A7 back into the loop
The tune (the 'crying melody', eight bars, D minor) is written in the notes of D minor = F major, so it is the SAME NOTES in every mode; the minor sections harmonise it in D minor (Dm Bb F C | Dm Bb Gm A7,
the leading tone C#), the major ones in F (C natural, F Bb F C | F Bb Gm C7).
Phase 2 is the same length and loop point and the same events, one rung higher: its intro and A are already in the major (the old reversal idea) while B drops back to the minor lament, the bass climbs the ladder
earlier (16ths from the second half of A, B and its double time are bursts and machine gun), the unison is thicker (bit, flute an octave up, guitar an octave down) and the drums are doubled.
Bass ladder: intro quarters -> A eighths -> A end / B eighths with 16th doubles, 3-3-2 figures, walks -> bursts (two beats of sixteenths) -> chorus and finale 16th machine gun.
Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Reaper's Arc (Boss004)", "Reaper's Arc, Sundered (Boss004)")
FILES = ("necromancer_phase1.nbs", "necromancer_phase2.nbs")
DESCRIPTIONS = ("Snow mountain reaper, phase 1 (sorrow and fated battle): 150 BPM, a crying violin tune in D minor, a bass release, a bell break, then the relative major with a third above and a whole-step finale.",
                "Snow mountain reaper, phase 2 (limit break and reversal): the same events one rung higher - major intro and A, minor lament, bell break, major chorus with harmony, a finale a whole step up.")
TPS, BAR, INTRO, BODY = 10, 16, 4, 80
# t279: the whole piece is written 5 semitones lower than in t277 (A minor / C major / D major instead of D minor / F major / G major) so that the tune lives at 60-72 and the bass roots stay low.
# Only the lament (B) and the pre-break stay in D minor: they are the one place the song climbs a fourth (the flute needs the notes there), then it falls back for the break.
KEY, KEY_B = -5, 0
_kit_sing = sing


def sing(score, bar, text, voices, transpose=0, key=None, **kw):
    return _kit_sing(score, bar, text, voices, transpose=transpose + (KEY if key is None else key), **kw)


def K(name):
    return shift_chord(name, KEY)


F_MAJOR = tuple((p + KEY) % 12 for p in (5, 7, 9, 10, 0, 2, 4))
G_MAJOR = tuple((p + KEY) % 12 for p in (7, 9, 11, 0, 2, 4, 6))


def maj(text):
    return text.replace("C#5", "C5").replace("C#6", "C6")


# --- the tune (the notes of D minor = F major); a bar is 16 ticks: a quarter is 4, an eighth 2, a sixteenth 1 ----------------------------
A = ["A4:3 _E5:1 D5:6 C5:2 A4:4", "Bb4:6 A4:2 F4:2 G4:2 A4:4", "C5:6 A4:2 F4:2 A4:2 C5:4", "C5:3 _F5:1 E5:8 D5:2 C5:2",
     "A4:3 _E5:1 D5:6 E5:2 D5:4", "D5:6 C5:2 Bb4:4 D5:4", "G4:2 Bb4:2 D5:4 C5:4 Bb4:4", "A4:4 C#5:4 E5:8"]
A2 = list(A)
A2[0] = "A4:2 A4:2 D5:6 C5:2 A4:4"
A2[3] = "C5:2 E5:2 F5:8 E5:2 D5:2"
A2[4] = "A4:4 D5:6 E5:2 F5:4"
A2[7] = "A4:4 C#5:4 E5:4 F5:4"
LAMENT = ["D5:8 C5:2 Bb4:2 D5:4", "C5:6 A4:2 C5:4 F5:4", "Eb5:8 D5:4 Bb4:4", "A4:4 D5:4 F#5:8",
          "Bb4:4 D5:4 F5:6 D5:2", "C5:4 F5:8 E5:2 C5:2", "Bb4:4 Eb5:8 D5:4", "A4:4 C#5:4 E5:8"]
LAMENT2 = list(LAMENT)
LAMENT2[0] = "D5:8 C5:2 Bb4:2 F5:4"
LAMENT2[3] = "A4:4 D5:4 F#5:4 E5:2 D5:2"
SEQ = ["F4:4 Bb4:6 A4:2 F4:4", "G4:4 C5:6 Bb4:2 G4:4", "A4:4 D5:6 C5:2 A4:4", "D5:4 F5:6 E5:2 F5:4"]
# the tune ENLARGED for the chorus: half and whole notes (held by re-striking)
PK = ["A4:4 D5:12", "D5:8 F5:8", "C5:4 F5:12", "E5:8 D5:4 C5:4", "A4:4 D5:12", "F5:8 D5:8", "D5:8 Bb4:8", "C#5:8 E5:8"]
PK2 = ["A4:4 D5:12", "D5:4 F5:12", "C5:4 F5:12", "E5:16", "A4:4 D5:12", "D5:4 F5:12", "D5:8 F5:8", "C#5:4 E5:12"]
CH_MIN = {"A": [K(c) for c in ["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A7"]], "B": ["Gm", "F", "Eb", "D", "Gm", "F", "Eb", "A7"], "C": ["Bb", "C", "Dm", "Dm"]}
CH_MAJ = {"A": [K(c) for c in ["F", "C7", "Dm", "Bb", "F", "C7", "Bb", "C7"]], "CH": [K(c) for c in ["F", "Bb", "F", "C", "F", "Bb", "Gm", "C7"]]}
CH_UP = {"A": [K(c) for c in ["G", "D7", "Em", "C", "G", "D7", "C", "D7"]], "CH": [K(c) for c in ["G", "C", "G", "D", "G", "C", "Am", "D7"]]}

# --- patterns of a bar of 16 ticks ---------------------------------------------------------------------------------------
HAT16 = [(t, 46 if t % 4 == 0 else (34 if t % 2 == 0 else 24)) for t in range(16)]
DRUMS = {
    "rock": {"kick": [(0, 100), (6, 60), (10, 74)], "snare": [(4, 90), (12, 98)], "hat": [(t, 44 if t % 4 == 0 else 30) for t in range(0, 16, 2)]},
    "drive": {"kick": [(0, 100), (3, 62), (6, 76), (8, 98), (11, 62), (14, 76)], "snare": [(4, 96), (12, 100), (15, 46)], "hat": [(t, 46 if t % 4 == 0 else 32) for t in range(0, 16, 2)]},
    "drive16": {"kick": [(0, 100), (3, 62), (6, 76), (8, 98), (11, 62), (14, 76)], "snare": [(4, 96), (12, 100), (15, 46)], "hat": HAT16},
    # pseudo half time: the same bar, but the drums strike half as often (the snare on the third beat only)
    "half": {"kick": [(0, 96), (10, 58)], "snare": [(8, 92)], "hat": [(t, 30 if t % 4 == 0 else 22) for t in range(0, 16, 2)]},
    # pseudo double time: the snare on EVERY beat, the kick on the off-beats, sixteenth hats
    "dbl": {"kick": [(2, 86), (6, 80), (10, 86), (14, 80)], "snare": [(0, 90), (4, 96), (8, 92), (12, 98)], "hat": HAT16},
    "peak": {"kick": [(0, 100), (2, 56), (5, 70), (6, 60), (8, 100), (10, 56), (13, 70), (14, 88)], "snare": [(4, 100), (12, 100), (7, 48), (15, 52)],
             "hat": [(t, 50 if t % 4 == 0 else (36 if t % 2 == 0 else 26)) for t in range(16)], "tom": [(3, 52, 57), (11, 52, 54)]},
    "stop": {"kick": [(0, 100), (8, 90)], "snare": [(0, 90), (8, 90)]},
}


def e8(octave_at=None):
    return [(t, "o" if t == octave_at else "r", 96 if t == 0 else 80 if t == 8 else 60 if t % 4 == 0 else 46) for t in range(0, 16, 2)]


BASS_PAT = {  # (tick, kind, velocity)
    "Q": [(0, "r", 64), (4, "r", 38), (8, "r", 50), (12, "r", 38)],
    "E": e8(),
    "E_soft": [(t, "r", 52 if t == 0 else 36) for t in range(0, 16, 2)],
    "E_o": e8(14),
    # the root moves a little: octave jumps and a fifth (so that the repeats never run for 20 seconds)
    "E_mv": [(0, "r", 96), (2, "r", 48), (4, "r", 62), (6, "o", 56), (8, "r", 80), (10, "f", 52), (12, "r", 60), (14, "o", 56)],
    # pseudo half time: the bass leans on the beats
    "halfb": [(0, "r", 98), (6, "r", 50), (8, "r", 84), (14, "o", 52)],
    # eighths with sudden 16th doubles (a note at 7 and 15 right after the eighth before it)
    "ED_a": [(0, "r", 98), (2, "r", 48), (4, "r", 64), (6, "r", 52), (7, "r", 42), (8, "r", 84), (10, "r", 48), (12, "r", 64), (14, "r", 52), (15, "o", 46)],
    # the same, the line moving up and down (root, third, fifth, back)
    "ED_b": [(0, "r", 98), (2, "r", 50), (4, "r", 66), (5, "r", 40), (6, "t", 56), (8, "f", 84), (10, "f", 50), (12, "t", 66), (13, "t", 40), (14, "r", 56)],
    # 3-3-2 / 3-3-2: the triplet-feel figure
    "TRI": [(0, "r", 98), (3, "r", 62), (6, "f", 72), (8, "r", 86), (11, "t", 62), (14, "o", 68)],
    # a walking line over the bar
    "WALK": [(0, "r", 98), (2, "r", 50), (4, "t", 66), (6, "t", 50), (8, "f", 84), (10, "f", 50), (12, "o", 70), (13, "f", 42), (14, "t", 56), (15, "r", 42)],
    # four steady eighths, then the machine gun for the last two beats
    "BURST": [(0, "r", 98), (2, "r", 50), (4, "r", 66), (6, "r", 50)] + [(t, "o" if t in (11, 15) else "r", 84 if t == 8 else 56 if t % 2 == 0 else 44) for t in range(8, 16)],
    "S16": [(t, "o" if t % 4 == 3 else "r", 98 if t == 0 else 84 if t == 8 else 66 if t % 4 == 0 else 50 if t % 2 == 0 else 42) for t in range(16)],
    "S16b": [(t, "o" if t % 4 == 3 else ("f" if t in (5, 13) else "r"), 98 if t == 0 else 84 if t == 8 else 66 if t % 4 == 0 else 50 if t % 2 == 0 else 42) for t in range(16)],
    "STOP": [(0, "r", 100), (8, "r", 90)],
    "END": [(0, "r", 96), (4, "r", 76), (8, "r", 60), (10, "r", 40)],
}
GUITAR_PAT = {
    "arp8": [(t, i, v) for t, i, v in zip(range(0, 16, 2), (0, 2, 3, 2, 1, 2, 3, 2), (52, 36, 40, 36, 46, 36, 40, 38))],
    "chug": [(t, 0, 66 if t % 8 == 0 else 46) for t in range(0, 16, 2)],
}
HARP_ARP = [(t, i, 26) for t, i in zip(range(1, 16, 2), (2, 3, 4, 3, 2, 3, 4, 3))]
HARP_Q = [(t, i, 30) for t, i in zip(range(2, 16, 4), (1, 2, 3, 2))]
DRONE = [(0, "r", 54), (4, "r", 34), (8, "r", 50), (12, "r", 34)]


def pick(plan, i):
    """plan: [(first bar, pattern name), ...] ascending; the pattern of bar i."""
    name = plan[0][1]
    for first, n in plan:
        if i >= first:
            name = n
    return name


def build(P):
    s = Score(TPS, BAR, INTRO, BODY)
    sec_a = CH_MAJ["A"] if P else CH_MIN["A"]            # phase 2: the intro and A are already in the major

    def mel_a(text):
        return maj(text) if P else text

    pling = Voice("lead", PLING, 0, 1.0, "cry", 2)
    bit = lambda g=0.55, off=0: Voice("dbl", BIT, 0, g, "cry", 4 if off else 2, offbeat=off)
    harp_hit = lambda g=0.5: Voice("dbl", HARP, 0, g, "none")
    flute12 = lambda g=0.6, off=0: Voice("oct", FLUTE, 12, g, "cry", 4 if off else 2, offbeat=off)
    gtr_lo = lambda g=0.7, off=0: Voice("low", GUITAR, -12, g, "cry", 4 if off else 2, offbeat=off)
    hand = [Voice("lead", PLING, 0, 1.0, "cry", 2, high=77), Voice("lead", FLUTE, 0, 1.0, "cry", 2, low=78)]     # the pling takes the tune as high as it sounds, the flute carries on

    def harmony(scale, g=0.55):
        return [Voice("harm", FLUTE, 12, g, "cry", 2, scale=scale)]      # the third above rings an octave up, on the flute

    def fill(j, length=4, hot=False):
        t0 = s.tick(s.b(j)) + 16 - length
        roll(s, t0, t0 + length, 1 if hot else 2, 40, 96)

    def acc(j, i, chord, bass_name, drum_name=None, guitar=None, harp=None, drone=False, pad=False, level=1.0, drum_level=1.0):
        bar = s.b(j + i)
        if bass_name:
            bass(s, bar, chord, BASS_PAT[bass_name], level, high=54)
        if drone:
            bass(s, bar, chord, DRONE, level, role="drone", instrument=DIDGERIDOO)
        if drum_name:
            drums(s, bar, DRUMS[drum_name], drum_level)
        if guitar:
            notes_from(chord, GUITAR_PAT[guitar], "gtr", GUITAR, 45, 66, s, bar, level)
        if harp:
            notes_from(chord, harp, "arp", HARP, 54, 72, s, bar, level)
        if pad:
            stab(s, bar, 0, chord, "pad", PLING, 50, 68, 26 * level, count=2, length=16, every=4, style="flat")

    # --- intro (bars 0-3, played once): the first half of the tune as a single line, like a solo string; quarter roots from bar 2; no drums -----
    iv = [pling, harp_hit(0.6)] if P == 0 else [pling, gtr_lo(0.6), harp_hit(0.6)]
    for k in range(4):
        c = sec_a[k]
        sing(s, k, mel_a(A[k]), iv, vel=86 if P == 0 else 92)
        if k >= 2 or P:
            bass(s, k, c, BASS_PAT["Q"], 1.0, high=54)
        notes_from(c, HARP_Q, "arp", HARP, 54, 72, s, k, 0.8)
    if P:
        stab(s, 0, 0, sec_a[0], "stab", PLING, 52, 72, 70, count=3, length=6, every=2)

    # --- intro (body 0-3, the loop point): the second half of the tune; the snare gets denser bar by bar, the bass climbs quarters -> eighths ---------------
    ramp = [[(12, 44)], [(4, 46), (12, 56)], [(4, 50), (8, 54), (12, 60), (14, 64)], [(t, 50 + 3 * t // 2) for t in range(0, 12, 2)]]
    for k in range(4):
        c = sec_a[4 + k]
        sing(s, s.b(k), mel_a(A[4 + k]), iv, vel=72 if P == 0 else 84)
        bass(s, s.b(k), c, BASS_PAT["Q" if k < 2 else "E_soft"], 0.8 if P == 0 else 1.0, high=54)
        notes_from(c, HARP_Q, "arp", HARP, 54, 72, s, s.b(k), 0.9)
        for t, v in ramp[k]:
            s.drum("snare", s.tick(s.b(k), t), SNARE, v * (1.0 if P == 0 else 1.15))
        if k == 3:
            s.drum("kick", s.tick(s.b(k), 0), BASEDRUM, 80)
            roll(s, s.tick(s.b(k), 12), s.tick(s.b(4)), 1, 60, 100)
        if k == 2:
            s.drum("kick", s.tick(s.b(k), 0), BASEDRUM, 60)

    # --- A (20 bars, body 4-23): full drums, the bass nails the root; the tune in minor (phase 2: major), eerie. Bars 16-19: pseudo half time. ---------------------
    A_BASS = [[(0, "E"), (8, "E_o"), (10, "E_mv"), (12, "halfb"), (16, "ED_a"), (18, "ED_b")],
              [(0, "E"), (4, "ED_a"), (8, "E_mv"), (12, "halfb"), (16, "BURST"), (18, "ED_b")]]
    A_DRUM = [[(0, "rock"), (10, "drive"), (12, "half"), (16, "drive")], [(0, "drive"), (12, "half"), (16, "peak")]]
    for i in range(20):
        if i < 8:
            text, voices = A[i], [pling, harp_hit(0.5)] + ([flute12(0.45, 2)] if i >= 4 else [])
        elif i < 16:
            text, voices = A2[i - 8], [pling, bit(0.6, 2), flute12(0.6, 2)] + ([gtr_lo(0.7, 2)] if i >= 12 else [])
        else:
            text, voices = (A2[0], A[1], A[6], A[7])[i - 16], [pling, bit(0.6, 2), flute12(0.7, 2), gtr_lo(0.7, 2)]
        idx = (i % 8) if i < 16 else (0, 1, 6, 7)[i - 16]
        c = sec_a[idx]
        if P:
            voices = voices + ([bit(0.65, 2)] if i < 8 else []) + ([gtr_lo(0.7, 2)] if 4 <= i < 12 else [])
        sing(s, s.b(4 + i), mel_a(text), voices, vel=(86 if P == 0 else 92) + (4 if i >= 8 else 0))
        half = 12 <= i < 16
        acc(4, i, c, pick(A_BASS[P], i), pick(A_DRUM[P], i), "arp8" if (i < 8 or (P == 0 and i < 12)) else None,
            HARP_ARP if (i >= 8 and not half) else None, drone=(i >= 8 and not half), level=0.9 if i < 8 else 0.95)
        if i in (7, 11, 19):
            fill(4 + i, 4, hot=(P == 1 or i == 19))
    if P:
        stab(s, s.b(4), 0, sec_a[0], "stab", PLING, 52, 72, 80, count=3, length=6, every=2)

    # --- B (20 bars, body 24-43): BASS RELEASE - the bass runs up and down; the tune an octave up (flute) with the pling under it; bars 14-21: pseudo double time ---
    B_BASS = [[(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "ED_b"), (4, "ED_a"), (5, "TRI"), (6, "WALK"), (7, "ED_b"), (8, "BURST"), (10, "ED_b"), (11, "BURST"), (12, "ED_b"),
               (13, "BURST"), (14, "ED_b"), (15, "BURST"), (16, "ED_b"), (17, "BURST"), (18, "BURST"), (19, "S16")],
              [(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "BURST"), (4, "ED_b"), (5, "TRI"), (6, "WALK"), (7, "BURST"), (8, "S16"), (9, "BURST"), (10, "S16"), (11, "BURST"), (12, "S16"),
               (13, "BURST"), (14, "S16"), (15, "BURST"), (16, "S16"), (17, "BURST"), (18, "S16"), (19, "S16b")]]
    for i in range(20):
        if i < 8:
            text, c = LAMENT[i], CH_MIN["B"][i]
            v = [Voice("lead", FLUTE, 12, 1.0, "cry", 2), Voice("dbl", PLING, -12, 0.95, "cry", 2), Voice("echo", HARP, -12, 0.5, "none", delay=3)]
        elif i < 16:
            text, c = LAMENT2[i - 8], CH_MIN["B"][i - 8]
            v = [Voice("lead", FLUTE, 12, 1.0, "cry", 2), Voice("dbl", PLING, -12, 0.95, "cry", 2), Voice("low", GUITAR, -12, 0.85, "cry", 2)]
        else:
            text, c = SEQ[i - 16], CH_MIN["C"][i - 16]
            v = [Voice("lead", FLUTE, 12, 1.0, "cry", 2), Voice("dbl", PLING, -12, 0.95, "cry", 2), Voice("low", GUITAR, -12, 0.85, "cry", 2)]
        sing(s, s.b(24 + i), text, v, key=KEY_B, vel=90 if P == 0 else 94)
        dbl = 10 <= i < 18
        dname = ("peak" if P and i >= 8 else "dbl") if dbl else ("dbl" if i >= 18 else ("drive16" if (P == 0 or i < 2) else "peak"))
        if i in (8, 9) and P == 0:
            dname = "drive16"
        acc(24, i, c, pick(B_BASS[P], i), dname, "chug" if i < 12 else None, HARP_ARP if i >= 8 else None, drone=(i >= 8), level=1.0)
        if i in (7, 9, 17):
            fill(24 + i, 4, hot=True)
    roll(s, s.tick(s.b(24 + 19), 8), s.tick(s.b(44)), 1, 50, 100)

    # --- pre-break (body 44-45): the dominant, then STOP-TIME (a stab on beats 1 and 3, nothing between); break (body 46): ALL silent but one bell ---------------
    sing(s, s.b(44), "A4:4 C#5:4 E5:8", [pling, bit(0.6, 2), flute12(0.7, 2), gtr_lo(0.7, 2)], key=KEY_B, vel=96)
    acc(44, 0, "A7", "ED_b", "drive16", None, None, drone=False, level=1.0)
    stab(s, s.b(44), 0, "A7", "stab", GUITAR, 45, 64, 80, count=3, length=4, every=2)
    fill(44, 4, hot=True)
    sing(s, s.b(45), "E5:16", [pling, flute12(0.8, 2)], key=KEY_B, vel=92)
    bass(s, s.b(45), "A7", BASS_PAT["STOP"], 1.0, high=54)
    drums(s, s.b(45), DRUMS["stop"], 1.0)
    for t in (0, 8):
        stab(s, s.b(45), t, "A7", "stab", GUITAR, 45, 64, 90, count=3, length=4, every=2)
    bell(s, s.tick(s.b(46)), 93, 80)

    # --- CHORUS (16 bars, body 47-62): the parallel (relative) MAJOR with a third above; the tune ENLARGED over the 16th machine gun ------------------------------------
    for r in range(2):
        j0 = 47 + 8 * r
        for i in range(8):
            c = CH_MAJ["CH"][i]
            text = maj((PK, PK2)[r][i])
            v = [pling, flute12(0.95), gtr_lo(0.95)] + harmony(F_MAJOR, 0.9)
            if r == 1:
                v.append(harp_hit(0.5))
            sing(s, s.b(j0 + i), text, v, vel=100)
            bass(s, s.b(j0 + i), c, BASS_PAT["S16b" if (r == 1 and i % 2) else "S16"], 1.0, high=54)
            drums(s, s.b(j0 + i), DRUMS["peak"], 1.0)
            if P == 1 and i in (0, 4):
                stab(s, s.b(j0 + i), 0, c, "stab", GUITAR, 45, 64, 70, count=3, length=6, every=2)
        stab(s, s.b(j0), 0, K("F"), "stab", PLING, 52, 72, 92, count=3, length=6, every=2)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        fill(j0 + 3, 4, hot=True)
        fill(j0 + 7, 8 if r == 1 else 4, hot=True)

    # --- FINALE (17 bars, body 63-79): the whole structure a WHOLE STEP UP (G major): the tune with its own rhythm, then the long notes again, then A7 back into the loop ---
    for r in range(2):
        j0 = 63 + 8 * r
        for i in range(8):
            if r == 0:
                text, c = maj(A2[i]), CH_UP["A"][i]
                v = hand + [flute12(0.95), gtr_lo(0.95)]
            else:
                text, c = maj(PK2[i]), CH_UP["CH"][i]
                v = hand + [flute12(0.95), gtr_lo(0.95), harp_hit(0.5)] + harmony(G_MAJOR, 0.9)
            sing(s, s.b(j0 + i), text, v, transpose=2, vel=100)
            bass(s, s.b(j0 + i), c, BASS_PAT["S16b" if i % 2 else "S16"], 1.0, high=54)
            drums(s, s.b(j0 + i), DRUMS["peak"], 1.0)
            if P == 1 and i % 4 == 0:
                stab(s, s.b(j0 + i), 0, c, "stab", GUITAR, 45, 64, 70, count=3, length=6, every=2)
        stab(s, s.b(j0), 0, K("G"), "stab", PLING, 52, 72, 96, count=3, length=6, every=2)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        fill(j0 + 3, 4, hot=True)
        fill(j0 + 7, 4, hot=True)
    # the link: A7 hangs, the beat thins out, and the loop starts again with the quiet single line
    sing(s, s.b(79), "A4:4 C#5:4 E5:8", [pling, bit(0.6, 2), flute12(0.7, 2)], vel=90)
    bass(s, s.b(79), K("A7"), BASS_PAT["END"], 1.0, high=54)
    for t, v in ((0, 90), (4, 70), (8, 56)):
        s.drum("kick", s.tick(s.b(79), t), BASEDRUM, v)
    roll(s, s.tick(s.b(79), 8), s.tick(s.b(79), 16), 2, 70, 30)
    return s


SECTIONS = [("intro", 0, 4), ("A", 4, 20), ("B", 24, 20), ("pre", 44, 2), ("break", 46, 1), ("chorus", 47, 16), ("finale", 63, 17)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in (0, 1):
        score = build(phase)
        finish(score, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], SECTIONS)


if __name__ == "__main__":
    main()
