"""Helpers for the songs of class 1 (overwhelming majesty and despair): a Score that collects layers, re-struck pads, held melody notes, chord voicings and drums.

Everything is written by what SOUNDS (nbslib.put_sound). A bar is 16 ticks (the engine's phase-switch line), the intro is 4 bars = 64 ticks, so the loop point is tick 64.
Nothing here is random. Used by make_atlas_boss3_bgm.py (the stone giant) and make_unkeyed_bgm.py (the god with no key).
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
        for name, volume, pan in (("bd", 100, 100), ("sn", 80, 105)):   # the drums are always the first layers
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

    def hold(self, lname, inst, snd, tick, length, v0, v1, every=4):
        """A note held for ``length`` ticks by striking it again every ``every`` ticks, the loudness moving from v0 to v1."""
        steps = max(1, (length - 1) // every)
        for index, offset in enumerate(range(0, length, every)):
            self.put(lname, tick + offset, inst, snd, v0 + (v1 - v0) * index / steps)

    def drum(self, tick, kind, vel):
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


def density_by_section(score, layers):
    result = []
    for name, bars in score.sections:
        start = score.starts[name] * BAR
        end = start + bars * BAR
        notes = sum(1 for layer in layers for tick in layer.notes if start <= tick < end)
        result.append((name, notes / ((end - start) / score.tps)))
    return result
