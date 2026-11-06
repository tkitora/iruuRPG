#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the faceless scythe reaper of the snow mountain (rebuilt from zero, t281).

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

Class 2 "sorrow and fated battle" in STYLE 1 ("royal high-speed battle"), 150 BPM, a song tick is a sixteenth (10 ticks a second), a 4/4 bar is 16 ticks, 80 bars of body and a 4-bar intro, 1344 ticks (134.4 s).
THE MOTIF is the scythe's ARC: a cell of four notes (root, the fifth above it, the third, the second: G D Bb A in G minor) with the long-short rhythm 6-2-4-4, repeated a diatonic STEP HIGHER each
bar over a falling bass (Gm F Eb), then the phrase leaves its last note HANGING UP on the leading tone of D7 (F#5). The second phrase starts a fifth lower and climbs the same way.
Keys: G minor (intro, A, B) -> the relative major B flat (CHORUS: Bb F Gm Eb, the tune in long notes with a third above) -> C major (FINALE, a whole step up). B (the bass release) runs the cell as arpeggios over a
rising bass (Gm Bb Cm Dm), 5-3-5-3. Phase 2 turns the intro and A into G MAJOR (G Am C D7: the old reversal idea) and drops back to the minor for B; the machine gun comes earlier.
Time table (bars from the song start; 1 bar = 1.6 s): intro 0-7 (a bare refrain, no drums; from the loop point the snare gets denser) | A 8-27 (full drums, root nailed; bars 20-23 pseudo half time) | B 28-43
(bass released, flute an octave up; bars 34-41 pseudo double time) | pre-break and stop-time 44-46, BREAK 47 (one bell) | CHORUS 48-67 (relative major, third above, 16th machine gun, long notes) | FINALE 68-83
(a whole step up: the cell in C major, then the chorus tune lifted; the last bar is D7 into the loop).
Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403
from style1 import build

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Reaper's Arc (Boss004)", "Reaper's Arc, Sundered (Boss004)")
FILES = ("necromancer_phase1.nbs", "necromancer_phase2.nbs")
DESCRIPTIONS = ("Snow mountain reaper, phase 1 (sorrow and fated battle): 150 BPM, a rising scythe-arc cell in G minor, a bass release, a bell break, the relative major with a third above, a whole-step finale.",
                "Snow mountain reaper, phase 2 (limit break and reversal): the same events one rung higher - G major intro and A, the minor B, the bell break, a blazing Bb major chorus, a finale in C.")
MINOR = [0, 2, 3, 5, 7, 8, 10]
MAJOR = [0, 2, 4, 5, 7, 9, 11]


def pitch(deg, scale, root=55):
    octave, d = divmod(deg, 7)
    return root + 12 * octave + scale[d]


def text(midis, rhythm):
    return " ".join(f"{note_name(m)}:{r}" for m, r in zip(midis, rhythm))


def shifted(txt, n):
    out = []
    for token in txt.split():
        name, length = token.split(":")
        out.append(f"{note_name(midi(name) + n)}:{length}")
    return " ".join(out)


def arc_bar(first_deg, scale, rhythm=(6, 2, 4, 4)):
    """the cell [0, 4, 2, 1] from the degree ``first_deg``"""
    return text([pitch(first_deg + d, scale) for d in (0, 4, 2, 1)], rhythm)


def tune(scale, minor):
    """8 bars: phrase 1 (the cell a step higher each bar, then a hang on the leading tone), phrase 2 (a fifth lower, the same climb, a hang)."""
    p1 = [arc_bar(7 + s, scale) for s in range(3)] + [text([69, 72, 78], (4, 4, 8))]
    p2 = [text([pitch(3 + s + d, scale, 55) + 0 for d in (0, 4, 2, 1)], (6, 2, 4, 4)) for s in range(3)] + [text([72, 75 if minor else 76, 78], (4, 4, 8))]
    return p1, p2


CH_MINOR_KEY = ["Gm", "F", "Eb", "D7", "Cm", "Eb", "F", "D7"]
# phase 1: G minor; phase 2: G major
P1_P1, P1_P2 = tune(MINOR, True)
P2_P1, P2_P2 = tune(MAJOR, False)
CH1_A = (["Gm", "F", "Eb", "D7"], ["Cm", "Eb", "F", "D7"])
CH2_A = (["G", "Am", "C", "D7"], ["C", "D", "Em", "D7"])

# --- B: arpeggio cells over a rising bass (minor in both phases) -----------------------------------------------------------------------------------------------
def b_bar(root, third, rhythm):
    return text([root, root + 7, root + third, root + 2], rhythm)


B_ROOTS = [(67, 3, "Gm"), (70, 4, "Bb"), (72, 3, "Cm"), (74, 3, "Dm"), (70, 4, "Bb"), (72, 3, "Cm"), (74, 3, "Dm")]
B1 = [(b_bar(r, th, (5, 3, 5, 3)), c) for r, th, c in B_ROOTS] + [(text([72, 75, 78], (4, 4, 8)), "D7")]
B2 = [(b_bar(r, th, (4, 4, 4, 4)), c) for r, th, c in [(67, 3, "Gm"), (70, 4, "Bb"), (72, 3, "Cm"), (74, 3, "Dm"), (75, 4, "Eb"), (72, 3, "Cm"), (74, 3, "Dm")]]
B2[4] = (B2[4][0], "Eb")
B2 = [(a, c) for a, c in B2] + [(text([72, 75, 78], (4, 4, 8)), "D7")]
B = B1 + B2

# --- CHORUS (Bb major, long notes) and its lift to C ---------------------------------------------------------------------------------------------------------------
CH_CHORDS = ["Bb", "F", "Gm", "Eb", "Bb", "F", "Eb", "F"]
CH1 = ["F4:4 Bb4:12", "A4:4 C5:12", "Bb4:8 D5:8", "G4:4 Bb4:4 Eb5:8", "D5:4 F5:12", "C5:4 F5:12", "Bb4:8 Eb5:8", "A4:4 C5:4 F5:8"]
CH2 = ["Bb4:4 D5:12", "C5:4 F5:12", "Bb4:4 D5:12", "G4:4 Bb4:4 Eb5:8", "F5:16", "A4:4 F5:12", "G4:4 Bb4:4 Eb5:8", "A4:4 C5:4 F5:8"]
CHTAG = ["G4:8 Bb4:8", "A4:8 C5:8", "Bb4:8 D5:8", "F5:16"]
CHTAG_CH = ["Eb", "F", "Gm", "F"]
CH = [(t, c) for t, c in zip(CH1, CH_CHORDS)] + [(t, c) for t, c in zip(CH2, CH_CHORDS)] + [(t, c) for t, c in zip(CHTAG, CHTAG_CH)]

# --- FINALE (C major): the cell in C major, then the chorus tune a whole step up -------------------------------------------------------------------------------
C_MAJOR_CELL = [text([pitch(7 + s + d, MAJOR, 48) for d in (0, 4, 2, 1)], (6, 2, 4, 4)) for s in range(3)] + [text([71, 74, 77], (4, 4, 8))]
C_PH2 = [text([pitch(10 + s + d, MAJOR, 48) for d in (0, 4, 2, 1)], (6, 2, 4, 4)) for s in range(3)] + [text([74, 77, 83], (4, 4, 8))]
FIN1 = list(zip(C_MAJOR_CELL, ["C", "Dm", "Em", "G7"])) + list(zip(C_PH2, ["F", "G", "Am", "G7"]))
FIN2 = [(shifted(t, 2), shift_chord(c, 2)) for t, c in zip(CH2, CH_CHORDS)][:7] + [("A4:4 C5:4 F#5:8", "D7")]
FIN = FIN1 + FIN2


# --- patterns of a bar of 16 ticks ------------------------------------------------------------------------------------------------------------------------------------
HAT16 = [(t, 46 if t % 4 == 0 else 34) for t in range(0, 16, 2)]
DRUMS = {
    "rock": {"kick": [(0, 100), (5, 64), (8, 92), (13, 64)], "snare": [(4, 92), (12, 100)], "hat": [(t, 44 if t % 4 == 0 else 30) for t in range(0, 16, 2)]},
    "drive": {"kick": [(0, 100), (3, 62), (6, 76), (8, 98), (11, 62), (14, 76)], "snare": [(4, 96), (12, 100), (15, 46)], "hat": [(t, 46 if t % 4 == 0 else 32) for t in range(0, 16, 2)]},
    "drive16": {"kick": [(0, 100), (3, 62), (6, 76), (8, 98), (11, 62), (14, 76)], "snare": [(4, 96), (12, 100), (15, 46)], "hat": HAT16},
    "half": {"kick": [(0, 96), (10, 58)], "snare": [(8, 92)], "hat": [(t, 30 if t % 4 == 0 else 22) for t in range(0, 16, 2)]},
    "dbl": {"kick": [(2, 86), (6, 80), (10, 86), (14, 80)], "snare": [(0, 90), (4, 96), (8, 92), (12, 98)], "hat": HAT16},
    "peak": {"kick": [(0, 100), (5, 72), (8, 100), (10, 60), (13, 72)], "snare": [(4, 100), (12, 100)],
             "hat": [(t, 50 if t % 4 == 0 else 36) for t in (0, 4, 8, 12, 6, 14)], "tom": [(11, 52, 54)]},
    "stop": {"kick": [(0, 100), (8, 90)], "snare": [(0, 90), (8, 90)]},
}
BASS_PAT = {
    "Q": [(0, "r", 70), (4, "r", 40), (8, "r", 55), (12, "r", 40)],
    "E_soft": [(t, "r", 56 if t == 0 else 40) for t in range(0, 16, 2)],
    "E": [(0, "r", 100), (2, "r", 56), (4, "r", 70), (6, "o", 60), (8, "r", 86), (10, "r", 56), (12, "r", 70), (14, "o", 60)],
    "E_mv": [(0, "r", 100), (2, "r", 56), (4, "r", 70), (6, "f", 60), (8, "r", 86), (10, "f", 56), (12, "t", 70), (14, "f", 60)],
    "halfb": [(0, "r", 100), (6, "r", 56), (8, "r", 86), (14, "o", 56)],
    "ED_a": [(0, "r", 100), (2, "r", 54), (3, "r", 44), (4, "r", 70), (6, "r", 54), (7, "r", 44), (8, "r", 86), (10, "r", 54), (11, "r", 44), (12, "r", 70), (14, "r", 56), (15, "o", 46)],
    "ED_b": [(0, "r", 100), (2, "r", 54), (4, "r", 70), (5, "r", 44), (6, "t", 58), (8, "f", 86), (10, "f", 54), (12, "t", 70), (13, "t", 44), (14, "r", 58)],
    "TRI": [(0, "r", 100), (3, "r", 64), (6, "f", 74), (8, "r", 88), (11, "t", 64), (14, "o", 70)],
    "WALK": [(0, "r", 100), (2, "r", 54), (4, "t", 70), (6, "t", 54), (8, "f", 86), (10, "f", 54), (12, "o", 72), (13, "f", 44), (14, "t", 58), (15, "r", 44)],
    "BURST": [(0, "r", 100), (2, "r", 54), (4, "r", 70), (6, "r", 54)] + [(t, "o" if t in (11, 15) else "r", 86 if t == 8 else 58 if t % 2 == 0 else 46) for t in range(8, 16)],
    "S16": [(t, "o" if t % 4 == 3 else "r", 100 if t == 0 else 86 if t == 8 else 68 if t % 4 == 0 else 52 if t % 2 == 0 else 44) for t in range(16)],
    "S16b": [(t, "o" if t % 4 == 3 else ("f" if t in (5, 13) else "r"), 100 if t == 0 else 86 if t == 8 else 68 if t % 4 == 0 else 52 if t % 2 == 0 else 44) for t in range(16)],
    "STOP": [(0, "r", 100), (8, "r", 92)],
    "END": [(0, "r", 98), (4, "r", 78), (8, "r", 62), (10, "r", 42)],
}
GUITAR_PAT = {"arp": [(t, i, v) for t, i, v in zip(range(0, 16, 4), (0, 2, 3, 2), (56, 42, 48, 42))],
              "chug": [(t, 0, 70 if t % 8 == 0 else 50) for t in range(0, 16, 4)]}
HARP_ARP = [(t, i, 28) for t, i in zip(range(2, 16, 4), (2, 3, 4, 3))]
HARP_Q = [(t, i, 30) for t, i in zip(range(2, 16, 4), (1, 2, 3, 2))]
DRONE = [(0, "r", 54), (4, "r", 34), (8, "r", 50), (12, "r", 34)]

# --- the plans: bass pattern and drum pattern of every bar (first bar, name), [phase 1, phase 2] ---------------------------------------------------------------------------
D = dict(
    tps=10, bar=16, intro_abs=4, body=80, lead=PLING, bass=BASS_PAT, drums=DRUMS, drone=DRONE, pah=(), pah_guitar=False, guitar=GUITAR_PAT, harp_arp=HARP_ARP, harp_q=HARP_Q,
    intro=[list(zip(P1_P2, CH1_A[1])), list(zip(P2_P2, CH2_A[1]))],
    rest=[list(zip(P1_P1, CH1_A[0])), list(zip(P2_P1, CH2_A[0]))],
    ramp=[[(12, 44)], [(4, 46), (12, 56)], [(4, 50), (8, 54), (12, 60), (14, 64)], [(t, 50 + 3 * t // 2) for t in range(0, 12, 2)]],
    A=[list(zip(P1_P1 + P1_P2 + P1_P1 + P1_P2 + P1_P1, CH1_A[0] + CH1_A[1] + CH1_A[0] + CH1_A[1] + CH1_A[0]))[:20],
       list(zip(P2_P1 + P2_P2 + P2_P1 + P2_P2 + P2_P1, CH2_A[0] + CH2_A[1] + CH2_A[0] + CH2_A[1] + CH2_A[0]))[:20]],
    A_bass=[[(0, "E"), (8, "E_mv"), (10, "ED_a"), (12, "halfb"), (16, "ED_a"), (18, "ED_b")], [(0, "E"), (4, "ED_a"), (8, "E_mv"), (12, "halfb"), (16, "ED_a"), (18, "ED_b")]],
    A_drum=[[(0, "rock"), (10, "drive"), (12, "half"), (16, "drive")], [(0, "drive"), (12, "half"), (16, "drive16")]],
    A_half=(12, 16),
    B=[B, B], B_dbl=(6, 14),
    B_bass=[[(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "ED_b"), (4, "ED_a"), (5, "TRI"), (6, "BURST"), (7, "ED_b"), (8, "WALK"), (9, "TRI"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "ED_b"), (14, "BURST"), (15, "S16")],
            [(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "BURST"), (4, "ED_b"), (5, "TRI"), (6, "BURST"), (7, "ED_b"), (8, "BURST"), (9, "ED_b"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "S16"), (14, "BURST"), (15, "S16b")]],
    B_drum=[[(0, "drive16"), (6, "dbl"), (14, "dbl")], [(0, "peak"), (6, "dbl"), (14, "peak")]],
    pre=[("A4:4 C5:4 F#5:8", "D7"), ("D5:16", "D7")], stop=("F#5:16", "D7"), stop_at=(0, 8), bell=93,
    CH=[CH, CH], CH_bass=[[(0, "S16"), (7, "S16b"), (8, "S16"), (15, "S16b"), (16, "S16")], [(0, "S16"), (1, "S16b"), (8, "S16b"), (9, "S16"), (16, "S16b")]],
    scale_ch=tuple(p % 12 for p in (10, 0, 2, 3, 5, 7, 9)),
    FIN=[FIN, FIN], fin_split=8, scale_fin=tuple(p % 12 for p in (0, 2, 4, 5, 7, 9, 11)),
    FIN_bass=[[(0, "S16"), (4, "S16b"), (8, "S16"), (12, "S16b")], [(0, "S16b"), (4, "S16"), (8, "S16b"), (12, "S16")]],
)
assert len(D["A"][0]) == 20 and len(B) == 16 and len(CH) == 20 and len(FIN) == 16, (len(D["A"][0]), len(B), len(CH), len(FIN))

SECTIONS = [("intro", 0, 4), ("A", 4, 20), ("B", 24, 16), ("pre+stop", 40, 3), ("break", 43, 1), ("chorus", 44, 20), ("finale", 64, 16)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in (0, 1):
        score = build(phase, D)
        finish(score, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], SECTIONS)


if __name__ == "__main__":
    main()
