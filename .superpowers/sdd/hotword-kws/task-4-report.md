# Task 4 report — запись образцов, калибровка, экспорт

## Что сделано

### `kws/TemplateStore.kt` (новый)
- `saveTemplates(dir, name, templates)` / `loadTemplatesFromDir(dir, name)` / `loadTemplates(assets, path)`.
- `saveThreshold(dir, value)` / `loadThresholdFromDir(dir)` / `loadThreshold(assets, path)`.
- Формат `templates.bin`: ASCII `HWK1`, `int count`, `int rows`, `int dims`, далее `float` little-endian через `ByteBuffer.order(LITTLE_ENDIAN)`.
- `threshold.txt`: `Float.toString()` одной строкой.
- Дополнительно: валидация одинаковой размерности кадров, безопасное чтение при отсутствии/повреждении файла (пустой список), проверка размера буфера.
- **Отклонение от буквы формата:** приведённый в brief round-trip тест использует шаблоны *разной* длины (`Array(3)` и `Array(2)`) при одной паре `rows/dims` в заголовке. Чтобы такой файл переживал round-trip, при неодинаковом числе кадров поле `rows` пишется как `0`, а перед данными каждого шаблона — его собственный `int rows`. Для прямоугольных наборов (основной сценарий, 10 образцов) файл полностью соответствует формату из brief. Это единственный способ удовлетворить и формат, и обязательный тест; подробно описано в KDoc.

### `kws/SampleRecorder.kt` (новый)
- `record(durationMs = 2200)`: запись → обрезка тишины → `MelFeatures.logMel`.
- `recordRaw(durationMs = 30_000)`: сырой PCM16 без обрезки.
- 16 кГц, моно, PCM16, `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- `AudioRecord.getMinBufferSize` проверяется; при `<= 0` или `state != STATE_INITIALIZED` — `IllegalStateException`.
- `AudioRecord.stop()` (в try/catch) и `release()` всегда в `finally`.
- Обрезка: RMS по кадрам 20 мс (320 сэмплов), порог 10% от максимального RMS, первый/последний кадр выше порога; результат расширяется симметрично до минимум 1 с; полная тишина → центральная секунда.
- Аудио не пишется на диск.

### `MainActivity.kt` (изменён)
Добавлены кнопки, существующие Старт / Стоп / Обновить лог сохранены:
- `Записать 10 образцов` — 10 × 2,2 с с паузой 0,7 с, статус `Образец N/10`, затем `Готово: 10 образцов`; после записи считаются self-scores (`sample i self=…`, `selfScores min=… mean=…`).
- `Записать фон 30 с` — `recordRaw(30000)` → `logMel` → окна 200 кадров с шагом 10 → минимум DTW по шаблонам в каждом окне → `minBackground`; порог `minBackground - 0.05`, clamp `[0.01, 5]`; сохраняется в `filesDir/hotword/threshold.txt`; в лог пишется `calibration selfMin=… selfMean=… bgMin=… threshold=…`.
- `Собрать templates.bin` — `filesDir/hotword/templates.bin` + `threshold.txt`.
- `Выгрузить` — копирование обоих файлов в `getExternalFilesDir(null)`, в статусе полные пути.
- Все длительные операции в обычном `Thread` (без корутин), UI — только через `runOnUiThread` с проверкой `isFinishing/isDestroyed`.
- Флаг `busy` защищает от параллельных операций.
- Проверка `RECORD_AUDIO` перед записью: при отсутствии — `requestPermissions`, после выдачи выполняется отложенное действие; без разрешения запись не начинается.
- При старте подхватываются ранее сохранённые шаблоны/порог.

## TDD evidence

### RED
Тест `TemplateStoreTest` создан до реализации; прогон:

```
> Task :hotword-spike:compileDebugUnitTestKotlin FAILED
e: .../TemplateStoreTest.kt:12:9 Unresolved reference 'TemplateStore'.
...
BUILD FAILED
```

(Компиляция невозможна — класса нет.)

Промежуточный прогон после первой версии `TemplateStore` выявил реальный дефект формата:

```
TemplateStoreTest > templates survive round trip FAILED
    java.lang.IllegalArgumentException at TemplateStoreTest.kt:12
