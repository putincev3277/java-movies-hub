package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler implements HttpHandler {

    private final MoviesStore store;
    private static final Gson GSON = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getRawQuery();

        // Проверка разрешённых методов для путей /movies и /movies/{id}
        if (path.equals("/movies") || path.matches("/movies/\\d+")) {
            if (!"GET".equalsIgnoreCase(method)
                    && !"POST".equalsIgnoreCase(method)
                    && !"DELETE".equalsIgnoreCase(method)) {
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

                    List<Movie> filtered = store.getByYear(year);
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
                sendError(ex, 400, "Некорректный ID");
                return;
            }

            Optional<Movie> opt = store.getById(id);
            if (opt.isPresent()) {
                sendJson(ex, 200, GSON.toJson(opt.get()));
            } else {
                sendErrorWithDetails(ex, 404, "Фильм с ID " + id + " не найден", List.of("Фильм не найден"));
            }
            return;
        }

        // 4. DELETE /movies/{id}
        if ("DELETE".equalsIgnoreCase(method) && path.matches("/movies/\\d+")) {
            int id;
            try {
                id = Integer.parseInt(path.substring("/movies/".length()));
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный ID");
                return;
            }

            boolean removed = store.removeById(id);
            if (removed) {
                ex.sendResponseHeaders(204, -1);
            } else {
                sendErrorWithDetails(ex, 404, "Фильм с ID " + id + " не найден", List.of("Фильм не найден"));
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

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            details.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            details.add("длина названия не должна превышать 100 символов");
        }

        int currentYear = Year.now().getValue();
        int minValidYear = 1888;
        int maxValidYear = currentYear + 1;
        int year = movie.getYear();

        if (year < minValidYear || year > maxValidYear) {
            details.add("год должен быть между " + minValidYear + " и " + maxValidYear);
        }

        if (!details.isEmpty()) {
            sendErrorWithDetails(ex, 422, "Ошибка валидации", details);
            return;
        }

        Movie added = store.add(movie);
        sendJson(ex, 201, GSON.toJson(added));
    }
}
