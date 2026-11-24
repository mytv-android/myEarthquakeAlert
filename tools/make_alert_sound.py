#!/usr/bin/env python3
"""Generates app/src/main/res/raw/eew_alert.wav — the built-in strong-alert sound.

Why a bundled sound (issue #2): the alert used to play whatever ringtone the system
had on TYPE_ALARM. Many default alarm ringtones fade in from silence, which delays
the moment a user notices a strong alert, and the only workaround was to replace the
user's own alarm ringtone with a siren. This asset removes both problems: it is the
same everywhere and it is at full level from the first sample.

Shape (1 s, loops seamlessly — MediaPlayer.isLooping):
  - a rising four-note figure, C6-E6-G6-C7, the shape an earthquake early warning is
    announced with on Japanese broadcasts: bright and electronic, unmistakably an alert
    but not a siren (a wail reads as an ambulance and drowns out speech);
  - the figure repeats once a second, so the alert never goes quiet for long;
  - each note gets a 10 ms attack and 40 ms release, just enough to avoid clicks — that
    is not a fade-in, the figure is at full level immediately;
  - the figure ends at 655 ms and the loop closes on 345 ms of silence, so the loop
    point sits exactly on zero and needs no cross-fade.

Regenerate after editing with:  python tools/make_alert_sound.py
"""

import os
import wave

import numpy as np

RATE = 22050        # mono 16-bit; plenty for a tone figure, ~43 kB per second
PERIOD = 1.0        # one loop = one alert figure, so the figure repeats every second
PEAK = 0.90         # headroom below 1.0 so the DAC never clips
ATTACK = 0.008      # per-note edges: only as long as it takes to avoid a click
RELEASE = 0.025

# (frequency in Hz, duration in seconds), played back to back.
FIGURE = [
    (1046.5, 0.130),  # C6
    (1318.5, 0.130),  # E6
    (1568.0, 0.130),  # G6
    (2093.0, 0.130),  # C7
]
GAP = 0.045          # silence between the notes of the figure

# A touch of upper harmonics gives the figure an electronic edge instead of a flute.
HARMONICS = ((1, 1.0), (2, 0.12), (3, 0.05))

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res", "raw", "eew_alert.wav")


def raised_cosine(n: int) -> np.ndarray:
    """0 -> 1 over n samples, flat at both ends (no click, no ramp you can hear)."""
    if n <= 1:
        return np.ones(max(n, 0))
    return 0.5 - 0.5 * np.cos(np.pi * np.arange(n) / (n - 1))


def note(freq: float, seconds: float) -> np.ndarray:
    count = int(round(seconds * RATE))
    phase = 2.0 * np.pi * freq * np.arange(count) / RATE
    tone = sum(weight * np.sin(order * phase) for order, weight in HARMONICS)
    envelope = np.ones(count)
    attack = min(int(round(ATTACK * RATE)), count)
    release = min(int(round(RELEASE * RATE)), count)
    envelope[:attack] *= raised_cosine(attack)
    envelope[count - release:] *= raised_cosine(release)[::-1]
    return tone * envelope


def main() -> None:
    samples = np.zeros(int(round(PERIOD * RATE)))
    at = 0.0
    for freq, seconds in FIGURE:
        chunk = note(freq, seconds)
        start = int(round(at * RATE))
        samples[start:start + len(chunk)] += chunk
        at += seconds + GAP

    figure_end = at - GAP
    if figure_end > PERIOD:
        raise SystemExit(f"figure runs {figure_end:.3f}s past the {PERIOD:.3f}s loop")

    samples *= PEAK / np.abs(samples).max()
    pcm = np.round(samples * 32767.0).astype("<i2")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with wave.open(OUT, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(pcm.tobytes())
    print(f"wrote {OUT} ({PERIOD:.1f} s, {RATE} Hz mono, {os.path.getsize(OUT)} bytes)")


if __name__ == "__main__":
    main()
