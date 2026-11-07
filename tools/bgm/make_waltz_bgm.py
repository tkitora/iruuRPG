#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "Waltz at the Edge of Dark, Out of Step (Boss008)", a tragic dance with death (rebuilt from zero, t281).

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

Class 2 "sorrow and fated battle" in STYLE 1 adapted to 3/4: 150 BPM, a song tick is a sixteenth (10 ticks a second), a beat is 4 ticks, a bar is 12 ticks (1.2 s; eighth 2, sixteenth 1). A 12-tick bar meets the
engine's 16-tick line every 4 bars, so every section is a multiple of 4 bars. Intro: 4 silent ticks + 5 bars (the loop starts at tick 64); body 108 bars; 1360 ticks (136 s). The lead is the piano (harp), the
violin (pling) and the flute joining; a guitar plays the pah-pah.
THE MOTIF (t290, new) is the BOW: a quarter, a leap up a sixth to a dotted quarter, a step on (F#4 D5 E5 in B minor, 4-6-2), played a scale step higher each bar over a B pedal, every bar ending UP, the 8th
bar hanging on E5 over F#7. Keys: B minor (intro, A, B) -> the RELATIVE MAJOR D (CHORUS: D A Bm G, a third above, the tune in long notes: half and dotted-half) -> E major (FINALE, a whole step up).
B (the bass release) turns the bow into arpeggios of four notes (fifth, third, root, fifth) over a RISING bass (Bm D Em ...). Phase 2 "Out of Step" has the same events with the footing lost: hemiola (two bars of 3/4 felt as three of
3/2: accents at 0, 8, 16 of 24 ticks) in the bass and drums at the ends of statements, the tune a beat late and cut off to catch up, a half-step sting in the bass; the machine gun comes earlier.
Time table (bars from the song start; 1 bar = 1.2 s): intro 0-12 (a bare refrain, no drums; from the loop point the snare gets denser) | A 13-36 (full drums, root nailed; bars 25-32 (30-40 s) pseudo half time) |
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
DESCRIPTIONS = ("Waltz boss, phase 1 (sorrow and fated battle): a tragic 3/4 waltz at 150 BPM in B minor, a rising bow, a bass release, a bell break, the relative major with a third above, and a whole-step finale.",
                "Waltz boss, phase 2: the same dance out of step - hemiola in the bass and drums, the tune a beat late, a half-step sting in the bass, an earlier machine gun.")


def text(midis, rhythm):
    return " ".join(f"{note_name(m)}:{r}" for m, r in zip(midis, rhythm))


def shifted(txt, n):
    out = []
    for token in txt.split():
        name, length = token.split(":")
        out.append(f"{note_name(midi(name) + n)}:{length}")
    return " ".join(out)


# --- THE BOW (B minor): a quarter, a leap up a sixth to a dotted quarter, a step on: low - high - higher (4-6-2), played a scale step higher each bar over a B pedal --------------------
MINOR = [0, 2, 3, 5, 7, 8, 10]
MAJOR = [0, 2, 4, 5, 7, 9, 11]


def pitch(deg, scale, root):
    octave, d = divmod(deg, 7)
    return root + 12 * octave + scale[d]


def bow(d, scale, root):
    return text([pitch(d + k, scale, root) for k in (0, 5, 6)], (4, 6, 2))


W1 = [bow(d, MINOR, 59) for d in (2, 3, 4, 5)] + [bow(5, MINOR, 59), "D4:4 B4:6 D5:2", "A#4:4 C#5:6 E5:2", "A#4:4 C#5:4 E5:4"]
W1_CH = ["Bm", "Bm", "Bm", "Bm", "Em", "G", "F#7", "F#7"]


# --- B: the bow turned into arpeggios (fifth, third, root, fifth) over a rising bass; minor in both phases --------------------------------------------------------------------------
def b_bar(root, third, rhythm=(4, 2, 2, 4)):
    return text([root + 7, root + third, root, root + 7], rhythm)


B_A = [(b_bar(r, th), c) for r, th, c in [(71, 3, "Bm"), (74, 4, "D"), (76, 3, "Em"), (66, 3, "F#m"), (67, 4, "G"), (69, 4, "A"), (71, 3, "Bm")]] + [("A#4:4 C#5:4 E5:4", "F#7")]
B_B = [(b_bar(r, th), c) for r, th, c in [(71, 3, "Bm"), (74, 4, "D"), (76, 3, "Em"), (66, 3, "F#m"), (67, 4, "G"), (66, 3, "F#m"), (69, 4, "A")]] + [("A#4:4 C#5:4 E5:4", "F#7")]
B = B_A + B_B + B_A

# --- CHORUS (D major, the relative major, long notes) and the finale (E major, a whole step up) ------------------------------------------------------------------------------------
CH_CHORDS = ["D", "A", "Bm", "G", "D", "A", "G", "A"]
CH1 = ["F#4:4 A4:8", "E4:4 A4:8", "D4:4 B4:8", "B4:4 D5:8", "A4:4 F#5:8", "A4:4 C#5:8", "B4:4 D5:8", "C#5:4 E5:8"]
CH2 = ["A4:4 D5:8", "A4:4 C#5:8", "B4:4 D5:8", "B4:4 D5:8", "A4:4 F#5:8", "C#5:4 E5:8", "B4:4 D5:8", "A4:4 C#5:4 E5:4"]
CH3 = ["D5:4 F#5:8", "C#5:4 E5:8", "D5:4 F#5:8", "B4:4 D5:8", "A4:4 F#5:8", "E5:4 C#5:8", "B4:4 D5:8", "C#5:4 E5:8"]
CH = [(t, c) for tune in (CH1, CH2, CH3) for t, c in zip(tune, CH_CHORDS)]
FIN_P1 = [bow(d, MAJOR, 52) for d in (6, 7, 8, 9)] + ["A4:4 E5:6 F#5:2", "F#4:4 D#5:6 F#5:2", "F4:4 G#4:6 B4:2", "G#4:4 B4:4 F5:4"]
FIN1 = list(zip(FIN_P1, ["E", "E", "E", "E", "A", "B", "C#7", "C#7"]))
FIN = FIN1 + [(shifted(t, 2), shift_chord(c, 2)) for t, c in zip(CH1, CH_CHORDS)] + [(shifted(t, 2), shift_chord(c, 2)) for t, c in zip(CH2, CH_CHORDS)]
FIN[-1] = ("A#4:4 C#5:4 E5:4", "F#7")

# --- patterns of a bar of 12 ticks --------------------------------------------------------------------------------------------------------------------------
HAT = [(t, 46 if t % 4 == 0 else 34) for t in range(0, 12, 2)]
DRUMS = {
    "waltz": {"kick": [(0, 100), (6, 52)], "snare": [(4, 82), (8, 82)], "hat": [(t, 40 if t % 4 == 0 else 28) for t in range(0, 12, 2)]},
    "drive16": {"kick": [(0, 100), (6, 62), (10, 52)], "snare": [(4, 94), (8, 98), (11, 46)], "hat": HAT},
    "half": {"kick": [(0, 96)], "snare": [(8, 90)], "hat": [(t, 28 if t % 4 == 0 else 20) for t in range(0, 12, 2)]},
    "dbl": {"kick": [(2, 86), (6, 80), (10, 86)], "snare": [(0, 90), (4, 96), (8, 96)], "hat": HAT},
    "peak": {"kick": [(0, 100), (6, 72), (10, 62)], "snare": [(4, 100), (8, 100)], "hat": [(t, 50 if t % 4 == 0 else 36) for t in (0, 4, 8, 6)], "tom": [(7, 50, 54)]},
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
GUITAR_PAT = {"arp": [(t, i, v) for t, i, v in zip(range(0, 12, 4), (0, 2, 3), (56, 42, 46))],
              "chug": [(t, 0, 70 if t == 0 else 50) for t in range(0, 12, 4)]}
HARP_ARP = [(t, i, 30) for t, i in zip((2, 6, 10), (1, 2, 3))]
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
    A_bass=[[(0, "E"), (12, "halfb"), (20, "ED_a")], [(0, "E"), (6, "ED_a"), (12, "halfb"), (20, "ED_a")]],
    A_drum=[[(0, "waltz"), (4, "drive16"), (12, "half"), (20, "drive16")], [(0, "waltz"), (4, "drive16"), (12, "half"), (20, "peak")]],
    A_half=(12, 20), A_pedal="Bm",
    B=[B, B], B_dbl=(10, 20),
    B_bass=[[(0, "ED_b"), (1, "TRI"), (2, "WALK"), (3, "ED_b"), (4, "ED_a"), (5, "TRI"), (6, "WALK"), (7, "BURST"), (8, "ED_b"), (9, "TRI"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "ED_b"),
             (14, "BURST"), (15, "ED_b"), (16, "WALK"), (17, "TRI"), (18, "BURST"), (19, "ED_b"), (20, "BURST"), (22, "S16")],
            [(0, "ED_a"), (1, "ED_a"), (2, "WALK"), (3, "BURST"), (4, "ED_b"), (5, "TRI"), (6, "BURST"), (7, "ED_b"), (8, "BURST"), (9, "ED_b"), (10, "BURST"), (11, "ED_b"), (12, "BURST"), (13, "ED_b"),
             (14, "BURST"), (15, "ED_b"), (16, "BURST"), (17, "S16"), (18, "BURST"), (19, "S16"), (20, "BURST"), (22, "S16b")]],
    B_drum=[[(0, "drive16"), (10, "dbl"), (20, "dbl")], [(0, "drive16"), (8, "peak"), (10, "dbl"), (20, "dbl")]],
    pre=[("A#4:4 C#5:4 E5:4", "F#7"), ("E5:12", "F#7")], stop=("E5:12", "F#7"), stop_at=(0, 8), bell=93,
    CH=[CH, CH], CH_bass=[[(0, "S16"), (7, "S16b"), (8, "S16"), (15, "S16b"), (16, "S16b")], [(0, "S16"), (1, "S16b"), (8, "S16b"), (16, "S16")]],
    scale_ch=tuple(p % 12 for p in (2, 4, 6, 7, 9, 11, 1)),
    FIN=[FIN, FIN], fin_split=8, scale_fin=tuple(p % 12 for p in (4, 6, 8, 9, 11, 1, 3)),
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
