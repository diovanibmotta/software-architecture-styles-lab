package com.github.diovanibmotta.layered.business;

import com.github.diovanibmotta.layered.domain.Loan;
import com.github.diovanibmotta.layered.persistence.BookRepository;
import com.github.diovanibmotta.layered.persistence.LoanRepository;
import com.github.diovanibmotta.layered.persistence.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Business layer: every loan rule lives here, and only here. It talks to the persistence layer below and is
 * called by the presentation layer above; it never sees HTTP or SQL.
 */
@Service
public class LoanService {

    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final int loanDays;
    private final int maxActiveLoans;
    private final int finePerDayCents;

    LoanService(BookRepository books, MemberRepository members, LoanRepository loans,
                @Value("${library.loan-days}") int loanDays,
                @Value("${library.max-active-loans}") int maxActiveLoans,
                @Value("${library.fine-per-day-cents}") int finePerDayCents) {
        this.books = books;
        this.members = members;
        this.loans = loans;
        this.loanDays = loanDays;
        this.maxActiveLoans = maxActiveLoans;
        this.finePerDayCents = finePerDayCents;
    }

    @Transactional
    public Loan borrow(String isbn, String memberId) {
        // Lock book then member (always this order, so concurrent borrows cannot deadlock): the checks below
        // and the insert become one atomic decision, so the last copy / last loan slot is never given twice.
        var book = books.findByIsbnForUpdate(isbn).orElseThrow(() -> new NotFoundException("BOOK"));
        members.findByIdForUpdate(memberId).orElseThrow(() -> new NotFoundException("MEMBER"));

        LocalDate today = LocalDate.now();
        List<Loan> active = loans.findActiveByMember(memberId);
        if (active.stream().anyMatch(loan -> loan.isOverdue(today))) {
            throw new LoanRejectedException(LoanRejectedException.Reason.HAS_OVERDUE_LOANS);
        }
        if (active.size() >= maxActiveLoans) {
            throw new LoanRejectedException(LoanRejectedException.Reason.LOAN_LIMIT_REACHED);
        }
        if (loans.countActiveByIsbn(isbn) >= book.totalCopies()) {
            throw new LoanRejectedException(LoanRejectedException.Reason.NO_COPIES_AVAILABLE);
        }

        long id = loans.insert(isbn, memberId, today, today.plusDays(loanDays));
        return loans.findById(id).orElseThrow();
    }

    @Transactional
    public Loan giveBack(long loanId) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new NotFoundException("LOAN"));
        LocalDate today = LocalDate.now();
        int fine = (int) Math.max(0, ChronoUnit.DAYS.between(loan.dueOn(), today)) * finePerDayCents;
        if (!loan.isActive() || loans.markReturned(loanId, today, fine) == 0) {
            throw new LoanRejectedException(LoanRejectedException.Reason.ALREADY_RETURNED);
        }
        return loans.findById(loanId).orElseThrow();
    }

    /** No rule to apply: a pass-through request (see "architecture sinkhole" in the docs). */
    @Transactional(readOnly = true)
    public List<Loan> loansOf(String memberId) {
        members.findById(memberId).orElseThrow(() -> new NotFoundException("MEMBER"));
        return loans.findByMember(memberId);
    }
}
