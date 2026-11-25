package com.github.diovanibmotta.layered.domain;

/** Shared domain object: carried through every layer, depends on none of them. */
public record Book(String isbn, String title, String author, int totalCopies) {
}
