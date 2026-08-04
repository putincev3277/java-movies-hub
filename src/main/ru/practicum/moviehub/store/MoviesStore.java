package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesStore {
    private final List<Movie> movies = new ArrayList<>();

    public Movie add(Movie movie) {
        movies.add(movie);
        return movie;
    }

    public List<Movie> getAll() {
        // Возвращаем копию, чтобы внешний код не мог модифицировать внутренний список напрямую
        return new ArrayList<>(movies);
    }

    public Optional<Movie> getById(int id) {
        for (Movie m : movies) {
            if (m.getId() == id) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    public boolean removeById(int id) {
        return movies.removeIf(m -> m.getId() == id);
    }
    public void clear() {
        movies.clear();
    }
}
