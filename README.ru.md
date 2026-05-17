# AI JVM Debug Assistant — Документация на русском языке

> CLI-инструмент на базе ИИ для анализа JVM стектрейсов, сбоев CI и логов сборки Java-проектов.

[![Build & Test](https://github.com/asamoljuk/jvm-ai-debug/actions/workflows/build.yml/badge.svg)](https://github.com/antonsamoljuk/jvm-ai-debug/actions/workflows/build.yml)

---

## Содержание

1. [Зачем нужен этот инструмент](#зачем-нужен-этот-инструмент)
2. [Возможности](#возможности)
3. [Установка](#установка)
4. [Быстрый старт](#быстрый-старт)
5. [Команды и параметры](#команды-и-параметры)
6. [AI-провайдеры](#ai-провайдеры)
7. [Категории ошибок](#категории-ошибок)
8. [Форматы вывода](#форматы-вывода)
9. [Интеграция с CI/CD](#интеграция-с-cicd)
10. [Архитектура проекта](#архитектура-проекта)
11. [Расширение функциональности](#расширение-функциональности)
12. [Разработка и тестирование](#разработка-и-тестирование)
13. [Переменные окружения](#переменные-окружения)

---

## Зачем нужен этот инструмент

Каждый Java-разработчик знаком с ситуацией: CI упал, терминал завален красным текстом — `BeanCreationException`, NPE в проде, Maven не компилируется с загадочным `cannot find symbol`. Поиск причины требует знания фреймворков, понимания паттернов ошибок и умения правильно читать стектрейсы.

Этот инструмент замыкает петлю обратной связи между упавшей сборкой и готовым решением:

- **Автоматически разбирает** лог — не нужно копировать стектрейс в ChatGPT вручную
- **Определяет категорию проблемы** — Spring context failure, NPE, Hibernate mapping и т.д.
- **Объясняет вероятную причину** на понятном языке
- **Предлагает конкретные шаги** для исправления в порядке приоритета
- **Интегрируется в CI/CD** — JSON-вывод читается любым downstream-инструментом
- **Работает локально** через Ollama — данные никуда не уходят

---

## Возможности

| Возможность | Описание |
|-------------|----------|
| Анализ стектрейсов | Извлечение исключений, цепочек caused-by, имён классов и методов |
| Spring Boot | Обнаружение циклических зависимостей и сбоев контекста приложения |
| Maven / Gradle | Диагностика ошибок сборки и компиляции |
| JUnit / TestNG | Анализ упавших тестов и assertion-ошибок |
| Hibernate / JPA | Определение ошибок маппинга и persistence |
| JVM-память | Анализ OutOfMemoryError, StackOverflowError, GC overhead |
| Несколько AI-провайдеров | OpenAI, Anthropic Claude, Ollama (локально), Mock (без ключей) |
| Форматы вывода | Текст (80-символьный перенос) или JSON для скриптов и CI |
| Verbose-режим | Подробная диагностика в stderr, не загрязняет stdout |
| GitHub Actions | Готовый workflow с автоматическим комментарием в Pull Request |

---

## Установка

### Требования

- Java 17+ (рекомендуется Java 21)
- Maven 3.9+

### Сборка из исходников

```bash
git clone https://github.com/asamoljuk/jvm-ai-debug.git
cd jvm-ai-debug
mvn clean package -DskipTests
```

В результате создаётся `target/jvm-ai-debug.jar` — fat JAR со всеми зависимостями.

### Проверка установки

```bash
java -jar target/jvm-ai-debug.jar version
# AI JVM Debug Assistant v1.0.0
# Java: 21.0.x
```

---

## Быстрый старт

### Без API-ключей (mock-провайдер)

```bash
# Анализ файла со стектрейсом
java -jar target/jvm-ai-debug.jar analyze src/test/resources/samples/spring-bean-failure.txt --provider mock

# Вывод в JSON
java -jar target/jvm-ai-debug.jar analyze src/test/resources/samples/null-pointer.txt --provider mock --format json
```

### С OpenAI

```bash
export OPENAI_API_KEY=sk-...
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --provider openai
```

### С Anthropic Claude

```bash
export ANTHROPIC_API_KEY=sk-ant-...
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --provider anthropic
```

### С Ollama (полностью локально)

```bash
# 1. Установите Ollama: https://ollama.com/download
# 2. Скачайте модель
ollama pull llama3.1

# 3. Запустите анализ
java -jar target/jvm-ai-debug.jar analyze stacktrace.txt --provider ollama
```

---

## Команды и параметры

### `analyze` — основная команда

```
java -jar jvm-ai-debug.jar analyze <файл> [опции]
```

| Параметр | Сокращение | Значения | По умолчанию | Описание |
|----------|-----------|---------|--------------|----------|
| `--provider` | `-p` | `mock`, `openai`, `anthropic`, `ollama` | авто-определение | AI-провайдер |
| `--format` | `-f` | `text`, `json` | `text` | Формат вывода |
| `--type` | `-t` | тип ошибки | — | Зарезервировано для будущего использования |
| `--verbose` | `-v` | — | выключено | Диагностика в stderr |

**Примеры:**

```bash
# Текстовый вывод, mock-провайдер
java -jar jvm-ai-debug.jar analyze build.log --provider mock

# JSON-вывод для CI
java -jar jvm-ai-debug.jar analyze build.log --provider mock --format json > analysis.json

# Диагностика в stderr (не загрязняет JSON в stdout)
java -jar jvm-ai-debug.jar analyze build.log --format json --verbose > analysis.json

# Автоопределение провайдера по наличию API-ключей
export OPENAI_API_KEY=sk-...
java -jar jvm-ai-debug.jar analyze build.log
```

### `version` — версия

```bash
java -jar jvm-ai-debug.jar version
```

### `help` — справка

```bash
java -jar jvm-ai-debug.jar help
java -jar jvm-ai-debug.jar help analyze
```

### Коды завершения

| Код | Значение |
|-----|---------|
| `0` | Анализ успешно завершён |
| `1` | Ошибка (файл не найден, ошибка API, некорректный JSON) |

---

## AI-провайдеры

### Mock (по умолчанию)

Не требует API-ключей и сетевого доступа. Возвращает детерминированные ответы для каждой категории ошибок. Используется по умолчанию, когда ни один API-ключ не задан.

```bash
java -jar jvm-ai-debug.jar analyze stacktrace.txt --provider mock
# или
export JVM_AI_DEBUG_PROVIDER=mock
```

**Когда использовать:**
- Локальная разработка и отладка самого инструмента
- CI без доступа к API-ключам
- Демонстрация без учётных данных
- Тестирование логики парсинга и определения категорий

### OpenAI

```bash
export OPENAI_API_KEY=sk-...
java -jar jvm-ai-debug.jar analyze stacktrace.txt --provider openai
```

| Параметр | Значение |
|----------|---------|
| Модель | `gpt-4o-mini` |
| Температура | `0.2` |
| Max tokens | `1500` |
| Таймаут чтения | 60 секунд |

### Anthropic Claude

```bash
export ANTHROPIC_API_KEY=sk-ant-...
java -jar jvm-ai-debug.jar analyze stacktrace.txt --provider anthropic
```

| Параметр | Значение |
|----------|---------|
| Модель | `claude-sonnet-4-6` |
| Max tokens | `1500` |
| Версия API | `2023-06-01` |
| Таймаут чтения | 60 секунд |

### Ollama (локальная модель)

Данные **не покидают ваш компьютер**. API-ключ не нужен.

```bash
# Базовое использование (127.0.0.1:11434, модель llama3.1)
java -jar jvm-ai-debug.jar analyze stacktrace.txt --provider ollama

# Кастомная модель
export OLLAMA_MODEL=codellama
java -jar jvm-ai-debug.jar analyze stacktrace.txt --provider ollama

# Удалённый сервер Ollama
export OLLAMA_BASE_URL=http://gpu-server:11434
export OLLAMA_MODEL=mistral
java -jar jvm-ai-debug.jar analyze stacktrace.txt --provider ollama
```

| Настройка | По умолчанию | Переменная окружения |
|-----------|-------------|---------------------|
| Base URL | `http://127.0.0.1:11434` | `OLLAMA_BASE_URL` |
| Модель | `llama3.1` | `OLLAMA_MODEL` |
| Таймаут чтения | **5 минут** | — |

> Таймаут в 5 минут — намеренный. Локальные модели на CPU могут генерировать ответ долго. Для ускорения используйте меньшую модель: `ollama pull phi3`.

**Решение проблем с Ollama:**

| Симптом | Причина | Решение |
|---------|---------|---------|
| `Connection refused` | Сервер не запущен | Выполните `ollama serve` |
| `404 Not Found` | Модель не скачана | Выполните `ollama pull <model>` |
| Таймаут | Модель слишком большая | Используйте `ollama pull phi3` |
| Некорректный JSON | Модель плохо следует инструкциям | Попробуйте `codellama` или `llama3.1` |

### Порядок автоопределения провайдера

Если `--provider` не указан явно, инструмент определяет провайдер в следующем порядке:

```
1. Явный флаг --provider
2. Переменная JVM_AI_DEBUG_PROVIDER
3. OPENAI_API_KEY задан → OpenAI
4. ANTHROPIC_API_KEY задан → Anthropic
5. Ни одного ключа → Mock
```

> **Ollama в автоопределение не входит** — у него нет API-ключа-сигнала. Его нужно включать явно.

---

## Категории ошибок

Инструмент определяет 11 категорий. Определение происходит автоматически в порядке приоритета — первое совпадение побеждает.

### Порядок проверки

```
1.  SPRING_CONTEXT_FAILURE   — Spring-контекст не запустился
2.  JVM_MEMORY_ERROR         — OutOfMemoryError / StackOverflow
3.  CLASS_NOT_FOUND          — ClassNotFoundException
4.  NO_CLASS_DEF_FOUND       — NoClassDefFoundError
5.  NULL_POINTER_EXCEPTION   — NullPointerException
6.  HIBERNATE_MAPPING_ERROR  — Hibernate / JPA ошибки маппинга
7.  JUNIT_TEST_FAILURE       — JUnit тест упал (ПЕРЕД Maven!)
8.  TESTNG_TEST_FAILURE      — TestNG тест упал (ПЕРЕД Maven!)
9.  MAVEN_BUILD_FAILURE      — Maven сборка упала
10. GRADLE_BUILD_FAILURE     — Gradle сборка упала
11. UNKNOWN                  — Не определено
```

> JUnit/TestNG проверяются **до** Maven/Gradle, потому что лог Maven с упавшими тестами содержит оба сигнала — более конкретная категория полезнее.

### Оценка уверенности (confidence)

| Условие | Уверенность |
|---------|-------------|
| Найдены исключения **и** цепочка caused-by | `HIGH` |
| Найдено что-то одно из двух | `MEDIUM` |
| Ничего не найдено или категория `UNKNOWN` | `LOW` |

### Описание категорий

#### `SPRING_CONTEXT_FAILURE`
**Триггеры:** `BeanCreationException`, `UnsatisfiedDependencyException`, `BeanDefinitionParsingException`, `NoSuchBeanDefinitionException`, или в тексте есть `applicationcontext` и `fail`.

Типичный пример:
```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'userService'
Caused by: org.springframework.beans.factory.BeanCurrentlyInCreationException: ...
```

#### `NULL_POINTER_EXCEPTION`
**Триггеры:** Исключение точно равно `NullPointerException`.

#### `CLASS_NOT_FOUND`
**Триггеры:** `ClassNotFoundException`. Класс отсутствует в classpath (нет зависимости или неверный scope).

#### `NO_CLASS_DEF_FOUND`
**Триггеры:** `NoClassDefFoundError`. Класс был на этапе компиляции, но отсутствует в рантайме. Часто — неверный scope или сбой в static-инициализаторе.

#### `JVM_MEMORY_ERROR`
**Триггеры:** `OutOfMemoryError`, `StackOverflowError`, `GCOverheadLimitExceededError`.

#### `HIBERNATE_MAPPING_ERROR`
**Триггеры:** `MappingException`, `HibernateException`, `PersistenceException`, `EntityNotFoundException`, или в тексте есть `hibernate` + (`mapping` или `schema`).

#### `JUNIT_TEST_FAILURE`
**Триггеры:** `org.junit`, `assertionerror`, `tests run:`, или JUnit в индикаторах фреймворка.

#### `MAVEN_BUILD_FAILURE`
**Триггеры:** `build failure`, `[error]`, `compilation failure`, `maven` — при условии, что это не Gradle.

#### `GRADLE_BUILD_FAILURE`
**Триггеры:** `task :`, `build failed`, `gradle`.

---

## Форматы вывода

### Текстовый формат (по умолчанию)

```bash
java -jar jvm-ai-debug.jar analyze stacktrace.txt
# или явно:
java -jar jvm-ai-debug.jar analyze stacktrace.txt --format text
```

Пример вывода:
```
=== AI JVM Debug Assistant ===

Detected issue:
  Spring Context Failure

Likely root cause:
  Циклическая зависимость между Spring-бинами не позволяет контексту
  инициализироваться...

Evidence:
  - BeanCreationException found in stack trace
  - Circular dependency detected in the Spring dependency graph

Suggested fixes:
  1. Вынесите общую логику в третий сервис
  2. Используйте @Lazy на одной стороне как временное решение
  ...

Confidence:
  High

Files/classes mentioned:
  - UserService
  - NotificationService
```

Секции без данных (`Evidence`, `Suggested fixes`, `Files/classes mentioned`) **опускаются** автоматически.

### JSON-формат

```bash
java -jar jvm-ai-debug.jar analyze stacktrace.txt --format json
```

Пример вывода:
```json
{
  "detectedIssue" : "SPRING_CONTEXT_FAILURE",
  "title" : "Spring application context failed to start",
  "likelyRootCause" : "A circular dependency between Spring beans...",
  "evidence" : [ "BeanCreationException found in stack trace", "..." ],
  "suggestedFixes" : [ "Refactor the dependency direction...", "..." ],
  "confidence" : "HIGH",
  "mentionedClasses" : [ "UserService", "NotificationService", "..." ]
}
```

**Схема JSON:**

| Поле | Тип | Описание |
|------|-----|----------|
| `detectedIssue` | string | Имя enum-категории (например, `SPRING_CONTEXT_FAILURE`) |
| `title` | string | Человекочитаемое название |
| `likelyRootCause` | string | Подробное объяснение причины |
| `evidence` | array | Доказательства из лога |
| `suggestedFixes` | array | Шаги по исправлению |
| `confidence` | string | `HIGH`, `MEDIUM` или `LOW` |
| `mentionedClasses` | array | Упомянутые классы из стектрейса |

**Работа с JSON через jq:**

```bash
# Получить только suggested fixes
java -jar jvm-ai-debug.jar analyze build.log --format json | jq '.suggestedFixes[]'

# Проверить уверенность в скрипте
CONFIDENCE=$(java -jar jvm-ai-debug.jar analyze build.log --format json | jq -r '.confidence')
if [ "$CONFIDENCE" = "HIGH" ]; then
  echo "Высокая уверенность в диагнозе"
fi

# Получить список упомянутых классов через запятую
java -jar jvm-ai-debug.jar analyze build.log --format json \
  | jq -r '.mentionedClasses | join(", ")'
```

### Verbose-режим

```bash
java -jar jvm-ai-debug.jar analyze stacktrace.txt --verbose
```

Диагностика пишется в **stderr**, не в stdout — JSON-вывод не загрязняется:

```bash
# JSON идёт в файл, диагностика — в терминал
java -jar jvm-ai-debug.jar analyze stacktrace.txt --format json --verbose > analysis.json
```

Verbose-вывод включает: провайдер, путь к файлу, формат, определённую категорию, уверенность, и полный промпт, отправленный в AI.

---

## Интеграция с CI/CD

### GitHub Actions

В репозитории уже есть готовый workflow `.github/workflows/build.yml`. При падении сборки или тестов он:

1. Собирает инструмент (`mvn package -DskipTests`)
2. Запускает полную сборку с тестами, сохраняя лог в `build-output.txt`
3. При неудаче запускает `jvm-ai-debug analyze build-output.txt --format json`
4. Публикует результат как комментарий в Pull Request

**Пример комментария в PR:**

```markdown
## 🔍 AI JVM Debug Assistant

> **Spring application context failed to start** · Confidence: 🔴 High

### Likely Root Cause
Циклическая зависимость между Spring-бинами...

### Evidence
- BeanCreationException found in stack trace
- ...

### Suggested Fixes
1. Вынести общую логику в третий сервис...
2. ...
```

**Необходимые права GitHub Actions:**

```yaml
permissions:
  pull-requests: write   # для публикации комментариев
  contents: read         # для checkout
```

**Использование реального AI в CI:**

```yaml
- name: Analyze failure
  if: steps.verify.outcome == 'failure'
  env:
    OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
  run: |
    java -jar target/jvm-ai-debug.jar analyze build-output.txt \
      --provider openai --format json > analysis.json
```

### GitLab CI

```yaml
analyze-failure:
  stage: analyze
  when: on_failure
  script:
    - java -jar jvm-ai-debug.jar analyze build.log --provider mock --format json > analysis.json
    - |
      CONFIDENCE=$(jq -r '.confidence' analysis.json)
      TITLE=$(jq -r '.title' analysis.json)
      echo "Диагноз: $TITLE (уверенность: $CONFIDENCE)"
  artifacts:
    paths:
      - analysis.json
    when: on_failure
```

### Jenkins Pipeline

```groovy
post {
    failure {
        script {
            sh 'java -jar jvm-ai-debug.jar analyze build.log --provider mock --format json > analysis.json'
            def analysis = readJSON file: 'analysis.json'
            echo "Обнаружено: ${analysis.detectedIssue} (${analysis.confidence})"
            echo "Причина: ${analysis.likelyRootCause}"
        }
    }
}
```

---

## Архитектура проекта

### Структура пакетов

```
src/main/java/com/antonsamoljuk/jvmaidbg/
├── cli/           Точка входа и команды picocli
├── parser/        Извлечение сырых данных из текста
├── analysis/      Определение категории, построение промпта, оркестрация
├── ai/            Интерфейс AiClient и реализации провайдеров
├── output/        Форматирование ответа
├── config/        Конфигурация и выбор провайдера
└── model/         Общие data-классы
```

### Поток данных (pipeline)

```
Файл на диске
     │
     ▼
AnalysisService.analyze(Path)
     │  читает содержимое файла
     ▼
LogParser.parse(String)
     │  возвращает ExtractedEvidence
     ▼
IssueDetector.detect(ExtractedEvidence, String)
     │  возвращает DetectedIssue (категория + уверенность + доказательства)
     ▼
PromptBuilder.build(DetectedIssue, String)
     │  возвращает AnalysisRequest (DetectedIssue + промпт + сырой контент)
     ▼
AiClient.analyze(AnalysisRequest)
     │  возвращает AnalysisResponse
     ▼
OutputFormatter.format(AnalysisResponse, OutputFormat)
     │  возвращает отформатированную строку
     ▼
System.out
```

`AnalysisService` владеет всем pipeline. CLI (`AnalyzeCommand`) создаёт конфигурацию, получает `AiClient`, строит `AnalysisService` и вызывает `analyze()` — он не работает с парсером, детектором или билдером промптов напрямую.

### Ключевые классы

| Класс | Тип | Назначение |
|-------|-----|-----------|
| `IssueCategory` | Enum | 11 обнаруживаемых категорий с display-именами |
| `ExtractedEvidence` | Mutable POJO | Все сигналы из `LogParser`. Mutable намеренно — строится инкрементально. |
| `DetectedIssue` | Immutable | Категория + уверенность + ссылка на `ExtractedEvidence` |
| `AnalysisRequest` | Immutable | `DetectedIssue` + строка промпта + сырой контент |
| `AnalysisResponse` | Mutable POJO + Jackson | Двунаправленный: десериализуется из ответа LLM и сериализуется для `--format json` |

### LogParser — что извлекает

| Что | Способ |
|-----|--------|
| Имена исключений/ошибок | Regex: токены, заканчивающиеся на `Exception` или `Error` |
| Цепочка caused-by | Regex: строки `Caused by: ...` |
| Имена классов и методов | Regex: строки `at ClassName.method(` |
| Классы приложения | Токены с заглавной буквы + известные суффиксы (`Service`, `Controller`, `Repository`, `Bean`, ...) |
| Индикаторы фреймворка | `String.contains()` по строкам Spring, Hibernate, Tomcat и т.д. |
| Индикаторы инструментов сборки | Ключевые слова Maven и Gradle |
| Индикаторы тест-фреймворков | JUnit, TestNG, Mockito |

> Сырой excerpt в `ExtractedEvidence` обрезается до **3 000 символов**. Excerpt в промпт дополнительно обрезается до **2 500 символов** в `PromptBuilder`.

---

## Расширение функциональности

### Добавление новой категории ошибок

1. Добавьте значение в `IssueCategory` с display-именем
2. Добавьте правило обнаружения в `IssueDetector.categorize()` — **порядок важен**: JUnit/TestNG должны быть выше Maven/Gradle
3. Добавьте mock-ответ в `MockAiClient` (новый `case` в switch)
4. Добавьте тестовый файл в `src/test/resources/samples/` и тест в `IssueDetectorTest`

### Добавление нового AI-провайдера

1. Реализуйте интерфейс `AiClient`:
   ```java
   public class MyProviderClient implements AiClient {
       @Override
       public AnalysisResponse analyze(AnalysisRequest request) { ... }
       @Override
       public String getProviderName() { return "myprovider"; }
   }
   ```
2. Добавьте case в `AppConfig.createAiClient()`
3. Используйте `OpenAiClient.extractJson(content)` для снятия markdown-оберток из ответа LLM
4. Обновите описание `--provider` в `AnalyzeCommand`

### Добавление нового формата вывода

1. Добавьте значение в enum `OutputFormat`
2. Добавьте case в `OutputFormatter.format()`
3. Обновите описание `--format` в `AnalyzeCommand`

---

## Разработка и тестирование

### Команды для разработки

```bash
# Полная сборка и тесты
mvn clean verify

# Только сборка fat JAR (без тестов)
mvn clean package -DskipTests

# Запустить все тесты
mvn test

# Запустить конкретный тест-класс
mvn test -Dtest=LogParserTest

# Запустить конкретный метод
mvn test -Dtest=IssueDetectorTest#detectsSpringContextFailure
```

### Тест-покрытие

| Тест-класс | Что тестирует |
|-----------|--------------|
| `LogParserTest` | Извлечение исключений, цепочек caused-by, имён классов, индикаторов, обрезку excerpt |
| `IssueDetectorTest` | Все основные категории, оценка уверенности, fallback на UNKNOWN |
| `PromptBuilderTest` | Имя категории в промпте, сырой контент, output-схема, evidence |
| `MockAiClientTest` | Все 11 категорий возвращают валидные не-null ответы; HIGH/MEDIUM/LOW уверенность |
| `OutputFormatterTest` | Заголовок, все текстовые секции, валидность JSON, null-safe обработка |
| `OllamaAiClientTest` | Имя провайдера, дефолты, trailing slash stripping, кастомный URL/модель, AppConfig-конфигурация |

Реальные сетевые вызовы в тестах **отсутствуют**.

### Тестовые образцы логов

В `src/test/resources/samples/` находятся четыре эталонных лога:

| Файл | Категория |
|------|----------|
| `spring-bean-failure.txt` | `SPRING_CONTEXT_FAILURE` |
| `null-pointer.txt` | `NULL_POINTER_EXCEPTION` |
| `maven-build-failure.txt` | `MAVEN_BUILD_FAILURE` |
| `junit-failure.txt` | `JUNIT_TEST_FAILURE` |

Их можно использовать как для автотестов, так и для ручного тестирования CLI.

---

## Переменные окружения

| Переменная | Назначение | Пример |
|-----------|-----------|--------|
| `JVM_AI_DEBUG_PROVIDER` | Принудительный выбор провайдера | `mock`, `openai`, `anthropic`, `ollama` |
| `OPENAI_API_KEY` | Ключ OpenAI API | `sk-...` |
| `ANTHROPIC_API_KEY` | Ключ Anthropic API | `sk-ant-...` |
| `OLLAMA_BASE_URL` | URL сервера Ollama | `http://127.0.0.1:11434` |
| `OLLAMA_MODEL` | Модель Ollama | `llama3.1`, `codellama`, `phi3` |

### Приоритет выбора провайдера

```
--provider флаг > JVM_AI_DEBUG_PROVIDER > OPENAI_API_KEY > ANTHROPIC_API_KEY > mock
```

Если реальный провайдер выбран, но ключ отсутствует — инструмент **падает с предупреждением** и переключается на mock.

---

## Лицензия

MIT
