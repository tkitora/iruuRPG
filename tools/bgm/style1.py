"""The arrangement engine of STYLE 1 ("royal high-speed battle", style doc 1) for the two sorrow bosses (Boss004 necromancer, Boss008 waltz), t281.

It holds NO music: the tunes, chords, patterns and plans come from the song script in a dict ``D``. What it does is the time table of the style doc, scaled to the length of the song:
    intro      a single-line solo refrain without drums, then (from the loop point) the tune again with the snare getting denser bar by bar
    A          full drums, the bass nails the root; a pseudo HALF TIME inside it
    B          bass release (the bass runs), the tune an octave up on the flute, pseudo DOUBLE TIME inside it
    pre-break  the dominant, STOP-TIME, then ONE bar with every drum and the bass muted and one bell
    CHORUS     the relative major, a third above, the tune in LONG notes over the 16th machine gun
    FINALE     the whole structure a whole step up; the last bar leads back to the quiet refrain
All velocities are written LOUD (t279: the tune struck again at ~0.9, bass ghost notes still weigh); see bossness.py. Nothing is random.
"""
from sorrow_kit import *  # noqa: F401,F403


def pick(plan, i):
    """plan: [(first bar, name), ...] ascending; the entry of bar i."""
    name = plan[0][1]
    for first, n in plan:
        if i >= first:
            name = n
    return name