TemplateStoreTest > templates file uses little endian FAILED
    java.lang.AssertionError at TemplateStoreTest.kt:41
15 tests completed, 2 failed
```

Причины: (1) тест из brief использует шаблоны разной длины — добавлена поддержка ragged-набора; (2) в собственном little-endian тесте были неверно указаны смещения float (`00 00 80 3F` лежит в байтах 16..19) — тест исправлен.

### GREEN
```
> Task :hotword-spike:testDebugUnitTest
BUILD SUCCESSFUL in 1s
```

Финальный прогон (тесты + сборка):
```
> Task :hotword-spike:testDebugUnitTest
> Task :hotword-spike:assembleDebug

BUILD SUCCESSFUL in 4s
40 actionable tasks: 7 executed, 33 up-to-date
```

Результаты из `build/test-results/testDebugUnitTest`:
```
DtwTest              tests=4 failures=0 errors=0
HotwordDetectorTest  tests=5 failures=0 errors=0
MelFeaturesTest      tests=3 failures=0 errors=0
TemplateStoreTest    tests=3 failures=0 errors=0
```

APK: `hotword-spike/build/outputs/apk/debug/hotword-spike-debug.apk`.

## Изменённые файлы
- `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/TemplateStore.kt` (новый)
- `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/SampleRecorder.kt` (новый)
- `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/MainActivity.kt` (изменён)
- `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/TemplateStoreTest.kt` (новый)

`MelFeatures.kt`, `Dtw.kt`, `HotwordDetector.kt`, `ListenService.kt`, `BootReceiver.kt`, `build.gradle.kts` не менялись.

## Self-review

- **Утечка Activity фоновыми потоками.** Потоки короткоживущие (запись/калибровка), создаются на операцию и завершаются; долгоживущих ссылок нет. `setStatus` проверяет `isFinishing/isDestroyed`, поэтому после уничтожения Activity UI не трогается. Замечание: если Activity уничтожат во время 30-секундной записи, поток продолжится до конца и освободит `AudioRecord`; это осознанно (нельзя оборвать блокирующий `read`), утечки после завершения нет.
- **UI только через `runOnUiThread`.** Да: `setStatus`; `refresh` вызывается на UI-потоке.
- **`AudioRecord` всегда освобождается.** `try/finally`, `stop()` в собственном try/catch, затем `release()`.
- **Запись при отсутствии разрешения.** `withAudioPermission` не запускает запись без `RECORD_AUDIO`; при отказе выводится сообщение.
- **Порог.** Формула и clamp соответствуют brief; при нехватке фона (< 200 кадров) выводится ошибка без записи порога.
- **Логи.** `calibration selfMin=… selfMean=… bgMin=… threshold=…` пишется через `SpikeLogger` в `spike.log`.
- **Чувствительность `selfMin == 0f`** используется как признак «self-scores ещё не считались»; реальный self-score, равный ровно 0, практически невозможен, но это эвристика.

## Concerns
1. **Формат ragged-шаблонов** — расширение формата (см. выше) вызвано противоречием между brief и его обязательным тестом. Прямоугольные наборы (рабочий сценарий) пишутся строго по спецификации.
2. **`loadSavedCalibration` выполняется на UI-потоке** при `onCreate`: только чтение двух небольших файлов, DTW не считается. При большом `templates.bin` это может занять заметное время; для 10×~200×26 это миллисекунды.
3. **Обрезка тишины не тестируется юнит-тестами** — требует Android `AudioRecord`; проверена визуально по коду и контракту. Реальная проверка — только на устройстве.
4. **`getExternalFilesDir(null)`** может вернуть `null` — обработано сообщением в статусе.
5. **Калибровка — однократная, без учёта фоновых окон с речью.** Если во время 30-секундной записи фона кто-то говорит, `minBackground` занизится; это ожидаемое поведение по brief (пользователь должен записывать тишину).
---

# Fix report — ревью Task 4

## Что исправлено

1. **CRITICAL: размер буфера `saveTemplates`.** Старый расчёт прибавлял `templates.size * dims * 4` лишний раз, и файл писался с хвостовыми нулями. Теперь размер считается как `HEADER_BYTES + (ragged ? count*4 : 0) + Σ rows*dims*4` в `Long` с проверкой на `Int.MAX_VALUE`, а на диск пишется ровно `buffer.position()` байт (`buffer.array().copyOf(buffer.position())`).
2. **IMPORTANT: повреждённый/усечённый `templates.bin`.**
   - `readTemplates` проверяет `count`, `headerRows`, `dims` на отрицательные значения и `dims == 0`, затем валидирует `rows` и `count` против `buffer.remaining()` до аллокаций; арифметика в `Long` с делением вместо потенциального переполнения.
   - Для прямоугольного формата требуется точное совпадение `count * rows * dims * 4 == remaining`; для ragged — `rows` каждого шаблона проверяется до создания массива, `ArrayList` больше не предаллоцируется по `count` (защита от OOM при абсурдном count), в конце требуется отсутствие лишних байт.
   - `MainActivity.loadSavedCalibration` оборачивает загрузку шаблонов в `runCatching { ... }.getOrDefault(emptyList())`, как и порог.
3. **IMPORTANT: пустая запись.** `SampleRecorder.recordRaw` бросает `IllegalStateException("microphone returned no data")`, если первый `read()` вернул `<= 0`; `record` бросает то же исключение, если `logMel` вернул пустой набор признаков.
4. **MINOR: обрезка тишины.** Сравнение заменено на `rms <= gate`, поэтому полностью тихая запись уходит в ветку «полная тишина» и обрезается до центральной секунды; контракт «не меньше 1 с, никогда не пусто» сохранён.

## Покрывающие тесты
Добавлены в `TemplateStoreTest`:
- `rectangular file has exactly expected length` — для 2 шаблонов 3×2 ожидается ровно `16 + 2*3*2*4 = 64` байта (ловит регрессию хвостовых нулей).
- `truncated file is rejected` — файл, обрезанный до 20 байт, даёт пустой список без падения.
- `absurd count does not allocate and is rejected` — `count = Int.MAX_VALUE` при 16-байтном файле отклоняется без OOM.

## Команда и результат
```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17";
.\gradlew.bat :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
```
```
> Task :hotword-spike:testDebugUnitTest
> Task :hotword-spike:assembleDebug

