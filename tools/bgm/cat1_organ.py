"""Helpers for the songs of class 1 (overwhelming majesty and despair) on the "sixteenth-note machine gun" guide (BGM t276).

A Score collects layers (re-struck pads, held melody notes, chord voicings, drums, hats) and a small vocabulary of BASS FIGURES that the songs combine to make the felt tempo 2x-4x the base tempo:
    Q  quarters (soft, the intro)           E  eighth-note root repeats (A)           E_ eighths and a sixteenth pick-up
    S  eighths with sixteenth double hits   T  triplet crawl (three to a beat: ticks 0, 1, 3)        WU / WV  eighths that walk up / up and back (the undulation)
    F16 eighths, then sixteenths in the second half (a fill into the next block)      M16  the full sixteenth machine gun (3-3-2 accents, octave pops)
A bar is 16 ticks (a sixteenth each; the engine's phase line), the intro is 4 bars = 64 ticks, so the loop point is tick 64. Everything is written by what SOUNDS (nbslib.put_sound). Nothing is random.
Used by make_atlas_boss3_bgm.py (the stone giant) and make_unkeyed_bgm.py (the god with no key).
"""
import itertools

from bossframe import bar_notes, chord as chord_of  # noqa: F401  (read only: the parser of a melody bar and the chord table)
from nbslib import *  # noqa: F401,F403


