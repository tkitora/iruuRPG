#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "Waltz at the Edge of Dark, Out of Step (Boss008)", a tragic dance with death (rebuilt from zero, t281).

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

Class 2 "sorrow and fated battle" in STYLE 1 adapted to 3/4: 150 BPM, a song tick is a sixteenth (10 ticks a second), a beat is 4 ticks, a bar is 12 ticks (1.2 s; eighth 2, sixteenth 1). A 12-tick bar meets the
engine's 16-tick line every 4 bars, so every section is a multiple of 4 bars. Intro: 4 silent ticks + 5 bars (the loop starts at tick 64); body 108 bars; 1360 ticks (136 s). The lead is the piano (harp), the
violin (pling) and the flute joining; a guitar plays the pah-pah.
THE MOTIF is the LILT: a rising arpeggio of three notes in the rhythm dotted-quarter, eighth, quarter (6-2-4) on the first beat's weight (A4 D5 F5 over D minor), bar after bar, every bar ending UP (F5, E5, D5, F5 ...)
and the 8th bar hanging on E5 over A7. Keys: D minor (intro, A, B) -> the RELATIVE MAJOR F (CHORUS: F C Dm Bb, a third above, the tune in long notes: half and dotted-half) -> G major (FINALE, a whole step up).
B (the bass release) runs the lilt as arpeggios of four notes (4-2-2-4) over a RISING bass (Gm Bb C Dm). Phase 2 "Out of Step" has the same events with the footing lost: hemiola (two bars of 3/4 felt as three of
3/2: accents at 0, 8, 16 of 24 ticks) in the bass and drums at the ends of statements, the tune a beat late and cut off to catch up, a half-step sting in the bass; the machine gun comes earlier.
Time table (bars from the song start; 1 bar = 1.2 s): intro 0-12 (a bare refrain, no drums; from the loop point the snare gets denser) | A 13-36 (full drums, root nailed; bars 25-28 pseudo half time) |
B 37-60 (bass released, flute an octave up; bars 47-56 pseudo double time) | pre-break and stop-time 61-63, BREAK 64 (one bell) | CHORUS 65-88 (relative major, third above, 16th machine gun, long notes) |
FINALE 89-112 (a whole step up; the last bar is A7 back into the loop).
Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403
from style1 import build

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Waltz at the Edge of Dark (Boss008)", "Waltz at the Edge of Dark, Out of Step (Boss008)")
FILES = ("waltz_phase1.nbs", "waltz_phase2.nbs")
DESCRIPTIONS = ("Waltz boss, phase 1 (sorrow and fated battle): a tragic 3/4 waltz at 150 BPM in D minor, a rising lilt, a bass release, a bell break, the relative major with a third above, and a whole-step finale.",
                "Waltz boss, phase 2: the same dance out of step - hemiola in the bass and drums, the tune a beat late, a half-step sting in the bass, an earlier machine gun.")


def text(midis, rhythm):
    return " ".join(f"{note_name(m)}:{r}" for m, r in zip(midis, rhythm))


def shifted(txt, n):
    out = []
    for token in txt.split():
        name, length = token.split(":")
        out.append(f"{note_name(midi(name) + n)}:{length}")
    return " ".join(out)


# --- the lilt (D minor) ---------------------------------------------------------------------------------------------------------------------------------
W1 = ["A4:6 D5:2 F5:4", "A4:6 C#5:2 E5:4", "F4:6 A4:2 D5:4", "Bb4:6 D5:2 F5:4", "F4:6 Bb4:2 D5:4", "G4:6 A4:2 C#5:4", "G4:6 Bb4:2 D5:4", "A4:4 C#5:4 E5:4"]
W1_CH = ["Dm", "A7", "Dm", "Gm", "Bb", "A7", "Gm", "A7"]


def lilt(chord, k):
    tones = tones_in(chord, 60, 79)
    return text(tones[k:k + 3], (6, 2, 4))


# --- B: four-note arpeggios (4-2-2-4) over a rising bass; minor in both phases --------------------------------------------------------------------------
def b_bar(root, third, rhythm=(4, 2, 2, 4)):
    return text([root, root + 7, root + third, root + 2], rhythm)


