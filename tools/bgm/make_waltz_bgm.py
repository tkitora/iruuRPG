#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "Waltz at the Edge of Dark, Out of Step (Boss008)", a tragic waltz in two phases.

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

Boss class 2 "sorrow and fated battle" as an elegant, doomed duel: 3/4 at 200 BPM (20 ticks a second, a beat of 6 ticks, a bar of 18 ticks; eighths of 3 ticks, triplets of 2). A bar of 18 ticks meets the engine's
16-tick line every 8 bars, so every section is a multiple of 8 bars and starts on such a line (the loop starts at tick 64 = a 10-tick silence and three bars of intro; the body is 136 bars, 2512 ticks, 125.6 s).
The tune (8 bars, E minor): a fifth up into the tonic (B4 E5), a leap to the leading tone's F#5 held over B7, a sigh down (E5 B4), a fall to A4, then the same rise and a turn (F#5 E5 D#5 F#5), a
descent over C (E5 D5 C5) and an arpeggio of B7 that hangs on F#5. The lead is a solo violin made of pling / bit / flute / harp whose long notes are struck again every eighth with the loudness breathing;
the first beat is heavy (bass and bass drum), the guitar plays the 'pah-pah' on beats 2 and 3, the harp arpeggios, the drums keep a fierce beat behind.
Phase 1 sections (bars): A 16, A' 16, B (the lament, takes away) 16, A'' 16, C (the tune climbing in sequence, then in the flute's high register) 16, B' (the lament on the piano) 8, build 8, PEAK 24, calm 16.
Phase 2 'Out of Step' is the same bars and notes with the footing lost: the bass and drums fall into hemiola (two bars of 3/4 felt as three of 3/2), a statement of the tune comes a beat late against the beat
(and is cut off to catch up), the bass stings a half step above (F against E), the beat is doubled to triplets - still minor and tragic, not the major reversal of Reaper's Arc.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Waltz at the Edge of Dark (Boss008)", "Waltz at the Edge of Dark, Out of Step (Boss008)")
FILES = ("waltz_phase1.nbs", "waltz_phase2.nbs")
DESCRIPTIONS = ("Waltz boss, phase 1 (sorrow and fated battle): a tragic 3/4 waltz at 200 BPM in E minor, a crying violin tune, a heavy first beat; same bars as phase 2.",
                "Waltz boss, phase 2: the same waltz out of step - hemiola in the bass and drums, the tune a beat late, a half-step sting in the bass, triplet drums; still E minor.")
TPS, BAR, INTRO, BODY = 20, 18, 3, 136

# --- the tune and its sisters (18 ticks a bar: a beat is 6, an eighth 3) --------------------------------------------------
T = ["B4:12 E5:6", "D#5:6 F#5:12", "E5:12 B4:6", "C5:12 A4:6", "B4:12 E5:6", "F#5:6 E5:3 D#5:3 F#5:6", "E5:9 D5:3 C5:6", "A4:6 D#5:6 F#5:6"]
TV = ["B4:12 E5:6", "D#5:3 E5:3 F#5:12", "E5:9 D5:3 B4:6", "C5:9 B4:3 A4:6", "B4:3 B4:3 B4:6 E5:6", "F#5:9 E5:3 D#5:6", "E5:9 D5:3 C5:3 D5:3", "A4:6 D#5:6 F#5:6"]
TX = list(T)
TX[0] = "B4:6 B4:3 E5:3 E5:6"
TX[7] = "A4:3 D#5:3 F#5:6 A5:6"
LAMENT = ["A4:6 C5:6 E5:6", "F#5:12 D5:6", "D5:12 B4:6", "E5:12 C5:6", "A4:6 C5:6 E5:6", "F#5:12 D#5:6", "G4:6 B4:6 E5:6", "D#5:6 F#5:12"]
LAMENT2 = list(LAMENT)
LAMENT2[0] = "A4:6 C5:3 E5:9"
LAMENT2[2] = "D5:9 E5:3 B4:6"
SEQ = ["B4:12 E5:6", "D5:12 G5:6", "E5:12 A5:6", "F#5:12 B5:6", "G5:6 B5:6 E6:6", "E6:12 D6:3 C6:3", "C6:6 B5:6 A5:6", "D#6:6 F#6:12"]
CHORDS = {
    "T": ["Em", "B7", "Em", "Am", "Em", "B7", "C", "B7"],
    "W": ["Am", "D", "G", "C", "Am", "B7", "Em", "B7"],
    "C": ["Em", "G", "Am", "B7", "Em", "C", "Am|B7", "B7"],
    "BUILD": ["Em", "Em", "C", "C", "Am", "Am", "B7", "B7"],
}

