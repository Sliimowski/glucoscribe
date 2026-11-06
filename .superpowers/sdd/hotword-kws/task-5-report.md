# Task 5 — отчёт: детектор встроен в foreground-сервис

## Что изменено

Единственный изменённый файл:
`hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`

1. **Ленивая загрузка детектора** — новый `private fun loadDetector(): HotwordDetector?`.
   Вызывается в начале `listenLoop()` (фоновый поток), не в `onCreate`/`onStartCommand`.
   - Читает `assets/hotword/templates.bin` и `assets/hotword/threshold.txt` через
     `TemplateStore.loadTemplates/loadThreshold`.
   - Всё обёрнуто в `runCatching`; пустой список шаблонов тоже считается ошибкой
     (`require(templates.isNotEmpty())`).
   - Успех: `kws enabled templates=N threshold=X`.
   - Ошибка/отсутствие assets: `kws disabled: <class> <message>`, сервис работает
     только с VAD (детектор = `null`).
2. **Цикл записи перестроен на кадры 10 мс**
   - `AudioRecord.read(data, 0, 160)` — ровно 160 `Short` за итерацию; буфер
     `AudioRecord` увеличен до `max(minBuffer, 160*2*16)` (≈160 мс).
   - Кольцевой буфер `ShortArray(400)` + флаг `filled`; при `filled` упорядоченная
     копия складывается в переиспользуемый `windowLinear` (два `System.arraycopy`,
     без аллокаций).
   - `KWS_FEATURES.logMel(windowLinear)` — берётся `lastOrNull()`
     (при окне 400 получается ровно 1 строка 26 признаков).
   - `kws.accept(row)`; при не-null:
     `kws match score=%.4f total=%d` (`Locale.US`, растущий счётчик `matchCount`).
   - `accept` вызывается только если `kws != null` — выключенный путь безопасен.
3. **VAD сохранён**: RMS по тем же 160 отсчётам, порог `1200.0`, старт после 3
   подряд громких кадров, продолжение при `rms >= 0.6*порога`, конец после 1 с
   тишины, `MAX_EVENT_MS` 30 с, длительность события в мс. Логи `voice event ...`
   не изменились по формату.
4. **Heartbeat**: `heartbeat frames=N avgRms=A kwsMatches=M screen=...`
   (`kwsMatches` — накопительный счётчик срабатываний с начала цикла).
5. `onStartCommand` не изменялся по объёму работы: только `startForeground`,
   регистрация ресиверов и запуск потока (загрузка assets — в потоке).

Константы KWS в `companion object`: `KWS_FEATURES = MelFeatures(16000, 400, 160, 26)`
(создаётся один раз, не на кадр).

## Сборка

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"
.\gradlew.bat :hotword-spike:assembleDebug --console=plain
BUILD SUCCESSFUL in 1s
34 actionable tasks: 4 executed, 30 up-to-date
```
`:hotword-spike:compileDebugKotlin` и `assembleDebug` — PASS.

## Файлы

- Изменён: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`
- Другие файлы не трогал (`MelFeatures.kt`, `Dtw.kt`, `HotwordDetector.kt`,
  `TemplateStore.kt`, `SampleRecorder.kt` — без изменений).
- APK не устанавливал (по инструкции — это делает контроллер).

## Self-review

- **Потокобезопасность**: `KWS_FEATURES` — `val` в `companion object`, состояние
  (`window`, `filters`) только читается после конструктора; `logMel` не хранит
  разделяемого мутабельного состояния (re/im/выход — локальные). `HotwordDetector`
  создаётся и используется только в потоке `spike-listen`. `matchCount`,
  счётчики VAD — локальные переменные потока. `running` — `AtomicBoolean`.
- **Аллокации на кадр**: сам `MelFeatures` не пересоздаётся; буферы `data`, `ring`,
  `windowLinear` переиспользуются. Но `MelFeatures.logMel` внутри аллоцирует
  `re`, `im` (1024 float каждый) и массив строк — это ограничение существующего
  класса, который по условию менять нельзя.
- **AudioRecord**: ранние выходы (create/init/start failure) делают `release()`;
  после цикла — `stop()`+`release()`; `onDestroy` освобождает `record` повторно
  (повторный `release()` безопасен, `record` обнуляется).
