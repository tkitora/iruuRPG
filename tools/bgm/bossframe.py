"""What the boss songs written by the BGM制作ガイド share: one structure, the layers that come in and go out, the second phase made from the first.

A boss song is written as a SPEC (a dict, see make_boss_demo below for every key) and built twice, once for each phase. The structure is the guide's:
intro / A / A' / B (takes away) / A'' (the peak, from 65 % on) / calm, then back to the start of A. Every section has its own chords (one chord a bar, or two with 'Em|C'), its own melody
(blocks of bars written in the notes of the key) and a list of voices; a voice is a tag: ``lead:HARP``, ``dbl:FLUTE:12:0.6`` (the melody an octave up on the flute at 60 %), ``cnt:GUITAR`` (the melody
a third below), ``arp:HARP:2`` (broken chord, a note every 2 ticks), ``stab:PLING:0,8`` (chord hits), ``bass:half|eighth|sixteenth|walk|pedal|stagger``, ``drone``, ``drums:heart|march|drive|double``, ``fill``,
``bell:head|hit``, ``echo:3:CHIME``. ``tag@8`` starts it at bar 8 of the section (layers are added by 4 or 8 bars), ``tag@8-12`` stops it at bar 12. The drums are the last to come in and the first to go.

The second phase is the first one with the changes the guide lists (always: a half step up; then two or three of: bass 8th -> 16th, the drums and the drone and the bell hits added, the melody in unison with
three instruments, a darker mode, the harmony twice as fast): they are in the ``voices`` of the spec for phase 2, and the SPEC says in its docstring which it took. Everything is written by what SOUNDS (put_sound).
"""
from nbslib import *  # noqa: F401,F403

NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
QUALITY = {"": [0, 4, 7], "m": [0, 3, 7], "dim": [0, 3, 6], "aug": [0, 4, 8], "5": [0, 7], "sus2": [0, 2, 7], "sus4": [0, 5, 7],
           "7": [0, 4, 7, 10], "m7": [0, 3, 7, 10], "maj7": [0, 4, 7, 11], "dim7": [0, 3, 6, 9], "m7b5": [0, 3, 6, 10], "b5": [0, 4, 6], "mb5": [0, 3, 6]}
INSTRUMENTS = {"HARP": HARP, "BASS": BASS, "GUITAR": GUITAR, "FLUTE": FLUTE, "BELL": BELL, "CHIME": CHIME, "XYLOPHONE": XYLOPHONE, "IRON_XYLOPHONE": IRON_XYLOPHONE,
               "COW_BELL": COW_BELL, "DIDGERIDOO": DIDGERIDOO, "BIT": BIT, "BANJO": BANJO, "PLING": PLING}
