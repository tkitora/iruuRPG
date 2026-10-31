#!/usr/bin/env python3
"""Measures a recording (a mono 11025 Hz 16-bit WAV, see analyze_audio.py) BAND BY BAND: the share of the energy in 30-250 Hz, 250-1k, 1-2k, 2-3.5k and 3.5-5k, and the notes a second in each of the three high bands.

    python3 -I tools/bgm/analyze_audio_bands.py song.wav

Why it exists: counting the notes over the WHOLE sound (analyze_audio_tempo.py) is ruled by the bass and misses a quiet sparkle on top; the first edition of gate_gravity was written as "sparse" from that count, and
this script showed a steady 3 to 5 notes a second in the high bands from the first bar to the last.
"""
import wave, struct, math, sys
w = wave.open(sys.argv[1]); sr = w.getframerate(); n = w.getnframes()
x = [v / 32768.0 for v in struct.unpack('<%dh' % n, w.readframes(n))]
N = 1024; HOP = 256
win = [0.5 - 0.5 * math.cos(2 * math.pi * k / N) for k in range(N)]
def fft(a):
    n_ = len(a); j = 0; a = a[:]
    for i in range(1, n_):
        bit = n_ >> 1
        while j & bit:
            j ^= bit; bit >>= 1
        j ^= bit
        if i < j: a[i], a[j] = a[j], a[i]
    length = 2
    while length <= n_:
        ang = -2 * math.pi / length
        wl = complex(math.cos(ang), math.sin(ang)); half = length // 2
        for i in range(0, n_, length):
            wn = 1 + 0j
            for k in range(half):
                u = a[i + k]; v = a[i + k + half] * wn
                a[i + k] = u + v; a[i + k + half] = u - v; wn *= wl
        length <<= 1
    return a
bh = sr / N
bands = {'low<250': (30, 250), 'mid250-1k': (250, 1000), '1k-2k': (1000, 2000), '2k-3.5k': (2000, 3500), '3.5k-5k': (3500, 5000)}
idx = {k: (int(a / bh), int(b / bh)) for k, (a, b) in bands.items()}
fps = sr / HOP
tot = {k: 0.0 for k in bands}
perblock = {}
prev = {k: None for k in bands}
flux = {k: [] for k in bands}
for st in range(0, n - N, HOP):
    sp = fft([complex(x[st + k] * win[k], 0) for k in range(N)])
    mag = [abs(sp[k]) for k in range(N // 2)]
    t = st / sr; b = int(t // 8)
    for k, (a, c) in idx.items():
        e = sum(m * m for m in mag[a:c])
        tot[k] += e
        perblock.setdefault(b, {}).setdefault(k, 0.0)
        perblock[b][k] += e
        lm = [math.log1p(10 * m) for m in mag[a:c]]
        if prev[k] is not None:
            flux[k].append(sum(max(0.0, lm[i] - prev[k][i]) for i in range(len(lm))))
        prev[k] = lm
s = sum(tot.values())
print("energy share:", {k: round(100 * v / s, 2) for k, v in tot.items()})
# onsets per band
for k in ('1k-2k', '2k-3.5k', '3.5k-5k'):
    f = flux[k]; W = int(fps * 0.5); on = []
    for i in range(2, len(f) - 2):
        seg = f[max(0, i - W):i + W]; mean = sum(seg) / len(seg)
        sd = math.sqrt(sum((v - mean) ** 2 for v in seg) / len(seg))
        if f[i] > mean + 1.0 * sd and f[i] >= f[i - 1] and f[i] >= f[i + 1] and (not on or i - on[-1] > fps * 0.06):
            on.append(i)
    per = [sum(1 for o in on if b * 8 <= o / fps < b * 8 + 8) / 8 for b in range(int(n / sr // 8) + 1)]
    print(k, "onsets/s overall", round(len(on) / (n / sr), 2), "per 8s:", [round(v, 1) for v in per])
print("share >1k per 8s block (%):", [round(100 * sum(v for kk, v in d.items() if kk in ('1k-2k', '2k-3.5k', '3.5k-5k')) / sum(d.values()), 1) for b, d in sorted(perblock.items())])
print("share >2k per 8s block (%):", [round(100 * sum(v for kk, v in d.items() if kk in ('2k-3.5k', '3.5k-5k')) / sum(d.values()), 1) for b, d in sorted(perblock.items())])
