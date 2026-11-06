# Task 5: Встроить детектор в сервис

**Files:**
- Modify: `hotword-spike/src/main/java/ru/samokontrol/hotwordspike/ListenService.kt`

**Что уже есть:**
- `kws/MelFeatures.kt` — `logMel(samples: ShortArray): Array<FloatArray>`.
- `kws/TemplateStore.kt` — `loadTemplates(assets, path)` и `loadThreshold(assets, path)`; бросают исключение на битом файле.
- `kws/HotwordDetector.kt` — `accept(frame: FloatArray): Float?`.

**Что сделать:**

1. В `onCreate` (или лениво при старте прослушивания) загрузить:
   - `assets/hotword/templates.bin`
   - `assets/hotword/threshold.txt`
   Обернуть в `runCatching`. При успехе: `logger.log("kws enabled templates=N threshold=X")`. При ошибке: `logger.log("kws disabled: <class> <message>")` и работать без детектора (VAD остаётся).
2. Перестроить цикл записи на кадры по 160 отсчётов (10 мс):
   - читать ровно 160 `Short` за итерацию (`AudioRecord.read(buf, 0, 160)`);
   - копить скользящее окно 400 отсчётов (кольцевой буфер);
   - на каждой итерации, когда окно заполнено, считать `MelFeatures.logMel(window400)` и брать **последнюю** строку (один кадр 26 признаков);
   - звать `detector.accept(row)`; при не-null: `logger.log("kws match score=%.4f total=%d")` с растущим счётчиком.
   - VAD по RMS считать по тем же 160 отсчётам (порог и логику оставить как было: событие начинается после 3 подряд громких кадров, заканчивается после 1 с тишины; длительность события — в мс).
3. Heartbeat (раз в 10 минут) дополнить: `kwsMatches=N`, `avgRms`, `screen`.
4. Убедиться, что при выключенном детекторе цикл не падает (нет шаблонов → `accept` не зовём вовсе).
5. Проверить, что сервис не делает работы в `onStartCommand` сверх запуска потока.

**Производительность (обязательно):**
- `MelFeatures` создаётся один раз, не на кадр.
- Не логировать каждый кадр.
- Логирование матчей — не чаще фактических срабатываний.

- [ ] **Step 1: Реализовать изменения.**
- [ ] **Step 2: `:hotword-spike:assembleDebug` — PASS.**
- [ ] **Step 3: Ручная проверка на телефоне:** установить APK (спросить подтверждение у контроллера — не устанавливать самому), запустить сервис, убедиться в логе, что при отсутствии assets пишется `kws disabled`, а VAD продолжает работать.

## Global Constraints

- 16 кГц, моно, PCM16, `VOICE_RECOGNITION`.
- Не менять `MelFeatures.kt`, `Dtw.kt`, `HotwordDetector.kt`, `TemplateStore.kt`, `SampleRecorder.kt`.
- Проект не под git: коммиты не нужны.
- Установку на телефон выполняет контроллер, не субагент.