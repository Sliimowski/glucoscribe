# Hotword «запиши сахар»: план реализации (спайк)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** научить `hotword-spike` детектировать фразу «запиши сахар» по шаблонам голоса владельца и измерить полноту и ложные срабатывания на реальном фоне.

**Architecture:** мел-признаки → скользящее окно 2 с → DTW против 10 шаблонов → порог → запись события в лог. Шаблоны и порог готовятся приложением на телефоне, выгружаются через adb и вшиваются в `assets`.

**Tech Stack:** Kotlin, `AudioRecord`, чистый JVM-код для DSP (тестируется JUnit4), Android SDK 36.

**Spec:** `docs/superpowers/specs/2026-10-30-hotword-kws-design.md`

## Global Constraints

- Только личный APK, пакет `ru.samokontrol.hotwordspike`.
- 16 кГц, моно, PCM16, `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- Кадр 25 мс (400), шаг 10 мс (160), 26 мел-полос 100–8000 Гц.
- Окно детекции 2 с, сдвиг 100 мс, DTW с полосой ±20%.
- Аудио не сохраняется и не отправляется; пишутся только признаки и счёт.
- Проект не под git: шаги «Commit» заменены на «проверить сборку/тесты».
- Собирать с `GRADLE_USER_HOME=C:\dev\gradle-home`, `JAVA_HOME=C:\dev\jdk-17`.

## Review Focus

- Тишина и гул (вентилятор, холодильник) не должны давать срабатываний.
- Быстрая и медленная речь: фраза длиннее/короче шаблона на ±30% должна ловиться.
- Телевизор с русской речью не должен срабатывать чаще 2 раз в сутки.
- Свой голос с другого расстояния/громкости должен ловиться (нормализация).
- Сразу после срабатывания не должно быть второго срабатывания (cooldown).

---

### Task 1: Мел-признаки

**Files:**
- Modify: `hotword-spike/build.gradle.kts` (тестовые зависимости, JVM 17)
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/MelFeatures.kt`
- Test: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/MelFeaturesTest.kt`

**Interfaces:**
- Produces: `class MelFeatures(val sampleRate: Int = 16000, val frameLength: Int = 400, val hopLength: Int = 160, val bands: Int = 26)`, `fun logMel(samples: ShortArray): Array<FloatArray>` — по строке на кадр, 26 значений.

- [ ] **Step 1: Добавить тестовую инфраструктуру**

В `hotword-spike/build.gradle.kts` добавить `testImplementation("junit:junit:4.13.2")` и блок
`tasks.withType<Test>().configureEach { javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }) }`
(копия из `app/build.gradle.kts`).

- [ ] **Step 2: Написать падающий тест**

```kotlin
class MelFeaturesTest {
    private val features = MelFeatures()

    @Test fun `frame count follows hop`() {
        val frames = features.logMel(ShortArray(16000))
        assertEquals(97, frames.size) // (16000-400)/160 + 1
        assertEquals(26, frames[0].size)
    }

    @Test fun `silence gives very low energy`() {
        val frames = features.logMel(ShortArray(16000))
        assertTrue(frames.all { row -> row.all { it < -20f } })
    }

    @Test fun `one kilohertz tone lights low bands`() {
        val samples = ShortArray(16000) { i -> (8000 * sin(2 * PI * 1000 * i / 16000)).toInt().toShort() }
        val row = features.logMel(samples)[40]
        val peak = row.indices.maxByOrNull { row[it] }!!
        assertTrue("peak band=$peak", peak in 4..10)
    }
}
```

- [ ] **Step 3: Запустить и убедиться, что падает**

`.\gradlew.bat :hotword-spike:testDebugUnitTest --tests "*MelFeaturesTest*"` → FAIL (класс не найден).

- [ ] **Step 4: Реализовать**

Окно Ханна, FFT (radix-2, 512), треугольные мел-фильтры, `ln(energy + 1e-6)`, вычитание среднего по кадру. Без внешних библиотек.

- [ ] **Step 5: Прогнать тесты** — PASS.

---

### Task 2: DTW-оценка

**Files:**
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/Dtw.kt`
- Test: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/DtwTest.kt`

**Interfaces:**
- Produces: `object Dtw { fun score(template: Array<FloatArray>, window: Array<FloatArray>, band: Float = 0.2f): Float }` — нормированное расстояние (0 — идентично).

- [ ] **Step 1: Тест**

```kotlin
class DtwTest {
    private fun seq(vararg v: Float) = Array(v.size) { floatArrayOf(v[it], v[it] * 2) }

