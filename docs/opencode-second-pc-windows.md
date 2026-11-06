# Подключение второго ПК (Windows 11) к локальной Gemma 4 12B

Инструкция для второго компьютера в локальной сети. Модель физически работает
на первом ПК (`192.168.1.63`), второй ПК только отправляет запросы по HTTP.

```
Второй ПК (OpenCode)  ──HTTP──►  192.168.1.63:11434  ──►  RTX 5080 / Ollama
```

---

## 0. Что должно быть готово на первом ПК

Проверьте на **первом** ПК (том, где стоит видеокарта):

```powershell
# сервер запущен и слушает все интерфейсы
netstat -ano | Select-String ':11434' | Select-String 'LISTENING'
# ожидаем: TCP 0.0.0.0:11434 ... LISTENING

# модель отвечает
Invoke-RestMethod http://127.0.0.1:11434/api/version
```

Если сервер не запущен:

```powershell
$env:OLLAMA_HOST='0.0.0.0:11434'
Start-Process "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" -ArgumentList 'serve' -WindowStyle Hidden
```

Первый ПК должен быть включён и не уходить в сон, пока им пользуется второй.
Проверьте: **Параметры → Система → Питание → Переход в спящий режим → Никогда**
(как минимум для сети).

---

## 1. Проверка связи до установки OpenCode

На **втором** ПК откройте PowerShell (Win + X → Terminal) и выполните:

```powershell
Test-NetConnection 192.168.1.63 -Port 11434
```

Ожидаемый результат:

```
TcpTestSucceeded : True
```

Затем проверьте API:

```powershell
Invoke-RestMethod http://192.168.1.63:11434/api/version
# ожидаем: version
#          0.40.13

Invoke-RestMethod http://192.168.1.63:11434/v1/models |
  Select-Object -ExpandProperty data |
  Select-Object id
# ожидаем: gemma4:12b
```

### Если связь не устанавливается

| Симптом | Причина и решение |
|---|---|
| `TcpTestSucceeded : False` | Другой подсеть. IP второго ПК должен быть `192.168.1.x`. Проверьте: `Get-NetIPAddress -AddressFamily IPv4`. |
| `False`, подсеть та же | Блокирует файрвол первого ПК. На первом ПК: `Get-NetFirewallRule -DisplayName 'Ollama API (LAN 192.168.1.0/24)'`. |
| `False`, включён VPN | VPN на втором ПК заворачивает трафик. Отключите VPN или добавьте маршрут к `192.168.1.0/24` через локальный шлюз. |
| `True`, но HTTP висит | На первом ПК сервер слушает только `127.0.0.1`. Вернитесь к шагу 0. |
| `True`, но 404 на `/v1/models` | Старая версия Ollama. Нужна 0.40+ (OpenAI-совместимый API). |

> IP `192.168.1.63` выдаётся роутером по DHCP и может измениться после
> перезагрузки. Закрепите его за первым ПК в настройках роутера
> (раздел DHCP → Static lease / Reserve address).

---

## 2. Установка OpenCode

### Вариант A — OpenCode Desktop (рекомендуется)

У вас на первом ПК стоит именно десктопное приложение, версия 2.2.1.

1. Откройте на втором ПК <https://opencode.ai/download>.
2. Скачайте установщик для **Windows x64** (`opencode-desktop-win-x64.exe`).
3. Запустите его. Права администратора не требуются — установка идёт в
   `%LOCALAPPDATA%\Programs\ai.opencode.desktop`.
4. Если появится синее окно **«Windows protected your PC»**:
   **More info → Run anyway**. Это стандартная реакция SmartScreen на
   относительно новое приложение.
5. После установки запустите **OpenCode** с рабочего стола.

### Вариант B — OpenCode CLI (если нужен терминальный интерфейс)

Нужен Node.js 20 или новее. Проверьте:

```powershell
node --version    # должно быть v20.x или выше
```

Установка:

```powershell
npm install -g opencode-ai@latest
opencode --version
```

Если `npm` ругается на политику выполнения (`npm.ps1 cannot be loaded`),
используйте `npm.cmd` вместо `npm`:

```powershell
npm.cmd install -g opencode-ai@latest
```

Можно поставить оба варианта: они используют один и тот же конфиг.

---

## 3. Настройка провайдера

OpenCode читает глобальный конфиг из:

```
C:\Users\<ИмяПользователя>\.config\opencode\opencode.jsonc
```

### Если файла ещё нет

Создайте папку и файл:

```powershell
$dir = "$env:USERPROFILE\.config\opencode"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
```