DRUMS = {  # (offset in sixteenths, drum, velocity)
    "four": [(0, BASEDRUM, 100), (8, BASEDRUM, 90), (4, SNARE, 72), (12, SNARE, 84)],
    "stomp": [(0, BASEDRUM, 100), (6, BASEDRUM, 62), (10, BASEDRUM, 66), (8, SNARE, 90)],
    "half": [(0, BASEDRUM, 100), (6, BASEDRUM, 68), (8, SNARE, 100), (14, BASEDRUM, 60)],
    "gallop": [(0, BASEDRUM, 100), (3, BASEDRUM, 66), (4, BASEDRUM, 72), (8, BASEDRUM, 96), (11, BASEDRUM, 66), (12, BASEDRUM, 72), (4, SNARE, 90), (12, SNARE, 96)],
    "waltz": [(0, BASEDRUM, 56), (5, SNARE, 34), (11, SNARE, 34)],
    "waltz2": [(0, BASEDRUM, 56), (5, BASEDRUM, 40), (11, BASEDRUM, 40), (3, HAT, 20), (8, HAT, 20)],
    "kicks4": [(0, BASEDRUM, 58), (4, BASEDRUM, 50), (8, BASEDRUM, 54), (12, BASEDRUM, 50), (4, SNARE, 46), (12, SNARE, 46)],
    "kicks": [(0, BASEDRUM, 56), (8, BASEDRUM, 46), (4, SNARE, 44), (12, SNARE, 44)],
    "heart": [(0, BASEDRUM, 46), (8, BASEDRUM, 36)],
    "march": [(0, BASEDRUM, 52), (8, BASEDRUM, 44), (4, SNARE, 40), (12, SNARE, 40)],
    "drive": [(0, BASEDRUM, 56), (6, BASEDRUM, 40), (8, BASEDRUM, 50), (14, BASEDRUM, 36), (4, SNARE, 46), (12, SNARE, 46), (2, HAT, 22), (6, HAT, 22), (10, HAT, 22), (14, HAT, 22)],
    "double": [(0, BASEDRUM, 58), (4, BASEDRUM, 48), (8, BASEDRUM, 54), (12, BASEDRUM, 48), (4, SNARE, 50), (12, SNARE, 50)] + [(t, HAT, 22) for t in range(0, 16, 2)],
}
BASS_STYLES = {  # (offset in sixteenths, "r" root / "f" fifth / "o" octave, velocity)
    "pulse": [(t, "o" if t in (6, 14) else "r", 84 if t % 8 == 0 else 58) for t in range(0, 16, 2)],
    "half": [(0, "r", 74), (8, "f", 58)],
    "eighth": [(0, "r", 74)] + [(t, "r", 56) for t in range(2, 16, 2)],
    "sixteenth": [(t, "r" if t % 4 != 2 else "o", 70 if t % 4 == 0 else 46) for t in range(16)],
    "walk": [(0, "r", 74), (4, "f", 54), (8, "o", 62), (12, "f", 54)],
    "pedal": [(t, "r", 66 if t % 8 == 0 else 46) for t in range(0, 16, 4)],
    "waltz": [(0, "r", 80), (5, "f", 50), (11, "f", 50)],
    "stagger": [(0, "r", 76), (3, "r", 56), (6, "r", 62), (9, "r", 56), (12, "r", 62), (14, "r", 50)],
}


def chord(name):
    """'Em' -> (root pitch class, intervals); 'Bb7', 'F#dim', 'E5'."""
    pc = NOTE_PC[name[0]]
    rest = name[1:]
    while rest and rest[0] in "#b" and not rest.startswith("b5"):
        pc += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return pc % 12, QUALITY[rest]


def tones_in(name, low, high):
    root, intervals = chord(name)
    return sorted(n for n in range(low, high + 1) if (n - root) % 12 in intervals)


def bass_notes(name, heavy=False):
    """Root, fifth and octave of the bass; a heavy song keeps the root between F#1 and E2 (the lowest the bass plays), so that it weighs."""
    root, _ = chord(name)
    top, floor = (41, 30) if heavy else (46, 33)
    base = root + 12 * 3
    while base > top:
        base -= 12
    while base < floor:
        base += 12
    return base, base + 7, base + 12


def guitar_root(name):
    """The root of the chord in the low guitar (F#2 to F3)."""
    root, _ = chord(name)
    base = root + 36
    while base > 53:
        base -= 12
    while base < 42:
        base += 12
    return base


HEAVY_GAIN = {"bass": 1.35, "drone": 1.5, "kick": 1.6, "snare": 1.5, "hat": 1.3, "stabA": 1.6, "stabB": 1.6, "stabC": 1.6, "riff": 1.0, "arp": 0.5, "bell": 0.7, "echo": 0.5,
              "lead": 1.15, "dbl": 1.2, "cnt": 1.2}


