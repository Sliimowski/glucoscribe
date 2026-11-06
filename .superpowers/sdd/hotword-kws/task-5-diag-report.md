# Task 5 — diagnostic minScore per voice event

## Status

Done. Modified only `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`.
No changes under `kws/`, no adb, no install, no git.

## What changed

- Added `Dtw` import.
- Added service fields:
  - `kwsTemplates: List<Array<FloatArray>>` — copy of templates loaded from assets.
  - `kwsThreshold: Float` — copy of the loaded threshold.
  - `eventMinScore: Float` — minimum accepted-match score for the current event.
- `loadDetector()` now stores the loaded templates/threshold into those fields. On load failure it resets them to `emptyList()` / `Float.MAX_VALUE` so diagnostics degrade to `none` instead of crashing or using stale data.
- In the KWS `accept` branch the existing `kws match score=... total=...` log is unchanged; additionally `eventMinScore` is updated with `minOf`.
- Event lifecycle:
  - On `voice event start`: `eventMinScore = Float.MAX_VALUE`, event row buffer is cleared, current feature row is stored.
  - While `eventOpen`: every computed feature row is appended to the event buffer.
  - On `voice event end`: min DTW score over all templates is computed against the buffered rows and logged.

## Event buffer bounding

- `ArrayDeque<FloatArray>(MAX_EVENT_ROWS)` with `MAX_EVENT_ROWS = 400` (400 frames = 4 s at 10 ms hop).
- Every append is followed by `if (size > MAX_EVENT_ROWS) removeFirst()`.
- Buffer is cleared on event start and event end.
- Rows are the same `FloatArray` objects already allocated by `KWS_FEATURES.logMel(...)` for the real detection path; no additional per-frame allocation was introduced. The buffer is only populated while a voice event is open.

## How minScore is computed

At event end:

```kotlin
val minScore = if (kwsTemplates.isNotEmpty() && eventRows.size >= MIN_EVENT_ROWS) {
    kwsTemplates.minOf { Dtw.score(it, eventRows.toTypedArray()) }
} else {
    null
}
```

- `MIN_EVENT_ROWS = 30` (0.3 s). Shorter events or missing templates produce `minScore=none`.
- Result is diagnostic only; it is not used for firing. The real detection path (`kws.accept`) is unchanged.
- End log format: `voice event end duration=<ms>ms minScore=<x.xxxx|none>`.

## Build output

Command (workdir `C:\work\samokontrol`, `GRADLE_USER_HOME=C:\dev\gradle-home`, `JAVA_HOME=C:\dev\jdk-17`):

```
gradlew.bat :hotword-spike:assembleDebug --console=plain
```

Result: `BUILD SUCCESSFUL in 1s`, 34 actionable tasks (4 executed, 30 up-to-date). `compileDebugKotlin`, `dexBuilderDebug`, `packageDebug`, `assembleDebug` succeeded.

## Risks / concerns

- Event-end DTW over up to 400 rows × all templates runs synchronously on the audio loop thread. For long events or many templates this can briefly delay `AudioRecord.read`, potentially causing an overrun (dropped audio). Acceptable for a diagnostic spike; if it becomes noisy, move the computation off-thread or cap templates.
- `minScore` is computed over the whole buffered event (up to 4 s), not over sliding 2 s windows like `HotwordDetector`. For events longer than 4 s only the last 400 rows are used, so the value is the event's tail minimum, not the full-event minimum.
- `kwsThreshold` is exposed as requested but currently unused by diagnostics; it may produce an unused-field warning in some tooling (no build failure observed).
- If the service is destroyed while an event is open, no end/minScore line is emitted (same as pre-existing behavior).