# Дневник самоконтроля

Открытое Android-приложение для ведения дневника самоконтроля при сахарном диабете.
Записывает глюкозу, контекст измерения и заметки, показывает динамику, считает
TIR/GMI/CV и формирует PDF/CSV-отчёт для врача.

**Приложение не является медицинским изделием, не рассчитывает дозы инсулина и не
заменяет консультацию врача.**

## Что уже работает (первый вертикальный срез)

- Ввод уровня глюкозы: `ммоль/л` или `мг/дл`, контекст (натощак, до/после еды, перед сном, другое), заметка.
- Инсулин (болюс/базал), углеводы, вес, давление, кетоны, HbA1c — быстрый ввод и таймлайн.
- Голосовой ввод с подтверждением значения, плитка в шторке и виджет (см. ниже).
- Хранение на устройстве в SQLite, без сети и облака.
- Таймлайн измерений с цветовой индикацией гипо/гипер.
- График за 3/7/14/30 дней (по умолчанию — 3 дня) с целевой зоной 3,9–10,0 ммоль/л и датами по оси X.
- Сводка: доля измерений в диапазоне (не TIR по CGM), тяжёлые низкие/высокие,
  средний, SD, CV%, GMI, оценка HbA1c (ADAG), полнота данных по дням.
- Экспорт PDF (сводка, график, разрезы по контекстам, эпизоды ниже диапазона)
  и CSV со всеми типами событий (Excel, `;`, UTF-8 BOM).
- Русский интерфейс, тёмная тема, Material 3.

## Стек

| Слой | Решение |
|---|---|
| Язык / UI | Kotlin, Jetpack Compose, Material 3 |
| Сборка | AGP 9.2.1 (встроенный Kotlin), Gradle 9.4.1, JDK 17 |
| minSdk / targetSdk | 26 / 36 |
| Хранение | SQLiteOpenHelper, схема v2: `events` + типизированные таблицы; Room — после обновления AGP/KSP |
| График | Compose Canvas (без внешней библиотеки) |
| PDF | `android.graphics.pdf.PdfDocument` |
| Распознавание | системный `SpeechRecognizer`, локальный при наличии |
| Тесты | JUnit4, 37 модульных тестов домена |

## Сборка

### Android Studio

1. Открыть папку проекта `C:\work\samokontrol` (ASCII-путь, см. ниже).
2. Settings → Build, Execution, Deployment → Build Tools → Gradle:
   **Gradle JDK = `C:\dev\jdk-17`**.
3. Run `app`.

Если Studio при открытии предложит **Trust Project** — подтвердить.

### Командная строка

