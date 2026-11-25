package com.github.diovanibmotta.layered.persistence;

import com.github.diovanibmotta.layered.domain.Book;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Persistence layer: the only code that knows SQL. Knows nothing about loan rules or HTTP. */
@Repository
public class BookRepository {

    private final JdbcClient jdbc;

    BookRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Book> findAll() {
        return jdbc.sql("SELECT isbn, title, author, total_copies FROM books ORDER BY title")
                .query(Book.class).list();
    }

    public Optional<Book> findByIsbn(String isbn) {
        return jdbc.sql("SELECT isbn, title, author, total_copies FROM books WHERE isbn = ?")
                .param(isbn).query(Book.class).optional();
    }

    /** Row lock held until the surrounding transaction ends: serializes concurrent loans of the same title. */
    public Optional<Book> findByIsbnForUpdate(String isbn) {
        return jdbc.sql("SELECT isbn, title, author, total_copies FROM books WHERE isbn = ? FOR UPDATE")
                .param(isbn).query(Book.class).optional();
    }
}
