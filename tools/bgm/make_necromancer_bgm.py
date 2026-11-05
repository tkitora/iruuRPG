#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the reaper of the snow mountain in two phases.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

Boss class 2 "sorrow and fated battle" (phase 1) and class 6 "limit break and reversal" (phase 2). One piece, rebuilt on the 16th-note machine-gun guide (t276): base tempo 150 BPM,
a song tick is a sixteenth (10 ticks a second), a bar of 4/4 is 16 ticks (it meets the engine's 16-tick line every bar), 80 bars of body and a 4-bar intro (1344 ticks, 134.4 s), loop from tick 64.

The tune (the 'crying melody', eight bars, D minor) is kept: a rising fifth into the tonic held long (A4 D5), a sigh down (C5 A4), a sequence over Bb and F, a climb to the held E5 over C, then an answer that
lifts to the leading tone and hangs on E5 over A7. It is written in the notes of D minor = F major, so it is the SAME NOTES in both phases: phase 1 harmonises it in D minor (Dm Bb F C | Dm Bb Gm A7,
the leading tone C#), phase 2 in the relative major F (F C7 Dm Bb | F C7 Bb C7, C natural) and the last 4 bars of its second peak are lifted a whole step into G major. The lead is a solo violin
made of pling / bit / flute / harp: a long note is struck again every 2 ticks with the loudness breathing (a vibrato).

The melody keeps the base tempo (quarters, halves) while the accompaniment is subdivided, so the piece feels 2x to 4x faster than its 150 BPM:
    intro    bass = quarter-note roots played softly, no drums (a roll at the very end)
    A, A'    bass = tight eighth-note root repeats (feels like 300), drums: backbeat
    B, B'    bass = eighths with sudden 16th double hits and 3-3-2 (triplet-feel) figures, the line moving up and down; the lament above is taken away to a flute and a pad
    A'', C   the same undulation, then half bars of sixteenths (bursts) as the push towards the peak
    build    quarters -> eighths -> doubles -> bursts, a drum roll, a gap
    PEAK     bass = full 16th heavy machine gun (feels like 600), hats in sixteenths too, and the melody is the same tune ENLARGED to half and whole notes held by re-striking: the very fast bass
             under the very broad tune is the contrast the guide asks for
    calm     the piano on quarters, a pad; the beat comes back in eighths for the loop
Phase 2 is the same length and loop point, one rung higher everywhere: the doubles come earlier, the machine gun starts in A'' (bar 3), the unison (pling, bit, flute an octave up, guitar an octave down) plays
from the start, the drums are doubled, the harmony turns major and the peak is lifted a whole step at its end.
Sections (bars of the body): A 8, A' 8, B (the lament) 8, A'' 8, C 8, B' 8, build 8, PEAK 16, calm 8. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Reaper's Arc (Boss004)", "Reaper's Arc, Sundered (Boss004)")
FILES = ("necromancer_phase1.nbs", "necromancer_phase2.nbs")
DESCRIPTIONS = ("Snow mountain reaper, phase 1 (sorrow and fated battle): 150 BPM, a crying violin tune in D minor over eighth-note bass that grows to a sixteenth machine gun; same bars as phase 2.",
                "Snow mountain reaper, phase 2 (limit break and reversal): the same tune turned to F major, a unison fanfare, the machine gun starts early, lifted a whole step at the end.")
TPS, BAR, INTRO, BODY = 10, 16, 4, 80


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
SEQ = ["F4:4 Bb4:6 A4:2 F4:4", "G4:4 C5:6 Bb4:2 G4:4", "A4:4 D5:6 C5:2 A4:4", "D5:4 F5:6 E5:2 F5:4",
       "F5:4 Bb5:6 A5:2 F5:4", "G5:4 C6:6 Bb5:2 G5:4", "A5:4 D6:6 C6:2 A5:4", "A5:4 C#6:4 E6:8"]
CALM = [["A4:8 D5:4 C5:4", "Bb4:8 A4:4 F4:4", "G4:8 Bb4:4 D5:4", "C#5:8 E5:8", "D5:8 E5:4 F5:4", "D5:6 C5:2 Bb4:4 D5:4", "G4:2 Bb4:2 D5:4 C5:4 Bb4:4", "A4:4 C#5:4 E5:8"],
        ["B4:8 E5:4 D5:4", "C5:8 B4:4 G4:4", "A4:8 C5:4 E5:4", "G4:4 Bb4:4 D5:8", "G4:4 Bb4:4 E5:8", "A4:4 D5:6 E5:2 D5:4", "D5:6 C5:2 Bb4:4 D5:4", "A4:4 C5:4 E5:8"]]
# the tune ENLARGED for the peak: half and whole notes (held by re-striking); the second time it climbs, in phase 2 the last four bars are written low and lifted a whole step
PK = ["A4:4 D5:12", "D5:8 F5:8", "C5:4 F5:12", "E5:8 D5:4 C5:4", "A4:4 D5:12", "F5:8 D5:8", "D5:8 Bb4:8", "C#5:8 E5:8"]
PK2 = ["A4:4 D5:12", "D5:4 F5:12", "C5:4 F5:12", "E5:16", "A4:4 D5:12", "D5:4 F5:12", "D5:8 F5:8", "C#5:4 E5:12"]
PKUP = ["A4:4 C5:12", "G4:4 Bb4:12", "Bb4:4 D5:12", "E5:16"]          # bars 4-7 of the second peak in phase 2, written in F and sounding a whole step higher (G major)
CHORDS = {
    "A": (["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A7"], ["F", "C7", "Dm", "Bb", "F", "C7", "Bb", "C7"]),
    "A+2": (None, ["G", "D7", "Em", "C", "G", "D7", "C", "D7"]),
    "B": (["Gm", "F", "Eb", "D", "Gm", "F", "Eb", "A7"], ["Bb", "F", "Eb", "Dm", "Bb", "F", "Eb", "C7"]),
    "C": (["Bb", "C", "Dm", "Dm", "Bb", "C", "Dm", "A7"], ["Bb", "C", "F", "F", "Bb", "C", "F", "C7"]),
    "BUILD": (["Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "A7", "A7"], ["F", "F", "Bb", "Bb", "Gm", "Gm", "C7", "C7"]),
    "CALM": (["Dm", "Bb", "Gm", "A7", "Dm", "Bb", "Gm", "A7"], ["G", "Em", "Am", "Gm", "C7", "F", "Bb", "C7"]),
}

# --- patterns of a bar of 16 ticks ---------------------------------------------------------------------------------------
DRUMS = {
    "rock": {"kick": [(0, 100), (6, 60), (10, 74)], "snare": [(4, 90), (12, 98)], "hat": [(t, 44 if t % 4 == 0 else 30) for t in range(0, 16, 2)]},
    "drive": {"kick": [(0, 100), (3, 62), (6, 76), (8, 98), (11, 62), (14, 76)], "snare": [(4, 96), (12, 100), (15, 46)], "hat": [(t, 46 if t % 4 == 0 else 32) for t in range(0, 16, 2)]},
    "half": {"kick": [(0, 92), (10, 56)], "snare": [(8, 84)], "hat": [(t, 26) for t in range(0, 16, 4)]},
    "march": {"kick": [(0, 84), (4, 50), (8, 70), (12, 50)], "snare": [(8, 76), (4, 32), (12, 32)]},
    "peak": {"kick": [(0, 100), (2, 56), (5, 70), (6, 60), (8, 100), (10, 56), (13, 70), (14, 88)], "snare": [(4, 100), (12, 100), (7, 48), (15, 52)],
             "hat": [(t, 50 if t % 4 == 0 else (36 if t % 2 == 0 else 26)) for t in range(16)], "tom": [(3, 52, 57), (11, 52, 54)]},
    "build": {"kick": [(0, 92), (4, 70), (8, 92), (12, 70)], "hat": [(t, 34) for t in range(0, 16, 2)]},
}


def e8(octave_at=None):
    return [(t, "o" if t == octave_at else "r", 96 if t == 0 else 80 if t == 8 else 60 if t % 4 == 0 else 46) for t in range(0, 16, 2)]


BASS_PAT = {  # (tick, kind, velocity)
    "Q": [(0, "r", 64), (4, "r", 38), (8, "r", 50), (12, "r", 38)],
    "half": [(0, "r", 78), (8, "r", 42)],
    "E": e8(),
    "E_o": e8(14),
    # eighths with sudden 16th doubles (a note at 7 and 15 right after the eighth before it)
    "ED_a": [(0, "r", 98), (2, "r", 48), (4, "r", 64), (6, "r", 52), (7, "r", 42), (8, "r", 84), (10, "r", 48), (12, "r", 64), (14, "r", 52), (15, "o", 46)],
    # the same, the line moving up and down (root, third, fifth, back)
    "ED_b": [(0, "r", 98), (2, "r", 50), (4, "r", 66), (5, "r", 40), (6, "t", 56), (8, "f", 84), (10, "f", 50), (12, "t", 66), (13, "t", 40), (14, "r", 56)],
    # 3-3-2 / 3-3-2: the triplet-feel figure
    "TRI": [(0, "r", 98), (3, "r", 62), (6, "f", 72), (8, "r", 86), (11, "t", 62), (14, "o", 68)],
    # four steady eighths, then the machine gun for the last two beats
    "BURST": [(0, "r", 98), (2, "r", 50), (4, "r", 66), (6, "r", 50)] + [(t, "o" if t in (11, 15) else "r", 84 if t == 8 else 56 if t % 2 == 0 else 44) for t in range(8, 16)],
    "S16": [(t, "o" if t % 4 == 3 else "r", 98 if t == 0 else 84 if t == 8 else 66 if t % 4 == 0 else 50 if t % 2 == 0 else 42) for t in range(16)],
    "S16b": [(t, "o" if t % 4 == 3 else ("f" if t in (5, 13) else "r"), 98 if t == 0 else 84 if t == 8 else 66 if t % 4 == 0 else 50 if t % 2 == 0 else 42) for t in range(16)],
    "END": [(0, "r", 96), (4, "r", 76), (8, "r", 60)],
}
GUITAR_PAT = {
    "arp8": [(t, i, v) for t, i, v in zip(range(0, 16, 2), (0, 2, 3, 2, 1, 2, 3, 2), (52, 36, 40, 36, 46, 36, 40, 38))],
    "chug": [(t, i, 66 if t % 8 == 0 else 44) for t in range(0, 16, 2) for i in (0, 2)],
}
HARP_ARP = [(t, i, 26) for t, i in zip(range(1, 16, 2), (2, 3, 4, 3, 2, 3, 4, 3))]
HARP_STEP = [(t, i, 34) for t, i in zip(range(0, 16, 4), (0, 1, 2, 1))]
DRONE = [(0, "r", 54), (4, "r", 34), (8, "r", 50), (12, "r", 34)]

# the bass of every section: one pattern a bar, [phase 1, phase 2]
PLAN = {
    "A": (["E"] * 4 + ["E_o"] * 4, ["E", "E", "E_o", "E_o", "ED_a", "ED_a", "ED_a", "ED_b"]),
    "A'": (["E"] * 6 + ["ED_a"] * 2, ["E", "E", "ED_a", "ED_a", "ED_b", "ED_b", "BURST", "BURST"]),
    "B": (["ED_a", "ED_b", "TRI", "ED_b", "ED_a", "ED_b", "TRI", "BURST"], ["ED_b", "TRI", "ED_b", "BURST", "ED_b", "TRI", "BURST", "BURST"]),
    "A''": (["ED_a", "ED_b", "ED_a", "TRI", "BURST", "BURST", "BURST", "BURST"], ["ED_b", "TRI", "S16", "S16", "S16", "S16", "S16", "S16b"]),
    "C": (["TRI", "ED_b", "TRI", "ED_b", "BURST", "BURST", "BURST", "BURST"], ["BURST", "BURST", "S16", "S16", "S16b", "S16", "S16", "S16b"]),
    "B'": (["E", "E", "ED_a", "E", "E", "E", "ED_a", "ED_b"], ["ED_a", "E", "ED_b", "E", "ED_a", "E", "ED_b", "BURST"]),
    "build": (["half", "half", "E", "E", "ED_b", "ED_b", "BURST", "END"], ["E", "E", "ED_b", "ED_b", "BURST", "BURST", "S16", "END"]),
    "calm": (["Q"] * 4 + ["E"] * 4, ["Q"] * 4 + ["E"] * 4),
}


def build(P):
    s = Score(TPS, BAR, INTRO, BODY)

    def sec_chords(key, i):
        return CHORDS[key][P][i]

    def mel(lst, i):
        return maj(lst[i]) if P else lst[i]

    pling = Voice("lead", PLING, 0, 1.0, "cry", 2)
    bit = lambda g=0.55, off=0: Voice("dbl", BIT, 0, g, "cry", 4 if off else 2, offbeat=off)
    harp_hit = lambda g=0.5: Voice("dbl", HARP, 0, g, "none")
    flute12 = lambda g=0.6, off=0: Voice("oct", FLUTE, 12, g, "cry", 4 if off else 2, offbeat=off)
    gtr_lo = lambda g=0.7, off=0: Voice("low", GUITAR, -12, g, "cry", 4 if off else 2, offbeat=off)

    def drum_fill(j, length=4, hot=False):
        t0 = s.tick(s.b(j)) + 16 - length
        roll(s, t0, t0 + length, 1 if hot else 2, 40, 96)

    def accompany(j, sec, key, i, drum_pat, guitar_pat=None, harp_pat=None, drone=False, pad=False, level=1.0, drum_level=1.0, bass_level=None):
        bar = s.b(j + i)
        c = sec_chords(key, i)
        name = PLAN[sec][P][i]
        bass(s, bar, c, BASS_PAT[name], level if bass_level is None else bass_level, high=54)
        if drone:
            bass(s, bar, c, DRONE, level, role="drone", instrument=DIDGERIDOO)
        if drum_pat:
            drums(s, bar, DRUMS[drum_pat], drum_level)
        if guitar_pat:
            notes_from(c, GUITAR_PAT[guitar_pat], "gtr", GUITAR, 45, 66, s, bar, level)
        if harp_pat:
            notes_from(c, harp_pat, "arp", HARP, 62, 77, s, bar, level)
        if pad:
            stab(s, bar, 0, c, "pad", PLING, 55, 72, 26 * level, count=2, length=16, every=4, style="flat")

    # --- intro: four bars, the tune alone over quarter-note roots played softly; no drums until a short roll into the loop -----------------
    idx = (0, 1, 6, 7)
    if P == 0:
        voices = [Voice("lead", PLING, 0, 0.8, "cry", 2), harp_hit(0.5)]
    else:
        voices = [pling, bit(0.7), flute12(0.7), gtr_lo(0.8)]
    for k, ai in enumerate(idx):
        c = CHORDS["A"][P][ai]
        sing(s, k, mel(A, ai), voices, vel=70 if P == 0 else 90)
        bass(s, k, c, BASS_PAT["Q"], 0.8 if P == 0 else 1.0, high=54)
        notes_from(c, [(t, kk, (30 + 3 * k) if P == 0 else 40) for t, kk in zip(range(2, 16, 4), (1, 2, 3, 2))], "arp", HARP, 62, 77, s, k)
    if P == 1:
        stab(s, 0, 0, "F", "stab", PLING, 57, 77, 92, count=3, length=8, every=2)
        stab(s, 0, 0, "F", "stab", GUITAR, 45, 64, 80, count=3, length=8, every=2)
        s.drum("kick", s.tick(0), BASEDRUM, 100)
        s.drum("snare", s.tick(0), SNARE, 90)
    roll(s, s.tick(3, 8), s.tick(4), 1 if P else 2, 34 if P == 0 else 44, 84 if P == 0 else 100)

    # --- A (8): the tune on the pling; picking, a heavy beat that does not hurry; the bass: tight eighths ----------------------------------
    for i in range(8):
        if P == 0:
            v = [pling, harp_hit(0.5)] + ([flute12(0.45, 2)] if i >= 4 else [])
            accompany(0, "A", "A", i, "rock", "arp8", level=0.9)
        else:
            v = [pling, bit(0.65, 2), gtr_lo(0.75, 2)] + ([flute12(0.65, 2)] if i >= 4 else [])
            accompany(0, "A", "A", i, "drive", "arp8" if i < 4 else None, None, drone=(i >= 4), level=0.95)
        sing(s, s.b(i), mel(A, i), v, vel=86 if P == 0 else 92)
    if P == 1:
        stab(s, s.b(0), 0, "F", "stab", PLING, 57, 77, 80, count=3, length=6, every=2)
    drum_fill(7, 4 if P == 0 else 8, hot=(P == 1))

    # --- A' (8): the tune a second time, the ending changed; bit and flute join, the harp shimmers in the second half -----------------------
    for i in range(8):
        v = [pling, bit(0.6, 2), flute12(0.6, 2)] + ([gtr_lo(0.7, 2)] if i >= 4 else [])
        sing(s, s.b(8 + i), mel(A2, i), v, vel=88 if P == 0 else 94)
        late = i >= 4
        if P == 0:
            accompany(8, "A'", "A", i, "rock" if not late else "drive", "arp8", HARP_ARP if late else None, drone=late, level=0.95)
        else:
            accompany(8, "A'", "A", i, "drive" if not late else "peak", "arp8" if not late else None, HARP_ARP if late else None, drone=late, level=1.0)
            if i % 4 == 0:
                stab(s, s.b(8 + i), 0, sec_chords("A", i), "stab", GUITAR, 45, 64, 54, count=3, length=6, every=2)
    drum_fill(8 + 3, 4, hot=(P == 1))
    drum_fill(8 + 7, 8, hot=True)

    # --- B (8): the lament; the upper parts take away: flute and pling, a pad, a half-time beat; the bass undulates (doubles, 3-3-2, up and down) --
    for i in range(8):
        c = sec_chords("B", i)
        sing(s, s.b(16 + i), mel(LAMENT, i), [Voice("lead", FLUTE, 12, 1.0, "cry", 2), Voice("dbl", PLING, 0, 0.7, "cry", 4, offbeat=2), Voice("echo", HARP, 0, 0.3, "none", delay=3)],
             vel=84 if P == 0 else 88)
        accompany(16, "B", "B", i, "half" if P == 0 else "march", None, HARP_STEP, pad=True, level=0.9 if P == 0 else 1.0, drum_level=0.85, bass_level=1.0)
        if P == 1 and i in (3, 7):
            stab(s, s.b(16 + i), 0, c, "stab", PLING, 55, 72, 60, count=3, length=8, every=2)
    roll(s, s.tick(s.b(16 + 7), 8), s.tick(s.b(16 + 8)), 2, 34, 80)

    # --- A'' (8): the bit and the harp lead, the flute an octave up; the answer is picked by the guitar; the bass bursts into sixteens -----------
    for i in range(8):
        v = [Voice("lead", BIT, 0, 0.9, "cry", 2), harp_hit(0.7), flute12(0.7, 2)] + ([pling] if i >= 4 else [])
        sing(s, s.b(24 + i), mel(A2, i), v, vel=90 if P == 0 else 96)
        if P == 0:
            accompany(24, "A''", "A", i, "drive", "arp8" if i < 4 else None, HARP_ARP if i >= 4 else None, drone=True)
        else:
            accompany(24, "A''", "A", i, "drive" if i < 2 else "peak", None, HARP_ARP if i >= 4 else None, drone=True)
            if i >= 4:
                sing(s, s.b(24 + i), mel(A2, i), [gtr_lo(0.8, 2)], vel=90)
    drum_fill(24 + 3, 4, hot=True)
    drum_fill(24 + 7, 8, hot=True)

    # --- C (8): the tune in sequence - low and in the middle, then four bars an octave higher with the flute alone on top ---------------------
    for i in range(8):
        if i < 4:
            v = [pling, bit(0.6, 2), flute12(0.6, 2), gtr_lo(0.6, 2)]
        else:
            v = [Voice("lead", FLUTE, 0, 1.0, "cry", 2), Voice("lead", PLING, 0, 1.0, "cry", 2, high=77), Voice("dbl", PLING, -12, 0.9, "cry", 2), Voice("oct", HARP, 0, 0.6, "none"),
                 Voice("dbl", BIT, -12, 0.55, "cry", 4, offbeat=2), Voice("low", GUITAR, -24, 0.7, "cry", 4, offbeat=2)]
        sing(s, s.b(32 + i), mel(SEQ, i), v, vel=92)
        accompany(32, "C", "C", i, "drive" if (P == 0 or i < 4) else "peak", "chug" if (i < 4 and P == 0) else None, None, drone=True)
    drum_fill(32 + 3, 4, hot=True)
    drum_fill(32 + 7, 8, hot=True)

    # --- B' (8): the lament again, the piano alone with a low guitar answering, a pad and a drone, a march; the bass breathes (eighths, a double now and then) ---
    for i in range(8):
        sing(s, s.b(40 + i), mel(LAMENT2, i), [Voice("lead", HARP, 0, 1.0, "cry", 2), bit(0.35, 2), Voice("low", GUITAR, -12, 0.55, "cry", 4, offbeat=2),
                                              Voice("echo", FLUTE, 12, 0.35, "none", delay=6)], vel=88 if P == 0 else 92)
        accompany(40, "B'", "B", i, "march", None, None, drone=True, pad=True, level=0.85, drum_level=0.8 if P == 0 else 0.95)
    roll(s, s.tick(s.b(40 + 7), 8), s.tick(s.b(40 + 8)), 2, 30, 78)

    # --- build (8): long notes climbing (A4 D5 F5 E5) struck again and again, the bass climbing the ladder, the drums back, a roll, a gap ------------
    for k, n in enumerate([69, 74, 77, 76]):
        t0 = s.tick(s.b(48), k * 32) if k < 3 else s.tick(s.b(54))
        length = 32 if k < 3 else 24
        held(s, "lead", PLING, t0, n, length, 46 + 8 * k, 84 + 4 * k, every=2)
        held(s, "oct", FLUTE, t0, n + 12, length, 30 + 8 * k, 70 + 4 * k, every=4)
        if k >= 1:
            held(s, "dbl", BIT, t0, n, length, 20, 60, every=4)
    for i in range(8):
        bar = s.b(48 + i)
        c = sec_chords("BUILD", i)
        bass(s, bar, c, BASS_PAT[PLAN["build"][P][i]], 0.95 if i < 7 else 1.0, high=54)
        if i < 7:
            notes_from(c, GUITAR_PAT["arp8"], "gtr", GUITAR, 45, 66, s, bar, 0.9)
            notes_from(c, [(t, 2 + (t // 2) % 3, 22 + 3 * i) for t in range(1, 16, 4)], "arp", HARP, 62, 77, s, bar)
        if 2 <= i < 7:
            drums(s, bar, DRUMS["build"] if i < 6 else DRUMS["drive"], 0.7 + 0.04 * i)
    roll(s, s.tick(s.b(54), 8), s.tick(s.b(55)), 2, 40, 80)
    roll(s, s.tick(s.b(55)), s.tick(s.b(55), 10), 1, 60, 100)
    for t, v in ((12, 70), (14, 82), (15, 100)):          # the gap (ticks 10 to 11 are empty), then a drum pick-up into the peak
        s.drum("kick", s.tick(s.b(55), t), BASEDRUM, v)

    # --- PEAK (16): the tune ENLARGED (half and whole notes) as a unison - pling, bit, the flute an octave up, the guitar an octave down - twice; under it the
    #     bass is a full sixteenth machine gun, the hats in sixteenths, the toms; in phase 2 the second time is lifted a whole step at its end ----------
    for r in range(2):
        j0 = 56 + 8 * r
        for i in range(8):
            bar = s.b(j0 + i)
            up = P == 1 and r == 1 and i >= 4
            text = PKUP[i - 4] if up else (PK if r == 0 else PK2)[i]
            c = CHORDS["A+2"][1][i] if up else CHORDS["A"][P][i]
            text = text if up else mel([text], 0)
            v = [pling, bit(0.6, 2), flute12(0.75, 2), gtr_lo(0.75, 2)]
            if r == 1:
                v.append(harp_hit(0.55))
            sing(s, bar, text, v, transpose=2 if up else 0, vel=96 if P == 0 else 98)
            bass(s, bar, c, BASS_PAT["S16b" if (r == 1 and i % 2) else "S16"], 1.0, high=54)
            if i % 2 == 0:
                bass(s, bar, c, DRONE, 1.0, role="drone", instrument=DIDGERIDOO)
            drums(s, bar, DRUMS["peak"], 1.0)
            if P == 1 and i in (0, 4):
                stab(s, bar, 0, c, "stab", GUITAR, 45, 64, 70, count=3, length=6, every=2)
        stab(s, s.b(j0), 0, CHORDS["A"][P][0], "stab", PLING, 57, 77, 92, count=3, length=6, every=2)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        if P == 1 and r == 1:
            stab(s, s.b(j0 + 4), 0, "G", "stab", PLING, 57, 77, 96, count=3, length=6, every=2)
            s.drum("tom", s.tick(s.b(j0 + 4)), BASEDRUM, 100, 54)
        drum_fill(j0 + 3, 4, hot=True)
        drum_fill(j0 + 7, 8, hot=(P == 1))

    # --- calm (8): the piano, a pad; then the beat comes back (quarters, then eighths) for the loop -------------------------------------------
    for i in range(8):
        quiet = i < 4
        if i < 6:
            v = [Voice("lead", HARP, 0, 1.0, "cry", 2), Voice("dbl", PLING, 0, 0.5, "cry", 4, offbeat=2), Voice("echo", FLUTE, 12, 0.3 if quiet else 0.5, "cry", 4)]
        else:
            v = [pling, harp_hit(0.5), flute12(0.5)]
        sing(s, s.b(72 + i), CALM[P][i], v, vel=70 if quiet else 86)
        c = sec_chords("CALM", i)
        bass(s, s.b(72 + i), c, BASS_PAT[PLAN["calm"][P][i]], 0.8 if quiet else 0.95, high=54)
        notes_from(c, GUITAR_PAT["arp8"], "gtr", GUITAR, 45, 66, s, s.b(72 + i), 0.7 if quiet else 0.9)
        if quiet:
            stab(s, s.b(72 + i), 0, c, "pad", PLING, 55, 72, 26, count=2, length=16, every=4, style="flat")
        if i >= 2:
            drums(s, s.b(72 + i), DRUMS["half"] if i < 4 else (DRUMS["march"] if i < 6 else DRUMS["rock"]), 0.8 if i < 6 else 0.9)
    roll(s, s.tick(s.b(72 + 7), 8), s.tick(s.b(72 + 8)), 2, 40, 84)

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
