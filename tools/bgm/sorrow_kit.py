"""Helpers of the two 'sorrow' boss pieces (necromancer / waltz): a Score in absolute bars, a singing lead (long notes struck again and again like a vibrato),
patterns for bass, guitar, harp and drums, and the writing of the two phases. Everything is written by what SOUNDS (put_sound) and nothing is random.

A Score counts bars from the first bar of the intro. The first 64 ticks are the intro (``offset`` silent ticks, then ``intro`` whole bars) and the loop starts at tick 64.
A melody is a string of tokens ``Name:ticks`` ('r' is a rest) that must fill a bar; a ``^`` before the name is an accent and a ``_`` a soft note.
"""
from dataclasses import dataclass

from bossframe import chord, tones_in
from nbslib import *  # noqa: F401,F403

VOL = {"lead": 100, "dbl": 80, "oct": 70, "low": 80, "echo": 55, "bass": 100, "drone": 70, "kick": 100, "snare": 95, "hat": 60, "tom": 90,
       "gtr": 75, "arp": 62, "chord": 70, "pad": 55, "stab": 75, "run": 70, "bell": 45}
PAN = {"lead": 100, "dbl": 95, "oct": 105, "echo": 125, "arp": 80, "gtr": 85, "pad": 115, "chord": 90, "hat": 115, "snare": 105}


def inst_range(instrument):
    return BASE[instrument], BASE[instrument] + 24


def fit(instrument, sounding):
    low, high = inst_range(instrument)
    while sounding > high:
        sounding -= 12
    while sounding < low:
        sounding += 12
    return sounding


def clamp(value):
    return max(1, min(100, int(round(value))))


class Score:
    def __init__(self, tps, bar, intro_bars, body_bars):
        self.tps, self.bar, self.intro, self.body = tps, bar, intro_bars, body_bars
        self.offset = 64 - intro_bars * bar
        assert self.offset >= 0
        self.length = 64 + body_bars * bar
        self.roles = {}
        self.layers = []

    def tick(self, bar, t=0):
        return self.offset + bar * self.bar + t

    def b(self, j):
        """absolute bar of the body bar j (the loop starts at the body bar 0)."""
        return self.intro + j

    def _layer(self, role, tick):
        group = self.roles.setdefault(role, [])
        for layer in group:
            if tick not in layer.notes:
                return layer
        name = role if not group else f"{role} {len(group) + 1}"
        layer = Layer(name, VOL.get(role, 80), PAN.get(role, 100))
        group.append(layer)
        self.layers.append(layer)
        return layer

    def put(self, role, tick, instrument, sounding, vel):
        """A pitched note; dropped when it falls outside the song or outside what the instrument can sound."""
        if tick < 0 or tick >= self.length:
            return False
        low, high = inst_range(instrument)
        if not low <= sounding <= high:
            return False
        if instrument in (BELL, CHIME) and not 90 <= sounding <= 99:
            return False
        self._layer(role, tick).put_sound(tick, instrument, sounding, clamp(vel))
        return True

    def drum(self, role, tick, instrument, vel, pitch=66):
        if tick < 0 or tick >= self.length:
            return False
        self._layer(role, tick).put(tick, instrument, pitch, clamp(vel))
        return True


def hold_velocities(length, every, v0, style):
    count = len(range(0, length, every))
    out = []
    for i in range(count):
        if i == 0:
            out.append(v0)
        elif style == "cry":       # struck again and again, the loudness breathing in and out and growing towards the end of a long note
            grow = i / max(1, count - 1)
            out.append(v0 * (0.52 + 0.2 * grow + (0.10 if i % 2 == 0 else -0.04)))
        elif style == "fade":
            out.append(v0 * (0.62 ** i))
        elif style == "swell":
            out.append(v0 * (0.5 + 0.5 * i / max(1, count - 1)))
        else:                      # flat
            out.append(v0 * 0.55)
    return out


@dataclass
class Voice:
    role: str
    instrument: int
    off: int = 0           # octaves (in semitones) from the written melody
    gain: float = 1.0
    style: str = "cry"     # how a long note is held: cry / fade / flat / none (a single strike)
    every: int = 4
    delay: int = 0         # ticks late (an echo)
    octave_fit: bool = False   # True: a note that the instrument cannot sound is moved by octaves; False: it is left out
    low: int = 0           # the voice plays only notes (as written) from this one ...
    high: int = 200        # ... up to this one


def play_voice(score, voice, tick, pitch, length, vel):
    if not voice.low <= pitch <= voice.high:
        return
    sounding = pitch + voice.off
    if voice.octave_fit:
        sounding = fit(voice.instrument, sounding)
    tick += voice.delay
    if voice.style == "none" or length < 2 * voice.every:
        score.put(voice.role, tick, voice.instrument, sounding, vel * voice.gain)
        return
    for index, velocity in enumerate(hold_velocities(length, voice.every, vel * voice.gain, voice.style)):
        score.put(voice.role, tick + index * voice.every, voice.instrument, sounding, velocity)


def parse(text, bar):
    out = []
    total = 0
    for token in text.split():
        mod = 0
        while token[0] in "^_":
            mod += 10 if token[0] == "^" else -14
            token = token[1:]
        name, length = token.split(":")
        length = int(length)
        out.append((None if name == "r" else midi(name), length, mod))
        total += length
    assert total == bar, f"'{text}' has {total} ticks, not {bar}"
    return out