def build(P, D):
    s = Score(D["tps"], D["bar"], D["intro_abs"], D["body"])
    bar_ticks = D["bar"]
    L = D["lead"]                    # the instrument of the tune (PLING or HARP)
    BP, DR = D["bass"], D["drums"]
    half_beat = bar_ticks // 2

    lead = Voice("lead", L, 0, 1.0, "cry", 3)
    bit = lambda g=0.55, off=0: Voice("dbl", BIT, 0, g, "cry", 3, offbeat=1 if off else 0)
    harp_hit = lambda g=0.5: Voice("dbl", HARP, 0, g, "none")
    flute12 = lambda g=0.6, off=0: Voice("oct", FLUTE, 12, g, "cry", 3, offbeat=1 if off else 0)
    gtr_lo = lambda g=0.7, off=0: Voice("low", GUITAR, -12, g, "cry", 3, offbeat=1 if off else 0)
    low_lead = lambda g=0.9, ev=4: Voice("dbl", L, -12, g, "cry", 3)           # the tune an octave lower too: heavier, and the mean pitch of the tune stays low
    oct4 = lambda g=0.95: Voice("oct", FLUTE, 12, g, "cry", 3)
    gtr4 = lambda g=0.95: Voice("low", GUITAR, -12, g, "cry", 3)
    hand = [Voice("lead", L, 0, 1.0, "cry", 3, high=77), Voice("lead", FLUTE, 0, 1.0, "cry", 3, low=78)]     # the lead takes the tune as high as it sounds, the flute carries on

    def harmony(scale, g=0.9):
        return [Voice("harm", FLUTE, 12, g, "cry", 3, scale=scale)]

    def fill(bar, length, hot=False):
        t0 = s.tick(bar) + bar_ticks - length
        roll(s, t0, t0 + length, 2, 40, 98)      # step 2: the 45 notes a second cap

    def displaced(sec, i):
        return D["late"](P, sec, i) if "late" in D else (0, False)

    def play(bar, text, voices, vel, sec, i, key_t=0):
        start, cut = displaced(sec, i)
        sing(s, bar, text, voices, transpose=key_t, vel=vel, start=start, cut=cut)

    def acc(bar, chord, bass_name, drum_name=None, guitar=None, harp=None, drone=False, pad=False, level=1.0, drum_level=1.0):
        if bass_name:
            bass(s, bar, chord, BP[bass_name], level, high=54)
        if drone:
            bass(s, bar, chord, D["drone"], level, role="drone", instrument=DIDGERIDOO)
        if drum_name:
            drums(s, bar, DR[drum_name], drum_level)
        if guitar == "pah":
            for t in D["pah"]:
                stab(s, bar, t, chord, "gtr", GUITAR, 47, 64, 54 * level, count=2)
        elif guitar:
            notes_from(chord, D["guitar"][guitar], "gtr", GUITAR, 45, 66, s, bar, level)
        if harp:
            notes_from(chord, harp, "arp", HARP, 54, 72, s, bar, level)
        if pad:
            stab(s, bar, 0, chord, "pad", PLING, 50, 68, 26 * level, count=2, length=bar_ticks, every=4, style="flat")

    def hemi(sec, i, bn, dn):
        return D["hemi"](P, sec, i, bn, dn) if "hemi" in D else (bn, dn)

    if s.offset:                                  # a note on the very first tick (and a second one before the tune) so that no silence opens the song
        first_chord = D["intro"][P][0][1]
        for t in range(0, s.offset, 2):
            score_put = s.put("bass", t, BASS, root_of(first_chord), 70)
    # --- intro, played once (abs bars): the refrain as a single line, quarter roots, no drums -----------------------------------------------------------
    iv = [lead, harp_hit(0.6)] if P == 0 else [lead, gtr_lo(0.6), harp_hit(0.6)]
    for k, (text, chord) in enumerate(D["intro"][P]):
        start, cut = D["late_intro"](P, k) if "late_intro" in D else (0, False)
        sing(s, k, text, iv, vel=90, start=start, cut=cut)
        bass(s, k, chord, BP["Q"], 1.0 if (k >= 2 or P) else 0.8, high=54)       # the bass never leaves (t283), not even in the first bar
        notes_from(chord, D["harp_q"], "arp", HARP, 54, 72, s, k, 0.8)
    if P:
        stab(s, 0, 0, D["intro"][P][0][1], "stab", PLING, 48, 66, 70, count=3, length=4, every=2)

    # --- intro rest (from the loop point): the whole refrain again; the snare gets denser bar by bar, the bass climbs quarters -> eighths --------------------
    rest = D["rest"][P]
    n_rest = len(rest)
    for k, (text, chord) in enumerate(rest):
        sing(s, s.b(k), text, iv, vel=92, start=D["late_rest"](P, k)[0] if "late_rest" in D else 0, cut=D["late_rest"](P, k)[1] if "late_rest" in D else False)
        bass(s, s.b(k), chord, BP["Q" if k < n_rest // 2 else "E_soft"], 1.0, high=54)
        notes_from(chord, D["harp_q"], "arp", HARP, 54, 72, s, s.b(k), 0.9)
        for t, v in D["ramp"][k]:
            s.drum("snare", s.tick(s.b(k), t), SNARE, v * (1.0 if P == 0 else 1.15))
    s.drum("kick", s.tick(s.b(n_rest - 2)), BASEDRUM, 60)
    s.drum("kick", s.tick(s.b(n_rest - 1)), BASEDRUM, 84)
    roll(s, s.tick(s.b(n_rest - 1), bar_ticks - 4), s.tick(s.b(n_rest)), 1, 60, 100)

    # --- A: full drums, the bass nails the root; the tune; a pseudo half time ---------------------------------------------------------------------------
    j = n_rest
    A = D["A"][P]
    nA = len(A)
    for i, (text, chord) in enumerate(A):
        r = i // 8
        if r == 0:
            v = [lead, harp_hit(0.5)] + ([flute12(0.45, 2)] if i % 8 >= 4 else []) + ([gtr_lo(0.6)] if P else [])
        else:
            v = [lead, low_lead(0.85)] + ([flute12(0.6, 2)] if r == 1 else [gtr_lo(0.7, 2)])
        play(s.b(j + i), text, v, (86 if P == 0 else 90) + 2 * r, "A", i)
        bn, dn = hemi("A", i, pick(D["A_bass"][P], i), pick(D["A_drum"][P], i))
        half = D["A_half"][0] <= i < D["A_half"][1]
        acc(s.b(j + i), chord, bn, dn, "pah" if D["pah_guitar"] and not half else ("arp" if not half else None),
            D["harp_arp"] if (r >= 1 and not half) else None, drone=(r >= 1 and not half), level=0.9 if r == 0 else 0.95)
        if i % 4 == 3:
            fill(s.b(j + i), 4, hot=(r >= 1))
    if P:
        stab(s, s.b(j), 0, A[0][1], "stab", PLING, 48, 66, 80, count=3, length=4, every=2)
    j += nA

    # --- B: bass release; the tune an octave up (flute) over the lead an octave down; a pseudo double time ----------------------------------------------------
    B = D["B"][P]
    for i, (text, chord) in enumerate(B):
        if i < 8:
            v = [Voice("lead", FLUTE, 12, 1.0, "cry", 3), Voice("dbl", L, -12, 0.95, "cry", 3), Voice("echo", HARP, -12, 0.5, "none", delay=3)]
        else:
            v = [Voice("lead", FLUTE, 12, 1.0, "cry", 3), Voice("dbl", L, -12, 0.95, "cry", 3), Voice("low", GUITAR, -12, 0.85, "cry", 3)]
        play(s.b(j + i), text, v, 90 if P == 0 else 94, "B", i)
        dbl = D["B_dbl"][0] <= i < D["B_dbl"][1]
        bn, dn = hemi("B", i, pick(D["B_bass"][P], i), pick(D["B_drum"][P], i))
        acc(s.b(j + i), chord, bn, dn, "chug" if i < len(B) * 3 // 4 else None, None, drone=(i >= 8), level=1.0)
        if i % 4 == 3:
            fill(s.b(j + i), 4, hot=True)
    roll(s, s.tick(s.b(j + len(B) - 1), bar_ticks - bar_ticks // 4), s.tick(s.b(j + len(B))), 2, 50, 100)
    j += len(B)

    # --- pre-break (the dominant), stop-time, BREAK (all silent but one bell) ------------------------------------------------------------------------------
    for i, (text, chord) in enumerate(D["pre"]):
        play(s.b(j + i), text, [lead, low_lead(0.9), flute12(0.7, 2), gtr_lo(0.7, 2)], 96, "pre", i)
        acc(s.b(j + i), chord, "ED_b", "drive16", None, None, level=1.0)
        stab(s, s.b(j + i), 0, chord, "stab", GUITAR, 45, 64, 80, count=3, length=0, every=2)
        fill(s.b(j + i), 4, hot=True)
    j += len(D["pre"])
    text, chord = D["stop"]
    sing(s, s.b(j), text, [lead, flute12(0.8, 2)], vel=94)
    bass(s, s.b(j), chord, BP["STOP"], 1.0, high=54)
    drums(s, s.b(j), DR["stop"], 1.0)
    for t in D["stop_at"]:
        stab(s, s.b(j), t, chord, "stab", GUITAR, 45, 64, 90, count=3, length=0, every=2)
    j += 1
    bell(s, s.tick(s.b(j)), D["bell"], 80)
    bass(s, s.b(j), chord, [(t, "r", 66) for t in range(0, bar_ticks, 3)], 1.0, high=54)       # the BREAK keeps the bass: a low pedal re-struck every 0.3 s (a total silence sounds like a stopped server)
    j += 1

    # --- CHORUS: the relative major, a third above, the tune in long notes over the 16th machine gun ------------------------------------------------------
    CH = D["CH"][P]
    for i, (text, chord) in enumerate(CH):
        r = i // 8
        v = [lead, low_lead(0.95), gtr4(0.9)] + harmony(D["scale_ch"], 0.9)
        play(s.b(j + i), text, v, 100, "CH", i)
        bn, dn = hemi("CH", i, pick(D["CH_bass"][P], i), "peak")
        bass(s, s.b(j + i), chord, BP[bn], 1.0, high=54)
        drums(s, s.b(j + i), DR[dn], 1.0)
        if i % 8 == 0 and i < 16:
            stab(s, s.b(j + i), 0, chord, "stab", PLING, 48, 66, 92, count=2, length=0, every=2)
            s.drum("tom", s.tick(s.b(j + i)), BASEDRUM, 100, 54)
        if i % 4 == 3:
            fill(s.b(j + i), 4, hot=True)
    j += len(CH)

    # --- FINALE: the whole structure a whole step up; the last bar leads back to the refrain ------------------------------------------------------------------
    FIN = D["FIN"][P]
    for i, (text, chord) in enumerate(FIN):
        last = i == len(FIN) - 1
        if i < D["fin_split"]:
            v = hand + [low_lead(0.95)]
        else:
            v = hand + [low_lead(0.95), gtr4(0.9)] + harmony(D["scale_fin"], 0.9)
        play(s.b(j + i), text, v, 100, "FIN", i)
        if last:
            bass(s, s.b(j + i), chord, BP["END"], 1.0, high=54)
            for t, vv in ((0, 90), (4, 70), (8, 56)):
                s.drum("kick", s.tick(s.b(j + i), t), BASEDRUM, vv)
            roll(s, s.tick(s.b(j + i), bar_ticks // 2), s.tick(s.b(j + i + 1)), 2, 70, 30)
            continue
        if i < D["fin_split"]:                        # the finale's first half has short notes: ensemble hits (power chords) on the first and the third beat keep it pushing
            for tt in (0, bar_ticks // 2):
                stab(s, s.b(j + i), tt, chord, "stab", GUITAR, 45, 64, 92, count=2, length=0, every=2)
        bn, dn = hemi("FIN", i, pick(D["FIN_bass"][P], i), "peak")
        bass(s, s.b(j + i), chord, BP[bn], 1.0, high=54)
        drums(s, s.b(j + i), DR[dn], 1.0)
        if i % 8 == 0:
            stab(s, s.b(j + i), 0, chord, "stab", PLING, 48, 66, 96, count=2, length=0, every=2)
            s.drum("tom", s.tick(s.b(j + i)), BASEDRUM, 100, 54)
        if i % 4 == 3:
            fill(s.b(j + i), 4, hot=True)
    assert j + len(FIN) == D["body"], (j + len(FIN), D["body"])
    return s
