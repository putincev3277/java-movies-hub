package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler implements HttpHandler {

    private static final Gson GSON = new Gson();
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getRawQuery();

        if (path.equals("/movies") || path.matches("/movies/\\d+")) {
            if (!"GET".equalsIgnoreCase(method)
                    && !"POST".equalsIgnoreCase(method)
                    && !"DELETE".equalsIgnoreCase(method)) {
                // Неподдерживаемый метод → 405
                sendError(ex, 405, "Method not allowed");
                return;
            }
        }

        // 1. GET /movies (все фильмы или фильтрация по year)
        if ("GET".equalsIgnoreCase(method) && "/movies".equals(path)) {
            if (query != null && query.startsWith("year=")) {
                try {
                    String yearStr = query.substring("year=".length());
                    if (yearStr.contains("&")) {
                        yearStr = yearStr.substring(0, yearStr.indexOf('&'));
                    }
                    int year = Integer.parseInt(yearStr);

                    List<Movie> filtered = new ArrayList<>();
                    for (Movie m : store.getAll()) {
                        if (m.getYear() == year) {
                            filtered.add(m);
                        }
                    }
                    sendJson(ex, 200, GSON.toJson(filtered));
                    return;
                } catch (NumberFormatException e) {
                    sendErrorWithDetails(ex, 400, "Некорректный параметр запроса",
                            List.of("Некорректный параметр запроса — 'year'"));
                    return;
                }
            }
            handleGetAll(ex);
            return;
        }

        // 2. POST /movies
        if ("POST".equalsIgnoreCase(method) && "/movies".equals(path)) {
            handlePost(ex);
            return;
        }

        // 3. GET /movies/{id}
        if ("GET".equalsIgnoreCase(method) && path.matches("/movies/\\d+")) {
            int id;
            try {
                id = Integer.parseInt(path.substring("/movies/".length()));
            } catch (NumberFormatException e) {
                // Некорректный ID (не число) → 400 Bad Request
                sendError(ex, 400, "Некорректный ID");
                return;
            }

            Optional<Movie> opt = store.getById(id);
            if (opt.isPresent()) {
                sendJson(ex, 200, GSON.toJson(opt.get()));
            } else {
                // Фильм не найден → 404 Not Found с единым форматом ошибки
                sendErrorWithDetails(ex, 404, "Фильм не найден", List.of("Фильм не найден"));
            }
            return;
        }


        // 4. DELETE /movies/{id}
        if ("DELETE".equalsIgnoreCase(method) && path.matches("/movies/\\d+")) {
            int id;
            try {
                id = Integer.parseInt(path.substring("/movies/".length()));
            } catch (NumberFormatException e) {
                // Если ID не число — 400 Bad Request
                sendError(ex, 400, "Некорректный ID");
                return;
            }

            boolean removed = store.removeById(id);
            if (removed) {
                // Успешное удаление: 204 No Content, без тела
                ex.sendResponseHeaders(204, -1);
                // Важно: НЕ пишем ничего в response body
            } else {
                // Фильм не найден: 404 + корректный формат ошибки
                sendErrorWithDetails(ex, 404, "Фильм не найден", List.of("Фильм не найден"));
            }
            return;
        }


        // 5. Если путь начинается с /movies, но не подошёл под наши шаблоны
        if (path.startsWith("/movies")) {
            sendError(ex, 400, "Invalid request: unsupported path or format");
            return;
        }

        // Всё остальное — 404
        sendError(ex, 404, "Not Found");
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        List<Movie> allMovies = store.getAll();
        sendJson(ex, 200, GSON.toJson(allMovies));
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentTypeHeader = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentTypeHeader == null || !contentTypeHeader.startsWith("application/json")) {
            // Требование: 415 Unsupported Media Type
            sendErrorWithDetails(ex, 415, "Unsupported Media Type",
                    List.of("Content-Type должен быть application/json"));
            return;
        }

        byte[] buffer = ex.getRequestBody().readAllBytes();
        String json = new String(buffer, StandardCharsets.UTF_8);

        Movie movie;
        try {
            movie = GSON.fromJson(json, Movie.class);
        } catch (com.google.gson.JsonSyntaxException e) {
            sendErrorWithDetails(ex, 400, "Invalid JSON",
                    List.of("Тело запроса не является корректным JSON"));
            return;
        }

        if (movie == null) {
            sendErrorWithDetails(ex, 400, "Invalid JSON",
                    List.of("JSON-объект равен null"));
            return;
        }

        List<String> details = new ArrayList<>();

        // Валидация title
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            details.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            details.add("длина названия не должна превышать 100 символов");
        }

        // Валидация year
        int currentYear = java.time.Year.now().getValue();
        int minValidYear = 1888;
        int maxValidYear = currentYear + 1;
        int year = movie.getYear();

        if (year < minValidYear || year > maxValidYear) {
            details.add("год должен быть между " + minValidYear + " и " + maxValidYear);
        }

        // Если есть ошибки валидации — 422 с details
        if (!details.isEmpty()) {
            sendErrorWithDetails(ex, 422, "Ошибка валидации", details);
            return;
        }

        // Успешное добавление: 201 Created, тело — фильм с ID
        Movie added = store.add(movie); // Предполагается, что store присваивает ID и возвращает объект с ним
        sendJson(ex, 201, GSON.toJson(added));
    }


    /**
     * Отправляет JSON-ответ (успех или ошибка) с правильным Content-Type.
     */
    protected void sendJson(HttpExchange ex, int statusCode, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Формирует и отправляет JSON-ответ об ошибке через Gson (без ручного экранирования).
     */
    private void sendErrorWithDetails(
            HttpExchange ex,
            int statusCode,
            String errorMessage,
            List<String> details
    ) throws IOException {
        ErrorResponse response = new ErrorResponse(errorMessage, details);
        String json = GSON.toJson(response);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }


    private void sendError(HttpExchange ex, int statusCode, String message) throws IOException {
        sendErrorWithDetails(ex, statusCode, "Error", List.of(message));
    }
}
