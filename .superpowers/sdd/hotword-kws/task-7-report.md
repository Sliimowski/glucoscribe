# Task 7 — per-frame z-score + device-calibrated templates

## Changes

### 1. `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/Dtw.kt`

- Replaced global mean centering (`centered`) with per-frame z-score normalization (`normalized`), applied independently to template and window before DTW.
- Each frame: mean over its dimensions, variance, `std = sqrt(var)`, `scale = 1/std` or `1f` when `std < 1e-3f`, then `(x_i - mean) * scale`.
- Kept cell cost (Euclidean), Sakoe–Chiba band `|i/n - j/m| <= band`, path-length normalization, `Float.MAX_VALUE` for empty/unreachable, and `score(template, window, band = 0.2f)` unchanged.
- KDoc updated to describe per-frame z-score instead of global centering.

### 2. `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/DtwTest.kt`

- Rewritten with non-degenerate 4-dimensional frames whose relative component ordering changes between frames.
- Coverage:
  1. `identical is zero`
  2. `loud and quiet copies score the same` (+25 to every value, `< 0.01`)
  3. `per-frame gain does not matter` (one frame ×5, `< 0.01`)
  4. `time-stretched is small` (each frame repeated, `< 0.5`)
  5. `noise is far` (unrelated spectral shapes, `> 0.5`)
  6. `empty sequence returns max` (both argument orders)

### 3. `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/HotwordDetectorTest.kt`

- Adjusted only the test fixture. Its old 2-dimensional frames `[v, 2v]` became degenerate under per-frame z-score (every frame maps to the same `[-1, 1]` vector), causing `silent on noise` to false-trigger. The fixture now uses 3-dimensional frames with a fixed spectral shape and varying gain, preserving the detector behavior the tests intend to exercise. Detection semantics were not changed.

### 4. `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`

- `loadDetector()` now attempts `filesDir/hotword/templates.bin` and `filesDir/hotword/threshold.txt` first via `TemplateStore.loadTemplatesFromDir` / `loadThresholdFromDir`, inside `runCatching`.
- Calibrated files are accepted only if templates are non-empty and the threshold is finite and `> 0`.
- On any failure or invalid calibration, falls back to the existing assets loading path unchanged.
- Logs `kws enabled templates=N threshold=X source=files` or `source=assets`.
- On total failure keeps `kws disabled: <class> <message>` and runs VAD-only.
- Detection semantics, ring buffer, cooldown, and match logging untouched.

## Test evidence

Full unit suite:

```
tests=22 failures=0 errors=0 skipped=0
BUILD SUCCESSFUL
```

DtwTest cases executed:

```
empty sequence returns max
per-frame gain does not matter
noise is far
identical is zero
loud and quiet copies score the same
time-stretched is small
```

Build command (workdir `C:\work\samokontrol`):

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17";
.\gradlew.bat :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
```

Result: `BUILD SUCCESSFUL in 1s`, 40 actionable tasks. Only pre-existing warning remains in `NormalizationStudyTest.kt:98` (nullable `File?`); it is unrelated to these changes.

## Risks

- **Silence/noise frames can be amplified.** A frame with tiny but non-zero variance (`std` just above `1e-3`) is scaled to unit variance, turning nearly flat low-level frames into full-amplitude spectral shapes. These can add distance or, rarely, resemble a template. The `std < 1e-3f` guard only protects exactly/nearly constant frames. Since `ListenService` currently runs KWS whenever the ring buffer is filled regardless of RMS, a real deployment may benefit from gating KWS on the VAD or adding an absolute log-energy floor.
- Per-frame normalization removes gain information entirely, so the matcher cannot distinguish a quiet structured frame from a loud one; this is intended but means SNR-based rejection is no longer implicit.
- Threshold calibration must be redone on-device because score distributions changed (study measured median 2.43 vs 6.24). The new files-first loading makes that possible without rebuilding, but stale calibrated files from before this change may produce mismatched thresholds and should be recalibrated.
- The assets fallback does not validate threshold positivity, matching prior behavior exactly; an invalid asset threshold would still disable/fire incorrectly as before.