#!/usr/bin/env python3
"""Makes atlas_vindicator.nbs: "The Axe at the Gate (Boss001)", the boss theme of the Atlas cave (the vindicator, the master of the first dungeon). The third version (t278).

    python3 tools/bgm/make_atlas_boss_bgm.py            writes src/main/resources/bgm/atlas_vindicator.nbs
    python3 tools/bgm/make_atlas_boss_bgm.py out.nbs    writes somewhere else

STYLE 1 of the style doc (royal high-speed battle) on the 16th-note ladder of the BPM guide, 150 BPM: 10 ticks a second, a tick is a sixteenth, a bar is 16 ticks (4/4); 4 intro bars + 81 bars
= 1360 ticks = 136 s. E minor (harmonic: D# over B7), one piece, no phases (the first boss: the thinnest of the boss songs - at most about 36 notes a second at the peak).
The MELODY keeps the base tempo; the accompaniment is subdivided (bass: none / quarters / root-fixed eighths / eighths with sixteenth doubles and triplets / full sixteenths). Time table:
  0:00  INTRO (4 bars) + PRE (6 bars, to 0:16): a solo refrain of the piano (the harp, struck again under every long note, string-like) WITHOUT drums; in the PRE bars the snare comes
        in and gets denser (one hit, backbeat, quarters, eighths), quarter-note bass roots under it.
  0:16  A (20 bars): the bass is FIXED on the tonic E in eighths (the weight of each step), full drums, the tune low and ominous on the guitar (E3 - F#4); the last four bars drop into HALF TIME (kick on 1,
        snare on 3, quarter bass) - a pseudo tempo change.
  0:48  B (18 bars): the bass is RELEASED - it follows the chords with sixteenth double hits and triplet figures (3 + 3 + 2) - and the tune moves an octave UP onto the piano; the kick follows the bass;
        the last eight bars the snare doubles (on every beat: DOUBLE TIME). The B ends on B7.
  1:17  BREAK (3 bars): two bars of a snare/kick crescendo on a B pedal that gets denser, then ONE BAR with every drum and the bass muted and a single bell note.
  1:22  CHORUS (18 bars): the PARALLEL MAJOR - E minor flips to E major (G#, C#, A# replace G, C, D: E - B - C#m - A) - the tune in long notes (whole and half notes held by re-striking, piano) with a THIRD-ABOVE
        harmony (the pling), over a full SIXTEENTH machine-gun bass (root heavy, ghost notes, octave pops), guitar power chords and chug, the hat in sixteenths.
  1:50  FINALE (16 bars): the same chorus a WHOLE STEP UP (F# major); the last four bars turn back: F# - C# - B - B7, a dominant that hangs on D# and leads into tick 64 (E minor).
Nothing is random: the same file comes out every time. The tune is the motif of Atlas, E5 B4 G4 E4, a falling E minor triad (the plains theme turned upside down). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat45 import *  # noqa: F401,F403 - Builder, hit_ticks, sustain ... (helpers; this file edits nothing shared)

TPS = 10.0
BAR_T = 16
START = 64
TITLE = "The Axe at the Gate (Boss001)"
DESCRIPTION = ("Atlas cave boss 1, style 1 (royal high-speed battle), 150 BPM: solo piano refrain without drums, tonic-fixed eighth bass, half time, released bass and tune an octave up, double time, "
               "a one-bar bell break, the chorus in E major with a third-above harmony over a sixteenth bass, the finale a whole step up. Loops from tick 64.")
SECTIONS = [("PRE", 6), ("A", 20), ("B", 18), ("BREAK", 3), ("CHORUS", 18), ("FINALE", 16)]
NBARS = sum(n for _, n in SECTIONS)            # 81
LENGTH = START + NBARS * BAR_T                 # 1360 ticks = 136 s

CHORDS = {   # root pitch class, quality; the bass root (what SOUNDS), the harp comp notes
    "Em": (4, 40), "C": (0, 36), "G": (7, 31), "D": (2, 38), "Am": (9, 33), "B7": (11, 35),
}
A_CHORDS = ["Em", "Em", "C", "D", "Em", "Em", "C", "B7", "Em", "Em", "C", "D", "Am", "C", "D", "B7"]
A_MELODY = [
    "E5:2 B4:2 G4:2 E4:2 E5:2 B4:2 G4:4",     # the motif, twice
    "B4:4 G4:2 A4:2 B4:4 E5:4",
    "C5:2 G4:2 E4:2 G4:2 C5:4 E5:4",
    "D5:2 A4:2 F#4:2 A4:2 D5:4 F#5:4",
    "E5:2 B4:2 G4:2 E4:2 E5:2 B4:2 G4:4",
    "B4:4 E5:4 G4:4 B4:4",
    "C5:2 E5:2 G4:2 C5:2 E5:8",
    "D#5:2 F#5:2 B4:2 D#5:2 F#5:4 B4:4",
    "E5:4 B4:4 E5:4 G4:4",
    "G4:2 B4:2 E5:4 D5:4 B4:4",
    "C5:4 G4:4 E4:2 G4:2 C5:4",
    "D5:4 F#5:4 A4:4 D5:4",
    "A4:2 C5:2 E5:4 C5:4 A4:4",
    "G4:2 C5:2 E5:4 G4:4 C5:4",
    "A4:2 D5:2 F#5:4 D5:4 A4:4",
    "B4:2 D#5:2 F#5:4 D#5:4 B4:4",
]
B_CHORDS = ["C", "D", "Em", "Em", "C", "D", "B7", "B7", "Am", "G", "D", "Em", "Am", "B7", "Em", "Em"]
B_MELODY = [
    "E5:8 G4:4 C5:4",
    "F#5:8 D5:4 A4:4",
    "E5:4 G4:4 B4:4 E5:4",
    "B4:6 G4:2 E4:8",
    "E5:8 C5:4 G4:4",
    "F#5:8 A4:4 D5:4",
    "D#5:4 F#5:4 B4:4 D#5:4",
    "F#5:6 D#5:2 B4:8",
    "A4:4 C5:4 E5:8",
    "D5:4 B4:4 G4:8",
    "A4:4 D5:4 F#5:8",
    "E5:8 B4:8",
    "C5:2 E5:2 A4:4 C5:2 E5:2 A4:4",
    "D#5:2 F#5:2 B4:4 D#5:2 F#5:2 B4:4",
    "E5:2 B4:2 G4:2 E4:2 E5:2 B4:2 G4:4",
    "E4:12 r:4",
]
# the chorus in E major (what SOUNDS; the finale is the same a whole step up): chords as (semitones above the tonic, quality)
CH_CHORDS = [(0, "M"), (7, "M"), (9, "m"), (5, "M"), (0, "M"), (7, "M"), (5, "M"), (7, "7")]
CH_MELODY = [
    "G#4:8 B4:8", "B4:8 D#5:8", "C#5:8 G#4:4 B4:4", "A4:8 C#5:8",
    "B4:8 E5:8", "D#5:8 B4:8", "C#5:8 E5:8", "D#5:4 E5:4 D#5:8",
]
# the last four bars of the finale, in F# (sounding): F# - C# - B - B7, hanging on D#
FIN_END_CHORDS = [(0, "M"), (7, "M"), (5, "M"), (5, "7")]
FIN_END_MELODY = ["A#4:8 C#5:8", "C#5:8 F5:8", "D#5:8 B4:8", "F#4:4 A4:4 D#5:8"]
MAJOR_SCALE = (0, 2, 4, 5, 7, 9, 11)

ROLES = {"tune": (100, 80), "harm": (75, 120), "stab": (90, 90), "guitar": (90, 100), "guitar2": (75, 100), "bass": (100, 100), "didg": (90, 100),
         "kick": (100, 100), "snare": (92, 105), "hat": (50, 115), "bell": (70, 100)}


def tune(b, role, instrument, t0, text, shift=0, vel=84, grace=False):
    """Plays a bar written as 'E5:2 B4:2 ...': a long note (6 ticks or more) is struck again every 4 ticks, the loudness rising a little."""
    tick = 0
    for pitch, length in parse_bar(text):
        if pitch is not None:
            p = pitch + shift
            if instrument == HARP:
                while p > 78:
                    p -= 12
            if length >= 6:
                sustain(b, role, t0 + tick, instrument, p, length, vel, vel + 10, every=4)
            else:
                b.note(role, t0 + tick, instrument, p, vel + (6 if tick % 8 == 0 else 0))
        tick += length


def third_above(pitch, tonic_pc, fold_below=78):
    """The note a diatonic third above in the major key (a third below when that would leave the range of the pling)."""
    rel = (pitch - tonic_pc) % 12
    if rel in MAJOR_SCALE:
        i = MAJOR_SCALE.index(rel)
        up = MAJOR_SCALE[(i + 2) % 7] + (12 if i + 2 >= 7 else 0) - rel
        down = (rel - MAJOR_SCALE[(i - 2) % 7]) % 12
    else:
        up, down = 4, 4
    if pitch + up <= fold_below:
        return pitch + up
    return pitch - down


def roll(b, t0, start, step, v_from, v_to, last=16):
    ticks = list(range(start, last, step))
    for index, tick in enumerate(ticks):
        v = int(v_from + (v_to - v_from) * index / max(1, len(ticks) - 1))
        b.drum("snare", t0 + tick, SNARE, v, once=True)


class Song:
    def __init__(self):
        self.b = Builder(LENGTH, ROLES)

    def hit(self, t0, pc, low, scale_=1.0, drums=True):
        """An ensemble hit (6 or more notes at one tick): the bass and the didgeridoo low, a power chord of the guitar, two harp stab notes, the kick and the snare."""
        b = self.b
        b.note("bass", t0, BASS, low, 100 * scale_, once=True)
        b.note("didg", t0, DIDGERIDOO, low, 96 * scale_, once=True)
        b.note("guitar", t0, GUITAR, near(pc, 47, GUITAR), 100 * scale_, once=True)
        b.note("guitar2", t0, GUITAR, near((pc + 7) % 12, 54, GUITAR), 90 * scale_, once=True)
        b.note("stab", t0, HARP, near(pc, 58, HARP), 96 * scale_, once=True)
        b.note("stab", t0, HARP, near((pc + 7) % 12, 66, HARP), 90 * scale_, once=True)
        if drums:
            b.drum("kick", t0, BASEDRUM, 100 * scale_, once=True)
            b.drum("snare", t0, SNARE, 92 * scale_, once=True)

    # ---- intro: a solo refrain of the piano, struck again under its long notes; no drums, no bass
    def intro(self):
        b = self.b
        refrain = ["E5:16", "B4:8 G4:8", "E5:4 B4:4 G4:4 E4:4", "B4:4 D#5:4 F#5:8"]
        for bar, text in enumerate(refrain):
            tune(b, "tune", HARP, 16 * bar, text, vel=84 + 4 * bar)
        self.hit(0, 4, 40, drums=False)                          # the first tick already weighs: E in the bass, the didgeridoo and the guitar under the refrain
        sustain(b, "stab", 48, GUITAR, 47, 16, 60, 90, every=4)    # a low B under the dominant
        sustain(b, "didg", 48, DIDGERIDOO, 35, 16, 60, 90, every=4)

    # ---- PRE (6 bars of the loop): the refrain goes on; the snare comes in
    def pre(self, t0, ib):
        b = self.b
        order = [0, 1, 2, 3, 4, 7]
        chords = ["Em", "Em", "C", "D", "Em", "B7"]
        tune(b, "tune", HARP, t0, A_MELODY[order[ib]], vel=86 + 2 * ib)
        if ib in (0, 3):
            self.hit(t0, CHORDS[chords[ib]][0], CHORDS[chords[ib]][1], 0.8, drums=ib > 0)
        snares = [(12,), (4, 12), (4, 12), (4, 8, 12), (0, 4, 8, 12), (0, 2, 4, 6, 8, 10, 12, 14)][ib]
        for index, tick in enumerate(snares):
            b.drum("snare", t0 + tick, SNARE, 36 + 8 * ib + 4 * index)
        if ib >= 2:
            for beat in range(4):
                b.note("bass", t0 + 4 * beat, BASS, CHORDS[chords[ib]][1], 44 + 4 * ib + (6 if beat % 2 == 0 else 0))
        if ib >= 3:
            for tick in (0, 8):
                b.drum("kick", t0 + tick, BASEDRUM, 56 + 6 * ib)

    # ---- A: the tonic is fixed in eighths; the tune low on the guitar; the last 4 bars in half time
    def a_bar(self, t0, ib):
        b = self.b
        half = ib >= 16
        idx = ib if ib < 16 else ib - 16
        chord = A_CHORDS[idx]
        if half:
            for tick in (0, 8):
                b.note("bass", t0 + tick, BASS, 40, 92 if tick == 0 else 70)
            b.drum("kick", t0, BASEDRUM, 100)
            b.drum("snare", t0 + 8, SNARE, 96)
            for tick in (4, 12):
                b.drum("hat", t0 + tick, HAT, 30)
            sustain(b, "didg", t0, DIDGERIDOO, 40, 16, 60, 80, every=4)
        else:
            for tick in range(0, 16, 2):
                b.note("bass", t0 + tick, BASS, 40, 100 if tick == 0 else (92 if tick % 8 == 0 else (84 if tick % 4 == 0 else 70)))
            for tick in range(0, 16, 4):
                b.note("didg", t0 + tick, DIDGERIDOO, 40, 92 if tick % 8 == 0 else 78)
            for tick, vel in ((0, 100), (6, 70), (8, 92), (14, 66)):
                b.drum("kick", t0 + tick, BASEDRUM, vel)
            for tick in (4, 12):
                b.drum("snare", t0 + tick, SNARE, 88)
            for tick in range(0, 16, 2):
                b.drum("hat", t0 + tick, HAT, 30 if tick % 4 == 0 else 20)
            if ib % 4 == 3:
                roll(b, t0, 12, 1, 40, 90)
            if ib in (0,):
                b.drum("kick", t0, BASEDRUM, 100, once=True)
        tune(b, "tune", GUITAR, t0, A_MELODY[idx], shift=-12, vel=96)
        if not half:                                           # the ensemble hit on the downbeat of every bar (the chord above the fixed tonic), a roll before the next block
            self.hit(t0, CHORDS[chord][0], 40, 0.9)
            if ib in (0, 8, 16):
                b.drum("snare", t0, SNARE, 100, once=True)

    # ---- B: the bass released (doubles and triplets), the tune an octave up on the piano; the last 8 bars double time
    def b_bar(self, t0, ib):
        b = self.b
        idx = ib if ib < 16 else ib - 4                         # bars 16, 17 are the Am and B7 bars of the tune (12, 13)
        chord = B_CHORDS[idx]
        root = CHORDS[chord][1]
        for tick in hit_ticks(3, "ST", ib + 5):
            off = (0, 0, 7, 0, 12, 7, 5, 7)[tick // 2]
            vel = bass_velocity(tick, 3)
            b.note("bass", t0 + tick, BASS, root + off, vel)
            if tick % 4 == 0:
                b.note("didg", t0 + tick, DIDGERIDOO, root + off, 84 if tick % 8 == 0 else 70)
        doubletime = ib >= 10
        for tick in (0, 3, 6, 8, 11, 14):
            b.drum("kick", t0 + tick, BASEDRUM, 100 if tick % 8 == 0 else 74)
        for tick in ((0, 4, 8, 12) if doubletime else (4, 12)):
            b.drum("snare", t0 + tick, SNARE, 86 if tick not in (0, 8) else 62)
        for tick in range(0, 16, 2):
            b.drum("hat", t0 + tick, HAT, 32 if tick % 4 == 0 else 22)
        if ib % 4 == 3 or ib == 17:
            roll(b, t0, 10, 1, 40, 96)
        tune(b, "tune", HARP, t0, B_MELODY[idx], vel=96)
        self.hit(t0, CHORDS[chord][0], root, 0.9)
        b.note("stab", t0 + 8, HARP, near(CHORDS[chord][0], 58, HARP), 90, once=True)
        for tick in (8,):                                       # guitar power chord on the third beat
            b.note("guitar", t0 + tick, GUITAR, near(CHORDS[chord][0], 47, GUITAR), 92, once=True)
            b.note("guitar2", t0 + tick, GUITAR, near((CHORDS[chord][0] + 7) % 12, 54, GUITAR), 84, once=True)

    # ---- BREAK: a crescendo on a B pedal, then one bar of silence with a single bell note
    def break_bar(self, t0, ib):
        b = self.b
        if ib == 2:
            b.note("bell", t0, BELL, 96, 88)
            return
        step = 2 if ib == 0 else 1
        for index, tick in enumerate(range(0, 16, step)):
            v = int(34 + 66 * (16 * ib + tick) / 31)
            b.drum("snare", t0 + tick, SNARE, v)
            b.note("bass", t0 + tick, BASS, 35, v * 0.9)
        for beat in range(4):
            b.drum("kick", t0 + 4 * beat, BASEDRUM, int(50 + 50 * (16 * ib + 4 * beat) / 31))
        sustain(b, "didg", t0, DIDGERIDOO, 35, 16, 60, 90, every=4)

    # ---- CHORUS and FINALE: long notes with a third-above harmony over the sixteenth bass
    def chorus_bar(self, t0, ib, finale):
        b = self.b
        shift = 2 if finale else 0
        end = finale and ib >= 12
        if end:
            chord_def = FIN_END_CHORDS[ib - 12]
            text = FIN_END_MELODY[ib - 12]
            shift = 0
        else:
            chord_def = CH_CHORDS[ib % 8]
            text = CH_MELODY[ib % 8]
        tonic_pc = (4 + shift) % 12 if not end else 6
        rel, quality = chord_def
        pc = (tonic_pc + rel) % 12
        low = near(pc, 36, BASS)
        # --- the sixteenth machine gun: root heavy, ghost notes, an octave pop on the third sixteenth; the fifth on the last beat
        for tick in range(16):
            beat, pos = divmod(tick, 4)
            pitch = low + (7 if beat == 3 and ib % 2 == 1 else 0)
            vel = (100, 54, 82, 52)[pos]
            if pos == 2:
                pitch += 12
            if pitch > 54:
                pitch -= 12
            b.note("bass", t0 + tick, BASS, pitch, vel)
        for tick in range(0, 16, 4):
            b.note("didg", t0 + tick, DIDGERIDOO, low, 92 if tick % 8 == 0 else 80)
        # --- guitar: power chord on the downbeats, chug in between
        for tick in range(0, 16, 2):
            b.note("guitar", t0 + tick, GUITAR, near(pc, 47, GUITAR), 100 if tick % 8 == 0 else 52)
        for tick in (0, 4, 8, 12):
            b.note("guitar2", t0 + tick, GUITAR, near((pc + 7) % 12, 54, GUITAR), 92 if tick % 8 == 0 else 76)
            b.note("stab", t0 + tick, HARP, near(pc, 58, HARP), 70, once=True)
        # --- drums: the kick in quarters with a pickup, the backbeat, the hat in sixteenths
        for tick, vel in ((0, 100), (4, 70), (8, 96), (12, 70), (14, 60)):
            b.drum("kick", t0 + tick, BASEDRUM, vel)
        for tick in (4, 12):
            b.drum("snare", t0 + tick, SNARE, 90)
        for tick in range(16):
            b.drum("hat", t0 + tick, HAT, 34 if tick % 4 == 0 else (26 if tick % 2 == 0 else 16))
        if ib % 8 == 7 and not end:
            roll(b, t0, 10, 1, 40, 96)
        if ib == 17 or (finale and ib == 15):
            roll(b, t0, 6, 1, 36, 100)
        # --- the tune in long notes on the piano, the pling a third above
        tick = 0
        for pitch, length in parse_bar(text):
            p = pitch + shift - 12 if pitch is not None else None      # an octave lower than written: the chorus lives at F#3 - E4 (a boss does not sing high)
            if p is not None:
                while p < 54:
                    p += 12
                sustain(b, "tune", t0 + tick, HARP, p, length, 98, 100, every=4)
                if not (finale and ib >= 14):
                    h = third_above(p, tonic_pc)
                    if 54 <= h <= 78:
                        sustain(b, "harm", t0 + tick, PLING, h, length, 70, 84, every=4)
            tick += length
        if ib % 8 == 0 and not end:                             # the unison hit that starts each phrase of eight bars
            b.drum("snare", t0, SNARE, 96, once=True)


def main():
    arg = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/atlas_vindicator.nbs"
    target = arg
    target.parent.mkdir(parents=True, exist_ok=True)
    song = Song()
    song.intro()
    edges = [("intro", 0, START)]
    cursor = START
    for name, n in SECTIONS:
        edges.append((name, cursor, cursor + n * BAR_T))
        for ib in range(n):
            t0 = cursor + ib * BAR_T
            if name == "PRE":
                song.pre(t0, ib)
            elif name == "A":
                song.a_bar(t0, ib)
            elif name == "B":
                song.b_bar(t0, ib)
            elif name == "BREAK":
                song.break_bar(t0, ib)
            else:
                song.chorus_bar(t0, ib, name == "FINALE")
        cursor += n * BAR_T
    assert cursor == LENGTH
    layers = finish(target, song.b, TPS, TITLE, DESCRIPTION)
    print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
    bass = [layer for layer in layers if layer.name.startswith("bass")]
    print("   bass notes per bar by section: " + ", ".join(
        f"{n} {sum(1 for layer in bass for t in layer.notes if s <= t < e) / ((e - s) / 16):.1f}" for n, s, e in edges))
    print("   section starts (s): " + ", ".join(f"{n} {s / TPS:.0f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
