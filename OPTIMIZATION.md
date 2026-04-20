# Оптимизация и Интерпретация Результатов

## 🎯 Как запустить benchmark

```bash
# 1. Запустить инфраструктуру
docker compose up -d

# 2. Запустить httpbin-proxy
./gradlew :httpbin-proxy:bootRun &

# 3. Тест Restate
./gradlew :restate-impl:run &
./gradlew :los-service:bootRun &

# Регистрация в Restate
curl -X POST http://localhost:9070/deployments \
  -H 'Content-Type: application/json' \
  -d '{"uri":"http://host.docker.internal:9080","use_http_11":true}'

# Benchmark
./scripts/simple-benchmark.sh 50

# 4. Тест Temporal (останавливаем Restate)
pkill -f 'restate-impl|los-service'
./gradlew :temporal-impl:run &
SERVER_PORT=8001 ./gradlew :los-service:bootRun &

# Benchmark
./scripts/simple-benchmark.sh 50

# 5. Визуализация
python3 scripts/plot_results.py
```

## 📊 Интерпретация результатов

### Разница < 10%
**Паритет** - оба движка одинаковы по производительности.
- Выбор зависит от экосистемы и опыта команды
- Temporal: зрелая экосистема, много интеграций
- Restate: проще операционка, меньше moving parts

### Restate быстрее на 15-30%
**Типичный результат** - архитектурное преимущество Restate:
- ✅ Меньше network hops (HTTP direct vs gRPC + polling)
- ✅ Нет worker polling overhead
- ✅ Журнал в памяти (пока не персистится)
- ⚠️ Но: меньше оптимизаций под high load

**Когда выбрать Restate:**
- Latency-sensitive операции (< 100ms требование)
- Средний QPS (< 1000 rps)
- Простота важнее масштаба
- Хотите быстро прототипировать

### Temporal быстрее на 15-30%
**Редкий результат** - значит хорошо настроили Temporal:
- ✅ Worker pool эффективнее утилизируется
- ✅ Batching activity schedules работает
- ✅ Connection pool настроен правильно
- ⚠️ Но: требует тюнинга

**Когда выбрать Temporal:**
- High throughput (> 10k rps)
- Длительные workflows (дни/недели)
- Нужны enterprise фичи (RBAC, multi-tenancy)
- Production-ready из коробки

### Разница > 30%
**Аномалия** - что-то не так:
- 🔍 Проверить httpbin-proxy latency (должно быть ~2s для AECB)
- 🔍 Проверить CPU/Memory (возможно throttling)
- 🔍 Один из движков некорректно настроен

## ⚡ Как ускорить Restate

### 1. Netty Threads
```bash
# Добавить в restate-impl/src/main/resources/application.properties
-Dio.netty.eventLoopThreads=16  # по умолчанию = CPU cores * 2
```

### 2. HTTP/2
```java
// RestateApp.java
RestateHttpEndpointBuilder.builder()
    .bind(new CreditCheckWorkflow())
    .withHttp2()  // если Restate SDK поддерживает
    .buildAndListen(9080);
```

### 3. Journal Compression (если доступно)
```
# Restate config
restate.journal.compression=true
```

## ⚡ Как ускорить Temporal

### 1. Worker Concurrency
```java
// TemporalApp.java
WorkerOptions options = WorkerOptions.newBuilder()
    .setMaxConcurrentActivityExecutionSize(50)  // default: 200
    .setMaxConcurrentWorkflowTaskExecutionSize(100)
    .build();

Worker worker = factory.newWorker(TASK_QUEUE, options);
```

### 2. Batching Activities
```java
// В CreditCheckWorkflowImpl
// Вместо последовательно:
ConsentRecord consent = activities.consent(...);
AecbReport aecb = activities.aecb(...);

// Параллельно (если возможно):
Promise<ConsentRecord> consentPromise = Async.function(activities::consent, ...);
Promise<AecbReport> aecbPromise = Async.function(activities::aecb, ...);
ConsentRecord consent = consentPromise.get();
AecbReport aecb = aecbPromise.get();
```

### 3. Activity Options Tuning
```java
ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(10))  // короче timeout
    .setScheduleToStartTimeout(Duration.ofSeconds(5))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(2)  // меньше retries
        .setBackoffCoefficient(1.5)
        .build())
    .build();
```

## ⚡ Общие оптимизации

### 1. HTTP Connection Pooling
```java
// common/client/WorkflowClient.java
private final OkHttpClient http = new OkHttpClient.Builder()
    .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
    .build();
```

### 2. Кеширование Product Configs
```java
// los-service/application/config/ProductConfigLoader.java
@Cacheable("product-configs")
public LoanProductConfig getConfig(String productId) { ... }
```

### 3. Async LOS Callbacks
```java
// Вместо синхронного:
client.notifyLos(appId, event);

// Асинхронно:
CompletableFuture.runAsync(() -> client.notifyLos(appId, event));
```

### 4. Уменьшить httpbin-proxy Latency
```yaml
# httpbin-proxy/src/main/resources/application.yaml
simulation:
  latency:
    aecb: 500  # вместо 2000
    open_banking: 200  # вместо 500
```

## 📈 Ожидаемые результаты оптимизации

| Оптимизация | Прирост Restate | Прирост Temporal |
|-------------|-----------------|------------------|
| HTTP pooling | +10-15% | +10-15% |
| Worker concurrency | - | +20-30% |
| Async callbacks | +15-20% | +15-20% |
| Уменьшить httpbin latency | +50%+ | +50%+ |
| Кеширование configs | +5% | +5% |

**Комплексная оптимизация**: можно достичь 2-3x улучшения при правильном тюнинге.

## 🔬 Профилирование

### JVM Profiling
```bash
# Добавить при запуске
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=60s,filename=profile.jfr
```

### Анализ с JFR
```bash
jcmd <PID> JFR.start duration=60s filename=restate-profile.jfr
jcmd <PID> JFR.stop

# Открыть в JMC (Java Mission Control)
jmc restate-profile.jfr
```

## 🎓 Выводы для дебрифа

**Для Ashish и Vitaliy:**

1. **Restate преимущество**: латентность из-за простоты архитектуры
2. **Temporal преимущество**: throughput и stability при правильном тюнинге
3. **Hexagonal arch работает**: один WorkflowClient для обоих
4. **Реальный выбор** зависит от:
   - Требования к latency (< 50ms → Restate, < 500ms → любой)
   - Throughput (< 1k rps → любой, > 10k rps → Temporal)
   - Опыт команды (есть Temporal expertise → Temporal)
   - Операционка (простота → Restate, зрелость → Temporal)

**Next steps:**
- [ ] Добавить Grafana/Prometheus метрики
- [ ] Stress test: 10k+ rps sustained load
- [ ] Recovery test: kill в середине workflow
- [ ] Cost analysis: infrastructure per 1M workflows
