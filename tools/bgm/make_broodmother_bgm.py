#!/usr/bin/env python3
"""Makes broodmother_phase1.nbs and broodmother_phase2.nbs: "Mother of a Thousand Legs (Boss006)" and "..., Swarming (Boss006)", the spider queen in two phases, written again by the BGM制作ガイド.

    python3 tools/bgm/make_broodmother_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_broodmother_bgm.py outdir     writes them somewhere else

C minor, 4/4, 80 bars written at 150 BPM (see the t276 note at the end); intro 4 / A 16 / A' 16 / B 16 / A'' 16 (the peak, bar 52 = 65 %) / calm 12, loop from bar 5. The theme is the world theme turned crooked: it rises
in a minor third and a fourth as in the plains but is cut into groups of 3+3+3+3+2+2 ticks (an uneven seven-ish gait, "many legs" that never keep step; the guide's 3+3+2 idea, in a bar of 16 sixteenths).
The device: a skittering staccato bass (root hit at 0, 3, 6, 9, 12, 14) and a snare that marches; in the peak one flash of C major (the queen's lit eyes), then back to the dark.
Phase 1 (natural): harp, flute, guitar, bass, snare march. Phase 2 (a half step up, C# minor): the same bars and chords with the electric piano and the 8-bit chip as legs, sixteenth bass, the whole kit, the tune in unison.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.

FASTER (t269, nekorozv: "everything is too slow, a little faster"): the song is written as above (80 bars of 16 ticks, 150 BPM) and PLAYED at 200 BPM - 20 ticks a second, a beat of 6 ticks, a bar of 24 -
with the sections lengthened to 24 / 24 / 24 / 20 / 8 bars (100 bars, 2:03) so that it still lasts two minutes (bossframe.widen). The only clean step up from 150 is 200: the tempo is a whole number of ticks to the beat.

T276 (nekorozv, Discord: "rebuild with this information, broodmother included"): the BASE tempo is now 180 BPM (the guide's 'wall of sound'): the same 24-tick bar played at 18 ticks a second
(spec "fast_tps"), a beat of 6 ticks, 100 bars = 2:17. The rhythm follows the guide: intro = quarter-note bass, A = eighths, A2/B = eighths with sixteenth doubles (bass:mix), the peak = the
full sixteenth machine gun with the tune in LONG notes (halves and wholes struck again by 'sus'), gun drums (hat on every sixteenth).
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 0, [0, 2, 3, 5, 7, 8, 10]
K = key_pcs(TONIC, SCALE)

T = "G4:3 Bb4:3 C5:3 Eb5:3 D5:2 C5:2"           # the world theme in groups of 3+3+3+3+2+2
U = "Eb5:3 D5:3 C5:3 Bb4:3 G4:4"
R = "r:16"
a1 = seq_block(T, U, K, [0, ("u", 0), -2, ("u", -2), -3, ("u", -3), "D5:3 F#5:3 A4:3 C5:3 B4:2 G4:2".replace("F#5", "F#4"), "G4:4 B4:4 D5:8"])
a2 = seq_block(T, U, K, [0, "C5:3 D5:3 Eb5:3 G4:3 Bb4:2 C5:2", -2, ("u", -2), -3, "Ab4:3 C5:3 Eb5:3 Ab4:3 G4:2 F4:2", "D5:3 F#4:3 A4:3 C5:3 B4:2 G4:2", "G4:8 r:8"])
a3 = seq_block(T, U, K, [0, "Eb5:3 G4:3 Bb4:3 C5:3 D5:2 Bb4:2", -2, "C5:3 Eb5:3 Ab4:3 G4:3 F4:2 Eb4:2", -3, "Ab4:3 C5:3 F4:3 Ab4:3 G4:2 F4:2", "D5:3 F#4:3 A4:3 C5:3 B4:2 G4:2", "G4:8 D5:8"])
b1 = seq_block(T, U, K, [-2, R, -3, R, -1, R, 0, R])
b2 = seq_block(T, U, K, [-2, ("u", -2), -3, ("u", -3), "G4:3 Bb4:3 D5:3 G4:3 F4:2 D4:2", "Bb4:4 D5:4 F5:8", "D5:3 F#4:3 A4:3 C5:3 B4:2 G4:2", "G4:8 r:8"])
p1 = seq_block(T, U, K, [0, "C5:3 D5:3 Eb5:3 G4:3 C5:2 D5:2", 0, "Eb5:3 G4:3 Bb4:3 C5:3 D5:2 Eb5:2", -2, "C5:3 Eb5:3 Ab4:3 C5:3 Eb5:2 C5:2", -3, "Ab4:3 C5:3 F4:3 Ab4:3 G4:2 F4:2"])
p2 = seq_block(T, U, K, [0, "C5:3 D5:3 Eb5:3 G4:3 C5:2 D5:2", -2, "C5:3 Eb5:3 Ab4:3 C5:3 Eb5:2 C5:2", "D5:3 F#4:3 A4:3 C5:3 B4:2 G4:2", "G4:3 B4:3 D5:3 G5:3 F5:2 D5:2".replace("G5", "G4"), "E5:3 G4:3 C5:3 E5:3 D5:2 C5:2", "C5:8 r:8"])
# t276: in the peak the tune is LONG notes (halves and wholes, struck again by "sus") over the sixteenth-note machine-gun bass: the contrast of the guide
p1L = ["C5:8 Eb5:8", "G5:16", "F5:8 D5:8", "G5:16", "Eb5:8 G5:8", "C6:16", "Ab5:8 G5:8", "F5:16"]
p2L = ["C5:8 Eb5:8", "G5:8 C6:8", "Bb5:8 G5:8", "D6:16", "Ab5:8 Bb5:8", "C6:16", "E5:8 G5:8", "D5:16"]
c1 = ["G4:3 Bb4:3 C5:10", R, "Ab4:3 C5:3 Eb5:10", R, "F4:3 Ab4:3 C5:10", R, "G4:3 Bb4:3 D5:10", R, "G4:3 B4:3 D5:10", R, "D5:3 F#4:3 A4:3 C5:3 B4:2 G4:2", "G3:4 B3:4 D4:4 F4:4"]

# t276: the rhythm of the guide - the intro has quarters, A eighths, B eighths with sixteenth doubles and triplets, the peak the sixteenth machine gun under the long-note tune
VOICES0 = {
    "intro": ["bass:quarter", "riff:GUITAR:4@1", "drums:heart@1", "roll@1-2"],
    "A": ["hit:8", "lead:HARP:-12", "dbl:GUITAR:-12:0.8", "bass:eighth", "drone:loud", "riff:GUITAR:4", "drums:stomp", "pow:GUITAR:0@8"],
    "A2": ["hit:8", "lead:HARP:-12", "dbl:GUITAR:-12:0.9", "dbl:BASS:-24:0.8@8", "pow:GUITAR:0,12", "bass:mix", "drone:loud", "riff:GUITAR:2@8", "riff:GUITAR:4@0-8", "drums:half", "fill"],
    "B": ["lead:BASS:-12", "bass:mix", "drone:loud", "drums:stomp", "pow:GUITAR:0@12", "roll@23-24"],
    "PEAK": ["hit:4", "lead:HARP:-12", "sus:HARP:-12:3", "dbl:GUITAR:-12:0.9", "dbl:BASS:-24:0.9", "pow:GUITAR:0,6,12", "bass:sixteenth", "drone:loud", "drums:gun", "fill"],
}
VOICES1 = {
    "intro": ["bass:quarter", "riff:GUITAR:4@1", "drums:heart@1", "roll@1-2"],
    "A": ["hit:8", "lead:PLING:-12", "dbl:BIT:-12:0.7", "bass:mix", "drone:loud", "riff:GUITAR:2", "drums:half", "pow:BIT:0,12@8"],
    "A2": ["hit:8", "lead:PLING:-12", "dbl:BIT:-12:0.8", "dbl:GUITAR:-12:0.8", "dbl:BASS:-24:0.8@8", "pow:BIT:0,6,12", "bass:sixteenth@8", "bass:mix@0-8", "drone:loud", "riff:GUITAR:2", "drums:gallop", "fill"],
    "B": ["lead:BIT:-12", "bass:mix", "drone:loud", "drums:half", "pow:BIT:0@12", "roll@23-24"],
    "PEAK": ["hit:4", "lead:PLING:-12", "sus:PLING:-12:3", "dbl:BIT:-12:0.9", "dbl:GUITAR:-12:0.9", "dbl:BASS:-24:0.9", "pow:BIT:0,3,6,12", "bass:sixteenth", "drone:loud", "drums:gun", "fill"],
}

SPEC = dict(
    bar=16, tps=10.0, faster=True, fast_tps=18.0, tonic=TONIC, scale=SCALE, shift=[0, 1],
    sections=[("A", 16), ("A2", 16), ("B", 16), ("PEAK", 16), ("CALM", 12)],
    chords={
        "intro": ["Cm", "Cm", "Ab", "G"],
        "A": "Cm Cm Ab Ab Fm Fm G G Cm Cm Ab Ab Fm G Cm Cm".split(),
        "A2": "Cm Cm Ab Ab Fm Fm G G Cm Cm Ab Ab Fm Fm G G".split(),
        "B": "Ab Ab Fm Fm Bb Bb G G Ab Ab Fm Fm Eb Eb G G".split(),
        "PEAK": ["Cm|Ab", "Cm|Ab", "Fm|G", "Fm|G", "Cm|Ab", "Cm|Ab", "Fm|G", "Fm|G", "Cm|Ab", "Cm|Ab", "Fm|G", "Fm|G", "Ab|Bb", "Ab|Bb", "C|C", "G"],
        "CALM": "Fm Fm Ab Ab Bb Bb Fm Fm Eb Eb G G".split(),
    },
    melody={"intro": None, "A": a1 + a2, "A2": a1 + a3, "B": b1 + b2, "PEAK": p1L + p2L, "CALM": c1},
    pedal={"PEAK": "Cm"},
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[55, 57, 59, 60],
    voices=[heavy_voices(0, special=VOICES0), heavy_voices(1, special=VOICES1)],
    heavy=True,
)

TITLES = ("Mother of a Thousand Legs (Boss006)", "Mother of a Thousand Legs, Swarming (Boss006)")
FILES = ("broodmother_phase1.nbs", "broodmother_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Spider queen, phase 1 (natural sounds): the world theme in groups of 3+3+3+3+2+2 ticks over a skittering bass, C minor, one flash of C major. 80 bars, loops from bar 5. Same bars as phase 2.",
                  "Spider queen, phase 2 (electronic, a half step up): the same bars and chords, legs of electric piano and 8-bit, sixteenth bass, the whole kit, unison tune."],
                 TITLES)


if __name__ == "__main__":
    main()
