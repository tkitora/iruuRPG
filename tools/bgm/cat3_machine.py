"""Helpers of the class-3 boss songs, "inorganic fear and erosion" (reaper_*.nbs and circuit_*.nbs): a machine, an alien, a will-less curse.

What these songs share (and the drive songs of bossdrive.py do not): the bar is NOT 16 ticks (5/4 or 7/4), so the song is laid on a ``Grid`` that knows its own bar, the loop starts at tick 64
whatever the bar is (the bars before it are numbered -1, -2 ... and a bar that begins before tick 0 is simply clipped: the intro is the tail of a bar and whole bars), nothing is random (``Rng`` is a
fixed linear congruential generator), and silence is composed: ``Grid.mute`` cuts a window out of every layer, ``strike`` is the blow that ends it.
Everything is written by what SOUNDS (Layer.put_sound).
"""
from nbslib import *  # noqa: F401,F403

DRUM_KINDS = (BASEDRUM, SNARE, HAT)


class Rng:
    """A fixed generator (the same song every time)."""

    def __init__(self, seed):
        self.state = (seed * 2654435761 + 12345) & 0x7FFFFFFF

    def below(self, n):
        self.state = (self.state * 1103515245 + 12345) & 0x7FFFFFFF
        return (self.state >> 12) % n

    def chance(self, percent):
        return self.below(100) < percent

    def pick(self, items):
        return items[self.below(len(items))]


