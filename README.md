# ShareIt

Учебный проект: сервис обмена вещами, Sprint 16.

## Модули

- `gateway` (`shareit-gateway`) — внешний HTTP API на порту **8080**, Bean Validation и REST-клиенты.
- `server` (`shareit-server`) — бизнес-логика, JPA и PostgreSQL, порт **9090**.

Структура модулей и BaseClient взяты из [официального шаблона Sprint 16](https://github.com/yandex-praktikum/java-shareit/tree/add-item-requests-and-gateway).
Код пользователей, вещей, бронирований и комментариев предыдущего спринта перенесён в `server`.

## Сборка и запуск

Нужны Java 21, Maven и PostgreSQL.

Из корня проекта:

```shell
mvn clean test
mvn clean verify
mvn clean install
```

Server использует переменные `DB_URL`, `DB_USER`, `DB_PASSWORD`; значения по умолчанию сохранены из предыдущего спринта.
Схема создаётся через `server/src/main/resources/schema.sql`.
Для тестов используется H2 в режиме PostgreSQL; PostgreSQL для Maven-тестов не требуется.

Запустите приложения в двух терминалах из корня:

```shell
java -jar server/target/shareit-server-0.0.1-SNAPSHOT.jar
java -jar gateway/target/shareit-gateway-0.0.1-SNAPSHOT.jar
```

Gateway обращается к `http://localhost:9090` через property `shareit-server.url`.
Адрес можно переопределить переменной `SHAREIT_SERVER_URL`.
Внешние запросы отправляются на `http://localhost:8080`.

## Запросы вещей

Для всех маршрутов нужен заголовок `X-Sharer-User-Id`:

| Метод | Путь | Назначение |
| --- | --- | --- |
| POST | `/requests` | Создать запрос с непустым `description` |
| GET | `/requests` | Свои запросы, сначала новые |
| GET | `/requests/all` | Запросы других пользователей, сначала новые |
| GET | `/requests/{requestId}` | Запрос по ID и вещи в ответ на него |

Ответ содержит `id`, `description`, `created`, `items`.
В каждом элементе `items` возвращаются `id`, `name`, `ownerId`.
Чтобы предложить вещь, передайте необязательный `requestId` в `POST /items` вместе с обязательными `name`, `description`, `available`.

Валидация входных данных находится в gateway. PATCH пользователей и вещей поддерживает частичное обновление.
Проверки существования записей, прав владельца, доступности вещи и завершённого APPROVED-бронирования остаются в server.
Gateway сохраняет HTTP-статус и тело ошибки server.

## Проверки

- Интеграционные тесты сервисов с Spring context и H2: запросы, ответы вещами, сортировка, неизвестные ID, отсутствие N+1.
- MockMvc-тесты всех маршрутов обоих модулей: сервисы server и клиенты gateway заменены mock-объектами.
- JSON-тесты дат бронирования, ответов запросов и вложенных DTO.
- MockRestServiceServer-тесты REST-клиентов: методы, заголовки, параметры, тела и проксирование ошибок.

[Официальная коллекция Postman Sprint 16](https://raw.githubusercontent.com/yandex-praktikum/java-shareit/add-item-requests-and-gateway/postman/sprint.json) использует gateway на порту 8080.
Для её прогона нужны оба приложения и PostgreSQL. Коллекция создаёт тестовые данные; используйте отдельную тестовую БД или схему.
