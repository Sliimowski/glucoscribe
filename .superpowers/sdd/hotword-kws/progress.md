# SDD ledger — plan: C:\work\samokontrol\docs\superpowers\plans\2026-10-30-hotword-kws.md

Project is NOT a git repository: no commits, no worktrees. Verification is
`gradlew` build + unit tests. Review packages are the changed file list plus
full file contents.

Spec: docs/superpowers/specs/2026-10-30-hotword-kws-design.mdTask 1: complete. Ruling: logMel returns raw ln(energy+1e-6), no per-frame centering; centering moved into Dtw.score (each sequence centered by its own global mean). Review: approved, minors only.
Task 2: complete. Dtw.score: independent centering of both sequences, Sakoe-Chiba 0.2, Euclid, path-length normalized, MAX_VALUE on empty/unreachable. Review: approved, minors only (missing empty-input test, silent truncation on mismatched vector size).
Task 3 ruling: brief test ires.size < 4 was arithmetically wrong for 400 frames with cooldown 50 (5 fires is correct). Replacement test must assert consecutive fire interval >= cooldownFrames and compare against cooldown=0.
Task 3: complete. HotwordDetector: 200-frame ring, evaluate at fill then every 10 frames, min over templates, cooldown in frames. Review: approved, minors only (exact fire positions untested, allocations per evaluation to revisit in Task 5, NaN not guarded).
Task 4: complete after 1 fix round (file length, corrupt-file validation, empty-read guard, silence trim). Review: approved.
Task 5: complete after 1 fix round (read-loop busy-loop, running flag). Review: approved. Manual on-device verification pending with Task 6.
Task 6: measurement complete. frameZ per-frame z-score chosen from local study (template self-distance 6.24 -> 2.43). Calibration: selfMin=1.94 selfMean=2.11 bgMin=2.82 threshold=2.47. Event-level trimmed scoring: 7 events, trimBest 2.29-3.72, 2/7 fired. Verdict: within-phrase spread exceeds background gap; criterion >=90% completeness with <=2 FP/day not achievable with template DTW. Do NOT port to main app. Recorded in design doc.
