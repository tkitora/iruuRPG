#!/usr/bin/env python3
"""Finds the tempo and the rhythm of a recording (a mono 11025 Hz 16-bit WAV, see analyze_audio.py): the notes a second, the most common gap between two notes, and the best tempi by autocorrelation.

    python3 -I tools/bgm/analyze_audio_tempo.py song.wav
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
binhz = sr / N
lo, hi = int(100 / binhz), int(3500 / binhz)
prev = None; flux = []
for st in range(0, n - N, HOP):
    sp = fft([complex(x[st + k] * win[k], 0) for k in range(N)])
    mag = [math.log1p(10 * abs(sp[k])) for k in range(lo, hi)]
    if prev is not None:
        flux.append(sum(max(0.0, mag[i] - prev[i]) for i in range(len(mag))))
    prev = mag
fps = sr / HOP
print("frames", len(flux), "fps", round(fps, 1))
# adaptive peak picking
W = int(fps * 0.5)
onsets = []
for i in range(2, len(flux) - 2):
    seg = flux[max(0, i - W):i + W]
    mean = sum(seg) / len(seg)
    sd = math.sqrt(sum((v - mean) ** 2 for v in seg) / len(seg))
    if flux[i] > mean + 1.0 * sd and flux[i] >= flux[i - 1] and flux[i] >= flux[i + 1] and (not onsets or i - onsets[-1] > fps * 0.08):
        onsets.append(i)
print("onsets:", len(onsets), "=> avg", round(len(onsets) / (n / sr), 2), "per second")
# per 8 s
for b in range(0, int(n / sr), 8):
    c = sum(1 for o in onsets if b <= o / fps < b + 8)
    print(f"  {b:3d}-{b+8:3d}s: {c / 8:.1f} onsets/s")
# IOI histogram
from collections import Counter
ioi = Counter()
for a, bb in zip(onsets, onsets[1:]):
    d = (bb - a) / fps
    if d < 2.0: ioi[round(d / 0.02) * 0.02] += 1
print("most common IOIs (s):", [(round(k, 2), v) for k, v in ioi.most_common(10)])
# autocorrelation of flux over lags
m = sum(flux) / len(flux); fz = [v - m for v in flux]
def ac(lag): return sum(fz[i] * fz[i + lag] for i in range(len(fz) - lag)) / (len(fz) - lag)
cands = []
for bpm10 in range(500, 2200, 2):
    bpm = bpm10 / 10
    lag = fps * 60 / bpm
    li = int(round(lag))
    cands.append((ac(li), bpm))
cands.sort(reverse=True)
seen = []
for s, bpm in cands:
    if all(abs(bpm - b) > 4 for b in seen):
        seen.append(bpm); print("bpm", bpm, "score", round(s, 4))
    if len(seen) >= 8: break