BUILD SUCCESSFUL in 5s
40 actionable tasks: 8 executed, 32 up-to-date
```
Из `build/test-results/testDebugUnitTest`:
```
DtwTest              tests=4 failures=0 errors=0
HotwordDetectorTest  tests=5 failures=0 errors=0
MelFeaturesTest      tests=3 failures=0 errors=0
TemplateStoreTest    tests=6 failures=0 errors=0
```
Итого 18 тестов, 0 падений.

## Остаточные замечания
- Старые файлы `templates.bin`, записанные первой версией с хвостовыми нулями, теперь считаются повреждёнными и не загрузятся (`loadTemplatesFromDir` вернёт пустой список) — их нужно пересобрать кнопкой «Собрать templates.bin».
- Проверка `count * perTemplate == remaining` намеренно строгая: файл с любым хвостом отклоняется. Это соответствует формату «ровно count*rows*dims значений».
---

# Fix report — сохранение образцов сразу после записи

Дата: 2026-10-30

## Что изменено

`hotword-spike/src/main/java/ru/samokontrol/hotwordspike/MainActivity.kt`, только `recordSamples()`:

- Сразу после `templates = recorded` и `logSelfScores(recorded)` шаблоны пишутся на диск:
  `TemplateStore.saveTemplates(hotwordDir, TemplateStore.TEMPLATES_FILE, recorded)` в `runCatching`.
- Успех: `logger.log("templates saved count=${recorded.size}")`.
- Ошибка: `logger.log("failed to save templates: <Class> <message>")`; in-memory `templates` остаётся заполненным, исключение не пробрасывается, Activity не падает.
- Кнопки, цикл 10 × 2,2 с + 0,7 с, тексты статусов, фон/калибровка не менялись.
- Загрузка при старте уже есть (`loadSavedCalibration` → `filesDir/hotword/templates.bin`), поэтому образцы теперь переживают пересоздание Activity и `recordBackground()` больше не упирается в «Сначала запишите образцы».

## Команда и результат

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17";
& ".\gradlew.bat" :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
```