class Grid:
    """The song on a grid: ``bar`` ticks to the bar, ``bars`` bars in the loop, the loop begins at tick 64, the song ends where the loop would begin again."""

    def __init__(self, tps, bar, bars, specs):
        self.tps = tps
        self.bar = bar
        self.bars = bars
        self.length = 64 + bar * bars
        self.layers = {name: Layer(name, volume, pan) for name, (volume, pan) in specs.items()}

    def start(self, bar_index):
        """The tick where bar ``bar_index`` of the loop begins (negative: a bar of the intro)."""
        return 64 + bar_index * self.bar

    def put(self, name, tick, instrument, sounding, velocity, over=False):
        """A note (``sounding`` is ignored for the drums). Outside the song, or on a tick the layer already holds (unless ``over``), it is dropped; returns whether it was written."""
        if tick < 0 or tick >= self.length:
            return False
        velocity = int(round(velocity))
        if velocity < 1:
            return False
        velocity = min(100, velocity)
        layer = self.layers[name]
        if tick in layer.notes:
            if not over:
                return False
            del layer.notes[tick]
        if instrument in DRUM_KINDS:
            layer.put(tick, instrument, 66, velocity)
        else:
            layer.put_sound(tick, instrument, sounding, velocity)
        return True

    def drum(self, name, tick, instrument, velocity, over=False):
        return self.put(name, tick, instrument, 66, velocity, over)

    def restrike(self, name, instrument, sounding, start, length, every, v_from, v_to, over=False):
        """A held note: struck again every ``every`` ticks while the loudness moves from ``v_from`` to ``v_to``."""
        steps = max(1, (length - 1) // every)
        for index, offset in enumerate(range(0, length, every)):
            self.put(name, start + offset, instrument, sounding, v_from + (v_to - v_from) * index / steps, over)

    def stutter(self, name, instrument, sounding, tick, count, velocity, decay=0.72, step=1, over=False):
        """The same note struck again and again on consecutive ticks, quieter each time: a machine that cannot let go. Returns the tick after the last."""
        for index in range(count):
            self.put(name, tick + index * step, instrument, sounding, velocity * (decay ** index), over)
        return tick + count * step

    def clicks(self, name, instrument, sounding, end, gaps, v_from, v_to, over=False):
        """Clicks that come faster and faster: the last one is at ``end``, the one before it ``gaps[0]`` ticks earlier, and so on (so ``gaps`` runs from the end backwards: small first)."""
        ticks = [end]
        for gap in gaps:
            ticks.append(ticks[-1] - gap)
        ticks.reverse()
        for index, tick in enumerate(ticks):
            self.put(name, tick, instrument, sounding, v_from + (v_to - v_from) * index / max(1, len(ticks) - 1), over)

    def mute(self, start, end, names=None, keep=()):
        """Silence: every note of ``names`` (all layers when None) from ``start`` up to but not including ``end`` is taken away."""
        for name, layer in self.layers.items():
            if (names is not None and name not in names) or name in keep:
                continue
            for tick in [t for t in layer.notes if start <= t < end]:
                del layer.notes[tick]

    def finish(self, limit=8):
        layers = [layer for layer in self.layers.values() if layer.notes]
        removed = limit_polyphony(layers, limit)
        layers = [layer for layer in layers if layer.notes]
        return layers, removed


def section_of(sections, bar_index):
    """(name, bar within the section, bars of the section) for a bar of the loop; ``sections`` is [(name, first bar, bars)]."""
    for name, first, count in sections:
        if first <= bar_index < first + count:
            return name, bar_index - first, count
    raise ValueError(bar_index)


def triangle(index, period, amplitude):
    """0 .. amplitude .. 0 over ``period`` bars (the wobble of an erosion that gains and loses)."""
    phase = (index % period) / period
    return int(round(amplitude * (1 - abs(2 * phase - 1))))


def write_song(grid, path, title, description, density_sections=None, bass_layers=(), keep_layers=()):
    """Writes the song and prints the numbers that show its shape: notes a second by section and the bass notes a bar (intro < A < B < PEAK).
    ``keep_layers`` (the held tone lines) are counted before and after the thinning out, to show that none of their notes was lost."""
    before = {name: len(grid.layers[name].notes) for name in keep_layers}
    layers, removed = grid.finish(8)
    write_nbs(path, layers, grid.length, grid.tps, title, description, 4)
    print(f"-- {removed} notes thinned out to keep 8 at most")
    for name in keep_layers:
        print(f"   held line {name}: {before[name]} notes before thinning, {len(grid.layers[name].notes)} after")
    report(path, layers, grid.length, grid.tps)
    if density_sections:
        print("   notes a second by section: " + ", ".join(f"{name} {sum(1 for layer in layers for t in layer.notes if a <= t < b) / ((b - a) / grid.tps):.1f}" for name, a, b in density_sections))
        if bass_layers:
            print("   bass notes a bar by section: " + ", ".join(
                f"{name} {sum(1 for layer in layers if layer.name in bass_layers for t in layer.notes if a <= t < b) / max(1e-9, (b - a) / grid.bar):.1f}" for name, a, b in density_sections))
    return layers


# ---- the rhythm of the bass: the song tick is a sixteenth, a beat is 4 ticks (the base tempo), so
#      "q" = quarters (every 4 ticks), "e" = eighths (every 2), "s" = the sixteenth machine gun (every tick),
#      "m" = eighths with sudden sixteenth double hits and, in every second bar, a run of 3-tick steps (a triplet-like figure that crosses the beat)
def gun_ticks(mode, bar, k, p, rng):
    """The ticks of the bar (0 .. bar - 1) where the bass strikes. ``p`` (0 .. 1) is how restless the "m" bars are."""
    beats = bar // 4
    if mode == "n":
        return []
    if mode == "q":
        return list(range(0, bar, 4))
    if mode == "e":
        return list(range(0, bar, 2))
    if mode == "s":
        return list(range(bar))
    ticks = []
    for b in range(beats):
        t = 4 * b
        r = rng.below(100)
        if r < 10 + 35 * p:
            ticks += [t, t + 1, t + 2]
        elif r < 18 + 50 * p:
            ticks += [t, t + 2, t + 3]
        elif r < 22 + 58 * p:
            ticks += [t, t + 1, t + 2, t + 3]
        else:
            ticks += [t, t + 2]
    if k % 2 == 1 and p >= 0.25:
        t0 = 0 if k % 4 == 1 else bar - 12
        ticks = [t for t in ticks if not t0 <= t < t0 + 12] + [t0, t0 + 3, t0 + 6, t0 + 9]
    return sorted(set(ticks))


def fit(instrument, pitch):
    """The octave of ``pitch`` that the instrument can sound."""
    low = BASE[instrument]
    while pitch > low + 24:
        pitch -= 12
    while pitch < low:
        pitch += 12
    return pitch


def partner(instrument, pitch, interval):
    """``pitch`` + ``interval``; a small interval that would leave the range of the instrument turns the other way round."""
    other = pitch + interval
    if other > BASE[instrument] + 24 and abs(interval) <= 2:
        other = pitch - interval
    return fit(instrument, other)


def held_line(grid, voices, line, start, v_from, v_to, every=4):
    """A line of LONG notes made of re-struck notes. ``line`` is [(pitch, ticks)], ``voices`` is [(layer, instrument, interval, offset, level)]: every voice strikes its note every ``every`` ticks
    (from tick ``offset`` of the note), so the voices take turns on different ticks (one note at a tick for the whole line); the loudness moves from ``v_from`` to ``v_to`` over each note
    (a new note is struck harder). Returns the tick after the last note."""
    tick = start
    for pitch, length in line:
        for name, instrument, interval, offset, level in voices:
            sounding = pitch if interval == 0 else partner(instrument, pitch, interval)
            for o in range(offset, length, every):
                v = v_from + (v_to - v_from) * o / max(1, length - 1)
                if o < every:
                    v += 8
                grid.put(name, tick + o, instrument, fit(instrument, sounding), v * level)
        tick += length
    return tick