# --- patterns of a bar of 18 ticks -------------------------------------------------------------------------------------
DRUMS = {
    "waltz": {"kick": [(0, 100), (9, 50)], "snare": [(6, 80), (12, 80)], "hat": [(t, 40 if t % 6 == 0 else 28) for t in range(0, 18, 3)]},
    "quiet": {"kick": [(0, 92)], "snare": [(12, 64)], "hat": [(6, 28), (12, 28)]},
    "drive": {"kick": [(0, 100), (9, 60), (15, 50)], "snare": [(6, 92), (12, 96), (16, 44)], "hat": [(t, 46 if t % 6 == 0 else 30) for t in range(0, 18, 3)]},
    "peak": {"kick": [(0, 100), (3, 50), (9, 70), (15, 60)], "snare": [(6, 100), (12, 100), (16, 56)], "hat": [(t, 46 if t % 6 == 0 else 28) for t in range(0, 18, 2)],
             "tom": [(4, 50, 57), (10, 50, 54)]},
    "build": {"kick": [(0, 92), (6, 66), (12, 66)], "hat": [(t, 32) for t in range(0, 18, 3)]},
    # hemiola: two bars of 3/4 felt as three of 3/2 (the strong beats at 0, 12, 24 of the 36 ticks)
    "hemiA": {"kick": [(0, 100), (12, 96)], "snare": [(6, 84), (15, 44)], "hat": [(t, 44 if t % 6 == 0 else 26) for t in range(0, 18, 2)]},
    "hemiB": {"kick": [(6, 96), (15, 44)], "snare": [(0, 84), (12, 84)], "hat": [(t, 44 if t % 6 == 0 else 26) for t in range(0, 18, 2)], "tom": [(9, 46, 54)]},
}
BASS_PAT = {  # (tick, kind, velocity); an integer kind is a MIDI note
    "oom": [(0, "r", 100), (6, "f", 52), (12, "f", 52)],
    "drive": [(0, "r", 100), (3, "r", 48), (6, "r", 64), (9, "r", 48), (12, "r", 64), (15, "o", 52)],
    "trip": [(t, "o" if t in (8, 14) else "r", 100 if t == 0 else 70 if t % 6 == 0 else 44) for t in range(0, 18, 2)],
    "half": [(0, "r", 80), (6, "r", 42), (12, "r", 50)],
    "hemiA": [(0, "r", 100), (6, "r", 46), (12, "r", 94), (15, "r", 44)],
    "hemiB": [(0, "r", 46), (6, "r", 96), (12, "r", 46), (15, "o", 56)],
    "sting": [(0, "r", 100), (6, "r", 56), (12, 41, 96), (15, 41, 60)],      # F2 against the B7: the half step above the tonic
}
GUITAR_PAT = {
    "pah": [(6, 0, 0), (12, 0, 0)],
    "arp": [(t, i, v) for t, i, v in zip(range(0, 18, 3), (0, 2, 3, 2, 3, 2), (52, 36, 40, 36, 40, 36))],
}
HARP_ARP = [(t, i, 30) for t, i in zip(range(0, 18, 3), (0, 1, 2, 3, 2, 1))]
HARP_RISE = [(t, i, 26) for t, i in zip(range(0, 18, 2), (0, 1, 2, 3, 4, 3, 2, 3, 4))]
HARP_SLOW = [(0, 0, 34), (6, 1, 30), (12, 2, 30)]
DRONE = [(0, "r", 54), (9, "r", 34)]


