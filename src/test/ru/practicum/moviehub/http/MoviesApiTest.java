package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.model.Movie;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = new MoviesServer();
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void beforeEach() {
        assertNotNull(server, "Сервер должен быть создан в @BeforeAll");
        // Очистка хранилища перед каждым тестом — единственное место для этого
        MoviesStore store = server.getStore();
        store.clear();
    }

    // --- Вспомогательные методы (убирают дублирование) ---

    private HttpResponse<String> sendPostRequest(String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Проверяет, что ответ — ошибка валидации (422) с нужным сообщением.
     */
    private void assertBadRequestWithError(HttpResponse<String> resp, String expectedErrorSubstring) {
        assertEquals(422, resp.statusCode(), "При ошибке валидации должен возвращаться статус 422");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ об ошибке должен иметь Content-Type: application/json; charset=UTF-8");

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\""), "Тело ответа должно содержать поле \"error\"");
        assertTrue(body.toLowerCase().contains(expectedErrorSubstring.toLowerCase()),
                "Сообщение об ошибке должно объяснять проблему. Ожидалась подстрока: '" +
                        expectedErrorSubstring + "'. Получено: " + body);
    }

    private HttpResponse<String> sendMethodRequest(String method) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Проверяет ответ 404 с ожидаемым сообщением об ошибке.
     */
    private void assertNotFound(HttpResponse<String> resp, String expectedMessageSubstring) {
        assertEquals(404, resp.statusCode(), "Должен возвращаться статус 404");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Content-Type должен быть application/json; charset=UTF-8");

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\""), "Тело ответа должно содержать поле \"error\"");
        assertTrue(body.toLowerCase().contains(expectedMessageSubstring.toLowerCase()),
                "Сообщение об ошибке должно содержать ожидаемую подстроку. Получено: " + body);
    }

    /**
     * Проверяет, что тело ответа — валидный JSON-массив.
     */
    private void assertJsonArray(String body) {
        String trimmed = body.trim();
        assertTrue(trimmed.startsWith("[") && trimmed.endsWith("]"),
                "Тело ответа должно быть JSON-массивом. Получено: " + trimmed);
    }

    /**
     * Проверяет, что тело ответа — валидный JSON-объект.
     */
    private void assertJsonObject(String body) {
        String trimmed = body.trim();
        assertTrue(trimmed.startsWith("{") && trimmed.endsWith("}"),
                "Тело ответа должно быть JSON-объектом. Получено: " + trimmed);
    }

    // --- Тесты ---

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
        assertEquals("[]", resp.body().trim());
    }

    @Test
    void getMovies_whenNotEmpty_returnsFilledArray() throws Exception {
        MoviesStore store = server.getStore();
        store.add(new Movie(1, "Inception", 2010));
        store.add(new Movie(2, "Interstellar", 2014));
        store.add(new Movie(3, "The Dark Knight", 2008));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));

        String body = resp.body().trim();
        assertJsonArray(body);
        assertTrue(body.contains("\"title\":\"Inception\""));
        assertTrue(body.contains("\"title\":\"Interstellar\""));
        assertTrue(body.contains("\"title\":\"The Dark Knight\""));
    }

    @Test
    void postMovies_addsMovie_onValidData() throws Exception {
        String jsonBody = "{\"id\":101,\"title\":\"The Matrix\",\"year\":1999}";
        HttpResponse<String> resp = sendPostRequest(jsonBody);

        assertEquals(201, resp.statusCode(), "POST должен вернуть 201");
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));

        MoviesStore store = server.getStore();
        assertEquals(1, store.getAll().size());

        Movie addedMovie = store.getAll().get(0);
        assertEquals(101, addedMovie.getId());
        assertEquals("The Matrix", addedMovie.getTitle());
        assertEquals(1999, addedMovie.getYear());
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpResponse<String> resp = sendMethodRequest("PUT");

        assertEquals(405, resp.statusCode(), "Неподдерживаемый метод должен вернуть 405");
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith("application/json"));
        assertTrue(resp.body().trim().contains("\"error\""));
    }

    @Test
    void postMovies_withEmptyTitle_returnsError() throws Exception {
        String jsonBody = "{\"id\":102,\"title\":\"\",\"year\":2000}";
        HttpResponse<String> resp = sendPostRequest(jsonBody);
        // Используем общую проверку ошибок валидации
        assertBadRequestWithError(resp, "не должно быть пустым");
    }

    @Test
    void postMovies_withLongTitle_returnsError() throws Exception {
        String longTitle = "A".repeat(101);
        String jsonBody = "{\"id\":103,\"title\":\"" + longTitle + "\",\"year\":2001}";

        HttpResponse<String> resp = sendPostRequest(jsonBody);
        assertBadRequestWithError(resp, "превышать 100");
    }

    @Test
    void postMovies_withInvalidYear_returnsError() throws Exception {
        int currentYear = Year.now().getValue();
        int maxValidYear = currentYear + 1;

        // Слишком старый год
        String jsonBodyTooOld = "{\"id\":104,\"title\":\"Old Film\",\"year\":1800}";
        HttpResponse<String> respOld = sendPostRequest(jsonBodyTooOld);
        // Теперь используем общий метод вместо ручной проверки
        assertBadRequestWithError(respOld, "1888");

        // Год из будущего
        String jsonBodyFuture = "{\"id\":105,\"title\":\"Future Film\",\"year\":" + (maxValidYear + 5) + "}";
        HttpResponse<String> respFuture = sendPostRequest(jsonBodyFuture);
        assertBadRequestWithError(respFuture, String.valueOf(maxValidYear));
    }

    @Test
    void postMovies_withWrongContentType_returnsError() throws Exception {
        String jsonBody = "{\"id\":106,\"title\":\"Wrong ContentType\",\"year\":2005}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                // ГЛАВНОЕ: ставим НЕПРАВИЛЬНЫЙ Content-Type
                .header("Content-Type", "text/plain")
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(415, resp.statusCode(), "При неправильном Content-Type должен возвращаться статус 415");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ об ошибке тоже должен иметь правильный Content-Type");

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\""), "Тело ответа должно содержать поле \"error\"");
        assertTrue(body.toLowerCase().contains("content") || body.toLowerCase().contains("type"),
                "Сообщение об ошибке должно упоминать проблему с Content-Type. Получено: " + body);
    }

    @Test
    void postMovies_withInvalidJson_returnsError() throws Exception {
        // ГЛАВНОЕ: передаём НЕВАЛИДНЫЙ JSON (пропущена закрывающая кавычка у title)
        String invalidJsonBody = "{\"id\":107,\"title\":\"Invalid JSON\",\"year\":2007";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(invalidJsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(400, resp.statusCode(), "При некорректном JSON должен возвращаться статус 400");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ об ошибке тоже должен иметь правильный Content-Type");

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\""), "Тело ответа должно содержать поле \"error\"");
        assertTrue(body.toLowerCase().contains("json") || body.toLowerCase().contains("invalid"),
                "Сообщение об ошибке должно упоминать проблему с JSON. Получено: " + body);
    }

    @Test
    void getMovie_byExistingId_returnsMovie() throws Exception {
        // 1. Сначала добавляем фильм в хранилище
        MoviesStore store = server.getStore();
        Movie expected = new Movie(42, "The Shawshank Redemption", 1994);
        store.add(expected);

        // 2. Делаем GET-запрос по ID
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/42"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        // 3. Проверяем статус 200
        assertEquals(200, resp.statusCode(), "Существующий фильм должен возвращаться со статусом 200");

        // 4. Проверяем заголовок Content-Type
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ должен иметь Content-Type: application/json; charset=UTF-8");

        // 5. Проверяем тело ответа (JSON)
        String body = resp.body().trim();
        assertJsonObject(body);
        assertTrue(body.contains("\"id\":42"), "В ответе должен быть id=42");
        assertTrue(body.contains("\"title\":\"The Shawshank Redemption\""), "В ответе должен быть правильный title");
        assertTrue(body.contains("\"year\":1994"), "В ответе должен быть правильный year");
    }

    @Test
    void getMovie_byNonExistingId_returns404() throws Exception {
        // Больше не очищаем хранилище вручную — это делает @BeforeEach
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertNotFound(resp, "фильм не найден");
    }

    @Test
    void getMovie_withInvalidId_returns400() throws Exception {
        // Передаём НЕЧИСЛОВОЙ ID (строку вместо числа)
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc123"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(400, resp.statusCode(), "Некорректный формат ID должен возвращать статус 400");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ошибка должна иметь правильный Content-Type");

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\""), "Тело ошибки должно содержать поле \"error\"");
        assertTrue(body.toLowerCase().contains("invalid") || body.toLowerCase().contains("id"),
                "Сообщение должно указывать на проблему с ID. Получено: " + body);
    }

    @Test
    void deleteMovie_byExistingId_removesMovie() throws Exception {
        // 1. Добавляем фильм в хранилище
        MoviesStore store = server.getStore();
        int idToDelete = 123;
        store.add(new Movie(idToDelete, "The Godfather", 1972));

        // Проверяем, что фильм есть до удаления
        assertEquals(1, store.getAll().size());

        // 2. Делаем DELETE-запрос
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + idToDelete))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        // 3. Проверяем статус 204 No Content
        assertEquals(204, resp.statusCode(), "Удаление существующего фильма должно возвращать 204");

        // ВАЖНО: при статусе 204 НЕ должно быть тела и часто не проверяют Content-Type.
        // Поэтому проверки body и Content-Type здесь убираем.

        // 4. Проверяем, что фильм действительно удалён из хранилища
        assertEquals(0, store.getAll().size(), "После DELETE фильм должен исчезнуть из хранилища");
    }

    @Test
    void deleteMovie_byNonExistingId_returns404() throws Exception {
        // Больше не очищаем хранилище вручную — это делает @BeforeEach
        int nonExistingId = 9999;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + nonExistingId))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertNotFound(resp, "фильм не найден");
    }

    @Test
    void deleteMovie_withInvalidId_returns400() throws Exception {
        String invalidId = "abc123"; // Не число

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + invalidId))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(400, resp.statusCode(),
                "При невалидном формате ID должен возвращаться статус 400");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ об ошибке должен иметь Content-Type: application/json; charset=UTF-8");

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\""),
                "Тело ответа должно содержать поле \"error\"");
        assertTrue(body.toLowerCase().contains("invalid") ||
                        body.toLowerCase().contains("format") ||
                        body.toLowerCase().contains("id"),
                "Сообщение об ошибке должно указывать на проблему с форматом ID. Получено: " + body);
    }

    @Test
    void getMovies_byYear_returnsFilteredList() throws Exception {
        MoviesStore store = server.getStore();
        // Хранилище уже очищено в @BeforeEach — не делаем это вручную

        // Добавляем фильмы разных лет
        store.add(new Movie(1, "Inception", 2010));
        store.add(new Movie(2, "Interstellar", 2014));
        store.add(new Movie(3, "The Dark Knight", 2008));
        store.add(new Movie(4, "Tenet", 2020));
        store.add(new Movie(5, "Dunkirk", 2017));

        int targetYear = 2014;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + targetYear))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(200, resp.statusCode(),
                "Запрос с параметром year должен возвращать 200");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ должен иметь Content-Type: application/json; charset=UTF-8");

        String body = resp.body().trim();
        assertJsonArray(body);

        // Проверяем, что в ответе есть фильмы нужного года
        assertTrue(body.contains("\"year\":" + targetYear),
                "В ответе должны быть фильмы с годом " + targetYear);

        // Убедимся, что нет фильмов другого года (грубая проверка по одному примеру)
        assertFalse(body.contains("\"year\":2010"),
                "В ответе не должно быть фильмов другого года (проверка на примере 2010)");
        assertFalse(body.contains("\"year\":2008"),
                "В ответе не должно быть фильмов другого года (проверка на примере 2008)");
    }

    @Test
    void getMovies_byYear_returnsEmptyList_whenNoMatches() throws Exception {
        MoviesStore store = server.getStore();
        // Хранилище уже очищено в @BeforeEach — не делаем это вручную

        // Добавляем фильмы, но НЕ за целевой год
        store.add(new Movie(1, "Inception", 2010));
        store.add(new Movie(2, "Interstellar", 2014));
        store.add(new Movie(3, "The Dark Knight", 2008));

        int nonExistentYear = 3000; // Такого года точно нет

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + nonExistentYear))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(200, resp.statusCode(),
                "Запрос с несуществующим годом должен возвращать 200");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ должен иметь Content-Type: application/json; charset=UTF-8");

        String body = resp.body().trim();
        assertJsonArray(body);

        // Ключевая проверка: пустой массив
        assertEquals("[]", body,
                "При отсутствии фильмов заданного года должен возвращаться пустой JSON-массив []");
    }

    @Test
    void getMovies_byYear_returns400_whenYearIsNotNumber() throws Exception {
        String invalidYear = "abc";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=" + invalidYear))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(400, resp.statusCode(),
                "При невалидном параметре year должен возвращаться статус 400 Bad Request");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Ответ при ошибке должен иметь Content-Type: application/json; charset=UTF-8");

        String body = resp.body().trim();
        assertTrue(body.contains("\"error\""),
                "Тело ответа должно содержать поле \"error\"");
        assertTrue(body.toLowerCase().contains("invalid") ||
                        body.toLowerCase().contains("year") ||
                        body.toLowerCase().contains("format"),
                "Сообщение об ошибке должно указывать на проблему с параметром year. Получено: " + body);
    }
}

