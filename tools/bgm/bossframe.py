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


def bass_notes(name):
    root, _ = chord(name)
    base = root + 12 * 3
    while base > 46:
        base -= 12
    while base < 33:
        base += 12
    return base, base + 7, base + 12


def fit(instrument, note):
    """The note moved by octaves into the real range of the instrument."""
    low = BASE[instrument]
    while note > low + 24:
        note -= 12
    while note < low:
        note += 12
    return note


def bar_notes(text, bar_ticks):
    """'E4:4 G4:4 r:8' -> [(tick, midi, ticks)]; the ticks must fill the bar."""
    tick = 0
    result = []
    for part in text.split():
        name, length = part.split(":")
        length = int(length)
        if name != "r":
            result.append((tick, midi(name), length))
        tick += length
    assert tick == bar_ticks, f"bar '{text}' has {tick} ticks of {bar_ticks}"
    return result


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
    bar = spec["bar"]
    sixteenth = bar / 16.0
    names = ["lead", "dbl", "cnt", "arp", "stabA", "stabB", "stabC", "bass", "drone", "kick", "snare", "hat", "bell", "echo"]
    pans = {"lead": 100, "dbl": 90, "cnt": 110, "arp": 135, "stabA": 80, "stabB": 120, "stabC": 100, "bass": 100, "drone": 100, "kick": 100, "snare": 105, "hat": 115, "bell": 120, "echo": 85}
    volumes = {"lead": 100, "dbl": 60, "cnt": 60, "arp": 62, "stabA": 70, "stabB": 70, "stabC": 70, "bass": 85, "drone": 60, "kick": 70, "snare": 60, "hat": 40, "bell": 50, "echo": 50}
    layer = {n: Layer(n, volumes[n], pans[n]) for n in names}
    shift = spec["shift"][phase]
    pcs = {(spec["tonic"] + i) % 12 for i in spec["scale"]}
    pcs = {(p + shift) % 12 for p in pcs}
    intro_bars = 64 // bar
    offset = 64 - intro_bars * bar
    sections = [("intro", intro_bars)] + list(spec["sections"])
    voices_of = spec["voices"][phase]
    fills_tick = {}

    def put(name, tick, instrument, sounding, vel, pitched=True):
        if tick in layer[name].notes or tick < 0:
            return
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
                root, fifth, octave = bass_notes(spec.get("pedal", {}).get(section, name))
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
                        for tick in range(0, span, max(1, int(round(8 * sixteenth)))):
                            put("drone", span_start + tick, DIDGERIDOO, root - 12 if root - 12 >= 30 else root, 44 * level)
                    elif kind == "arp":
                        instrument = INSTRUMENTS[parts[1]]
                        step = int(parts[2])
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
                            tick = int(round(offs * sixteenth))
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
                if parts[0] == "fill" and index % 8 == 7:
                    for k in range(4):
                        put("snare", start + int(round((12 + k) * sixteenth)), SNARE, 66, (34 + 8 * k) * level, pitched=False)
            # the melody
            if melody is not None:
                events = bar_notes(melody[index], bar)
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
                            put("lead", start + tick, INSTRUMENTS[parts[1]], note + shift, vel * level)
                        elif parts[0] == "dbl":
                            factor = float(parts[3]) if len(parts) > 3 else 0.6
                            put("dbl", start + tick, INSTRUMENTS[parts[1]], note + shift + int(parts[2]), vel * factor * level)
                        elif parts[0] == "cnt":
                            put("cnt", start + tick, INSTRUMENTS[parts[1]], scale_down(note + shift, pcs, 2), vel * 0.62 * level)
                        elif parts[0] == "echo":
                            put("echo", start + tick + int(parts[1]), INSTRUMENTS[parts[2]], note + shift, vel * 0.3 * level)
        position += bars * bar
    # the run into the loop: the last half bar climbs to the first note of A
    if spec.get("run"):
        end = position
        for place, note in enumerate(spec["run"]):
            put("arp", end - len(spec["run"]) * 2 + place * 2, HARP, note + shift, 36 + 4 * place)
    return [layer[n] for n in names if layer[n].notes], position


def write_phases(spec, targets, descriptions, titles):
    """Builds both phases (or the one phase of a single song) and writes them."""
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
    bar = spec["bar"]
    start = 0
    result = []
    for name, bars in [("intro", 64 // bar)] + list(spec["sections"]):
        end = start + (64 if name == "intro" else bars * bar)
        notes = sum(1 for layer in layers for tick in layer.notes if start <= tick < end)
        result.append((name, notes / ((end - start) / spec["tps"])))
        start = end
    return result