```bat
cd C:\work\samokontrol
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## CI (GitHub Actions)

Приватный репозиторий: `Sliimowski/samokontrol`.

На каждый пуш в `main`, тег `v*` и pull request запускается
`.github/workflows/android-ci.yml` — два задания на облачном раннере GitHub
(`ubuntu-latest`, JDK 17, Android SDK 36):

| Задание | Что делает |
|---|---|
| `Unit tests` | `./gradlew :app:testDebugUnitTest` — 26 JVM-тестов домена |
| `Build debug APK` | `./gradlew :app:assembleDebug`, APK сохраняется в артефактах на 30 дней |

Тесты идут в JVM на сервере GitHub, а не на телефоне. Инструментальные тесты
(`androidTest`) и эмулятор в CI не входят. Отчёт тестов и APK скачиваются на
вкладке **Actions** → нужный прогон → **Artifacts**.

## Голосовой ввод

Записать измерение голосом можно тремя способами:

1. **Плитка в шторке.** Опустить шторку → «Изменить» (карандаш) → перетащить
   «Записать сахар» на первую страницу. Дальше: шторка → тап по плитке.
2. **Виджет.** Долгое нажатие на рабочем столе → «Виджеты» → «Дневник
   самоконтроля» → «Запись глюкозы». Тап по виджету сразу открывает запись.
3. **В приложении.** Иконка микрофона в правом верхнем углу вкладки «Дневник».

Сценарий: экран слушает → диалог «Проверьте значение» → при необходимости
поправить число, выбрать контекст, добавить заметку → «Сохранить».
Время измерения — момент начала записи, а не момент нажатия «Сохранить».

Что понимает разбор речи (текущая единица — та, что выбрана в приложении):

- цифры: «5,6», «5.6», «5 6»;
- слова: «пять и шесть», «шесть целых пять десятых», «сто двадцать»;
- обёртки: «сахар пять и шесть», «уровень 5.6».

Диапазон проверяется тем же кодом, что и ручной ввод (18–600 мг/дл).
Если значение не распознано или вне диапазона — предлагается повторить или
ввести вручную. Аудио не сохраняется. При наличии локального распознавателя
(Android 12+) используется он, и аудио не уходит в сеть; иначе экран честно
предупреждает, что системная служба может использовать интернет.

### Почему не кнопка питания

На HyperOS 3 (Xiaomi 2312FPCA6G) долгое нажатие питания захардкожено на
`com.miui.voiceassist`, которого в глобальной прошивке нет. Роль ассистента,
`voice_interaction_service` и настройка `long_press_power_key` системой
игнорируются; подменить пакет нельзя (MIUI блокирует установку). Подробности —
в `docs/superpowers/specs/2026-10-29-voice-input-design.md`.

## Окружение на этой машине

Установлено и зафиксировано (не зависит от временных папок):

| Что | Где | Зачем |
|---|---|---|
| Android Studio 2026.2.2.8 | `C:\Program Files\Android\Android Studio` | IDE |
| JDK 17 (Temurin) | `C:\dev\jdk-17` | Gradle JDK: JBR 25 из Studio ломает запуск JUnit |
| Gradle home | `C:\dev\gradle-home` | ASCII-путь + `org.gradle.java.home=C:/dev/jdk-17` |
| Android SDK 36 | `C:\dev\android-sdk` | `platforms/android-36`, `build-tools/36.0.0`, `platform-tools` |

Пользовательские переменные: `JAVA_HOME=C:\dev\jdk-17`,
`ANDROID_HOME=C:\dev\android-sdk`, `GRADLE_USER_HOME=C:\dev\gradle-home`,
`C:\dev\jdk-17\bin` и `C:\dev\android-sdk\platform-tools` в `PATH`.
Путь к SDK также записан в `local.properties`.

Не ставьте **Gradle JDK = встроенный JBR 25**: Studio тогда пишет
`gradle/gradle-daemon-jvm.properties` с `toolchainVersion=25`, демон уходит на
JDK 25, и JUnit-тесты падают с `Failed to execute test class`. В проекте Gradle
JDK зафиксирован на `C:\dev\jdk-17` (`.idea/gradle.xml`,
`org.gradle.java.home` в `C:\dev\gradle-home\gradle.properties`). Файл
`gradle/gradle-daemon-jvm.properties`, если появится, надо удалить.

Переменные подхватываются новыми процессами: после изменения нужно перезапустить
терминал и Android Studio (в Studio — `File → Restart IDE`).

## Важно: кириллица в пути

Проект лежит в `C:\Users\Артем\Desktop\Воркшоп`. Java на Windows читает arg-файлы
classpath в системной кодировке (CP866), из-за чего воркеры тестов падают с
`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`
и `Could not find or load main class ...GradleWorkerMain`.

Обход, который работает:

1. Junction с ASCII-путём:
   ```bat
   mklink /J C:\work\samokontrol "C:\Users\Артем\Desktop\Воркшоп"
   ```
2. ASCII-кэш Gradle `C:\dev\gradle-home` (переменная `GRADLE_USER_HOME`).
3. Сборка и открытие в Studio — из `C:\work\samokontrol`.

Долгосрочное решение — перенести проект в путь без кириллицы (например,
`C:\dev\samokontrol`) либо включить в Windows «Beta: Use Unicode UTF-8 for
worldwide language support». Тогда обход не нужен.

`android.overridePathCheck=true` в `gradle.properties` отключает предупреждение AGP
о не-ASCII пути; на сборку APK оно не влияет.

## Структура

```
app/src/main/java/ru/samokontrol/diary/
  domain/      Glucose, GlucoseMetrics — чистый Kotlin, покрыт тестами
  data/        GlucoseEntry, DiaryRepository, SqliteDiaryRepository
  export/      ReportExporter — PDF и CSV через FileProvider
  voice/       SpeechGlucoseRecognizer, плитка, виджет, шина запросов
  ui/          DiaryRoot, DiaryViewModel, screens/, components/, theme/
  ui/voice/    диалоги записи и подтверждения, сценарий голосового ввода
app/src/test/  модульные тесты домена
docs/          исследование и решения
```

## Дорожная карта

1. **MVP + 0.2 (сделано):** глюкоза, график, доля измерений в диапазоне,
   инсулин, углеводы, вес, давление, кетоны, HbA1c, PDF/CSV v2.
2. Напоминания (измерение, инсулин, смена места/сенсора).
3. Room после обновления AGP/KSP, шифрование БД (SQLCipher), бэкап/восстановление.
4. Health Connect (глюкоза, давление, питание), импорт/экспорт Nightscout.
5. CGM: приём xDrip-broadcast и уведомлений CGM-приложений, AGP-отчёт.
6. База продуктов: Open Food Facts (ODbL) + локальные частые блюда.

Подробный разбор функций, клинических метрик, интеграций и приоритетов —
в [`docs/research/roadmap-2026-11-06.md`](docs/research/roadmap-2026-11-06.md).

Калькулятор доз в roadmap отсутствует осознанно: это переводит приложение в
категорию медицинского изделия.

## Лицензия

GNU General Public License v3.0, см. `LICENSE`. Проект использует AndroidX,
Jetpack Compose и Kotlin под Apache-2.0; их уведомления — в `NOTICE`.