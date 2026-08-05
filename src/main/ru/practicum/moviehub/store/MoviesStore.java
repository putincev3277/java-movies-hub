package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.stream.Collectors;

public class MoviesStore {
    // Храним фильмы по ID: ключ = id, значение = Movie
    private final Map<Integer, Movie> moviesById = new HashMap<>();

    // Для генерации следующего ID (если нужно, чтобы сервер сам присваивал ID)
    private int nextId = 1;

    /**
     * Добавляет фильм. Если у фильма уже есть ID — используем его.
     * Если ID нет (0 или null) — присваиваем новый уникальный ID.
     */
    public Movie add(Movie movie) {
        int id;
        if (movie.getId() <= 0) {
            // Если ID не задан, генерируем новый
            id = nextId++;
            // Создаём новый объект с присвоенным ID, чтобы не менять исходный
            movie = new Movie(id, movie.getTitle(), movie.getYear());
        } else {
            id = movie.getId();

        }

        moviesById.put(id, movie);
        return movie; // Возвращаем объект с актуальным ID
    }

    /**
     * Возвращает список всех фильмов (порядок не важен, но для предсказуемости можно сортировать)
     */
    public List<Movie> getAll() {
        // Возвращаем копию списка, чтобы внешний код не мог модифицировать хранилище напрямую
        return new ArrayList<>(moviesById.values());
    }

    /**
     * Получает фильм по ID
     */
    public Optional<Movie> getById(int id) {
        return Optional.ofNullable(moviesById.get(id));
    }

    /**
     * Удаляет фильм по ID. Возвращает true, если фильм был найден и удалён.
     */
    public boolean removeById(int id) {
        return moviesById.remove(id) != null;
    }

    /**
     * Фильтрация по году (теперь без цикла по всему списку вручную — используем stream)
     */
    public List<Movie> getByYear(int year) {
        return moviesById.values().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
    }

    /**
     * Очистка хранилища (для тестов)
     */
    public void clear() {
        moviesById.clear();
        nextId = 1; // Сбрасываем генератор ID для чистоты тестов
    }
}
