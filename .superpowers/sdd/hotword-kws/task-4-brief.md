# Task 4: Запись образцов, калибровка, экспорт

**Files:**
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/SampleRecorder.kt`
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/TemplateStore.kt`
- Modify: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/MainActivity.kt`
- Test: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/TemplateStoreTest.kt`

**Interfaces (создать ровно такие):**

```kotlin
class SampleRecorder(
    private val features: MelFeatures,
    private val sampleRate: Int = 16000,
) {
    // Блокирующая запись durationMs, обрезка тишины по краям.
    // Бросает IllegalStateException, если AudioRecord не инициализировался.
    fun record(durationMs: Int = 2200): Array<FloatArray>

    // Запись фона durationMs без обрезки; для калибровки нужен сырой поток.
    fun recordRaw(durationMs: Int = 30_000): ShortArray
}

object TemplateStore {
    fun saveTemplates(dir: File, name: String, templates: List<Array<FloatArray>>)
    fun loadTemplatesFromDir(dir: File, name: String): List<Array<FloatArray>>
    fun loadTemplates(assets: AssetManager, path: String): List<Array<FloatArray>>
    fun saveThreshold(dir: File, value: Float)
    fun loadThreshold(assets: AssetManager, path: String): Float
    fun loadThresholdFromDir(dir: File): Float
}
```

**Формат `templates.bin`:** ASCII `HWK1`, затем `int` count, `int` rows, `int` dims, далее `count * rows * dims` значений `float` little-endian (`DataOutputStream.writeInt/writeFloat` дают big-endian — их использовать нельзя; нужен `ByteBuffer.order(LITTLE_ENDIAN)` или ручная запись байтов).
**Формат `threshold.txt`:** одна строка с числом (`Float.toString`).

**Семантика калибровки:**
- После 10 образцов считаем `selfScores` = минимум `Dtw.score` каждого образца против остальных; пишем в лог.
- Фон 30 с → признаки `MelFeatures.logMel(raw)`; идём окном 200 кадров с шагом 10, для каждого окна берём минимум `Dtw.score` по шаблонам; `minBackground` = минимум по окнам.
- Порог = `minBackground - 0.05`, зажать в `[0.01f, 5f]`.
- Логировать: `calibration selfMin=… selfMean=… bgMin=… threshold=…`.

**UI (добавить в существующий `MainActivity`, не ломая текущие кнопки Старт/Стоп/Обновить лог):**
- `Записать 10 образцов` — по нажатию последовательно пишет 10 образцов по 2,2 с с паузой 0,7 с; в `status` показывает `Образец 3/10`, в конце `Готово: 10 образцов`.
- `Записать фон 30 с` — пишет 30 с, считает и сохраняет порог, пишет результат в `status`.
- `Собрать templates.bin` — сохраняет 10 образцов в `filesDir/hotword/templates.bin` и порог в `filesDir/hotword/threshold.txt`.
- `Выгрузить` — копирует `templates.bin` и `threshold.txt` в `getExternalFilesDir(null)` и показывает полный путь.
- Все длительные операции — в фоновом `Thread`, UI обновлять через `runOnUiThread`. Без корутин (в модуле нет зависимости `kotlinx-coroutines`).

- [ ] **Step 1: Тест `TemplateStore` (round-trip)**

```kotlin
class TemplateStoreTest {
    @Test fun `templates survive round trip`() {
        val dir = Files.createTempDirectory("hwk").toFile()
        val t = listOf(Array(3) { floatArrayOf(1f, -2.5f) }, Array(2) { floatArrayOf(0.25f, 7f) })
        TemplateStore.saveTemplates(dir, "t.bin", t)
        val back = TemplateStore.loadTemplatesFromDir(dir, "t.bin")
        assertEquals(2, back.size)
        assertEquals(3, back[0].size)
        assertEquals(-2.5f, back[0][1][1], 0.0001f)
        assertEquals(7f, back[1][1][1], 0.0001f)
    }

    @Test fun `threshold survives round trip`() {
        val dir = Files.createTempDirectory("hwk").toFile()
        TemplateStore.saveThreshold(dir, 0.42f)
        assertEquals(0.42f, TemplateStore.loadThresholdFromDir(dir), 0.0001f)
    }
}
```

- [ ] **Step 2: Прогнать — RED.**
- [ ] **Step 3: Реализовать `TemplateStore` — GREEN.**
- [ ] **Step 4: Реализовать `SampleRecorder`** (AudioRecord `VOICE_RECOGNITION`, 16 кГц, моно, PCM16; обрезка тишины: RMS по кадрам 20 мс, порог = 10% от максимального RMS, оставить не меньше 1 с; вернуть признаки через `MelFeatures.logMel`).
- [ ] **Step 5: Добавить UI и калибровку** по семантике выше.
- [ ] **Step 6: `:hotword-spike:testDebugUnitTest` и `:hotword-spike:assembleDebug` — PASS.**

## Global Constraints

- 16 кГц, моно, PCM16, источник `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- Аудио не сохраняется на диск: только признаки (`FloatArray`) и порог.
- Проект не под git: коммиты не нужны.
- Не менять `MelFeatures.kt`, `Dtw.kt`, `HotwordDetector.kt`.
- Не добавлять зависимости (кроме уже имеющегося JUnit для тестов).