package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    // Конструктор для тестов: можно передать свой MoviesStore, порт по умолчанию 8080
    public MoviesServer(MoviesStore store) {
        this(store, 8080);
    }

    // Конструктор по умолчанию: создаёт своё хранилище, порт 8080
    public MoviesServer() {
        this(new MoviesStore());
    }

    // НОВЫЙ: конструктор с хранилищем и портом
    public MoviesServer(MoviesStore store, int port) {
        try {
            this.store = store;
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
            this.server.createContext("/movies", new MoviesHandler(this.store));
            this.server.setExecutor(null);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер на порту " + port, e);
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public MoviesStore getStore() {
        return store;
    }
}
