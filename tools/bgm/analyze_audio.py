#!/usr/bin/env python3
"""Measures a recording (a mono 11025 Hz 16-bit WAV): the pitch classes and a key, and per 8 seconds the loudness, the centre of the sound, the share of low / mid / high energy and the notes a second.

    afconvert -f WAVE -d LEI16@11025 -c 1 song.mp3 song.wav        (macOS; the mp3 is the user's own file, kept OUT of the repository)
    python3 -I tools/bgm/analyze_audio.py song.wav

Only numbers come out (tempo, key, density, how the loudness moves): used to make a NEW song with the same feel, never to copy a melody or chords. Pure Python (no numpy). The tempo guess of this script
is rough: use analyze_audio_tempo.py for it.
"""
import wave, struct, math, sys, cmath
w = wave.open(sys.argv[1]); sr = w.getframerate(); n = w.getnframes()
x = struct.unpack('<%dh' % n, w.readframes(n)); x = [v / 32768.0 for v in x]
dur = n / sr
print(f"duration {dur:.1f}s sr {sr}")

# ---- envelope and onsets (hop 256)
H = 256
env = []
for i in range(0, n - H, H):
    e = sum(v * v for v in x[i:i + H]) / H
    env.append(e)
logenv = [math.log(e + 1e-9) for e in env]
# high-passed energy for transient detection: first difference energy
env_d = []
for i in range(0, n - H, H):
    e = sum((x[j] - x[j - 1]) ** 2 for j in range(i + 1, i + H)) / H
    env_d.append(e)
fl = [max(0.0, math.log(env_d[i] + 1e-9) - math.log(env_d[i - 1] + 1e-9)) for i in range(1, len(env_d))]
fps = sr / H
# tempo by autocorrelation of onset flux
m = sum(fl) / len(fl)
fz = [v - m for v in fl]
best = []
for lag in range(int(fps * 60 / 220), int(fps * 60 / 50)):
    s = sum(fz[i] * fz[i + lag] for i in range(0, len(fz) - lag))
    best.append((s / (len(fz) - lag), lag))
best.sort(reverse=True)
print("tempo candidates (bpm, score):", [(round(60 * fps / l, 1), round(s, 3)) for s, l in best[:6]])

# ---- spectral frames
N = 2048; HOP = 1024
win = [0.5 - 0.5 * math.cos(2 * math.pi * k / N) for k in range(N)]
def fft(a):
    n_ = len(a); j = 0
    a = a[:]
    for i in range(1, n_):
        bit = n_ >> 1
        while j & bit:
            j ^= bit; bit >>= 1
        j ^= bit
        if i < j: a[i], a[j] = a[j], a[i]
    length = 2
    while length <= n_:
        ang = -2 * math.pi / length
        wl = complex(math.cos(ang), math.sin(ang))
        for i in range(0, n_, length):
            wn = 1 + 0j
            half = length // 2
            for k in range(half):
                u = a[i + k]; v = a[i + k + half] * wn
                a[i + k] = u + v; a[i + k + half] = u - v
                wn *= wl
        length <<= 1
    return a
frames = []
for st in range(0, n - N, HOP):
    seg = [x[st + k] * win[k] for k in range(N)]
    sp = fft([complex(v, 0) for v in seg])
    mag = [abs(sp[k]) for k in range(N // 2)]
    frames.append(mag)
binhz = sr / N
def chroma_of(mag):
    c = [0.0] * 12
    for k in range(1, len(mag)):
        f = k * binhz
        if 65 <= f <= 2100:
            midi = 69 + 12 * math.log2(f / 440.0)
            c[int(round(midi)) % 12] += mag[k] ** 2
    return c
chroma = [chroma_of(f) for f in frames]
names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
tot = [sum(c[i] for c in chroma) for i in range(12)]
tt = sum(tot)
print("pitch class share:", {names[i]: round(tot[i] / tt, 3) for i in sorted(range(12), key=lambda i: -tot[i])})
maj = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
mnr = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]
def corr(a, b):
    ma = sum(a) / 12; mb = sum(b) / 12
    num = sum((a[i] - ma) * (b[i] - mb) for i in range(12))
    return num / math.sqrt(sum((v - ma) ** 2 for v in a) * sum((v - mb) ** 2 for v in b))
res = []
for r in range(12):
    res.append((corr(tot, [maj[(i - r) % 12] for i in range(12)]), names[r] + ' major'))
    res.append((corr(tot, [mnr[(i - r) % 12] for i in range(12)]), names[r] + ' minor'))
res.sort(reverse=True)
print("key candidates:", [(k, round(s, 3)) for s, k in res[:4]])

# ---- per block (8 s)
fh = HOP / sr
block = int(8 / fh)
print("\nblock  t(s)   rms_db  centroid  low/mid/high%  onsets/s  top pitch classes")
for b in range(0, len(frames), block):
    fr = frames[b:b + block]
    if not fr: break
    t0 = b * fh
    seg = x[int(t0 * sr):int((t0 + 8) * sr)]
    rms = math.sqrt(sum(v * v for v in seg) / max(1, len(seg)))
    db = 20 * math.log10(rms + 1e-9)
    lo = mid = hi = cs = ct = 0.0
    for mag in fr:
        for k in range(1, len(mag)):
            f = k * binhz; p = mag[k] ** 2
            if f < 250: lo += p
            elif f < 1000: mid += p
            else: hi += p
            cs += f * p; ct += p
    s = lo + mid + hi
    cen = cs / ct if ct else 0
    # onsets in this block: flux peaks above threshold
    i0 = int(t0 * fps); i1 = int((t0 + 8) * fps)
    thr = 1.2
    on = 0; last = -10
    for i in range(i0 + 1, min(i1, len(fl) - 1)):
        if fl[i] > thr and fl[i] >= fl[i - 1] and fl[i] >= fl[i + 1] and i - last > fps * 0.06:
            on += 1; last = i
    cc = [sum(chroma[b + j][i] for j in range(len(fr))) for i in range(12)]
    ctot = sum(cc)
    top = sorted(range(12), key=lambda i: -cc[i])[:4]
    print(f"{b // block:3d} {t0:6.1f} {db:7.1f} {cen:8.0f}   {100*lo/s:3.0f}/{100*mid/s:3.0f}/{100*hi/s:3.0f}   {on / 8:6.1f}   " + ' '.join(f"{names[i]}{100*cc[i]/ctot:.0f}" for i in top))

# silence / dynamics
fr_rms = [math.sqrt(env[i]) for i in range(len(env))]
mx = max(fr_rms)
quiet = sum(1 for v in fr_rms if v < 0.1 * mx) / len(fr_rms)
print(f"\nfraction of time below 10% of peak amplitude: {quiet:.2f}")
# per-second rms curve (dB rel peak) coarse for structure
print("rms per 2s (dB rel peak):")
per = int(2 * fps)
line = []
for i in range(0, len(fr_rms), per):
    seg = fr_rms[i:i + per]
    line.append(round(20 * math.log10((sum(seg) / len(seg)) / mx + 1e-9)))
print(line)
