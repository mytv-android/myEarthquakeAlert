#!/usr/bin/env python3
"""Generates app/src/main/res/raw/eew_alert.wav — the built-in strong-alert sound.

Why a bundled sound (issue #2): the alert used to play whatever ringtone the system
had on TYPE_ALARM. Many default alarm ringtones fade in from silence, which delays
the moment a user notices a strong alert, and the only workaround was to replace the
user's own alarm ringtone with a siren. This asset removes both problems: it is the
same everywhere and it is at full level from the first sample.

Shape (4 s, loops seamlessly — MediaPlayer.isLooping):
  - a wail sweeping 620 Hz <-> 1480 Hz over 2 s, plus 2nd/3rd harmonics for a reedy,
    harsh timbre that cuts through ambient noise;
  - a 4 Hz amplitude pulse, so the sound reads as an alert siren rather than a ringtone;
  - no fade-in: the first sample is already at full level.

Both the sweep and the pulse are whole cycles within the 4 s, and the phase integral
over one loop is an integer number of cycles, so the loop point is continuous and
click-free without any cross-fade.

Regenerate after editing with:  python tools/make_alert_sound.py
"""

import math
import os
import wave

import numpy as np

RATE = 22050          # mono 16-bit; plenty for a siren, ~172 kB for 4 s
DURATION = 4.0        # one loop
PEAK = 0.88           # headroom below 1.0 so the DAC never clips
WAIL_CENTER = 1050.0  # Hz
WAIL_DEPTH = 430.0    # Hz  -> 620 .. 1480
WAIL_PERIOD = 2.0     # s, whole number of cycles in DURATION
PULSE_HZ = 4.0        # amplitude pulses per second
HARMONICS = ((1, 1.0), (2, 0.22), (3, 0.10))

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res", "raw", "eew_alert.wav")


def sweep_integral(frac: np.ndarray) -> np.ndarray:
    """Integral of one period of the triangle that drives the sweep, at `frac` in [0, 1).

    triangle(u) = 4*|u - 0.5| - 1, whose integral over a full period is zero, so the
    phase is continuous across loop boundaries.
    """
    return np.where(frac <= 0.5, frac - 2.0 * frac ** 2, 2.0 * frac ** 2 - 3.0 * frac + 1.0)


def main() -> None:
    t = np.arange(int(RATE * DURATION)) / RATE
    # Analytic phase: integrating the sweep keeps the endpoint an exact whole number of
    # cycles, which is what makes the loop click-free.
    phase = 2.0 * np.pi * (WAIL_CENTER * t + 2.0 * WAIL_DEPTH * sweep_integral((t / WAIL_PERIOD) % 1.0))
    sample = sum(weight * np.sin(order * phase) for order, weight in HARMONICS)

    # 4 Hz pulse: full level at t = 0 and at every loop boundary, so the alert starts loud.
    pulse = 0.5 + 0.5 * np.cos(2.0 * np.pi * PULSE_HZ * t)
    sample *= pulse ** 1.6

    sample *= PEAK / np.abs(sample).max()
    pcm = np.round(sample * 32767.0).astype("<i2")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with wave.open(OUT, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(pcm.tobytes())
    print(f"wrote {OUT} ({DURATION:.1f} s, {RATE} Hz mono, {os.path.getsize(OUT)} bytes)")


if __name__ == "__main__":
    main()
