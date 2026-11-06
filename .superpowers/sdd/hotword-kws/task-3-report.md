# Task 3 Report: Детектор и cooldown

**Status:** DONE_WITH_CONCERNS (код и тесты зелёные; одно расхождение с текстом теста брифа — см. ниже)

## Implementation

`hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/HotwordDetector.kt`

```kotlin
class HotwordDetector(
    private val templates: List<Array<FloatArray>>,
    private val threshold: Float,
    private val cooldownFrames: Int = DEFAULT_COOLDOWN_FRAMES, // 50
) {
    private val buffer = ArrayDeque<FloatArray>(WINDOW_FRAMES) // 200
    private var framesSeen = 0L
    private var framesSinceFire = Int.MAX_VALUE

    fun accept(frame: FloatArray): Float? {
        if (templates.isEmpty()) return null
        buffer.addLast(frame.copyOf())
        if (buffer.size > WINDOW_FRAMES) buffer.removeFirst()
        framesSeen++
        if (framesSinceFire < Int.MAX_VALUE) framesSinceFire++
        if (buffer.size < WINDOW_FRAMES || framesSeen % SCORE_EVERY != 0L) return null
        val score = bestScore(buffer.toTypedArray())
        if (score > threshold || framesSinceFire < cooldownFrames) return null
        framesSinceFire = 0
        return score
    }
    // bestScore = min по Dtw.score(template, window)
}
```

Semantics implemented verbatim:
- ring buffer 200 frames (`ArrayDeque`), oldest evicted;
- score computed once buffer is full, then every 10 accepted frames;
- final score = min DTW distance over templates;
- fire iff `score <= threshold` AND `framesSinceFire >= cooldownFrames`; counter reset on fire;
- empty templates → always `null`;
- frame is defensively copied (protects history if caller reuses the frame array);
- `framesSeen` is `Long` to avoid overflow on long-running service (2^31 frames ≈ 248 days).

## TDD evidence

### RED

Test file written from brief, then:

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"; & ".\gradlew.bat" :hotword-spike:testDebugUnitTest --tests "*HotwordDetectorTest*" --console=plain
...
e: .../HotwordDetectorTest.kt:13:17 Unresolved reference 'HotwordDetector'.
e: .../HotwordDetectorTest.kt:20:17 Unresolved reference 'HotwordDetector'.
e: .../HotwordDetectorTest.kt:26:17 Unresolved reference 'HotwordDetector'.
e: .../HotwordDetectorTest.kt:33:17 Unresolved reference 'HotwordDetector'.
> Task :hotword-spike:compileDebugUnitTestKotlin FAILED
BUILD FAILED in 911ms
```

(Компиляционный RED — тест не может собраться без класса.)

### Промежуточный RED по существу: бриф-тест `cooldown` противоречит бриф-семантике

После первой реализации 4 из 5 тестов прошли, упал ровно бриф-тест:

```
HotwordDetectorTest > cooldown blocks immediate second fire FAILED
    java.lang.AssertionError at HotwordDetectorTest.kt:29
4 tests completed, 1 failed
```

Отладочный прогон (временный scratch-тест, удалён) показал фактические срабатывания при бриф-параметрах (`cooldownFrames = 50`, 400 кадров):

```
FIRES_AT=[199, 249, 299, 349, 399]
```

То есть 5 срабатываний с интервалом ровно 50 кадров — это **корректная** работа заявленной семантики («не меньше `cooldownFrames` кадров»), но утверждение брифа `fires.size < 4` арифметически недостижимо: 400 кадров / 50 = 8, с учётом оценки раз в 10 кадров — 5.

**Решение:** семантика брифа оставлена без изменений (она явно зафиксирована и нужна Task 5); тест `cooldown blocks immediate second fire` сохранён по смыслу и имени, но проверяет свойство точнее и без ложного числа:

```kotlin
val fires = mutableListOf<Int>()
repeat(400) { i -> d.accept(template[i % 150])?.let { fires += i } }
assertTrue("expected several fires, got $fires", fires.size > 1)
for (k in 1 until fires.size) assertTrue("fires too close: $fires", fires[k] - fires[k - 1] >= 50)
```

Дополнительно добавлен тест `cooldown reduces fire count`: один и тот же вход прогоняется через детекторы с `cooldownFrames = 0` и `50`, утверждается `cooledFires < freeFires`.

### GREEN

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"; & ".\gradlew.bat" :hotword-spike:testDebugUnitTest --tests "*HotwordDetectorTest*" --console=plain
> Task :hotword-spike:testDebugUnitTest
BUILD SUCCESSFUL in 1s
```

Полный набор + сборка:

```
& ".\gradlew.bat" :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
BUILD SUCCESSFUL in 1s
40 actionable tasks: 7 executed, 33 up-to-date
```

Результаты (test-results XML):

```
DtwTest              tests=4 failures=0 errors=0
HotwordDetectorTest  tests=5 failures=0 errors=0
MelFeaturesTest      tests=3 failures=0 errors=0
```

## Files changed

- Added: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/HotwordDetector.kt`
- Added: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/HotwordDetectorTest.kt`
- Ничего вне `hotword-spike/` не менялось; `Dtw.kt`, `MelFeatures.kt` не тронуты; временный scratch-тест удалён.

## Self-review findings

1. **Границы оценки.** Первая оценка происходит на 200-м принятом кадре (индекс 199), затем каждые 10 кадров — соответствует брифу.
2. **Cooldown до первого срабатывания.** `framesSinceFire = Int.MAX_VALUE` + guard от инкремента: первое срабатывание не блокируется и переполнения нет. При `cooldownFrames <= 0` cooldown фактически отключён.
3. **Aliasing.** Добавлена `frame.copyOf()` (26 float на кадр) — иначе Task 5 при переиспользовании буфера кадра испортил бы историю. Стоимость пренебрежима на фоне DTW.
4. **Overflow.** `framesSeen` — `Long`; `% 10` не «съедет» за месяцы работы.
5. **Пустые шаблоны.** Ранний выход до любых аллокаций — ровно `null`, буфер не растёт.
6. **Порог.** `score > threshold` → срабатывание при `<=`, NaN не считается срабатыванием по факту невозможен для непустых последовательностей (Dtw возвращает `Float.MAX_VALUE`).
7. **Kotlin `ArrayDeque`** (не `java.util.ArrayDeque`) — доступен на любом API, без desugaring.

## Concerns

1. **Расхождение брифа.** Тест `cooldown blocks immediate second fire` с `assertTrue(fires.size < 4)` несовместим с заявленной семантикой cooldown при 400 кадрах (фактически 5 срабатываний с шагом 50). Тест адаптирован (проверяет интервал и факт блокировки), семантика не менялась. Если ожидалось именно `< 4`, надо менять не тест, а семантику (например, cooldown 100 кадров) — нужно решение владельца плана.
2. **Стоимость DTW в проде.** `Dtw.score` на каждый вызов аллоцирует центрированные копии и матрицы `n×m` (~150×200 float + int). При оценке 10 раз/с и нескольких шаблонах на устройстве это заметное GC-давление. Для спайка приемлемо, но Task 5 стоит рассмотреть: переиспользуемые буферы в `Dtw`, ограничение числа шаблонов, либо оценка только по «хвосту» окна.
3. **`buffer.toTypedArray()`** каждые 10 кадров — 200 ссылок, мелочь на фоне п.2; оптимизировать раньше DTW смысла нет.
4. **Нет теста на «тишину до заполнения буфера»** отдельно — покрыто косвенно (`no templates` и отсутствие срабатываний в первых 199 кадрах в `silent on noise`).