B_A = [(b_bar(r, th), c) for r, th, c in [(67, 3, "Gm"), (70, 4, "Bb"), (72, 4, "C"), (74, 3, "Dm"), (70, 4, "Bb"), (72, 4, "C"), (74, 3, "Dm")]] + [("C#5:4 E5:4 G5:4", "A7")]
B_B = [(b_bar(r, th), c) for r, th, c in [(67, 3, "Gm"), (70, 4, "Bb"), (72, 3, "Cm"), (74, 3, "Dm"), (75, 4, "Eb"), (72, 3, "Cm"), (74, 3, "Dm")]] + [("C#5:4 E5:4 G5:4", "A7")]
B = B_A + B_B + B_A

# --- CHORUS (F major, long notes) and the finale (G major, a whole step up) -----------------------------------------------------------------------------
CH_CHORDS = ["F", "C", "Dm", "Bb", "F", "C", "Bb", "C"]
CH1 = ["F4:4 A4:8", "G4:4 C5:8", "A4:4 D5:8", "Bb4:4 D5:8", "A4:4 C5:8", "G4:4 C5:8", "Bb4:4 D5:8", "C5:4 E5:8"]
CH2 = ["A4:4 C5:8", "C5:4 E5:8", "D5:4 F5:8", "D5:4 F5:8", "C5:4 F5:8", "C5:4 E5:8", "D5:4 F5:8", "C5:4 E5:4 F5:4"]
CH3 = ["C5:4 F5:8", "C5:4 E5:8", "D5:4 F5:8", "Bb4:4 F5:8", "C5:4 F5:8", "E5:4 F5:8", "D5:4 F5:8", "C5:4 E5:8"]
CH = [(t, c) for tune in (CH1, CH2, CH3) for t, c in zip(tune, CH_CHORDS)]
FIN_CHORDS1 = [("G", 1), ("D", 1), ("G", 2), ("Am", 1), ("C", 2), ("D", 2), ("Am", 1)]
FIN1 = [(lilt(c, k), c) for c, k in FIN_CHORDS1] + [("A4:4 C5:4 F#5:4", "D7")]
FIN = FIN1 + [(shifted(t, 2), shift_chord(c, 2)) for t, c in zip(CH1, CH_CHORDS)] + [(shifted(t, 2), shift_chord(c, 2)) for t, c in zip(CH2, CH_CHORDS)]
FIN[-1] = ("A4:4 C#5:4 E5:4", "A7")

# --- patterns of a bar of 12 ticks --------------------------------------------------------------------------------------------------------------------------
HAT = [(t, 46 if t % 4 == 0 else 34) for t in range(0, 12, 2)]
DRUMS = {
    "waltz": {"kick": [(0, 100), (6, 52)], "snare": [(4, 82), (8, 82)], "hat": [(t, 40 if t % 4 == 0 else 28) for t in range(0, 12, 2)]},
    "drive16": {"kick": [(0, 100), (6, 62), (10, 52)], "snare": [(4, 94), (8, 98), (11, 46)], "hat": HAT + [(7, 24), (11, 24)]},
    "half": {"kick": [(0, 96)], "snare": [(8, 90)], "hat": [(t, 28 if t % 4 == 0 else 20) for t in range(0, 12, 2)]},
    "dbl": {"kick": [(2, 86), (6, 80), (10, 86)], "snare": [(0, 90), (4, 96), (8, 96)], "hat": HAT + [(7, 24), (11, 24)]},
    "peak": {"kick": [(0, 100), (2, 52), (6, 72), (10, 62)], "snare": [(4, 100), (8, 100), (11, 56)], "hat": HAT, "tom": [(3, 50, 57), (7, 50, 54)]},
    "stop": {"kick": [(0, 100), (8, 90)], "snare": [(0, 90), (8, 90)]},
    "hemiA": {"kick": [(0, 100), (8, 96)], "snare": [(4, 84), (11, 44)], "hat": [(t, 44 if t % 4 == 0 else 26) for t in range(0, 12, 2)]},
    "hemiB": {"kick": [(4, 96), (11, 44)], "snare": [(0, 84), (8, 84)], "hat": [(t, 44 if t % 4 == 0 else 26) for t in range(0, 12, 2)], "tom": [(6, 46, 54)]},
    "hemiPA": {"kick": [(0, 100), (2, 56), (8, 96), (10, 56)], "snare": [(4, 90), (6, 60), (11, 50)], "hat": HAT},
    "hemiPB": {"kick": [(4, 98), (6, 56), (11, 50)], "snare": [(0, 90), (2, 56), (8, 90), (10, 56)], "hat": HAT, "tom": [(7, 50, 54)]},
}


