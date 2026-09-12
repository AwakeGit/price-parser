# Price Parser - многопоточный парсер цен на книги

Итоговое задание вариант 2: парсер цен на товары.

Сервис принимает ссылки на товары или страницы каталога сайта [books.toscrape.com](https://books.toscrape.com), в фоне обходит их несколькими потоками, собирает название, цену и краткое описание каждого товара и сохраняет их во встроенную базу H2. Цены периодически обновляются по расписанию. Результаты доступны через эндпоинт `/answer` с фильтрацией, сортировкой и пагинацией.

На сайте нет даты публикации товара, поэтому вместо неё сохраняется дата и время сбора данных (`collectedAt`).

## Стек

- Java 21, Spring Boot 4.1.1 (Spring MVC, Spring Data JPA, Validation)
- H2 (in-memory) + H2 Console
- RestTemplate - загрузка страниц, Jsoup - разбор HTML
- JUnit 5, Mockito, MockMvc - тесты
- Maven (через Maven Wrapper, отдельная установка Maven не нужна)

## Требования

- JDK 21
- Доступ в интернет (страницы загружаются с https://books.toscrape.com)

## Запуск

**Из терминала:**

```bash
./mvnw spring-boot:run
```

Для Windows: `mvnw.cmd spring-boot:run`.

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

Таблица с товарами - `PRODUCTS`.

### 5. Посмотреть лог

События приложения (запуск и завершение задач, обход страниц каталога, ошибки, плановые обновления) пишутся в консоль и в файл `logs/parser.log` - в каждой строке указан поток, в котором произошло событие.

## Многопоточность

| Требование                                  | Где реализовано                                                                                                                                                                  |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Простые потоки (`Thread`, `Runnable`)        | `CrawlService` - для каждой задачи создаётся отдельный поток-производитель `new Thread(job::produce, "crawl-producer-N")`                                                         |
| Кастомный `ExecutorService`                  | `ParserConfig.parserExecutor` - фиксированный пул потоков `parser-worker-N`, в нём работают потоки-потребители и плановое обновление цен                                         |
| Планировщик                                  | `PriceRefreshScheduler` - `ScheduledExecutorService` раз в `parser.refresh-interval` заново загружает все сохранённые товары и обновляет цены (`invokeAll` на пуле воркеров)     |
| Блокирующие структуры                        | `CrawlJob` - схема producer-consumer на `LinkedBlockingQueue`: производитель кладёт ссылки на товары (`put`), потребители забирают (`take`); остановка через poison pill        |
| Примитивы синхронизации                      | `CountDownLatch` (производитель ждёт завершения всех потребителей), `ReentrantLock` в `ProductStorage` (защита от дублей при одновременном сохранении), `AtomicInteger`/`AtomicLong`, `volatile` |
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
- `TaskControllerTest` - REST API задач: `202`, `400` для чужих ссылок, `404` для несуществующей задачи
- `PriceParserApplicationTests` - поднятие контекста приложения