def heavy_voices(phase, bass=("eighth", "walk", "eighth"), special=None, lead="HARP", extra=None, drum_map=None, bass_all=None):
    """The voices of a boss song that weighs: a bass and a didgeridoo that never stop, a low guitar that chugs, power chords, a bass drum that is heard, the tune low and in unison
    with the guitar and (later) the bass two octaves down, an ensemble hit every eighth bar, a roll before the peak. ``special`` replaces or adds tags per section (a song's own device).
    Phase 0 is natural (harp, guitar, bass, didgeridoo, drums), phase 1 electronic (electric piano, 8-bit chip, the same weight, the drums twice as busy)."""
    a, b, c = bass
    if phase == 0:
        tune, grit, power = lead, "GUITAR", "GUITAR"
        sections = {
            "intro": ["bass:half", "drone:loud", "riff:GUITAR:4@1", "drums:stomp@1", "roll@1-2"],
            "A": ["hit:8", "lead:%s:-12" % tune, "dbl:%s:-12:0.8" % grit, "bass:%s" % a, "drone:loud", "riff:GUITAR:4", "drums:stomp", "pow:%s:0@8" % power],
            "A2": ["hit:8", "lead:%s:-12" % tune, "dbl:%s:-12:0.9" % grit, "dbl:BASS:-24:0.8@8", "pow:%s:0,12" % power, "bass:%s" % b, "drone:loud", "riff:GUITAR:2@8", "riff:GUITAR:4@0-8", "drums:half", "fill"],
            "B": ["lead:BASS:-12", "bass:half", "drone:loud", "drums:stomp", "pow:%s:0@12" % power, "roll@23-24"],
            "PEAK": ["hit:4", "lead:%s:-12" % tune, "dbl:%s:-12:0.9" % grit, "dbl:BASS:-24:0.9", "pow:%s:0,6,12" % power, "bass:%s" % c, "drone:loud", "riff:GUITAR:2", "drums:gallop", "fill"],
            "CALM": ["lead:%s:-12" % tune, "bass:half", "drone:loud", "drums:heart"],
        }
    else:
        tune, grit, power = "PLING", "BIT", "BIT"
        sections = {
            "intro": ["bass:half", "drone:loud", "riff:GUITAR:4@1", "drums:half@1", "roll@1-2"],
            "A": ["hit:8", "lead:PLING:-12", "dbl:BIT:-12:0.7", "bass:%s" % b, "drone:loud", "riff:GUITAR:2", "drums:half", "pow:BIT:0,12@8"],
            "A2": ["hit:8", "lead:PLING:-12", "dbl:BIT:-12:0.8", "dbl:GUITAR:-12:0.8", "dbl:BASS:-24:0.8@8", "pow:BIT:0,6,12", "bass:sixteenth", "drone:loud", "riff:GUITAR:2", "drums:gallop", "fill"],
            "B": ["lead:BIT:-12", "bass:%s" % a, "drone:loud", "drums:half", "pow:BIT:0@12", "roll@23-24"],
            "PEAK": ["hit:4", "lead:PLING:-12", "dbl:BIT:-12:0.9", "dbl:GUITAR:-12:0.9", "dbl:BASS:-24:0.9", "pow:BIT:0,3,6,12", "bass:sixteenth", "drone:loud", "riff:GUITAR:1", "drums:double", "fill"],
            "CALM": ["lead:PLING:-12", "bass:half", "drone:loud", "drums:heart"],
        }
    for key, value in (special or {}).items():
        sections[key] = value
    for key, value in (extra or {}).items():
        sections[key] = sections[key] + value
    if drum_map or bass_all:
        for key, tags in sections.items():
            fixed = []
            for tag in tags:
                body, _, window = tag.partition("@")
                parts = body.split(":")
                if parts[0] == "drums" and drum_map:
                    parts[1] = drum_map.get(parts[1], parts[1])
                if parts[0] == "bass" and bass_all:
                    parts[1] = bass_all
                fixed.append(":".join(parts) + ("@" + window if window else ""))
            sections[key] = fixed
    return sections


def fit(instrument, note):
    """The note moved by octaves into the real range of the instrument."""
    low = BASE[instrument]
    while note > low + 24:
        note -= 12
    while note < low:
        note += 12
    return note


def bar_notes(text, src_ticks, bar_ticks=None):
    """'E4:4 G4:4 r:8' -> [(tick, midi, ticks)]; the ticks must fill the bar as it is written (``src_ticks``); the notes are put on the grid of the real bar
    (``bar_ticks``: a song written in 16-tick bars played in 24-tick bars has every position times one and a half, rounded)."""
    bar_ticks = bar_ticks or src_ticks
    tick = 0
    result = []
    for part in text.split():
        name, length = part.split(":")
        length = int(length)
        if name != "r":
            result.append((int(round(tick * bar_ticks / src_ticks)), midi(name), length))
        tick += length
    assert tick == src_ticks, f"bar '{text}' has {tick} ticks of {src_ticks}"
    return result


