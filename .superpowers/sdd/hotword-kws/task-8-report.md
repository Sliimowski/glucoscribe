# Task 8 — Event-level detection with trimmed event audio

## Status

Completed. Detection is now event-level: a voice event is buffered with a
pre-roll, trimmed to speech when it ends, and compared once against the trimmed
templates. The per-frame `HotwordDetector.accept(...)` path has been removed
from `ListenService`.

## Files changed

- `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/AudioTrim.kt` (new)
- `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/SampleRecorder.kt`
- `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`
- `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/AudioTrimTest.kt` (new)

`MelFeatures`, `Dtw`, `TemplateStore`, `HotwordDetector` and its tests are
unchanged.

## Changes

### `AudioTrim`

The trim logic was moved verbatim from `SampleRecorder.trimSilence` into a JVM
object:

```kotlin
object AudioTrim {
    fun trimSilence(samples: ShortArray, sampleRate: Int = 16000): ShortArray
}
```

Semantics preserved exactly:

- RMS per 20 ms frame (`sampleRate / 50`);
- gate = 10% of the maximum RMS;
- full-silence input returns the central second (never empty);
- result is expanded symmetrically around the detected region to at least 1 s;
- input shorter than one frame is returned unchanged;
- empty input returns empty.

`SampleRecorder` now calls `AudioTrim.trimSilence(raw, sampleRate)`; its public
API and behavior are unchanged.

### Event buffering in `ListenService`

Per full 160-sample hop (10 ms):

1. The hop is appended to a fixed ring `preRoll` of `PRE_ROLL_SAMPLES = 4800`
   (300 ms at 16 kHz). The ring always holds the most recent audio.
2. When the VAD opens an event (`speechFrames >= 3`), the pre-roll is copied
   out oldest-first into `eventSamples`, and `eventSize = preRollCount`. This
   includes the hop that triggered the event, so the first consonant is kept.
3. While the event stays open, each subsequent hop is appended to
   `eventSamples` up to `MAX_EVENT_SAMPLES = 96_000` (6 s). Once the cap is
   reached, appends are skipped silently and the event stays open (no per-frame
   logging).
4. When the event ends (RMS below 60% of the VAD threshold for > 1 s), the
   collected samples are copied and passed to `AudioTrim.trimSilence`.
5. If the trimmed result is shorter than `MIN_TRIM_SAMPLES = 8000` (0.5 s), the
   event is logged as `trimBest=none` and skipped.
6. Otherwise `KWS_FEATURES.logMel(trimmed)` is computed once, then
   `best = templates.minOfOrNull { Dtw.score(it, eventFeatures) }`.

Event-end logging:

```
voice event end duration=<ms>ms trimBest=<x.xxxx|none> samples=<n>
```

`trimBest` is logged for every event, including non-firing ones, which provides
the measurement needed for recalibration.

Decision: exactly one decision per event. If
`trimBest <= threshold`, the service logs:

```
kws match score=<x.xxxx> total=<N>
```

There is no longer any per-frame DTW evaluation or per-frame firing, so
duplicate triggers within an event cannot occur. `matchCount` still drives the
heartbeat `kwsMatches=` field.

### `loadDetector`

Now returns a small private `KwsModel(templates, threshold)` instead of a
`HotwordDetector`. Loading order and logging are unchanged:

- calibrated files first (`source=files`), validated templates non-empty and
  threshold finite/positive;
- otherwise assets (`source=assets`);
- on failure `kws disabled: ...`;
- on success `kws enabled templates=N threshold=X source=...`.

The threshold/calibration was not changed (~2.47).

## Bounds and lifecycle

- Pre-roll: fixed `ShortArray(4800)` ring, never grows.
- Event buffer: fixed `ShortArray(96_000)`, one allocation per listen loop.
- `eventSize` is reset to 0 after every finalized event.
- Both buffers are local to `listenLoop` and become unreachable when the loop
  exits; no audio is written to disk.
- No unbounded collections are introduced.

## Test evidence

New `AudioTrimTest` (3 tests, all passing):

- silence-only input returns non-empty result of at most 1 s (all zeros);
- a loud 1 s burst inside 3 s of silence is retained in the trimmed region;
- empty input returns empty.

Full module unit test run: **25 tests, 0 failures, 0 errors, 0 skipped**.

```
AudioTrimTest          tests=3 failures=0
DtwTest                tests=6 failures=0
HotwordDetectorTest    tests=6 failures=0
MelFeaturesTest        tests=3 failures=0
NormalizationStudyTest tests=1 failures=0
TemplateStoreTest      tests=6 failures=0
```

## Build output

Command:

```powershell
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"
.\gradlew.bat :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
```

Result:

```
BUILD SUCCESSFUL in 2s
40 actionable tasks: 7 executed, 33 up-to-date
```

No Kotlin warnings from the changed production files. APK produced at
`hotword-spike/build/outputs/apk/debug/hotword-spike-debug.apk`. No adb,
installation or device run was performed.

## Risks / notes

- Event-end latency is unchanged: an event closes after about 1 s of
  below-threshold RMS, so the decision is made ~1 s after speech stops.
- If an event exceeds the 6 s cap, only the first 6 s (including pre-roll) are
  scored. This is intentional to bound memory/CPU; a phrase near the end of a
  very long event would be missed. The end log still reports the full wall
  duration, while `samples` reflects the trimmed buffer length.
- `AudioTrim` always keeps at least 1 s when speech is present. For very short
  utterances (< 1 s) this intentionally includes some surrounding context; the
  0.5 s minimum check therefore mainly rejects near-empty/failed captures.
- `HotwordDetector` remains in the codebase and its tests still run, but the
  service no longer instantiates or uses it.
- Because DTW normalizes each frame independently, trimming primarily removes
  silence padding/uninformative frames; recalibration of the ~2.47 threshold
  should still be based on the new `trimBest` logs collected on device.