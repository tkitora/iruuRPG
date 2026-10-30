#!/usr/bin/env python3
"""Makes unkeyed_phase1.nbs and unkeyed_phase2.nbs: "Eyes Without a Key (Boss009)" and "Eyes Without a Key, Unwritten (Boss009)", the boss theme of something that does not play by the rules of music.

    python3 tools/bgm/make_unkeyed_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_unkeyed_bgm.py outdir     writes them somewhere else

Written from the analysis that was sent with the request (t66): the player must not be able to tell what key it is in; a few uncanny notes repeated until it is brainwashing; the piece builds up from a
silence ticking like a clock to chaos, in stages; polyrhythm; the themes of the journey quoted in distorted form; cold metal on the top and a bass that is an earthquake.
  - No key. The bass falls by a HALF STEP every three bars (24 steps = two octaves down, then the loop starts over: the end of the loop and its start are the same note). Above it, a cluster of three notes
    (a half step, a tritone, a fourth - the sets 0-1-6, 0-6-7, 0-1-5 and 0-5-6) whose root jumps by fifths with no relation to the bass: the chords never resolve.
  - The motif is four notes, a half step and a tritone: m, m+1, m+6, m+5 - struck every three ticks and then repeated, over and over, on a new pitch and ON A NEW INSTRUMENT (harp, iron xylophone, 8-bit, electric
    piano) at every step of the bass.
  - Three stages in one loop (24 bars each), the stages of the analysis: (1) OBSERVATION: no drums, a ticking click like a clock, one note repeated that grows to two and then to the four notes, the bass a long
    low tone that swells, the didgeridoo under it, and twice a single high bell with a long echo. (2) EROSION: sixteenths on the hat and the snare (accents in 3+3+3+3+4), the bass in 5/4, then 7/4 and 5/8
    patterns under the 4/4 drums, the motif in 3/4, then 5/8 and 7/8 patterns: the beat is lost; the clusters are struck on the guitar. (3) COLLAPSE: everything: the bass every eighth like a bombardment, four on the
    floor, and the themes of the earlier bosses played AT THE SAME TIME, each in another key and another meter (the arc of Boss004 in A, the lullaby of Boss006 a half step up, the pendulum of Boss005 a minor
    third up, the waltz of Boss008 a major third down) over the cluster, with glass (the chime) falling down and a high bell. The last bar is silence but for the echo of the bell.
  - The intro is four bars of a click (the second hand) and one low note.
Two phases that are ONE piece (the same tempo, bars, steps and loop point): phase 2 is a HALF STEP HIGHER with the beat there from the very first bar (kick, hat, the 8-bit on the motif), as if the thing has stopped
watching and started to hunt.
150 BPM (10 ticks a second: a song tick is exactly 2 server ticks), 4/4 for the engine (a bar is 16 ticks), 76 bars (2:01); the loop starts after the 4 bars of the intro. Nothing here is random: the same file
comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # 150 BPM
LOOP_START_BAR = 4  # the click and the low note (4 bars) are played once
TITLES = {1: "Eyes Without a Key (Boss009)", 2: "Eyes Without a Key, Unwritten (Boss009)"}
FILES = {1: "unkeyed_phase1.nbs", 2: "unkeyed_phase2.nbs"}
POLYPHONY = 8
SHIFT = {1: 0, 2: 1}   # phase 2 is a half step higher
INTRO = 64
STAGE = 384            # a stage is 24 bars
STEP = 48              # the bass falls a half step every 3 bars
LENGTH = INTRO + 3 * STAGE

MOTIF = (0, 1, 6, 5)                                  # the four uncanny notes, in half steps above m
CLUSTERS = ((0, 1, 6), (0, 6, 7), (0, 1, 5), (0, 5, 6))
VOICES = (HARP, IRON_XYLOPHONE, BIT, PLING)           # the motif changes instrument at every step

# the themes of the earlier bosses (sounding MIDI notes and lengths in ticks), each played in another key and another cycle
ARC = ([69, 72, 76, 74, 72, 71, 69], [2, 2, 4, 2, 2, 2, 2])               # Boss004
LULLABY = ([67, 72, 75, 74, 72], [4, 4, 4, 2, 2])                          # Boss006
PENDULUM = ([71, 69, 66, 69, 71], [4, 2, 4, 2, 4])                         # Boss005
WALTZ = ([69, 72, 76], [4, 4, 4])                                          # Boss008


def fold(note, low=54, high=78):
    """The note moved by octaves into what the harp-like instruments can sound."""
    while note > high:
        note -= 12
    while note < low:
        note += 12
    return note


def guitar_note(note):
    while note > 66:
        note -= 12
    return note


def put(layer, tick, instrument, sounding, vel):
    """Puts the note unless the layer is taken at that tick or the tick is outside the song."""
    if 0 <= tick < LENGTH and tick not in layer.notes:
        layer.put_sound(tick, instrument, sounding, max(1, min(100, int(round(vel)))))


def drum(layer, tick, instrument, key_, vel):
    if 0 <= tick < LENGTH and tick not in layer.notes:
        layer.put(tick, instrument, key_, max(1, min(100, int(round(vel)))))


def step_of(tick):
    return ((tick - INTRO) // STEP) % 24 if tick >= INTRO else 0


def root_at(step, up):
    """The bass: F2 down to F#1 by half steps, then again."""
    return 41 - (step % 12) + up


