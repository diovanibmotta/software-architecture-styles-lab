package com.github.diovanibmotta.layered.business;

import com.github.diovanibmotta.layered.domain.Book;
import com.github.diovanibmotta.layered.persistence.BookRepository;
import com.github.diovanibmotta.layered.persistence.LoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CatalogService {

    public record BookAvailability(Book book, int availableCopies) {
    }

    private final BookRepository books;
    private final LoanRepository loans;

    CatalogService(BookRepository books, LoanRepository loans) {
        this.books = books;
        this.loans = loans;
    }

    @Transactional(readOnly = true)
    public List<BookAvailability> catalog() {
        var active = loans.countActiveGroupedByIsbn();
        return books.findAll().stream()
                .map(book -> new BookAvailability(book, book.totalCopies() - active.getOrDefault(book.isbn(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BookAvailability> find(String isbn) {
        return books.findByIsbn(isbn)
                .map(book -> new BookAvailability(book, book.totalCopies() - loans.countActiveByIsbn(isbn)));
    }
}
