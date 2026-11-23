# Price Parser - многопоточный парсер цен на книги

Итоговое задание вариант 2: парсер цен на товары.

Сервис принимает ссылки на товары или страницы каталога сайта [books.toscrape.com](https://books.toscrape.com), в фоне обходит их несколькими потоками, собирает название, цену и краткое описание каждого товара и сохраняет их во встроенную базу H2. Цены периодически обновляются по расписанию. Результаты доступны через эндпоинт `/answer` с фильтрацией, сортировкой и пагинацией.

На сайте нет даты публикации товара, поэтому вместо неё сохраняется дата и время сбора данных (`collectedAt`).

## Стек

- Java 21, Spring Boot 4.1.1 (Spring MVC, Spring Data JPA, Validation)
- H2 (in-memory) + H2 Console
- RestTemplate - загрузка страниц, Jsoup - разбор HTML
- Spring Boot Actuator, Micrometer, Prometheus, Grafana - метрики и мониторинг
- JMH - микробенчмарки обработки коллекций
- JMeter - нагрузочное тестирование REST API
- VisualVM, JFR, jcmd - профилирование JVM, дампы и анализ GC
- OpenTelemetry Java Agent, Jaeger - распределённый трейсинг
- JUnit 5, Mockito, MockMvc - тесты
- Maven (через Maven Wrapper, отдельная установка Maven не нужна)

## Требования

- JDK 21
- Docker с Docker Compose для Prometheus и Grafana
- JMeter 5.6.3 для нагрузочного тестирования
- VisualVM 2.2.2 для профилирования JVM
- Доступ в интернет (страницы загружаются с https://books.toscrape.com)

## Запуск

**Из терминала:**

```bash
./mvnw spring-boot:run
```

Для Windows: `mvnw.cmd spring-boot:run`.

Для запуска мониторинга:

```bash
docker compose up -d
```

- Grafana: http://localhost:3000, дашборд `Price Parser: производительность` в папке `Price Parser`.
- Prometheus: http://localhost:9090.
- Jaeger: http://localhost:16686.
- Метрики приложения: http://localhost:8080/actuator/prometheus.
- Состояние приложения: http://localhost:8080/actuator/health.

Grafana доступна анонимно с правами чтения. Остановить мониторинг можно командой `docker compose down`.

## Сценарий проверки

### Через веб-интерфейс

После запуска открыть в браузере http://localhost:8080:

1. В разделе «Запуск сбора данных» уже подставлены примеры ссылок - нажать «Запустить». Ссылка на сам сайт books.toscrape.com есть в начале страницы.
2. Под кнопкой раз в секунду обновляется строка со статусом задачи и счётчиками: найдено, сохранено, ошибок.
3. Когда задача перейдёт в `DONE`, таблица «Результаты» обновится автоматически. В ней можно фильтровать товары по названию и цене, менять сортировку и листать страницы.

Интерфейс работает через тот же REST API, что описан ниже.

### Через REST API

### 1. Запустить сбор данных

`POST http://localhost:8080/api/tasks`

```json
{
  "urls": [
    "https://books.toscrape.com/catalogue/category/books/mystery_3/index.html",
    "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html"
  ]
}
```

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"urls": ["https://books.toscrape.com/catalogue/category/books/mystery_3/index.html", "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html"]}'
```

Можно передавать как ссылки на отдельные товары, так и ссылки на страницы каталога или категории - в этом случае сервис сам соберёт ссылки на товары, переходя по страницам пагинации (не больше `parser.max-catalog-pages` страниц на одну ссылку). Принимаются только ссылки на `https://books.toscrape.com/`, иначе вернётся `400 Bad Request`.

Ответ `202 Accepted` приходит сразу, обработка идёт в фоне:

```json
{
  "id": 1,
  "status": "RUNNING",
  "sourceUrls": ["..."],
  "found": 0,
  "saved": 0,
  "failed": 0,
  "createdAt": "2026-09-11T23:40:00.123",
  "finishedAt": null
}
```

### 2. Проверить прогресс задачи

`GET http://localhost:8080/api/tasks/1`

```bash
curl http://localhost:8080/api/tasks/1
```

Поля `found` / `saved` / `failed` растут по мере обработки, по завершении `status` становится `DONE`. Для несуществующей задачи возвращается `404`.

### 3. Получить результаты

`GET http://localhost:8080/answer`

```bash
curl "http://localhost:8080/answer"
curl "http://localhost:8080/answer?sortBy=PRICE&direction=ASC"
curl "http://localhost:8080/answer?title=the&minPrice=20&maxPrice=40&sortBy=TITLE&direction=ASC&page=0&size=10"
```

| Параметр    | Описание                                           | По умолчанию   |
|-------------|----------------------------------------------------|----------------|
| `title`     | подстрока в названии (без учёта регистра)          | нет            |
| `minPrice`  | минимальная цена                                   | нет            |
| `maxPrice`  | максимальная цена                                  | нет            |
| `sortBy`    | поле сортировки: `PRICE`, `TITLE`, `COLLECTED_AT`  | `COLLECTED_AT` |
| `direction` | направление: `ASC`, `DESC`                         | `DESC`         |
| `page`      | номер страницы, с 0                                | `0`            |
| `size`      | размер страницы, от 1 до 100                       | `20`           |

Пример ответа:

```json
{
  "items": [
    {
      "id": 1,
      "url": "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html",
      "title": "A Light in the Attic",
      "price": 51.77,
      "description": "It's hard to imagine a world without A Light in the Attic...",
      "collectedAt": "2026-09-11T23:40:02.456"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 4. Посмотреть данные в H2

H2 Console: http://localhost:8080/h2-console

- JDBC URL: `jdbc:h2:mem:prices`
- User Name: `sa`
- Password: пустой

Таблица с товарами - `PRODUCTS`, её структура описана в разделе [Схема БД](#схема-бд).

### 5. Посмотреть лог

События приложения (запуск и завершение задач, обход страниц каталога, ошибки, плановые обновления) пишутся в консоль и в файл `logs/parser.log` - в каждой строке указан поток, в котором произошло событие.

## Архитектура

Приложение разделено на слои: контроллеры принимают HTTP-запросы, сервисы запускают обход и выбирают результаты, репозиторий Spring Data JPA работает с H2.

```
HTTP-клиент / веб-интерфейс
        |
web          TaskController (POST /api/tasks, GET /api/tasks/{id}), AnswerController (GET /answer)
        |
crawl        CrawlService - реестр задач, запуск производителя и потребителей
             CrawlJob - обход ссылок по схеме producer-consumer, CrawlTask - состояние и счётчики задачи
        |
client       PageFetcher - загрузка страниц через RestTemplate с повторами
parser       BookPageParser - разбор HTML через Jsoup
product      ProductProcessor - загрузка, разбор и сохранение товара
             ProductStorage - сохранение без дублей, ProductQueryService - выборка для /answer
             Product, ProductRepository - сущность и репозиторий H2
        |
H2 (таблица PRODUCTS)
```

| Пакет       | Что внутри                                                                 |
|-------------|----------------------------------------------------------------------------|
| `web`       | REST-контроллеры, запросы и ответы API                                     |
| `crawl`     | задачи сбора данных, производитель и потребители                           |
| `client`    | HTTP-клиент для books.toscrape.com                                         |
| `parser`    | разбор страниц товара и каталога                                           |
| `product`   | сущность товара, репозиторий, сохранение, выборка с фильтрами              |
| `scheduler` | плановое обновление цен                                                    |
| `logging`   | демон-поток записи лога в файл                                             |
| `config`    | пул потоков, RestTemplate, настройки `parser.*`                            |

Задачи сбора данных хранятся в памяти (`ConcurrentHashMap` в `CrawlService`), в базу попадают только товары.

## Схема БД

База H2 работает в памяти (`jdbc:h2:mem:prices`), схему создаёт Hibernate по сущности `Product` при запуске (`spring.jpa.hibernate.ddl-auto=create-drop`). После перезапуска приложения данные очищаются.

Таблица `PRODUCTS`:

| Колонка        | Тип                      | Ограничения                  | Описание                                |
|----------------|--------------------------|------------------------------|-----------------------------------------|
| `ID`           | `BIGINT`                 | первичный ключ, автоинкремент | идентификатор товара                    |
| `URL`          | `VARCHAR(255)`           | `NOT NULL`, `UNIQUE`         | ссылка на страницу товара               |
| `TITLE`        | `VARCHAR(255)`           | `NOT NULL`                   | название                                |
| `PRICE`        | `NUMERIC(10, 2)`         | `NOT NULL`                   | цена в фунтах                           |
| `DESCRIPTION`  | `CHARACTER LARGE OBJECT` |                              | краткое описание                        |
| `COLLECTED_AT` | `TIMESTAMP`              | `NOT NULL`                   | дата и время последнего сбора данных    |

При повторном сборе или плановом обновлении `ProductStorage` находит товар по `URL` и обновляет его запись. Ограничение `UNIQUE` на `URL` не даёт создать вторую запись для той же ссылки.

## Многопоточность

| Требование                                  | Где реализовано                                                                                                                                                                  |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Простые потоки (`Thread`, `Runnable`)        | `CrawlService` - для каждой задачи создаётся отдельный поток-производитель `new Thread(job::produce, "crawl-producer-N")`                                                         |
| Кастомный `ExecutorService`                  | `ParserConfig.parserExecutor` - фиксированный пул потоков `parser-worker-N`, в нём работают потоки-потребители и плановое обновление цен                                         |
| Планировщик                                  | `PriceRefreshScheduler` - `ScheduledExecutorService` раз в `parser.refresh-interval` заново загружает все сохранённые товары и обновляет цены (`invokeAll` на пуле воркеров)     |
| Блокирующие структуры                        | `CrawlJob` - схема producer-consumer на `LinkedBlockingQueue`: производитель кладёт ссылки на товары (`put`), потребители забирают (`take`); остановка через poison pill        |
| Примитивы синхронизации                      | `CountDownLatch` (производитель ждёт завершения всех потребителей), `ReentrantLock` в `ProductProcessor` (защита от дублей на всём участке сохранения, включая commit), `AtomicInteger`/`AtomicLong`, `volatile` |
| Потокобезопасные структуры данных            | `ConcurrentHashMap` (реестр задач), `LinkedBlockingQueue`, атомарные счётчики в `CrawlTask`                                                                                      |
| Демон-поток (логирование)                    | `ActivityLog` - демон-поток `activity-log-writer` забирает строки из `BlockingQueue` и пишет их в `logs/parser.log`                                                               |
| Встроенное хранилище H2                      | `Product` + `ProductRepository` (Spring Data JPA)                                                                                                                                 |
| Фильтрация через Parallel Stream             | `ProductQueryService` - фильтрация и сортировка результатов из H2 через `parallelStream()`                                                                                       |
| Межсервисное взаимодействие                  | `PageFetcher` - HTTP-запросы к books.toscrape.com через `RestTemplate` с таймаутами                                                                                              |

### Как обрабатывается задача

1. `POST /api/tasks` создаёт `CrawlTask`, отправляет в пул `parser.worker-count` потребителей и запускает отдельный поток-производитель.
2. Производитель обходит входные ссылки: ссылки на товары сразу кладёт в очередь, для страниц каталога загружает страницу, извлекает ссылки на товары и переходит по кнопке `next`.
3. Потребители забирают ссылки из очереди, загружают страницу товара, разбирают её и сохраняют или обновляют товар в H2.
4. Когда ссылки закончились, производитель кладёт в очередь по одной poison pill на каждого потребителя, ждёт их завершения на `CountDownLatch` и помечает задачу как `DONE`.

## Настройки

`src/main/resources/application.properties`:

| Свойство                   | Описание                                         | Значение          |
|----------------------------|--------------------------------------------------|-------------------|
| `parser.worker-count`      | число потоков в пуле обработчиков                | `4`               |
| `parser.queue-capacity`    | ёмкость очереди ссылок на товары                 | `100`             |
| `parser.max-catalog-pages` | максимум страниц каталога на одну ссылку         | `3`               |
| `parser.connect-timeout`   | таймаут подключения                              | `5s`              |
| `parser.read-timeout`      | таймаут чтения ответа                            | `15s`             |
| `parser.max-fetch-attempts`| число попыток загрузки страницы при сетевой ошибке или таймауте | `3` |
| `parser.refresh-interval`  | период планового обновления цен                  | `10m`             |
| `parser.user-agent`        | заголовок User-Agent                             | `price-parser/1.0`|
| `parser.log-file`          | файл лога                                        | `logs/parser.log` |

## Тесты

```bash
./mvnw test
```

- `BookPageParserTest` - разбор страниц товара и каталога, преобразование относительных ссылок, определение последней страницы
- `ProductQueryServiceTest` - фильтрация, сортировка и пагинация результатов
- `ProductProcessorTest` - параллельная обработка одного URL без дублей и ошибок уникального индекса
- `TaskControllerTest` - REST API задач: `202`, `400` для чужих ссылок, `404` для несуществующей задачи
- `CrawlJobTest` - задача завершается, даже если обработка одной из входных ссылок упала с исключением
- `PriceParserApplicationTests` - поднятие контекста приложения

## JMH

`ProductQueryBenchmark` сравнивает `for`, `stream()` и `parallelStream()` на наборах из 1 000, 10 000 и 100 000 товаров. Во всех вариантах выполняются одинаковые фильтрация по названию и цене, сортировка по цене и преобразование в `ProductResponse`. Отдельный вариант `parallelStreamPrecomputedTitle` измеряет оптимизацию, при которой условие поиска переводится в нижний регистр один раз на запрос, а не для каждого товара.

```bash
./mvnw test-compile exec:exec
```

Результаты в формате JSON сохраняются в `target/jmh-results.json`.

Результаты запуска на JDK 21.0.8, JMH 1.37, режим `avgt`, мс/операция:

| Количество товаров | `for` | `stream()` | `parallelStream()` |
|--------------------|-------|------------|--------------------|
| 1 000 | 0,034 ± 0,001 | 0,034 ± 0,002 | 0,090 ± 0,011 |
| 10 000 | 0,357 ± 0,078 | 0,330 ± 0,029 | 0,241 ± 0,041 |
| 100 000 | 4,915 ± 0,185 | 3,836 ± 0,035 | 1,560 ± 0,118 |

На 1 000 товаров `parallelStream()` оказался медленнее последовательных вариантов. На 10 000 и 100 000 товаров он показал меньшее среднее время. Погрешность указана для доверительного интервала 99,9%; каждая конфигурация выполнялась с тремя итерациями прогрева и пятью итерациями измерения в одной forked JVM.

## JMeter

Тест-план `jmeter/price-parser-baseline.jmx` запускает две группы пользователей:

- 20 пользователей выполняют по 25 GET-запросов к `/answer`, всего 500 запросов;
- 5 пользователей выполняют по 2 POST-запроса к `/api/tasks`, всего 10 запросов.

Перед запуском необходимо запустить приложение. Команда нагрузочного теста:

```bash
rm -rf build/jmeter && mkdir -p build/jmeter
jmeter -n -t jmeter/price-parser-baseline.jmx \
  -l build/jmeter/results.csv \
  -j build/jmeter/jmeter.log \
  -e -o build/jmeter/report
```

HTML-отчёт создаётся в `build/jmeter/report/index.html`.

Базовые результаты на JDK 21.0.8, JMeter 5.6.3:

| Запрос | Количество | Throughput, запросов/с | Среднее, мс | p95, мс | p99, мс | Ошибки |
|--------|------------|------------------------|-------------|---------|---------|--------|
| `GET /answer` | 500 | 105,49 | 2,126 | 4 | 10,99 | 0% |
| `POST /api/tasks` | 10 | 6,39 | 9,9 | 71 | 71 | 0% |

`POST /api/tasks` возвращает `202 Accepted` после постановки задачи, поэтому его HTTP latency не включает полное время парсинга. Micrometer зафиксировал 10 успешных обработок товара без ошибок; суммарное время обработки составило 7,241 секунды, в среднем 0,724 секунды на товар с учётом внешнего HTTP-запроса.

## VisualVM и диагностика JVM

Для записи событий GC приложение запускается командой:

```bash
mkdir -p build/visualvm
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Xlog:gc*:file=build/visualvm/gc.log:time,uptime,level,tags"
```

VisualVM открывается через `open -a VisualVM` в macOS и автоматически обнаруживает локальный процесс `PriceParserApplication`. PID процесса можно найти через `jcmd -l`.

Примеры команд диагностики:

```bash
jcmd <PID> JFR.start name=price-parser-load settings=profile \
  filename=build/visualvm/price-parser-load.jfr
jcmd <PID> Thread.print -l > build/visualvm/thread-dump.txt
jcmd <PID> JFR.stop name=price-parser-load
jcmd <PID> GC.heap_info > build/visualvm/heap-info.txt
jcmd <PID> GC.class_histogram > build/visualvm/class-histogram.txt
jcmd <PID> GC.heap_dump -gz=1 build/visualvm/price-parser-heap.hprof.gz
```

Heap dump открывается в VisualVM через `File -> Load`. JFR-запись анализируется командой `jfr view hot-methods build/visualvm/price-parser-load.jfr`; также доступны views `allocation-by-site`, `contention-by-site` и `gc-pauses`.

Результаты после трёх запусков JMeter, всего 1 530 HTTP-запросов без ошибок:

- JFR зафиксировал 19 пауз GC: средняя 4,22 мс, p95 13,5 мс, максимум 13,5 мс.
- Выраженного CPU hotspot не обнаружено: наибольшее число samples получило `StringLatin1.inflate`: 7,41%; остальные методы распределены между сериализацией метрик, строками и инфраструктурным кодом.
- События contention в JFR отсутствуют.
- Во время нагрузки четыре `parser-worker` находились в `RUNNABLE`; потоки `crawl-producer` ожидали `CountDownLatch`. Deadlock не обнаружен.
- До heap dump было занято 64,4 МБ heap. В histogram больше всего памяти занимали byte arrays: 11,1 МБ, `java.lang.Class`: 2,53 МБ и `String`: 2,37 МБ.
- Full GC при диагностике вызывался командами `GC.class_histogram` и `GC.heap_dump`; эти паузы не относятся к обычной нагрузке приложения.

Снимок VisualVM, GC-лог, JFR-запись, thread dump, heap dump и текстовые сводки сохраняются в `build/visualvm/`.

## Оптимизация производительности и корректности

Внесённые изменения:

- `ProductQueryService` переводит условие поиска в нижний регистр один раз на запрос. Раньше `filter.title().toLowerCase(Locale.ROOT)` вызывался для каждого товара.
- `ProductStorage.save` выполняет чтение и обновление товара в одной `@Transactional`; `findAllUrls` использует `@Transactional(readOnly = true)`.
- После добавления транзакции JMeter выявил гонку: блокировка в `ProductStorage` освобождалась до commit, и параллельная вставка одинакового URL вызывала ошибку уникального индекса. `ReentrantLock` перенесён в `ProductProcessor`, поэтому блокировка теперь охватывает вызов транзакционного метода и commit.
- N+1-запросы не обнаружены: у `Product` нет связей с другими сущностями. Уникальное ограничение `URL` уже создаёт индекс. Дополнительные индексы не добавлялись, поскольку фильтрация выполняется в памяти и не использует их.
- Блокировка сохранена: JFR не показал contention, а тест доказал необходимость синхронизации вокруг полного сохранения.

JMH, сравнение в одном запуске, мс/операция:

| Количество товаров | До: `parallelStream` | После: `parallelStreamPrecomputedTitle` |
|--------------------|----------------------|------------------------------------------|
| 1 000 | 0,087 ± 0,001 | 0,086 ± 0,003 |
| 10 000 | 0,222 ± 0,002 | 0,212 ± 0,002 |
| 100 000 | 1,521 ± 0,019 | 1,506 ± 0,016 |

На 10 000 товаров среднее время снизилось примерно на 4,5%. На 100 000 разница около 1%, а доверительные интервалы пересекаются, поэтому ускорение на этом размере не подтверждено.

Повторный JMeter после исправлений:

| Метрика `GET /answer` | До | После |
|-----------------------|----|-------|
| Запросов | 500 | 500 |
| Throughput, запросов/с | 105,49 | 104,71 |
| Среднее время, мс | 2,126 | 2,910 |
| p95, мс | 4 | 5 |
| HTTP-ошибки | 0% | 0% |

HTTP-ускорение не обнаружено: в тесте в базе находится один товар, а основное время занимают HTTP и инфраструктура приложения. После исправления гонки все 10 фоновых парсингов завершились успешно, ошибок уникального индекса нет. HTML-отчёт итогового запуска сохранён в `build/jmeter-fixed/report/index.html`. Регрессионный `ProductProcessorTest` запускает 10 параллельных сохранений одного URL и проверяет, что в базе остаётся одна запись.

## OpenTelemetry и Jaeger

В `compose.yaml` добавлен Jaeger 2.23.0 с OTLP HTTP-приёмником на порту 4318 и UI на порту 16686. Для автоматической инструментации используется OpenTelemetry Java Agent 2.34.0.

Скачивание агента:

```bash
mkdir -p build/telemetry
curl --fail --location \
  https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.34.0/opentelemetry-javaagent-2.34.0.jar \
  --output build/telemetry/opentelemetry-javaagent.jar
```

Запуск инфраструктуры и приложения с трейсингом:

```bash
docker compose up -d
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:build/telemetry/opentelemetry-javaagent.jar -Dotel.service.name=price-parser -Dotel.traces.exporter=otlp -Dotel.exporter.otlp.protocol=http/protobuf -Dotel.exporter.otlp.endpoint=http://localhost:4318 -Dotel.metrics.exporter=none -Dotel.logs.exporter=none"
```

Метрики и логи Java Agent отключены, потому что метрики уже собираются через Micrometer/Prometheus, а логи записываются через `ActivityLog`. Jaeger доступен на http://localhost:16686.

Пользовательские spans:

- `ProductProcessor.process`: `product.process`;
- `PageFetcher.fetch`: `product.fetch`;
- `BookPageParser.parseProduct`: `product.parse`;
- `ProductStorage.save`: `product.save`.

`CrawlService` передаёт текущий OpenTelemetry `Context` в потребители и поток-производитель через `context.wrap(...)`. Благодаря этому фоновые этапы попадают в trace исходного `POST /api/tasks`. Java Agent дополнительно создаёт spans для внешнего HTTP-запроса, Spring Data и JDBC.

`ProductProcessor` записывает в `ActivityLog` отдельную длительность загрузки, разбора и сохранения. Пример реального запроса: загрузка 1 247 мс, разбор 2 мс, сохранение 453 мс.

Проверенная трассировка содержит 13 spans и глубину 6: `POST /api/tasks`, `product.process`, все четыре этапа, внешний `GET`, вызовы репозитория, SQL `SELECT`/`INSERT` и `Transaction.commit`. Длительность всей трассировки: 1,8 секунды. Результат сохранён в `build/telemetry/trace.json`, диаграмма Jaeger: в `build/telemetry/jaeger-trace.png`.

## Материалы по производительности

- Полный отчёт: `docs/performance-report.md`.
- Презентация: `docs/performance-presentation.pptx` и `docs/performance-presentation.pdf`.
- Снимки Grafana, VisualVM и Jaeger: `docs/images/`.
