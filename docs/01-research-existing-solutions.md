# Дневник самоконтроля диабета — исследование готовых решений и модулей

Дата: 2026-10-29. Статус: первичное исследование перед выбором архитектуры.

## 1. Короткий вывод

Для задачи «дневник самоконтроля» **не нужно писать с нуля** — есть два зрелых open-source Android-приложения, которые покрывают почти весь функционал:

| Что нужно | Готовое решение | Лицензия | Можно ли брать код |
|---|---|---|---|
| Дневник: сахар, инсулин, углеводы, HbA1c, вес, АД, пульс, SpO2, еда, статистика, PDF/CSV, напоминания, RU-локализация | **Diaguard** (`Faltenreich/Diaguard`) | GPL-3.0 + доп. условие | Только если ваше приложение тоже GPL |
| CGM: график, тревоги, Nightscout/Tidepool, Health Connect, AGP/TIR | **Strimma** (`psjostrom/strimma`) | GPL-3.0 | Только GPL |
| Сбор данных с сенсоров (Libre/Dexcom/Medtronic и др.) | **xDrip+** | GPL-3.0 | Только GPL, код хрупкий |
| Замкнутый контур (автодозация) | **AndroidAPS** | AGPL-3.0 | Не переиспользовать |
| CGM-хаб с поддержкой Libre/Dexcom/Sibionics | **Juggluco** | GPL-3.0 | Только GPL; удобен как внешний источник |
| Формулы IOB/COB/базала (если понадобятся) | **oref0** | MIT | Да, но это JS; для дневника не нужно |
| Лёгкий Android-дневник с импортом из xDrip/Juggluco | **DiaX-Tracker** | MIT | Да, самый permissive из Android-дневников |
| Облачная платформа + веб-просмотр | **Tidepool platform / blip** | BSD-2-Clause | Да, но это Go/JS-бэкенд, не Android |
| Данные о еде | **Open Food Facts** | ODbL (данные), AGPL (код) | Данные — да, с атрибуцией |
| Стандарт обмена | **HL7 FHIR + LOINC**, **Nightscout API** | открытые спеки | Да |

**Главный вывод по лицензиям:** все Android-приложения в нише — GPL/AGPL (copyleft). Если приложение планируется **закрытым/коммерческим**, переиспользовать их код нельзя — можно только изучать архитектуру и UX. Тогда база — свои модули на Kotlin/Compose + открытые библиотеки (Apache/MIT) + данные ODbL/CC0.

**Главный вывод по регулированию:** пока приложение только *записывает и показывает* данные — это, как правило, не медицинское изделие. Как только оно *считает и рекомендует дозу инсулина* или *интерпретирует* показатели — это медицинское изделие, требующее регистрации в Росздравнадзоре (с 01.03.2025 — Постановление Правительства РФ № 1684). Это меняет сроки и стоимость на порядок.

---

## 2. Ключевые open-source проекты

### 2.1 Diaguard — ближайший аналог «дневника самоконтроля»

