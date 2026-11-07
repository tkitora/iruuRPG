#!/usr/bin/env python3
"""Measures whether a boss song really CONTAINS what the two structure guides ask for (t290): the 楽曲構成設計書 (four styles, tension gimmick every 30-45 s) and the BPM 設計ガイド (the 4th -> 8th -> 16th ladder).

    python3 tools/bgm/guidecheck.py src/main/resources/bgm/necromancer_phase1.nbs [more.nbs ...]    # one block per song, the failed checks marked with !
    python3 tools/bgm/guidecheck.py --events src/main/resources/bgm/necromancer_phase1.nbs           # and the detected time line

bossness.py says the song is loud and does not stop; this says the guide's content is in it. Every check is read from the notes, not from what the script claims. The checks:
  all styles
    gap      the longest time between two tension gimmicks (bass released from the root, break, half/double-time drums, mode flip / transposition, melody taken away,
             melody jumping an octave, melody halved), the start counted as one                                                          <= 45 s   (checklist: one every 30-45 s)
    root     the longest stretch the bass plays the same pitch again and again                                                          <= 20 s   (checklist: bass root)
    ladder   the bass steps up: a stretch of quarter-ish notes (<= 1.6 a beat) in the first 20 s, eighths (1.7-2.5) for >= 12 s, then 16ths (>= 3.3 a beat) for >= 10 s,
             the 16ths coming after the eighths and in the second half; and 16th doubles / triplets mixed into the eighths somewhere                      (BPM guide section 4)
    long     where the bass runs in 16ths the tune moves slowly: <= 1.4 new tune notes a beat on average, and fewer than a third of the bass rate        (BPM guide: long-note melody over the machine-gun bass)
  style 1 (150-160, fast battle)   drumless intro (drums in the first 12 s <= 60 % of the song mean) / B: bass freed and tune an octave up / break / chorus: minor -> major + a 3rd above / finale +2 semitones
  style 2 (120-130, multi-stage)   irregular start (dissonant 2nd/tritone share >= 8 % in the first 30 s) / double-time snare / lost melody (>= 15 s without a tune, bass + staccato) / sacred long chords / climax
  style 3 (170-180, symphonic)     opening at full force (tune >= 85 in the first 8 s) / B: tune halved while the drums stay / break / chorus: minor -> major, top note >= F#5 / outro tom fill
  style 4 (165, wafu)              waterfall arpeggio intro (>= 5 notes a second, falling) / pentatonic A, bass root-fifth / half-time / build-up (no tune, drums rise) / chorus +3 semitones and the tune up
Exit code 1 when a check fails.
"""
import sys
from collections import defaultdict

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from check_song import read, sounding  # noqa: E402
import retime  # noqa: E402

TYPES = {"atlas_vindicator": 1, "necromancer": 1, "waltz": 1, "colossus": 2, "reaper": 2, "unkeyed": 2, "circuit": 2, "broodmother": 3, "mirror": 3, "atlas_vindicator_2": 4}
TUNE = (0, 5, 6, 9, 10, 13, 14, 15)       # harp guitar flute xylophone iron-xylophone bit banjo pling
LOW = (1, 12)
KICK, SNARE, HAT = 2, 3, 4
DECOR = (7, 8, 11)
STACCATO = (9, 10, 11, 4)                   # xylophone (bone) / iron xylophone (anvil) / cow bell / click

MAJOR = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
MINOR = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]


def load(path):
    song = read(path)
    layers = retime.read_all(path)["layers"]
    song["notes"] = [(t, l, i, k, v * layers[l][2] / 100.0) for t, l, i, k, v in song["notes"]]
    return song


def style_of(path):
    name = path.rsplit("/", 1)[-1].rsplit(".", 1)[0]
    for suffix in ("_phase1", "_phase2"):
        if name.endswith(suffix):
            name = name[:-len(suffix)]
    return TYPES.get(name)


