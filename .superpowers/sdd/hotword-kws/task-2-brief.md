# Task 2: DTW-оценка

**Files:**
- Create: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/Dtw.kt`
- Test: `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/DtwTest.kt`

**Interfaces:**
- Consumes: `Array<FloatArray>` (кадры × 26 признаков), как возвращает `MelFeatures.logMel`.
- Produces: `object Dtw { fun score(template: Array<FloatArray>, window: Array<FloatArray>, band: Float = 0.2f): Float }` — нормированное расстояние (0 — идентично).

**Важное решение (уже принято):** `MelFeatures.logMel` возвращает сырые лог-энергии без центрирования. Поэтому `Dtw.score` центрирует **каждую последовательность отдельно**: вычитает из всех значений последовательности её среднее по всем кадрам и полосам. Это убирает зависимость от громкости и расстояния до микрофона.

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
    @Test fun `loud and quiet copies score the same`() {
        val t = seq(1f, 2f, 3f, 4f, 5f)
        val loud = Array(t.size) { i -> FloatArray(t[i].size) { j -> t[i][j] + 25f } }
        assertTrue(Dtw.score(t, loud) < 0.01f)
    }
}
```

- [ ] **Step 2: Прогнать и убедиться, что падает**

`$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"; & ".\gradlew.bat" :hotword-spike:testDebugUnitTest --tests "*DtwTest*"` → FAIL (класс не найден).

- [ ] **Step 3: Реализовать**

Центрирование обеих последовательностей; стоимость ячейки — евклидово расстояние векторов; полоса Сакое–Чибы `|i/n - j/m| <= band` (ячейки вне полосы = +∞); накопление минимальным путём; результат = стоимость / длина пути. Пустые последовательности → `Float.MAX_VALUE`.

- [ ] **Step 4: Прогнать** — PASS, затем `:hotword-spike:assembleDebug`.

## Global Constraints

- Только JVM-Kotlin, без внешних библиотек.
- Проект не под git: коммиты не нужны.
- Не менять файлы вне `hotword-spike/`.
- `MelFeatures.kt` уже готов: `class MelFeatures(...)` и `fun logMel(samples: ShortArray): Array<FloatArray>`.