    @Test fun `identical is zero`() = assertEquals(0f, Dtw.score(seq(1f, 2f, 3f), seq(1f, 2f, 3f)), 0.001f)
    @Test fun `stretched is small`() {
        val t = seq(1f, 2f, 3f, 4f, 5f)
        val w = seq(1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f)
        assertTrue(Dtw.score(t, w) < 0.5f)
    }
    @Test fun `noise is far`() {
        val t = seq(1f, 2f, 3f, 4f)
        val w = seq(9f, 1f, 7f, 2f, 8f, 3f)
        assertTrue(Dtw.score(t, w) > 0.5f)
    }
}
```

- [ ] **Step 2: Прогнать** — FAIL.
- [ ] **Step 3: Реализовать** полосу Сакое–Чибы `|i/n - j/m| <= band`, копить стоимость, делить на длину пути.
- [ ] **Step 4: Прогнать** — PASS.

---

### Task 3: Детектор и cooldown

**Files:**
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/HotwordDetector.kt`
- Test: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/HotwordDetectorTest.kt`

**Interfaces:**
- Produces: `class HotwordDetector(private val templates: List<Array<FloatArray>>, private val threshold: Float, private val cooldownFrames: Int = 50)`, `fun accept(frame: FloatArray): Float?` — возвращает оценку при срабатывании, иначе null. Внутри хранит окно 200 кадров и считает каждые 10 кадров.

- [ ] **Step 1: Тест**

```kotlin
class HotwordDetectorTest {
    private val template = Array(150) { i -> floatArrayOf(i / 150f, (i % 7) / 7f) }

    @Test fun `fires on template repeated`() {
        val d = HotwordDetector(listOf(template), threshold = 0.4f)
        var fired = false
        repeat(200) { d.accept(template[it % 150])?.let { fired = true } }
        assertTrue(fired)
    }

    @Test fun `silent on noise`() {
        val d = HotwordDetector(listOf(template), threshold = 0.4f)
        val rnd = Random(1)
        repeat(500) { d.accept(FloatArray(2) { rnd.nextFloat() * 10 })?.let { fail("false trigger") } }
    }

    @Test fun `cooldown blocks immediate second fire`() {
        val d = HotwordDetector(listOf(template), threshold = 0.4f, cooldownFrames = 50)
        val fires = mutableListOf<Float>()
        repeat(400) { d.accept(template[it % 150])?.let { s -> fires += s } }
        assertTrue(fires.size < 4)
    }
}
```

- [ ] **Step 2: Прогнать** — FAIL.
- [ ] **Step 3: Реализовать** кольцевой буфер, шаг 10 кадров, `min` по шаблонам, cooldown в кадрах.
- [ ] **Step 4: Прогнать** — PASS.

---

### Task 4: Запись образцов в спайке

**Files:**
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/SampleRecorder.kt`
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/TemplateStore.kt`
- Modify: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/MainActivity.kt`

**Interfaces:**
- Produces: `SampleRecorder(scope: CoroutineScope, features: MelFeatures) { fun record(durationMs: Int = 2200, onDone: (Array<FloatArray>) -> Unit, onError: (String) -> Unit) }`
- Produces: `TemplateStore` с `fun saveTemplates(dir: File, name: String, templates: List<Array<FloatArray>>)`, `fun loadTemplates(assets: AssetManager, path: String): List<Array<FloatArray>>`, `fun saveThreshold(dir: File, value: Float)`, `fun loadThreshold(assets: AssetManager, path: String): Float`. Формат: `HWK1` + int32 count + int32 rows + int32 dims + float32 данные (little-endian).
- Produces в UI: кнопки «Записать 10 образцов» (счётчик 1/10 → 10/10), «Записать фон 30 с», «Собрать порог», «Выгрузить шаблоны».

