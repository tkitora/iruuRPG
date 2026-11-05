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


# ---------------------------------------------------------------------------------------------------------------------------------------------
# t276: the "16th-note machine gun" rhythm structure shared by the class 4 and class 5 songs.
# The melody keeps the base tempo; the bass is subdivided: level 0 quarters (intro), 1 eighths (A), 2 eighths that sway (A'), 3 eighths with sudden sixteenth
# double hits and triplet figures (B), 4 full sixteenths (PEAK). A bar is 16 ticks (a sixteenth each, 4 ticks a beat).
# ---------------------------------------------------------------------------------------------------------------------------------------------
GRIDS = {"SW": (0, 3, 4, 7, 8, 11, 12, 15),         # swung eighths: long-short = 3 + 1 ticks (a triplet beat rounded to the grid)
         "ST": (0, 2, 4, 6, 8, 10, 12, 14),         # straight eighths
         "TR": (0, 3, 5, 8, 11, 13, 15)}            # a triplet figure over two beats: 8 ticks in three (0, 2.67, 5.33) twice


def hit_ticks(level, feel, seed):
    """The ticks (0..15) of a bar where the bass strikes. ``seed`` makes level 3 irregular but deterministic."""
    if level <= 0:
        return [0, 4, 8, 12]
    grid = GRIDS["ST" if feel in ("HT", "SB", "MR", "DB") else feel]
    if level in (1, 2):
        return list(grid)
    if level >= 4:
        return list(range(16))
    result = set()
    for half in (0, 8):
        choice = hash_(seed, half, 41) % 5
        part = [t - half for t in grid if half <= t < half + 8]
        if choice in (0, 1):                                # sixteenth double hits after two of the eighths
            for index in (hash_(seed, half, 7) % len(part), (hash_(seed, half, 9) % len(part))):
                if part[index] + 1 not in part and part[index] + 1 < 8:
                    part.append(part[index] + 1)
            if len(part) < 6:
                part.append(part[-1] + 1 if part[-1] + 1 < 8 else part[-1] - 1)
        elif choice == 2:                                   # a double after every other eighth
            part = part + [t + 1 for t in part[::2] if t + 1 < 8 and t + 1 not in part]
        elif choice == 3:                                   # the last eighth becomes a pair of sixteenths
            last = part[-1]
            part = part[:-1] + [last, last + 1] if last + 1 < 8 else part + [last - 1]
        else:                                               # the triplet figure (3 + 3 + 2) with a run of sixteenths at the end
            part = [0, 3, 5, 7] + ([6] if hash_(seed, half, 3) % 2 else [])
        result.update(half + t for t in set(part))
    return sorted(result)


def bass_velocity(tick, level):
    """Accents of the machine gun: the first sixteenth of a beat is heavy, the third medium, the in-betweens ghost notes ('zuku-zuku')."""
    if level >= 4:
        return (100, 58, 86, 54)[tick % 4] if tick % 8 else 100
    if tick % 8 == 0:
        return 100
    if tick % 4 == 0:
        return 88 if level >= 2 else 80
    if tick % 2 == 0:
        return 76 if level >= 2 else 66
    return 56


def sustain(b, role, tick, instrument, pitch, length, v_from, v_to, every=4, once=True):
    """A held note made of re-strikes (a note block dies away at once): from ``tick`` for ``length`` ticks, struck every ``every`` ticks, the loudness moving from v_from to v_to."""
    steps = max(1, (length - 1) // every)
    for index, t in enumerate(range(0, length, every)):
        b.note(role, tick + t, instrument, pitch, v_from + (v_to - v_from) * index / steps, once=once)
