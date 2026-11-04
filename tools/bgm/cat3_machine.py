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


def write_song(grid, path, title, description, density_sections=None):
    layers, removed = grid.finish(8)
    write_nbs(path, layers, grid.length, grid.tps, title, description, 4)
    print(f"-- {removed} notes thinned out to keep 8 at most")
    report(path, layers, grid.length, grid.tps)
    if density_sections:
        print("   notes a second by section: " + ", ".join(f"{name} {sum(1 for layer in layers for t in layer.notes if a <= t < b) / ((b - a) / grid.tps):.1f}" for name, a, b in density_sections))
    return layers
