# Normalization study — DTW template self-similarity

Offline JVM study over the 10 real templates in
`hotword-spike/src/main/assets/hotword/templates.bin`.

- Parse check: 10 templates, `dims = 26`, all rows > 0 (ragged `HWK1`, little-endian).
- DTW: Euclidean frame distance, Sakoe-Chiba band `|i/n - j/m| <= 0.2`,
  path-length normalization (sum of cell costs / steps), `Float.MAX_VALUE` on no path.
- 45 unordered template pairs per variant.

## Results

| variant | min | median | mean | max |
|---|---:|---:|---:|---:|
| global | 4.667 | 6.235 | 6.599 | 10.837 |
| bandCenter | 4.564 | 6.260 | 6.520 | 10.642 |
| bandZ | 2.530 | 3.023 | 3.273 | 5.060 |
| frameCenter | 4.057 | 4.810 | 5.122 | 7.116 |
| **frameZ** | **1.944** | **2.426** | **2.511** | 3.558 |
| bandZ_then_frameCenter | 2.166 | 2.503 | 2.610 | **3.408** |
| bandZ_smooth2 | 2.233 | 2.774 | 3.059 | 5.063 |

Per-template best match to another template, `frameZ` (best variant):

| template | selfMin | matchWith |
|---|---:|---:|
| T0 | 2.264 | T3 |
| T1 | 2.454 | T4 |
| T2 | 2.285 | T3 |
| T3 | 2.026 | T6 |
| T4 | 1.978 | T8 |
| T5 | 2.109 | T8 |
| T6 | 1.944 | T7 |
| T7 | 1.944 | T6 |
| T8 | 1.978 | T4 |
| T9 | 2.132 | T7 |

## Which variant wins and why

`frameZ` wins on min, median and mean of the 45 pairs. Per-frame z-scoring removes
both the per-frame loudness (mean) and the per-frame dynamic-range (std) variation,
which are the dominant non-phonetic sources of mismatch in log-mel frames. It leaves
the relative shape of the 26 bands, which is what DTW should compare. The result is a
~2.0x lower median than the current `global` behavior (2.43 vs 6.24) and a much tighter
spread.

`bandZ_then_frameCenter` is a close second with the lowest maximum (3.408) and a
slightly lower tail than `frameZ` (max 3.558 vs 3.408), i.e. it is a bit more robust
against the worst pair. `bandZ_smooth2` improves over `bandZ` but does not beat `frameZ`.
Plain `bandCenter` is essentially identical to `global` and does not help.

## Recommendation

Change the feature normalization before the next on-device round from global centering
to **per-frame z-scoring (`frameZ`)**: subtract each frame's mean over the 26 bands and
divide by that frame's std (std < 1e-3 -> 1.0), applied per template independently and
also to the incoming window. It roughly halves within-phrase DTW distance and tightens
the distribution, which should allow a clearer threshold separation.

If worst-case robustness matters more than the median, consider
`bandZ_then_frameCenter` as the fallback (lowest max, nearly the same median). Do not
bother with `bandCenter` alone.

Implementation note: the transform must be applied consistently to both stored
templates and live windows; otherwise distances are not comparable. Keep this offline
test file only until the change is decided, then delete it.

Test file: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/NormalizationStudyTest.kt`
Raw output: `.superpowers/sdd/hotword-kws/normalization-study.txt`