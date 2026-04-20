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

**Duration-based RPS Testing** - best practices approach:
- Warmup period (10s) before measurement
- Duration-based testing (30s per RPS level)
- Rate limiting to target RPS
- Percentile latency (p50, p95, p99)
- Error rate tracking

```bash
# Quick test (30s at 100 RPS)
python3 benchmark.py

# Custom RPS level
python3 benchmark.py --rps 500 --duration 30

# Full stress test (10-15k RPS) + graphs
pip3 install matplotlib  # для графиков
python3 benchmark.py --stress

# Shorter stress test (10s per level)
python3 benchmark.py --stress --duration 10
```

**Результаты**:
- CSV: `benchmark-results.csv` или `benchmark-stress-results.csv`
- Графики: `benchmark-graph-latest.png` (4 charts: throughput, latency, p95, errors)
- Terminal: Real-time progress + comparison table

**См. также**: [QUICKSTART.md](QUICKSTART.md) - полное руководство по запуску

## Результаты бенчмарка (реальные данные)

### Тест @ 100 RPS (20 секунд)

```
Engine       Actual RPS   Avg (ms)   p95 (ms)   p99 (ms)   Success Rate
------------------------------------------------------------------------
Restate           83.4        1.1        2.2        4.7       100.0%
Temporal          65.6     1116.5     3119.6     8939.0        98.0%
```

### Stress Test (частичные результаты)

| RPS   | Restate Avg | Temporal Avg | Restate Success | Temporal Success |
|-------|-------------|--------------|-----------------|------------------|
| 10    | 8 ms        | 25 ms        | 100%            | 100%             |
| 50    | 8 ms        | 789 ms       | 100%            | 100%             |
| 100   | 17 ms       | 1897 ms      | 100%            | 90.3%            |
| 500   | 86 ms       | crash        | 100%            | connection reset |

**Ключевые выводы**:
- ⚡ **Restate в 1000x быстрее** на средних нагрузках (1ms vs 1116ms @ 100 RPS)
- ✅ **Restate стабильнее**: 100% success rate до 500 RPS
- ⚠️ **Temporal проблемы**:
  - Высокая латентность (1-9 секунд p99)
  - Начинает терять запросы при 100 RPS (90% success)
  - Падает при 500 RPS (connection reset)
- 🔍 **Причина**: Temporal блокирует HTTP endpoint при старте workflow (не async)
- ✅ **Одинаковый код**: оба используют WorkflowClient для бизнес-логики

## Структура

```
├── common/              # Домен, DTOs, HTTP клиент (shared)
├── los-service/         # Spring Boot + FSM (5 состояний)
├── restate-impl/        # Restate workflow (ctx.run)
├── temporal-impl/       # Temporal workflow + activities
├── httpbin-proxy/       # Симуляция внешних сервисов
└── benchmark.py         # Benchmark + stress test + графики (все в одном)
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

## Интерпретация результатов

### Почему такая разница?

**Restate (1-86 ms)**:
- HTTP endpoint сразу возвращает ответ (async)
- Workflow выполняется в фоне через CompletableFuture
- Нет блокировок при старте

**Temporal (1000+ ms)**:
- WorkflowClient.start() блокирует до подтверждения от Temporal server
- Latency включает: сериализацию → gRPC → сохранение в Postgres → ответ
- При высокой нагрузке очередь забивается → timeouts

### Рекомендации

**Когда использовать Restate**:
- ✅ Требуется низкая latency HTTP API (< 10ms)
- ✅ Высокий RPS (500+ requests/sec)
- ✅ Простые workflows без complex state management
- ✅ Минималистичный подход без тяжелых зависимостей

**Когда использовать Temporal**:
- ✅ Сложные long-running workflows (часы/дни)
- ✅ Нужна полная event history и replay
- ✅ Критична надежность (проверенная система, production-ready)
- ✅ Требуется визуализация workflow в UI
- ⚠️ Но нужна оптимизация для высоких RPS (worker pool, batching)

**Следующие шаги для Temporal**:
1. Async workflow start: `WorkflowClient.start()` в отдельном потоке
2. Увеличить worker pool (сейчас default)
3. Batch API calls (группировать старты workflows)
4. HTTP/2 для gRPC соединений

См. также **[OPTIMIZATION.md](OPTIMIZATION.md)** для деталей оптимизации.

## Дальнейшие эксперименты

1. **Stress test**: `python3 benchmark.py --stress` (100-15k requests, графики)
2. **Добавить failures**: в httpbin-proxy увеличить failure_rate → проверить retry
3. **Профилирование**: JFR для поиска bottlenecks
4. **Облако**: развернуть в k8s, сравнить с большим QPS

---

**Для дебрифа**: показать benchmark графики, Restate UI (live journal), Temporal UI (event history). Обсудить trade-offs: latency vs throughput, простота vs зрелость.