- **Выключенный детектор**: `kws == null` → блок KWS пропускается целиком,
  VAD/heartbeat работают; отсутствие файлов даёт `FileNotFoundException` внутри
  `runCatching` и лог `kws disabled`.
- **Логирование**: матчи — только по факту срабатывания; кадры не логируются.
  `read` логируется только при аномалиях: ошибка (`read failed code=`) — один раз
  и выход; нулевой read — не чаще раза в 5 c и выход после 100 подряд.
- **Порядок кадров окна**: `ringPos` после заполнения указывает на самый старый
  сэмпл (следующая запись перезапишет его), копия даёт строго хронологические 400
  отсчётов.

## Concerns

- **Производительность (главный риск)**: `logMel(400)` считается каждые 10 мс
  (100 раз/с) — FFT 512 на кадр + 26 мел-полос. На слабом устройстве это заметная
  нагрузка CPU и батареи. Плюс `HotwordDetector` раз в 10 кадров делает DTW по
  окну 200 кадров × все шаблоны — всплески до нескольких мс. Если обработка
  кадра в среднем превысит 10 мс, аудио-буфер (≈160 мс) начнёт переполняться:
  возможны неполные `read`, дропы аудио и пропуски фразы. Это надо измерить на
  телефоне (Task 6/7). Оптимизация без правки `MelFeatures` невозможна; при
  проблемах — перейти на «считать мел раз в 2–3 кадра» или увеличить hop.
- `logMel` аллоцирует ~8 КБ на кадр (≈800 КБ/с мусора) → давление на GC.
  Влияет на батарею/джиттер, но не на корректность.
- VAD-семантика стала точнее по времени (кадр 10 мс вместо 50 мс): «3 подряд
  громких кадра» теперь 30 мс вместо 150 мс — реакция на голос стала быстрее,
  порог RMS не менялся. Это осознанно соответствует брифу.
- Ручная проверка на телефоне (лог `kws disabled` при отсутствии assets и
  работа VAD) не выполнялась — установку делает контроллер.
---

# Fix after review (Task 5)

## Что исправлено

Файл тот же: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`.

1. **Busy-loop в цикле чтения устранён.**
   - `read < 0` (ERROR_DEAD_OBJECT / ERROR_INVALID_OPERATION): пишется одна строка
     `read failed code=<code>` и `break` — штатный путь cleanup (`stop()`,
     `release()`, `running=false`) выполняется.
   - `read == 0`: не логируется каждый раз; первая строка сразу, далее не чаще
     одного раза в 5 c (`read returned 0 zeros=N`). После `MAX_ZERO_READS = 100`
     подряд пустых чтений — `read stalled: ...` и `break`.
   - **Короткий положительный read больше не теряется**: накопление идёт в буфер
     `hop` (160 `Short`): `recorder.read(hop, hopFill, hopSamples - hopFill)`,
     `hopFill += read`; обработка кадра (RMS, ring, KWS) происходит только когда
     `hopFill == 160`. Недобор продолжает добираться следующим read.
2. **Сброс `running`**: `running.set(false)` добавлен в cleanup после цикла, а
   также во все ранние выходы `listenLoop` (create/init/start failure), чтобы
   повторный `onStartCommand` на том же инстансе не писал
   `start ignored: already running`.

Кольцевая математика окна 400, VAD, KWS, heartbeat — без изменений.

## Сборка

```
$env:GRADLE_USER_HOME="C:\dev\gradle-home"; $env:JAVA_HOME="C:\dev\jdk-17"
.\gradlew.bat :hotword-spike:assembleDebug --console=plain
BUILD SUCCESSFUL in 1s
34 actionable tasks: 4 executed, 30 up-to-date
```

`:hotword-spike:compileDebugKotlin` / `assembleDebug` — PASS.

## Замечания

- При `read == 0` цикл между логированиями всё ещё опрашивает `read` без
  задержки (до 100 попыток) — это ограниченное, а не бесконечное вращение;
  `AudioRecord.read` в блокирующем режиме обычно сам ждёт данные. При желании
  можно добавить короткий `Thread.sleep(10)` на ветке нулевого чтения.
- Остальные concerns из основного отчёта (стоимость `logMel` каждые 10 мс,
  DTW-всплески, ~8 КБ аллокаций на кадр) в силе.