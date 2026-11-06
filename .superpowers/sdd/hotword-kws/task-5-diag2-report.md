# Task 5 — diagnostic v2: raw best DTW score

## Changes

### `hotword-spike/.../kws/HotwordDetector.kt`
- Added read-only `lastBestScore: Float = Float.MAX_VALUE` with `private set`.
- Updated after every real evaluation (buffer full + `framesSeen % 10 == 0`), before threshold/cooldown checks, using the already computed `bestScore` (no duplicate DTW).
- Empty templates still return before any evaluation, so `lastBestScore` stays `Float.MAX_VALUE`.
- `accept` semantics unchanged: ring 200, step 10, cooldown 50, min over templates, `null` when not firing.

### `hotword-spike/.../kws/HotwordDetectorTest.kt`
- Added `last best score set even without firing`: threshold `0f`, 210 noisy frames, asserts `accept` is always `null` and `lastBestScore` is finite and `< Float.MAX_VALUE`.
- Existing 5 detector tests unchanged.

### `hotword-spike/.../ListenService.kt`
- Removed whole-event DTW diagnostic: `eventRows` buffer, `MAX_EVENT_ROWS`, `MIN_EVENT_ROWS`, `kwsTemplates`/`kwsThreshold` copies, `eventMinScore`, and the now-unused `Dtw` import.
- Added `private var eventBestScore: Float = Float.MAX_VALUE`.
- Reset to `Float.MAX_VALUE` when a voice event starts; the start frame's evaluation is included.
- Every frame while an event is open and `detector.lastBestScore != Float.MAX_VALUE` (including the frame that closes the event), `eventBestScore = minOf(eventBestScore, detector.lastBestScore)`.
- Event end now logs `voice event end duration=<ms>ms best=<x.xxxx|none>`; `none` when no evaluation happened during the event.
- `kws match ...` logging and the other `voice event start/end` fields are unchanged.

## Test evidence

`HotwordDetectorTest`: 6 tests, 0 failures (5 existing + 1 new).
Full unit-test set: DtwTest 4, HotwordDetectorTest 6, MelFeaturesTest 3, TemplateStoreTest 6 — all passing.

## Build output

Command (workdir `C:\work\samokontrol`):

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17";
.\gradlew.bat :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
```

Result: `BUILD SUCCESSFUL in 1s` (8 tasks executed, 32 up-to-date).

## Risks / notes

- `lastBestScore` persists between evaluations; it is only meaningful after at least one scored window. Consumers must compare against `Float.MAX_VALUE` (as `ListenService` does).
- The event best score is sampled at the detector's 10-frame evaluation cadence, not per frame; a very short event may legitimately log `best=none`.
- `eventBestScore` is a field, unlike the old local buffer; it is reset on each event start and never read outside logging.
- No audio is stored; no new dependencies; PCM16 16 kHz mono unchanged.