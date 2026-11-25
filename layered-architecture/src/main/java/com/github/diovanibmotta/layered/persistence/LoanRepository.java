package com.github.diovanibmotta.layered.persistence;

import com.github.diovanibmotta.layered.domain.Loan;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LoanRepository {

    private static final String COLUMNS = "id, isbn, member_id, borrowed_on, due_on, returned_on, fine_cents";

    private final JdbcClient jdbc;

    LoanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String isbn, String memberId, LocalDate borrowedOn, LocalDate dueOn) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO loans (isbn, member_id, borrowed_on, due_on) VALUES (?, ?, ?, ?)")
                .params(isbn, memberId, borrowedOn, dueOn)
                .update(keys, "id");
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public Optional<Loan> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM loans WHERE id = ?")
                .param(id).query(Loan.class).optional();
    }

    public List<Loan> findByMember(String memberId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM loans WHERE member_id = ? ORDER BY borrowed_on DESC, id DESC")
                .param(memberId).query(Loan.class).list();
    }

    public List<Loan> findActiveByMember(String memberId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM loans WHERE member_id = ? AND returned_on IS NULL")
                .param(memberId).query(Loan.class).list();
    }

    public int countActiveByIsbn(String isbn) {
        return jdbc.sql("SELECT COUNT(*) FROM loans WHERE isbn = ? AND returned_on IS NULL")
                .param(isbn).query(Integer.class).single();
    }

    /** isbn -> active loans, only for titles with at least one copy out. */
    public Map<String, Integer> countActiveGroupedByIsbn() {
        return jdbc.sql("SELECT isbn, COUNT(*) AS active FROM loans WHERE returned_on IS NULL GROUP BY isbn")
                .query((rs, n) -> Map.entry(rs.getString("isbn"), rs.getInt("active")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Conditional update: returns 0 when the loan was already closed (e.g. a concurrent return won). */
    public int markReturned(long id, LocalDate returnedOn, int fineCents) {
        return jdbc.sql("UPDATE loans SET returned_on = ?, fine_cents = ? WHERE id = ? AND returned_on IS NULL")
                .params(returnedOn, fineCents, id)
                .update();
    }
}
