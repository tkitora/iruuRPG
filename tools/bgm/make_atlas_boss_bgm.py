#!/usr/bin/env python3
"""Makes atlas_vindicator.nbs: "The Axe at the Gate (Boss001)", the boss theme of the Atlas cave (the vindicator, the master of the first dungeon). The second version.

    python3 tools/bgm/make_atlas_boss_bgm.py            writes src/main/resources/bgm/atlas_vindicator.nbs
    python3 tools/bgm/make_atlas_boss_bgm.py out.nbs    writes somewhere else

E minor (harmonic: D# over B7), 4/4, 150 BPM (one tick = a sixteenth; 10 ticks a second, so a song tick is exactly 2 server ticks), 80 bars (2:08). Same form, tune and chords as the first
version (intro, A, B, a bridge in half time, A, B with everything, a four-bar run-up that jumps back to A; one piece, no phases: the dungeon is short); written again by what SOUNDS and with
the techniques of the style guides (docs/bgm-style-guide.md, docs/bgm-style-guide-01.md) and of the later requests for the boss songs:
  - No flute, bell or chime (they sounded two or three octaves above the rest). The tune is on the piano (the harp, E4 to F#5), struck again under a long note (a note block dies away at once).
  - Natural first, electronic as it grows: the first A is only natural (piano, guitar, bass, drums); the first chorus brings the 8-bit square wave on the tune and a counter-melody on the
    electric piano that runs in the gaps of the long notes; the last chorus adds the iron xylophone (vibraphone) sparkle and a snare chain of sixteenths.
  - The tonic pedal point: in the second A the bass strikes the tonic E in sixteenths with an octave jump while the chords change above it; in the last chorus the bass jumps an octave every eighth.
  - Grace notes: a half step above the first note of every phrase, one tick before it, so that the note starts sharply.
  - A solo: the guitar plays the bridge (E3 to F#4, a held note struck again), over a bass and a snare in half time. It ends on a tritone (F over B7) held in the 8-bit, and a half step (F over E)
    at the end of the last chorus. These are the only places that rub, on purpose.
  - The lowest note: the last bar of the run-up holds one low B (B1) that grows, the earth (didgeridoo) under it.
Drums are thick: a kick that follows the bass, a snare on 2 and 4, a hat; the bass and the guitar play the 3-3-2 pattern (ticks 0, 3, 6, 8, 11, 14). The melody is the motif of Atlas, played fast.

The motif of Atlas (the place): E5 B4 G4 E4 - a falling E minor triad, the plains theme turned upside down. It is the same as in the cave theme ("Where the Titan Sleeps"), here
in eighths and driving. Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # ticks a second (a divisor of 20: a song tick is 2 server ticks); a tick is a sixteenth note, a quarter is 4 ticks: 150 BPM
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "The Axe at the Gate (Boss001)"
POLYPHONY = 8
E_MINOR = scale("E3 F#3 G3 A3 B3 C4 D4 E4 F#4 G4 A4 B4 C5 D5 E5 F#5")

CHORDS = {  # the notes for the piano, low to high; the root and the fifth for the bass (it sounds F#1 to F#3); the guitar plays them an octave up. All by what SOUNDS
    "Em": ([59, 64, 67, 71, 76], 40, 47),
    "C": ([60, 64, 67, 72, 76], 36, 43),
    "G": ([55, 59, 62, 67, 71], 31, 38),
    "D": ([57, 62, 66, 69, 74], 38, 45),
    "Am": ([57, 60, 64, 69, 72], 33, 40),
    "B7": ([59, 63, 66, 69, 71], 35, 42),
}
TRESILLO = [0, 3, 6, 8, 11, 14]   # 3 + 3 + 2, twice
TONIC = 40                         # E2: the pedal point (and E3 an octave up)

INTRO_CHORDS = ["Em", "Em", "Em", "B7"]
INTRO_MELODY = ["r:16", "r:16", "E5:4 B4:4 G4:4 E4:4", "B4:4 D#5:4 F#5:8"]
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
BRIDGE_CHORDS = ["Em", "Em", "Am", "Am", "C", "D", "B7", "B7"]
SOLO = [                      # the guitar sounds F#2 to F#4 (E3 to F#4 here)
    "E3:4 G3:4 B3:4 E4:4",
    "F#4:4 E4:4 B3:8",
    "A3:4 C4:4 E4:4 C4:4",
    "E4:6 D4:2 C4:8",
    "C4:2 E4:2 G3:4 C4:4 E4:4",
    "F#4:4 D4:4 A3:4 D4:4",
    "D#4:2 F#4:2 B3:4 D#4:4 F#4:4",
    "F#4:8 r:8",
]
OUTRO_CHORDS = ["Em", "C", "D", "B7"]
OUTRO_MELODY = ["E5:4 B4:4 G4:4 E4:4", "C5:4 G4:4 E4:4 G4:4", "D5:4 A4:4 F#4:4 A4:4", "D#5:2 F#5:2 B4:4 r:8"]

# two notes held and struck again a little: (section, bar of the section, the lower note, the higher note, who plays them): a tritone and a half step
CLASHES = [("bridge", 7, 59, 65), ("B2", 15, 64, 65)]

# (name, chords, melody, drums, guitar, the extras)
#   drums:  "kick" kick only | "full" | "half" (half time) | "climax" (the kick follows the bass, a snare chain) | "roll" (a snare roll in the last bar)
#   guitar: None | "muted" (ticks 0 and 8) | "tresillo"
#   extras: "grace" (a half step above the first note of a phrase) | "bit" (8-bit on the tune) | "counter" (electric piano runs in the long notes) | "harmony" (piano a third below)
#           | "sparkle" (iron xylophone on the bar) | "pedal" (the bass strikes the tonic in sixteenths) | "octave" (the bass jumps an octave every eighth) | "hat16" (the hat in sixteenths)
#           | "solo" (the guitar plays the bridge) | "soft-bass" | "roll-intro" (snare in the last two bars) | "low" (the last bar holds a low note)
SECTIONS = [
    ("intro", INTRO_CHORDS, INTRO_MELODY, "kick", None, {"roll-intro"}),
    ("A", A_CHORDS, A_MELODY, "full", "muted", {"grace"}),
    ("B", B_CHORDS, B_MELODY, "full", "tresillo", {"grace", "bit", "counter", "hat16"}),
    ("bridge", BRIDGE_CHORDS, None, "half", None, {"solo", "soft-bass"}),
    ("A2", A_CHORDS, A_MELODY, "full", "tresillo", {"grace", "harmony", "pedal"}),
    ("B2", B_CHORDS, B_MELODY, "climax", "tresillo", {"grace", "bit", "counter", "harmony", "sparkle", "octave"}),
    ("outro", OUTRO_CHORDS, OUTRO_MELODY, "roll", "muted", {"low"}),
]


def put_over(layer, tick, instrument, sounding, vel):
    """A drum or a fill puts its note over what is there."""
    layer.notes.pop(tick, None)
    layer.put(tick, instrument, sounding, vel)


def build():
    lead = Layer("Piano (melody)", 100, 100)
    hold = Layer("Piano (melody held)", 60, 100)
    grace = Layer("Piano (grace note)", 50, 100)
    chip = Layer("8-bit (doubles the melody)", 40, 120)
    harmony = Layer("Piano (a third below)", 50, 80)
    counter = Layer("Electric piano (counter-melody)", 45, 90)
    sparkle = Layer("Iron xylophone (sparkle)", 45, 125)
    solo = Layer("Guitar (solo)", 80, 100)
    clash_a = Layer("Rub (lower)", 45, 90)
    clash_b = Layer("Rub (higher)", 45, 110)
    pad = Layer("Piano (arpeggio)", 55, 90)
    guitar_root = Layer("Guitar (root)", 75, 60)
    guitar_fifth = Layer("Guitar (fifth)", 70, 70)
    bass = Layer("Bass", 90, 100)
    earth = Layer("Didgeridoo (the earth)", 55, 100)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat", 55, 115)
    layers = [lead, hold, grace, chip, harmony, counter, sparkle, solo, clash_a, clash_b, pad, guitar_root, guitar_fifth, bass, earth, kick, snare, hat]

    bar_number = 0
    for name, chords, tune, drums, guitar, extras in SECTIONS:
        last = len(chords) - 1
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            tones, root, fifth = CHORDS[chord]

            # ---- the bass
            if name == "bridge":
                for tick in (0, 8):
                    bass.put_sound(start + tick, BASS, root + 12, 56)
            elif name == "intro" and index < 2:
                pass
            elif "low" in extras and index == last:  # the lowest note, held and growing
                swell(bass, BASS, 35, start, BAR, 60, 92, 4)
                swell(earth, DIDGERIDOO, 35, start, BAR, 40, 70, 4)
            elif "pedal" in extras:  # the tonic in sixteenths with an octave jump, whatever the chord
                for tick in range(16):
                    bass.put_sound(start + tick, BASS, TONIC + 12 if tick % 4 == 2 else TONIC, 72 if tick % 8 == 0 else 54 if tick % 4 == 0 else 44)
            elif "octave" in extras:  # eighths, the root jumping an octave
                for step in range(8):
                    bass.put_sound(start + step * 2, BASS, root + 12 if step % 2 else root, 76 if step == 0 else 62)
            else:  # the 3-3-2 pattern on the root, the fifth on the third hit of each half
                for tick in TRESILLO:
                    bass.put_sound(start + tick, BASS, fifth if tick in (6, 14) else root, 78 if tick in (0, 8) else 64)

            # ---- the guitar: power chord stabs
            if guitar is not None:
                stabs = [0, 8] if guitar == "muted" else TRESILLO
                for tick in stabs:
                    guitar_root.put_sound(start + tick, GUITAR, root + 12, 56 if tick in (0, 8) else 48)
                    if tick in (0, 8):
                        guitar_fifth.put_sound(start + tick, GUITAR, fifth + 12, 44)

            # ---- the drums
            if drums == "kick":
                kick.put(start, BASEDRUM, 62, 80)
                kick.put(start + 8, BASEDRUM, 62, 70)
            elif drums in ("full", "roll"):
                for tick in (0, 6, 8, 14):
                    kick.put(start + tick, BASEDRUM, 62, 84 if tick == 0 else 70)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 70)
                if "hat16" in extras:
                    for tick in range(16):
                        hat.put(start + tick, HAT, 66, 34 if tick % 4 == 0 else 24 if tick % 2 == 0 else 14)
                else:
                    for tick in range(0, 16, 2):
                        hat.put(start + tick, HAT, 66, 34 if tick in (4, 12) else 24)
            elif drums == "half":
                kick.put(start, BASEDRUM, 62, 80)
                snare.put(start + 8, SNARE, 66, 66)
                for tick in range(0, 16, 4):
                    hat.put(start + tick, HAT, 66, 22)
            elif drums == "climax":
                for tick in TRESILLO:
                    kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 74)
                for tick in (2, 6, 10, 14, 15):  # the chain: the snare fills the sixteenths between its hits
                    snare.put(start + tick, SNARE, 66, 22 if tick != 15 else 30)
                for tick in range(0, 16, 2):
                    hat.put(start + tick, HAT, 66, 38 if tick % 4 == 0 else 22)
            # snare rolls that run up to the next section: the last bar of the outro, the last two of the intro
            roll_bar = (drums == "roll" and index == last) or ("roll-intro" in extras and index >= last - 1)
            if roll_bar:
                for tick in list(snare.notes):
                    if start <= tick < start + BAR:
                        del snare.notes[tick]
                for tick in range(16):
                    if "roll-intro" in extras and index == last - 1 and tick % 2:
                        continue
                    progress = (tick + (16 if index == last else 0)) / 32.0
                    snare.put(start + tick, SNARE, 66, 30 + int(48 * progress))
                if drums == "roll":
                    for tick in list(hat.notes):
                        if start <= tick < start + BAR:
                            del hat.notes[tick]

            # ---- the pad of the second A: a soft arpeggio in eighths
            if "pedal" in extras:
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 3, 2, 1]):
                    pad.put_sound(start + step * 2 + 1, HARP, tones[tone_index], 24)

            # ---- the solo of the bridge
            if "solo" in extras:
                tick = 0
                for note, length in parse_bar(SOLO[index]):
                    if note is not None:
                        solo.put_sound(start + tick, GUITAR, note, 88 if tick == 0 else 78)
                        for again in range(4, length, 4):
                            solo.put_sound(start + tick + again, GUITAR, note, 42)
                    tick += length
                lead.put_sound(start, HARP, tones[3], 40)
                lead.put_sound(start + 8, HARP, tones[2], 32)

            # ---- the rubs
            for (section, bar, lower, higher) in CLASHES:
                if section == name and bar == index:
                    for again in range(4, 12, 4):
                        clash_a.put_sound(start + again, BIT, lower, 46 - again * 2)
                        clash_b.put_sound(start + again, BIT, higher, 46 - again * 2)

            # ---- the melody
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        at = start + tick
                        lead.put_sound(at, HARP, note, 90 if tick == 0 else 84)
                        for again in range(4, length, 4):  # a long note is struck again, softer
                            hold.put_sound(at + again, HARP, note, 50 - again * 2)
                        if "grace" in extras and tick == 0 and index % 4 == 0 and at > 0 and note + 1 <= 78:
                            grace.put_sound(at - 1, HARP, note + 1, 52)
                        if "bit" in extras:
                            chip.put_sound(at, BIT, note, 34)
                        if "harmony" in extras:
                            harmony.put_sound(at, HARP, max(54, third_below(note, E_MINOR)), 44)
                        if "sparkle" in extras and tick == 0 and index % 4 == 0:
                            sparkle.put_sound(at + 2, IRON_XYLOPHONE, note, 38)
                        if "counter" in extras and length >= 8:  # the counter-melody runs where the tune holds
                            for step, offset in enumerate(range(2, length, 2)):
                                counter.put_sound(at + offset, PLING, tones[(1, 2, 3, 4, 3, 2, 3)[step % 7]], 42)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    removed = limit_polyphony(layers, POLYPHONY)
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/atlas_vindicator.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Boss theme of the Atlas cave (the vindicator). Fast, heavy drums, 80 bars, loops from bar 5 (the intro is played once).", LOOP_START_BAR)
    report(target, layers, length, TPS)
    print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