def build(P):
    s = Score(TPS, BAR, INTRO, BODY)

    def mel(lst, i):
        return lst[i % 8]

    pling = Voice("lead", PLING, 0, 1.0, "cry", 3, octave_fit=True)
    bit = lambda g=0.55: Voice("dbl", BIT, 0, g, "cry", 3, octave_fit=True)
    harp_hit = lambda g=0.55: Voice("dbl", HARP, 0, g, "none", octave_fit=True)
    flute12 = lambda g=0.6: Voice("oct", FLUTE, 12, g, "cry", 3, octave_fit=True)
    gtr_lo = lambda g=0.7: Voice("low", GUITAR, -12, g, "cry", 3, octave_fit=True)

    def fill(j, length=12, hot=False):
        t0 = s.tick(s.b(j)) + 18 - length
        roll(s, t0, t0 + length, 2 if hot else 3, 40, 98)

    def lead(j, i, text, voices, vel, start=0, cut=False, transpose=0):
        sing(s, s.b(j + i), text, voices, transpose=transpose, vel=vel, start=start, cut=cut)

    def accompany(j, i, chords, bass_pat, drum_pat, guitar=None, harp=None, drone=False, pad=False, level=1.0, drum_level=1.0, hemi=False):
        """hemi: bass and drums fall into 3/2 over this pair of bars (i is even: the pair is bars i and i + 1)."""
        bar = s.b(j + i)
        c = chords[i % 8]
        if hemi:
            part = "hemiA" if i % 2 == 0 else "hemiB"
            bass(s, bar, c, BASS_PAT[part], level, high=52)
            drums(s, bar, DRUMS[part], drum_level)
        else:
            if bass_pat:
                bass(s, bar, c, BASS_PAT[bass_pat], level, high=52)
            if drum_pat:
                drums(s, bar, DRUMS[drum_pat], drum_level)
        if drone:
            bass(s, bar, c, DRONE, level, role="drone", instrument=DIDGERIDOO)
        if guitar == "pah":
            for t in (6, 12):
                stab(s, bar, t, c, "gtr", GUITAR, 47, 64, (54 if t == 6 else 46) * level, count=3)
        elif guitar:
            notes_from(c, GUITAR_PAT[guitar], "gtr", GUITAR, 45, 66, s, bar, level)
        if harp:
            notes_from(c, harp, "arp", HARP, 58, 77, s, bar, level)
        if pad:
            stab(s, bar, 0, c, "pad", PLING, 55, 72, 26 * level, count=2, length=18, every=6, style="flat")

    TC, WC = CHORDS["T"], CHORDS["W"]

    # --- intro: three bars (after a silence of ten ticks): the tune alone over a pedal; phase 2 comes in a beat late and stumbles on -------------------
    late = 6 if P else 0
    for i, text in enumerate((T[0], T[1], T[7])):
        sing(s, i, text, [Voice("lead", PLING, 0, 0.8, "cry", 3, octave_fit=True), harp_hit(0.5)] + ([flute12(0.5)] if P else []), vel=72 if P == 0 else 84, start=late if i < 2 else 0, cut=(i == 1))
        c = ["Em", "B7", "B7"][i]
        held(s, "bass", BASS, s.tick(i), root_of(c), 18, 60 if P == 0 else 84, 76 if P == 0 else 96, every=6)
        notes_from(c, [(t, k, 30 + 4 * i) for t, k in zip(range(0, 18, 3), (0, 1, 2, 3, 2, 1))], "arp", HARP, 58, 77, s, i)
    if P == 0:
        s.drum("kick", s.tick(2, 0), BASEDRUM, 70)
        s.drum("kick", s.tick(2, 6), BASEDRUM, 60)
        roll(s, s.tick(2, 12), s.tick(3), 2, 34, 84)
    else:
        s.drum("kick", s.tick(0), BASEDRUM, 96)
        drums(s, 1, DRUMS["hemiA"], 0.8)
        drums(s, 2, DRUMS["hemiB"], 0.8)
        roll(s, s.tick(2, 10), s.tick(3), 2, 40, 96)

    # --- A (16): the tune twice on the pling, picking and the pah-pah, a heavy first beat; phase 2 falls into hemiola at the end of each statement ----
    for r in range(2):
        for i in range(8):
            v = [pling, harp_hit(0.5)] + ([flute12(0.45)] if (r == 1 or i >= 4) else [])
            lead(0, 8 * r + i, T[i], v, 86 if P == 0 else 90)
            accompany(0, 8 * r + i, TC, "oom" if (r == 0 or P == 0) else "drive", "waltz", "pah", None, level=0.9, hemi=(P == 1 and i >= 6), drum_level=0.9)
        fill(8 * r + 7, 12, hot=bool(P))

    # --- A' (16): the tune again, bit and flute join, the harp circles; phase 2: the second statement a beat late against the beat -------------------
    for r in range(2):
        for i in range(8):
            v = [pling, bit(0.6), flute12(0.6)] + ([gtr_lo(0.7)] if i >= 4 else [])
            displaced = P == 1 and r == 1
            lead(16, 8 * r + i, mel(TV, i), v, 88 if P == 0 else 92, start=6 if displaced else 0, cut=displaced and i == 7)
            accompany(16, 8 * r + i, TC, "drive" if (r == 1 or i >= 4) else "oom", "drive" if (r == 1 or i >= 4) else "waltz", "pah", HARP_ARP if (r == 1 or i >= 4) else None,
                      drone=(i >= 4), level=0.95, hemi=(P == 1 and r == 0 and i >= 6))
        fill(16 + 8 * r + 7, 12, hot=True)

    # --- B (16): the lament - a flute and a pling, a pad, a slow harp, a half-time beat (phase 2 limps in 3/2) ------------------------------------------
    for r in range(2):
        for i in range(8):
            lead(32, 8 * r + i, LAMENT[i], [Voice("lead", FLUTE, 12, 1.0, "cry", 3, octave_fit=True), Voice("dbl", PLING, 0, 0.7, "cry", 3, octave_fit=True),
                                           Voice("echo", HARP, 0, 0.3, "none", delay=6, octave_fit=True)], 84 if P == 0 else 88)
            accompany(32, 8 * r + i, WC, "half", "quiet", "arp" if r == 1 else None, HARP_SLOW if r == 0 else None, pad=True, level=0.9, drum_level=0.85, hemi=(P == 1 and r == 1 and i < 6))
        roll(s, s.tick(s.b(32 + 8 * r + 7), 6), s.tick(s.b(32 + 8 * r + 8)), 3, 30, 80)

    # --- A'' (16): the bit and the harp lead, the flute above; the second time the guitar answers an octave down ----------------------------------
    for r in range(2):
        for i in range(8):
            v = [Voice("lead", BIT, 0, 0.9, "cry", 3, octave_fit=True), harp_hit(0.7), flute12(0.7)] + ([pling] if (r == 1 or i >= 4) else []) + ([gtr_lo(0.7)] if r == 1 else [])
            displaced = P == 1 and r == 1
            lead(48, 8 * r + i, mel(TV, i), v, 90 if P == 0 else 94, start=3 if displaced else 0, cut=displaced and i == 7)
            hot = r == 1 and i >= 4
            accompany(48, 8 * r + i, TC, "trip" if hot else "drive", "peak" if hot else "drive", "pah", HARP_RISE if hot else None,
                      drone=(i >= 4), level=1.0, hemi=(P == 1 and i >= 6))
        fill(48 + 8 * r + 7, 12, hot=True)

    # --- C (16): the tune climbing in sequence (flute above), then the tune in the high register with everything underneath -----------------------------
    for i in range(8):
        v = [Voice("lead", PLING, 0, 1.0, "cry", 3, octave_fit=True), bit(0.55), Voice("oct", FLUTE, 0, 0.8, "cry", 3, octave_fit=True), gtr_lo(0.6)]
        lead(64, i, SEQ[i], v, 92)
        accompany(64, i, CHORDS["C"], "drive", "drive", "arp", None, drone=True, hemi=(P == 1))
    fill(64 + 7, 12, hot=True)
    for i in range(8):
        v = [Voice("lead", FLUTE, 0, 1.0, "cry", 3, octave_fit=True), Voice("dbl", PLING, -12, 0.9, "cry", 3, octave_fit=True), Voice("oct", HARP, -12, 0.6, "none", octave_fit=True),
             Voice("dbl", BIT, -12, 0.5, "cry", 3, octave_fit=True), Voice("low", GUITAR, -24, 0.7, "cry", 3, octave_fit=True)]
        lead(64, 8 + i, mel(TV, i), v, 92, transpose=12)
        accompany(64, 8 + i, TC, "trip", "drive", "arp", None, drone=True)
    fill(64 + 15, 12, hot=True)

    # --- B' (8): the lament on the piano, a low guitar answering, a pad and a drone ------------------------------------------------------------------
    for i in range(8):
        lead(80, i, LAMENT2[i], [Voice("lead", HARP, 0, 1.0, "cry", 3, octave_fit=True), bit(0.35), Voice("low", GUITAR, -12, 0.55, "cry", 3, octave_fit=True),
                                 Voice("echo", FLUTE, 12, 0.35, "none", delay=9, octave_fit=True)], 88 if P == 0 else 92)
        accompany(80, i, WC, "half", "quiet", None, None, drone=True, pad=True, level=0.85, drum_level=0.8, hemi=(P == 1 and i < 6))
    roll(s, s.tick(s.b(80 + 7), 6), s.tick(s.b(80 + 8)), 3, 30, 78)

    # --- build (8): long notes climbing (B4 E5 G5 F#5) struck again and again, the drums back, a roll, a gap before the peak -----------------------------
    for k, (n, length) in enumerate(((71, 36), (76, 36), (79, 36), (78, 36))):
        t0 = s.tick(s.b(88), 36 * k)
        pitch = n if n <= 78 else n - 12
        length = 36 if k < 3 else 24
        held(s, "lead", PLING, t0, pitch, length, 46 + 8 * k, 84 + 4 * k, every=3)
        held(s, "oct", FLUTE, t0, fit(FLUTE, n + 12), length, 30 + 8 * k, 70 + 4 * k, every=3)
        if k >= 1:
            held(s, "dbl", BIT, t0, pitch, length, 20, 60, every=6)
    for i in range(8):
        bar = s.b(88 + i)
        c = CHORDS["BUILD"][i]
        if i < 7:
            bass(s, bar, c, BASS_PAT["half"] if i < 4 else BASS_PAT["drive"], 0.95, high=52)
            notes_from(c, GUITAR_PAT["arp"], "gtr", GUITAR, 45, 66, s, bar, 0.9)
            notes_from(c, [(t, 2 + (t // 3) % 3, 22 + 3 * i) for t in range(0, 18, 3)], "arp", HARP, 58, 77, s, bar)
        else:
            bass(s, bar, c, [(0, "r", 96), (6, "r", 76)], 1.0, high=52)
        if 2 <= i < 7:
            if P == 1 and i < 6:
                drums(s, bar, DRUMS["hemiA" if i % 2 == 0 else "hemiB"], 0.7 + 0.03 * i)
            else:
                drums(s, bar, DRUMS["build"] if i < 6 else DRUMS["drive"], 0.7 + 0.04 * i)
    roll(s, s.tick(s.b(94), 6), s.tick(s.b(95)), 3, 40, 80)
    roll(s, s.tick(s.b(95)), s.tick(s.b(95), 8), 2, 60, 100)
    for t, v in ((14, 70), (16, 100)):                      # the gap (ticks 8 to 13 are empty), then a drum pick-up
        s.drum("kick", s.tick(s.b(95), t), BASEDRUM, v)

    # --- PEAK (24): the tune three times as a unison (pling, bit, the flute above, the guitar below); the third time the harp circles and the end climbs to A5 --------
    for r in range(3):
        j0 = 96 + 8 * r
        for i in range(8):
            bar = s.b(j0 + i)
            text = mel(TX if r == 2 else T, i)
            v = [pling, bit(0.6), flute12(0.75), gtr_lo(0.75)] + ([harp_hit(0.55)] if r >= 1 else [])
            displaced = P == 1 and r == 2
            sing(s, bar, text, v, vel=96, start=6 if displaced else 0, cut=displaced and i == 7)
            c = TC[i]
            hemi = P == 1 and r == 1
            if hemi:
                bass(s, bar, c, BASS_PAT["hemiA" if i % 2 == 0 else "hemiB"], 1.0, high=52)
                drums(s, bar, DRUMS["hemiA" if i % 2 == 0 else "hemiB"], 1.0)
            else:
                bass(s, bar, c, BASS_PAT["sting" if (P == 1 and i == 7) else "trip"], 1.0, high=52)
                drums(s, bar, DRUMS["peak"], 1.0)
            bass(s, bar, c, DRONE, 1.0, role="drone", instrument=DIDGERIDOO)
            if r >= 1:
                notes_from(c, HARP_RISE if r == 2 else [(6, 3, 44), (12, 3, 44)], "arp", HARP, 58, 77, s, bar)
            elif i % 2 == 0:
                for t in (6, 12):
                    stab(s, bar, t, c, "gtr", GUITAR, 47, 64, 46, count=3)
        stab(s, s.b(j0), 0, "Em", "stab", PLING, 57, 77, 92, count=3, length=12, every=3)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        fill(j0 + 3, 6, hot=True)
        fill(j0 + 7, 12, hot=bool(P))

    # --- calm (16): the piano, a pad, a low drum; then the beat returns for the loop -----------------------------------------------------------------
    for r in range(2):
        base = 120 + 8 * r
        for i in range(8):
            bar = s.b(base + i)
            if r == 0:
                v = [Voice("lead", HARP, 0, 1.0, "cry", 3, octave_fit=True), Voice("dbl", PLING, 0, 0.5, "cry", 3, octave_fit=True), Voice("echo", FLUTE, 12, 0.3, "cry", 3, octave_fit=True)]
            else:
                v = [pling, harp_hit(0.5), flute12(0.5)]
            sing(s, bar, T[i], v, vel=70 if r == 0 else 86)
            c = TC[i]
            bass(s, bar, c, BASS_PAT["half"] if r == 0 else (BASS_PAT["oom"] if i < 4 else BASS_PAT["drive"]), 0.8 if r == 0 else 0.95, high=52)
            if r == 0:
                stab(s, bar, 0, c, "pad", PLING, 55, 72, 26, count=2, length=18, every=6, style="flat")
                notes_from(c, GUITAR_PAT["arp"], "gtr", GUITAR, 45, 66, s, bar, 0.6)
                if i >= 4:
                    drums(s, bar, DRUMS["quiet"], 0.8)
            else:
                notes_from(c, GUITAR_PAT["arp"], "gtr", GUITAR, 45, 66, s, bar, 0.9)
                drums(s, bar, DRUMS["quiet"] if i < 2 else (DRUMS["waltz"] if i < 6 else DRUMS["drive"]), 0.85)
    roll(s, s.tick(s.b(120 + 15), 6), s.tick(s.b(120 + 16)), 3, 40, 86)

    return s


SECTIONS = [("A", 0, 16), ("A'", 16, 16), ("B", 32, 16), ("A''", 48, 16), ("C", 64, 16), ("B'", 80, 8), ("build", 88, 8), ("PEAK", 96, 24), ("calm", 120, 16)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in (0, 1):
        score = build(phase)
        finish(score, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], SECTIONS)


if __name__ == "__main__":
    main()