def widen(spec):
    """A song written for 80 bars of 16 ticks at 10 ticks a second (150 BPM) played at 200 BPM: 20 ticks a second, a beat of 6 ticks (a bar of 24; the waltz 18),
    and more bars so that it still lasts two minutes (the sections become 24 / 24 / 24 / 20 / 8 bars, the waltz 32 / 32 / 24 / 24 / 24): bars are added by repeating the first eight bars of a section.
    The tempo of a song is a whole number of ticks to the beat (the ticks divide 20 a second): from 150 the next clean step is 200."""
    if not spec.get("faster") or spec.get("widened"):
        return spec
    new = dict(spec)
    new["widened"] = True
    src = spec["bar"]
    waltz = src == 12
    new["src_bar"] = src
    new["bar"] = 18 if waltz else 24
    new["tps"] = 20.0
    chords = {k: list(v) for k, v in spec["chords"].items()}
    melody = {k: (list(v) if v is not None else None) for k, v in spec["melody"].items()}
    voices = [{k: [t.replace("@2", "@1") if k == "intro" else t for t in v] for k, v in phase.items()} for phase in spec["voices"]]
    chords["intro"] = chords["intro"][::2] if not waltz else [chords["intro"][0], chords["intro"][2], chords["intro"][4]]
    breaks = {k: tuple(v) for k, v in spec.get("breaks", {}).items()}

    def grow(table, name, how):
        data = table[name]
        if data is None:
            return
        if how == "a8":          # eight more bars of the first eight
            table[name] = data + data[:8]
        elif how == "p4":        # four more bars before the end: the first four bars once more at the start
            table[name] = data[:4] + data
        elif how == "p8":
            table[name] = data[:8] + data
        elif how == "calm":      # 12 bars -> 8: the first six, then the last two (the way back into the loop)
            table[name] = data[:6] + data[-2:]

    if waltz:
        sections = [("A", 32), ("A2", 32), ("B", 24), ("PEAK", 24), ("CALM", 24)]
        for table in (chords, melody):
            grow(table, "A", "a8"); grow(table, "A2", "a8"); grow(table, "B", "a8"); grow(table, "PEAK", "p8"); grow(table, "CALM", "p8")
    else:
        sections = [("A", 24), ("A2", 24), ("B", 24), ("PEAK", 20), ("CALM", 8)]
        old_a_chords, old_a_melody = chords["A"][8:16], melody["A"][8:16]
        for table in (chords, melody):
            grow(table, "A", "a8"); grow(table, "B", "a8"); grow(table, "PEAK", "p4"); grow(table, "CALM", "calm")
        chords["A2"] = chords["A2"] + old_a_chords
        melody["A2"] = melody["A2"] + old_a_melody
        if "A" in breaks:
            breaks["A"] = breaks["A"] + (23,)
        if "A2" in breaks:
            breaks["A2"] = breaks["A2"] + (23,)
        if "PEAK" in breaks:
            breaks["PEAK"] = tuple(b + 4 for b in breaks["PEAK"])
    new.update(sections=sections, chords=chords, melody=melody, voices=voices, breaks=breaks)
    return new


def scale_down(note, pitch_classes, steps):
    """The note ``steps`` scale steps down (``pitch_classes`` is the set of the key)."""
    count = 0
    while count < steps:
        note -= 1
        if note % 12 in pitch_classes:
            count += 1
    return note


def mv(text, key_pcs, steps):
    """A bar of the melody moved ``steps`` steps along the scale (a diatonic sequence); ``key_pcs`` is the set of pitch classes of the key."""
    out = []
    for part in text.split():
        name, length = part.split(":")
        if name == "r" or steps == 0:
            out.append(part)
            continue
        note = midi(name)
        direction = 1 if steps > 0 else -1
        count = 0
        while count < abs(steps):
            note += direction
            if note % 12 in key_pcs:
                count += 1
        out.append(f"{note_name(note)}:{length}")
    return " ".join(out)