```
> Task :hotword-spike:testDebugUnitTest
> Task :hotword-spike:assembleDebug

BUILD SUCCESSFUL in 2s
40 actionable tasks: 8 executed, 32 up-to-date
```

Тесты из `build/test-results/testDebugUnitTest`:

```
DtwTest              tests=4 failures=0 errors=0
HotwordDetectorTest  tests=5 failures=0 errors=0
MelFeaturesTest      tests=3 failures=0 errors=0
TemplateStoreTest    tests=6 failures=0 errors=0
```

Итого 18 тестов, 0 падений. APK: `hotword-spike/build/outputs/apk/debug/hotword-spike-debug.apk`.

## Замечания

- Запись файла выполняется в фоновом потоке (`runInBackground`), UI не блокируется.
- Порог по-прежнему берётся только из 30-секундной фоновой калибровки; сохранение образцов его не трогает.
- Юнит-тестами само сохранение из Activity не покрыто (требует Android-контекста); логика `TemplateStore` уже покрыта `TemplateStoreTest`.

---

# Fix report — gap-midpoint threshold calibration

Дата: 2026-10-30

## Что изменено

`hotword-spike/src/main/java/ru/samokontrol/hotwordspike/MainActivity.kt` — только `recordBackground()` и константа `MAX_THRESHOLD`.

Новая формула порога:

```kotlin
val span = minBackground - selfMean
val calibrated = if (selfMean > 0f && span > 0f) {
    (selfMean + span / 2f).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
} else {
    (minBackground - THRESHOLD_MARGIN).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
}
```

- Порог ставится посередине между средним self-score фразы и минимальным фоном, давая запас с обеих сторон.
- `MAX_THRESHOLD` поднят с `5f` до `20f`; `MIN_THRESHOLD = 0.01f` и `THRESHOLD_MARGIN = 0.05f` без изменений.
- Строка лога `calibration selfMin=… selfMean=… bgMin=… threshold=…` и тексты статуса не менялись.
- `kws/` не затрагивался.

При измеренных `selfMean=5.218273`, `bgMin=6.597653` новый порог ≈ `5.9079` вместо прежних `5.0`, то есть с запасом ~0.69 от среднего фразы и ~0.69 от фона.

## Команда и результат

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17";
& ".\gradlew.bat" :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
```

```
> Task :hotword-spike:compileDebugKotlin
> Task :hotword-spike:testDebugUnitTest
> Task :hotword-spike:assembleDebug

BUILD SUCCESSFUL in 2s
40 actionable tasks: 8 executed, 32 up-to-date
```

Тесты из `build/test-results/testDebugUnitTest`:

```
DtwTest              tests=4 failures=0 errors=0
HotwordDetectorTest  tests=5 failures=0 errors=0
MelFeaturesTest      tests=3 failures=0 errors=0
TemplateStoreTest    tests=6 failures=0 errors=0
```

Итого 18 тестов, 0 падений. APK: `hotword-spike/build/outputs/apk/debug/hotword-spike-debug.apk`.

## Замечания

- Fallback (`selfMean <= 0` или `span <= 0`) сохраняет прежнюю формулу `bgMin - 0.05`; случай `span <= 0` означает, что фон не отделён от фразы, и калибровку стоит перезаписать.
- Проверка на устройстве не выполнялась (adb/установка запрещены); эффект правила нужно подтвердить live-замером.