def sing(score, bar, text, voices, transpose=0, vel=88, level=1.0, start=0, cut=False):
    """Plays one bar of melody with every voice. ``start`` shifts the whole bar later by that many ticks (the notes then run into the next bar; with ``cut`` the notes
    that would begin after the end of the bar are left out instead)."""
    pos = 0
    for pitch, length, mod in parse(text, score.bar):
        if pitch is not None and not (cut and pos + start >= score.bar):
            tick = score.tick(bar, pos + start)
            first = 0 if pos == 0 else 1
            for voice in voices:
                play_voice(score, voice, tick, pitch + transpose, length, (vel + mod - 4 * first) * level)
        pos += length


def root_of(name, low=30, high=41):
    """The bass note of a chord: its root between F#1 and F2 (the lowest the bass plays, so that it weighs)."""
    pc, _ = chord(name)
    n = 24 + pc
    while n < low:
        n += 12
    while n > high:
        n -= 12
    return n


def chords_of(spec):
    return spec.split("|")


def chord_at(spec, t, bar):
    parts = chords_of(spec)
    return parts[min(len(parts) - 1, t * len(parts) // bar)]


BASS_KIND = {"r": 0, "f": 7, "o": 12, "a": 2, "u": -5, "t": 3, "s": 5, "b": 10}


def bass(score, bar, spec, pat, level=1.0, role="bass", instrument=BASS, high=46):
    for t, kind, vel in pat:
        name = chord_at(spec, t, score.bar)
        n = kind if isinstance(kind, int) else root_of(name) + BASS_KIND[kind]
        while n > high:
            n -= 12
        score.put(role, score.tick(bar, t), instrument, n, vel * level)


def notes_from(spec, pat, role, instrument, low, high, score, bar, level=1.0, shift=0):
    """pat: (tick, index into the chord tones from low up, velocity); an index past the top goes up an octave."""
    for t, index, vel in pat:
        name = chord_at(spec, t, score.bar)
        tones = tones_in(name, low, high)
        n = tones[index % len(tones)] + 12 * (index // len(tones)) + shift
        score.put(role, score.tick(bar, t), instrument, n, vel * level)


def stab(score, bar, t, spec, role, instrument, low, high, vel, count=3, length=0, every=8, style="fade", shift=0):
    name = chord_at(spec, t, score.bar)
    tones = tones_in(name, low, high)
    if len(tones) > count:
        start = (len(tones) - count) // 2
        tones = tones[start:start + count]
    for n in tones:
        play_voice(score, Voice(role, instrument, 0, 1.0, style if length else "none", every), score.tick(bar, t), n + shift, length, vel)


def drums(score, bar, pat, level=1.0):
    """pat: {'kick': [(tick, vel)], 'snare': [...], 'hat': [...], 'tom': [(tick, vel, pitch)]}"""
    base = score.tick(bar)
    for t, v in pat.get("kick", []):
        score.drum("kick", base + t, BASEDRUM, v * level)
    for t, v in pat.get("snare", []):
        score.drum("snare", base + t, SNARE, v * level)
    for t, v in pat.get("hat", []):
        score.drum("hat", base + t, HAT, v * level)
    for t, v, p in pat.get("tom", []):
        score.drum("tom", base + t, BASEDRUM, v * level, p)


def roll(score, t0, t1, step, v0, v1, role="snare", instrument=SNARE, pitch=66):
    """A roll from tick t0 to t1 (absolute), the loudness rising or falling from v0 to v1."""
    ticks = list(range(t0, t1, step))
    for i, t in enumerate(ticks):
        score.drum(role, t, instrument, v0 + (v1 - v0) * i / max(1, len(ticks) - 1), pitch)


def held(score, role, instrument, tick, sounding, length, v0, v1, every=4):
    """A long note struck again every ``every`` ticks while the loudness moves from v0 to v1."""
    steps = max(1, (length - 1) // every)
    for i, t in enumerate(range(0, length, every)):
        score.put(role, tick + t, instrument, sounding, v0 + (v1 - v0) * i / steps)


def run(score, role, instrument, tick, notes, step, v0, v1):
    for i, n in enumerate(notes):
        score.put(role, tick + i * step, instrument, n, v0 + (v1 - v0) * i / max(1, len(notes) - 1))


def scale_run(low, high, pcs):
    """All notes from low to high (MIDI) whose pitch class is in pcs, ascending."""
    return [n for n in range(low, high + 1) if n % 12 in pcs]


def density(score, sections):
    """[(name, notes a second)] for sections given as (name, first body bar, bars)."""
    result = []
    for name, j, bars in sections:
        start, end = score.tick(score.b(j)), score.tick(score.b(j + bars))
        count = sum(1 for layer in score.layers for tick in layer.notes if start <= tick < end)
        result.append((name, count / ((end - start) / score.tps)))
    return result


def finish(score, path, title, description, sections):
    removed = limit_polyphony(score.layers, 8)
    write_nbs(path, score.layers, score.length, score.tps, title, description, 4 if score.bar == 16 else 4)
    print(f"-- {title}: {removed} notes thinned out to keep 8 at most")
    report(path, score.layers, score.length, score.tps)
    print("   notes a second by section: " + ", ".join(f"{name} {count:.1f}" for name, count in density(score, sections)))
