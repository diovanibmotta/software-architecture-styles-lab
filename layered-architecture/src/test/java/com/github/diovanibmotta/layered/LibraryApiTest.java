package com.github.diovanibmotta.layered;

import com.github.diovanibmotta.layered.business.LoanRejectedException;
import com.github.diovanibmotta.layered.business.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Goes through all layers (HTTP -> business -> persistence -> H2). Each test creates its own book/members. */
@SpringBootTest
@AutoConfigureMockMvc
class LibraryApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    LoanService loanService;

    @Test
    void borrowOnTimeAndReturnWithoutFine() throws Exception {
        String isbn = book(2);
        String ana = member();

        long loanId = loanIdOf(borrow(isbn, ana).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueOn").value(LocalDate.now().plusDays(14).toString())));
        mvc.perform(get("/books/" + isbn)).andExpect(jsonPath("$.availableCopies").value(1));

        mvc.perform(post("/loans/" + loanId + "/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineCents").value(0));
        mvc.perform(get("/books/" + isbn)).andExpect(jsonPath("$.availableCopies").value(2));
        mvc.perform(post("/loans/" + loanId + "/return"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_RETURNED"));
    }

    @Test
    void lastCopyGoesToOnlyOneMember() throws Exception {
        String isbn = book(1);
        borrow(isbn, member()).andExpect(status().isCreated());
        borrow(isbn, member()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_COPIES_AVAILABLE"));
    }

    @Test
    void memberCannotHoldMoreThanThreeBooks() throws Exception {
        String bruno = member();
        for (int i = 0; i < 3; i++) {
            borrow(book(1), bruno).andExpect(status().isCreated());
        }
        borrow(book(1), bruno).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LOAN_LIMIT_REACHED"));
    }

    @Test
    void overdueLoanBlocksNewLoansAndIsFinedOnReturn() throws Exception {
        String carla = member();
        String isbn = book(1);
        LocalDate borrowed = LocalDate.now().minusDays(19); // due 5 days ago
        jdbc.sql("INSERT INTO loans (isbn, member_id, borrowed_on, due_on) VALUES (?, ?, ?, ?)")
                .params(isbn, carla, borrowed, borrowed.plusDays(14)).update();
        long overdueId = jdbc.sql("SELECT id FROM loans WHERE member_id = ?").param(carla).query(Long.class).single();

        borrow(book(1), carla).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("HAS_OVERDUE_LOANS"));

        mvc.perform(post("/loans/" + overdueId + "/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineCents").value(5 * 200));
        borrow(book(1), carla).andExpect(status().isCreated());
        mvc.perform(get("/members/" + carla + "/loans")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unknownResourcesAndInvalidInput() throws Exception {
        borrow("no-such-isbn", member()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOK_NOT_FOUND"));
        borrow(book(1), "nobody").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MEMBER_NOT_FOUND"));
        mvc.perform(post("/loans/999999/return")).andExpect(status().isNotFound());
        mvc.perform(get("/members/nobody/loans")).andExpect(status().isNotFound());
        borrow("", "").andExpect(status().isBadRequest());
    }

    @Test
    void concurrentBorrowersNeverGetMoreCopiesThanExist() throws Exception {
        String isbn = book(2);
        int borrowers = 20;
        var start = new CountDownLatch(1);
        var granted = new AtomicInteger();
        var rejections = ConcurrentHashMap.<LoanRejectedException.Reason>newKeySet();
        try (var pool = Executors.newFixedThreadPool(borrowers)) {
            for (int i = 0; i < borrowers; i++) {
                String m = member();
                pool.submit(() -> {
                    start.await();
                    try {
                        loanService.borrow(isbn, m);
                        granted.incrementAndGet();
                    } catch (LoanRejectedException e) {
                        rejections.add(e.reason());
                    }
                    return null;
                });
            }
            start.countDown();
        }
        assertThat(granted).hasValue(2);
        assertThat(rejections).containsExactly(LoanRejectedException.Reason.NO_COPIES_AVAILABLE);
        mvc.perform(get("/books/" + isbn)).andExpect(jsonPath("$.availableCopies").value(0));
    }

    private ResultActions borrow(String isbn, String memberId) throws Exception {
        return mvc.perform(post("/loans").contentType(MediaType.APPLICATION_JSON)
                .content("{\"isbn\":\"%s\",\"memberId\":\"%s\"}".formatted(isbn, memberId)));
    }

    private long loanIdOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private String book(int copies) {
        String isbn = "T-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.sql("INSERT INTO books (isbn, title, author, total_copies) VALUES (?, 'Test book', 'Tester', ?)")
                .params(isbn, copies).update();
        return isbn;
    }

    private String member() {
        String id = "m-" + UUID.randomUUID();
        jdbc.sql("INSERT INTO members (id, name) VALUES (?, 'Test member')").param(id).update();
        return id;
    }
}