class Score:
    def __init__(self, tps, sections, shift=0):
        self.tps = tps
        self.sections = sections
        self.bars = sum(b for _, b in sections)
        self.length = self.bars * BAR
        self.layers = {}
        self.starts = {}
        self.shift = shift
        position = 0
        for name, bars in sections:
            self.starts[name] = position
            position += bars
        self.refit = 0
        self.refit_log = {}
        for name, volume, pan in (("bd", 100, 100), ("sn", 80, 105), ("hat", 55, 100)):   # the drums are always the first layers
            self.layer(name, volume, pan)

    def layer(self, name, volume=70, pan=100):
        if name not in self.layers:
            self.layers[name] = Layer(name, volume, pan)
        return self.layers[name]

    def put(self, lname, tick, inst, snd, vel, shift=True):
        """One note by what it sounds as (moved by octaves into the range of the instrument if it must be). A note that is already there stays."""
        tick = int(tick)
        if tick < 0 or tick >= self.length:
            return False
        layer = self.layers[lname]
        if tick in layer.notes:
            return False
        vel = max(1, min(100, int(round(vel))))
        if inst in BASE:
            snd = snd + (self.shift if shift else 0)
            low = BASE[inst]
            while snd < low:
                snd += 12
                self.refit += 1
                self.refit_log[lname] = self.refit_log.get(lname, 0) + 1
            while snd > low + 24:
                snd -= 12
                self.refit += 1
                self.refit_log[lname] = self.refit_log.get(lname, 0) + 1
            layer.put_sound(tick, inst, snd, vel)
        else:
            layer.put(tick, inst, 66, vel)
        return True

    def fits(self, inst, snd, shift=True):
        """True when the note sounds inside the range of the instrument without being moved by an octave."""
        snd = snd + (self.shift if shift else 0)
        return BASE[inst] <= snd <= BASE[inst] + 24

    def hold(self, lname, inst, snd, tick, length, v0, v1, every=4):
        """A note held for ``length`` ticks by striking it again every ``every`` ticks, the loudness moving from v0 to v1."""
        steps = max(1, (length - 1) // every)
        for index, offset in enumerate(range(0, length, every)):
            self.put(lname, tick + offset, inst, snd, v0 + (v1 - v0) * index / steps)

    def drum(self, tick, kind, vel):
        if kind == "hat":
            self.put("hat", tick, HAT, 66, vel)
        else:
            self.put("bd" if kind == "bd" else "sn", tick, BASEDRUM if kind == "bd" else SNARE, 66, vel)

    def roll(self, tick, ticks=8, v0=34, v1=92, density="dense"):
        """A snare roll over ``ticks`` ticks that gets louder and denser (a pickup for the downbeat that follows)."""
        offsets = list(range(0, ticks, 2)) if density == "dense" else list(range(0, ticks, 4))
        if density == "dense":
            offsets += [o for o in range(ticks - 4, ticks) if o not in offsets]
        offsets = sorted(set(offsets))
        for index, offset in enumerate(offsets):
            self.drum(tick + offset, "sn", v0 + (v1 - v0) * index / max(1, len(offsets) - 1))

    def build(self):
        return [layer for layer in self.layers.values() if layer.notes]


# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- bass figures
# (tick, semitones over the root of the chord ("t": its third, 3 or 4), velocity)
def _eighths(accents=(92, 50, 72, 50, 86, 50, 72, 56)):
    return [(2 * i, 0, v) for i, v in enumerate(accents)]


FIGURES = {
    "Q": [(0, 0, 70), (4, 0, 44), (8, 0, 60), (12, 0, 44)],
    "E": _eighths(),
    "E_": _eighths() + [(15, 0, 48)],
    "S": [(0, 0, 92), (2, 0, 50), (4, 0, 76), (6, 0, 52), (7, 0, 42), (8, 0, 88), (10, 0, 50), (12, 0, 76), (14, 0, 56), (15, 0, 46)],
    "T": [(0, 0, 92), (1, 0, 48), (3, 0, 56), (4, 0, 76), (5, 0, 44), (7, 0, 50), (8, 0, 88), (9, 0, 48), (11, 0, 56), (12, 0, 76), (13, 0, 44), (15, 0, 50)],
    "WU": [(0, 0, 92), (2, 0, 52), (4, "t", 70), (6, "t", 52), (8, 7, 86), (10, 7, 50), (12, 10, 72), (14, 12, 60)],
    "WV": [(0, 0, 92), (2, 0, 50), (4, 7, 70), (6, 7, 50), (8, 12, 86), (10, 12, 52), (12, 7, 72), (14, "t", 56)],
    "F16": [(0, 0, 92), (2, 0, 50), (4, 0, 76), (6, 0, 52)] + [(t, 0, 80 if t == 8 else 40 + 4 * (t - 8)) for t in range(8, 16)],
    "M16": [(t, 12 if t in (7, 15) else 0, 94 if t == 0 else 86 if t == 8 else 72 if t in (3, 6, 11, 14) else 64 if t % 4 == 0 else 58 if t in (7, 15) else 44) for t in range(16)],
}


def figure(kind, third=3):
    return [(tick, third if offset == "t" else offset, vel) for tick, offset, vel in FIGURES[kind]]


# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- harmony
def candidates(pcs, low, high):
    return [p for p in range(low, high + 1) if p % 12 in pcs]


def voicing(pcs, low, high, count, previous=None, tight=False, wide=False):
    """``count`` pitches between ``low`` and ``high`` that cover as many of the pitch classes ``pcs`` as possible and move as little as possible from ``previous``
    (voice leading); with ``tight`` the chord is as close together as it can be (a cluster), with ``wide`` as far apart (an open chord)."""
    pcs = set(pcs)
    options = candidates(pcs, low, high)
    best, best_score = None, None
    for combo in itertools.combinations(options, count):
        covered = len({p % 12 for p in combo})
        movement = sum(abs(a - b) for a, b in zip(combo, previous)) if previous and len(previous) == count else 0
        span = combo[-1] - combo[0]
        score = (-covered, span if tight else (-span if wide else 0), movement, span)
        if best_score is None or score < best_score:
            best, best_score = combo, score
    return list(best)


def ladder(pcs, low, count):
    """The first ``count`` pitches from ``low`` up whose pitch class is in ``pcs`` (adjacent steps of a scale: a cluster)."""
    out, p = [], low
    while len(out) < count:
        if p % 12 in pcs:
            out.append(p)
        p += 1
    return out


def lerp(a, b, fraction):
    return a + (b - a) * fraction


def melody_events(text):
    """[(tick, midi, ticks)] of a bar written like 'C3:8 G3:8'."""
    return bar_notes(text, BAR)


# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- numbers
def section_of(score, tick):
    bar = tick // BAR
    for name, bars in score.sections:
        if score.starts[name] <= bar < score.starts[name] + bars:
            return name
    return None


def density_by_section(score, layers):
    result = []
    for name, bars in score.sections:
        start = score.starts[name] * BAR
        end = start + bars * BAR
        notes = sum(1 for layer in layers for tick in layer.notes if start <= tick < end)
        result.append((name, notes / ((end - start) / score.tps)))
    return result


def count_by_section(score, layers, names):
    """Notes of the named layers in every section, and per bar: [(section, notes, notes a bar)]."""
    result = []
    for name, bars in score.sections:
        start = score.starts[name] * BAR
        end = start + bars * BAR
        notes = sum(1 for layer in layers if layer.name in names for tick in layer.notes if start <= tick < end)
        result.append((name, notes, notes / bars))
    return result
