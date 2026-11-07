#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the faceless scythe reaper of the snow mountain (rebuilt from zero, t281).

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

Class 2 "sorrow and fated battle" in STYLE 1 ("royal high-speed battle"), 150 BPM, a song tick is a sixteenth (10 ticks a second), a 4/4 bar is 16 ticks, 80 bars of body and a 4-bar intro, 1344 ticks (134.4 s).
THE MOTIF (t290, new) is the CRESCENT, the blade of the scythe seen from the side: from a high note down in two steps and up again past where it began (B4 G4 E4 A4 ... degrees 4, 2, 0, 3), long - short - short - long
(6-2-2-6), the last note always rising; phrase 1 plays it a scale step higher each bar over an E pedal (the bass stays on E), phrase 2 brings it down the steps over Am C D and hangs on F#5 over B7.
Keys: E minor (intro, A, B) -> the relative major G (CHORUS: G D Em C, the tune in long notes with a third above) -> A major (FINALE, a whole step up from G). B (the bass release) turns the crescent into
arpeggios (fifth, third, root, fifth) over a rising bass (Em G A B). Phase 2 turns the intro and A into E MAJOR (A B C#m B7) and drops back to the minor for B; the machine gun comes earlier.
Time table (bars from the song start; 1 bar = 1.6 s): intro 0-7 (a bare refrain, no drums; from the loop point the snare gets denser) | A 8-27 (full drums, root nailed; bars 22-27 (30-40 s) pseudo half time) | B 28-43
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
DESCRIPTIONS = ("Snow mountain reaper, phase 1 (sorrow and fated battle): 150 BPM, a crescent motif in E minor, a bass release, a bell break, the relative major with a third above, a whole-step finale.",
                "Snow mountain reaper, phase 2 (limit break and reversal): the same events one rung higher - E major intro and A, the minor B, the bell break, a blazing G major chorus, a finale in A.")
MINOR = [0, 2, 3, 5, 7, 8, 10]
MAJOR = [0, 2, 4, 5, 7, 9, 11]


def pitch(deg, scale, root=52):
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


def crescent(d, scale, root=52, rhythm=(6, 2, 2, 6)):
    """THE MOTIF: the blade of the scythe seen from the side, a crescent: from a high note down in two steps and up again past where it began (degrees d+4, d+2, d, d+3),
    long - short - short - long, the last note rising. Played again a step higher each bar."""
    return text([pitch(d + k, scale, root) for k in (4, 2, 0, 3)], rhythm)


def tune(scale):
    """8 bars in E (E4 = 64): phrase 1 = the crescent four steps higher, one per bar, over an E pedal; phrase 2 = the crescent coming down the steps over Am C D, then B7 with the last note hanging on F#5."""
    p1 = [crescent(d, scale, 57) for d in (1, 2, 3, 4)]
    p2 = [crescent(3, scale, 57), crescent(2, scale, 57), text([74, 69, 66, 69], (6, 2, 2, 6)), text([71, 75, 78], (4, 4, 8))]
    return p1, p2


P1_P1, P1_P2 = tune(MINOR)
P2_P1, P2_P2 = tune(MAJOR)
CH1_A = (["Em", "Em", "Em", "Em"], ["Am", "C", "D", "B7"])
CH2_A = (["E", "E", "E", "E"], ["A", "B", "C#m", "B7"])

# --- B: the crescent turned into arpeggios (fifth, third, root, fifth) over a rising bass; minor in both phases --------------------------------------------------------
def b_bar(root, third, rhythm=(5, 3, 5, 3)):
    return text([root + 7, root + third, root, root + 7], rhythm)


B_ROOTS = [(64, 3, "Em"), (67, 4, "G"), (69, 4, "A"), (71, 4, "B"), (67, 4, "G"), (69, 4, "A"), (71, 4, "B")]
B1 = [(b_bar(r, th), c) for r, th, c in B_ROOTS] + [(text([71, 75, 78], (4, 4, 8)), "B7")]
B2 = [(b_bar(r, th, (4, 4, 4, 4)), c) for r, th, c in [(64, 3, "Em"), (67, 4, "G"), (69, 3, "Am"), (71, 4, "B"), (72, 4, "C"), (69, 3, "Am"), (71, 4, "B")]] + [(text([71, 75, 78], (4, 4, 8)), "B7")]
B = B1 + B2

# --- CHORUS (G major, the relative major, long notes) and its lift to A -------------------------------------------------------------------------------------------------
CH_CHORDS = ["G", "D", "Em", "C", "G", "D", "C", "D"]
CH1 = ["D4:4 G4:12", "F#4:4 A4:12", "G4:8 B4:8", "E4:4 G4:4 C5:8", "B4:4 D5:12", "A4:4 D5:12", "G4:8 C5:8", "F#4:4 A4:4 D5:8"]
CH2 = ["G4:4 B4:12", "A4:4 D5:12", "B4:8 E5:8", "C5:8 E5:8", "B4:4 D5:12", "D5:4 F#5:12", "C5:4 E5:12", "A4:4 D5:4 F#5:8"]
CHTAG = ["E4:8 G4:8", "F#4:8 A4:8", "G4:8 B4:8", "D5:16"]
CHTAG_CH = ["C", "D", "Em", "D"]
CH = [(t, c) for t, c in zip(CH1, CH_CHORDS)] + [(t, c) for t, c in zip(CH2, CH_CHORDS)] + [(t, c) for t, c in zip(CHTAG, CHTAG_CH)]

# --- FINALE (A major): the crescent in the major, then the chorus tune a whole step up ---------------------------------------------------------------------------
FIN_P1 = [crescent(d, MAJOR, 57 + 2) for d in (1, 2, 3, 4)]
FIN_P2 = [crescent(3, MAJOR, 57 + 2), crescent(2, MAJOR, 57 + 2), text([76, 71, 68, 71], (6, 2, 2, 6)), text([73, 77, 80], (4, 4, 8))]
FIN1 = list(zip(FIN_P1, ["A", "A", "A", "A"])) + list(zip(FIN_P2, ["D", "E", "F#m", "E7"]))
FIN2 = [(shifted(t, 2), shift_chord(c, 2)) for t, c in zip(CH2, CH_CHORDS)][:7] + [("B4:4 D#5:4 F#5:8", "B7")]
FIN = FIN1 + FIN2


# --- patterns of a bar of 16 ticks ------------------------------------------------------------------------------------------------------------------------------------
HAT16 = [(t, 46 if t % 4 == 0 else 34) for t in range(0, 16, 2)]
DRUMS = {
    "rock": {"kick": [(0, 100), (3, 60), (8, 92), (11, 60)], "snare": [(4, 92), (12, 100)], "hat": [(t, 44 if t % 4 == 0 else 30) for t in range(0, 16, 2)]},
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
    A_bass=[[(0, "E"), (11, "halfb"), (17, "ED_a")], [(0, "E"), (6, "ED_a"), (11, "halfb"), (17, "ED_a")]],
    A_drum=[[(0, "rock"), (8, "drive"), (11, "half"), (17, "drive")], [(0, "drive"), (11, "half"), (17, "drive16")]],
    A_half=(11, 17),
    B=[B, B], B_dbl=(6, 14),
    B_bass=[[(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "ED_b"), (4, "ED_a"), (5, "TRI"), (6, "BURST"), (7, "ED_b"), (8, "WALK"), (9, "TRI"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "ED_b"), (14, "BURST"), (15, "S16")],
            [(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "BURST"), (4, "ED_b"), (5, "TRI"), (6, "BURST"), (7, "ED_b"), (8, "BURST"), (9, "ED_b"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "S16"), (14, "BURST"), (15, "S16b")]],
    B_drum=[[(0, "drive16"), (6, "dbl"), (14, "dbl")], [(0, "peak"), (6, "dbl"), (14, "peak")]],
    pre=[("B4:4 D#5:4 F#5:8", "B7"), ("F#5:16", "B7")], stop=("F#5:16", "B7"), stop_at=(0, 8), bell=95,
    CH=[CH, CH], CH_bass=[[(0, "S16"), (7, "S16b"), (8, "S16"), (15, "S16b"), (16, "S16")], [(0, "S16"), (1, "S16b"), (8, "S16b"), (9, "S16"), (16, "S16b")]],
    scale_ch=tuple(p % 12 for p in (7, 9, 11, 0, 2, 4, 6)),
    FIN=[FIN, FIN], fin_split=8, scale_fin=tuple(p % 12 for p in (9, 11, 1, 2, 4, 6, 8)),
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
