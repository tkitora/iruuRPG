#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the reaper of the snow mountain in two phases.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

Boss class 2 "sorrow and fated battle" (phase 1) and class 6 "limit break and reversal" (phase 2). One piece: 150 BPM (20 ticks a second, a beat of 8 ticks, a bar of 32 ticks = two of the engine's
16-tick lines, so the phase may change in the middle of any bar), 80 bars of body and a 2-bar intro (2624 ticks, 131 s), loop from tick 64.

The tune (the 'crying melody', eight bars, D minor): a rising fifth into the tonic held long (A4 D5), a sigh down (C5 A4), a sequence over Bb and F, a climb to the held E5 over C, then an answer that lifts
to the leading tone and hangs on E5 over A7. It is written in the notes of D minor = F major, so it is the SAME NOTES in both phases: phase 1 harmonises it in D minor (Dm Bb F C | Dm Bb Gm A7,
bass on the minor roots, the leading tone C#), phase 2 in the relative major F (F C7 Dm Bb | F C7 Bb C7, C natural), and the last 8 bars of its peak are lifted a whole step into G major.
The lead is a solo violin made of pling / bit / flute / harp: a long note is struck again every 4 ticks with the loudness breathing (a vibrato), accompanied by an acoustic guitar picking, a heavy bass and drums.
Sections (bars of the body): A 8, A' 8, B (the lament, takes away) 8, A'' 8, C (the tune in sequence, then an octave higher) 8, B' (the lament on the piano) 8, build 8, PEAK 16, calm 8.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Reaper's Arc (Boss004)", "Reaper's Arc, Sundered (Boss004)")
FILES = ("necromancer_phase1.nbs", "necromancer_phase2.nbs")
DESCRIPTIONS = ("Snow mountain reaper, phase 1 (sorrow and fated battle): 150 BPM, a crying violin tune in D minor over a heavy beat; same bars as phase 2.",
                "Snow mountain reaper, phase 2 (limit break and reversal): the same tune turned to F major, arranged as a unison fanfare, drums doubled, lifted a whole step at the end.")
TPS, BAR, INTRO, BODY = 20, 32, 2, 80


def maj(text):
    return text.replace("C#5", "C5").replace("C#6", "C6")


# --- the tune (the notes of D minor = F major) -------------------------------------------------------------------------
A = ["A4:7 _E5:1 D5:12 C5:4 A4:8", "Bb4:12 A4:4 F4:4 G4:4 A4:8", "C5:12 A4:4 F4:4 A4:4 C5:8", "C5:7 _F5:1 E5:16 D5:4 C5:4",
     "A4:7 _E5:1 D5:12 E5:4 D5:8", "D5:12 C5:4 Bb4:8 D5:8", "G4:4 Bb4:4 D5:8 C5:8 Bb4:8", "A4:8 C#5:8 E5:16"]
A2 = list(A)
A2[0] = "A4:4 A4:4 D5:12 C5:4 A4:8"
A2[3] = "C5:4 E5:4 F5:16 E5:4 D5:4"
A2[4] = "A4:8 D5:12 E5:4 F5:8"
A2[7] = "A4:8 C#5:8 E5:8 F5:8"
AUP = list(A)          # the statement that is lifted a whole step in phase 2: its top is E5, so that F#5 is the highest the pling must sound
AUP[0] = A2[0]
LAMENT = ["D5:16 C5:4 Bb4:4 D5:8", "C5:12 A4:4 C5:8 F5:8", "Eb5:16 D5:8 Bb4:8", "A4:8 D5:8 F#5:16",
          "Bb4:8 D5:8 F5:12 D5:4", "C5:8 F5:16 E5:4 C5:4", "Bb4:8 Eb5:16 D5:8", "A4:8 C#5:8 E5:16"]
LAMENT2 = list(LAMENT)
LAMENT2[0] = "D5:16 C5:4 Bb4:4 F5:8"
LAMENT2[3] = "A4:8 D5:8 F#5:8 E5:4 D5:4"
SEQ = ["F4:8 Bb4:12 A4:4 F4:8", "G4:8 C5:12 Bb4:4 G4:8", "A4:8 D5:12 C5:4 A4:8", "D5:8 F5:12 E5:4 F5:8",
       "F5:8 Bb5:12 A5:4 F5:8", "G5:8 C6:12 Bb5:4 G5:8", "A5:8 D6:12 C6:4 A5:8", "A5:8 C#6:8 E6:16"]
CALM = [["A4:16 D5:8 C5:8", "Bb4:16 A4:8 F4:8", "G4:16 Bb4:8 D5:8", "C#5:16 E5:16", "D5:16 E5:8 F5:8", "D5:12 C5:4 Bb4:8 D5:8", "G4:4 Bb4:4 D5:8 C5:8 Bb4:8", "A4:8 C#5:8 E5:16"],
        ["B4:16 E5:8 D5:8", "C5:16 B4:8 G4:8", "A4:16 C5:8 E5:8", "G4:8 Bb4:8 D5:16", "G4:8 Bb4:8 E5:16", "A4:8 D5:12 E5:4 D5:8", "D5:12 C5:4 Bb4:8 D5:8", "A4:8 C5:8 E5:16"]]
CHORDS = {
    "A": (["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A7"], ["F", "C7", "Dm", "Bb", "F", "C7", "Bb", "C7"]),
    "A+2": (None, ["G", "D7", "Em", "C", "G", "D7", "C", "D7"]),
    "B": (["Gm", "F", "Eb", "D", "Gm", "F", "Eb", "A7"], ["Bb", "F", "Eb", "Dm", "Bb", "F", "Eb", "C7"]),
    "C": (["Bb", "C", "Dm", "Dm", "Bb", "C", "Dm", "A7"], ["Bb", "C", "F", "F", "Bb", "C", "F", "C7"]),
    "BUILD": (["Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "A7", "A7"], ["F", "F", "Bb", "Bb", "Gm", "Gm", "C7", "C7"]),
    "CALM": (["Dm", "Bb", "Gm", "A7", "Dm", "Bb", "Gm", "A7"], ["G", "Em", "Am", "Gm", "C7", "F", "Bb", "C7"]),
}

# --- patterns of a bar of 32 ticks -----------------------------------------------------------------------------------
DRUMS = {
    "rock": {"kick": [(0, 100), (12, 70), (16, 96), (28, 66)], "snare": [(8, 90), (24, 98)], "hat": [(t, 44 if t % 8 == 0 else 30) for t in range(0, 32, 4)]},
    "drive": {"kick": [(0, 100), (6, 62), (12, 76), (16, 98), (22, 62), (28, 76)], "snare": [(8, 96), (24, 100), (30, 46)],
              "hat": [(t, 46 if t % 8 == 0 else 32) for t in range(0, 32, 4)]},
    "half": {"kick": [(0, 92), (20, 56)], "snare": [(16, 84)], "hat": [(t, 26) for t in range(0, 32, 8)]},
    "march": {"kick": [(0, 84), (8, 50), (16, 70), (24, 50)], "snare": [(16, 76), (8, 32), (24, 32)]},
    "peak": {"kick": [(0, 100), (4, 60), (10, 70), (12, 88), (16, 100), (20, 60), (26, 70), (28, 88)], "snare": [(8, 100), (24, 100), (14, 48), (30, 52)],
             "hat": [(t, 50 if t % 8 == 0 else (36 if t % 4 == 0 else 26)) for t in range(0, 32, 2)], "tom": [(6, 52, 57), (22, 52, 54)]},
    "build": {"kick": [(0, 92), (8, 70), (16, 92), (24, 70)], "hat": [(t, 34) for t in range(0, 32, 4)]},
}
BASS_PAT = {  # (tick, kind, velocity)
    "drive8": [(0, "r", 96), (4, "r", 56), (8, "r", 74), (12, "r", 56), (16, "r", 90), (20, "r", 56), (24, "r", 74), (28, "o", 60)],
    "half": [(0, "r", 78), (8, "r", 42), (16, "r", 64), (24, "r", 42)],
    "run16": [(t, "o" if t % 8 == 6 else "r", 98 if t == 0 else 84 if t == 16 else 66 if t % 8 == 0 else 50 if t % 4 == 0 else 42) for t in range(0, 32, 2)],
}
GUITAR_PAT = {
    "arp8": [(t, i, v) for t, i, v in zip(range(0, 32, 4), (0, 2, 3, 2, 1, 2, 3, 2), (52, 36, 40, 36, 46, 36, 40, 38))],
    "chug": [(t, i, 66 if t % 16 == 0 else 44) for t in range(0, 32, 4) for i in (0, 2)],
}
HARP_ARP = [(t, i, 26) for t, i in zip(range(2, 32, 4), (2, 3, 4, 3, 2, 3, 4, 3))]
HARP_STEP = [(t, i, 34) for t, i in zip(range(0, 32, 8), (0, 1, 2, 1))]
DRONE = [(0, "r", 54), (8, "r", 34), (16, "r", 50), (24, "r", 34)]


def build(P):
    s = Score(TPS, BAR, INTRO, BODY)

    def sec_chords(key, i):
        return CHORDS[key][P][i]

    def mel(lst, i):
        return maj(lst[i]) if P else lst[i]

    pling = Voice("lead", PLING)
    bit = lambda g=0.55: Voice("dbl", BIT, 0, g)
    harp_hit = lambda g=0.5: Voice("dbl", HARP, 0, g, "none")
    flute12 = lambda g=0.6: Voice("oct", FLUTE, 12, g)
    gtr_lo = lambda g=0.7: Voice("low", GUITAR, -12, g)

    def drum_fill(j, length=8, hot=False):
        t0 = s.tick(s.b(j)) + 32 - length
        roll(s, t0, t0 + length, 2 if hot else 4, 40, 96)

    def accompany(j, key, i, bass_pat, drum_pat, guitar_pat=None, harp_pat=None, drone=False, pad=False, level=1.0, drum_level=1.0):
        bar = s.b(j + i)
        c = sec_chords(key, i)
        if bass_pat:
            bass(s, bar, c, BASS_PAT[bass_pat], level, high=52)
        if drone:
            bass(s, bar, c, DRONE, level, role="drone", instrument=DIDGERIDOO)
        if drum_pat:
            drums(s, bar, DRUMS[drum_pat], drum_level)
        if guitar_pat:
            notes_from(c, GUITAR_PAT[guitar_pat], "gtr", GUITAR, 45, 66, s, bar, level)
        if harp_pat:
            notes_from(c, harp_pat, "arp", HARP, 62, 77, s, bar, level)
        if pad:
            stab(s, bar, 0, c, "pad", PLING, 55, 72, 26 * level, count=2, length=32, every=8, style="flat")

    # --- intro: two bars, the tune alone over a pedal, then the heartbeat and a short roll into the loop ----------------
    if P == 0:
        voices = [Voice("lead", PLING, 0, 0.8), harp_hit(0.5)]
    else:
        voices = [pling, bit(0.7), flute12(0.7), gtr_lo(0.8)]
    chords_i = ["Dm", "A7"] if P == 0 else ["F", "C7"]
    for i in (0, 1):
        sing(s, i, mel(A, 0 if i == 0 else 7), voices, vel=70 if P == 0 else 90)
        held(s, "bass", BASS, s.tick(i), root_of(chords_i[i]), 32, 60 if P == 0 else 84, 80 if P == 0 else 96, every=8)
        notes_from(chords_i[i], [(t, k, (36 + 4 * i) if P == 0 else 44) for t, k in zip(range(0, 32, 4), (0, 1, 2, 3, 2, 1, 2, 3))], "arp", HARP, 62, 77, s, i)
    if P == 0:
        s.drum("kick", s.tick(1, 16), BASEDRUM, 70)
        s.drum("kick", s.tick(1, 24), BASEDRUM, 64)
        roll(s, s.tick(1, 24), s.tick(2), 2, 34, 84)
    else:
        stab(s, 0, 0, "F", "stab", PLING, 57, 77, 92, count=3, length=16, every=4)
        stab(s, 0, 0, "F", "stab", GUITAR, 45, 64, 80, count=3, length=16, every=4)
        s.drum("kick", s.tick(0), BASEDRUM, 100)
        s.drum("snare", s.tick(0), SNARE, 90)
        drums(s, 1, DRUMS["drive"])
        roll(s, s.tick(1, 16), s.tick(2), 2, 50, 100)

    # --- A (8): the tune on the pling; picking, a heavy beat that does not hurry; phase 2: the unison already, the bass in sixteenths ---
    for i in range(8):
        if P == 0:
            v = [pling, harp_hit(0.5)] + ([flute12(0.45)] if i >= 4 else [])
            accompany(0, "A", i, "drive8", "rock", "arp8", level=0.9)
        else:
            v = [pling, bit(0.65), gtr_lo(0.75)] + ([flute12(0.65)] if i >= 4 else [])
            accompany(0, "A", i, "run16" if i >= 4 else "drive8", "drive" if i < 4 else "peak", None, None, drone=(i >= 4), level=0.95)
        sing(s, s.b(i), mel(A, i), v, vel=86 if P == 0 else 92)
    if P == 1:
        stab(s, s.b(0), 0, "F", "stab", PLING, 57, 77, 80, count=3, length=12, every=4)
    drum_fill(7, 8 if P == 0 else 16, hot=(P == 1))

    # --- A' (8): the tune a second time, the ending changed; bit and flute join, the harp shimmers in the second half ---
    for i in range(8):
        v = [pling, bit(0.6), flute12(0.6)] + ([gtr_lo(0.7)] if i >= 4 else [])
        sing(s, s.b(8 + i), mel(A2, i), v, vel=88 if P == 0 else 94)
        late = i >= 4
        if P == 0:
            accompany(8, "A", i, "drive8", "rock" if not late else "drive", "arp8", HARP_ARP if late else None, drone=late, level=0.95)
        else:
            accompany(8, "A", i, "run16" if late else "drive8", "peak" if late else "drive", None, HARP_ARP if late else None, drone=late, level=1.0)
            if i % 4 == 0:
                stab(s, s.b(8 + i), 0, sec_chords("A", i), "stab", GUITAR, 45, 64, 54, count=3, length=12, every=4)
    drum_fill(8 + 3, 8, hot=(P == 1))
    drum_fill(8 + 7, 16, hot=True)

    # --- B (8): the lament; everything takes away: flute and pling, a pad, a half-time beat, the harp's echo -----------------
    for i in range(8):
        c = sec_chords("B", i)
        sing(s, s.b(16 + i), mel(LAMENT, i), [Voice("lead", FLUTE, 12, 1.0), Voice("dbl", PLING, 0, 0.7), Voice("echo", HARP, 0, 0.3, "none", delay=6)], vel=84 if P == 0 else 88)
        accompany(16, "B", i, "half", "half" if P == 0 else "march", None, HARP_STEP, pad=True, level=0.9 if P == 0 else 1.0, drum_level=0.85)
        if P == 1 and i in (3, 7):
            stab(s, s.b(16 + i), 0, c, "stab", PLING, 55, 72, 60, count=3, length=16, every=4)
    roll(s, s.tick(s.b(16 + 7), 16), s.tick(s.b(16 + 8)), 4, 34, 80)

    # --- A'' (8): the bit and the harp lead, the flute an octave up; the answer is picked by the guitar ------------------------
    for i in range(8):
        v = [Voice("lead", BIT, 0, 0.9), harp_hit(0.7), flute12(0.7)] + ([pling] if i >= 4 else [])
        sing(s, s.b(24 + i), mel(A2, i), v, vel=90 if P == 0 else 96)
        if P == 0:
            accompany(24, "A", i, "drive8" if i < 4 else "run16", "drive", "arp8", HARP_ARP if i >= 4 else None, drone=True)
        else:
            accompany(24, "A", i, "run16" if i >= 2 else "drive8", "peak" if i >= 2 else "drive", None, HARP_ARP if i >= 4 else None, drone=True)
            if i >= 4:
                sing(s, s.b(24 + i), mel(A2, i), [gtr_lo(0.8)], vel=90)
    drum_fill(24 + 3, 8, hot=True)
    drum_fill(24 + 7, 16, hot=True)

    # --- C (8): the tune in sequence - low and in the middle, then four bars an octave higher with the flute alone on top ---
    for i in range(8):
        if i < 4:
            v = [pling, bit(0.6), flute12(0.6), gtr_lo(0.6)]
        else:
            v = [Voice("lead", FLUTE, 0, 1.0), Voice("dbl", PLING, -12, 0.9), Voice("oct", HARP, 0, 0.6, "none"), Voice("dbl", BIT, -12, 0.55), Voice("low", GUITAR, -24, 0.7)]
        sing(s, s.b(32 + i), mel(SEQ, i), v, vel=92)
        accompany(32, "C", i, "drive8" if (i < 4 and P == 0) else "run16", "drive" if (P == 0 or i < 4) else "peak", "chug" if i < 4 else None, None, drone=True)
    drum_fill(32 + 3, 8, hot=True)
    drum_fill(32 + 7, 16, hot=True)

    # --- B' (8): the lament again, the piano alone with a low guitar answering, a pad and a drone, a march ---------------------
    for i in range(8):
        sing(s, s.b(40 + i), mel(LAMENT2, i), [Voice("lead", HARP, 0, 1.0, "cry", 4), bit(0.35), Voice("low", GUITAR, -12, 0.55), Voice("echo", FLUTE, 12, 0.35, "none", delay=12)], vel=88 if P == 0 else 92)
        accompany(40, "B", i, "half", "march", None, None, drone=True, pad=True, level=0.85, drum_level=0.8 if P == 0 else 0.95)
    roll(s, s.tick(s.b(40 + 7), 8), s.tick(s.b(40 + 8)), 4, 30, 78)

    # --- build (8): long notes climbing (A4 D5 F5 E5) struck again and again, the drums coming back, a roll, then a gap before the peak ---
    for k, n in enumerate([69, 74, 77, 76]):
        t0 = s.tick(s.b(48), k * 64) if k < 3 else s.tick(s.b(54))
        length = 64 if k < 3 else 48
        held(s, "lead", PLING, t0, n, length, 46 + 8 * k, 84 + 4 * k)
        held(s, "oct", FLUTE, t0, n + 12, length, 30 + 8 * k, 70 + 4 * k)
        if k >= 1:
            held(s, "dbl", BIT, t0, n, length, 20, 60, every=8)
    for i in range(8):
        bar = s.b(48 + i)
        c = sec_chords("BUILD", i)
        if i < 7:
            bass(s, bar, c, BASS_PAT["half"] if i < 4 else BASS_PAT["drive8"], 0.95, high=52)
            notes_from(c, GUITAR_PAT["arp8"], "gtr", GUITAR, 45, 66, s, bar, 0.9)
            notes_from(c, [(t, 2 + (t // 4) % 3, 22 + 3 * i) for t in range(2, 32, 4)], "arp", HARP, 62, 77, s, bar)
        else:
            bass(s, bar, c, [(0, "r", 96), (8, "r", 76), (16, "r", 60)], 1.0, high=52)
        if 2 <= i < 7:
            drums(s, bar, DRUMS["build"] if i < 6 else DRUMS["drive"], 0.7 + 0.04 * i)
    roll(s, s.tick(s.b(54), 16), s.tick(s.b(55)), 4, 40, 80)
    roll(s, s.tick(s.b(55)), s.tick(s.b(55), 20), 2, 60, 100)
    for t, v in ((24, 70), (28, 82), (30, 100)):          # the gap (ticks 20 to 23 are empty), then a drum pick-up into the peak
        s.drum("kick", s.tick(s.b(55), t), BASEDRUM, v)

    # --- PEAK (16): the tune as a unison - pling, bit, the flute an octave up, the guitar an octave down - twice; phase 2 lifts the second time a whole step ---
    for r in range(2):
        j0 = 56 + 8 * r
        for i in range(8):
            bar = s.b(j0 + i)
            up = 2 if (P == 1 and r == 1 and i >= 4) else 0
            text = mel(AUP if up else (A if r == 0 else A2), i)
            c = CHORDS["A+2"][1][i] if up else CHORDS["A"][P][i]
            v = [pling, bit(0.6), flute12(0.75), gtr_lo(0.75)]
            if r == 1:
                v.append(harp_hit(0.55))
            sing(s, bar, text, v, transpose=up, vel=96 if P == 0 else 98)
            bass(s, bar, c, BASS_PAT["run16"], 1.0, high=52)
            bass(s, bar, c, DRONE, 1.0, role="drone", instrument=DIDGERIDOO)
            drums(s, bar, DRUMS["peak"], 1.0)
            if P == 0 and i % 2 == 0:
                notes_from(c, [(12, 3, 40), (28, 3, 40)], "chord", HARP, 55, 72, s, bar)
            if P == 1:
                notes_from(c, [(12, 3, 44), (28, 3, 44)], "chord", HARP, 55, 72, s, bar)
                notes_from(c, [(t, 0, 50 if t % 16 == 0 else 36) for t in range(0, 32, 4)], "gtr", GUITAR, 45, 66, s, bar)
                if i in (0, 4):
                    stab(s, bar, 0, c, "stab", GUITAR, 45, 64, 70, count=3, length=12, every=4)
        stab(s, s.b(j0), 0, CHORDS["A"][P][0], "stab", PLING, 57, 77, 92, count=3, length=12, every=4)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        if P == 1 and r == 1:
            stab(s, s.b(j0 + 4), 0, "G", "stab", PLING, 57, 77, 96, count=3, length=12, every=4)
            s.drum("tom", s.tick(s.b(j0 + 4)), BASEDRUM, 100, 54)
        drum_fill(j0 + 3, 8, hot=True)
        drum_fill(j0 + 7, 16, hot=(P == 1))

    # --- calm (8): the piano, a pad; then the beat comes back for the loop ------------------------------------------------------
    for i in range(8):
        quiet = i < 4
        if i < 6:
            v = [Voice("lead", HARP, 0, 1.0, "cry", 4), Voice("dbl", PLING, 0, 0.5), Voice("echo", FLUTE, 12, 0.3 if quiet else 0.5)]
        else:
            v = [pling, harp_hit(0.5), flute12(0.5)]
        sing(s, s.b(72 + i), CALM[P][i], v, vel=70 if quiet else 86)
        c = sec_chords("CALM", i)
        bass(s, s.b(72 + i), c, BASS_PAT["half"] if i < 5 else BASS_PAT["drive8"], 0.8 if quiet else 0.95, high=52)
        notes_from(c, GUITAR_PAT["arp8"], "gtr", GUITAR, 45, 66, s, s.b(72 + i), 0.7 if quiet else 0.9)
        if quiet:
            stab(s, s.b(72 + i), 0, c, "pad", PLING, 55, 72, 26, count=2, length=32, every=8, style="flat")
        if i >= 2:
            drums(s, s.b(72 + i), DRUMS["half"] if i < 4 else (DRUMS["march"] if i < 6 else DRUMS["rock"]), 0.8 if i < 6 else 0.9)
    roll(s, s.tick(s.b(72 + 7), 8), s.tick(s.b(72 + 8)), 4, 40, 84)

    return s


SECTIONS = [("A", 0, 8), ("A'", 8, 8), ("B", 16, 8), ("A''", 24, 8), ("C", 32, 8), ("B'", 40, 8), ("build", 48, 8), ("PEAK", 56, 16), ("calm", 72, 8)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in (0, 1):
        score = build(phase)
        finish(score, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], SECTIONS)


if __name__ == "__main__":
    main()
