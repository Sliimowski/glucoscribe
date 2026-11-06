# Task 2 Report: DTW-оценка

## Что реализовано

Создан `object Dtw` с методом:

```kotlin
fun score(template: Array<FloatArray>, window: Array<FloatArray>, band: Float = 0.2f): Float
```

Поведение:
- **Центрирование**: каждая последовательность центрируется отдельно — из всех значений вычитается среднее по всем кадрам и полосам (согласно решению в брифе).
- **Стоимость ячейки**: евклидово расстояние между центрированными векторами кадров.
- **Полоса Сакое–Чибы**: `|i/n - j/m| <= band`; ячейки вне полосы = `+∞`.
- **Накопление**: минимальный путь по трём предшественникам (диагональ, вверх, влево); параллельно хранится длина пути.
- **Результат**: суммарная стоимость / длина пути.
- **Пустые последовательности** → `Float.MAX_VALUE`.
- **Недостижимый конец** (например, при band, отсекающем финальную ячейку) → `Float.MAX_VALUE`.

Файлов затронуто только два, оба внутри `hotword-spike/`. Сторонних библиотек нет.

## TDD-доказательства

### RED — тест написан, класс отсутствует

Команда:

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"; & ".\gradlew.bat" :hotword-spike:testDebugUnitTest --tests "*DtwTest*" --console=plain
```

Фрагмент вывода:

```
> Task :hotword-spike:compileDebugUnitTestKotlin FAILED
e: .../DtwTest.kt:10:56 Unresolved reference 'Dtw'.
e: .../DtwTest.kt:15:20 Unresolved reference 'Dtw'.
e: .../DtwTest.kt:21:20 Unresolved reference 'Dtw'.
e: .../DtwTest.kt:27:20 Unresolved reference 'Dtw'.
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':hotword-spike:compileDebugUnitTestKotlin'.
> Compilation error. See log for more details
BUILD FAILED in 903ms
```

### GREEN — реализация добавлена, тесты проходят

Та же команда:

```
> Task :hotword-spike:compileDebugKotlin
> Task :hotword-spike:compileDebugUnitTestKotlin
> Task :hotword-spike:testDebugUnitTest
BUILD SUCCESSFUL in 1s
```

Отчёт JUnit подтверждает все 4 теста:

```xml
<testsuite name="ru.samokontrol.hotwordspike.kws.DtwTest" tests="4" skipped="0" failures="0" errors="0" ...>
```

### Полный прогон + сборка

```
& ".\gradlew.bat" :hotword-spike:testDebugUnitTest :hotword-spike:assembleDebug --console=plain
...
> Task :hotword-spike:assembleDebug
BUILD SUCCESSFUL in 1s
```

Все юнит-тесты модуля (включая `MelFeaturesTest`) прошли, `assembleDebug` собрался.

## Изменённые файлы

- **Создан** `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/kws/Dtw.kt`
- **Создан** `hotword-spike/src/test/java/ru/samokontrol/hotwordspike/kws/DtwTest.kt` (тесты ровно по брифу)

## Self-review

- Интерфейс и значения по умолчанию (`band = 0.2f`) совпадают с брифом дословно — Task 3 сможет вызвать `Dtw.score(template, window)`.
- Центрирование выполняется внутри `score`, не мутирует входные массивы (создаются копии).
- `Float.MAX_VALUE` возвращается и для пустого входа, и для недостижимого конца — детектор сможет отличить «плохо» от обычного значения.
- Формула полосы реализована буквально как `|i/n - j/m|`, деление на размеры (а не `n-1`/`m-1`), как в брифе.
- Лишней функциональности нет: нет обратного прохода/выравнивания, нет кэшей, нет зависимостей.
- Стиль (KDoc по-русски, компактность) соответствует `MelFeatures.kt`.

## Concerns

1. **Полоса и очень короткие последовательности.** При использовании `i/n` финальная ячейка может оказаться вне полосы, если длины сильно различаются и одна из них очень мала (например, 1 кадр против 2). Тогда вернётся `Float.MAX_VALUE`. Для реальных последовательностей (десятки кадров) это не проблема, но стоит помнить.
2. **Разная размерность векторов.** `distance` сравнивает по `min(size)` — молчаливое усечение. В рамках пайплайна обе последовательности всегда 26 полос, так что это лишь защитная мера, а не штатный путь.
3. **Нормировка на длину пути.** Результат зависит от длины пути, а не от абсолютной длины; это соответствует брифу и тестам, но порог детектора в Task 3 нужно будет калибровать эмпирически.