Содержимое `opencode.jsonc`:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "ollama-lan": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Ollama (LAN)",
      "options": {
        "baseURL": "http://192.168.1.63:11434/v1",
        "apiKey": "ollama"
      },
      "models": {
        "gemma4:12b": {
          "name": "Gemma 4 12B (LAN)",
          "attachment": true,
          "modalities": {
            "input": ["text", "image"],
            "output": ["text"]
          },
          "limit": {
            "context": 131072,
            "output": 8192
          }
        }
      }
    }
  }
}
```

### Если файл уже есть

Не перезаписывайте его целиком. Откройте и добавьте блок `ollama-lan`
внутрь существующего объекта `"provider"` — рядом с другими провайдерами:

```jsonc
"provider": {
  "какой-то-провайдер": { ... },

  "ollama-lan": {
    "npm": "@ai-sdk/openai-compatible",
    "name": "Ollama (LAN)",
    "options": {
      "baseURL": "http://192.168.1.63:11434/v1",
      "apiKey": "ollama"
    },
    "models": {
      "gemma4:12b": {
        "name": "Gemma 4 12B (LAN)",
        "attachment": true,
        "modalities": {
          "input": ["text", "image"],
          "output": ["text"]
        },
        "limit": {
          "context": 131072,
          "output": 8192
        }
      }
    }
  }
}
```

Важные детали:

- `apiKey` может быть любым — Ollama не проверяет ключ. Строка нужна только
  потому, что OpenAI-совместимый клиент требует непустое поле.
- Имя модели — ровно `gemma4:12b`, включая двоеточие. Это тег Ollama.
- `context` должен совпадать с `OLLAMA_CONTEXT_LENGTH` на первом ПК
  (сейчас `131072`). Если поставить больше, OpenCode будет считать, что
  контекст длиннее, чем реально выделено.
- `output: 8192` — разумный потолок для ответа. У Gemma 12B на 16 GB
  нет смысла ставить больше.
- Файл может содержать `//` комментарии — это JSONC, они разрешены.

### Проверка синтаксиса

```powershell
$p = "$env:USERPROFILE\.config\opencode\opencode.jsonc"
$t = [regex]::Replace((Get-Content $p -Raw), '(?m)^\s*//.*$', '')
try { $t | ConvertFrom-Json | Out-Null; "JSON OK" } catch { "JSON ERROR: $($_.Exception.Message)" }
```

---

## 4. Запуск и выбор модели

1. Запустите OpenCode.
2. Откройте список моделей (`/models` в CLI, селектор модели в десктопе).
3. Выберите **Ollama (LAN) → Gemma 4 12B (LAN)**.
4. Отправьте тестовое сообщение: `Ответь одним предложением, кто ты.`

Первый запрос может занять 5–20 секунд: модель загружается в VRAM.
Последующие — быстрее.

Если конфиг менялся при открытом OpenCode, перезагрузите его:
`/reload` в CLI или перезапуск приложения.

### Контроль на первом ПК

Пока второй ПК работает с моделью, на первом выполните:

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" ps
```

Должна быть строка `gemma4:12b ... 100% GPU ... 131072`.
Если `PROCESSOR` показывает не `100% GPU`, а проценты CPU — модель не влезла
в VRAM (например, её заняла игра).

---

## 5. Типичные ошибки

| Ошибка в OpenCode | Что делать |
|---|---|
| `fetch failed` / `ECONNREFUSED` | Сервер на первом ПК не запущен или слушает только localhost. Шаг 0. |
| `ETIMEDOUT` | Блокирует файрвол или VPN. Шаги 1. |
| `404 model not found` | Опечатка в теге. Должно быть `gemma4:12b`. Проверьте `Invoke-RestMethod http://192.168.1.63:11434/v1/models`. |
| `400 Bad Request` | Несовпадение версии API. Обновите Ollama до 0.40+. |
| Модель не появилась в списке | Ошибка JSON в конфиге. Проверьте синтаксис (шаг 3) и сделайте `/reload`. |
| Ответы очень медленные | Модель частично на CPU: не хватает VRAM. Закройте игры/браузер на первом ПК. |
| Обрыв на длинном ответе | Упритесь в `output`/контекст. Уменьшите запрос или поднимите `output` до 16384 (осторожно с VRAM). |

Логи OpenCode на втором ПК:

```
C:\Users\<ИмяПользователя>\.local\share\opencode\log\opencode.log
```

---

## 6. Безопасность

- У Ollama **нет аутентификации**. Любой, кто дотянется до порта 11434,
  может гонять модель, а также скачивать и удалять модели через API.
- Правило файрвола на первом ПК ограничено подсетью `192.168.1.0/24`.
  Этого достаточно для домашней сети.
- **Не пробрасывайте порт 11434 на роутере в интернет.**
- Если нужен доступ только со второго ПК, сузьте правило до одного адреса.
  На первом ПК (в PowerShell от администратора):

```powershell
Set-NetFirewallRule -DisplayName 'Ollama API (LAN 192.168.1.0/24)' `
  -RemoteAddress 192.168.1.<IP-второго-ПК>
```

- Отключить доступ совсем:

```powershell
Disable-NetFirewallRule -DisplayName 'Ollama API (LAN 192.168.1.0/24)'
```

---

## 7. Краткая шпаргалка

```powershell
# 1. Проверка связи
Test-NetConnection 192.168.1.63 -Port 11434

# 2. Проверка API
Invoke-RestMethod http://192.168.1.63:11434/api/version

# 3. Список моделей
(Invoke-RestMethod http://192.168.1.63:11434/v1/models).data.id

# 4. Ручной тест генерации (без OpenCode)
$body = @{
  model  = 'gemma4:12b'
  prompt = 'Скажи одно слово: привет'
  stream = $false
} | ConvertTo-Json
Invoke-RestMethod http://192.168.1.63:11434/api/generate `
  -Method Post -Body $body -ContentType 'application/json'
```

Если шаг 4 возвращает текст — сеть и модель работают, остаётся только
правильно прописать провайдера в OpenCode.