class Song:
    def __init__(self, path):
        s = load(path)
        self.path, self.tps, self.length, self.notes = path, s["tps"], s["length"], s["notes"]
        self.seconds = self.length / self.tps
        self.style = style_of(path)
        by_tick = defaultdict(list)
        for n in self.notes:
            by_tick[n[0]].append(n)
        self.by_tick = by_tick

    def sec(self, tick):
        return tick / self.tps

    def tick(self, seconds):
        return int(round(seconds * self.tps))

    def in_range(self, a, b):
        return [n for t in range(max(0, a), min(self.length, b)) for n in self.by_tick.get(t, ())]

    def window(self, a_s, b_s):
        return self.in_range(self.tick(a_s), self.tick(b_s))

    def tune_line(self):
        """The top TUNE note of every tick that has one: [(tick, sounding pitch, velocity)]."""
        line = []
        for t in sorted(self.by_tick):
            tunes = [(sounding(n[2], n[3]), n[4]) for n in self.by_tick[t] if n[2] in TUNE]
            if tunes:
                line.append((t,) + max(tunes))
        return line

    def new_tune_notes(self, a_s, b_s):
        """Tune notes that are a NEW pitch (the same pitch struck again within 0.5 s is a sustain, not a note)."""
        out, last, last_t = [], None, -99
        for t, p, v in self.tune_line():
            if t < self.tick(a_s) or t >= self.tick(b_s):
                continue
            if p != last or (t - last_t) / self.tps > 0.5:
                out.append((t, p, v))
            last, last_t = p, t
        return out


def best_key(notes):
    hist = [0.0] * 12
    for n in notes:
        if n[2] in (2, 3, 4):
            continue
        p = sounding(n[2], n[3])
        if p is not None:
            hist[p % 12] += n[4] * (2.0 if n[2] in LOW else 1.0)
    if sum(hist) == 0:
        return None
    best = None
    for tonic in range(12):
        rot = [hist[(tonic + i) % 12] for i in range(12)]
        for mode, prof in (("maj", MAJOR), ("min", MINOR)):
            mean_h, mean_p = sum(rot) / 12, sum(prof) / 12
            num = sum((a - mean_h) * (b - mean_p) for a, b in zip(rot, prof))
            den = (sum((a - mean_h) ** 2 for a in rot) * sum((b - mean_p) ** 2 for b in prof)) ** 0.5
            r = num / den if den else 0.0
            if best is None or r > best[0]:
                best = (r, tonic, mode)
    return best[1], best[2]


def bass_onsets_per_beat(song, a_s, b_s):
    ticks = {n[0] for n in song.window(a_s, b_s) if n[2] in LOW}
    beats = (b_s - a_s) * song.tps / 4.0
    return len(ticks) / beats if beats else 0.0


def drum_per_beat(song, a_s, b_s, instruments=(KICK, SNARE)):
    ticks = [n for n in song.window(a_s, b_s) if n[2] in instruments]
    beats = (b_s - a_s) * song.tps / 4.0
    return len(ticks) / beats if beats else 0.0