- Репозиторий: https://github.com/Faltenreich/Diaguard
- Лицензия: **GPL-3.0** + отдельное условие: «permission for redistribution must be manually requested in advance» (нужно письменное разрешение автора на распространение/форк).
- Стек: **Java**, Android Views + XML, ORMLite + SQLite, MPAndroidChart, Retrofit/Gson, PDFjet, Open Food Facts.
- Активность: живой (последний push 2026-10-14), 146★, 33 форка, релиз 3.15.2. Идёт переписывание на **Kotlin Multiplatform** в ветке `feature/multiplatform` (PR #51).
- Функционал (совпадает с ТЗ почти 1:1):
  - сахар, инсулин, углеводы, HbA1c, активность, вес, пульс, давление, сатурация;
  - настраиваемые единицы (mg/dL / mmol/L);
  - график сахара, подробные логи;
  - базальная скорость, фактор коррекции, углеводный коэффициент;
  - база продуктов (тысячи записей);
  - экспорт **PDF и CSV**, бэкап, напоминания, оценка HbA1c, статистика;
  - тёмная тема; локализации: EN, DE, FR, ES, IT, **RU**, TR.
- Ограничения: монолит на Java, MVC + DDD-пакеты, тестов мало; поддержка замедлена из-за перехода на KMP; GPL + доп. условие.

**Вывод:** это лучший донор идей и данных (структура БД, набор показателей, PDF-отчёт). Форк имеет смысл, только если вы согласны на GPL и на письменное разрешение автора. Иначе — изучаем и повторяем на Kotlin.

### 2.2 Strimma — современный CGM-компаньон на Kotlin

- Репозиторий: https://github.com/psjostrom/strimma
- Лицензия: **GPL-3.0**.
- Стек: **Kotlin, Jetpack Compose, Material 3, Room, Hilt, DataStore, EncryptedSharedPreferences, Ktor, Glance**; Android 13+; тесты Robolectric.
- Что умеет:
  - источники: уведомления CGM-приложений (Dexcom, Libre 2, CamAPS, Juggluco, xDrip+, Medtronic, Eversense), xDrip-broadcast, Nightscout follower, **LibreLinkUp**;
  - график с зумом, прогноз «Low in X min», уведомления, виджет;
  - **Health Connect**: упражнения, пульс, шаги; запись глюкозы обратно;
  - **Nightscout** push `/api/v1/entries`, Tidepool upload, xDrip-broadcast для часов;
  - статистика: **TIR, GMI, CV%, AGP**, постпрандиальный анализ, IOB;
  - локальный HTTP-сервер для Garmin.
- Ограничения: молодой проект (5★), один автор, много «умной» логики (IOB, прогнозы) — то, что с точки зрения регулирования уже пограничная зона.

**Вывод:** лучший референс для Kotlin/Compose-слоя и интеграций (Health Connect, Nightscout, CGM). Код под GPL.

### 2.3 xDrip+ — «золотой стандарт» чтения CGM

- Репозиторий: https://github.com/NightscoutFoundation/xDrip
- Лицензия: **GPL-3.0**, Java, 1815★, активен (push 2026-10-23).
- Ценность: реверс-инжиниринг протоколов Libre/Dexcom/Medtronic/Eversense, broadcast-API `ACTION_NEW_BG_ESTIMATE`, алгоритмы калибровки, коллекторы.
- Важно: протоколы сенсоров неофициальны и меняются с прошивками. Переносить этот код в закрытый продукт нельзя, а поддерживать его в одиночку дорого.
- Практический путь: **не читать сенсор самим**, а принимать xDrip-broadcast / уведомления CGM-приложения (как делает Strimma). Это резко снижает юридические и инженерные риски.

**Документированные точки интеграции xDrip+ (важно для нас):**
- входящий broadcast `com.eveningoutpost.dexdrip.NS_EMULATOR` с JSON `{type:"sgv", date, sgv, direction}`;
- исходящие broadcast’ы `com.eveningoutpost.dexdrip.BgEstimate` (их использует AAPS);
- локальный HTTP-сервер `127.0.0.1:17580` (`/sgv.json`, `/pebble`);
- настройка «Inter-app settings / Broadcast data».
То есть дневник может получать сахар вообще без разрешений BLE и без драйверов — достаточно `BroadcastReceiver` и/или HTTP-запроса на localhost.

### 2.3.1 Juggluco — второй по важности источник CGM
- https://github.com/j-kaltes/Juggluco, **GPL-3.0**, активен (коммиты 22.10.2026).
- Поддерживает Libre 1/2/3, Dexcom G7/ONE+, Sibionics GS1Sb/GS3, Accu-Chek SmartGuide, CareSens Air, Linx/Aidex.
- Отдаёт данные в xDrip/AAPS/Nightscout — то есть для нас это ещё один внешний источник, а не библиотека.
- Полезен как «второй эндпоинт» в настройках импорта: xDrip-broadcast + Juggluco-broadcast покрывают большинство российских сенсоров.

### 2.4 AndroidAPS — не переиспользовать

- Репозиторий: https://github.com/nightscout/AndroidAPS
- Лицензия: **AGPL-3.0**, Kotlin, 1231★, очень активен.
- Это система автоматической доставки инсулина (closed loop). Код огромный, завязан на безопасность, требует Nightscout и помпы.
- Для дневника не нужен. AGPL + медицинская ответственность делают переиспользование бессмысленным.

### 2.5 Tidepool — открытая облачная платформа и просмотр

- `tidepool-org/platform` (Go, **BSD-2-Clause**) — API и модель данных.
- `tidepool-org/blip` (JS/React, **BSD-2-Clause**) — веб-просмотр данных.
- `tidepool-org/uploader` — загрузчик данных с устройств.
- Ценность: **permissive-лицензия** и продуманная модель данных (bg, bolus, basal, carbs, device events, wizard). Можно легально взять идеи модели и даже код бэкенда.
- Ограничение: не Android-приложение; для «дневника на телефоне» — только источник схемы данных и, при желании, синхронизации.

### 2.6 Glucosio — исторический пример, мёртв

- https://github.com/Glucosio/glucosio-android — GPL-3.0, Java, 339★, последний push 2022 → фактически заброшен.
- Интересен как ранний open-source дневник (сахар, HbA1c, холестерин, вес, давление, напоминания). Брать код не стоит, кроме идей UX.

### 2.7 DiaX-Tracker — единственный permissive Android-дневник

- **MIT**, Android, новый (2026), умеет импорт из xDrip/Juggluco.
- Сырой и маленький, но лицензия позволяет брать код в любой проект (в т.ч. закрытый) с сохранением нотиса.
- Стоит изучить хотя бы ради парсеров импорта и структуры экранов.
- Если бы проект был закрытым — это был бы главный кандидат на базис вместо Diaguard.

### 2.8 Проприетарные (не open source)

Diabetes:M, mySugr, SocialDiabetes, DiaMeter, приложение «Диабет» (RuStore) — закрытый код. Полезны как **конкурентный анализ** функций и цен, но не как источник модулей.

---

## 3. Что переиспользовать по слоям

| Слой | Готовое / рекомендуемое | Лицензия | Комментарий |
|---|---|---|---|
| UI | Jetpack Compose + Material 3 | Apache-2.0 | Стандарт 2026 |
| Архитектура | MVVM + UDF, Hilt, Coroutines/Flow | Apache-2.0 | Как в Strimma |
| Локальная БД | Room + SQLite; при чувствительных данных — SQLCipher (`net.zetetic:sqlcipher-android`) | Apache-2.0 / BSD | Шифрование БД для медданных |
| Настройки/секреты | DataStore + EncryptedSharedPreferences | Apache-2.0 | |
| Графики | **Vico** (Compose) или MPAndroidChart (Views) | Apache-2.0 / MIT | Vico лучше для Compose |
| Напоминания | WorkManager + NotificationCompat, точные будильники через AlarmManager | Apache-2.0 | Учитывать Doze и разрешения Android 13+ |
| Health Connect | `androidx.health.connect:connect-client` | Apache-2.0 | Глюкоза, давление, питание, вес, пульс, сон, упражнения |
| CGM | xDrip-broadcast, парсинг уведомлений, LibreLinkUp | — | Как в Strimma; не писать свои драйверы сенсоров |
| BLE (если всё же свои датчики) | `android.bluetooth.le` GATT, **Nordic Android-BLE-Library** (BSD-3) или **Kable** (Apache-2.0, KMP) | BSD-3 / Apache-2.0 | Только для своих устройств с документированным протоколом; Nordic надёжнее сырого GATT |
| Экспорт PDF | `android.graphics.pdf.PdfDocument` или **PdfBox-Android** | Apache-2.0 | PDFjet в Diaguard — без чёткой свободной лицензии, лучше не брать |
| Экспорт CSV | `kotlin-csv` | Apache-2.0 | |
| Еда | **Open Food Facts** API + локальный кэш; **USDA FoodData Central** (public domain) | ODbL / CC0 | OFF требует атрибуции и share-alike для производной БД |
| Русские углеводы | своя таблица/импорт + OFF; готовой свободной RU-БД нет | — | Нужен ручной контент или договор с источником |
| Синхронизация | Nightscout REST (`/api/v1/entries`, `/api/v1/treatments`, `/api/v1/profile`) | AGPL (сервер) | Формат открыт, можно импорт/экспорт |
| Стандарт данных | HL7 FHIR R4/R5 `Observation`, LOINC `2339-0` (Glucose [Mass/volume] in Blood), CGM IG | открытые спеки | Пригодится для выгрузки врачу/ЕГИСЗ |
| Тесты | JUnit5, Turbine, Robolectric, Compose UI Test | Apache-2.0 | |

---

## 4. Модель данных (минимально достаточная)

Проверенная схема (Diaguard/Nightscout/Tidepool сходятся):

```
Patient/Profile: unit (mg/dL|mmol/L), targetLow, targetHigh,
                 ICR (carb ratio), ISF (correction factor), basalRate, dia (insulin duration)

Event:
  id, type, timestamp, note, tags[]
  type = GLUCOSE      -> value, measurementContext (fasting/preprandial/postprandial/random), source (manual/meter/cgm)
       | INSULIN      -> kind (bolus|basal), units, insulinType
       | CARBS        -> grams, foodRef, mealType
       | HBA1C        -> percent / mmol/mol
       | WEIGHT       -> kg
       | BLOOD_PRESSURE -> sys, dia, pulse
       | OXYGEN       -> SpO2
       | KETONES      -> mmol/L
       | ACTIVITY     -> minutes, intensity
       | NOTE
Food: id, name, brand, carbsPer100g, protein, fat, kcal, source, barcode
```

Схема сверена с реальными сущностями Diaguard (`shared/data/database/entity/`): `Entry`, `BloodSugar`, `Insulin`, `Meal`, `Food`, `FoodEaten`, `HbA1c`, `Activity`, `Weight`, `Pulse`, `Pressure`, `OxygenSaturation`, `Tag`, `EntryTag`. То есть набор показателей и связей уже проверен на живом приложении — можно брать как эталон.

Единицы: `mg/dL = mmol/L × 18.0182` (клинически часто округляют до 18). Хранить **в одной канонической единице** — удобнее целое `Int` в mg/dL (без ошибок округления), а в UI показывать mmol/L с одним знаком. Health Connect принимает `Double` в mmol/L, поэтому на границе интеграции — явная конвертация и тесты на диапазон 1.0–33.3 mmol/L / 18–600 mg/dL.

Метрики: TIR/TITR/TAR, средний сахар, SD, CV%, GMI (`3.31 + 0.02392 × mean_mgdl`), оценка HbA1c, количество гипо, AGP-профиль по часам.

---

## 5. Интеграции

### Nightscout
- REST, MongoDB; ключевые коллекции `entries` (сахар), `treatments` (инсулин/еды/заметки), `profile`.
- **API v1** (legacy): `/api/v1/entries.json`, `/api/v1/treatments.json`, `/api/v1/profile.json`, авторизация заголовком `api-secret` (SHA-1 от пароля).
- **API v3**: обобщённый REST `/api/v3/{collection}` с `GET/POST/PUT/PATCH/DELETE`, фильтрами `sort$desc`, `limit`, `fields`; инкрементальная синхронизация через `srvModified`, `/lastModified`, `/history/{ts}` и мягкое удаление `isValid:false`; авторизация — Bearer JWT.
- Практика: **экспорт в Nightscout** как дополнительный канал, а не как обязательная зависимость. Если делать синхронизацию — сразу на v3, там есть инкрементальность.

### Health Connect
- `BloodGlucoseRecord` — `READ_BLOOD_GLUCOSE` / `WRITE_BLOOD_GLUCOSE`; значение хранится в **mmol/L** (0–50).
- `NutritionRecord`, `BloodPressureRecord`, `WeightRecord`, `HeartRateRecord`, `OxygenSaturationRecord`, `ExerciseSessionRecord`, `SleepSessionRecord`.
- **Инсулина и HbA1c в Health Connect нет** — эти данные хранятся только локально. (В Medical Records есть FHIR `Medication*`, но это не дозы инсулина.)
- Чтение чужых данных: 30 дней по умолчанию, история — отдельное разрешение `PERMISSION_READ_HEALTH_DATA_HISTORY`; фон — `READ_HEALTH_DATA_IN_BACKGROUND`.
- Google Fit API поддерживается до конца 2026 → сразу Health Connect.

### CGM
- Не писать драйверы. Варианты: broadcast xDrip (`com.eveningoutpost...`), парсинг уведомлений CGM-приложения, LibreLinkUp (неофициальный облачный API), Nightscout follower.
- Учесть: Android 13+ ограничивает доступ к уведомлениям; нужен explicit opt-in и понятное объяснение.

---

## 6. Юридическая рамка (РФ)

1. **Медицинское изделие** — 323-ФЗ, ст. 38. ПО относится к медизделиям, если предназначено для диагностики, профилактики, лечения, реабилитации или мониторинга.
2. Регистрация — **Постановление Правительства РФ от 30.11.2024 № 1684**, действует с 01.03.2025 (заменило прежний порядок). Сроки регистрации ПО на практике — от 6 до 18 месяцев, с клинико-лабораторными испытаниями и техдокументацией.
3. **Практическая граница:**
   - «журнал» (пользователь вводит и видит свои данные, экспорт для врача, статистика без рекомендаций) → обычно **не медизделие**;
   - «калькулятор болюса», «рекомендация дозы», «прогноз гипогликемии с советом», автоматическая интерпретация → **медизделие**, высокая степень риска. Требует регистрации, а для дозирования инсулина — особенно строгой.
   - Рекомендуется до старта получить письменное юридическое заключение и зафиксировать позиционирование в описании приложения (wellness / education, not a medical device).
4. **152-ФЗ «О персональных данных»:** данные о здоровье — специальная категория (ст. 10), нужно отдельное согласие; локализация ПДн в РФ (ст. 18 ч. 5); уведомление Роскомнадзора об обработке. При локальном хранении «только на устройстве» рисков меньше, но политика конфиденциальности и согласие всё равно нужны.
5. **GPL/AGPL:** производное приложение обязано быть под той же лицензией; код нельзя закрыть. AGPL дополнительно «срабатывает» при сетевом использовании (AndroidAPS, Nightscout).
6. **Google Play / RuStore:** политики для health-приложений, декларация данных, запрет медицинских заявлений без подтверждения; для Health Connect — декларация типов данных.

---

## 7. Рекомендуемая стратегия

### Вариант A — «Форк Diaguard» (быстро, но GPL)
- Плюсы: готовый дневник, PDF/CSV, RU-локализация, база продуктов, экономия месяцев.
- Минусы: Java-монолит, GPL + письменное разрешение автора, чужой roadmap (KMP-переписывание), сложно строить коммерческий продукт.
- Кому: открытый/некоммерческий проект, пациентское сообщество, учебный проект.

### Вариант B — «Своё приложение на Kotlin/Compose, идеи и данные — из open source» (рекомендуется)
- Плюсы: чистые лицензии, современный стек, контроль над продуктом и регулированием, KMP-задел на iOS.
- Минусы: 2–4 месяца до MVP, нужно самим сделать еду/отчёты.
- Что берём легально: схему данных (Diaguard/Tidepool), UX-паттерны, Health Connect, OFF (ODbL), FHIR/LOINC, Nightscout-формат. Код GPL-проектов — только как референс, не копируем.

### Вариант C — «Гибрид»
- MVP на Kotlin/Compose (вариант B), а CGM-часть не пишем сами: подписываемся на xDrip-broadcast / уведомления / Nightscout. Это самый прагматичный путь.

### Предлагаемый объём MVP (вариант B/C)
1. Ручной ввод: глюкоза (с контекстом), инсулин (болюс/базал), углеводы/еда, вес, АД, пульс, кетоны, заметки, теги.
2. Единицы mg/dL ↔ mmol/L, настраиваемые целевые диапазоны.
3. Таймлайн + графики (день/7/30/90), TIR, средний, SD, CV%, GMI, оценка HbA1c, число гипо.
4. Напоминания (измерение, инсулин, приём пищи).
5. Экспорт **PDF-отчёт для эндокринолога** (на русском) + CSV.
6. Локальное хранение, шифрование, бэкап/восстановление, без облака.
7. RU-интерфейс, доступность (TalkBack, крупный шрифт).

Фаза 2: Health Connect, Nightscout, AGP, база продуктов (OFF + локальная RU), CGM через broadcast.
Фаза 3 (только после юридической проверки): калькулятор болюса, облако/кабинет врача, ЕГИСЗ/FHIR-экспорт.

---

## 8. Риски

| Риск | Влияние | Митигация |
|---|---|---|
| GPL/AGPL заражение | Нельзя закрыть код | Не копировать GPL-код; использовать permissive-библиотеки |
| Регулирование медизделия | Месяцы и стоимость | Не давать рекомендаций по дозам в MVP; письменное юрзаключение |
| Неверные дозы из-за калькулятора | Вред пациенту, ответственность | Не включать или сделать с явными дисклеймерами и валидацией |
| Драйверы CGM ломаются | Потеря данных | Не писать свои; принимать broadcast/уведомления |
| 152-ФЗ | Штрафы, блокировки | Локальное хранение по умолчанию, согласие, политика, уведомление РКН |
| Потеря данных пациента | Критично | Автобэкап, миграции БД, экспорт |
| Еда: нет свободной RU-БД | Плохой UX | OFF + ручной ввод + импорт CSV; позже договор с источником |

---

## 9. Принятое решение

Зафиксировано с заказчиком:
- проект **открытый, GPL-совместимый** → код Diaguard/Strimma/xDrip переиспользовать можно;
- **калькулятора доз не будет** → приложение позиционируется как дневник, вне регистрации медизделия;
- облако и ЕГИСЗ в MVP не входят.

Отсюда два законных пути:

**Путь 1 — форк Diaguard (быстрый MVP).**
Взять `Faltenreich/Diaguard`, сохранить GPL-3.0, написать автору письмо о распространении (его дополнительное условие), перевести сборку на актуальный Gradle/SDK, добавить CGM-модуль по образцу Strimma. Через 2–4 недели — работающий дневник с PDF/CSV и RU.
Риски: Java-монолит, замедленный апстрим, конфликт с KMP-переписыванием.

**Путь 2 — новое приложение на Kotlin/Compose под GPL-3.0 (рекомендуется для долгой жизни).**
С нуля UI и архитектура, но легально переносим:
- схему данных и `food.csv` из Diaguard (та же лицензия, с атрибуцией);
- CGM/Health Connect/Nightscout-логику из Strimma (GPL-3.0);
- формат экспорта — совместимый с Nightscout.
Дольше (2–4 месяца до MVP), но современный стек и KMP-задел на iOS.

Компромисс: **Путь 2, но с вертикальными срезами** — сначала дневник + отчёт врачу, затем CGM, затем еда/AGP.

## 10. Ближайшие шаги

1. Юридически зафиксировать позиционирование: «дневник, не медизделие», дисклеймер в приложении и описании стора.
2. Выбрать путь 1/2 и создать репозиторий с GPL-3.0 + `NOTICE` об использованных компонентах.
3. Спроектировать схему Room (раздел 4) и экраны: Таймлайн, Ввод, Графики, Отчёт, Настройки.
4. Реализовать MVP-срез: ввод глюкозы/инсулина/углеводов, единицы, график, TIR/GMI, PDF-отчёт.
5. Подключить Health Connect и импорт/экспорт Nightscout.
6. Добавить CGM: приём xDrip-broadcast и парсинг уведомлений.
7. Настроить шифрование БД, бэкап и миграции.

## 11. Источники

- Diaguard — https://github.com/Faltenreich/Diaguard (GPL-3.0, Java, README)
- Strimma — https://github.com/psjostrom/strimma (GPL-3.0, Kotlin/Compose, README)
- xDrip+ — https://github.com/NightscoutFoundation/xDrip (GPL-3.0); протокол broadcast — https://github.com/NightscoutFoundation/xDrip/blob/master/Documentation/technical/Incoming_Glucose_Broadcast.md; локальный HTTP — https://github.com/NightscoutFoundation/xDrip/blob/master/Documentation/technical/Local_Web_Services.md
- Juggluco — https://github.com/j-kaltes/Juggluco (GPL-3.0)
- DiaX-Tracker — MIT, Android-дневник с импортом из xDrip/Juggluco (2026)
- oref0 — https://github.com/openaps/oref0 (MIT)
- Nordic Android-BLE-Library — https://github.com/NordicSemiconductor/Android-BLE-Library (BSD-3-Clause); Kable — https://github.com/juullabs/kable (Apache-2.0)
- Nightscout API v3 — https://github.com/nightscout/cgm-remote-monitor/blob/master/lib/api3/doc/tutorial.md
- AndroidAPS — https://github.com/nightscout/AndroidAPS (AGPL-3.0)
- Tidepool platform — https://github.com/tidepool-org/platform (BSD-2-Clause); blip — https://github.com/tidepool-org/blip (BSD-2-Clause)
- Glucosio — https://github.com/Glucosio/glucosio-android (GPL-3.0, неактивен с 2022)
- Nightscout — https://github.com/nightscout/cgm-remote-monitor (AGPL-3.0)
- Open Food Facts data — https://world.openfoodfacts.org/data (ODbL), API — https://openfoodfacts.github.io/openfoodfacts-server/api/
- Health Connect, типы данных — https://developer.android.com/health-and-fitness/health-connect/data-types
- Health Connect, чтение данных и разрешения — https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data
- Health Connect, `BloodGlucoseRecord` — https://developer.android.com/reference/androidx/health/connect/client/records/BloodGlucoseRecord
- HL7 FHIR CGM IG — https://build.fhir.org/ig/HL7/cgm/
- LOINC 2339-0 Glucose [Mass/volume] in Blood — https://loinc.org/2339-0
- Постановление Правительства РФ № 1684 от 30.11.2024 — http://government.ru/docs/all/156514/
- Регистрация медизделий, Росздравнадзор — https://roszdravnadzor.gov.ru/medproducts/registration
- 323-ФЗ, ст. 38 (медицинские изделия) — https://www.consultant.ru/document/cons_doc_LAW_121895/
- 152-ФЗ «О персональных данных» — https://www.consultant.ru/document/cons_doc_LAW_61801/