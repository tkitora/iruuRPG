"""Helpers shared by the class 4 (carnival of madness) and class 5 (dry death match) boss songs.

Not a framework: a Builder that hands out layers by ROLE (a role takes a new layer whenever its notes would collide, so a chord is just three calls),
a few pitch helpers (everything is written by what SOUNDS) and the writer. Nothing here is random: every 'chance' is an arithmetic hash of the bar and the tick.
"""
from nbslib import *  # noqa: F401,F403

RANGE = {HARP: (54, 78), BASS: (30, 54), GUITAR: (42, 66), FLUTE: (78, 102), BELL: (90, 99), CHIME: (90, 99), XYLOPHONE: (90, 114), IRON_XYLOPHONE: (54, 78),
         COW_BELL: (66, 90), DIDGERIDOO: (30, 54), BIT: (54, 78), BANJO: (54, 78), PLING: (54, 78)}
DRUMS = (BASEDRUM, SNARE, HAT)


def clamp(value, low, high):
    return max(low, min(high, value))


def fold_into(instrument, pitch):
    """The note moved by octaves into the real range of the instrument."""
    low, high = RANGE[instrument]
    while pitch > high:
        pitch -= 12
    while pitch < low:
        pitch += 12
    return pitch


def near(pitch_class, target, instrument):
    """The note of this pitch class nearest to ``target`` inside the range of the instrument."""
    low, high = RANGE[instrument]
    best = None
    for candidate in range(low, high + 1):
        if candidate % 12 == pitch_class % 12 and (best is None or abs(candidate - target) < abs(best - target)):
            best = candidate
    return best


def hash_(*values):
    """A small deterministic hash (not random): the same input is always the same number."""
    h = 2166136261
    for value in values:
        h = ((h ^ (value & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return h ^ (h >> 16)


class Builder:
    """Layers by role. ``roles`` is {role: (volume, pan)}; the order of the dict is the order of the layers in the file."""

    def __init__(self, length, roles):
        self.length = length
        self.roles = roles
        self.layers = {}

    def _free(self, role, tick):
        found = self.layers.setdefault(role, [])
        for layer in found:
            if tick not in layer.notes:
                return layer
        volume, pan = self.roles[role]
        layer = Layer(role if not found else f"{role}{len(found) + 1}", volume, pan)
        found.append(layer)
        return layer

    def taken(self, role, tick):
        return any(int(tick) in layer.notes for layer in self.layers.get(role, []))

    def note(self, role, tick, instrument, sounding, vel, fold=True, once=False):
        """A pitched note by what it sounds as; folded by octaves into the instrument's range unless ``fold`` is False (then a note out of range is dropped).
        ``once``: nothing happens when the role already has a note at this tick."""
        tick = int(tick)
        if tick < 0 or tick >= self.length or vel < 1 or (once and self.taken(role, tick)):
            return False
        low, high = RANGE[instrument]
        if not low <= sounding <= high:
            if not fold:
                return False
            sounding = fold_into(instrument, sounding)
        self._free(role, tick).put_sound(tick, instrument, int(sounding), clamp(int(round(vel)), 1, 100))
        return True

    def drum(self, role, tick, instrument, vel, key=66, once=False):
        tick = int(tick)
        if tick < 0 or tick >= self.length or vel < 1 or (once and self.taken(role, tick)):
            return False
        self._free(role, tick).put(tick, instrument, key, clamp(int(round(vel)), 1, 100))
        return True

    def all_layers(self):
        return [layer for role in self.roles for layer in self.layers.get(role, [])]


def sections_density(layers, length, tps, edges):
    """[(name, notes a second)] for ``edges`` = [(name, start tick, end tick)]."""
    result = []
    for name, start, end in edges:
        count = sum(1 for layer in layers for tick in layer.notes if start <= tick < end)
        result.append((name, count / ((end - start) / tps)))
    return result


def finish(path, builder, tps, title, description, polyphony=8):
    """Thins the song to ``polyphony`` notes at one tick, writes it (loop from tick 64) and prints the usual report; returns the layers."""
    layers = builder.all_layers()
    removed = limit_polyphony(layers, polyphony)
    layers = [layer for layer in layers if layer.notes]
    write_nbs(path, layers, builder.length, tps, title, description, 4)
    print(f"-- {removed} notes thinned out to keep {polyphony} at most")
    report(path, layers, builder.length, tps)
    return layers