- [ ] **Step 1: Реализовать `TemplateStore` + round-trip тест**
```kotlin
@Test fun `templates survive round trip`() {
    val dir = createTempDir()
    val t = listOf(Array(3) { floatArrayOf(1f, 2f) })
    TemplateStore.saveTemplates(dir, "t.bin", t)
    val back = TemplateStore.loadTemplatesFromDir(dir, "t.bin")
    assertEquals(1, back.size); assertEquals(2, back[0][0][1], 0.0001f)
}
```
- [ ] **Step 2: Прогнать** — FAIL; **Step 3: реализовать**; **Step 4: PASS**.
- [ ] **Step 5: Реализовать `SampleRecorder`** (2,2 с записи, обрезка тишины по краям: порог = 10% от максимума RMS).
- [ ] **Step 6: Добавить экран образцов** в `MainActivity`: после 10 записей — `templates.bin` в `filesDir/export`, фон 30 с пишется в `background.bin` (те же признаки), порог = `max(лучшая_оценка_фона) + 0,05`, кнопка выгрузки копирует оба файла в `getExternalFilesDir(null)`.
- [ ] **Step 7: Собрать APK**, установить (спросить подтверждение), записать 10 образцов и 30 с фона с пользователем, выгрузить файлы через adb.

---

### Task 5: Встроить детектор в сервис

**Files:**
- Modify: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`
- Modify: `hotword-spike/src/main/AndroidManifest.xml` (ничего нового не требуется)

**Interfaces:**
- Consumes: `MelFeatures`, `HotwordDetector`, `TemplateStore.loadTemplates/loadThreshold`.
- Produces: строки лога `kws match score=…` и `kws fire total=…`.

- [ ] **Step 1: Загрузить `assets/hotword/templates.bin` и `threshold.txt` в `onCreate`**, при ошибке писать `kws disabled: …` и работать без детектора.
- [ ] **Step 2: В цикле записи** после каждого кадра 160 отсчётов считать признаки кадра и звать `detector.accept(row)`; при срабатывании писать в лог `kws match score=… total=N`.
- [ ] **Step 3: Сохранить VAD-лог** как есть, чтобы сравнить число кандидатов.
- [ ] **Step 4: Собрать, установить (спросить подтверждение), включить сервис.**

---

### Task 6: Замер и решение

**Files:**
- Modify: `hotword-spike/src/main/assets/hotword/templates.bin`, `threshold.txt`

- [ ] **Step 1: Положить выгруженные файлы в assets**, пересобрать и переустановить (спросить подтверждение) — теперь детектор работает без ручной подготовки.
- [ ] **Step 2: Пользователь 20 раз произносит фразу** вразброс за 10 минут. Снять лог, посчитать полноту: `срабатываний / 20`.
- [ ] **Step 3: 2–4 часа обычного фона** (телевизор, разговоры). Снять лог, посчитать ложные: `kws match`, не совпавшие с намеренными произнесениями.
- [ ] **Step 4: Выгрузить `batterystats`**, посчитать `%/час` и сравнить с 2,5%/час.
- [ ] **Step 5: Записать результат в `docs/superpowers/specs/2026-10-30-hotword-kws-design.md`** и доложить: полнота, ложные в сутки, расход. Решение о переносе в основное приложение — после этого.

---

## Проверка критериев

- Полнота ≥ 90% (≥ 18 из 20).
- ≤ 2 ложных срабатывания в сутки.
- ≤ +1%/час к расходу.

Если хотя бы один критерий не выполнен — не переносим в основное приложение, обсуждаем openWakeWord/Vosk с полученными цифрами.