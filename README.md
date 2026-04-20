# Restate vs Temporal POC

Сравнение Restate и Temporal на одном потоке кредитования (UAE).

## Архитектура

```
Common HTTP Client (WorkflowClient)
           ↓
   ┌───────┴───────┐
   │               │
Restate         Temporal
Workflow        Workflow
   │               │
   └───────┬───────┘
           ↓
    LOS Service (FSM)
           ↓
   httpbin-proxy (симуляция AECB, Open Banking)
```

**Ключевое отличие**: один клиент, две реализации workflow. FSM в LOS валидирует переходы.

## Быстрый старт

```bash
# 1. Инфраструктура
docker compose up -d

# 2. Сервисы
./gradlew :httpbin-proxy:bootRun &      # Порт 8090
./gradlew :restate-impl:run &            # Порт 9080
./gradlew :los-service:bootRun &         # Порт 8000

# 3. Регистрация Restate
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'

# 4. Тест
./demo-script.sh happy-path
```

## Бенчмарк

```bash
# Запустить Temporal вместо Restate
pkill -f 'restate-impl\|los-service'
./gradlew :temporal-impl:run &           # Порт 9081
SERVER_PORT=8001 ./gradlew :los-service:bootRun &  # Порт 8001

# Бенчмарк (требует Apache Bench)
./scripts/benchmark.sh 100 10            # 100 запросов, 10 concurrent

# График (требует Python 3)
python3 scripts/plot_results.py
```

## Результаты (пример)

```
📊 Benchmark Results
============================================================

Requests/sec:
------------------------------------------------------------
  restate     🏆  ████████████████████████████████████       245.32
  temporal        ██████████████████████████                 198.45

Time/req (ms):
------------------------------------------------------------
  restate     🏆  ████████████████████████████               40.78
  temporal        ████████████████████████████████████       50.42

p95 latency (ms):
------------------------------------------------------------
  restate     🏆  ████████████████████████████               48.23
  temporal        ████████████████████████████████████       61.15
```

**Выводы**:
- Restate: быстрее на ~20% (одинbinary, меньше хопов)
- Temporal: стабильнее при длительных workflows (проверенная система)
- Оба: одинаковый код бизнес-логики (WorkflowClient)

## Структура

```
├── common/              # Домен, DTOs, HTTP клиент (shared)
├── los-service/         # Spring Boot + FSM (5 состояний)
├── restate-impl/        # Restate workflow (ctx.run)
├── temporal-impl/       # Temporal workflow + activities
├── httpbin-proxy/       # Симуляция внешних сервисов
└── scripts/             # benchmark.sh, plot_results.py
```

## FSM (5 состояний)

```
Submitted → Processing(stage) → ManualReview → Approved/Rejected
                            ↓
                        Auto Approved/Rejected
```

Stages: consent → aecb → open_banking → decisioning

## Конфигурация продуктов

`los-service/src/main/resources/product-configs.yaml`:

```yaml
personal_loan:
  stages:
    open_banking: enabled: true
  decision:
    auto_approve_score: 700
    auto_reject_score: 500

auto_loan:
  stages:
    open_banking: enabled: false  # Не нужен для авто
  decision:
    auto_approve_score: 650
```

## UI

- **Restate**: http://localhost:9070 (журнал ctx.run)
- **Temporal**: http://localhost:8088 (event history)

## Технологии

Java 21, Spring Boot 3.4, Restate SDK 2.0, Temporal SDK 1.26, OkHttp

## Дальнейшие эксперименты

1. **Увеличить нагрузку**: `./scripts/benchmark.sh 1000 50`
2. **Добавить failures**: в httpbin-proxy добавить сбои AECB → посмотреть retry
3. **Stress test**: JMeter/Gatling для долгих тестов
4. **Облако**: развернуть в k8s, сравнить с большим QPS

---

**Для дебрифа**: показать benchmark результаты, Restate UI (live journal), Temporal UI (event history). Обсудить trade-offs: простота vs зрелость экосистемы.