def sixteenths(accents, octave=(3, 7, 11), fifth=()):
    return [(t, "o" if t in octave else ("f" if t in fifth else "r"), accents.get(t, 52 if t % 2 == 0 else 44)) for t in range(12)]


BASS_PAT = {
    "Q": [(0, "r", 70), (4, "r", 40), (8, "r", 40)],
    "E_soft": [(t, "r", 56 if t == 0 else 40) for t in range(0, 12, 2)],
    # boom-pah-pah with the octave on the weak beats
    "E": [(0, "r", 100), (2, "r", 56), (4, "o", 72), (6, "r", 56), (8, "o", 72), (10, "r", 56)],
    "E_mv": [(0, "r", 100), (2, "r", 56), (4, "f", 72), (6, "r", 56), (8, "o", 72), (10, "f", 56)],
    "halfb": [(0, "r", 100), (6, "r", 56), (8, "r", 76)],
    "ED_a": [(0, "r", 100), (2, "r", 54), (4, "r", 70), (5, "r", 44), (6, "r", 54), (8, "r", 70), (10, "r", 54), (11, "o", 46)],
    "ED_b": [(0, "r", 100), (2, "r", 54), (4, "f", 70), (5, "f", 44), (6, "t", 56), (8, "f", 70), (10, "t", 54), (11, "r", 46)],
    "TRI": [(0, "r", 100), (3, "r", 64), (6, "f", 76), (9, "t", 66)],
    "WALK": [(0, "r", 100), (2, "r", 54), (4, "t", 70), (6, "f", 56), (8, "o", 70), (10, "f", 54), (11, "t", 46)],
    "BURST": [(0, "r", 100), (2, "r", 54)] + [(t, "o" if t in (7, 11) else "r", 74 if t == 4 else 58 if t % 2 == 0 else 46) for t in range(4, 12)],
    "S16": sixteenths({0: 100, 4: 74, 8: 74}),
    "S16b": sixteenths({0: 100, 4: 74, 8: 74}, fifth=(5, 9)),
    "S16sting": [(0, "r", 100), (1, "r", 44), (2, "r", 52), (3, "o", 44), (4, "r", 74), (5, "r", 44), (6, "r", 52), (7, "o", 44)] + [(t, 41, 98 if t == 8 else 62 if t % 2 == 0 else 48) for t in range(8, 12)],
    "hemiA": [(t, "r", 100 if t == 0 else 96 if t == 8 else 50) for t in range(0, 12, 2)],
    "hemiB": [(t, "o" if t == 10 else "r", 98 if t == 4 else 50) for t in range(0, 12, 2)],
    "hemiPA": sixteenths({0: 100, 8: 98}),
    "hemiPB": sixteenths({4: 100, 0: 52}),
    "STOP": [(0, "r", 100), (8, "r", 92)],
    "END": [(0, "r", 98), (4, "r", 72), (8, "r", 54)],
}
GUITAR_PAT = {"arp": [(t, i, v) for t, i, v in zip(range(0, 12, 2), (0, 2, 3, 2, 3, 2), (52, 36, 40, 36, 40, 36))],
              "chug": [(t, 0, 70 if t == 0 else 50) for t in range(0, 12, 4)]}
HARP_ARP = [(t, i, 30) for t, i in zip(range(0, 12, 2), (0, 1, 2, 3, 2, 1))]
HARP_Q = [(t, i, 30) for t, i in zip((2, 6, 10), (1, 2, 3))]
DRONE = [(0, "r", 54), (6, "r", 34)]


def late(P, sec, i):
    """phase 2: the tune a beat late (and cut off to catch up) near the ends of A and of the chorus."""
    if P == 1 and ((sec == "A" and 20 <= i < 24) or (sec == "CH" and 16 <= i < 24)):
        return 4, i in (23,)
    return 0, False


