# Task 3: Детектор и cooldown

**Files:**
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/HotwordDetector.kt`
- Test: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/HotwordDetectorTest.kt`

**Interfaces:**
- Consumes: `Dtw.score(template: Array<FloatArray>, window: Array<FloatArray>, band: Float = 0.2f): Float` (Task 2, готов).
- Produces: `class HotwordDetector(private val templates: List<Array<FloatArray>>, private val threshold: Float, private val cooldownFrames: Int = 50)` и `fun accept(frame: FloatArray): Float?` — возвращает оценку при срабатывании, иначе `null`.

**Семантика (зафиксировать в коде):**
- Внутренний кольцевой буфер на 200 кадров (2 с при шаге 10 мс).
- Оценка считается, когда буфер заполнен, и далее каждые 10 принятых кадров.
- Итоговая оценка — минимум по всем шаблонам.
- Срабатывание: `score <= threshold` и с прошлого срабатывания прошло не меньше `cooldownFrames` кадров (50 = 0,5 с). После срабатывания счётчик cooldown сбрасывается.
- Пустой список шаблонов → всегда `null`.

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

    @Test fun `no templates never fires`() {
        val d = HotwordDetector(emptyList(), threshold = 1f)
        repeat(300) { assertNull(d.accept(template[it % 150])) }
    }
}
```

- [ ] **Step 2: Прогнать и убедиться, что падает**

`$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"; & ".\gradlew.bat" :hotword-spike:testDebugUnitTest --tests "*HotwordDetectorTest*"` → FAIL.

- [ ] **Step 3: Реализовать** ровно по семантике выше. Буфер — `ArrayDeque<FloatArray>`, без копирования окна на каждой оценке, если это не усложняет код: при оценке допустимо собрать `Array(buffer.size)`.

- [ ] **Step 4: Прогнать тесты** — PASS, затем `:hotword-spike:assembleDebug`.

## Global Constraints

- Чистый JVM-Kotlin, без внешних библиотек.
- Проект не под git: коммиты не нужны.
- Не менять файлы вне `hotword-spike/`.
- `Dtw.kt` и `MelFeatures.kt` уже готовы и менять их не нужно.