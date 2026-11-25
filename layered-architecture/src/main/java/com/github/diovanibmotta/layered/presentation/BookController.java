package com.github.diovanibmotta.layered.presentation;

import com.github.diovanibmotta.layered.business.CatalogService;
import com.github.diovanibmotta.layered.business.CatalogService.BookAvailability;
import com.github.diovanibmotta.layered.business.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Presentation layer: HTTP in, HTTP out. Delegates every decision to the business layer. */
@RestController
class BookController {

    record BookView(String isbn, String title, String author, int totalCopies, int availableCopies) {
        static BookView of(BookAvailability a) {
            var b = a.book();
            return new BookView(b.isbn(), b.title(), b.author(), b.totalCopies(), a.availableCopies());
        }
    }

    private final CatalogService catalog;

    BookController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/books")
    List<BookView> list() {
        return catalog.catalog().stream().map(BookView::of).toList();
    }

    @GetMapping("/books/{isbn}")
    BookView get(@PathVariable String isbn) {
        return catalog.find(isbn).map(BookView::of).orElseThrow(() -> new NotFoundException("BOOK"));
    }
}
