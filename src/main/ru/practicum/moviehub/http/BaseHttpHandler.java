package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHttpHandler {

    protected static final String CT_JSON = "application/json; charset=UTF-8";
    private static final Gson GSON = new Gson();

    public abstract void handle(HttpExchange ex) throws IOException;

    /**
     * Отправляет JSON-ответ (успех или ошибка) с правильным Content-Type.
     */
    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Формирует и отправляет JSON-ответ об ошибке с деталями (для валидации, невалидных ID и т.п.).
     */
    protected void sendErrorWithDetails(
            HttpExchange ex,
            int statusCode,
            String errorMessage,
            List<String> details
    ) throws IOException {
        ErrorResponse response = new ErrorResponse(errorMessage, details);
        String json = GSON.toJson(response);
        sendJson(ex, statusCode, json);
    }

    /**
     * Привет, Ирек! Спасибо за комментарий!
     * Из-за отпуска немного запоздал с проектом...
     */
    protected void sendError(HttpExchange ex, int statusCode, String message) throws IOException {
        sendErrorWithDetails(ex, statusCode, "Error", java.util.List.of(message));
    }
}