def seq_block(theme, answer, k, plan):
    """A block of bars from a theme and an answer: an int moves the theme that many scale steps, ('u', n) moves the answer, a string is a bar written out."""
    out = []
    for item in plan:
        if isinstance(item, int):
            out.append(mv(theme, k, item))
        elif isinstance(item, tuple):
            out.append(mv(answer, k, item[1]))
        else:
            out.append(item)
    return out


def invert(text, axis):
    """A bar turned upside down about the note ``axis`` (a midi number)."""
    out = []
    for part in text.split():
        name, length = part.split(":")
        out.append(part if name == "r" else f"{note_name(2 * axis - midi(name))}:{length}")
    return " ".join(out)


def key_pcs(tonic, scale):
    return {(tonic + i) % 12 for i in scale}


def parse_tag(tag):
    body, _, window = tag.partition("@")
    start, end = 0, 10 ** 6
    if window:
        first, _, last = window.partition("-")
        start = int(first)
        end = int(last) if last else end
    return body.split(":"), start, end


def build(spec, phase):
    """(layers, length in ticks) of the song, for phase 0 or 1."""
    spec = widen(spec)
    bar = spec["bar"]
    src = spec.get("src_bar", bar)
    stretch = bar / src                       # the ticks of a song written for a shorter bar
    slower = spec["tps"] / 10.0               # ticks of 20 a second are half as long as ticks of 10 a second
    sixteenth = bar / 16.0
    names = ["lead", "dbl", "cnt", "arp", "stabA", "stabB", "stabC", "bass", "drone", "kick", "snare", "hat", "bell", "echo", "riff"]
    pans = {"lead": 100, "dbl": 90, "cnt": 110, "arp": 135, "stabA": 80, "stabB": 120, "stabC": 100, "bass": 100, "drone": 100, "kick": 100, "snare": 105, "hat": 115, "bell": 120, "echo": 85, "riff": 125}
    volumes = {"lead": 100, "dbl": 60, "cnt": 60, "arp": 62, "stabA": 70, "stabB": 70, "stabC": 70, "bass": 85, "drone": 60, "kick": 70, "snare": 60, "hat": 40, "bell": 50, "echo": 50, "riff": 75}
    layer = {n: Layer(n, volumes[n], pans[n]) for n in names}
    shift = spec["shift"][phase]
    pcs = {(spec["tonic"] + i) % 12 for i in spec["scale"]}
    pcs = {(p + shift) % 12 for p in pcs}
    intro_bars = 64 // bar
    offset = 64 - intro_bars * bar
    sections = [("intro", intro_bars)] + list(spec["sections"])
    voices_of = spec["voices"][phase]
    fills_tick = {}

    total = offset + sum(b for _, b in sections) * bar        # a note that would fall after the end is dropped
    sections_total = total

    def put(name, tick, instrument, sounding, vel, pitched=True):
        if tick in layer[name].notes or tick < 0 or tick >= sections_total:
            return
        if spec.get("heavy"):
            vel = vel * HEAVY_GAIN.get(name, 1.0)
        if pitched:
            sounding = fit(instrument, sounding)
            if instrument in (BELL, CHIME):          # the bell and the chime stay between F#6 and D#7 (quiet, never shrill)
                while sounding > 99:
                    sounding -= 12
                if sounding < 90:
                    return
            layer[name].put_sound(tick, instrument, sounding, max(1, min(100, int(round(vel)))))
        else:
            layer[name].put(tick, instrument, 66, max(1, min(100, int(round(vel)))))

    position = offset
    bar_index = 0
    for section, bars in sections:
        chords = spec["chords"][section]
        melody = spec["melody"].get(section)
        tags = [parse_tag(t) for t in voices_of.get(section, [])]
        assert len(chords) == bars, f"{section}: {len(chords)} chords for {bars} bars"
        if melody is not None:
            assert len(melody) == bars, f"{section}: {len(melody)} melody bars for {bars} bars"
        level = spec["level"].get(section, 1.0)
        for index in range(bars):
            start = position + index * bar
            halves = chords[index].split("|")
            half = bar // 2
            for h, name in enumerate(halves):
                span_start = start + (h * half if len(halves) == 2 else 0)
                span = half if len(halves) == 2 else bar
                root, fifth, octave = bass_notes(spec.get("pedal", {}).get(section, name), bool(spec.get("heavy")))
                root += shift
                fifth += shift
                octave += shift
                for parts, first, last in tags:
                    if not (first <= index < last):
                        continue
                    kind = parts[0]
                    if index in spec.get("breaks", {}).get(section, ()) and kind not in spec.get("break_keep", ("arp_never",)):
                        continue
                    if kind == "bass":
                        for offs, which, vel in BASS_STYLES[parts[1]]:
                            tick = int(round(offs * sixteenth))
                            if tick >= span:
                                continue
                            note = {"r": root, "f": fifth, "o": octave}[which]
                            put("bass", span_start + tick, BASS, note, vel * level)
                    elif kind == "drone":
                        loud = len(parts) > 1 and parts[1] == "loud"
                        for tick in range(0, span, max(1, int(round((4 if loud else 8) * sixteenth)))):
                            put("drone", span_start + tick, DIDGERIDOO, root - 12 if root - 12 >= 30 else root, (62 if loud else 44) * level)
                    elif kind == "riff":
                        instrument = INSTRUMENTS[parts[1]]
                        step = max(1, int(round(int(parts[2]) * stretch)))
                        low = guitar_root(name) + shift
                        for place, tick in enumerate(range(0, span, step)):
                            put("riff", span_start + tick, instrument, low + (12 if place % 4 == 3 else 0), (76 if place % 2 == 0 else 54) * level)
                    elif kind == "pow":
                        instrument = INSTRUMENTS[parts[1]]
                        low = guitar_root(name) + shift
                        for offs in [int(o) for o in parts[2].split(",")]:
                            tick = int(round(offs * stretch))
                            if tick >= span:
                                continue
                            for k, note in enumerate((low, low + 7, low + 12)):
                                put(("stabA", "stabB", "stabC")[k], span_start + tick, instrument, note, 78 * level)
                    elif kind == "hit":
                        if index % int(parts[1]) == 0 and h == 0:
                            low = guitar_root(name) + shift
                            for k, note in enumerate((low, low + 7, low + 12)):
                                put(("stabA", "stabB", "stabC")[k], span_start, GUITAR, note, 100)
                            put("bass", span_start, BASS, root, 100)
                            put("drone", span_start, DIDGERIDOO, root, 90)
                    elif kind == "arp":
                        instrument = INSTRUMENTS[parts[1]]
                        step = max(1, int(round(int(parts[2]) * stretch)))
                        low = BASE[instrument] + (4 if instrument == HARP else 0)
                        notes = tones_in(name, spec["arp_low"] + shift, spec["arp_high"] + shift)
                        order = [0, 1, 2, 3, 2, 1] if len(notes) >= 4 else [0, 1, 2, 1]
                        for place, tick in enumerate(range(0, span, step)):
                            put("arp", span_start + tick, instrument, notes[order[place % len(order)] % len(notes)], (34 if place % 4 == 0 else 28) * level)
                    elif kind == "stab":
                        instrument = INSTRUMENTS[parts[1]]
                        notes = tones_in(name, spec["stab_low"] + shift, spec["stab_high"] + shift)
                        offsets = [int(o) for o in parts[2].split(",")]
                        for offs in offsets:
                            tick = int(round(offs * stretch))
                            if tick >= span:
                                continue
                            for k, note in enumerate(notes[:3]):
                                put(("stabA", "stabB", "stabC")[k], span_start + tick, instrument, note, 56 * level)
                    elif kind == "bell":
                        if parts[1] == "head" and not (index % 2 == 0 and h == 0):
                            continue
                        window = [n for n in range(90 + shift, 100 + shift) if (n - chord(name)[0] - shift) % 12 in chord(name)[1]]
                        if window:
                            put("bell", span_start + (0 if parts[1] == "head" else 0), BELL, window[len(window) // 2], 50 * level)
                    elif kind == "drums" or kind == "fill":
                        pass
            # drums and fills work on the whole bar
            for parts, first, last in tags:
                if not (first <= index < last):
                    continue
                if index in spec.get("breaks", {}).get(section, ()):
                    continue
                if parts[0] == "drums":
                    for offs, drum, vel in DRUMS[parts[1]]:
                        put({BASEDRUM: "kick", SNARE: "snare", HAT: "hat"}[drum], start + int(round(offs * sixteenth)), drum, 66, vel * level, pitched=False)
                if parts[0] == "hit" and index % int(parts[1]) == 0:
                    put("kick", start, BASEDRUM, 66, 100, pitched=False)
                    put("snare", start, SNARE, 66, 90, pitched=False)
                if parts[0] == "roll":
                    put("kick", start, BASEDRUM, 66, 90, pitched=False)
                    for k in range(8):
                        put("snare", start + int(round(k * 2 * sixteenth)), SNARE, 66, 38 + 8 * k, pitched=False)
                if parts[0] == "fill" and index % 8 == 7:
                    for k in range(4):
                        put("snare", start + int(round((12 + k) * sixteenth)), SNARE, 66, (34 + 8 * k) * level, pitched=False)
            # the melody
            if melody is not None:
                events = bar_notes(melody[index], src, bar)
                top = max((n for _, n, _ in events), default=0)
                for tick, note, length in events:
                    vel = 88 if tick == 0 else (86 if note == top else 74)
                    for parts, first, last in tags:
                        if not (first <= index < last):
                            continue
                        if parts[0] == "mir":
                            put("cnt", start + tick, INSTRUMENTS[parts[1]], 2 * (spec["axis"] + shift) - (note + shift), vel * 0.7 * level)
                        if index in spec.get("breaks", {}).get(section, ()) and parts[0] not in spec.get("break_keep", ("lead", "echo")):
                            continue
                        if parts[0] == "lead":
                            put("lead", start + tick, INSTRUMENTS[parts[1]], note + shift + (int(parts[2]) if len(parts) > 2 else 0), vel * level)
                        elif parts[0] == "dbl":
                            factor = float(parts[3]) if len(parts) > 3 else 0.6
                            put("dbl", start + tick, INSTRUMENTS[parts[1]], note + shift + int(parts[2]), vel * factor * level)
                        elif parts[0] == "cnt":
                            put("cnt", start + tick, INSTRUMENTS[parts[1]], scale_down(note + shift, pcs, 2), vel * 0.62 * level)
                        elif parts[0] == "echo":
                            put("echo", start + tick + int(round(int(parts[1]) * slower)), INSTRUMENTS[parts[2]], note + shift, vel * 0.3 * level)
        position += bars * bar
    # the run into the loop: the last half bar climbs to the first note of A
    if spec.get("run"):
        end = position
        for place, note in enumerate(spec["run"]):
            put("arp", end - len(spec["run"]) * 2 * int(slower) + place * 2 * int(slower), HARP, note + shift, 36 + 4 * place)
    return [layer[n] for n in names if layer[n].notes], position


def write_phases(spec, targets, descriptions, titles):
    """Builds both phases (or the one phase of a single song) and writes them."""
    spec = widen(spec)
    results = []
    for phase, target in enumerate(targets):
        layers, length = build(spec, phase)
        removed = limit_polyphony(layers, 8)
        write_nbs(target, layers, length, spec["tps"], titles[phase], descriptions[phase], 4 if spec["bar"] == 16 else 4)
        print(f"-- phase {phase + 1}: {removed} notes thinned out to keep 8 at most")
        report(target, layers, length, spec["tps"])
        print("   notes a second by section: " + ", ".join(f"{name} {count:.1f}" for name, count in density(spec, layers)))
        results.append((layers, length))
    return results


def density(spec, layers):
    """[(section, notes a second)]: how the song swells and takes away."""
    spec = widen(spec)
    bar = spec["bar"]
    start = 0
    result = []
    for name, bars in [("intro", 64 // bar)] + list(spec["sections"]):
        end = start + (64 if name == "intro" else bars * bar)
        notes = sum(1 for layer in layers for tick in layer.notes if start <= tick < end)
        result.append((name, notes / ((end - start) / spec["tps"])))
        start = end
    return result
