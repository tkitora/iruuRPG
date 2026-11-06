"""Small helpers of the t281 rebuild of Boss002 / Boss006 / Boss007 (tools, not music): a per-second note limit, rolls, tom rolls, and the writer.

The limit of the rebuild: at most 8 notes at one tick (nbslib.limit_polyphony) and at most CAP notes in any second of the song (the busiest second counted the way nbslib.statistics counts it,
``int(tick / ticks_per_second)``): the quietest notes of the DECORATION layers (hat, guitar chug, extra power-chord notes, stabs, chip) are taken away first, the tune, the bass, the didgeridoo
and the drums last.
"""
from cat45 import *  # noqa: F401,F403

CAP = 40
DECOR = ("hat", "chug", "power2", "stab", "chip", "arp", "harm", "cow", "dbl", "guitar2")


def thin_per_second(layers, tps, cap=CAP, decor=DECOR):
    """A SAFETY, not a design tool (t283): the busiest second counted the way bossness.py counts it (a sliding window of round(tps) ticks) is brought down to ``cap`` by taking away the quietest
    notes of the decoration layers first, the others last."""
    window = max(1, int(round(tps)))
    by_tick = {}
    for layer in layers:
        for tick, (_, _, vel) in layer.notes.items():
            by_tick.setdefault(tick, []).append((layer, vel * layer.volume))
    removed = 0
    ticks = sorted(by_tick)
    for end in ticks:
        window_notes = [(layer, t, loud) for t in range(end - window + 1, end + 1) for layer, loud in by_tick.get(t, []) if t in layer.notes]
        excess = len(window_notes) - cap
        if excess <= 0:
            continue
        for rank in (decor, None):
            pool = [n for n in window_notes if rank is None or n[0].name in rank or n[0].name.rstrip("0123456789") in rank]
            pool.sort(key=lambda n: n[2])
            for layer, t, _ in pool:
                if excess <= 0:
                    break
                if t in layer.notes:
                    del layer.notes[t]
                    removed += 1
                    excess -= 1
            if excess <= 0:
                break
    return removed


def finish281(path, builder, tps, title, description, cap=CAP, loop_bar=4):
    layers = builder.all_layers()
    r1 = limit_polyphony(layers, 8)
    r2 = thin_per_second(layers, tps, cap)
    layers = [layer for layer in layers if layer.notes]
    write_nbs(path, layers, builder.length, tps, title, description, loop_bar)
    print(f"-- {r1} notes thinned to keep 8 at one tick, {r2} to keep {cap} a second")
    report(path, layers, builder.length, tps)
    return layers


def snare_roll(b, t0, start, step, v_from, v_to, last=16, role="snare"):
    ticks = list(range(start, last, step))
    for index, tick in enumerate(ticks):
        v = int(v_from + (v_to - v_from) * index / max(1, len(ticks) - 1))
        b.drum(role, t0 + tick, SNARE, v, once=True)


def tom_roll(b, t0, start, end, ascending=False, vel_from=40, vel_to=100):
    """A tom roll: the bass drum struck on falling (or rising) pitches (the key of a drum note is its pitch), a snare accent on the beats."""
    count = end - start
    for index, tick in enumerate(range(start, end)):
        frac = index / max(1, count - 1)
        k = int(round(78 - 24 * (frac if not ascending else 1 - frac)))
        b.drum("tom", t0 + tick, BASEDRUM, int(vel_from + (vel_to - vel_from) * frac), key=k, once=True)
        if tick % 4 == 0:
            b.drum("snare", t0 + tick, SNARE, int(vel_from + (vel_to - vel_from) * frac) - 10, once=True)


def hold(b, role, instrument, tick, pitch, length, vel, rise=8, every=4):
    """``every``: the re-strike distance in ticks - at most 0.35 s (4 ticks at 12 ticks a second, 3 at 11); the strikes never get quieter than the first one (t283)."""
    """A note held by re-striking: a short note is struck once."""
    if length >= every + 1:
        sustain(b, role, tick, instrument, pitch, length, vel, min(100, vel + rise), every=every)
    else:
        b.note(role, tick, instrument, pitch, vel, once=True)