def motif_pitch(step):
    return 64 + (step * 5) % 7


def cluster_at(step, up):
    base = 57 + (step * 5) % 12
    return [base + interval + up for interval in CLUSTERS[step % 4]]


def build(phase):
    p2 = phase == 2
    up = SHIFT[phase]
    click = Layer("Click (the second hand)", 55, 115)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare (sixteenths)", 70, 105)
    bass = Layer("Bass (the earthquake)", 95, 100)
    earth = Layer("Didgeridoo (the weight)", 60, 100)
    motif = Layer("The four notes (changing instrument)", 85, 100)
    motif2 = Layer("The four notes (answer)", 45, 130)
    chip = Layer("8-bit (doubles the four notes)", 40, 120)
    guitar = Layer("Guitar (the cluster)", 70, 60)
    harp_cluster = Layer("Harp (the cluster)", 55, 80)
    arc = Layer("Piano (the arc, Boss004)", 80, 100)
    lullaby = Layer("Iron xylophone (the lullaby, Boss006)", 70, 80)
    pendulum = Layer("8-bit (the pendulum, Boss005)", 55, 120)
    waltz = Layer("Electric piano (the waltz, Boss008)", 55, 100)
    glass = Layer("Chime (the glass)", 40, 90)
    bell = Layer("Bell (the keen)", 50, 100)
    layers = [click, kick, snare, bass, earth, motif, motif2, chip, guitar, harp_cluster, arc, lullaby, pendulum, waltz, glass, bell]

    # ---- the intro: a click like a second hand that grows, and one low note
    for tick in range(0, INTRO, 4):
        drum(click, tick, HAT, 66, 14 + tick * 14 // INTRO)
    swell(bass, BASS, root_at(0, up), 32, 32, 30, 62, 4)

    for stage in range(3):
        start = INTRO + stage * STAGE
        for bar in range(24):
            bar_start = start + bar * BAR
            step = step_of(bar_start)
            root = root_at(step, up)

            # ---- the bass
            if stage == 0:                                   # a long low tone that swells over the three bars of a step
                pass
            elif stage == 1:
                cycle = 20 if bar < 12 else 14 if bar < 18 else 10
                group = start + (0 if bar < 12 else 12 * BAR if bar < 18 else 18 * BAR)
                for tick in range(bar_start, bar_start + BAR):
                    local = (tick - group) % cycle
                    if local in (0, 3, 6, 10, 13, 16) and local < cycle:
                        put(bass, tick, BASS, root_at(step_of(tick), up), 84 if local == 0 else 62)
            else:                                            # the bombardment: every eighth, the accents in groups of three
                for tick in range(bar_start, bar_start + BAR, 2):
                    put(bass, tick, BASS, root_at(step_of(tick), up), 90 if (tick - bar_start) in (0, 6, 12) else 66)

        # the long tone of the observation and the didgeridoo (per step, a swell)
        for index in range(STAGE // STEP):
            at = start + index * STEP
            root = root_at(step_of(at), up)
            if stage == 0:
                swell(bass, BASS, root, at, STEP, 36, 76, 4)
                if index >= 2 or p2:
                    swell(earth, DIDGERIDOO, root, at, STEP, 20, 46, 4)
            elif stage == 1:
                swell(earth, DIDGERIDOO, root, at, STEP, 30, 58, 4)
            else:
                swell(earth, DIDGERIDOO, root, at, STEP, 54, 82, 4)

        # ---- the click goes on in the observation (and a little in the erosion)
        if stage == 0:
            for tick in range(start, start + STAGE, 4):
                drum(click, tick, HAT, 66, 30 if p2 else 24)
        elif stage == 1:
            for tick in range(start, start + STAGE, 4):
                drum(click, tick, HAT, 66, 26)

        # ---- the drums
        if stage == 0 and p2:
            for tick in range(start, start + STAGE, 8):
                drum(kick, tick, BASEDRUM, 62, 60 if (tick - start) % 16 == 0 else 40)
        if stage == 1:
            for tick in range(start, start + STAGE):
                local = (tick - start) % 16
                drum(snare, tick, SNARE, 66, 44 if local in (0, 3, 6, 9, 12) else 20)
                if local in (0, 8) or (p2 and local in (4, 12)):
                    drum(kick, tick, BASEDRUM, 62, 82 if local == 0 else 66)
        if stage == 2:
            for tick in range(start, start + STAGE):
                local = (tick - start) % 16
                if local % 4 == 0:
                    drum(kick, tick, BASEDRUM, 62, 90 if local == 0 else 76)
                if local in (4, 12):
                    drum(snare, tick, SNARE, 66, 80)
                elif (tick - start) % 128 >= 112:            # a roll of sixteenths in the last bar of eight
                    drum(snare, tick, SNARE, 66, 30 + ((tick - start) % 16) * 3)

    # ---- the four notes, over and over, on a new pitch and a new instrument at every step
    for stage in range(3):
        start = INTRO + stage * STAGE
        if stage == 0:
            plan = [(0, 8 * BAR, 12, 1), (8 * BAR, 16 * BAR, 12, 2), (16 * BAR, 24 * BAR, 12, 4)]
        elif stage == 1:
            plan = [(0, 12 * BAR, 12, 4), (12 * BAR, 18 * BAR, 10, 4), (18 * BAR, 24 * BAR, 14, 4)]
        else:
            plan = [(0, 24 * BAR, 12, 4)]
        for begin, end, cycle, count in plan:
            tick = start + begin
            while tick < start + end:
                for number in range(count):
                    at = tick + number * 3
                    if at >= start + end:
                        break
                    step = step_of(at)
                    pitch = motif_pitch(step) + MOTIF[number] + up
                    voice = VOICES[(step + (1 if p2 else 0)) % 4]
                    put(motif, at, voice, fold(pitch), (30 if stage == 0 else 48 if stage == 1 else 62) + (6 if number == 0 else 0))
                    if stage >= 1 and number % 2 == 0:       # answered a tritone below on the next voice
                        put(motif2, at + 1, VOICES[(step + 1) % 4], fold(pitch - 6), 30)
                    if p2 and stage <= 1:
                        put(chip, at, BIT, fold(pitch), 26)
                tick += cycle

    # ---- the clusters of the erosion (struck on the guitar, answered by the harp) and of the collapse
    for stage in (1, 2):
        start = INTRO + stage * STAGE
        for index in range(STAGE // 12):
            at = start + index * 12
            if stage == 1 and index % 2:
                continue
            step = step_of(at)
            for number, note in enumerate(cluster_at(step, up)):
                put(guitar, at + number, GUITAR, guitar_note(note), 42 if stage == 1 else 50)
                if stage == 2 and number == 0:
                    put(harp_cluster, at + 6, HARP, fold(note + 12), 34)

    # ---- the collapse: the themes of the earlier bosses, all at once, each in its own key and cycle
    start = INTRO + 2 * STAGE
    quotes = [
        (arc, HARP, ARC, 0, 0, 16, 0),                 # (layer, instrument, theme, from bar, shift, cycle, offset)
        (pendulum, BIT, PENDULUM, 0, 3, 16, 8),
        (lullaby, IRON_XYLOPHONE, LULLABY, 8, 1, 16, 4),
        (waltz, PLING, WALTZ, 16, -4, 12, 0),
    ]
    for layer, instrument, (notes, lengths), from_bar, shift, cycle, offset in quotes:
        tick = start + from_bar * BAR + offset
        while tick < start + 23 * BAR:
            at = tick
            for note, length in zip(notes, lengths):
                put(layer, at, instrument, fold(note + shift + up), 64)
                at += length
            tick += cycle

    # ---- the glass: the chime falls down in half steps (by what it sounds: F#6 to D#7 at most), in the bars 5 and 6 of every 8 in the erosion and the collapse
    for stage in (1, 2):
        start = INTRO + stage * STAGE
        for group in range(3):
            for bar in (5, 6):
                at = start + (group * 8 + bar) * BAR
                for number in range(8):
                    put(glass, at + number * 2, CHIME, 98 - number + up, 30 + (10 if stage == 2 else 0) + number)

    # ---- the keen: one high bell with a long echo
    keens = [INTRO + 8 * BAR, INTRO + 20 * BAR, INTRO + STAGE + 23 * BAR - 2, INTRO + 2 * STAGE + 12 * BAR, INTRO + 2 * STAGE + 23 * BAR]
    kime = [(INTRO + STAGE + 23 * BAR + 10, INTRO + 2 * STAGE), (INTRO + 2 * STAGE + 23 * BAR, LENGTH)]
    for first, end in kime:
        for layer in layers:
            for tick in range(first, end):
                layer.notes.pop(tick, None)
    for at in keens:
        swell(bell, BELL, 98 + up, at, 28, 56 if at != keens[-1] else 70, 4, 4)
    for layer in layers:   # the echo of the last bell must not run past the end of the song
        for tick in [tick for tick in layer.notes if tick >= LENGTH]:
            del layer.notes[tick]
    return layers, LENGTH


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
    out.mkdir(parents=True, exist_ok=True)
    for phase in (1, 2):
        layers, length = build(phase)
        assert length % BAR == 0, length
        removed = limit_polyphony(layers, POLYPHONY)
        target = out / FILES[phase]
        write_nbs(target, layers, length, TPS, TITLES[phase],
                  f"Boss theme with no key, phase {phase} of 2: the same tempo, the same bars and the same steps as the other phase (a half step higher in phase 2); the game goes over at a bar line. "
                  + ("Observation, erosion, collapse: the beat comes late." if phase == 1 else "The beat is there from the first bar."), LOOP_START_BAR)
        report(target, layers, length, TPS)
        print(f"  ({removed} quiet notes taken away where more than {POLYPHONY} sounded at once)")


if __name__ == "__main__":
    main()