def windows(song, size=10.0):
    return [(i * size, min(song.seconds, (i + 1) * size)) for i in range(int(song.seconds // size) + (1 if song.seconds % size > 2 else 0))]


def bass_run(song):
    """Longest time the bass keeps one pitch (consecutive bass notes of the same sounding pitch)."""
    notes = sorted((n for n in song.notes if n[2] in LOW), key=lambda n: n[0])
    best = 0.0
    run_start, last = None, None
    for n in notes:
        p = sounding(n[2], n[3])
        if p == last:
            best = max(best, song.sec(n[0] - run_start))
        else:
            run_start, last = n[0], p
    return best


def events(song):
    """The tension gimmicks found in the notes: [(seconds, kind)]."""
    found = []
    ws = windows(song, 10.0)
    # bass released from the root: a window pinned on one pitch, then one that moves
    pinned = []
    for a, b in ws:
        pitches = [sounding(n[2], n[3]) for n in song.window(a, b) if n[2] in LOW]
        if not pitches:
            pinned.append(None)
            continue
        top = max(pitches.count(p) for p in set(pitches)) / len(pitches)
        pinned.append((top, max(pitches) - min(pitches)))
    for i in range(1, len(ws)):
        if pinned[i - 1] and pinned[i] and pinned[i - 1][0] >= 0.55 and pinned[i][0] <= 0.42 and pinned[i][1] >= 5:
            found.append((ws[i][0], "bass released"))
    # half / double time: the snare + kick rate changes by a lot between neighbouring windows
    rate = [drum_per_beat(song, a, b) for a, b in ws]
    for i in range(1, len(ws)):
        if rate[i - 1] > 0.2 and rate[i] > 0.2 and (rate[i] / rate[i - 1] >= 1.7 or rate[i] / rate[i - 1] <= 0.58):
            found.append((ws[i][0], "half/double-time drums"))
    # mode flip / transposition: the best-fit key changes and stays
    keys = [best_key(song.window(a, b)) for a, b in ws]
    for i in range(1, len(ws) - 1):
        if keys[i] and keys[i - 1] and keys[i + 1] and keys[i] != keys[i - 1] and keys[i] == keys[i + 1]:
            kind = "mode flip" if keys[i][0] == keys[i - 1][0] else ("transposition" if keys[i][1] == keys[i - 1][1] else "key + mode change")
            found.append((ws[i][0], kind))
    # break: about a bar with no drums, one or two bell / chime notes, the bass staying, loud drums just before or after; melody taken away: >= 4 beats without a tune note
    step = max(1, song.tick(0.5))
    last_cut = -99
    for t0 in range(song.tick(15), song.length - 12, step):
        chunk = song.in_range(t0, t0 + 12)
        if not chunk or not any(n[2] in LOW for n in chunk):
            continue
        drums = sum(1 for n in chunk if n[2] in (KICK, SNARE))
        bells = sum(1 for n in chunk if n[2] in (7, 8))
        around = sum(1 for n in song.in_range(t0 - 12, t0) + song.in_range(t0 + 12, t0 + 24) if n[2] in (KICK, SNARE))
        kind = None
        if drums == 0 and 1 <= bells <= 2 and around >= 5:
            kind = "break"
        elif not any(n[2] in TUNE for n in chunk):
            kind = "melody taken away"
        if kind and song.sec(t0) - last_cut >= 3.0:
            last_cut = song.sec(t0)
            found.append((song.sec(t0), kind))
    # the tune jumps up an octave / is halved between neighbouring windows
    ps = []
    for a, b in ws:
        tune = [p for _, p, _ in song.new_tune_notes(a, b)]
        ps.append((sum(tune) / len(tune), len(tune)) if len(tune) >= 4 else None)
    for i in range(1, len(ws)):
        if ps[i - 1] and ps[i]:
            if ps[i][0] - ps[i - 1][0] >= 8:
                found.append((ws[i][0], "melody an octave up"))
            elif ps[i][1] <= 0.55 * ps[i - 1][1] and rate[i] >= 0.8 * rate[i - 1]:
                found.append((ws[i][0], "melody halved"))
    found.sort()
    return found


def check_all(song):
    out = []   # (name, ok, text)

    def add(name, ok, text):
        out.append((name, bool(ok), text))

    ev = events(song)
    times = [0.0] + [t for t, _ in ev] + [song.seconds]
    gap = max(b - a for a, b in zip(times, times[1:]))
    add("gap", gap <= 45, f"longest stretch without a gimmick {gap:.0f} s (<= 45); {len(ev)} found")
    run = bass_run(song)
    add("root", run <= 20, f"bass keeps one pitch up to {run:.0f} s (<= 20)")
    ws = windows(song, 5.0)
    ladder = [bass_onsets_per_beat(song, a, b) for a, b in ws]
    first = [r for (a, b), r in zip(ws, ladder) if b <= 20]
    quarter = bool(first) and min(first) <= 1.6
    eighth = [a for (a, b), r in zip(ws, ladder) if 1.7 <= r <= 2.5]
    sixteenth = [a for (a, b), r in zip(ws, ladder) if r >= 3.3]
    order = bool(eighth) and bool(sixteenth) and min(eighth) < min(sixteenth) and min(sixteenth) >= 0.4 * song.seconds
    mixed = sum(1 for (a, b), r in zip(ws, ladder) if 2.0 <= r < 3.3) >= 2
    add("ladder", quarter and len(eighth) * 5 >= 12 and len(sixteenth) * 5 >= 10 and order and mixed,
        f"quarter start {'yes' if quarter else 'NO'} (min {min(first) if first else 0:.1f}/beat), eighths {len(eighth) * 5} s, 16ths {len(sixteenth) * 5} s from {min(sixteenth) if sixteenth else 0:.0f} s, in-between {'yes' if mixed else 'NO'}")
    rate_new, rate_bass = [], []
    for (a, b), r in zip(ws, ladder):
        if r >= 3.3:
            rate_bass.append(r)
            rate_new.append(len(song.new_tune_notes(a, b)) / ((b - a) * song.tps / 4.0))
    mean_new = sum(rate_new) / len(rate_new) if rate_new else 99
    add("long", rate_new and mean_new <= 1.4 and mean_new <= sum(rate_bass) / len(rate_bass) / 3, f"over the 16th bass the tune plays {mean_new:.2f} new notes a beat (<= 1.4)" if rate_new else "no 16th bass passage")
    style = song.style
    if style == 1:
        mean_drum = drum_per_beat(song, 0, song.seconds)
        intro = drum_per_beat(song, 0, 12)
        add("s1 intro", intro <= 0.6 * mean_drum, f"drums in the first 12 s {intro:.2f}/beat vs song mean {mean_drum:.2f} (<= 60 %)")
        ev_kinds = {k for _, k in ev}
        add("s1 B", "bass released" in ev_kinds and "melody an octave up" in ev_kinds, f"bass released {'yes' if 'bass released' in ev_kinds else 'NO'}, tune an octave up {'yes' if 'melody an octave up' in ev_kinds else 'NO'}")
        add("s1 break", "break" in ev_kinds, "a bar with no tune and no drums while the bass stays: " + ("yes" if "break" in ev_kinds else "NO"))
        keys = [best_key(song.window(a, b)) for a, b in windows(song, 10.0)]
        flips = [(i, k) for i, k in enumerate(keys) if k]
        minors = [i for i, k in flips if k[1] == "min"]
        majors = [i for i, k in flips if k[1] == "maj"]
        add("s1 mode", minors and majors and max(majors) > min(minors) and max(majors) >= len(keys) * 0.4, f"minor windows {len(minors)}, major windows {len(majors)}, the major after the minor")
        tonics = {k[0] for _, k in flips}
        add("s1 finale", len(tonics) >= 2, f"{len(tonics)} different tonics over the song (a whole-tone-up finale needs >= 2)")
        third = 0
        total = 0
        for i in majors:
            a, b = i * 10.0, (i + 1) * 10.0
            for t in range(song.tick(a), song.tick(b)):
                tunes = [sounding(n[2], n[3]) for n in song.by_tick.get(t, ()) if n[2] in TUNE]
                if len(tunes) >= 2:
                    total += 1
                    top = max(tunes)
                    if any((top - p) % 12 in (3, 4) for p in tunes):
                        third += 1
        add("s1 3rd", total and third >= 8, f"{third} tune ticks with a 3rd-below harmony in the major part")
    elif style == 2:
        first30 = song.window(0, 30)
        diss = tot = 0
        for t in range(0, song.tick(30)):
            ps = sorted({sounding(n[2], n[3]) % 12 for n in song.by_tick.get(t, ()) if n[2] not in (2, 3, 4) and sounding(n[2], n[3]) is not None})
            if len(ps) >= 2:
                tot += 1
                if any((b - a) in (1, 6, 11) for a in ps for b in ps if b > a):
                    diss += 1
        share = diss / tot if tot else 0
        add("s2 start", share >= 0.08, f"dissonant ticks (2nd / tritone) in the first 30 s: {share * 100:.0f} % (>= 8 %)")
        ws10 = windows(song, 5.0)
        snare = [drum_per_beat(song, a, b, (SNARE,)) for a, b in ws10]
        double = any(snare[j] >= 1.8 * snare[i] > 0 for i in range(len(snare)) for j in range(i + 1, len(snare)))
        add("s2 double", double, "snare rate rises to >= 1.8 times of an earlier part: " + ("yes" if double else "NO"))
        lost = []
        for a, b in ws10:
            chunk = song.window(a, b)
            tune = [n for n in chunk if n[2] in TUNE and n[2] not in STACCATO]
            stac = [n for n in chunk if n[2] in STACCATO]
            if len(tune) <= 2 and len(stac) >= 4 and any(n[2] in LOW for n in chunk):
                lost.append(a)
        add("s2 lost", len(lost) * 5 >= 15, f"melody lost (bass + staccato, no tune): {len(lost) * 5} s (>= 15)")
        sacred = []
        for a, b in ws10:
            chunk = song.window(a, b)
            if not chunk:
                continue
            ticks = defaultdict(int)
            for n in chunk:
                if n[2] not in (2, 3, 4) + LOW:
                    ticks[n[0]] += 1
            chords = sum(1 for c in ticks.values() if c >= 3)
            kick = drum_per_beat(song, a, b)
            if ticks and chords / len(ticks) >= 0.5 and len(song.new_tune_notes(a, b)) / ((b - a) * song.tps / 4.0) <= 0.7 and kick <= 0.7 * drum_per_beat(song, 0, song.seconds):
                sacred.append(a)
        add("s2 sacred", len(sacred) * 5 >= 15, f"sacred long chords (>= 3 voices, tune slow, drums down): {len(sacred) * 5} s (>= 15)")
        climax = False
        for (a, b), s in zip(ws10, snare):
            chunk = song.window(a, b)
            top = sum(1 for n in chunk if n[2] in TUNE and sounding(n[2], n[3]) >= 76)
            ticks = defaultdict(int)
            for n in chunk:
                if n[2] not in (2, 3, 4) + LOW:
                    ticks[n[0]] += 1
            if s > 0 and s >= 0.8 * max(snare) and top / (b - a) >= 2 and ticks and sum(1 for c in ticks.values() if c >= 3) / len(ticks) >= 0.35:
                climax = True
        add("s2 climax", climax, "a part with the fast snare, the long chords and the top note struck >= 2 a second: " + ("yes" if climax else "NO"))
    elif style == 3:
        opening = [n[4] for n in song.window(0, 8) if n[2] in TUNE]
        top = sorted(opening, reverse=True)
        add("s3 opening", top and sum(top[:max(1, len(top) * 4 // 10)]) / max(1, len(top[:max(1, len(top) * 4 // 10)])) >= 85, f"tune loudness in the first 8 s {sum(top[:max(1, len(top) * 4 // 10)]) / max(1, len(top[:max(1, len(top) * 4 // 10)])):.0f} (>= 85)")
        ev_kinds = {k for _, k in ev}
        add("s3 B", "melody halved" in ev_kinds, "the tune halved while the drums stay: " + ("yes" if "melody halved" in ev_kinds else "NO"))
        add("s3 break", "break" in ev_kinds, "a bar with no tune and no drums while the bass stays: " + ("yes" if "break" in ev_kinds else "NO"))
        keys = [best_key(song.window(a, b)) for a, b in windows(song, 10.0)]
        flips = [(i, k) for i, k in enumerate(keys) if k]
        minors = [i for i, k in flips if k[1] == "min"]
        majors = [i for i, k in flips if k[1] == "maj"]
        add("s3 mode", minors and majors and max(majors) > min(minors), f"minor windows {len(minors)}, major windows {len(majors)}, the major after the minor")
        peak = max((sounding(n[2], n[3]) for n in song.notes if n[2] in TUNE), default=0)
        add("s3 top", peak >= 76, f"highest tune note {peak} (F#5 = 78; >= 76)")
        end = song.window(song.seconds - 12, song.seconds)
        toms = sum(1 for n in end if n[2] == KICK or n[2] == SNARE)
        add("s3 outro", toms >= 16, f"drum hits in the last 12 s: {toms} (a fill, >= 16)")
    elif style == 4:
        arp = [n for n in song.window(0, 15) if n[2] in (0, 15)]
        falls = sum(1 for a, b in zip(arp, arp[1:]) if b[3] < a[3])
        add("s4 intro", len(arp) / 15 >= 5 and falls / max(1, len(arp) - 1) >= 0.55, f"arpeggio {len(arp) / 15:.1f} notes/s, {falls * 100 // max(1, len(arp) - 1)} % falling (>= 5/s, >= 55 %)")
        mains = [n for n in song.window(20, 60) if n[2] in LOW]
        pcs = defaultdict(int)
        for n in mains:
            pcs[sounding(n[2], n[3]) % 12] += 1
        top2 = sum(sorted(pcs.values(), reverse=True)[:2]) / max(1, len(mains))
        add("s4 A bass", top2 >= 0.85, f"bass on root/fifth only in 20-60 s: {top2 * 100:.0f} % (>= 85 %)")
        ev_kinds = {k for _, k in ev}
        add("s4 half", "half/double-time drums" in ev_kinds, "half-time (drums halved) found: " + ("yes" if "half/double-time drums" in ev_kinds else "NO"))
        add("s4 build", "melody taken away" in ev_kinds or "break" in ev_kinds, "a part with no tune while the drums rise: " + ("yes" if ("melody taken away" in ev_kinds or "break" in ev_kinds) else "NO"))
        add("s4 chorus", "melody an octave up" in ev_kinds and ("transposition" in ev_kinds or "key + mode change" in ev_kinds), "chorus: the tune an octave up and the key moved: " + ("yes" if ("melody an octave up" in ev_kinds and ("transposition" in ev_kinds or "key + mode change" in ev_kinds)) else "NO"))
    return out, ev


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    show = "--events" in sys.argv
    failed = False
    for path in args:
        song = Song(path)
        results, ev = check_all(song)
        name = path.rsplit("/", 1)[-1]
        bad = [r for r in results if not r[1]]
        failed |= bool(bad)
        print(f"{name}  style {song.style}  {song.tps:.2f} t/s  {song.seconds:.0f} s   {len(results) - len(bad)}/{len(results)} guide checks")
        for n, ok, text in results:
            print(f"   {'ok' if ok else '!!'} {n:10s} {text}")
        if show:
            print("   time line: " + ", ".join(f"{t:.0f}s {k}" for t, k in ev))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