def hemi(P, sec, i, bn, dn):
    if P != 1:
        return bn, dn
    if sec == "A" and i in (6, 7, 22, 23) or sec == "B" and i in (4, 5, 12, 13, 20, 21):
        name = "hemiA" if i % 2 == 0 else "hemiB"
        return name, name
    if sec == "CH":
        if 8 <= i < 16:
            return ("hemiPA" if i % 2 == 0 else "hemiPB"), ("hemiPA" if i % 2 == 0 else "hemiPB")
        if i % 8 == 7:
            return "S16sting", dn
    if sec == "FIN" and i % 8 == 7 and i < 23:
        return "S16sting", dn
    return bn, dn


D = dict(
    tps=10, bar=12, intro_abs=5, body=108, lead=HARP, bass=BASS_PAT, drums=DRUMS, drone=DRONE, pah=(4, 8), pah_guitar=True, guitar=GUITAR_PAT, harp_arp=HARP_ARP, harp_q=HARP_Q,
    intro=[list(zip(W1[3:], W1_CH[3:]))] * 2,
    rest=[list(zip(W1, W1_CH))] * 2,
    ramp=[[], [], [], [(8, 40)], [(8, 46)], [(4, 50), (8, 56)], [(4, 56), (6, 60), (8, 64), (10, 66)], [(t, 52 + 4 * t // 2) for t in range(0, 8, 2)]],
    A=[list(zip(W1 * 3, W1_CH * 3))] * 2,
    A_bass=[[(0, "E"), (10, "E_mv"), (12, "halfb"), (16, "ED_a"), (20, "ED_b")], [(0, "E"), (6, "ED_a"), (10, "E_mv"), (12, "halfb"), (16, "BURST"), (20, "ED_b")]],
    A_drum=[[(0, "waltz"), (4, "drive16"), (12, "half"), (16, "drive16")], [(0, "waltz"), (4, "drive16"), (12, "half"), (16, "peak")]],
    A_half=(12, 16),
    B=[B, B], B_dbl=(10, 20),
    B_bass=[[(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "ED_b"), (4, "ED_a"), (5, "TRI"), (6, "WALK"), (7, "BURST"), (8, "ED_b"), (9, "TRI"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "ED_b"),
             (14, "BURST"), (15, "ED_b"), (16, "WALK"), (17, "TRI"), (18, "BURST"), (19, "ED_b"), (20, "BURST"), (22, "S16")],
            [(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "BURST"), (4, "ED_b"), (5, "TRI"), (6, "BURST"), (7, "ED_b"), (8, "BURST"), (9, "ED_b"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "ED_b"),
             (14, "BURST"), (15, "ED_b"), (16, "BURST"), (17, "S16"), (18, "BURST"), (19, "S16"), (20, "BURST"), (22, "S16b")]],
    B_drum=[[(0, "drive16"), (10, "dbl"), (20, "dbl")], [(0, "drive16"), (8, "peak"), (10, "dbl"), (20, "dbl")]],
    pre=[("C#5:4 E5:4 A5:4", "A7"), ("E5:12", "A7")], stop=("E5:12", "A7"), stop_at=(0, 8), bell=93,
    CH=[CH, CH], CH_bass=[[(0, "S16"), (7, "S16b"), (8, "S16"), (15, "S16b"), (16, "S16b")], [(0, "S16"), (1, "S16b"), (8, "S16b"), (16, "S16")]],
    scale_ch=tuple(p % 12 for p in (5, 7, 9, 10, 0, 2, 4)),
    FIN=[FIN, FIN], fin_split=8, scale_fin=tuple(p % 12 for p in (7, 9, 11, 0, 2, 4, 6)),
    FIN_bass=[[(0, "S16"), (4, "S16b"), (8, "S16"), (12, "S16b"), (16, "S16b")], [(0, "S16b"), (4, "S16"), (8, "S16b"), (12, "S16"), (16, "S16b")]],
    late=late, hemi=hemi,
)
assert len(D["A"][0]) == 24 and len(B) == 24 and len(CH) == 24 and len(FIN) == 24

SECTIONS = [("intro", 0, 8), ("A", 8, 24), ("B", 32, 24), ("pre+stop", 56, 3), ("break", 59, 1), ("chorus", 60, 24), ("finale", 84, 24)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in (0, 1):
        score = build(phase, D)
        finish(score, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], SECTIONS)


if __name__ == "__main__":
    main()
