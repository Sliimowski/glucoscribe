# Task 1: Мел-признаки

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
        assertEquals(98, frames.size) // (16000-400)/160 + 1
        assertEquals(26, frames[0].size)
    }

    @Test fun `silence gives very low energy`() {
        val frames = features.logMel(ShortArray(16000))
        assertTrue(frames.all { row -> row.all { it < -8f } })
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

Окно Ханна, FFT (radix-2, 512), треугольные мел-фильтры, `ln(energy + 1e-6)`. **Среднее по кадру не вычитать** — центрирование делает DTW (Task 2). Без внешних библиотек.

- [ ] **Step 5: Прогнать тесты** — PASS.

## Global Constraints

- 16 кГц, моно, PCM16, `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- Кадр 25 мс (400), шаг 10 мс (160), 26 мел-полос 100–8000 Гц.
- Аудио не сохраняется и не отправляется; пишутся только признаки и счёт.
- Проект не под git: коммиты не нужны; по завершении — сборка и тесты.
- Собирать с `GRADLE_USER_HOME=C:\dev\gradle-home`, `JAVA_HOME=C:\dev\jdk-17`, из `C:\work\samokontrol`.